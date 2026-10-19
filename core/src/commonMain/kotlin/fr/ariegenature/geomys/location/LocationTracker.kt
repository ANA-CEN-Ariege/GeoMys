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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/location/LocationTracker.kt) :
// les règles du suivi (filtre de précision, parcours, distance) restent ici ; LocationManager passe
// derrière SourcePositions, LiveData devient StateFlow. La distance est celle de Location.distanceTo
// (geo/Distance.kt).

package fr.ariegenature.geomys.location

import fr.ariegenature.geomys.geo.distanceMetres
import fr.ariegenature.geomys.model.PointTrace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Suit la position de l'appareil et, pendant un parcours, en accumule la trace et la distance.
 *  Une seule instance par processus (ServicesGeoNature.locationTracker) : la carte, la saisie et
 *  le service de suivi lisent la même. */
class LocationTracker(private val source: SourcePositions) {

    private val _position = MutableStateFlow<PositionGps?>(null)
    val position: StateFlow<PositionGps?> = _position.asStateFlow()

    private val _parcours = MutableStateFlow<List<PointTrace>>(emptyList())
    val parcours: StateFlow<List<PointTrace>> = _parcours.asStateFlow()

    private val _estEnCours = MutableStateFlow(false)
    val estEnCours: StateFlow<Boolean> = _estEnCours.asStateFlow()

    private val _distanceTotale = MutableStateFlow(0.0)
    val distanceTotale: StateFlow<Double> = _distanceTotale.asStateFlow()

    private var dernierePosition: PositionGps? = null
    private var ecoute = false

    init {
        source.brancher(::recevoir)
    }

    private fun recevoir(p: PositionGps) {
        // Toujours mettre à jour la position courante (sinon l'UI reste vide tant que la
        // précision ne descend pas sous 50 m).
        _position.value = p
        // Filtre des points imprécis uniquement pour l'enregistrement du parcours.
        if (p.precision > PRECISION_MAX_PARCOURS) return
        if (_estEnCours.value) {
            _parcours.update { it + PointTrace(p.latitude, p.longitude) }
            dernierePosition?.let { prec ->
                _distanceTotale.update { it + distanceMetres(p.latitude, p.longitude, prec.latitude, prec.longitude) }
            }
            dernierePosition = p
        }
    }

    /** Démarre la réception des positions et pose aussitôt la dernière position connue du
     *  système. Sans effet si la réception tourne déjà, ou si rien ne peut en donner. */
    fun demarrer() {
        if (ecoute) return
        if (!source.demarrer()) return
        ecoute = true
        // Position immédiate depuis le cache système.
        source.derniereConnue()?.let { _position.value = it }
    }

    /** Meilleure position connue IMMÉDIATEMENT : la dernière reçue, sinon le cache système. Pour
     *  que le bouton « centrer » ne reste jamais muet quand les mises à jour continues dorment
     *  (immobile, ou premier point pas encore arrivé). */
    fun positionConnue(): PositionGps? = _position.value ?: source.derniereConnue()

    /** Force une mesure fraîche (les mises à jour continues peuvent dormir) : pose d'abord la
     *  position connue (la carte se recentre tout de suite), puis demande un point, qui arrive par
     *  le même chemin que les autres. */
    fun rafraichirPosition() {
        if (!source.disponible()) return
        positionConnue()?.let { _position.value = it }
        source.demanderPosition()
    }

    fun arreter() {
        if (!ecoute) return
        source.arreter()
        ecoute = false
    }

    fun demarrerParcours() {
        _parcours.value = emptyList()
        _distanceTotale.value = 0.0
        dernierePosition = null
        _estEnCours.value = true
        source.enregistrement(true)
    }

    fun arreterParcours() {
        _estEnCours.value = false
        source.enregistrement(false)
    }

    /** REPREND une trace existante sans rien effacer : sortie rouverte depuis « Mes saisies »
     *  ([restaurerParcours]), retour sur l'écran de saisie, ou relance après une pause. C'est
     *  toute la différence avec [demarrerParcours], qui repart de zéro. Le trajet parcouru pendant
     *  l'arrêt n'est pas compté dans la distance. */
    fun reprendreParcours() {
        dernierePosition = null
        _estEnCours.value = true
        source.enregistrement(true)
    }

    fun reinitialiser() {
        _parcours.value = emptyList()
        _distanceTotale.value = 0.0
        dernierePosition = null
        _estEnCours.value = false
        source.enregistrement(false)
    }

    fun restaurerParcours(points: List<PointTrace>) {
        _parcours.value = points.toList()
    }

    /** Restaure la distance accumulée : à la reprise d'une sortie sauvegardée, le compteur
     *  affiché continue depuis sa valeur précédente. */
    fun definirDistance(distance: Double) {
        _distanceTotale.value = distance
    }

    companion object {
        /** Précision au-delà de laquelle un point n'entre pas dans le parcours, en mètres. */
        const val PRECISION_MAX_PARCOURS = 50f
    }
}
