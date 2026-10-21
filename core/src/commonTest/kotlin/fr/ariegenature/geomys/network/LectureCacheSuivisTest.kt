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

    @Test
    fun groupes_de_sites_observateurs_des_champs_et_champs_affiches_viennent_du_cache() = runTest {
        // 21 octobre : les identifiants affichés comme sur le web (display_field_names).
        m.cache.setJson(
            m.cache.keySchema("stom"),
            """{"module":{"id_list_observer":3,"children_types":["sites_group","site"]},
               "display_field_names":{"taxonomy":"lb_nom","sites_group":"sites_group_name","area":null},
               "site":{"generic":{
                 "id_sites_group":{"type_widget":"datalist","type_util":"sites_group","attribut_label":"Groupe",
                   "api":"monitorings/list/stom/sites_group?id_module=18","keyValue":"id_sites_group","keyLabel":"sites_group_name"}}},
               "visit":{"generic":{
                 "observers":{"type_widget":"datalist","type_util":"user","attribut_label":"Observateurs",
                   "api":"users/menu/7","keyValue":"id_role","keyLabel":"nom_complet","multiple":true}}}}""",
        )
        val schema = assertNotNull(m.api.lireSchemaProtocole("stom"))
        assertEquals(
            mapOf("taxonomy" to "lb_nom", "sites_group" to "sites_group_name"),
            schema.getValue("module").champsAffichage,
            "une valeur nulle laisse le défaut",
        )
        m.cache.setJson(m.cache.keyEnfants("stom"), """{"children":{"sites_group":[{"id":21,"properties":{"sites_group_name":"Vallée d'Aston"}}]}}""")
        m.cache.setJson(m.cache.keyObservateurs(7), """[{"id_role":15,"nom_complet":"ROUX Léa"}]""")
        val resolveur = m.datalists.lireResolveurLabels(m.config, schema, "stom")
        assertEquals("Vallée d'Aston", resolveur.resoudre(schema.getValue("site").properties.getValue("id_sites_group"), "21"))
        assertEquals("ROUX Léa", resolveur.resoudre(schema.getValue("visit").properties.getValue("observers"), "15"), "la liste propre au champ")
        assertTrue(m.serveur.requetes.isEmpty())
    }

    @Test
    fun les_champs_des_types_de_site_viennent_du_cache() = runTest {
        // L'onglet « Propriétés spécifiques » du web (addSpecificConfig).
        m.cache.setJson(
            m.cache.keySchema("arbres"),
            """{"site":{"generic":{"types_site":{"type_widget":"datalist","type_util":"types_site","attribut_label":"Types",
                 "api":"monitorings/modules/arbres/types_sites","keyValue":"id_nomenclature_type_site","keyLabel":"label","multiple":true}}}}""",
        )
        val site = assertNotNull(m.api.lireSchemaProtocole("arbres")).getValue("site")
        m.cache.setJson(
            m.cache.keyOptionsDatalist(site.properties.getValue("types_site").apiUrl!!),
            """{"count":2,"items":[
                {"id_nomenclature_type_site":580,"label":"Arbre","config":{"specific":{
                   "essence":{"type_widget":"text","attribut_label":"Essence"},
                   "aide":{"type_widget":"html","html":"<b>Aide</b>"}}}},
                {"id_nomenclature_type_site":581,"label":"Mare","config":{"specific":{
                   "profondeur":{"type_widget":"number","attribut_label":"Profondeur"}}}}]}""",
        )
        val champs = m.datalists.lireChampsTypesSite(site, listOf("580"))
        assertEquals(listOf("essence"), champs.keys.toList(), "le html exclu, le type 581 n'est pas celui du site")
        assertEquals("Essence", champs.getValue("essence").label)
        assertTrue(m.datalists.lireChampsTypesSite(site, emptyList()).isEmpty())
    }
}
