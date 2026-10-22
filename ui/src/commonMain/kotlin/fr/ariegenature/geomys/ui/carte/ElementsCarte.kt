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

// Éléments posés sur les cartes de saisie : le point de position et son cercle de précision
// (MyLocationNewOverlay et ic_gps_blue_dot de GeoMys-Android, point bleu de MapKit sous GeoMys-IOS) et
// les marqueurs déplaçables (Marker draggable d'osmdroid, annotations déplaçables de MapKit).

package fr.ariegenature.geomys.ui.carte

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_gps_blue_dot
import fr.ariegenature.geomys.ui.ressources.ic_location_on
import fr.ariegenature.geomys.ui.ressources.ic_pin_drop
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.overlay.MapOverlayScope
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource

/** Pointe de la goutte de GeoMys-IOS : y = 22 sur 24 dans l'icône. */
val ANCRE_GOUTTE_IOS = BiasAlignment(0f, 2f * 22f / 24f - 1f)

/** Bleu du point de position de MapKit. */
private val BLEU_POSITION_IOS = Color(0xFF007AFF)

/** Le cercle de précision de la position : un disque de [rayonMetres] autour de [centre], sous les
 *  marqueurs. Couche de carte, à poser dans le style.
 *  - Android : celui de `MyLocationNewOverlay` d'osmdroid (bleu 100, 100, 255 : fond à 50/255,
 *    contour d'un pixel à 150/255) ;
 *  - iOS : le halo de MapKit, bleu système translucide. */
@Composable
fun CerclePrecision(centre: LatLon, rayonMetres: Double) {
    if (rayonMetres <= 0.0) return
    val ios = estIos
    val pixel = with(LocalDensity.current) { 1f.toDp() }
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(cercleGeoJson(centre, rayonMetres)))
    FillLayer(
        id = "precision-surface",
        source = source,
        color = const(if (ios) BLEU_POSITION_IOS.copy(alpha = 0.15f) else Color(100, 100, 255, 50)),
    )
    if (!ios) LineLayer(id = "precision-contour", source = source, color = const(Color(100, 100, 255, 150)), width = const(pixel))
}

/** Un cercle de [rayon] mètres autour de [centre], en polygone de 64 côtés (approximation
 *  locale, juste à l'échelle d'une précision GPS). */
private fun cercleGeoJson(centre: LatLon, rayon: Double): String {
    val rayonTerre = 6_371_008.8
    val dLat = rayon / rayonTerre * 180.0 / PI
    val dLon = dLat / cos(centre.latitude * PI / 180.0)
    val anneau = buildJsonArray {
        for (i in 0..64) {
            val angle = 2.0 * PI * (i % 64) / 64.0
            add(buildJsonArray {
                add(JsonPrimitive(centre.longitude + dLon * sin(angle)))
                add(JsonPrimitive(centre.latitude + dLat * cos(angle)))
            })
        }
    }
    return buildJsonObject {
        put("type", "Feature")
        put("properties", buildJsonObject {})
        put("geometry", buildJsonObject {
            put("type", "Polygon")
            put("coordinates", buildJsonArray { add(anneau) })
        })
    }.toString()
}

/** Le point de position de l'appareil.
 *  - Android : `ic_gps_blue_dot` (disque bleu primaire, cœur blanc, 20 dp), centré sur la position ;
 *  - iOS : le point bleu de MapKit (disque bleu cerclé de blanc, ombré). */
@Composable
fun MapOverlayScope.PointPosition(position: LatLon) {
    if (estIos) {
        Box(
            Modifier
                .placedAt(position.enPosition(), Alignment.Center)
                .size(22.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
                .border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(16.dp).background(BLEU_POSITION_IOS, CircleShape))
        }
    } else {
        Image(
            painterResource(Res.drawable.ic_gps_blue_dot),
            contentDescription = "Ma position",
            modifier = Modifier.placedAt(position.enPosition(), Alignment.Center).size(20.dp),
        )
    }
}

/** Un marqueur qu'on déplace en le gardant appuyé puis en le faisant glisser, comme ceux d'osmdroid
 *  et de MapKit ; un toucher bref appelle [onClic]. [onDebut] annonce le glisser (`onMarkerDragStart`) ;
 *  pendant le glisser, le marqueur suit le doigt et [onDeplacement] reçoit chaque position (forme
 *  redessinée en direct) ; [onFin] reçoit la position finale. [ancre] : point de l'icône posé sur
 *  la position. */
