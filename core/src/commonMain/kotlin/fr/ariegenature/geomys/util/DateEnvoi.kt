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

// Remplace les SimpleDateFormat("yyyy-MM-dd") et ("HH:mm") des envois de GeoMys-Android @ cdd5196
// (network/GeoNatureUpload.kt, network/OccHabUpload.kt) : même format, fuseau de l'appareil.

package fr.ariegenature.geomys.util

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Dates et heures des corps d'envoi GeoNature, à l'heure locale de l'appareil. */
object DateEnvoi {

    /** `yyyy-MM-dd` de l'instant [ms]. */
    fun date(ms: Long, fuseau: TimeZone = TimeZone.currentSystemDefault()): String =
        local(ms, fuseau).let { "${it.year.toString().padStart(4, '0')}-${deux(it.month.ordinal + 1)}-${deux(it.day)}" }

    /** `HH:mm` de l'instant [ms]. */
    fun heure(ms: Long, fuseau: TimeZone = TimeZone.currentSystemDefault()): String =
        local(ms, fuseau).let { "${deux(it.hour)}:${deux(it.minute)}" }

    private fun local(ms: Long, fuseau: TimeZone): LocalDateTime = Instant.fromEpochMilliseconds(ms).toLocalDateTime(fuseau)

    private fun deux(valeur: Int): String = valeur.toString().padStart(2, '0')
}
