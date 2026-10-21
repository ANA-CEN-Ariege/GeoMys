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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/MonitoringCache.kt) :
// l'objet devient une classe sur son dossier ; java.io remplacé par okio et FichierAtomique ; la
// ConcurrentHashMap par une map sous verrou d'atomicfu (même garde de génération).

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.plateforme.exclureDesSauvegardes
import fr.ariegenature.geomys.store.io.FichierAtomique
import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/** Cache offline du module Suivis (gn_module_monitoring). Stocke les payloads JSON
 *  bruts retournés par les 4 endpoints clé : modules, config (schéma), object/<module>/module
 *  (enfants directs), object/<module>/<type>/<id> (fiche objet).
 *
 *  Stratégie d'utilisation côté [fr.ariegenature.geomys.network.MonitoringApi] :
 *  - Succès HTTP → on écrit le payload brut dans le cache (write-through).
 *  - Échec réseau (IOException, timeout) → on retombe sur le cache si présent.
 *  - HTTP non-OK (4xx/5xx) → on ne touche pas au cache et on propage l'erreur.
 *
 *  Le pré-chargement complet (tous les modules + leurs enfants + leurs fiches) est
 *  orchestré par [fr.ariegenature.geomys.network.MonitoringSync.synchroniserSuivis]. Une seule
 *  instance dans l'appli, sur le dossier [dir] (`filesDir/monitoring` sous Android). */
class MonitoringCache(private val dir: Path, private val systeme: FileSystem = FileSystem.SYSTEM) {

    /** Cache MÉMOIRE des payloads déjà lus (clé fichier → JSON brut). Évite de relire le disque à
     *  CHAQUE appel de getJson : les résolveurs de labels/genres/chemins (MonitoringApi) tapent le
     *  cache des dizaines de fois par écran (par ligne de « Mes visites », par enfant de fiche).
     *  Peuplé UNIQUEMENT à la lecture (audit 2026-08-23 : mémoïser aussi dans setJson faisait
     *  retenir en RAM l'intégralité de la synchro BFS — tous les modules × fiches × datalists —
     *  à vie du process ; en lecture seule, la map ne contient que ce que les écrans affichent).
     *  Seules les valeurs PRÉSENTES sont mémoïsées (une absence coûte juste un `exists()`).
     *  Cohérence : INVALIDATION par [setJson]/[vider] + garde [generation] contre la course
     *  lecture/écriture (un getJson concurrent d'un setJson ne doit pas re-mémoïser la vieille
     *  valeur par-dessus la neuve). Thread-safe (UI + IO), sous [verrouMemoire]. */
    private val memoire = HashMap<String, String>()
    private val verrouMemoire = SynchronizedObject()

    /** Les écritures se suivent : le fichier temporaire d'une clé porte un nom fixe, et deux
     *  écritures simultanées de la même clé se le partageaient (défaut n° 11 de l'inventaire,
     *  présent dans GeoMys-Android). */
    private val verrouEcriture = SynchronizedObject()

    /** Incrémenté à chaque mutation ([setJson]/[vider]) : un getJson qui a lu le disque AVANT la
     *  mutation détecte le changement et renonce à mémoïser sa valeur (devenue périmée). */
    @Volatile private var generation = 0

    init {
        // Retéléchargeable : hors des sauvegardes, comme le dossier monitoring sous Android.
        systeme.createDirectories(dir)
        exclureDesSauvegardes(dir)
    }

    /** Normalise une clé arbitraire en nom de fichier safe (préserve les caractères
     *  alphanumériques + .-_, remplace tout le reste par _). Évite les ennuis avec
     *  des module_code contenant des espaces ou des accents. */
    private fun safeKey(raw: String): String = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun fichier(nom: String): Path = dir / nom

    fun getJson(name: String): String? {
        synchronized(verrouMemoire) { memoire[name] }?.let { return it }
        return try {
            val g = generation
            val f = fichier(name)
            if (systeme.exists(f)) systeme.read(f) { readUtf8() }.also {
                // Mémoïse seulement si AUCUNE mutation n'a eu lieu pendant la lecture disque
                // (sinon la valeur lue peut être périmée) ; sans écraser une entrée posée
                // entre-temps par un autre lecteur.
                synchronized(verrouMemoire) { if (g == generation && name !in memoire) memoire[name] = it }
            } else null
        } catch (_: Exception) { null }
    }

