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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/MonitoringEnvoi.kt) :
// l'objet devient une classe injectée ; HttpURLConnection remplacé par ClientsGeoNature, Log par
// Journal. Le journal du corps envoyé, réservé aux versions de mise au point sous Android
// (BuildConfig.DEBUG), est omis : l'appli commune n'a pas de drapeau équivalent, et ce corps porte des
// données métier. runCatching laisse désormais passer l'annulation. Le jeu de données n'est plus
// deviné (premier jeu du module, puis celui d'OccTax) : seul celui du formulaire part, comme sur le web.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.monitoring.NouvelleVisite
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.get
import fr.ariegenature.geomys.network.http.patchJson
import fr.ariegenature.geomys.network.http.postJson
import fr.ariegenature.geomys.store.GeoNatureConfig
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** Envoi des saisies Suivis vers le serveur : POST de création d'objet (visite, observation, site…)
 *  avec normalisation du corps (types, complément Marshmallow, id_digitiser), et vérification
 *  anti-doublon par uuid client. Appelé principalement par [OutboxEnvoi]. */
class MonitoringEnvoi(
    private val auth: GeoNatureAuth,
    private val clients: ClientsGeoNature,
) {

    /** Cherche sur le serveur l'objet [objectType] dont [uuidFieldName] vaut [uuid] : l'anti-doublon
     *  d'un POST resté sans réponse. Route `GET /monitorings/list/<module>/<type>?<champ uuid>=<uuid>`,
     *  dont le serveur fait un filtre sur toute colonne du modèle (`get_list`, droits de lecture
     *  appliqués). La fiche du parent (`?depth=1`), lue jusqu'au 21 octobre 2026, ne sérialise ses
     *  enfants qu'avec leur `display_list`, sans uuid : l'objet passait toujours pour absent, et un
     *  renvoi le recréait (audit du 21 octobre, C4 ; hérité de GeoMys-Android).
     *  Renvoie son id serveur, 0 s'il existe mais que son id ne se lit pas, null s'il est absent.
     *  LÈVE en cas d'erreur réseau, d'authentification ou de réponse illisible : l'appelant ne doit
     *  alors PAS re-POSTer. */
    suspend fun chercherParUuid(
        config: GeoNatureConfig,
        moduleCode: String,
        objectType: String,
        uuidFieldName: String,
        uuid: String,
    ): Int? = withContext(Dispatchers.IO) {
        val base = config.urlServeur.trim().trimEnd('/')
        val (token, _, cookies) = auth.loginAvecCookies(base, config.login, config.motDePasse)
            ?: throw GNErreur.AuthEchouee(401)
        // `uuid_base_visit` → `id_base_visit`, `uuid_observation` → `id_observation` : la convention
        // des types de gn_module_monitoring (`id_field_name`, `uuid_field_name`).
        val champId = uuidFieldName.replaceFirst("uuid_", "id_")
        val url = "$base/api/monitorings/list/$moduleCode/$objectType" +
            "?$uuidFieldName=${uuid.encodeURLParameter()}&fields=$champId&fields=$uuidFieldName"
        val reponse = clients.get(url, token, cookies, timeoutMs = 15_000)
        val code = reponse.status.value
        if (code != 200) {
            throw GNErreur.EnvoiEchoue(code, "vérification anti-doublon ($objectType)")
        }
        val objets = try {
            JSONArray(reponse.bodyAsText())
        } catch (e: Exception) {
            throw GNErreur.EnvoiEchoue(code, "vérification anti-doublon ($objectType) : réponse illisible")
        }
        for (i in 0 until objets.length()) {
            val objet = objets.optJSONObject(i) ?: continue
            if (!objet.optString(uuidFieldName).equals(uuid, ignoreCase = true)) continue
            return@withContext objet.optInt(champId, -1).takeIf { it > 0 } ?: 0
        }
        null
    }

    /** Envoie une nouvelle visite (ou tout objet Suivis « saisissable ») au serveur.
     *  Point d'entrée : `POST /api/monitorings/object/<moduleCode>/<objectType>`. Le corps est
     *  `{properties}` ; pour les visites, la géométrie est en général héritée du parent côté
     *  serveur — on n'envoie donc pas de `geometry`.
     *
     *  [parentIdField] / [parentId] : nom du champ de clé étrangère vers le parent (ex.
     *  `id_base_site`) et son id. Posés directement dans `properties` (le serveur les attend là,
     *  pas dans l'URL).
     *
     *  [valeurs] : code de propriété → valeur typée, lue dans le formulaire :
     *  - Int / Long / Number → nombre JSON
     *  - Boolean → booléen JSON
     *  - List<*> → JSONArray (sélection multiple, observateurs…)
     *  - String → chaîne JSON pour les [champsTexteLibre] ; ailleurs, entier si elle en est un (une
     *    saisie enregistrée avant le 20 octobre 2026 garde ses ids en texte).
     *  Les [champsDate] partent en `année-mois-jour` sans zéros, comme le `fromForm` du web.
     *
     *  Renvoie l'`id` du nouvel objet par Result.success, ou l'exception par Result.failure
     *  (authentification, HTTP, lecture de la réponse). */
    suspend fun envoyerVisite(
        config: GeoNatureConfig,
        moduleCode: String,
        objectType: String,
        parentIdField: String?,
        parentId: Int?,
        valeurs: Map<String, Any?>,
        nomsChampsSchema: Collection<String> = emptyList(),
        /** Codes des valeurs en texte, qui ne doivent PAS être converties en Int même numériques
         *  (cf. audit B6). Vide → conversion historique. */
        champsTexteLibre: Collection<String> = emptyList(),
        champsDate: Collection<String> = emptyList(),
        /** Les textes et heures vidés, qui partent en "" comme sur le web. */
        champsVidesGardes: Collection<String> = emptyList(),
        /** uuid pré-généré côté client à injecter dans le corps sous [uuidFieldName]. Sert à
         *  connaître à l'avance l'uuid_attached_row pour l'envoi des médias qui suit. */
        uuidClient: String? = null,
        uuidFieldName: String? = null,
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Result.success(envoyerObjet(
                config, moduleCode, objectType, parentIdField, parentId, valeurs,
                nomsChampsSchema, champsTexteLibre, champsDate, champsVidesGardes, uuidClient, uuidFieldName,
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private suspend fun envoyerObjet(
        config: GeoNatureConfig,
        moduleCode: String,
        objectType: String,
        parentIdField: String?,
        parentId: Int?,
        valeurs: Map<String, Any?>,
        nomsChampsSchema: Collection<String>,
        champsTexteLibre: Collection<String>,
        champsDate: Collection<String>,
        champsVidesGardes: Collection<String>,
        uuidClient: String?,
        uuidFieldName: String?,
    ): Int {
        val base = config.urlServeur.trim().trimEnd('/')
        val (token, idRoleAuth, cookies) = auth.loginAvecCookies(base, config.login, config.motDePasse)
            ?: throw GNErreur.AuthEchouee(401)
        val properties = proprietes(
            parentIdField, parentId, valeurs, champsTexteLibre, champsDate, champsVidesGardes, uuidClient, uuidFieldName,
        )
        // `id_digitiser` : le serveur attend l'id_role de l'utilisateur qui enregistre. Contrainte
        // NOT NULL côté base Suivis → un 500 silencieux sans ce champ. Dérivé EN DIRECT du login
        // courant (comme Occtax/OccHab) plutôt que de la copie persistée `config.idRoleUtilisateur` :
        // évite d'estampiller l'ancien auteur après un changement de compte. Repli sur la valeur
        // persistée si l'authentification ne l'a pas renvoyé.
        val idRole = idRoleAuth ?: config.idRoleUtilisateur.takeIf { it > 0 }
        if (idRole != null && !properties.has("id_digitiser")) {
            properties.put("id_digitiser", idRole)
        }
        // Complément : le serveur Marshmallow vérifie que TOUTES les propriétés du schéma sont
        // présentes dans le corps, y compris les champs techniques cachés à l'écran (id_base_visit,
        // id_module, medias, nb_observations, observers_txt…). Les champs absents reçoivent null —
        // ce que fait aussi le formulaire web, sauf quelques champs de type tableau qui prennent `[]`.
        val champsArrayParDefaut = setOf("medias")
        nomsChampsSchema.forEach { k ->
            if (!properties.has(k)) {
                if (k in champsArrayParDefaut) properties.put(k, JSONArray()) else properties.put(k, JSONObject.NULL)
            }
        }

        // Forme simple `{properties: …}` — c'est exactement ce que le formulaire web GeoNature envoie
        // sur la même instance (vérifié avec les DevTools). L'enveloppe GeoJSON Feature complète
        // déclenche un 500 silencieux côté serveur.
        val corps = JSONObject().put("properties", properties)

        val url = "$base/api/monitorings/object/$moduleCode/$objectType"
        val reponse = clients.postJson(url, corps.toString(), token, cookies, timeoutMs = 20_000)
        val code = reponse.status.value
        if (code !in 200..299) {
            val erreur = try { reponse.bodyAsText() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            Journal.w(TAG, "POST $url → HTTP $code\n  réponse=$erreur")
            throw GNErreur.EnvoiEchoue(code, erreur?.take(500) ?: "pas de message")
        }
        val obj = JSONObject(reponse.bodyAsText())
        // L'API renvoie le nouvel objet sous forme de Feature GeoJSON. L'id se trouve soit à la
        // racine, soit dans `properties`, soit dans `id_<type>` du corps.
        return obj.optInt("id", -1).takeIf { it > 0 }
            ?: obj.optJSONObject("properties")?.optInt("id", -1)?.takeIf { it > 0 }
            ?: obj.optJSONObject("properties")?.optInt("id_$objectType", -1)?.takeIf { it > 0 }
            ?: 0
    }

    /** Modifie l'objet [id] déjà créé (visite, observation) avec ses [valeurs] actuelles :
     *  `PATCH /monitorings/object/<module>/<type>/<id>`, comme le web à l'édition (`patch`, dont le
     *  corps porte tous les champs du formulaire). Sans compléments null ni `[]` : le serveur garde une
     *  propriété générique absente du corps, et `medias: []` détacherait les médias ; sans
     *  `id_digitiser`, qui reste celui de la création. Sert à transmettre une saisie corrigée après un
     *  envoi partiel (audit du 21 octobre, C3 ; décision de l'utilisateur : « envoyer de vraies mises
     *  à jour »). */
    suspend fun modifierObjet(
        config: GeoNatureConfig,
        moduleCode: String,
        objectType: String,
        id: Int,
        parentIdField: String?,
        parentId: Int?,
        valeurs: Map<String, Any?>,
        champsTexteLibre: Collection<String> = emptyList(),
        champsDate: Collection<String> = emptyList(),
        champsVidesGardes: Collection<String> = emptyList(),
        uuidClient: String? = null,
        uuidFieldName: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val base = config.urlServeur.trim().trimEnd('/')
            val (token, _, cookies) = auth.loginAvecCookies(base, config.login, config.motDePasse)
                ?: throw GNErreur.AuthEchouee(401)
            val properties = proprietes(
                parentIdField, parentId, valeurs, champsTexteLibre, champsDate, champsVidesGardes, uuidClient, uuidFieldName,
            )
            properties.remove("medias")
            // Une valeur vidée part à null : absente du corps, le serveur garderait l'ancienne.
            valeurs.filterValues { it == "" }.keys.forEach { k -> if (!properties.has(k)) properties.put(k, JSONObject.NULL) }
            val url = "$base/api/monitorings/object/$moduleCode/$objectType/$id"
            val reponse = clients.patchJson(url, JSONObject().put("properties", properties).toString(), token, cookies, timeoutMs = 20_000)
            val code = reponse.status.value
            if (code !in 200..299) {
                val erreur = try { reponse.bodyAsText() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                Journal.w(TAG, "PATCH $url → HTTP $code\n  réponse=$erreur")
                throw GNErreur.EnvoiEchoue(code, erreur?.take(500) ?: "pas de message")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /** Les `properties` d'un objet, à la création comme à la modification : le parent, les [valeurs]
     *  au format du web, l'uuid client ; les "" retirés sauf les textes vidés ; la date de fin d'une
     *  visite reprise de son début. */
    private fun proprietes(
        parentIdField: String?,
        parentId: Int?,
        valeurs: Map<String, Any?>,
        champsTexteLibre: Collection<String>,
        champsDate: Collection<String>,
        champsVidesGardes: Collection<String>,
        uuidClient: String?,
        uuidFieldName: String?,
    ): JSONObject {
        val properties = JSONObject()
        // Lien au parent : injecté dans properties (le sélecteur de parent est masqué à l'écran, car
        // déjà choisi par la navigation qui amène ici).
        if (!parentIdField.isNullOrEmpty() && parentId != null && parentId > 0) {
            properties.put(parentIdField, parentId)
        }
        for ((code, brut) in valeurs) {
            // Un texte garde sa valeur String telle quelle : « 42 » dans un commentaire ne doit pas
            // partir en nombre (audit B6), ni une option « 1 ».
            val valeur = if (code in champsDate && brut is String) dateDuWeb(brut) else brut
            properties.put(code, normaliserPourJson(valeur, preserverString = code in champsTexteLibre))
        }
        // uuid pré-généré côté client (cas où un média sera rattaché après création). Injecté
        // seulement si l'appelant a fourni le nom du champ — sinon on ne sait pas où le mettre dans
        // le corps et le serveur générera son propre uuid.
        if (!uuidClient.isNullOrEmpty() && !uuidFieldName.isNullOrEmpty() && !properties.has(uuidFieldName)) {
            properties.put(uuidFieldName, uuidClient)
        }
        // `id_dataset` : celui du formulaire, jamais un jeu inventé ici. Le formulaire web n'en pose
        // pas d'autre, et un objet dont le schéma n'a pas ce champ (une observation) le recevait
        // dans ses données spécifiques.
        // Les "" : un texte ou une heure vidés par l'utilisateur partent tels quels, comme sur le web
        // ([champsVidesGardes]) ; les autres (une date, ou une saisie enregistrée avant le
        // 20 octobre 2026) sont retirés et partent à null, une date vide cassant la lecture du serveur.
        val cles = mutableListOf<String>()
        properties.keys().forEach { k -> cles.add(k) }
        cles.forEach { k ->
            val v = properties.opt(k)
            if (v is String && v.isEmpty() && (k !in champsVidesGardes || k in champsDate)) properties.remove(k)
        }
        // `visit_date_max` (en général NOT NULL en base) que le formulaire n'a pas remplie — masquée,
        // ou vidée — reprend `visit_date_min`, comme dans la 0.1.0 et GeoMys-Android. Le web la mettait
        // au jour de la saisie ; décision de l'utilisateur du 21 octobre : la date de début.
        val dateFin = NouvelleVisite.DATE_FIN_VISITE
        if (properties.has("visit_date_min") && !properties.has(dateFin)) {
            properties.put(dateFin, properties.opt("visit_date_min"))
        }
        return properties
    }

    private companion object {
        const val TAG = "MonitoringApi"

        /** Une date `aaaa-mm-jj` en `aaaa-m-j`, la forme que le `fromForm` du web envoie pour un
         *  widget `date` ; toute autre valeur reste telle quelle. */
        fun dateDuWeb(valeur: String): String {
            val (a, m, j) = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").find(valeur.trim())?.destructured ?: return valeur
            return "$a-${m.toInt()}-${j.toInt()}"
        }

        /** [preserverString] : quand true, une valeur String numérique reste String (texte), dans un
         *  tableau aussi. Sinon, une chaîne numérique devient un nombre : les "121" d'une saisie
         *  enregistrée avant le 20 octobre 2026 (observateurs…), que le serveur veut en Int. */
        fun normaliserPourJson(v: Any?, preserverString: Boolean = false): Any {
            if (v === JSONObject.NULL) return JSONObject.NULL
            return when (v) {
                null -> JSONObject.NULL
                is Boolean -> v
                is Number -> v
                // JSONArray arrive quand on relit un corps depuis la file d'envoi : chaque élément est
                // normalisé comme sa racine.
                is JSONArray -> {
                    val out = JSONArray()
                    for (i in 0 until v.length()) out.put(normaliserPourJson(v.opt(i), preserverString))
                    out
                }
                is JSONObject -> v
                is List<*> -> JSONArray().apply { v.forEach { put(normaliserPourJson(it, preserverString)) } }
                // Sans retirer d'espaces : le web envoie le texte tel quel.
                is String -> if (preserverString) v else v.trim().toIntOrNull() ?: v
                else -> v.toString()
            }
        }
    }
}
