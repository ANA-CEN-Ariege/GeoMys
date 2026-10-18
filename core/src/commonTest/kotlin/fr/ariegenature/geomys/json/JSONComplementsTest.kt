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

package fr.ariegenature.geomys.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Ce que JSONAndroidTest ne couvre pas : la conversion des valeurs Kotlin ([JSONObject.wrap]),
 *  l'égalité de [JSONObject.NULL], et les deux écarts assumés avec Android. */
class JSONComplementsTest {

    @Test
    fun uneMapEtUneListeDeviennentObjetEtTableau() {
        val objet = JSONObject(
            mapOf(
                "liste" to listOf(1, "a", null),
                "tableau" to intArrayOf(1, 2),
                "objets" to arrayOf(mapOf("b" to 2.0)),
                "nul" to null,
                "car" to 'c',
            ),
        )
        assertEquals("""{"liste":[1,"a",null],"tableau":[1,2],"objets":[{"b":2}],"nul":null,"car":"c"}""", objet.toString())
        assertSame(JSONObject.NULL, objet.opt("nul"))
    }

    @Test
    fun unTypeInconnuDonneNull() {
        assertNull(JSONObject.wrap(Any()))
        val objet = JSONObject(mapOf("x" to Any()))
        assertTrue(objet.has("x"))
        assertTrue(objet.isNull("x"))
    }

    @Test
    fun nullSeDeclareEgalANull() {
        // Comme sous Android : l'égalité « cassée » que prévoit l'API d'org.json.
        assertTrue(JSONObject.NULL.equals(null))
        assertTrue(JSONObject.NULL == JSONObject("""{"a":null}""").opt("a"))
        assertFalse(JSONObject.NULL == "null")
    }

    @Test
    fun lesNombresMisSansControleDansUnTableauRendentToStringNull() {
        // Android rend `null` (pas de texte) ; Kotlin impose un texte : "null".
        val nan: Any = Double.NaN
        val tableau = JSONArray().put(nan)
        assertEquals("null", tableau.toString())
        assertFailsWith<JSONException> { tableau.toString(2) }
    }

    @Test
    fun unStringerVideRendUneChaineVide() {
        assertEquals("", JSONStringer().toString())
    }
}
