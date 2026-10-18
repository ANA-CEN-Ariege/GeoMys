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

// Les quatre premiers cas sont portés de GeoMys-Android @ cdd5196
// (app/src/test/java/fr/ariegenature/geomys/GeoNatureAuthTest.kt) : MockWebServer remplacé par
// MockEngine. Les suivants couvrent le reste du contrat de GeoNatureAuth.

package fr.ariegenature.geomys.network

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.store.CoffreEnMemoire
import fr.ariegenature.geomys.store.GeoNatureConfig
import io.ktor.client.request.get
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

/** Flux d'authentification GeoNature (POST /api/auth/login) : extraction du token, de l'id_role
 *  et des cookies de session ; gestion des réponses d'échec ; diagnostic du test de connexion. */
class GeoNatureAuthTest {

    private val serveur = ServeurSimule()
    private var maintenant = 1_000_000L
    private val auth = GeoNatureAuth(serveur.clients, horloge = { maintenant })

    private fun config(url: String = serveur.base) = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
        urlServeur = url
        login = "alice"
        motDePasse = "pwd"
    }

    @Test
    fun login_200_extrait_token_idrole_et_cookies() = runTest {
        serveur.repondreJson(200, """{"access_token":"abc123","user":{"id_role":42}}""", "Set-Cookie" to "token=abc123; Path=/; HttpOnly")
        val res = auth.loginAvecCookies(serveur.base, "alice", "pwd")
        assertNotNull(res)
        val (token, idRole, cookies) = res
        assertEquals("abc123", token)
        assertEquals(42, idRole)
        assertTrue(cookies.contains("token=abc123"))

        // La requête envoyée est bien un POST JSON sur /api/auth/login avec login+password.
        val req = serveur.requetes.single()
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("/api/auth/login", req.url.encodedPath)
        assertTrue(req.texte.contains("\"login\":\"alice\""))
        assertTrue(req.texte.contains("\"password\":\"pwd\""))
    }

    @Test
    fun login_401_renvoie_null() = runTest {
        serveur.repondre(401)
        assertNull(auth.loginAvecCookies(serveur.base, "bob", "x"))
    }

    @Test
    fun login_200_mais_pas_json_renvoie_null() = runTest {
        serveur.repondre(200, "<html>login</html>", "Content-Type" to "text/html")
        assertNull(auth.loginAvecCookies(serveur.base, "carol", "x"))
    }

    @Test
    fun token_lu_aussi_depuis_le_champ_token() = runTest {
        serveur.repondreJson(200, """{"token":"xyz","user":{"id_role":7}}""")
        assertEquals("xyz", auth.loginAvecCookies(serveur.base, "dave", "x")!!.first)
    }

    @Test
    fun login_200_sans_jeton_ni_id_role_est_un_refus() = runTest {
        // Échec « soft » de Flask-JWT : il ne doit pas être mis en cache comme une session.
        serveur.repondreJson(200, """{"msg":"Bad credentials"}""")
        assertNull(auth.loginAvecCookies(serveur.base, "erin", "x"))
        assertNull(auth.sessionEnCache(serveur.base, "erin"))
    }

    @Test
    fun une_panne_reseau_est_levee_et_non_prise_pour_un_refus() = runTest {
        serveur.couperLeReseau()
        assertFailsWith<IOException> { auth.loginAvecCookies(serveur.base, "frank", "x") }
    }

    @Test
    fun une_url_invalide_est_une_erreur_de_connexion_sans_requete() = runTest {
        assertFailsWith<IOException> { auth.loginAvecCookies("", "gina", "x") }
        assertTrue(serveur.requetes.isEmpty(), "rien ne doit partir vers localhost")
    }

    @Test
    fun la_session_en_cache_evite_un_second_login_pendant_5_minutes() = runTest {
        serveur.repondreJson(200, """{"access_token":"t1","user":{"id_role":3}}""")
        auth.loginAvecCookies(serveur.base, "henri", "x")
        maintenant += 4 * 60_000
        assertEquals("t1", auth.loginAvecCookies(serveur.base, "henri", "x")!!.first)
        assertEquals(1, serveur.requetes.size)
        maintenant += 2 * 60_000
        serveur.repondreJson(200, """{"access_token":"t2","user":{"id_role":3}}""")
        assertEquals("t2", auth.loginAvecCookies(serveur.base, "henri", "x")!!.first, "session expirée : nouveau login")
    }

    @Test
    fun un_401_sur_un_appel_quelconque_vide_la_session() = runTest {
        serveur.repondreJson(200, """{"access_token":"t1","user":{"id_role":3}}""")
        auth.loginAvecCookies(serveur.base, "ines", "x")
        serveur.repondre(401)
        serveur.clients.standard.get("${serveur.base}/api/monitorings/modules")
        assertNull(auth.sessionEnCache(serveur.base, "ines"))
    }

    @Test
    fun le_login_ne_suit_pas_les_redirections() = runTest {
        serveur.repondre(302, "", "Location" to "${serveur.base}/#/login")
        assertNull(auth.loginAvecCookies(serveur.base, "jules", "x"))
        assertEquals(1, serveur.requetes.size)
    }

    @Test
    fun connexion_reussie_enregistre_utilisateur_et_version() = runTest {
        serveur.repondreJson(200, """{"access_token":"t","user":{"id_role":42,"prenom_role":"Alice","nom_role":"Martin"}}""")
        serveur.repondreJson(200, """{"GEONATURE_VERSION":"2.16.1"}""")
        val c = config()
        assertEquals(Pair(true, "Connexion réussie"), auth.testerConnexion(c))
        assertEquals("Alice Martin", c.nomUtilisateur)
        assertEquals(42, c.idRoleUtilisateur)
        assertEquals("2.16.1", c.versionGeoNatureServeur)
        assertTrue(c.serveurCompatible)
        assertEquals("/api/gn_commons/config", serveur.requetes[1].url.encodedPath)
    }

    @Test
    fun connexion_sans_version_detectee_le_signale() = runTest {
        serveur.repondreJson(200, """{"access_token":"t"}""")
        serveur.repondre(404)
        val (ok, message) = auth.testerConnexion(config())
        assertTrue(ok)
        assertTrue(message.startsWith("Connexion réussie (version GeoNature non détectée"), message)
    }

    @Test
    fun serveur_trop_ancien_rend_la_config_incompatible() = runTest {
        serveur.repondreJson(200, """{"access_token":"t"}""")
        serveur.repondreJson(200, """{"GEONATURE_VERSION":"2.14.2"}""")
        val c = config()
        val (ok, message) = auth.testerConnexion(c)
        assertFalse(ok)
        assertTrue(message.startsWith("GeoNature v2.14.2 détecté"), message)
        assertFalse(c.serveurCompatible)
        assertFalse(c.connexionConfiguree)
    }

    @Test
    fun redirection_vers_la_page_de_login_signifie_identifiants_incorrects() = runTest {
        serveur.repondre(302, "", "Location" to "${serveur.base}/#/login")
        assertEquals(Pair(false, "Identifiant ou mot de passe incorrect"), auth.testerConnexion(config()))
    }

    @Test
    fun redirection_vers_https_demande_de_corriger_l_url() = runTest {
        serveur.repondre(301, "", "Location" to "https://geonature.test/api/auth/login")
        val (ok, message) = auth.testerConnexion(config("http://geonature.test"))
        assertFalse(ok)
        assertTrue(message.contains("https://"), message)
    }

    @Test
    fun une_page_html_signifie_une_mauvaise_url() = runTest {
        serveur.repondre(200, "<!doctype html><html></html>", "Content-Type" to "text/html")
        val (ok, message) = auth.testerConnexion(config())
        assertFalse(ok)
        assertTrue(message.startsWith("L'URL saisie ne pointe pas vers une API GeoNature"), message)
    }

    @Test
    fun un_json_sans_jeton_rend_le_message_du_serveur_ou_identifiants_incorrects() = runTest {
        serveur.repondreJson(200, """{"msg":"Invalid password"}""")
        assertEquals(Pair(false, "Identifiant ou mot de passe incorrect"), auth.testerConnexion(config()))
        serveur.repondreJson(200, """{"msg":"Compte désactivé"}""")
        assertEquals(Pair(false, "Compte désactivé"), auth.testerConnexion(config()))
    }

    @Test
    fun une_url_vide_ne_part_pas_et_le_dit() = runTest {
        val (ok, message) = auth.testerConnexion(config(url = ""))
        assertFalse(ok)
        assertTrue(message.startsWith("Impossible de joindre le serveur : URL invalide"), message)
        assertTrue(serveur.requetes.isEmpty())
    }

    @Test
    fun codes_401_et_404_du_test_de_connexion() = runTest {
        serveur.repondre(401)
        assertEquals(Pair(false, "Identifiant ou mot de passe incorrect"), auth.testerConnexion(config()))
        serveur.repondre(404)
        assertEquals(Pair(false, "URL serveur introuvable (HTTP 404) — vérifiez l'adresse"), auth.testerConnexion(config()))
    }
}
