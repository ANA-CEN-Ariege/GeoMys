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

import fr.ariegenature.geomys.network.MonitoringApi.LabelResolver
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringPropertySchema
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.MonitoringApi.OptionDatalist
import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.get
import fr.ariegenature.geomys.network.http.postJson
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.MonitoringCache
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.io.listeDepuisJson
import fr.ariegenature.geomys.util.essayer
import io.ktor.client.statement.bodyAsText
import io.ktor.http.decodeURLQueryComponent
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
     *  (`type_util` user, nomenclature, dataset) traduits par les listes que la synchro a mises en
     *  cache — observateurs de la liste du protocole, options des nomenclatures et des jeux de
     *  données de ses formulaires, jeux de données OccTax et OccHab. Un identifiant sans libellé en
     *  cache reste affiché tel quel. */
    suspend fun lireResolveurLabels(config: GeoNatureConfig, schema: Map<String, MonitoringSchemaObjet>): LabelResolver =
        withContext(Dispatchers.IO) {
            val proprietes = schema.values.flatMap { it.properties.values }
            /** Les entrées d'une liste en cache ([prop] : son api), projetées (clé → libellé). */
            fun libelles(prop: MonitoringPropertySchema, cle: String, vararg libelle: String): Map<String, String> {
                val entrees = listeEnCache(prop) ?: return emptyMap()
                val map = mutableMapOf<String, String>()
                for (i in 0 until entrees.length()) {
                    val item = entrees.optJSONObject(i) ?: continue
                    val id = item.opt(cle)?.toString()?.takeIf { it.isNotEmpty() } ?: continue
                    val lbl = libelle.firstNotNullOfOrNull { k -> item.opt(k)?.toString()?.takeIf { it.isNotEmpty() } } ?: continue
                    map[id] = lbl
                }
                return map
            }
            val nomenclatures = mutableMapOf<String, MutableMap<String, String>>()
            proprietes.filter { it.typeUtil == "nomenclature" }.forEach { p ->
                val code = p.nomenclatureType ?: return@forEach
                nomenclatures.getOrPut(code) { mutableMapOf() } += libelles(p, "id_nomenclature", "label_fr", "label_default")
            }
            val idListObserver = schema["module"]?.idListObserver
            val users = if (proprietes.any { it.typeUtil == "user" } && idListObserver != null) {
                val arr = lireObservateursDeListe(idListObserver)
                buildMap {
                    for (i in 0 until (arr?.length() ?: 0)) {
                        val item = arr?.optJSONObject(i) ?: continue
                        val id = item.opt("id_role")?.toString()?.takeIf { it.isNotEmpty() } ?: continue
                        val lbl = item.opt("nom_complet")?.toString()?.takeIf { it.isNotEmpty() } ?: continue
                        put(id, lbl)
                    }
                }
            } else emptyMap()
            val datasets = mutableMapOf<String, String>()
            if (proprietes.any { it.typeUtil == "dataset" }) {
                listOf(config.datasetsCacheJson, config.datasetsOcchabCacheJson).filter { it.isNotEmpty() }.forEach { json ->
                    runCatching { listeDepuisJson(GeoNatureDataset.serializer(), json) }.getOrNull()
                        ?.forEach { datasets[it.id.toString()] = it.nom }
                }
                proprietes.filter { it.apiUrl?.startsWith("meta/datasets") == true || it.typeWidget.equals("dataset", ignoreCase = true) }
                    .forEach { datasets += libelles(it, "id_dataset", "dataset_name") }
            }
            LabelResolver(nomenclatures = nomenclatures, users = users, datasets = datasets, taxRef = taxRef)
        }

    /** La liste en cache d'une datalist ([prop] : son api), telle que la synchro l'a reçue. */
    private fun listeEnCache(prop: MonitoringPropertySchema): JSONArray? {
        val apiPath = prop.apiUrl ?: return null
        val cles = (listOfNotNull(prop.dataPath) + listOf("values", "data", "items", "results")).toTypedArray()
        return cache.getJson(cache.keyOptionsDatalist(apiPath))?.let { runCatching { it.parserTableauJson(*cles) }.getOrNull() }
    }

    /** Jeux de données ACTIFS rattachés à [moduleCode], demandés au serveur (le cache OCCTAX ne les
     *  contient pas — synchro filtrée `module_code=OCCTAX`) et mis en cache. Essaie les deux casses du
     *  code module (routes Flask sensibles à la casse). Renvoie le tableau brut (id_dataset,
     *  id_taxa_list…) non vide, ou null si injoignable ou vide, le cache restant alors tel quel.
     *  Réservé à la synchro et à l'envoi d'une visite : les écrans lisent le cache
     *  ([lireDatasetsDuModule]). */
    suspend fun chargerDatasetsDuModule(config: GeoNatureConfig, moduleCode: String): JSONArray? = try {
        val base = config.urlServeur.trim().trimEnd('/')
        val session = auth.loginAvecCookies(base, config.login, config.motDePasse)
        if (session == null) {
            null
        } else {
            val (token, _, cookies) = session
            var trouve: JSONArray? = null
            for (variante in listOf(moduleCode, moduleCode.lowercase()).distinct()) {
                val reponse = clients.get("$base/api/meta/datasets?module_code=$variante&active=true&fields=modules", token, cookies, timeoutMs = 10_000)
                if (reponse.status.value != 200) continue
                val arr = reponse.bodyAsText().parserTableauJson("data") ?: continue
                if (arr.length() > 0) {
                    trouve = arr
                    break
                }
            }
            trouve?.also { cache.setJson(cache.keyDatasetsModule(moduleCode), it.toString()) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Les jeux de données du protocole en cache ([chargerDatasetsDuModule]), sans requête ; null
     *  s'ils n'y sont pas. */
    fun lireDatasetsDuModule(moduleCode: String): JSONArray? =
        cache.getJson(cache.keyDatasetsModule(moduleCode))?.let { runCatching { JSONArray(it) }.getOrNull() }

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
     *  mises en cache ([chargerOptionsDatalist]), sans requête ; à défaut, pour un jeu de données,
     *  ceux du module dans le cache des jeux OccTax. Null si rien n'est en cache. */
    suspend fun lireOptionsDatalist(config: GeoNatureConfig, prop: MonitoringPropertySchema): List<OptionDatalist>? =
        withContext(Dispatchers.IO) {
            val apiPath = prop.apiUrl ?: return@withContext null
            val keyLabel = prop.keyLabel ?: return@withContext null
            val keyValue = prop.keyValue ?: "id"
            if (prop.application != null && prop.application != "GeoNature") return@withContext null
            Regex("""users/menu/(\d+)""").find(apiPath)?.let { obs ->
                val arr = lireObservateursDeListe(obs.groupValues[1].toIntOrNull() ?: return@withContext null)
                    ?: return@withContext null
                return@withContext extraireOptions(arr, keyValue, keyLabel, prop.filtres)
            }
            optionsEnCache(prop, keyValue, keyLabel) ?: datasetsDuModuleEnCache(config, prop, apiPath)
        }

    /** Les options en cache d'une datalist, null si absentes ou vides. */
    private fun optionsEnCache(prop: MonitoringPropertySchema, keyValue: String, keyLabel: String): List<OptionDatalist>? =
        listeEnCache(prop)?.let { extraireOptions(it, keyValue, keyLabel, prop.filtres) }?.takeIf { it.isNotEmpty() }

    /** Repli d'un champ jeu de données : les jeux du module dans le cache des jeux OccTax (non filtré
     *  CRUVED, donc en dernier ressort). Null hors champ jeu de données ou sans jeu du module. */
    private fun datasetsDuModuleEnCache(config: GeoNatureConfig, prop: MonitoringPropertySchema, apiPath: String): List<OptionDatalist>? {
        if (!apiPath.startsWith("meta/datasets") && !prop.typeWidget.equals("dataset", ignoreCase = true)) return null
        val moduleCodeFiltre = Regex("""module_code=([^&]+)""").find(apiPath)?.groupValues?.get(1)
            ?: prop.moduleCodeFiltre ?: return null
        val cacheDatasets = config.datasetsCacheJson.takeIf { it.isNotEmpty() } ?: return null
        val associes = listeDepuisJson(GeoNatureDataset.serializer(), cacheDatasets).filter { moduleCodeFiltre in it.moduleCodes }
        if (associes.isEmpty()) return null
        return associes.map { OptionDatalist(it.id.toString(), it.nom) }.sortedBy { it.label.lowercase() }
    }

    /** Fetch dynamiquement les options d'un widget `datalist` ou `nomenclature` (forme ancienne).
     *  Construit l'URL `<base>/api/<api>`, parse selon `dataPath` (si fourni) → array d'objets,
     *  puis projette chaque entrée sur (keyValue, keyLabel). Renvoie null sur erreur HTTP/auth/parse.
     *  Application "TaxHub" non supportée pour l'instant — renvoie null avec log. Réservé à la
     *  synchro, qui en remplit le cache : les écrans le lisent ([lireOptionsDatalist]). */
    suspend fun chargerOptionsDatalist(
        config: GeoNatureConfig,
        prop: MonitoringPropertySchema,
    ): List<OptionDatalist>? = withContext(Dispatchers.IO) {
        val apiPath = prop.apiUrl ?: return@withContext null
        val keyLabel = prop.keyLabel ?: return@withContext null
        val keyValue = prop.keyValue ?: "id"
        if (prop.application != null && prop.application != "GeoNature") {
            return@withContext null // TaxHub à supporter plus tard
        }
        // Cas spécial observateurs : on passe par le helper qui gère le cache disque
        // (fetch live → cache → réutilisable en offline). Détection par préfixe d'URL.
        val matchObs = Regex("""users/menu/(\d+)""").find(apiPath)
        if (matchObs != null) {
            val idListe = matchObs.groupValues[1].toIntOrNull() ?: return@withContext null
            val arr = chargerObservateursDeListe(config, idListe) ?: return@withContext null
            return@withContext extraireOptions(arr, keyValue, keyLabel, prop.filtres)
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
        // Fallback offline : si le fetch live échoue, on retombe en bas sur le cache filtré
        // par moduleCodes — meilleure approximation hors-ligne, à défaut de mieux.
        // Replis hors ligne : la liste exacte en cache, puis les jeux du module (jeux de données).
        fun fallbackDatasetCache(): List<OptionDatalist>? = datasetsDuModuleEnCache(config, prop, apiPath)
        val clesArray = (listOfNotNull(prop.dataPath) + listOf("values", "data", "items", "results")).toTypedArray()
        val cleCache = cache.keyOptionsDatalist(apiPath)
        fun cacheOffline(): List<OptionDatalist>? = optionsEnCache(prop, keyValue, keyLabel)
        val base = config.urlServeur.trim().trimEnd('/')
        // Hors-ligne (login impossible) : cache datalist EXACT d'abord — le cache datasets
        // d'Occtax n'est pas filtré CRUVED, il ne sert qu'en dernier ressort.
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
            ?: return@withContext cacheOffline() ?: fallbackDatasetCache()
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
            val urlStr = "$base/api/$chemin" + if (querysUrl.isNotEmpty()) "?${querysUrl.joinToString("&")}" else ""
            urlStr to paramsBody.toString()
        } else {
            "$base/api/$apiPath" to null
        }
        // Tentatives successives pour le dataset : body complet, puis SANS le filtre `create`
        // (CRUVED action C). Sur certaines instances, `create=<module>.<objet>` renvoie vide ou
        // échoue (objet de permission absent de la config du protocole) alors que des jeux de
        // données sont bien rattachés au module — le web ne s'en aperçoit pas car il masque le
        // champ à dataset unique sans appeler cette route. Champ requis vide = saisie bloquée,
        // on préfère élargir au module (parité avec ce que le protocole affiche en tête).
        val bodies: List<String?> = if (postBody != null && postBody.contains("\"create\"")) {
            val sansCreate = JSONObject(postBody).also { it.remove("create") }.toString()
            listOf(postBody, sansCreate)
        } else listOf(postBody)
        for ((index, body) in bodies.withIndex()) {
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
            val options = extraireOptions(array, keyValue, keyLabel, prop.filtres)
            // Dataset vide avec une tentative plus permissive encore disponible → on la joue.
            if (estDataset && options.isEmpty() && index < bodies.size - 1) {
                Journal.w("MonitoringApi",
                    "Datalist dataset vide avec filtre create — nouvelle tentative sans create")
                continue
            }
            if (options.isEmpty()) {
                // 200 valide mais 0 option — deux cas distincts (audit 2026-08-23) :
                // - dataset : quirk connu (filtre create/objet de permission), déjà retenté
                //   sans create ci-dessus → repli caches, en préférant le cache datalist
                //   EXACT (write-through, filtré CRUVED) au cache datasets d'Occtax (non
                //   filtré — l'utilisateur y verrait des JDD où il ne peut pas créer) ;
                // - autre datalist (nomenclatures…) : la réponse vide FAIT FOI — resservir
                //   un cache périmé re-proposerait indéfiniment des valeurs retirées côté
                //   serveur (options fantômes). On mémorise le vide et on le retourne.
                if (estDataset) return@withContext cacheOffline() ?: fallbackDatasetCache() ?: options
                cache.setJson(cleCache, text)
                return@withContext options
            }
            // Write-through : mémorise la réponse brute pour l'usage OFFLINE (nouvelle visite).
            cache.setJson(cleCache, text)
            return@withContext options
        }
        // Échec réseau/HTTP sur toutes les tentatives : cache datalist exact d'abord,
        // cache datasets grossier en dernier ressort (datasets seulement).
        cacheOffline() ?: fallbackDatasetCache()
    }

    companion object {

        /** Factorise la conversion d'un JSONArray d'objets en `List<OptionDatalist>` triée par
         *  label, avec application des filtres déclarés au schéma (ex: stade biologique
         *  restreint à ["Inconnu", "Chrysalide", …]). */
        private fun extraireOptions(
            array: JSONArray,
            keyValue: String,
            keyLabel: String,
            filtres: Map<String, List<String>>,
        ): List<OptionDatalist> {
            val opts = mutableListOf<OptionDatalist>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val v = item.opt(keyValue)?.toString().orEmpty()
                val l = item.opt(keyLabel)?.toString().orEmpty()
                if (v.isEmpty() || l.isEmpty()) continue
                val cdNom = item.opt("cd_nomenclature")?.toString()?.takeIf { it.isNotEmpty() }
                val lblDef = item.opt("label_default")?.toString()?.takeIf { it.isNotEmpty() }
                opts.add(OptionDatalist(v, l, cdNom, lblDef))
            }
            val filtrees = if (filtres.isEmpty()) opts else opts.filter { o ->
                filtres.all { (champ, valeursAcceptables) ->
                    val v = when (champ) {
                        "label_default" -> o.labelDefaut
                        "cd_nomenclature" -> o.cdNomenclature
                        else -> null
                    }
                    v == null || v in valeursAcceptables
                }
            }
            return filtrees.sortedBy { it.label.lowercase() }
        }
    }
}
