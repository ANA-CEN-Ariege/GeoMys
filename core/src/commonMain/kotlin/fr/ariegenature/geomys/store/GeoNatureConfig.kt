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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/GeoNatureConfig.kt) :
// SharedPreferences remplacées par multiplatform-settings (mêmes clés, mêmes défauts), MdpChiffre
// par CoffreMotDePasse, org.json par son portage. Retirés : le nettoyage des anciens coffres
// Android (sans objet, GeoMys Common ne reprend pas les données).

package fr.ariegenature.geomys.store

import com.russhwolf.settings.Settings
import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureListe
import fr.ariegenature.geomys.store.io.listeDepuisJson
import kotlin.concurrent.Volatile

/** Les clés de `OCCTAX.form_fields` que le schéma du serveur masque par défaut (FormConfig de
 *  conf_schema_toml.py) ; toutes les autres sont affichées par défaut. */
private val CHAMPS_MASQUES_PAR_DEFAUT = setOf(
    "depth_min", "depth_max", "tech_collect", "group_type", "blurring", "source_status",
    "geo_object_nature", "grp_method", "place_name", "precision",
)

/** Le champ de formulaire serveur [key] (clé de `OCCTAX.form_fields`, ex. `group_type`) est-il
 *  visible d'après [formFieldsJson] ? Si la clé est absente ou la config non publiée, le défaut du
 *  schéma du serveur, comme le web. Partagé entre [GeoNatureConfig] et l'UI (DetailsReleveDialog). */
fun champFormVisible(formFieldsJson: String, key: String): Boolean {
    val defaut = key !in CHAMPS_MASQUES_PAR_DEFAUT
    return try {
        if (formFieldsJson.isBlank()) defaut
        else JSONObject(formFieldsJson).optBoolean(key, defaut)
    } catch (_: Exception) { defaut }
}

/** Les réglages GeoNature (fichier `gn_config` de GeoMys-Android) : connexion, sélections,
 *  caches JSON de la configuration du serveur, et règles de validité de la saisie.
 *
 *  [prefs] : les réglages de la plateforme (SharedPreferences `gn_config` sur Android,
 *  NSUserDefaults sur iOS). [coffre] garde le mot de passe. */
class GeoNatureConfig(private val prefs: Settings, private val coffre: CoffreMotDePasse) {

    var urlServeur: String
        get() = prefs.getString("gn_url", "")
        set(v) = prefs.putString("gn_url", v)

    var login: String
        get() = prefs.getString("gn_login", "")
        set(v) = prefs.putString("gn_login", v)

    /** Mot de passe. Gardé par le [coffre] (Keystore Android, trousseau iOS) si possible ; sinon
     *  gardé EN MÉMOIRE seulement (jamais en clair sur disque) — perdu au redémarrage du process,
     *  l'utilisateur le re-saisit. [motDePasseMemoire] est process-wide, comme sous Android. */
    var motDePasse: String
        get() = coffre.lire()?.takeIf { it.isNotEmpty() }
            ?: motDePasseMemoire ?: ""
        set(v) {
            when {
                v.isEmpty() -> { coffre.effacer(); motDePasseMemoire = null }
                coffre.ecrire(v) -> motDePasseMemoire = null
                else -> motDePasseMemoire = v // coffre KO → mémoire seule, jamais de clair.
            }
        }

    /** Le jeu de données par défaut des Paramètres, obligatoire mais sans valeur au départ :
     *  l'utilisateur le choisit. Lire [idDatasetParDefaut], qui écarte un jeu qui n'est plus
     *  proposé. */
    var idDataset: String
        get() = prefs.getString("gn_dataset", "")
        set(v) = prefs.putString("gn_dataset", v)

    /** Nom du jeu de données sélectionné (affiché dans Config GeoNature à la place de l'id). */
    var nomDataset: String
        get() = prefs.getString("gn_dataset_nom", "")
        set(v) = prefs.putString("gn_dataset_nom", v)

