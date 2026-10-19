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

// Les cartes des écrans : MapView d'osmdroid sous GeoMys-Android (ui/MapUtils.kt), MKMapView sous
// GeoMys-IOS (UI/Map/MapView.swift). Une seule carte MapLibre, aux deux allures pour ce qu'elle
// dessine elle-même (marqueurs, bulles).

package fr.ariegenature.geomys.ui.carte

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.occtax.CadrageSortie
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_bird_marker
import fr.ariegenature.geomys.ui.ressources.ic_location_on
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.interaction.ClickEvent
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.CameraConstraints
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.overlay.MapOverlayScope
import org.maplibre.spatialk.geojson.BoundingBox

/** Une carte des écrans de GeoMys : déplacement et zoom au doigt, sans rotation ni inclinaison
 *  (la rotation viendra du cap), sans logo, échelle ni boussole (osmdroid n'en montrait pas).
 *  [onClic] reçoit les touchers que rien d'autre n'a pris (couches cliquables, contenu Compose
 *  posé par [contenu]) ; il rend vrai s'il s'en est servi. [onAvantClic] reçoit chaque toucher
 *  AVANT les couches cliquables, avec sa position à l'écran : rendre vrai le garde pour lui.
 *  [contenu] place des éléments Compose sur la carte (`Modifier.placedAt`). [bornesZoom] : les zooms
 *  permis, s'ils sont plus étroits que ceux de MapLibre. */
@Composable
fun CarteGeoMys(
    etat: MapState,
    modifier: Modifier = Modifier,
    onClic: ((LatLon) -> Boolean)? = null,
    onAvantClic: ((LatLon, DpOffset) -> Boolean)? = null,
    bornesZoom: ClosedFloatingPointRange<Double>? = null,
    contenu: @Composable MapOverlayScope.() -> Unit = {},
) {
    val interactions = remember(onClic, onAvantClic) {
        MapInteractions {
            camera {
                rotate { enabled = false }
                pitch { enabled = false }
            }
            if (onClic != null || onAvantClic != null) {
                callbacks {
                    click {
                        if (onAvantClic != null) {
                            onEvent { evenement: ClickEvent ->
                                val p = evenement.position
                                if (p != null && onAvantClic(LatLon(p.latitude, p.longitude), evenement.screenOffset)) ClickResult.Consume else ClickResult.Pass
                            }
                        }
                        if (onClic != null) {
                            onUnhandled { evenement: ClickEvent ->
                                val p = evenement.position
                                if (p != null && onClic(LatLon(p.latitude, p.longitude))) ClickResult.Consume else ClickResult.Pass
                            }
                        }
                    }
                }
            }
        }
    }
    val contraintes = remember(bornesZoom) {
        bornesZoom?.let { b ->
            CameraConstraints(CameraConstraints.Standard) {
                minZoom = b.start
                maxZoom = b.endInclusive
            }
        } ?: CameraConstraints.Standard
    }
    MaplibreMap(modifier = modifier, state = etat, cameraConstraints = contraintes, interactions = interactions, overlay = { contenu() })
}

/** Position de départ d'une carte cadrée par [cadrage] avant de connaître sa taille : le centre,
 *  au zoom de la France, d'un point, ou d'une emprise (que [cadrer] ajuste une fois la carte prête). */
fun cameraInitiale(cadrage: CadrageSortie): CameraPosition = when (cadrage) {
    CadrageSortie.France -> CameraPosition(center = LatLon(46.5, 2.5).enPosition(), zoom = 6.0)
    is CadrageSortie.Point -> CameraPosition(center = cadrage.centre.enPosition(), zoom = 15.0)
    is CadrageSortie.Emprise -> CameraPosition(
        center = LatLon((cadrage.sud + cadrage.nord) / 2, (cadrage.ouest + cadrage.est) / 2).enPosition(),
        zoom = 14.0,
    )
}

