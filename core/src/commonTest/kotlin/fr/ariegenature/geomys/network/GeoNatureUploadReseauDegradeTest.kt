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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/GeoNatureUploadReseauDegradeTest.kt) :
// Robolectric remplacé par ServicesDeTest, MockWebServer par ServeurSimule (la coupure de socket
// après lecture de la requête devient ServeurSimule.panne), JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.panne
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import fr.ariegenature.geomys.store.NomValeur
import io.ktor.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** Flux d'envoi OccTax face à un réseau DÉGRADÉ : coupures en plein POST (mode avion, zone
 *  blanche), réponses serveur inattendues (HTML au lieu de JSON, id absent), jeton expiré. Vérifie
 *  qu'aucun scénario ne laisse un relevé vide non signalé côté GeoNature et que l'erreur remonte
 *  toujours en [GNErreur] humanisable — jamais en IOException brute. */
class GeoNatureUploadReseauDegradeTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config
    private val upload get() = t.services.upload
    private var deletes = 0

    private val typesNomenclature = listOf(
        "METH_OBS", "STATUT_OBS", "SEXE", "STADE_VIE", "STATUT_BIO", "ETA_BIO",
        "PREUVE_EXIST", "OBJ_DENBR", "TYP_DENBR", "OCC_COMPORTEMENT", "METH_DETERMIN", "TYPE_MEDIA",
    )

    @BeforeTest
    fun setup() {
        deletes = 0
        t.services.nomenclatures.setAll(typesNomenclature.associateWith { listOf(NomValeur(1, "x")) })
        config.apply {
            urlServeur = serveur.base
            login = "alice"; motDePasse = "pwd"; idDataset = "12"
        }
    }

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun obs(id: String, releveId: String? = null) = Observation(
        id = id, espece = "Merle", cdNom = 4001, latitude = 42.9, longitude = 1.4,
        nombre = 1, releveId = releveId, date = 1_700_000_000_000L,
    )

    /** La coupure est rendue APRÈS réception de la requête, avant toute réponse → IOException côté
     *  client (la SocketPolicy.DISCONNECT_AFTER_REQUEST de MockWebServer). */
    private fun reponse(code: Int, corps: String): ServeurSimule.Reponse =
        if (code == PANNE) panne() else json(corps, code)

    /** Route authentification + relevé + occurrence + DELETE. [PANNE] sur un code = coupure réseau. */
    private fun router(
        codeReleve: Int = 200,
        corpsReleve: String = """{"id":100}""",
        codeOccurrence: Int = 200,
        codeDelete: Int = 200,
    ) {
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> reponse(200, LOGIN)
                chemin.endsWith("/only/releve") -> reponse(codeReleve, corpsReleve)
                chemin.contains("/occurrence") -> reponse(codeOccurrence, "{}")
                requete.method == HttpMethod.Delete -> {
                    deletes++
                    reponse(codeDelete, "{}")
                }
                // Dump d'un relevé : la purge des orphelins s'en sert pour vérifier qu'un relevé
                // mémorisé est TOUJOURS vide avant de le supprimer (un relevé réutilisé entre-temps
                // contiendrait des occurrences transmises). Le scénario de ce fichier est « relevé
                // sans taxon resté sur le serveur » : le dump est donc celui d'un relevé existant
                // et vide.
                chemin.contains("/releve/") -> reponse(200, """{"id_releve_occtax":100}""")
                else -> statut(404)
            }
        }
    }

    private suspend fun TestScope.envoyerEtAttendreEchec(sortie: Sortie): GNErreur.EnvoiEchoue =
        assertFailsWith<GNErreur.EnvoiEchoue>("une GNErreur.EnvoiEchoue était attendue") {
            upload.envoyer(sortie, config)
        }

    // ── Coupures réseau en plein POST ──────────────────────────────────────────────

    @Test
    fun coupure_reseau_pendant_le_releve_remonte_une_GNErreur_pas_une_IOException() = runTest {
        router(codeReleve = PANNE)
        val e = envoyerEtAttendreEchec(Sortie(observations = listOf(obs("o1"))))
        assertTrue(e.message!!.contains("Réseau interrompu"), "le message doit expliquer la coupure : ${e.message}")
        // Aucun relevé créé côté serveur → rien à annuler, pas d'orphelin.
        assertEquals(0, deletes)
    }

    @Test
    fun coupure_reseau_pendant_l_occurrence_rollback_le_releve() = runTest {
        router(codeOccurrence = PANNE, codeDelete = 200)
        val e = envoyerEtAttendreEchec(Sortie(observations = listOf(obs("o1"))))
        assertTrue(e.message!!.contains("Réseau interrompu"))
        // Le relevé 100 créé puis resté vide doit avoir été supprimé (DELETE reçu).
        assertEquals(1, deletes, "le relevé vide doit être annulé")
        // DELETE accepté → pas d'orphelin signalé.
        assertTrue(!e.message!!.contains("orphelin") && !e.message!!.contains("à supprimer manuellement"),
            "pas d'orphelin attendu : ${e.message}")
    }

    @Test
    fun coupure_reseau_sur_occurrence_puis_rollback_signale_un_orphelin() = runTest {
        router(codeOccurrence = PANNE, codeDelete = PANNE)
        val e = envoyerEtAttendreEchec(Sortie(observations = listOf(obs("o1"))))
        // DELETE impossible (réseau toujours coupé) → le relevé 100 doit être signalé.
        assertTrue(e.message!!.contains("100"), "doit signaler le relevé orphelin 100 : ${e.message}")
    }

    @Test
    fun orphelin_memorise_puis_supprime_automatiquement_a_l_envoi_suivant() = runTest {
        // Scénario terrain (mode avion en plein transfert) : relevé créé, occurrence ET rollback en
        // échec → « relevé sans taxon » resté sur le serveur.
        router(codeOccurrence = PANNE, codeDelete = PANNE)
        envoyerEtAttendreEchec(Sortie(observations = listOf(obs("o1"))))
        assertEquals(listOf(100), t.services.relevesOrphelins.liste(config.urlServeur),
            "l'orphelin doit être mémorisé pour nettoyage différé")
        val deletesApresPanne = deletes

        // Réseau rétabli, l'utilisateur renvoie (nouvelle sortie ou la même) : l'envoi commence par
        // purger les orphelins mémorisés.
        router() // tout passe désormais
        val res = upload.envoyer(Sortie(observations = listOf(obs("o2"))), config)
        assertEquals(1, res.nbCrees)
        assertEquals(1, deletes - deletesApresPanne, "le DELETE de l'orphelin doit être passé")
        assertTrue(t.services.relevesOrphelins.liste(config.urlServeur).isEmpty(),
            "plus d'orphelin mémorisé après nettoyage")
    }

    // ── Réponses serveur dégradées ─────────────────────────────────────────────────

    @Test
    fun reponse_releve_200_sans_id_remonte_une_erreur_claire() = runTest {
        router(corpsReleve = """{"statut":"ok"}""")
        val e = envoyerEtAttendreEchec(Sortie(observations = listOf(obs("o1"))))
        assertTrue(e.message!!.contains("id absent"), "doit mentionner l'id absent : ${e.message}")
    }

    @Test
    fun reponse_releve_200_html_remonte_une_erreur_claire() = runTest {
        // Cas réel : proxy inverse ou SPA qui renvoie une page HTML avec un code 200.
        router(corpsReleve = "<html><body>maintenance</body></html>")
        val e = envoyerEtAttendreEchec(Sortie(observations = listOf(obs("o1"))))
        assertTrue(e.message!!.contains("non-JSON"), "doit mentionner la réponse non-JSON : ${e.message}")
    }

    @Test
    fun erreur_500_avec_corps_html_ne_crashe_pas_le_parse() = runTest {
        router(codeReleve = 500, corpsReleve = "<html>Internal Server Error</html>")
        val e = envoyerEtAttendreEchec(Sortie(observations = listOf(obs("o1"))))
        assertEquals(500, e.code)
        assertTrue(e.message!!.contains("500"))
    }

    @Test
    fun occurrence_401_token_expire_rollback_et_remonte_le_code() = runTest {
        router(codeOccurrence = 401)
        val e = envoyerEtAttendreEchec(Sortie(observations = listOf(obs("o1"))))
        assertEquals(401, e.code, "le code 401 doit remonter pour le message « reconnecte-toi »")
        assertEquals(1, deletes, "le relevé vide doit être annulé")
    }

    // ── Envoi partiel sur plusieurs relevés ────────────────────────────────────────

    @Test
    fun envoi_partiel_seul_le_groupe_en_echec_est_rollback() = runTest {
        // Deux relevés distincts (releveId R1/R2) : la 1re occurrence passe, la 2e échoue.
        var nbOccurrences = 0
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> reponse(200, LOGIN)
                chemin.endsWith("/only/releve") -> reponse(200, """{"id":100}""")
                chemin.contains("/occurrence") ->
                    if (++nbOccurrences == 1) reponse(200, "{}") else reponse(500, """{"description":"boom"}""")
                requete.method == HttpMethod.Delete -> {
                    deletes++
                    reponse(200, "{}")
                }
                else -> statut(404)
            }
        }
        val sortie = Sortie(observations = listOf(obs("a", releveId = "R1"), obs("b", releveId = "R2")))
        val res = upload.envoyer(sortie, config)
        assertEquals(1, res.nbCrees, "1 occurrence créée sur 2")
        assertEquals(2, res.nbTotal)
        assertEquals(1, deletes, "seul le relevé du groupe en échec est annulé")
        assertTrue(res.relevesOrphelins.isEmpty())
    }

    private companion object {
        const val LOGIN = """{"access_token":"t","user":{"id_role":1}}"""

        /** Code sentinelle : coupe la connexion sans répondre (IOException côté client). */
        const val PANNE = -1
    }
}
