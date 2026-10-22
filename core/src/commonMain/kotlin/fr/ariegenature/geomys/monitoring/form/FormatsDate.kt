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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/monitoring/form/FormulaireRenderer.kt,
// creerChampDate, creerChampTime, creerChampDateTime et parseHeure) : SimpleDateFormat en mode
// tolérant (« lenient », son mode par défaut) remplacé par une lecture maison sur kotlinx-datetime,
// qui reproduit ce qu'il acceptait et comment il reportait les débordements.

package fr.ariegenature.geomys.monitoring.form

import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Les formats des champs DATE, TIME et DATETIME du formulaire des Suivis, et leur lecture tolérante.
 *
 * Valeurs (ce qui part au serveur) :
 * - DATE : `yyyy-M-d`, sans zéros au mois ni au jour (ex. « 2026-5-21 »), comme le formulaire web
 *   GeoNature ;
 * - TIME : `HH:MM` ;
 * - DATETIME : `yyyy-MM-dd HH:mm:ss`, secondes à zéro quand l'utilisateur la choisit (accepté par le
 *   backend, cf. formatDateTime de gn_mobile_monitoring).
 *
 * Affichage : `dd/MM/yyyy` pour DATE, `dd/MM/yyyy HH:mm` pour DATETIME, l'heure telle quelle.
 *
 * Lecture d'une valeur initiale (défaut serveur, date du jour, saisie relue) : celle de
 * `SimpleDateFormat` en mode tolérant. Le texte doit COMMENCER par les champs du motif (la suite est
 * ignorée : une heure ou un fuseau après une date), chaque nombre peut avoir autant de chiffres
 * qu'on veut, et les valeurs hors bornes se reportent : le 30 février devient le 2 mars, le mois 13
 * janvier de l'année suivante.
 */
object FormatsDate {

    private val DATE = Regex("""^[ \t]*(\d+)-[ \t]*(\d+)-[ \t]*(\d+)""")
    private val DATE_HEURE_SECONDES =
        Regex("""^[ \t]*(\d+)-[ \t]*(\d+)-[ \t]*(\d+) [ \t]*(\d+):[ \t]*(\d+):[ \t]*(\d+)""")
    private val DATE_HEURE = Regex("""^[ \t]*(\d+)-[ \t]*(\d+)-[ \t]*(\d+) [ \t]*(\d+):[ \t]*(\d+)""")

    /** Une date DATE à partir d'une valeur initiale : la partie heure éventuelle (après « T » ou
     *  une espace) est retirée, puis le reste est lu comme `yyyy-M-d`, qui accepte aussi
     *  `yyyy-MM-dd`. Null si le texte ne commence pas par une date. */
    fun lireDate(brut: String): LocalDate? {
        val sansHeure = brut.substringBefore('T').substringBefore(' ')
        val m = DATE.find(sansHeure) ?: return null
        val (a, mo, j) = m.destructured
        return try {
            dateTolerante(a.toIntOrNull() ?: return null, mo.toIntOrNull() ?: return null, j.toIntOrNull() ?: return null)
        } catch (_: IllegalArgumentException) {
            null // année hors de ce que sait représenter un calendrier
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** Une date-heure DATETIME à partir d'une valeur initiale : « T » ou espace entre la date et
     *  l'heure, secondes facultatives. Null si le texte ne commence par aucun des deux motifs. */
    fun lireDateHeure(brut: String): LocalDateTime? {
        val normalise = brut.trim().replace('T', ' ')
        val m = DATE_HEURE_SECONDES.find(normalise) ?: DATE_HEURE.find(normalise) ?: return null
        val g = m.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
        return try {
            dateHeureTolerante(g[0], g[1], g[2], g[3], g[4], g.getOrElse(5) { 0 })
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** L'heure et la minute d'une valeur TIME, tolérante : `HH:MM`, `HH:MM:SS`, `10h30`. Une heure
     *  hors de 0..23 rend null ; une minute absente ou hors de 0..59 vaut 0. */
    fun lireHeure(brut: String): Pair<Int, Int>? {
        val s = brut.trim().lowercase().replace("h", ":")
        val parts = s.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
        return h to m
    }

    /** Valeur d'un champ DATE : `yyyy-M-d`. */
    fun date(d: LocalDate): String = "${annee(d.year)}-${d.month.ordinal + 1}-${d.day}"

    /** Valeur d'un champ DATE choisi au sélecteur ([mois] de 1 à 12). */
    fun date(annee: Int, mois: Int, jour: Int): String = date(dateTolerante(annee, mois, jour))

    /** Affichage d'une date : `dd/MM/yyyy`. */
    fun affichageDate(d: LocalDate): String = "${deux(d.day)}/${deux(d.month.ordinal + 1)}/${annee(d.year)}"

    /** Valeur d'un champ TIME : `HH:MM`. */
    fun heure(heure: Int, minute: Int): String = "${deux(heure)}:${deux(minute)}"

    /** Valeur d'un champ DATETIME : `yyyy-MM-dd HH:mm:ss`. */
    fun dateHeure(dt: LocalDateTime): String =
        "${annee(dt.year)}-${deux(dt.month.ordinal + 1)}-${deux(dt.day)} ${deux(dt.hour)}:${deux(dt.minute)}:${deux(dt.second)}"

    /** Affichage d'une date-heure : `dd/MM/yyyy HH:mm`. */
    fun affichageDateHeure(dt: LocalDateTime): String =
        "${deux(dt.day)}/${deux(dt.month.ordinal + 1)}/${annee(dt.year)} ${deux(dt.hour)}:${deux(dt.minute)}"

    /** La date que donnait un calendrier tolérant pour ces champs : le mois, puis le jour, sont
     *  ajoutés au 1er janvier de l'année, d'où le report des débordements. */
    private fun dateTolerante(annee: Int, mois: Int, jour: Int): LocalDate =
        LocalDate(annee, 1, 1).plus(mois - 1, DateTimeUnit.MONTH).plus(jour - 1, DateTimeUnit.DAY)

    private fun dateHeureTolerante(annee: Int, mois: Int, jour: Int, heure: Int, minute: Int, seconde: Int): LocalDateTime {
        val debut = dateTolerante(annee, mois, jour).atStartOfDayIn(TimeZone.UTC)
        val instant = debut.plus(DateTimePeriod(hours = heure, minutes = minute, seconds = seconde), TimeZone.UTC)
        return instant.toLocalDateTime(TimeZone.UTC)
    }

    /** L'année sur quatre chiffres au moins, comme le motif `yyyy`. */
    private fun annee(a: Int): String = a.toString().padStart(4, '0')

    private fun deux(v: Int): String = v.toString().padStart(2, '0')
}
