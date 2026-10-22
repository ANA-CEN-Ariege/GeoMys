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

package fr.ariegenature.geomys.plateforme

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCaseInsensitiveSearch
import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSLocale
import platform.Foundation.NSMakeRange
import platform.Foundation.NSString
import platform.Foundation.compare
import platform.Foundation.decomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun decomposerNfd(texte: String): String = (texte as NSString).decomposedStringWithCanonicalMapping

@OptIn(ExperimentalForeignApi::class)
@Suppress("CAST_NEVER_SUCCEEDS")
actual fun comparateurFrancais(): Comparator<String> {
    val locale = NSLocale(localeIdentifier = "fr_FR")
    return Comparator { a, b ->
        val chaine = a as NSString
        chaine.compare(
            b,
            options = NSCaseInsensitiveSearch or NSDiacriticInsensitiveSearch,
            range = NSMakeRange(0u, chaine.length),
            locale = locale,
        ).toInt()
    }
}
