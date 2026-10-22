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

package fr.ariegenature.geomys.util

import kotlin.test.Test
import kotlin.test.assertEquals

class DateAffichageTest {

    @Test
    fun dateIsoEnFrancais() {
        assertEquals("21/05/2026", DateAffichage.isoVersFr("2026-05-21"))
        assertEquals("21/05/2026", DateAffichage.isoVersFr("2026-05-21T11:12:06"))
        assertEquals("hier", DateAffichage.isoVersFr("hier"))
    }

    @Test
    fun dateHeureTronqueeALaDate() {
        assertEquals("2026-05-21", DateAffichage.isoTronquerDate("2026-05-21T11:12:06"))
        assertEquals("21/05/2026", DateAffichage.isoTronquerDate("21/05/2026"))
    }
}
