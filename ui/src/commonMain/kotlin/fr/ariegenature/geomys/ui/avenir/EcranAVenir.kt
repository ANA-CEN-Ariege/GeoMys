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

package fr.ariegenature.geomys.ui.avenir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.composants.BalayageRetourIosDesactive
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys

/** Écran pas encore porté : bandeau de navigation (la maison ramène à l'accueil) et le lot qui
 *  l'apportera. Sous iOS, c'est le `PlaceholderEcran` de GeoMys-IOS. */
@Composable
fun EcranAVenir(titre: String, lot: String, onAccueil: () -> Unit) {
    val description = "Cet écran arrive avec le $lot du portage."
    BalayageRetourIosDesactive()
    FondGeoMys {
        if (estIos) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BandeauNavigation(titre, onAccueil)
                Spacer(Modifier.weight(1f))
                SymboleSf("hammer.fill", 48f, listOf(CouleursGeoMys.jauneClair))
                Text(titre, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(description, color = CouleursGeoMys.blanc70, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                Spacer(Modifier.weight(1f))
            }
        } else {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
                BandeauNavigation(titre, onAccueil, Modifier.padding(start = 12.dp, top = 12.dp))
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(titre, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
