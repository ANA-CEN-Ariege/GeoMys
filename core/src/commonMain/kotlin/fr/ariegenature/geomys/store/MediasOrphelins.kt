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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/MediasOrphelins.kt) :
// l'objet devient une classe sur le dossier des médias et les deux stores qui les référencent ;
// java.io.File remplacé par okio.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.util.maintenantMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * Balayage des MÉDIAS ORPHELINS de `medias/` (photos et sons importés, 0,3 à 1 Mo pièce). Aucun
 * chemin ne supprimait ces fichiers après l'envoi ou la suppression d'une saisie (audit 2026-08-23,
 * M-N2) : sur une saison de terrain, des centaines de Mo de médias s'accumulaient sur l'appareil.
 *
 * Plutôt que de purger à chaque suppression (en devant vérifier qu'aucune AUTRE entrée ne référence
 * le même fichier), un BALAYAGE au démarrage supprime les fichiers que plus aucun store ne
 * référence — couvre d'un coup tous les chemins d'abandon (envoi réussi puis purge, suppression de
 * saisie, arrêt brutal entre l'import et l'enregistrement).
 *
 * Garde d'ÂGE (48 h) : un média tout juste importé peut n'être référencé que par l'état d'un
 * formulaire EN COURS (valeurs pas encore écrites dans un store). On ne touche donc qu'aux fichiers
 * vieux de plus de 48 h, fenêtre largement suffisante pour terminer une saisie entamée.
 */
class MediasOrphelins(
    private val systeme: FileSystem,
    private val dossier: Path,
    private val sorties: SortieStore,
    private val outbox: OutboxMonitoring,
    private val horloge: () -> Long = ::maintenantMillis,
) {

    /** Lance le balayage, hors du fil principal. Au mieux : toute erreur est avalée, le balayage
     *  retentera au prochain démarrage. Renvoie le nombre de fichiers supprimés. */
    suspend fun purger(): Int = withContext(Dispatchers.IO) {
        try {
            val fichiers = systeme.listOrNull(dossier)?.filter { systeme.metadataOrNull(it)?.isRegularFile == true }
                ?: return@withContext 0
            if (fichiers.isEmpty()) return@withContext 0

            // Ensemble des NOMS de fichiers référencés par les stores. Comparaison par nom : les
            // références mélangent URI file:///… et chemins absolus, mais les noms générés à
            // l'import (horodatage et aléa) sont uniques dans le dossier.
            val references = HashSet<String>()
            fun referencer(uri: String?) {
                val nom = uri?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: return
                references.add(nom)
            }
            // Occtax : médias portés par les observations (dénombrement 0 et additionnels).
            sorties.charger().forEach { sortie ->
                sortie.observations.forEach { obs ->
                    obs.mediaUrisCounting0.forEach { referencer(it) }
                    obs.denombrementsAdditionnels.forEach { d -> d.mediaUris.forEach { referencer(it) } }
                }
            }
            // Suivis : médias des saisies en attente (plusieurs pièces et ancien champ unique).
            outbox.tout().forEach { saisie -> saisie.mediasLocaux().forEach { referencer(it) } }

            val seuil = horloge() - AGE_MINIMUM_MS
            var supprimes = 0
            fichiers.forEach { f ->
                val modifie = systeme.metadataOrNull(f)?.lastModifiedAtMillis ?: return@forEach
                if (f.name !in references && modifie < seuil) {
                    try {
                        systeme.delete(f)
                        supprimes++
                    } catch (_: Exception) {}
                }
            }
            if (supprimes > 0) Journal.i("MediasOrphelins", "$supprimes média(s) orphelin(s) purgé(s)")
            supprimes
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) { 0 }
    }

    private companion object {
        const val AGE_MINIMUM_MS = 48L * 3600 * 1000
    }
}
