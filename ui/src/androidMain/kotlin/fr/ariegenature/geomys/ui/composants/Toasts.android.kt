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

package fr.ariegenature.geomys.ui.composants

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Le Toast d'Android lui-même, comme dans GeoMys-Android. */
@Composable
actual fun ZoneToasts(content: @Composable () -> Unit) {
    val contexte = LocalContext.current.applicationContext
    val toasts = remember(contexte) {
        Toasts { texte, longue -> Toast.makeText(contexte, texte, if (longue) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() }
    }
    CompositionLocalProvider(LocalToasts provides toasts, content = content)
}
