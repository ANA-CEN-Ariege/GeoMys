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

package fr.ariegenature.geomys.plateforme

import kotlin.test.Test
import kotlin.test.assertEquals

class TexteTest {

    @Test
    fun sans_diacritiques_retire_les_accents_et_garde_le_reste() {
        assertEquals("Forets riveraines a Saules", sansDiacritiques("Forêts riveraines à Saules"))
        assertEquals("Cœur-de-Marie", sansDiacritiques("Cœur-de-Marie"))
    }

    @Test
    fun comparateur_francais_ignore_casse_et_accents() {
        val tri = listOf("Zoo", "Épervier", "abeille", "éclair", "Eclair", "2026", "epervier")
            .sortedWith(comparateurFrancais())
        // « éclair » et « Eclair » sont égaux au niveau primaire : le tri, stable, garde leur ordre.
        assertEquals(listOf("2026", "abeille", "éclair", "Eclair", "Épervier", "epervier", "Zoo"), tri)
        assertEquals(0, comparateurFrancais().compare("ÉLÈVE", "eleve"))
    }
}
