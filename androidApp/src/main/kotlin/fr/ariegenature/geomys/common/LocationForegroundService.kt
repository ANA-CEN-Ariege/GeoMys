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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/location/LocationForegroundService.kt) :
// les LiveData observées deviennent les StateFlow du suivi commun ; notification, canal et icône
// inchangés, titre au nom de l'appli.

package fr.ariegenature.geomys.common

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import fr.ariegenature.geomys.location.LocationTracker
import fr.ariegenature.geomys.location.SuiviGps
import fr.ariegenature.geomys.servicesGeoNature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** [SuiviGps] Android : le suivi passe par [LocationForegroundService], qui garde le GPS actif
 *  écran éteint ou appli en arrière-plan. */
class SuiviGpsAndroid(context: Context) : SuiviGps {
    private val contexte = context.applicationContext

    override fun demarrer(tracker: LocationTracker) {
        ContextCompat.startForegroundService(contexte, Intent(contexte, LocationForegroundService::class.java))
    }

    override fun arreter(tracker: LocationTracker) {
        contexte.stopService(Intent(contexte, LocationForegroundService::class.java))
    }
}

class LocationForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "GeoMys_location"
        private const val NOTIF_ID = 1
    }

    private val tracker by lazy {
        servicesGeoNature(applicationContext, ExecutionLongueAndroid(applicationContext), SuiviGpsAndroid(applicationContext))
            .locationTracker
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // Autorisation de localisation retirée entre-temps : Android refuse le service au premier
        // plan de type « location ». Il s'arrête alors sans planter l'appli.
        val demarre = runCatching {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(0.0, enCours = false),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        }.isSuccess
        if (!demarre) {
            stopSelf()
            return
        }
        scope.launch {
            combine(tracker.distanceTotale, tracker.estEnCours) { distance, enCours -> distance to enCours }
                .collect { (distance, enCours) -> updateNotification(distance, enCours) }
        }
        tracker.demarrer()
    }

    // START_NOT_STICKY : si Android tue le process pendant une saisie, il ne doit PAS
    // recréer le service SEUL — le système le relançait avec un GPS actif en continu et une
    // notification « en attente » alors qu'aucune saisie n'existait plus : drain batterie
    // silencieux sur le terrain. Les écrans de saisie redémarrent le service à chaque entrée ; les
    // données, elles, sont déjà protégées par l'auto-save au fil de l'eau.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        tracker.arreter()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(distance: Double, enCours: Boolean) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(distance, enCours))
    }

    private fun buildNotification(distance: Double, enCours: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val texte = if (enCours) "%.0f m parcourus".format(distance) else "En attente de démarrage"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("${getString(R.string.app_name)} – suivi GPS actif")
            .setContentText(texte)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentIntent(pi)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
