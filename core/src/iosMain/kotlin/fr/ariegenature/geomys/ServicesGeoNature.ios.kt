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

import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.moteurHttpPlateforme
import fr.ariegenature.geomys.store.CoffreKeychain
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.reglagesGeoNature

private val services: ServicesGeoNature by lazy {
    ServicesGeoNature(
        config = GeoNatureConfig(reglagesGeoNature(), CoffreKeychain()),
        clients = ClientsGeoNature(moteurHttpPlateforme(), moteurHttpPlateforme(ecriture = true)),
    )
}

/** Les services de l'appli, créés au premier appel puis partagés par tout le processus. */
fun servicesGeoNature(): ServicesGeoNature = services
