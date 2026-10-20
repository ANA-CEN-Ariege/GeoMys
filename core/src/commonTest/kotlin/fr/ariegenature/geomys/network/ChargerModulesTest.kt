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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/ChargerModulesTest.kt) :
// MockWebServer remplacé par ServeurSimule, Robolectric par MonitoringDeTest, les objets par des
// instances, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Liste des protocoles monitoring sur un serveur simulé : parsing + filtrage CRUVED (on n'expose
 *  que les modules avec au moins un droit > 0), gestion des codes 403/404 → liste vide. */
class ChargerModulesTest {

    private val m = MonitoringDeTest()

    @AfterTest
    fun nettoyer() = m.nettoyer()

    private fun router(modulesResponse: () -> ServeurSimule.Reponse) {
        m.serveur.router(
            "/api/auth/login" to { json("""{"access_token":"t","user":{"id_role":1}}""") },
            "/api/monitorings/modules" to modulesResponse,
        )
    }

    @Test
    fun filtre_cruved_garde_seulement_les_modules_avec_un_droit() = runTest {
        router {
            json(
                """
                [{"id_module":1,"module_code":"STOM","module_label":"STOM","cruved":{"R":1,"U":0}},
                 {"id_module":2,"module_code":"SANS_DROIT","module_label":"X","cruved":{"R":0,"U":0,"C":0}},
                 {"id_module":3,"module_code":"LEGACY","module_label":"Legacy"}]
                """.trimIndent(),
            )
        }
        val modules = m.api.chargerModules(m.config)
        val codes = modules.map { it.moduleCode }.toSet()
        assertTrue("STOM" in codes, "STOM (droit R=1) conservé")
        assertTrue("LEGACY" in codes, "LEGACY (cruved absent) conservé")
        assertTrue("SANS_DROIT" !in codes, "SANS_DROIT (tous droits nuls) masqué")
        assertEquals(2, modules.size)
    }

    @Test
    fun http_403_renvoie_liste_vide() = runTest {
        router { statut(403) }
        assertTrue(m.api.chargerModules(m.config).isEmpty())
    }

    @Test
    fun http_404_module_absent_renvoie_liste_vide() = runTest {
        router { statut(404) }
        assertTrue(m.api.chargerModules(m.config).isEmpty())
    }

    @Test
    fun reponse_enveloppee_dans_modules() = runTest {
        router { json("""{"modules":[{"id_module":9,"module_code":"A","module_label":"A","cruved":{"R":2}}]}""") }
        val modules = m.api.chargerModules(m.config)
        assertEquals(listOf("A"), modules.map { it.moduleCode })
    }

    // Propres au portage.

    @Test
    fun le_cache_disque_ne_garde_que_les_modules_accessibles_et_sert_hors_ligne() = runTest {
        router {
            json(
                """
                [{"id_module":1,"module_code":"STOM","module_label":"STOM","cruved":{"R":1}},
                 {"id_module":2,"module_code":"SANS_DROIT","module_label":"X","cruved":{"R":0}}]
                """.trimIndent(),
            )
        }
        m.api.chargerModules(m.config)
        assertEquals(1, m.modules.countModulesEnCache())
        // Hors ligne, au démarrage suivant : les mêmes modules, depuis le disque.
        val horsLigne = MonitoringDeTest(ServeurSimule().apply { router { ServeurSimule.panne() } })
        try {
            horsLigne.cache.setJson(horsLigne.cache.keyModules(), m.cache.getJson(m.cache.keyModules())!!)
            assertEquals(listOf("STOM"), horsLigne.api.chargerModules(horsLigne.config).map { it.moduleCode })
        } finally {
            horsLigne.nettoyer()
        }
    }

    /** Décisions de l'utilisateur du 20 octobre : les écrans lisent la liste que la synchro a mise en
     *  cache, comme GeoMys-IOS, et ne demandent jamais rien au serveur — rien avant la première synchro. */
    @Test
    fun les_ecrans_lisent_la_liste_en_cache_sans_requete() = runTest {
        router {
            json(
                """
                [{"id_module":1,"module_code":"STOM","module_label":"STOM","cruved":{"R":1}},
                 {"id_module":2,"module_code":"MARES","module_label":"Mares","cruved":{"R":1}}]
                """.trimIndent(),
            )
        }
        // Aucune synchro : pas de liste, et pas de requête.
        assertNull(m.api.chargerModulesDepuisCache())
        assertEquals(0, m.serveur.chemins.count { it.startsWith("/api/monitorings/modules") })
        // La synchro interroge le serveur et met la liste en cache, que les écrans lisent.
        assertEquals(listOf("Mares", "STOM"), m.api.chargerModules(m.config).map { it.moduleLabel })
        assertEquals(listOf("Mares", "STOM"), m.api.chargerModulesDepuisCache()?.map { it.moduleLabel })
        assertEquals(1, m.serveur.chemins.count { it.startsWith("/api/monitorings/modules") })
        // Le serveur change : les écrans gardent la liste en cache, sans le rappeler.
        router { json("""[{"id_module":3,"module_code":"NOUVEAU","module_label":"Nouveau","cruved":{"R":1}}]""") }
        assertEquals(listOf("MARES", "STOM"), m.api.chargerModulesDepuisCache()?.map { it.moduleCode })
        assertEquals(1, m.serveur.chemins.count { it.startsWith("/api/monitorings/modules") })
        assertEquals("STOM", m.modules.moduleParCode("STOM")?.moduleCode, "la liste lue sert au détail d'un protocole")
        // La synchro suivante rafraîchit le cache.
        assertEquals(listOf("NOUVEAU"), m.api.chargerModules(m.config).map { it.moduleCode })
        assertEquals(listOf("NOUVEAU"), m.api.chargerModulesDepuisCache()?.map { it.moduleCode })
    }
}
