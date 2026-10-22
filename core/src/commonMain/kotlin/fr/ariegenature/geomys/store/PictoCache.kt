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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/PictoCache.kt) :
// l'objet devient une classe ; HttpURLConnection remplacé par ClientsGeoNature (téléchargements
// suspend), java.io par okio, java.net.URL par Url de Ktor ; le contrôle d'image par BitmapFactory
// par celui des signatures de formats (estSignatureImage).

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.urlAbsolue
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import kotlin.coroutines.cancellation.CancellationException
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * Cache DISQUE des pictogrammes de protocole monitoring (une image `<module_code>.jpg`).
 *
 * Les images viennent de la CONVENTION serveur `<base>/api/media/monitorings/<code>/img.jpg`
 * (comme le web du module Suivi), INDÉPENDANTE de `module_picto` (souvent au défaut
 * « fa-puzzle-piece »). Objectif : affichage **instantané, persistant et HORS-LIGNE**, sans
 * re-télécharger à chaque ouverture de l'écran Suivis.
 *
 * Une seule instance dans l'appli, sur le dossier [dossier] (`filesDir/monitoring/pictos` sous
 * Android). Les téléchargements sont `suspend` et réservés à la synchro ([prefetch]) : l'écran
 * Suivis ne lit que [fichierLocal] (décision de l'utilisateur du 20 octobre : rien n'est téléchargé
 * hors synchro), ce qui rend sans objet le cache négatif d'un 404 de GeoMys-Android.
 */
