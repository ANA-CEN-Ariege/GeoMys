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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabCarteFragment.kt,
// lancerRemplissageAltitudes, et StationsServeurOverlay.kt, charger) : l'appel au MNT du serveur pour la
// géométrie d'une station, et le chargement des stations du serveur pour un jeu de données.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.network.GeoNatureUpload
import kotlinx.atomicfu.atomic

/** Les altitudes min/max du MNT du serveur (`POST /geo/info`, le même appel que le web au dessin
 *  d'une géométrie) pour la géométrie de [station]. BEST-EFFORT : null hors ligne ou en erreur
 *  (les champs restent saisissables à la main). */
suspend fun ServicesGeoNature.altitudesMnt(station: OccHabStation): Pair<Int, Int>? {
    val geometrie = GeoNatureUpload.construireGeometrie(
        station.geometryType, station.geometryCoordsJson, station.latitude, station.longitude, station.geometryTrousJson,
    )
    return occHab.altitudesPourGeometrie(config, geometrie)
}

/** Les stations du serveur d'un jeu de données, et le nombre de celles qu'il détient mais que
 *  l'appli ne sait pas dessiner (MultiPoint, GeometryCollection… tracés sous QGIS), écartées. */
data class StationsDuServeur(val stations: List<OccHabStation>, val nbIgnorees: Int)

/** Charge les stations de l'utilisateur DÉJÀ sur le serveur pour le jeu de données [idJdd] (filtre
 *  serveur `id_dataset` + filtre client), et les écrit dans le cache hors ligne au fil de l'eau
 *  (les autres JDD, chargés par la synchro, sont conservés). Lève l'erreur réseau : l'appelant
 *  se replie sur le cache. */
suspend fun ServicesGeoNature.chargerStationsDuJdd(idJdd: Int): StationsDuServeur {
    val ignorees = atomic(0)
    val stations = occHab.chargerStations(config, idDataset = idJdd, ignorees = ignorees).filter { it.idDataset == idJdd }
    stationsServeur.remplacerJdd(idJdd, stations)
    return StationsDuServeur(stations, ignorees.value)
}
