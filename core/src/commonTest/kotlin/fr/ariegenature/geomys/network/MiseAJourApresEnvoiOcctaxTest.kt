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

// Propre au portage (audit du 21 octobre 2026, C3 et M33 ; décision de l'utilisateur : « envoyer de vraies
// mises à jour après un envoi partiel »). GeoMys-Android perdait ces modifications.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.network.GeoNatureUpload.Companion.empreinteOccurrence
import fr.ariegenature.geomys.network.GeoNatureUpload.Companion.empreinteReleve
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import fr.ariegenature.geomys.store.NomValeur
import io.ktor.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.test.runTest

/** Une saisie OccTax déjà transmise en partie, puis reprise (« Continuer la saisie ») : une espèce
 *  ajoutée rejoint le relevé déjà créé, une espèce ou un relevé modifiés partent en mise à jour. */
class MiseAJourApresEnvoiOcctaxTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config
    private val upload get() = t.services.upload
    private val verrou = SynchronizedObject()

    /** Les requêtes reçues (hors login) : « MÉTHODE chemin », et leurs corps. */
    private val requetes = mutableListOf<String>()
    private val corps = mutableMapOf<String, String>()

    /** L'occurrence de « a » sur le serveur ; null : supprimée sur le web. */
    private var occurrenceSurLeServeur: String? =
        """{"id_occurrence_occtax":500,"unique_id_occurence_occtax":"aaaaaaaa-0000-0000-0000-000000000001",""" +
            """"cor_counting_occtax":[{"id_counting_occtax":901},{"id_counting_occtax":900}]}"""

    @BeforeTest
    fun setup() {
        t.services.nomenclatures.setAll(
            listOf("METH_OBS", "STATUT_OBS", "SEXE", "STADE_VIE", "TYPE_MEDIA").associateWith { listOf(NomValeur(1, "x")) },
        )
        config.apply {
            urlServeur = serveur.base
            login = "alice"; motDePasse = "pwd"; idDataset = "12"
        }
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPath
            val requeteTexte = "${requete.method.value} $chemin"
            if (!chemin.startsWith("/api/auth")) synchronized(verrou) {
                requetes += requeteTexte
                if (requete.method == HttpMethod.Post) corps[requeteTexte] = requete.texte
            }
            when {
                chemin.startsWith("/api/auth/login") -> json("""{"access_token":"t","user":{"id_role":7}}""")
                chemin == "/api/occtax/OCCTAX/releves" -> {
                    val uuid = requete.url.parameters["unique_id_sinp_grp"]
                    json("""{"type":"FeatureCollection","features":[{"id":100,"properties":{"id_releve_occtax":100,"unique_id_sinp_grp":"$uuid"}}]}""")
                }
                chemin == "/api/occtax/OCCTAX/releve/100" && requete.method == HttpMethod.Get -> json(
                    """{"releve":{"id":100,"properties":{"id_releve_occtax":100,"t_occurrences_occtax":[${occurrenceSurLeServeur.orEmpty()}]}}}""",
                )
                chemin == "/api/occtax/OCCTAX/only/releve" -> json("""{"id":200}""")
                chemin.startsWith("/api/occtax/OCCTAX/") && requete.method == HttpMethod.Post -> json("{}")
                else -> statut(404)
            }
        }
    }

    @AfterTest
    fun nettoyer() = t.nettoyer()

    /** « a », transmise avec son relevé R1 : ses empreintes sont celles de ce qui est parti. */
    private fun transmise(): Observation = Observation(
        id = "a", espece = "Merle noir", cdNom = 4001, latitude = 42.9, longitude = 1.4, nombre = 1,
        releveId = "R1", date = 1_700_000_000_000L, commentReleve = "Lisière",
        denombrementsAdditionnels = listOf(Denombrement(nombreMin = 2, nombreMax = 2)),
    ).apply {
        uuidOccurrence = "aaaaaaaa-0000-0000-0000-000000000001"
        envoyeeServeur = true
        releveTente = true
        empreinteEnvoi = empreinteOccurrence(this)
        empreinteReleveEnvoi = empreinteReleve(this)
    }

    @Test
    fun une_espece_ajoutee_a_un_releve_deja_cree_le_rejoint() = runTest {
        // M33 : elle partait dans un second relevé, même lieu, mêmes dates, même identifiant SINP.
        val nouvelle = transmise().copy(id = "b", espece = "Pic vert", cdNom = 3611, uuidOccurrence = "bbbb").apply {
            envoyeeServeur = false
            releveTente = false
            empreinteEnvoi = null
            empreinteReleveEnvoi = null
        }
        val res = upload.envoyer(Sortie(observations = listOf(transmise(), nouvelle)), config)
        assertEquals(1, res.nbCrees)
        assertFalse(requetes.any { it == "POST /api/occtax/OCCTAX/only/releve" }, "pas de second relevé : $requetes")
        assertTrue("POST /api/occtax/OCCTAX/releve/100/occurrence" in requetes, "$requetes")
        assertEquals(0, res.nbMisesAJour, "« a » n'a pas changé")
    }

    @Test
    fun une_espece_modifiee_apres_envoi_part_en_mise_a_jour() = runTest {
        val modifiee = transmise().apply {
            nombre = 3
            nombreMax = 3
            notes = ""
            denombrementsAdditionnels = denombrementsAdditionnels + Denombrement(nombreMin = 5, nombreMax = 5)
        }
        val transmises = mutableListOf<Map<String, Pair<String?, String?>>>()
        val res = upload.envoyer(Sortie(observations = listOf(modifiee)), config, marqueur(transmises))
        assertEquals(1, res.nbMisesAJour)
        assertEquals(0, res.nbMisesAJourEnEchec)
        val occurrence = JSONObject(corps.getValue("POST /api/occtax/OCCTAX/occurrence/500"))
        val denombrements = occurrence.getJSONArray("cor_counting_occtax")
        // Les deux du serveur gardent leur ligne (dans l'ordre de création), le troisième est créé.
        assertEquals(900, denombrements.getJSONObject(0).getInt("id_counting_occtax"))
        assertEquals(901, denombrements.getJSONObject(1).getInt("id_counting_occtax"))
        assertFalse(denombrements.getJSONObject(2).has("id_counting_occtax"))
        assertEquals(3, denombrements.getJSONObject(0).getInt("count_min"))
        assertFalse(denombrements.getJSONObject(0).has("medias"), "le serveur garde les photos")
        assertTrue(occurrence.isNull("comment"), "un texte vidé part à null")
        // L'empreinte de ce qui vient de partir est enregistrée : rien ne repartira.
        assertEquals(empreinteOccurrence(modifiee), transmises.single().getValue("a").first)
        assertFalse(requetes.any { it.startsWith("POST /api/occtax/OCCTAX/only/releve") }, "le relevé n'a pas changé : $requetes")
    }

    @Test
    fun un_releve_modifie_apres_envoi_part_en_mise_a_jour_sans_ses_occurrences() = runTest {
        val modifiee = transmise().apply { commentReleve = "Lisière nord" }
        val res = upload.envoyer(Sortie(observations = listOf(modifiee)), config)
        assertEquals(1, res.nbMisesAJour)
        val releve = JSONObject(corps.getValue("POST /api/occtax/OCCTAX/only/releve/100"))
        val proprietes = releve.getJSONObject("properties")
        assertEquals("Lisière nord", proprietes.getString("comment"))
        assertFalse(proprietes.has("t_occurrences_occtax"), "le serveur supprimerait les occurrences absentes")
        assertFalse(proprietes.has("id_digitiser"), "le numérisateur reste celui de la création")
        assertTrue(proprietes.isNull("hour_min"), "un champ vide part à null")
        assertFalse(requetes.any { it.startsWith("POST /api/occtax/OCCTAX/occurrence/") }, "l'espèce n'a pas changé")
    }

    @Test
    fun rien_de_modifie_rien_ne_part() = runTest {
        val res = upload.envoyer(Sortie(observations = listOf(transmise())), config)
        assertEquals(0, res.nbTotal)
        assertEquals(0, res.nbMisesAJour)
        assertTrue(requetes.none { it.startsWith("POST") }, "$requetes")
    }

    @Test
    fun une_espece_supprimee_sur_le_web_n_est_pas_recreee() = runTest {
        occurrenceSurLeServeur = null
        val modifiee = transmise().apply { nombre = 4 }
        assertFailsWith<GNErreur.EnvoiEchoue> { upload.envoyer(Sortie(observations = listOf(modifiee)), config) }
        assertTrue(requetes.none { it.startsWith("POST /api/occtax/OCCTAX/occurrence/") || it.endsWith("/occurrence") }, "$requetes")
    }

    @Test
    fun de_bout_en_bout_les_empreintes_sont_gardees_et_la_saisie_close_une_fois_tout_transmis() = runTest {
        val store = t.services.sortieStore
        // Une saisie partiellement envoyée : « a » est transmise, puis reprise et modifiée.
        val sortie = Sortie(id = "s1", observations = listOf(transmise().apply { nombre = 6 }))
        store.ajouter(sortie)

        val res = t.services.envoiSortie.envoyerSortieVersGeoNature(sortie, config)

        assertTrue(res.succes, res.message)
        assertTrue("modification" in res.message, res.message)
        val relue = store.charger().single { it.id == "s1" }
        assertTrue(relue.envoyeGeoNature, "tout est transmis : la saisie est close")
        assertEquals(empreinteOccurrence(relue.observations.single()), relue.observations.single().empreinteEnvoi)
    }

    @Test
    fun de_bout_en_bout_une_mise_a_jour_en_echec_laisse_la_saisie_a_envoyer() = runTest {
        occurrenceSurLeServeur = null
        val store = t.services.sortieStore
        val sortie = Sortie(id = "s1", observations = listOf(transmise().apply { nombre = 6 }))
        store.ajouter(sortie)

        val res = t.services.envoiSortie.envoyerSortieVersGeoNature(sortie, config)

        assertFalse(res.succes)
        assertFalse(store.charger().single { it.id == "s1" }.envoyeGeoNature, "la modification n'est pas partie")
    }

    private fun marqueur(transmises: MutableList<Map<String, Pair<String?, String?>>>) = object : MarqueurEnvoiOcctax {
        override suspend fun occurrenceCreee(obsId: String, empreinte: String?, empreinteReleve: String?) {}
        override suspend fun empreintesTransmises(empreintes: Map<String, Pair<String?, String?>>) { transmises += empreintes }
        override suspend fun occurrenceIncertaine(obsId: String, idReleve: Int) {}
        override suspend fun occurrencesATenter(obsIds: List<String>, idReleve: Int) {}
        override suspend fun occurrenceEchecNet(obsId: String) {}
        override suspend fun releveATenter(obsIds: List<String>) {}
        override suspend fun releveEchecNet(obsIds: List<String>) {}
    }
}
