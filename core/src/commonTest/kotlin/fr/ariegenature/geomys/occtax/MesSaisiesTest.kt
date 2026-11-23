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

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.PointTrace
import fr.ariegenature.geomys.model.Sortie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MesSaisiesTest {

    private val trace = listOf(PointTrace(42.9, 1.4), PointTrace(42.91, 1.4))

    private val merle = Observation(id = "a", espece = "Merle noir", latitude = 42.96491, longitude = 1.60555)

    @Test
    fun la_distance_n_est_affichee_qu_avec_une_trace_gps() {
        assertEquals("1250 m", MesSaisies.distanceLigne(Sortie(pointsParcours = trace, distanceTotale = 1250.4)))
        assertEquals("0 m", MesSaisies.distanceLigne(Sortie(pointsParcours = trace.take(1))), "trace d'un seul point")
        assertNull(MesSaisies.distanceLigne(Sortie(observations = listOf(merle))), "sans trace GPS")
        assertNull(
            MesSaisies.distanceLigne(Sortie(observations = listOf(merle), distanceTotale = 80.0)),
            "une distance sans trace ne s'affiche pas non plus",
        )
    }
}
