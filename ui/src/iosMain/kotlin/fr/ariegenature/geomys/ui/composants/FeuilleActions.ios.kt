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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.popoverPresentationController

/** UIAlertController en feuille d'actions, boutons teintés du jaune de GeoMys-IOS. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun FeuilleActionsSysteme(visible: Boolean, titre: String, boutons: List<BoutonAlerte>, onFermee: () -> Unit, message: String?) {
    val controleur = LocalUIViewController.current
    val fermer by rememberUpdatedState(onFermee)
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        val feuille = UIAlertController.alertControllerWithTitle(titre, message, UIAlertControllerStyleActionSheet)
        for (bouton in boutons) {
            val style = when {
                bouton.annulation -> UIAlertActionStyleCancel
                bouton.destructif -> UIAlertActionStyleDestructive
                else -> UIAlertActionStyleDefault
            }
            feuille.addAction(UIAlertAction.actionWithTitle(bouton.texte, style) { _ ->
                bouton.action()
                fermer()
            })
        }
        feuille.view.tintColor = CouleursGeoMys.jauneClair.versUIColor()
        // Sur iPad, la feuille s'ouvre en bulle : il lui faut une origine.
        feuille.popoverPresentationController?.let { bulle ->
            bulle.sourceView = controleur.view
            controleur.view.bounds.useContents { bulle.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
        }
        var sommet = controleur
        while (sommet.presentedViewController != null) sommet = sommet.presentedViewController!!
        sommet.presentViewController(feuille, animated = true, completion = null)
    }
}
