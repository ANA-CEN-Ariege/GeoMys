#!/usr/bin/env python3
# GeoMys — application de saisie naturaliste pour GeoNature, Android et iOS.
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

"""Un serveur GeoNature réduit à l'authentification, pour essayer l'écran « Connexion GeoNature »
avec les vrais moteurs HTTP (OkHttp, NSURLSession), sans serveur réel.

Il se comporte comme GeoNature sur les points délicats du portage :
- POST /api/auth/login : 200 + jeton + DEUX Set-Cookie pour alice / secret ; sinon un 302 vers
  la page de connexion (/#/login), que le client du login ne doit pas suivre ;
- GET /api/gn_commons/config : la version de GeoNature ;
- GET /api/gn_commons/modules : 200 seulement avec le jeton ET les deux cookies, 401 sinon.

Usage : outils/geonature-simule/serveur.py [--port 8766]
Écoute sur 127.0.0.1 : un émulateur Android y accède par 10.0.2.2, un téléphone par
`adb reverse tcp:8766 tcp:8766`, le simulateur iOS par localhost.
"""

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

JETON = "jeton-de-test"
COOKIES = ("session=s1", "csrf=c2")


class Gestionnaire(BaseHTTPRequestHandler):
    def journaliser(self):
        entetes = {k: self.headers.get(k) for k in ("Authorization", "Cookie", "Content-Type", "User-Agent") if self.headers.get(k)}
        print(f"{self.command} {self.path} {json.dumps(entetes, ensure_ascii=False)}", flush=True)

    def repondre(self, code, corps=b"", entetes=()):
        self.send_response(code)
        for nom, valeur in entetes:
            self.send_header(nom, valeur)
        self.send_header("Content-Length", str(len(corps)))
        self.end_headers()
        self.wfile.write(corps)

    def do_POST(self):
        self.journaliser()
        longueur = int(self.headers.get("Content-Length") or 0)
        corps = self.rfile.read(longueur)
        if self.path != "/api/auth/login":
            return self.repondre(404)
        try:
            donnees = json.loads(corps)
        except ValueError:
            return self.repondre(400)
        if donnees.get("login") == "alice" and donnees.get("password") == "secret":
            reponse = {"access_token": JETON, "user": {"id_role": 42, "prenom_role": "Alice", "nom_role": "Simulée"}}
            return self.repondre(200, json.dumps(reponse).encode(), [
                ("Content-Type", "application/json"),
                ("Set-Cookie", COOKIES[0] + "; Expires=Wed, 21 Oct 2026 07:28:00 GMT; Path=/; HttpOnly"),
                ("Set-Cookie", COOKIES[1] + "; Path=/"),
            ])
        # Comme GeoNature derrière Apache : un échec d'authentification renvoie vers la SPA.
        return self.repondre(302, entetes=[("Location", "/#/login")])

    def do_GET(self):
        self.journaliser()
        if self.path == "/api/gn_commons/config":
            return self.repondre(200, b'{"GEONATURE_VERSION": "2.16.0"}', [("Content-Type", "application/json")])
        if self.path == "/api/gn_commons/modules":
            cookie = self.headers.get("Cookie") or ""
            if self.headers.get("Authorization") == "Bearer " + JETON and all(c in cookie for c in COOKIES):
                return self.repondre(200, b"[]", [("Content-Type", "application/json")])
            return self.repondre(401, b'{"msg": "Missing token"}', [("Content-Type", "application/json")])
        return self.repondre(404, b"<html>introuvable</html>", [("Content-Type", "text/html")])

    def log_message(self, *args):
        pass


def main():
    parametres = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parametres.add_argument("--port", type=int, default=8766)
    port = parametres.parse_args().port
    print(f"GeoNature simulé sur http://127.0.0.1:{port}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", port), Gestionnaire).serve_forever()


if __name__ == "__main__":
    main()
