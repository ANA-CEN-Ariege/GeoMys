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

// Propre au portage : le changement de serveur dans les Paramètres, devenu un geste courant avec la liste
// des serveurs (audit du 14 novembre 2026, M46 à M49). Rien ne s'efface sans une adresse valide et une
// confirmation, et le mot de passe enregistré ne part pas vers un autre serveur.

package fr.ariegenature.geomys.ui.parametres

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.TaxRefEntry
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChangementServeurTest {

    private val essai = ServicesEssai()
    private val config get() = essai.services.config
    private val magasin = ViewModelStore()

    @AfterTest
    fun nettoyer() {
        magasin.clear()
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    /** Une configuration complète sur `https://exemple.test`. */
    private fun vm(): ParametresViewModel {
        config.apply {
            urlServeur = "https://exemple.test"
            login = "alice"
            motDePasse = "secret"
            serveurCompatible = true
            datasetsCacheJson = """[{"id":5,"nom":"Inventaire","moduleCodes":["OCCTAX"]}]"""
            datasetsCreablesOcctax = setOf(5)
            idDataset = "5"
            listesCacheJson = """[{"id":100,"nom":"Saisie"}]"""
            taxaListeId = "100"
            observateursCacheJson = """[{"idRole":8,"nomComplet":"MARTIN Paul"}]"""
            idRoleUtilisateur = 8
            nomUtilisateur = "MARTIN Paul"
            versionDonneesChargees = GeoNatureConfig.VERSION_DONNEES_REQUISE
        }
        essai.services.taxRef.remplacerTout(mapOf(TaxRefCache.normaliser("Machaon") to TaxRefEntry(54468, "Papilio machaon", "Machaon")))
        essai.services.taxRef.versionSauvegardee = "17"
        essai.services.taxRef.listesSynchronisees = listOf(100)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        return ViewModelProvider.create(magasin, viewModelFactory { initializer { ParametresViewModel(essai.services) } })[ParametresViewModel::class]
    }

    private fun attendre(condition: () -> Boolean) {
        val fin = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < fin) { "délai dépassé" }
            Thread.sleep(10)
        }
    }

    private fun ParametresViewModel.evenement(): EvenementParametres = runBlocking { withTimeout(5_000) { evenements.first() } }

    @Test
    fun une_recherche_dans_la_liste_n_est_jamais_enregistree_comme_serveur() {
        val vm = vm()
        vm.changerUrl("pyr")
        vm.testerConnexion()
        assertEquals(ADRESSE_NON_VALIDE, vm.resultatTest?.texte)
        assertNull(vm.changementServeur)
        assertEquals("https://exemple.test", config.urlServeur)
        assertEquals("5", config.idDataset, "rien n'est purgé")
        assertTrue(essai.services.taxRef.count > 0)
    }

    @Test
    fun la_coche_garde_la_connexion_precedente_si_la_nouvelle_n_est_pas_testee() {
        val vm = vm()
        vm.changerUrl("https://autre.test")
        vm.valider()
        assertEquals(
            EvenementParametres.Toast("Connexion modifiée mais non testée : la connexion précédente est conservée.", longue = true),
            vm.evenement(),
        )
        assertEquals(EvenementParametres.Fermer, vm.evenement())
        assertEquals("https://exemple.test", config.urlServeur)
        assertEquals("secret", config.motDePasse)
        assertEquals("5", config.idDataset)
        assertEquals("https://exemple.test", vm.url, "le champ revient à la connexion enregistrée")
    }

    @Test
    fun le_mot_de_passe_enregistre_ne_suit_pas_vers_un_autre_serveur() {
        val vm = vm()
        assertEquals("secret", vm.motDePasse)
        vm.changerUrl("https://autre.test/geonature")
        assertEquals("", vm.motDePasse, "il n'est pas destiné à ce serveur")
        assertEquals("alice", vm.login, "l'identifiant reste proposé")
        vm.changerUrl("https://exemple.test/")
        assertEquals("secret", vm.motDePasse, "de retour sur le serveur enregistré")
        // Un mot de passe tapé pour le nouveau serveur n'est pas effacé par une retouche de l'adresse.
        vm.changerUrl("https://autre.test")
        vm.changerMotDePasse("nouveau")
        vm.changerUrl("https://autre.test/geonature")
        assertEquals("nouveau", vm.motDePasse)
    }

    @Test
    fun changer_de_serveur_demande_confirmation_et_compte_les_envois_en_attente() {
        val vm = vm()
        runBlocking { essai.services.sortieStore.ajouter(Sortie(observations = listOf(Observation(espece = "Machaon", latitude = 42.9, longitude = 1.6)))) }
        vm.changerUrl("https://autre.test")
        vm.changerMotDePasse("p2")
        vm.testerConnexion()
        attendre { vm.changementServeur != null }
        val changement = assertNotNull(vm.changementServeur)
        assertEquals("https://exemple.test", changement.ancien)
        assertEquals("https://autre.test", changement.nouveau)
        assertEquals(1, changement.envoisEnAttente)
        assertTrue(changement.message.contains("1 saisie attend encore d'être envoyée"), changement.message)
        assertEquals("https://exemple.test", config.urlServeur, "rien n'est enregistré avant la confirmation")
        assertTrue(essai.services.taxRef.count > 0)

        vm.annulerChangementServeur()
        assertNull(vm.changementServeur)
        assertEquals("https://exemple.test", config.urlServeur)
        assertEquals("5", config.idDataset)

        vm.testerConnexion()
        attendre { vm.changementServeur != null }
        vm.confirmerChangementServeur()
        assertEquals("https://autre.test", config.urlServeur)
        assertEquals("p2", config.motDePasse)
        assertEquals("", config.idDataset, "les sélections de l'ancien serveur sont vidées")
        assertEquals(0, essai.services.taxRef.count, "et son référentiel aussi")
    }

    @Test
    fun retester_le_meme_serveur_ne_demande_rien() {
        val vm = vm()
        vm.changerUrl("https://exemple.test/")
        vm.testerConnexion()
        assertNull(vm.changementServeur)
        assertEquals("https://exemple.test", config.urlServeur)
        assertEquals("5", config.idDataset)
        assertTrue(essai.services.taxRef.count > 0)
    }
}
