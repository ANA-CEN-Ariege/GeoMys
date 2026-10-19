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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/GeoJsonCoordsTest.kt) : Robolectric
// n'est plus nécessaire (GeoPoint devient LatLon), JUnit devient kotlin.test.

package fr.ariegenature.geomys.util

import fr.ariegenature.geomys.geo.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parsing / sérialisation / centroïde des sommets `[[lon,lat], …]` (source unique, ex-12 copies). */
class GeoJsonCoordsTest {

    @Test
    fun parse_lon_lat_dans_le_bon_ordre_et_tolerant() {
        val pts = GeoJsonCoords.parse("[[1.4,42.9],[1.5,43.0]]")
        assertEquals(2, pts.size)
        assertEquals(42.9, pts[0].latitude, 1e-9)
        assertEquals(1.4, pts[0].longitude, 1e-9)
        assertTrue(GeoJsonCoords.parse(null).isEmpty())
        assertTrue(GeoJsonCoords.parse("").isEmpty())
        assertTrue(GeoJsonCoords.parse("pas du json").isEmpty())
        assertEquals(1, GeoJsonCoords.parse("[[1.4,42.9],[1.5],\"x\"]").size, "paire malformée ignorée, les autres conservées")
    }

    @Test
    fun format_puis_parse_est_un_aller_retour_exact() {
        val pts = listOf(LatLon(42.9, 1.4), LatLon(43.0, 1.5), LatLon(42.95, 1.45))
        val json = GeoJsonCoords.format(pts)
        assertEquals("[[1.4,42.9],[1.5,43],[1.45,42.95]]", json)
        val relus = GeoJsonCoords.parse(json)
        assertEquals(pts, relus)
    }

    @Test
    fun paires_modifiables_et_reserialisees() {
        val paires = GeoJsonCoords.parsePaires("[[1.4,42.9],[1.5,43.0]]")!!
        paires[1][0] = 1.6
        assertEquals("[[1.4,42.9],[1.6,43]]", GeoJsonCoords.formatPaires(paires))
        assertNull(GeoJsonCoords.parsePaires("[[1.4]]"))
        assertNull(GeoJsonCoords.parsePaires(null))
    }

    @Test
    fun centroide_moyenne_des_sommets() {
        val c = GeoJsonCoords.centroide(listOf(LatLon(42.0, 1.0), LatLon(44.0, 3.0)))!!
        assertEquals(43.0, c.latitude, 1e-9)
        assertEquals(2.0, c.longitude, 1e-9)
        assertNull(GeoJsonCoords.centroide(emptyList()))
    }
}
