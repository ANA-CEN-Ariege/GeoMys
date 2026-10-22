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

package fr.ariegenature.geomys.geo

import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LissageCapTest {

    @Test
    fun la_premiere_mesure_est_prise_telle_quelle() {
        val lissage = LissageCap()
        assertEquals(42.0, lissage.avancer(42.0, Duration.ZERO))
        assertEquals(42.0, lissage.cap)
    }

    @Test
    fun le_cap_passe_le_nord_par_le_plus_court_chemin() {
        val lissage = LissageCap()
        lissage.avancer(350.0, Duration.ZERO)
        // À mi-chemin d'une constante de temps : on doit être entre 350° et 10°, du côté du nord,
        // et non être reparti en arrière vers 180°.
        val cap = lissage.avancer(10.0, 150.milliseconds)
        assertTrue(cap > 350.0 || cap < 10.0, "cap = $cap")
        // Le temps finit par rattraper la cible.
        assertProche(10.0, lissage.avancer(10.0, 10.seconds))
    }

    @Test
    fun une_constante_de_temps_rattrape_63_pour_cent_de_l_ecart() {
        val lissage = LissageCap(constanteTemps = 150.milliseconds)
        lissage.avancer(0.0, Duration.ZERO)
        assertProche(90.0 * (1 - exp(-1.0)), lissage.avancer(90.0, 150.milliseconds))
    }

    @Test
    fun un_petit_ecart_dans_la_zone_morte_ne_fait_pas_bouger_la_carte() {
        val lissage = LissageCap(zoneMorte = 0.5)
        lissage.avancer(100.0, Duration.ZERO)
        assertEquals(100.0, lissage.avancer(100.3, 1.seconds))
        assertEquals(100.0, lissage.avancer(99.8, 1.seconds))
    }

    @Test
    fun les_angles_sont_ramenes_entre_0_et_360() {
        assertEquals(10.0, normaliserDegres(370.0))
        assertEquals(350.0, normaliserDegres(-10.0))
        assertEquals(0.0, normaliserDegres(720.0))
        assertEquals(20.0, ecartAngulaire(350.0, 10.0))
        assertEquals(-20.0, ecartAngulaire(10.0, 350.0))
        assertEquals(-180.0, ecartAngulaire(0.0, 180.0))
    }

    private fun assertProche(attendu: Double, obtenu: Double, tolerance: Double = 1e-6) {
        assertTrue(abs(attendu - obtenu) < tolerance, "attendu $attendu, obtenu $obtenu")
    }
}
