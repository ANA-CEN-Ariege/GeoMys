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

// Essai d'affichage du lot 7, lancé par le paramètre caché `action=monitoring-essai` de GeoMysApp, sur
// l'émulateur et le simulateur seulement : trois protocoles fictifs, leurs schémas, des sites, des visites et
// des observations, les observateurs et le jeu de données du protocole STOM, écrits dans le cache des Suivis
// comme après une synchro. Sans serveur joignable, l'authentification échoue et chaque écran retombe sur ce
// cache.

package fr.ariegenature.geomys.outils

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.MediaSuivi
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.util.maintenantMillis

/** Écrit dans le cache des Suivis (`MonitoringCache`) les réponses d'un serveur fictif, et met une
 *  visite du Col de Port en attente d'envoi. */
suspend fun ServicesGeoNature.ajouterMonitoringEssai() {
    val cache = monitoringCache
    cache.setJson(cache.keyModules(), MODULES)
    cache.setJson(cache.keySchema("stom_ariege"), SCHEMA_STOM)
    cache.setJson(cache.keyEnfants("stom_ariege"), ENFANTS_STOM)
    cache.setJson(cache.keyObjet("stom_ariege", "site", 501), SITE_COL_DE_PORT)
    cache.setJson(cache.keyObjet("stom_ariege", "visit", 9001), VISITE_COL_DE_PORT)
    cache.setJson(cache.keySchema("mares_ana"), SCHEMA_MARES)
    cache.setJson(cache.keyEnfants("mares_ana"), ENFANTS_MARES)
    cache.setJson(cache.keySchema("chiro_gites"), SCHEMA_CHIRO)
    cache.setJson(cache.keyEnfants("chiro_gites"), """{"children":{"site":[]}}""")
    cache.setJson(cache.keyObservateurs(1), OBSERVATEURS)
    cache.setJson(cache.keyOptionsDatalist("meta/datasets?module_code=stom_ariege&active=true"), JEUX_STOM)
    // Les droits de création (`/api/gn_commons/modules`) : visites et observations du STOM, mais pas
    // celles des mares, dont le « + » est grisé comme sur le web.
    monitoring.enregistrerDroits(DROITS)
    // La liste taxonomique du protocole STOM (id_list_taxonomy 100) : les oiseaux du référentiel
    // d'essai du lot 6 (`action=taxref-essai`, à lancer AVANT : il vide le cache).
    taxRef.ajouterListesParCdNom(OISEAUX_ESSAI.associateWith { listOf(LISTE_STOM) })
    // Identifiant fixe : relancer l'essai remplace la visite au lieu d'en ajouter une autre.
    outbox.supprimer(VISITE_EN_ATTENTE)
    outbox.ajouter(
        SaisieEnAttente(
            uuid = VISITE_EN_ATTENTE,
            moduleCode = "stom_ariege",
            objectType = "visit",
            parentObjectType = "site",
            parentIdServeur = 501,
            parentIdField = "id_base_site",
            nomsChampsSchema = listOf("visit_date_min", "heure_debut", "meteo", "vent", "comments"),
            valeursJson = """{"visit_date_min":"2026-10-19","heure_debut":"07:05","meteo":"Beau","vent":"Nul"}""",
            dateLocale = maintenantMillis() - 3_600_000L,
            // Enregistrée sans sa température de fin : « à compléter ».
            champsManquants = listOf("Température de fin (°C)"),
            // Une photo sans titre : le formulaire média du web l'exige.
            mediaPathsLocal = listOf(PHOTO_ESSAI),
            mediaSchemaDotTable = "gn_monitoring.t_base_visits",
            mediasSuivis = listOf(MediaSuivi(PHOTO_ESSAI, champ = "medias", schemaDotTable = "gn_monitoring.t_base_visits")),
        ),
    )
}

private const val VISITE_EN_ATTENTE = "essai-visite-col-de-port"

private const val PHOTO_ESSAI = "file:///essai/col-de-port.jpg"

private val DROITS = """
[{"module_code":"stom_ariege","cruved":{"C":0,"R":3},"objects":[{"code_object":"MONITORINGS_VISITES","cruved":{"C":3,"R":3}}]},
 {"module_code":"mares_ana","cruved":{"C":0,"R":3},"objects":[{"code_object":"MONITORINGS_VISITES","cruved":{"C":0,"R":3}}]},
 {"module_code":"chiro_gites","cruved":{"C":0,"R":1}}]
""".trimIndent()

private const val LISTE_STOM = 100

/** Les oiseaux de TaxRefEssai. */
private val OISEAUX_ESSAI = listOf(4117, 4001, 534742, 3764, 3603, 3611, 2623)

private val OBSERVATEURS = """
[{"id_role":3,"nom_role":"DUPONT","prenom_role":"Alice","nom_complet":"DUPONT Alice"},
 {"id_role":7,"nom_role":"MARTIN","prenom_role":"Paul","nom_complet":"MARTIN Paul"},
 {"id_role":12,"nom_role":"ROUX","prenom_role":"Inès","nom_complet":"ROUX Inès"}]
""".trimIndent()

