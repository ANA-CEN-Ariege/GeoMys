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

// Propre au portage : la règle du suivi GPS sous Android (audit du 14 novembre 2026, M60).

package fr.ariegenature.geomys.location

import kotlin.test.Test
import kotlin.test.assertEquals

class SuiviGpsTest {

    @Test
    fun trace_eteinte_le_gps_suit_l_ecran_sans_service() {
        assertEquals(EtatSuiviGps(ecoute = true, service = false), etatSuiviGps(demande = true, premierPlan = true, trace = false))
        assertEquals(EtatSuiviGps(ecoute = false, service = false), etatSuiviGps(demande = true, premierPlan = false, trace = false))
    }

    @Test
    fun pendant_une_trace_le_service_garde_le_gps_ecran_verrouille() {
        assertEquals(EtatSuiviGps(ecoute = true, service = true), etatSuiviGps(demande = true, premierPlan = false, trace = true))
        assertEquals(EtatSuiviGps(ecoute = true, service = true), etatSuiviGps(demande = true, premierPlan = true, trace = true))
    }

    @Test
    fun hors_des_ecrans_de_saisie_rien_ne_tourne() {
        for (premierPlan in listOf(true, false)) for (trace in listOf(true, false)) {
            assertEquals(EtatSuiviGps(ecoute = false, service = false), etatSuiviGps(demande = false, premierPlan = premierPlan, trace = trace))
        }
    }
}
