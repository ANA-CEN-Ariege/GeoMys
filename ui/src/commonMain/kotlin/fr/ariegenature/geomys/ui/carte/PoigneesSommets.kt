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

package fr.ariegenature.geomys.ui.carte

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import fr.ariegenature.geomys.geo.LatLon
import org.maplibre.compose.map.MapState
import org.maplibre.spatialk.geojson.Position

/** Poignées déplaçables au doigt, posées au-dessus de la carte sur chaque sommet.
 *
 *  maplibre-compose n'a pas de marqueur déplaçable : chaque poignée est un élément Compose, placé
 *  par la projection de la carte et recalculé à chaque mouvement de caméra. Une poignée consomme
 *  ses gestes, donc la carte ne se déplace pas pendant qu'on la tire. [onDeplacement] reçoit la
 *  nouvelle position à chaque mouvement du doigt, ce qui permet de redessiner la forme en direct. */
@Composable
fun PoigneesSommets(
    etatCarte: MapState,
    sommets: List<LatLon>,
    onDeplacement: (index: Int, position: LatLon) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Lire la caméra abonne ce composable à ses changements : les poignées suivent la carte.
    etatCarte.cameraPosition
    Box(modifier.fillMaxSize()) {
        sommets.forEachIndexed { index, sommet ->
            val ecran = etatCarte.screenLocationFromPosition(sommet.enPosition()) ?: return@forEachIndexed
            Poignee(
                centre = ecran,
                onGlisser = { nouvelEcran ->
                    etatCarte.positionFromScreenLocation(nouvelEcran)?.let { position ->
                        onDeplacement(index, LatLon(latitude = position.latitude, longitude = position.longitude))
                    }
                },
            )
        }
    }
}

/** Zone tactile de 44 dp (taille recommandée, et rayon d'aimantage de GeoMys-Android) ;
 *  le rond visible est plus petit. */
private val TAILLE_POIGNEE = 44.dp

@Composable
private fun Poignee(centre: DpOffset, onGlisser: (DpOffset) -> Unit) {
    val centreCourant by rememberUpdatedState(centre)
    val onGlisserCourant by rememberUpdatedState(onGlisser)
    Box(
        Modifier
            .offset(x = centre.x - TAILLE_POIGNEE / 2, y = centre.y - TAILLE_POIGNEE / 2)
            .size(TAILLE_POIGNEE)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { changement, _ ->
                        changement.consume()
                        // Le sommet suit exactement le doigt : coin de la poignée + position du doigt
                        // dans la poignée. Cumuler les déplacements laisserait de côté le seuil de
                        // détection du glisser (≈ 8 dp de retard).
                        val doigt = DpOffset(
                            x = centreCourant.x - TAILLE_POIGNEE / 2 + changement.position.x.toDp(),
                            y = centreCourant.y - TAILLE_POIGNEE / 2 + changement.position.y.toDp(),
                        )
                        onGlisserCourant(doigt)
                    },
                )
            }
            .padding(12.dp)
            .background(Color.White, CircleShape)
            .padding(3.dp)
            .background(Color(0xFFD32F2F), CircleShape),
    )
}

/** MapLibre et spatial-k attendent l'ordre longitude, latitude. */
fun LatLon.enPosition(): Position = Position(longitude = longitude, latitude = latitude)
