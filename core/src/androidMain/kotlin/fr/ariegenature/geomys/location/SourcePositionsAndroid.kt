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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/location/LocationTracker.kt) :
// les appels au LocationManager, mêmes fournisseurs et mêmes intervalles.

package fr.ariegenature.geomys.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

/** [SourcePositions] Android : le GPS, ou à défaut le réseau, une mesure toutes les 2 s au plus
 *  et tous les 5 m. */
class SourcePositionsAndroid(context: Context) : SourcePositions {
    private val gestionnaire = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var recepteur: ((PositionGps) -> Unit)? = null

    // Toutes les méthodes sont écrites : avant Android 11, seule onLocationChanged a une
    // implémentation par défaut.
    private inner class Ecouteur : LocationListener {
        override fun onLocationChanged(location: Location) {
            recepteur?.invoke(location.enPosition())
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }

    private val ecouteur = Ecouteur()

    // La mesure ponctuelle de « Centrer » a son propre écouteur : Android ne garde qu'une demande
    // par couple écouteur–fournisseur, et celle d'un seul point remplaçait le suivi continu, qui
    // s'éteignait après ce point (trace et position figées ; audit du 20 octobre, C1).
    private val ecouteurPonctuel = Ecouteur()

    override fun brancher(recepteur: (PositionGps) -> Unit) {
        this.recepteur = recepteur
    }

    private fun gpsActif() = gestionnaire.isProviderEnabled(LocationManager.GPS_PROVIDER)

    private fun reseauActif() = gestionnaire.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    @SuppressLint("MissingPermission")
    override fun demarrer(): Boolean {
        val gps = gpsActif()
        if (!gps && !reseauActif()) return false
        val fournisseur = if (gps) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        // Autorisation retirée entre-temps : rien ne démarre, sans planter.
        return runCatching {
            gestionnaire.requestLocationUpdates(fournisseur, 2000L, 5f, ecouteur, Looper.getMainLooper())
        }.isSuccess
    }

    override fun arreter() {
        gestionnaire.removeUpdates(ecouteur)
        gestionnaire.removeUpdates(ecouteurPonctuel)
    }

    override fun disponible(): Boolean = gpsActif() || reseauActif()

    @SuppressLint("MissingPermission")
    override fun derniereConnue(): PositionGps? {
        val gps = gpsActif()
        val reseau = reseauActif()
        fun derniere(fournisseur: String) = runCatching { gestionnaire.getLastKnownLocation(fournisseur) }.getOrNull()
        val position = when {
            gps -> derniere(LocationManager.GPS_PROVIDER) ?: if (reseau) derniere(LocationManager.NETWORK_PROVIDER) else null
            reseau -> derniere(LocationManager.NETWORK_PROVIDER)
            else -> null
        }
        return position?.enPosition()
    }

    @SuppressLint("MissingPermission")
    override fun demanderPosition() {
        val fournisseur = when {
            gpsActif() -> LocationManager.GPS_PROVIDER
            reseauActif() -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        @Suppress("DEPRECATION")
        runCatching { gestionnaire.requestSingleUpdate(fournisseur, ecouteurPonctuel, Looper.getMainLooper()) }
    }

    override fun enregistrement(actif: Boolean) {}
}

private fun Location.enPosition() = PositionGps(latitude, longitude, accuracy)
