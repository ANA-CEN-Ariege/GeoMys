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

// Propre au portage : la liste du champ « Serveur GeoNature » des Paramètres (13 novembre 2026),
// retouchée le 20 novembre 2026 : deux serveurs retirés, un renommé, le serveur de tests de l'ANA
// réservé aux versions de test.

package fr.ariegenature.geomys.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeursGeoNatureTest {

    private fun noms(saisie: String, versionDeTest: Boolean = false) =
        ServeursGeoNature.filtrer(saisie, versionDeTest).map { it.nom }

    @Test
    fun saisie_vide_tous_les_serveurs() {
        val release = ServeursGeoNature.proposes(versionDeTest = false)
        assertEquals(19, release.size)
        assertEquals(release, ServeursGeoNature.filtrer(""))
        assertEquals(release, ServeursGeoNature.filtrer("  "))
        assertEquals(20, ServeursGeoNature.tous.size)
        assertEquals(ServeursGeoNature.tous, ServeursGeoNature.filtrer("", versionDeTest = true))
    }

    /** Demande du 20 novembre 2026 : le serveur de tests de l'ANA ne doit jamais apparaître dans
     *  une release, et le filtre par défaut est celui d'une release. */
    @Test
    fun le_serveur_de_tests_n_est_propose_que_dans_une_version_de_test() {
        assertEquals(listOf("ANA - CEN Ariège - Tests"), ServeursGeoNature.tous.filter { it.testsSeulement }.map { it.nom })
        assertTrue(ServeursGeoNature.proposes(versionDeTest = false).none { it.testsSeulement })
        assertEquals(emptyList(), noms("tests"))
        assertEquals(listOf("ANA - CEN Ariège"), noms("ariege"))
        assertEquals(listOf("ANA - CEN Ariège - Tests"), noms("tests", versionDeTest = true))
        assertEquals(listOf("ANA - CEN Ariège", "ANA - CEN Ariège - Tests"), noms("ariege", versionDeTest = true))
    }

    /** Demande du 20 novembre 2026 : le serveur renommé est rangé à sa place alphabétique. */
    @Test
    fun le_serveur_renomme_suit_l_ordre_alphabetique() {
        assertEquals(listOf("GeoNat'îdF", "GeoNat'Occitanie"), noms("geonat'"))
    }

    @Test
    fun le_nom_filtre_sans_casse_ni_accents() {
        assertEquals(listOf("Parc national des Pyrénées"), noms("PYRENEES"))
        assertEquals(listOf("Parc national des Cévennes"), noms("cevennes"))
        assertEquals(listOf("GeoNat'îdF"), noms("geonat'idf"))
        assertEquals(listOf("GeoNat'Occitanie"), noms("geonat'occitanie"))
    }

    @Test
    fun l_adresse_filtre_aussi() {
        assertEquals(listOf("GeoNat'Occitanie"), noms("biodiv-occitanie"))
    }

    @Test
    fun une_url_inconnue_ne_propose_rien() {
        assertEquals(emptyList(), noms("https://geonature.mon-asso.test"))
    }

    /** L'adresse est celle du web : l'appli y ajoute elle-même `/api`. */
    @Test
    fun adresses_en_https_sans_api_ni_barre_finale() {
        for (s in ServeursGeoNature.tous) {
            assertTrue(s.url.startsWith("https://"), s.url)
            assertTrue(!s.url.endsWith("/") && !s.url.contains("/api"), s.url)
        }
        assertEquals(ServeursGeoNature.tous.size, ServeursGeoNature.tous.map { it.url }.toSet().size)
    }
}
