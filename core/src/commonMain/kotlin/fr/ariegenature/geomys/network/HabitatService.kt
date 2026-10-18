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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/HabitatService.kt) :
// HttpURLConnection remplacé par ClientsGeoNature, URLEncoder par encodeURLParameter (les espaces
// deviennent %20 au lieu de « + » ; le serveur décode les deux), org.json par son portage ; l'objet
// devient une classe sur les deux caches HABREF ; HabitatSuggestion passe dans model/.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.model.HabitatSuggestion
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.get
import fr.ariegenature.geomys.store.HabitatCacheAcces
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** Recherche d'habitats (référentiel HABREF) pour le champ `cd_hab` du relevé Occtax.
 *
 *  Endpoint **public** (pas d'auth) `GET /api/habref/habitats/autocomplete?search_name=…&limit=…`,
 *  qui renvoie une liste de `{cd_hab, lb_code, lb_nom_typo, search_name}`. On retient `cd_hab`
 *  (valeur envoyée à l'upload) et `search_name` comme libellé d'affichage (déjà préfixé du code).
 *
 *  Tolérant : terme trop court → liste vide ; erreur réseau / serveur indisponible → repli sur le
 *  cache local [HabitatCache] (habitats déjà rencontrés en ligne → champ utilisable HORS LIGNE),
 *  jamais de crash. Chaque recherche en ligne alimente ce cache. */
class HabitatService(
    private val clients: ClientsGeoNature,
    private val habitatsOcctax: HabitatCacheAcces,
    private val habitatsOccHab: HabitatCacheAcces,
) {

    /** Recherche d'habitats.
     *  - [occhab] false (Occtax) → cache complet OCCTAX si dispo, sinon serveur (+ [idList]).
     *  - [occhab] true (OccHab)  → cache DÉDIÉ OccHab (jamais le cache OCCTAX ; recherche
     *    sensible aux accents = mêmes résultats que le web) ; sinon serveur avec `id_list`.
     *  [idList] = `OCCHAB.ID_LIST_HABITAT` (souvent null → tout HABREF). */
    suspend fun rechercher(
        base: String, terme: String, limite: Int = 20,
        idList: Int? = null, occhab: Boolean = false,
    ): List<HabitatSuggestion> =
        withContext(Dispatchers.IO) {
            val t = terme.trim()
            if (t.length < 2) return@withContext emptyList()
            val urlBase = base.trim().trimEnd('/')
            val url = "$urlBase/api/habref/habitats/autocomplete" +
                "?search_name=${t.encodeURLParameter()}&limit=$limite" +
                (idList?.let { "&id_list=$it" } ?: "")
            // Cache du bon périmètre.
            val cache: HabitatCacheAcces = if (occhab) habitatsOccHab else habitatsOcctax
            // Cache présent → recherche LOCALE (hors-ligne, sans round-trip par frappe).
            if (cache.estDisponible) return@withContext cache.rechercher(t, limite)
            // Cache absent → requête serveur (avec id_list éventuel).
            try {
                val reponse = clients.get(url, timeoutMs = 8000)
                if (reponse.status.value != 200) {
                    return@withContext if (cache.estDisponible) cache.rechercher(t, limite) else emptyList()
                }
                val arr = JSONArray(reponse.bodyAsText())
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val cd = o.optInt("cd_hab", -1)
                    if (cd <= 0) return@mapNotNull null
                    val libelle = o.optString("search_name").ifBlank { o.optString("lb_code") }
                        .trim().ifBlank { cd.toString() }
                    HabitatSuggestion(cd, libelle, o.optInt("cd_typo", -1).takeIf { it > 0 })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (cache.estDisponible) cache.rechercher(t, limite) else emptyList()
            }
        }
}
