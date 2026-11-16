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

// Propre au portage (demande du 16 novembre 2026) : en saisie simplifiée, les Paramètres ne proposent
// que les jeux de données personnels de l'utilisateur connecté, s'il en a.

package fr.ariegenature.geomys.ui.parametres

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.ModeSaisie
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.TaxRefEntry
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class JeuxPersonnelsTest {

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

    /** Un serveur chargé : un jeu commun (5), le jeu personnel de l'utilisateur 8 (6) et, selon
     *  [deuxPersonnels], un second (7). */
    private fun vm(deuxPersonnels: Boolean = false, idDataset: String = "", idListe: String = "", idObservateur: String = ""): ParametresViewModel {
        val second = if (deuxPersonnels) """,{"id":7,"nom":"Mon jardin","moduleCodes":["OCCTAX"],"acteurs":[8]}""" else ""
        config.apply {
            urlServeur = "https://exemple.test"
            login = "alice"
            motDePasse = "secret"
            serveurCompatible = true
            datasetsCacheJson = """[{"id":5,"nom":"Inventaire","moduleCodes":["OCCTAX"]},""" +
                """{"id":6,"nom":"Personnel","moduleCodes":["OCCTAX"],"acteurs":[8]}$second]"""
            datasetsCreablesOcctax = setOf(5, 6, 7)
            observateursCacheJson = """[{"idRole":8,"nomComplet":"MARTIN Paul"},{"idRole":12,"nomComplet":"DURAND Anne"}]"""
            observateurDefautId = idObservateur
            listesCacheJson = """[{"id":100,"nom":"Saisie Occtax"},{"id":200,"nom":"Oiseaux"}]"""
            occtaxIdListeTaxons = 100
            taxaListeId = idListe
            this.idDataset = idDataset
            idRoleUtilisateur = 8
            nomUtilisateur = "MARTIN Paul"
            versionDonneesChargees = GeoNatureConfig.VERSION_DONNEES_REQUISE
        }
        essai.services.taxRef.remplacerTout(mapOf(TaxRefCache.normaliser("Machaon") to TaxRefEntry(54468, "Papilio machaon", "Machaon")))
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val vm = ViewModelProvider.create(magasin, viewModelFactory { initializer { ParametresViewModel(essai.services) } })[ParametresViewModel::class]
        // Les choix se repeuplent depuis le cache en tâche de fond, à l'ouverture.
        val fin = System.currentTimeMillis() + 5_000
        while (vm.datasets.isEmpty()) {
            check(System.currentTimeMillis() < fin) { "délai dépassé" }
            Thread.sleep(10)
        }
        return vm
    }

    @Test
    fun le_mode_simplifie_ne_propose_que_le_jeu_personnel_et_le_choisit_s_il_est_seul() {
        val vm = vm()
        assertEquals(listOf(5, 6), vm.datasets.map { it.id }, "mode pro : tout")
        assertEquals(null, vm.datasetChoisi, "et rien n'est choisi d'office")
        vm.choisirMode(ModeSaisie.SIMPLIFIE)
        assertEquals(listOf(6), vm.datasets.map { it.id })
        assertEquals(6, vm.datasetChoisi?.id)
        assertEquals("6", config.idDataset)
        vm.choisirMode(ModeSaisie.PRO)
        assertEquals(listOf(5, 6), vm.datasets.map { it.id }, "de retour en mode pro, tout revient")
        assertEquals("6", config.idDataset)
    }

    @Test
    fun le_mode_simplifie_ne_propose_que_la_liste_de_saisie_d_occtax_et_la_choisit() {
        val vm = vm()
        assertEquals(listOf(100, 200), vm.listes.listes.map { it.id }, "mode pro : tout")
        assertEquals(null, vm.listeChoisie)
        vm.choisirMode(ModeSaisie.SIMPLIFIE)
        assertEquals(listOf(100), vm.listes.listes.map { it.id })
        assertEquals(100, vm.listeChoisie?.id)
        assertEquals("100", config.taxaListeId)
        vm.choisirMode(ModeSaisie.PRO)
        assertEquals(listOf(100, 200), vm.listes.listes.map { it.id })
    }

    @Test
    fun une_liste_deja_choisie_n_est_pas_effacee_par_le_changement_de_mode() {
        val vm = vm(idListe = "200")
        vm.choisirMode(ModeSaisie.SIMPLIFIE)
        assertEquals(listOf(100), vm.listes.listes.map { it.id })
        assertEquals(200, vm.listeChoisie?.id)
        assertEquals("200", config.taxaListeId)
    }

    @Test
    fun le_mode_simplifie_ne_propose_que_l_utilisateur_connecte_comme_observateur() {
        val vm = vm()
        assertEquals(listOf(8, 12), vm.observateurs.map { it.idRole }, "mode pro : tous")
        vm.choisirMode(ModeSaisie.SIMPLIFIE)
        assertEquals(listOf(8), vm.observateurs.map { it.idRole })
        assertEquals(8, vm.observateurChoisi?.idRole)
        vm.choisirMode(ModeSaisie.PRO)
        assertEquals(listOf(8, 12), vm.observateurs.map { it.idRole })
    }

    @Test
    fun un_observateur_deja_choisi_n_est_pas_efface_par_le_changement_de_mode() {
        val vm = vm(idObservateur = "12")
        vm.choisirMode(ModeSaisie.SIMPLIFIE)
        assertEquals(listOf(8), vm.observateurs.map { it.idRole })
        assertEquals(12, vm.observateurChoisi?.idRole)
        assertEquals("12", config.observateurDefautId)
    }

    @Test
    fun plusieurs_jeux_personnels_restent_a_choisir() {
        val vm = vm(deuxPersonnels = true)
        vm.choisirMode(ModeSaisie.SIMPLIFIE)
        assertEquals(listOf(6, 7), vm.datasets.map { it.id })
        assertEquals(null, vm.datasetChoisi)
    }

    @Test
    fun un_jeu_deja_choisi_n_est_pas_efface_par_le_changement_de_mode() {
        val vm = vm(idDataset = "5")
        vm.choisirMode(ModeSaisie.SIMPLIFIE)
        assertEquals(listOf(6), vm.datasets.map { it.id })
        assertEquals(5, vm.datasetChoisi?.id)
        assertEquals("5", config.idDataset)
    }
}