    /** La liste de taxons par défaut des Paramètres (id), obligatoire mais sans valeur au départ
     *  (cf. [listeParDefautValide]). Elle sert aux relevés dont le jeu de données n'impose pas la
     *  sienne (cf. [listeTaxonsOcctax]). */
    var taxaListeId: String
        get() = prefs.getString("gn_taxa_liste", "")
        set(v) = prefs.putString("gn_taxa_liste", v)

    /** L'observateur par défaut des Paramètres (id_role et nom complet), obligatoire : l'utilisateur
     *  connecté au départ, modifiable. Il est l'observateur et le déterminateur d'un nouveau relevé ;
     *  vide, l'utilisateur connecté. */
    var observateurDefautId: String
        get() = prefs.getString("gn_obs_defaut_id", "")
        set(v) = prefs.putString("gn_obs_defaut_id", v)

    var observateurDefautNom: String
        get() = prefs.getString("gn_obs_defaut_nom", "")
        set(v) = prefs.putString("gn_obs_defaut_nom", v)

    /** L'observateur d'un nouveau relevé : celui des Paramètres, sinon l'utilisateur connecté,
     *  comme le web ; null s'il n'est pas connu. */
    val idObservateurParDefaut: Int?
        get() = observateurDefautId.trim().toIntOrNull()?.takeIf { it > 0 } ?: idRoleUtilisateur.takeIf { it > 0 }

    /** Le nom complet de [idObservateurParDefaut] : celui de l'observateur des Paramètres, sinon
     *  celui de l'utilisateur connecté (« NOM Prénom »), sinon son identifiant. Le déterminateur et
     *  le texte des observateurs d'un nouveau relevé. */
    val nomObservateurParDefaut: String
        get() = observateurDefautNom.takeIf { observateurDefautId.trim().toIntOrNull()?.let { id -> id > 0 } == true && it.isNotBlank() }
            ?: nomUtilisateur.ifEmpty { login }

    /** Nom complet (« NOM Prénom », le `nom_complet` du web) renvoyé par GeoNature lors de la
     *  dernière connexion réussie, mis à jour par le test de connexion et la synchro : le
     *  déterminateur par défaut et le texte des observateurs, comme sur le web. */
    var nomUtilisateur: String
        get() = prefs.getString("gn_nom_utilisateur", "")
        set(v) = prefs.putString("gn_nom_utilisateur", v)

    /** id_role de l'utilisateur actuellement connecté (renvoyé par GeoNature au login).
     *  Sert à pré-sélectionner cet utilisateur dans les champs observers/datalist de saisie.
     *  -1 si pas encore connecté ou non détecté. */
    var idRoleUtilisateur: Int
        get() = prefs.getInt("gn_id_role_utilisateur", -1)
        set(v) = prefs.putInt("gn_id_role_utilisateur", v)

    /** `id_organisme` de l'utilisateur connecté (renvoyé au login) : le web demande avec lui les
     *  défauts de nomenclature d'OccTax (`defaultNomenclatures?organism=`). -1 s'il est inconnu. */
    var idOrganisme: Int
        get() = prefs.getInt("gn_id_organisme", -1)
        set(v) = prefs.putInt("gn_id_organisme", v)

    /** Version de l'instance GeoNature relevée au dernier test de connexion réussi
     *  (`/api/gn_commons/config`, best-effort). Vide si inconnue. Affichée dans Paramètres
     *  pour diagnostiquer les comportements propres à une version serveur. */
    var versionGeoNatureServeur: String
        get() = prefs.getString("gn_version_serveur", "")
        set(v) = prefs.putString("gn_version_serveur", v)

    /** Version des DONNÉES synchronisées présentes sur l'appareil (cf. [VERSION_DONNEES_REQUISE]).
     *  Écrite par SyncRunner en fin de synchro (TaxRef chargé) ; 0 = jamais synchronisé depuis
     *  l'introduction du mécanisme (v1.3.17). */
    var versionDonneesChargees: Int
        get() = prefs.getInt("gn_version_donnees_chargees", 0)
        set(v) = prefs.putInt("gn_version_donnees_chargees", v)

