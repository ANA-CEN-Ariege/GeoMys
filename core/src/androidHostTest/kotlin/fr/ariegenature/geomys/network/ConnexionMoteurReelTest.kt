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

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.moteurHttpPlateforme
import fr.ariegenature.geomys.store.CoffreEnMemoire
import fr.ariegenature.geomys.store.GeoNatureConfig
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.client.request.post
import kotlinx.coroutines.test.runTest

/** La vérification de connexion avec les vrais moteurs (OkHttp) face à un serveur qui ferme chaque
 *  connexion juste après sa réponse, comme un serveur réel ferme les connexions inactives. Le moteur
 *  des lectures doit alors rouvrir une connexion plutôt que d'échouer (`unexpected end of stream`),
 *  et les écritures partir avec `Connection: close`. */
class ConnexionMoteurReelTest {

    private val requetes = Collections.synchronizedList(mutableListOf<String>())
    private val serveur = ServeurQuiFerme { methode, chemin, entetes, corps ->
        requetes += "$methode $chemin ${entetes["connection"].orEmpty()}"
        when {
            methode == "POST" && chemin == "/api/auth/login" && "\"password\":\"secret\"" in corps -> Reponse(
                200,
                """{"access_token":"jeton","user":{"id_role":42,"prenom_role":"Alice","nom_role":"Test"}}""",
                "Content-Type" to "application/json",
                "Set-Cookie" to "session=s1; Path=/",
                "Set-Cookie" to "csrf=c2; Path=/",
            )
            methode == "POST" && chemin == "/api/auth/login" -> Reponse(302, "", "Location" to "/#/login")
            chemin == "/api/gn_commons/config" -> Reponse(200, """{"GEONATURE_VERSION":"2.16.0"}""", "Content-Type" to "application/json")
            chemin == "/api/gn_commons/modules" && entetes["authorization"] == "Bearer jeton" &&
                entetes["cookie"] == "session=s1; csrf=c2" -> Reponse(200, "[]", "Content-Type" to "application/json")
            chemin == "/api/test/ecriture" -> Reponse(201, "{}", "Content-Type" to "application/json")
            else -> Reponse(401, "{}", "Content-Type" to "application/json")
        }
    }
    private val clients = ClientsGeoNature(moteurHttpPlateforme(), moteurHttpPlateforme(ecriture = true))
    private val verification = VerificationConnexion(GeoNatureAuth(clients), clients)

    private fun config(motDePasse: String) = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
        urlServeur = "http://127.0.0.1:${serveur.port}"
        login = "alice"
        this.motDePasse = motDePasse
    }

    @AfterTest
    fun arreter() = serveur.close()

    @Test
    fun connexionEtAppelAuthentifieMalgreLesConnexionsFermees() = runTest {
        val resultat = verification.verifier(config("secret"))
        assertTrue(resultat.reussi, resultat.message)
        assertEquals("2.16.0", resultat.version)
        assertEquals(42, resultat.idRole)
        assertEquals(200, resultat.appelAuthentifie)
    }

    @Test
    fun leLoginNeSuitPasLaRedirectionDUnMauvaisMotDePasse() = runTest {
        val resultat = verification.verifier(config("mauvais"))
        assertEquals("Identifiant ou mot de passe incorrect", resultat.message)
        assertEquals(1, requetes.size, "une seule requête : la redirection n'est pas suivie")
        assertTrue(requetes.single().startsWith("POST /api/auth/login"))
    }

    @Test
    fun lesEcrituresDemandentLaFermetureDeLaConnexion() = runTest {
        clients.ecriture.post("http://127.0.0.1:${serveur.port}/api/test/ecriture")
        assertEquals(listOf("POST /api/test/ecriture close"), requetes.toList())
    }
}

private class Reponse(val code: Int, val corps: String, vararg val entetes: Pair<String, String>)

/** Un serveur HTTP/1.1 minimal qui ferme chaque connexion juste après sa réponse, sans l'annoncer
 *  (pas d'en-tête `Connection: close`) : le client croit pouvoir la réutiliser. */
private class ServeurQuiFerme(
    private val repondre: (methode: String, chemin: String, entetes: Map<String, String>, corps: String) -> Reponse,
) : AutoCloseable {
    private val ecoute = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val port: Int = ecoute.localPort

    init {
        thread(isDaemon = true) {
            while (!ecoute.isClosed) {
                val connexion = try { ecoute.accept() } catch (_: Exception) { break }
                try { connexion.use { traiter(it) } } catch (_: Exception) { }
            }
        }
    }

    private fun traiter(connexion: Socket) {
        val entree = connexion.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val (methode, chemin) = entree.readLine().split(" ").let { it[0] to it[1] }
        val entetes = generateSequence { entree.readLine()?.takeIf { it.isNotEmpty() } }
            .associate { it.substringBefore(":").trim().lowercase() to it.substringAfter(":").trim() }
        val longueur = entetes["content-length"]?.toInt() ?: 0
        val corps = CharArray(longueur).also { var lu = 0; while (lu < longueur) lu += entree.read(it, lu, longueur - lu) }.concatToString()
        val reponse = repondre(methode, chemin, entetes, corps)
        val octets = reponse.corps.toByteArray()
        val texte = buildString {
            append("HTTP/1.1 ${reponse.code} X\r\n")
            append("Content-Length: ${octets.size}\r\n")
            for ((nom, valeur) in reponse.entetes) append("$nom: $valeur\r\n")
            append("\r\n")
        }
        connexion.getOutputStream().apply {
            write(texte.toByteArray(Charsets.ISO_8859_1))
            write(octets)
            flush()
        }
    }

    override fun close() = ecoute.close()
}
