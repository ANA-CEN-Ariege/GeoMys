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

package fr.ariegenature.geomys.ui.occhab

import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.occhab.OccHabDetailsSession
import fr.ariegenature.geomys.ui.ServicesEssai
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Les dates d'une station, comme le web (occhab-form.component.ts) : changer l'une recopie l'autre
 *  tant qu'elles sont égales ; une station de plusieurs jours garde l'autre (21 octobre). */
class DatesStationTest {

    private val essai = ServicesEssai()

    @AfterTest
    fun nettoyer() = essai.nettoyer()

    private fun etat(debut: Long, fin: Long) = EtatDetailsStation(
        demarrage = false,
        details = OccHabDetailsSession(idDataset = 12, dateMin = debut, dateMax = fin),
        station = OccHabStation(),
        config = essai.services.config,
        nomenclatures = essai.services.nomenclatures,
    )

    @Test
    fun une_station_de_plusieurs_jours_garde_l_autre_date() {
        val debut = etat(JOUR_1, JOUR_3).apply { choisirDateMin(JOUR_2) }
        assertEquals(JOUR_2 to JOUR_3, debut.dateMin to debut.dateMax)
        val fin = etat(JOUR_1, JOUR_3).apply { choisirDateMax(JOUR_2) }
        assertEquals(JOUR_1 to JOUR_2, fin.dateMin to fin.dateMax)
    }

    @Test
    fun une_station_d_un_jour_recopie_la_date() {
        val e = etat(JOUR_1, JOUR_1).apply { choisirDateMin(JOUR_3) }
        assertEquals(JOUR_3 to JOUR_3, e.dateMin to e.dateMax)
    }

    private companion object {
        const val JOUR_1 = 1_779_840_000_000L
        const val JOUR_2 = JOUR_1 + 86_400_000L
        const val JOUR_3 = JOUR_1 + 2 * 86_400_000L
    }
}
