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
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.io.IOException

/** Un serveur GeoNature simulé : il rend, dans l'ordre, les réponses mises en file par [repondre]
 *  (l'équivalent du MockWebServer des tests Android), et garde les requêtes reçues. Avec [router],
 *  il répond plutôt selon le chemin, comme le Dispatcher de MockWebServer. */
internal class ServeurSimule {

    /** [octets], s'il est donné, remplace [corps] : un corps binaire (image). */
    class Reponse(
        val code: Int,
        val corps: String,
        val entetes: List<Pair<String, String>>,
        val panne: Boolean,
        val octets: ByteArray? = null,
    )

    private val file = ArrayDeque<Reponse>()
    private var aiguillage: ((HttpRequestData) -> Reponse)? = null
    val requetes = mutableListOf<HttpRequestData>()

    /** Chemins des requêtes reçues, requête comprise (le `request.path` de MockWebServer). */
    val chemins: List<String> get() = requetes.map { it.url.encodedPathAndQuery }

    val moteur = MockEngine { requete ->
        requetes += requete
        val reponse = aiguillage?.invoke(requete)
            ?: file.removeFirstOrNull() ?: error("aucune réponse prévue pour ${requete.url}")
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
        file.addLast(Reponse(code, corps, entetes.toList(), panne = false))
    }

    fun repondre(reponse: Reponse) {
        file.addLast(reponse)
    }

    fun repondreJson(code: Int, corps: String, vararg entetes: Pair<String, String>) =
        repondre(code, corps, "Content-Type" to "application/json", *entetes)

    fun couperLeReseau() {
        file.addLast(Reponse(0, "", emptyList(), panne = true))
    }

    /** Répond selon le préfixe du chemin, la première route qui convient l'emportant ; 404 sans
     *  route. La file n'est alors plus lue. */
    fun router(vararg routes: Pair<String, () -> Reponse>) {
        router { chemin -> routes.firstOrNull { chemin.startsWith(it.first) }?.second?.invoke() ?: statut(404) }
    }

    /** Répond selon le chemin complet, requête comprise. La file n'est alors plus lue. */
    fun router(aiguillage: (chemin: String) -> Reponse) {
        routerRequetes { requete -> aiguillage(requete.url.encodedPathAndQuery) }
    }

    /** Répond selon la requête entière (méthode, corps…). La file n'est alors plus lue. */
    fun routerRequetes(aiguillage: (HttpRequestData) -> Reponse) {
        this.aiguillage = aiguillage
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
    }
}

internal val HttpRequestData.texte: String get() = (body as TextContent).text
