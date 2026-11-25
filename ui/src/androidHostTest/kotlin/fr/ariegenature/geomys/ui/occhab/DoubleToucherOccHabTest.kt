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

// Relecture du 25 novembre 2026 : l'écran qui se ferme reste touchable tant que le suivant se dessine
// (près d'une seconde sur le Galaxy A12). Un second toucher de « Valider l'habitat » ajoutait l'habitat
// deux fois à la station ; un second toucher de la coche de la station, reçu après l'écriture, écrivait
// la station vierge de la suivante (point 0,0, sans habitat) dans la saisie, prête à partir.

package fr.ariegenature.geomys.ui.occhab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.model.HabitatSuggestion
import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.occhab.detailsSessionParDefaut
import fr.ariegenature.geomys.outils.ajouterOccHabEssai
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
class DoubleToucherOccHabTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services
    private val magasin = ViewModelStore()

    /** Le fil principal : les coroutines des écrans n'avancent que lorsque le test le pompe. Deux
     *  touchers à la suite arrivent donc avant que le premier ait rien écrit, comme sur le téléphone. */
    private val principal = StandardTestDispatcher()
    private val portee = CoroutineScope(principal)

    @BeforeTest
    fun preparer() {
        Dispatchers.setMain(principal)
        runBlocking { services.ajouterOccHabEssai() }
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

    /** Pompe le fil principal un moment : ce qu'un toucher aurait lancé a le temps de s'écrire. */
    private fun laisserFinir() {
        repeat(30) {
            principal.scheduler.advanceUntilIdle()
            Thread.sleep(10)
        }
    }

    /** Une session OccHab et sa nouvelle station ponctuelle, sans habitat. */
    private fun sessionAvecStation(): SessionOccHabViewModel {
        val session = vm { SessionOccHabViewModel() }
        session.session.demarrerSession(detailsSessionParDefaut(services.config))
        session.session.definirGeometrie("Point", 42.955, 1.6215, null)
        session.session.marquerStationNouvelle()
        return session
    }

    /** Les stations écrites, lues sur le fil principal que le test pompe : lues en le bloquant, elles
     *  pourraient attendre le verrou d'une écriture qui ne finit pas sans lui. */
    private fun stationsEcrites(saisieId: String): List<OccHabStation> {
        var lues: List<OccHabSaisie>? = null
        portee.launch { lues = services.occHabStore.charger() }
        attendre("la lecture des saisies OccHab") { lues != null }
        return lues.orEmpty().firstOrNull { it.id == saisieId }?.stations.orEmpty()
    }

    @Test
    fun un_second_valider_ne_double_pas_le_nouvel_habitat() {
        val session = sessionAvecStation()
        val ecran = vm { HabitatOccHabViewModel(services, session, habitatId = null) }
        val recus = mutableListOf<EvenementHabitatOccHab>()
        portee.launch { ecran.evenements.collect { recus += it } }
        ecran.choisirHabitat(HabitatSuggestion(9001, "Pelouses calcicoles mésophiles"))

        // Deux touchers à la suite ; le second retour est celui que la pile ignore (retourDepuis).
        ecran.valider()
        ecran.valider()
        assertEquals(1, session.session.station.habitats.size, "un seul habitat sur la station")
        attendre("les deux retours") { recus.count { it == EvenementHabitatOccHab.Retour } == 2 }
        assertEquals(1, stationsEcrites(session.session.saisieId).single().habitats.size, "un seul habitat écrit")

        // Encore un, une fois l'habitat écrit (l'écran se fermait) : c'est toujours le même habitat.
        ecran.valider()
        attendre("le troisième retour") { recus.count { it == EvenementHabitatOccHab.Retour } == 3 }
        assertEquals(1, session.session.station.habitats.size)
        assertEquals(1, stationsEcrites(session.session.saisieId).single().habitats.size)
    }

    @Test
    fun la_coche_de_la_station_ne_l_ecrit_et_n_ouvre_la_carte_suivante_qu_une_fois() {
        val session = sessionAvecStation()
        val ecran = vm { StationOccHabViewModel(services, session) }
        val recus = mutableListOf<EvenementStationOccHab>()
        portee.launch { ecran.evenements.collect { recus += it } }

        ecran.terminer()
        ecran.terminer()
        attendre("la carte suivante") { EvenementStationOccHab.CarteSuivante in recus }
        // Un troisième, reçu une fois la station écrite : la session porte déjà la station vierge de la
        // suivante.
        ecran.terminer()
        laisserFinir()

        assertEquals(1, recus.count { it == EvenementStationOccHab.CarteSuivante }, "une seule carte suivante")
        val ecrites = stationsEcrites(session.session.saisieId)
        assertEquals(1, ecrites.size, "ni double ni station vierge dans la saisie")
        assertTrue(ecrites.single().geometrieDefinie())
    }

    @Test
    fun une_station_sans_geometrie_ne_s_ecrit_jamais() {
        // La station vierge que la coche laisse à la suivante, vue par un écran neuf.
        val session = sessionAvecStation()
        session.session.nouvelleStation()
        val ecran = vm { StationOccHabViewModel(services, session) }
        val recus = mutableListOf<EvenementStationOccHab>()
        portee.launch { ecran.evenements.collect { recus += it } }

        ecran.terminer()
        laisserFinir()

        assertTrue(recus.isEmpty(), "ni message ni carte suivante : $recus")
        assertTrue(stationsEcrites(session.session.saisieId).isEmpty(), "aucune station (0,0) écrite")
    }
}