    /** true si le module OccHab est installé sur le serveur ET accessible à l'utilisateur
     *  (au moins un droit CRUVED). Détecté à la synchro via `GET /api/gn_commons/modules`.
     *  Conditionne l'affichage de la tuile OccHab sur l'accueil (comme Monitoring). */
    var occhabDisponible: Boolean
        get() = prefs.getBoolean("gn_occhab_dispo", false)
        set(v) = prefs.putBoolean("gn_occhab_dispo", v)

    /** true si l'utilisateur a le droit de CRÉER des stations OccHab (cruved C > 0). Gate le
     *  bouton d'envoi ; false → consultation seule. */
    var occhabPeutCreer: Boolean
        get() = prefs.getBoolean("gn_occhab_creer", false)
        set(v) = prefs.putBoolean("gn_occhab_creer", v)

    /** true si l'utilisateur a le droit de CRÉER des relevés Occtax (cruved C > 0 du module
     *  OCCTAX, détecté à la synchro comme pour OccHab). Gate le bouton d'envoi de « Mes
     *  saisies » — sans droit, le POST partirait en 403. Défaut TRUE (contrairement à OccHab) :
     *  l'app repose sur Occtax, on ne verrouille jamais l'envoi sur un serveur qui ne publie
     *  pas ses modules — le serveur refusera lui-même le cas échéant. */
    var occtaxPeutCreer: Boolean
        get() = prefs.getBoolean("gn_occtax_creer", true)
        set(v) = prefs.putBoolean("gn_occtax_creer", v)

    /** Id de la liste HABREF du module OccHab (`OCCHAB.ID_LIST_HABITAT`), lue à la synchro.
     *  -1 = non configurée → tout le référentiel HABREF. Restreint l'autocomplétion habitat
     *  OccHab à la même liste que le web. */
    var occhabIdListHabitat: Int
        get() = prefs.getInt("gn_occhab_id_list_habitat", -1)
        set(v) = prefs.putInt("gn_occhab_id_list_habitat", v)

    /** Dernières « informations obligatoires » OccHab validées (JSON OccHabDetailsSession) :
     *  pré-remplit le formulaire du RELEVÉ SUIVANT (JDD, observateurs, nomenclatures — les
     *  dates repartent du jour). Écrit à chaque validation du formulaire. */
    var occhabDetailsPrecedentsJson: String
        get() = prefs.getString("gn_occhab_details_prec", "")
        set(v) = prefs.putString("gn_occhab_details_prec", v)

    /** `OCCHAB.formConfig` (map champ→booléen de visibilité) sérialisé, lu à la synchro. Pilote
     *  quels champs afficher dans le formulaire OccHab, exactement comme le web. */
    var occhabFormConfigJson: String
        get() = prefs.getString("gn_occhab_formconfig", "")
        set(v) = prefs.putString("gn_occhab_formconfig", v)

    /** Visibilité d'un champ du formulaire OccHab (`OCCHAB.formConfig[champ]`). Si la config est
     *  absente ou le champ non listé, le défaut du schéma du serveur (FormConfig) : affiché, sauf le
     *  recouvrement. */
    fun occhabChampVisible(champ: String): Boolean {
        val defaut = champ != "recovery_percentage"
        val json = occhabFormConfigJson
        if (json.isBlank()) return defaut
        return try { JSONObject(json).optBoolean(champ, defaut) } catch (_: Exception) { defaut }
    }

    /** `OCCHAB.OBSERVER_AS_TXT`, lu à la synchro : les observateurs d'une station se saisissent en
     *  texte libre (`observers_txt`) au lieu d'être choisis dans une liste, comme sur le web. */
    var occhabObservateursEnTexte: Boolean
        get() = prefs.getBoolean("gn_occhab_observateurs_texte", false)
        set(v) = prefs.putBoolean("gn_occhab_observateurs_texte", v)

