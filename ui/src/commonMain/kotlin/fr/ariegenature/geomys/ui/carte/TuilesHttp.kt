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

package fr.ariegenature.geomys.ui.carte

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.fromHttpToGmtDate
import io.ktor.http.toHttpDate
import io.ktor.util.date.GMTDate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceKind
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceRequest

/** Les tuiles de certains serveurs ([hotes]), téléchargées par l'appli elle-même plutôt que par
 *  MapLibre, pour maîtriser les en-têtes : l'intercepteur de maplibre-compose 0.20.0 n'en applique
 *  aucun (PROTOTYPE-CARTE.md § 2), et la politique d'usage d'OpenStreetMap demande un User-Agent
 *  qui identifie l'appli. [client] le porte.
 *
 *  Les réponses gardent leurs informations de cache (ETag, dates d'expiration et de modification) :
 *  MapLibre s'en sert pour revalider une tuile (réponse 304) au lieu de la retélécharger. */
internal class TuilesHttp(private val client: HttpClient, private val hotes: Set<String>) {

    fun accepte(requete: MapResourceRequest): Boolean =
        requete.kind == MapResourceKind.Tile && hote(requete.url) in hotes

    suspend fun charger(requete: MapResourceLoadRequest): MapResourceLoad {
        val reponse = try {
            client.get(requete.url) {
                requete.priorEtag?.let { header(HttpHeaders.IfNoneMatch, it) }
                requete.priorModified?.let { header(HttpHeaders.IfModifiedSince, GMTDate(it.toEpochMilliseconds()).toHttpDate()) }
                timeout {
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 30_000
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return MapResourceLoad.Failed(MapResourceError.Connection, e.message ?: "serveur de tuiles injoignable")
        }
        val modifiee = date(reponse.headers[HttpHeaders.LastModified])
        val expire = expiration(reponse)
        return when (val code = reponse.status.value) {
            200 -> MapResourceLoad.Bytes(reponse.readRawBytes(), reponse.headers[HttpHeaders.ETag], aRevalider(reponse), modifiee, expire)
            204 -> MapResourceLoad.NoContent(modifiee, expire)
            304 -> MapResourceLoad.NotModified(modifiee, expire)
            404 -> MapResourceLoad.Failed(MapResourceError.NotFound, "tuile absente (HTTP 404)", null, modifiee, expire)
            429 -> MapResourceLoad.Failed(MapResourceError.RateLimit, "trop de requêtes (HTTP 429)", reessayerApres(reponse), null, null)
            in 500..599 -> MapResourceLoad.Failed(MapResourceError.Server, "erreur du serveur de tuiles (HTTP $code)")
            else -> MapResourceLoad.Failed(MapResourceError.Other, "réponse inattendue du serveur de tuiles (HTTP $code)")
        }
    }

    private fun hote(url: String): String? = runCatching { Url(url).host }.getOrNull()

    private fun date(texte: String?): Instant? =
        texte?.let { runCatching { Instant.fromEpochMilliseconds(it.fromHttpToGmtDate().timestamp) }.getOrNull() }

    /** `Cache-Control: max-age` d'abord, puis `Expires`, comme le cache HTTP de MapLibre. */
    private fun expiration(reponse: HttpResponse): Instant? {
        val maxAge = MAX_AGE.find(reponse.headers[HttpHeaders.CacheControl].orEmpty())?.groupValues?.get(1)?.toLongOrNull()
        return if (maxAge != null) Clock.System.now() + maxAge.seconds else date(reponse.headers[HttpHeaders.Expires])
    }

    private fun aRevalider(reponse: HttpResponse): Boolean {
        val cacheControl = reponse.headers[HttpHeaders.CacheControl].orEmpty()
        return "no-cache" in cacheControl || "must-revalidate" in cacheControl
    }

    private fun reessayerApres(reponse: HttpResponse): Instant? =
        reponse.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.let { Clock.System.now() + it.seconds }

    private companion object {
        val MAX_AGE = Regex("max-age=(\\d+)")
    }
}
