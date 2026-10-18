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

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// Dans l'ordre des tests de GeoMys-Android (GNErreur.kt:76-81). OkHttp laisse passer ces
// exceptions telles quelles.
internal actual fun classerErreurReseauPlateforme(e: Throwable): ErreurReseau? = when (e) {
    is UnknownHostException -> ErreurReseau.HOTE_INCONNU
    is SocketTimeoutException -> ErreurReseau.DELAI_DEPASSE
    is ConnectException -> ErreurReseau.CONNEXION_IMPOSSIBLE
    else -> null
}

internal actual fun detailErreurReseau(e: Throwable): String? = e.message
