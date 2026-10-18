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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/StationsServeurCacheTest.kt) :
// Robolectric remplacé par ServicesDeTest, java.io par okio, les fonctions de purge par
// CachesSynchronises et InvalidationCaches, JUnit par kotlin.test.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.util.maintenantMillis
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/** Cache hors-ligne des stations serveur OccHab (StationsServeurCache) : rempli par la synchro
 *  (tous JDD) et au fil de l'eau par la carte (un JDD), lu par JDD en repli hors-ligne, purgé
 *  par « Vider le cache » et au changement d'identité serveur. */
class StationsServeurCacheTest {

    private val t = ServicesDeTest()
    private val cache get() = t.services.stationsServeur
    private val systeme = FileSystem.SYSTEM

    @AfterTest
    fun nettoyer() = t.nettoyer()

    @BeforeTest
    fun setup() {
        cache.vider()
    }

    private fun station(
        idServeur: Int,
        idJdd: Int,
        nom: String? = null,
        anaEval: String? = null,
        habitats: List<OccHabHabitat> = emptyList(),
    ) = OccHabStation(
        idStationServeur = idServeur,
        idDataset = idJdd,
        stationName = nom,
        anaEvalJson = anaEval,
        habitats = habitats,
        origineServeur = true,
        latitude = 42.9, longitude = 1.6,
    )

    // ── Lecture par JDD ──

    @Test
    fun lire_filtre_par_jeu_de_donnees() {
        cache.remplacerTout(listOf(station(1, 10), station(2, 10), station(3, 20)))
        assertEquals(listOf(1, 2), cache.lire(10).map { it.idStationServeur })
        assertEquals(listOf(3), cache.lire(20).map { it.idStationServeur })
        assertTrue(cache.lire(30).isEmpty())
    }

    @Test
    fun cache_jamais_ecrit_lit_vide_et_sans_date() {
        assertTrue(cache.lire(10).isEmpty())
        assertNull(cache.dateChargement)
        assertEquals(0, cache.count)
    }

    @Test
    fun count_total_tous_jdd_confondus() {
        // Compteur de l'écran Paramètres (boîte « Chargement des données »).
        cache.remplacerTout(listOf(station(1, 10), station(2, 10), station(3, 20)))
        assertEquals(3, cache.count)
        cache.vider()
        assertEquals(0, cache.count)
    }

    // ── Remplacements ──

    @Test
    fun remplacer_jdd_conserve_les_autres_jdd() {
        // Synchro : tous JDD. Puis la carte recharge le JDD 10 seul (une station retirée côté
        // serveur, une nouvelle) : le JDD 20 (non rechargé) doit survivre.
        cache.remplacerTout(listOf(station(1, 10), station(2, 10), station(3, 20)))
        cache.remplacerJdd(10, listOf(station(2, 10), station(4, 10)))
        assertEquals(listOf(2, 4), cache.lire(10).map { it.idStationServeur })
        assertEquals(listOf(3), cache.lire(20).map { it.idStationServeur })
    }

    @Test
    fun remplacer_tout_vide_ecrase_reellement() {
        // Un chargement RÉUSSI qui renvoie 0 station est un vrai état (stations supprimées côté
        // serveur) : le cache doit refléter le vide — l'appelant ne remplace jamais sur échec.
        cache.remplacerTout(listOf(station(1, 10)))
        cache.remplacerTout(emptyList())
        assertTrue(cache.lire(10).isEmpty())
        assertNotNull(cache.dateChargement) // le cache existe, il est juste vide
    }

    @Test
    fun remplacer_met_a_jour_la_date_de_chargement() {
        val avant = maintenantMillis()
        cache.remplacerTout(listOf(station(1, 10)))
        val date = cache.dateChargement
        assertNotNull(date)
        assertTrue(date >= avant)
    }

    // ── Durabilité (round-trip disque : ce que la RÉÉDITION doit préserver survit) ──

    @Test
    fun round_trip_disque_preserve_les_champs_de_reedition() {
        val h = OccHabHabitat(cdHab = 1234, nomCite = "Forêts de Fagus", idHabitatServeur = 77)
        cache.remplacerTout(listOf(
            station(5, 10, nom = "Tourbière", anaEval = "{\"etat\":\"bon\"}", habitats = listOf(h)),
        ))
        // Relecture DEPUIS LE DISQUE (l'état mémoire est jeté, comme après un kill du process).
        cache.resetPourTests()
        val relue = cache.lire(10).single()
        assertEquals(5, relue.idStationServeur)
        assertEquals("Tourbière", relue.stationName)
        assertEquals("{\"etat\":\"bon\"}", relue.anaEvalJson)
        assertTrue(relue.origineServeur)
        assertEquals(1234, relue.habitats.single().cdHab)
        assertEquals(77, relue.habitats.single().idHabitatServeur)
        assertEquals("Forêts de Fagus", relue.habitats.single().nomCite)
    }

    private fun fichierCache(): Path = t.dossier / "fichiers" / "stations_serveur_occhab_v1.json"

    private fun ecrireFichier(texte: String) {
        systeme.write(fichierCache()) { writeUtf8(texte) }
    }

    @Test
    fun fichier_corrompu_lit_vide_sans_crash() {
        cache.remplacerTout(listOf(station(1, 10)))
        ecrireFichier("{ pas du json")
        cache.resetPourTests()
        assertTrue(cache.lire(10).isEmpty())
        assertNull(cache.dateChargement)
    }