private val JEUX_STOM = """[{"id_dataset":12,"dataset_name":"STOM Ariège 2026"}]"""

private val MODULES = """
[
  {"id_module":101,"module_code":"stom_ariege","module_label":"STOM Ariège",
   "module_desc":"Suivi temporel des oiseaux de montagne : points d'écoute de dix minutes.",
   "module_picto":"fa-bird","id_list_observer":1,"id_list_taxonomy":100,"cruved":{"C":0,"R":3,"U":0,"V":0,"E":3,"D":0}},
  {"id_module":102,"module_code":"mares_ana","module_label":"Suivi des mares",
   "module_desc":"Amphibiens et végétation aquatique des mares de l'Ariège.",
   "module_picto":"fa-water","id_list_observer":1,"cruved":{"C":0,"R":3,"U":0,"V":0,"E":3,"D":0}},
  {"id_module":103,"module_code":"chiro_gites","module_label":"Chiroptères en gîtes","cruved":{"R":1}}
]
""".trimIndent()

private val SCHEMA_STOM = """
{
  "module":{"label":"Protocole","children_types":["site"],"id_list_observer":1,"id_list_taxonomy":100},
  "site":{
    "label":"Point d'écoute","label_list":"Points d'écoute","genre":"M",
    "description_field_name":"base_site_name","id_field_name":"id_base_site","geometry_type":"Point",
    "parent_types":["module"],"children_types":["visit"],
    "display_properties":["base_site_name","base_site_code","altitude_min","base_site_description"],
    "display_list":["base_site_name","base_site_code"],
    "generic":{
      "id_base_site":{"type_widget":"text","hidden":true},
      "base_site_name":{"type_widget":"text","attribut_label":"Nom","required":true},
      "base_site_code":{"type_widget":"text","attribut_label":"Code"},
      "base_site_description":{"type_widget":"textarea","attribut_label":"Description"},
      "altitude_min":{"type_widget":"integer","attribut_label":"Altitude"}
    }
  },
  "visit":{
    "label":"Visite","label_list":"Visites","genre":"F","id_field_name":"id_base_visit","uuid_field_name":"uuid_base_visit",
    "parent_types":["site"],"children_types":["observation"],"chained":true,
    "display_properties":["visit_date_min","heure_debut","meteo","vent","comments"],
    "display_list":["visit_date_min","meteo"],
    "sorts":[{"prop":"visit_date_min","dir":"desc"}],
    "generic":{
      "id_base_visit":{"type_widget":"text","hidden":true},
      "id_base_site":{"type_widget":"text","hidden":true},
      "visit_date_min":{"type_widget":"date","attribut_label":"Date de la visite","required":true},
      "id_dataset":{"type_widget":"dataset","attribut_label":"Jeu de données","required":true},
      "observers":{"type_widget":"observers","attribut_label":"Observateurs","type_util":"user","multiple":true,"required":true},
      "comments":{"type_widget":"textarea","attribut_label":"Commentaire"}
    },
    "specific":{
      "heure_debut":{"type_widget":"time","attribut_label":"Heure de début"},
      "temperature_fin":{"type_widget":"number","attribut_label":"Température de fin (°C)","required":true,
        "definition":"Relevée à la fin des dix minutes d'écoute."},
      "meteo":{"type_widget":"select","attribut_label":"Météo","values":["Beau","Nuageux","Pluie"]},
      "vent":{"type_widget":"radio","attribut_label":"Vent","values":["Nul","Faible","Fort"]},
      "medias":{"type_widget":"medias","attribut_label":"Photos","schema_dot_table":"gn_monitoring.t_base_visits"}
    }
  },
  "observation":{
    "label":"Observation","label_list":"Observations","genre":"F","id_field_name":"id_observation",
    "uuid_field_name":"uuid_observation","parent_types":["visit"],"chained":true,"keep":["distance"],"redirect_to_parent":true,
    "display_properties":["cd_nom","count","distance","comments"],
    "display_list":["cd_nom","count"],
    "generic":{
      "id_observation":{"type_widget":"text","hidden":true},
      "id_base_visit":{"type_widget":"text","hidden":true},
      "cd_nom":{"type_widget":"taxonomy","attribut_label":"Espèce","required":true,"type_util":"taxonomy"},
      "comments":{"type_widget":"textarea","attribut_label":"Commentaire"}
    },
    "specific":{
      "count":{"type_widget":"number","attribut_label":"Effectif","min":1,"required":true},
      "distance":{"type_widget":"radio","attribut_label":"Distance","values":["< 25 m","25 à 100 m","> 100 m"]},
      "medias":{"type_widget":"medias","attribut_label":"Photos","schema_dot_table":"gn_monitoring.t_observations"}
    }
  }
}
""".trimIndent()

