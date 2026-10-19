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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/StationServeurCacheApresEnvoiTest.kt) :
// Robolectric remplacé par ServicesDeTest (store et cache des stations neufs à chaque test), la
// fonction envoyerSaisieOccHabVersGeoNature par la classe EnvoiOccHab, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * UNE STATION ENVOYÉE ENTRE DANS LE CACHE DES STATIONS SERVEUR (constat terrain 2026-10-17).
 *
 * Le cache n'était alimenté que par la synchro et par le chargement réseau de la carte. Deux
 * conséquences, toutes deux constatées sur le terrain : une station tout juste créée puis envoyée
 * n'y figurait pas (invisible et non aimantable tant qu'on n'avait pas rechargé), et une station
 * MODIFIÉE puis envoyée y restait dans sa version PRÉCÉDENTE — rouvrir OccHab réaffichait
 * l'ancienne géométrie, celle du cache.
 */
class StationServeurCacheApresEnvoiTest {

    private val t = ServicesDeTest()
    private val store get() = t.services.occHabStore
    private val config get() = t.services.config
    private val cache get() = t.services.stationsServeur
    private val envoi get() = t.services.envoiOccHab

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun station(id: String, lat: Double, idServeur: Int? = null) = OccHabStation(
        id = id, latitude = lat, longitude = 1.40, idDataset = 627, idStationServeur = idServeur,
        habitats = listOf(OccHabHabitat(cdHab = 1, habitatLabel = "Prairie")),
    )

    private suspend fun saisie() = store.charger().first { it.id == "sa" }
    private fun enCache(idServeur: Int) = cache.lire(627).firstOrNull { it.idStationServeur == idServeur }

    @Test
    fun une_station_creee_puis_envoyee_entre_dans_le_cache() = runTest {
        store.upsertStation("sa", station("A", lat = 42.90))
        val res = envoi.envoyerSaisieOccHabVersGeoNature(saisie(), config) { _, _ ->
            OccHabEnvoiResult(idStationServeur = 1234, nbHabitats = 1)
        }
        assertTrue(res.succes, "envoi réussi : ${res.message}")

        val mise = enCache(1234)
        assertNotNull(mise, "la station créée doit figurer parmi les stations serveur")
        assertEquals(42.90, mise.latitude, 0.0001)
        assertTrue(mise.origineServeur, "elle doit avoir la forme d'une station serveur")
        assertTrue(mise.envoyeGeoNature)
    }

    /** LE cas signalé : on modifie une station existante, on l'envoie, on rouvre OccHab. */
    @Test
    fun une_station_modifiee_puis_envoyee_remplace_sa_version_en_cache() = runTest {
        // État initial du cache : la station 900 telle qu'elle est sur le serveur.
        cache.remplacerTout(listOf(
            station("serveur-900", lat = 42.90, idServeur = 900).copy(origineServeur = true, envoyeGeoNature = true),
            station("serveur-901", lat = 43.50, idServeur = 901).copy(origineServeur = true, envoyeGeoNature = true),
        ))
        // L'utilisateur la reprend, déplace sa géométrie, et envoie.
        store.upsertStation("sa", station("A", lat = 42.99, idServeur = 900))
        val res = envoi.envoyerSaisieOccHabVersGeoNature(saisie(), config) { _, _ ->
            OccHabEnvoiResult(idStationServeur = 900, nbHabitats = 1)
        }
        assertTrue(res.succes, "envoi réussi : ${res.message}")

        assertEquals(42.99, enCache(900)!!.latitude, 0.0001, "le cache doit porter la version ENVOYÉE, pas la précédente")
        assertEquals(1, cache.lire(627).count { it.idStationServeur == 900 }, "une seule entrée pour cette station serveur")
        assertEquals(43.50, enCache(901)!!.latitude, 0.0001, "les autres stations du cache sont conservées")
    }

    /** La date du cache situe l'instantané du serveur DANS SON ENSEMBLE : une station envoyée ne dit
     *  rien des autres, elle ne doit donc pas faire passer tout le cache pour frais. */
    @Test
    fun un_envoi_ne_rafraichit_pas_la_date_du_cache() = runTest {
        cache.remplacerTout(listOf(station("serveur-900", lat = 42.90, idServeur = 900).copy(origineServeur = true)))
        val dateAvant = cache.dateChargement
        store.upsertStation("sa", station("A", lat = 42.99, idServeur = 900))
        envoi.envoyerSaisieOccHabVersGeoNature(saisie(), config) { _, _ ->
            OccHabEnvoiResult(idStationServeur = 900, nbHabitats = 1)
        }
        assertEquals(dateAvant, cache.dateChargement, "la date du dernier chargement serveur reste la même")
    }

    /** CONTRE-ÉPREUVE : un envoi qui échoue ne doit rien écrire dans le cache. */
    @Test
    fun un_envoi_en_echec_ne_touche_pas_le_cache() = runTest {
        cache.remplacerTout(listOf(station("serveur-900", lat = 42.90, idServeur = 900).copy(origineServeur = true)))
        store.upsertStation("sa", station("A", lat = 42.99, idServeur = 900))
        envoi.envoyerSaisieOccHabVersGeoNature(saisie(), config) { _, _ ->
            throw GNErreur.EnvoiEchoue(400, "refus")
        }
        assertEquals(42.90, enCache(900)!!.latitude, 0.0001, "le cache garde la version du serveur")
    }

    // ── Une station appartient à UNE saisie ─────────────────────────────────

    /**
     * La carte affiche les stations de TOUTES les saisies : on peut donc reprendre une station née
     * dans une autre. Elle doit rester dans la sienne — sinon « Mes stations » gagnait un relevé de
     * plus à chaque modification (constat terrain 2026-10-18 : créer une station, la resélectionner,
     * valider sans rien changer ⇒ deux relevés).
     */
    @Test
    fun modifier_une_station_depuis_une_autre_saisie_ne_la_duplique_pas() = runTest {
        store.upsertStation("saisie-1", station("A", lat = 42.90))
        // Nouvelle session (on est ressorti d'OccHab et revenu) : la saisie courante a changé.
        store.upsertStation("saisie-2", station("A", lat = 42.95))

        val saisies = store.charger().filter { s -> s.stations.any { it.id == "A" } }
        assertEquals(1, saisies.size, "la station ne doit exister que dans UNE saisie")
        assertEquals("saisie-1", saisies.first().id, "et c'est celle où elle est née")
        assertEquals(42.95, saisies.first().stations.first { it.id == "A" }.latitude, 0.0001, "avec la modification appliquée")
    }

    /** CONTRE-ÉPREUVE : deux stations DIFFÉRENTES dans deux saisies restent bien séparées. */
    @Test
    fun deux_stations_distinctes_vivent_dans_leurs_saisies_respectives() = runTest {
        store.upsertStation("saisie-1", station("A", lat = 42.90))
        store.upsertStation("saisie-2", station("B", lat = 43.10))
        assertEquals(1, store.stationsDeSaisie("saisie-1").size)
        assertEquals(1, store.stationsDeSaisie("saisie-2").size)
    }
}
