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

// Propre au portage : la fin d'une saisie vivait dans TraceFragment et SaisieRapideFragment
// (GeoMys-Android @ cdd5196), sans test.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.location.PositionGps
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** « Terminer la saisie » : écriture de la sortie, remise à zéro de la session. */
class FinSaisieTest {

    private val t = ServicesDeTest()
    private val store get() = t.services.sortieStore
    private val tracker get() = t.services.locationTracker

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun TestScope.session() = SessionSaisieOcctax(store, tracker, backgroundScope)

    private fun obs(id: String) = Observation(id = id, espece = "Merle", cdNom = 4117, latitude = 42.9, longitude = 1.4)

    @Test
    fun options_du_dialogue() {
        assertEquals(listOf("Enregistrer et quitter", "Enregistrer et envoyer à GeoNature", "Continuer la saisie"), FinSaisie.options(true))
        assertEquals(listOf("Enregistrer et quitter", "Continuer la saisie"), FinSaisie.options(false))
    }

    @Test
    fun multi_ecrit_la_trace_et_les_observations_puis_remet_a_zero() = runTest {
        val session = session()
        tracker.demarrer()
        tracker.demarrerParcours()
        t.positions.emettre(PositionGps(42.90, 1.40, 5f))
        t.positions.emettre(PositionGps(42.91, 1.40, 5f))
        session.ajouterObservation(obs("a"))
        val id = session.sortieEnEditionId
        val res = FinSaisie.terminerMulti(session, store)
        assertTrue(res.ecrite)
        assertFalse(tracker.estEnCours.value, "le parcours s'arrête")
        val sortie = store.charger().single()
        assertEquals(id, sortie.id, "même entrée que le brouillon")
        assertEquals(2, sortie.pointsParcours.size)
        assertTrue(session.observations.value.isEmpty())
        assertNull(session.sortieEnEditionId)
        assertTrue(tracker.parcours.value.isEmpty())
    }

    @Test
    fun multi_reprise_garde_id_et_date() = runTest {
        store.ajouter(Sortie(id = "s1", date = 1_000L, observations = listOf(obs("a"))))
        val session = session()
        session.reprendreSortie(store.charger().single())
        session.ajouterObservation(obs("b"))
        val res = FinSaisie.terminerMulti(session, store)
        assertEquals("s1", res.sortie.id)
        assertEquals(1_000L, store.charger().single().date)
        assertEquals(listOf("a", "b"), store.charger().single().observations.map { it.id })
    }

    @Test
    fun echec_d_ecriture_la_session_reste_intacte() = runTest {
        val session = session()
        session.ajouterObservation(obs("a"))
        session.attendreEcritures()
        t.services.socle.systeme.createDirectories(t.dossier / "fichiers" / "sorties_store.json.tmp")
        val res = FinSaisie.terminerMulti(session, store)
        assertFalse(res.ecrite)
        assertEquals(1, session.observations.value.size, "seule copie de la saisie : rien n'est effacé")
    }

    @Test
    fun mono_videe_supprime_son_brouillon() = runTest {
        val session = session()
        session.ajouterObservation(obs("a"))
        session.attendreEcritures()
        session.supprimerObservation("a")
        val res = FinSaisie.terminerMono(session, store)
        assertTrue(res.ecrite)
        assertTrue(store.charger().isEmpty())
    }
}
