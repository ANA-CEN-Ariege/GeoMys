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

// Propre au portage : les SimpleDateFormat d'affichage des écrans de GeoMys-Android, dans le fuseau
// de l'appareil et en français.

package fr.ariegenature.geomys.util

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Dates d'affichage (epoch millis → texte, heure locale), aux motifs des écrans d'Android. */
object FormatDate {

    /** Mois abrégés en français, comme `MMM` de SimpleDateFormat (données CLDR). */
    private val MOIS_ABREGES = listOf(
        "janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.",
    )

    private fun local(millis: Long): LocalDateTime =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())

    private fun deux(v: Int) = v.toString().padStart(2, '0')

    private fun annee(t: LocalDateTime) = t.year.toString().padStart(4, '0')

    /** `dd/MM/yyyy`. */
    fun jour(millis: Long): String = local(millis).let { "${deux(it.day)}/${deux(it.month.ordinal + 1)}/${annee(it)}" }

    /** `HH:mm`. */
    fun heure(millis: Long): String = local(millis).let { "${deux(it.hour)}:${deux(it.minute)}" }

    /** `dd/MM/yyyy HH:mm`. */
    fun jourHeure(millis: Long): String = "${jour(millis)} ${heure(millis)}"

    /** `dd/MM HH:mm`. */
    fun jourMoisHeure(millis: Long): String =
        local(millis).let { "${deux(it.day)}/${deux(it.month.ordinal + 1)} ${deux(it.hour)}:${deux(it.minute)}" }

    /** `dd MMM yyyy HH:mm` (« 19 oct. 2026 07:50 »). */
    fun jourMoisLettresHeure(millis: Long): String =
        local(millis).let { "${deux(it.day)} ${MOIS_ABREGES[it.month.ordinal]} ${annee(it)} ${deux(it.hour)}:${deux(it.minute)}" }
}
