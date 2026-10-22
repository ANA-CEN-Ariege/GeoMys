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

// Sur le modèle de MapTileCacheTests de GeoMys-IOS @ 5fda84c (les cas de TuileMath) : les calculs que
// GeoMys-Android faisait dans CacheManagerFragment, sans test.

package fr.ariegenature.geomys.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TuileMathTest {

    @Test
    fun au_zoom_0_une_seule_tuile() {
        assertEquals(0 to 0, TuileMath.tuile(2.35, 48.85, 0))
        assertEquals(0 to 0, TuileMath.tuile(-120.0, -30.0, 0))
    }

    @Test
    fun les_indices_restent_dans_la_grille() {
        for (z in 0..18) {
            val dernier = (1 shl z) - 1
            val (x, y) = TuileMath.tuile(179.999, -85.0, z)
            assertTrue(x in 0..dernier && y in 0..dernier, "zoom $z : ($x, $y)")
            // Au-delà des latitudes de Web Mercator, la tuile reste bornée.
            val (_, yNord) = TuileMath.tuile(0.0, 89.9, z)
            assertEquals(0, yNord)
        }
    }

    @Test
    fun x_croit_vers_l_est_et_y_vers_le_sud() {
        assertTrue(TuileMath.tuile(2.0, 43.0, 14).first >= TuileMath.tuile(1.0, 43.0, 14).first)
        assertTrue(TuileMath.tuile(1.5, 43.0, 14).second >= TuileMath.tuile(1.5, 44.0, 14).second)
    }

    @Test
    fun le_monde_au_zoom_1_compte_4_tuiles() {
        assertEquals(4, TuileMath.nombreDeTuiles(Emprise(sud = -85.0, ouest = -179.0, nord = 85.0, est = 179.0), 1, 1))
    }

    @Test
    fun le_nombre_de_tuiles_croit_avec_le_zoom_max_et_suit_la_pyramide() {
        val foix = Emprise(sud = 42.95, ouest = 1.58, nord = 42.98, est = 1.63)
        val seul = TuileMath.nombreDeTuiles(foix, 12, 12)
        val trois = TuileMath.nombreDeTuiles(foix, 12, 14)
        assertTrue(trois > seul)
        assertEquals(seul + TuileMath.nombreDeTuiles(foix, 13, 13) + TuileMath.nombreDeTuiles(foix, 14, 14), trois)
        assertEquals(0, TuileMath.nombreDeTuiles(foix, 15, 14), "zoom min au-delà du zoom max : aucune tuile")
    }

    @Test
    fun la_surface_d_une_petite_zone_tombe_juste() {
        // 0,01° de côté vers 43° N : à peu près 0,81 × 1,11 km.
        val s = TuileMath.surfaceKm2(Emprise(sud = 42.99, ouest = 1.40, nord = 43.00, est = 1.41))
        assertTrue(s > 0.6 && s < 1.2, "surface : $s")
        assertEquals(0.0, TuileMath.surfaceKm2(Emprise(sud = 43.0, ouest = 1.40, nord = 42.99, est = 1.41)), "sud au-dessus du nord : 0")
    }
}
