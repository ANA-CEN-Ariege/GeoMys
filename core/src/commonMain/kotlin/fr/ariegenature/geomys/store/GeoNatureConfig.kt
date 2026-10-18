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
// Android (sans objet, GeoMys Common ne reprend pas les données) ; additionalFieldsOcctaxJsonActif
// attend OcctaxFieldsConfig.

package fr.ariegenature.geomys.store

import com.russhwolf.settings.Settings
import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import kotlin.concurrent.Volatile

/** Le champ de formulaire serveur [key] (clé de `OCCTAX.form_fields`, ex. `group_type`) est-il
 *  visible d'après [formFieldsJson] ? **true par défaut** si la clé est absente ou la config non
 *  publiée — on ne masque jamais un champ faute d'information. Partagé entre [GeoNatureConfig] et
 *  l'UI (DetailsReleveDialog). */
fun champFormVisible(formFieldsJson: String, key: String): Boolean = try {
    if (formFieldsJson.isBlank()) true
    else JSONObject(formFieldsJson).optBoolean(key, true)
} catch (_: Exception) { true }

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

    var idDataset: String
        get() = prefs.getString("gn_dataset", "")
        set(v) = prefs.putString("gn_dataset", v)

    /** Nom du jeu de données sélectionné (affiché dans Config GeoNature à la place de l'id). */
    var nomDataset: String
        get() = prefs.getString("gn_dataset_nom", "")
        set(v) = prefs.putString("gn_dataset_nom", v)

    var taxaListeId: String
        get() = prefs.getString("gn_taxa_liste", "")
        set(v) = prefs.putString("gn_taxa_liste", v)

    /** Nom complet (prénom + nom) renvoyé par GeoNature lors de la dernière connexion réussie.
     *  Mis à jour par GeoNatureAuth.testerConnexion. Fallback pour le déterminateur si
     *  aucun observateur par défaut n'est sélectionné. */
    var nomUtilisateur: String
        get() = prefs.getString("gn_nom_utilisateur", "")
        set(v) = prefs.putString("gn_nom_utilisateur", v)

    /** id_role de l'utilisateur actuellement connecté (renvoyé par GeoNature au login).
     *  Sert à pré-sélectionner cet utilisateur dans les champs observers/datalist de saisie.
     *  -1 si pas encore connecté ou non détecté. */
    var idRoleUtilisateur: Int
        get() = prefs.getInt("gn_id_role_utilisateur", -1)
        set(v) = prefs.putInt("gn_id_role_utilisateur", v)

    /** Version de l'instance GeoNature relevée au dernier test de connexion réussi
     *  (`/api/gn_commons/config`, best-effort). Vide si inconnue. Affichée dans Paramètres
     *  pour diagnostiquer les comportements propres à une version serveur. */
    var versionGeoNatureServeur: String
        get() = prefs.getString("gn_version_serveur", "")
        set(v) = prefs.putString("gn_version_serveur", v)

    /** Nom complet de l'observateur sélectionné comme déterminateur par défaut. */
    var observateurDefautNom: String
        get() = prefs.getString("gn_obs_defaut_nom", "")
        set(v) = prefs.putString("gn_obs_defaut_nom", v)

    /** id_role de l'observateur sélectionné comme déterminateur par défaut. */
    var observateurDefautId: String
        get() = prefs.getString("gn_obs_defaut_id", "")
        set(v) = prefs.putString("gn_obs_defaut_id", v)

    /** Version des DONNÉES synchronisées présentes sur l'appareil (cf. [VERSION_DONNEES_REQUISE]).
     *  Écrite par SyncRunner en fin de synchro (TaxRef chargé) ; 0 = jamais synchronisé depuis
     *  l'introduction du mécanisme (v1.3.17). */
    var versionDonneesChargees: Int
        get() = prefs.getInt("gn_version_donnees_chargees", 0)
        set(v) = prefs.putInt("gn_version_donnees_chargees", v)

    /** true entre le lancement qui a détecté une mise à jour exigeant un rechargement
     *  (`armerRechargementSiRequis`) et la synchro qui l'effectue (purge au début de SyncRunner,
     *  puis désarmement) : rend `configurationComplete` faux et pilote le bandeau explicatif et
     *  les messages de blocage de l'écran Paramètres. */
    var rechargementRequisApresMaj: Boolean
        get() = prefs.getBoolean("gn_rechargement_requis_maj", false)
        set(v) = prefs.putBoolean("gn_rechargement_requis_maj", v)

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

    /** Visibilité d'un champ du formulaire OccHab (`OCCHAB.formConfig[champ]`). Défaut true si la
     *  config est absente ou le champ non listé — on ne masque QUE ce que le serveur masque. */
    fun occhabChampVisible(champ: String): Boolean {
        val json = occhabFormConfigJson
        if (json.isBlank()) return true
        return try { JSONObject(json).optBoolean(champ, true) } catch (_: Exception) { true }
    }

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
     *  rafraîchie à chaque synchro des settings. Vide ⇒ on déduit `<URL_GeoNature>/api/taxhub`. */
    var taxhubUrlCache: String
        get() = prefs.getString("gn_taxhub_url", "")
        set(v) = prefs.putString("gn_taxhub_url", v)

    /** Base TaxHub RÉSOLUE pour tous les appels TaxRef/biblistes : l'URL publiée par le serveur si
     *  présente (gère les instances où TaxHub est hébergé ailleurs), sinon déduite de l'URL GeoNature.
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
     *  Vide = non publié ⇒ tout visible. Permet d'aligner la visibilité d'un champ sur le serveur
     *  (par instance), ex. masquer le type de regroupement si le serveur le masque. */
    var formFieldsJson: String
        get() = prefs.getString("gn_form_fields", "")
        set(v) = prefs.putString("gn_form_fields", v)

    /** Le champ de formulaire serveur [key] (clé de `form_fields`, ex. `group_type`) est-il visible ?
     *  **true par défaut** si la clé est absente ou la config non publiée — on ne masque jamais un
     *  champ faute d'information (pas de régression sur un serveur qui n'expose pas `form_fields`). */
    fun champFormVisible(key: String): Boolean = champFormVisible(formFieldsJson, key)

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

    /** Date de FIN du relevé proposée (le « + » du web) — pilotée par `form_fields.date_max`, avec
     *  repli sur `settings.input.date.enable_end_date` si le serveur n'expose pas form_fields. */
    val dateFinVisible: Boolean get() = champFormVisible("date_max") && (formFieldsJson.isNotBlank() || dateAvecFin)

    // additionalFieldsOcctaxJsonActif (GeoNatureConfig.kt:296-300 sous Android) arrive avec
    // OcctaxFieldsConfig, dont il dépend.

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

    val estConfiguree: Boolean
        get() = connexionConfiguree && idDataset.trim().isNotEmpty()

    /** Le jeu de données configuré existe-t-il dans le cache des datasets du serveur courant ?
     *  Un id_dataset hérité d'un autre serveur (ex. après changement d'URL) part en clé
     *  étrangère invalide → 500 opaque côté GeoNature au lieu d'une vraie erreur de validation.
     *  Si le cache est vide (datasets jamais synchronisés), on ne peut pas trancher → on
     *  considère valide pour ne pas bloquer à tort un utilisateur en cours de configuration. */
    val datasetValide: Boolean
        get() {
            val id = idDataset.trim().toIntOrNull()?.takeIf { it > 0 } ?: return false
            val json = datasetsCacheJson.takeIf { it.isNotEmpty() } ?: return true
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).any { arr.getJSONObject(it).optInt("id", -1) == id }
            } catch (_: Exception) {
                true
            }
        }

    /** Config complète ET cohérente avec le serveur courant : pré-requis pour démarrer ou
     *  envoyer une saisie (connexion + jeu de données réellement présent côté serveur). */
    val saisiePossible: Boolean
        get() = estConfiguree && datasetValide

    /** Un id est-il présent dans un cache JSON (liste d'objets) sous la clé [champ] ?
     *  Renvoie false si le cache est vide/illisible OU si l'id est absent — interprétation
     *  STRICTE (à l'inverse de [datasetValide], permissif) : sert à n'AUTORISER une saisie que
     *  lorsqu'on a la preuve que la sélection existe sur le serveur courant. */
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


    /** Le jeu de données sélectionné est présent dans le cache ET marqué ACTIF (`actif=true`).
     *  Un dataset archivé/inactif sur le serveur (cf. `actif:false`) reste présent dans le cache
     *  mais NE doit PAS valider la config : une saisie y partirait dans le vide (invisible dans
     *  l'interface web, masquée par défaut). `actif` absent du cache → permissif (true). */
    val datasetActifDansCache: Boolean
        get() {
            val id = idDataset.trim().toIntOrNull()?.takeIf { it > 0 } ?: return false
            val j = datasetsCacheJson.takeIf { it.isNotEmpty() } ?: return false
            return try {
                val arr = JSONArray(j)
                (0 until arr.length()).any {
                    val o = arr.getJSONObject(it)
                    o.optInt("id", -1) == id && o.optBoolean("actif", true)
                }
            } catch (_: Exception) {
                false
            }
        }

    /** Le jeu de données [id] est-il acceptable pour un envoi ? Permissif si le cache datasets est
     *  vide (on ne peut pas trancher → on n'empêche pas l'envoi), strict sinon. Sert à valider un
     *  override de relevé (« Détails du relevé ») AVANT le POST, pour éviter le 500 opaque sur FK
     *  invalide — même garde que [datasetValide], mais pour un id arbitraire. */
    fun datasetAcceptablePourEnvoi(id: Int): Boolean {
        if (datasetsCacheJson.isEmpty()) return true
        return idPresentDansCache(datasetsCacheJson, id, "id")
    }

    /** La liste de taxons sélectionnée est présente dans le cache des listes. */
    val listePresenteDansCache: Boolean
        get() = idPresentDansCache(listesCacheJson, taxaListeId.trim().toIntOrNull(), "id")

    /** L'observateur (déterminateur) par défaut est présent dans le cache des observateurs. */
    val observateurPresentDansCache: Boolean
        get() = idPresentDansCache(observateursCacheJson, observateurDefautId.trim().toIntOrNull(), "idRole")

    /** Saisie OCCTAX (multi/mono) réellement réalisable : connexion configurée ET les trois
     *  sélections (jeu de données, liste de taxons, observateur par défaut) sont chargées dans
     *  les caches du serveur courant. Faux tant que les caches sont vides (rien synchronisé) ou
     *  qu'une sélection est fantôme — pilote l'activation des boutons de saisie sur l'accueil. */
    val saisieOcctaxValide: Boolean
        get() = connexionConfiguree && datasetActifDansCache &&
            listePresenteDansCache && observateurPresentDansCache

    companion object {
        /** Version des données synchronisées EXIGÉE par cette release. À INCRÉMENTER dans toute
         *  release qui rend « Recharger les données » OBLIGATOIRE (nouveau cache, nouveau champ lu
         *  à la synchro, format modifié…) : au premier lancement, une installation dont
         *  [versionDonneesChargees] est en retard arrive sur Paramètres (bloqué) avec un bandeau ;
         *  la synchro suivante purge les caches synchronisés avant de les réécrire (cf.
         *  `armerRechargementSiRequis` / SyncRunner). Les JSON de config (datasets, listes,
         *  observateurs, champs add., settings) ne sont pas purgés : ils sont REMPLACÉS par la
         *  phase A quand elle répond non vide. Historique (GeoMys Common) :
         *  1 = première version. */
        const val VERSION_DONNEES_REQUISE = 1

        // Mot de passe conservé EN MÉMOIRE uniquement quand le coffre est indisponible — jamais
        // persisté en clair sur disque. Perdu au redémarrage du process (re-saisie).
        @Volatile private var motDePasseMemoire: String? = null
    }
}