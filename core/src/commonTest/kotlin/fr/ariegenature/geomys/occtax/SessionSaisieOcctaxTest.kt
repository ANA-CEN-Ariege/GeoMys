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

// Les deux premiers cas sont portés de GeoMys-Android @ cdd5196
// (app/src/test/java/fr/ariegenature/geomys/TraceViewModelReleveTest.kt) : le ViewModel Robolectric devient la
// session commune, JUnit kotlin.test. Les cas de l'auto-save sont propres au portage : sous Android,
// l'écriture synchrone du store n'avait pas besoin d'être testée à part.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.location.PositionGps
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.PointTrace
import fr.ariegenature.geomys.model.Sortie
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** [SessionSaisieOcctax] : l'upsert des relevés et l'auto-save de la sortie en cours. */
class SessionSaisieOcctaxTest {

    private val t = ServicesDeTest()
    private val store get() = t.services.sortieStore
    private val tracker get() = t.services.locationTracker

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun TestScope.session() = SessionSaisieOcctax(store, tracker, backgroundScope)

    private fun obs(id: String, releveId: String? = null, medias: List<String> = emptyList(), dataset: Int? = null) =
        Observation(
            id = id, espece = "Merle", cdNom = 4001, latitude = 42.9, longitude = 1.4,
            nombre = 1, releveId = releveId, date = 1_700_000_000_000L,
            mediaUrisCounting0 = medias, idDatasetReleve = dataset,
        )

    /** Régression du bug terrain « espèce en double sur un point » : une obs MONO-taxon
     *  (releveId=null) éditée via l'écran multi-taxons ressort avec un releveId de session — la
     *  purge par seul releveId ne retrouvait pas l'originale, qui survivait à côté de la version
     *  éditée (même id d'obs). */
    @Test
    fun editer_une_obs_mono_taxon_la_remplace_au_lieu_de_la_dupliquer() = runTest {
        val session = session()
        // Obs mono-taxon d'origine : releveId = null (chaque obs = son propre relevé).
        session.ajouterObservation(obs("o1"))
        // Édition (ajout d'une photo) : l'écran multi-taxons ré-émet l'obs avec le MÊME id
        // mais le releveId de sa session d'édition.
        session.remplacerObservationsDuReleve(
            "session-uuid",
            listOf(obs("o1", releveId = "session-uuid", medias = listOf("file:///p.jpg"))),
        )
        val obsFinales = session.observations.value
        assertEquals(1, obsFinales.size, "l'obs doit être REMPLACÉE, pas dupliquée")
        assertEquals(listOf("file:///p.jpg"), obsFinales.single().mediaUrisCounting0)
    }

    @Test
    fun remplacer_un_releve_multi_taxons_conserve_les_autres_releves() = runTest {
        val session = session()
        session.ajouterObservation(obs("a", releveId = "R1"))
        session.ajouterObservation(obs("b", releveId = "R1"))
        session.ajouterObservation(obs("autre", releveId = "R2"))
        // Le lot R1 est ré-émis avec une espèce retirée (b supprimée) et une ajoutée.
        session.remplacerObservationsDuReleve("R1", listOf(obs("a", releveId = "R1"), obs("c", releveId = "R1")))
        val ids = session.observations.value.map { it.id }.toSet()
        assertEquals(setOf("a", "c", "autre"), ids)
    }

    @Test
    fun la_premiere_observation_cree_la_sortie_les_suivantes_la_mettent_a_jour() = runTest {
        val session = session()
        session.ajouterObservation(obs("a"))
        val id = session.sortieEnEditionId
        session.ajouterObservation(obs("b"))
        session.mettreAJourObservationPosition("a", 43.0, 1.5)
        session.attendreEcritures()
        val sortie = store.charger().single()
        assertEquals(id, sortie.id, "id fixé dès la première écriture")
        assertEquals(listOf("a", "b"), sortie.observations.map { it.id })
        assertEquals(43.0, sortie.observations.first().latitude)
    }

