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

// Le comportement d'ExplorerFragment de GeoMys-Android @ cdd5196 (chargerObservations, planifierChargement,
// toggleFiltre) que porte ExplorerViewModel. GeoMys-Android n'a pas de test de cet écran.

package fr.ariegenature.geomys.ui.explorer

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.explorer.Explorer
import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.ObsExplorer
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerViewModelTest {

    private val essai = ServicesEssai()
    private val magasin = ViewModelStore()

    /** Une zone de 2,4 km sur 2,2 km près de Foix, et une de 160 km de large. */
    private val zone = Emprise(sud = 42.95, ouest = 1.60, nord = 42.97, est = 1.63)
    private val tropLarge = Emprise(sud = 42.0, ouest = 0.0, nord = 43.5, est = 2.0)

    private fun obs(taxon: Taxon, lat: Double, lon: Double, date: String = "2026-05-12") =
        ObsExplorer(nomCite = "x", nomVern = "", nomSci = "x", latitude = lat, longitude = lon, date = date, nombre = 1, taxon = taxon)

    private val trois = listOf(
        obs(Taxon.OISEAU, 42.9601, 1.6101),
        obs(Taxon.PLANTE, 42.96012, 1.61012),
        obs(Taxon.OISEAU, 42.9650, 1.6200),
    )

    @AfterTest
    fun nettoyer() {
        magasin.clear()
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    private fun vm(
        principal: TestDispatcher,
        configure: Boolean = true,
        source: suspend (Emprise) -> List<ObsExplorer>,
    ): ExplorerViewModel {
        Dispatchers.setMain(principal)
        return ViewModelProvider.create(
            magasin,
            viewModelFactory { initializer { ExplorerViewModel(essai.services, source, configure) } },
        )[ExplorerViewModel::class]
    }

    @Test
    fun une_zone_trop_large_n_est_pas_demandee_et_vide_la_carte() {
        val principal = StandardTestDispatcher()
        var appels = 0
        val vm = vm(principal) { appels++; trois }
        vm.charger(zone)
        principal.scheduler.advanceUntilIdle()
        assertEquals(3, vm.observations.size)

        vm.charger(tropLarge)
        principal.scheduler.advanceUntilIdle()
        assertEquals(1, appels)
        assertTrue(vm.observations.isEmpty())
        assertFalse(vm.compteurVisible)
        assertEquals(Explorer.ZONE_TROP_LARGE, vm.message)
        assertEquals("Zoomez pour charger les observations (zone > 100 km)", vm.message)
    }

    @Test
    fun les_observations_recues_font_les_marqueurs_et_le_compteur() {
        val principal = StandardTestDispatcher()
        val vm = vm(principal) { trois }
        vm.charger(zone)
        principal.scheduler.runCurrent()
        assertFalse(vm.chargement)
        // Les deux premières sont dans la même cellule de 0,001°.
        assertEquals(listOf(2, 1), vm.groupes.map { it.observations.size })
        assertEquals(Taxon.OISEAU, vm.groupes.first().premiere.taxon)
        assertTrue(vm.compteurVisible)
        assertNull(vm.message)
        // area_observation_duration par défaut : 365 jours.
        assertEquals("3 obs. (12 mois)", vm.compteur)
    }

    @Test
    fun une_erreur_garde_les_observations_affichees() {
        val principal = StandardTestDispatcher()
        var echec = false
        val vm = vm(principal) { if (echec) error("serveur injoignable") else trois }
        vm.charger(zone)
        principal.scheduler.advanceUntilIdle()
        echec = true
        vm.charger(zone)
        principal.scheduler.advanceUntilIdle()
        assertEquals(3, vm.observations.size)
        assertEquals("Erreur : serveur injoignable", vm.message)
        assertFalse(vm.chargement)
    }

    @Test
    fun une_requete_abandonnee_ne_coupe_pas_l_indicateur_de_la_suivante() {
        val principal = StandardTestDispatcher()
        val reponses = mutableListOf<CompletableDeferred<List<ObsExplorer>>>()
        val vm = vm(principal) { CompletableDeferred<List<ObsExplorer>>().also { reponses += it }.await() }
        vm.charger(zone)
        principal.scheduler.advanceUntilIdle()
        assertTrue(vm.chargement)
        vm.charger(zone)
        principal.scheduler.advanceUntilIdle()
        // La première est annulée ; la seconde attend toujours sa réponse.
        assertEquals(2, reponses.size)
        assertTrue(vm.chargement)
        reponses[1].complete(trois)
        principal.scheduler.advanceUntilIdle()
        assertFalse(vm.chargement)
        assertEquals(3, vm.observations.size)
    }

    /** Le répartiteur principal de l'appli est immédiat : une réponse sans attente arrive avant que
     *  `launch` rende la main (l'essai `explorer-essai` plantait). */
    @Test
    fun une_reponse_immediate_termine_le_chargement() {
        val vm = vm(UnconfinedTestDispatcher()) { trois }
        vm.charger(zone)
        assertFalse(vm.chargement)
        assertEquals(3, vm.observations.size)
    }

    @Test
    fun la_carte_qui_bouge_n_est_chargee_qu_apres_le_delai() {
        val principal = StandardTestDispatcher()
        val demandes = mutableListOf<Emprise>()
        val vm = vm(principal) { demandes += it; trois }
        val autre = zone.copy(ouest = 1.61, est = 1.64)
        vm.carteBougee(zone)
        principal.scheduler.advanceTimeBy(500)
        vm.carteBougee(autre)
        principal.scheduler.advanceTimeBy(Explorer.DELAI_MS - 1)
        principal.scheduler.runCurrent()
        assertTrue(demandes.isEmpty())
        principal.scheduler.advanceTimeBy(2)
        principal.scheduler.runCurrent()
        // Seul le dernier cadrage est demandé.
        assertEquals(listOf(autre), demandes)
    }

    @Test
    fun le_filtre_garde_un_groupe_et_se_retire_au_second_toucher() {
        val principal = StandardTestDispatcher()
        val vm = vm(principal) { trois }
        vm.charger(zone)
        principal.scheduler.advanceUntilIdle()
        vm.basculerFiltre(Taxon.PLANTE)
        assertEquals(Taxon.PLANTE, vm.groupeFiltre)
        assertEquals(listOf(Taxon.PLANTE), vm.groupes.flatMap { g -> g.observations.map { it.taxon } })
        assertEquals("1 obs. (12 mois)", vm.compteur)
        vm.basculerFiltre(Taxon.OISEAU)
        assertEquals(Taxon.OISEAU, vm.groupeFiltre)
        vm.basculerFiltre(Taxon.OISEAU)
        assertNull(vm.groupeFiltre)
        assertEquals(3, vm.groupes.sumOf { it.observations.size })
    }

    @Test
    fun sans_serveur_configure_rien_n_est_demande() {
        val principal = StandardTestDispatcher()
        var appels = 0
        val vm = vm(principal, configure = false) { appels++; trois }
        assertEquals(Explorer.NON_CONFIGURE, vm.message)
        vm.charger(zone)
        vm.carteBougee(zone)
        principal.scheduler.advanceUntilIdle()
        assertEquals(0, appels)
        assertEquals("Configurez GeoNature pour explorer les observations", vm.message)
    }

    @Test
    fun sans_position_connue_la_carte_part_du_centre_de_la_france() {
        val vm = vm(StandardTestDispatcher()) { trois }
        assertEquals(LatLon(46.5, 2.5) to 6.0, vm.depart)
        assertFalse(vm.centreeSurPosition)
        assertNull(vm.positionPourCentrer())
    }

    @Test
    fun la_liste_d_une_cellule_s_ouvre_et_se_ferme() {
        val principal = StandardTestDispatcher()
        val vm = vm(principal) { trois }
        vm.charger(zone)
        principal.scheduler.advanceUntilIdle()
        val cellule = vm.groupes.first()
        vm.ouvrir(cellule)
        assertEquals(cellule, vm.groupeOuvert)
        vm.fermerListe()
        assertNull(vm.groupeOuvert)
    }
}
