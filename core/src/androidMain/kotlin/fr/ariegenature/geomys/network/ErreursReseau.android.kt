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

import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

// Dans l'ordre des tests de GeoMys-Android (GNErreur.kt:76-81), complétés des erreurs TLS.
internal actual fun classerErreurReseauPlateforme(e: Throwable): ErreurReseau? {
    // OkHttp 5 essaie plusieurs routes (IPv6 puis IPv4) et ne relève que l'erreur de la première :
    // celles des suivantes sont dans ses « suppressed ». Un certificat refusé (nom d'hôte absent du
    // certificat, certificat expiré, chaîne non reconnue, portail d'un Wi-Fi public) ou une
    // négociation TLS ratée, où qu'ils soient, l'emportent donc sur l'échec de la première route.
    val famille = famille(e)
    if (famille.any { it is SSLPeerUnverifiedException || it is CertificateException || it is CertPathValidatorException }) {
        return ErreurReseau.CERTIFICAT
    }
    if (famille.any { it is SSLException }) return ErreurReseau.CONNEXION_SECURISEE
    return when (e) {
        is UnknownHostException -> ErreurReseau.HOTE_INCONNU
        is SocketTimeoutException -> ErreurReseau.DELAI_DEPASSE
        // Refus (ConnectException), absence de route, et coupure : « Connection reset », « Broken
        // pipe »… Depuis Android 11, un RST pendant la négociation TLS arrive ainsi, et non en
        // SSLException. Comme NSURLErrorNetworkConnectionLost sous iOS.
        is SocketException -> ErreurReseau.CONNEXION_IMPOSSIBLE
        else -> null
    }
}

/** [e], ses causes et ses « suppressed », chacun une fois. */
private fun famille(e: Throwable): List<Throwable> {
    val vues = ArrayList<Throwable>()
    fun visiter(x: Throwable?, profondeur: Int) {
        if (x == null || profondeur > 8 || vues.any { it === x }) return
        vues += x
        visiter(x.cause, profondeur + 1)
        x.suppressed.forEach { visiter(it, profondeur + 1) }
    }
    visiter(e, 0)
    return vues
}

internal actual fun detailErreurReseauPlateforme(e: Throwable): String? = e.message
