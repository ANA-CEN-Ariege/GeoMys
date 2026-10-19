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

// Le jour d'une station OccHab : le TextView cliquable et le DatePickerDialog du dialogue des détails de
// GeoMys-Android (OccHabDetailsDialog), le DatePicker compact d'OccHabInfosStationSheet (GeoMys-IOS).

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** Un jour sans heure : [onChoix] reçoit minuit, heure du téléphone, du jour choisi (les dates de
 *  station OccHab sont des jours). Les jours futurs sont permis.
 *  - Android : texte de 15 sp (« dd/MM/yyyy », « — » sans date), marges de 4 × 10 dp, ondulation
 *    au toucher, qui ouvre le DatePickerDialog du système ;
 *  - iOS : UIDatePicker compact, sur [fondIos] ; sans date, le jour même. */
@Composable
expect fun ChampJour(
    millis: Long?,
    onChoix: (Long) -> Unit,
    modifier: Modifier = Modifier,
    fondIos: Color = CouleursGeoMys.fondBas,
)
