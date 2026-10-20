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

// La boussole des cartes de saisie : CompassView et MapCompassController de GeoMys-Android @ cdd5196,
// BoussoleBouton de GeoMys-IOS (UI/Map/MapView.swift). Le cap, rapporté au nord magnétique comme sous
// GeoMys-Android sur les deux plateformes (rememberFournisseurCap), est lissé comme dans le prototype carte
// du lot 0 (LissageCap).

package fr.ariegenature.geomys.ui.carte

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.geo.LissageCap
import fr.ariegenature.geomys.geo.ecartAngulaire
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.location.HeadingRequest
import org.maplibre.compose.map.MapState
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees

/** Bouton boussole de 44 de côté ; l'aiguille rouge pointe vers le nord de la carte, tournée de
 *  [aiguille] degrés.
 *  - Android : CompassView (disque blanc ombré, anneau bleu quand la carte suit le cap, aiguille
 *    rouge marquée « N », talon gris, moyeu gris foncé) ;
 *  - iOS : BoussoleBouton de GeoMys-IOS (disque translucide cerclé, vert quand la carte suit le cap ;
 *    pointe rouge, talon blanc). */
@Composable
fun BoutonBoussole(aiguille: Double, actif: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clic = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = Role.Button,
        onClick = onClick,
    )
    if (estIos) {
        Box(
            modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.35f), CircleShape)
                .border(if (actif) 2.dp else 1.dp, if (actif) CouleursGeoMys.vert else Color.White.copy(alpha = 0.25f), CircleShape)
                .then(clic),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(9.dp, 24.dp).rotate(aiguille.toFloat()), contentAlignment = Alignment.Center) {
                Box(Modifier.size(9.dp, 12.dp).offset(y = (-6).dp).background(Color.Red, TRIANGLE_HAUT))
                Box(Modifier.size(9.dp, 12.dp).offset(y = 6.dp).rotate(180f).background(Color.White.copy(alpha = 0.85f), TRIANGLE_HAUT))
                Box(Modifier.size(3.dp).background(Color.White, CircleShape))
            }
        }
    } else {
        val mesure = rememberTextMeasurer()
        Canvas(modifier.size(44.dp).shadow(4.dp, CircleShape).then(clic)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = min(cx, cy) - 2f
            drawCircle(Color.White, r)
            if (actif) drawCircle(Color(0xFF1976D2), r - 2f, style = Stroke(width = 4f))
            rotate(aiguille.toFloat(), Offset(cx, cy)) {
                val largeur = r * 0.26f
                val hauteur = r * 0.70f
                val ecart = r * 0.14f
                drawPath(
                    Path().apply { moveTo(cx, cy - hauteur); lineTo(cx - largeur, cy - ecart); lineTo(cx + largeur, cy - ecart); close() },
                    Color(0xFFE53935),
                )
                drawPath(
                    Path().apply { moveTo(cx, cy + hauteur); lineTo(cx - largeur, cy + ecart); lineTo(cx + largeur, cy + ecart); close() },
                    Color(0xFFBDBDBD),
                )
                val taille = r * 0.36f
                val texte = mesure.measure("N", TextStyle(color = Color.White, fontSize = (taille / density).sp, fontWeight = FontWeight.Bold))
                val y = cy - hauteur * 0.42f + taille * 0.36f
                // Base du texte en y, centré horizontalement (Paint.Align.CENTER).
                drawText(texte, topLeft = Offset(cx - texte.size.width / 2f, y - texte.firstBaseline))
            }
            drawCircle(Color(0xFF424242), r * 0.10f)
        }
    }
}

/** Triangle pointe en haut. */
private val TRIANGLE_HAUT = GenericShape { taille, _ ->
    moveTo(taille.width / 2, 0f)
    lineTo(taille.width, taille.height)
    lineTo(0f, taille.height)
    close()
}

/** Le cap de l'appareil, en degrés depuis le nord, à chaque mesure du capteur ; nul avant la
 *  première (simulateur, émulateur sans magnétomètre). L'Explorer en oriente l'aiguille de sa
 *  boussole, sans faire tourner la carte. */
@Composable
fun rememberCapAppareil(): Double? {
    val fournisseurCap = rememberFournisseurCap()
    val cap by produceState<Double?>(null, fournisseurCap) {
        fournisseurCap.updates(HeadingRequest { minimumInterval = Duration.ZERO })
            // Capteur refusé par le système : l'aiguille reste au nord.
            .catch { }
            .collect { mesure -> value = (mesure.bearing - Bearing.North).inDegrees }
    }
    return cap
}

/** Commande de cap appliquée sans transition. */
private val CAP_IMMEDIAT = CameraAnimation.Ease { duration = Duration.ZERO }

/** Tant que [actif], la carte tourne avec le cap de l'appareil (lissé, à chaque image) ; sinon elle
 *  revient le nord en haut. Le cap seul est commandé : le suivi de position et le zoom, animés à
 *  part, continuent ; pendant un geste, la carte reste au doigt (une commande la lui reprendrait). */
@Composable
fun SuiviCap(etat: MapState, actif: Boolean) {
    val fournisseurCap = rememberFournisseurCap()
    LaunchedEffect(actif) {
        if (!actif) {
            if (etat.cameraPosition.bearing != 0.0) etat.setCameraPosition(etat.cameraPosition.copy(bearing = 0.0))
            return@LaunchedEffect
        }
        // Toutes les mesures du capteur : le réglage par défaut n'en garde qu'une par seconde, ce
        // qui faisait tourner la carte par à-coups.
        var cible: Double? = null
        launch {
            fournisseurCap.updates(HeadingRequest { minimumInterval = Duration.ZERO }).collect { mesure ->
                cible = (mesure.bearing - Bearing.North).inDegrees
            }
        }
        // À chaque image, le cap affiché glisse vers la dernière mesure : la rotation est continue,
        // et le bruit du capteur ne fait pas trembler la carte.
        val lissage = LissageCap()
        var imagePrecedente = 0L
        while (true) {
            val cap = withFrameNanos { maintenant ->
                val ecoule = if (imagePrecedente == 0L) Duration.ZERO else (maintenant - imagePrecedente).nanoseconds
                imagePrecedente = maintenant
                cible?.let { lissage.avancer(it, ecoule) }
            } ?: continue
            if (etat.isCameraMoving && etat.cameraMoveReason == CameraMoveReason.Gesture) continue
            if (abs(ecartAngulaire(etat.cameraPosition.bearing, cap)) > 0.05) {
                etat.animerCamera(CameraUpdate(bearing = cap), CAP_IMMEDIAT)
            }
        }
    }
}
