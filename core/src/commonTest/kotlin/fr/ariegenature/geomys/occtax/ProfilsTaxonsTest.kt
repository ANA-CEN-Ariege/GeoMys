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

// Les réponses du serveur simulé sont celles relevées le 23 octobre 2026 sur le serveur de l'utilisateur
// (GeoNature 2.17) : Hirondelle rustique en janvier, synonyme Sylvia cantillans → Curruca cantillans.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.network.ServeurSimule
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.panne
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import fr.ariegenature.geomys.network.texte
import fr.ariegenature.geomys.store.NomValeur
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class ProfilsTaxonsTest {

    private val t = ServicesDeTest()
    private val serveur get() = t.serveur
    private val profils get() = t.services.profils

    private val hirondelle = 3696
    private val fauvetteSynonyme = 4229
    private val curruca = 824087

    /** Les corps des vérifications reçues, par `cd_ref`. */
    private val verifications = mutableListOf<JSONObject>()
    private var appelsAltitude = 0
    private var appelsTaxref = 0

    @BeforeTest
    fun setup() {
        t.services.config.urlServeur = serveur.base
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPath
            when {
                chemin.startsWith("/api/taxhub/api/taxref/") -> {
                    appelsTaxref++
                    val cdNom = chemin.substringAfterLast('/').toInt()
                    json("""{"cd_nom":$cdNom,"cd_ref":${if (cdNom == fauvetteSynonyme) curruca else cdNom}}""")
                }
                chemin == "/api/geo/altitude" -> {
                    appelsAltitude++
                    json("""{"altitude_max":420,"altitude_min":385}""")
                }
                chemin == "/api/gn_profiles/check_observation" -> {
                    val corps = JSONObject(requete.texte).also { verifications += it }
                    when (corps.getInt("cd_ref")) {
                        hirondelle -> json("""{"errors":[{"type":"period","value":"Le taxon n'a jamais été observé à cette periode"}],"valid_phenology":false}""")
                        1 -> statut(404, "No profile for this cd_ref")
                        else -> json("""{"errors":[],"valid_phenology":true}""")
                    }
                }
                else -> statut(404)
            }
        }
    }

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private val quinzeJanvier = LocalDateTime(2026, 1, 15, 9, 30).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private fun obs(id: String, cdNom: Int?, releveId: String? = "R1", espece: String = "Espèce $cdNom") =
        Observation(id = id, espece = espece, cdNom = cdNom, latitude = 42.965, longitude = 1.6075, date = quinzeJanvier, releveId = releveId)

    @Test
    fun une_observation_inhabituelle_est_signalee_avec_les_mots_du_serveur() = runBlocking {
        val observations = listOf(obs("h", hirondelle, espece = "Hirondelle rustique"), obs("m", 4117, espece = "Merle noir"))
        assertTrue(profils.verifier(observations))
        val exceptionnelles = profils.exceptionnelles(observations)
        assertEquals(listOf("h"), exceptionnelles.map { it.first.id })
        assertEquals(listOf("Le taxon n'a jamais été observé à cette période"), exceptionnelles.single().second.messages)
        // Ce que le web envoie : cd_ref, dates, altitudes du MNT, géométrie du relevé.
        val corps = verifications.first { it.getInt("cd_ref") == hirondelle }
        assertEquals("2026-01-15", corps.getString("date_min"))
        assertEquals("2026-01-15", corps.getString("date_max"))
        assertEquals(385, corps.getInt("altitude_min"))
        assertEquals(420, corps.getInt("altitude_max"))
        assertEquals("Point", corps.getJSONObject("geom").getString("type"))
        assertEquals(1, appelsAltitude, "le MNT une fois pour le relevé")
    }

    @Test
    fun ce_qui_a_ete_verifie_ne_l_est_plus_meme_apres_un_redemarrage() = runBlocking {
        val observations = listOf(obs("h", hirondelle))
        profils.verifier(observations)
        val requetes = serveur.requetes.size
        assertTrue(profils.verifier(observations))
        assertEquals(requetes, serveur.requetes.size, "rien de redemandé")
        t.redemarrer()
        profils.charger()
        assertEquals(listOf("h"), profils.exceptionnelles(observations).map { it.first.id }, "le résultat est gardé sur le disque")
        assertFalse(profils.aVerifier(observations))
    }

    @Test
    fun une_observation_modifiee_est_reverifiee() = runBlocking {
        val avant = listOf(obs("h", hirondelle))
        profils.verifier(avant)
        val apres = listOf(avant.single().copy(dateDebutReleve = quinzeJanvier + 120L * 24 * 3600 * 1000))
        assertTrue(profils.aVerifier(apres), "la date a changé")
        assertTrue(profils.exceptionnelles(apres).isEmpty(), "l'ancien résultat ne vaut plus")
        profils.verifier(apres)
        assertEquals(2, verifications.size)
        assertEquals("2026-05-15", verifications.last().getString("date_min"))
    }

    @Test
    fun les_altitudes_saisies_le_synonyme_et_le_stade_de_vie() = runBlocking {
        t.services.nomenclatures.setAll(mapOf("STADE_VIE" to listOf(NomValeur(17, "Adulte"))))
        val fauvette = obs("f", fauvetteSynonyme, releveId = null).copy(
            champsReleveExtra = mapOf("altitude_min" to "1200"),
            stadeVie = "2",
            denombrementsAdditionnels = listOf(Denombrement(stadeVie = "2")),
        )
        profils.verifier(listOf(fauvette))
        val corps = verifications.single()
        assertEquals(curruca, corps.getInt("cd_ref"), "les profils sont rangés par cd_ref")
        assertEquals(1200, corps.getInt("altitude_min"))
        assertEquals(1200, corps.getInt("altitude_max"), "une altitude saisie vaut pour les deux")
        assertEquals(0, appelsAltitude, "pas de MNT quand l'altitude est saisie")
        assertEquals("[17]", corps.getJSONArray("life_stages").toString())
    }

    @Test
    fun sans_reseau_rien_n_est_garde_et_on_reessaie_plus_tard() = runBlocking {
        val observations = listOf(obs("h", hirondelle))
        serveur.routerRequetes { panne() }
        assertFalse(profils.verifier(observations))
        assertTrue(profils.aVerifier(observations))
        setup()
        assertTrue(profils.verifier(observations))
        assertEquals(1, profils.exceptionnelles(observations).size)
    }

    @Test
    fun un_taxon_sans_profil_n_a_rien_a_signaler_et_n_est_pas_redemande() = runBlocking {
        val observations = listOf(obs("x", 1))
        assertTrue(profils.verifier(observations))
        assertTrue(profils.exceptionnelles(observations).isEmpty())
        assertFalse(profils.aVerifier(observations))
    }

    @Test
    fun ni_les_envoyees_ni_les_releves_sans_espece_ni_les_especes_non_reconnues() {
        val observations = listOf(
            obs("envoyee", hirondelle).copy(envoyeeServeur = true),
            obs("absence", null).copy(releveSansEspece = true),
            obs("inconnue", null),
            obs("h", hirondelle),
        )
        assertEquals(listOf("h"), demandesProfil(observations).map { it.obsId })
    }

    @Test
    fun avant_l_envoi_un_serveur_muet_ne_retient_pas_l_envoi() = runBlocking {
        serveur.routerRequetes { ServeurSimule.Reponse(200, "{}", emptyList(), panne = false, delaiMs = 30_000) }
        val debut = kotlin.time.TimeSource.Monotonic.markNow()
        assertTrue(profils.avantEnvoi(listOf(obs("h", hirondelle)), delaiMs = 300).isEmpty())
        assertTrue(debut.elapsedNow().inWholeMilliseconds < 5_000)
    }

    @Test
    fun les_resultats_des_saisies_supprimees_sont_oublies() = runBlocking {
        val observations = listOf(obs("h", hirondelle), obs("m", 4117))
        profils.verifier(observations)
        profils.oublierSauf(setOf("m"))
        assertEquals(setOf("m"), profils.resultats.value.keys)
    }

    /** Toutes les vérifications répondent [reponse] ; le reste comme d'habitude. */
    private fun verificationsRepondent(reponse: () -> ServeurSimule.Reponse) {
        serveur.routerRequetes { requete ->
            val chemin = requete.url.encodedPath
            when {
                chemin.startsWith("/api/taxhub/api/taxref/") -> json("{\"cd_ref\":${chemin.substringAfterLast('/')}}")
                chemin == "/api/geo/altitude" -> json("{\"altitude_max\":420,\"altitude_min\":385}")
                chemin == "/api/gn_profiles/check_observation" -> reponse().also { verifications += JSONObject(requete.texte) }
                else -> statut(404)
            }
        }
    }

    private val sansProfil = { statut(404, "<p>No profile for this cd_ref</p>") }

    @Test
    fun un_serveur_sans_aucun_profil_n_est_plus_interroge() = runBlocking {
        verificationsRepondent(sansProfil)
        val premieres = (1..DisponibiliteProfils.SEUIL_SANS_PROFIL).map { obs("o$it", 1000 + it, releveId = null) }
        assertTrue(profils.verifier(premieres))
        assertEquals(DisponibiliteProfils.SEUIL_SANS_PROFIL, verifications.size)
        val requetes = serveur.requetes.size
        assertTrue(profils.verifier(listOf(obs("autre", 2000, releveId = null))))
        assertEquals(requetes, serveur.requetes.size, "plus aucune requête pour ce serveur")
        assertTrue(profils.avantEnvoi(listOf(obs("encore", 2001, releveId = null))).isEmpty())
        assertEquals(requetes, serveur.requetes.size)
    }

    @Test
    fun un_seul_profil_trouve_et_le_serveur_reste_interroge() = runBlocking {
        profils.verifier(listOf(obs("h", hirondelle)))
        verificationsRepondent(sansProfil)
        val suivantes = (1..2 * DisponibiliteProfils.SEUIL_SANS_PROFIL).map { obs("o$it", 1000 + it, releveId = null) }
        profils.verifier(suivantes)
        assertEquals(1 + suivantes.size, verifications.size, "toutes vérifiées")
        assertTrue(DisponibiliteProfils(t.services.config).disponibles)
    }

    @Test
    fun une_route_absente_suffit_et_un_autre_serveur_est_interroge() = runBlocking {
        verificationsRepondent { statut(404, "<h1>Not Found</h1>") }
        profils.verifier(listOf(obs("a", 1001, releveId = null), obs("b", 1002, releveId = null)))
        assertEquals(1, verifications.size, "dès la première réponse")
        assertFalse(DisponibiliteProfils(t.services.config).disponibles)
        // Un autre serveur dans les Paramètres : il est interrogé.
        t.services.config.urlServeur = "https://autre.test/"
        setup()
        t.services.config.urlServeur = "https://autre.test/"
        assertTrue(profils.verifier(listOf(obs("h", hirondelle))))
        assertEquals(2, verifications.size)
    }

    @Test
    fun recharger_les_donnees_repart_de_zero_selon_le_reglage_du_serveur() {
        val disponibilite = DisponibiliteProfils(t.services.config)
        disponibilite.absents()
        assertFalse(disponibilite.disponibles)
        disponibilite.recharger(actifs = true)
        assertTrue(disponibilite.disponibles)
        disponibilite.recharger(actifs = false)
        assertFalse(disponibilite.disponibles)
    }

    @Test
    fun la_synchro_lit_enable_profiles_comme_le_web() = runBlocking {
        val config = t.services.config
        DisponibiliteProfils(config).absents()
        serveur.routerRequetes { requete ->
            if (requete.url.encodedPath == "/api/gn_commons/config") json("{\"FRONTEND\":{\"ENABLE_PROFILES\":true}}") else statut(404)
        }
        t.services.geoNatureSync.synchroniserSettingsOcctax(config)
        assertTrue(DisponibiliteProfils(config).disponibles, "le rechargement repart de zéro")
        serveur.routerRequetes { requete ->
            if (requete.url.encodedPath == "/api/gn_commons/config") json("{\"FRONTEND\":{\"ENABLE_PROFILES\":false}}") else statut(404)
        }
        t.services.geoNatureSync.synchroniserSettingsOcctax(config)
        assertFalse(DisponibiliteProfils(config).disponibles, "le web n'interroge pas les profils")
    }

    @Test
    fun les_textes_de_mes_saisies() {
        assertEquals("⚠ 1 donnée exceptionnelle", TextesProfils.ligneSaisie(1))
        assertEquals("⚠ 2 données exceptionnelles", TextesProfils.ligneSaisie(2))
        val h = obs("h", hirondelle, espece = "Hirondelle rustique")
        val sortie = Sortie(observations = listOf(h, obs("m", 4117, espece = "Merle noir")))
        val v = VerificationProfil("h", "cle", listOf("Le taxon n'a jamais été observé à cette période"))
        // Le texte voulu par l'utilisateur, à la place des phrases du serveur.
        val message = "Donnée exceptionnelle. Le taxon n'a jamais été observé dans cette zone et/ou à cette date"
        assertEquals(
            listOf("⚠ Hirondelle rustique — 1 ind.", "      $message", "Merle noir — 1 ind."),
            MesSaisies.lignesEspeces(sortie, TextesProfils.especes(listOf(h to v))),
        )
        assertEquals(
            "$message :\n\n• Hirondelle rustique (15/01/2026)\n\nVérifiez, ou envoyez quand même.",
            TextesProfils.messageEnvoi(listOf(h to v)),
        )
    }
}
