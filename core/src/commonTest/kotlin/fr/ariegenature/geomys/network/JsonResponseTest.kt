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

package fr.ariegenature.geomys.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonResponseTest {

    @Test
    fun tableauDirectOuEnveloppe() {
        assertEquals("[1,2]", "[1,2]".parserTableauJson("data")?.toString())
        assertEquals("[4]", """{"items":[3],"data":[4]}""".parserTableauJson("data", "items")?.toString(), "l'ordre des clés passées fait la priorité")
        assertEquals("[3]", """{"items":[3]}""".parserTableauJson("data", "items")?.toString())
    }

    @Test
    fun niTableauNiCleConnue() {
        assertNull("""{"autre":[1]}""".parserTableauJson("data"))
        assertNull("<html></html>".parserTableauJson("data"))
    }
}
