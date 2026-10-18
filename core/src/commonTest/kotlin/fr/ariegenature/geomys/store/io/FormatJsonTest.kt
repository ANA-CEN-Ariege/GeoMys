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

package fr.ariegenature.geomys.store.io

import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.SaisieEnAttente
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException

/** La tolérance de lecture que les stores tenaient de Gson. */
class FormatJsonTest {

    @Test
    fun lesChampsAbsentsPrennentLeurDefaut() {
        val obs = JSON_STOCKAGE.decodeFromString<Observation>("""{"espece":"Turdus merula","latitude":42.9,"longitude":1.4}""")
        assertEquals(1, obs.nombre)
        assertEquals("", obs.notes)
        assertFalse(obs.envoyeeServeur)
        assertNull(obs.taxon)
        assertTrue(obs.uuidOccurrence.isNotEmpty())
    }

    @Test
    fun lesClesInconnuesSontIgnorees() {
        val obs = JSON_STOCKAGE.decodeFromString<Observation>("""{"espece":"x","latitude":1,"longitude":2,"champFutur":{"a":[1]}}""")
        assertEquals("x", obs.espece)
    }

    @Test
    fun unNullOuUneEnumerationInconnueDonnentLeDefaut() {
        val obs = JSON_STOCKAGE.decodeFromString<Observation>("""{"espece":"x","latitude":1,"longitude":2,"nombre":null,"taxon":"DRAGON"}""")
        assertEquals(1, obs.nombre)
        assertNull(obs.taxon)
        val saisie = JSON_STOCKAGE.decodeFromString<SaisieEnAttente>("""{"moduleCode":"m","objectType":"visit","valeursJson":"{}","etat":"INCONNU"}""")
        assertEquals(SaisieEnAttente.Etat.PENDING, saisie.etat)
    }

    @Test
    fun lesNullNeSontPasEcritsLesDefautsLeSont() {
        val texte = JSON_STOCKAGE.encodeToString(Observation(id = "o", espece = "x", latitude = 1.0, longitude = 2.0))
        assertFalse("\"sexe\"" in texte, texte)
        assertTrue("\"nombre\":1" in texte, texte)
        assertTrue("\"envoyeeServeur\":false" in texte, texte)
    }

    @Test
    fun unChampObligatoireManquantFaitEchouerLaLecture() {
        // Gson le laissait à null ; les stores mettent un tel élément à l'écart.
        assertFailsWith<SerializationException> {
            JSON_STOCKAGE.decodeFromString<Observation>("""{"latitude":1,"longitude":2}""")
        }
    }
}
