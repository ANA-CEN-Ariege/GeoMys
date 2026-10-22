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

// L'OccHabViewModel de GeoMys-Android @ cdd5196 était un `activityViewModels()`, partagé par la carte, l'écran
// d'une station et celui d'un habitat : celui-ci vit au même niveau, au-dessus du graphe de navigation.

package fr.ariegenature.geomys.ui.occhab

import androidx.lifecycle.ViewModel
import fr.ariegenature.geomys.occhab.SessionOccHab

/** La session de saisie OccHab ([SessionOccHab]), partagée par les écrans OccHab. Les altitudes du MNT
 *  ne sont plus demandées au serveur à chaque géométrie, mais à l'envoi (`EnvoiOccHab`) : les écrans
 *  ne demandent rien au serveur (décision de l'utilisateur du 20 octobre). */
class SessionOccHabViewModel : ViewModel() {
    val session = SessionOccHab()
}
