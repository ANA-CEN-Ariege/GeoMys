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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/LocationTrackerTest.kt) : le
// shadow Robolectric du LocationManager devient SourcePositionsSimulee, JUnit kotlin.test. Les trois
// derniers cas sont propres au portage.

package fr.ariegenature.geomys.location

import fr.ariegenature.geomys.model.PointTrace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tracé GPS ([LocationTracker]) : les positions précises (≤ 50 m) alimentent le parcours et la
 *  distance pendant un parcours démarré ; une position imprécise est ignorée ; arrêt, restauration
 *  et distance imposée. */
class LocationTrackerTest {

    private val source = SourcePositionsSimulee()
    private val tracker = LocationTracker(source)

    @Test
    fun parcours_accumule_les_positions_precises_et_la_distance() {
        tracker.demarrer()
        tracker.demarrerParcours()
        assertEquals(true, tracker.estEnCours.value)

        source.emettre(PositionGps(42.90, 1.40, 5f))
        source.emettre(PositionGps(42.90, 1.50, 120f)) // imprécise → ignorée
        source.emettre(PositionGps(42.91, 1.40, 5f))

        assertEquals(2, tracker.parcours.value.size, "2 points précis")
        val d = tracker.distanceTotale.value
        assertTrue(d > 1000.0 && d < 1300.0, "≈ 1,1 km entre 42.90 et 42.91 (d=$d)")
        assertEquals(42.91, tracker.position.value!!.latitude, 1e-9)

        // Parcours arrêté : la position est toujours suivie, le tracé n'accumule plus.
        tracker.arreterParcours()
        source.emettre(PositionGps(42.92, 1.40, 5f))
        assertEquals(2, tracker.parcours.value.size)
        assertEquals(42.92, tracker.position.value!!.latitude, 1e-9)
        tracker.arreter()
    }

    @Test
    fun restauration_et_distance_imposee() {
        tracker.restaurerParcours(listOf(PointTrace(42.9, 1.4), PointTrace(42.91, 1.41), PointTrace(42.92, 1.42)))
        tracker.definirDistance(50.0)
        assertEquals(3, tracker.parcours.value.size)
        assertEquals(50.0, tracker.distanceTotale.value, 1e-9)
        tracker.reinitialiser()
        assertEquals(0, tracker.parcours.value.size)
        assertEquals(0.0, tracker.distanceTotale.value, 1e-9)
        assertEquals(false, tracker.estEnCours.value)
    }

    /** Reprise après une pause : la trace et la distance continuent, sans compter le trajet fait
     *  pendant l'arrêt. */
    @Test
    fun reprise_sans_compter_le_trajet_de_la_pause() {
        tracker.demarrer()
        tracker.demarrerParcours()
        source.emettre(PositionGps(42.90, 1.40, 5f))
        source.emettre(PositionGps(42.91, 1.40, 5f))
        val avantPause = tracker.distanceTotale.value
        tracker.arreterParcours()
        assertFalse(source.enArrierePlan, "plus d'arrière-plan pendant la pause")
        tracker.reprendreParcours()
        assertTrue(source.enArrierePlan)
        source.emettre(PositionGps(43.50, 1.40, 5f))
        assertEquals(avantPause, tracker.distanceTotale.value, 1e-9, "le saut de la pause n'est pas compté")
        assertEquals(3, tracker.parcours.value.size)
        source.emettre(PositionGps(43.51, 1.40, 5f))
        assertTrue(tracker.distanceTotale.value > avantPause + 1000.0)
    }

    @Test
    fun demarrage_pose_la_position_connue_du_systeme() {
        source.derniere = PositionGps(42.95, 1.60, 30f)
        assertEquals(42.95, tracker.positionConnue()!!.latitude, 1e-9, "avant tout démarrage")
        tracker.demarrer()
        assertTrue(source.ecoute)
        assertEquals(42.95, tracker.position.value!!.latitude, 1e-9)
        tracker.rafraichirPosition()
        assertEquals(1, source.demandes)
        tracker.arreter()
        assertFalse(source.ecoute)
    }

    @Test
    fun localisation_coupee_rien_ne_demarre() {
        source.active = false
        source.derniere = PositionGps(42.95, 1.60, 30f)
        tracker.demarrer()
        assertFalse(source.ecoute)
        assertNull(tracker.position.value)
        tracker.rafraichirPosition()
        assertEquals(0, source.demandes)
        assertNull(tracker.position.value)
        // Localisation rallumée : le démarrage suivant réussit.
        source.active = true
        tracker.demarrer()
        assertTrue(source.ecoute)
    }
}
