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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/CacheManagerFragment.kt) :
// `surfaceKm2`, `nombreDeTuiles` et `lonLatToTile`, que GeoMys-IOS a aussi sortis dans `TuileMath`.

package fr.ariegenature.geomys.geo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

/** Les tuiles Web Mercator de 256 px (niveaux de zoom d'osmdroid) et l'estimation d'une zone. */
object TuileMath {

    /** La tuile (x, y) qui contient le point au [zoom], bornée à la grille de 2^zoom tuiles de côté. */
    fun tuile(longitude: Double, latitude: Double, zoom: Int): Pair<Int, Int> {
        val n = 1 shl zoom
        val lat = latitude * PI / 180.0
        val x = floor((longitude + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val y = floor((1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return x to y
    }

    /** Le nombre de tuiles de l'[emprise], du zoom [zoomMin] au zoom [zoomMax] compris. */
    fun nombreDeTuiles(emprise: Emprise, zoomMin: Int, zoomMax: Int): Int {
        var total = 0
        for (z in zoomMin..zoomMax) {
            val (xMin, yMax) = tuile(emprise.ouest, emprise.sud, z)
            val (xMax, yMin) = tuile(emprise.est, emprise.nord, z)
            total += max(0, xMax - xMin + 1) * max(0, yMax - yMin + 1)
        }
        return total
    }

    /** La surface de l'[emprise] en km², par l'approximation équirectangulaire : à moins de 1 % près
     *  sous 500 km² aux latitudes tempérées, assez pour la comparer à un seuil. */
    fun surfaceKm2(emprise: Emprise): Double {
        val latMoyenne = (emprise.nord + emprise.sud) / 2.0 * PI / 180.0
        val largeur = (emprise.est - emprise.ouest) * 111.32 * cos(latMoyenne)
        val hauteur = (emprise.nord - emprise.sud) * 110.57
        return max(0.0, largeur * hauteur)
    }
}
