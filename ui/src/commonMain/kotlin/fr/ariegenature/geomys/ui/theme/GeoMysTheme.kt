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

package fr.ariegenature.geomys.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Couleurs de GeoMys, reprises de res/values/colors.xml de GeoMys-Android. */
object CouleursGeoMys {
    val primaire = Color(0xFF1B6CA8)
    val orange = Color(0xFFFF6D00)
    /** Couleur des « liens » (boutons texte) de GeoMys. */
    val jauneClair = Color(0xFFFFE082)
    val fondHaut = Color(0xFF14402E)
    val fondBas = Color(0xFF0D264C)
}

private val schemaSombre = darkColorScheme(
    primary = CouleursGeoMys.primaire,
    onPrimary = Color.White,
    secondary = CouleursGeoMys.orange,
    onSecondary = Color.White,
)

/** Thème sombre sur les deux plateformes, comme GeoMys (mode nuit forcé sur Android). */
@Composable
fun GeoMysTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schemaSombre, content = content)
}

/** Fond commun des écrans : dégradé diagonal du haut-gauche vers le bas-droite
 *  (`bg_accueil` de GeoMys-Android, angle 315°). */
@Composable
fun FondGeoMys(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(CouleursGeoMys.fondHaut, CouleursGeoMys.fondBas),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
        content = content,
    )
}
