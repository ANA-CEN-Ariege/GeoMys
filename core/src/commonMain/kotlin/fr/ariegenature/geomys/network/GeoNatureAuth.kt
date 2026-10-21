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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/GeoNatureAuth.kt) :
// HttpURLConnection remplacé par Ktor (le login passe par le client sans redirection), synchronized
// par un Mutex (loginAvecCookies devient suspend), org.json par son portage. L'objet devient une
// classe : l'appli en crée une seule, sur ses ClientsGeoNature. Les annulations de coroutine ne
// sont plus avalées par les `catch (e: Exception)`.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.cookiesDeSession
import fr.ariegenature.geomys.network.http.urlAbsolue
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.util.maintenantMillis
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

class GeoNatureAuth(
    private val clients: ClientsGeoNature,
    private val horloge: () -> Long = ::maintenantMillis,
) {

    suspend fun testerConnexion(config: GeoNatureConfig): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val base = config.urlServeur.trim().trimEnd('/')
                val url = urlAbsolue("$base/api/auth/login")
                // GeoNature renvoie un 302 vers `/#/login` (la SPA HTML) quand les identifiants
                // sont invalides au lieu d'un 401 propre. Si on laissait le client suivre
                // automatiquement, on tomberait sur la SPA et on diagnostiquerait à tort une
                // mauvaise URL. Parité gn_mobile_monitoring (login_api_impl.dart).
                val body = JSONObject().put("login", config.login).put("password", config.motDePasse).toString()
                val reponse = clients.sansRedirection.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                    timeout {
                        connectTimeoutMillis = 10_000
                        socketTimeoutMillis = 10_000
                    }
                }

                val code = reponse.status.value
                // Distinction fine des réponses GeoNature observées en prod :
                //   - HTTP 200 + body JSON avec token → succès,
                //   - HTTP 200 + body JSON sans token / avec `msg` → auth refusée en mode "soft",
                //   - HTTP 200 + body HTML → vraie mauvaise URL (la racine du site sert la SPA),
                //   - HTTP 302 vers `#/login` ou hors `/api/` → identifiants invalides
                //     (cas Apache/Gunicorn qui transforme un échec auth en redirect SPA),
                //   - HTTP 302 HTTP→HTTPS → l'utilisateur doit corriger l'URL,
                //   - HTTP 401/403 → identifiants invalides (cas propre),
                //   - HTTP 404 → URL serveur introuvable.
                when (code) {
                    in 300..399 -> diagnostiquerRedirect(reponse, url)
                    200 -> {
                        val bodyText = try {
                            reponse.bodyAsText()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            ""
                        }
                        val json: JSONObject? = try { JSONObject(bodyText) } catch (_: Exception) { null }
                        if (json == null) {
                            // Pas du JSON parsable → typiquement la SPA HTML servie quand l'URL
                            // ne pointe pas vers l'API (cas le plus fréquent ici : racine du
                            // site GeoNature au lieu de l'URL d'instance).
                            return@withContext Pair(
                                false,
                                "L'URL saisie ne pointe pas vers une API GeoNature : réponse HTML reçue. Vérifiez l'URL du serveur.",
                            )
                        }
                        val token = extractToken(json)
                        if (token == null) {
                            // JSON valide mais sans token → message d'erreur d'auth en HTTP 200
                            // (pattern Flask-JWT-Extended sur certaines instances).
                            val msgErreur = json.optString("msg", "")
                                .ifEmpty { json.optString("message", "") }
                                .ifEmpty { json.optString("error", "") }
                            val ressembleAuth = msgErreur.contains("identifi", ignoreCase = true)
                                || msgErreur.contains("password", ignoreCase = true)
                                || msgErreur.contains("mot de passe", ignoreCase = true)
                                || msgErreur.contains("login", ignoreCase = true)
                                || msgErreur.contains("auth", ignoreCase = true)
                                || msgErreur.contains("invalid", ignoreCase = true)
                                || msgErreur.contains("incorrect", ignoreCase = true)
                            return@withContext Pair(
                                false,
                                if (ressembleAuth || msgErreur.isEmpty())
                                    "Identifiant ou mot de passe incorrect"
                                else msgErreur,
                            )
                        }
                        // Sauvegarde du nom complet de l'utilisateur (utilisé comme déterminateur
                        // par défaut dans la saisie multi-taxons) et de son id_role (pour la
                        // pré-sélection dans les champs observers de saisie monitoring). Best-effort.
                        extraireNomComplet(json)?.let { config.nomUtilisateur = it }
                        extraireIdRole(json)?.let { config.idRoleUtilisateur = it }
                        json.optJSONObject("user")?.optInt("id_organisme", -1)?.takeIf { it > 0 }?.let { config.idOrganisme = it }
                        // Version de l'instance GeoNature — best-effort (l'endpoint
                        // /api/gn_commons/config n'existe pas sur les vieux serveurs). Affichée
                        // dans le résultat du test et mémorisée pour l'écran Paramètres : l'API
                        // GeoNature n'étant pas versionnée par endpoint, c'est le seul indice
                        // pour diagnostiquer un comportement propre à une version serveur.
                        val version = versionGeoNature(base)
                        config.versionGeoNatureServeur = version ?: ""
                        // Garde version minimale : une instance trop ancienne n'expose pas les
                        // routes requises (TaxHub intégré sous /api/taxhub…). On invalide la
                        // config (serveurCompatible → connexionConfiguree) plutôt que de laisser
                        // l'app échouer de façon opaque à la première synchro. Une version illisible
                        // (`gn_commons/config` absent, d'avant la 2.12) est refusée de même : le
                        // bénéfice du doute laissait passer un serveur dont les taxons ne se
                        // chargeaient pas (demande de l'utilisateur du 21 octobre).
                        if (version == null) {
                            config.serveurCompatible = false
                            return@withContext Pair(false, VERSION_INTROUVABLE)
                        }
                        if (!versionGeoNatureSupportee(version)) {
                            config.serveurCompatible = false
                            return@withContext Pair(
                                false,
                                "GeoNature v$version détecté — version minimale supportée : " +
                                    "v$VERSION_GEONATURE_MINIMALE. Mettez à jour le serveur pour " +
                                    "utiliser l'application.",
                            )
                        }
                        config.serveurCompatible = true
                        // Pas de suffixe : la version est affichée sur la ligne « Serveur GeoNature
                        // v… » sous le message.
                        Pair(true, "Connexion réussie")
                    }
                    401, 403 -> Pair(false, "Identifiant ou mot de passe incorrect")
                    404 -> Pair(false, "URL serveur introuvable (HTTP 404) — vérifiez l'adresse")
                    else -> Pair(false, "Erreur serveur HTTP $code")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Pair(false, "Impossible de joindre le serveur : ${e.message}")
            }
        }

    /** Version de l'instance GeoNature via `/api/gn_commons/config` (exposée par les
     *  GeoNature récents ; clé `GEONATURE_VERSION`). Best-effort : null si l'endpoint est
     *  absent (404 sur les vieilles versions), protégé, ou si la clé a changé. */
    internal suspend fun versionGeoNature(base: String): String? = try {
        val reponse = clients.standard.get(urlAbsolue("$base/api/gn_commons/config")) {
            header(HttpHeaders.Accept, "application/json")
            timeout {
                // Une version illisible refuse le serveur : un réseau lent ne doit pas y suffire.
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
        }
        if (reponse.status.value != 200) {
            null
        } else {
            val json = JSONObject(reponse.bodyAsText())
            listOf("GEONATURE_VERSION", "geonature_version", "VERSION", "version")
                .firstNotNullOfOrNull { k -> json.optString(k, "").takeIf { it.isNotEmpty() } }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Diagnostique une réponse 3xx du serveur GeoNature en se basant sur le header `Location`.
     *  Trois sorties possibles, alignées sur la logique gn_mobile_monitoring :
     *
     *  - **HTTP → HTTPS** : le serveur redirige vers son équivalent HTTPS. Le POST initial
     *    serait perdu si le client rejouait la requête — on demande à l'utilisateur de mettre
     *    à jour son URL pour éviter d'envoyer son mot de passe en clair.
     *  - **Redirect SPA** : URL cible contient `#` (= fragment Angular `/#/login`), OU le
     *    redirect SORT de `/api/` (origine sous `/api/auth/login`, cible hors). C'est le
     *    pattern GeoNature pour signaler des identifiants invalides via une redirection.
     *  - **Autre** : redirection inattendue, on rapporte le code + le Location pour aider au
     *    diagnostic. */
    private fun diagnostiquerRedirect(reponse: HttpResponse, urlOrigine: Url): Pair<Boolean, String> {
        val location = reponse.headers[HttpHeaders.Location].orEmpty()
        val code = reponse.status.value
        return when {
            urlOrigine.protocol.name == "http" && location.startsWith("https://", ignoreCase = true) ->
                Pair(
                    false,
                    "Le serveur redirige vers HTTPS. Modifiez l'URL pour qu'elle commence par « https:// ».",
                )
            location.contains("#") ||
                (urlOrigine.encodedPath.contains("/api/") && !location.contains("/api/")) ->
                Pair(false, "Identifiant ou mot de passe incorrect")
            else ->
                Pair(false, "Redirection inattendue ($code) → $location")
        }
    }

    /** Cache de session du dernier login réussi : permet aux appels en rafale (par ex. 73
     *  chargerObjet en parallèle pour la carte d'un protocole) de ne pas re-authentifier
     *  chaque fois. Invalidé quand (base, login) change ou après [CACHE_TTL_MS]. */
    private data class CacheAuth(
        val base: String,
        val login: String,
        val token: String?,
        val idRole: Int?,
        val cookies: String,
        val expireAt: Long,
        /** L'organisme et le nom complet de l'utilisateur, renvoyés par le login. */
        val idOrganisme: Int? = null,
        val nomComplet: String? = null,
    )

    /** L'organisme (`id_organisme`) et le nom complet (« NOM Prénom », le `nom_complet` du web) de
     *  l'utilisateur de la dernière connexion à ([base], [login]), sans réseau ; null sans connexion
     *  récente. */
    internal fun identiteEnCache(base: String, login: String): Pair<Int?, String?>? {
        val c = cache ?: return null
        return if (c.base == base && c.login == login) c.idOrganisme to c.nomComplet else null
    }

    @Volatile
    private var cache: CacheAuth? = null

    /** Vidage explicite du cache d'auth (à appeler depuis l'écran config quand l'utilisateur
     *  modifie ses identifiants ou l'URL serveur). Appelé aussi sur tout 401. */
    fun invaliderCache() {
        cache = null
    }

    init {
        clients.surNonAutorise = ::invaliderCache
    }

    /** Session déjà en cache (fraîche) pour ([base], [login]), SANS jamais déclencher de login
     *  réseau. Null si aucun login récent — l'appelant se débrouille alors sans auth (usage :
     *  auth opportuniste des téléchargements de pictos, cf. PictoCache). Même forme de retour
     *  que [loginAvecCookies] : (token, idRole, cookies). */
    internal fun sessionEnCache(base: String, login: String): Triple<String?, Int?, String>? {
        val c = cache ?: return null
        return if (c.base == base && c.login == login && c.expireAt > horloge())
            Triple(c.token, c.idRole, c.cookies)
        else null
    }

    /** Verrou de sérialisation des logins réseau : au miss de cache, N appels parallèles (rafale
     *  chargerObjet pour une carte) déclenchaient N POST /auth/login simultanés. Verrou GLOBAL
     *  simple ASSUMÉ bien que la fonction fasse du réseau : l'appel est court (un POST unique,
     *  timeout 15 s), un seul serveur est configuré à la fois (un verrou par (base, login)
     *  serait sur-machiné), et le double-check du cache sous verrou garantit que seul le 1ᵉʳ
     *  appel touche le réseau — les suivants ressortent sur le cache tout juste rempli. */
    private val verrouLogin = Mutex()

    /** (token, idRole, cookies) — cookies à renvoyer avec les appels suivants. null = REFUS
     *  (HTTP ≠ 200, réponse non-JSON, 200 sans jeton) ; une IOException = problème RÉSEAU. */
    internal suspend fun loginAvecCookies(base: String, login: String, password: String): Triple<String?, Int?, String>? {
        // Fast-path SANS verrou : cache frais → zéro contention (cas ultra-majoritaire).
        sessionEnCache(base, login)?.let { return it }
        return verrouLogin.withLock {
            // Double-check : un appel concurrent a pu remplir le cache pendant l'attente du verrou.
            sessionEnCache(base, login)?.let { return@withLock it }
            val now = horloge()
            try {
                val reponse = clients.sansRedirection.post(urlAbsolue("$base/api/auth/login")) {
                    contentType(ContentType.Application.Json)
                    setBody(JSONObject().put("login", login).put("password", password).toString())
                    timeout {
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }
                if (reponse.status.value != 200) return@withLock null
                val ct = reponse.headers[HttpHeaders.ContentType] ?: ""
                if (!ct.contains("json")) return@withLock null

                val cookies = cookiesDeSession(reponse.headers.getAll(HttpHeaders.SetCookie).orEmpty())
                val json = JSONObject(reponse.bodyAsText())
                val token = extractToken(json)

                // Sur GeoNature, id_role est souvent dans l'objet "user"
                val userJson = json.optJSONObject("user")
                val idRole = userJson?.optInt("id_role", -1)?.takeIf { it > 0 }
                    ?: json.optInt("id_role", -1).takeIf { it > 0 }

                // ÉCHEC SOFT Flask-JWT : HTTP 200 avec un body `msg` SANS token ni user (mauvais
                // identifiants sur certaines instances — le même motif que gère testerConnexion).
                // Le traiter en succès mettait en cache 5 min un Triple non authentifié : tous les
                // appels partaient sans Authorization → 401/403 opaques en aval (audit N3).
                if (token == null && idRole == null) return@withLock null

                val idOrganisme = userJson?.optInt("id_organisme", -1)?.takeIf { it > 0 }
                cache = CacheAuth(base, login, token, idRole, cookies, now + CACHE_TTL_MS, idOrganisme, extraireNomComplet(json))
                Triple(token, idRole, cookies)
            } catch (e: IOException) {
                // RÉSEAU (hôte introuvable, timeout, connexion refusée) ≠ refus d'authentification :
                // relevée telle quelle pour que l'appelant affiche « Pas de réseau » et non
                // « Identifiants expirés (HTTP 401) » — hors-ligne, l'utilisateur ressaisissait ses
                // identifiants, et une faute purgeait les caches (audit 2026-08-27). Null reste
                // réservé au REFUS (HTTP ≠ 200, réponse non-JSON, 200 sans jeton).
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    internal suspend fun login(base: String, login: String, password: String): Pair<String?, Int?>? =
        loginAvecCookies(base, login, password)?.let { (token, idRole, _) -> Pair(token, idRole) }

    /** Extrait le nom complet du payload JSON de login : son `nom_complet`, sinon « NOM Prénom »,
     *  comme le construit le web (`nom_role + ' ' + prenom_role`). Renvoie null si absent. */
    private fun extraireNomComplet(json: JSONObject): String? {
        val userJson = json.optJSONObject("user") ?: return null
        userJson.optString("nom_complet", "").trim().takeIf { it.isNotEmpty() && it != "null" }?.let { return it }
        val prenom = userJson.optString("prenom_role", "").trim()
        val nom = userJson.optString("nom_role", "").trim()
        val complet = listOf(nom, prenom).filter { it.isNotEmpty() }.joinToString(" ")
        return complet.takeIf { it.isNotEmpty() }
    }

    /** Extrait l'id_role de l'utilisateur connecté du payload de login. Renvoie null si absent. */
    private fun extraireIdRole(json: JSONObject): Int? {
        val userJson = json.optJSONObject("user")
        return userJson?.optInt("id_role", -1)?.takeIf { it > 0 }
            ?: json.optInt("id_role", -1).takeIf { it > 0 }
    }

    private fun extractToken(json: JSONObject): String? {
        val userJson = json.optJSONObject("user")
        return json.optString("access_token").takeIf { it.isNotEmpty() }
            ?: json.optString("token").takeIf { it.isNotEmpty() }
            ?: userJson?.optString("access_token").takeIf { !it.isNullOrEmpty() }
            ?: userJson?.optString("token").takeIf { !it.isNullOrEmpty() }
    }

    private companion object {
        const val CACHE_TTL_MS = 5L * 60L * 1000L // 5 minutes
    }
}
