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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/JsonDepuisMapTest.kt) :
// JUnit remplacé par kotlin.test, org.json par son portage. Depuis le 20 octobre, l'heuristique ne type
// plus que les valeurs sans définition (ChampsAdditionnels.versJson).

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Typage des champs additionnels sans définition (champ retiré du serveur) vers le JSONObject
 *  envoyé : "true"/"false" → booléen, entier → int, décimal → double, sinon chaîne ; vides
 *  ignorés. */
class JsonDepuisMapTest {

    private fun jsonDepuisMap(map: Map<String, String>) = ChampsAdditionnels.versJson(map, emptyList())

    @Test
    fun booleens() {
        val j = jsonDepuisMap(mapOf("ok" to "true", "ko" to "FALSE"))
        assertTrue(j.get("ok") is Boolean)
        assertTrue(j.getBoolean("ok"))
        assertFalse(j.getBoolean("ko"))
    }

    @Test
    fun entier_et_decimal() {
        val j = jsonDepuisMap(mapOf("n" to "42", "x" to "3.14", "v" to "12,5", "liste" to "1,2,3"))
        assertEquals(42, j.getInt("n"))
        assertEquals(3.14, j.getDouble("x"), 1e-9)
        assertEquals(12.5, j.getDouble("v"), 1e-9, "virgule décimale")
        assertEquals("1,2,3", j.getString("liste"), "plusieurs virgules = texte")
        assertTrue(j.get("n") is Int)
    }

    @Test
    fun texte_reste_string() {
        val j = jsonDepuisMap(mapOf("note" to "RAS"))
        assertEquals("RAS", j.getString("note"))
    }

    @Test
    fun valeurs_vides_ignorees() {
        val j = jsonDepuisMap(mapOf("a" to "", "b" to "ok"))
        assertFalse(j.has("a"))
        assertTrue(j.has("b"))
    }
}
