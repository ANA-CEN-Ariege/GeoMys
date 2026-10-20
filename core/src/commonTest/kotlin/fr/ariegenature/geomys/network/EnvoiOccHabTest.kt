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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/EnvoiOccHabTest.kt) :
// Robolectric remplacé par ServicesDeTest (store neuf à chaque test), la fonction
// envoyerSaisieOccHabVersGeoNature par la classe EnvoiOccHab, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Envoi d'une SAISIE OccHab ([EnvoiOccHab.envoyerSaisieOccHabVersGeoNature]) : envoi partiel SANS
 * PERTE + anti-doublon. Le POST réseau est injecté par un bouchon (paramètre `envoyer`) pour couvrir
 * les cas de fiabilité identifiés à l'audit : 5xx après enregistrement et échec de persistance →
 * statut INCERTAIN (le ré-envoi vérifiera l'existence par UUID), 4xx → échec net.
 */
class EnvoiOccHabTest {

    private val t = ServicesDeTest()
    private val store get() = t.services.occHabStore
    private val config get() = t.services.config
    private val envoi get() = t.services.envoiOccHab

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private suspend fun preparer() {
        store.upsertStation("sa", station("A"))
        store.upsertStation("sa", station("B"))
    }

    private fun station(id: String) = OccHabStation(
        id = id, habitats = listOf(OccHabHabitat(cdHab = 1, habitatLabel = "Habitat $id")),
    )

    private suspend fun saisie() = store.charger().first { it.id == "sa" }
    private suspend fun stationB() = store.stationsDeSaisie("sa").first { it.id == "B" }

    @Test
    fun creation_sans_id_serveur_reste_INCERTAINE_et_ne_duplique_pas() = runTest {
        preparer()
        // 2xx mais id_station illisible (corps tronqué, id sous un autre nom), et la récupération par
        // uuid a elle aussi échoué. Marquer « envoyée » ici poserait envoiIncertain = false avec
        // idStationServeur = null : la station n'aurait plus aucune identité serveur, et sa réédition
        // — qui repasse par une CRÉATION faute d'id — en produirait une SECONDE sur GeoNature, le
        // module OccHab n'ayant aucune contrainte d'unicité (R1-M2).
        val res = envoi.envoyerSaisieOccHabVersGeoNature(saisie(), config) { _, _ ->
            OccHabEnvoiResult(idStationServeur = null, nbHabitats = 1)
        }

        assertFalse(res.succes, "un envoi sans identifiant serveur n'est pas un succès")
        store.stationsDeSaisie("sa").forEach { st ->
            assertFalse(st.envoyeGeoNature, "la station ne doit pas être marquée envoyée sans id serveur")
            assertTrue(
                st.envoiIncertain,
                "elle doit rester INCERTAINE pour que le prochain envoi la retrouve par son uuid " +
                    "au lieu d'en créer une seconde",
            )
        }
    }

    @Test
    fun mise_a_jour_sans_id_renvoye_reste_un_succes() = runTest {
        preparer()
        // Contre-épreuve : en MISE À JOUR l'id serveur est déjà connu, le POST /stations/<id>/ est
        // idempotent — l'absence d'id dans la réponse n'a aucune conséquence.
        store.upsertStation("sa", station("A").copy(idStationServeur = 900))
        store.upsertStation("sa", station("B").copy(idStationServeur = 901))
        val res = envoi.envoyerSaisieOccHabVersGeoNature(saisie(), config) { _, _ ->
            OccHabEnvoiResult(idStationServeur = null, nbHabitats = 1)
        }

        assertTrue(res.succes, "une mise à jour sans id renvoyé reste un succès : ${res.message}")
        store.stationsDeSaisie("sa").forEach { st ->
            assertTrue(st.envoyeGeoNature)
            assertEquals(true, (st.idStationServeur ?: 0) > 0, "l'id serveur connu doit être conservé")
        }
    }

    @Test
    fun envoi_complet_marque_la_saisie_envoyee() = runTest {
        preparer()
        val res = envoi.envoyerSaisieOccHabVersGeoNature(saisie(), config) { _, _ ->
            OccHabEnvoiResult(idStationServeur = 100, nbHabitats = 1)
        }
        assertTrue(res.succes)
        assertTrue(saisie().envoyeGeoNature)
        assertTrue(store.stationsDeSaisie("sa").all { it.envoyeGeoNature })
    }

    @Test
    fun echec_4xx_de_la_2e_station_saisie_reste_a_envoyer_sans_perte() = runTest {
        preparer()
        val res = envoi.envoyerSaisieOccHabVersGeoNature(saisie(), config) { st, _ ->
            if (st.id == "B") throw GNErreur.EnvoiEchoue(400, "rejet")
            OccHabEnvoiResult(idStationServeur = 100, nbHabitats = 1)
        }
        assertFalse(res.succes)
        assertFalse(saisie().envoyeGeoNature, "saisie pas envoyée (partiel)")
        assertTrue(store.stationsDeSaisie("sa").first { it.id == "A" }.envoyeGeoNature, "A conservée envoyée")
        assertFalse(stationB().envoyeGeoNature)
        assertFalse(stationB().envoiIncertain, "4xx = échec NET, pas incertain")
    }

    @Test
    fun erreur_5xx_apres_commit_serveur_passe_la_station_en_incertain() = runTest {
        preparer()
        envoi.envoyerSaisieOccHabVersGeoNature(saisie(), config) { st, _ ->
            if (st.id == "B") throw GNErreur.EnvoiEchoue(500, "boom")
            OccHabEnvoiResult(idStationServeur = 100, nbHabitats = 1)
        }
        assertFalse(stationB().envoyeGeoNature)
        assertTrue(stationB().envoiIncertain, "5xx (peut-être créée) = incertain → ré-envoi vérifiera par UUID")
    }

    @Test
    fun envoi_incertain_reseau_propage_l_etat() = runTest {
        preparer()
        envoi.envoyerSaisieOccHabVersGeoNature(saisie(), config) { st, _ ->
            if (st.id == "B") throw GNErreur.EnvoiIncertain("réseau coupé")
            OccHabEnvoiResult(idStationServeur = 100, nbHabitats = 1)
        }
        assertTrue(stationB().envoiIncertain)
    }

    @Test
    fun re_envoi_ne_repousse_que_les_stations_restantes() = runTest {
        preparer()
        // A déjà envoyée, B en attente : un ré-envoi ne doit POSTer que B (anti-doublon de A).
        store.marquerStationEnvoyee("sa", "A", 100)
        val appelees = mutableListOf<String>()
        val res = envoi.envoyerSaisieOccHabVersGeoNature(saisie(), config) { st, _ ->
            appelees.add(st.id)
            OccHabEnvoiResult(idStationServeur = 200, nbHabitats = 1)
        }
        assertEquals(listOf("B"), appelees, "seule B est (re)postée")
        assertTrue(res.succes)
        assertTrue(saisie().envoyeGeoNature)
    }

    /** Propre au portage (décision de l'utilisateur du 20 octobre) : les altitudes du MNT ne sont plus
     *  demandées au dessin, mais à l'envoi, pour la seule station qui n'en a aucune ; reportées dans la
     *  saisie. Une station aux altitudes saisies à la main les garde. */
    @Test
    fun les_altitudes_du_mnt_sont_demandees_a_l_envoi() = runTest {
        store.upsertStation("sa", station("A"))
        store.upsertStation("sa", station("B").copy(altitudeMin = 800, altitudeMax = 820))
        val demandees = mutableListOf<String>()
        val envoiMnt = EnvoiOccHab(t.services.occHabUpload, store, t.services.stationsServeur) { st ->
            demandees.add(st.id)
            1200 to 1350
        }
        val parties = mutableMapOf<String, Pair<Int?, Int?>>()
        val res = envoiMnt.envoyerSaisieOccHabVersGeoNature(saisie(), config) { st, _ ->
            parties[st.id] = st.altitudeMin to st.altitudeMax
            OccHabEnvoiResult(idStationServeur = 300 + parties.size, nbHabitats = 1)
        }
        assertTrue(res.succes)
        assertEquals(listOf("A"), demandees, "seule la station sans altitude les demande")
        assertEquals<Map<String, Pair<Int?, Int?>>>(mapOf("A" to (1200 to 1350), "B" to (800 to 820)), parties)
        assertEquals(1200, store.stationsDeSaisie("sa").first { it.id == "A" }.altitudeMin, "reportées dans la saisie")
    }

    /** Propre au portage : les voisines d'une autre saisie que la topologie partagée a modifiées
     *  partent seules, avec l'envoi de la saisie courante (demande de l'utilisateur du 20 octobre). */
    @Test
    fun seules_les_stations_designees_partent() = runTest {
        preparer()
        val appelees = mutableListOf<String>()
        val res = envoi.envoyerSaisieOccHabVersGeoNature(saisie(), config, seulement = setOf("B")) { st, _ ->
            appelees.add(st.id)
            OccHabEnvoiResult(idStationServeur = 400, nbHabitats = 1)
        }
        assertTrue(res.succes, res.message)
        assertEquals(listOf("B"), appelees)
        assertFalse(saisie().envoyeGeoNature, "A reste à envoyer")
    }
}
