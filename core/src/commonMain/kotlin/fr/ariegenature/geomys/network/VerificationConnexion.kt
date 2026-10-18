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

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.urlAbsolue
import fr.ariegenature.geomys.store.GeoNatureConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.io.IOException

/** Vérifie de bout en bout la connexion à un serveur GeoNature : le test de connexion de l'écran
 *  de configuration ([GeoNatureAuth.testerConnexion]), puis un appel authentifié (Bearer et cookies
 *  de session) sur `/api/gn_commons/modules`, qu'un serveur refuse sans session valide. C'est le
 *  critère de sortie du lot 1 (INVENTAIRE.md § 10). */
class VerificationConnexion(private val auth: GeoNatureAuth, private val clients: ClientsGeoNature) {

    data class Resultat(
        val reussi: Boolean,
        val message: String,
        /** L'identifiant envoyé, tel quel (une majuscule ajoutée par le clavier se voit ainsi). */
        val login: String,
        val version: String,
        val utilisateur: String,
        val idRole: Int,
        /** Code HTTP de l'appel authentifié, null s'il n'a pas pu partir. */
        val appelAuthentifie: Int?,
    ) {
        fun enLigne(): String =
            "CONNEXION_GEONATURE reussi=$reussi login=\"$login\" version=$version id_role=$idRole utilisateur=\"$utilisateur\" " +
                "appel_authentifie=${appelAuthentifie ?: "-"} message=\"$message\""
    }

    suspend fun verifier(config: GeoNatureConfig): Resultat {
        // Identifiants peut-être changés : pas de session d'avant.
        auth.invaliderCache()
        val (reussi, message) = auth.testerConnexion(config)
        var code: Int? = null
        if (reussi) {
            val base = config.urlServeur.trim().trimEnd('/')
            val session = try {
                auth.loginAvecCookies(base, config.login, config.motDePasse)
            } catch (_: IOException) {
                null
            }
            if (session != null) {
                val (token, _, cookies) = session
                code = try {
                    clients.standard.get(urlAbsolue("$base/api/gn_commons/modules")) {
                        header(HttpHeaders.Accept, "application/json")
                        if (token != null) bearerAuth(token)
                        if (cookies.isNotEmpty()) header(HttpHeaders.Cookie, cookies)
                        timeout {
                            connectTimeoutMillis = 15_000
                            socketTimeoutMillis = 15_000
                        }
                    }.status.value
                } catch (_: IOException) {
                    null
                }
            }
        }
        return Resultat(reussi, message, config.login, config.versionGeoNatureServeur, config.nomUtilisateur, config.idRoleUtilisateur, code)
    }
}