    /** Cache JSON des observateurs proposés par OccHab (List<GeoNatureObservateur>) : la liste
     *  `OCCHAB.OBSERVER_LIST_ID` du serveur, qui peut différer de celle d'OccTax. */
    var observateursOcchabCacheJson: String
        get() = prefs.getString("gn_cache_obs_occhab", "")
        set(v) = prefs.putString("gn_cache_obs_occhab", v)

    /** Valeurs par défaut des nomenclatures OccHab (`/occhab/defaultNomenclatures`), map
     *  mnémonique→id_nomenclature sérialisée. Pré-remplit les sélecteurs d'un NOUVEL habitat
     *  (ex. technique de collecte = « In situ »), comme le web. */
    var occhabDefautsNomencJson: String
        get() = prefs.getString("gn_occhab_defauts_nomenc", "")
        set(v) = prefs.putString("gn_occhab_defauts_nomenc", v)

    /** Id_nomenclature par défaut OccHab pour un mnémonique. null si inconnu. */
    fun occhabDefautNomenclature(mnemonique: String): Int? {
        val json = occhabDefautsNomencJson
        if (json.isBlank()) return null
        return try { JSONObject(json).optInt(mnemonique, -1).takeIf { it > 0 } } catch (_: Exception) { null }
    }

    /** Cache JSON des datasets chargés depuis le serveur (List<GeoNatureDataset>). */
    var datasetsCacheJson: String
        get() = prefs.getString("gn_cache_datasets", "")
        set(v) = prefs.putString("gn_cache_datasets", v)

    /** Cache JSON des listes de taxons chargées (List<GeoNatureListe>). */
    var listesCacheJson: String
        get() = prefs.getString("gn_cache_listes", "")
        set(v) = prefs.putString("gn_cache_listes", v)

    /** IDs des jeux de données CRÉABLES en Occtax (CRUVED C) — pour ne proposer en saisie que
     *  ceux-là, comme le web. Vide ⇒ pas de restriction (filet : offline / endpoint indisponible). */
    var datasetsCreablesOcctax: Set<Int>
        get() = prefs.getString("gn_cache_ds_creables", "")
            .split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        set(v) = prefs.putString("gn_cache_ds_creables", v.joinToString(","))

    /** Cache JSON des datasets rattachés au module OccHab (List<GeoNatureDataset>). Distinct du
     *  cache OCCTAX : le périmètre de jeux de données diffère d'un module à l'autre côté serveur. */
    var datasetsOcchabCacheJson: String
        get() = prefs.getString("gn_cache_datasets_occhab", "")
        set(v) = prefs.putString("gn_cache_datasets_occhab", v)

    /** IDs des jeux de données CRÉABLES en OccHab (CRUVED C). Vide ⇒ pas de restriction. */
    var datasetsCreablesOcchab: Set<Int>
        get() = prefs.getString("gn_cache_ds_creables_occhab", "")
            .split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        set(v) = prefs.putString("gn_cache_ds_creables_occhab", v.joinToString(","))

    /** Cache JSON des observateurs chargés (List<GeoNatureObservateur>). */
    var observateursCacheJson: String
        get() = prefs.getString("gn_cache_obs", "")
        set(v) = prefs.putString("gn_cache_obs", v)

    /** Cache JSON des champs additionnels Occtax (List<AdditionalFieldDef>) — tous niveaux confondus. */
    var additionalFieldsOcctaxJson: String
        get() = prefs.getString("gn_cache_add_fields_occtax", "")
        set(v) = prefs.putString("gn_cache_add_fields_occtax", v)

    /** Cache JSON de l'objet `nomenclature` du settings.json Occtax du serveur (sections
     *  information[]/counting[]), récupéré via /api/gn_commons/t_mobile_apps. Pilote la visibilité
     *  des champs standards de saisie (cf. [OcctaxFieldsConfig]). Vide ⇒ registre par défaut. */
    var settingsOcctaxJson: String
        get() = prefs.getString("gn_cache_settings_occtax", "")
        set(v) = prefs.putString("gn_cache_settings_occtax", v)

