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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/GeoNatureBrowse.kt,
// ObsExplorer) : passé dans model/, sans autre changement.

package fr.ariegenature.geomys.model

/** Une observation de la Synthèse affichée par l'Explorer. */
data class ObsExplorer(
    val nomCite: String,
    val nomVern: String,
    val nomSci: String,
    val latitude: Double,
    val longitude: Double,
    val date: String,
    val nombre: Int,
    val taxon: Taxon = Taxon.OISEAU
)
