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

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// Les éléments que chaque plateforme dessine elle-même : interrupteur, indicateur d'activité,
// symboles SF et alertes système d'iOS. Sous Android, Compose reproduit les widgets de Material
// Components ; sous iOS, ce sont les contrôles UIKit qu'emploie SwiftUI dans GeoMys-IOS.

/** Graisse d'un symbole SF (`.font(.system(size:weight:))` de SwiftUI). */
enum class GraisseSymbole { NORMALE, MOYENNE, DEMI_GRASSE, GRASSE }

/** Symbole SF [nom] de [taille] points, rendu par iOS lui-même (`Image(systemName:)` de SwiftUI).
 *  Une couleur : symbole monochrome ; plusieurs : rendu « palette », dans l'ordre des calques
 *  (`.symbolRenderingMode(.palette)`). Ne s'affiche que sous iOS. */
@Composable
expect fun SymboleSf(
    nom: String,
    taille: Float,
    couleurs: List<Color>,
    modifier: Modifier = Modifier,
    graisse: GraisseSymbole = GraisseSymbole.NORMALE,
    description: String? = null,
)

/** Interrupteur : MaterialSwitch de GeoMys-Android, `Toggle` (UISwitch) teinté en bleu primaire
 *  dans GeoMys-IOS. */
@Composable
expect fun Interrupteur(
    coche: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
)

/** Indicateur d'activité indéterminé : ProgressBar d'Android dans une boîte de [tailleAndroid] dp
 *  (le cercle occupe 42/48 de la boîte, comme le drawable Material), `ProgressView` de SwiftUI
 *  (UIActivityIndicatorView) sous iOS, réduit si [petitIos] (`.controlSize(.small)`). */
@Composable
expect fun IndicateurActivite(
    couleur: Color,
    modifier: Modifier = Modifier,
    tailleAndroid: Float = 48f,
    petitIos: Boolean = false,
)

/** Un bouton d'alerte système. [annulation] : le rôle `.cancel` de SwiftUI. */
data class BoutonAlerte(val texte: String, val annulation: Boolean = false, val action: () -> Unit = {})

/** Présente l'alerte système d'iOS (UIAlertController, comme `.alert` de SwiftUI). [onFermee] est
 *  appelé après chaque bouton. Sans effet sous Android, où l'alerte est un dialogue Compose. */
@Composable
expect fun AlerteSysteme(
    visible: Boolean,
    titre: String,
    message: String,
    boutons: List<BoutonAlerte>,
    onFermee: () -> Unit,
)
