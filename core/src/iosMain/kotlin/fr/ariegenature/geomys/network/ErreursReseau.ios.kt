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

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut

// Les erreurs de NSURLSession qui correspondent aux exceptions java.net testées sous Android.
internal actual fun classerErreurReseauPlateforme(e: Throwable): ErreurReseau? {
    val erreur = (e as? DarwinHttpRequestException)?.origin ?: return null
    if (erreur.domain != NSURLErrorDomain) return null
    return when (erreur.code) {
        NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed, NSURLErrorNotConnectedToInternet -> ErreurReseau.HOTE_INCONNU
        NSURLErrorTimedOut -> ErreurReseau.DELAI_DEPASSE
        NSURLErrorCannotConnectToHost, NSURLErrorNetworkConnectionLost -> ErreurReseau.CONNEXION_IMPOSSIBLE
        else -> null
    }
}

internal actual fun detailErreurReseau(e: Throwable): String? =
    (e as? DarwinHttpRequestException)?.origin?.localizedDescription ?: e.message
