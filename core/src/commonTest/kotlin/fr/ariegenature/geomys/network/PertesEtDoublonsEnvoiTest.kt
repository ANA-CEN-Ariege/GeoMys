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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/PertesEtDoublonsEnvoiTest.kt) :
// Robolectric remplacé par ServicesDeTest (orphelins, store et filet mémoire neufs à chaque test),
// MockWebServer par ServeurSimule (les requêtes reçues par ServeurSimule.requetes), JUnit par
// kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.store.NomValeur
import io.ktor.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * CE QUE LE SERVEUR A VRAIMENT FAIT N'EST PAS CE QUE LE CLIENT CROIT (portage iOS, 2026-10-17).
 *
 * Quatre défauts du chemin d'envoi Occtax, relevés en portant le code sur iOS. Deux détruisent des
 * données DÉJÀ acceptées par GeoNature (rollback d'un relevé réutilisé, purge d'un orphelin
 * repeuplé), deux fabriquent des doublons au renvoi (5xx pris pour un refus, réponse illisible prise
 * pour une absence). C'est la cinquième occurrence du même schéma que le lot B de septembre, cette
 * fois sur le chemin de VÉRIFICATION et non d'envoi.
 */
class PertesEtDoublonsEnvoiTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config
    private val orphelins get() = t.services.relevesOrphelins

    /** Les requêtes reçues, « MÉTHODE chemin ». */
    private val requetes: List<String>
        get() = serveur.requetes.map { "${it.method.value} ${it.url.encodedPathAndQuery}" }

    private val typesNomenclature = listOf(
        "METH_OBS", "STATUT_OBS", "SEXE", "STADE_VIE", "STATUT_BIO", "ETA_BIO",
        "PREUVE_EXIST", "OBJ_DENBR", "TYP_DENBR", "OCC_COMPORTEMENT", "METH_DETERMIN", "TYPE_MEDIA",
    )

    @BeforeTest
    fun setup() {
        t.services.nomenclatures.setAll(typesNomenclature.associateWith { listOf(NomValeur(1, "x")) })
        config.apply {
            urlServeur = serveur.base
            login = "alice"; motDePasse = "pwd"; idDataset = "12"
        }
    }

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun obs(id: String, envoyee: Boolean = false, tente: Boolean = true) = Observation(
        id = id, espece = "Merle", cdNom = 4001, latitude = 42.9, longitude = 1.4,
        nombre = 1, releveId = "grp1", date = 1_700_000_000_000L,
        releveTente = tente, envoyeeServeur = envoyee,
    )

    private fun sortie(vararg observations: Observation) =
        Sortie(id = "s1", date = 1_700_000_000_000L, observations = observations.toList())

    /** Le relevé 42 porte l'uuid que l'application dérive du groupe « grp1 ». */
    private fun releveTrouve() =
        """{"items":{"features":[{"properties":{"id_releve_occtax":42,""" +
            """"unique_id_sinp_grp":"${GeoNatureUpload.uuidReleveGroupe("grp1")}"}}]}}"""

    private fun router(
        rechercheReleve: String = releveTrouve(),
        codeOccurrence: Int = 200,
        dumpReleve: String = """{"id_releve_occtax":42}""",
    ) {
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.contains("/releves?") -> json(rechercheReleve)
                chemin.endsWith("/only/releve") -> json("""{"id":77}""")
                chemin.contains("/occurrence") -> json("{}", codeOccurrence)
                requete.method == HttpMethod.Delete -> json("{}")
                // GET /releve/<id> : le dump, qui sert à juger si un relevé est vide.
                chemin.contains("/releve/") -> json(dumpReleve)
                else -> json("{}")
            }
        }
    }

    private suspend fun envoyer(s: Sortie, marqueur: MarqueurEnvoiOcctax? = null) =
        try { t.services.upload.envoyer(s, config, marqueur) } catch (_: GNErreur.EnvoiEchoue) { null }

    private fun aucunDelete(message: String) =
        assertTrue(requetes.none { it.startsWith("DELETE") }, "$message — requêtes : $requetes")

    // ── 1. Le rollback ne doit jamais détruire un relevé RÉUTILISÉ ───────────

    /**
     * Envoi précédent interrompu : le relevé 42 existe côté serveur avec l'occurrence A dedans. Au
     * ré-envoi, seule B repart (A est marquée envoyée, donc exclue du lot) — et B est refusée. Aucune
     * occurrence n'ayant abouti DANS CET ENVOI, le rollback se déclenchait et supprimait le relevé
     * 42 : GeoNature emportait A en cascade, une donnée acceptée la veille.
     */
    @Test
    fun un_releve_reutilise_nest_jamais_supprime_par_le_rollback() = runTest {
        router(codeOccurrence = 400)
        envoyer(sortie(obs("A", envoyee = true), obs("B")))
        aucunDelete("le relevé réutilisé contient des occurrences déjà transmises")
    }

    /** CONTRE-ÉPREUVE : un relevé que CET envoi vient de créer et qui reste vide doit bien être
     *  supprimé — sinon le correctif aurait seulement désactivé le rollback. */
    @Test
    fun un_releve_cree_par_cet_envoi_et_reste_vide_est_bien_supprime() = runTest {
        router(rechercheReleve = """{"items":{"features":[]}}""", codeOccurrence = 400)
        envoyer(sortie(obs("A", tente = false)))
        assertTrue(
            requetes.any { it.startsWith("DELETE") },
            "un relevé vide créé à l'instant doit être nettoyé — requêtes : $requetes",
        )
    }

    // ── 2. Un 5xx ne vaut pas rejet franc ────────────────────────────────────

    private class MarqueurEspion : MarqueurEnvoiOcctax {
        val incertitudesLevees = mutableListOf<String>()
        val preMarquees = mutableListOf<String>()
        override suspend fun occurrenceCreee(obsId: String, empreinte: String?, empreinteReleve: String?) {}
        override suspend fun occurrenceIncertaine(obsId: String, idReleve: Int) {}
        override suspend fun occurrencesATenter(obsIds: List<String>, idReleve: Int) { preMarquees += obsIds }
        override suspend fun occurrenceEchecNet(obsId: String) { incertitudesLevees += obsId }
        override suspend fun releveATenter(obsIds: List<String>) {}
        override suspend fun releveEchecNet(obsIds: List<String>) {}
    }

    /**
     * Un proxy peut rendre 502 sur un POST que GeoNature a pourtant honoré. Lever l'incertitude du
     * pré-marquage efface alors la seule trace qui aurait déclenché la vérification par uuid au
     * renvoi : l'occurrence repart, en double.
     */
    @Test
    fun une_erreur_5xx_garde_lincertitude_du_pre_marquage() = runTest {
        router(codeOccurrence = 502)
        val espion = MarqueurEspion()
        envoyer(sortie(obs("A")), espion)
        assertEquals(listOf("A"), espion.preMarquees, "l'occurrence doit avoir été pré-marquée")
        assertTrue(
            espion.incertitudesLevees.isEmpty(),
            "un 502 ne prouve pas que rien n'a été créé : l'incertitude doit rester",
        )
    }

    /** CONTRE-ÉPREUVE : un vrai refus (4xx) doit bien lever l'incertitude, sinon chaque envoi
     *  ultérieur paierait une vérification par uuid inutile. */
    @Test
    fun un_refus_4xx_leve_bien_lincertitude() = runTest {
        router(codeOccurrence = 400)
        val espion = MarqueurEspion()
        envoyer(sortie(obs("A")), espion)
        assertEquals(listOf("A"), espion.incertitudesLevees)
    }

    // ── 3. Une réponse illisible n'est pas une absence ───────────────────────

    /**
     * La vérification par uuid distinguait « aucun résultat » de « le serveur ignore le filtre »,
     * mais pas de « je n'ai pas su lire la réponse » : une enveloppe JSON inconnue était traitée
     * comme une liste vide, donc comme « le relevé n'existe pas » — et l'application en créait un
     * second.
     */
    @Test
    fun une_reponse_de_forme_inconnue_ne_fait_pas_recreer_le_releve() = runTest {
        router(rechercheReleve = """{"resultat":[{"id_releve_occtax":42}]}""")
        envoyer(sortie(obs("A")))
        assertTrue(
            requetes.none { it.contains("/only/releve") },
            "aucun relevé ne doit être créé sur une réponse qu'on n'a pas comprise — requêtes : $requetes",
        )
    }

    /** CONTRE-ÉPREUVE : un tableau RECONNU et vide est une vraie réponse « aucun », qui doit
     *  autoriser la création. */
    @Test
    fun un_tableau_reconnu_et_vide_autorise_la_creation() = runTest {
        router(rechercheReleve = """{"items":{"features":[]}}""")
        envoyer(sortie(obs("A")))
        assertTrue(
            requetes.any { it.contains("/only/releve") },
            "une réponse vide reconnue signifie bien « relevé absent » — requêtes : $requetes",
        )
    }

    // ── 4. Un orphelin repeuplé n'est plus un orphelin ───────────────────────

    /**
     * Un relevé mémorisé comme vide (DELETE en échec) peut avoir été réutilisé entre-temps et
     * contenir désormais des occurrences. La purge de début d'envoi le supprimait sans rien
     * vérifier, emportant leur contenu.
     */
    @Test
    fun un_orphelin_qui_contient_des_occurrences_nest_pas_supprime() = runTest {
        orphelins.ajouter(config.urlServeur, listOf(42))
        router(dumpReleve = """{"id_releve_occtax":42,"occurrences":[{"id_occurrence_occtax":9}]}""")
        envoyer(sortie(obs("A", tente = false)))
        aucunDelete("le relevé 42 n'est plus vide")
        assertEquals(emptyList(), orphelins.liste(config.urlServeur), "il ne doit plus être suivi comme orphelin")
    }

    /** CONTRE-ÉPREUVE : un orphelin réellement vide doit toujours être supprimé. */
    @Test
    fun un_orphelin_reellement_vide_est_supprime() = runTest {
        orphelins.ajouter(config.urlServeur, listOf(42))
        router(dumpReleve = """{"id_releve_occtax":42,"occurrences":[]}""")
        envoyer(sortie(obs("A", tente = false)))
        assertTrue(
            requetes.any { it.startsWith("DELETE") && it.contains("/releve/42") },
            "un relevé vide doit être nettoyé — requêtes : $requetes",
        )
    }

    /** Vérification impossible (5xx) : on ne supprime rien et on garde l'orphelin pour plus tard. */
    @Test
    fun un_orphelin_invérifiable_est_conserve_sans_etre_supprime() = runTest {
        orphelins.ajouter(config.urlServeur, listOf(42))
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.contains("/releve/42") -> json("{}", 503)
                chemin.contains("/releves?") -> json("""{"items":{"features":[]}}""")
                chemin.endsWith("/only/releve") -> json("""{"id":77}""")
                chemin.contains("/occurrence") -> json("{}")
                else -> json("{}")
            }
        }
        envoyer(sortie(obs("A", tente = false)))
        aucunDelete("on ne supprime jamais sur un doute")
        assertEquals(listOf(42), orphelins.liste(config.urlServeur), "l'orphelin reste à nettoyer au prochain envoi")
    }

    // ── 5. Une observation ajoutée PENDANT l'envoi ne doit pas être enterrée ──

    /**
     * Le verrou d'envoi interdit deux envois simultanés, pas une ÉDITION concurrente : un envoi avec
     * photos dure assez longtemps pour qu'une espèce soit ajoutée entre-temps. Elle n'entre dans
     * aucun total de l'envoi en cours (calculés sur l'instantané relu au départ) ; marquer la saisie
     * « envoyée » la faisait sortir de « Mes saisies » sans qu'elle soit jamais partie.
     *
     * L'ajout est simulé à l'instant exact du POST de l'occurrence, depuis l'aiguillage du serveur.
     */
    @Test
    fun une_observation_ajoutee_pendant_lenvoi_laisse_la_saisie_a_envoyer() = runTest {
        val store = t.services.sortieStore
        val initiale = sortie(obs("A", tente = false))
        store.ajouter(initiale)

        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.endsWith("/only/releve") -> json("""{"id":77}""")
                chemin.contains("/occurrence") -> {
                    // L'observateur ajoute une espèce pendant que l'envoi tourne.
                    store.charger().find { it.id == "s1" }?.let { s ->
                        store.remplacer("s1", s.copy(observations = s.observations + obs("B", tente = false)))
                    }
                    json("{}")
                }
                else -> json("{}")
            }
        }

        val res = t.services.envoiSortie.envoyerSortieVersGeoNature(initiale, config)
        assertTrue(res.succes, "l'envoi de A a réussi")

        val apres = store.charger().find { it.id == "s1" }!!
        assertFalse(apres.envoyeGeoNature, "la saisie ne doit PAS être close : B n'a jamais été transmise")
        assertTrue(apres.observations.first { it.id == "A" }.envoyeeServeur, "A reste marquée transmise")
        assertFalse(apres.observations.first { it.id == "B" }.envoyeeServeur, "B reste à envoyer")
        assertTrue(res.message.contains("ajoutée(s) pendant l'envoi"), "le message doit le dire")
    }

    /** CONTRE-ÉPREUVE : sans ajout concurrent, la saisie est bien close. */
    @Test
    fun sans_ajout_concurrent_la_saisie_est_marquee_envoyee() = runTest {
        val store = t.services.sortieStore
        val initiale = sortie(obs("A", tente = false))
        store.ajouter(initiale)
        router(rechercheReleve = """{"items":{"features":[]}}""")

        val res = t.services.envoiSortie.envoyerSortieVersGeoNature(initiale, config)
        assertTrue(res.succes)
        assertTrue(store.charger().find { it.id == "s1" }!!.envoyeGeoNature, "une saisie entièrement transmise doit être close")
    }

    private companion object {
        const val LOGIN = """{"access_token":"t","user":{"id_role":1}}"""
    }
}
