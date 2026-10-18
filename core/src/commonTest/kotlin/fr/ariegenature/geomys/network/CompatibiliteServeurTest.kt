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

package fr.ariegenature.geomys.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tourne sur la JVM (tests host Android) et sur le simulateur iOS : vérifie au passage que le
 *  code commun se comporte pareil sur les deux plateformes. */
class CompatibiliteServeurTest {

    @Test
    fun les_versions_se_comparent_par_segment_et_non_comme_du_texte() {
        assertTrue(comparerVersions("2.9", "2.15") < 0)
        assertTrue(comparerVersions("2.15.1", "2.15") > 0)
        assertTrue(comparerVersions("10.0", "9.9") > 0)
    }

    @Test
    fun un_segment_manquant_vaut_zero() {
        assertEquals(0, comparerVersions("2.15", "2.15.0"))
        assertEquals(0, comparerVersions(" 2.15 ", "2.15"))
    }

    @Test
    fun un_suffixe_non_numerique_est_ignore() {
        assertEquals(0, comparerVersions("2.15.0-rc1", "2.15"))
        assertEquals(0, comparerVersions("2.x", "2.0"))
    }

    @Test
    fun seule_une_version_au_moins_egale_a_la_minimale_est_supportee() {
        assertTrue(versionGeoNatureSupportee(VERSION_GEONATURE_MINIMALE))
        assertTrue(versionGeoNatureSupportee("2.16.3"))
        assertFalse(versionGeoNatureSupportee("2.14.2"))
    }
}
