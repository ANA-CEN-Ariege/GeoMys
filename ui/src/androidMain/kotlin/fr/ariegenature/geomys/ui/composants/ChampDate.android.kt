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
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.util.FormatDate

/** Les cases « dd/MM/yyyy » et « HH:mm » ; DatePickerDialog et TimePickerDialog du système, teintés
 *  par l'accent du thème de l'activité. */
@Composable
actual fun ChampDateHeure(millis: Long, heures: Boolean, onChange: (Long) -> Unit, modifier: Modifier, fondIos: Color) {
    val contexte = LocalContext.current
    val valeur by rememberUpdatedState(millis)
    val rappel by rememberUpdatedState(onChange)
    Row(modifier) {
        CaseDate(FormatDate.jour(millis)) {
            val (annee, mois, jour) = DetailsReleveFormulaire.jourDe(valeur)
            DatePickerDialog(contexte, { _, a, m, j -> rappel(DetailsReleveFormulaire.avecJour(valeur, a, m + 1, j)) }, annee, mois - 1, jour)
                .apply { datePicker.maxDate = System.currentTimeMillis() } // pas de date future
                .show()
        }
        if (heures) {
            Spacer(Modifier.width(8.dp))
            CaseDate(FormatDate.heure(millis)) {
                val (heure, minute) = DetailsReleveFormulaire.heureDe(valeur)
                TimePickerDialog(contexte, { _, h, m -> rappel(DetailsReleveFormulaire.avecHeure(valeur, h, m)) }, heure, minute, true).show()
            }
        }
    }
}