    /** URL de base de TaxHub publiée par le serveur (`t_mobile_apps` OCCTAX → `settings.sync.taxhub_url`),
     *  retenue à la synchro seulement quand le TaxHub intégré (`<URL_GeoNature>/api/taxhub`, celui du
     *  web) ne répond pas. Vide ⇒ le TaxHub intégré. */
    var taxhubUrlCache: String
        get() = prefs.getString("gn_taxhub_url", "")
        set(v) = prefs.putString("gn_taxhub_url", v)

    /** Base TaxHub RÉSOLUE pour tous les appels TaxRef/biblistes : celle du web, déduite de l'URL
     *  GeoNature, sauf si la synchro a dû se rabattre sur l'URL publiée ([taxhubUrlCache]).
     *  Sans slash final ; les appelants ajoutent `/api/taxref…` ou `/api/biblistes`. */
    val urlTaxhub: String
        get() = taxhubUrlCache.trim().trimEnd('/')
            .ifEmpty { urlServeur.trim().trimEnd('/') + "/api/taxhub" }

    /** Profondeur d'historique de l'Explorer, en jours (`settings.area_observation_duration` du
     *  serveur). Défaut 365 si non publié. */
    var dureeObservationJours: Int
        get() = prefs.getInt("gn_obs_duration_days", 365)
        set(v) = prefs.putInt("gn_obs_duration_days", v)

    /** Visibilité des champs du formulaire Occtax DU SERVEUR (`OCCTAX.form_fields`), en JSON brut.
     *  Vide = non publié ⇒ les défauts du schéma du serveur. Permet d'aligner la visibilité d'un
     *  champ sur le serveur (par instance), ex. masquer le type de regroupement si le serveur le
     *  masque. */
    var formFieldsJson: String
        get() = prefs.getString("gn_form_fields", "")
        set(v) = prefs.putString("gn_form_fields", v)

    /** Le champ de formulaire serveur [key] (clé de `form_fields`, ex. `group_type`) est-il visible ?
     *  Si la clé est absente ou la config non publiée, le défaut du schéma du serveur. */
    fun champFormVisible(key: String): Boolean = champFormVisible(formFieldsJson, key)

    /** `ENABLE_NOMENCLATURE_TAXONOMIC_FILTERS` du serveur : les nomenclatures d'une espèce sont
     *  filtrées par son taxon. Vrai par défaut, comme le schéma. */
    var nomenclaturesFiltreesParTaxon: Boolean
        get() = prefs.getBoolean("gn_nomenc_filtres_taxon", true)
        set(v) = prefs.putBoolean("gn_nomenc_filtres_taxon", v)

    /** `OCCTAX.ENABLE_MEDIAS` : le bloc Médias d'un dénombrement. Vrai par défaut, comme le schéma. */
    var occtaxMediasActifs: Boolean
        get() = prefs.getBoolean("gn_occtax_medias", true)
        set(v) = prefs.putBoolean("gn_occtax_medias", v)

    /** `OCCTAX.digital_proof_validator` : une preuve numérique doit être une adresse (http, https,
     *  ftp). Vrai par défaut, comme le schéma. */
    var occtaxPreuveNumeriqueUrl: Boolean
        get() = prefs.getBoolean("gn_occtax_preuve_url", true)
        set(v) = prefs.putBoolean("gn_occtax_preuve_url", v)

    /** `OCCTAX.observers_txt` : les observateurs d'un relevé se saisissent en texte libre, comme sur
     *  le web. Faux par défaut, comme le schéma. */
    var occtaxObservateursEnTexte: Boolean
        get() = prefs.getBoolean("gn_occtax_observateurs_texte", false)
        set(v) = prefs.putBoolean("gn_occtax_observateurs_texte", v)

    /** `OCCTAX.EXPANDED_TAXON_ADVANCED_DETAILS` : la section « Avancé » d'un taxon est ouverte
     *  d'emblée. Faux par défaut, comme le schéma. */
    var occtaxAvanceOuvert: Boolean
        get() = prefs.getBoolean("gn_occtax_avance_ouvert", false)
        set(v) = prefs.putBoolean("gn_occtax_avance_ouvert", v)

