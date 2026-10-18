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

package fr.ariegenature.geomys.journal

import co.touchlab.kermit.Logger

/** Le journal de l'appli, avec la signature d'`android.util.Log` pour que les appels de
 *  GeoMys-Android se portent tels quels (`Log.w(TAG, "…", e)` devient `Journal.w(TAG, "…", e)`).
 *  Il écrit dans logcat sur Android et dans le journal unifié (OSLog) sur iOS, via Kermit. */
object Journal {
    fun d(tag: String, message: String, erreur: Throwable? = null) = Logger.d(message, erreur, tag)

    fun i(tag: String, message: String, erreur: Throwable? = null) = Logger.i(message, erreur, tag)

    fun w(tag: String, message: String, erreur: Throwable? = null) = Logger.w(message, erreur, tag)

    fun e(tag: String, message: String, erreur: Throwable? = null) = Logger.e(message, erreur, tag)
}
