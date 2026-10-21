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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/MonitoringDatalists.kt) :
// HttpURLConnection remplacé par ClientsGeoNature (le POST des jeux de données passe par postJson),
// org.json par son portage, Gson par listeDepuisJson, URLDecoder par decodeURLQueryComponent,
// android.util.Log par Journal, runCatching par essayer autour du réseau. L'objet devient une
// classe ; son cache de résolveurs passe sous verrou ; extraireOptions, pur, va dans son
// compagnon.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.network.MonitoringApi.LabelResolver
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringPropertySchema
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.MonitoringApi.OptionDatalist
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.get
import fr.ariegenature.geomys.network.http.postJson
import fr.ariegenature.geomys.plateforme.comparateurFrancais
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.MonitoringCache
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.io.listeDepuisJson
import fr.ariegenature.geomys.util.essayer
import io.ktor.client.statement.bodyAsText
import io.ktor.http.decodeURLQueryComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

/** Options dynamiques des formulaires monitoring : fetch des datalists/nomenclatures/datasets
 *  (+ write-through cache offline), listes d'observateurs UsersHub, et construction du
 *  [LabelResolver] qui traduit les IDs (id_role, id_nomenclature, id_dataset) en libellés
 *  humains. Extrait de [MonitoringApi] (découpage du god-object) —
 *  [MonitoringApi.chargerOptionsDatalist] reste la façade appelée par les écrans. */
