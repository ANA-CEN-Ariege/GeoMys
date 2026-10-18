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

package fr.ariegenature.geomys.store

import com.russhwolf.settings.NSUserDefaultsSettings
import fr.ariegenature.geomys.store.io.RacinesStockage
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

/** Les réglages de l'appli : une suite NSUserDefaults par nom, l'équivalent des fichiers
 *  SharedPreferences de GeoMys-Android. */
fun fabriqueReglages(): FabriqueReglages = FabriqueReglages { nom -> NSUserDefaultsSettings(NSUserDefaults(suiteName = nom)) }

/** Application Support (données à garder, sauvegardées par défaut) et Caches (que le système peut
 *  vider) : les équivalents de `filesDir` et `cacheDir`. */
fun racinesStockage(): RacinesStockage {
    val fichiers = dossierSysteme(NSApplicationSupportDirectory)
    val cache = dossierSysteme(NSCachesDirectory)
    FileSystem.SYSTEM.createDirectories(fichiers)
    FileSystem.SYSTEM.createDirectories(cache)
    return RacinesStockage(fichiers, cache)
}

private fun dossierSysteme(type: NSSearchPathDirectory): Path {
    val url = NSFileManager.defaultManager.URLsForDirectory(type, NSUserDomainMask).first() as NSURL
    return requireNotNull(url.path) { "dossier système introuvable : $type" }.toPath()
}
