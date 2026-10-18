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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Purge du « rechargement requis après mise à jour » (SyncRunner).
 *
 * RÉGRESSION COUVERTE (audit 2026-09-14, R1-C2) : la purge était faite APRÈS la phase A, qui venait
 * précisément de réécrire les deux caches HABREF et les stations serveur — et la phase B ne les
 * réécrit jamais. Après un rechargement pourtant annoncé RÉUSSI, l'utilisateur se retrouvait sans
 * aucun habitat proposé hors ligne : saisie OccHab impossible, l'habitat y étant obligatoire.
 *
 * L'invariant tient en une phrase : **SyncRunner ne purge que ce que la phase B réécrit derrière
 * elle** ; le bouton « Vider le cache » de Paramètres, lui, enlève tout.
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

    /** Les deux tests ci-dessus prouvent que les DEUX purges se comportent correctement — pas que
     *  SyncRunner appelle la bonne. Or c'est précisément le site d'appel qui portait le défaut.
     *  Sous Android, une garde lisait la source de SyncRunner ; portée, la synchro se teste. */
    @Test
    fun rechargement_requis_purge_la_phase_B_et_epargne_ce_que_la_phase_A_vient_d_ecrire() = runTest {
        s.config.urlServeur = t.serveur.base
        s.config.login = "alice"
        s.config.motDePasse = "pwd"
        // Données d'une version précédente, et drapeau armé au lancement (armerRechargementSiRequis).
        s.taxRef.remplacerTout(mapOf("ancien" to TaxRefEntry(1, "Ancien taxon")))
        s.taxRef.versionSauvegardee = "16"
        s.config.rechargementRequisApresMaj = true
        t.serveur.routerGeoNatureComplet()

        s.synchro.executer()

        assertTrue(s.synchro.etat.value.succes, s.synchro.etat.value.resume)
        assertNull(s.taxRef.get("ancien"), "la purge de la phase B a vidé l'ancien TaxRef")
        assertEquals("17", s.taxRef.versionSauvegardee)
        assertEquals(1, s.habitatsOcctax.count, "HABREF Occtax écrit par la phase A, puis effacé")
        assertEquals(1, s.habitatsOccHab.count, "HABREF OccHab écrit par la phase A, puis effacé")
        assertTrue(s.stationsServeur.dateChargement != null, "stations serveur écrites par la phase A, puis effacées")
        assertFalse(s.config.rechargementRequisApresMaj, "drapeau désarmé après la synchro")
        assertEquals(GeoNatureConfig.VERSION_DONNEES_REQUISE, s.config.versionDonneesChargees)
    }
}
