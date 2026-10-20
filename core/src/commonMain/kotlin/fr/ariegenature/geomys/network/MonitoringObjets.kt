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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/MonitoringObjets.kt) :
// HttpURLConnection remplacé par ClientsGeoNature, org.json par son portage, String.format par
// formaterDecimal. L'objet devient une classe sur l'authentification, les clients et le cache
// Monitoring ; ses fonctions pures passent dans son compagnon.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.network.MonitoringApi.MonitoringEnfant
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringObjet
import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.get
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.MonitoringCache
import fr.ariegenature.geomys.util.formaterDecimal
import io.ktor.client.statement.bodyAsText
import kotlin.math.abs
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

/** Objets de la hiérarchie monitoring (sites, groupes de sites, visites, observations…) :
 *  drill-down enfants/fiches via `/api/monitorings/object/…` (+ cache disque), aplatissement
 *  des propriétés, heuristiques de nommage, labels/genres/chemins résolus depuis le cache
 *  local, formatage de géométrie et tri des listes. Extrait de [MonitoringApi] (découpage
 *  du god-object) — [MonitoringApi.chargerEnfants] et [MonitoringApi.chargerObjet] restent
 *  les façades appelées par les écrans. */
class MonitoringObjets(
    private val auth: GeoNatureAuth,
    private val clients: ClientsGeoNature,
    private val cache: MonitoringCache,
) {

    /** Le schéma en cache de chaque module, analysé une fois : (JSON brut, objet). Les libellés et
     *  genres des types se lisent ligne à ligne (le « Nouvelle visite » de chaque site, les lignes de
     *  « Mes visites ») ; analyser le schéma à chaque lecture coûtait 6 ms par ligne sous iOS, soit
     *  2 s pour un protocole de 300 sites (mesuré le 20 octobre). Le JSON brut sert de clé :
     *  [MonitoringCache] rend la même chaîne tant que le schéma n'est pas réécrit. */
    private val schemasAnalyses = HashMap<String, Pair<String, JSONObject>>()
    private val verrouSchemas = SynchronizedObject()

    private fun schemaEnCache(moduleCode: String): JSONObject? {
        val json = cache.getJson(cache.keySchema(moduleCode)) ?: return null
        synchronized(verrouSchemas) { schemasAnalyses[moduleCode] }
            ?.let { (brut, schema) -> if (brut === json) return schema }
        val schema = try { JSONObject(json) } catch (_: Exception) { return null }
        synchronized(verrouSchemas) { schemasAnalyses[moduleCode] = json to schema }
        return schema
    }

    /** GET /api/monitorings/object/<module_code>/module?depth=1 — récupère le module et la liste
     *  de ses enfants directs avec leurs propriétés brutes. Map `object_type → [enfants]`, map
     *  vide si aucun enfant. **Throw** [GNErreur.AuthEchouee] si l'auth tombe, et
     *  [GNErreur.EnvoiEchoue] avec le code HTTP sur 403/404/500 — l'appelant peut humaniser via
     *  [humaniserErreurReseau]. Réservé à la synchro : les écrans lisent le cache ([lireEnfants]). */
    suspend fun chargerEnfants(config: GeoNatureConfig, moduleCode: String): Map<String, List<MonitoringEnfant>> =
        withContext(Dispatchers.IO) {
            val base = config.urlServeur.trim().trimEnd('/')
            val text = try {
                val session = auth.loginAvecCookies(base, config.login, config.motDePasse)
                if (session == null) {
                    cache.getJson(cache.keyEnfants(moduleCode)) ?: throw GNErreur.AuthEchouee(401)
                } else {
                    val (token, _, cookies) = session
                    val reponse = clients.get("$base/api/monitorings/object/$moduleCode/module?depth=1", token, cookies, 15000)
                    val code = reponse.status.value
                    if (code != 200) throw GNErreur.EnvoiEchoue(code, "Enfants du module $moduleCode")
                    val brut = reponse.bodyAsText()
                    // Valider AVANT de mettre en cache : un 200 non-JSON (portail captif, page
                    // d'erreur proxy) écrit tel quel empoisonnait le cache offline — resservi à
                    // chaque repli jusqu'au prochain fetch réussi (audit N6).
                    runCatching { JSONObject(brut) }.getOrNull()?.let {
                        cache.setJson(cache.keyEnfants(moduleCode), brut)
                    }
                    brut
                }
            } catch (e: IOException) {
                cache.getJson(cache.keyEnfants(moduleCode)) ?: throw e
            }
            val obj = try { JSONObject(text) } catch (_: Exception) {
                throw GNErreur.EnvoiEchoue(0, "Enfants $moduleCode : JSON illisible")
            }
            enfantsDe(obj)
        }

    /** Les enfants directs du module pour les écrans : ceux que la synchro a mis en cache, sans
     *  requête (décision de l'utilisateur du 20 octobre : rien n'est téléchargé hors synchro).
     *  Null s'ils n'y sont pas : protocole jamais synchronisé. */
    suspend fun lireEnfants(moduleCode: String): Map<String, List<MonitoringEnfant>>? =
        withContext(Dispatchers.IO) {
            val json = cache.getJson(cache.keyEnfants(moduleCode)) ?: return@withContext null
            try { enfantsDe(JSONObject(json)) } catch (_: Exception) { null }
        }

    /** Résout le label "humain" d'un objet serveur à partir de la fiche en cache local.
     *  Utilisé par la liste des saisies en attente pour afficher le nom du parent au lieu
     *  de "#id". Retourne null si la fiche n'a pas été mise en cache, ou si l'heuristique
     *  ne trouve qu'un fallback "#id". Pas d'appel réseau. */
    fun labelObjetEnCache(moduleCode: String, objectType: String, id: Int): String? {
        if (id <= 0) return null
        val json = cache.getJson(cache.keyObjet(moduleCode, objectType, id))
            ?: return null
        return try {
            val obj = JSONObject(json)
            val props = aplatirProprietes(obj.optJSONObject("properties"))
            val nom = extraireNomHeuristique(props, objectType, id)
            nom.takeIf { it != "#$id" && it != "—" }
        } catch (_: Exception) { null }
    }

    /** Lit dans le schéma cache de [moduleCode] le `label` humain d'un type d'objet (par ex.
     *  "Site", "Station", "Visite"). Retourne null si le schéma n'est pas en cache ou si le
     *  type n'a pas de label déclaré. Pas d'appel réseau. */
    fun labelTypeEnCache(moduleCode: String, type: String): String? {
        val v = schemaEnCache(moduleCode)?.optJSONObject(type) ?: return null
        return v.optString("label", "").takeIf { it.isNotEmpty() }
            ?: v.optString("label_list", "").takeIf { it.isNotEmpty() }
    }

    /** Lit dans le schéma cache de [moduleCode] le `genre` ("M"/"F") d'un type d'objet, pour
     *  accorder les phrases (« cette visite » / « ce passage »). Null si absent. Pas de réseau. */
    fun genreTypeEnCache(moduleCode: String, type: String): String? =
        schemaEnCache(moduleCode)?.optJSONObject(type)?.optString("genre", "")?.takeIf { it.isNotEmpty() }

    /** Libellé d'action « Nouvelle visite » / « Nouveau passage » / « Nouvel inventaire » pour
     *  créer un objet de type [type] dans [moduleCode], accordé au genre déclaré par le schéma
     *  (label serveur via [labelTypeEnCache], genre via [genreTypeEnCache]). Repli « Nouvelle
     *  entrée » si le type n'a pas de label en cache. Sert aux écrans carte/fiche/suivi. */
    fun libelleNouveau(moduleCode: String, type: String): String {
        val mot = labelTypeEnCache(moduleCode, type)?.lowercase() ?: return "Nouvelle entrée"
        val masculin = genreTypeEnCache(moduleCode, type).equals("M", ignoreCase = true)
        val voyelle = mot.firstOrNull()?.let {
            it in setOf('a', 'à', 'â', 'e', 'é', 'è', 'ê', 'i', 'î', 'o', 'ô', 'u', 'ù', 'û', 'h', 'y')
        } == true
        val adj = when { !masculin -> "Nouvelle"; voyelle -> "Nouvel"; else -> "Nouveau" }
        return "$adj $mot"
    }

    /** Lit dans le schéma cache de [moduleCode] le couple (parent_type, id_field_name du
     *  parent) pour un type donné. Retourne null si le schéma n'est pas dispo ou si l'objet
     *  n'a pas de parent. L'idFieldName est porté par le SCHÉMA DU PARENT — c'est le nom de
     *  la propriété qui contient l'id du parent dans la fiche enfant. */
    private fun parentTypeEtIdField(moduleCode: String, type: String): Pair<String, String>? {
        val schema = schemaEnCache(moduleCode) ?: return null
        val v = schema.optJSONObject(type) ?: return null
        val parentType = v.optString("parent_type", "")
            .ifEmpty { v.optJSONArray("parent_types")?.optString(0, "").orEmpty() }
            .takeIf { it.isNotEmpty() } ?: return null
        val parentObj = schema.optJSONObject(parentType) ?: return null
        val idField = parentObj.optString("id_field_name", "")
            .takeIf { it.isNotEmpty() } ?: return null
        return parentType to idField
    }

    /** Remonte la chaîne des ancêtres serveur d'un objet en s'appuyant uniquement sur le
     *  cache local (schéma + fiches). Retourne la liste des (type, label) du parent direct
     *  vers le plus haut ancêtre. Liste vide si l'objet n'a pas de parent ou si le cache ne
     *  permet pas de résoudre la chaîne. Safety net à 5 niveaux pour éviter une boucle. */
    fun chaineParentsEnCache(
        moduleCode: String,
        objectType: String,
        id: Int,
    ): List<Triple<String, Int, String>> {
        val result = mutableListOf<Triple<String, Int, String>>()
        var typeCourant = objectType
        var idCourant = id
        val visites = mutableSetOf<Pair<String, Int>>()
        var profondeur = 0
        while (visites.add(typeCourant to idCourant) && profondeur++ < 5) {
            val (parentType, idField) = parentTypeEtIdField(moduleCode, typeCourant) ?: break
            val ficheJson = cache.getJson(
                cache.keyObjet(moduleCode, typeCourant, idCourant),
            ) ?: break
            val parentIdStr = try {
                val props = aplatirProprietes(JSONObject(ficheJson).optJSONObject("properties"))
                props[idField]
            } catch (_: Exception) { null } ?: break
            val parentId = parentIdStr.toIntOrNull() ?: break
            val label = labelObjetEnCache(moduleCode, parentType, parentId)
                ?: "$parentType #$parentId"
            result.add(Triple(parentType, parentId, label))
            typeCourant = parentType
            idCourant = parentId
        }
        return result
    }

    /** GET /api/monitorings/object/<module_code>/<object_type>/<id>?depth=1 — fiche d'un objet
     *  (site, visite, observation, …) avec ses propriétés et ses enfants directs.
     *  **Throw** [GNErreur.AuthEchouee] sur défaut d'auth, [GNErreur.EnvoiEchoue] sur HTTP != 200
     *  ou parse cassé (le code est ainsi exploitable côté UI pour humaniser le message). Réservé à
     *  la synchro, et à la fiche d'une saisie (visite, observation), que la synchro ne charge pas :
     *  ouverte avec du réseau, elle se met à jour ainsi après s'être affichée depuis le cache
     *  (demande de l'utilisateur du 20 octobre). Les autres écrans lisent le cache ([lireObjet]). */
    suspend fun chargerObjet(
        config: GeoNatureConfig,
        moduleCode: String,
        objectType: String,
        id: Int,
    ): MonitoringObjet =
        withContext(Dispatchers.IO) {
            val base = config.urlServeur.trim().trimEnd('/')
            val text = try {
                val session = auth.loginAvecCookies(base, config.login, config.motDePasse)
                if (session == null) {
                    cache.getJson(cache.keyObjet(moduleCode, objectType, id))
                        ?: throw GNErreur.AuthEchouee(401)
                } else {
                    val (token, _, cookies) = session
                    val reponse = clients.get("$base/api/monitorings/object/$moduleCode/$objectType/$id?depth=1", token, cookies, 15000)
                    val code = reponse.status.value
                    if (code != 200) throw GNErreur.EnvoiEchoue(code, "$objectType #$id")
                    val brut = reponse.bodyAsText()
                    // Valider AVANT de cacher — même protection anti-portail-captif que
                    // chargerEnfants (audit N6).
                    runCatching { JSONObject(brut) }.getOrNull()?.let {
                        cache.setJson(cache.keyObjet(moduleCode, objectType, id), brut)
                    }
                    brut
                }
            } catch (e: IOException) {
                cache.getJson(cache.keyObjet(moduleCode, objectType, id)) ?: throw e
            }
            val obj = try { JSONObject(text) } catch (_: Exception) {
                throw GNErreur.EnvoiEchoue(0, "$objectType #$id : JSON illisible")
            }
            objetDe(obj, moduleCode, objectType, id)
        }

    /** La fiche d'un objet pour les écrans : celle que la synchro (ou un envoi, cf.
     *  [reporterObjetCree]) a mise en cache, sans requête. Null si elle n'y est pas : la synchro ne
     *  charge que les objets structurels (sites, groupes…), pas l'historique des visites. */
    suspend fun lireObjet(moduleCode: String, objectType: String, id: Int): MonitoringObjet? =
        withContext(Dispatchers.IO) {
            val json = cache.getJson(cache.keyObjet(moduleCode, objectType, id)) ?: return@withContext null
            try { objetDe(JSONObject(json), moduleCode, objectType, id) } catch (_: Exception) { null }
        }

    /** La fiche d'un objet absent du cache, reconstituée depuis sa ligne chez son parent ([parentType]
     *  null : le module) : ses propriétés et sa géométrie, sans ses enfants, que la synchro ne charge
     *  pas (une visite passée et ses observations). Null si le parent n'est pas en cache ou ne la
     *  porte pas. */
    suspend fun lireObjetDepuisParent(
        moduleCode: String,
        objectType: String,
        id: Int,
        parentType: String?,
        parentId: Int,
    ): MonitoringObjet? = withContext(Dispatchers.IO) {
        val parent = cache.getJson(cleParent(moduleCode, parentType, parentId) ?: return@withContext null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return@withContext null
        val ligne = parent.optJSONObject("children")?.optJSONArray(objectType)?.let { lignes ->
            (0 until lignes.length()).mapNotNull { lignes.optJSONObject(it) }
                .firstOrNull { it.optInt("id", it.optInt("${objectType}_id", -1)) == id }
        } ?: return@withContext null
        objetDe(ligne, moduleCode, objectType, id).copy(enfants = emptyMap(), enfantsConnus = false)
    }

    /** Reporte dans le cache un objet que l'appli vient de créer sur le serveur (visite, observation…),
     *  pour que les écrans le montrent sans attendre la synchro suivante (décision de l'utilisateur
     *  du 20 octobre : ce que l'appli modifie est répercuté dans le cache) : sa fiche, sans enfants,
     *  si elle n'y est pas déjà, et sa ligne parmi les enfants de son parent ([parentType] null ou
     *  « module » : les enfants directs du protocole), remplacée si elle y est déjà (nouvel essai).
     *  [proprietes] et [geometrie] : l'objet tel qu'il a été envoyé. Un parent absent du cache est
     *  laissé tel quel. */
    fun reporterObjetCree(
        moduleCode: String,
        objectType: String,
        id: Int,
        proprietes: JSONObject,
        geometrie: JSONObject?,
        parentType: String?,
        parentId: Int,
    ) {
        if (id <= 0) return
        fun ligne() = JSONObject().put("id", id).put("properties", proprietes).also { o ->
            if (geometrie != null) o.put("geometry", geometrie)
        }
        val cleFiche = cache.keyObjet(moduleCode, objectType, id)
        if (cache.getJson(cleFiche) == null) {
            cache.setJson(cleFiche, ligne().put("object_type", objectType).put("children", JSONObject()).toString())
        }
        val cle = cleParent(moduleCode, parentType, parentId) ?: return
        val parent = cache.getJson(cle)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        val enfants = parent.optJSONObject("children") ?: JSONObject().also { parent.put("children", it) }
        val lignes = enfants.optJSONArray(objectType) ?: JSONArray().also { enfants.put(objectType, it) }
        val rang = (0 until lignes.length()).firstOrNull { lignes.optJSONObject(it)?.optInt("id", -1) == id }
        if (rang != null) lignes.put(rang, ligne()) else lignes.put(ligne())
        cache.setJson(cle, parent.toString())
    }

    /** Le fichier du cache qui porte les enfants d'un parent : la fiche de l'objet, ou les enfants
     *  directs du module ([parentType] null ou « module »). */
    private fun cleParent(moduleCode: String, parentType: String?, parentId: Int): String? = when {
        parentType == null || parentType == "module" -> cache.keyEnfants(moduleCode)
        parentId > 0 -> cache.keyObjet(moduleCode, parentType, parentId)
        else -> null
    }

    companion object {

        /** Les enfants directs d'un objet ou du module (`children`), par type, dans l'ordre du JSON. */
        internal fun enfantsDe(obj: JSONObject): Map<String, List<MonitoringEnfant>> {
            val children = obj.optJSONObject("children") ?: return emptyMap()
            val result = linkedMapOf<String, List<MonitoringEnfant>>()
            val it = children.keys()
            while (it.hasNext()) {
                val type = it.next()
                val arr = children.optJSONArray(type) ?: continue
                val liste = mutableListOf<MonitoringEnfant>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optInt("id", item.optInt("${type}_id", -1))
                    val proprietes = aplatirProprietes(item.optJSONObject("properties"))
                    val nom = extraireNomHeuristique(proprietes, type, id)
                    val geoJson = item.optJSONObject("geometry")?.toString()
                    liste.add(MonitoringEnfant(id, nom, proprietes, geoJson))
                }
                result[type] = liste
            }
            return result
        }

        /** La fiche d'un objet : ses propriétés, ses enfants directs et sa géométrie. */
        internal fun objetDe(obj: JSONObject, moduleCode: String, objectType: String, id: Int): MonitoringObjet {
            val geoObj = obj.optJSONObject("geometry")
            return MonitoringObjet(
                type = objectType,
                id = id,
                moduleCode = moduleCode,
                proprietes = aplatirProprietes(obj.optJSONObject("properties")),
                enfants = enfantsDe(obj),
                geometrie = formatGeometrie(geoObj),
                geometrieGeoJson = geoObj?.toString(),
            )
        }

        /** Aplatit le bloc `properties` d'un objet monitoring en `Map<String, String>`. Filtre les
         *  valeurs null/objets/tableaux : on garde uniquement les scalaires (string, number, bool)
         *  utiles à l'affichage. */
        internal fun aplatirProprietes(props: JSONObject?): Map<String, String> {
            if (props == null) return emptyMap()
            val map = linkedMapOf<String, String>()
            val it = props.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = props.opt(k)
                when (v) {
                    null, JSONObject.NULL -> { /* skip */ }
                    is String -> if (v.isNotEmpty()) map[k] = v
                    is Number, is Boolean -> map[k] = v.toString()
                    else -> { /* on ignore arrays/objects pour l'instant */ }
                }
            }
            return map
        }

        /** Fallback heuristique pour le nom d'un enfant quand le schéma n'est pas disponible.
         *  Essaie `base_<type>_name`, `<type>_name`, `base_site_name`, `name`, `label`. Sinon `#id`. */
        internal fun extraireNomHeuristique(proprietes: Map<String, String>, type: String, id: Int): String {
            val candidats = listOf("base_${type}_name", "${type}_name", "base_site_name", "name", "label")
            for (c in candidats) proprietes[c]?.takeIf { it.isNotEmpty() }?.let { return it }
            return if (id > 0) "#$id" else "—"
        }

        /** Convertit un objet GeoJSON en libellé court pour affichage. Point → "lat°N/S, lon°E/W",
         *  Polygon/MultiPolygon → "Polygone (N sommets)", autres → le type GeoJSON brut. */
        internal fun formatGeometrie(geo: JSONObject?): String? {
            if (geo == null) return null
            val type = geo.optString("type", "").ifEmpty { return null }
            val coords = geo.opt("coordinates")
            return when (type) {
                "Point" -> {
                    val arr = coords as? JSONArray ?: return type
                    if (arr.length() < 2) return type
                    val lon = arr.optDouble(0, Double.NaN)
                    val lat = arr.optDouble(1, Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) return type
                    val ns = if (lat >= 0) "N" else "S"
                    val ew = if (lon >= 0) "E" else "W"
                    "${formaterDecimal(abs(lat), 5)}° $ns, ${formaterDecimal(abs(lon), 5)}° $ew"
                }
                "Polygon" -> {
                    val nb = (coords as? JSONArray)?.optJSONArray(0)?.length() ?: 0
                    if (nb > 0) "Polygone ($nb sommets)" else "Polygone"
                }
                "MultiPolygon" -> "MultiPolygone"
                "LineString" -> {
                    val nb = (coords as? JSONArray)?.length() ?: 0
                    if (nb > 0) "Ligne ($nb points)" else "Ligne"
                }
                "MultiPoint" -> {
                    val nb = (coords as? JSONArray)?.length() ?: 0
                    if (nb > 0) "$nb points" else "MultiPoint"
                }
                else -> type
            }
        }

        /** Trie une liste d'enfants selon les critères déclarés par le schéma (champ `sorts`).
         *  Tente une comparaison numérique d'abord, fallback comparaison de strings (les dates ISO
         *  YYYY-MM-DD se trient correctement en string). Sans critères : tri alpha par nom. */
        fun trierEnfants(enfants: List<MonitoringEnfant>, sorts: List<Pair<String, String>>): List<MonitoringEnfant> {
            if (sorts.isEmpty()) return enfants.sortedBy { it.nom.lowercase() }
            val comp = Comparator<MonitoringEnfant> { a, b ->
                var r = 0
                for ((prop, dir) in sorts) {
                    val va = a.proprietes[prop] ?: ""
                    val vb = b.proprietes[prop] ?: ""
                    val da = va.toDoubleOrNull()
                    val db = vb.toDoubleOrNull()
                    r = if (da != null && db != null) da.compareTo(db) else va.compareTo(vb)
                    if (dir == "desc") r = -r
                    if (r != 0) break
                }
                r
            }
            return enfants.sortedWith(comp)
        }
    }
}