    /** `OCCTAX.CD_TYPO_HABITAT` : la typologie à laquelle le web restreint l'habitat d'un relevé ;
     *  -1 sans restriction (défaut du schéma). */
    var occtaxTypologieHabitat: Int
        get() = prefs.getInt("gn_occtax_cd_typo", -1)
        set(v) = prefs.putInt("gn_occtax_cd_typo", v)

    /** `MONITORINGS.PERMISSION_LEVEL` du serveur (JSON), lu à la synchro : l'objet de permission de
     *  chaque type d'objet des Suivis ; vide, les défauts de gn_module_monitoring. */
    var niveauxPermissionSuivisJson: String
        get() = prefs.getString("gn_suivis_niveaux_permission", "")
        set(v) = prefs.putString("gn_suivis_niveaux_permission", v)

    /** `OCCTAX.id_taxon_list` : la liste de taxons du formulaire du web, quand le jeu de données du
     *  relevé n'en déclare pas ; -1 sans liste (tout TaxRef). */
    var occtaxIdListeTaxons: Int
        get() = prefs.getInt("gn_occtax_id_taxon_list", -1)
        set(v) = prefs.putInt("gn_occtax_id_taxon_list", v)

    /** `OCCTAX.DATE_FORM_WITH_TODAY` : la date du jour posée dans un nouveau relevé ; à faux, la date
     *  reste vide, à choisir, comme sur le web (vrai par défaut). */
    var occtaxDateDuJour: Boolean
        get() = prefs.getBoolean("gn_occtax_date_du_jour", true)
        set(v) = prefs.putBoolean("gn_occtax_date_du_jour", v)

    /** Taille de page pour la pagination TaxRef (`settings.sync.page_size` du serveur). Défaut 1000. */
    var taxrefPageSize: Int
        get() = prefs.getInt("gn_taxref_page_size", 1000)
        set(v) = prefs.putInt("gn_taxref_page_size", v)

    /** Saisie de l'HEURE du relevé activée (`settings.input.date.enable_hours`). Défaut false. */
    var dateAvecHeures: Boolean
        get() = prefs.getBoolean("gn_date_hours", false)
        set(v) = prefs.putBoolean("gn_date_hours", v)

    /** Saisie d'une DATE DE FIN du relevé activée (`settings.input.date.enable_end_date`). Défaut false. */
    var dateAvecFin: Boolean
        get() = prefs.getBoolean("gn_date_end", false)
        set(v) = prefs.putBoolean("gn_date_end", v)

    /** Heures du relevé proposées — pilotées par `form_fields.hour_min` (comme le web Occtax), avec
     *  repli sur le réglage mobile `settings.input.date.enable_hours` si le serveur n'expose pas la clé. */
    val heuresVisibles: Boolean get() = champFormVisible("hour_min") && (formFieldsJson.isNotBlank() || dateAvecHeures)

    /** L'heure de fin du relevé, sous sa propre clé `hour_max` (le web la montre à part). */
    val heureFinVisible: Boolean get() = champFormVisible("hour_max") && (formFieldsJson.isNotBlank() || dateAvecHeures)

    /** Date de FIN du relevé proposée (le « + » du web) — pilotée par `form_fields.date_max`, avec
     *  repli sur `settings.input.date.enable_end_date` si le serveur n'expose pas form_fields. */
    val dateFinVisible: Boolean get() = champFormVisible("date_max") && (formFieldsJson.isNotBlank() || dateAvecFin)

    /** false quand le dernier test de connexion a détecté une version GeoNature inférieure
     *  à la minimale supportée ([fr.ariegenature.geomys.network.VERSION_GEONATURE_MINIMALE]).
     *  Invalide [connexionConfiguree] — donc toutes les opérations réseau (envois, Explorer,
     *  recherche TaxRef live…) — jusqu'à un nouveau test réussi contre un serveur à jour.
     *  true par défaut : bénéfice du doute quand la version n'est pas détectable. */
    var serveurCompatible: Boolean
        get() = prefs.getBoolean("gn_serveur_compatible", true)
        set(v) = prefs.putBoolean("gn_serveur_compatible", v)

