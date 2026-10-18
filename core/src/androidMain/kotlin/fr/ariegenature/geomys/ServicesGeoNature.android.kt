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

package fr.ariegenature.geomys

import android.content.Context
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.moteurHttpPlateforme
import fr.ariegenature.geomys.plateforme.ExecutionLongue
import fr.ariegenature.geomys.store.CoffreAndroid
import fr.ariegenature.geomys.store.fabriqueReglages
import fr.ariegenature.geomys.store.racinesStockage

@Volatile
private var services: ServicesGeoNature? = null

/** Les services de l'appli, créés au premier appel puis partagés par tout le processus.
 *  [executionLongue] vient du module de l'appli : son service au premier plan et sa notification
 *  ont besoin de ses ressources (icône, nom). */
fun servicesGeoNature(context: Context, executionLongue: ExecutionLongue): ServicesGeoNature =
    services ?: synchronized(ServicesGeoNature::class) {
        services ?: ServicesGeoNature(
            Socle(
                racines = racinesStockage(context),
                reglages = fabriqueReglages(context),
                coffre = CoffreAndroid(context),
                clients = ClientsGeoNature(moteurHttpPlateforme(), moteurHttpPlateforme(ecriture = true)),
                executionLongue = executionLongue,
            ),
        ).also { services = it }
    }
