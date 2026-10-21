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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/MonitoringEnvoiTest.kt) :
// Robolectric remplacé par ServicesDeTest, MockWebServer par ServeurSimule, org.json par son
// portage, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import io.ktor.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Envoi d'une saisie Suivis ([MonitoringEnvoi.envoyerVisite]) : contrat HTTP et corps (parent,
 * typage des valeurs, texte libre préservé, uuid client, id_digitiser, champs du schéma absents →
 * null / medias → []), échec HTTP → Result.failure ; et cache hors ligne des observateurs
 * ([MonitoringDatalists.chargerObservateursDeListe]) : en direct → cache, panne → cache, portail
 * captif → cache PRÉSERVÉ. Trous de couverture relevés par l'audit 2026-08-27.
 */
class MonitoringEnvoiTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config

    @BeforeTest
    fun setup() {
        t.services.monitoringCache.vider()
        config.apply {
            urlServeur = serveur.base
            login = "alice"; motDePasse = "pwd"; idDataset = "12"
        }
    }

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun login() = json("""{"access_token":"t","user":{"id_role":1}}""")

    @Test
    fun envoyer_visite_payload_et_id_serveur() = runTest {
        val corps = mutableListOf<String>()
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> login()
                chemin == "/api/monitorings/object/stom/visit" && requete.method == HttpMethod.Post -> {
                    corps.add(requete.texte); json("""{"id":77}""")
                }
                else -> statut(404)
            }
        }
        val res = t.services.monitoringEnvoi.envoyerVisite(
            config, moduleCode = "stom", objectType = "visit",
            parentIdField = "id_base_site", parentId = 5,
            valeurs = mapOf("comments" to "42", "nb" to "3", "id_dataset" to 12, "vide" to ""),
            nomsChampsSchema = listOf("comments", "nb", "visit_date_min", "medias"),
            champsTexteLibre = listOf("comments"),
            uuidClient = "u-1", uuidFieldName = "uuid_base_visit",
        )
        assertEquals(77, res.getOrThrow())
        val props = JSONObject(corps.single()).getJSONObject("properties")
        assertEquals(5, props.getInt("id_base_site"))
        assertEquals("42", props.getString("comments"), "texte libre préservé")
        assertEquals(3, props.getInt("nb"), "numérique converti")
        assertEquals("u-1", props.getString("uuid_base_visit"))
        assertEquals(1, props.getInt("id_digitiser"), "id_digitiser = id_role du login")
        assertEquals(12, props.getInt("id_dataset"))
        assertTrue(props.isNull("visit_date_min"), "champ du schéma absent → null explicite")
        assertEquals(0, props.getJSONArray("medias").length(), "medias absent → tableau vide")
        assertTrue(!props.has("vide"), "valeur vide retirée")
    }

    @Test
    fun formats_du_web_dates_sans_zeros_textes_gardes_et_pas_de_date_max_copiee() = runTest {
        val corps = mutableListOf<String>()
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> login()
                chemin == "/api/monitorings/object/stom/visit" && requete.method == HttpMethod.Post -> {
                    corps.add(requete.texte); json("""{"id":79}""")
                }
                else -> statut(404)
            }
        }
        val res = t.services.monitoringEnvoi.envoyerVisite(
            config, moduleCode = "stom", objectType = "visit", parentIdField = "id_base_site", parentId = 5,
            valeurs = mapOf(
                "visit_date_min" to "2026-03-05", "date_ponte" to "2026-11-20",
                "nb_adulte" to "1", "methodes" to listOf("1", "2"), "observers" to listOf(121), "id_dataset" to 12,
            ),
            nomsChampsSchema = listOf("visit_date_min", "visit_date_max", "date_ponte", "nb_adulte", "methodes", "observers", "id_dataset"),
            champsTexteLibre = listOf("visit_date_min", "date_ponte", "nb_adulte", "methodes"),
            champsDate = listOf("visit_date_min", "visit_date_max", "date_ponte"),
        )
        assertEquals(79, res.getOrThrow())
        val props = JSONObject(corps.single()).getJSONObject("properties")
        assertEquals("2026-3-5", props.getString("visit_date_min"), "date du web, sans zéros")
        assertEquals("2026-11-20", props.getString("date_ponte"))
        assertEquals("1", props.get("nb_adulte"), "une option « 1 » reste un texte")
        assertEquals("1", props.getJSONArray("methodes").get(0), "dans une liste aussi")
        assertEquals(121, props.getJSONArray("observers").get(0))
        assertTrue(props.isNull("visit_date_max"), "plus copiée de visit_date_min : le formulaire la fournit")
    }

    @Test
    fun aucun_jeu_de_donnees_n_est_invente() = runTest {
        // Une observation n'a pas de jeu de données : l'appli en ajoutait un (premier jeu du module,
        // puis celui d'OccTax), que le serveur rangeait dans ses données spécifiques.
        val corps = mutableListOf<String>()
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> login()
                chemin == "/api/monitorings/object/stom/observation" && requete.method == HttpMethod.Post -> {
                    corps.add(requete.texte); json("""{"id":78}""")
                }
                else -> statut(404)
            }
        }
        val res = t.services.monitoringEnvoi.envoyerVisite(
            config, moduleCode = "stom", objectType = "observation",
            parentIdField = "id_base_visit", parentId = 77,
            valeurs = mapOf("cd_nom" to 4117), nomsChampsSchema = listOf("cd_nom", "comments"),
        )
        assertEquals(78, res.getOrThrow())
        assertFalse(JSONObject(corps.single()).getJSONObject("properties").has("id_dataset"))
    }

    @Test
    fun envoyer_visite_echec_http_est_un_failure_type() = runTest {
        serveur.routerRequetes { requete ->
            if (requete.url.encodedPathAndQuery.startsWith("/api/auth/login")) login()
            else json("""{"description":"boom"}""", 500)
        }
        val res = t.services.monitoringEnvoi.envoyerVisite(config, "stom", "visit", null, null, mapOf("id_dataset" to 12))
        assertTrue(res.isFailure)
        val e = res.exceptionOrNull()
        assertTrue(e is GNErreur.EnvoiEchoue)
        assertEquals(500, e.code)
    }

    @Test
    fun observateurs_live_puis_cache_hors_ligne_et_portail_captif_ignore() = runTest {
        var reponse: () -> ServeurSimule.Reponse = { json("""[{"id_role":7,"nom_complet":"DUPONT jean"}]""") }
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> login()
                chemin.startsWith("/api/users/menu/9") -> reponse()
                else -> statut(404)
            }
        }
        val cache = t.services.monitoringCache
        val datalists = t.services.monitoringDatalists
        val cle = cache.keyObservateurs(9)
        val live = datalists.chargerObservateursDeListe(config, 9)
        assertNotNull(live)
        assertEquals(7, live.getJSONObject(0).getInt("id_role"))
        assertNotNull(cache.getJson(cle), "écrit dans le cache hors ligne")

        reponse = { statut(500) }
        val horsLigne = datalists.chargerObservateursDeListe(config, 9)
        assertEquals(1, horsLigne!!.length(), "panne serveur → cache")

        reponse = { statut(200, "<html>wifi de la gare</html>", "Content-Type" to "text/html") }
        val captif = datalists.chargerObservateursDeListe(config, 9)
        assertEquals(1, captif!!.length(), "200 non JSON → cache servi, jamais écrasé")
        assertTrue(cache.getJson(cle)!!.trimStart().startsWith("["))
    }
}
