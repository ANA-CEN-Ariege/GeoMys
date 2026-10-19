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

// Propre au portage : le filtre des ArrayAdapter de SpeciesAutocomplete (GeoMys-Android @ cdd5196)
// n'était pas testé.

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.store.TaxRefCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Le filtre d'autocomplétion des espèces. */
class SuggestionsTest {

    private val noms = listOf("Grand Paon de nuit", "Paon du jour", "Pavo cristatus", "Paon", "Mésange bleue")
    private val normalises = noms.map { TaxRefCache.normaliser(it) to it }

    @Test
    fun requete_vide_rend_toute_la_liste() {
        assertSame(noms, filtrerSuggestions(normalises, noms, null))
        assertSame(noms, filtrerSuggestions(normalises, noms, ""))
    }

    @Test
    fun commencent_puis_contiennent_chacun_dans_l_ordre_de_la_liste() {
        assertEquals(listOf("Paon du jour", "Paon", "Grand Paon de nuit"), filtrerSuggestions(normalises, noms, "paon"))
        assertEquals(listOf("Paon du jour", "Pavo cristatus", "Paon", "Grand Paon de nuit"), filtrerSuggestions(normalises, noms, "PA"))
    }

    @Test
    fun sans_tenir_compte_des_accents_ni_de_la_casse() {
        assertEquals(listOf("Mésange bleue"), filtrerSuggestions(normalises, noms, "MESANGE"))
        assertEquals(listOf("Mésange bleue"), filtrerSuggestions(normalises, noms, "  mésange b"))
        assertEquals(emptyList(), filtrerSuggestions(normalises, noms, "merle"))
    }

    /** Une requête faite d'espaces se normalise en chaîne vide, qui commence chaque clé. */
    @Test
    fun requete_blanche_rend_tout_dans_l_ordre_des_cles() {
        assertEquals(noms, filtrerSuggestions(normalises, noms, "   "))
    }
}
