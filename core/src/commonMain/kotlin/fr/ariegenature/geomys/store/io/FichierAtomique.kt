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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/OutboxMonitoring.kt,
// lireBrut, ecrireBrut et quarantaine) : java.io.File remplacé par okio, fsync par
// synchroniserSurDisque.

package fr.ariegenature.geomys.store.io

import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.plateforme.synchroniserSurDisque
import okio.BufferedSink
import okio.FileSystem
import okio.IOException
import okio.Path

/** Un fichier écrit de façon durable et atomique : le contenu part dans `<nom>.tmp`, est forcé
 *  sur le disque, puis remplace le fichier d'un seul renommage. Une coupure à n'importe quel
 *  moment laisse donc soit l'ancien contenu, soit le nouveau, jamais un fichier tronqué.
 *
 *  [synchroniser] n'est remplacé que dans les tests sur un système de fichiers en mémoire. */
class FichierAtomique(
    private val systeme: FileSystem,
    val chemin: Path,
    private val nom: String,
    private val synchroniser: (Path) -> Unit = ::synchroniserSurDisque,
) {
    private val dossier: Path = requireNotNull(chemin.parent) { "pas de dossier parent : $chemin" }
    private val temporaire: Path = dossier / (chemin.name + ".tmp")

    /** La copie du PREMIER contenu illisible, que les incidents suivants n'écrasent jamais. */
    val quarantaine: Path = dossier / (chemin.name + ".corrupt")

    /** Le contenu du fichier, ou null s'il n'existe pas. */
    fun lire(): String? = if (systeme.exists(chemin)) systeme.read(chemin) { readUtf8() } else null

    /** Écrit [contenu] durablement. Rend false si la persistance a échoué (disque plein, E/S…) :
     *  l'appelant DOIT le vérifier pour une création, sinon la saisie que l'utilisateur croit
     *  enregistrée n'existe nulle part. */
    fun ecrire(contenu: String): Boolean = ecrire { writeUtf8(contenu) }

    /** Comme [ecrire], le contenu étant produit en flux par [ecrivain] : un gros fichier (le cache
     *  TaxRef) n'est jamais construit en entier en mémoire. */
    fun ecrire(ecrivain: BufferedSink.() -> Unit): Boolean = try {
        systeme.createDirectories(dossier)
        systeme.write(temporaire) { ecrivain() }
        // Sans fsync, une coupure d'alimentation brutale peut valider le renommage sans les
        // blocs de données : fichier cible vide ou tronqué.
        synchroniser(temporaire)
        try {
            systeme.atomicMove(temporaire, chemin)
        } catch (e: IOException) {
            systeme.delete(chemin)
            systeme.atomicMove(temporaire, chemin)
        }
        Journal.i(nom, "sauvegarde OK (path=$chemin)")
        true
    } catch (e: Exception) {
        Journal.w(nom, "sauvegarder ECHEC : ${e.message}", e)
        runCatching { systeme.delete(temporaire) }
        false
    }

    /** Met de côté une copie fidèle du fichier fautif (premier incident seulement), avant qu'une
     *  écriture ne l'écrase. */
    fun mettreEnQuarantaine() {
        try {
            if (!systeme.exists(quarantaine) && systeme.exists(chemin)) {
                systeme.copy(chemin, quarantaine)
            }
        } catch (_: Exception) {
        }
    }
}
