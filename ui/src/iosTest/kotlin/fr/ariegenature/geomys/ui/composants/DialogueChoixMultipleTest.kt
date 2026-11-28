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

// Demande du 27 novembre 2026 : les observateurs des formulaires des Suivis se cherchent au clavier. Le
// dialogue de choix multiple prend un filtre à partir de 20 noms : la barre de recherche du `.searchable`
// de la feuille de GeoMys-IOS (SelectMultipleSheet), que le portage avait laissée, et un EditText souligné
// sous Android, où l'AlertDialog de GeoMys-Android n'en avait pas. Les deux allures, sur le simulateur.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import fr.ariegenature.geomys.ui.theme.Apparence
import fr.ariegenature.geomys.ui.theme.LocalApparence
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DialogueChoixMultipleTest {

    /** Vingt observateurs d'essai, puis Jean Dupont et Éric Martin. */
    private val observateurs = List(SEUIL_RECHERCHE_LISTE) { "Observateur d'essai ${it + 1}" } + listOf("Jean Dupont", "Éric Martin")
    private val jean = observateurs.indexOf("Jean Dupont")
    private val eric = observateurs.indexOf("Éric Martin")

    /** Un dialogue ouvert : [visible] le ferme et le rouvre, [validations] reçoit chaque « Valider ». */
    private class Dialogue {
        var visible by mutableStateOf(true)
        val validations = mutableListOf<Set<Int>>()
    }

    /** Le dialogue des [observateurs], à recherche, ouvert sous [apparence], [coches] cochées ; [seFerme]
     *  faux : il reste ouvert après ses boutons. */
    private fun ComposeUiTest.ouvrir(apparence: Apparence, coches: Set<Int> = emptySet(), seFerme: Boolean = true): Dialogue {
        val d = Dialogue()
        setContent {
            CompositionLocalProvider(LocalApparence provides apparence) {
                MaterialTheme {
                    DialogueChoixMultiple(
                        d.visible,
                        "Observateurs",
                        observateurs,
                        coches,
                        onValider = { d.validations += it },
                        onFermer = { if (seFerme) d.visible = false },
                        recherche = true,
                    )
                }
            }
        }
        return d
    }

    private fun ComposeUiTest.texteDuFiltre(): String = onNode(hasSetTextAction()).texte()

    private fun SemanticsNodeInteraction.texte(): String = fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun leFiltreAPartirDuSeuil(apparence: Apparence) = runComposeUiTest {
        var options by mutableStateOf(observateurs.take(SEUIL_RECHERCHE_LISTE - 1))
        var recherche by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalApparence provides apparence) {
                MaterialTheme { DialogueChoixMultiple(true, "Observateurs", options, emptySet(), {}, {}, recherche = recherche) }
            }
        }
        // 19 noms : ni filtre ni clavier, comme avant.
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
        onNodeWithText("Observateur d'essai 19").assertExists()
        // 20 : le filtre, vide et sans le focus : le clavier ne s'ouvre qu'à son toucher.
        options = observateurs.take(SEUIL_RECHERCHE_LISTE)
        waitForIdle()
        onNode(hasSetTextAction()).assertIsNotFocused()
        assertEquals("", texteDuFiltre())
        onNodeWithText("Filtrer…", useUnmergedTree = true).assertExists()
        // Sans recherche (les champs additionnels d'OccTax), jamais de filtre.
        recherche = false
        waitForIdle()
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun le_filtre_a_partir_de_vingt_noms_sous_android() = leFiltreAPartirDuSeuil(Apparence.ANDROID)

    @Test
    fun le_filtre_a_partir_de_vingt_noms_sous_ios() = leFiltreAPartirDuSeuil(Apparence.IOS)

    private fun lesCochesMasqueesRestent(apparence: Apparence) = runComposeUiTest {
        val d = ouvrir(apparence, coches = setOf(jean))
        // La frappe filtre, accents et casse ignorés.
        onNode(hasSetTextAction()).performTextInput("eric")
        onNodeWithText("Éric Martin").assertExists()
        onNodeWithText("Jean Dupont").assertDoesNotExist()
        onAllNodesWithText("Observateur d'essai", substring = true).assertCountEquals(0)
        onNodeWithText("Éric Martin").performClick()
        // Rien ne correspond : une ligne le dit.
        onNode(hasSetTextAction()).performTextInput("z")
        onNodeWithText("Aucun résultat").assertExists()
        onNodeWithText("Éric Martin").assertDoesNotExist()
        // Jean Dupont, coché à l'ouverture et masqué depuis, part avec Éric Martin.
        onNodeWithText("Valider").performClick()
        assertEquals(listOf(setOf(jean, eric)), d.validations)
    }

    @Test
    fun une_coche_masquee_par_le_filtre_reste_et_part_sous_android() = lesCochesMasqueesRestent(Apparence.ANDROID)

    @Test
    fun une_coche_masquee_par_le_filtre_reste_et_part_sous_ios() = lesCochesMasqueesRestent(Apparence.IOS)

    private fun rouvertLeFiltreEstVide(apparence: Apparence) = runComposeUiTest {
        val d = ouvrir(apparence)
        onNode(hasSetTextAction()).performTextInput("eric")
        onNodeWithText("Éric Martin").performClick()
        onNodeWithText("Annuler").performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
        // « Annuler » ne rend rien.
        assertEquals(emptyList<Set<Int>>(), d.validations)
        // Rouvert : le filtre est vide et toute la liste revient.
        d.visible = true
        waitForIdle()
        assertEquals("", texteDuFiltre())
        onNodeWithText("Jean Dupont").assertExists()
        onNodeWithText("Observateur d'essai 1").assertExists()
    }

    @Test
    fun rouvert_le_filtre_est_vide_sous_android() = rouvertLeFiltreEstVide(Apparence.ANDROID)

    @Test
    fun rouvert_le_filtre_est_vide_sous_ios() = rouvertLeFiltreEstVide(Apparence.IOS)

    private fun lesBoutonsFermentLeClavier(apparence: Apparence) = runComposeUiTest {
        // Le dialogue reste affiché après ses boutons : on y voit le focus rendu.
        ouvrir(apparence, seFerme = false)
        val filtre = onNode(hasSetTextAction())
        filtre.performClick()
        filtre.assertIsFocused()
        onNodeWithText("Valider").performClick()
        filtre.assertIsNotFocused()
        // Une seconde plus tard : deux touchers rapprochés du champ feraient un double toucher.
        mainClock.advanceTimeBy(1_000)
        filtre.performClick()
        filtre.assertIsFocused()
        onNodeWithText("Annuler").performClick()
        filtre.assertIsNotFocused()
    }

    @Test
    fun valider_et_annuler_ferment_le_clavier_sous_android() = lesBoutonsFermentLeClavier(Apparence.ANDROID)

    @Test
    fun valider_et_annuler_ferment_le_clavier_sous_ios() = lesBoutonsFermentLeClavier(Apparence.IOS)

    /** Relecture du 27 novembre 2026 : toucher « Observateurs » ne prend pas le focus. Un autre champ du
     *  formulaire (ici une remarque) le gardait, son clavier ouvert sous le dialogue, et la frappe destinée
     *  au filtre partait dans ce champ caché. */
    private fun ouvertIlRetireLeFocusDUnAutreChamp(apparence: Apparence) = runComposeUiTest {
        var remarque by mutableStateOf("")
        val d = Dialogue().apply { visible = false }
        setContent {
            CompositionLocalProvider(LocalApparence provides apparence) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize()) {
                        BasicTextField(remarque, { remarque = it }, Modifier.fillMaxWidth().testTag("remarque"))
                        DialogueChoixMultiple(
                            d.visible,
                            "Observateurs",
                            observateurs,
                            emptySet(),
                            onValider = { d.validations += it },
                            onFermer = { d.visible = false },
                            recherche = true,
                        )
                    }
                }
            }
        }
        val champRemarque = onNodeWithTag("remarque")
        champRemarque.performClick()
        champRemarque.assertIsFocused()
        d.visible = true
        waitForIdle()
        // Le dialogue ouvert : ni la remarque ni le filtre n'ont le focus, le clavier est fermé.
        champRemarque.assertIsNotFocused()
        val filtre = onNode(hasSetTextAction() and !hasTestTag("remarque"))
        filtre.assertIsNotFocused()
        // La frappe va au filtre touché, la remarque reste vide.
        filtre.performTextInput("eric")
        assertEquals("eric", filtre.texte())
        assertEquals("", remarque)
        onNodeWithText("Jean Dupont").assertDoesNotExist()
    }

    @Test
    fun ouvert_il_retire_le_focus_d_un_autre_champ_sous_android() = ouvertIlRetireLeFocusDUnAutreChamp(Apparence.ANDROID)

    @Test
    fun ouvert_il_retire_le_focus_d_un_autre_champ_sous_ios() = ouvertIlRetireLeFocusDUnAutreChamp(Apparence.IOS)

    @Test
    fun la_liste_filtree_repart_de_son_haut_sous_android() = runComposeUiTest {
        // Relecture du 27 novembre 2026 : sous Android, le filtre est hors du défilement de la liste, qui
        // gardait sa position ; les premiers noms trouvés restaient cachés au-dessus. (Sous iOS, la barre
        // de recherche défile avec la liste.) Quarante observateurs d'essai, puis quarante Éric Martin.
        val noms = List(40) { "Observateur d'essai ${it + 1}" } + List(40) { "Éric Martin ${it + 1}" }
        setContent {
            CompositionLocalProvider(LocalApparence provides Apparence.ANDROID) {
                MaterialTheme { DialogueChoixMultiple(true, "Observateurs", noms, emptySet(), {}, {}, recherche = true) }
            }
        }
        // Défilée jusqu'au dernier nom, puis filtrée : les quarante Éric Martin se lisent depuis le premier.
        onNodeWithText("Éric Martin 40").performScrollTo()
        onNode(hasSetTextAction()).performTextInput("martin")
        onNodeWithText("Éric Martin 1").assertIsDisplayed()
    }
}
