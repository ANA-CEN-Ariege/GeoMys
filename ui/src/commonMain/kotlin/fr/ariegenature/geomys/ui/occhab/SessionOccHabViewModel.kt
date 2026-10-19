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

// L'OccHabViewModel de GeoMys-Android @ cdd5196 était un `activityViewModels()`, partagé par la carte, l'écran
// d'une station et celui d'un habitat : celui-ci vit au même niveau, au-dessus du graphe de navigation.

package fr.ariegenature.geomys.ui.occhab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.occhab.SessionOccHab
import fr.ariegenature.geomys.occhab.altitudesMnt
import kotlinx.coroutines.launch

/** La session de saisie OccHab ([SessionOccHab]), partagée par les écrans OccHab. */
class SessionOccHabViewModel(private val services: ServicesGeoNature) : ViewModel() {
    val session = SessionOccHab()

    /** Remplit les altitudes min/max de la station courante via le MNT du serveur. Lancé dans la
     *  portée de la SESSION : la réponse arrive généralement APRÈS la navigation vers l'écran de la
     *  station — celle de la carte serait annulée. À l'arrivée, ne touche la session que si la
     *  station courante est TOUJOURS celle du lancement, et reporte dans le store si la station y
     *  est déjà (réédition persistée à « Valider », ou 1er habitat validé entre-temps) ; sinon les
     *  altitudes partiront avec la prochaine persistance de la station. */
    fun remplirAltitudes() {
        val station = session.station
        val saisieId = session.saisieId
        viewModelScope.launch {
            val (min, max) = services.altitudesMnt(station) ?: return@launch
            if (session.station.id == station.id) session.definirAltitudes(min, max)
            services.occHabStore.stationsDeSaisie(saisieId).firstOrNull { it.id == station.id }?.let {
                services.occHabStore.upsertStation(saisieId, it.copy(altitudeMin = min, altitudeMax = max))
            }
        }
    }
}
