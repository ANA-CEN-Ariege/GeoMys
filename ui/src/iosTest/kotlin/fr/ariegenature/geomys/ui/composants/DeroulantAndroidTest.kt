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

// Propre au portage : le champ « menu déroulant » de l'allure Android, commun, éprouvé ici sur le
// simulateur avec les autres tests d'écran. Sous Android, chaque touche du clavier referme sa liste
// (une fenêtre sans focus, au-dessus du clavier) ; la liste rendait alors au champ le libellé choisi,
// et chaque lettre tapée remplaçait la précédente (JDD et observateurs d'une nouvelle station OccHab,
// relevé sur le Galaxy A12 le 29 octobre 2026). Un toucher hors de la liste en est l'équivalent ici.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DeroulantAndroidTest {

    @Test
    fun la_liste_refermee_par_un_toucher_ailleurs_garde_la_frappe() = runComposeUiTest {
        val jeux = List(SEUIL_RECHERCHE_LISTE + 5) { "Jeu $it" }
        setContent {
            Column(Modifier.width(361.dp)) {
                // Au-dessus du champ : la liste se déroule dessous.
                Box(Modifier.size(361.dp, 80.dp).testTag("ailleurs"))
                DeroulantAndroid("Jeu de données", jeux, "", restaurer = true, onChoix = {})
            }
        }
        val champ = onNode(hasSetTextAction())
        champ.performClick()
        champ.performTextInput("je")
        onNodeWithText("Jeu 0").assertExists()
        onNodeWithTag("ailleurs").performClick()
        onNodeWithText("Jeu 0").assertDoesNotExist()
        champ.performTextInput("u")
        assertEquals("jeu", champ.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        onNodeWithText("Jeu 0").assertExists()
    }
}
