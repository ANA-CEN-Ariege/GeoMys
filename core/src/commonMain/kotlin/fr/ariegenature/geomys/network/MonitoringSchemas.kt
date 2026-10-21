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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/MonitoringSchemas.kt) :
// HttpURLConnection remplacé par ClientsGeoNature, org.json par son portage, URLDecoder par
// decodeURLQueryComponent. L'objet devient une classe sur l'authentification, les clients et le
// cache Monitoring ; le parsing, pur, passe dans son compagnon.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.network.MonitoringApi.MonitoringPropertySchema
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.get
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.MonitoringCache
import io.ktor.client.statement.bodyAsText
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLQueryComponent
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

/** Schémas déclaratifs des protocoles gn_module_monitoring : chargement du
 *  `/api/monitorings/config/<module_code>` (+ cache disque), fusion des blocs
 *  `generic`/`specific`, parsing des propriétés saisissables, substitution des
 *  placeholders `__MODULE.XXX` et dérivation des URLs d'API implicites
 *  (observers/dataset/taxonomy_list). Extrait de [MonitoringApi] (découpage du
 *  god-object) — [MonitoringApi.chargerSchemaProtocole] reste la façade appelée
 *  par les écrans. */
class MonitoringSchemas(
    private val auth: GeoNatureAuth,
    private val clients: ClientsGeoNature,
    private val cache: MonitoringCache,
) {

    /** GET /api/monitorings/config/<module_code> — récupère le schéma déclaratif du protocole
     *  (fichier objects.json côté serveur). Pour chaque object_type expose le label, le
     *  `name_field`, le `parent_type` et les `children`. Permet de driver l'affichage et la
     *  navigation au lieu d'utiliser des heuristiques. Null si l'endpoint échoue. Réservé à la
     *  synchro : les écrans lisent le cache ([lireSchemaProtocole]). */
    suspend fun chargerSchemaProtocole(config: GeoNatureConfig, moduleCode: String): Map<String, MonitoringSchemaObjet>? =
        withContext(Dispatchers.IO) {
            val base = config.urlServeur.trim().trimEnd('/')
            val text: String? = try {
                val session = auth.loginAvecCookies(base, config.login, config.motDePasse)
                if (session == null) {
                    // Auth en échec (offline ou serveur down) → fallback cache si présent.
                    cache.getJson(cache.keySchema(moduleCode))
                } else {
                    val (token, _, cookies) = session
                    val reponse = clients.get("$base/api/monitorings/config/$moduleCode", token, cookies, 10000)
                    val code = reponse.status.value
                    if (code != 200) {
                        // Fallback cache pour les erreurs serveur transitoires.
                        cache.getJson(cache.keySchema(moduleCode))
                    } else {
                        val brut = reponse.bodyAsText()
                        // VALIDÉ avant mise en cache : un 200 non-JSON (portail captif) servait
                        // ensuite un formulaire vide hors-ligne (audit 2026-08-27).
                        if (runCatching { JSONObject(brut) }.isSuccess) {
                            cache.setJson(cache.keySchema(moduleCode), brut)
                            brut
                        } else {
                            cache.getJson(cache.keySchema(moduleCode))
                        }
                    }
                }
            } catch (_: IOException) {
                cache.getJson(cache.keySchema(moduleCode))
            }
            if (text.isNullOrEmpty()) return@withContext null
            analyser(text, moduleCode)
        }

    /** Le schéma du protocole pour les écrans : celui que la synchro a mis en cache, sans requête
     *  (décision de l'utilisateur du 20 octobre : rien n'est téléchargé hors synchro). Null s'il n'y
     *  est pas. Analysé une fois par version du cache : les écrans le relisent à chaque ouverture. */
    suspend fun lireSchemaProtocole(moduleCode: String): Map<String, MonitoringSchemaObjet>? =
        withContext(Dispatchers.IO) {
            val json = cache.getJson(cache.keySchema(moduleCode)) ?: return@withContext null
            synchronized(verrouAnalyses) { analyses[moduleCode] }
                ?.let { (brut, schema) -> if (brut === json) return@withContext schema }
            val schema = analyser(json, moduleCode) ?: return@withContext null
            synchronized(verrouAnalyses) { analyses[moduleCode] = json to schema }
            schema
        }

    /** Les schémas déjà analysés, par module : (JSON brut, schéma). Le JSON brut sert de clé :
     *  [MonitoringCache] rend la même chaîne tant que le schéma n'est pas réécrit. */
    private val analyses = HashMap<String, Pair<String, Map<String, MonitoringSchemaObjet>>>()
    private val verrouAnalyses = SynchronizedObject()

    /** Le schéma [text] analysé : types d'objets, propriétés fusionnées, variables substituées. Null
     *  si le JSON est illisible. */
    private fun analyser(text: String, moduleCode: String): Map<String, MonitoringSchemaObjet>? {
        val obj = try { JSONObject(text) } catch (_: Exception) { return null }
        // Substitution des variables `__MODULE.XXX` (parité substituteVariables de
        // gn_mobile_monitoring) : certains schémas embarquent ces placeholders dans des
        // chaînes (api, id_list…). On les remplace par leur valeur lue dans le bloc `custom`.
        substituerVariablesModule(obj)
        val result = linkedMapOf<String, MonitoringSchemaObjet>()
        val it = obj.keys()
        while (it.hasNext()) {
            val type = it.next()
            val v = obj.optJSONObject(type) ?: continue
            val childrenArr = v.optJSONArray("children")
            val children = mutableListOf<String>()
            if (childrenArr != null) {
                for (i in 0 until childrenArr.length()) {
                    val s = childrenArr.optString(i, "")
                    if (s.isNotEmpty()) children.add(s)
                }
            }
            // gn_module_monitoring varie d'une version à l'autre : on essaie plusieurs noms
            // de clé pour le champ "nom".
            val nameField = v.optString("description_field_name", "")
                .ifEmpty { v.optString("display_field_name", "") }
                .ifEmpty { v.optString("name_field", "") }
                .takeIf { it.isNotEmpty() }
            val displayPropsArr = v.optJSONArray("display_properties")
            val displayProps = mutableListOf<String>()
            if (displayPropsArr != null) {
                for (i in 0 until displayPropsArr.length()) {
                    displayPropsArr.optString(i, "").takeIf { it.isNotEmpty() }?.let { displayProps.add(it) }
                }
            }
            val displayListArr = v.optJSONArray("display_list")
            val displayListNoms = mutableListOf<String>()
            if (displayListArr != null) {
                for (i in 0 until displayListArr.length()) {
                    displayListArr.optString(i, "").takeIf { it.isNotEmpty() }?.let { displayListNoms.add(it) }
                }
            }
            val displayFormArr = v.optJSONArray("display_form")
            val displayFormNoms = mutableListOf<String>()
            if (displayFormArr != null) {
                for (i in 0 until displayFormArr.length()) {
                    displayFormArr.optString(i, "").takeIf { it.isNotEmpty() }?.let { displayFormNoms.add(it) }
                }
            }
            // `change` : tableau de lignes JS conservé brut pour l'évaluateur de règles, ou une seule
            // chaîne, que le web accepte aussi (toFunction).
            val changeLignes = mutableListOf<String>()
            when (val change = v.opt("change")) {
                is JSONArray -> for (i in 0 until change.length()) changeLignes.add(change.optString(i, ""))
                is String -> changeLignes.addAll(change.lines())
                else -> {}
            }
            val sortsArr = v.optJSONArray("sorts")
            val sortsList = mutableListOf<Pair<String, String>>()
            if (sortsArr != null) {
                for (i in 0 until sortsArr.length()) {
                    val s = sortsArr.optJSONObject(i) ?: continue
                    val prop = s.optString("prop", "").takeIf { it.isNotEmpty() } ?: continue
                    val dir = s.optString("dir", "asc").lowercase()
                    sortsList.add(prop to dir)
                }
            }
            // gn_module_monitoring expose les propriétés saisissables dans DEUX blocs côte à
            // côte : `generic` (champs hérités du modèle de base — id, dates système, etc.)
            // et `specific` (champs custom du protocole). Le merge fait que specific peut
            // surcharger generic. Et `parent_types` est un array, pas un scalar.
            val parentTypeFromArr = v.optJSONArray("parent_types")?.optString(0, "")
                ?.takeIf { it.isNotEmpty() }
            val childrenFromTypesArr = v.optJSONArray("children_types")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optString(i, "").takeIf { it.isNotEmpty() } }
            }.orEmpty()
            result[type] = MonitoringSchemaObjet(
                type = type,
                label = v.optString("label", "").takeIf { it.isNotEmpty() },
                labelList = v.optString("label_list", "").takeIf { it.isNotEmpty() },
                nameField = nameField,
                parentType = v.optString("parent_type", "")
                    .ifEmpty { parentTypeFromArr ?: "" }
                    .takeIf { it.isNotEmpty() },
                childrenTypes = children.ifEmpty { childrenFromTypesArr },
                properties = parserPropertiesFusionnees(v),
                displayProperties = displayProps,
                displayForm = displayFormNoms,
                changeRules = changeLignes,
                displayList = displayListNoms,
                sorts = sortsList,
                idFieldName = v.optString("id_field_name", "").takeIf { it.isNotEmpty() },
                idListObserver = v.optInt("id_list_observer", -1).takeIf { it > 0 },
                idListTaxonomy = v.optInt("id_list_taxonomy", -1).takeIf { it > 0 },
                geometryType = v.optString("geometry_type", "").takeIf { it.isNotEmpty() && it != "null" },
                uuidFieldName = v.optString("uuid_field_name", "").takeIf { it.isNotEmpty() },
                genre = v.optString("genre", "").takeIf { it.isNotEmpty() },
                enchainable = v.optBoolean("chained", false),
                gardes = v.optJSONArray("keep")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i -> arr.optString(i, "").takeIf { it.isNotEmpty() } }
                }.orEmpty(),
                retourAuParent = v.optBoolean("redirect_to_parent", false),
            )
        }
        // Post-processing : dérive l'URL des widgets `observers`/`dataset`/`taxonomy_list`
        // qui n'ont pas d'`api` explicite dans le schéma (raccourci natif gn_module_monitoring
        // — le client est censé connaître l'URL standard). Utilise les ID de listes déclarés
        // au niveau du module.
        val moduleBloc = obj.optJSONObject("module")
        val idListObserver = moduleBloc?.optInt("id_list_observer", -1)?.takeIf { it > 0 }
        val idListTaxonomy = moduleBloc?.optInt("id_list_taxonomy", -1)?.takeIf { it > 0 }
        // Le champ affiché pour chaque type_util, au niveau racine de la config (une valeur nulle,
        // réglage absent du protocole, laisse le défaut de gn_module_monitoring).
        val champsAffichage = obj.optJSONObject("display_field_names")?.let { noms ->
            buildMap {
                val cles = noms.keys()
                while (cles.hasNext()) {
                    val cle = cles.next()
                    if (!noms.isNull(cle)) noms.optString(cle, "").takeIf { it.isNotEmpty() }?.let { put(cle, it) }
                }
            }
        }.orEmpty()
        return result.mapValues { (type, schemaObjet) ->
            schemaObjet.copy(
                properties = schemaObjet.properties.mapValues { (_, prop) ->
                    derirverApiSiManquant(prop, idListObserver, idListTaxonomy, moduleCode)
                },
                champsAffichage = if (type == "module") champsAffichage else emptyMap(),
            )
        }
    }

    companion object {

        /** Pour les widgets `observers`/`dataset`/`taxonomy_list` déclarés sans `api`/`keyLabel`/
         *  `keyValue`, applique les conventions standard gn_module_monitoring (URL fixe + champs
         *  par défaut). Pour `observers`/`taxonomy_list`, si le widget a déjà un api on ne touche
         *  à rien. Le widget `dataset` fait exception : on garantit toujours un filtre `module_code`
         *  (cf. ci-dessous), même quand un api explicite est fourni.
         *  [moduleCodeProtocole] : code du protocole en cours, utilisé pour filtrer les datasets
         *  d'un widget `dataset` quand le schéma n'a pas explicitement `module_code` — on prend
         *  par défaut les datasets rattachés au protocole, ce qui est ce que veut le serveur. */
        private fun derirverApiSiManquant(
            prop: MonitoringPropertySchema,
            idListObserver: Int?,
            idListTaxonomy: Int?,
            moduleCodeProtocole: String,
        ): MonitoringPropertySchema {
            // Cas `dataset` traité AVANT l'early-return sur apiUrl : on veut TOUJOURS un filtre
            // `module_code`, même quand le schéma fournit déjà un `api` explicite (sinon
            // /api/meta/datasets renvoie tous les jeux de données de l'instance, pas seulement
            // ceux rattachés au protocole). Priorité au `module_code` du schéma, sinon le module
            // du protocole en cours. Idempotent : on n'ajoute rien si `module_code` est déjà là.
            if (prop.typeWidget.equals("dataset", ignoreCase = true) ||
                prop.apiUrl?.contains("meta/datasets") == true
            ) {
                val codeFiltre = prop.moduleCodeFiltre ?: moduleCodeProtocole
                val base = prop.apiUrl ?: "meta/datasets"
                val avecModule = if (base.contains("module_code=")) base
                    else base + (if (base.contains('?')) "&" else "?") + "module_code=$codeFiltre"
                // `active=true` : exclut les jeux de données archivés du picker (parité web).
                // Idempotent : on n'ajoute pas si le schéma fournit déjà un filtre actif explicite.
                val avecActif = if (avecModule.contains("active=")) avecModule
                    else avecModule + "&active=true"
                // `create=<module>.<code_object>` : restreint via CRUVED action=C sur l'objet visé
                // par cette création (ex. apollons.MONITORINGS_VISITES pour un id_dataset de visite).
                // Backend gn_meta : `TDatasets.filter_by_creatable(module_code, object_code)`. Sans
                // ça on listait les datasets du module entier, y compris ceux où le user n'a pas
                // le droit de saisir une visite. Idempotent.
                val apiFiltree = prop.creatableInModule?.takeIf { it.isNotEmpty() }?.let { cim ->
                    if (avecActif.contains("create=")) avecActif
                    else avecActif + "&create=$cim"
                } ?: avecActif
                return prop.copy(
                    apiUrl = apiFiltree,
                    keyLabel = prop.keyLabel ?: "dataset_name",
                    keyValue = prop.keyValue ?: "id_dataset",
                )
            }
            if (prop.apiUrl != null) {
                // Widget `nomenclature` : l'api est dérivé (`nomenclatures/nomenclature/<TYPE>`) mais
                // le schéma ne fournit JAMAIS keyLabel/keyValue. L'endpoint GeoNature renvoie des items
                // `{id_nomenclature, cd_nomenclature, label_default, label_fr, …}` → valeur=id_nomenclature,
                // libellé=label_default. Sans ça chargerOptionsDatalist renvoyait null → « fetch options
                // échoué » (cas id_nomenclature_statut_obs du protocole Chevêches).
                if (prop.apiUrl.contains("nomenclatures/nomenclature/") &&
                    (prop.keyLabel == null || prop.keyValue == null)
                ) {
                    return prop.copy(
                        keyValue = prop.keyValue ?: "id_nomenclature",
                        // Le composant pnx-nomenclature affiche label_<langue>, soit label_fr.
                        keyLabel = prop.keyLabel ?: "label_fr,label_default",
                    )
                }
                // Un datalist `monitorings/list/<module>/<type>?fields=ID&fields=NOM` (ex. id_base_site
                // du protocole Chevêches) déclare un `api` mais souvent PAS de keyLabel/keyValue : on
                // les dérive des `fields=` de l'URL (champ `id_*` = valeur, l'autre = libellé). Sans ça
                // chargerOptionsDatalist renvoyait null → « fetch options échoué ».
                if (prop.keyLabel == null || prop.keyValue == null) {
                    val fields = Regex("""fields=([^&]+)""").findAll(prop.apiUrl)
                        .map { it.groupValues[1].decodeURLQueryComponent(plusIsSpace = true) }.toList()
                    if (fields.size >= 2) {
                        val idField = fields.firstOrNull { it.startsWith("id_") || it == "id" }
                        val labelField = fields.firstOrNull { it != idField }
                        return prop.copy(
                            keyValue = prop.keyValue ?: idField ?: fields[0],
                            keyLabel = prop.keyLabel ?: labelField ?: fields[1],
                        )
                    }
                }
                return prop
            }
            val (api, kLabel, kValue) = when (prop.typeWidget.lowercase()) {
                "observers" -> Triple(
                    (prop.idListTaxonomie ?: idListObserver)?.let { "users/menu/$it" }
                        ?: prop.codeList?.let { "users/menu_from_code/$it" }
                        ?: return prop,
                    "nom_complet", "id_role",
                )
                "taxonomy_list" -> Triple(
                    idListTaxonomy?.let { "biblistes/$it" } ?: return prop,
                    "nom_liste", "id_liste",
                )
                else -> return prop
            }
            return prop.copy(
                apiUrl = api,
                keyLabel = prop.keyLabel ?: kLabel,
                keyValue = prop.keyValue ?: kValue,
            )
        }

        /** Fusionne les blocs `generic` (héritage du modèle de base) et `specific` (custom protocole)
         *  d'un object_type gn_module_monitoring. La fusion est faite **attribut par attribut** :
         *  un `specific` peut surcharger seulement le `attribut_label` d'un champ generic tout en
         *  conservant son `type_widget` (parité avec mergeConfigurations de gn_mobile_monitoring).
         *  Ordre : celui du formulaire web (mergeObjects(specific, generic)), les champs du bloc
         *  specific d'abord, puis ceux du bloc generic ; le serveur range déjà dans generic un champ
         *  generic que le protocole redéfinit.
         *  Fallback sur `properties` (vieille forme) si les deux blocs sont absents. */
        internal fun parserPropertiesFusionnees(objSchema: JSONObject): Map<String, MonitoringPropertySchema> {
            val generic = objSchema.optJSONObject("generic")
            val specific = objSchema.optJSONObject("specific")
            if (generic == null && specific == null) {
                val map = linkedMapOf<String, MonitoringPropertySchema>()
                objSchema.optJSONObject("properties")?.let { parserBlocProperties(it, map) }
                return map
            }
            val cles = linkedSetOf<String>()
            specific?.keys()?.forEach { cles.add(it) }
            generic?.keys()?.forEach { cles.add(it) }
            // Les clés du bloc `specific`, pour marquer les propriétés qui en viennent.
            val clesSpecific = specific?.keys()?.asSequence()?.toSet().orEmpty()
            val map = linkedMapOf<String, MonitoringPropertySchema>()
            for (nom in cles) {
                val fusion = fusionnerChamp(generic?.optJSONObject(nom), specific?.optJSONObject(nom))
                val estSpecific = nom in clesSpecific
                parserUnePropriete(nom, fusion, enSpecific = estSpecific)?.let {
                    map[nom] = if (estSpecific) it.copy(enSpecific = true) else it
                }
            }
            return map
        }

        /** Fusion shallow de deux config de champ : on copie d'abord les attributs `generic`,
         *  puis ceux de `specific` qui les surchargent un par un. */
        internal fun fusionnerChamp(generic: JSONObject?, specific: JSONObject?): JSONObject {
            val out = JSONObject()
            generic?.keys()?.forEach { k -> out.put(k, generic.get(k)) }
            specific?.keys()?.forEach { k -> out.put(k, specific.get(k)) }
            return out
        }

        /** Parse un bloc d'object_type (generic ou specific) et accumule dans [into]. */
        private fun parserBlocProperties(propsObj: JSONObject, into: MutableMap<String, MonitoringPropertySchema>) {
            val it = propsObj.keys()
            while (it.hasNext()) {
                val nom = it.next()
                val v = propsObj.optJSONObject(nom) ?: continue
                parserUnePropriete(nom, v)?.let { into[nom] = it }
            }
        }

        /** Parse une config de champ (déjà fusionnée generic+specific) en [MonitoringPropertySchema].
         *  Retourne null pour les entrées à ignorer (ni type_widget ni hidden:true), comme le
         *  formulaire web, que le champ vienne du bloc generic ou specific ([enSpecific]). */
        internal fun parserUnePropriete(
            nom: String,
            v: JSONObject,
            enSpecific: Boolean = false,
        ): MonitoringPropertySchema? {
            // `hidden` peut être :
            //  - Boolean true  → champ technique masqué à l'UI (id_base_visit, id_module,
            //    medias, nb_observations…). On le CONSERVE quand même dans le schéma car
            //    le serveur Marshmallow l'attend dans le payload POST avec valeur null —
            //    sans ça, un 500 silencieux. C'est `construireFormulaire` qui filtre l'UI.
            //  - Boolean false → champ visible inconditionnellement.
            //  - String        → expression d'affichage dynamique (à évaluer côté UI).
            // Seules les clés que lit le web : `hidden`, `type_widget` et `attribut_label`.
            val hiddenBrut = v.opt("hidden")
            val hiddenBool = hiddenBrut is Boolean && hiddenBrut
            val hiddenExpr = hiddenBrut as? String
            val typeWidgetBrut = v.optString("type_widget", "")
            // Champ sans `type_widget` : le formulaire web l'écarte (initObjFormDefiniton ne garde que
            // les définitions qui en ont un), qu'il soit du bloc generic ou specific. Il ne reste dans
            // le schéma que masqué, pour le corps envoyé.
            val typeUtilBrut = v.optString("type_util", "").takeIf { it.isNotEmpty() }
            val typeWidget = typeWidgetBrut.ifEmpty { if (hiddenBool) "" else return null }
            val label = v.optString("attribut_label", "")
            // `required` : booléen OU expression dynamique `({value}) => …` (ex. champs
            // végétation du protocole Point écoute avifaune, requis seulement au passage 2 —
            // même mécanique que `hidden`). L'expression est transportée jusqu'au renderer qui
            // l'évalue contre les valeurs courantes du formulaire.
            val requiredBrut = v.opt("required")
            val obligatoire = when (requiredBrut) {
                is Boolean -> requiredBrut
                is String -> requiredBrut.equals("true", ignoreCase = true)
                else -> false
            }
            val obligatoireExpr = (requiredBrut as? String)
                ?.takeIf { it.contains("=>") || it.contains("value") }
            val apiBrut = v.optString("api", "").takeIf { it.isNotEmpty() }
            // Détection enrichie du type de nomenclature (parité isNomenclatureField de
            // gn_mobile_monitoring) : code explicite, sinon celui de la `value` objet (le web la
            // résout par ce type, même dans un champ masqué de widget texte), sinon inféré depuis
            // l'`api` (.../nomenclature/CODE) ou le nom du champ (id_nomenclature_CODE).
            val nomenclatureType = v.optString("code_nomenclature_type", "")
                .ifEmpty { v.optString("nomenclature_type", "") }
                .ifEmpty { (v.opt("value") as? JSONObject)?.optString("code_nomenclature_type", "").orEmpty() }
                .takeIf { it.isNotEmpty() }
                ?: infererCodeNomenclature(nom, apiBrut)
            val valeursArr = v.optJSONArray("values")
            val valeurs = mutableListOf<Pair<String, String>>()
            val valeursBrutes = mutableMapOf<String, Any>()
            if (valeursArr != null) {
                for (i in 0 until valeursArr.length()) {
                    val entry = valeursArr.optJSONObject(i)
                    if (entry != null) {
                        val value = entry.optString("value", "")
                        val lbl = entry.optString("label", value)
                        if (value.isNotEmpty()) {
                            valeurs.add(value to lbl)
                            entry.opt("value")?.takeIf { it != JSONObject.NULL }?.let { valeursBrutes[value] = it }
                        }
                    } else {
                        valeursArr.optString(i, "").takeIf { it.isNotEmpty() }?.let { s ->
                            valeurs.add(s to s)
                            valeursArr.opt(i)?.takeIf { it != JSONObject.NULL }?.let { valeursBrutes[s] = it }
                        }
                    }
                }
            }
            // Restriction d'une nomenclature (`cd_nomenclatures`) et paramètres d'une datalist
            // (`params`), transmis au serveur comme le fait le formulaire web.
            val cdNomenclatures = v.optJSONArray("cd_nomenclatures")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.opt(i)?.toString()?.takeIf { it.isNotEmpty() && it != "null" } }
            }.orEmpty()
            val params = mutableListOf<Pair<String, String>>()
            v.optJSONObject("params")?.let { pObj ->
                pObj.keys().forEach { k ->
                    when (val pv = pObj.opt(k)) {
                        null, JSONObject.NULL -> {}
                        is JSONArray -> for (i in 0 until pv.length()) pv.opt(i)?.let { params += k to it.toString() }
                        else -> params += k to pv.toString()
                    }
                }
            }
            // L'URL des options, celle du formulaire web : l'`api` et ses `params` (getDataList), ou,
            // pour le widget `nomenclature`, la nomenclature triée par libellé, filtrée par règne et
            // groupe et restreinte à ses `cd_nomenclatures` (composant pnx-nomenclature).
            val apiUrl = when {
                apiBrut != null -> avecParametres(apiBrut, params)
                nomenclatureType != null && typeWidget.equals("nomenclature", ignoreCase = true) ->
                    avecParametres(
                        "nomenclatures/nomenclature/$nomenclatureType",
                        buildList {
                            add("orderby" to "label_default")
                            v.optString("regne", "").takeIf { it.isNotEmpty() }?.let { add("regne" to it) }
                            v.optString("group2Inpn", "").takeIf { it.isNotEmpty() }?.let { add("group2_inpn" to it) }
                            cdNomenclatures.forEach { add("cd_nomenclature" to it) }
                        },
                    )
                nomenclatureType != null -> "nomenclatures/nomenclature/$nomenclatureType"
                else -> null
            }
            val application = v.optString("application", "").takeIf { it.isNotEmpty() }
            val keyLabel = v.optString("keyLabel", "").takeIf { it.isNotEmpty() }
            val keyValue = v.optString("keyValue", "").takeIf { it.isNotEmpty() }
            val dataPath = v.optString("data_path", "").takeIf { it.isNotEmpty() }
            // Choix multiple, selon la clé que lit le composant web du widget : `multiple` pour une
            // datalist, `multi_select` pour une nomenclature, des observateurs ou un jeu de données ;
            // un `select` ou un `radio` est toujours simple, un `multiselect` ou un `checkbox` toujours
            // multiple.
            val multiple = when (typeWidget.lowercase()) {
                "datalist" -> v.optBoolean("multiple", false)
                "nomenclature", "observers", "dataset" -> v.optBoolean("multi_select", false)
                "multiselect", "checkbox" -> true
                else -> false
            }
            val typeUtil = typeUtilBrut
            // Liste taxonomique portée par le champ lui-même : `id_list` direct ou extrait de
            // l'api `taxref/allnamebylist/<id>` (parité getTaxonListId). Sert à restreindre
            // l'autocomplete TaxRef à la liste autorisée pour ce champ.
            val idListTaxonomie = extraireInt(v.opt("id_list"))
                ?: apiBrut?.let { extraireIdListeAllnamebylist(it) }
            // Valeur initiale : la seule `value` du schéma, comme le formulaire web (toForm de
            // gn_module_monitoring) ; un objet `{code_nomenclature_type, cd_nomenclature}` se résout
            // par son cd. Le `default` ne vaut que pour une datalist (composant datalist du web).
            var defaultValue: String? = null
            var valeurTypee: Any? = null
            val defaultObjet = mutableMapOf<String, String>()
            when (val brute = v.opt("value")) {
                is String -> brute.takeIf { it.isNotEmpty() && it != "null" }?.let {
                    defaultValue = it
                    valeurTypee = it
                }
                is Number, is Boolean -> {
                    defaultValue = brute.toString()
                    valeurTypee = brute
                }
                is JSONObject -> defaultObjet += objetEnTextes(brute)
                else -> { /* null, JSONObject.NULL, ou type non géré */ }
            }
            val defautDatalist = (v.opt("default") as? JSONObject)?.let(::objetEnTextes).orEmpty()
            // Bornes numériques, celles du web : un nombre, ou une fonction (`({value}) =>
            // value.count_max`) résolue à la volée par
            // [fr.ariegenature.geomys.monitoring.form.ValidationExpr]. Gardées en texte.
            val minValue = bornBrute(v.opt("min"))
            val maxValue = bornBrute(v.opt("max"))

            // Filtres : Map<champ, liste-de-valeurs-acceptables>
            val filtresMap = mutableMapOf<String, List<String>>()
            v.optJSONObject("filters")?.let { fObj ->
                val fIt = fObj.keys()
                while (fIt.hasNext()) {
                    val fKey = fIt.next()
                    val fArr = fObj.optJSONArray(fKey) ?: continue
                    val fVals = (0 until fArr.length()).mapNotNull { i ->
                        fArr.optString(i, "").takeIf { it.isNotEmpty() }
                    }
                    if (fVals.isNotEmpty()) filtresMap[fKey] = fVals
                }
            }
            return MonitoringPropertySchema(
                nom = nom,
                typeWidget = typeWidget,
                label = label,
                obligatoire = obligatoire,
                typeUtil = typeUtil,
                nomenclatureType = nomenclatureType,
                valeurs = valeurs,
                valeursBrutes = valeursBrutes.toMap(),
                apiUrl = apiUrl,
                application = application,
                donneesPost = when (val d = v.opt("data")) {
                    is JSONObject, is JSONArray -> d.toString()
                    else -> null
                },
                keyLabel = keyLabel,
                keyValue = keyValue,
                dataPath = dataPath,
                multiple = multiple,
                html = v.optString("html", "").takeIf { it.isNotEmpty() },
                defaultValue = defaultValue,
                valeurTypee = valeurTypee,
                defaultObjet = defaultObjet.toMap(),
                hiddenExpr = hiddenExpr,
                obligatoireExpr = obligatoireExpr,
                hiddenBool = hiddenBool,
                filtres = filtresMap.toMap(),
                definition = v.optString("definition", "").takeIf { it.isNotEmpty() },
                moduleCodeFiltre = v.optString("module_code", "").takeIf { it.isNotEmpty() },
                idListTaxonomie = idListTaxonomie,
                minValue = minValue,
                maxValue = maxValue,
                schemaDotTable = v.optString("schema_dot_table", "").takeIf { it.isNotEmpty() },
                creatableInModule = v.optString("creatable_in_module", "").takeIf { it.isNotEmpty() },
                cdNomenclatures = cdNomenclatures,
                defautDatalist = defautDatalist,
                ordreOptions = v.optString("orderBy", "").takeIf { it.isNotEmpty() },
                codeList = v.optString("code_list", "").takeIf { it.isNotEmpty() },
                aide = v.optString("help", "").takeIf { it.isNotEmpty() && it != "null" },
                motif = (v.opt("pattern") as? String)?.takeIf { it.isNotEmpty() },
                messageMotif = v.optString("pattern_message", "").takeIf { it.isNotEmpty() && it != "null" },
                longueurMax = (v.opt("max_length") as? Number)?.toInt()?.takeIf { it > 0 },
                lignes = (v.opt("rows") as? Number)?.toInt()?.takeIf { it > 0 },
                sansOptionVide = v.optBoolean("noNullOption", false),
                avecAucun = v.optBoolean("nullDefault", false),
            )
        }

        /** Les clés d'un objet JSON et leurs valeurs en texte, sans les valeurs nulles ou vides. */
        private fun objetEnTextes(obj: JSONObject): Map<String, String> = buildMap {
            obj.keys().forEach { k -> obj.opt(k)?.toString()?.takeIf { it.isNotEmpty() && it != "null" }?.let { put(k, it) } }
        }

        /** [api] et ses paramètres de requête, dans l'ordre (une clé répétée pour un tableau). */
        internal fun avecParametres(api: String, params: List<Pair<String, String>>): String =
            if (params.isEmpty()) api
            else api + (if ('?' in api) "&" else "?") + params.joinToString("&") { (k, v) -> "$k=${v.encodeURLQueryComponent()}" }

        /** Normalise une borne (`min`/`max`) lue dans le schéma : Number/Boolean → toString,
         *  String non vide → trimée, JSONObject.NULL / null / "" → null. On ne valide PAS le
         *  contenu ici : une expression `(value) => …` est conservée telle quelle, pour résolution
         *  ultérieure par ValidationExpr. */
        /** Une borne comme le web la prend : un nombre, ou une chaîne que son `toFunction` change en
         *  fonction (elle contient « ( », « ) », « { », « } » et « => ») ; une autre chaîne n'est pas
         *  un nombre pour lui, et la borne ne s'applique pas. */
        private fun bornBrute(brut: Any?): String? = when (brut) {
            is Number -> brut.toString()
            is String -> brut.trim().takeIf { t -> listOf("(", ")", "{", "}", "=>").all { it in t } }
            else -> null
        }

        /** Infère le code mnémonique d'un type de nomenclature quand il n'est pas explicite :
         *  depuis l'api (`.../nomenclatures/nomenclature/STADE_VIE`) ou le nom du champ
         *  (`id_nomenclature_stade_vie` → `stade_vie`). Renvoie null si rien d'exploitable. */
        internal fun infererCodeNomenclature(nom: String, api: String?): String? {
            if (api != null && api.contains("nomenclatures/nomenclature/")) {
                api.substringAfterLast('/').takeIf { it.isNotEmpty() }?.let { return it }
            }
            if (nom.startsWith("id_nomenclature_")) {
                return nom.removePrefix("id_nomenclature_").takeIf { it.isNotEmpty() }
            }
            return null
        }

        /** Extrait l'id de liste taxonomique d'une api `taxref/allnamebylist/<id>`. */
        internal fun extraireIdListeAllnamebylist(api: String): Int? {
            if (!api.contains("allnamebylist/")) return null
            val parts = api.split('/')
            val idx = parts.indexOf("allnamebylist")
            return if (idx >= 0 && idx + 1 < parts.size) parts[idx + 1].toIntOrNull() else null
        }

        /** Remplace en place les placeholders `__MODULE.XXX` présents dans les chaînes du schéma
         *  par leur valeur. La table de correspondance est construite depuis le bloc `custom`
         *  (clés `__MODULE.XXX`) — c'est exactement ce que fait substituteVariables côté
         *  gn_mobile_monitoring. No-op si aucun bloc `custom`. */
        internal fun substituerVariablesModule(racine: JSONObject) {
            val custom = racine.optJSONObject("custom") ?: return
            val subs = mutableMapOf<String, String>()
            custom.keys().forEach { cle ->
                if (cle.startsWith("__MODULE.")) {
                    val valeur = custom.opt(cle)
                    if (valeur != null && valeur != JSONObject.NULL) subs[cle] = valeur.toString()
                }
            }
            if (subs.isEmpty()) return
            substituerRecursif(racine, subs)
        }

        /** Parcourt récursivement un nœud JSON et remplace, dans chaque feuille String contenant
         *  `__MODULE.`, les placeholders connus par leur valeur. */
        private fun substituerRecursif(node: Any?, subs: Map<String, String>) {
            when (node) {
                is JSONObject -> {
                    val cles = node.keys().asSequence().toList()
                    for (cle in cles) {
                        when (val valeur = node.opt(cle)) {
                            is String -> if (valeur.contains("__MODULE.")) {
                                node.put(cle, appliquerSubstitutions(valeur, subs))
                            }
                            is JSONObject, is JSONArray -> substituerRecursif(valeur, subs)
                        }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        when (val valeur = node.opt(i)) {
                            is String -> if (valeur.contains("__MODULE.")) {
                                node.put(i, appliquerSubstitutions(valeur, subs))
                            }
                            is JSONObject, is JSONArray -> substituerRecursif(valeur, subs)
                        }
                    }
                }
            }
        }

        private fun appliquerSubstitutions(valeur: String, subs: Map<String, String>): String {
            var out = valeur
            for ((placeholder, remplacement) in subs) {
                if (out.contains(placeholder)) out = out.replace(placeholder, remplacement)
            }
            return out
        }

        /** Convertit une valeur JSON (Int/Double/String numérique) en Int, sinon null. */
        private fun extraireInt(value: Any?): Int? = when (value) {
            null, JSONObject.NULL -> null
            is Int -> value
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }
}