@Composable
fun MapOverlayScope.MarqueurDeplacable(
    etat: MapState,
    position: LatLon,
    icone: DrawableResource,
    taille: Dp,
    ancre: Alignment,
    teinte: Color? = null,
    description: String? = null,
    onClic: (() -> Unit)? = null,
    onDebut: (() -> Unit)? = null,
    onDeplacement: ((LatLon) -> Unit)? = null,
    onFin: (LatLon) -> Unit,
) {
    var glisse by remember { mutableStateOf<LatLon?>(null) }
    val affichee = glisse ?: position
    val positionCourante by rememberUpdatedState(affichee)
    val rappelClic by rememberUpdatedState(onClic)
    val rappelDebut by rememberUpdatedState(onDebut)
    val rappelDeplacement by rememberUpdatedState(onDeplacement)
    val rappelFin by rememberUpdatedState(onFin)
    Image(
        painterResource(icone),
        contentDescription = description,
        colorFilter = teinte?.let { ColorFilter.tint(it) },
        modifier = Modifier
            .placedAt(affichee.enPosition(), ancre)
            .size(taille)
            .pointerInput(Unit) { detectTapGestures(onTap = { rappelClic?.invoke() }) }
            .pointerInput(etat) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { rappelDebut?.invoke() },
                    onDrag = { changement, ecart ->
                        changement.consume()
                        val depart = etat.screenLocationFromPosition(positionCourante.enPosition()) ?: return@detectDragGesturesAfterLongPress
                        val arrivee = DpOffset(depart.x + ecart.x.toDp(), depart.y + ecart.y.toDp())
                        val p = etat.positionFromScreenLocation(arrivee) ?: return@detectDragGesturesAfterLongPress
                        val nouvelle = LatLon(p.latitude, p.longitude)
                        glisse = nouvelle
                        rappelDeplacement?.invoke(nouvelle)
                    },
                    onDragEnd = {
                        glisse?.let { rappelFin(it) }
                        glisse = null
                    },
                    onDragCancel = { glisse = null },
                )
            },
    )
}

/** Sommet d'une géométrie en cours de saisie ou d'un relevé repris :
 *  - Android : la goutte `ic_pin_drop` (24 dp, rouge), ancrée en bas au centre ;
 *  - iOS : la goutte de GeoMys-IOS (`ic_location_on` évidée, 40 pt, [teinteIos]), pointe sur le sommet. */
@Composable
fun MapOverlayScope.SommetDeplacable(
    etat: MapState,
    position: LatLon,
    onDeplacement: (LatLon) -> Unit,
    onFin: (LatLon) -> Unit,
    onDebut: (() -> Unit)? = null,
    onClic: (() -> Unit)? = null,
    teinteIos: Color = CouleursGeoMys.rougePastille,
) {
    val ios = estIos
    MarqueurDeplacable(
        etat = etat,
        position = position,
        icone = if (ios) Res.drawable.ic_location_on else Res.drawable.ic_pin_drop,
        taille = if (ios) 40.dp else 24.dp,
        ancre = if (ios) ANCRE_GOUTTE_IOS else BiasAlignment(0f, 1f),
        teinte = if (ios) teinteIos else null,
        description = "Sommet",
        onClic = onClic,
        onDebut = onDebut,
        onDeplacement = onDeplacement,
        onFin = onFin,
    )
}

/** La goutte de GeoMys-IOS sur les cartes (`PinLocationMarker`, `PinDropMarker`) : celle de
 *  `ic_location_pin`, évidée en son centre et cerclée d'un trait blanc de [trait]. */
internal class GoutteIos(private val couleur: Color, private val trait: Dp = 1.5.dp) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val sx = size.width / 24f
        val sy = size.height / 24f
        fun goutte() = Path().apply {
            moveTo(12f * sx, 2f * sy)
            cubicTo(8.13f * sx, 2f * sy, 5f * sx, 5.13f * sy, 5f * sx, 9f * sy)
            cubicTo(5f * sx, 14.25f * sy, 12f * sx, 22f * sy, 12f * sx, 22f * sy)
            cubicTo(12f * sx, 22f * sy, 19f * sx, 14.25f * sy, 19f * sx, 9f * sy)
            cubicTo(19f * sx, 5.13f * sy, 15.87f * sx, 2f * sy, 12f * sx, 2f * sy)
            close()
        }
        val evidee = goutte().apply {
            addOval(Rect(9.5f * sx, 6.5f * sy, 14.5f * sx, 11.5f * sy))
            fillType = PathFillType.EvenOdd
        }
        drawPath(evidee, couleur)
        drawPath(goutte(), Color.White, style = Stroke(trait.toPx()))
    }
}
