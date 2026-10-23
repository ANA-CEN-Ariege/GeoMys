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

// Propre au portage (demande du 23 octobre 2026) : l'avertissement « Donnée exceptionnelle » du formulaire
// OccTax du web (contrib/occtax/…/occurrence/profiles.component.ts), qui interroge les profils de taxons
// de GeoNature (backend/geonature/core/gn_profiles/routes.py). Les trois routes sont publiques.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.get
import fr.ariegenature.geomys.network.http.postJson
import fr.ariegenature.geomys.store.GeoNatureConfig
import io.ktor.client.statement.bodyAsText
import kotlin.coroutines.cancellation.CancellationException

/** Ce que le serveur répond à la vérification d'une observation. */
sealed interface ReponseProfil {
    /** Le profil existe : [messages] vide, l'observation lui ressemble ; sinon, ce qui s'en écarte. */
    data class Verifiee(val messages: List<String>) : ReponseProfil

    /** Pas de profil pour ce taxon (404 « No profile for this cd_ref ») : rien à signaler, comme sur le
     *  web. */
    data object SansProfil : ReponseProfil

    /** Observation que le serveur ne sait pas vérifier (400 : altitude nulle, par exemple) : rien à
     *  signaler non plus. */
    data object Invalide : ReponseProfil

    /** Le serveur n'a pas la route des profils (404 d'une route inconnue, 405) : inutile d'y revenir. */
    data object ServiceAbsent : ReponseProfil

    /** Pas de réponse (hors réseau, serveur injoignable, erreur) : à retenter. */
    data object Injoignable : ReponseProfil
}

/** Les appels au serveur dont la vérification a besoin : le `cd_ref` d'un taxon (les profils sont
 *  rangés par `cd_ref`, l'appli ne garde que le `cd_nom`), les altitudes du MNT d'une géométrie, et la
 *  vérification elle-même. */
interface ApiProfils {
    /** Le `cd_ref` du taxon [cdNom], null sans réponse. */
    suspend fun cdRef(cdNom: Int): Int?

    /** Les altitudes min et max du MNT sous [geometrie], null sans réponse. */
    suspend fun altitudes(geometrie: JSONObject): Pair<Int, Int>?

    /** `POST /gn_profiles/check_observation` avec [corps], le même que celui du web. */
    suspend fun verifier(corps: JSONObject): ReponseProfil
}

/** [ApiProfils] sur le serveur GeoNature des Paramètres, sans connexion (routes publiques). Délais
 *  courts : la vérification tourne en arrière-plan, un réseau faible vaut une absence de réseau. */
class ApiProfilsGeoNature(private val clients: ClientsGeoNature, private val config: GeoNatureConfig) : ApiProfils {

    private val base: String get() = config.urlServeur.trim().trimEnd('/') + "/api"

    override suspend fun cdRef(cdNom: Int): Int? = auMieux {
        val reponse = clients.get("${config.urlTaxhub}/api/taxref/$cdNom", timeoutMs = DELAI)
        if (reponse.status.value != 200) null
        else JSONObject(reponse.bodyAsText()).optInt("cd_ref", -1).takeIf { it > 0 }
    }

    override suspend fun altitudes(geometrie: JSONObject): Pair<Int, Int>? = auMieux {
        val feature = JSONObject().put("type", "Feature").put("geometry", geometrie).put("properties", JSONObject())
        val reponse = clients.postJson("$base/geo/altitude", feature.toString(), timeoutMs = DELAI)
        if (reponse.status.value != 200) return@auMieux null
        val alt = JSONObject(reponse.bodyAsText())
        val min = alt.opt("altitude_min")?.toString()?.toDoubleOrNull()?.toInt()
        val max = alt.opt("altitude_max")?.toString()?.toDoubleOrNull()?.toInt()
        if (min == null || max == null) null else min to max
    }

    override suspend fun verifier(corps: JSONObject): ReponseProfil = auMieux {
        val reponse = clients.postJson("$base/gn_profiles/check_observation", corps.toString(), timeoutMs = DELAI)
        when (reponse.status.value) {
            200 -> lireReponse(reponse.bodyAsText())
            400 -> ReponseProfil.Invalide
            404 -> if (reponse.bodyAsText().contains("No profile")) ReponseProfil.SansProfil else ReponseProfil.ServiceAbsent
            405, 501 -> ReponseProfil.ServiceAbsent
            else -> ReponseProfil.Injoignable
        }
    } ?: ReponseProfil.Injoignable

    private suspend fun <T> auMieux(bloc: suspend () -> T?): T? = try {
        bloc()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val DELAI = 8_000L

        /** Les phrases du serveur (`errors[].value`), celles que le web liste sous « Donnée
         *  exceptionnelle ». */
        internal fun lireReponse(texte: String): ReponseProfil {
            val erreurs = JSONObject(texte).optJSONArray("errors") ?: JSONArray()
            val messages = (0 until erreurs.length()).mapNotNull { i ->
                // « periode » sans accent dans les phrases du serveur.
                erreurs.optJSONObject(i)?.optString("value", "")?.trim()?.takeIf { it.isNotEmpty() }?.replace("periode", "période")
            }
            return ReponseProfil.Verifiee(messages)
        }
    }
}
