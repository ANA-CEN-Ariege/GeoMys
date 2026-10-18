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
 *  (l'équivalent du MockWebServer des tests Android), et garde les requêtes reçues. */
internal class ServeurSimule {

    class Reponse(val code: Int, val corps: String, val entetes: List<Pair<String, String>>, val panne: Boolean)

    private val file = ArrayDeque<Reponse>()
    val requetes = mutableListOf<HttpRequestData>()

    val moteur = MockEngine { requete ->
        requetes += requete
        val reponse = file.removeFirstOrNull() ?: error("aucune réponse prévue pour ${requete.url}")
        if (reponse.panne) throw IOException("réseau coupé")
        respond(
            content = reponse.corps,
            status = HttpStatusCode.fromValue(reponse.code),
            headers = headersOf(*reponse.entetes.groupBy({ it.first }, { it.second }).toList().toTypedArray()),
        )
    }

    val clients = ClientsGeoNature(moteur)

    val base = "https://geonature.test"

    fun repondre(code: Int, corps: String = "", vararg entetes: Pair<String, String>) {
        file.addLast(Reponse(code, corps, entetes.toList(), panne = false))
    }

    fun repondreJson(code: Int, corps: String, vararg entetes: Pair<String, String>) =
        repondre(code, corps, "Content-Type" to "application/json", *entetes)

    fun couperLeReseau() {
        file.addLast(Reponse(0, "", emptyList(), panne = true))
    }
}

internal val HttpRequestData.texte: String get() = (body as TextContent).text
