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

import fr.ariegenature.geomys.network.ServeurSimule.Companion.json

/** Un GeoNature minimal mais complet : tous les appels de la synchro globale (SyncRunner) y
 *  trouvent une réponse valide — un jeu de données, une liste de taxons d'un taxon, un
 *  observateur, une nomenclature, un habitat HABREF, le module OccHab sans station, un protocole
 *  de suivi à un site. Le reste (pictogrammes…) répond 404. */
internal fun ServeurSimule.routerGeoNatureComplet(
    habitats: String = """[{"cd_hab":1,"search_name":"G1.6 - Hêtraies","lb_code":"G1.6"}]""",
) = router(
    "/api/auth/login" to { json("""{"access_token":"t","user":{"id_role":1}}""") },
    // Suivis.
    "/api/monitorings/modules" to {
        json("""[{"id_module":1,"module_code":"STOM","module_label":"STOM","cruved":{"R":1}}]""")
    },
    "/api/monitorings/config/STOM" to {
        json("""{"module":{"label":"Module","children_types":["site"]},"site":{"label":"Site","geometry_type":"Point"}}""")
    },
    "/api/monitorings/object/STOM/module" to {
        json("""{"children":{"site":[{"id":5,"properties":{"base_site_name":"Forêt de Foix"}}]}}""")
    },
    "/api/monitorings/object/STOM/site/5" to { json("""{"properties":{"base_site_name":"Forêt de Foix"},"children":{}}""") },
    // Jeux de données (GET filtré et POST des créables), listes, observateurs.
    "/api/meta/datasets" to { json("""[{"id_dataset":12,"dataset_name":"JDD test","modules":["OCCTAX","OCCHAB"]}]""") },
    "/api/taxhub/api/biblistes" to { json("""{"data":[{"id_liste":100,"nom_liste":"Faune"}]}""") },
    "/api/gn_commons/config" to { json("""{"OCCTAX":{"id_observers_list":9}}""") },
    "/api/users/menu/9" to { json("""[{"id_role":3,"nom_complet":"DUPONT Jean"}]""") },
    "/api/gn_commons/additional_fields" to { json("[]") },
    "/api/gn_commons/t_mobile_apps" to { json("""[{"app_code":"OCCTAX","settings":{}}]""") },
    // HABREF et OccHab.
    "/api/habref/habitats/autocomplete" to { json(habitats) },
    "/api/gn_commons/modules" to {
        json("""[{"module_code":"OCCTAX","cruved":{"C":1,"R":1}},{"module_code":"OCCHAB","cruved":{"C":1,"R":1}}]""")
    },
    "/api/occhab/defaultNomenclatures" to { json("{}") },
    "/api/occhab/stations/" to { json("""{"type":"FeatureCollection","features":[]}""") },
    // TaxRef : une page, plus courte que la taille de page, donc liste complète.
    "/api/taxhub/api/taxref/version" to { json("""{"version":"17"}""") },
    "/api/taxhub/api/taxref" to {
        json(
            """{"items":[{"cd_nom":4319,"lb_nom":"Muscicapa striata","nom_vern":"Gobemouche gris",
               "group2_inpn":"Oiseaux","group1_inpn":"Chordés","regne":"Animalia"}]}""",
        )
    },
    // Nomenclatures.
    "/api/nomenclatures/nomenclatures/taxonomy" to {
        json("""[{"mnemonique":"SEXE","nomenclatures":[{"id_nomenclature":1,"label_default":"Mâle"}]}]""")
    },
    "/api/occtax/defaultNomenclatures" to { json("""{"SEXE":1}""") },
)
