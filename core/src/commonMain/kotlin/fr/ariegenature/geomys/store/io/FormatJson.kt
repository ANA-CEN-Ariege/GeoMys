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

package fr.ariegenature.geomys.store.io

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
