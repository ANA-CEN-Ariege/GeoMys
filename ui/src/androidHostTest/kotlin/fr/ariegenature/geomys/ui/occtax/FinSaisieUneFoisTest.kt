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

// Relecture du 25 novembre 2026 : pendant l'écriture de la fin d'une saisie, la carte restait touchable.
// Un relevé, la liste ou une fiche ouverts entre-temps la recouvraient, et « Quitter », refusé par la
// garde de la pile (retourDepuis), se perdait ; le retour système rouvrait « Terminer la saisie ».

package fr.ariegenature.geomys.ui.occtax

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.occtax.GeometrieFinale
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class FinSaisieUneFoisTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services
    private val magasin = ViewModelStore()

    /** Le fil principal : les coroutines n'avancent que lorsque le test le pompe. Les touchers qui
     *  suivent « Enregistrer et quitter » arrivent donc pendant l'écriture, comme sur le téléphone. */
    private val principal = StandardTestDispatcher()
    private val portee = CoroutineScope(principal)

    @BeforeTest
    fun preparer() {
        Dispatchers.setMain(principal)
    }

    @AfterTest
    fun nettoyer() {
        portee.cancel()
        magasin.clear()
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    private inline fun <reified T : ViewModel> vm(noinline creer: () -> T): T =
        ViewModelProvider.create(magasin, viewModelFactory { initializer { creer() } })[T::class]

    /** Pompe le fil principal jusqu'à [condition] : le stockage s'écrit sur d'autres fils. */
    private fun attendre(quoi: String, condition: () -> Boolean) {
        val limite = System.currentTimeMillis() + 10_000
        while (true) {
            principal.scheduler.advanceUntilIdle()
            if (condition()) return
            if (System.currentTimeMillis() > limite) fail("attendu : $quoi")
            Thread.sleep(10)
        }
    }

    /** Pompe le fil principal un moment : ce qu'un toucher aurait lancé a le temps de finir. */
    private fun laisserFinir() {
        repeat(30) {
            principal.scheduler.advanceUntilIdle()
            Thread.sleep(10)
        }
    }

    /** Les saisies écrites, lues sur le fil principal que le test pompe : lues en le bloquant, elles
     *  attendaient le verrou d'une écriture qui ne pouvait finir sans lui. */
    private fun saisiesEcrites(): List<Sortie> {
        var lues: List<Sortie>? = null
        portee.launch { lues = services.sortieStore.charger() }
        attendre("la lecture des saisies") { lues != null }
        return lues.orEmpty()
    }

    @Test
    fun la_fin_d_une_saisie_multi_taxons_ne_part_qu_une_fois() {
        val session = vm { SessionSaisieViewModel(services) }
        val multi = vm { SaisieMultiViewModel(services, session, null) }
        val recus = mutableListOf<EvenementSaisieMulti>()
        portee.launch { multi.evenements.collect { recus += it } }
        ReleveEnCours(multi.session, services.config, OuvertureReleve(geometrie = GeometrieFinale("Point", null, 42.9, 1.6)), dernierTaxon = null)
            .ajouter(Taxon.INSECTE, "Machaon", 54303)
        attendre("la saisie enregistrée au fil de l'eau") { saisiesEcrites().size == 1 }

        multi.demanderTerminer()
        assertTrue(multi.confirmerTerminer)
        multi.terminer(envoyer = false)
        assertTrue(multi.finEnCours, "la carte ne répond plus pendant l'écriture")
        // Reçus pendant l'écriture : le retour système, puis un second « Enregistrer et quitter ».
        multi.demanderTerminer()
        assertFalse(multi.confirmerTerminer, "« Terminer la saisie » ne se rouvre pas")
        multi.terminer(envoyer = false)
        attendre("la carte quittée") { EvenementSaisieMulti.Quitter in recus }
        laisserFinir()

        assertFalse(multi.finEnCours, "l'écriture finie, la carte répond de nouveau")
        assertEquals(1, recus.count { it == EvenementSaisieMulti.Quitter }, "un seul « Quitter » : $recus")
        assertEquals(1, saisiesEcrites().single().observations.size)
    }

    @Test
    fun la_fin_d_une_saisie_mono_taxon_ne_part_qu_une_fois() {
        runBlocking { services.ajouterTaxRefEssai() }
        services.config.apply {
            datasetsCacheJson = """[{"id":12,"nom":"JDD","moduleCodes":["OCCTAX"]}]"""
            datasetsCreablesOcctax = setOf(12)
            idDataset = "12"
        }
        services.preferences.memoiserTaxon(Taxon.INSECTE)
        val session = vm { SessionSaisieViewModel(services) }
        val mono = vm { SaisieMonoViewModel(services, session, null) }
        val recus = mutableListOf<EvenementSaisieMono>()
        portee.launch { mono.evenements.collect { recus += it } }
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        attendre("la fiche de la série") { EvenementSaisieMono.FicheSerie in recus }
        mono.demarrer()
        mono.enregistrerIci(LatLon(42.9, 1.6))
        attendre("la saisie enregistrée au fil de l'eau") { saisiesEcrites().size == 1 }

        mono.demanderTerminer()
        assertTrue(mono.confirmerTerminer)
        mono.terminer(envoyer = false)
        assertTrue(mono.finEnCours, "l'écran ne répond plus pendant l'écriture")
        mono.demanderTerminer()
        assertFalse(mono.confirmerTerminer, "« Terminer la saisie » ne se rouvre pas")
        mono.terminer(envoyer = false)
        attendre("la saisie quittée") { EvenementSaisieMono.Quitter in recus }
        laisserFinir()

        assertFalse(mono.finEnCours)
        assertEquals(1, recus.count { it == EvenementSaisieMono.Quitter }, "un seul « Quitter » : $recus")
        assertEquals(1, saisiesEcrites().single().observations.size)
    }

    @Test
    fun quitter_le_flux_de_saisie_oublie_le_positionnement_du_releve_suivant() {
        // Posé par la coche d'un relevé, il n'était lu qu'au retour de la carte : un retour système
        // reçu juste après quittait aussi la carte, et la saisie suivante s'ouvrait en positionnement.
        val session = vm { SessionSaisieViewModel(services) }
        session.demarrerSaisieSuivante = true
        session.quitterFluxSaisie()
        assertFalse(session.demarrerSaisieSuivante)
    }
}
