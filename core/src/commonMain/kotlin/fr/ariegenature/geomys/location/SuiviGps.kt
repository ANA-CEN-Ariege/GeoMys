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

package fr.ariegenature.geomys.location

/** Le suivi GPS des écrans de saisie, maintenu quand l'appli passe en arrière-plan :
 *  - Android : un service au premier plan, avec sa notification « suivi GPS actif » ;
 *  - iOS : le récepteur seul, que l'autorisation des mises à jour en arrière-plan garde actif
 *    ([SourcePositions.enregistrement]). */
interface SuiviGps {
    fun demarrer(tracker: LocationTracker)

    fun arreter(tracker: LocationTracker)
}

/** [SuiviGps] sans service : le récepteur démarre et s'arrête avec l'écran (iOS, tests). */
object SuiviGpsDirect : SuiviGps {
    override fun demarrer(tracker: LocationTracker) = tracker.demarrer()

    override fun arreter(tracker: LocationTracker) = tracker.arreter()
}
