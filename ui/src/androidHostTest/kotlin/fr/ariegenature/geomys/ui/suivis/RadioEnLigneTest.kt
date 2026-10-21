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

package fr.ariegenature.geomys.ui.suivis

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Les options d'un champ radio des Suivis, sur une ligne ou une par ligne, sous Android comme sous
 *  iOS depuis le 21 octobre. */
class RadioEnLigneTest {

    @Test
    fun peu_d_options_courtes_sur_une_ligne_sinon_une_par_ligne() {
        assertTrue(radioEnLigne(listOf("Oui", "Non")))
        // La fiabilité d'une visite STERF-eBMS, qui s'écrasait sous iOS.
        assertFalse(radioEnLigne(listOf("A. Conditions appropriées", "B. Conditions inappropriées", "C. Relevé impossible")))
        assertFalse(radioEnLigne(listOf("Oui", "Non, la visite n'a pas pu être complètement effectuée")))
        assertFalse(radioEnLigne(listOf("1", "2", "3", "4", "5")), "plus de quatre options")
    }
}
