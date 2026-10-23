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

// Propre au portage : constat du 23 octobre 2026 sur l'iPhone, la dictée ne finissait jamais sa phrase.

package fr.ariegenature.geomys.ui.composants

import kotlin.test.Test
import kotlin.test.assertEquals

/** [FinDePhrase] : la phrase finit au silence qui suit le DERNIER mot entendu, une seule fois. */
class FinDePhraseTest {

    /** Les actions programmées, déclenchées à la main dans l'ordre (le temps qui passe). */
    private val programmees = ArrayList<Pair<Long, () -> Unit>>()
    private var fins = 0
    private val fin = FinDePhrase(SILENCE_FIN_DE_PHRASE, { delai, action -> programmees.add(delai to action) }) { fins++ }

    private fun laisserPasser() {
        val aLancer = programmees.toList()
        programmees.clear()
        aLancer.forEach { it.second() }
    }

    @Test
    fun la_phrase_finit_au_silence_apres_le_dernier_mot() {
        fin.entendu() // « rouge »
        fin.entendu() // « rouge-gorge »
        assertEquals(listOf(SILENCE_FIN_DE_PHRASE, SILENCE_FIN_DE_PHRASE), programmees.map { it.first })
        laisserPasser()
        assertEquals(1, fins, "le silence après « rouge » ne compte plus : seul le dernier finit la phrase")
    }

    @Test
    fun une_ecoute_close_ou_relancee_ne_finit_plus_rien() {
        fin.entendu()
        fin.oublier()
        laisserPasser()
        assertEquals(0, fins)
    }
}
