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

// Demande du 24 novembre 2026 : sur la carte de la saisie mono-taxon, un marqueur touché ouvre le popup de
// son observation, « Éditer » (sa fiche, directement) ou « Supprimer » (après confirmation), au lieu de la
// liste des observations à 30 m. Plusieurs au même point : on choisit d'abord laquelle.

package fr.ariegenature.geomys.ui.occtax

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.occtax.OuvertureReleve
import fr.ariegenature.geomys.occtax.ReleveEnCours
import fr.ariegenature.geomys.outils.ajouterTaxRefEssai
import fr.ariegenature.geomys.store.SuggestionTaxon
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ObservationToucheeMonoTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services
    private val magasin = ViewModelStore()

    @BeforeTest
    fun preparer() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun nettoyer() {
        magasin.clear()
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    private inline fun <reified T : ViewModel> vm(noinline creer: () -> T): T =
        ViewModelProvider.create(magasin, viewModelFactory { initializer { creer() } })[T::class]

    /** Une série démarrée (Machaon, jeu de données par défaut des Paramètres), la carte ouverte. */
    private suspend fun serie(): SaisieMonoViewModel {
        services.ajouterTaxRefEssai()
        services.config.apply {
            datasetsCacheJson = """[{"id":12,"nom":"JDD","moduleCodes":["OCCTAX"]}]"""
            datasetsCreablesOcctax = setOf(12)
            idDataset = "12"
        }
        services.preferences.memoiserTaxon(Taxon.INSECTE)
        val session = vm { SessionSaisieViewModel(services) }
        val mono = vm { SaisieMonoViewModel(services, session, null) }
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(EvenementSaisieMono.FicheSerie, mono.evenements.first(), "l'espèce choisie ouvre la fiche de la série")
        mono.demarrer()
        assertTrue(mono.modeActif)
        return mono
    }

    @Test
    fun le_marqueur_touche_ouvre_le_popup_de_son_observation_et_editer_ouvre_sa_fiche() = runTest {
        val mono = serie()
        mono.enregistrerIci(LatLon(42.9, 1.6))
        mono.enregistrerIci(LatLon(42.9001, 1.6001))
        val premiere = mono.session.observations.value.first()
        mono.toucherObservation(premiere)
        assertEquals(premiere, mono.observationTouchee, "le popup de l'observation touchée, pas la liste de celles à 30 m")
        mono.editerObservationTouchee()
        assertNull(mono.observationTouchee)
        assertEquals(EvenementSaisieMono.OuvrirFiche(premiere.id), mono.evenements.first(), "sa fiche, sans l'écran du relevé")
    }

    @Test
    fun supprimer_demande_confirmation_puis_retire_l_observation_et_la_decompte() = runTest {
        val mono = serie()
        // Enregistrée avant l'ouverture de l'écran (saisie reprise) : le compteur la compte aussi, comme
        // toutes celles de la saisie (25 novembre 2026).
        val ancienne = mono.saisie.enregistrer(42.89, 1.59)
        mono.enregistrerIci(LatLon(42.9, 1.6))
        mono.enregistrerIci(LatLon(42.91, 1.61))
        val derniere = mono.session.observations.value.last()
        assertEquals(3, mono.nombreObservations())

        mono.toucherObservation(derniere)
        mono.demanderSuppression()
        assertNull(mono.observationTouchee, "le popup laisse place à la confirmation")
        assertEquals(derniere, mono.suppressionDemandee)
        mono.annulerSuppression()
        assertEquals(3, mono.session.observations.value.size, "rien n'est supprimé sans confirmation")

        mono.toucherObservation(derniere)
        mono.demanderSuppression()
        mono.confirmerSuppression()
        assertNull(mono.suppressionDemandee)
        assertTrue(mono.session.observations.value.none { it.id == derniere.id })
        assertEquals(2, mono.nombreObservations())

        mono.toucherObservation(ancienne)
        mono.demanderSuppression()
        mono.confirmerSuppression()
        assertEquals(1, mono.session.observations.value.size)
        assertEquals(1, mono.nombreObservations(), "une observation d'avant l'ouverture se décompte aussi")
    }

    /** Observations au même point (une espèce ajoutée au relevé d'une observation par l'écran du
     *  relevé y prend sa position) : on choisit d'abord laquelle ; « Éditer » ouvre alors sa fiche par
     *  leur relevé, à son rang, sans effacer l'autre espèce. */
    @Test
    fun au_meme_point_on_choisit_puis_editer_passe_par_le_releve_commun() = runTest {
        val mono = serie()
        mono.enregistrerIci(LatLon(42.9, 1.6))
        val machaon = mono.session.observations.value.single()
        ReleveEnCours(mono.session, services.config, OuvertureReleve(obsId = machaon.id), dernierTaxon = null).ajouter(Taxon.INSECTE, "Flambé", 54317)
        val flambe = mono.session.observations.value.single { it.espece == "Flambé" }
        assertEquals(machaon.id, flambe.releveId, "le relevé de l'observation seule garde sa clé")

        mono.toucherObservation(machaon)
        assertNull(mono.observationTouchee)
        assertEquals(setOf(machaon.id, flambe.id), mono.observationsAuPoint.orEmpty().map { it.id }.toSet())
        mono.choisirObservationAuPoint(flambe)
        assertNull(mono.observationsAuPoint)
        assertEquals(flambe, mono.observationTouchee)
        mono.editerObservationTouchee()
        val fiche = mono.evenements.first() as EvenementSaisieMono.OuvrirFiche
        assertEquals(EvenementSaisieMono.OuvrirFiche(flambe.id, machaon.id, 1), fiche)
        // Ce que la navigation ouvre : leur relevé, dont l'espèce de ce rang est le Flambé ; valider sa
        // fiche le modifie et garde le Machaon (ouvert seul, le Flambé aurait remplacé le relevé).
        val porteur = ReleveEnCours(mono.session, services.config, OuvertureReleve(releveId = fiche.releveId), dernierTaxon = null)
        assertEquals(flambe.id, porteur.especes.value[fiche.index].existingId)
        porteur.modifier(fiche.index) { it.copy(nombre = 4) }
        assertEquals(4, mono.session.observations.value.single { it.id == flambe.id }.nombre)
        assertTrue(mono.session.observations.value.any { it.id == machaon.id }, "l'autre espèce du relevé reste")
    }
}