class MonitoringDatalists(
    private val auth: GeoNatureAuth,
    private val clients: ClientsGeoNature,
    private val cache: MonitoringCache,
    private val taxRef: TaxRefCache,
) {

    /** Le LabelResolver d'un protocole, depuis le cache seulement (décision de l'utilisateur du
     *  20 octobre : rien n'est téléchargé hors synchro) : les identifiants que [schema] déclare
     *  (`type_util` user, nomenclature, dataset, site, sites_group, habitat, module…) traduits par
     *  les listes que la synchro a mises en cache — observateurs de la liste du protocole et des
     *  champs, options des datalists et des nomenclatures de ses formulaires, jeux de données OccTax
     *  et OccHab, sites et groupes de sites de [moduleCode]. Le libellé est le champ que le web
     *  affiche (`display_field_names`). Un identifiant sans libellé en cache reste affiché tel quel. */
    suspend fun lireResolveurLabels(
        config: GeoNatureConfig,
        schema: Map<String, MonitoringSchemaObjet>,
        moduleCode: String? = null,
    ): LabelResolver =
        withContext(Dispatchers.IO) {
            val proprietes = schema.values.flatMap { it.properties.values }
            val champsAffichage = schema["module"]?.champsAffichage.orEmpty()
            /** Les entrées d'une liste en cache ([prop] : son api), projetées (clé → libellé). */
            fun libelles(prop: MonitoringPropertySchema, cle: String, vararg libelle: String): Map<String, String> {
                val entrees = listeEnCache(prop)?.first ?: return emptyMap()
                return projeter(entrees, cle, *libelle)
            }
            val nomenclatures = mutableMapOf<String, MutableMap<String, String>>()
            proprietes.filter { it.typeUtil == "nomenclature" }.forEach { p ->
                val code = p.nomenclatureType ?: return@forEach
                nomenclatures.getOrPut(code) { mutableMapOf() } += libelles(p, "id_nomenclature", "label_fr", "label_default")
            }
            // Les observateurs : la liste du protocole, celles des champs (`users/menu/<id>`), et les
            // autres listes d'utilisateurs de ses formulaires (`users/roles`…).
            val users = mutableMapOf<String, String>()
            val proprietesUser = proprietes.filter { it.typeUtil == "user" }
            if (proprietesUser.isNotEmpty()) {
                val listes = listOfNotNull(schema["module"]?.idListObserver) +
                    proprietesUser.mapNotNull { p -> p.apiUrl?.let { LISTE_OBSERVATEURS.find(it) }?.groupValues?.get(1)?.toIntOrNull() }
                listes.distinct().forEach { idListe ->
                    lireObservateursDeListe(idListe)?.let { users += projeter(it, "id_role", "nom_complet") }
                }
                proprietesUser.forEach { p -> users += libelles(p, "id_role", "nom_complet") }
            }
            // Les autres identifiants : les options en cache de leurs datalists, et, pour les sites et
            // les groupes de sites, ceux du protocole mis en cache par la synchro.
            val autres = mutableMapOf<String, MutableMap<String, String>>()
            proprietes.filter { it.typeUtil != null && it.typeUtil !in TYPES_RESOLUS_A_PART }.forEach { p ->
                val type = p.typeUtil ?: return@forEach
                val libelle = champsAffichage[type] ?: CHAMPS_AFFICHAGE_PAR_DEFAUT[type] ?: p.keyLabel ?: return@forEach
                val cle = p.keyValue ?: CLES_PAR_DEFAUT[type] ?: return@forEach
                autres.getOrPut(type) { mutableMapOf() } += libelles(p, cle, libelle)
            }
            if (moduleCode != null && proprietes.any { it.typeUtil == "site" || it.typeUtil == "sites_group" }) {
                sitesEtGroupesEnCache(moduleCode).forEach { (type, noms) ->
                    val connus = autres.getOrPut(type) { mutableMapOf() }
                    noms.forEach { (id, nom) -> connus.getOrPut(id) { nom } }
                }
            }
            val datasets = mutableMapOf<String, String>()
            if (proprietes.any { it.typeUtil == "dataset" }) {
                listOf(config.datasetsCacheJson, config.datasetsOcchabCacheJson).filter { it.isNotEmpty() }.forEach { json ->
                    runCatching { listeDepuisJson(GeoNatureDataset.serializer(), json) }.getOrNull()
                        ?.forEach { datasets[it.id.toString()] = it.nom }
                }
                proprietes.filter { it.apiUrl?.startsWith("meta/datasets") == true || it.typeWidget.equals("dataset", ignoreCase = true) }
                    .forEach { datasets += libelles(it, "id_dataset", "dataset_name") }
            }
            LabelResolver(
                nomenclatures = nomenclatures,
                users = users,
                datasets = datasets,
                taxRef = taxRef,
                autres = autres,
                champsAffichage = champsAffichage,
            )
        }

    /** Les champs propres aux types de site [typesSite] (identifiants), lus dans le cache de la
     *  datalist `types_site` du site ([schemaSite]) : le `config.specific` de chaque type, comme
     *  `addSpecificConfig` du web (onglet « Propriétés spécifiques ») — champs html exclus, un champ
     *  déclaré par plusieurs types une seule fois. Vide si rien n'est en cache. */
    suspend fun lireChampsTypesSite(schemaSite: MonitoringSchemaObjet?, typesSite: List<String>): Map<String, MonitoringPropertySchema> =
        withContext(Dispatchers.IO) {
            val prop = schemaSite?.properties?.get("types_site") ?: return@withContext emptyMap()
            val types = listeEnCache(prop)?.first ?: return@withContext emptyMap()
            val champs = linkedMapOf<String, MonitoringPropertySchema>()
            for (i in 0 until types.length()) {
                val type = types.optJSONObject(i) ?: continue
                if (type.opt("id_nomenclature_type_site")?.toString() !in typesSite) continue
                val specific = type.optJSONObject("config")?.optJSONObject("specific") ?: continue
                val noms = specific.keys()
                while (noms.hasNext()) {
                    val nom = noms.next()
                    val champ = specific.optJSONObject(nom) ?: continue
                    if (champ.optString("type_widget", "") == "html") continue
                    MonitoringSchemas.parserUnePropriete(nom, champ)?.let { champs[nom] = it }
                }
            }
            champs
        }

    /** Les sites et les groupes de sites de [moduleCode] en cache, par type (identifiant → nom) : les
     *  enfants directs du protocole et ceux des fiches de ses groupes de sites. */
    private fun sitesEtGroupesEnCache(moduleCode: String): Map<String, Map<String, String>> {
        val parType = mutableMapOf<String, MutableMap<String, String>>()
        fun ajouter(enfants: Map<String, List<MonitoringApi.MonitoringEnfant>>) {
            for ((type, liste) in enfants) {
                if (type != "site" && type != "sites_group") continue
                val noms = parType.getOrPut(type) { mutableMapOf() }
                val champ = if (type == "site") "base_site_name" else "sites_group_name"
                liste.forEach { e -> noms.getOrPut(e.id.toString()) { e.proprietes[champ] ?: e.nom } }
            }
        }
        val duModule = cache.getJson(cache.keyEnfants(moduleCode))
            ?.let { runCatching { MonitoringObjets.enfantsDe(JSONObject(it)) }.getOrNull() }
            ?: return parType
        ajouter(duModule)
        duModule["sites_group"].orEmpty().forEach { groupe ->
            cache.getJson(cache.keyObjet(moduleCode, "sites_group", groupe.id))
                ?.let { runCatching { MonitoringObjets.enfantsDe(JSONObject(it)) }.getOrNull() }
                ?.let(::ajouter)
        }
        return parType
    }

    /** La liste en cache d'une datalist ([prop] : son api), telle que la synchro l'a reçue. À défaut,
     *  celle de son api sans paramètres, mise en cache par une version qui ne les demandait pas
     *  encore (restriction d'une nomenclature, `params` d'une datalist) ; le booléen le dit, et
     *  [optionsEnCache] applique alors elle-même la restriction et le tri. */
    private fun listeEnCache(prop: MonitoringPropertySchema): Pair<JSONArray, Boolean>? {
        val apiPath = prop.apiUrl ?: return null
        val cles = (listOfNotNull(prop.dataPath) + listOf("values", "data", "items", "results")).toTypedArray()
        fun lire(api: String) = cache.getJson(cache.keyOptionsDatalist(api))?.let { runCatching { it.parserTableauJson(*cles) }.getOrNull() }
        lire(cleCache(prop))?.let { return it to false }
        val sansParametres = apiPath.substringBefore('?').takeIf { it != apiPath } ?: return null
        return lire(sansParametres)?.let { it to true }
    }

    /** La liste d'observateurs d'une UsersHub `id_liste` en cache, sans requête : celle que la synchro
     *  y a mise ([chargerObservateursDeListe]). Null si elle n'y est pas. */
    fun lireObservateursDeListe(idListe: Int): JSONArray? {
        if (idListe <= 0) return null
        return cache.getJson(cache.keyObservateurs(idListe))?.let { runCatching { JSONArray(it) }.getOrNull() }
    }

    /** Charge la liste d'observateurs d'une UsersHub `id_liste` : fetch live + persistance
     *  dans le cache `MonitoringCache.keyObservateurs`, ou fallback sur le cache disque si
     *  pas de réseau / auth en échec. Retourne null si vraiment rien ne marche. Réservé à la
     *  synchro : les écrans lisent le cache ([lireObservateursDeListe]). */
    suspend fun chargerObservateursDeListe(
        config: GeoNatureConfig,
        idListe: Int,
    ): JSONArray? = withContext(Dispatchers.IO) {
        if (idListe <= 0) return@withContext null
        val key = cache.keyObservateurs(idListe)
        val base = config.urlServeur.trim().trimEnd('/')
        val session = essayer { auth.loginAvecCookies(base, config.login, config.motDePasse) }.getOrNull()
        if (session != null) {
            val (token, _, cookies) = session
            val res = essayer {
                val reponse = clients.get("$base/api/users/menu/$idListe", token, cookies, 15000)
                if (reponse.status.value != 200) null
                else reponse.bodyAsText()
            }.getOrNull()
            if (res != null) {
                // VALIDÉ avant mise en cache : un 200 non-JSON (portail captif, proxy) ne doit
                // jamais empoisonner le cache hors-ligne (audit 2026-08-27).
                val tableau = runCatching { JSONArray(res) }.getOrNull()
                if (tableau != null) {
                    cache.setJson(key, res)
                    return@withContext tableau
                }
            }
        }
        // Fallback cache disque pour usage offline.
        lireObservateursDeListe(idListe)
    }

    /** Les options d'un widget `datalist` ou `nomenclature` pour les écrans : celles que la synchro a
     *  mises en cache ([chargerOptionsDatalist]), sans requête. Null si rien n'est en cache. */
    suspend fun lireOptionsDatalist(config: GeoNatureConfig, prop: MonitoringPropertySchema): List<OptionDatalist>? =
        withContext(Dispatchers.IO) {
            val apiPath = prop.apiUrl ?: return@withContext null
            val (keyValue, keyLabel) = cles(prop) ?: return@withContext null
            Regex("""users/menu/(\d+)""").find(apiPath)?.let { obs ->
                val arr = lireObservateursDeListe(obs.groupValues[1].toIntOrNull() ?: return@withContext null)
                    ?: return@withContext null
                return@withContext extraireOptions(arr, keyValue, keyLabel, prop.filtres, ordre = prop.ordreOptions)
            }
            optionsEnCache(prop, keyValue, keyLabel)
        }

    /** Les options en cache d'une datalist, null si elles n'y sont pas. Une liste vide reçue du
     *  serveur fait foi : sans droit de création, aucun jeu de données n'est proposé, comme sur le
     *  web. */
    private fun optionsEnCache(prop: MonitoringPropertySchema, keyValue: String, keyLabel: String): List<OptionDatalist>? {
        val (liste, repli) = listeEnCache(prop) ?: return null
        val options = extraireOptions(
            liste, keyValue, keyLabel, prop.filtres,
            cdNomenclatures = if (repli) prop.cdNomenclatures else emptyList(),
            ordre = prop.ordreOptions,
        )
        // Les jeux de données triés par nom, comme les demande le web (`orderby=dataset_name`).
        val estDataset = prop.apiUrl?.startsWith("meta/datasets") == true || prop.typeWidget.equals("dataset", ignoreCase = true)
        if (estDataset && prop.ordreOptions == null) {
            val comparateur = comparateurFrancais()
            return options.sortedWith { a, b -> comparateur.compare(a.label, b.label) }
        }
        // Une nomenclature relue sans ses paramètres n'a pas l'ordre par libellé que le serveur lui
        // donne désormais.
        if (!repli || !prop.typeWidget.equals("nomenclature", ignoreCase = true)) return options
        val comparateur = comparateurFrancais()
        return options.sortedWith { a, b -> comparateur.compare(a.labelDefaut ?: a.label, b.labelDefaut ?: b.label) }
    }

    /** Fetch dynamiquement les options d'un widget `datalist` ou `nomenclature` (forme ancienne).
     *  Construit l'URL du web selon l'`application` (`<base>/api/<api>`, celle de TaxHub, ou
     *  l'adresse brute), en POST avec le corps `data` s'il y en a un, parse selon `dataPath` (si
     *  fourni) → array d'objets, puis projette chaque entrée sur (keyValue, keyLabel). Renvoie null
     *  sur erreur HTTP/auth/parse. Réservé à la synchro, qui en remplit le cache : les écrans le
     *  lisent ([lireOptionsDatalist]). */
    suspend fun chargerOptionsDatalist(
        config: GeoNatureConfig,
        prop: MonitoringPropertySchema,
    ): List<OptionDatalist>? = withContext(Dispatchers.IO) {
        val apiPath = prop.apiUrl ?: return@withContext null
        val (keyValue, keyLabel) = cles(prop) ?: return@withContext null
        // Cas spécial observateurs : on passe par le helper qui gère le cache disque
        // (fetch live → cache → réutilisable en offline). Détection par préfixe d'URL.
        val matchObs = Regex("""users/menu/(\d+)""").find(apiPath)
        if (matchObs != null) {
            val idListe = matchObs.groupValues[1].toIntOrNull() ?: return@withContext null
            val arr = chargerObservateursDeListe(config, idListe) ?: return@withContext null
            return@withContext extraireOptions(arr, keyValue, keyLabel, prop.filtres, ordre = prop.ordreOptions)
        }
        // Cas spécial dataset : la source autoritative est l'endpoint serveur
        // /api/meta/datasets, qui applique côté backend (a) le filtre `create=<module>.<code_object>`
        // = CRUVED action=C scope du user authentifié (cf. TDatasets.filter_by_creatable +
        // filter_by_scope sur cor_dataset_actor — un user "MES données" scope=1 ne voit
        // que les datasets dont il est acteur), (b) le filtre `active=true`, (c) le filtre
        // `module_code`. Le cache local datasetsCacheJson n'est PAS une source fiable :
        // peuplé via /meta/datasets?fields=modules sans `create`, il contient TOUS les
        // datasets rattachés au module, sans filtre CRUVED. On le réservait donc à un
        // fallback offline.
        // L'apiUrl reçue par chargerOptionsDatalist a déjà été enrichie par derirverApiSiManquant
        // (active=true + create=<creatable_in_module> + module_code=<code>), donc le fetch
        // live ci-dessous renvoie exactement la liste que le web propose. On ne court-circuite
        // PAS avec le cache local.
        // Hors ligne, la liste exacte en cache seulement : les jeux OccTax du module ne disent rien
        // des droits de création, et le web n'en propose pas d'autres.
        val clesArray = (listOfNotNull(prop.dataPath) + listOf("values", "data", "items", "results")).toTypedArray()
        val cleCache = cache.keyOptionsDatalist(cleCache(prop))
        fun cacheOffline(): List<OptionDatalist>? = optionsEnCache(prop, keyValue, keyLabel)
        val base = config.urlServeur.trim().trimEnd('/')
        // Hors-ligne (login impossible) : le cache datalist exact.
        // HORS-LIGNE : `loginAvecCookies` LÈVE l'IOException depuis le lot B3 (audit 2026-08-27)
        // au lieu de renvoyer null — `null` y est désormais réservé au REFUS d'authentification.
        // Sans ce catch, l'exception traversait `enrichirAvecOptions` (awaitAll d'un `async`)
        // jusqu'au `lifecycleScope.launch` de NouvelleVisiteFragment, qui n'a aucun try/catch sur
        // tout le chemin : l'application PLANTAIT à l'ouverture — et donc aussi à la COMPLÉTION —
        // d'une visite sans réseau, alors que le repli hors-ligne ci-dessous existait déjà
        // (audit 2026-09-14, R7-C2). Même patron que chargerSchemaProtocole / chargerModules /
        // chargerEnfants : réseau absent ⇒ on sert le cache, on ne remonte pas l'exception.
        val session = try {
            auth.loginAvecCookies(base, config.login, config.motDePasse)
        } catch (_: IOException) { null }
        val (token, _, cookies) = session
            ?: return@withContext cacheOffline()
        val estDataset = apiPath.startsWith("meta/datasets") ||
            prop.typeWidget.equals("dataset", ignoreCase = true)
        // Pour le widget `dataset`, on doit obligatoirement POST avec body JSON : le backend
        // n'applique `filter_by_params` (= filtre `active`, `module_code` etc.) QUE quand
        // `request.is_json` est True. En GET avec query string, le `create=…` filtre via
        // CRUVED mais `active=true` est ignoré — on récupère alors les datasets inactifs en
        // plus, et la liste est plus grosse que ce que propose la version web.
        // Cf. backend/geonature/core/gn_meta/routes.py::get_datasets.
        val (urlFinale, postBody) = if (estDataset) {
            // Sépare la query string : on garde l'éventuel `orderby` côté URL (comme le web),
            // et on transforme `module_code`, `active`, `create`, … en JSON body.
            val (chemin, query) = apiPath.split('?', limit = 2).let {
                it[0] to (it.getOrNull(1).orEmpty())
            }
            val paramsBody = JSONObject()
            val querysUrl = mutableListOf<String>()
            query.split('&').filter { it.isNotEmpty() }.forEach { kv ->
                val (k, vRaw) = kv.split('=', limit = 2).let {
                    it[0] to (it.getOrNull(1).orEmpty())
                }
                val v = vRaw.decodeURLQueryComponent(plusIsSpace = true)
                when {
                    k == "orderby" -> querysUrl.add("$k=$vRaw")
                    k == "active" -> paramsBody.put(k, v.equals("true", ignoreCase = true))
                    else -> paramsBody.put(k, v)
                }
            }
            // Triés par nom, comme les demande le composant pnx-datasets du web.
            if (querysUrl.none { it.startsWith("orderby=") }) querysUrl.add("orderby=dataset_name")
            val urlStr = "$base/api/$chemin" + if (querysUrl.isNotEmpty()) "?${querysUrl.joinToString("&")}" else ""
            urlStr to paramsBody.toString()
        } else {
            // L'adresse du web (getDataList) : l'api du serveur pour `GeoNature`, celle de TaxHub pour
            // `TaxHub`, sinon l'adresse brute ; en POST avec le corps `data` s'il y en a un. Une api
            // relative sans application vise le serveur.
            val url = when {
                prop.application == "TaxHub" -> "${config.urlTaxhub}/api/$apiPath"
                prop.application == "GeoNature" -> "$base/api/$apiPath"
                apiPath.startsWith("http://") || apiPath.startsWith("https://") -> apiPath
                else -> "$base/api/$apiPath"
            }
            url to prop.donneesPost
        }
        // Une seule demande, filtre de création compris, comme le composant pnx-datasets du web :
        // sans droit de création, aucun jeu n'est proposé. L'appli redemandait sans ce filtre, et
        // proposait des jeux où l'envoi serait refusé.
        for (body in listOf(postBody)) {
            // IOException ici = réseau tombé APRÈS un login servi par le cache d'auth
            // (fenêtre TTL 5 min) : non rattrapée, elle remontait par l'async
            // d'enrichirAvecOptions jusqu'au launch de NouvelleVisiteFragment → crash à
            // l'ouverture du formulaire (audit 2026-08-23). On dégrade vers la tentative
            // suivante puis le repli cache, comme les autres échecs réseau de la boucle.
            val reponse = try {
                if (body != null) clients.postJson(urlFinale, body, token, cookies, 15000)
                else clients.get(urlFinale, token, cookies, 15000)
            } catch (_: IOException) {
                continue
            }
            val httpCode = reponse.status.value
            if (httpCode != 200) continue
            val text = try { reponse.bodyAsText() } catch (_: IOException) { continue }
            // Réponse soit array direct, soit objet contenant data_path → array.
            val array: JSONArray = text.parserTableauJson(*clesArray) ?: continue
            val options = extraireOptions(array, keyValue, keyLabel, prop.filtres, ordre = prop.ordreOptions)
            // Write-through : mémorise la réponse brute pour l'usage OFFLINE (nouvelle visite). Une
            // réponse vide fait foi, jeux de données compris : resservir un cache périmé proposerait
            // des valeurs retirées côté serveur, ou des jeux où l'utilisateur ne peut plus créer.
            cache.setJson(cleCache, text)
            return@withContext options
        }
        // Échec réseau/HTTP : la liste exacte en cache.
        cacheOffline()
    }

    /** Les clés de valeur et de libellé d'une liste : celles du schéma, sinon celles que le web
     *  donne à une datalist (`value`, `label`) ; null sans libellé pour une autre liste. */
    private fun cles(prop: MonitoringPropertySchema): Pair<String, String>? {
        val estDatalist = prop.typeWidget.equals("datalist", ignoreCase = true)
        val keyLabel = prop.keyLabel ?: (if (estDatalist) "label" else return null)
        val keyValue = prop.keyValue ?: if (estDatalist) "value" else "id"
        return keyValue to keyLabel
    }

    /** La clé de cache d'une liste : son api, et le corps `data` d'une liste demandée en POST. */
    private fun cleCache(prop: MonitoringPropertySchema): String =
        prop.apiUrl.orEmpty() + (prop.donneesPost?.let { "#$it" } ?: "")

    companion object {

        /** Une liste d'utilisateurs d'UsersHub dans une api de datalist. */
        private val LISTE_OBSERVATEURS = Regex("""users/menu/(\d+)""")

        /** Les `type_util` résolus par leurs propres tables du [LabelResolver]. */
        private val TYPES_RESOLUS_A_PART = setOf("user", "nomenclature", "dataset", "taxonomy")

        /** `default_display_field_names` de gn_module_monitoring (config/generic/config.json), pour
         *  un protocole dont la config ne porte pas `display_field_names`. */
        private val CHAMPS_AFFICHAGE_PAR_DEFAUT = mapOf(
            "observer_list" to "nom_liste",
            "taxonomy_list" to "nom_liste",
            "sites_group" to "sites_group_name",
            "habitat" to "lb_hab_fr",
            "area" to "area_name",
            "municipality" to "nom_com_dept",
            "site" to "base_site_name",
            "module" to "module_label",
        )

        /** L'identifiant de chaque `type_util` dans les options de sa datalist, quand le champ ne
         *  déclare pas de `keyValue` (`id_field_name_dict` du serveur). */
        private val CLES_PAR_DEFAUT = mapOf(
            "observer_list" to "id_liste",
            "taxonomy_list" to "id_liste",
            "sites_group" to "id_sites_group",
            "habitat" to "cd_hab",
            "area" to "id_area",
            "municipality" to "id_area",
            "site" to "id_base_site",
            "module" to "id_module",
        )

        /** Les entrées [entrees] projetées (valeur de [cle] → valeur du premier [libelle] renseigné). */
        private fun projeter(entrees: JSONArray, cle: String, vararg libelle: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            for (i in 0 until entrees.length()) {
                val item = entrees.optJSONObject(i) ?: continue
                val id = item.opt(cle)?.takeIf { it != JSONObject.NULL }?.toString()?.takeIf { it.isNotEmpty() } ?: continue
                val lbl = libelle.flatMap { it.split(',') }.firstNotNullOfOrNull { k ->
                    item.opt(k.trim())?.takeIf { it != JSONObject.NULL }?.toString()?.takeIf { it.isNotEmpty() }
                } ?: continue
                map[id] = lbl
            }
            return map
        }

        /** Les options d'une liste reçue du serveur, comme le composant datalist du formulaire web :
         *  - libellé [keyLabel], qui peut nommer plusieurs clés séparées par des virgules (la
         *    première non vide l'emporte) ;
         *  - doublons de [keyValue] retirés ;
         *  - [filtres] appliqués clé par clé, sur n'importe quelle clé de l'objet, dans l'ordre de
         *    leurs valeurs ;
         *  - [cdNomenclatures] : restriction d'une nomenclature, pour une liste en cache que le
         *    serveur n'a pas déjà restreinte ;
         *  - triées par libellé si [ordre] (`asc`, `desc`) le demande, sinon dans l'ordre du serveur. */
        internal fun extraireOptions(
            array: JSONArray,
            keyValue: String,
            keyLabel: String,
            filtres: Map<String, List<String>>,
            cdNomenclatures: List<String> = emptyList(),
            ordre: String? = null,
        ): List<OptionDatalist> {
            fun texte(item: JSONObject, cle: String) = item.opt(cle)?.toString()?.takeIf { it.isNotEmpty() && it != "null" }
            val vues = HashSet<String>()
            var items = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
                .filter { item -> texte(item, keyValue)?.let(vues::add) == true }
            for ((cle, valeurs) in filtres) {
                if (valeurs.isEmpty()) continue
                items = valeurs.mapNotNull { f -> items.firstOrNull { texte(it, cle) == f } }
            }
            if (cdNomenclatures.isNotEmpty()) items = items.filter { texte(it, "cd_nomenclature") in cdNomenclatures }
            val clesLibelle = keyLabel.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val options = items.mapNotNull { item ->
                val libelle = clesLibelle.firstNotNullOfOrNull { texte(item, it) } ?: return@mapNotNull null
                OptionDatalist(texte(item, keyValue)!!, libelle, texte(item, "cd_nomenclature"), texte(item, "label_default"), item.opt(keyValue))
            }
            val comparateur = comparateurFrancais()
            return when (ordre?.lowercase()) {
                "asc" -> options.sortedWith { a, b -> comparateur.compare(a.label, b.label) }
                "desc" -> options.sortedWith { a, b -> comparateur.compare(b.label, a.label) }
                else -> options
            }
        }
    }
}