    // ── Version du format (audit 2026-09-14, R3-C1 / R2-M1) ──

    @Test
    fun cache_ecrit_par_une_version_anterieure_est_ignore_et_supprime() {
        // Fichier tel que l'écrivaient les v1.3.22 à v1.4.1 : pas de champ versionFormat, et un
        // polygone à trou déjà APLATI par l'ancien parseur (geometryTrousJson absent). Le relire
        // afficherait un polygone plein, et l'envoi de la copie modifiée SUPPRIMERAIT le trou
        // côté GeoNature — la régression corrigée en v1.4.0, rouverte par le chemin hors-ligne.
        cache.remplacerTout(emptyList()) // crée le dossier
        ecrireFichier(
            """{"dateChargement":1757000000000,"stations":[""" +
                """{"id":"x","uuidStation":"u","idStationServeur":1,"idDataset":10,""" +
                """"geometryType":"Polygon","latitude":42.9,"longitude":1.6,""" +
                """"geometryCoordsJson":"[[1.6,42.9],[1.7,42.9],[1.7,43.0]]",""" +
                """"habitats":[],"origineServeur":true}]}"""
        )
        cache.resetPourTests()
        assertTrue(cache.lire(10).isEmpty(), "un cache d'un autre format ne doit JAMAIS être servi")
        assertNull(cache.dateChargement)
        assertTrue(!systeme.exists(fichierCache()), "le fichier périmé doit être supprimé, pas relu à chaque ouverture")
    }

    @Test
    fun cache_ecrit_par_la_version_courante_est_relu() {
        // Contre-épreuve de la précédente : le rejet doit porter sur la VERSION, pas tout jeter.
        cache.remplacerTout(listOf(station(1, 10)))
        cache.resetPourTests()
        assertEquals(listOf(1), cache.lire(10).map { it.idStationServeur })
        assertNotNull(cache.dateChargement)
    }

    @Test
    fun station_a_champs_nuls_est_normalisee_a_la_relecture() {
        // Un JSON portant des `null` EXPLICITES sur des champs non-nullables (fichier tronqué,
        // écrit par une autre version, ou édité à la main) court-circuite les valeurs par défaut
        // de Kotlin : Gson les pose tels quels et l'application manipulait ensuite des `null` sur
        // des types déclarés non-null — crash au premier usage. C'est la 3ᵉ porte d'entrée
        // d'OccHabStation (avec le store et le parseur réseau) et la seule qui ne normalisait pas
        // (audit 2026-09-14, R2-m1).
        cache.remplacerTout(emptyList()) // crée le dossier
        ecrireFichier(
            """{"dateChargement":1757000000000,"versionFormat":2,"stations":[""" +
                """{"id":"x","uuidStation":null,"idStationServeur":1,"idDataset":10,""" +
                """"geometryType":null,"observateursIds":null,"observateursNoms":null,""" +
                """"latitude":42.9,"longitude":1.6,"habitats":[]},""" +
                // Sans `id` : structurellement invalide, écartée.
                """{"id":null,"idStationServeur":2,"idDataset":10,"habitats":[]}]}"""
        )
        cache.resetPourTests()
        val lues = cache.lire(10)
        assertEquals(listOf(1), lues.map { it.idStationServeur })
        assertNotNull(lues[0].uuidStation, "uuidStation doit être généré, pas laissé null")
        assertEquals("Point", lues[0].geometryType)
        assertTrue(lues[0].observateursIds.isEmpty())
        assertTrue(lues[0].observateursNoms.isEmpty())
    }

    // Propre au portage : les deux autres cas de normaliserStation et normaliserHabitat.
    @Test
    fun station_sans_habitats_et_habitat_sans_id_sont_ecartes() {
        cache.remplacerTout(emptyList()) // crée le dossier
        ecrireFichier(
            """{"dateChargement":1757000000000,"versionFormat":2,"stations":[""" +
                """{"id":"a","idStationServeur":1,"idDataset":10,"habitats":[""" +
                """{"id":"h1","cdHab":11},{"id":null,"cdHab":12},{"cdHab":13},null]},""" +
                """{"id":"b","idStationServeur":2,"idDataset":10}]}"""
        )
        cache.resetPourTests()
        val lues = cache.lire(10)
        assertEquals(listOf(1), lues.map { it.idStationServeur }, "station sans habitats écartée")
        assertEquals(listOf(11), lues.single().habitats.map { it.cdHab }, "habitats sans id écartés")
    }

    // ── Purges ──

    @Test
    fun vider_caches_synchronises_purge_les_stations() {
        cache.remplacerTout(listOf(station(1, 10)))
        t.services.cachesSynchronises.viderCachesSynchronises()
        assertTrue(cache.lire(10).isEmpty())
        assertNull(cache.dateChargement)
    }

    @Test
    fun changement_didentite_serveur_purge_les_stations() {
        // URL/login/mdp changés : id_station et id_dataset de l'ancienne instance, stations d'un
        // autre compte — rien ne doit survivre (même logique que les nomenclatures).
        cache.remplacerTout(listOf(station(1, 10)))
        t.services.invalidationCaches.invaliderCachesSession()
        assertTrue(cache.lire(10).isEmpty())
    }
}
