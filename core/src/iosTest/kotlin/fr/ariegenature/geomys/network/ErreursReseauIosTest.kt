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
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.request.get
import io.ktor.utils.io.ClosedByteChannelException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import platform.Foundation.NSError
import platform.Foundation.NSLocalizedDescriptionKey
import platform.Foundation.NSPOSIXErrorDomain
import platform.Foundation.NSURLErrorBadServerResponse
import platform.Foundation.NSURLErrorClientCertificateRequired
import platform.Foundation.NSURLErrorDataNotAllowed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorSecureConnectionFailed
import platform.Foundation.NSURLErrorServerCertificateHasBadDate
import platform.Foundation.NSURLErrorServerCertificateUntrusted
import platform.posix.ECONNABORTED
import platform.posix.ENETDOWN
import platform.posix.ETIMEDOUT

/** Le contrat « erreur réseau = IOException » avec le vrai moteur iOS (NSURLSession), dont dépend
 *  tout le mode hors ligne, et le classement de ses erreurs. */
class ErreursReseauIosTest {

    @Test
    fun uneConnexionRefuseeEstUneIOExceptionConnexionImpossible() = runTest {
        // Le port 9 (discard) n'écoute pas : refus immédiat, sans réseau ni DNS.
        val erreur = runCatching { HttpClient(moteurHttpPlateforme()).get("http://127.0.0.1:9/api/auth/login") }.exceptionOrNull()
        assertTrue(erreur is IOException, "obtenu : $erreur")
        assertEquals(ErreurReseau.CONNEXION_IMPOSSIBLE, classerErreurReseau(erreur))
        assertTrue(humaniserErreurReseau(erreur).startsWith("Connexion au serveur impossible"))
    }

    /** Une erreur de NSURLSession telle que la rend le moteur Darwin de Ktor. */
    private fun erreurUrl(code: Long) = DarwinHttpRequestException(NSError.errorWithDomain(NSURLErrorDomain, code, null))

    private fun erreurPosix(code: Int) = DarwinHttpRequestException(NSError.errorWithDomain(NSPOSIXErrorDomain, code.toLong(), null))

    @Test
    fun lesErreursPosixEtLesEnveloppesDeKtorSontClassees() {
        // Depuis iOS 12 : 53 au retour d'arrière-plan pendant un envoi.
        assertEquals(ErreurReseau.CONNEXION_IMPOSSIBLE, classerErreurReseau(erreurPosix(ECONNABORTED)))
        assertEquals(ErreurReseau.DELAI_DEPASSE, classerErreurReseau(erreurPosix(ETIMEDOUT)))
        assertEquals(ErreurReseau.HOTE_INCONNU, classerErreurReseau(erreurPosix(ENETDOWN)))
        // Erreur pendant la lecture du corps : Ktor l'enveloppe dans une ClosedByteChannelException.
        val enveloppee = ClosedByteChannelException(erreurUrl(NSURLErrorNetworkConnectionLost))
        assertEquals(ErreurReseau.CONNEXION_IMPOSSIBLE, classerErreurReseau(enveloppee))
        assertFalse(detailErreurReseau(enveloppee).orEmpty().contains("Error Domain="), detailErreurReseau(enveloppee))
        // Certificat client exigé : non classé, la description du système le dit.
        assertNull(classerErreurReseau(erreurUrl(NSURLErrorClientCertificateRequired)))
        // Sans le point final de la description du système entre parenthèses (celle que donne
        // NSURLSession ; un NSError sans userInfo a une description générique, terminée par « .) »).
        val horsLigne = DarwinHttpRequestException(
            NSError.errorWithDomain(
                NSURLErrorDomain,
                NSURLErrorNotConnectedToInternet,
                mapOf<Any?, Any?>(NSLocalizedDescriptionKey to "La connexion Internet semble être désactivée."),
            ),
        )
        val message = humaniserErreurReseau(horsLigne)
        assertTrue(message.contains("(La connexion Internet semble être désactivée). "), message)
    }

    @Test
    fun lesErreursTlsEtDeCertificatSontClassees() {
        // -1200 : négociation TLS coupée, vue le 26 novembre 2026 en 5G (liaison « minimale »).
        assertEquals(ErreurReseau.CONNEXION_SECURISEE, classerErreurReseau(erreurUrl(NSURLErrorSecureConnectionFailed)))
        assertEquals(ErreurReseau.CERTIFICAT, classerErreurReseau(erreurUrl(NSURLErrorServerCertificateUntrusted)))
        assertEquals(ErreurReseau.CERTIFICAT, classerErreurReseau(erreurUrl(NSURLErrorServerCertificateHasBadDate)))
        // Données cellulaires refusées à l'appli : pas de réseau.
        assertEquals(ErreurReseau.HOTE_INCONNU, classerErreurReseau(erreurUrl(NSURLErrorDataNotAllowed)))
        assertEquals(CONNEXION_SECURISEE_IMPOSSIBLE, humaniserErreurReseau(erreurUrl(NSURLErrorSecureConnectionFailed)))
        assertEquals(CERTIFICAT_REFUSE, humaniserErreurReseau(erreurUrl(NSURLErrorServerCertificateUntrusted)))
    }

    @Test
    fun uneErreurNonClasseeNeMontrePasLeNSErrorEntier() {
        val message = humaniserErreurReseau(erreurUrl(NSURLErrorBadServerResponse))
        assertTrue(message.startsWith("Erreur réseau : "), message)
        assertFalse(message.contains("Error Domain=") || message.contains("Exception in http request"), message)
    }

    @Test
    fun leTestDeConnexionExpliqueUneNegociationTlsCoupee() = runTest {
        val serveur = ServeurSimule()
        serveur.routerRequetes { throw erreurUrl(NSURLErrorSecureConnectionFailed) }
        val config = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
            urlServeur = serveur.base
            login = "alice"
            motDePasse = "pwd"
        }
        val (ok, message) = GeoNatureAuth(serveur.clients).testerConnexion(config)
        assertFalse(ok)
        assertEquals(CONNEXION_SECURISEE_IMPOSSIBLE, message)
    }
}
