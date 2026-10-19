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

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.network.http.ClientsGeoNature
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.delay
import kotlinx.io.IOException

/** Un serveur GeoNature simulé : il rend, dans l'ordre, les réponses mises en file par [repondre]
 *  (l'équivalent du MockWebServer des tests Android), et garde les requêtes reçues. Avec [router],
 *  il répond plutôt selon le chemin, comme le Dispatcher de MockWebServer. Il supporte les envois
 *  concurrents (deux écrans qui envoient la même saisie) : sa file et son relevé des requêtes sont
 *  sous verrou, mais les compteurs d'un aiguillage restent à la charge du test. */
internal class ServeurSimule {

    /** [octets], s'il est donné, remplace [corps] : un corps binaire (image). [delaiMs] retarde la
     *  réponse, requête reçue (le setBodyDelay de MockWebServer). */
    class Reponse(
        val code: Int,
        val corps: String,
        val entetes: List<Pair<String, String>>,
        val panne: Boolean,
        val octets: ByteArray? = null,
        val delaiMs: Long = 0,
    )

    private val verrou = SynchronizedObject()
    private val file = ArrayDeque<Reponse>()
    private var aiguillage: (suspend (HttpRequestData) -> Reponse)? = null
    private val recues = mutableListOf<HttpRequestData>()

    /** Corps des requêtes qui ne sont pas du texte (multipart des médias), lus à la réception : un
     *  corps écrit en flux ne se relit pas après coup. */
    private val corpsEcrits = mutableMapOf<HttpRequestData, ByteArray>()

    /** Les requêtes reçues, dans l'ordre. */
    val requetes: List<HttpRequestData> get() = synchronized(verrou) { recues.toList() }

    /** Chemins des requêtes reçues, requête comprise (le `request.path` de MockWebServer). */
    val chemins: List<String> get() = requetes.map { it.url.encodedPathAndQuery }

    val moteur = MockEngine { requete ->
        val ecrit = when (requete.body) {
            is TextContent, is OutgoingContent.NoContent -> null
            else -> requete.body.toByteArray()
        }
        synchronized(verrou) {
            recues += requete
            if (ecrit != null) corpsEcrits[requete] = ecrit
        }
        val reponse = synchronized(verrou) { aiguillage }?.invoke(requete)
            ?: synchronized(verrou) { file.removeFirstOrNull() } ?: error("aucune réponse prévue pour ${requete.url}")
        if (reponse.delaiMs > 0) delay(reponse.delaiMs)
        if (reponse.panne) throw IOException("réseau coupé")
        respond(
            content = reponse.octets ?: reponse.corps.encodeToByteArray(),
            status = HttpStatusCode.fromValue(reponse.code),
            headers = headersOf(*reponse.entetes.groupBy({ it.first }, { it.second }).toList().toTypedArray()),
        )
    }

    val clients = ClientsGeoNature(moteur)

    val base = "https://geonature.test"

    fun repondre(code: Int, corps: String = "", vararg entetes: Pair<String, String>) {
        repondre(Reponse(code, corps, entetes.toList(), panne = false))
    }

    fun repondre(reponse: Reponse) {
        synchronized(verrou) { file.addLast(reponse) }
    }

    fun repondreJson(code: Int, corps: String, vararg entetes: Pair<String, String>) =
        repondre(code, corps, "Content-Type" to "application/json", *entetes)

    fun couperLeReseau() {
        repondre(panne())
    }

    /** Le corps de [requete] en texte, quelle que soit sa forme : JSON, ou multipart (médias). */
    fun corps(requete: HttpRequestData): String =
        (requete.body as? TextContent)?.text ?: synchronized(verrou) { corpsEcrits[requete] }?.decodeToString().orEmpty()

    /** Répond selon le préfixe du chemin, la première route qui convient l'emportant ; 404 sans
     *  route. La file n'est alors plus lue. */
    fun router(vararg routes: Pair<String, () -> Reponse>) {
        router { chemin -> routes.firstOrNull { chemin.startsWith(it.first) }?.second?.invoke() ?: statut(404) }
    }

    /** Répond selon le chemin complet, requête comprise. La file n'est alors plus lue. */
    fun router(aiguillage: (chemin: String) -> Reponse) {
        routerRequetes { requete -> aiguillage(requete.url.encodedPathAndQuery) }
    }

    /** Répond selon la requête entière (méthode, corps…). La file n'est alors plus lue. L'aiguillage
     *  peut suspendre : il agit alors pendant que le client attend la réponse (une saisie modifiée
     *  au moment exact d'un POST, par exemple). */
    fun routerRequetes(aiguillage: suspend (HttpRequestData) -> Reponse) {
        synchronized(verrou) { this.aiguillage = aiguillage }
    }

    companion object {
        fun statut(code: Int, corps: String = "", vararg entetes: Pair<String, String>) =
            Reponse(code, corps, entetes.toList(), panne = false)

        fun json(corps: String, code: Int = 200) = statut(code, corps, "Content-Type" to "application/json")

        /** Coupure réseau : la requête part, aucune réponse ne revient. */
        fun panne() = Reponse(0, "", emptyList(), panne = true)

        /** Un 200 au corps binaire. */
        fun octets(contenu: ByteArray, typeDeContenu: String) =
            Reponse(200, "", listOf("Content-Type" to typeDeContenu), panne = false, octets = contenu)

        /** La valeur du champ [nom] d'un corps multipart (form-data), ou null s'il n'y figure pas. Ktor
         *  ne met le nom entre guillemets que s'il le faut, et fait suivre ses propres en-têtes
         *  (Content-Length) : la forme écrite à la main par GeoMys-Android n'est pas la seule. */
        fun champMultipart(corps: String, nom: String): String? =
            Regex("""name="?${Regex.escape(nom)}"?(?:;[^\r\n]*)?\r\n(?:[^\r\n]+\r\n)*\r\n(.*?)\r\n""", RegexOption.DOT_MATCHES_ALL)
                .find(corps)?.groupValues?.get(1)
    }
}

internal val HttpRequestData.texte: String get() = (body as TextContent).text
