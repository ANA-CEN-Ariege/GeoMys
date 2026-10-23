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

// Propre au portage (demande du 23 octobre 2026) : l'avertissement « Donnée exceptionnelle » des profils de
// taxons, à la saisie s'il y a du réseau, sinon dans « Mes saisies » et avant l'envoi.

package fr.ariegenature.geomys.ui.occtax

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.occtax.GeometrieFinale
import fr.ariegenature.geomys.occtax.OuvertureReleve
import fr.ariegenature.geomys.store.SuggestionTaxon
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class DonneesExceptionnellesTest {

    /** Le Machaon est signalé hors période ; les autres taxons ressemblent à leur profil. L'envoi,
     *  lui, n'aboutit pas (pas de connexion simulée). */
    private val serveur = MockEngine { requete ->
        val chemin = requete.url.encodedPath
        val entetes = headersOf("Content-Type", "application/json")
        when {
            chemin.startsWith("/api/taxhub/api/taxref/") ->
                respond("""{"cd_ref":${chemin.substringAfterLast('/')}}""", HttpStatusCode.OK, entetes)
            chemin == "/api/geo/altitude" -> respond("""{"altitude_min":385,"altitude_max":385}""", HttpStatusCode.OK, entetes)
            chemin == "/api/gn_profiles/check_observation" ->
                if ((requete.body as io.ktor.http.content.TextContent).text.contains("\"cd_ref\":54303")) {
                    respond("""{"errors":[{"value":"Le taxon n'a jamais été observé à cette periode"}]}""", HttpStatusCode.OK, entetes)
                } else {
                    respond("""{"errors":[]}""", HttpStatusCode.OK, entetes)
                }
            else -> respondError(HttpStatusCode.ServiceUnavailable)
        }
    }
    private val essai = ServicesEssai(serveur)
    private val services get() = essai.services
    private val magasin = ViewModelStore()

    @BeforeTest
    fun preparer() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.config.apply {
            urlServeur = "https://geonature.test"
            login = "alice"
            motDePasse = "secret"
            datasetsCacheJson = """[{"id":12,"nom":"JDD","moduleCodes":["OCCTAX"]}]"""
            datasetsCreablesOcctax = setOf(12)
            idDataset = "12"
        }
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

    /** La vérification passe par le réseau (Dispatchers.IO) : l'horloge du test avance (attente
     *  avant vérification) jusqu'à ce que [condition] soit remplie. */
    private fun TestScope.attendre(condition: () -> Boolean) {
        repeat(500) {
            testScheduler.advanceTimeBy(100)
            testScheduler.runCurrent()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("jamais arrivé")
    }

    private fun observation(id: String, cdNom: Int, espece: String) =
        Observation(id = id, espece = espece, cdNom = cdNom, taxon = Taxon.INSECTE, latitude = 42.965, longitude = 1.6075, idDatasetReleve = 12)

    @Test
    fun le_releve_signale_sous_l_espece_ce_que_le_serveur_trouve_inhabituel() = runTest {
        // Pas d'oiseau : l'ouverture automatique de la nidification lirait les ressources de l'appli.
        services.preferences.memoiserTaxon(Taxon.INSECTE)
        val session = vm { SessionSaisieViewModel(services) }
        val releve = vm { SaisieReleveViewModel(services, session, OuvertureReleve(geometrie = GeometrieFinale("Point", null, 42.965, 1.6075))) }
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        releve.choisirSuggestion(SuggestionTaxon("Flambé", 54210))
        attendre { releve.avertissements(releve.releve.especes.value[0], releve.profils.value).isNotEmpty() }
        assertEquals(listOf("Le taxon n'a jamais été observé à cette période"), releve.avertissements(releve.releve.especes.value[0], releve.profils.value))
        assertEquals(emptyList(), releve.avertissements(releve.releve.especes.value[1], releve.profils.value))
    }

    @Test
    fun mes_saisies_signale_puis_fait_confirmer_l_envoi() = runTest {
        val sortie = Sortie(observations = listOf(observation("m", 54303, "Machaon"), observation("f", 54210, "Flambé")))
        services.sortieStore.ajouter(sortie)
        val mesSaisies = vm { MesSaisiesViewModel(services) }
        mesSaisies.afficher()
        attendre { mesSaisies.toutes.isNotEmpty() && mesSaisies.exceptionnelles(mesSaisies.toutes.single(), mesSaisies.profils.value).isNotEmpty() }
        assertEquals(listOf("m"), mesSaisies.exceptionnelles(mesSaisies.toutes.single(), mesSaisies.profils.value).map { it.first.id })

        // L'envoi attend « Envoyer quand même ».
        mesSaisies.envoyer(mesSaisies.toutes.single())
        attendre { mesSaisies.confirmationExceptionnelles != null }
        assertNull(mesSaisies.information, "rien n'est parti")
        assertTrue(!mesSaisies.envoiEnCours)
        mesSaisies.envoyerQuandMeme()
        attendre { mesSaisies.information != null }
        assertNull(mesSaisies.confirmationExceptionnelles)
        assertNotNull(mesSaisies.information, "l'envoi a été tenté")
    }

    @Test
    fun une_saisie_ordinaire_part_sans_confirmation() = runTest {
        services.sortieStore.ajouter(Sortie(observations = listOf(observation("f", 54210, "Flambé"))))
        val mesSaisies = vm { MesSaisiesViewModel(services) }
        mesSaisies.afficher()
        attendre { mesSaisies.toutes.isNotEmpty() }
        mesSaisies.envoyer(mesSaisies.toutes.single())
        attendre { mesSaisies.information != null }
        assertNull(mesSaisies.confirmationExceptionnelles)
    }
}
