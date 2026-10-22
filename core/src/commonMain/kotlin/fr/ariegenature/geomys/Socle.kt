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

package fr.ariegenature.geomys

import fr.ariegenature.geomys.location.SourcePositions
import fr.ariegenature.geomys.location.SuiviGps
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.plateforme.ExecutionLongue
import fr.ariegenature.geomys.store.CoffreMotDePasse
import fr.ariegenature.geomys.store.FabriqueReglages
import fr.ariegenature.geomys.store.io.RacinesStockage
import okio.FileSystem
import okio.SYSTEM

/** Ce que la plateforme fournit aux services de l'appli : dossiers, réglages, coffre, réseau,
 *  exécution des travaux longs, récepteur GPS et suivi en arrière-plan. Les tests en fournissent
 *  des doublures (dossier temporaire, MapSettings, MockEngine, positions simulées). */
class Socle(
    val racines: RacinesStockage,
    val reglages: FabriqueReglages,
    val coffre: CoffreMotDePasse,
    val clients: ClientsGeoNature,
    val executionLongue: ExecutionLongue,
    val positions: SourcePositions,
    val suiviGps: SuiviGps,
    val systeme: FileSystem = FileSystem.SYSTEM,
)
