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

// Le téléchargement de zones du Maps Manager : le CacheManager d'osmdroid sous GeoMys-Android (downloadAreaAsync
// et le cache SqlTileWriter), les paquets hors ligne de MapLibre ici (essayés au prototype du lot 0).

package fr.ariegenature.geomys.ui.carte

import fr.ariegenature.geomys.geo.Emprise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.OfflineStorage
import org.maplibre.compose.offline.OfflineStorageState
import org.maplibre.compose.offline.offlineStorage
import org.maplibre.spatialk.geojson.BoundingBox

/** L'issue d'un téléchargement de zone. */
sealed interface IssueTelechargement {
    data object Termine : IssueTelechargement

    /** Des tuiles n'ont pas été reçues ; [message] : l'erreur de MapLibre. */
    data class Incomplet(val message: String?) : IssueTelechargement

    /** La zone dépasse le nombre de tuiles que MapLibre accepte. */
    data class Limite(val limite: Long) : IssueTelechargement
}

/** Les zones téléchargées pour le hors-ligne. Les niveaux de zoom sont ceux des tuiles de 256 px. */
interface ZonesHorsLigne {
    /** Les octets reçus par toutes les zones. */
    val occupation: Flow<Long>

    /** Télécharge l'[emprise] du style [urlStyle], des niveaux [niveauMin] à [niveauMax] ; [progression]
     *  reçoit les ressources reçues et attendues. */
    suspend fun telecharger(
        urlStyle: String,
        emprise: Emprise,
        niveauMin: Int,
        niveauMax: Int,
        densite: Float,
        progression: (recues: Long, attendues: Long) -> Unit,
    ): IssueTelechargement

    /** Supprime toutes les zones et le cache des tuiles vues en ligne ; faux si une suppression a échoué. */
    suspend fun viderTout(): Boolean
}

/** Les paquets hors ligne de MapLibre. Une zone n'expire pas : seul « Vider » la supprime, avec le cache
 *  des tuiles vues en ligne (le cache d'osmdroid était purgé au-delà de 800 Mo). */
class ZonesMapLibre(private val stockage: OfflineStorage = DefaultMapRuntime.instance.offlineStorage) : ZonesHorsLigne {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val occupation: Flow<Long> = stockage.state.flatMapLatest { etat ->
        val paquets = (etat as? OfflineStorageState.Ready)?.packs.orEmpty().toList()
        if (paquets.isEmpty()) {
            flowOf(0L)
        } else {
            combine(paquets.map { it.downloadProgress }) { progressions ->
                progressions.sumOf { (it as? DownloadProgress.Healthy)?.completedResourceBytes ?: 0L }
            }
        }
    }

    private suspend fun pret(): OfflineStorageState.Ready? =
        stockage.state.first { it !is OfflineStorageState.Loading } as? OfflineStorageState.Ready

    override suspend fun telecharger(
        urlStyle: String,
        emprise: Emprise,
        niveauMin: Int,
        niveauMax: Int,
        densite: Float,
        progression: (recues: Long, attendues: Long) -> Unit,
    ): IssueTelechargement {
        if (pret() == null) return IssueTelechargement.Incomplet("stockage hors ligne indisponible")
        // MapLibre zoome en tuiles de 512 px : ses zooms 13 à 16 couvrent les niveaux 14 à 17 d'une source
        // de 256 px.
        val paquet = stockage.create(
            definition = OfflinePackDefinition.TilePyramid(
                styleUrl = urlStyle,
                bounds = BoundingBox(west = emprise.ouest, south = emprise.sud, east = emprise.est, north = emprise.nord),
                pixelRatio = densite,
                minZoom = (niveauMin - 1).coerceAtLeast(0).toDouble(),
                maxZoom = (niveauMax - 1).toDouble(),
            ),
            metadata = urlStyle.encodeToByteArray(),
        )
        stockage.resume(paquet)
        return try {
            when (val fin = paquet.downloadProgress.first { etat ->
                when (etat) {
                    is DownloadProgress.Healthy -> {
                        progression(etat.completedResourceCount, etat.requiredResourceCount)
                        etat.status == DownloadStatus.Complete
                    }
                    is DownloadProgress.Error, is DownloadProgress.TileLimitExceeded -> true
                    else -> false
                }
            }) {
                is DownloadProgress.Error -> IssueTelechargement.Incomplet(fin.message)
                is DownloadProgress.TileLimitExceeded -> IssueTelechargement.Limite(fin.limit)
                else -> IssueTelechargement.Termine
            }
        } catch (e: CancellationException) {
            stockage.pause(paquet)
            throw e
        }
    }

    override suspend fun viderTout(): Boolean {
        val etat = pret() ?: return false
        var complet = true
        for (paquet in etat.packs) {
            try {
                stockage.delete(paquet)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                complet = false
            }
        }
        try {
            stockage.clearAmbientCache()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            complet = false
        }
        return complet
    }
}
