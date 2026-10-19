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

// Porté de GeoMys-Android @ d72b8da (app/src/test/java/fr/ariegenature/geomys/DefautsPortageIosTest.kt, volet B) :
// les trois cas du tracker, Robolectric et son looper en moins ; la garde
// le_bouton_de_trace_ne_redemarre_de_zero_que_sur_une_trace_vide lisait le texte de TraceFragment : la règle,
// sortie de l'écran, se vérifie ici en actionnant le bouton.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.location.LocationTracker
import fr.ariegenature.geomys.location.PositionGps
import fr.ariegenature.geomys.location.SourcePositionsSimulee
import fr.ariegenature.geomys.model.PointTrace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ▶ / ⏸ de la carte multi-taxons ([CarteSaisie.basculerParcours]) : ▶ continue la trace, il ne la
 *  recommence pas. */
class BasculerParcoursTest {

    private val source = SourcePositionsSimulee()
    private val tracker = LocationTracker(source)

    private val deuxPoints = listOf(PointTrace(42.90, 1.40), PointTrace(42.91, 1.41))

    @Test
    fun reprendre_un_parcours_conserve_les_points_deja_enregistres() {
        tracker.restaurerParcours(deuxPoints)
        tracker.reprendreParcours()
        assertEquals(deuxPoints, tracker.parcours.value, "les points restaurés doivent survivre")
        assertTrue(tracker.estEnCours.value, "l'enregistrement doit être réarmé")
    }

    /** CONTRE-ÉPREUVE : démarrer une NOUVELLE trace doit bien repartir de zéro — sinon le
     *  correctif aurait simplement neutralisé la remise à zéro. */
    @Test
    fun demarrer_un_parcours_repart_bien_de_zero() {
        tracker.restaurerParcours(deuxPoints)
        tracker.demarrerParcours()
        assertTrue(tracker.parcours.value.isEmpty(), "une nouvelle trace part vide")
        assertEquals(0.0, tracker.distanceTotale.value, 0.0001)
    }

    @Test
    fun une_pause_puis_une_reprise_ne_perd_rien() {
        tracker.restaurerParcours(deuxPoints)
        tracker.reprendreParcours()
        tracker.arreterParcours()
        assertFalse(tracker.estEnCours.value, "en pause")
        tracker.reprendreParcours()
        assertEquals(deuxPoints, tracker.parcours.value, "la pause ne doit rien effacer")
    }

    @Test
    fun le_bouton_continue_une_trace_non_vide_au_lieu_de_l_effacer() {
        tracker.demarrer()
        CarteSaisie.basculerParcours(tracker)
        assertTrue(tracker.estEnCours.value, "▶ sur une trace vide : le parcours démarre")
        source.emettre(PositionGps(42.90, 1.40, 5f))
        source.emettre(PositionGps(42.91, 1.40, 5f))

        CarteSaisie.basculerParcours(tracker)
        assertFalse(tracker.estEnCours.value, "⏸ : le parcours s'arrête")
        assertEquals(2, tracker.parcours.value.size)

        // Un aller-retour par l'accueil a mis la trace en pause : ▶ la CONTINUE.
        CarteSaisie.basculerParcours(tracker)
        assertTrue(tracker.estEnCours.value)
        assertEquals(2, tracker.parcours.value.size, "la trace de la journée n'est pas effacée")
        source.emettre(PositionGps(42.92, 1.40, 5f))
        assertEquals(3, tracker.parcours.value.size)
        tracker.arreter()
    }
}
