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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/JsonCollectionStore.kt) :
// Gson remplacé par kotlinx.serialization, décodé élément par élément ; synchronized remplacé par
// un Mutex, les API deviennent suspend (INVENTAIRE.md § 11, décision 7) ; android.util.Log par
// Journal.

package fr.ariegenature.geomys.store.io

import fr.ariegenature.geomys.journal.Journal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Base commune aux stores JSON « collection » (une liste d'objets sérialisée en un blob JSON) :
 * SortieStore (Occtax), OccHabStore, OutboxMonitoring. Mutualise la logique DÉLICATE :
 *
 *  - **cache mémoire** (évite de re-désérialiser tout le store à chaque action de saisie). Le
 *    store doit donc être unique dans le processus ;
 *  - **lecture élément par élément** et **quarantaine** : une entrée illisible est écartée sans
 *    perdre les autres, et le blob d'origine est mis de côté (premier incident, jamais écrasé)
 *    avant que la prochaine écriture ne l'écrase ;
 *  - **lire-modifier-écrire ATOMIQUE sous verrou** ([muter]) : les mutations viennent de l'UI
 *    (saisie au fil de l'eau) ET du chemin d'envoi ; sans verrou, deux écrivains croisés se
 *    perdent mutuellement des entrées (lost update).
 *
 * Le BACKEND reste propre à chaque store, via [lireBrut] / [ecrireBrut] / [quarantaine] ; voir
 * [StoreFichierJson] pour le fichier atomique.
 */
abstract class JsonCollectionStore<T : Any>(serialiseur: KSerializer<T>) {

    private val serialiseurElement = serialiseur
    private val liste = ListSerializer(serialiseur)
    private val verrou = Mutex()

    /** null = à relire du disque. */
    private var cache: List<T>? = null

    /** Étiquette des journaux. */
    protected abstract val nom: String

    /** Lit le blob JSON brut du backend (null = store vide / absent). */
    protected abstract suspend fun lireBrut(): String?

    /** Persiste le blob JSON (true = persistance DURABLE confirmée). */
    protected abstract suspend fun ecrireBrut(json: String): Boolean

    /** Met le blob fautif de côté (premier incident) avant qu'il soit écrasé. */
    protected abstract suspend fun quarantaine(json: String)

    /** Écarte, AVANT décodage, une entrée que le décodage tolérant accepterait à tort : un état
     *  d'envoi inconnu ramené à sa valeur par défaut, un identifiant absent régénéré… Gson, lui,
     *  les laissait à null, et le store concerné les rejetait. */
    protected open fun accepter(brut: JsonObject): Boolean = true

    /** Reconstruit une entrée SÛRE après décodage ; renvoie null pour l'ÉCARTER. */
    protected open fun normaliser(item: T): T? = item

    /** Snapshot défensif : les appelants peuvent muter la copie sans affecter le cache. */
    suspend fun charger(): MutableList<T> = verrou.withLock { ArrayList(chargerInterne()) }

    /** Oublie le cache mémoire : la prochaine lecture repart du disque (démarrage à froid). */
    suspend fun oublierCache() {
        verrou.withLock { cache = null }
    }

    private suspend fun chargerInterne(): List<T> {
        cache?.let { return it }
        val json = lireBrut()
        val parsed: List<T> = if (json == null) {
            emptyList()
        } else {
            val lecture = decoder(json)
            if (lecture.erreur != null) {
                Journal.e(nom, "charger : JSON illisible, mise en quarantaine", lecture.erreur)
                quarantaine(json)
            } else if (lecture.ecartees > 0) {
                // Des entrées écartées = JSON corrompu-mais-parsable : on met le blob d'origine
                // de côté AVANT que la prochaine écriture ne l'écrase (sinon perte définitive).
                Journal.w(nom, "charger : ${lecture.ecartees} entrée(s) illisible(s) écartée(s)")
                quarantaine(json)
            }
            lecture.valides
        }
        cache = parsed
        return parsed
    }

    private class Lecture<T>(val valides: List<T>, val ecartees: Int, val erreur: Exception?)

    private fun decoder(json: String): Lecture<T> {
        val tableau = try {
            JSON_STOCKAGE.parseToJsonElement(json) as? JsonArray
                ?: throw IllegalArgumentException("le contenu n'est pas un tableau JSON")
        } catch (e: Exception) {
            return Lecture(emptyList(), 0, e)
        }
        val valides = ArrayList<T>(tableau.size)
        var ecartees = 0
        for (brut in tableau) {
            // Une entrée null est sautée sans quarantaine, comme le filterNotNull d'Android.
            if (brut is JsonNull) continue
            val item = try {
                if (brut is JsonObject && accepter(brut)) normaliser(JSON_STOCKAGE.decodeFromJsonElement(serialiseurElement, brut)) else null
            } catch (e: Exception) {
                null
            }
            if (item != null) valides.add(item) else ecartees++
        }
        return Lecture(valides, ecartees, null)
    }

    /** Écrit [items] via le backend et ne met le cache à jour QU'APRÈS une persistance confirmée
     *  (sinon l'app afficherait comme sauvées des données qui disparaîtraient au redémarrage). */
    protected suspend fun sauvegarder(items: List<T>): Boolean = verrou.withLock { sauvegarderInterne(items) }

    private suspend fun sauvegarderInterne(items: List<T>): Boolean {
        val ok = ecrireBrut(JSON_STOCKAGE.encodeToString(liste, items))
        if (ok) {
            cache = ArrayList(items)
        } else {
            Journal.e(nom, "sauvegarder ECHEC — cache mémoire NON mis à jour (disque conservé)")
        }
        return ok
    }

    /** Lire-modifier-écrire ATOMIQUE : le verrou est tenu du chargement à la sauvegarde, donc deux
     *  écrivains croisés (UI + envoi) ne peuvent plus se perdre une entrée. Renvoie le succès de
     *  la persistance. */
    protected suspend fun muter(bloc: (MutableList<T>) -> Unit): Boolean = verrou.withLock {
        val liste = ArrayList(chargerInterne())
        bloc(liste)
        sauvegarderInterne(liste)
    }

    /** Comme [muter] mais renvoie la valeur calculée par [bloc] (le succès de persistance est
     *  ignoré — parité avec l'ancien `supprimerCascade` qui renvoyait un compteur). */
    protected suspend fun <R> muterCalcul(bloc: (MutableList<T>) -> R): R = verrou.withLock {
        val liste = ArrayList(chargerInterne())
        val r = bloc(liste)
        sauvegarderInterne(liste)
        r
    }
}
