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

// La barre des champs obligatoires du FormulaireRenderer de GeoMys-Android (une View de 3 dp à gauche
// du champ) et de NouvelleVisiteView de GeoMys-IOS, partagée depuis le 20 octobre 2026 par tous les
// formulaires : le web borde de rouge (`ng-invalid`) chaque champ obligatoire vide ou refusé.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** Rouge des barres « champ obligatoire à saisir » sous Android (ROUGE_OBLIGATOIRE) : le rouge franc
 *  des repères de la carte, et non la couleur d'erreur du thème, rose pâle en sombre. */
internal val ROUGE_OBLIGATOIRE = Color(0xFFD32F2F)

/** La barre rouge à gauche de ce qu'elle borde, sur toute sa hauteur, le contenu décalé d'autant
 *  (plus [espace]) : un champ obligatoire encore vide, ou une valeur refusée. */
internal fun Modifier.barreObligatoire(visible: Boolean, couleur: Color, largeur: Dp, espace: Dp = 0.dp, arrondi: Dp = 0.dp): Modifier =
    if (!visible) {
        this
    } else {
        drawBehind {
            drawRoundRect(couleur, size = Size(largeur.toPx(), size.height), cornerRadius = CornerRadius(arrondi.toPx()))
        }.padding(start = largeur + espace)
    }

/** [barreObligatoire] à l'allure de chaque plateforme : 3 dp de [ROUGE_OBLIGATOIRE] collés au champ
 *  sous Android, 3 pt de rouge foncé aux coins arrondis, à 8 pt du champ, sous iOS. */
@Composable
internal fun Modifier.barreChampObligatoire(visible: Boolean): Modifier =
    if (estIos) {
        barreObligatoire(visible, CouleursGeoMys.holoRougeFonce, 3.dp, espace = 8.dp, arrondi = 1.5.dp)
    } else {
        barreObligatoire(visible, ROUGE_OBLIGATOIRE, 3.dp)
    }