class PictoCache(
    private val dossier: Path,
    private val clients: ClientsGeoNature,
    private val systeme: FileSystem = FileSystem.SYSTEM,
) {

    init {
        systeme.createDirectories(dossier)
    }

    private fun fichier(moduleCode: String): Path =
        dossier / (moduleCode.replace('/', '_') + ".jpg")

    /** Fichier local du picto s'il est en cache (présent et non vide), sinon null. */
    fun fichierLocal(moduleCode: String): Path? =
        fichier(moduleCode).takeIf { systeme.metadataOrNull(it)?.let { m -> m.isRegularFile && (m.size ?: 0L) > 0L } == true }

    /** URL du picto : image explicite portée par `module_picto` si c'en est une (URL/chemin/
     *  fichier image), sinon la convention monitoring `<base>/api/media/monitorings/<code>/img.jpg`. */
    fun urlPicto(base: String, moduleCode: String, modulePicto: String?): String {
        val b = base.trim().trimEnd('/')
        val p = modulePicto?.trim().orEmpty()
        if (p.startsWith("http://", true) || p.startsWith("https://", true)) return p
        val estImage = p.startsWith("/") ||
            Regex(".+\\.(png|jpe?g|gif|webp|svg)$", RegexOption.IGNORE_CASE).matches(p)
        if (estImage) return "$b${if (p.startsWith("/")) p else "/$p"}"
        return "$b/api/media/monitorings/$moduleCode/img.jpg"
    }

    /** Télécharge le picto depuis [url] et l'écrit sur disque (tmp + rename atomique). Renvoie le
     *  fichier en cas de succès, null sinon (404, réseau…). [token]/[cookies] optionnels : posés en
     *  Authorization Bearer / Cookie si fournis (serveurs qui protègent les médias) — jamais de
     *  login déclenché ici. */
    suspend fun telecharger(
        moduleCode: String, url: String, token: String? = null, cookies: String? = null,
        /** URL de l'instance GeoNature : jeton et cookies ne partent QUE vers son hôte et son
         *  schéma. Un `module_picto` absolu pointant ailleurs (CDN, ancien serveur, http nu)
         *  est téléchargé SANS session (audit sécurité 2026-08-27). Null = jamais d'auth. */
        base: String? = null,
    ): Path? = try {
        val sessionTransmissible = memeOrigine(url, base)
        val reponse = clients.standard.get(urlAbsolue(url)) {
            timeout {
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
            if (sessionTransmissible) {
                token?.takeIf { it.isNotEmpty() }?.let { bearerAuth(it) }
                cookies?.takeIf { it.isNotEmpty() }?.let { header(HttpHeaders.Cookie, it) }
            }
        }
        if (reponse.status.value != 200) {
            null
        } else {
            val octets = reponse.readRawBytes()
            if (octets.isEmpty() || !estImageValide(octets)) null else {
                // Recrée le dossier si besoin : MonitoringCache.vider() (changement d'identité
                // serveur, « Vider le cache ») supprimait le sous-dossier pictos/ quand il était
                // VIDE (1ʳᵉ configuration) → toutes les écritures échouaient ensuite en silence,
                // plus aucun picto ne s'affichait (bug terrain 2026-08-24).
                systeme.createDirectories(dossier)
                val cible = fichier(moduleCode)
                val tmp = dossier / (cible.name + ".tmp")
                systeme.write(tmp) { write(octets) }
                // Renommage atomique uniquement : une copie laissait une fenêtre « fichier
                // tronqué » (kill pendant la copie) qui serait resservie hors-ligne à jamais.
                // Un renommage qui échoue = pas de mise à jour (l'ancienne image, si présente,
                // reste servie ; sinon repli emoji côté UI) — dégradation sûre.
                try {
                    systeme.atomicMove(tmp, cible)
                    cible
                } catch (_: Exception) {
                    runCatching { systeme.delete(tmp) }
                    null
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Vrai si [octets] est une image. Un serveur qui répond 200 avec une page HTML (portail
     *  captif, proxy, page d'erreur) ne doit PAS être persisté : le fichier invalide serait
     *  resservi hors-ligne indéfiniment (audit 2026-08-23). Sous Android, BitmapFactory en lisait
     *  les dimensions ; ici, la signature des formats qu'il décode (JPEG, PNG, GIF, WebP, BMP). */
    private fun estImageValide(octets: ByteArray): Boolean = estSignatureImage(octets)

    /** true si [url] a le même hôte ET le même schéma que [base] (session transmissible). */
    internal fun memeOrigine(url: String, base: String?): Boolean {
        if (base.isNullOrBlank()) return false
        return try {
            val u = urlAbsolue(url)
            val b = urlAbsolue(base.trim().trimEnd('/'))
            u.host.equals(b.host, ignoreCase = true) && u.protocol.name.equals(b.protocol.name, ignoreCase = true)
        } catch (_: Exception) { false }
    }

    /** Prefetch (rafraîchissement) des pictos de [modules] (`module_code` → `module_picto`) pour
     *  l'usage HORS-LIGNE. Best-effort : un échec par module n'interrompt pas les autres. Re-écrit
     *  les images existantes (rafraîchissement au « Recharger les données »). [token]/[cookies]
     *  optionnels, transmis à [telecharger]. */
    suspend fun prefetch(base: String, modules: List<Pair<String, String?>>, token: String? = null, cookies: String? = null) {
        modules.forEach { (code, picto) ->
            telecharger(code, urlPicto(base, code, picto), token, cookies, base)
        }
    }

    /** Vide tout le cache des pictos (« Vider le cache » / changement d'identité serveur), y
     *  compris les marqueurs `.absent` des versions précédentes (list ne filtre pas par extension). */
    fun vider() {
        runCatching { systeme.list(dossier).forEach { systeme.delete(it) } }
    }
}

/** Vrai si [octets] commencent par la signature d'un format d'image que BitmapFactory décode
 *  sous Android : JPEG, PNG, GIF, WebP ou BMP. */
internal fun estSignatureImage(octets: ByteArray): Boolean {
    fun commencePar(vararg signature: Int): Boolean =
        octets.size >= signature.size && signature.indices.all { (octets[it].toInt() and 0xFF) == signature[it] }
    fun ascii(debut: Int, texte: String): Boolean =
        octets.size >= debut + texte.length && texte.indices.all { octets[debut + it].toInt() == texte[it].code }
    return commencePar(0xFF, 0xD8, 0xFF) ||
        commencePar(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) ||
        ascii(0, "GIF87a") || ascii(0, "GIF89a") ||
        (ascii(0, "RIFF") && ascii(8, "WEBP")) ||
        ascii(0, "BM")
}
