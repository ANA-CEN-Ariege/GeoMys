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

package fr.ariegenature.geomys.network

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.store.CoffreEnMemoire
import fr.ariegenature.geomys.store.GeoNatureConfig
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class VerificationConnexionTest {

    private val serveur = ServeurSimule()
    private val auth = GeoNatureAuth(serveur.clients)
    private val verification = VerificationConnexion(auth, serveur.clients)

    private fun config() = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
        urlServeur = serveur.base + "/"
        login = "alice"
        motDePasse = "secret"
    }

    @Test
    fun lAppelAuthentifiePorteLeJetonEtLesCookies() = runTest {
        val connexion = """{"access_token":"jeton","user":{"id_role":42,"prenom_role":"Alice","nom_role":"Test"}}"""
        serveur.repondreJson(200, connexion) // test de connexion
        serveur.repondreJson(200, """{"GEONATURE_VERSION":"2.16.0"}""")
        serveur.repondreJson(200, connexion, "Set-Cookie" to "session=s1; Path=/", "Set-Cookie" to "csrf=c2; Path=/") // session
        serveur.repondreJson(200, "[]") // modules
        val resultat = verification.verifier(config())
        assertTrue(resultat.reussi)
        assertEquals("2.16.0", resultat.version)
        assertEquals("Test Alice", resultat.utilisateur, "nom_role prenom_role, comme le nom_complet du web")
        assertEquals(42, resultat.idRole)
        assertEquals(200, resultat.appelAuthentifie)
        val modules = serveur.requetes.last()
        assertEquals("/api/gn_commons/modules", modules.url.encodedPath)
        assertEquals("Bearer jeton", modules.headers[HttpHeaders.Authorization])
        assertEquals("session=s1; csrf=c2", modules.headers[HttpHeaders.Cookie])
    }

    @Test
    fun unEchecDuTestNeTentePasDAppelAuthentifie() = runTest {
        serveur.repondre(401)
        val resultat = verification.verifier(config())
        assertFalse(resultat.reussi)
        assertNull(resultat.appelAuthentifie)
        assertEquals(1, serveur.requetes.size)
    }
}
