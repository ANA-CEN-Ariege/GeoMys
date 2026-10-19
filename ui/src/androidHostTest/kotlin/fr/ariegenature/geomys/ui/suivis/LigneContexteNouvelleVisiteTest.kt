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

// Garde de GeoMys-Android @ 5b981f3 qui lisait le texte de NouvelleVisiteFragment faute de pouvoir l'actionner
// (MonitoringLigneContexteTest : la réinitialisation du formulaire repose la ligne de contexte). Ici, une
// observation de la chaîne de saisie d'essai est enregistrée, et l'on vérifie la ligne du formulaire suivant.

package fr.ariegenature.geomys.ui.suivis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.monitoring.form.PropositionTaxon
import fr.ariegenature.geomys.outils.ajouterMonitoringEssai
import fr.ariegenature.geomys.outils.ajouterTaxRefEssai
import fr.ariegenature.geomys.ui.ServicesEssai
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class LigneContexteNouvelleVisiteTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services
    private val magasin = ViewModelStore()

    /** Le fil principal de l'écran : ses coroutines n'avancent que lorsque le test le pompe. */
    private val principal = StandardTestDispatcher()

    @BeforeTest
    fun preparer() {
        Dispatchers.setMain(principal)
        runBlocking {
            services.ajouterTaxRefEssai()
            services.ajouterMonitoringEssai()
        }
    }

    @AfterTest
    fun nettoyer() {
        magasin.clear()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    private inline fun <reified T : ViewModel> vm(noinline creer: () -> T): T =
        ViewModelProvider.create(magasin, viewModelFactory { initializer { creer() } })[T::class]

    /** Pompe le fil principal jusqu'à [condition] : l'écran lit le stockage sur d'autres fils. */
    private fun attendre(quoi: String, condition: () -> Boolean) {
        val limite = System.currentTimeMillis() + 10_000
        while (true) {
            principal.scheduler.advanceUntilIdle()
            if (condition()) return
            if (System.currentTimeMillis() > limite) fail("attendu : $quoi")
            Thread.sleep(10)
        }
    }

    @Test
    fun la_ligne_de_contexte_survit_a_enregistrer_et_suivante() {
        val ecran = vm {
            NouvelleVisiteViewModel(
                services,
                OuvertureNouvelleVisite(
                    moduleCode = "stom_ariege",
                    parentObjectType = "visit",
                    titreSite = "Visite (local)",
                    childObjectType = "observation",
                    parentUuidLocal = "essai-visite-col-de-port",
                ),
            )
        }
        attendre("le formulaire d'une observation") { ecran.etat != null }
        assertTrue(ecran.modeChaine, "observations en chaîne")
        assertEquals(CONTEXTE, ecran.contexte.lineSequence().first())

        val premier = requireNotNull(ecran.etat)
        premier.choisirPropositionTaxon("cd_nom", PropositionTaxon("Merle noir", 4117, null))
        premier.saisirTexte("count", "2")
        attendre("l'observation complète") { ecran.boutonActif }
        ecran.enregistrer()
        attendre("le formulaire de l'observation suivante") { ecran.etat.let { it != null && it !== premier } }
        assertEquals(CONTEXTE, ecran.contexte.lineSequence().first(), "la ligne de contexte est reposée avec le formulaire suivant")
    }

    private companion object {
        const val CONTEXTE = "Sur : Visite (local) — Protocole : stom_ariege"
    }
}
