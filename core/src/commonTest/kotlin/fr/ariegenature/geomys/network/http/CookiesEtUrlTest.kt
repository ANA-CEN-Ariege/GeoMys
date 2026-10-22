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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.io.IOException

class CookiesEtUrlTest {

    @Test
    fun unSetCookieParEnTete() {
        assertEquals("token=abc; session=42", cookiesDeSession(listOf("token=abc; Path=/; HttpOnly", "session=42; Secure")))
    }

    @Test
    fun setCookieFusionnesParNSURLSession() {
        // NSHTTPURLResponse joint plusieurs Set-Cookie par des virgules ; une date en contient aussi.
        val fusionnes = "token=abc; Expires=Wed, 21 Oct 2026 07:28:00 GMT; Path=/, session=42; HttpOnly"
        assertEquals("token=abc; session=42", cookiesDeSession(listOf(fusionnes)))
    }

    @Test
    fun aucunCookie() {
        assertEquals("", cookiesDeSession(emptyList()))
    }

    @Test
    fun seulesLesUrlAbsoluesHttpPassent() {
        assertEquals("geonature.test", urlAbsolue("https://geonature.test/api/auth/login").host)
        assertEquals("10.0.2.2", urlAbsolue("HTTP://10.0.2.2:8000/api").host)
        for (invalide in listOf("/api/auth/login", "", "geonature.test/api", "ftp://x/api")) {
            assertFailsWith<IOException>(invalide) { urlAbsolue(invalide) }
        }
    }
}