    /** Défaut n° 7 de l'inventaire corrigé : une saisie mono se sait mono, pour que « Continuer la
     *  saisie » la rouvre dans ce mode, avec les champs de relevé de sa dernière observation. */
    @Test
    fun une_saisie_mono_le_reste_et_se_continue_en_mono() = runTest {
        val session = session()
        session.saisieMono = true
        session.ajouterObservation(obs("a").apply { additionalFieldsReleve = mapOf("meteo" to "beau") })
        session.attendreEcritures()
        val mono = store.charger().single()
        assertTrue(mono.saisieMono)
        assertEquals(mapOf("meteo" to "beau"), MesSaisies.additionnelsPourContinuerEnMono(mono))
        // Reprise : la sortie garde son mode à chaque réécriture.
        session.reprendreSortie(mono)
        session.ajouterObservation(obs("b"))
        session.attendreEcritures()
        assertTrue(store.charger().single().saisieMono)
        // Une saisie multi-taxons, elle, ne l'est pas.
        val multi = session()
        multi.saisieMono = false
        multi.ajouterObservation(obs("c", releveId = "R"))
        multi.attendreEcritures()
        assertFalse(store.charger().first { s -> s.observations.any { it.id == "c" } }.saisieMono)
    }

    @Test
    fun la_trace_part_avec_les_observations_et_la_saisie_videe_est_purgee() = runTest {
        val session = session()
        tracker.demarrer()
        tracker.demarrerParcours()
        t.positions.emettre(PositionGps(42.90, 1.40, 5f))
        t.positions.emettre(PositionGps(42.91, 1.40, 5f))
        session.ajouterObservation(obs("a"))
        session.attendreEcritures()
        val sortie = store.charger().single()
        assertEquals(2, sortie.pointsParcours.size)
        assertTrue(sortie.distanceTotale > 1000.0)
        // Plus aucune observation ni trace : le brouillon disparaît de « Mes saisies ».
        tracker.reinitialiser()
        session.supprimerObservation("a")
        session.attendreEcritures()
        assertTrue(store.charger().isEmpty())
    }

    @Test
    fun une_sortie_deja_envoyee_n_est_jamais_reecrite() = runTest {
        val envoyee = Sortie(id = "s1", observations = listOf(obs("a")), envoyeGeoNature = true)
        store.ajouter(envoyee)
        val session = session()
        session.reprendreSortie(envoyee)
        session.ajouterObservation(obs("b"))
        session.attendreEcritures()
        assertEquals(listOf("a"), store.charger().single().observations.map { it.id })
    }

    @Test
    fun un_echec_d_ecriture_est_signale_jusqu_a_l_acquittement() = runTest {
        // Le temporaire du store occupé par un dossier : l'écriture atomique échoue.
        t.services.socle.systeme.createDirectories(t.dossier / "fichiers" / "sorties_store.json.tmp")
        val session = session()
        session.ajouterObservation(obs("a"))
        session.attendreEcritures()
        assertTrue(session.echecEcritureBrouillon.value)
        session.acquitterEchecEcriture()
        assertFalse(session.echecEcritureBrouillon.value)
        // L'id reste fixé : la tentative suivante remplace au lieu d'ajouter un doublon.
        val id = session.sortieEnEditionId
        t.services.socle.systeme.deleteRecursively(t.dossier / "fichiers" / "sorties_store.json.tmp")
        session.ajouterObservation(obs("b"))
        session.attendreEcritures()
        assertEquals(id, store.charger().single().id)
    }

    @Test
    fun reprendre_une_sortie_restaure_la_trace_et_repart_en_direct() = runTest {
        val session = session()
        session.definirDateAPosteriori(1L, 2L)
        session.definirDetailsCommuns(DetailsReleveResult(idDataset = 3, idsObservateurs = emptyList(), nomsObservateurs = emptyList(), additionnels = emptyMap()))
        val sortie = Sortie(
            id = "s2",
            observations = listOf(obs("a", dataset = 0), obs("b", dataset = 12)),
            pointsParcours = listOf(PointTrace(42.9, 1.4), PointTrace(42.91, 1.4)),
            distanceTotale = 1100.0,
        )
        session.reprendreSortie(sortie)
        assertEquals("s2", session.sortieEnEditionId)
        assertEquals(2, tracker.parcours.value.size)
        assertEquals(1100.0, tracker.distanceTotale.value)
        assertFalse(session.saisieAPosteriori)
        assertNull(session.dateDebutReleveAPosteriori)
        assertNull(session.detailsCommuns)
        assertEquals(12, session.datasetSortie(), "un jeu de données à 0 ne compte pas")
        session.forcerRepriseAuProchainEcran()
        assertNull(session.sortieEnEditionId)
        session.reinitialiser()
        assertTrue(session.observations.value.isEmpty())
        assertTrue(tracker.parcours.value.isEmpty())
    }
}
