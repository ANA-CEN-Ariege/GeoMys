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

// Propre au portage : constat du 23 octobre, la dictée vocale ajoutait la phrase suivante à l'ancienne quand
// on avait effacé le champ à la main (la reconnaissance d'iOS accumule la transcription d'une session). Le
// même jour, une dictée non reconnue montre les espèces qui lui ressemblent, sans que le champ ait le focus.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.runComposeUiTest
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.saisie.LigneStatutTaxRef
import fr.ariegenature.geomys.store.SuggestionTaxon
import fr.ariegenature.geomys.ui.composants.Dictee
import fr.ariegenature.geomys.ui.theme.Apparence
import fr.ariegenature.geomys.ui.theme.LocalApparence
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DicteeChampEspeceTest {

    /** Une dictée en cours, qui compte les modifications à la main qu'on lui signale. */
    private class DicteeEspion : Dictee {
        override val enCours = true
        var modifications = 0

        override fun basculer(vocabulaire: List<String>) {}

        override fun arreter() {}

        override fun texteModifie() {
            modifications++
        }
    }

    private fun verifier(apparence: Apparence) = runComposeUiTest {
        val dictee = DicteeEspion()
        var texte by mutableStateOf("")
        setContent {
            CompositionLocalProvider(LocalApparence provides apparence) {
                MaterialTheme {
                    ChampEspece(texte, { texte = it }, "Espèce", emptyList(), {}, LigneStatutTaxRef(), false, dictee, {})
                }
            }
        }
        // La dictée pose sa transcription : ce n'est pas une modification à la main.
        texte = "Merle"
        waitForIdle()
        assertEquals(0, dictee.modifications)
        // Le champ effacé à la main : la dictée en est prévenue, la suivante remplacera le texte.
        onNode(hasSetTextAction()).performTextClearance()
        waitForIdle()
        assertEquals("", texte)
        assertEquals(1, dictee.modifications)
    }

    @Test
    fun le_champ_efface_a_la_main_previent_la_dictee_sous_ios() = verifier(Apparence.IOS)

    @Test
    fun le_champ_efface_a_la_main_previent_la_dictee_sous_android() = verifier(Apparence.ANDROID)

    /** Après une dictée non reconnue : les espèces proches dans la liste, ouverte sans que le champ ait
     *  le focus (le micro ne le donne pas), et ce qui a été entendu à la place de « Nom invalide ». */
    private fun especesProches(apparence: Apparence, fin: String) = runComposeUiTest {
        val tichodrome = SuggestionTaxon("Tichodrome échelette", 4494, "Tichodroma muraria")
        var choisie: SuggestionTaxon? = null
        val ligne = LigneStatutTaxRef(TaxRefStatut.NonTrouve, texteVisible = true)
        setContent {
            CompositionLocalProvider(LocalApparence provides apparence) {
                MaterialTheme {
                    ChampEspece(
                        "psychorame", {}, "Espèce", listOf(tichodrome), { choisie = it }, ligne, false, DicteeEspion(), {},
                        entendu = "psychorame",
                    )
                }
            }
        }
        onNodeWithText("Entendu : « psychorame » — choisissez l'espèce $fin").assertExists()
        onNodeWithText("Nom invalide — choisissez parmi les propositions", substring = true).assertDoesNotExist()
        onNodeWithText("Tichodrome échelette").performClick()
        assertEquals(tichodrome, choisie)
    }

    @Test
    fun une_dictee_non_reconnue_montre_les_especes_proches_sous_ios() = especesProches(Apparence.IOS, "ci-dessus")

    @Test
    fun une_dictee_non_reconnue_montre_les_especes_proches_sous_android() = especesProches(Apparence.ANDROID, "dans la liste")
}
