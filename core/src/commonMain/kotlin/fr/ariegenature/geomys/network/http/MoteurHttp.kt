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

package fr.ariegenature.geomys.network.http

import io.ktor.client.engine.HttpClientEngine

/** Le moteur HTTP de la plateforme : OkHttp sous Android, NSURLSession sans gestion automatique des
 *  cookies sous iOS. [ecriture] : pour [ClientsGeoNature.ecriture], un moteur qui ne rejoue jamais
 *  une requête, même sur une connexion fermée par le serveur. */
expect fun moteurHttpPlateforme(ecriture: Boolean = false): HttpClientEngine
