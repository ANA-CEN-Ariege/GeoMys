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

// Propre au portage (audit du 21 octobre 2026, M27) : GeoMys-Android n'appelait jamais d'adresse venue du
// serveur avec la session.

package fr.ariegenature.geomys.network.http

import fr.ariegenature.geomys.network.ServeurSimule
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** Le jeton et les cookies de la session ne partent que vers le serveur GeoNature configuré : une
 *  liste d'une API absolue, un TaxHub publié sur un autre domaine ou une redirection vers un autre
 *  hôte les envoyaient à un tiers. Décision de l'utilisateur : « la demander sans jeton ». */
class JetonVersLeSeulServeurTest {

    private val serveur = ServeurSimule().apply { clients.origineInstance = { base } }

    @Test
    fun un_autre_hote_ne_recoit_ni_jeton_ni_cookie() = runTest {
        serveur.router { json("[]") }
        serveur.clients.get("${serveur.base}/api/monitorings/modules", "jeton", "session=1")
        serveur.clients.get("https://api.tiers.test/liste", "jeton", "session=1")
        serveur.clients.postJson("https://api.tiers.test/liste", "{}", "jeton", "session=1")
        val (instance, tiers, tiersPost) = serveur.requetes
        assertEquals("Bearer jeton", instance.headers[HttpHeaders.Authorization])
        assertEquals("session=1", instance.headers[HttpHeaders.Cookie])
        for (requete in listOf(tiers, tiersPost)) {
            assertNull(requete.headers[HttpHeaders.Authorization], "${requete.url}")
            assertNull(requete.headers[HttpHeaders.Cookie], "${requete.url}")
        }
    }

    @Test
    fun le_meme_hote_en_http_ne_recoit_rien() = runTest {
        serveur.router { json("[]") }
        serveur.clients.get(serveur.base.replace("https://", "http://") + "/api/x", "jeton", "session=1")
        assertNull(serveur.requetes.single().headers[HttpHeaders.Authorization])
        assertNull(serveur.requetes.single().headers[HttpHeaders.Cookie])
    }

    @Test
    fun une_redirection_vers_un_autre_hote_perd_le_jeton_et_le_cookie() = runTest {
        serveur.routerRequetes { requete ->
            if (requete.url.host == "geonature.test") statut(302, "", "Location" to "https://ailleurs.test/piege")
            else json("[]")
        }
        serveur.clients.get("${serveur.base}/api/taxhub/api/biblistes", "jeton", "session=1")
        val (premiere, redirigee) = serveur.requetes
        assertEquals("Bearer jeton", premiere.headers[HttpHeaders.Authorization])
        assertEquals("ailleurs.test", redirigee.url.host)
        assertNull(redirigee.headers[HttpHeaders.Authorization])
        assertNull(redirigee.headers[HttpHeaders.Cookie])
    }

    @Test
    fun sans_serveur_configure_rien_n_est_retire() = runTest {
        // Les tests des autres classes, sans origine branchée, gardent leurs en-têtes.
        val autre = ServeurSimule()
        autre.router { json("[]") }
        autre.clients.get("https://api.tiers.test/liste", "jeton", "session=1")
        assertEquals("Bearer jeton", autre.requetes.single().headers[HttpHeaders.Authorization])
    }
}
