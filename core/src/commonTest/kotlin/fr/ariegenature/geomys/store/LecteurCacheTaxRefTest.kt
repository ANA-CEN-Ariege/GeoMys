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

package fr.ariegenature.geomys.store

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okio.FileSystem
import okio.SYSTEM
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Le lecteur à la main du cache principal de TaxRef rend exactement ce que rendait le décodeur
 *  générique de kotlinx-serialization, noms difficiles compris. */
class LecteurCacheTaxRefTest {

    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val serialiseur = MapSerializer(String.serializer(), TaxRefEntry.serializer())

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    private fun lire(json: String) = LecteurCacheTaxRef.lire(json.encodeToByteArray())

    @Test
    fun le_cache_ecrit_par_la_synchro_se_relit_a_l_identique() {
        val entrees = mapOf(
            "merle noir" to TaxRefEntry(4117, "Turdus merula Linnaeus, 1758", "Merle noir"),
            "turdus merula" to TaxRefEntry(4117, "Turdus merula Linnaeus, 1758"),
            "rougegorge familier" to TaxRefEntry(4001, "Erithacus rubecula (Linnaeus, 1758)", "Rougegorge familier"),
            // Guillemets, barre oblique inverse, accents, caractères de contrôle et hors du plan de base.
            "nom \"cite\" \\ barre" to TaxRefEntry(1, "Genre \\ espèce", "Pinson des arbres \"commun\""),
            "ligne\nsuivante\tfin" to TaxRefEntry(2, "Carex pendula Huds., 1762", "Laîche\nà épis pendants"),
            "mesange a longue queue" to TaxRefEntry(3, "Ægithalos caudatus", "Mésange à longue queue œ ß 🐦"),
            "controle \u0001" to TaxRefEntry(-4, "", ""),
        )
        val taxRef = TaxRefCache(dossier, MapSettings())
        assertTrue(taxRef.remplacerTout(entrees))
        val octets = systeme.read(dossier / "cache_v3.json") { readByteArray() }
        val attendu = JSON_STOCKAGE.decodeFromString(serialiseur, octets.decodeToString())
        assertEquals(attendu, LecteurCacheTaxRef.lire(octets))
        // Et par le cache lui-même, relu par un nouveau processus.
        val relu = TaxRefCache(dossier, MapSettings())
        assertEquals(entrees.size, relu.count)
        assertEquals("Mésange à longue queue œ ß 🐦", relu.get("mesange a longue queue")?.nomFrOriginal)
        assertEquals("Pinson des arbres \"commun\"", relu.get("nom \"cite\" \\ barre")?.nomFrOriginal)
    }

    @Test
    fun espaces_ordre_des_champs_et_champs_inconnus() {
        val lu = lire(
            """
            {
              "pic vert" : { "sciNom" : "Picus viridis" , "inconnu" : {"a":[1,2.5e3,{"b":null}],"c":true} ,
                             "cdNom" : 3603 , "nomFrOriginal" : "Pic vert", "liste": [] },
              "pic noir": {"cdNom":3611,"nomFr":"Pic noir","sciNom":"Dryocopus martius","x":false},
              "vide": {}
            }
            """.trimIndent(),
        )
        assertEquals(TaxRefEntry(3603, "Picus viridis", "Pic vert"), lu["pic vert"])
        assertEquals(TaxRefEntry(3611, "Dryocopus martius", "Pic noir"), lu["pic noir"], "l'ancien champ nomFr de GeoMys-Android")
        assertEquals(TaxRefEntry(0, "", null), lu["vide"])
        assertEquals(emptyMap(), lire("{}"))
        assertEquals(TaxRefEntry(5, "A", null), lire("""{"a":{"cdNom":5,"sciNom":"A","nomFrOriginal":null}}""")["a"])
    }

    @Test
    fun un_cache_mal_forme_leve_une_exception() {
        for (json in listOf("", "[]", """{"a":{"cdNom":}}""", """{"a":{"cdNom":1,"sciNom":"B"}""", """{"a" {}}""", """{"a":{"sciNom":"B\q"}}""")) {
            assertFailsWith<Exception>(json) { lire(json) }
        }
    }
}
