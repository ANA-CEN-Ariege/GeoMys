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

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Messages brefs au bas de l'écran, qui s'effacent seuls : `Toast.makeText` de GeoMys-Android,
 *  `Toast.shared.afficher` de GeoMys-IOS. [longue] : LENGTH_LONG (≈ 3,5 s) au lieu de LENGTH_SHORT
 *  (≈ 2 s). */
fun interface Toasts {
    fun afficher(texte: String, longue: Boolean)
}

/** Raccourci pour un message bref. */
fun Toasts.afficher(texte: String) = afficher(texte, longue = false)

val LocalToasts = staticCompositionLocalOf<Toasts> { error("Aucune zone de toasts : ZoneToasts doit envelopper l'appli") }

/** Fournit [LocalToasts] à [content] : vrai Toast d'Android, capsule de GeoMys-IOS dessinée par
 *  Compose sous iOS. À poser une fois, à la racine. */
@Composable
expect fun ZoneToasts(content: @Composable () -> Unit)
