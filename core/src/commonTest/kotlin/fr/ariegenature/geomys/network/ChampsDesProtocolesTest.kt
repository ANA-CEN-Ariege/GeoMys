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

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.monitoring.NouvelleVisite
import fr.ariegenature.geomys.monitoring.form.construireFormulaire
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** Les champs et les options que proposent des protocoles réels (extraits du dépôt public
 *  protocoles_suivi), tels que les propose le formulaire web de gn_module_monitoring 1.3.2 :
 *  l'appli inventait des options, en ignorait les restrictions, et envoyait à vide une valeur fixe. */
class ChampsDesProtocolesTest {

    private val m = MonitoringDeTest()

    @AfterTest
    fun nettoyer() = m.nettoyer()

    private suspend fun schema(module: String, json: String): Map<String, MonitoringApi.MonitoringSchemaObjet> {
        m.cache.setJson(m.cache.keySchema(module), json)
        return m.api.lireSchemaProtocole(module)!!
    }

    private fun nomenclature(vararg items: Triple<Int, String, String>) =
        """{"mnemonique":"X","values":[""" + items.joinToString(",") { (id, cd, libelle) ->
            """{"id_nomenclature":$id,"cd_nomenclature":"$cd","label_default":"$libelle","label_fr":"$libelle"}"""
        } + "]}"

    @Test
    fun chiro_nomenclature_restreinte_a_ses_cd_nomenclatures() = runTest {
        val chiro = schema(
            "chiro",
            """{"observation":{"generic":{},"specific":{"id_nomenclature_sex":{"attribut_label":"Sexe",""" +
                """"cd_nomenclatures":["0","2","3","6"],"code_nomenclature_type":"SEXE","required":true,""" +
                """"type_util":"nomenclature","type_widget":"nomenclature",""" +
                """"value":{"cd_nomenclature":"6","code_nomenclature_type":"SEXE"}}}}}""",
        )
        val sexe = chiro.getValue("observation").properties.getValue("id_nomenclature_sex")
        // La requête du composant pnx-nomenclature : triée par libellé, restreinte à ses cd.
        assertEquals(
            "nomenclatures/nomenclature/SEXE?orderby=label_default&cd_nomenclature=0&cd_nomenclature=2&cd_nomenclature=3&cd_nomenclature=6",
            sexe.apiUrl,
        )
        val reponse = nomenclature(
            Triple(190, "0", "Inconnu"), Triple(192, "2", "Femelle"), Triple(193, "3", "Mâle"), Triple(196, "6", "Non renseigné"),
        )
        m.cache.setJson(m.cache.keyOptionsDatalist(sexe.apiUrl!!), reponse)
        assertEquals(listOf("Inconnu", "Femelle", "Mâle", "Non renseigné"), m.api.lireOptionsDatalist(m.config, sexe)?.map { it.label })

        // La `value` du schéma choisit son option par le cd_nomenclature.
        val objet = chiro.getValue("observation")
        val (champs, _) = NouvelleVisite.enrichirAvecOptions(construireFormulaire(objet).fields, objet, null, 0) { p ->
            m.api.lireOptionsDatalist(m.config, p)
        }
        assertEquals("196", champs.single().value)
    }

    @Test
    fun liste_mise_en_cache_sans_parametres_restreinte_et_triee() = runTest {
        // Cache d'une version qui ne demandait pas encore la restriction : elle s'applique ici.
        val sexe = schema(
            "chiro",
            """{"observation":{"specific":{"id_nomenclature_sex":{"cd_nomenclatures":["2","3"],""" +
                """"code_nomenclature_type":"SEXE","type_util":"nomenclature","type_widget":"nomenclature"}}}}""",
        ).getValue("observation").properties.getValue("id_nomenclature_sex")
        m.cache.setJson(
            m.cache.keyOptionsDatalist("nomenclatures/nomenclature/SEXE"),
            nomenclature(Triple(193, "3", "Mâle"), Triple(190, "0", "Inconnu"), Triple(192, "2", "Femelle")),
        )
        assertEquals(listOf("Femelle", "Mâle"), m.api.lireOptionsDatalist(m.config, sexe)?.map { it.label })
    }

