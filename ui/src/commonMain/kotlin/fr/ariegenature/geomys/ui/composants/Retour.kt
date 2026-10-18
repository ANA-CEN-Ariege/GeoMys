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
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/** Intercepte le retour système : bouton ou geste Retour d'Android, balayage depuis le bord gauche
 *  sous iOS. Le dernier composé l'emporte : un écran passe devant la navigation, un dialogue devant
 *  son écran. */
@Composable
fun RetourSysteme(actif: Boolean = true, onRetour: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = actif,
        onBackCompleted = onRetour,
    )
}

/** Les écrans de GeoMys-IOS masquent le bouton retour de la barre de navigation
 *  (`.navigationBarBackButtonHidden(true)`), ce qui désactive aussi le balayage depuis le bord :
 *  on s'y quitte par la coche, « Retour » ou la maison du fil. Sous iOS, ce composable avale donc
 *  le balayage ; sous Android, le bouton Retour garde son comportement. */
@Composable
fun BalayageRetourIosDesactive() {
    if (estIos) RetourSysteme {}
}
