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

package fr.ariegenature.geomys.store.io

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Le format JSON des fichiers de l'appli (saisies, files d'envoi, caches). Il reproduit ce que
 *  faisait Gson dans GeoMys-Android :
 *  - les valeurs `null` ne sont pas écrites, toutes les autres le sont, défauts compris ;
 *  - à la lecture, une clé inconnue est ignorée, et une valeur absente, `null` alors que la
 *    propriété ne l'admet pas, ou d'énumération inconnue, prend la valeur par défaut.
 *
 *  Une seule différence : un champ obligatoire (sans défaut) manquant fait échouer la lecture de
 *  l'élément, là où Gson le laissait à `null`. Les stores lisent donc élément par élément et
 *  mettent à l'écart ce qui ne se lit pas (lot 1, stockage). */
val JSON_STOCKAGE: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/** [liste] en JSON de stockage, pour les caches JSON des réglages (`Gson.toJson` sous Android). */
fun <T> listeVersJson(serialiseur: KSerializer<T>, liste: List<T>): String =
    JSON_STOCKAGE.encodeToString(ListSerializer(serialiseur), liste)

/** La liste d'un cache JSON des réglages ; vide si [json] est vide ou illisible. */
fun <T> listeDepuisJson(serialiseur: KSerializer<T>, json: String): List<T> {
    if (json.isEmpty()) return emptyList()
    return try {
        JSON_STOCKAGE.decodeFromString(ListSerializer(serialiseur), json)
    } catch (_: Exception) { emptyList() }
}

/** [valeurs] en objet JSON de chaînes (`Gson.toJson` d'une Map<String, String> sous Android). */
fun mapVersJson(valeurs: Map<String, String>): String =
    JSON_STOCKAGE.encodeToString(MapSerializer(String.serializer(), String.serializer()), valeurs)

/** Les valeurs d'un objet JSON de chaînes ; vide si [json] est vide ou illisible. */
fun mapDepuisJson(json: String): Map<String, String> {
    if (json.isEmpty()) return emptyMap()
    return try {
        JSON_STOCKAGE.decodeFromString(MapSerializer(String.serializer(), String.serializer()), json)
    } catch (_: Exception) { emptyMap() }
}
