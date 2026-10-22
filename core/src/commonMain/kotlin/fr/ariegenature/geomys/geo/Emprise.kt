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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/MapUtils.kt) : le calcul
// de la boîte de `zoomerSur`, que le fragment confiait ensuite à osmdroid (zoomToBoundingBox).

package fr.ariegenature.geomys.geo

/** Une emprise en degrés. */
data class Emprise(val sud: Double, val ouest: Double, val nord: Double, val est: Double)

/** La boîte de `zoomerSur` pour englober [points] : une boîte artificielle de ± [demiBoite]° autour
 *  d'un point seul ou de points confondus (emprise quasi nulle), sinon leur emprise agrandie
 *  [agrandissement] fois autour de son centre (`increaseByScale`). Null sans point. Les écrans
 *  diffèrent par leurs réglages : 0,004 et 1,8 pour OccHab, 0,002 et 1,3 pour la carte des Suivis. */
fun empriseZoomerSur(points: List<LatLon>, demiBoite: Double, agrandissement: Double): Emprise? {
    if (points.isEmpty()) return null
    fun boite(p: LatLon) = Emprise(p.latitude - demiBoite, p.longitude - demiBoite, p.latitude + demiBoite, p.longitude + demiBoite)
    if (points.size == 1) return boite(points[0])
    val sud = points.minOf { it.latitude }
    val nord = points.maxOf { it.latitude }
    val ouest = points.minOf { it.longitude }
    val est = points.maxOf { it.longitude }
    if (nord - sud < 0.0001 && est - ouest < 0.0001) return boite(points[0])
    val dLat = (nord - sud) * (agrandissement - 1) / 2
    val dLon = (est - ouest) * (agrandissement - 1) / 2
    return Emprise(sud - dLat, ouest - dLon, nord + dLat, est + dLon)
}
