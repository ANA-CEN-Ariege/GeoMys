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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeCustom
import platform.UIKit.UIColor
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementState
import platform.UIKit.accessibilityLabel

/** Un UIButton transparent posé PAR-DESSUS le libellé dessiné par Compose : le toucher ouvre son
 *  menu (`showsMenuAsPrimaryAction`), comme le `Menu` + `Picker` de SwiftUI. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun MenuSysteme(options: List<String>, selection: Int, onChoix: (Int) -> Unit, modifier: Modifier) {
    val rappel by rememberUpdatedState(onChoix)
    UIKitView(
        factory = {
            UIButton.buttonWithType(UIButtonTypeCustom).apply {
                backgroundColor = UIColor.clearColor
                showsMenuAsPrimaryAction = true
            }
        },
        modifier = modifier,
        update = { bouton ->
            val actions = options.mapIndexed { i, titre ->
                UIAction.actionWithTitle(titre, image = null, identifier = null) { _ -> rappel(i) }.apply {
                    state = if (i == selection) UIMenuElementState.UIMenuElementStateOn else UIMenuElementState.UIMenuElementStateOff
                }
            }
            bouton.menu = UIMenu.menuWithTitle("", children = actions)
            bouton.accessibilityLabel = options.getOrNull(selection)
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
            placedAsOverlay = true,
        ),
    )
}