private val ENFANTS_STOM = """
{"children":{"site":[
  {"id":501,"properties":{"id_base_site":501,"base_site_name":"Col de Port","base_site_code":"STOM-01","altitude_min":1249},
   "geometry":{"type":"Point","coordinates":[1.4987,42.8770]}},
  {"id":502,"properties":{"id_base_site":502,"base_site_name":"Plateau de Beille","base_site_code":"STOM-02","altitude_min":1790},
   "geometry":{"type":"Point","coordinates":[1.7006,42.7189]}},
  {"id":503,"properties":{"id_base_site":503,"base_site_name":"Étang de Lers","base_site_code":"STOM-03","altitude_min":1270},
   "geometry":{"type":"Point","coordinates":[1.3870,42.8078]}}
]}}
""".trimIndent()

private val SITE_COL_DE_PORT = """
{"properties":{"id_base_site":501,"base_site_name":"Col de Port","base_site_code":"STOM-01","altitude_min":1249,
  "base_site_description":"Point d'écoute en lisière de hêtraie-sapinière, côté Massat."},
 "children":{"visit":[
   {"id":9001,"properties":{"id_base_visit":9001,"id_base_site":501,"visit_date_min":"2026-06-12","heure_debut":"06:15","meteo":"Beau","vent":"Faible"}},
   {"id":9002,"properties":{"id_base_visit":9002,"id_base_site":501,"visit_date_min":"2025-06-18","heure_debut":"06:40","meteo":"Nuageux","vent":"Nul"}}
 ]},
 "geometry":{"type":"Point","coordinates":[1.4987,42.8770]}}
""".trimIndent()

private val VISITE_COL_DE_PORT = """
{"properties":{"id_base_visit":9001,"id_base_site":501,"visit_date_min":"2026-06-12","heure_debut":"06:15","meteo":"Beau",
  "vent":"Faible","comments":"Brouillard léger au lever du jour."},
 "children":{"observation":[
   {"id":70001,"properties":{"id_observation":70001,"id_base_visit":9001,"cd_nom":4117,"count":2,"distance":"< 25 m"}},
   {"id":70002,"properties":{"id_observation":70002,"id_base_visit":9001,"cd_nom":3764,"count":1,"distance":"25 à 100 m"}}
 ]}}
""".trimIndent()

private val SCHEMA_MARES = """
{
  "module":{"label":"Protocole","children_types":["site"],"id_list_observer":1},
  "site":{
    "label":"Mare","label_list":"Mares","genre":"F",
    "description_field_name":"base_site_name","id_field_name":"id_base_site","geometry_type":"Polygon",
    "parent_types":["module"],"children_types":["visit"],
    "display_properties":["base_site_name","base_site_code","surface"],
    "display_list":["base_site_name","surface"],
    "generic":{
      "id_base_site":{"type_widget":"text","hidden":true},
      "base_site_name":{"type_widget":"text","attribut_label":"Nom","required":true},
      "base_site_code":{"type_widget":"text","attribut_label":"Code"}
    },
    "specific":{"surface":{"type_widget":"number","attribut_label":"Surface (m²)"}}
  },
  "visit":{
    "label":"Passage","label_list":"Passages","genre":"M","id_field_name":"id_base_visit",
    "parent_types":["site"],
    "display_properties":["visit_date_min","comments"],
    "display_list":["visit_date_min"],
    "generic":{
      "id_base_visit":{"type_widget":"text","hidden":true},
      "id_base_site":{"type_widget":"text","hidden":true},
      "visit_date_min":{"type_widget":"date","attribut_label":"Date du passage","required":true},
      "comments":{"type_widget":"textarea","attribut_label":"Commentaire"}
    }
  }
}
""".trimIndent()

private val ENFANTS_MARES = """
{"children":{"site":[
  {"id":601,"properties":{"id_base_site":601,"base_site_name":"Mare des Escoumes","base_site_code":"MAR-12","surface":85},
   "geometry":{"type":"Polygon","coordinates":[[[1.5650,42.9601],[1.5658,42.9603],[1.5660,42.9597],[1.5652,42.9595],[1.5650,42.9601]]]}},
  {"id":602,"properties":{"id_base_site":602,"base_site_name":"Mare du Pla","base_site_code":"MAR-27","surface":140},
   "geometry":{"type":"Polygon","coordinates":[[[1.6011,42.9405],[1.6022,42.9408],[1.6025,42.9399],[1.6014,42.9396],[1.6011,42.9405]],
     [[1.6016,42.9403],[1.6019,42.9403],[1.6019,42.9401],[1.6016,42.9401],[1.6016,42.9403]]]}}
]}}
""".trimIndent()

private val SCHEMA_CHIRO = """
{
  "module":{"label":"Protocole","children_types":["site"]},
  "site":{"label":"Gîte","label_list":"Gîtes","genre":"M","description_field_name":"base_site_name",
    "id_field_name":"id_base_site","geometry_type":"Point","parent_types":["module"],"children_types":[],
    "generic":{"base_site_name":{"type_widget":"text","attribut_label":"Nom","required":true}}}
}
""".trimIndent()
