#!/usr/bin/env python3
# GeoMys-Common — application de saisie naturaliste pour GeoNature, Android et iOS.
# Copyright (C) 2026 ANA - CEN Ariège
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

"""Outils d'essai du prototype carte (lot 0) de GeoMys Common.

    python3 carte_test.py mbtiles <fichier.mbtiles>
        Génère un MBTiles de tuiles colorées (une couleur par zoom, damier de 32 px) autour de
        Foix, zooms 10 à 16. Sert au fond « MBTiles de test » de l'appli.

    python3 carte_test.py serveur [--port 8765] [--journal requetes.log]
        Sert des tuiles colorées sur http://127.0.0.1:<port>/tuiles/{z}/{x}/{y}.png et journalise
        chaque requête avec son User-Agent. L'émulateur Android le joint par 10.0.2.2, le
        simulateur iOS par localhost. L'arrêter permet de vérifier qu'une zone téléchargée pour le
        hors-ligne s'affiche sans réseau.

Aucune dépendance : bibliothèque standard seulement (PNG écrit à la main).
"""

import argparse
import http.server
import math
import re
import sqlite3
import struct
import sys
import time
import zlib

TAILLE = 256

# Emprise du MBTiles de test : autour du château de Foix.
OUEST, SUD, EST, NORD = 1.580, 42.950, 1.635, 42.980
ZOOM_MIN, ZOOM_MAX = 10, 16

PALETTE_MBTILES = [(46, 125, 50), (239, 108, 0), (0, 121, 107), (249, 168, 37), (93, 64, 55), (124, 179, 66), (216, 67, 21)]
PALETTE_SERVEUR = [(57, 73, 171), (123, 31, 162), (2, 119, 189), (69, 39, 160), (0, 96, 100), (173, 20, 87), (40, 53, 147)]


def png(lignes):
    """Encode une image RGB 8 bits ; lignes = liste de bytes (3 octets par pixel)."""
    brut = b"".join(b"\x00" + ligne for ligne in lignes)

    def bloc(type_, donnees):
        return struct.pack(">I", len(donnees)) + type_ + donnees + struct.pack(">I", zlib.crc32(type_ + donnees) & 0xFFFFFFFF)

    entete = struct.pack(">IIBBBBB", TAILLE, TAILLE, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + bloc(b"IHDR", entete) + bloc(b"IDAT", zlib.compress(brut, 9)) + bloc(b"IEND", b"")


def tuile(z, x, y, palette):
    """Damier de deux teintes de la couleur du zoom, bordure blanche de 3 px : chaque tuile et
    chaque niveau de zoom se voient à l'écran."""
    r, g, b = palette[z % len(palette)]
    fonce = bytes((r, g, b))
    clair = bytes((min(255, r + 40), min(255, g + 40), min(255, b + 40)))
    blanc = b"\xff\xff\xff"
    lignes = []
    for py in range(TAILLE):
        if py < 3 or py >= TAILLE - 3:
            lignes.append(blanc * TAILLE)
            continue
        cases = []
        for case in range(TAILLE // 32):
            teinte = fonce if (case + py // 32 + x + y) % 2 == 0 else clair
            cases.append(teinte * 32)
        ligne = bytearray(b"".join(cases))
        ligne[0:9] = blanc * 3
        ligne[-9:] = blanc * 3
        lignes.append(bytes(ligne))
    return png(lignes)


def tuile_depuis_lonlat(lon, lat, z):
    n = 2 ** z
    x = int((lon + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2.0 * n)
    return x, y


def generer_mbtiles(chemin):
    base = sqlite3.connect(chemin)
    base.executescript(
        """
        DROP TABLE IF EXISTS metadata;
        DROP TABLE IF EXISTS tiles;
        CREATE TABLE metadata (name TEXT, value TEXT);
        CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB);
        CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row);
        """
    )
    metadonnees = {
        "name": "GeoMys Common — MBTiles de test",
        "type": "baselayer",
        "version": "1",
        "description": "Tuiles colorées générées par outils/prototype-carte/carte_test.py",
        "format": "png",
        "minzoom": str(ZOOM_MIN),
        "maxzoom": str(ZOOM_MAX),
        "bounds": f"{OUEST},{SUD},{EST},{NORD}",
        "center": f"{(OUEST + EST) / 2},{(SUD + NORD) / 2},14",
    }
    base.executemany("INSERT INTO metadata VALUES (?, ?)", metadonnees.items())
    total = 0
    for z in range(ZOOM_MIN, ZOOM_MAX + 1):
        x_min, y_min = tuile_depuis_lonlat(OUEST, NORD, z)
        x_max, y_max = tuile_depuis_lonlat(EST, SUD, z)
        for x in range(x_min, x_max + 1):
            for y in range(y_min, y_max + 1):
                rangee_tms = (2 ** z - 1) - y
                base.execute("INSERT INTO tiles VALUES (?, ?, ?, ?)", (z, x, rangee_tms, tuile(z, x, y, PALETTE_MBTILES)))
                total += 1
    base.commit()
    base.execute("VACUUM")
    base.close()
    print(f"{chemin} : {total} tuiles, zooms {ZOOM_MIN} à {ZOOM_MAX}")


def servir(port, chemin_journal, entetes=False):
    journal = open(chemin_journal, "a", encoding="utf-8")
    motif = re.compile(r"/tuiles/(\d+)/(\d+)/(\d+)\.png")

    class Gestionnaire(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            # Tous les User-Agent reçus : un en-tête ajouté par l'appli peut arriver en doublon de
            # celui du moteur HTTP.
            agents = self.headers.get_all("User-Agent") or [""]
            journal.write(f"{time.strftime('%H:%M:%S')} {self.path} User-Agent={' | '.join(agents)}\n")
            if entetes:
                journal.write(f"    {dict(self.headers.items())}\n")
            journal.flush()
            correspondance = motif.fullmatch(self.path)
            if not correspondance:
                self.send_error(404)
                return
            z, x, y = (int(v) for v in correspondance.groups())
            donnees = tuile(z, x, y, PALETTE_SERVEUR)
            self.send_response(200)
            self.send_header("Content-Type", "image/png")
            self.send_header("Content-Length", str(len(donnees)))
            self.end_headers()
            self.wfile.write(donnees)

        def log_message(self, *args):
            pass

    serveur = http.server.ThreadingHTTPServer(("127.0.0.1", port), Gestionnaire)
    print(f"Tuiles de test sur http://127.0.0.1:{port}/tuiles/{{z}}/{{x}}/{{y}}.png, journal : {chemin_journal}")
    sys.stdout.flush()
    serveur.serve_forever()


def main():
    analyseur = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sous = analyseur.add_subparsers(dest="commande", required=True)
    p_mbtiles = sous.add_parser("mbtiles")
    p_mbtiles.add_argument("fichier")
    p_serveur = sous.add_parser("serveur")
    p_serveur.add_argument("--port", type=int, default=8765)
    p_serveur.add_argument("--journal", default="requetes-tuiles.log")
    p_serveur.add_argument("--entetes", action="store_true", help="journalise aussi tous les en-têtes reçus")
    arguments = analyseur.parse_args()
    if arguments.commande == "mbtiles":
        generer_mbtiles(arguments.fichier)
    else:
        servir(arguments.port, arguments.journal, arguments.entetes)


if __name__ == "__main__":
    main()
