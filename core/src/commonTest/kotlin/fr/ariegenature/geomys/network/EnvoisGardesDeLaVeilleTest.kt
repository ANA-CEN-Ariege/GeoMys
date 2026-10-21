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

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import fr.ariegenature.geomys.store.NomValeur
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.test.runTest

/** Les trois envois sont gardés de la mise en veille ([fr.ariegenature.geomys.plateforme.ExecutionLongue.pendant],
 *  une tâche d'arrière-plan sous iOS, comme TacheArrierePlan dans GeoMys-IOS) : sans elle, iOS
 *  suspendait l'appli quelques secondes après le verrouillage, et l'envoi restait partiel ou
 *  incertain. Toutes leurs requêtes partent sous cette garde. */
class EnvoisGardesDeLaVeilleTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config
    private val verrou = SynchronizedObject()

    /** Pour chaque requête reçue : partie sous la garde ou non. */
    private val sousGarde = mutableListOf<Boolean>()

    @BeforeTest
    fun setup() {
        t.services.nomenclatures.setAll(TYPES_NOMENCLATURE.associateWith { listOf(NomValeur(1, "x")) })
        config.apply {
            urlServeur = serveur.base
            login = "alice"; motDePasse = "pwd"; idDataset = "12"
        }
        // Un serveur indisponible : les envois échouent, mais leurs requêtes partent.
        serveur.routerRequetes { requete ->
            synchronized(verrou) { sousGarde += t.protege }
            if (requete.url.encodedPath.startsWith("/api/auth/login")) json(LOGIN) else statut(503)
        }
    }

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun requetesToutesSousGarde() {
        val vues = synchronized(verrou) { sousGarde.toList() }
        assertTrue(vues.isNotEmpty(), "l'envoi doit avoir interrogé le serveur")
        assertTrue(vues.all { it }, "toutes les requêtes doivent partir sous la garde : $vues")
    }

    @Test
    fun l_envoi_occtax_est_garde_de_la_veille() = runTest {
        val store = t.services.sortieStore
        store.ajouter(
            Sortie(
                id = "s1",
                observations = listOf(
                    Observation(
                        id = "o1", espece = "Merle", cdNom = 4001, latitude = 42.9, longitude = 1.4,
                        nombre = 1, releveId = "grp1", date = 1_700_000_000_000L,
                    ),
                ),
            ),
        )

        t.services.envoiSortie.envoyerSortieVersGeoNature(store.charger().first(), config)

        assertEquals(listOf("Envoi OccTax"), t.protections)
        requetesToutesSousGarde()
    }

    @Test
    fun l_envoi_des_suivis_est_garde_de_la_veille() = runTest {
        t.services.outbox.ajouter(
            SaisieEnAttente(
                uuid = "v1", moduleCode = "STOC", objectType = "visite",
                parentObjectType = "site", parentIdServeur = 5, parentIdField = "id_base_site",
                valeursJson = """{"comments":"essai"}""",
            ),
        )

        t.services.outboxEnvoi.envoyerTout(config) { _, _, _ -> }

        assertEquals(listOf("Envoi des Suivis"), t.protections)
        requetesToutesSousGarde()
    }

    @Test
    fun l_envoi_occhab_est_garde_de_la_veille() = runTest {
        val store = t.services.occHabStore
        store.upsertStation("sa", OccHabStation(id = "A", habitats = listOf(OccHabHabitat(cdHab = 1, habitatLabel = "Pelouse"))))
        var stationEnvoyeeSousGarde = false

        t.services.envoiOccHab.envoyerSaisieOccHabVersGeoNature(store.charger().first { it.id == "sa" }, config) { _, _ ->
            stationEnvoyeeSousGarde = t.protege
            OccHabEnvoiResult(idStationServeur = 100, nbHabitats = 1)
        }

        assertEquals(listOf("Envoi OccHab"), t.protections)
        assertTrue(stationEnvoyeeSousGarde, "la station doit partir sous la garde")
    }

    private companion object {
        const val LOGIN = """{"access_token":"t","user":{"id_role":1}}"""
        val TYPES_NOMENCLATURE = listOf(
            "METH_OBS", "STATUT_OBS", "SEXE", "STADE_VIE", "STATUT_BIO", "ETA_BIO",
            "PREUVE_EXIST", "OBJ_DENBR", "TYP_DENBR", "OCC_COMPORTEMENT", "METH_DETERMIN", "TYPE_MEDIA",
        )
    }
}
