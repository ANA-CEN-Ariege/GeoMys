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

@file:OptIn(ExperimentalForeignApi::class)

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import fr.ariegenature.geomys.monitoring.form.SelectionDateHeure
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.UIKit.UIAction
import platform.UIKit.UIControlContentHorizontalAlignmentLeading
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle

/** Fond des pastilles de UIDatePicker compact (tertiarySystemFill du mode sombre). */
private val FOND_PASTILLE = Color(0x3D767680)

private fun versNSDate(s: SelectionDateHeure): NSDate? {
    val composantes = NSDateComponents().apply {
        year = s.annee.toLong()
        month = s.mois.toLong()
        day = s.jour.toLong()
        hour = s.heure.toLong()
        minute = s.minute.toLong()
    }
    return NSCalendar.currentCalendar.dateFromComponents(composantes)
}

private fun depuisNSDate(date: NSDate): SelectionDateHeure {
    val unites = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour or NSCalendarUnitMinute
    val c = NSCalendar.currentCalendar.components(unites, fromDate = date)
    return SelectionDateHeure(c.year.toInt(), c.month.toInt(), c.day.toInt(), c.hour.toInt(), c.minute.toInt())
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun ChampDateMonitoring(
    mode: ModeDateHeure,
    affichage: String?,
    initiale: SelectionDateHeure,
    onChoix: (SelectionDateHeure) -> Unit,
    modifier: Modifier,
) {
    val rappel by rememberUpdatedState(onChoix)
    if (affichage == null) {
        // Sans valeur, le sélecteur montrerait maintenant sans l'avoir retenu : l'invite à la place.
        BoutonIos({ rappel(initiale) }, modifier, opaciteAppui = 0.6f) {
            Box(Modifier.background(FOND_PASTILLE, RoundedCornerShape(6.dp)).padding(horizontal = 11.dp, vertical = 6.dp)) {
                Text(mode.invite, fontSize = 17.sp, color = CouleursGeoMys.jauneClair)
            }
        }
        return
    }
    var taille by remember { mutableStateOf(DpSize.Zero) }
    UIKitView(
        factory = {
            UIDatePicker().apply {
                preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleCompact
                tintColor = CouleursGeoMys.jauneClair.versUIColor()
                // Pastilles à gauche, sous le libellé, comme le DatePicker de GeoMys-IOS : le sélecteur
                // prend toute la largeur (ci-dessous) et calerait sinon son contenu à droite.
                contentHorizontalAlignment = UIControlContentHorizontalAlignmentLeading
                addAction(
                    UIAction.actionWithHandler { action ->
                        val selecteur = action?.sender as? UIDatePicker ?: return@actionWithHandler
                        rappel(depuisNSDate(selecteur.date))
                    },
                    forControlEvents = UIControlEventValueChanged,
                )
            }
        },
        modifier = modifier.size(taille),
        update = { selecteur ->
            selecteur.backgroundColor = FOND_LIGNE_FORMULAIRE_IOS.versUIColor()
            selecteur.datePickerMode = when (mode) {
                ModeDateHeure.DATE -> UIDatePickerMode.UIDatePickerModeDate
                ModeDateHeure.HEURE -> UIDatePickerMode.UIDatePickerModeTime
                ModeDateHeure.DATE_HEURE -> UIDatePickerMode.UIDatePickerModeDateAndTime
            }
            versNSDate(initiale)?.let { selecteur.setDate(it, animated = false) }
            // Sans taille intrinsèque, le sélecteur rend la largeur proposée : il prend toute la largeur
            // offerte. À sa taille compressée, plus étroite d'un tiers de point que « 28 mai 2026 », il
            // passait au format court (« 28/05/2026 ») et décalait sa pastille, selon les jours.
            selecteur.sizeThatFits(CGSizeMake(1000.0, 1000.0)).useContents { taille = DpSize(width.dp, height.dp) }
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
    )
}
