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

package fr.ariegenature.geomys.plateforme

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid

/** [ExecutionLongue] iOS, comme Network/SyncRunner.swift de GeoMys-IOS : une tâche d'arrière-plan
 *  par travail, et la veille de l'écran suspendue tant qu'un travail dure. La synchro dure des
 *  minutes sur un référentiel complet : sans délai d'arrière-plan, verrouiller l'écran la coupait
 *  en plein milieu. Le gestionnaire d'expiration est celui de TacheArrierePlan (EnvoiSortie.swift) :
 *  sans lui, iOS termine l'appli quand le délai est dépassé. */
class ExecutionLongueIos : ExecutionLongue {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Travaux en cours ; lu et écrit sur le fil principal seulement. */
    private var enCours = 0

    /** Tâche d'arrière-plan d'un travail, invalide une fois terminée (fil principal). */
    private class Tache(var id: UIBackgroundTaskIdentifier)

    override fun lancer(nom: String, avancement: Flow<String>, travail: suspend () -> Unit) {
        scope.launch {
            val tache = withContext(Dispatchers.Main) { commencer(nom) }
            try {
                travail()
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { terminer(tache) }
            }
        }
    }

    override suspend fun <T> pendant(nom: String, travail: suspend () -> T): T {
        val tache = withContext(Dispatchers.Main) { commencer(nom) }
        try {
            return travail()
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { terminer(tache) }
        }
    }

    private fun commencer(nom: String): Tache {
        val app = UIApplication.sharedApplication
        enCours++
        app.idleTimerDisabled = true
        val tache = Tache(UIBackgroundTaskInvalid)
        tache.id = app.beginBackgroundTaskWithName(nom) { terminerTache(tache) }
        return tache
    }

    private fun terminer(tache: Tache) {
        terminerTache(tache)
        if (--enCours == 0) UIApplication.sharedApplication.idleTimerDisabled = false
    }

    private fun terminerTache(tache: Tache) {
        if (tache.id == UIBackgroundTaskInvalid) return
        UIApplication.sharedApplication.endBackgroundTask(tache.id)
        tache.id = UIBackgroundTaskInvalid
    }
}
