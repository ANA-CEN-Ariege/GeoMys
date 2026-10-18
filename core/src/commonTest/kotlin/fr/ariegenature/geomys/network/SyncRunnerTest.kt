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
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.network.ServeurSimule.Companion.panne
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.io.listeDepuisJson
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** La synchro globale (« Recharger les données ») de bout en bout, sur un GeoNature simulé :
 *  la version automatisée du critère de sortie du lot 2, en ligne puis hors ligne. Propre au
 *  portage : sous Android, SyncRunner n'était testé que par des gardes sur sa source. */
class SyncRunnerTest {

    private val t = ServicesDeTest()
    private val s get() = t.services

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun configurer() {
        s.config.urlServeur = t.serveur.base
        s.config.login = "alice"
        s.config.motDePasse = "pwd"
    }

    @Test
    fun synchro_complete_remplit_tous_les_caches() = runTest {
        configurer()
        t.serveur.routerGeoNatureComplet()
        s.synchro.executer()

        val fin = s.synchro.etat.value
        assertTrue(fin.termine)
        assertTrue(fin.succes, fin.resume)
        assertEquals("17", s.taxRef.versionSauvegardee)
        assertEquals(1, s.taxRef.nbTaxonsUniques)
        assertEquals(listOf(100), s.taxRef.listesSynchronisees)
        assertEquals(1, s.nomenclatures.count)
        assertEquals("1", s.nomenclatures.defautPour("SEXE"))
        assertEquals(listOf(12), listeDepuisJson(GeoNatureDataset.serializer(), s.config.datasetsCacheJson).map { it.id })
        assertEquals(setOf(12), s.config.datasetsCreablesOcctax)
        assertTrue(s.config.listesCacheJson.contains("Faune"))
        assertTrue(s.config.observateursCacheJson.contains("DUPONT Jean"))
        assertEquals(1, s.habitatsOcctax.count)
        assertEquals(1, s.habitatsOccHab.count)
        assertTrue(s.config.occhabDisponible)
        assertNotNull(s.stationsServeur.dateChargement, "stations serveur chargées, même vides")
        assertEquals(1, s.monitoringModules.countModulesEnCache())
        assertEquals("Forêt de Foix", s.monitoringObjets.labelObjetEnCache("STOM", "site", 5))
        assertEquals(GeoNatureConfig.VERSION_DONNEES_REQUISE, s.config.versionDonneesChargees)
    }

    /** Rien de ce que la synchro précédente avait écrit ne doit disparaître. */
    private fun verifierDonneesIntactes() {
        assertEquals("17", s.taxRef.versionSauvegardee)
        assertEquals(1, s.taxRef.nbTaxonsUniques)
        assertEquals(1, s.nomenclatures.count)
        assertEquals(1, s.habitatsOcctax.count)
        assertEquals(1, s.monitoringModules.countModulesEnCache())
        assertTrue(s.config.datasetsCacheJson.contains("JDD test"))
    }

    @Test
    fun hors_ligne_avec_session_en_cache_chaque_etape_echoue_sans_rien_effacer() = runTest {
        configurer()
        t.serveur.routerGeoNatureComplet()
        s.synchro.executer()
        s.synchro.accuserReception()

        // Plus de réseau, mais la session du login précédent vaut encore 5 minutes : chaque étape
        // échoue à son tour, et le compte-rendu les énumère.
        t.serveur.router { panne() }
        s.synchro.executer()

        val fin = s.synchro.etat.value
        assertTrue(fin.termine)
        assertFalse(fin.succes)
        assertTrue(fin.resume.orEmpty().startsWith("⚠ Chargement incomplet"), fin.resume)
        assertTrue(fin.resume.orEmpty().contains("TaxRef"), fin.resume)
        verifierDonneesIntactes()
    }

    @Test
    fun hors_ligne_au_demarrage_la_synchro_s_interrompt_sans_rien_effacer() = runTest {
        configurer()
        t.serveur.routerGeoNatureComplet()
        s.synchro.executer()

        // Appli relancée hors couverture : aucune session. Comme sous Android, la config Occtax
        // s'authentifie hors de son try et l'erreur réseau remonte au filet final, constaté sur
        // l'A12 et l'iPhone le 18 octobre (« Chargement interrompu : UnknownHostException… »).
        t.serveur.router { panne() }
        t.redemarrer()
        s.synchro.executer()

        val fin = s.synchro.etat.value
        assertTrue(fin.termine)
        assertFalse(fin.succes)
        assertTrue(fin.resume.orEmpty().startsWith("Chargement interrompu"), fin.resume)
        verifierDonneesIntactes()
    }

    @Test
    fun hors_ligne_au_redemarrage_les_donnees_restent_lisibles() = runTest {
        configurer()
        t.serveur.routerGeoNatureComplet()
        s.synchro.executer()
        t.serveur.router { panne() }
        // Démarrage à froid : tout se relit depuis le disque et les réglages, sans réseau.
        t.redemarrer()
        assertEquals(1, s.taxRef.nbTaxonsUniques)
        assertEquals(4319, s.taxRef.get("gobemouche gris")?.cdNom)
        assertEquals(1, s.habitatsOcctax.rechercher("hêtraie", 5).size)
        assertEquals(1, s.monitoringModules.countModulesEnCache())
        assertEquals(listOf("STOM"), s.monitoring.chargerModules(s.config).map { it.moduleCode })
    }

    @Test
    fun lancer_confie_la_synchro_a_l_execution_longue() = runTest {
        configurer()
        t.serveur.routerGeoNatureComplet()
        s.synchro.lancer()
        assertEquals(1, t.travauxLongs.size)
        assertFalse(s.synchro.etat.value.termine, "rien ne tourne avant l'exécution longue")
        t.travauxLongs.single().invoke()
        assertTrue(s.synchro.etat.value.succes)
    }

    @Test
    fun une_seule_synchro_a_la_fois() = runTest {
        configurer()
        t.serveur.routerGeoNatureComplet()
        s.synchro.lancer()
        s.synchro.lancer()
        // La garde atomique d'executer ignore le second lancement s'il arrive pendant le premier ;
        // lancés l'un après l'autre, ils synchronisent deux fois, sans conflit.
        t.travauxLongs.forEach { it.invoke() }
        assertTrue(s.synchro.etat.value.succes)
        assertFalse(s.synchro.actif)
    }
}
