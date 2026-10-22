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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabCarteFragment.kt,
// lancerRemplissageAltitudes) : l'appel au MNT du serveur pour la géométrie d'une station, fait à l'envoi
// depuis le 20 octobre. Le chargement des stations du serveur d'un jeu de données (StationsServeurOverlay.kt,
// charger) a disparu le même jour : la carte lit le cache de la synchro.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.network.GeoNatureUpload

/** Les altitudes min/max du MNT du serveur (`POST /geo/info`, le même appel que le web au dessin
 *  d'une géométrie) pour la géométrie de [station], demandées à l'envoi (`EnvoiOccHab`). BEST-EFFORT :
 *  null hors ligne ou en erreur (les champs restent saisissables à la main). */
suspend fun ServicesGeoNature.altitudesMnt(station: OccHabStation): Pair<Int, Int>? {
    val geometrie = GeoNatureUpload.construireGeometrie(
        station.geometryType, station.geometryCoordsJson, station.latitude, station.longitude, station.geometryTrousJson,
    )
    return occHab.altitudesPourGeometrie(config, geometrie)
}
