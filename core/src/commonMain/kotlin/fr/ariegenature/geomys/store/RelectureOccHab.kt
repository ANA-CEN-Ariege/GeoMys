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
// dont l'`id` ou la liste `habitats` est explicitement null, ou un habitat dont l'`id` est null,
// était écarté. Absents, ces champs prenaient leur valeur par défaut : OccHabStation et
// OccHabHabitat ont un constructeur sans argument, que Gson utilise (cf. NettoyageJson). kotlinx
// donnerait aussi le défaut à un null explicite ; ce filtre, appliqué AVANT le décodage, l'écarte.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import fr.ariegenature.geomys.store.io.sansElementsNuls
import fr.ariegenature.geomys.store.io.transformerTableau
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Une station relue d'un fichier, ou null si elle est structurellement invalide (`id` ou
 *  `habitats` explicitement nul, ou illisible). Ses habitats d'`id` nul sont écartés ; les autres
 *  champs nuls ou absents prennent leur valeur par défaut, comme le faisait la normalisation
 *  d'Android. */
internal fun stationDepuisJson(element: JsonElement): OccHabStation? {
    val station = stationNettoyee(element) ?: return null
    return try {
        JSON_STOCKAGE.decodeFromJsonElement(OccHabStation.serializer(), station)
    } catch (_: Exception) {
        null
    }
}

/** La station brute nettoyée selon la même règle, sans la décoder (relecture d'OccHabStore, qui
 *  décode la saisie entière), ou null si elle est à écarter. Des `habitats` présents mais qui ne
 *  sont pas un tableau l'écartent aussi : sous Gson, ils faisaient échouer la lecture du fichier
 *  entier. Les observateurs nuls sont retirés. */
internal fun stationNettoyee(element: JsonElement): JsonObject? {
    val station = element as? JsonObject ?: return null
    if (station["id"] is JsonNull) return null
    val habitats = station["habitats"]
    if (habitats != null && habitats !is JsonArray) return null
    return station
        .transformerTableau("habitats") { h -> h.takeIf { it is JsonObject && it["id"] !is JsonNull } }
        .sansElementsNuls("observateursIds", "observateursNoms")
}
