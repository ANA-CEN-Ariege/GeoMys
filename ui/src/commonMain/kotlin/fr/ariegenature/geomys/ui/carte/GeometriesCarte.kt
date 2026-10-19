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

import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.monitoring.GeometrieSuivi
import fr.ariegenature.geomys.monitoring.ObjetCarte
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Les géométries des [objets] retenus par [garder] qui passent [filtre], en FeatureCollection pour une
 *  source GeoJSON ; chaque entité porte l'indice de son objet (`i`), pour le toucher. */
internal fun collectionGeometries(
    objets: List<ObjetCarte>,
    garder: (ObjetCarte) -> Boolean,
    filtre: (GeometrieSuivi) -> Boolean,
): String = buildJsonObject {
    put("type", "FeatureCollection")
    put("features", buildJsonArray {
        objets.forEachIndexed { i, o ->
            if (!garder(o)) return@forEachIndexed
            o.geometries.filter(filtre).forEach { g ->
                add(buildJsonObject {
                    put("type", "Feature")
                    put("properties", buildJsonObject { put("i", i) })
                    put("geometry", buildJsonObject {
                        when (g) {
                            is GeometrieSuivi.Point -> {
                                put("type", "Point")
                                put("coordinates", coordonnee(g.position))
                            }
                            is GeometrieSuivi.Ligne -> {
                                put("type", "LineString")
                                put("coordinates", JsonArray(g.sommets.map(::coordonnee)))
                            }
                            // Le contour puis les trous : MapLibre les rend en creux.
                            is GeometrieSuivi.Polygone -> {
                                put("type", "Polygon")
                                put("coordinates", JsonArray(g.anneaux.map { anneau -> JsonArray(anneau.map(::coordonnee)) }))
                            }
                        }
                    })
                })
            }
        }
    })
}.toString()

private fun coordonnee(p: LatLon) = JsonArray(listOf(JsonPrimitive(p.longitude), JsonPrimitive(p.latitude)))
