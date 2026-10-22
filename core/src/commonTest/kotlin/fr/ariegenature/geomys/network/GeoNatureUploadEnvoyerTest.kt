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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/GeoNatureUploadEnvoyerTest.kt) :
// Robolectric remplacé par ServicesDeTest, MockWebServer par ServeurSimule, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import fr.ariegenature.geomys.store.NomValeur
import io.ktor.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Flux d'envoi OccTax de bout en bout (POST relevé → POST occurrence(s) → rollback) sur le serveur
 *  simulé. Valide l'orchestration en plusieurs étapes et le contrat HTTP. */
class GeoNatureUploadEnvoyerTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config
    private val upload get() = t.services.upload

    private val typesNomenclature = listOf(
        "METH_OBS", "STATUT_OBS", "SEXE", "STADE_VIE", "STATUT_BIO", "ETA_BIO",
        "PREUVE_EXIST", "OBJ_DENBR", "TYP_DENBR", "OCC_COMPORTEMENT", "METH_DETERMIN", "TYPE_MEDIA",
    )

    @BeforeTest
    fun setup() {
        // Cache non vide pour TOUS les types → évite tout appel réseau de résolution de
        // nomenclatures pendant l'envoi (on se concentre sur le flux relevé/occurrence).
        t.services.nomenclatures.setAll(typesNomenclature.associateWith { listOf(NomValeur(1, "x")) })
        config.apply {
            urlServeur = serveur.base
            login = "alice"; motDePasse = "pwd"; idDataset = "12"
        }
    }

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun obs(id: String, releveId: String? = null, cdNom: Int? = 4001) = Observation(
        id = id, espece = "Merle", cdNom = cdNom, latitude = 42.9, longitude = 1.4,
        nombre = 1, releveId = releveId, date = 1_700_000_000_000L,
    )

    /** Route authentification + relevé + occurrence + DELETE selon des codes paramétrables. */
    private fun router(
        codeReleve: Int = 200,
        codeOccurrence: Int = 200,
        codeDelete: Int = 200,
    ) {
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.endsWith("/only/releve") -> json("""{"id":100}""", codeReleve)
                chemin.contains("/occurrence") -> json("{}", codeOccurrence)
                requete.method == HttpMethod.Delete -> json("{}", codeDelete)
                else -> statut(404)
            }
        }
    }

    @Test
    fun envoi_mono_taxon_cree_un_releve_et_une_occurrence() = runTest {
        router()
        val res = upload.envoyer(Sortie(observations = listOf(obs("o1"))), config)
        assertEquals(1, res.nbCrees)
        assertEquals(1, res.nbTotal)
        assertEquals(100, res.premierIdReleve)
        assertTrue(res.relevesOrphelins.isEmpty())
    }

    @Test
    fun envoi_multi_taxons_un_releve_plusieurs_occurrences() = runTest {
        router()
        val obs = listOf(obs("a", releveId = "R1"), obs("b", releveId = "R1"))
        val res = upload.envoyer(Sortie(observations = obs), config)
        assertEquals(2, res.nbCrees, "2 occurrences sur 1 relevé")
        assertEquals(2, res.nbTotal)
    }

    @Test
    fun occurrence_en_echec_declenche_rollback_et_leve_une_erreur() = runTest {
        router(codeReleve = 200, codeOccurrence = 500, codeDelete = 200)
        // Relevé supprimé avec succès (DELETE 200) → pas d'orphelin signalé dans le message.
        assertFailsWith<GNErreur.EnvoiEchoue> {
            upload.envoyer(Sortie(observations = listOf(obs("o1"))), config)
        }
    }

    @Test
    fun rollback_impossible_signale_un_releve_orphelin() = runTest {
        router(codeReleve = 200, codeOccurrence = 500, codeDelete = 500)
        val e = assertFailsWith<GNErreur.EnvoiEchoue> {
            upload.envoyer(Sortie(observations = listOf(obs("o1"))), config)
        }
        assertTrue(e.message!!.contains("100"), "doit mentionner le relevé orphelin 100")
    }

    @Test
    fun aucune_obs_avec_cd_nom_leve_une_erreur() = runTest {
        router()
        assertFailsWith<GNErreur.AucuneObservationCompatible> {
            upload.envoyer(Sortie(observations = listOf(obs("o1", cdNom = null))), config)
        }
    }

    /** Relevé SANS ESPÈCE : un remplaçant (releveSansEspece=true, cd_nom null) crée le relevé côté
     *  serveur SANS aucune occurrence, ne déclenche PAS de rollback (pas d'orphelin) et est compté
     *  comme créé + remonté dans obsCreesIds (anti-doublon au ré-envoi). */
    @Test
    fun releve_sans_espece_cree_le_releve_sans_occurrence_ni_rollback() = runTest {
        var nbOcc = 0
        var nbDelete = 0
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.endsWith("/only/releve") -> json("""{"id":100}""")
                chemin.contains("/occurrence") -> { nbOcc++; json("{}") }
                requete.method == HttpMethod.Delete -> { nbDelete++; json("{}") }
                else -> statut(404)
            }
        }
        val remplacant = obs("p1", releveId = "R1", cdNom = null).apply { releveSansEspece = true }
        val res = upload.envoyer(Sortie(observations = listOf(remplacant)), config)
        assertEquals(1, res.nbCrees, "le relevé vide compte comme créé")
        assertEquals(1, res.nbTotal)
        assertEquals(100, res.premierIdReleve)
        assertEquals(listOf("p1"), res.obsCreesIds)
        assertTrue(res.relevesOrphelins.isEmpty(), "pas de relevé orphelin")
        assertEquals(0, nbOcc, "aucune occurrence postée")
        assertEquals(0, nbDelete, "aucun rollback DELETE")
    }

    // ── Anti-doublon par uuid client + marquage au fil de l'eau (audit 2026-08-27) ──────────

    /** Chaque occurrence part avec son `unique_id_occurence_occtax` et le marqueur est appelé
     *  PENDANT l'envoi dès le 2xx (pas seulement au retour). */
    @Test
    fun occurrence_postee_avec_uuid_client_et_marquee_au_fil_de_l_eau() = runTest {
        val corps = mutableListOf<String>()
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.endsWith("/only/releve") -> json("""{"id":100}""")
                chemin.contains("/occurrence") -> { corps.add(requete.texte); json("{}") }
                else -> statut(404)
            }
        }
        val o = obs("o1").apply { uuidOccurrence = "dddddddd-1111-2222-3333-444444444444" }
        val crees = mutableListOf<String>()
        val aTenter = mutableListOf<String>()
        val marqueur = object : MarqueurEnvoiOcctax {
            override suspend fun occurrenceCreee(obsId: String, empreinte: String?, empreinteReleve: String?) { crees.add(obsId) }
            override suspend fun occurrenceIncertaine(obsId: String, idReleve: Int) {}
            override suspend fun occurrencesATenter(obsIds: List<String>, idReleve: Int) { aTenter.addAll(obsIds) }
            override suspend fun occurrenceEchecNet(obsId: String) {}
            override suspend fun releveATenter(obsIds: List<String>) {}
            override suspend fun releveEchecNet(obsIds: List<String>) {}
        }
        val res = upload.envoyer(Sortie(observations = listOf(o)), config, marqueur)
        assertEquals(1, res.nbCrees)
        assertEquals(listOf("o1"), crees)
        // Pré-marquage AVANT le POST : l'occurrence est déclarée « peut-être créée » dès l'émission,
        // sinon la mort du processus la laisserait sur GeoNature sans trace locale (R1-C1).
        assertEquals(listOf("o1"), aTenter, "le groupe doit être pré-marqué avant tout POST")
        assertTrue(corps.single().contains("\"unique_id_occurence_occtax\":\"dddddddd-1111-2222-3333-444444444444\""),
            "uuid client dans le corps")
    }

    /** Occurrence dont le POST précédent est resté sans réponse (idReleveIncertain) : si le relevé
     *  serveur la contient déjà (uuid retrouvé), elle est acquise SANS nouveau relevé ni re-POST. */
    @Test
    fun occurrence_incertaine_retrouvee_par_uuid_n_est_pas_repostee() = runTest {
        var nbReleves = 0
        var nbOcc = 0
        val uuid = "eeeeeeee-1111-2222-3333-444444444444"
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.endsWith("/releve/55") && requete.method == HttpMethod.Get -> json(
                    """{"releve":{"id":55,"properties":{"t_occurrences_occtax":[{"unique_id_occurence_occtax":"$uuid"}]}}}""")
                chemin.endsWith("/only/releve") -> { nbReleves++; json("""{"id":100}""") }
                chemin.contains("/occurrence") -> { nbOcc++; json("{}") }
                else -> statut(404)
            }
        }
        val o = obs("o1").apply { uuidOccurrence = uuid; idReleveIncertain = 55 }
        val res = upload.envoyer(Sortie(observations = listOf(o)), config)
        assertEquals(1, res.nbCrees, "acquise")
        assertEquals(listOf("o1"), res.obsCreesIds)
        assertEquals(0, nbReleves, "aucun nouveau relevé")
        assertEquals(0, nbOcc, "aucun re-POST")
    }

    /** Vérification IMPOSSIBLE (relevé en 500) : pas de re-POST aveugle — l'obs reste à envoyer. */
    @Test
    fun occurrence_incertaine_verification_impossible_pas_de_repost() = runTest {
        var nbOcc = 0
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.endsWith("/releve/55") && requete.method == HttpMethod.Get -> json("{}", 500)
                chemin.contains("/occurrence") -> { nbOcc++; json("{}") }
                else -> statut(404)
            }
        }
        val o = obs("o1").apply { idReleveIncertain = 55 }
        val e = assertFailsWith<GNErreur.EnvoiEchoue>("attendu : EnvoiEchoue (rien créé)") {
            upload.envoyer(Sortie(observations = listOf(o)), config)
        }
        assertTrue(e.message!!.contains("anti-doublon"))
        assertEquals(0, nbOcc, "aucun re-POST aveugle")
    }

    @Test
    fun releve_sans_jeu_de_donnees_leve_une_erreur_claire() = runTest {
        // Ni jeu figé dans le relevé, ni jeu par défaut : le jeu est obligatoire, comme sur le web.
        router()
        config.idDataset = ""
        val e = assertFailsWith<GNErreur.EnvoiEchoue>("attendu : GNErreur.EnvoiEchoue (dataset)") {
            upload.envoyer(Sortie(observations = listOf(obs("o1"))), config)
        }
        assertTrue(e.message!!.contains("Jeu de données manquant"), e.message)
    }

    // ── Envoi partiel & ré-envoi (audit 2026-07 : une seule occurrence créée verrouillait toute la
    //    sortie ; les obs restantes étaient perdues) ─────────────────────────────────────────────

    /** Le réseau tombe entre le groupe 1 et le groupe 2 : l'envoi NE lève PAS (acquis préservé),
     *  remonte nbCrees < nbTotal et la liste exacte des obs créées — c'est elle que l'appelant
     *  persiste pour que le ré-envoi ne re-poste pas le groupe 1. */
    @Test
    fun envoi_partiel_remonte_les_obs_creees_sans_lever() = runTest {
        var nbReleves = 0
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.endsWith("/only/releve") ->
                    if (++nbReleves == 1) json("""{"id":100}""") else json("{}", 500)
                chemin.contains("/occurrence") -> json("{}")
                else -> statut(404)
            }
        }
        // Deux obs mono-taxon = deux groupes/relevés distincts.
        val sortie = Sortie(observations = listOf(obs("a"), obs("b")))
        val res = upload.envoyer(sortie, config)
        assertEquals(1, res.nbCrees)
        assertEquals(2, res.nbTotal)
        assertEquals(listOf("a"), res.obsCreesIds)
        assertTrue(res.messageDerniereErreur != null,
            "l'erreur du groupe 2 doit être remontée pour le message utilisateur")
    }

    /** Ré-envoi après succès partiel : les obs marquées envoyeeServeur ne repartent PAS
     *  (anti-doublon) — une seule occurrence est postée, l'autre est comptée « déjà envoyée ». */
    @Test
    fun reenvoi_ignore_les_obs_deja_envoyees() = runTest {
        var nbOccurrences = 0
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.endsWith("/only/releve") -> json("""{"id":100}""")
                chemin.contains("/occurrence") -> { nbOccurrences++; json("{}") }
                else -> statut(404)
            }
        }
        val deja = obs("a").apply { envoyeeServeur = true }
        val res = upload.envoyer(Sortie(observations = listOf(deja, obs("b"))), config)
        assertEquals(1, res.nbCrees)
        assertEquals(1, res.nbTotal)
        assertEquals(1, res.nbDejaEnvoyees)
        assertEquals(listOf("b"), res.obsCreesIds)
        assertEquals(1, nbOccurrences, "une seule occurrence doit avoir été postée")
    }

    /** Toutes les obs déjà créées (échec précédent APRÈS le dernier groupe) : rien à poster,
     *  résultat 0/0 → l'appelant clôture la sortie sans aucun POST relevé/occurrence. */
    @Test
    fun tout_deja_envoye_retourne_zero_sur_zero() = runTest {
        router()
        val sortie = Sortie(observations = listOf(
            obs("a").apply { envoyeeServeur = true },
            obs("b").apply { envoyeeServeur = true },
        ))
        val res = upload.envoyer(sortie, config)
        assertEquals(0, res.nbCrees)
        assertEquals(0, res.nbTotal)
        assertEquals(2, res.nbDejaEnvoyees)
    }

    private companion object {
        const val LOGIN = """{"access_token":"t","user":{"id_role":1}}"""
    }
}
