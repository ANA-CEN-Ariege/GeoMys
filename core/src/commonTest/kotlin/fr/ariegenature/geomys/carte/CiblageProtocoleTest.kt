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

// Propre au portage : « Cibler un protocole » du Maps Manager de GeoMys-Android @ cdd5196
// (CacheManagerFragment.chargerSitesProtocole), sans test.

package fr.ariegenature.geomys.carte

import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.network.MonitoringApi
import fr.ariegenature.geomys.network.MonitoringModule
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CiblageProtocoleTest {

    private fun enfant(id: Int, nom: String, geoJson: String? = null) = MonitoringApi.MonitoringEnfant(id, nom, emptyMap(), geoJson)

    @Test
    fun les_sites_viennent_de_la_liste_ou_de_leur_fiche_et_les_autres_sont_ecartes() = runTest {
        val fiches = mutableListOf<Pair<String, Int>>()
        val sites = CiblageProtocole.charger(
            enfants = {
                mapOf(
                    "sites_group" to listOf(
                        enfant(1, "Col de Port", """{"type":"Point","coordinates":[1.50,42.90]}"""),
                        enfant(2, "Étang de Lers"),
                        enfant(3, "Sans géométrie"),
                        enfant(4, "Illisible", "pas du json"),
                    ),
                )
            },
            geometrie = { type, id ->
                fiches += type to id
                if (id == 2) """{"type":"Polygon","coordinates":[[[1.38,42.80],[1.40,42.80],[1.40,42.82],[1.38,42.80]]]}""" else null
            },
        )
        assertEquals(listOf("Col de Port", "Étang de Lers"), sites.objets.map { it.label })
        assertEquals(listOf("sites_group" to 2, "sites_group" to 3), fiches.sortedBy { it.second }, "seuls les sites sans géométrie dans la liste vont à leur fiche")
        assertEquals(Emprise(sud = 42.80, ouest = 1.38, nord = 42.90, est = 1.50), sites.emprise, "l'emprise exacte de tous les sommets")
    }

    @Test
    fun un_point_seul_a_une_petite_boite_et_une_erreur_reseau_ne_donne_rien() = runTest {
        val seul = CiblageProtocole.charger(
            enfants = { mapOf("site" to listOf(enfant(7, "Mare", """{"type":"Point","coordinates":[1.60,42.96]}"""))) },
            geometrie = { _, _ -> null },
        )
        val boite = seul.emprise!!
        assertTrue(boite.nord - boite.sud > 0.003 && boite.est - boite.ouest > 0.003, "± 0,002° autour du point : $boite")

        val rien = CiblageProtocole.charger(enfants = { error("hors ligne") }, geometrie = { _, _ -> null })
        assertTrue(rien.objets.isEmpty())
        assertNull(rien.emprise)
    }

    @Test
    fun le_libelle_d_un_protocole() {
        assertEquals("STOM Ariège (stom_ariege)", CiblageProtocole.libelle(MonitoringModule(idModule = 4, moduleCode = "stom_ariege", moduleLabel = "STOM Ariège")))
    }
}
