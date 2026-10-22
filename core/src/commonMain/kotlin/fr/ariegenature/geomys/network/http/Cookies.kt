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

/** Les cookies de session à renvoyer (`nom=valeur` joints par « ; »), tirés des en-têtes
 *  Set-Cookie d'une réponse (GeoNatureAuth.kt:242-244 sous Android). Sous iOS, NSURLSession
 *  fusionne plusieurs Set-Cookie en une seule valeur séparée par des virgules : on la redécoupe aux
 *  virgules qui ouvrent un nouveau cookie, sans couper les dates (`Expires=Wed, 21 Oct 2026…`). */
fun cookiesDeSession(setCookies: List<String>): String =
    setCookies.flatMap { it.split(DEBUT_DE_COOKIE) }
        .map { it.substringBefore(";").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("; ")

private val DEBUT_DE_COOKIE = Regex(",(?=\\s*[^;,=\\s]+=)")
