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

package fr.ariegenature.geomys.ui.carte

import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Fonds raster en ligne, repris de GeoMys-Android (ui/MapUtils.kt). [zoomMax] est le vrai zoom
 *  maximal du serveur : au-delà, MapLibre agrandit les tuiles du dernier niveau. */
enum class FondCarte(
    val id: String,
    val libelle: String,
    val tuiles: List<String>,
    val zoomMax: Int,
    val attribution: String,
) {
    PLAN_IGN(
        id = "plan-ign",
        libelle = "Plan IGN",
        tuiles = listOf(urlWmtsIgn("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2", "image/png")),
        zoomMax = 18,
        attribution = "© IGN-F/Géoportail",
    ),
    ORTHO_IGN(
        id = "ortho-ign",
        libelle = "Photos aériennes IGN",
        tuiles = listOf(urlWmtsIgn("ORTHOIMAGERY.ORTHOPHOTOS", "image/jpeg")),
        zoomMax = 19,
        attribution = "© IGN-F/Géoportail",
    ),
    OSM(
        id = "osm",
        libelle = "OpenStreetMap",
        tuiles = listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
        zoomMax = 19,
        attribution = "© les contributeurs d'OpenStreetMap",
    ),
}

/** GetTile WMTS de la Géoplateforme IGN, dans la grille Web Mercator (PM). */
private fun urlWmtsIgn(couche: String, format: String): String =
    "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
        "&LAYER=$couche&STYLE=normal&FORMAT=$format" +
        "&TILEMATRIXSET=PM&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"

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
