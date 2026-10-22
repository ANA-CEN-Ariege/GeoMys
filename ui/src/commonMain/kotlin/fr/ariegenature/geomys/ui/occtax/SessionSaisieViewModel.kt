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

// Le TraceViewModel de GeoMys-Android @ cdd5196 était un `activityViewModels()`, partagé par tous les écrans
// de saisie OccTax : celui-ci vit au même niveau, au-dessus du graphe de navigation.

package fr.ariegenature.geomys.ui.occtax

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.occtax.SessionSaisieOcctax

/** La session de saisie OccTax ([SessionSaisieOcctax]), partagée par la carte, les relevés, les
 *  dénombrements, la caractérisation et la liste des observations. */
class SessionSaisieViewModel(private val services: ServicesGeoNature) : ViewModel() {
    val session = SessionSaisieOcctax(services.sortieStore, services.locationTracker, viewModelScope)

    /** Un relevé multi-taxons vient d'être enregistré avec au moins une espèce : au retour, la
     *  carte enchaîne sur le positionnement du relevé suivant (`demarrerSaisieSuivante`). */
    var demarrerSaisieSuivante = false

    /** On quitte le flux de saisie DANS l'appli (≠ arrière-plan, veille) : la sortie en cours est
     *  persistée avec sa trace (sinon la trace accumulée depuis la dernière observation serait
     *  perdue), puis le tracé GPS et son suivi s'arrêtent (MainActivity de GeoMys-Android). */
    fun quitterFluxSaisie() {
        session.sauvegarderBrouillon()
        services.locationTracker.arreterParcours()
        services.arreterSuiviGps()
    }

    override fun onCleared() {
        session.fermer()
    }
}