    @Test
    fun rhomeo_odonate_filtres_sur_label_fr_dans_leur_ordre() = runTest {
        val comportement = schema(
            "RHOMEOOdonate",
            """{"observation":{"specific":{"id_nomenclature_behaviour":{"api":"nomenclatures/nomenclature/OCC_COMPORTEMENT",""" +
                """"application":"GeoNature","attribut_label":"Type de comportement reproducteur","data_path":"values",""" +
                """"default":null,"filters":{"label_fr":["Non renseigné","Tandem","Territorial","Pond","Accouplement"]},""" +
                """"keyLabel":"label_fr","keyValue":"id_nomenclature","multiple":true,""" +
                """"params":{"group2_inpn":"Insectes","regne":"Animalia"},"type_util":"nomenclature","type_widget":"datalist"}}}}""",
        ).getValue("observation").properties.getValue("id_nomenclature_behaviour")
        // Les `params` partent avec la requête (getDataList du web).
        assertEquals("nomenclatures/nomenclature/OCC_COMPORTEMENT?group2_inpn=Insectes&regne=Animalia", comportement.apiUrl)
        m.cache.setJson(
            m.cache.keyOptionsDatalist(comportement.apiUrl!!),
            nomenclature(
                Triple(1, "1", "Accouplement"), Triple(2, "2", "Alimentation"), Triple(3, "3", "Pond"),
                Triple(4, "4", "Tandem"), Triple(5, "5", "Territorial"), Triple(0, "0", "Non renseigné"),
            ),
        )
        // Seules les valeurs du filtre, dans son ordre : « Alimentation » n'est pas proposée.
        assertEquals(
            listOf("Non renseigné", "Tandem", "Territorial", "Pond", "Accouplement"),
            m.api.lireOptionsDatalist(m.config, comportement)?.map { it.label },
        )
    }

    @Test
    fun nidif_gypa_cd_nom_fixe_envoye_avec_l_observation() = runTest {
        val observation = schema(
            "nidif_gypa",
            """{"observation":{"generic":{"id_base_visit":{"type_widget":"number","hidden":true},""" +
                """"cd_nom":{"type_widget":"taxonomy","attribut_label":"Espèce","type_util":"taxonomy","required":true}},""" +
                """"specific":{"cd_nom":{"type_widget":"text","type_util":"taxonomy","hidden":true,"value":2852}}}}""",
        ).getValue("observation")
        // Le champ espèce, masqué, n'est pas proposé…
        assertEquals(emptyList(), construireFormulaire(observation).fields.map { it.code })
        // … mais son cd_nom part avec la saisie, en nombre.
        assertEquals(mapOf<String, Any>("cd_nom" to 2852), NouvelleVisite.valeursFixes(observation, "id_base_visit") { null })
    }

    @Test
    fun nomenclature_d_un_champ_texte_masque_lue_par_le_type_de_sa_valeur() = runTest {
        // suivi_nardaie : la technique de collecte fixe, dans un champ texte masqué.
        val visite = schema(
            "suivi_nardaie",
            """{"visit":{"specific":{"id_nomenclature_tech_collect_campanule":{"type_widget":"text",""" +
                """"type_util":"nomenclature","hidden":true,""" +
                """"value":{"code_nomenclature_type":"TECHNIQUE_OBS","cd_nomenclature":"133"}}}}}""",
        ).getValue("visit")
        val technique = visite.properties.getValue("id_nomenclature_tech_collect_campanule")
        assertEquals("nomenclatures/nomenclature/TECHNIQUE_OBS", technique.apiUrl)
        m.cache.setJson(m.cache.keyOptionsDatalist(technique.apiUrl!!), nomenclature(Triple(500, "133", "Vu"), Triple(501, "134", "Entendu")))
        assertEquals(
            mapOf<String, Any>("id_nomenclature_tech_collect_campanule" to 500),
            NouvelleVisite.valeursFixes(visite, null) { p -> m.api.lireOptionsDatalist(m.config, p) },
        )
    }

    @Test
    fun regle_change_donnee_en_une_seule_chaine() = runTest {
        val visite = schema(
            "x",
            """{"visit":{"change":"({objForm}) => {\nobjForm.patchValue({a: 1})\n}","specific":{"a":{"type_widget":"integer"}}}}""",
        ).getValue("visit")
        assertEquals(listOf("({objForm}) => {", "objForm.patchValue({a: 1})", "}"), visite.changeRules)
    }
}
