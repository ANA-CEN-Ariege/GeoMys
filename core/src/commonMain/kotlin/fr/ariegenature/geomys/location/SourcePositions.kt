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

/** Une position mesurée par le récepteur de l'appareil. */
data class PositionGps(
    val latitude: Double,
    val longitude: Double,
    /** Rayon de précision horizontale, en mètres ; 0 si le récepteur ne la donne pas. */
    val precision: Float,
)

/**
 * Le récepteur de positions de la plateforme, sous [LocationTracker] :
 * - Android : LocationManager, et non le fournisseur « fused » de Google, pour garder les
 *   positions des récepteurs RTK externes (SW Maps…), qui passent par les positions fictives ;
 * - iOS : CLLocationManager.
 *
 * Chaque mesure, continue comme ponctuelle, est passée au récepteur branché par [brancher], sur le
 * fil principal.
 */
interface SourcePositions {
    fun brancher(recepteur: (PositionGps) -> Unit)

    /** Démarre les mesures continues. Faux si rien ne peut en donner (localisation coupée,
     *  autorisation refusée) : l'appelant réessaiera. */
    fun demarrer(): Boolean

    fun arreter()

    /** Vrai si une mesure peut être demandée (localisation active, autorisation non refusée). */
    fun disponible(): Boolean

    /** La dernière position que le système garde en mémoire, ou null. */
    fun derniereConnue(): PositionGps?

    /** Demande une mesure fraîche, en plus du flux continu. */
    fun demanderPosition()

    /** Pendant l'enregistrement d'une trace, les mesures continuent l'appli en arrière-plan. Sous
     *  iOS, c'est l'autorisation des mises à jour en arrière-plan ; sous Android, le service au
     *  premier plan ([SuiviGps]) s'en charge et cet appel ne fait rien. */
    fun enregistrement(actif: Boolean)
}
