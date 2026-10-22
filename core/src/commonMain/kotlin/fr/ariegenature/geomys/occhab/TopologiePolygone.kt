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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/util/TopologiePolygone.kt) :
// `milieu` et `pointDansAnneau`, sur des LatLon. La topologie partagée (`memePoint`, `insererSurArete`,
// `deplacerSommetsConfondus`), retirée de GeoMys-Android le 18 octobre (cdd5196), revient de sa version
// précédente le 20 octobre, à la demande de l'utilisateur.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.geo.LatLon
import kotlin.math.abs

/** Outils géométriques de la carte OccHab : milieu d'une arête (poignée « + »), appartenance d'un
 *  point à un anneau (sommet de trou sorti du polygone), et topologie PARTAGÉE des polygones
 *  aimantés — deux stations qui partagent un sommet ou une arête restent raccordées quand l'une est
 *  éditée. */
object TopologiePolygone {

    /** Tolérance de [memePoint] : 1e-9°, environ 0,1 mm. */
    private const val EPSILON = 1e-9

    /** Deux positions confondues : un sommet partagé, l'aimantage COPIANT les coordonnées du sommet
     *  cible. */
    fun memePoint(a: LatLon, b: LatLon): Boolean =
        abs(a.latitude - b.latitude) < EPSILON && abs(a.longitude - b.longitude) < EPSILON

    /** [anneau] (sommets distincts, fermeture implicite) avec [insere] entre les deux extrémités de
     *  son arête {[a], [b]}, dans un sens ou dans l'autre ; null s'il ne la possède pas. */
    fun insererSurArete(anneau: List<LatLon>, a: LatLon, b: LatLon, insere: LatLon): List<LatLon>? {
        for (i in anneau.indices) {
            val pi = anneau[i]
            val pj = anneau[(i + 1) % anneau.size]
            if ((memePoint(a, pi) && memePoint(b, pj)) || (memePoint(b, pi) && memePoint(a, pj))) {
                return anneau.toMutableList().also { it.add(i + 1, insere) }
            }
        }
        return null
    }

    /** [anneau] dont tout sommet confondu avec [avant] (sommet partagé) est déplacé vers [apres] ;
     *  null si aucun ne l'est. */
    fun deplacerSommetsConfondus(anneau: List<LatLon>, avant: LatLon, apres: LatLon): List<LatLon>? {
        if (anneau.none { memePoint(it, avant) }) return null
        return anneau.map { if (memePoint(it, avant)) apres else it }
    }

    /** Milieu de l'arête [a]-[b] (position de la poignée « + »). */
    fun milieu(a: LatLon, b: LatLon): LatLon =
        LatLon((a.latitude + b.latitude) / 2, (a.longitude + b.longitude) / 2)

    /**
     * [p] est-il À L'INTÉRIEUR de l'anneau [anneau] (liste de sommets DISTINCTS, fermeture
     * implicite) ? Lancer de rayon standard (parité impaire des intersections), en degrés :
     * suffisant pour l'usage — avertir l'utilisateur qu'un sommet de TROU est sorti du contour
     * extérieur, ce que le serveur (PostGIS) refuserait. Un point exactement sur le bord peut
     * tomber d'un côté ou de l'autre : on ne s'en sert que pour un AVERTISSEMENT, jamais pour
     * bloquer une saisie. False si l'anneau a moins de 3 sommets.
     */
    fun pointDansAnneau(p: LatLon, anneau: List<LatLon>): Boolean {
        if (anneau.size < 3) return false
        var dedans = false
        var j = anneau.size - 1
        for (i in anneau.indices) {
            val xi = anneau[i].longitude
            val yi = anneau[i].latitude
            val xj = anneau[j].longitude
            val yj = anneau[j].latitude
            if ((yi > p.latitude) != (yj > p.latitude) &&
                p.longitude < (xj - xi) * (p.latitude - yi) / (yj - yi) + xi
            ) dedans = !dedans
            j = i
        }
        return dedans
    }
}
