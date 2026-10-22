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

// Repris de GeoMys-IOS (GeoMys-IOS/Location/LocationTracker.swift) : réglages du CLLocationManager,
// autorisation demandée au premier démarrage, arrière-plan autorisé le temps d'une trace.

package fr.ariegenature.geomys.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLActivityTypeFitness
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject

/** [SourcePositions] iOS : CLLocationManager, précision maximale, une mesure tous les 5 m. À créer
 *  sur le fil principal. */
class SourcePositionsIos : SourcePositions {
    private val gestionnaire = CLLocationManager()

    // Le gestionnaire ne garde qu'une référence faible sur son délégué.
    private val delegue = DelegueLocalisation(this)
    private var recepteur: ((PositionGps) -> Unit)? = null
    internal var ecoute = false
        private set

    init {
        gestionnaire.delegate = delegue
        gestionnaire.desiredAccuracy = kCLLocationAccuracyBest
        // Comme le filtre de 5 m d'Android.
        gestionnaire.distanceFilter = 5.0
        gestionnaire.activityType = CLActivityTypeFitness
        // Les mesures doivent continuer en arrière-plan pendant une trace.
        gestionnaire.pausesLocationUpdatesAutomatically = false
    }

    override fun brancher(recepteur: (PositionGps) -> Unit) {
        this.recepteur = recepteur
    }

    internal fun recevoir(position: PositionGps) {
        recepteur?.invoke(position)
    }

    override fun demarrer(): Boolean {
        when (gestionnaire.authorizationStatus) {
            // L'autorisation arrive plus tard : le délégué relance alors les mesures.
            kCLAuthorizationStatusNotDetermined -> gestionnaire.requestWhenInUseAuthorization()
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> return false
        }
        gestionnaire.startUpdatingLocation()
        ecoute = true
        return true
    }

    override fun arreter() {
        gestionnaire.stopUpdatingLocation()
        ecoute = false
    }

    override fun disponible(): Boolean = !refusee(gestionnaire.authorizationStatus)

    override fun derniereConnue(): PositionGps? = gestionnaire.location?.enPosition()

    override fun demanderPosition() {
        gestionnaire.requestLocation()
    }

    override fun enregistrement(actif: Boolean) {
        gestionnaire.allowsBackgroundLocationUpdates = actif
        if (actif) gestionnaire.showsBackgroundLocationIndicator = true
    }

    internal companion object {
        fun refusee(statut: CLAuthorizationStatus) =
            statut == kCLAuthorizationStatusDenied || statut == kCLAuthorizationStatusRestricted

        fun autorisee(statut: CLAuthorizationStatus) =
            statut == kCLAuthorizationStatusAuthorizedWhenInUse || statut == kCLAuthorizationStatusAuthorizedAlways
    }
}

private class DelegueLocalisation(private val source: SourcePositionsIos) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        // Autorisation donnée après le démarrage : les mesures repartent.
        if (source.ecoute && SourcePositionsIos.autorisee(manager.authorizationStatus)) manager.startUpdatingLocation()
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val position = didUpdateLocations.lastOrNull() as? CLLocation ?: return
        source.recevoir(position.enPosition())
    }

    // Ignorée, comme sous Android ; requestLocation exige pourtant que le délégué la déclare.
    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {}
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.enPosition(): PositionGps =
    coordinate.useContents { PositionGps(latitude, longitude, horizontalAccuracy.toFloat()) }
