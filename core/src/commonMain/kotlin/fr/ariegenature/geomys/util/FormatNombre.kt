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

package fr.ariegenature.geomys.util

import kotlin.math.abs
import kotlin.math.floor

/** [valeur] avec [decimales] chiffres après la virgule, comme `"%.Nf".format(valeur)` sous
 *  Android sur un appareil en français : arrondi au plus proche (moitié vers le haut), virgule
 *  décimale. Pour des valeurs d'affichage (coordonnées, distances), pas au-delà de 10¹³. */
fun formaterDecimal(valeur: Double, decimales: Int): String {
    require(decimales in 0..9) { "decimales hors de 0..9 : $decimales" }
    if (valeur.isNaN()) return "NaN"
    if (valeur.isInfinite()) return if (valeur > 0) "Infinity" else "-Infinity"
    var facteur = 1L
    repeat(decimales) { facteur *= 10 }
    val arrondi = floor(abs(valeur) * facteur + 0.5).toLong()
    val entiere = arrondi / facteur
    val fraction = (arrondi % facteur).toString().padStart(decimales, '0')
    val signe = if (valeur < 0) "-" else ""
    return if (decimales == 0) "$signe$entiere" else "$signe$entiere,$fraction"
}

/** [valeur] avec ses milliers séparés par une espace fine insécable, comme `"%,d".format(valeur)`
 *  sous Android en français (« 5 294 »). */
fun formaterMilliers(valeur: Long): String {
    val chiffres = abs(valeur).toString()
    val groupes = chiffres.reversed().chunked(3).joinToString("\u202F").reversed()
    return if (valeur < 0) "-$groupes" else groupes
}
