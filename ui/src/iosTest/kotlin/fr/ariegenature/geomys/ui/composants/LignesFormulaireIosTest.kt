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

// Propre au portage : les lignes de formulaire de l'allure iOS. Le libellé d'un champ est au-dessus de
// lui, comme sous Android (demande du 19 octobre) ; à côté, une valeur longue le réduisait à une lettre
// par ligne (relevé sur l'iPhone 16, détails d'une station). Le menu du système, une vue UIKit, ne se
// compose pas sans fenêtre : on mesure ce qu'il recouvre. Le jeu de données des dialogues de détails, partagé
// depuis le 20 octobre, se choisit dans une liste filtrée, qui défile depuis le 21 octobre (à partir de 20
// valeurs ; en dessous, la ligne de choix et son menu).

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LignesFormulaireIosTest {

    // Une feuille de 361 pt, celle de l'iPhone 16.

    @Test
    fun le_libelle_d_un_choix_est_au_dessus_de_sa_valeur() = runComposeUiTest {
        val valeur = "Calculée à partir de la géométrie numérisée sur le terrain"
        setContent { Box(Modifier.width(361.dp)) { LigneIos { ContenuLigneChoixIos("Méthode de calcul de la surface", valeur) } } }
        val libelle = onNodeWithText("Méthode de calcul de la surface").getBoundsInRoot()
        val choix = onNodeWithText(valeur).getBoundsInRoot()
        assertTrue(libelle.bottom <= choix.top, "le libellé (bas à ${libelle.bottom}) doit être au-dessus de la valeur (haut à ${choix.top})")
        assertTrue(libelle.right - libelle.left >= 200.dp, "le libellé tient sur sa ligne")
    }

    @Test
    fun le_libelle_d_un_nombre_est_au_dessus_de_son_champ() = runComposeUiTest {
        setContent { Box(Modifier.width(361.dp)) { LigneNombreIos("Altitude min (m)", "", {}) } }
        val libelle = onNodeWithText("Altitude min (m)").getBoundsInRoot()
        val champ = onNode(hasSetTextAction()).getBoundsInRoot()
        assertTrue(libelle.bottom <= champ.top, "le libellé (bas à ${libelle.bottom}) doit être au-dessus du champ (haut à ${champ.top})")
    }

    @Test
    fun le_plus_des_profondeurs_est_au_bout_de_la_ligne_de_l_altitude() = runComposeUiTest {
        var touches = 0
        setContent {
            Box(Modifier.width(361.dp)) {
                LigneNombreIos("", "850", {}, apres = { BoutonCadreIos("plus", Color.White, "Afficher les profondeurs") { touches++ } })
            }
        }
        val champ = onNode(hasSetTextAction()).getBoundsInRoot()
        val plus = onNodeWithContentDescription("Afficher les profondeurs").getBoundsInRoot()
        assertTrue(champ.right <= plus.left, "le « + » (gauche à ${plus.left}) doit suivre le champ (droite à ${champ.right})")
        assertTrue(champ.right - champ.left >= 250.dp, "le champ garde presque toute la ligne")
        onNodeWithContentDescription("Afficher les profondeurs").performClick()
        assertEquals(1, touches)
    }

    @Test
    fun le_jeu_choisi_dans_la_liste_filtree_remplace_la_recherche() = runComposeUiTest {
        // À partir de 20 valeurs, le champ à filtrer ; toute la liste défile (on n'en voyait que cinq).
        val jeux = listOf("Inventaires généraux (12)", "Suivi des chiroptères (7)", "Flore patrimoniale (3)") +
            (1..22).map { "Jeu d'essai $it (${100 + it})" }
        var choisi by mutableStateOf(jeux[0])
        setContent {
            Column(Modifier.width(361.dp)) {
                LignesRechercheIos("Rechercher un jeu de données", jeux, choisi, onChoix = { choisi = jeux[it] })
            }
        }
        // Le toucher vide le champ et déroule toute la liste ; la frappe la filtre, casse et accents ignorés.
        onNode(hasSetTextAction()).performClick()
        onNodeWithText("Flore patrimoniale (3)").assertExists()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Jeu d'essai 22 (122)"))
        onNodeWithText("Jeu d'essai 22 (122)").assertExists()
        onNode(hasSetTextAction()).performTextInput("CHIROPTERES")
        onAllNodesWithText("Flore patrimoniale (3)").assertCountEquals(0)
        onNodeWithText("Suivi des chiroptères (7)").performClick()
        // Le choix referme la liste, et le champ, qui a perdu le focus, affiche le jeu choisi.
        assertEquals("Suivi des chiroptères (7)", choisi)
        onNode(hasSetTextAction()).assert(hasText("Suivi des chiroptères (7)"))
        onAllNodesWithText("Inventaires généraux (12)").assertCountEquals(0)
    }
}
