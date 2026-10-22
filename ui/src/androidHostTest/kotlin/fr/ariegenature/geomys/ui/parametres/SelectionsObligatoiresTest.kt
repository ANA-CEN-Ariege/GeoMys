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

// Propre au portage : les sélections par défaut des Paramètres, obligatoires et sans valeur d'office
// (demandes du 20 octobre 2026), comme les trois menus de ConfigGeoNatureFragment de GeoMys-Android.

package fr.ariegenature.geomys.ui.parametres

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.model.GeoNatureListe
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SelectionsObligatoiresTest {

    private val essai = ServicesEssai()
    private val magasin = ViewModelStore()

    @AfterTest
    fun nettoyer() {
        magasin.clear()
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    /** Une connexion, des données chargées : deux jeux proposés, dont un qui impose sa liste, deux
     *  listes, un observateur, et l'utilisateur connecté. */
    private fun vm(): ParametresViewModel {
        essai.services.config.apply {
            urlServeur = "https://exemple.test"
            login = "u"
            motDePasse = "p"
            serveurCompatible = true
            datasetsCacheJson = """[{"id":5,"nom":"Inventaire","moduleCodes":["OCCTAX"]},
                {"id":6,"nom":"Atlas","idTaxaList":100,"moduleCodes":["OCCTAX"]}]"""
            datasetsCreablesOcctax = setOf(5, 6)
            listesCacheJson = """[{"id":100,"nom":"Saisie"},{"id":101,"nom":"Flore"},{"id":102,"nom":"Lichens"}]"""
            observateursCacheJson = """[{"idRole":8,"nomComplet":"MARTIN Paul"}]"""
            idRoleUtilisateur = 13
            nomUtilisateur = "MARTIN Alice"
            versionDonneesChargees = GeoNatureConfig.VERSION_DONNEES_REQUISE
        }
        essai.services.taxRef.remplacerTout(mapOf(TaxRefCache.normaliser("Machaon") to TaxRefEntry(54468, "Papilio machaon", "Machaon")))
        essai.services.taxRef.versionSauvegardee = "17"
        essai.services.taxRef.listesSynchronisees = listOf(100, 101)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        return ViewModelProvider.create(
            magasin,
            viewModelFactory { initializer { ParametresViewModel(essai.services) } },
        )[ParametresViewModel::class]
    }

    /** L'écran lit le cache hors du fil principal, et, sous le dispatcher de test, poursuit sur ce
     *  fil : on attend qu'il ait fini de poser ses choix (l'observateur, posé en dernier). */
    private fun attendre(condition: () -> Boolean) {
        val fin = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < fin) { "délai dépassé" }
            Thread.sleep(10)
        }
    }

    private fun ParametresViewModel.evenement(): EvenementParametres = runBlocking { withTimeout(5_000) { evenements.first() } }

    @Test
    fun le_jeu_et_la_liste_sont_a_choisir_et_bloquent_la_sortie() {
        val vm = vm()
        attendre { vm.sectionDonneesVisible && vm.observateurChoisi != null }
        // Rien d'office : le jeu et la liste sont vides et portent la barre ; l'observateur est
        // l'utilisateur connecté.
        assertNull(vm.datasetChoisi)
        assertNull(vm.listeChoisie)
        assertTrue(vm.jddManquant)
        assertTrue(vm.listeManquante)
        assertEquals("MARTIN Alice", vm.observateurChoisi?.nomComplet)
        assertFalse(vm.observateurManquant)
        vm.valider()
        assertEquals(
            EvenementParametres.Toast(
                "Configuration incomplète : renseignez la connexion, chargez les données, puis choisissez jeu de données / liste / observateur.",
                longue = true,
            ),
            vm.evenement(),
        )
        assertFalse(vm.configurationComplete)

        vm.choisirDataset(vm.datasets.first { it.id == 5 })
        assertFalse(vm.jddManquant)
        assertTrue(vm.listeManquante, "le jeu 5 n'impose pas de liste")
        vm.choisirListe(GeoNatureListe(101, "Flore"))
        assertFalse(vm.listeManquante)
        attendre { vm.configurationComplete }
        vm.valider()
        assertEquals(EvenementParametres.Fermer, vm.evenement())
    }

    @Test
    fun la_liste_imposee_par_le_jeu_suffit() {
        val vm = vm()
        attendre { vm.sectionDonneesVisible && vm.observateurChoisi != null }
        vm.choisirDataset(vm.datasets.first { it.id == 6 })
        assertTrue(vm.listes.imposee)
        assertFalse(vm.listeManquante, "le jeu 6 impose la liste 100")
        attendre { vm.configurationComplete }
    }

    @Test
    fun un_observateur_choisi_puis_retire_revient_a_l_utilisateur_connecte() {
        val vm = vm()
        attendre { vm.sectionDonneesVisible && vm.observateurChoisi != null }
        vm.choisirObservateur(vm.observateurs.first { it.idRole == 8 })
        assertEquals("MARTIN Paul", vm.observateurChoisi?.nomComplet)
        vm.choisirObservateur(null)
        assertEquals("MARTIN Alice", vm.observateurChoisi?.nomComplet)
        assertFalse(vm.observateurManquant)
    }

    @Test
    fun une_liste_absente_du_telephone_se_charge_des_son_choix() {
        // 21 octobre : la synchro ne charge que les listes utiles.
        val vm = vm()
        attendre { vm.sectionDonneesVisible && vm.observateurChoisi != null }
        vm.choisirListe(GeoNatureListe(102, "Lichens"))
        assertEquals(EvenementParametres.Toast("Chargement de la liste « Lichens »…", longue = false), vm.evenement())
        assertTrue(vm.synchroEnCours, "le chargement est lancé")
        assertEquals("102", essai.services.config.taxaListeId)
    }
}
