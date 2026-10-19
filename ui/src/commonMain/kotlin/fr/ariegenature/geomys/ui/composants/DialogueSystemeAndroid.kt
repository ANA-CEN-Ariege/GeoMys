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

/** Une ligne d'une liste à corbeilles : son [texte] (sur une ou deux lignes) et le nom de sa corbeille,
 *  lu par TalkBack. */
data class LigneCorbeille(val texte: String, val descriptionCorbeille: String)

/** Un dialogue `android.app.AlertDialog`, que le Maps Manager de GeoMys-Android est le seul écran à
 *  employer (les autres passent par AppCompat, reproduit en Compose) : Android le dessine lui-même, sous
 *  le thème de l'activité (boutons à la couleur d'accent, en capitales ; coins et marges du système).
 *  Son [titre], puis son [message], les [elements] d'une liste (`setItems`), des [lignes] à corbeille
 *  (la vue du Maps Manager) ou, si [progression] n'est pas nul, le message et une barre de progression
 *  (`LinearProgressIndicator` de Material 3 : avancement de 0 à 1, indéterminée si la valeur est nulle) ;
 *  ses boutons [positif] et [negatif]. Toucher un élément, une corbeille ou un bouton ferme le dialogue
 *  puis appelle son action ; Retour et le voile appellent [onFermer], sauf s'il n'est pas [annulable].
 *  Sans effet sous iOS. */
@Composable
expect fun DialogueSystemeAndroid(
    visible: Boolean,
    titre: String,
    onFermer: () -> Unit,
    message: String? = null,
    elements: List<String> = emptyList(),
    onElement: (Int) -> Unit = {},
    lignes: List<LigneCorbeille>? = null,
    onCorbeille: (Int) -> Unit = {},
    progression: Progression? = null,
    positif: BoutonAlerte? = null,
    negatif: BoutonAlerte? = null,
    annulable: Boolean = true,
)

/** L'avancement d'une barre de progression : de 0 à 1, indéterminé si nul. */
data class Progression(val avancement: Float?)
