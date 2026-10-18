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

package fr.ariegenature.geomys.ui.accueil

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.ariegenature.geomys.InfosApp
import fr.ariegenature.geomys.network.VERSION_GEONATURE_MINIMALE
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.accueil_geonature_min
import fr.ariegenature.geomys.ui.ressources.accueil_sous_titre
import fr.ariegenature.geomys.ui.ressources.accueil_version
import fr.ariegenature.geomys.ui.ressources.app_name
import fr.ariegenature.geomys.ui.ressources.logo_description
import fr.ariegenature.geomys.ui.ressources.logo_geomys
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Accueil provisoire du socle : il valide le rendu commun (logo, thème, ressources, code de
 *  `core`) sur les deux plateformes, en attendant l'accueil de GeoMys (tuiles de saisie, menu). */
@Composable
fun AccueilScreen(infos: InfosApp) {
    FondGeoMys {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.logo_geomys),
                contentDescription = stringResource(Res.string.logo_description),
                modifier = Modifier.size(160.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.accueil_sous_titre),
                style = MaterialTheme.typography.bodyLarge,
                color = CouleursGeoMys.jauneClair,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(Res.string.accueil_version, infos.version, infos.plateforme),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = stringResource(Res.string.accueil_geonature_min, VERSION_GEONATURE_MINIMALE),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
