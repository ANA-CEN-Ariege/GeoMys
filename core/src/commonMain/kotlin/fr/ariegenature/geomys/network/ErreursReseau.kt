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

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException

/** Les erreurs réseau que [humaniserErreurReseau] explique à l'utilisateur. */
enum class ErreurReseau { HOTE_INCONNU, DELAI_DEPASSE, CONNEXION_IMPOSSIBLE }

/** La nature d'une erreur réseau, ou null si [e] n'en est pas une connue. Les délais de Ktor sont
 *  reconnus ici ; le reste dépend du moteur (java.net sous Android, NSURLErrorDomain sous iOS). */
fun classerErreurReseau(e: Throwable): ErreurReseau? = when (e) {
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> ErreurReseau.DELAI_DEPASSE
    else -> classerErreurReseauPlateforme(e)
}

internal expect fun classerErreurReseauPlateforme(e: Throwable): ErreurReseau?

/** Le détail lisible d'une erreur réseau : le message de l'exception sous Android, la description
 *  du système sous iOS. */
internal expect fun detailErreurReseau(e: Throwable): String?
