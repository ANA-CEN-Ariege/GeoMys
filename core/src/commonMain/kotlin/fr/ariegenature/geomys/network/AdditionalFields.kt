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

// Porté de GeoMys-Android @ d72b8da (app/src/main/java/fr/ariegenature/geomys/network/AdditionalFields.kt,
// AdditionalFieldsApi) : HttpURLConnection remplacé par ClientsGeoNature, org.json par son portage,
// android.util.Log par Journal. L'objet devient une classe sur l'authentification ; le parsing, pur,
// reste dans son compagnon. Les modèles passent dans model/. Valeurs et défauts suivent le formulaire
// web depuis la vérification des champs du 20 octobre (label_default, default_value seul,
// additional_attributes, ordre du serveur).

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.OptionNomenclature
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.get
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.store.GeoNatureConfig
import io.ktor.client.statement.bodyAsText
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class AdditionalFieldsApi(
    private val auth: GeoNatureAuth,
    private val clients: ClientsGeoNature,
) {

    /** GET /api/gn_commons/additional_fields?module_code=OCCTAX
     *  Retourne la liste de tous les champs additionnels Occtax (tous niveaux confondus).
     *  Renvoie [] silencieusement si HTTP 404 (endpoint absent — feature optionnelle).
     *  Sur toute autre erreur (timeout, 5xx, parse), propage l'exception au caller. */
    suspend fun charger(config: GeoNatureConfig, moduleCode: String = "OCCTAX"): List<AdditionalFieldDef> =
        withContext(Dispatchers.IO) {
            val base = config.urlServeur.trim().trimEnd('/')
            val (token, _, cookies) = auth.loginAvecCookies(base, config.login, config.motDePasse)
                ?: throw GNErreur.AuthEchouee(401)

            val reponse = clients.get("$base/api/gn_commons/additional_fields?module_code=$moduleCode", token, cookies, 10000)
            val code = reponse.status.value
            if (code == 404) return@withContext emptyList() // endpoint absent — feature optionnelle
            if (code != 200) throw GNErreur.EnvoiEchoue(code, "additional_fields : HTTP $code")

            val text = reponse.bodyAsText()
            val array: JSONArray = text.parserTableauJson("data", "items")
                ?: throw GNErreur.EnvoiEchoue(code, "additional_fields : format JSON inattendu")
            val result = parserChamps(array)
            // Pour les widgets `nomenclature` : fetch parallèle des listes serveur
            // (/api/nomenclatures/nomenclature/<CODE>) pour résoudre id_nomenclature → label_fr.
            val codesNom = result.mapNotNull { it.codeNomenclatureType }.toSet()
            val nomenclaturesResolues: Map<String, List<OptionNomenclature>> =
                if (codesNom.isEmpty()) emptyMap() else coroutineScope {
                    codesNom.map { code ->
                        async { code to fetchValeursNomenclature(base, token, cookies, code) }
                    }.awaitAll().toMap()
                }
            // Le défaut d'un champ est son seul `default_value`, comme sur le web : pas celui de la
            // nomenclature du module, que GeoMys-Android y mettait.
            result.map { def ->
                def.codeNomenclatureType?.let { code -> def.copy(nomenclatureOptions = nomenclaturesResolues[code].orEmpty()) } ?: def
            }
            // Dans l'ordre du serveur (field_order), comme le web.
        }

    /** Fetch des valeurs d'une nomenclature (`/api/nomenclatures/nomenclature/<code>`), triées par
     *  label_default comme le composant du web. Retourne la liste (id_nomenclature, label_fr,
     *  label_default, cd_nomenclature) ou vide en cas d'erreur. */
    private suspend fun fetchValeursNomenclature(
        base: String,
        token: String?,
        cookies: String,
        code: String,
    ): List<OptionNomenclature> {
        return try {
            val reponse = clients.get("$base/api/nomenclatures/nomenclature/$code?orderby=label_default", token, cookies, 10000)
            if (reponse.status.value != 200) return emptyList()
            val text = reponse.bodyAsText()
            val arr = text.parserTableauJson("values", "data") ?: return emptyList()
            val out = mutableListOf<OptionNomenclature>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.opt("id_nomenclature")?.toString() ?: continue
                val labelDefault = item.optString("label_default", "").takeIf { it.isNotEmpty() && it != "null" }
                val lbl = item.optString("label_fr", "")
                    .ifEmpty { labelDefault.orEmpty() }
                    .takeIf { it.isNotEmpty() } ?: continue
                val cd = item.optString("cd_nomenclature", "").takeIf { it.isNotEmpty() && it != "null" }
                out.add(OptionNomenclature(id, lbl, labelDefault, cd))
            }
            out
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { emptyList() }
    }

    companion object {

        /** Cherche le default sous plusieurs clés serveur connues (le format varie selon les
         *  versions GeoNature et la migration gn_commons). Essaie également dans
         *  `additional_attributes` qui est l'enveloppe utilisée par certains widgets. Enfin,
         *  pour les widgets SELECT/RADIO/DATALIST, prend le premier `field_values[i].default==true`. */
        /** Parse le tableau JSON brut renvoyé par /api/gn_commons/additional_fields en
         *  liste de [AdditionalFieldDef]. Gère les formes variables du serveur GeoNature :
         *  type_widget objet vs string, field_values string vs {value,label}, objects[].code_object,
         *  datasets en ids nus ou objets {id_dataset}. Pur (pas de réseau) → testable. */
        internal fun parserChamps(array: JSONArray): List<AdditionalFieldDef> {
            val result = mutableListOf<AdditionalFieldDef>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val idField = item.optInt("id_field", -1).takeIf { it > 0 } ?: continue
                val fieldName = item.optString("field_name", "").ifEmpty { continue }
                // `additional_attributes`, que le web étale sur la définition du champ
                // (getadditionalFields) : ses clés l'emportent.
                val attributs = item.optJSONObject("additional_attributes")
                fun attribut(cle: String): Any? = attributs?.opt(cle)?.takeIf { it != JSONObject.NULL }
                fun texte(cle: String): String? = attribut(cle)?.toString()?.takeIf { it.isNotEmpty() && it != "null" }
                fun vrai(cle: String): Boolean = when (val v = attribut(cle)) {
                    is Boolean -> v
                    is String -> v.equals("true", ignoreCase = true)
                    else -> false
                }
                val fieldLabel = item.optString("field_label", fieldName)
                // type_widget peut être renvoyé sous plusieurs formes selon la version GN :
                //   - JSONObject {widget_name: "select", ...}
                //   - String "select"
                //   - Absent (rare, on a alors WidgetType.INCONNU)
                val typeWidgetBrut = item.opt("type_widget")
                val widgetName = when (typeWidgetBrut) {
                    is JSONObject -> typeWidgetBrut.optString("widget_name", "")
                        .ifEmpty { typeWidgetBrut.optString("name", "") }
                        .ifEmpty { typeWidgetBrut.optString("code_widget", "") }
                    is String -> typeWidgetBrut
                    else -> ""
                }
                // Les options (`values`) : field_values, ou celles d'additional_attributes. Une option
                // simple vaut {label: v, value: v}, comme dans dynamic-form.component.ts ; une datalist
                // lit ses clés `keyValue` et `keyLabel`.
                val brutes = (attribut("values") as? JSONArray) ?: item.optJSONArray("field_values")
                val cleValeur = if (widgetName.equals("datalist", true)) texte("keyValue") ?: "value" else "value"
                val cleLibelle = if (widgetName.equals("datalist", true)) texte("keyLabel") ?: "label" else "label"
                val fieldValues = mutableListOf<String>()      // libellés affichés
                val fieldValueCodes = mutableListOf<String>()   // valeurs, en texte
                val valeursBrutes = mutableListOf<String>()     // valeurs, en JSON
                if (brutes != null) for (j in 0 until brutes.length()) {
                    val v = brutes.opt(j)?.takeIf { it != JSONObject.NULL } ?: continue
                    val valeur = if (v is JSONObject) v.opt(cleValeur)?.takeIf { it != JSONObject.NULL } else v
                    val libelle = (if (v is JSONObject) v.opt(cleLibelle)?.takeIf { it != JSONObject.NULL } else v)
                        ?.toString()?.takeIf { it.isNotEmpty() }
                    if (valeur == null && libelle == null) continue
                    fieldValues.add(libelle ?: ChampsAdditionnels.texteValeur(valeur))
                    fieldValueCodes.add(ChampsAdditionnels.texteValeur(valeur))
                    valeursBrutes.add(ChampsAdditionnels.jsonLitteral(valeur))
                }
                val widget = when (widgetName.lowercase()) {
                    "text" -> WidgetType.TEXT
                    "textarea" -> WidgetType.TEXTAREA
                    "number" -> WidgetType.NUMBER
                    "select" -> WidgetType.SELECT
                    "radio" -> WidgetType.RADIO
                    "checkbox" -> WidgetType.CHECKBOX
                    "bool_checkbox" -> WidgetType.BOOL_CHECKBOX
                    "bool_radio" -> WidgetType.BOOL_RADIO
                    "nomenclature" -> WidgetType.NOMENCLATURE
                    "html" -> WidgetType.HTML
                    "date" -> WidgetType.DATE
                    "time" -> WidgetType.TIME
                    "multiselect" -> WidgetType.MULTISELECT
                    "taxonomy" -> WidgetType.TAXONOMY
                    "observers" -> WidgetType.OBSERVERS
                    "observers-text" -> WidgetType.OBSERVERS_TEXT
                    // Une datalist servie par une API ne se charge pas hors ligne.
                    "datalist" -> if (fieldValues.isEmpty()) WidgetType.INCONNU else WidgetType.DATALIST
                    else -> WidgetType.INCONNU
                }
                val cdNomenclatures = (attribut("cd_nomenclatures") as? JSONArray)?.let { arr ->
                    (0 until arr.length()).mapNotNull { j -> arr.opt(j)?.toString()?.takeIf { it.isNotEmpty() && it != "null" } }
                }.orEmpty()
                fun borne(cle: String): Double? = attribut(cle)?.let { (it as? Number)?.toDouble() ?: it.toString().toDoubleOrNull() }
                // Pour widget=nomenclature : essaie d'extraire le code_nomenclature_type
                // depuis plusieurs emplacements possibles (varie selon les versions GN).
                val codeNomType: String? = if (widget == WidgetType.NOMENCLATURE) {
                    item.optString("code_nomenclature_type", "")
                        .ifEmpty { attributs?.optString("code_nomenclature_type", "") ?: "" }
                        .ifEmpty {
                            (typeWidgetBrut as? JSONObject)?.optString("code_nomenclature_type", "") ?: ""
                        }
                        .takeIf { it.isNotEmpty() && it != "null" }
                } else null
                val objectsCode = mutableListOf<String>()
                item.optJSONArray("objects")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        arr.optJSONObject(j)?.optString("code_object", "")
                            ?.takeIf { it.isNotEmpty() }?.let(objectsCode::add)
                    }
                }
                // Restrictions par dataset : tableau d'objets {id_dataset, ...} ou d'ids nus.
                val datasetsIds = mutableListOf<Int>()
                item.optJSONArray("datasets")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val v = arr.opt(j)
                        when (v) {
                            is Int -> datasetsIds.add(v)
                            is Long -> datasetsIds.add(v.toInt())
                            is JSONObject -> v.optInt("id_dataset", -1).takeIf { it > 0 }?.let(datasetsIds::add)
                        }
                    }
                }
                // Restriction par liste UsersHub de taxons.
                val idList = (attribut("id_list") as? Number)?.toInt()?.takeIf { it > 0 } ?: item.optInt("id_list", -1).takeIf { it > 0 }
                result.add(AdditionalFieldDef(
                    idField = idField,
                    fieldName = fieldName,
                    fieldLabel = fieldLabel,
                    widget = widget,
                    fieldValues = fieldValues,
                    fieldValueCodes = fieldValueCodes,
                    required = if (attributs?.has("required") == true) vrai("required") else item.optBoolean("required", false),
                    description = item.optString("description", "")
                        .takeIf { it.isNotEmpty() && it != "null" },
                    defaultValue = defautDuChamp(item),
                    objectsCode = objectsCode,
                    datasetsIds = datasetsIds,
                    idList = idList,
                    widgetServeur = widgetName,
                    codeNomenclatureType = codeNomType,
                    masque = vrai("hidden"),
                    cdNomenclatures = cdNomenclatures,
                    min = borne("min"),
                    max = borne("max"),
                    valeursBrutes = valeursBrutes,
                    aide = texte("help"),
                    definition = texte("definition"),
                    lienDefinition = texte("link_definition"),
                    motif = texte("pattern"),
                    messageMotif = texte("pattern_message"),
                    longueurMax = (attribut("max_length") as? Number)?.toInt() ?: texte("max_length")?.toIntOrNull(),
                    lignes = (attribut("rows") as? Number)?.toInt() ?: texte("rows")?.toIntOrNull(),
                    sansOptionVide = vrai("noNullOption"),
                    avecAucun = vrai("nullDefault"),
                    html = texte("html"),
                    choixMultiple = vrai(if (widget == WidgetType.DATALIST) "multiple" else "multi_select"),
                    desactive = vrai("disabled"),
                ))
            }
            return result
        }

        /** Le défaut d'un champ, celui du web (getadditionalFields) : `default_value`, que
         *  remplace `additional_attributes.value` quand il est donné (le web fusionne ces attributs
         *  après). GeoMys-Android en cherchait sous d'autres clés, et prenait une valeur de liste
         *  marquée `default`, que le web ignore. */
        internal fun defautDuChamp(item: JSONObject): String? {
            val attributs = item.optJSONObject("additional_attributes")
            return extraireDefaut(if (attributs?.has("value") == true) attributs.opt("value") else item.opt("default_value"))
        }

        /** Normalise une valeur `default_value` arbitraire (String, Number, JSONArray, JSONObject)
         *  en une String exploitable par les widgets. Conventions :
         *  - String / Number → String simple (filtre `"null"` et chaîne vide).
         *  - JSONArray → le tableau en JSON (défaut d'un choix multiple).
         *  - JSONObject → champ `value` si présent, sinon `id_nomenclature`, sinon vide.
         *  - null / JSONObject.NULL → vide. */
        private fun extraireDefaut(brut: Any?): String? = when (brut) {
            null, JSONObject.NULL -> null
            is String -> brut.takeIf { it.isNotEmpty() && it != "null" }
            is Number -> brut.toString()
            is Boolean -> brut.toString()
            is JSONArray -> if (brut.length() > 0) brut.toString() else null
            is JSONObject -> {
                val v = brut.opt("value")?.toString()?.takeIf { it.isNotEmpty() && it != "null" }
                    ?: brut.opt("id_nomenclature")?.toString()?.takeIf { it.isNotEmpty() && it != "null" }
                v
            }
            else -> brut.toString().takeIf { it.isNotEmpty() && it != "null" }
        }
    }
}
