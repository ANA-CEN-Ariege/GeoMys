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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/MapUtils.kt) : l'aire
// géodésique d'un polygone (airePolygoneM2), brute puis nette de ses trous.

package fr.ariegenature.geomys.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Aire GÉODÉSIQUE (m²) du polygone défini par [sommets] (anneau non fermé accepté), par la
 *  formule de l'excès sphérique (sphère de rayon terrestre moyen R = 6 371 008,8 m — même
 *  approximation que computeArea de Google Maps Utils). Précision largement suffisante à
 *  l'échelle d'une station naturaliste (écart < 0,1 % sur quelques hectares). Sert au calcul
 *  AUTOMATIQUE ET LOCAL de la surface d'une station OccHab (le web délègue au serveur
 *  /geo/area_size — impossible hors-ligne). Renvoie 0.0 si moins de 3 sommets. */
fun airePolygoneM2(sommets: List<LatLon>): Double {
    if (sommets.size < 3) return 0.0
    val r = 6371008.8
    var somme = 0.0
    var prec = sommets.last()
    for (p in sommets) {
        // Formule des trapèzes sphériques : Σ (λ2-λ1)·(2 + sin φ1 + sin φ2), aire = |Σ|·R²/2.
        somme += (radians(p.longitude) - radians(prec.longitude)) *
            (2.0 + sin(radians(prec.latitude)) + sin(radians(p.latitude)))
        prec = p
    }
    return abs(somme * r * r / 2.0)
}

/** Aire NETTE d'un polygone à trous : anneau extérieur MOINS ses anneaux intérieurs — même
 *  convention que PostGIS (`ST_Area` soustrait les trous), donc que la surface calculée côté
 *  serveur. Un trou dégénéré (< 3 sommets) compte pour 0. Jamais négative (trous incohérents). */
fun airePolygoneM2(exterieur: List<LatLon>, trous: List<List<LatLon>>): Double =
    (airePolygoneM2(exterieur) - trous.sumOf { airePolygoneM2(it) }).coerceAtLeast(0.0)

/** `Math.toRadians`. */
private fun radians(degres: Double): Double = degres / 180.0 * PI
