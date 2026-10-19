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
// compose pas sans fenêtre : on mesure ce qu'il recouvre.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
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
}
