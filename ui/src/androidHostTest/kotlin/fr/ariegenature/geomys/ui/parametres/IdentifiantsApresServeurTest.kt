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

// Propre au portage : identifiant, mot de passe et « Tester la connexion » des Paramètres masqués tant
// que le serveur est vide (demandes du 13 novembre 2026).

package fr.ariegenature.geomys.ui.parametres

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IdentifiantsApresServeurTest {

    private val essai = ServicesEssai()
    private val magasin = ViewModelStore()

    @AfterTest
    fun nettoyer() {
        magasin.clear()
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    private fun vm(): ParametresViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        return ViewModelProvider.create(
            magasin,
            viewModelFactory { initializer { ParametresViewModel(essai.services) } },
        )[ParametresViewModel::class]
    }

    @Test
    fun identifiant_mot_de_passe_et_test_attendent_le_serveur() {
        // Premier lancement : aucun serveur enregistré.
        val vm = vm()
        assertFalse(vm.champsConnexionVisibles)
        vm.changerUrl("   ")
        assertFalse(vm.champsConnexionVisibles, "des espaces ne font pas un serveur")
        vm.changerUrl("https://exemple.test")
        assertTrue(vm.champsConnexionVisibles)
        vm.changerUrl("")
        assertFalse(vm.champsConnexionVisibles, "serveur effacé : les champs disparaissent")
    }

    @Test
    fun un_serveur_deja_enregistre_montre_les_champs() {
        essai.services.config.urlServeur = "https://exemple.test"
        assertTrue(vm().champsConnexionVisibles)
    }
}
