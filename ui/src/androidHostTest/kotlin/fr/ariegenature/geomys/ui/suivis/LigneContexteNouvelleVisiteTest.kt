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

// Garde de GeoMys-Android @ 5b981f3 qui lisait le texte de NouvelleVisiteFragment faute de pouvoir l'actionner
// (MonitoringLigneContexteTest : la réinitialisation du formulaire repose la ligne de contexte). Ici, une
// observation de la chaîne de saisie d'essai est enregistrée, et l'on vérifie la ligne du formulaire suivant.
// Propre au portage : les enchaînements de GeoMys-Android repris le 22 octobre (« Terminer », et « Valider »
// d'une visite, nouvelle ou modifiée, qui ouvre la saisie de ses observations).

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
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

    /** Les collectes d'événements des écrans, arrêtées à la fin du test. */
    private val collectes = Job()

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
        collectes.cancel()
        magasin.clear()
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    private inline fun <reified T : ViewModel> vm(noinline creer: () -> T): T =
        ViewModelProvider.create(magasin, viewModelFactory { initializer { creer() } })[T::class]

    /** Les événements que l'écran envoie, au fil du test. */
    private fun evenements(ecran: NouvelleVisiteViewModel): List<EvenementNouvelleVisite> {
        val recus = mutableListOf<EvenementNouvelleVisite>()
        CoroutineScope(principal + collectes).launch { ecran.evenements.collect { recus += it } }
        return recus
    }

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
    fun la_ligne_de_contexte_survit_aux_saisies_enchainees() {
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
        // La chaîne de saisies de GeoMys-Android (22 octobre) : une observation sous une visite, sans
        // l'interrupteur du web.
        assertTrue(ecran.modeChaine, "observation sous une visite")
        assertFalse(ecran.enchainementPropose)
        assertEquals("Enregistrer + suivante", ecran.libelleBouton)
        assertEquals(CONTEXTE, ecran.contexte.lineSequence().first())

        val premier = requireNotNull(ecran.etat)
        premier.choisirPropositionTaxon("cd_nom", PropositionTaxon("Merle noir", 4117, null))
        premier.saisirTexte("count", "2")
        premier.choisirRadio("distance", 1)
        attendre("l'observation complète") { ecran.boutonActif }
        ecran.enregistrer()
        attendre("le formulaire de l'observation suivante") { ecran.etat.let { it != null && it !== premier } }
        assertEquals(CONTEXTE, ecran.contexte.lineSequence().first(), "la ligne de contexte est reposée avec le formulaire suivant")
        val suivant = requireNotNull(ecran.etat)
        assertEquals("25 à 100 m", suivant.lireValeurs()["distance"], "le champ `keep` est repris, comme sur le web")
        assertEquals(null, suivant.lireValeurs()["cd_nom"], "les autres repartent à vide")
        assertTrue(ecran.modeChaine, "la chaîne continue")
    }

    @Test
    fun terminer_enregistre_l_observation_puis_propose_de_completer_la_visite() {
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
        val etat = requireNotNull(ecran.etat)
        // Modifiée mais incomplète : « Terminer » demande avant de la perdre.
        etat.saisirTexte("count", "2")
        ecran.terminer()
        assertIs<DialogueNouvelleVisite.TerminerBloque>(ecran.dialogue)
        ecran.fermerDialogue()
        // Complète : « Terminer » l'enregistre, puis propose la visite parente, « à compléter ».
        etat.choisirPropositionTaxon("cd_nom", PropositionTaxon("Merle noir", 4117, null))
        etat.choisirRadio("distance", 1)
        attendre("l'observation complète") { ecran.boutonActif }
        val avant = runBlocking { services.outbox.tout().size }
        ecran.terminer()
        attendre("la visite à compléter proposée") { ecran.dialogue is DialogueNouvelleVisite.Completer }
        assertEquals(avant + 1, runBlocking { services.outbox.tout().size }, "l'observation est enregistrée")
    }

    /** Une nouvelle visite du STOM d'essai, sous le site 501, l'utilisateur connecté pour observateur. */
    private fun nouvelleVisite(): NouvelleVisiteViewModel {
        services.config.idRoleUtilisateur = 13
        val ecran = vm {
            NouvelleVisiteViewModel(
                services,
                OuvertureNouvelleVisite(moduleCode = "stom_ariege", parentObjectType = "site", parentId = 501, titreSite = "Col de Port", childObjectType = "visit"),
            )
        }
        attendre("le formulaire d'une visite") { ecran.etat != null }
        return ecran
    }

    @Test
    fun seules_les_informations_de_fin_d_une_visite_peuvent_attendre() {
        // 22 octobre : « seule l'heure de fin, si elle est demandée, n'est pas obligatoire au début ;
        // les autres champs obligatoires doivent être renseignés dès le début », puis les autres
        // informations de fin, « seulement si elles sont obligatoires ».
        val ecran = nouvelleVisite()
        val etat = requireNotNull(ecran.etat)
        assertFalse(ecran.boutonActif, "la météo manque")
        assertTrue("meteo" in ecran.barres)
        assertFalse("heure_fin" in ecran.barres, "l'heure de fin n'est pas attendue au début")
        assertFalse("temperature_fin" in ecran.barres, "la température de fin non plus")
        etat.choisirOption("meteo", 0)
        attendre("la visite enregistrable sans ses informations de fin") { ecran.boutonActif }
        assertFalse("heure_fin" in ecran.barres)
        // Rouverte pour être complétée, avant l'envoi : l'heure de fin porte sa barre. (Un écran à la
        // fois : le magasin rendrait le même.)
        magasin.clear()
        val completion = vm { NouvelleVisiteViewModel(services, OuvertureNouvelleVisite(moduleCode = "stom_ariege", editUuid = "essai-visite-col-de-port", modeCompletion = true)) }
        attendre("le formulaire à compléter") { completion.etat != null }
        assertTrue("heure_fin" in completion.barres)
    }

    @Test
    fun valider_une_nouvelle_visite_ouvre_la_saisie_de_ses_observations() {
        // 22 octobre : « Annuler » et « Valider » seuls ; « Valider » remplace « Valider et saisir des
        // Observations », pour toujours enchaîner, comme GeoMys-Android.
        val ecran = nouvelleVisite()
        assertFalse(ecran.modeChaine)
        assertFalse(ecran.enchainementPropose)
        assertEquals("Valider", ecran.libelleBouton)
        val recus = evenements(ecran)
        requireNotNull(ecran.etat).choisirOption("meteo", 0)
        attendre("la visite enregistrable") { ecran.boutonActif }
        ecran.enregistrer()
        attendre("la saisie des observations") { recus.any { it is EvenementNouvelleVisite.Remplacer } }
        val suite = recus.filterIsInstance<EvenementNouvelleVisite.Remplacer>().single().ouverture
        assertEquals("observation", suite.childObjectType)
        assertEquals("visit", suite.parentObjectType)
        val visite = runBlocking { services.outbox.tout() }.single { it.uuid == suite.parentUuidLocal }
        assertEquals("visit", visite.objectType)
        assertEquals(setOf("Heure de fin", "Température de fin (°C)"), visite.champsManquants.orEmpty().toSet(), "partie « à compléter », sans ses informations de fin")
    }

    @Test
    fun valider_une_visite_modifiee_ouvre_la_saisie_de_nouvelles_observations() {
        services.config.idRoleUtilisateur = 13
        val ecran = vm { NouvelleVisiteViewModel(services, OuvertureNouvelleVisite(moduleCode = "stom_ariege", editUuid = "essai-visite-col-de-port")) }
        attendre("le formulaire de la visite") { ecran.etat != null }
        assertEquals("Valider", ecran.libelleBouton)
        val recus = evenements(ecran)
        // La photo d'essai n'a pas de titre, obligatoire comme sur le web.
        requireNotNull(ecran.etat).modifierMedia("medias", "file:///essai/col-de-port.jpg", titre = "Col de Port")
        attendre("la visite enregistrable " + ecran.etat!!.champsObligatoiresManquants()) { ecran.boutonActif }
        ecran.enregistrer()
        attendre("la saisie des observations") { recus.any { it is EvenementNouvelleVisite.Remplacer } }
        val suite = recus.filterIsInstance<EvenementNouvelleVisite.Remplacer>().single().ouverture
        assertEquals("observation", suite.childObjectType)
        assertEquals("visit", suite.parentObjectType)
        assertEquals("essai-visite-col-de-port", suite.parentUuidLocal)
    }

    private companion object {
        const val CONTEXTE = "Sur : Visite (local) — Protocole : stom_ariege"
    }
}
