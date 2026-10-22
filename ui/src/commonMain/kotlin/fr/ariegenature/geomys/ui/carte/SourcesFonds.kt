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

// Sources de tuiles reprises de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/MapUtils.kt) :
// mêmes serveurs et attributions. Les zooms maximaux sont ceux des serveurs (GetCapabilities de la
// Géoplateforme pour l'IGN) : au-delà, MapLibre agrandit les tuiles du dernier niveau.

package fr.ariegenature.geomys.ui.carte

import fr.ariegenature.geomys.carte.FondCarte
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Les tuiles d'un fond en ligne : URL (`{z}/{x}/{y}`, ou `{z}/{y}/{x}` pour Esri), zooms du
 *  serveur, attribution. */
internal class SourceFond(val tuiles: List<String>, val zoomMax: Int, val attribution: String)

/** Identifiant du style MapLibre de ce fond, que l'appli sert elle-même ([ConfigurationCarte.urlStyle]). */
val FondCarte.id: String
    get() = when (this) {
        FondCarte.OSM -> "osm"
        FondCarte.OPENTOPO -> "opentopo"
        FondCarte.TOPO -> "plan-ign"
        FondCarte.SCAN25 -> "scan25"
        FondCarte.ORTHO -> "ortho-ign"
        FondCarte.ESRI -> "esri"
    }

internal val FondCarte.source: SourceFond
    get() = when (this) {
        FondCarte.OSM -> SourceFond(
            tuiles = listOf("https://$HOTE_OSM/{z}/{x}/{y}.png"),
            zoomMax = 19,
            attribution = "© OpenStreetMap contributors",
        )
        FondCarte.OPENTOPO -> SourceFond(
            tuiles = HOTES_OPENTOPO.map { "https://$it/{z}/{x}/{y}.png" },
            zoomMax = 17,
            attribution = "© OpenTopoMap (CC-BY-SA) · © OpenStreetMap contributors",
        )
        FondCarte.TOPO -> SourceFond(listOf(urlWmtsIgn("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2", "image/png", prive = false)), 19, ATTRIBUTION_IGN)
        FondCarte.SCAN25 -> SourceFond(listOf(urlWmtsIgn("GEOGRAPHICALGRIDSYSTEMS.MAPS", "image/jpeg", prive = true)), 18, ATTRIBUTION_IGN)
        FondCarte.ORTHO -> SourceFond(listOf(urlWmtsIgn("ORTHOIMAGERY.ORTHOPHOTOS", "image/jpeg", prive = false)), 19, ATTRIBUTION_IGN)
        FondCarte.ESRI -> SourceFond(
            tuiles = listOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"),
            zoomMax = 19,
            attribution = "Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community",
        )
    }

/** La mention des fonds de la Géoplateforme de l'IGN (GeoMys-Android n'en donnait aucune). */
private const val ATTRIBUTION_IGN = "© IGN"

/** Le serveur de tuiles d'OpenStreetMap. */
internal const val HOTE_OSM = "tile.openstreetmap.org"

/** Les serveurs de tuiles d'OpenTopoMap. */
internal val HOTES_OPENTOPO = listOf("a.tile.opentopomap.org", "b.tile.opentopomap.org", "c.tile.opentopomap.org")

/** GetTile WMTS de la Géoplateforme IGN, dans la grille Web Mercator (PM). Le SCAN 25 passe par le
 *  service privé et sa clé publique. */
private fun urlWmtsIgn(couche: String, format: String, prive: Boolean): String {
    val base = if (prive) {
        "https://data.geopf.fr/private/wmts?apikey=ign_scan_ws&SERVICE=WMTS"
    } else {
        "https://data.geopf.fr/wmts?SERVICE=WMTS"
    }
    return "$base&REQUEST=GetTile&VERSION=1.0.0" +
        "&LAYER=$couche&STYLE=normal&FORMAT=$format" +
        "&TILEMATRIXSET=PM&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"
}

/** Style MapLibre minimal : une seule source raster en tuiles de 256 px, affichée en fond. */
fun styleRaster(tuiles: List<String>, zoomMax: Int, attribution: String): String =
    buildJsonObject {
        put("version", 8)
        putJsonObject("sources") {
            putJsonObject("fond") {
                put("type", "raster")
                putJsonArray("tiles") { tuiles.forEach { add(it) } }
                put("tileSize", 256)
                put("maxzoom", zoomMax)
                put("attribution", attribution)
            }
        }
        putJsonArray("layers") {
            addJsonObject {
                put("id", "fond")
                put("type", "raster")
                put("source", "fond")
            }
        }
    }.toString()
