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

// Propre au portage : la liste du champ « Serveur GeoNature » des Paramètres (13 novembre 2026).

package fr.ariegenature.geomys.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeursGeoNatureTest {

    private fun noms(saisie: String) = ServeursGeoNature.filtrer(saisie).map { it.nom }

    @Test
    fun saisie_vide_tous_les_serveurs() {
        assertEquals(22, ServeursGeoNature.proposes.size)
        assertEquals(ServeursGeoNature.proposes, ServeursGeoNature.filtrer(""))
        assertEquals(ServeursGeoNature.proposes, ServeursGeoNature.filtrer("  "))
    }

    @Test
    fun le_nom_filtre_sans_casse_ni_accents() {
        assertEquals(listOf("Parc national des Pyrénées"), noms("PYRENEES"))
        assertEquals(listOf("Parc national des Cévennes"), noms("cevennes"))
        assertEquals(listOf("GeoNat'îdF"), noms("geonat'idf"))
        assertEquals(listOf("ANA - CEN Ariège", "ANA - CEN Ariège - Tests"), noms("ariege"))
    }

    @Test
    fun l_adresse_filtre_aussi() {
        assertEquals(listOf("Biodiv'Occitanie"), noms("biodiv-occitanie"))
        assertEquals(listOf("Silene"), noms("https://expert."))
    }

    @Test
    fun une_url_inconnue_ne_propose_rien() {
        assertEquals(emptyList(), noms("https://geonature.mon-asso.test"))
    }

    /** L'adresse est celle du web : l'appli y ajoute elle-même `/api`. */
    @Test
    fun adresses_en_https_sans_api_ni_barre_finale() {
        for (s in ServeursGeoNature.proposes) {
            assertTrue(s.url.startsWith("https://"), s.url)
            assertTrue(!s.url.endsWith("/") && !s.url.contains("/api"), s.url)
        }
        assertEquals(ServeursGeoNature.proposes.size, ServeursGeoNature.proposes.map { it.url }.toSet().size)
    }
}
