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

package fr.ariegenature.geomys.network.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlin.concurrent.Volatile
import kotlinx.io.IOException

/** Les clients HTTP de l'appli : OkHttp sous Android, NSURLSession sous iOS (voir
 *  [moteurHttpPlateforme]), MockEngine dans les tests. Ils remplacent les HttpURLConnection de
 *  GeoMys-Android (network/HttpClient.kt) :
 *  - aucun code HTTP ne lève d'exception (`expectSuccess = false`) : les appelants lisent le code,
 *    comme avant ;
 *  - les délais se posent requête par requête (`timeout { }`), sans plafond de durée totale :
 *    `socketTimeoutMillis` tient lieu du readTimeout d'Android ;
 *  - un 401, sur n'importe quel appel, appelle [surNonAutorise] (lireCode sous Android) : la session
 *    est vidée, et c'est l'appel suivant qui se reconnecte.
 *
 *  Les requêtes qui créent ou modifient des données (POST, DELETE) passent par [ecriture], sur son
 *  propre moteur ([moteurEcriture]) qui ne rejoue jamais une requête. */
class ClientsGeoNature(moteurLecture: HttpClientEngine, moteurEcriture: HttpClientEngine = moteurLecture) {

    /** Branché par GeoNatureAuth sur son invaliderCache. */
    @Volatile
    var surNonAutorise: () -> Unit = {}

    /** Le client des lectures : il suit les redirections, et son moteur rouvre une connexion
     *  gardée ouverte que le serveur a fermée entre-temps (rejouer une lecture est sans danger). */
    val standard: HttpClient = creer(moteurLecture, suivreRedirections = true)

    /** Le client du login, sans redirection : GeoNature répond à des identifiants refusés par un
     *  302 vers sa page de connexion (`/#/login`), qui passerait sinon pour une réponse valide.
     *  Rejouer un login est sans danger : il partage le moteur des lectures. */
    val sansRedirection: HttpClient = creer(moteurLecture, suivreRedirections = false)

    /** Le client des écritures (relevés, occurrences, visites, médias) : `Connection: close` sur
     *  chaque requête, comme les POST de GeoMys-Android, donc une connexion neuve à chaque fois, sur
     *  un moteur qui ne rejoue jamais rien. Une écriture dont la réponse s'est perdue n'est donc
     *  jamais renvoyée en silence : le serveur l'aurait traitée deux fois (doublons). Ktor ne suit
     *  pas les redirections d'un POST. */
    val ecriture: HttpClient = creer(moteurEcriture, suivreRedirections = true, fermerLaConnexion = true)

    private fun creer(moteur: HttpClientEngine, suivreRedirections: Boolean, fermerLaConnexion: Boolean = false): HttpClient {
        val client = HttpClient(moteur) {
            expectSuccess = false
            followRedirects = suivreRedirections
            install(HttpTimeout)
            if (fermerLaConnexion) {
                defaultRequest { header(HttpHeaders.Connection, "close") }
            }
        }
        client.plugin(HttpSend).intercept { requete ->
            val appel = execute(requete)
            if (appel.response.status.value == 401) surNonAutorise()
            appel
        }
        return client
    }
}

/** [texte] comme URL absolue http(s). Ktor complète une URL relative avec `localhost` : sans ce
 *  contrôle, une URL de serveur vide enverrait les identifiants sur l'appareil lui-même. Lève une
 *  IOException, comme le `MalformedURLException` de `java.net.URL` sous Android : pour les
 *  appelants, c'est un problème de connexion, pas un refus d'authentification. */
fun urlAbsolue(texte: String): Url {
    val schemaOk = texte.startsWith("http://", ignoreCase = true) || texte.startsWith("https://", ignoreCase = true)
    val url = if (schemaOk) runCatching { Url(texte) }.getOrNull() else null
    if (url == null || url.host.isEmpty()) {
        throw IOException("URL invalide : $texte")
    }
    return url
}
