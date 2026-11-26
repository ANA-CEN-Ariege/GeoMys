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

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSError
import platform.Foundation.NSPOSIXErrorDomain
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDataNotAllowed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorInternationalRoamingOff
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorSecureConnectionFailed
import platform.Foundation.NSURLErrorServerCertificateHasBadDate
import platform.Foundation.NSURLErrorServerCertificateHasUnknownRoot
import platform.Foundation.NSURLErrorServerCertificateNotYetValid
import platform.Foundation.NSURLErrorServerCertificateUntrusted
import platform.Foundation.NSURLErrorTimedOut
import platform.posix.ECONNABORTED
import platform.posix.ECONNREFUSED
import platform.posix.ECONNRESET
import platform.posix.EHOSTUNREACH
import platform.posix.ENETDOWN
import platform.posix.ENETUNREACH
import platform.posix.ENOTCONN
import platform.posix.ETIMEDOUT

/** L'erreur de NSURLSession que porte [e] ou l'une de ses causes. */
private fun erreurSysteme(e: Throwable): NSError? =
    generateSequence(e) { it.cause }.take(10).firstNotNullOfOrNull { (it as? DarwinHttpRequestException)?.origin }

// Les erreurs de NSURLSession qui correspondent aux exceptions java.net testées sous Android.
internal actual fun classerErreurReseauPlateforme(e: Throwable): ErreurReseau? {
    val erreur = erreurSysteme(e) ?: return null
    return when (erreur.domain) {
        NSURLErrorDomain -> when (erreur.code) {
            // Sans réseau : hors ligne, données cellulaires refusées à l'appli, itinérance coupée.
            NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed, NSURLErrorNotConnectedToInternet,
            NSURLErrorDataNotAllowed, NSURLErrorInternationalRoamingOff -> ErreurReseau.HOTE_INCONNU
            NSURLErrorTimedOut -> ErreurReseau.DELAI_DEPASSE
            NSURLErrorCannotConnectToHost, NSURLErrorNetworkConnectionLost -> ErreurReseau.CONNEXION_IMPOSSIBLE
            // -1200 : négociation TLS coupée (vue le 26 novembre 2026 en 5G, liaison « minimale »).
            // Les certificats client exigés ou refusés (-1205, -1206) restent non classés : la
            // description du système le dit mieux qu'un conseil de réseau.
            NSURLErrorSecureConnectionFailed -> ErreurReseau.CONNEXION_SECURISEE
            NSURLErrorServerCertificateHasBadDate, NSURLErrorServerCertificateUntrusted,
            NSURLErrorServerCertificateHasUnknownRoot, NSURLErrorServerCertificateNotYetValid -> ErreurReseau.CERTIFICAT
            else -> null
        }
        // Depuis iOS 12, NSURLSession rend aussi des erreurs POSIX : 53 au retour d'arrière-plan
        // pendant un envoi, par exemple.
        NSPOSIXErrorDomain -> when (erreur.code.toInt()) {
            ENETDOWN, ENETUNREACH -> ErreurReseau.HOTE_INCONNU
            ETIMEDOUT -> ErreurReseau.DELAI_DEPASSE
            ECONNABORTED, ECONNRESET, ENOTCONN, ECONNREFUSED, EHOSTUNREACH -> ErreurReseau.CONNEXION_IMPOSSIBLE
            else -> null
        }
        else -> null
    }
}

internal actual fun detailErreurReseauPlateforme(e: Throwable): String? =
    erreurSysteme(e)?.localizedDescription ?: e.message