    val connexionConfiguree: Boolean
        get() = urlServeur.trim().isNotEmpty() && login.trim().isNotEmpty() && motDePasse.isNotEmpty() &&
            serveurCompatible

    /** La connexion est configurée : pré-requis des envois, chaque saisie portant son jeu de
     *  données. */
    val estConfiguree: Boolean
        get() = connexionConfiguree

    /** Un id est-il présent dans un cache JSON (liste d'objets) sous la clé [champ] ?
     *  Renvoie false si le cache est vide/illisible OU si l'id est absent — interprétation
     *  STRICTE : sert à n'AUTORISER une saisie que lorsqu'on a la preuve que la sélection existe
     *  sur le serveur courant. */
    private fun idPresentDansCache(json: String, id: Int?, champ: String): Boolean {
        if (id == null || id <= 0) return false
        val j = json.takeIf { it.isNotEmpty() } ?: return false
        return try {
            val arr = JSONArray(j)
            (0 until arr.length()).any { arr.getJSONObject(it).optInt(champ, -1) == id }
        } catch (_: Exception) {
            false
        }
    }


    /** Le jeu de données [id] est-il acceptable pour un envoi ? Permissif si le cache datasets est
     *  vide (on ne peut pas trancher → on n'empêche pas l'envoi), strict sinon. Sert à valider le
     *  jeu d'un relevé AVANT le POST : un id_dataset hérité d'un autre serveur (après changement
     *  d'URL) partirait en clé étrangère invalide, 500 opaque. */
    fun datasetAcceptablePourEnvoi(id: Int): Boolean {
        if (datasetsCacheJson.isEmpty()) return true
        return idPresentDansCache(datasetsCacheJson, id, "id")
    }

    /** La liste de taxons d'un relevé du jeu de données [idDataset] : celle du jeu (`id_taxa_list`),
     *  comme le web, sinon la liste par défaut des Paramètres, sinon celle de la configuration
     *  d'OccTax (`OCCTAX.id_taxon_list`) ; null : tous les taxons (ceux du cache). */
    fun listeTaxonsOcctax(idDataset: Int?): Int? =
        idDataset?.let { listesDesJeux()[it] }
            ?: taxaListeId.trim().toIntOrNull()?.takeIf { it > 0 }
            ?: occtaxIdListeTaxons.takeIf { it > 0 }

    /** `id_taxa_list` de chaque jeu de données du cache, lu une fois par version du cache : le
     *  champ espèce le demande à chaque ouverture d'un relevé. */
    private fun listesDesJeux(): Map<Int, Int> {
        val json = datasetsCacheJson
        memoListesDesJeux?.let { (brut, listes) -> if (brut == json) return listes }
        val listes = try {
            val arr = JSONArray(json.ifEmpty { "[]" })
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .mapNotNull { o -> o.optInt("idTaxaList", -1).takeIf { it > 0 }?.let { o.optInt("id", -1) to it } }
                .toMap()
        } catch (_: Exception) {
            emptyMap()
        }
        memoListesDesJeux = json to listes
        return listes
    }

    @Volatile private var memoListesDesJeux: Pair<String, Map<Int, Int>>? = null

    /** Les jeux de données que le formulaire du web propose à un relevé (actifs, rattachés à OCCTAX
     *  et créables), lus dans le cache. */
    private fun idsDatasetsOcctaxProposes(): Set<Int> = try {
        ReglesSelectionOcctax.datasetsProposes(listeDepuisJson(GeoNatureDataset.serializer(), datasetsCacheJson), datasetsCreablesOcctax)
            .map { it.id }.toSet()
    } catch (_: Exception) {
        emptySet()
    }

