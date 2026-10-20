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

import androidx.compose.runtime.Composable
import org.maplibre.compose.location.HeadingProvider

/** Le fournisseur du cap de l'appareil, rapporté au nord MAGNÉTIQUE, comme la boussole de GeoMys-Android
 *  (décision de l'utilisateur du 20 octobre). Sous Android, celui de maplibre-compose l'est déjà : il lit
 *  le capteur de rotation, sans déclinaison. Sous iOS, le sien passe au nord géographique dès que la
 *  position est connue (`CLHeading.trueHeading`) : on y lit le cap magnétique. */
@Composable
internal expect fun rememberFournisseurCap(): HeadingProvider
