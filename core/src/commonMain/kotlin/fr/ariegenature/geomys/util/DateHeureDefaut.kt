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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/util/DateHeureDefaut.kt).
// SimpleDateFormat et Date remplacés par kotlinx-datetime ; mêmes formats, même fuseau (celui de
// l'appareil).

package fr.ariegenature.geomys.util

import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Valeurs par défaut « date du jour / heure actuelle » pour les champs date/heure des
 *  formulaires, appliquées seulement quand le serveur GeoNature ne fournit pas de défaut
 *  propre. Les formats correspondent à ce qu'attendent les renderers et le backend :
 *   - date     → "yyyy-MM-dd"          (ISO, accepté en entrée par creerChampDate)
 *   - heure    → "HH:mm"               (cf. creerChampTime)
 *   - datetime → "yyyy-MM-dd HH:mm:ss" (cf. creerChampDateTime / formatDateTime serveur)
 */
object DateHeureDefaut {
    fun dateDuJour(): String = date(maintenant())

    fun heureActuelle(): String = maintenant().let { "${deux(it.hour)}:${deux(it.minute)}" }

    fun dateHeureActuelle(): String =
        maintenant().let { "${date(it)} ${deux(it.hour)}:${deux(it.minute)}:${deux(it.second)}" }

    private fun maintenant(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    private fun date(moment: LocalDateTime): String =
        "${moment.year.toString().padStart(4, '0')}-${deux(moment.month.ordinal + 1)}-${deux(moment.day)}"

    private fun deux(valeur: Int): String = valeur.toString().padStart(2, '0')
}
