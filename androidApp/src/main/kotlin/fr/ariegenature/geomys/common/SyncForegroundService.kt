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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/sync/SyncForegroundService.kt) :
// le service exécute les travaux confiés par ExecutionLongueAndroid au lieu d'appeler SyncRunner
// lui-même ; notification, canal et icône inchangés, titre au nom de l'appli.

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
import fr.ariegenature.geomys.plateforme.ExecutionLongue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** [ExecutionLongue] Android : le travail part dans [SyncForegroundService], qui le mène dans son
 *  propre scope tant que le processus vit, quel que soit le sort de l'écran. */
class ExecutionLongueAndroid(context: Context) : ExecutionLongue {
    private val contexte = context.applicationContext

    override fun lancer(nom: String, avancement: Flow<String>, travail: suspend () -> Unit) {
        SyncForegroundService.travaux.add(TravailLong(avancement, travail))
        ContextCompat.startForegroundService(contexte, Intent(contexte, SyncForegroundService::class.java))
    }
}

internal class TravailLong(val avancement: Flow<String>, val bloc: suspend () -> Unit)

/** Service au premier plan qui exécute la synchro globale en arrière-plan.
 *  Tant qu'il tourne, le système garde le process vivant et autorise le réseau même écran
 *  éteint / app en arrière-plan — c'est ce qui permet au « Recharger les données » de NE PAS
 *  s'interrompre quand l'utilisateur quitte l'écran ou met le téléphone en veille. */
class SyncForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "GeoMys_sync"
        private const val NOTIF_ID = 2

        /** Travaux confiés par [ExecutionLongueAndroid], pris un par un à chaque démarrage. */
        internal val travaux = ConcurrentLinkedQueue<TravailLong>()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Travaux en cours dans cette instance du service. */
    private val enCours = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification("Démarrage de la synchronisation…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val travail = travaux.poll()
        if (travail == null) {
            if (enCours.get() == 0) stopSelf(startId)
            return START_NOT_STICKY
        }
        enCours.incrementAndGet()
        // Sert UNIQUEMENT à mettre à jour le texte de la notification au fil de la progression.
        // L'arrêt du service est piloté par la coroutine d'exécution, pas par ce suivi.
        val suivi = scope.launch { travail.avancement.collect { updateNotification(it) } }
        // La coroutine du service exécute le travail PUIS arrête le service. Le scope du service
        // survit aux changements d'écran ; il n'est annulé qu'à l'arrêt du service.
        scope.launch {
            try {
                travail.bloc()
            } finally {
                suivi.cancel()
                if (enCours.decrementAndGet() == 0) arreter(startId)
            }
        }
        // NOT_STICKY : une synchro tuée en cours ne se relance pas toute seule avec un intent nul.
        return START_NOT_STICKY
    }

    /** stopSelfResult : sans effet si un lancement plus récent a eu lieu entre-temps. */
    private fun arreter(startId: Int) {
        if (stopSelfResult(startId)) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(texte: String) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(texte))
    }

    private fun buildNotification(texte: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("${getString(R.string.app_name)} — synchronisation")
            .setContentText(texte)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
