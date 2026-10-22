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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/HttpClient.kt,
// get, postJson et delete) : mêmes paramètres, pour que les appels du code réseau se portent tels
// quels. Le corps se lit ensuite par bodyAsText(), le code par status.value (le 401 vide la session,
// comme lireCode). Les variantes « EnFlux » et corpsEnTampon remplacent la lecture en flux de Gson.

package fr.ariegenature.geomys.network.http

import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import okio.Buffer

/** GET JSON. `token`/`cookies` optionnels (en-têtes ajoutés seulement si non vides). */
suspend fun ClientsGeoNature.get(
    url: String,
    token: String? = null,
    cookies: String = "",
    timeoutMs: Long = 10_000,
    readTimeoutMs: Long = timeoutMs,
): HttpResponse = standard.get(urlAbsolue(url)) { configurer(token, cookies, timeoutMs, readTimeoutMs) }

/** POST avec corps JSON, par le client des écritures : `Connection: close` et jamais de rejeu. Sous
 *  HttpURLConnection, la connexion réutilisée pouvait REJOUER silencieusement un POST, et le serveur
 *  le traiter deux fois (relevés, occurrences ou visites en double). */
suspend fun ClientsGeoNature.postJson(
    url: String,
    corps: String,
    token: String? = null,
    cookies: String = "",
    timeoutMs: Long = 10_000,
    readTimeoutMs: Long = timeoutMs,
): HttpResponse = ecriture.post(urlAbsolue(url)) {
    configurer(token, cookies, timeoutMs, readTimeoutMs)
    contentType(ContentType.Application.Json)
    setBody(corps)
}

/** PATCH avec corps JSON, par le client des écritures, comme [postJson] : la modification d'un objet
 *  des Suivis (`/monitorings/object/<module>/<type>/<id>`). */
suspend fun ClientsGeoNature.patchJson(
    url: String,
    corps: String,
    token: String? = null,
    cookies: String = "",
    timeoutMs: Long = 10_000,
    readTimeoutMs: Long = timeoutMs,
): HttpResponse = ecriture.patch(urlAbsolue(url)) {
    configurer(token, cookies, timeoutMs, readTimeoutMs)
    contentType(ContentType.Application.Json)
    setBody(corps)
}

/** [get] dont [lire] reçoit la réponse AVANT que Ktor n'en garde tout le corps en mémoire (il le fait
 *  pour une réponse ordinaire). Pour les longues listes : jeux de données, HABREF complet. */
suspend fun <T> ClientsGeoNature.getEnFlux(
    url: String,
    token: String? = null,
    cookies: String = "",
    timeoutMs: Long = 10_000,
    readTimeoutMs: Long = timeoutMs,
    lire: suspend (HttpResponse) -> T,
): T = standard.prepareGet(urlAbsolue(url)) { configurer(token, cookies, timeoutMs, readTimeoutMs) }.execute(lire)

/** [postJson] dont [lire] reçoit la réponse avant que Ktor n'en garde le corps (cf. [getEnFlux]). */
suspend fun <T> ClientsGeoNature.postJsonEnFlux(
    url: String,
    corps: String,
    token: String? = null,
    cookies: String = "",
    timeoutMs: Long = 10_000,
    readTimeoutMs: Long = timeoutMs,
    lire: suspend (HttpResponse) -> T,
): T = ecriture.preparePost(urlAbsolue(url)) {
    configurer(token, cookies, timeoutMs, readTimeoutMs)
    contentType(ContentType.Application.Json)
    setBody(corps)
}.execute(lire)

/** Le corps de la réponse dans un tampon okio, lu par morceaux : ni chaîne géante (deux octets par
 *  caractère), ni tableau d'un seul tenant. Se lit ensuite en flux (cf. lireTableauEnFlux). */
suspend fun HttpResponse.corpsEnTampon(): Buffer {
    val canal = bodyAsChannel()
    val tampon = Buffer()
    val morceau = ByteArray(8 * 1024)
    while (true) {
        val lus = canal.readAvailable(morceau, 0, morceau.size)
        if (lus == -1) break
        tampon.write(morceau, 0, lus)
    }
    return tampon
}

/** DELETE JSON, par le client des écritures. */
suspend fun ClientsGeoNature.delete(
    url: String,
    token: String? = null,
    cookies: String = "",
    timeoutMs: Long = 15_000,
    readTimeoutMs: Long = timeoutMs,
): HttpResponse = ecriture.delete(urlAbsolue(url)) { configurer(token, cookies, timeoutMs, readTimeoutMs) }

private fun HttpRequestBuilder.configurer(token: String?, cookies: String, timeoutMs: Long, readTimeoutMs: Long) {
    header(HttpHeaders.Accept, "application/json")
    if (token != null) bearerAuth(token)
    if (cookies.isNotEmpty()) header(HttpHeaders.Cookie, cookies)
    timeout {
        connectTimeoutMillis = timeoutMs
        socketTimeoutMillis = readTimeoutMs
    }
}
