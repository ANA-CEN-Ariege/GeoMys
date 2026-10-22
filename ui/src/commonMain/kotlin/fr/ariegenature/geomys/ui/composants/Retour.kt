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

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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

/** La garde de sortie d'un formulaire d'espèce ou d'habitat ([rememberGardeSortie]). */
class GardeSortie internal constructor() {
    internal var modifie: () -> Boolean = { false }

    /** La sortie demandée, retenue le temps de la question. */
    internal var enAttente by mutableStateOf<(() -> Unit)?>(null)

    /** Sort par [sortie] : tout de suite si le formulaire est intact, après « Quitter sans
     *  enregistrer » sinon. La maison passe par ici. */
    fun demander(sortie: () -> Unit) {
        if (modifie()) enAttente = sortie else sortie()
    }
}

/** Garde de sortie, comme celle du formulaire d'une visite : le retour système (Android), le
 *  balayage depuis le bord (iOS) et la maison ([GardeSortie.demander]) quittent aussitôt un
 *  formulaire intact, et demandent avant de perdre un formulaire [modifie] ; ils quittaient sans rien
 *  demander, et un geste perdait un dénombrement et ses photos. [onRetour] : la sortie par le retour.
 *  La question est [DialogueGardeSortie]. */
@Composable
fun rememberGardeSortie(modifie: () -> Boolean, onRetour: () -> Unit): GardeSortie {
    val garde = remember { GardeSortie() }
    garde.modifie = modifie
    val retour by rememberUpdatedState(onRetour)
    RetourSysteme { garde.demander { retour() } }
    return garde
}

/** La question de [garde], à composer après le contenu de l'écran, comme tout dialogue (celui
 *  d'Android se dessine dans l'écran). [titre] : « Dénombrement non enregistré »… ; [onAbandon] :
 *  appelé avant la sortie quand l'utilisateur renonce à ses modifications. */
@Composable
fun DialogueGardeSortie(garde: GardeSortie, titre: String, onAbandon: () -> Unit = {}) {
    val enAttente = garde.enAttente
    DialogueAlerte(
        visible = enAttente != null,
        titre = titre,
        message = "Les modifications de cet écran seront perdues.",
        boutons = listOf(
            BoutonAlerte("Continuer la saisie", annulation = true),
            BoutonAlerte("Quitter sans enregistrer", destructif = true) {
                onAbandon()
                enAttente?.invoke()
            },
        ),
        onFermer = { garde.enAttente = null },
    )
}
