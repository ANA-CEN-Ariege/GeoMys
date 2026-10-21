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

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.occhab.DetailsStation
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.util.FormatDate
import fr.ariegenature.geomys.util.maintenantMillis

/** Le TextView du jour (selectableItemBackground) et le DatePickerDialog du système, ouvert sur le
 *  jour affiché, sinon sur aujourd'hui. */
@Composable
actual fun ChampJour(millis: Long?, onChoix: (Long) -> Unit, modifier: Modifier, fondIos: Color) {
    val contexte = LocalContext.current
    val valeur by rememberUpdatedState(millis)
    val rappel by rememberUpdatedState(onChoix)
    Text(
        millis?.let(FormatDate::jour) ?: "—",
        fontSize = 15.sp,
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val (annee, mois, jour) = DetailsReleveFormulaire.jourDe(valeur ?: maintenantMillis())
                DatePickerDialog(contexte, { _, a, m, j -> rappel(DetailsStation.jour(a, m + 1, j)) }, annee, mois - 1, jour).apply {
                    datePicker.minDate = PREMIER_JOUR_WEB
                    datePicker.maxDate = maintenantMillis()
                }.show()
            }
            .padding(horizontal = 4.dp, vertical = 10.dp),
    )
}
