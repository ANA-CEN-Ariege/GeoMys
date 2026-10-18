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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** La zone de toasts de GeoMys-IOS (`zoneToasts()`) : capsule noire à 85 %, texte blanc 14 pt
 *  centré, 64 pt au-dessus du bas de la zone sûre, fondu de 0,2 s ; un toast ne capte jamais les
 *  touchers. Un nouveau message remplace le précédent et relance son minuteur. */
@Composable
actual fun ZoneToasts(content: @Composable () -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }
    var dernier by remember { mutableStateOf("") }
    var longue by remember { mutableStateOf(false) }
    var numero by remember { mutableIntStateOf(0) }
    val toasts = remember {
        Toasts { texte, duree ->
            message = texte
            dernier = texte
            longue = duree
            numero++
        }
    }
    LaunchedEffect(numero) {
        if (message == null) return@LaunchedEffect
        delay(if (longue) 3_500 else 2_000)
        message = null
    }
    CompositionLocalProvider(LocalToasts provides toasts) {
        Box(Modifier.fillMaxSize()) {
            content()
            AnimatedVisibility(
                visible = message != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
            ) {
                Box(Modifier.padding(start = 32.dp, end = 32.dp, bottom = 64.dp)) {
                    Text(
                        text = message ?: dernier,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(percent = 50))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
