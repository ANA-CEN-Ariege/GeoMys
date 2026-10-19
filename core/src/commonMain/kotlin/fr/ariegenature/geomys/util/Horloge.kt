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

package fr.ariegenature.geomys.util

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/** L'heure courante en millisecondes depuis l'époque Unix : `System.currentTimeMillis()` dans
 *  GeoMys-Android. */
fun maintenantMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Le mois courant (1 à 12) dans le fuseau du téléphone : `Calendar.MONTH + 1` dans GeoMys-Android. */
fun moisCourant(): Int = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).month.number

/** Minuit, heure du téléphone, du jour de [millis] : un `Calendar` remis à 0 h 0 min 0 s 0 ms dans
 *  GeoMys-Android (dates des stations OccHab, des jours sans heure). */
fun debutDuJour(millis: Long = maintenantMillis(), fuseau: TimeZone = TimeZone.currentSystemDefault()): Long =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(fuseau).date.atStartOfDayIn(fuseau).toEpochMilliseconds()
