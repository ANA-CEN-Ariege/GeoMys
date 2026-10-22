/*
 * GeoMys — application de saisie naturaliste pour GeoNature, Android et iOS.
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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/MbtilesStoreTest.kt) : Robolectric
// et son SQLiteDatabase deviennent le pilote natif d'androidx.sqlite, sur le simulateur iOS (les tests
// « JVM » tournent sur la cible Android, sans SQLite). Le dernier cas est propre au portage.

package fr.ariegenature.geomys.carte

import androidx.sqlite.driver.NativeSQLiteDriver
import androidx.sqlite.execSQL
import fr.ariegenature.geomys.util.uuidAleatoire
import okio.FileSystem
import okio.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CartesMbtilesSqliteTest {

    private val systeme = FileSystem.SYSTEM
    private val racine = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-mbtiles-sqlite-${uuidAleatoire()}"
    private val sources = racine / "sources"
    private val cartes = CartesMbtiles(racine / "mbtiles", systeme, LecteurMbtilesSqlite())

    init {
        systeme.createDirectories(sources)
    }

    @AfterTest
    fun nettoyer() {
        systeme.deleteRecursively(racine)
    }

    private fun base(nom: String, vararg requetes: String): Path {
        val f = sources / nom
        val connexion = NativeSQLiteDriver().open(f.toString())
        try {
            requetes.forEach { connexion.execSQL(it) }
        } finally {
            connexion.close()
        }
        return f
    }

    private fun creerMbtiles(nom: String, avecTiles: Boolean): Path = base(
        nom,
        "CREATE TABLE metadata (name TEXT, value TEXT)",
        "INSERT INTO metadata VALUES ('name','Ariège'),('minzoom','8'),('maxzoom','14'),('format','jpg')",
        *(
            if (avecTiles) {
                arrayOf(
                    "CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)",
                    "INSERT INTO tiles VALUES (8, 1, 1, x'00'), (14, 2, 2, x'00')",
                )
            } else {
                emptyArray()
            }
            ),
    )

    private fun importer(f: Path) = cartes.importer(systeme.source(f), f.name, instant = 1)

    @Test
    fun fichier_valide_importe_nom_assaini_et_infos_lues() {
        val dest = importer(creerMbtiles("Carte Ariège.mbtiles", avecTiles = true))
        assertNotNull(dest)
        assertEquals("Carte_Ari_ge.mbtiles", dest.name)
        assertTrue(cartes.tailleOctets(dest) > 0)
        assertEquals(InfoMbtiles(8, 14, "jpg", "Ariège"), cartes.info(dest))
        assertEquals("Ariège", cartes.nomAffichage(dest))
        assertEquals(listOf(dest.name), cartes.liste().map { it.name })
        assertTrue(cartes.supprimer(dest))
        assertTrue(cartes.liste().isEmpty())
    }

    @Test
    fun fichier_sans_table_tiles_rejete_et_nettoye() {
        assertNull(importer(creerMbtiles("pas_une_carte.mbtiles", avecTiles = false)))
        assertFalse(systeme.exists(cartes.dossier / "pas_une_carte.mbtiles"), "aucun résidu dans le dossier")
        // Fichier quelconque (pas SQLite) : rejeté aussi.
        val texte = sources / "notes.mbtiles"
        systeme.write(texte) { writeUtf8("ceci n'est pas une base") }
        assertNull(importer(texte))
        assertTrue(systeme.list(cartes.dossier).isEmpty())
    }

    @Test
    fun zooms_deduits_des_tuiles_sans_metadonnees() {
        val f = base(
            "brut.mbtiles",
            "CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)",
            "INSERT INTO tiles VALUES (10, 1, 1, x'00'), (12, 2, 2, x'00')",
        )
        assertEquals(InfoMbtiles(10, 12, "png", null), cartes.info(f))
        assertEquals("brut", cartes.nomAffichage(f))
    }

    @Test
    fun une_table_tiles_en_vue_est_acceptee() {
        // Le schéma de mbutil et de MOBAC : les tuiles dans `map` et `images`, `tiles` en vue.
        val f = base(
            "mbutil.mbtiles",
            "CREATE TABLE map (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_id TEXT)",
            "CREATE TABLE images (tile_data BLOB, tile_id TEXT)",
            "CREATE VIEW tiles AS SELECT map.zoom_level AS zoom_level, map.tile_column AS tile_column, map.tile_row AS tile_row, " +
                "images.tile_data AS tile_data FROM map JOIN images ON images.tile_id = map.tile_id",
            "INSERT INTO map VALUES (9, 1, 1, 'a'), (11, 2, 2, 'b')",
            "INSERT INTO images VALUES (x'00', 'a'), (x'00', 'b')",
        )
        val dest = importer(f)
        assertNotNull(dest)
        assertEquals(InfoMbtiles(9, 11, "png", null), cartes.info(dest))
    }
}
