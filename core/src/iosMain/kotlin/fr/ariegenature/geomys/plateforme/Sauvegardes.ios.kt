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

import fr.ariegenature.geomys.journal.Journal
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey

actual fun exclureDesSauvegardes(dossier: Path) = marquer(dossier, exclu = true)

actual fun rendreAuxSauvegardes(dossier: Path) = marquer(dossier, exclu = false)

/** L'attribut vaut pour le dossier ET tout son contenu. */
@OptIn(ExperimentalForeignApi::class)
private fun marquer(dossier: Path, exclu: Boolean) {
    val ok = NSURL.fileURLWithPath(dossier.toString(), isDirectory = true)
        .setResourceValue(exclu, forKey = NSURLIsExcludedFromBackupKey, error = null)
    if (!ok) Journal.w("Sauvegardes", "Attribut de sauvegarde impossible à poser (exclu = $exclu) : $dossier")
}
