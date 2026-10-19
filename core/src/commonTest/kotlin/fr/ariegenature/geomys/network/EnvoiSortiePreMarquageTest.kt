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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/EnvoiSortiePreMarquageTest.kt) :
// Robolectric remplacé par ServicesDeTest, MockWebServer par ServeurSimule, JUnit par kotlin.test.
// Le disque se relit dans l'aiguillage du serveur, qui n'est pas suspendu : directement dans le
// fichier du store, par okio.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import okio.FileSystem
import okio.SYSTEM

/**
 * PRÉ-MARQUAGE AVANT LE POST D'UNE OCCURRENCE OCCTAX (audit 2026-09-14, R1-C1).
 *
 * Jusqu'ici, le seul marquage persistant d'une occurrence en vol était dans le `catch (IOException)`
 * et le succès n'était écrit qu'APRÈS la réponse. Si le système tuait le processus entre l'émission
 * et la réponse — ce qui arrive précisément quand l'envoi est long, donc sur un réseau de terrain —,
 * l'occurrence existait côté serveur et l'appareil l'ignorait. Au ré-envoi elle repartait sans la
 * moindre vérification, le filtre anti-doublon étant justement indexé sur `idReleveIncertain`, qui
 * n'avait jamais été posé. Doublon dans Occtax, donc dans la synthèse nationale, sans trace locale
 * permettant seulement de le soupçonner. Le `NonCancellable` protège de l'annulation de coroutine,
 * pas de la mort du processus.
 *
 * On ne peut pas tuer le processus dans un test — mais on peut observer ce que le DISQUE contient à
 * l'instant précis où le serveur reçoit le POST : c'est exactement l'état que retrouverait
 * l'application au redémarrage.
 */
class EnvoiSortiePreMarquageTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config
    private val store get() = t.services.sortieStore

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

    private fun obs(id: String) = Observation(
        id = id, espece = "Merle", cdNom = 4001, latitude = 42.9, longitude = 1.4,
        nombre = 1, releveId = "grp1", date = 1_700_000_000_000L,
    )

    /** Relit la sortie DEPUIS LE DISQUE, sans passer par le cache mémoire du store — c'est ce que
     *  ferait l'application au redémarrage après avoir été tuée. */
    private fun depuisLeDisque(): Sortie {
        val texte = FileSystem.SYSTEM.read(t.dossier / "fichiers" / "sorties_store.json") { readUtf8() }
        return JSON_STOCKAGE.decodeFromString(ListSerializer(Sortie.serializer()), texte).first { it.id == "s1" }
    }

    @Test
    fun l_incertitude_est_sur_le_DISQUE_avant_que_le_serveur_ne_reponde() = runTest {
        val sortie = Sortie(id = "s1", observations = listOf(obs("o1"), obs("o2")))
        store.ajouter(sortie)

        // Observé DANS l'aiguillage : au moment où le serveur reçoit le POST de la 1ʳᵉ occurrence,
        // que contient le disque de l'appareil ? C'est l'état exact qu'on retrouverait si le
        // processus mourait à cette seconde.
        var incertainsAuMomentDuPost: List<Int?>? = null
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.endsWith("/only/releve") -> json("""{"id":100}""")
                chemin.contains("/occurrence") -> {
                    if (incertainsAuMomentDuPost == null) {
                        incertainsAuMomentDuPost = depuisLeDisque().observations.map { it.idReleveIncertain }
                    }
                    json("{}")
                }
                else -> json("", 404)
            }
        }

        t.services.envoiSortie.envoyerSortieVersGeoNature(store.charger().first(), config)

        assertNotNull(incertainsAuMomentDuPost, "le serveur n'a jamais reçu de POST d'occurrence")
        assertEquals(
            listOf(100, 100), incertainsAuMomentDuPost,
            "les deux occurrences du groupe doivent être « peut-être créées » SUR LE DISQUE " +
                "avant que le serveur ne réponde — sinon la mort du processus ici produit un doublon",
        )

        // Une fois le lot passé, plus aucune incertitude ne subsiste : chaque succès la lève.
        val finale = depuisLeDisque()
        assertEquals(
            listOf(null, null), finale.observations.map { it.idReleveIncertain },
            "l'incertitude doit être levée par les succès",
        )
        assertEquals(listOf(true, true), finale.observations.map { it.envoyeeServeur })
    }

    @Test
    fun un_rejet_franc_du_serveur_leve_l_incertitude() = runTest {
        // 422 = le serveur a REFUSÉ l'occurrence : rien n'a été créé. Laisser l'incertitude coûterait
        // une vérification par uuid à chaque envoi ultérieur, pour rien.
        val sortie = Sortie(id = "s1", observations = listOf(obs("o1")))
        store.ajouter(sortie)
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.endsWith("/only/releve") -> json("""{"id":100}""")
                chemin.contains("/occurrence") -> json("""{"msg":"refus"}""", 422)
                else -> json("{}")
            }
        }

        t.services.envoiSortie.envoyerSortieVersGeoNature(store.charger().first(), config)

        val finale = depuisLeDisque()
        assertNull(
            finale.observations.first().idReleveIncertain,
            "un rejet 4xx ne crée rien : l'incertitude du pré-marquage doit être levée",
        )
        assertEquals(false, finale.observations.first().envoyeeServeur)
    }

    private companion object {
        const val LOGIN = """{"access_token":"t","user":{"id_role":1}}"""
    }
}
