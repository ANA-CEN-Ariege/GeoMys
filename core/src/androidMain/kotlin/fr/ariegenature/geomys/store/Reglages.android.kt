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

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import fr.ariegenature.geomys.store.io.RacinesStockage
import okio.Path.Companion.toOkioPath

/** Les réglages de l'appli : un fichier SharedPreferences par nom, comme GeoMys-Android. */
fun fabriqueReglages(context: Context): FabriqueReglages {
    val appli = context.applicationContext
    return FabriqueReglages { nom -> SharedPreferencesSettings(appli.getSharedPreferences(nom, Context.MODE_PRIVATE)) }
}

/** `filesDir` et `cacheDir`, comme GeoMys-Android. */
fun racinesStockage(context: Context): RacinesStockage =
    RacinesStockage(context.filesDir.toOkioPath(), context.cacheDir.toOkioPath())
