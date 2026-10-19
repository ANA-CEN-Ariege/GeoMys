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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/util/TopologiePolygone.kt) :
// `milieu` et `pointDansAnneau`, sur des LatLon. `memePoint` n'est pas repris : la topologie partagée
// qu'il servait a été retirée de GeoMys-Android (cdd5196), et il n'y avait plus d'appelant.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.geo.LatLon

/** Outils géométriques de la carte OccHab : milieu d'une arête (poignée « + ») et appartenance
 *  d'un point à un anneau (sommet de trou sorti du polygone). */
object TopologiePolygone {

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
