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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/EnvoiReleveAntiDoublonTest.kt) :
// Robolectric remplacé par ServicesDeTest (store et filet mémoire neufs à chaque test), MockWebServer
// par ServeurSimule, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.store.NomValeur
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ANTI-DOUBLON DU RELEVÉ OCCTAX par uuid client (audit 2026-09-14, R1-M1).
 *
 * Le relevé ne portait aucune identité client, et trois chemins laissaient un relevé
 * potentiellement créé sans la moindre trace locale (réseau coupé, réponse non JSON, id absent de la
 * réponse). Au ré-envoi, l'application en créait un second.
 *
 * Pour un relevé ordinaire, le surcoût restait un relevé VIDE — que l'application sait déjà détecter
 * et supprimer. Mais pour un RELEVÉ SANS ESPÈCE — un passage sans observation, donnée d'absence
 * parfaitement légitime que la saisie multi-taxons produit dès qu'on enregistre sans espèce — le
 * relevé EST la donnée : le re-créer produit un doublon franc d'absence en base régionale, qu'aucune
 * contrainte serveur n'arrête.
 *
 * L'identité est `unique_id_sinp_grp`, colonne de `TRelevesOccurrence` que le filtre générique du
 * backend sait exploiter, et elle est DÉTERMINISTE (dérivée de la clé de regroupement locale) :
 * l'application peut interroger le serveur à son sujet même après un redémarrage, sans avoir eu à
 * persister l'uuid.
 */
class EnvoiReleveAntiDoublonTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val config get() = t.services.config
    private val store get() = t.services.sortieStore
    private val envoi get() = t.services.envoiSortie
    private var relevesCrees = 0
    private var occurrencesPostees = 0
    private val relevesCibles = mutableListOf<String>()
    private var corpsReleve: String? = null
    private var appelsAltitude = 0

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
        relevesCrees = 0; occurrencesPostees = 0; appelsAltitude = 0
        relevesCibles.clear(); corpsReleve = null
    }

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun obs(id: String, cdNom: Int? = 4001, tente: Boolean = false) = Observation(
        id = id, espece = "Merle", cdNom = cdNom, latitude = 42.9, longitude = 1.4,
        nombre = 1, releveId = "grp1", date = 1_700_000_000_000L,
        releveSansEspece = cdNom == null, releveTente = tente,
    )

    /** L'uuid que l'application va dériver pour le groupe « grp1 ». */
    private fun uuidDuGroupe() = GeoNatureUpload.uuidReleveGroupe("grp1")

    /** [reponseRecherche] = corps renvoyé par GET /releves (la recherche par uuid). */
    private fun router(reponseRecherche: String, altitudes: String = "{}") {
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin == "/api/geo/altitude" -> {
                    appelsAltitude++
                    json(altitudes)
                }
                chemin.contains("/releves?") -> json(reponseRecherche)
                chemin.endsWith("/only/releve") -> {
                    corpsReleve = requete.texte
                    val n = 700 + ++relevesCrees
                    json("""{"id":$n}""")
                }
                chemin.contains("/occurrence") -> {
                    occurrencesPostees++
                    relevesCibles.add(chemin.substringAfter("/releve/").substringBefore("/"))
                    json("{}")
                }
                else -> json("{}")
            }
        }
    }

    private fun reponseTrouve(uuid: String, idReleve: Int = 555) =
        """{"total":1,"items":{"type":"FeatureCollection","features":[""" +
            """{"type":"Feature","properties":{"id_releve_occtax":$idReleve,"unique_id_sinp_grp":"$uuid"}}]}}"""

    private val reponseVide = """{"total":0,"items":{"type":"FeatureCollection","features":[]}}"""

    @Test
    fun les_heures_ne_partent_que_si_elles_ont_ete_saisies() = runTest {
        // Le web laisse hour_min et hour_max nuls tant qu'on ne les tape pas (sous le « + » des
        // dates) ; l'appli envoyait l'heure de saisie, ou 00:00 et 23:59.
        store.ajouter(Sortie(id = "s1", observations = listOf(obs("o1"))))
        router(reponseVide)
        envoi.envoyerSortieVersGeoNature(store.charger().first(), config)
        val sansHeures = JSONObject(corpsReleve!!).getJSONObject("properties")
        assertFalse(sansHeures.has("hour_min"), "aucune heure saisie : $sansHeures")
        assertFalse(sansHeures.has("hour_max"))

        store.ajouter(Sortie(id = "s2", observations = listOf(obs("o2").copy(heureDebutReleve = "8:30", heureFinReleve = "09:15"))))
        envoi.envoyerSortieVersGeoNature(store.charger().first { it.id == "s2" }, config)
        val avecHeures = JSONObject(corpsReleve!!).getJSONObject("properties")
        assertEquals("8:30", avecHeures.getString("hour_min"), "telle que tapée, comme le web")
        assertEquals("09:15", avecHeures.getString("hour_max"))
    }

    @Test
    fun les_altitudes_vides_viennent_du_mnt_du_serveur_comme_sur_le_web() = runTest {
        store.ajouter(Sortie(id = "s1", observations = listOf(obs("o1"))))
        router(reponseVide, altitudes = """{"altitude_min":812.4,"altitude_max":830}""")
        envoi.envoyerSortieVersGeoNature(store.charger().first(), config)
        val proprietes = JSONObject(corpsReleve!!).getJSONObject("properties")
        assertEquals(812, proprietes.getInt("altitude_min"))
        assertEquals(830, proprietes.getInt("altitude_max"))
        assertEquals(1, appelsAltitude)

        // Saisies : elles partent telles quelles, sans appel au serveur.
        store.ajouter(Sortie(id = "s2", observations = listOf(obs("o2").copy(champsReleveExtra = mapOf("altitude_min" to "1200")))))
        envoi.envoyerSortieVersGeoNature(store.charger().first { it.id == "s2" }, config)
        val saisies = JSONObject(corpsReleve!!).getJSONObject("properties")
        assertEquals(1200, saisies.getInt("altitude_min"))
        assertFalse(saisies.has("altitude_max"))
        assertEquals(1, appelsAltitude)
    }

    @Test
    fun le_releve_part_avec_son_uuid_client() = runTest {
        store.ajouter(Sortie(id = "s1", observations = listOf(obs("o1"))))
        router(reponseVide)

        envoi.envoyerSortieVersGeoNature(store.charger().first(), config)

        assertTrue(
            corpsReleve!!.contains("\"unique_id_sinp_grp\":\"${uuidDuGroupe()}\""),
            "le POST du relevé doit porter unique_id_sinp_grp — sans identité client, un relevé " +
                "dont la réponse se perd est irrattrapable. Corps : $corpsReleve",
        )
    }

    @Test
    fun un_releve_deja_cree_est_REUTILISE_au_lieu_d_etre_recree() = runTest {
        // Envoi précédent interrompu : le relevé a été tenté (et créé côté serveur, id 555), mais
        // l'appareil n'a jamais vu la réponse.
        store.ajouter(Sortie(id = "s1", observations = listOf(obs("o1", tente = true))))
        router(reponseTrouve(uuidDuGroupe()))

        envoi.envoyerSortieVersGeoNature(store.charger().first(), config)

        assertEquals(0, relevesCrees, "aucun nouveau relevé ne doit être créé")
        assertEquals(listOf("555"), relevesCibles, "l'occurrence doit être postée dans le relevé RETROUVÉ")
    }

    @Test
    fun releve_sans_espece_deja_cree_n_est_pas_duplique() = runTest {
        // LE cas qui compte : le relevé d'absence EST la donnée, il n'a aucune occurrence pour le
        // rattraper. Le re-créer produit un doublon franc dans la base régionale.
        store.ajouter(Sortie(id = "s1", observations = listOf(obs("o1", cdNom = null, tente = true))))
        router(reponseTrouve(uuidDuGroupe()))

        val res = envoi.envoyerSortieVersGeoNature(store.charger().first(), config)

        assertEquals(0, relevesCrees, "le relevé d'absence ne doit pas être re-créé")
        assertTrue(res.succes, "l'envoi doit se clore normalement")
    }

    @Test
    fun verification_impossible_ne_cree_RIEN() = runTest {
        // Le serveur IGNORE le filtre (version ancienne) : il renvoie des relevés quelconques.
        // Conclure « absent » ferait créer un doublon — l'invariant du projet impose de ne rien
        // poster quand la vérification anti-doublon échoue.
        store.ajouter(Sortie(id = "s1", observations = listOf(obs("o1", tente = true))))
        router(reponseTrouve("11111111-2222-3333-4444-555555555555", idReleve = 42))

        val res = envoi.envoyerSortieVersGeoNature(store.charger().first(), config)

        assertEquals(0, relevesCrees, "aucun relevé ne doit être créé")
        assertEquals(0, occurrencesPostees, "aucune occurrence ne doit partir")
        assertFalse(res.succes, "l'envoi doit être signalé en échec, pas passer pour un succès")
        assertTrue(
            res.message.contains("vérifier") || res.message.contains("réessayez"),
            "le message doit expliquer, message = ${res.message}",
        )
    }

    @Test
    fun un_premier_envoi_jamais_tente_n_interroge_pas_le_serveur() = runTest {
        // Pas de vérification inutile : le coût d'un aller-retour par groupe ne se paie que lorsque
        // la création a réellement déjà été tentée.
        store.ajouter(Sortie(id = "s1", observations = listOf(obs("o1"))))
        var recherches = 0
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPathAndQuery
            when {
                chemin.startsWith("/api/auth/login") -> json(LOGIN)
                chemin.contains("/releves?") -> {
                    recherches++
                    json(reponseVide)
                }
                chemin.endsWith("/only/releve") -> json("""{"id":701}""")
                else -> json("{}")
            }
        }

        envoi.envoyerSortieVersGeoNature(store.charger().first(), config)

        assertEquals(0, recherches, "aucune recherche par uuid sur un premier envoi")
    }

    private companion object {
        const val LOGIN = """{"access_token":"t","user":{"id_role":1}}"""
    }
}
