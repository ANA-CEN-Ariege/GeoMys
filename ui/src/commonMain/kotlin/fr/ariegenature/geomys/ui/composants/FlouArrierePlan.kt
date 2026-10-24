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

// Propre au portage (demande du 24 octobre 2026) : le flou des matériaux de SwiftUI (`.regularMaterial`…) sur
// ce que Compose dessine. Sur une vue native (la carte), c'est le matériau d'iOS lui-même (MateriauUltraFin).

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp

/**
 * Le flou d'arrière-plan d'un élément translucide : [sourceFlou] enregistre ce qui est dessous,
 * [fondFlou] le redessine flouté sous l'élément. Compose ne floute pas ce qu'il a déjà dessiné : le
 * contenu du dessous est enregistré dans une couche, puis redessiné flouté, découpé à la forme de
 * l'élément. Ce contenu doit donc être dessiné avant l'élément, et ne pas le contenir.
 */
class EtatFlou internal constructor(internal val dessous: GraphicsLayer, internal val flou: GraphicsLayer) {
    internal var origineDessous = Offset.Zero
    internal var origineElement = Offset.Zero
}

@Composable
fun rememberEtatFlou(): EtatFlou {
    val dessous = rememberGraphicsLayer()
    val flou = rememberGraphicsLayer()
    return remember(dessous, flou) { EtatFlou(dessous, flou) }
}

/** Le contenu (fond compris, pour que le flou soit opaque) que les éléments [fondFlou] floutent. */
fun Modifier.sourceFlou(etat: EtatFlou): Modifier = this
    .onGloballyPositioned { etat.origineDessous = it.positionInRoot() }
    .drawWithContent {
        etat.dessous.record { this@drawWithContent.drawContent() }
        drawLayer(etat.dessous)
    }

/** Fond d'un élément translucide : ce que [etat] a enregistré sous lui, flouté de [rayon], puis
 *  [teinte] par-dessus, dans [forme]. */
fun Modifier.fondFlou(etat: EtatFlou, forme: Shape, teinte: Color, rayon: Dp): Modifier = this
    .onGloballyPositioned { etat.origineElement = it.positionInRoot() }
    .drawBehind {
        val decalage = etat.origineDessous - etat.origineElement
        val r = rayon.toPx()
        etat.flou.renderEffect = BlurEffect(r, r, TileMode.Clamp)
        etat.flou.record { translate(decalage.x, decalage.y) { drawLayer(etat.dessous) } }
        clipPath(Path().apply { addOutline(forme.createOutline(size, layoutDirection, this@drawBehind)) }) {
            drawLayer(etat.flou)
            drawRect(teinte)
        }
    }
