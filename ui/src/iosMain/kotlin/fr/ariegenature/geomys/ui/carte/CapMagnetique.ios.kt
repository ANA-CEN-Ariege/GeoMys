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

// Sur le modèle d'IosHeadingProvider de maplibre-compose 0.20.0 (Apache 2.0), qui rend `trueHeading`
// quand iOS le connaît : celui-ci rend toujours `magneticHeading`.

package fr.ariegenature.geomys.ui.carte

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.sample
import org.maplibre.compose.location.HeadingMeasurement
import org.maplibre.compose.location.HeadingProvider
import org.maplibre.compose.location.HeadingReference
import org.maplibre.compose.location.HeadingRequest
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.degrees
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

@Composable
internal actual fun rememberFournisseurCap(): HeadingProvider = remember { FournisseurCapMagnetique() }

/** Le cap magnétique de l'iPhone (`CLHeading.magneticHeading`), à chaque mesure valide du compas. */
@OptIn(FlowPreview::class)
private class FournisseurCapMagnetique : HeadingProvider {
    override fun updates(request: HeadingRequest): Flow<HeadingMeasurement> = callbackFlow {
        if (!CLLocationManager.headingAvailable()) {
            close()
            return@callbackFlow
        }
        val gestionnaire = CLLocationManager()
        val delegue = DelegueCap(channel)
        gestionnaire.delegate = delegue
        gestionnaire.startUpdatingHeading()
        // CLLocationManager ne retient pas son délégué : cette fermeture le garde jusqu'à la fin.
        awaitClose {
            gestionnaire.stopUpdatingHeading()
            gestionnaire.delegate = null
            delegue.arreter()
        }
    }
        .flowOn(Dispatchers.Main)
        .let { mesures -> if (request.minimumInterval == Duration.ZERO) mesures else mesures.sample(request.minimumInterval) }
}

private class DelegueCap(private val canal: SendChannel<HeadingMeasurement>) : NSObject(), CLLocationManagerDelegateProtocol {
    private var actif = true

    fun arreter() {
        actif = false
    }

    override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
        // Une précision négative marque un cap invalide (compas à étalonner, interférence).
        if (!actif || didUpdateHeading.headingAccuracy < 0.0) return
        canal.trySend(
            HeadingMeasurement(
                bearing = Bearing.North + didUpdateHeading.magneticHeading.degrees,
                reference = HeadingReference.MagneticNorth,
                accuracy = didUpdateHeading.headingAccuracy.degrees,
                measuredAt = Instant.fromEpochMilliseconds((didUpdateHeading.timestamp.timeIntervalSince1970 * 1_000).toLong()),
            ),
        )
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        canal.close(IllegalStateException(didFailWithError.localizedDescription))
    }
}
