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

package fr.ariegenature.geomys.location

/** Le récepteur GPS des tests (le shadow du LocationManager sous Robolectric) : les mesures
 *  arrivent par [emettre]. */
class SourcePositionsSimulee : SourcePositions {
    private var recepteur: ((PositionGps) -> Unit)? = null

    /** Localisation active sur l'appareil. */
    var active = true

    /** La position que le système garde en mémoire. */
    var derniere: PositionGps? = null

    var ecoute = false
        private set
    var enArrierePlan = false
        private set
    var demandes = 0
        private set

    override fun brancher(recepteur: (PositionGps) -> Unit) {
        this.recepteur = recepteur
    }

    override fun demarrer(): Boolean {
        if (!active) return false
        ecoute = true
        return true
    }

    override fun arreter() {
        ecoute = false
    }

    override fun disponible(): Boolean = active

    override fun derniereConnue(): PositionGps? = derniere

    override fun demanderPosition() {
        demandes++
    }

    override fun enregistrement(actif: Boolean) {
        enArrierePlan = actif
    }

    /** Une mesure du récepteur. */
    fun emettre(position: PositionGps) {
        recepteur?.invoke(position)
    }
}
