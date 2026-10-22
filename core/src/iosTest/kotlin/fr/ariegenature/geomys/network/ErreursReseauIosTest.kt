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

import fr.ariegenature.geomys.network.http.moteurHttpPlateforme
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

/** Le contrat « erreur réseau = IOException » avec le vrai moteur iOS (NSURLSession), dont dépend
 *  tout le mode hors ligne, et le classement de ses erreurs. */
class ErreursReseauIosTest {

    @Test
    fun uneConnexionRefuseeEstUneIOExceptionConnexionImpossible() = runTest {
        // Le port 9 (discard) n'écoute pas : refus immédiat, sans réseau ni DNS.
        val erreur = runCatching { HttpClient(moteurHttpPlateforme()).get("http://127.0.0.1:9/api/auth/login") }.exceptionOrNull()
        assertTrue(erreur is IOException, "obtenu : $erreur")
        assertEquals(ErreurReseau.CONNEXION_IMPOSSIBLE, classerErreurReseau(erreur))
        assertTrue(humaniserErreurReseau(erreur).startsWith("Connexion au serveur impossible"))
    }
}
