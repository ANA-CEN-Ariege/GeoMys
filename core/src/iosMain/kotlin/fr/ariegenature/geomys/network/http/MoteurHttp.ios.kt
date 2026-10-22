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

package fr.ariegenature.geomys.network.http

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSHTTPCookieAcceptPolicy

// NSURLSession ne rejoue d'elle-même que les requêtes idempotentes (GET…), jamais un POST : le même
// réglage convient aux lectures et aux écritures.
actual fun moteurHttpPlateforme(ecriture: Boolean): HttpClientEngine = Darwin.create {
    configureSession {
        // Les cookies de session sont gérés à la main (GeoNatureAuth) : sans cela, NSURLSession
        // les garde et les renvoie aussi, en double (GeoMys-IOS, HttpClient.swift:58-59).
        HTTPShouldSetCookies = false
        HTTPCookieAcceptPolicy = NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyNever
    }
}
