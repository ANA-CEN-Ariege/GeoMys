/*
 * GeoMys-Common — application de saisie naturaliste pour GeoNature, Android et iOS.
 * Copyright (C) 2026 ANA - CEN Ariège
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// Les requêtes de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/MbtilesStore.kt),
// par androidx.sqlite : le SQLite du système (SQLiteDatabase sous Android, libsqlite3 sous iOS).

package fr.ariegenature.geomys.carte

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import okio.Path

/** Le pilote SQLite de la plateforme. */
expect fun piloteSqlite(): SQLiteDriver

/** Lit les fichiers MBTiles par SQLite. Toute erreur (fichier qui n'est pas une base, table absente)
 *  donne « rien », jamais une exception. Le fichier doit exister : ouvrir un chemin inconnu créerait
 *  une base vide. */
class LecteurMbtilesSqlite(private val pilote: SQLiteDriver = piloteSqlite()) : LecteurMbtiles {

    private inline fun <T> avecBase(fichier: Path, lire: (SQLiteConnection) -> T): T? = try {
        val base = pilote.open(fichier.toString())
        try {
            lire(base)
        } finally {
            base.close()
        }
    } catch (_: Exception) {
        null
    }

    override fun metadonnees(fichier: Path): Map<String, String>? = avecBase(fichier) { base ->
        val requete = base.prepare("SELECT name, value FROM metadata")
        try {
            buildMap {
                while (requete.step()) {
                    if (!requete.isNull(0) && !requete.isNull(1)) put(requete.getText(0), requete.getText(1))
                }
            }
        } finally {
            requete.close()
        }
    }

    override fun zoomsDesTuiles(fichier: Path): Pair<Int, Int>? = avecBase(fichier) { base ->
        val requete = base.prepare("SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles")
        try {
            if (requete.step() && !requete.isNull(0)) requete.getLong(0).toInt() to requete.getLong(1).toInt() else null
        } finally {
            requete.close()
        }
    }

    /** Une table ou une vue : les MBTiles de mbutil ou de MOBAC exposent `tiles` en vue sur leurs tables
     *  `map` et `images`, que GeoMys-Android refusait alors que MapLibre les lit. */
    override fun aUneTableTiles(fichier: Path): Boolean = avecBase(fichier) { base ->
        val requete = base.prepare("SELECT count(*) FROM sqlite_master WHERE type IN ('table', 'view') AND name='tiles'")
        try {
            requete.step() && requete.getLong(0) > 0
        } finally {
            requete.close()
        }
    } ?: false
}