/** Cadre la carte sur une emprise (`zoomToBoundingBox`), sans marge ni animation. Sans effet pour un
 *  point ou la France, déjà posés par [cameraInitiale]. */
suspend fun MapState.cadrer(cadrage: CadrageSortie) {
    if (cadrage !is CadrageSortie.Emprise) return
    awaitViewport()
    fitCameraToBounds(
        BoundingBox(west = cadrage.ouest, south = cadrage.sud, east = cadrage.est, north = cadrage.nord),
        CameraFit(),
    )
}

/** Anime la caméra comme `animateTo` d'osmdroid. Un geste ou une commande plus récente interrompt
 *  l'animation sans interrompre l'appelant (maplibre-compose annule alors l'appel). */
suspend fun MapState.animerCamera(update: CameraUpdate, animation: CameraAnimation = CameraAnimation.Ease.Standard) {
    try {
        animateCamera(update, animation)
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
    }
}

/** Hauteur du marqueur d'un relevé : 32 dp sous Android (`ic_bird_marker`), 40 pt sous iOS. */
val hauteurMarqueur: Dp
    @Composable get() = if (estIos) 40.dp else 32.dp

/** Le marqueur d'un relevé ponctuel, posé sur sa position.
 *  - Android : l'épingle orange `ic_bird_marker` (32 dp), ancrée en bas au centre ;
 *  - iOS : la goutte rouge de GeoMys-IOS (`ic_location_on` évidée, 40 pt), pointe sur la position. */
@Composable
fun MapOverlayScope.MarqueurReleve(position: LatLon, onClick: () -> Unit) {
    val ios = estIos
    val ancre = if (ios) ANCRE_GOUTTE_IOS else BiasAlignment(0f, 1f)
    Image(
        painterResource(if (ios) Res.drawable.ic_location_on else Res.drawable.ic_bird_marker),
        contentDescription = "Relevé",
        colorFilter = if (ios) ColorFilter.tint(CouleursGeoMys.rougePastille) else null,
        modifier = Modifier
            .placedAt(position.enPosition(), ancre)
            .size(hauteurMarqueur)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    )
}

/** Bulle d'information d'un relevé, posée au-dessus de [position] ([decalage] : hauteur du marqueur).
 *  Toucher la bulle la ferme.
 *  - Android : `infowindow_obs` (fond noir à 65 %, coins de 20 dp, 180 à 300 dp de large, titre 14 sp
 *    gras et contenu 13 sp blancs) ;
 *  - iOS : bulle de MapKit (fond blanc, coins arrondis, pointe vers le relevé, titre demi-gras et
 *    détail 13 pt sur 240 pt de large). */
@Composable
fun MapOverlayScope.BulleCarte(position: LatLon, decalage: Dp, titre: String, contenu: String, onFermer: () -> Unit) {
    val fermeture = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFermer)
    if (estIos) {
        val forme = RoundedCornerShape(12.dp)
        Column(
            Modifier
                .placedAt(position.enPosition(), BiasAlignment(0f, 1f))
                .padding(bottom = decalage)
                .then(fermeture),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .shadow(8.dp, forme)
                    .background(Color.White, forme)
                    .widthIn(max = 264.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(titre, color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (contenu.isNotEmpty()) Text(contenu, color = Color.Black, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            // Pointe de la bulle.
            Box(Modifier.size(width = 20.dp, height = 10.dp).background(Color.White, POINTE))
        }
    } else {
        Column(
            Modifier
                .placedAt(position.enPosition(), BiasAlignment(0f, 1f))
                .padding(bottom = decalage)
                .then(fermeture)
                .background(Color(0xA6000000), RoundedCornerShape(20.dp))
                .widthIn(min = 180.dp, max = 300.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(titre, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (contenu.isNotEmpty()) Text(contenu, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Triangle pointant vers le bas, sous la bulle iOS. */
private val POINTE = GenericShape { taille, _ ->
    moveTo(0f, 0f)
    lineTo(taille.width, 0f)
    lineTo(taille.width / 2, taille.height)
    close()
}
