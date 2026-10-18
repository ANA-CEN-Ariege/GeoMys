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

package fr.ariegenature.geomys.geo

/** Position géographique WGS84, en degrés décimaux. Type commun de toute la logique, à la place du
 *  `GeoPoint` d'osmdroid de GeoMys-Android : immuable, et indépendant de la bibliothèque de carte
 *  (la conversion vers MapLibre se fait dans la couche carte de l'UI, dans l'ordre lon/lat). */
data class LatLon(
    val latitude: Double,
    val longitude: Double,
)