    /** Le jeu de données par défaut des Paramètres s'il est encore proposé, sinon null : un jeu
     *  fantôme (retiré du serveur, devenu inactif ou non créable) ne s'impose à aucun relevé. */
    val idDatasetParDefaut: Int?
        get() = idDataset.trim().toIntOrNull()?.takeIf { it > 0 && it in idsDatasetsOcctaxProposes() }

    /** La liste de taxons par défaut est réglée : celle qu'impose le jeu de données par défaut, sinon
     *  celle choisie, encore dans le cache. Sans aucune liste en cache, il n'y a rien à choisir. */
    val listeParDefautValide: Boolean
        get() = idDatasetParDefaut?.let { listesDesJeux()[it] } != null ||
            idPresentDansCache(listesCacheJson, taxaListeId.trim().toIntOrNull(), "id") ||
            listeDepuisJson(GeoNatureListe.serializer(), listesCacheJson).isEmpty()

    /** L'observateur par défaut est réglé : celui choisi, l'utilisateur connecté ou un observateur
     *  encore dans le cache, sinon l'utilisateur connecté, s'il est connu. */
    val observateurParDefautValide: Boolean
        get() {
            val choisi = observateurDefautId.trim().toIntOrNull()?.takeIf { it > 0 } ?: return idRoleUtilisateur > 0
            return choisi == idRoleUtilisateur || idPresentDansCache(observateursCacheJson, choisi, "idRole")
        }

    /** Saisie OCCTAX (multi/mono) réellement réalisable : connexion configurée ET les trois
     *  sélections par défaut des Paramètres réglées (jeu de données proposé, liste de taxons,
     *  observateur), comme sous GeoMys-Android ; aucune n'est choisie d'office, sauf l'observateur.
     *  Faux tant que les caches sont vides (rien synchronisé) — pilote l'activation des boutons de
     *  saisie sur l'accueil. */
    val saisieOcctaxValide: Boolean
        get() = connexionConfiguree && idDatasetParDefaut != null && listeParDefautValide && observateurParDefautValide

    companion object {
        /** Version des données synchronisées qu'attend cette release : à incrémenter quand la
         *  synchro lit quelque chose de nouveau. Depuis le 21 octobre 2026, une installation en
         *  retard n'est plus bloquée ni purgée, comme sous GeoMys-Android : les données en place
         *  servent, un bandeau de Paramètres suggère « Recharger les données », et la synchro
         *  suivante réécrit les caches. TaxRef n'est retéléchargé que si sa version a changé sur le
         *  serveur, ou si le format de son cache a changé ([TaxRefCache.VERSION_FORMAT]).
         *  Historique (GeoMys Common) :
         *  1 = première version ;
         *  2 = vérification des champs du 20 octobre : groupe 3 des taxons, lignes taxonomiques des
         *      nomenclatures, réglages OccTax (médias, preuves, filtres taxonomiques) et OccHab
         *      (observateurs) lus à la synchro, valeurs des champs additionnels, paramètres des listes
         *      des Suivis ;
         *  3 = alignement des formulaires sur le web (20 octobre) : `label_fr` des nomenclatures,
         *      organisme et nom complet de l'utilisateur (défauts de nomenclature d'OccTax), réglages
         *      du formulaire OccTax (observateurs en texte, section avancée, typologie d'habitat, liste
         *      de taxons), valeurs typées et attributs des champs additionnels, noms valides TAXREF,
         *      listes de taxons de la configuration d'OccTax et des champs des Suivis ;
         *  4 = réponses du 20 octobre : droits de création des Suivis (`/api/gn_commons/modules`,
         *      `MONITORINGS.PERMISSION_LEVEL`), listes des Suivis alimentées par une API (TaxHub,
         *      adresses absolues, `data`). */
        const val VERSION_DONNEES_REQUISE = 4

        // Mot de passe conservé EN MÉMOIRE uniquement quand le coffre est indisponible — jamais
        // persisté en clair sur disque. Perdu au redémarrage du process (re-saisie).
        @Volatile private var motDePasseMemoire: String? = null
    }
}