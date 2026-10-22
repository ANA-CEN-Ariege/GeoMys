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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/PurgeRechargementApresMajTest.kt) :
// Robolectric remplacé par ServicesDeTest, les fonctions de purge par la classe CachesSynchronises,
// JUnit par kotlin.test. La garde textuelle sur la source de SyncRunner devient un vrai test de la
// synchro, sur un GeoNature simulé.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.HabitatSuggestion
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.network.routerGeoNatureComplet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Les purges des caches synchronisés, et la synchro qui suit une mise à jour de l'application.
 *
 * RÉGRESSION COUVERTE (audit 2026-09-14, R1-C2) : la purge du rechargement après mise à jour était
 * faite APRÈS la phase A, qui venait de réécrire les deux caches HABREF et les stations serveur — et
 * la phase B ne les réécrit jamais : plus aucun habitat proposé hors ligne. Depuis le 21 octobre 2026,
 * la synchro ne purge plus rien après une mise à jour : elle réécrit ses caches, et ne retélécharge
 * TaxRef que si sa version a changé sur le serveur ou si le format de son cache a changé. Le bouton
 * « Vider le cache » de Paramètres, lui, enlève tout.
 */
class PurgeRechargementApresMajTest {

    private val t = ServicesDeTest()
    private val s get() = t.services

    @AfterTest
    fun nettoyer() = t.nettoyer()

    @BeforeTest
    fun setup() {
        s.cachesSynchronises.viderCachesSynchronises()
        s.stationsServeur.resetPourTests()
    }

    private fun remplirLesCachesDeLaPhaseA() {
        s.habitatsOcctax.remplacerTout(listOf(HabitatSuggestion(cdHab = 1, libelle = "Hêtraie")))
        s.habitatsOccHab.remplacerTout(listOf(HabitatSuggestion(cdHab = 2, libelle = "Pelouse")))
        s.stationsServeur.remplacerTout(
            listOf(OccHabStation(idStationServeur = 1, idDataset = 10, origineServeur = true))
        )
        assertEquals(1, s.habitatsOcctax.count)
        assertEquals(1, s.habitatsOccHab.count)
        assertEquals(1, s.stationsServeur.count)
    }

    @Test
    fun purge_de_synchro_epargne_les_caches_ecrits_par_la_phase_A() {
        remplirLesCachesDeLaPhaseA()
        s.cachesSynchronises.viderCachesReecritsEnPhaseB()
        assertEquals(1, s.habitatsOcctax.count, "HABREF Occtax effacé juste après avoir été rechargé")
        assertEquals(1, s.habitatsOccHab.count, "HABREF OccHab effacé juste après avoir été rechargé")
        s.stationsServeur.resetPourTests()
        assertEquals(1, s.stationsServeur.count, "stations serveur effacées juste après avoir été rechargées")
    }

    @Test
    fun vider_le_cache_de_parametres_enleve_bien_tout() {
        // Contre-épreuve : le bouton « Vider le cache » garde son contrat complet.
        remplirLesCachesDeLaPhaseA()
        s.cachesSynchronises.viderCachesSynchronises()
        assertEquals(0, s.habitatsOcctax.count)
        assertEquals(0, s.habitatsOccHab.count)
        s.stationsServeur.resetPourTests()
        assertEquals(0, s.stationsServeur.count)
    }

    // ── Le SITE D'APPEL, testé pour de bon ──

    /** Une installation chargée par une version précédente de l'application, devant le GeoNatureSimulé
     *  (TaxRef v17, une liste, la 100) : son TaxRef est-il [formatAJour] ? */
    private fun installationPrecedente(formatAJour: Boolean) {
        s.config.urlServeur = t.serveur.base
        s.config.login = "alice"
        s.config.motDePasse = "pwd"
        s.config.versionDonneesChargees = GeoNatureConfig.VERSION_DONNEES_REQUISE - 1
        s.config.taxaListeId = "100"
        s.taxRef.remplacerTout(mapOf("ancien" to TaxRefEntry(1, "Ancien taxon")))
        s.taxRef.versionSauvegardee = "17"
        s.taxRef.listesSynchronisees = listOf(100)
        s.taxRef.versionFormat = if (formatAJour) TaxRefCache.VERSION_FORMAT else null
        t.serveur.routerGeoNatureComplet()
    }

    @Test
    fun apres_une_mise_a_jour_taxref_n_est_pas_retelecharge_a_version_egale() = runTest {
        installationPrecedente(formatAJour = true)

        s.synchro.executer()

        assertTrue(s.synchro.etat.value.succes, s.synchro.etat.value.resume)
        assertEquals(1, s.taxRef.get("ancien")?.cdNom, "TaxRef gardé : sa version n'a pas changé sur le serveur")
        assertNull(s.taxRef.get(TaxRefCache.normaliser("Muscicapa striata")))
        assertEquals(1, s.habitatsOcctax.count, "HABREF Occtax écrit par la phase A")
        assertEquals(1, s.habitatsOccHab.count, "HABREF OccHab écrit par la phase A")
        assertTrue(s.stationsServeur.dateChargement != null, "stations serveur écrites par la phase A")
        assertEquals(GeoNatureConfig.VERSION_DONNEES_REQUISE, s.config.versionDonneesChargees)
    }

    @Test
    fun un_cache_de_taxref_d_un_format_ancien_est_retelecharge_meme_a_version_egale() = runTest {
        installationPrecedente(formatAJour = false)

        s.synchro.executer()

        assertTrue(s.synchro.etat.value.succes, s.synchro.etat.value.resume)
        assertNull(s.taxRef.get("ancien"), "TaxRef retéléchargé pour son nouveau format")
        assertEquals(4319, s.taxRef.get(TaxRefCache.normaliser("Muscicapa striata"))?.cdNom)
        assertEquals(TaxRefCache.VERSION_FORMAT, s.taxRef.versionFormat)
        assertEquals("17", s.taxRef.versionSauvegardee)
    }
}
