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

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/** Rectangle aux coins « continus » d'iOS, `RoundedRectangle(cornerRadius:style: .continuous)` de
 *  SwiftUI : la courbure croît progressivement au lieu de passer d'un coup du segment droit au
 *  quart de cercle. Tracé de `UIBezierPath(roundedRect:cornerRadius:)` (iOS 7 et suivants), tel que
 *  l'a relevé PaintCode : trois courbes de Bézier par coin, qui débordent de 1,528 rayon sur les
 *  côtés. Le rayon est réduit, comme sous iOS, quand le rectangle est trop petit pour le porter. */
class FormeContinue(private val rayon: Dp) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = min(with(density) { rayon.toPx() }, min(w, h) / 2f / A)
        val p = Path().apply {
            moveTo(A * r, 0f)
            // Haut, puis coin haut-droit.
            lineTo(w - A * r, 0f)
            cubicTo(w - B * r, 0f, w - C * r, 0f, w - D * r, E * r)
            cubicTo(w - F * r, G * r, w - G * r, F * r, w - E * r, D * r)
            cubicTo(w, C * r, w, B * r, w, A * r)
            // Droite, puis coin bas-droit.
            lineTo(w, h - A * r)
            cubicTo(w, h - B * r, w, h - C * r, w - E * r, h - D * r)
            cubicTo(w - G * r, h - F * r, w - F * r, h - G * r, w - D * r, h - E * r)
            cubicTo(w - C * r, h, w - B * r, h, w - A * r, h)
            // Bas, puis coin bas-gauche.
            lineTo(A * r, h)
            cubicTo(B * r, h, C * r, h, D * r, h - E * r)
            cubicTo(F * r, h - G * r, G * r, h - F * r, E * r, h - D * r)
            cubicTo(0f, h - C * r, 0f, h - B * r, 0f, h - A * r)
            // Gauche, puis coin haut-gauche.
            lineTo(0f, A * r)
            cubicTo(0f, B * r, 0f, C * r, E * r, D * r)
            cubicTo(G * r, F * r, F * r, G * r, D * r, E * r)
            cubicTo(C * r, 0f, B * r, 0f, A * r, 0f)
            close()
        }
        return Outline.Generic(p)
    }

    override fun equals(other: Any?): Boolean = other is FormeContinue && other.rayon == rayon

    override fun hashCode(): Int = rayon.hashCode()

    private companion object {
        // Coefficients du tracé d'UIBezierPath, en rayons.
        const val A = 1.52866483f
        const val B = 1.08849323f
        const val C = 0.86840689f
        const val D = 0.66993427f
        const val E = 0.06549600f
        const val F = 0.37282392f
        const val G = 0.16906013f
    }
}
