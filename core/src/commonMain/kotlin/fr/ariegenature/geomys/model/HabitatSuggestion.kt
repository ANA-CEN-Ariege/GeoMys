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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/HabitatService.kt,
// classe HabitatSuggestion) : sortie du réseau vers model/, @Serializable (caches HABREF).

package fr.ariegenature.geomys.model

import kotlinx.serialization.Serializable

/** Une suggestion d'habitat issue du référentiel HABREF du serveur.
 *  [cdTypo] = typologie HABREF de l'habitat (CORINE, EUNIS, Cahiers d'habitats…), informatif.
 *  Null si inconnu (anciens caches). */
@Serializable
data class HabitatSuggestion(val cdHab: Int, val libelle: String, val cdTypo: Int? = null)
