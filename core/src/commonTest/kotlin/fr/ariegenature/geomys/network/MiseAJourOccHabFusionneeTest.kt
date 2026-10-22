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
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import fr.ariegenature.geomys.occhab.CarteOccHab
import fr.ariegenature.geomys.occhab.versOrigineJson
import fr.ariegenature.geomys.util.GeoJsonCoords
import io.ktor.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.test.runTest

/** Une station renvoyée en mise à jour est relue sur le serveur juste avant, et fusionnée avec la
 *  copie locale : renvoyée telle quelle, elle repartait avec la version de la dernière synchro, et
 *  le serveur supprimait l'habitat qu'un collègue venait d'ajouter sur le web. */
class MiseAJourOccHabFusionneeTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config
    private val store get() = t.services.occHabStore
    private val verrou = SynchronizedObject()

    /** Ce que rend `GET /api/occhab/stations/42/`. */
    private var lecture = json(feature(comment = "Commentaire du web", habitats = HABITAT_91 + "," + HABITAT_92))
    private var reponsePost = json("""{"type":"Feature","id":42,"properties":{"id_station":42}}""")
    private val requetes = mutableListOf<String>()
    private val corpsPostes = mutableListOf<String>()

    @BeforeTest
    fun setup() {
        config.apply {
            urlServeur = serveur.base
            login = "alice"; motDePasse = "pwd"; idDataset = "12"
        }
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPath
            if (chemin.startsWith("/api/occhab")) synchronized(verrou) { requetes += "${requete.method.value} $chemin" }
            when {
                chemin.startsWith("/api/auth/login") -> json("""{"access_token":"t","user":{"id_role":1}}""")
                chemin == "/api/occhab/stations/42/" && requete.method == HttpMethod.Get -> lecture
                chemin.startsWith("/api/occhab/stations") && requete.method == HttpMethod.Post -> {
                    synchronized(verrou) { corpsPostes += requete.texte }
                    reponsePost
                }
                else -> statut(404)
            }
        }
    }

    @AfterTest
    fun nettoyer() = t.nettoyer()

    /** La station 42 telle que la synchro l'a reçue, puis déplacée par la topologie partagée. */
    private fun voisineDeplacee(): OccHabStation {
        val recue = OccHabApi.parserFeatureCollection(
            """{"type":"FeatureCollection","features":[${feature(comment = "Commentaire d'origine", habitats = HABITAT_91)}]}""",
        ).single()
        return CarteOccHab.voisineDeplacee(recue, GeoJsonCoords.parse("[[1.4,42.9],[1.5,42.9],[1.55,43.05]]"), copieLocale = false)
    }

    @Test
    fun une_voisine_deplacee_garde_l_habitat_et_le_commentaire_ajoutes_sur_le_web() = runTest {
        store.upsertStation("sa", voisineDeplacee())

        val res = t.services.envoiOccHab.envoyerSaisieOccHabVersGeoNature(store.charger().first { it.id == "sa" }, config)

        assertTrue(res.succes, res.message)
        assertEquals(listOf("GET /api/occhab/stations/42/", "POST /api/occhab/stations/42/"), synchronized(verrou) { requetes.filter { "stations/42" in it } })
        val corps = JSONObject(synchronized(verrou) { corpsPostes.single() })
        val props = corps.getJSONObject("properties")
        assertEquals("Commentaire du web", props.getString("comment"))
        val habitats = props.getJSONArray("habitats")
        assertEquals(listOf(91, 92), (0 until habitats.length()).map { habitats.getJSONObject(it).getInt("id_habitat") })
        assertTrue("1.55" in corps.getJSONObject("geometry").toString(), "la nouvelle géométrie part : ${corps.getJSONObject("geometry")}")
        // Le cache des stations du serveur porte la version partie, habitat du web compris.
        assertEquals(listOf(91, 92), t.services.stationsServeur.lire(12).single().habitats.map { it.idHabitatServeur })
    }

    @Test
    fun une_station_mise_en_plusieurs_parties_sur_le_web_n_est_pas_renvoyee() = runTest {
        // Audit du 21 octobre, C6 : l'appli n'en lit que la première partie, et l'envoi aurait
        // supprimé les autres.
        lecture = json(feature(comment = "Commentaire du web", habitats = HABITAT_91).replace(
            """"geometry":{"type":"Polygon","coordinates":[[[1.4,42.9],[1.5,42.9],[1.5,43.0],[1.4,42.9]]]}""",
            """"geometry":{"type":"MultiPolygon","coordinates":[[[[1.4,42.9],[1.5,42.9],[1.5,43.0],[1.4,42.9]]],[[[2.0,43.0],[2.1,43.0],[2.1,43.1],[2.0,43.0]]]]}""",
        ))
        store.upsertStation("sa", voisineDeplacee())

        val res = t.services.envoiOccHab.envoyerSaisieOccHabVersGeoNature(store.charger().first { it.id == "sa" }, config)

        assertFalse(res.succes)
        assertTrue(synchronized(verrou) { corpsPostes.isEmpty() }, "aucun POST")
        assertTrue(OccHabUpload.GEOMETRIE_EN_PLUSIEURS_PARTIES in res.message, res.message)
    }

    @Test
    fun un_contour_retouche_sur_le_web_n_est_pas_ecrase_par_la_topologie() = runTest {
        // La voisine a été déplacée dans l'appli, et son contour retouché sur le web depuis la synchro.
        lecture = json(feature(comment = "Commentaire du web", habitats = HABITAT_91).replace("[1.5,43.0]", "[1.52,43.02]"))
        store.upsertStation("sa", voisineDeplacee())

        val res = t.services.envoiOccHab.envoyerSaisieOccHabVersGeoNature(store.charger().first { it.id == "sa" }, config)

        assertFalse(res.succes)
        assertTrue(synchronized(verrou) { corpsPostes.isEmpty() }, "aucun POST")
        assertTrue(OccHabUpload.GEOMETRIE_MODIFIEE_SUR_LE_WEB in res.message, res.message)
    }

    @Test
    fun une_station_supprimee_sur_le_web_n_est_pas_recreee() = runTest {
        lecture = statut(404)
        store.upsertStation("sa", voisineDeplacee())

        val res = t.services.envoiOccHab.envoyerSaisieOccHabVersGeoNature(store.charger().first { it.id == "sa" }, config)

        assertFalse(res.succes)
        assertTrue(synchronized(verrou) { corpsPostes.isEmpty() }, "aucun POST")
        assertFalse(store.stationsDeSaisie("sa").single().envoyeGeoNature)
    }

    @Test
    fun apres_un_envoi_les_habitats_portent_les_ids_du_serveur() = runTest {
        reponsePost = json(
            """{"type":"Feature","id":77,"properties":{"id_station":77,"habitats":[""" +
                """{"id_habitat":501,"cd_hab":629,"unique_id_sinp_hab":"h-501"},""" +
                """{"id_habitat":502,"cd_hab":631,"unique_id_sinp_hab":"h-502"}]}}""",
        )
        store.upsertStation(
            "sa",
            OccHabStation(
                id = "A", latitude = 42.9, longitude = 1.4, idDataset = 12,
                habitats = listOf(OccHabHabitat(cdHab = 629, nomCite = "Prairie"), OccHabHabitat(cdHab = 631, nomCite = "Pelouse")),
            ),
        )

        val res = t.services.envoiOccHab.envoyerSaisieOccHabVersGeoNature(store.charger().first { it.id == "sa" }, config)

        assertTrue(res.succes, res.message)
        val envoyee = store.stationsDeSaisie("sa").single()
        assertEquals(listOf(501, 502), envoyee.habitats.map { it.idHabitatServeur })
        assertEquals(listOf("h-501", "h-502"), envoyee.habitats.map { it.uuidHabitat })
        assertEquals(listOf(501, 502), t.services.stationsServeur.lire(12).single().habitats.map { it.idHabitatServeur })
    }

    @Test
    fun une_valeur_videe_part_en_null_dans_une_mise_a_jour() = runTest {
        // Le web envoie toutes les valeurs de son formulaire, vides comprises : une valeur absente
        // du corps, le serveur gardait l'ancienne.
        val avecAbondance = HABITAT_91.removeSuffix("}") + ""","id_nomenclature_abundance":7,"determiner":"Alice"}"""
        val recue = OccHabApi.parserFeatureCollection(
            """{"type":"FeatureCollection","features":[${feature(comment = "Commentaire d'origine", habitats = avecAbondance)}]}""",
        ).single()
        lecture = json(feature(comment = "Commentaire d'origine", habitats = avecAbondance))
        store.upsertStation(
            "sa",
            recue.copy(
                comment = null,
                habitats = recue.habitats.map { it.copy(idNomAbondance = null, determiner = null) },
                envoyeGeoNature = false,
                origineJson = recue.versOrigineJson(),
            ),
        )

        val res = t.services.envoiOccHab.envoyerSaisieOccHabVersGeoNature(store.charger().first { it.id == "sa" }, config)

        assertTrue(res.succes, res.message)
        val props = JSONObject(synchronized(verrou) { corpsPostes.single() }).getJSONObject("properties")
        assertTrue(props.has("comment") && props.isNull("comment"), "commentaire vidé : null explicite")
        assertTrue(props.has("depth_min") && props.isNull("depth_min"))
        val habitat = props.getJSONArray("habitats").getJSONObject(0)
        assertTrue(habitat.has("id_nomenclature_abundance") && habitat.isNull("id_nomenclature_abundance"))
        assertTrue(habitat.has("determiner") && habitat.isNull("determiner"))
        assertFalse(habitat.has("id_nomenclature_sensitivity"), "hors du formulaire web : jamais vidée")
    }

    @Test
    fun une_creation_omet_les_valeurs_vides_et_les_observateurs_en_texte_partent_seuls() = runTest {
        config.occhabObservateursEnTexte = true
        store.upsertStation(
            "sa",
            OccHabStation(
                id = "A", latitude = 42.9, longitude = 1.4, idDataset = 12, observateursTxt = "Alice, Bob",
                habitats = listOf(OccHabHabitat(cdHab = 629, nomCite = "Prairie")),
            ),
        )

        val res = t.services.envoiOccHab.envoyerSaisieOccHabVersGeoNature(store.charger().first { it.id == "sa" }, config)

        assertTrue(res.succes, res.message)
        val props = JSONObject(synchronized(verrou) { corpsPostes.single() }).getJSONObject("properties")
        assertFalse(props.has("comment"), "création : le serveur applique ses défauts")
        assertFalse(props.getJSONArray("habitats").getJSONObject(0).has("id_nomenclature_abundance"))
        assertEquals("Alice, Bob", props.getString("observers_txt"))
        assertEquals(0, props.getJSONArray("observers").length(), "pas d'utilisateur connecté ajouté d'office")
    }

    private companion object {
        const val HABITAT_91 = """{"id_habitat":91,"unique_id_sinp_hab":"h-91","cd_hab":629,"nom_cite":"Prairie de fauche"}"""
        const val HABITAT_92 = """{"id_habitat":92,"unique_id_sinp_hab":"h-92","cd_hab":631,"nom_cite":"Ajouté sur le web"}"""

        fun feature(comment: String, habitats: String) =
            """{"type":"Feature","id":42,"geometry":{"type":"Polygon","coordinates":[[[1.4,42.9],[1.5,42.9],[1.5,43.0],[1.4,42.9]]]},""" +
                """"properties":{"id_station":42,"unique_id_sinp_station":"aaaaaaaa-0000-0000-0000-000000000042","id_dataset":12,""" +
                """"date_min":"2026-05-01 00:00:00","date_max":"2026-05-01 00:00:00","station_name":"Prairie",""" +
                """"comment":"$comment","habitats":[$habitats]}}"""
    }
}
