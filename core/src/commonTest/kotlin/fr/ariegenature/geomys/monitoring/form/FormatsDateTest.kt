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

// Propre au portage : la lecture tolérante remplace le SimpleDateFormat « lenient » de GeoMys-Android ;
// les cas attendus sont ceux qu'il rendait.

package fr.ariegenature.geomys.monitoring.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** Lecture tolérante et formats des champs DATE, TIME et DATETIME. */
class FormatsDateTest {

    @Test
    fun date_avec_ou_sans_zeros_et_suite_ignoree() {
        assertEquals(LocalDate(2026, 5, 21), FormatsDate.lireDate("2026-5-21"))
        assertEquals(LocalDate(2026, 5, 21), FormatsDate.lireDate("2026-05-21"))
        assertEquals(LocalDate(2026, 5, 21), FormatsDate.lireDate("2026-05-21T10:00:00Z"))
        assertEquals(LocalDate(2026, 5, 21), FormatsDate.lireDate("2026-05-21 10:00"))
        assertEquals(LocalDate(2026, 5, 21), FormatsDate.lireDate("2026-5-21xyz"))
    }

    @Test
    fun date_debordements_reportes() {
        assertEquals(LocalDate(2026, 3, 2), FormatsDate.lireDate("2026-2-30"))
        assertEquals(LocalDate(2027, 1, 1), FormatsDate.lireDate("2026-13-1"))
        assertEquals(LocalDate(2025, 12, 1), FormatsDate.lireDate("2026-0-1"))
        assertEquals(LocalDate(2026, 4, 30), FormatsDate.lireDate("2026-5-0"))
    }

    @Test
    fun date_illisible() {
        assertNull(FormatsDate.lireDate("21/05/2026"))
        assertNull(FormatsDate.lireDate("2026-5"))
        assertNull(FormatsDate.lireDate(""))
        assertNull(FormatsDate.lireDate(" 2026-5-21"), "espace en tête : la date est coupée avant")
    }

    @Test
    fun formats_de_date() {
        assertEquals("2026-5-1", FormatsDate.date(LocalDate(2026, 5, 1)))
        assertEquals("0026-5-1", FormatsDate.date(LocalDate(26, 5, 1)), "année sur quatre chiffres")
        assertEquals("01/05/2026", FormatsDate.affichageDate(LocalDate(2026, 5, 1)))
        assertEquals("2026-12-31", FormatsDate.date(2026, 12, 31))
    }

    @Test
    fun heure_tolerante() {
        assertEquals(8 to 5, FormatsDate.lireHeure("8:5"))
        assertEquals(10 to 30, FormatsDate.lireHeure("10h30"))
        assertEquals(10 to 0, FormatsDate.lireHeure("10H"))
        assertEquals(10 to 30, FormatsDate.lireHeure(" 10:30:15 "))
        assertEquals(10 to 0, FormatsDate.lireHeure("10:75"))
        assertNull(FormatsDate.lireHeure("24:00"))
        assertNull(FormatsDate.lireHeure("midi"))
        assertEquals("07:03", FormatsDate.heure(7, 3))
    }

    @Test
    fun date_heure_avec_t_ou_espace_et_secondes_facultatives() {
        assertEquals(LocalDateTime(2026, 5, 21, 10, 30, 0), FormatsDate.lireDateHeure("2026-05-21T10:30"))
        assertEquals(LocalDateTime(2026, 5, 21, 10, 30, 15), FormatsDate.lireDateHeure("2026-05-21 10:30:15"))
        assertEquals(LocalDateTime(2026, 5, 21, 10, 30, 15), FormatsDate.lireDateHeure("2026-05-21T10:30:15.123+02:00"))
        assertEquals(LocalDateTime(2026, 5, 21, 10, 30, 0), FormatsDate.lireDateHeure(" 2026-05-21 10:30: "))
        assertEquals(LocalDateTime(2026, 3, 3, 1, 0, 0), FormatsDate.lireDateHeure("2026-02-30 25:00:00"))
        assertNull(FormatsDate.lireDateHeure("2026-05-21"))
        assertEquals("2026-05-01 07:03:00", FormatsDate.dateHeure(LocalDateTime(2026, 5, 1, 7, 3, 0)))
        assertEquals("01/05/2026 07:03", FormatsDate.affichageDateHeure(LocalDateTime(2026, 5, 1, 7, 3, 0)))
    }
}
