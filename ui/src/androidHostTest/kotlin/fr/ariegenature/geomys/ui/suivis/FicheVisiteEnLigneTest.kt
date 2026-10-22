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

// Propre au portage (demandes de l'utilisateur du 20 octobre) : les écrans lisent le cache de la synchro,
// qui ne charge pas l'historique ; la fiche d'une visite passée s'affiche depuis la ligne de son site, puis,
// si le serveur répond, se complète de ses observations, gardées en cache.

package fr.ariegenature.geomys.ui.suivis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.monitoring.AffichageSuivis
import fr.ariegenature.geomys.monitoring.ElementFiche
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.monitoring.filRacineSuivis
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class FicheVisiteEnLigneTest {

    /** Le serveur répond-il ? */
    private var reseau = true
    private val essai = ServicesEssai(
        MockEngine { requete ->
            val chemin = requete.url.encodedPath
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                !reseau -> respondError(HttpStatusCode.ServiceUnavailable)
                chemin.endsWith("/api/auth/login") -> respond("""{"access_token":"t","user":{"id_role":1}}""", HttpStatusCode.OK, json)
                chemin.endsWith("/api/monitorings/object/stom/visit/9") -> respond(VISITE, HttpStatusCode.OK, json)
                else -> respondError(HttpStatusCode.NotFound)
            }
        },
    )
    private val services get() = essai.services
    private val magasin = ViewModelStore()
    private val principal = StandardTestDispatcher()
    private val fil = filRacineSuivis("STOM") + FilSegment("site", 5, "Col de Port") + FilSegment("visit", 9, "25/08/2025")

    @AfterTest
    fun nettoyer() {
        magasin.clear()
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    private fun preparer() {
        Dispatchers.setMain(principal)
        services.config.urlServeur = "https://geonature.exemple.org"
        services.config.login = "alice"
        services.config.motDePasse = "secret"
        val cache = services.monitoringCache
        cache.setJson(cache.keySchema("stom"), SCHEMA)
        cache.setJson(
            cache.keyObjet("stom", "site", 5),
            """{"id":5,"properties":{"base_site_name":"Col de Port"},"geometry":{"type":"Point","coordinates":[1.5,42.9]},
               "children":{"visit":[{"id":9,"properties":{"visit_date_min":"2025-08-25"}}]}}""",
        )
    }

    private fun fiche(): FicheObjetViewModel =
        ViewModelProvider.create(magasin, viewModelFactory { initializer { FicheObjetViewModel(services, "stom", "visit", 9, fil) } })[FicheObjetViewModel::class]

    /** Pompe le fil principal jusqu'à [condition] : la fiche lit le cache et le serveur sur d'autres fils. */
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
    fun une_visite_passee_se_complete_depuis_le_serveur_et_reste_en_cache() {
        preparer()
        val vm = fiche()
        attendre("les observations du serveur") { vm.elements.any { it is ElementFiche.Serveur } }
        assertNull(vm.note)
        assertEquals(listOf(31), vm.elements.filterIsInstance<ElementFiche.Serveur>().map { it.id })
        val enCache = runBlocking { services.monitoring.lireObjet("stom", "visit", 9) }
        assertEquals(listOf(31), assertNotNull(enCache, "gardée en cache pour le hors-ligne").enfants.getValue("observation").map { it.id })
    }

    @Test
    fun hors_reseau_la_visite_garde_ses_proprietes_et_sa_note() {
        reseau = false
        preparer()
        val vm = fiche()
        attendre("la fiche du cache, sans le serveur") { vm.note == AffichageSuivis.ENFANTS_HORS_CACHE }
        assertNull(vm.erreur)
        assertEquals(emptyList(), vm.elements.filterIsInstance<ElementFiche.Serveur>())
        assertEquals(listOf("Date" to "25/08/2025"), vm.proprietes, "les propriétés reprises de la fiche du site, la date comme le web")
    }

    private companion object {
        val SCHEMA = """{
            "module":{"children_types":["site"]},
            "site":{"label":"Site","label_list":"Sites","geometry_type":"Point","children_types":["visit"]},
            "visit":{"label":"Visite","label_list":"Visites","genre":"F","children_types":["observation"],
              "display_properties":["visit_date_min"],
              "generic":{"visit_date_min":{"type_widget":"date","attribut_label":"Date"}}},
            "observation":{"label":"Observation","label_list":"Observations","genre":"F",
              "generic":{"cd_nom":{"type_widget":"taxonomy","attribut_label":"Espèce"}}}
        }"""
        val VISITE = """{"id":9,"properties":{"visit_date_min":"2025-08-25"},
            "children":{"observation":[{"id":31,"properties":{"cd_nom":"54321"}}]}}"""
    }
}
