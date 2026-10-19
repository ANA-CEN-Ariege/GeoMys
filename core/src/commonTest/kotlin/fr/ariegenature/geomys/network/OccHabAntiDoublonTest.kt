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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/OccHabAntiDoublonTest.kt) :
// Robolectric remplacé par ServicesDeTest, MockWebServer par ServeurSimule, org.json par son
// portage, JUnit par kotlin.test. Les noms de test entre accents graves (espaces, tirets longs)
// deviennent des identifiants simples, que Kotlin/Native accepte.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import io.ktor.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Anti-doublon OccHab : la station porte un `unique_id_sinp_station` stable ; après un envoi
 * INCERTAIN (réponse perdue), le ré-envoi retrouve la station déjà créée par cet UUID au lieu d'en
 * poster une seconde. On verrouille ici la fonction d'appariement sur les DEUX formats que le
 * serveur OccHab peut renvoyer (`format=json` = tableau plat ; repli GeoJSON = features), PUIS le
 * chemin de MISE À JOUR (station avec id serveur → POST /stations/<id>/, id_habitat dans le corps,
 * re-POST direct après une mise à jour incertaine) sur le serveur simulé.
 */
class OccHabAntiDoublonTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private val uuid = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"

    // Réponse GET /stations/?format=json : tableau d'objets, id + unique_id_sinp_station à plat.
    private val reponseJson = """
        [
          {"id_station": 11, "unique_id_sinp_station": "aaaaaaaa-0000-0000-0000-000000000000"},
          {"id_station": 42, "unique_id_sinp_station": "$uuid"}
        ]
    """.trimIndent()

    // Repli GeoJSON (FeatureCollection) : l'UUID est dans properties.
    private val reponseGeojson = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","properties":{"id_station": 42, "unique_id_sinp_station": "$uuid"}}
        ]}
    """.trimIndent()

    @Test
    fun format_json_station_trouvee_par_UUID() {
        assertEquals(42, OccHabUpload.trouverIdParUuid(reponseJson, uuid))
    }

    @Test
    fun repli_geojson_station_trouvee_par_UUID() {
        assertEquals(42, OccHabUpload.trouverIdParUuid(reponseGeojson, uuid))
    }

    @Test
    fun comparaison_UUID_insensible_a_la_casse() {
        assertEquals(42, OccHabUpload.trouverIdParUuid(reponseJson, uuid.uppercase()))
    }

    @Test
    fun UUID_absent_aucun_match_on_postera() {
        assertNull(OccHabUpload.trouverIdParUuid(reponseJson, "99999999-9999-9999-9999-999999999999"))
    }

    @Test
    fun liste_vide_aucun_match() {
        assertNull(OccHabUpload.trouverIdParUuid("[]", uuid))
    }

    @Test
    fun reponse_illisible_aucun_match_plutot_que_crash() {
        assertNull(OccHabUpload.trouverIdParUuid("<html>502 Bad Gateway</html>", uuid))
    }

    // ── MISE À JOUR d'une station portant un id serveur (import du serveur / rééditée) ──────

    /** Serveur simulé : login + POST création/mise à jour + GET liste (comptés pour vérifier que la
     *  mise à jour ne déclenche PAS la vérification d'existence par UUID). */
    private fun serveurOcchab() {
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json("""{"access_token":"t","user":{"id_role":1}}""")
                chemin.startsWith("/api/occhab/stations") && requete.method == HttpMethod.Post ->
                    json("""{"type":"Feature","id":42,"properties":{"id_station":42}}""")
                chemin.startsWith("/api/occhab/stations") && requete.method == HttpMethod.Get -> json("[]")
                else -> statut(404)
            }
        }
        config.apply {
            urlServeur = serveur.base
            login = "alice"; motDePasse = "pwd"; idDataset = "12"
        }
    }

    /** Toutes les requêtes /api/occhab reçues par le serveur simulé (méthode, chemin). */
    private fun requetesOcchab(): List<Pair<String, String>> =
        serveur.requetes.mapNotNull { r ->
            val chemin = r.url.encodedPathAndQuery
            if (!chemin.startsWith("/api/occhab")) null else r.method.value to chemin
        }

    /** Les POST /api/occhab reçus, avec leur corps : (méthode, chemin, corps). */
    private fun requetesOcchabAvecCorps(): List<Triple<String, String, String>> =
        serveur.requetes.mapNotNull { r ->
            val chemin = r.url.encodedPathAndQuery
            if (!chemin.startsWith("/api/occhab") || r.method != HttpMethod.Post) null
            else Triple(r.method.value, chemin, serveur.corps(r))
        }

    private fun stationServeur() = OccHabStation(
        uuidStation = uuid,
        idStationServeur = 42,
        idDataset = 12,
        habitats = listOf(
            // Habitat EXISTANT côté serveur : son id_habitat + uuid doivent repartir (mise à jour).
            OccHabHabitat(idHabitatServeur = 91, uuidHabitat = "aaaaaaaa-0000-0000-0000-000000000001",
                cdHab = 629, nomCite = "Prairie de fauche"),
            // Habitat AJOUTÉ localement : pas d'id → le serveur le crée.
            OccHabHabitat(cdHab = 630, nomCite = "Pelouse"),
        ),
    )

    @Test
    fun update_nominal_POST_sur_stations_id_et_id_habitat_dans_le_payload() = runTest {
        serveurOcchab()
        val res = t.services.occHabUpload.envoyer(stationServeur(), config)
        assertEquals(42, res.idStationServeur)
        assertFalse(res.dejaPresente)

        val posts = requetesOcchabAvecCorps()
        assertEquals(listOf("POST /api/occhab/stations/42/"), posts.map { "${it.first} ${it.second}" },
            "un seul POST, sur /stations/42/")
        val props = JSONObject(posts.single().third).getJSONObject("properties")
        assertEquals(uuid, props.getString("unique_id_sinp_station"), "l'UUID SINP serveur est renvoyé tel quel")
        val habs = props.getJSONArray("habitats")
        assertEquals(2, habs.length())
        assertEquals(91, habs.getJSONObject(0).getInt("id_habitat"),
            "habitat existant → id_habitat renvoyé (mis à jour, pas dupliqué)")
        assertEquals("aaaaaaaa-0000-0000-0000-000000000001", habs.getJSONObject(0).getString("unique_id_sinp_hab"))
        assertFalse(habs.getJSONObject(1).has("id_habitat"),
            "habitat ajouté localement → pas d'id_habitat (création côté serveur)")
    }

    @Test
    fun update_apres_incertain_re_POST_direct_sans_verification_par_UUID() = runTest {
        // Une mise à jour incertaine se re-POSTe telle quelle : /stations/<id>/ est IDEMPOTENT (le
        // serveur recharge la même instance) — la vérification d'existence par UUID ne sert qu'à la
        // CRÉATION (où un re-POST aveugle dupliquerait).
        serveurOcchab()
        val res = t.services.occHabUpload.envoyer(stationServeur().copy(envoiIncertain = true), config)
        assertEquals(42, res.idStationServeur)
        assertFalse(res.dejaPresente, "pas de court-circuit « déjà présente » en mise à jour")
        val reqs = requetesOcchab()
        assertEquals(listOf("POST" to "/api/occhab/stations/42/"), reqs, "aucun GET de vérification, un seul POST de mise à jour")
    }

    @Test
    fun creation_jamais_d_id_habitat_dans_le_payload_meme_s_il_est_connu() = runTest {
        // Garde-fou : en CRÉATION (pas d'id station serveur), un id_habitat résiduel ne doit JAMAIS
        // partir — le serveur rejetterait un habitat appartenant à une autre station
        // (validate_habitats de StationSchema).
        serveurOcchab()
        val station = stationServeur().copy(idStationServeur = null)
        t.services.occHabUpload.envoyer(station, config)
        val posts = requetesOcchabAvecCorps()
        assertEquals(listOf("POST /api/occhab/stations/"), posts.map { "${it.first} ${it.second}" })
        val habs = JSONObject(posts.single().third).getJSONObject("properties").getJSONArray("habitats")
        for (i in 0 until habs.length()) {
            assertFalse(habs.getJSONObject(i).has("id_habitat"))
            assertFalse(habs.getJSONObject(i).has("unique_id_sinp_hab"))
        }
    }

    /** CRÉATION après un envoi incertain : la vérification par UUID filtre par `id_dataset` et, si
     *  elle ÉCHOUE (HTTP 500 / réseau), l'envoi ressort INCERTAIN sans aucun POST — re-POSTer à
     *  l'aveugle dupliquerait (audit 2026-08-27). */
    @Test
    fun creation_apres_incertain_verification_en_echec_EnvoiIncertain_aucun_POST() = runTest {
        val gets = mutableListOf<String>()
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json("""{"access_token":"t","user":{"id_role":1}}""")
                chemin.startsWith("/api/occhab/stations") && requete.method == HttpMethod.Get -> {
                    gets.add(chemin); json("{}", 500)
                }
                chemin.startsWith("/api/occhab/stations") && requete.method == HttpMethod.Post ->
                    json("""{"type":"Feature","id":43,"properties":{"id_station":43}}""")
                else -> statut(404)
            }
        }
        config.apply {
            urlServeur = serveur.base
            login = "alice"; motDePasse = "pwd"; idDataset = "12"
        }
        val station = stationServeur().copy(idStationServeur = null, envoiIncertain = true)
        val e = assertFailsWith<GNErreur.EnvoiIncertain>("attendu : GNErreur.EnvoiIncertain") {
            t.services.occHabUpload.envoyer(station, config)
        }
        assertTrue(e.msg.contains("HTTP 500"))
        assertEquals(listOf("/api/occhab/stations/?format=json&id_dataset=12"), gets,
            "un seul GET de vérification, filtré par jeu de données")
        assertEquals(emptyList(), requetesOcchabAvecCorps().map { it.second }, "aucun POST")
    }
}
