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

// Reprend la règle de normaliserStation et normaliserHabitat de GeoMys-Android @ cdd5196
// (app/src/main/java/fr/ariegenature/geomys/store/OccHabStore.kt) : sous Gson, une station relue
// sans `id` ou sans liste `habitats`, ou un habitat sans `id`, était écarté. kotlinx leur donnerait
// les valeurs par défaut ; ce filtre, appliqué AVANT le décodage, les écarte comme avant.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Une station relue d'un fichier, ou null si elle est structurellement invalide (sans `id`, sans
 *  liste `habitats`, ou illisible). Ses habitats sans `id` sont écartés ; les autres champs nuls
 *  ou absents prennent leur valeur par défaut, comme le faisait la normalisation d'Android. */
internal fun stationDepuisJson(element: JsonElement): OccHabStation? {
    val station = element as? JsonObject ?: return null
    if (station["id"].estNul()) return null
    val habitats = station["habitats"] as? JsonArray ?: return null
    val habitatsValides = JsonArray(habitats.filter { it is JsonObject && !it["id"].estNul() })
    return try {
        JSON_STOCKAGE.decodeFromJsonElement(OccHabStation.serializer(), JsonObject(station + ("habitats" to habitatsValides)))
    } catch (_: Exception) {
        null
    }
}

private fun JsonElement?.estNul(): Boolean = this == null || this is JsonNull
