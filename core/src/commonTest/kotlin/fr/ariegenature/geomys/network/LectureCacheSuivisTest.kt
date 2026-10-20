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

// Propre au portage : décision de l'utilisateur du 20 octobre, les écrans des Suivis ne lisent que le cache
// que remplit la synchro, et ce que l'appli envoie y est reporté. Aucune requête n'est attendue : le serveur
// simulé n'a pas de réponse prévue.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.json.JSONObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LectureCacheSuivisTest {

    private val m = MonitoringDeTest()

    @AfterTest
    fun nettoyer() = m.nettoyer()

    private val site = """{"id":5,"properties":{"base_site_name":"Col de Port"},
        "children":{"visit":[{"id":9,"properties":{"visit_date_min":"2026-06-01"}}]},
        "geometry":{"type":"Point","coordinates":[1.5,42.9]}}"""

    @Test
    fun sans_synchro_rien_n_est_lu_ni_demande() = runTest {
        assertNull(m.api.lireEnfants("stom"))
        assertNull(m.api.lireObjet("stom", "site", 5))
        assertNull(m.api.lireSchemaProtocole("stom"))
        assertTrue(m.serveur.requetes.isEmpty())
    }

    @Test
    fun les_sites_et_leurs_fiches_viennent_du_cache() = runTest {
        m.cache.setJson(m.cache.keyEnfants("stom"), """{"children":{"site":[{"id":5,"properties":{"base_site_name":"Col de Port"}}]}}""")
        m.cache.setJson(m.cache.keyObjet("stom", "site", 5), site)
        assertEquals(listOf("Col de Port"), m.api.lireEnfants("stom")?.getValue("site")?.map { it.nom })
        val fiche = assertNotNull(m.api.lireObjet("stom", "site", 5))
        assertEquals(listOf(9), fiche.enfants.getValue("visit").map { it.id })
        assertTrue(fiche.enfantsConnus)
        assertTrue(m.serveur.requetes.isEmpty())
    }

    @Test
    fun une_visite_passee_se_reconstitue_depuis_la_fiche_de_son_site() = runTest {
        m.cache.setJson(m.cache.keyObjet("stom", "site", 5), site)
        assertNull(m.api.lireObjet("stom", "visit", 9), "la synchro ne charge pas l'historique")
        val visite = assertNotNull(m.api.lireObjetDepuisParent("stom", "visit", 9, "site", 5))
        assertEquals("2026-06-01", visite.proprietes["visit_date_min"])
        assertTrue(visite.enfants.isEmpty())
        assertFalse(visite.enfantsConnus, "ses observations ne sont pas dans le cache")
        assertNull(m.api.lireObjetDepuisParent("stom", "visit", 10, "site", 5))
    }

    @Test
    fun un_objet_cree_est_reporte_une_fois_sous_le_module() = runTest {
        m.cache.setJson(m.cache.keyEnfants("stom"), """{"children":{"site":[{"id":5,"properties":{"base_site_name":"Col de Port"}}]}}""")
        val proprietes = JSONObject().put("base_site_name", "Étang de Lers")
        m.objets.reporterObjetCree("stom", "site", 6, proprietes, null, null, -1)
        // Nouvel essai du même envoi : la ligne est remplacée, pas doublée.
        m.objets.reporterObjetCree("stom", "site", 6, proprietes, null, "module", -1)
        assertEquals(listOf("Col de Port", "Étang de Lers"), m.api.lireEnfants("stom")?.getValue("site")?.map { it.nom })
        assertEquals("Étang de Lers", m.api.lireObjet("stom", "site", 6)?.proprietes?.get("base_site_name"))
    }

    @Test
    fun le_schema_est_analyse_une_fois_par_version_du_cache() = runTest {
        m.cache.setJson(m.cache.keySchema("stom"), """{"site":{"label":"Site"}}""")
        val premier = m.api.lireSchemaProtocole("stom")
        assertSame(premier, m.api.lireSchemaProtocole("stom"))
        m.cache.setJson(m.cache.keySchema("stom"), """{"site":{"label":"Point d'écoute"}}""")
        assertEquals("Point d'écoute", m.api.lireSchemaProtocole("stom")?.get("site")?.label)
    }

    @Test
    fun les_libelles_viennent_des_listes_en_cache() = runTest {
        m.cache.setJson(
            m.cache.keySchema("stom"),
            """{"module":{"id_list_observer":3},
               "site":{"generic":{
                 "id_inventor":{"type_widget":"observers","type_util":"user","attribut_label":"Inventeur"},
                 "id_nomenclature_type_site":{"type_widget":"nomenclature","type_util":"nomenclature",
                   "code_nomenclature_type":"TYPE_SITE","attribut_label":"Type"},
                 "id_dataset":{"type_widget":"dataset","type_util":"dataset","attribut_label":"Jeu"}}}}""",
        )
        val schema = assertNotNull(m.api.lireSchemaProtocole("stom"))
        val proprietes = schema.getValue("site").properties
        m.cache.setJson(m.cache.keyObservateurs(3), """[{"id_role":12,"nom_complet":"DUPONT Jean"}]""")
        m.cache.setJson(
            m.cache.keyOptionsDatalist(proprietes.getValue("id_nomenclature_type_site").apiUrl!!),
            """{"values":[{"id_nomenclature":686,"label_fr":"Point d'écoute","label_default":"Point"}]}""",
        )
        m.cache.setJson(
            m.cache.keyOptionsDatalist(proprietes.getValue("id_dataset").apiUrl!!),
            """[{"id_dataset":4,"dataset_name":"Suivi STOM"}]""",
        )
        val resolveur = m.datalists.lireResolveurLabels(m.config, schema)
        assertEquals("DUPONT Jean", resolveur.resoudre(proprietes.getValue("id_inventor"), "12"))
        assertEquals("Point d'écoute", resolveur.resoudre(proprietes.getValue("id_nomenclature_type_site"), "686"))
        assertEquals("Suivi STOM", resolveur.resoudre(proprietes.getValue("id_dataset"), "4"))
        assertTrue(m.serveur.requetes.isEmpty())
    }

    @Test
    fun les_options_d_un_formulaire_viennent_du_cache() = runTest {
        m.cache.setJson(
            m.cache.keySchema("stom"),
            """{"module":{"id_list_observer":3},"visit":{"generic":{
                 "observers":{"type_widget":"observers","type_util":"user","attribut_label":"Observateurs"},
                 "meteo":{"type_widget":"datalist","api":"nomenclatures/nomenclature/METEO","attribut_label":"Météo"}}}}""",
        )
        val proprietes = assertNotNull(m.api.lireSchemaProtocole("stom")).getValue("visit").properties
        m.cache.setJson(m.cache.keyObservateurs(3), """[{"id_role":12,"nom_complet":"DUPONT Jean"}]""")
        assertEquals(listOf("DUPONT Jean"), m.api.lireOptionsDatalist(m.config, proprietes.getValue("observers"))?.map { it.label })
        assertNull(m.api.lireOptionsDatalist(m.config, proprietes.getValue("meteo")), "rien en cache : rien de demandé")
        assertTrue(m.serveur.requetes.isEmpty())
    }
}
