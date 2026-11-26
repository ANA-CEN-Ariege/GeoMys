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

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.ClosedByteChannelException

/** Les erreurs réseau que [humaniserErreurReseau] explique à l'utilisateur. [CONNEXION_SECURISEE] :
 *  la négociation TLS a échoué (souvent un réseau trop faible, qui la coupe) ; [CERTIFICAT] : le
 *  certificat du serveur est refusé (expiré, non reconnu, ou page de connexion d'un Wi-Fi public). */
enum class ErreurReseau { HOTE_INCONNU, DELAI_DEPASSE, CONNEXION_IMPOSSIBLE, CONNEXION_SECURISEE, CERTIFICAT }

/** L'erreur d'origine. Une erreur survenue pendant la lecture du corps de la réponse, après les
 *  en-têtes, arrive enveloppée par Ktor dans une [ClosedByteChannelException]
 *  (ClosedReadChannelException…), parfois deux fois (copie de la reprise des traces de coroutines). */
internal fun origineReseau(e: Throwable): Throwable {
    var x = e
    repeat(8) {
        val cause = x.cause
        if (x !is ClosedByteChannelException || cause == null) return x
        x = cause
    }
    return x
}

/** La nature d'une erreur réseau, ou null si [e] n'en est pas une connue. Les délais de Ktor sont
 *  reconnus ici ; le reste dépend du moteur (java.net sous Android, NSURLSession sous iOS). */
fun classerErreurReseau(e: Throwable): ErreurReseau? = when (val origine = origineReseau(e)) {
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> ErreurReseau.DELAI_DEPASSE
    else -> classerErreurReseauPlateforme(origine)
}

internal expect fun classerErreurReseauPlateforme(e: Throwable): ErreurReseau?

/** Le détail lisible d'une erreur, à montrer à l'utilisateur au lieu de `e.message` : le message de
 *  l'exception sous Android, la description du système sous iOS (jamais le NSError entier), sans
 *  point final, car il se met souvent entre parenthèses. */
fun detailErreurReseau(e: Throwable): String? =
    detailErreurReseauPlateforme(origineReseau(e))?.trim()?.trimEnd('.', ' ')?.takeIf { it.isNotEmpty() }

internal expect fun detailErreurReseauPlateforme(e: Throwable): String?
