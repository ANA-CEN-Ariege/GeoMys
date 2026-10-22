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

@file:OptIn(ExperimentalForeignApi::class)

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIAction
import platform.UIKit.UIControlContentHorizontalAlignmentLeading
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle

/** `UIDatePicker` compact, celui du `DatePicker` de SwiftUI, teinté jaune clair comme toute
 *  l'interface de GeoMys-IOS ; la vue prend toute la largeur offerte, ses pastilles à gauche. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun ChampDateHeure(millis: Long, heures: Boolean, onChange: (Long) -> Unit, modifier: Modifier, fondIos: Color) {
    val rappel by rememberUpdatedState(onChange)
    var taille by remember { mutableStateOf(DpSize.Zero) }
    UIKitView(
        factory = {
            UIDatePicker().apply {
                preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleCompact
                tintColor = CouleursGeoMys.jauneClair.versUIColor()
                // Pastilles à gauche, comme le DatePicker de GeoMys-IOS : le sélecteur prend toute la
                // largeur (ci-dessous) et calerait sinon son contenu à droite.
                contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeading
                addAction(
                    UIAction.actionWithHandler { action ->
                        val selecteur = action?.sender as? UIDatePicker ?: return@actionWithHandler
                        rappel((selecteur.date.timeIntervalSince1970 * 1000).toLong())
                    },
                    forControlEvents = UIControlEventValueChanged,
                )
            }
        },
        modifier = modifier.size(taille),
        update = { selecteur ->
            selecteur.backgroundColor = fondIos.versUIColor()
            selecteur.datePickerMode = if (heures) UIDatePickerMode.UIDatePickerModeDateAndTime else UIDatePickerMode.UIDatePickerModeDate
            // Du 1ᵉʳ janvier 1735 à aujourd'hui, comme le pnx-date du web.
            selecteur.minimumDate = NSDate.dateWithTimeIntervalSince1970(PREMIER_JOUR_WEB / 1000.0)
            selecteur.maximumDate = NSDate()
            selecteur.setDate(NSDate.dateWithTimeIntervalSince1970(millis / 1000.0), animated = false)
            // Sans taille intrinsèque, le sélecteur rend la largeur proposée : il prend toute la largeur
            // offerte. À sa taille compressée, il passait au format court (« 28/05/2026 ») selon les jours.
            selecteur.sizeThatFits(CGSizeMake(1000.0, 1000.0)).useContents { taille = DpSize(width.dp, height.dp) }
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
    )
}
