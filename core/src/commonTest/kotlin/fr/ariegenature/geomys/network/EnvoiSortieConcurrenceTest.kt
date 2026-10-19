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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/EnvoiSortieConcurrenceTest.kt) :
// Robolectric remplacé par ServicesDeTest (store, verrous et filet mémoire neufs à chaque test),
// MockWebServer par ServeurSimule (le délai de réponse par Reponse.delaiMs), JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.store.NomValeur
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest

/**
 * ENVOI CONCURRENT D'UNE MÊME SAISIE OCCTAX — anti-doublon serveur.
 *
 * RÉGRESSION COUVERTE (audit 2026-09-14, instruction du 2026-09-16) : les cinq points d'appel d'un
 * envoi Occtax n'avaient pour garde que des drapeaux `envoiEnCours` d'ÉCRAN, aveugles d'un écran à
 * l'autre. Cas réel : l'envoi est lancé depuis l'écran de détail, l'utilisateur revient à la liste —
 * qui n'a jamais posé son drapeau —, la saisie y est toujours « À envoyer » avec sa flèche, et un
 * second envoi part sans même un toast. Chaque envoi crée son PROPRE relevé, et rien ne les arrête
 * côté serveur : `unique_id_occurence_occtax` n'a aucune contrainte d'unicité dans GeoNature, et
 * `unique_id_sinp_occtax`, qui l'a, n'est jamais envoyé par l'application.
 *
 * Deux moitiés indissociables sont testées ici. Le VERROU de processus, qui empêche les deux envois
 * de se chevaucher — et la RELECTURE SOUS VERROU, sans laquelle le second envoi, une fois sérialisé,
 * repartirait de l'instantané chargé par son écran et reposterait tout ce que le premier vient de
 * créer : le doublon serait seulement décalé.
 */
class EnvoiSortieConcurrenceTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config
    private val store get() = t.services.sortieStore
    private val envoi get() = t.services.envoiSortie
    private val relevesPostes = atomic(0)
    private val occurrencesPostees = atomic(0)

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
        relevesPostes.value = 0
        occurrencesPostees.value = 0
    }

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun obs(id: String) = Observation(
        id = id, espece = "Merle", cdNom = 4001, latitude = 42.9, longitude = 1.4,
        nombre = 1, releveId = "grp1", date = 1_700_000_000_000L,
    )

    /** Serveur nominal, mais LENT sur le POST de relevé : c'est ce délai qui ouvre la fenêtre
     *  pendant laquelle l'utilisateur revient en arrière et relance un envoi. */
    private fun routerLent(delaiMs: Long = 300) {
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json("""{"access_token":"t","user":{"id_role":1}}""")
                chemin.contains("only/releve") -> {
                    val n = relevesPostes.incrementAndGet()
                    ServeurSimule.Reponse(
                        200, """{"id":$n,"properties":{}}""", listOf("Content-Type" to "application/json"),
                        panne = false, delaiMs = delaiMs,
                    )
                }
                chemin.contains("/occurrence") -> {
                    occurrencesPostees.incrementAndGet()
                    json("""{"id":900}""")
                }
                else -> json("{}")
            }
        }
    }

    @Test
    fun deux_envois_concurrents_de_la_meme_saisie_ne_creent_qu_un_seul_releve() = runTest {
        val sortie = Sortie(id = "s1", observations = listOf(obs("o1"), obs("o2")))
        store.ajouter(sortie)
        routerLent()

        val (r1, r2) = listOf(
            async { envoi.envoyerSortieVersGeoNature(sortie, config) },
            async { envoi.envoyerSortieVersGeoNature(sortie, config) },
        ).awaitAll()

        assertEquals(1, relevesPostes.value, "les deux écrans ont posté leur propre relevé : doublon en base régionale")
        assertEquals(2, occurrencesPostees.value, "chaque observation ne doit partir qu'une fois")
        // L'un des deux passe, l'autre est refusé explicitement — jamais deux succès silencieux.
        val refuses = listOf(r1, r2).filter { !it.succes }
        assertEquals(1, refuses.size, "exactement un envoi doit être refusé")
        assertTrue(refuses[0].message.contains("déjà en cours"), "le refus doit DIRE pourquoi : message obtenu = ${refuses[0].message}")
    }

    @Test
    fun un_envoi_sequentiel_sur_un_instantane_perime_ne_reposte_rien() = runTest {
        // Le second écran a chargé sa copie AVANT le premier envoi : ses observations y sont encore
        // « à envoyer ». Sans relecture sous verrou, il les reposterait toutes.
        val sortie = Sortie(id = "s1", observations = listOf(obs("o1"), obs("o2")))
        store.ajouter(sortie)
        val instantanePerime = sortie.copy()
        routerLent(delaiMs = 0)

        envoi.envoyerSortieVersGeoNature(sortie, config)
        assertEquals(1, relevesPostes.value)
        assertEquals(2, occurrencesPostees.value)

        val r = envoi.envoyerSortieVersGeoNature(instantanePerime, config)

        assertEquals(1, relevesPostes.value, "aucun relevé supplémentaire ne doit partir")
        assertEquals(2, occurrencesPostees.value, "aucune occurrence ne doit repartir")
        assertTrue(r.succes, "l'envoi doit se clore proprement, pas échouer")
    }

    @Test
    fun les_marquages_du_disque_survivent_a_une_reecriture_par_un_ecran_d_edition() = runTest {
        // « Continuer la saisie » réécrit la sortie avec la liste d'observations de son ViewModel,
        // instantanée à la reprise. Sans fusion préservante, les observations acquittées entre-temps
        // par un envoi en cours repassaient à « à envoyer » SUR LE DISQUE : le doublon survivait au
        // redémarrage et l'anti-doublon restait désarmé pour tous les envois suivants.
        val sortie = Sortie(id = "s1", observations = listOf(obs("o1"), obs("o2")))
        store.ajouter(sortie)
        assertTrue(store.marquerObservationsEnvoyees("s1", listOf("o1")))

        // L'écran d'édition réécrit avec SON instantané, pris avant le marquage.
        store.remplacer("s1", sortie.copy(observations = listOf(obs("o1"), obs("o2"), obs("o3"))))

        val relue = store.charger().first { it.id == "s1" }
        assertTrue(
            relue.observations.first { it.id == "o1" }.envoyeeServeur,
            "o1 avait été transmise à GeoNature : son marquage ne doit pas être effacé",
        )
        assertTrue(!relue.observations.first { it.id == "o2" }.envoyeeServeur, "les observations non transmises restent à envoyer")
        assertEquals(3, relue.observations.size, "l'édition doit bien avoir ajouté la nouvelle observation")
    }
}
