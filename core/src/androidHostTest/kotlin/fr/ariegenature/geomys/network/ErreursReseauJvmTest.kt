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

package fr.ariegenature.geomys.network

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.network.http.moteurHttpPlateforme
import fr.ariegenature.geomys.store.CoffreEnMemoire
import fr.ariegenature.geomys.store.GeoNatureConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.utils.io.ClosedReadChannelException
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

/** Le classement des erreurs réseau sous Android, et le contrat « erreur réseau = IOException »
 *  avec le vrai moteur (OkHttp). */
class ErreursReseauJvmTest {

    @Test
    fun exceptionsJavaNet() {
        assertEquals(ErreurReseau.HOTE_INCONNU, classerErreurReseau(UnknownHostException("geonature.test")))
        assertEquals(ErreurReseau.DELAI_DEPASSE, classerErreurReseau(SocketTimeoutException()))
        assertEquals(ErreurReseau.CONNEXION_IMPOSSIBLE, classerErreurReseau(ConnectException("refusée")))
        assertEquals(null, classerErreurReseau(IllegalStateException()))
    }

    @Test
    fun exceptionsTls() {
        // Négociation coupée, par un réseau trop faible par exemple : connexion sécurisée impossible.
        val coupee = SSLHandshakeException("Connection closed by peer")
        assertEquals(ErreurReseau.CONNEXION_SECURISEE, classerErreurReseau(coupee))
        assertEquals(ErreurReseau.CONNEXION_SECURISEE, classerErreurReseau(SSLException("Read error: ssl=0x7b: I/O error during system call")))
        // Certificat refusé : expiré, chaîne non reconnue (message d'Android), nom d'hôte non vérifié (OkHttp).
        val expire = SSLHandshakeException("certificat").apply { initCause(CertificateExpiredException("NotAfter")) }
        assertEquals(ErreurReseau.CERTIFICAT, classerErreurReseau(expire))
        val inconnu = SSLHandshakeException("Trust anchor for certification path not found.")
            .apply { initCause(CertificateException(CertPathValidatorException("Trust anchor for certification path not found."))) }
        assertEquals(ErreurReseau.CERTIFICAT, classerErreurReseau(inconnu))
        assertEquals(ErreurReseau.CERTIFICAT, classerErreurReseau(SSLPeerUnverifiedException("Hostname geonature.test not verified")))
        assertEquals(CONNEXION_SECURISEE_IMPOSSIBLE, humaniserErreurReseau(coupee))
        assertEquals(CERTIFICAT_REFUSE, humaniserErreurReseau(expire))
        // Android 11 et plus : une fermeture pendant la négociation (cause EOFException), et un RST,
        // qui arrive en SocketException et non en SSLException.
        assertEquals(ErreurReseau.CONNEXION_SECURISEE, classerErreurReseau(SSLHandshakeException("connection closed").apply { initCause(EOFException("connection closed")) }))
        assertEquals(ErreurReseau.CONNEXION_IMPOSSIBLE, classerErreurReseau(SocketException("Connection reset")))
    }

    @Test
    fun lesEnveloppesDeKtorEtLesRoutesSuivantesDOkHttpSontLues() {
        // Erreur pendant la lecture du corps : Ktor l'enveloppe dans une ClosedReadChannelException.
        assertEquals(ErreurReseau.CONNEXION_SECURISEE, classerErreurReseau(ClosedReadChannelException(SSLException("Read error: ssl=0x7b: I/O error during system call, Connection reset by peer"))))
        assertEquals(ErreurReseau.DELAI_DEPASSE, classerErreurReseau(ClosedReadChannelException(SocketTimeoutException("timeout"))))
        assertEquals("timeout", detailErreurReseau(ClosedReadChannelException(SocketTimeoutException("timeout"))))
        // OkHttp 5 relève l'erreur de la première route (IPv6) ; celles des suivantes sont en « suppressed ».
        val certificatSurLaRouteSuivante = ConnectException("Failed to connect to /2001:db8::1:443").apply {
            addSuppressed(SSLHandshakeException("certificat").apply { initCause(CertificateException("expiré")) })
        }
        assertEquals(ErreurReseau.CERTIFICAT, classerErreurReseau(certificatSurLaRouteSuivante))
        val negociationCoupeeSurLaRouteSuivante = ConnectException("Failed to connect to /2001:db8::1:443").apply {
            addSuppressed(SSLHandshakeException("connection closed"))
        }
        assertEquals(ErreurReseau.CONNEXION_SECURISEE, classerErreurReseau(negociationCoupeeSurLaRouteSuivante))
    }

    @Test
    fun leDetailPerdSonPointFinalEntreParentheses() {
        assertEquals("hôte introuvable", detailErreurReseau(UnknownHostException("hôte introuvable.")))
        assertTrue(humaniserErreurReseau(UnknownHostException("hôte introuvable.")).contains("(hôte introuvable). "))
    }

    @Test
    fun leTestDeConnexionExpliqueUneNegociationTlsCoupee() = runTest {
        val serveur = ServeurSimule()
        serveur.routerRequetes { throw SSLHandshakeException("Connection closed by peer") }
        val config = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
            urlServeur = serveur.base
            login = "alice"
            motDePasse = "pwd"
        }
        val (ok, message) = GeoNatureAuth(serveur.clients).testerConnexion(config)
        assertFalse(ok)
        assertEquals(CONNEXION_SECURISEE_IMPOSSIBLE, message)
    }

    @Test
    fun uneConnexionRefuseeEstUneIOExceptionConnexionImpossible() = runTest {
        // Le port 9 (discard) n'écoute pas : refus immédiat, sans réseau ni DNS.
        val erreur = runCatching { HttpClient(moteurHttpPlateforme()).get("http://127.0.0.1:9/api/auth/login") }.exceptionOrNull()
        assertTrue(erreur is IOException, "obtenu : $erreur")
        assertEquals(ErreurReseau.CONNEXION_IMPOSSIBLE, classerErreurReseau(erreur))
        assertTrue(humaniserErreurReseau(erreur).startsWith("Connexion au serveur impossible"))
    }
}
