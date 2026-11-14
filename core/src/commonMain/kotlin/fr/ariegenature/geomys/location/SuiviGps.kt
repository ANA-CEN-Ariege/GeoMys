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

/** Le suivi GPS des écrans de saisie, maintenu en arrière-plan PENDANT UNE TRACE seulement :
 *  - Android : un service au premier plan, avec sa notification « suivi GPS actif », le temps de la
 *    trace ; trace éteinte, le récepteur suit l'écran ([etatSuiviGps]) ;
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

/** Ce que le suivi tient allumé : le récepteur ([ecoute]) et le service au premier plan d'Android
 *  ([service]). */
data class EtatSuiviGps(val ecoute: Boolean, val service: Boolean)

/** La règle du suivi sous Android (décision du 14 novembre 2026, audit M60) : un écran de saisie le
 *  [demande] ; trace éteinte, le GPS ne tourne que l'appli à l'écran ([premierPlan]), et s'arrête
 *  écran verrouillé ou appli quittée ; pendant une [trace], le service le garde actif partout. Avant,
 *  le service tournait pendant toute la saisie, téléphone en poche compris. */
fun etatSuiviGps(demande: Boolean, premierPlan: Boolean, trace: Boolean): EtatSuiviGps =
    EtatSuiviGps(ecoute = demande && (premierPlan || trace), service = demande && trace)
