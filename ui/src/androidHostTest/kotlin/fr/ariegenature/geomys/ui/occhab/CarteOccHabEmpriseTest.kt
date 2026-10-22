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

// Garde de GeoMys-Android @ cdd5196 qui lisait le texte de StationsServeurOverlay faute de pouvoir actionner
// la carte (OccHabEmpriseStableTest, premier cas : le chargement des stations serveur ne touche jamais à
// l'emprise). Ici, le chargement est actionné — à l'ouverture d'une saisie reprise, et par « Ajouter une
// station » — et l'on vérifie qu'il ne commande aucun cadrage. Le second cas (pas de repli sur les stations
// de session) est dans CarteOccHabTest, côté socle.

package fr.ariegenature.geomys.ui.occhab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.occhab.detailsSessionParDefaut
import fr.ariegenature.geomys.outils.ajouterOccHabEssai
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class)
class CarteOccHabEmpriseTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services
    private val magasin = ViewModelStore()

    @BeforeTest
    fun preparer() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { services.ajouterOccHabEssai() }
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

    /** Les événements de [carte] jusqu'au message du chargement des stations serveur (hors ligne :
     *  repli sur le cache, le réseau des tests échouant toujours). */
    private fun evenementsJusquAuChargement(carte: CarteOccHabViewModel): List<EvenementCarteOccHab> = runBlocking {
        withTimeout(10_000) {
            val recus = mutableListOf<EvenementCarteOccHab>()
            carte.evenements.first { e ->
                recus += e
                e is EvenementCarteOccHab.Message && "station(s)" in e.texte
            }
            recus
        }
    }

    @Test
    fun le_chargement_des_stations_serveur_a_l_ouverture_ne_touche_pas_a_l_emprise() {
        val session = vm { SessionOccHabViewModel() }
        val saisie = runBlocking { services.occHabStore.charger() }.first { it.id == "essai-occhab-a-envoyer" }
        // Saisie reprise au JDD connu : la carte charge ses stations serveur dès l'ouverture.
        session.session.reprendreSaisie(saisie)
        val carte = vm { CarteOccHabViewModel(services, session) }
        carte.ouverture()

        val evenements = evenementsJusquAuChargement(carte)
        assertEquals(
            emptyList(),
            evenements.filterIsInstance<EvenementCarteOccHab.Camera>(),
            "les stations serveur se dessinent sans recadrer la carte : l'emprise ne change que sur un geste",
        )
        assertTrue(carte.stationsViolettes.isNotEmpty(), "les stations du cache sont dessinées")
    }

    @Test
    fun ajouter_une_station_charge_les_stations_du_jdd_sans_toucher_a_l_emprise() {
        val session = vm { SessionOccHabViewModel() }
        session.session.demarrerSession(detailsSessionParDefaut(services.config))
        val carte = vm { CarteOccHabViewModel(services, session) }
        carte.ouverture()

        // « Ajouter une station » : le jeu de données choisi dans les informations obligatoires
        // déclenche le chargement — c'est par là que l'emprise sautait sur l'ensemble des stations.
        carte.ajouterStation()
        val details = requireNotNull(carte.details) { "les informations obligatoires s'ouvrent" }
        details.choisirDataset(details.datasets.indexOfFirst { it.first == 21 })
        details.choisirObservateur(0)
        carte.validerDetails(details.saisies())

        val evenements = evenementsJusquAuChargement(carte)
        assertEquals(emptyList(), evenements.filterIsInstance<EvenementCarteOccHab.Camera>())
        assertTrue(carte.enCreation, "la carte passe en dessin")
        assertTrue(
            carte.stationsViolettes.any { it.idStationServeur == 504 },
            "les stations des autres jeux de données restent affichées",
        )
    }
}
