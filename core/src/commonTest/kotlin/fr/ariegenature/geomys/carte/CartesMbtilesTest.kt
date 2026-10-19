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

// Propre au portage : l'import des cartes MBTiles (MbtilesStore de GeoMys-Android @ cdd5196) sur le vrai
// système de fichiers, SQLite remplacé par un lecteur factice. La lecture SQLite elle-même est éprouvée
// sur le simulateur iOS (CartesMbtilesSqliteTest, le MbtilesStoreTest d'Android porté). Les trois cas MBTiles
// de DefautsPariteLot2Test (GeoMys-Android @ cdd5196) sont réécrits ici :
// reimporter_une_copie_corrompue_ne_detruit_pas_la_carte_en_place et l_import_ne_laisse_aucun_temporaire dans
// une_copie_invalide_ne_touche_pas_la_carte_de_meme_nom, un_mbtiles_valide_remplace_bien_celui_en_place dans
// une_carte_valide_remplace_celle_de_meme_nom.

package fr.ariegenature.geomys.carte

import fr.ariegenature.geomys.util.uuidAleatoire
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CartesMbtilesTest {

    private val racine = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-mbtiles-${uuidAleatoire()}"
    private val systeme = FileSystem.SYSTEM

    /** Une « carte » est valide si son contenu commence par « carte » ; ses métadonnées sont le reste. */
    private val lecteur = object : LecteurMbtiles {
        var lectures = 0
        private fun contenu(fichier: Path) = systeme.read(fichier) { readUtf8() }
        override fun aUneTableTiles(fichier: Path) = contenu(fichier).startsWith("carte")
        override fun metadonnees(fichier: Path): Map<String, String>? {
            lectures++
            return contenu(fichier).removePrefix("carte").split(';').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        }
        override fun zoomsDesTuiles(fichier: Path) = 10 to 12
    }

    private val cartes = CartesMbtiles(racine / "mbtiles", systeme, lecteur)

    private fun flux(texte: String) = Buffer().writeUtf8(texte)

    @AfterTest
    fun nettoyer() {
        systeme.deleteRecursively(racine)
    }

    @Test
    fun une_carte_valide_est_importee_sous_un_nom_assaini_sans_residu() {
        val f = cartes.importer(flux("carte;name=Ariège;minzoom=8;maxzoom=14;format=jpg"), "Carte Ariège.mbtiles", instant = 1)
        assertNotNull(f)
        assertEquals("Carte_Ari_ge.mbtiles", f.name)
        assertEquals(listOf(f), cartes.liste())
        assertEquals(InfoMbtiles(8, 14, "jpg", "Ariège"), cartes.info(f))
        assertEquals("Ariège", cartes.nomAffichage(f))
        assertTrue(systeme.list(cartes.dossier).none { it.name.endsWith(".tmp") })
        assertTrue(cartes.supprimer(f))
        assertTrue(cartes.liste().isEmpty())
    }

    @Test
    fun une_copie_invalide_ne_touche_pas_la_carte_de_meme_nom() {
        // Le constat du portage iOS de GeoMys-Android : réimporter une copie corrompue détruisait la carte.
        val bonne = cartes.importer(flux("carte;name=Bonne"), "ariege.mbtiles", instant = 1)!!
        assertNull(cartes.importer(flux("corrompue"), "ariege.mbtiles", instant = 2))
        assertEquals("carte;name=Bonne", systeme.read(bonne) { readUtf8() })
        assertEquals(listOf("ariege.mbtiles"), systeme.list(cartes.dossier).map { it.name }, "aucun temporaire laissé")
    }

    @Test
    fun une_carte_valide_remplace_celle_de_meme_nom() {
        cartes.importer(flux("carte;name=Ancienne"), "ariege.mbtiles", instant = 1)
        val f = cartes.importer(flux("carte;name=Nouvelle"), "ariege.mbtiles", instant = 2)!!
        assertEquals("Nouvelle", cartes.nomAffichage(f), "les métadonnées de l'ancienne ne restent pas en mémoire")
    }

    @Test
    fun sans_nom_le_fichier_prend_l_instant_et_sans_zoom_ceux_des_tuiles() {
        val f = cartes.importer(flux("carte;"), null, instant = 1_789_000)!!
        assertEquals("carte_1789000.mbtiles", f.name)
        assertEquals(InfoMbtiles(10, 12, "png", null), cartes.info(f))
        assertEquals("carte_1789000", cartes.nomAffichage(f), "le nom du fichier faute de nom déclaré")
        assertEquals("ma_carte.mbtiles", CartesMbtiles.nettoyerNom("dossier/ma carte"))
    }

    @Test
    fun les_metadonnees_sont_lues_une_fois_et_un_fichier_disparu_n_est_pas_ouvert() {
        val f = cartes.importer(flux("carte;name=A"), "a.mbtiles", instant = 1)!!
        val avant = lecteur.lectures
        repeat(3) { cartes.info(f) }
        assertEquals(avant + 1, lecteur.lectures)
        val disparu = cartes.fichier("disparu.mbtiles")
        assertEquals(InfoMbtiles(0, 20, "png", null), cartes.info(disparu))
        assertTrue(!systeme.exists(disparu), "aucune base vide créée à sa place")
    }

    @Test
    fun les_textes_du_dialogue() {
        assertEquals("zoom 8–14 · 12 Mo", CartesMbtiles.detail(InfoMbtiles(8, 14, "png", null), 12L * 1024 * 1024 + 5))
        assertEquals("📁 Ariège (hors-ligne)", CartesMbtiles.libelleFond("Ariège"))
        assertEquals("Carte importée : Ariège", CartesMbtiles.importee("Ariège"))
    }
}
