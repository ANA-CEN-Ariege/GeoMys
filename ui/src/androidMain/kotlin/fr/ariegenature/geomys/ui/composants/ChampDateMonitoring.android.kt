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

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.monitoring.form.SelectionDateHeure

/** `bg_carte_contour` : trait de 1 dp #40888888, sans fond. */
private val CONTOUR_CARTE = Color(0x40888888)

@Composable
actual fun ChampDateMonitoring(
    mode: ModeDateHeure,
    affichage: String?,
    initiale: SelectionDateHeure,
    onChoix: (SelectionDateHeure) -> Unit,
    modifier: Modifier,
    onEffacer: (() -> Unit)?,
) {
    if (affichage != null && onEffacer != null) {
        // Le « ✕ » rouge sans contour de la date de fin de « Détails du relevé ».
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ChampDateMonitoring(mode, affichage, initiale, onChoix, Modifier.weight(1f), null)
            Spacer(Modifier.width(8.dp))
            CaseDate("✕", couleur = MaterialTheme.colorScheme.error, contour = false, description = "Effacer", onClick = onEffacer)
        }
        return
    }
    val contexte = LocalContext.current
    val depart by rememberUpdatedState(initiale)
    val rappel by rememberUpdatedState(onChoix)
    val couleurs = MaterialTheme.colorScheme
    fun choisirHeure(base: SelectionDateHeure) {
        TimePickerDialog(contexte, { _, h, m -> rappel(base.copy(heure = h, minute = m)) }, base.heure, base.minute, true).show()
    }
    Text(
        affichage ?: mode.invite,
        fontSize = 16.sp,
        color = if (affichage != null) couleurs.onSurface else couleurs.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CONTOUR_CARTE, RoundedCornerShape(8.dp))
            // Un TextView à fond de forme : pas d'effet au toucher.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                val d = depart
                when (mode) {
                    ModeDateHeure.HEURE -> choisirHeure(d)
                    // Le mois du sélecteur va de 0 à 11.
                    // Du 1ᵉʳ janvier 1735 à aujourd'hui, comme la date du web.
                    ModeDateHeure.DATE -> DatePickerDialog(
                        contexte,
                        { _, a, m, j -> rappel(d.copy(annee = a, mois = m + 1, jour = j)) },
                        d.annee, d.mois - 1, d.jour,
                    ).apply {
                        datePicker.minDate = PREMIER_JOUR_WEB
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                    ModeDateHeure.DATE_HEURE -> DatePickerDialog(
                        contexte,
                        { _, a, m, j -> choisirHeure(d.copy(annee = a, mois = m + 1, jour = j)) },
                        d.annee, d.mois - 1, d.jour,
                    ).show()
                }
            }
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}
