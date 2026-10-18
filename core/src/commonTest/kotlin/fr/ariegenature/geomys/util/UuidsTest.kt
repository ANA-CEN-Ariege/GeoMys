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

package fr.ariegenature.geomys.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UuidsTest {

    /** Valeurs de `java.util.UUID.nameUUIDFromBytes(texte.toByteArray(UTF_8))`, relevées sur la JVM. */
    @Test
    fun memeUuidQueJavaPourLesMemesOctets() {
        val attendus = mapOf(
            "" to "d41d8cd9-8f00-3204-a980-0998ecf8427e",
            "a" to "0cc175b9-c0f1-36a8-b1c3-99e269772661",
            "geomys-releve:abc" to "9588395f-1bb1-31e4-b4a3-c07e7254d922",
            "geomys-releve:2026-10-18|42|Foix éèà" to "2df0eeef-e93e-307e-8929-84d38cb962ee",
        )
        for ((texte, uuid) in attendus) {
            assertEquals(uuid, uuidDepuisOctets(texte.encodeToByteArray()), texte)
        }
    }

    @Test
    fun uuidAleatoireDeVersion4() {
        val uuid = uuidAleatoire()
        assertTrue(uuid.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")), uuid)
        assertNotEquals(uuid, uuidAleatoire())
    }
}