    /** Écriture atomique — évite un fichier tronqué si le process est tué pendant la
     *  sauvegarde (cohérent avec TaxRefCache). */
    fun setJson(name: String, json: String) {
        try {
            synchronized(verrouEcriture) { FichierAtomique(systeme, fichier(name), "MonitoringCache").ecrire(json) }
            // INVALIDE l'entrée mémoire au lieu de la peupler : la synchro (qui écrit tout)
            // ne doit pas charger la RAM ; le prochain getJson relira le disque et mémoïsera
            // à la demande. generation++ d'abord, pour qu'un getJson concurrent qui a lu
            // l'ANCIEN fichier renonce à re-mémoïser sa valeur périmée.
            synchronized(verrouMemoire) {
                generation++
                memoire.remove(name)
            }
        } catch (_: Exception) {}
    }

    fun vider() {
        try {
            // Fichiers d'abord, mémoire ensuite : un getJson concurrent ne peut au pire que
            // resservir brièvement une entrée mémoire sur le point d'être purgée — jamais
            // re-mémoïser un fichier en cours de suppression (generation++ l'en empêche).
            // FICHIERS SEULEMENT : le sous-dossier pictos/ (PictoCache) vit sous ce même
            // répertoire et a sa PROPRE purge — il ne survivait ici que parce que delete()
            // échoue sur un dossier non vide ; un dossier pictos VIDE (1ʳᵉ configuration)
            // était réellement supprimé → plus aucun picto téléchargeable (audit m-N5 +
            // bug terrain 2026-08-24).
            synchronized(verrouMemoire) { generation++ }
            systeme.list(dir).forEach { if (systeme.metadataOrNull(it)?.isRegularFile == true) systeme.delete(it) }
            synchronized(verrouMemoire) { memoire.clear() }
        } catch (_: Exception) {}
    }

    // ─── Clés typées ──────────────────────────────────────────────────────────
    fun keyModules(): String = "modules.json"

    /** Les droits de création de l'utilisateur sur les modules (cf. DroitsSuivis), lus à la synchro. */
    fun keyDroits(): String = "droits_creation.json"
    fun keySchema(moduleCode: String): String = "schema_${safeKey(moduleCode)}.json"
    fun keyEnfants(moduleCode: String): String = "enfants_${safeKey(moduleCode)}.json"
    fun keyObjet(moduleCode: String, type: String, id: Int): String =
        "objet_${safeKey(moduleCode)}_${safeKey(type)}_$id.json"
    /** Cache local d'une liste UsersHub (`/api/users/menu/<id_liste>`) — utilisé pour
     *  l'autocomplétion observateurs et la résolution id_role → nom complet en offline. */
    fun keyObservateurs(idListe: Int): String = "observateurs_$idListe.json"

    /** Cache local des OPTIONS d'un widget datalist/nomenclature de formulaire monitoring,
     *  indexé par son `apiUrl` (query params compris → clé unique par filtre). Peuplé au 1er
     *  fetch live ET au prefetch de synchro ; relu hors-ligne pour ne pas bloquer la saisie
     *  d'une nouvelle visite (liste déroulante vide). */
    fun keyOptionsDatalist(apiUrl: String): String {
        val nom = safeKey(apiUrl)
        if (nom.length <= LONGUEUR_MAX_ADRESSE) return "datalist_$nom.json"
        // Une adresse trop longue pour un nom de fichier, limité à 255 octets sous Android et iOS :
        // une nomenclature restreinte à beaucoup de codes (449 octets pour le « Code phénologique »
        // de pt_ecoute_avifaune, sur le serveur de démonstration, le 21 octobre) ne s'écrivait pas,
        // sans erreur visible, et sa liste restait vide. Son début, puis l'empreinte de l'adresse
        // entière, qui sépare deux adresses au même début.
        val empreinte = apiUrl.encodeUtf8().sha256().hex().take(16)
        return "datalist_${nom.take(LONGUEUR_MAX_ADRESSE - empreinte.length - 1)}_$empreinte.json"
    }

    private companion object {
        /** La partie adresse d'un nom de fichier : avec « datalist_ », « .json » et le plus long
         *  suffixe d'écriture (« .corrupt », cf. FichierAtomique), le nom reste sous 255 octets. */
        const val LONGUEUR_MAX_ADRESSE = 200
    }
}
