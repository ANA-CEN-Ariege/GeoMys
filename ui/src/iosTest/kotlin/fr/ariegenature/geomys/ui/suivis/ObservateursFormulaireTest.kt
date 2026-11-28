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

// Demande du 27 novembre 2026 : dans les formulaires des Suivis, les observateurs se cherchent au clavier.
// À choix simple et à partir de 20 noms, le champ à filtre des détails d'un relevé (Android) ou d'une
// station (iOS) remplace la liste ; à choix multiple, le dialogue prend son filtre. Sous 20 noms, et pour
// toute autre liste, rien ne change. Sous iOS, le menu du système, une vue UIKit, ne se compose pas ici :
// le choix entre menu et filtre est éprouvé sur la JVM (ObservateursAFiltrerTest).

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.monitoring.form.EditableField
import fr.ariegenature.geomys.monitoring.form.FormulaireEtat
import fr.ariegenature.geomys.monitoring.form.PropertyValue
import fr.ariegenature.geomys.monitoring.form.ViewType
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.ui.composants.SEUIL_RECHERCHE_LISTE
import fr.ariegenature.geomys.ui.theme.Apparence
import fr.ariegenature.geomys.ui.theme.LocalApparence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import okio.FileSystem
import okio.SYSTEM
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ObservateursFormulaireTest {

    private val portee = CoroutineScope(Job() + Dispatchers.Unconfined)

    /** Vingt observateurs d'essai, puis Jean Dupont et Éric Martin. */
    private val noms = List(SEUIL_RECHERCHE_LISTE) { "Observateur d'essai ${it + 1}" } + listOf("Jean Dupont", "Éric Martin")
    private val eric = noms.indexOf("Éric Martin")

    private fun options(noms: List<String>) = noms.mapIndexed { i, nom -> PropertyValue("${i + 1}", nom) }

    // Une liste ne lit pas le référentiel : un cache vide suffit.
    private fun etat(champs: List<EditableField>) = FormulaireEtat(
        champs,
        TaxRefCache(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-ui-test-taxref", MapSettings()),
        portee,
    )

    @AfterTest
    fun fermer() {
        portee.cancel()
    }

    /** L'observateur d'une visite, à choix simple et effaçable, comme le widget `observers` du web. */
    private fun observateur(noms: List<String>) =
        EditableField("id_observateur", ViewType.SELECT, "Observateur", values = options(noms), effacable = true, observateurs = true)

    private fun ComposeUiTest.formulaire(apparence: Apparence, e: FormulaireEtat) = setContent {
        CompositionLocalProvider(LocalApparence provides apparence) {
            MaterialTheme { FormulaireMonitoring(e, emptySet(), {}, {}) }
        }
    }

    private fun unObservateurParmiVingt(apparence: Apparence) = runComposeUiTest {
        val e = etat(listOf(observateur(noms)))
        formulaire(apparence, e)
        // Toucher le champ déroule la liste ; la frappe la filtre, accents et casse ignorés.
        val champ = onNode(hasSetTextAction())
        champ.performClick()
        onNodeWithText("Observateur d'essai 1").assertExists()
        champ.performTextInput("eric")
        onNodeWithText("Observateur d'essai 1").assertDoesNotExist()
        onNodeWithText("Éric Martin").performClick()
        assertEquals(eric, e.indexChoisi("id_observateur"))
        // Le choix fait, la liste se referme et le champ montre le nom choisi.
        onNode(hasSetTextAction()).assert(hasText("Éric Martin"))
        onNodeWithText("Observateur d'essai 1").assertDoesNotExist()
        // La croix du web vide le choix.
        if (apparence == Apparence.IOS) onNodeWithContentDescription("Effacer").performClick() else onNodeWithText("✕").performClick()
        assertEquals(-1, e.indexChoisi("id_observateur"))
    }

    @Test
    fun un_observateur_parmi_vingt_se_cherche_au_clavier_sous_android() = unObservateurParmiVingt(Apparence.ANDROID)

    @Test
    fun un_observateur_parmi_vingt_se_cherche_au_clavier_sous_ios() = unObservateurParmiVingt(Apparence.IOS)

    @Test
    fun la_liste_d_un_champ_bas_dans_l_ecran_vient_a_l_ecran_sous_ios() = runComposeUiTest {
        // Relecture du 27 novembre 2026 : la liste s'ouvre sous le champ ; un champ en bas de l'écran la
        // laissait hors de vue, sous le clavier et la barre de boutons. Ici, un écran de 400 pt dont le
        // champ occupe le bas : touché, le champ et sa liste défilent ensemble jusqu'à se voir.
        val e = etat(listOf(observateur(noms)))
        setContent {
            CompositionLocalProvider(LocalApparence provides Apparence.IOS) {
                MaterialTheme {
                    Column(Modifier.height(400.dp).verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(336.dp))
                        FormulaireMonitoring(e, emptySet(), {}, {})
                    }
                }
            }
        }
        val champ = onNode(hasSetTextAction())
        champ.performClick()
        champ.assertIsDisplayed()
        onNodeWithText("Observateur d'essai 5").assertIsDisplayed()
        // Une espace en fin de frappe ne compte pas (une suggestion du clavier en ajoute une).
        champ.performTextInput("martin ")
        onNodeWithText("Éric Martin").assertIsDisplayed()
    }

    @Test
    fun ok_du_clavier_referme_la_liste_sous_ios() = runComposeUiTest {
        // Relecture du 27 novembre 2026 : seul un choix refermait la liste. « OK » quitte le champ, qui
        // reprend le nom choisi (ici aucun : l'invite).
        val e = etat(listOf(observateur(noms)))
        formulaire(Apparence.IOS, e)
        val champ = onNode(hasSetTextAction())
        champ.performClick()
        champ.performTextInput("eric")
        onNodeWithText("Éric Martin").assertExists()
        champ.performImeAction()
        champ.assertIsNotFocused()
        onNodeWithText("Éric Martin").assertDoesNotExist()
        onNodeWithText("Choisir…", useUnmergedTree = true).assertExists()
        assertEquals(-1, e.indexChoisi("id_observateur"))
        // Retouché une seconde plus tard (deux touchers rapprochés feraient un double toucher), il rouvre
        // toute la liste.
        mainClock.advanceTimeBy(1_000)
        champ.performClick()
        champ.assertIsFocused()
        onNodeWithText("Observateur d'essai 1").assertExists()
    }

    @Test
    fun sous_vingt_noms_ou_pour_une_autre_liste_rien_ne_change_sous_android() = runComposeUiTest {
        val e = etat(
            listOf(
                observateur(noms.takeLast(SEUIL_RECHERCHE_LISTE - 1)),
                EditableField("id_habitat", ViewType.SELECT, "Habitat", values = options(noms)),
            ),
        )
        formulaire(Apparence.ANDROID, e)
        // Deux listes déroulantes (Spinner), sans clavier. Aucun champ de texte, même en lecture seule :
        // sous 20 noms, le champ à filtre l'est, et n'a pas d'action de saisie.
        onNodeWithText("Observateur").assertExists()
        onNodeWithText("Habitat").assertExists()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertCountEquals(0)
    }

    @Test
    fun le_dialogue_des_observateurs_se_filtre_pas_celui_des_jeux_de_donnees() = runComposeUiTest {
        val e = etat(
            listOf(
                EditableField("observers", ViewType.SELECT_MULTIPLE, "Observateurs", values = options(noms), observateurs = true),
                EditableField("id_dataset", ViewType.SELECT_MULTIPLE, "Jeux de données", values = options(noms)),
            ),
        )
        var ouvert by mutableStateOf<String?>("observers")
        setContent {
            CompositionLocalProvider(LocalApparence provides Apparence.ANDROID) {
                MaterialTheme { DialogueChoixMultipleFormulaire(e, ouvert) { ouvert = null } }
            }
        }
        onNode(hasSetTextAction()).performTextInput("eric")
        onNodeWithText("Éric Martin").performClick()
        onNodeWithText("Valider").performClick()
        waitForIdle()
        assertEquals(listOf<Any?>("${eric + 1}"), e.choixMultiples("observers"))
        // Les jeux de données : une autre liste, sans filtre.
        ouvert = "id_dataset"
        waitForIdle()
        onNodeWithText("Jean Dupont").assertExists()
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
}
