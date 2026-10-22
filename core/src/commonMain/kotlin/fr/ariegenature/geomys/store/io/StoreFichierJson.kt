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

package fr.ariegenature.geomys.store.io

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer

/** Un [JsonCollectionStore] dans un fichier écrit par [FichierAtomique] : le backend
 *  d'OutboxMonitoring dans GeoMys-Android, retenu ici pour tous les stores de saisies. SortieStore
 *  et OccHabStore quittent ainsi les SharedPreferences, dont l'écriture ne rend pas de résultat
 *  fiable sur toutes les plateformes. La quarantaine copie le fichier lui-même. */
open class StoreFichierJson<T : Any>(
    serialiseur: KSerializer<T>,
    private val fichier: FichierAtomique,
    override val nom: String,
) : JsonCollectionStore<T>(serialiseur) {

    override suspend fun lireBrut(): String? = withContext(Dispatchers.IO) { fichier.lire() }

    override suspend fun ecrireBrut(json: String): Boolean = withContext(Dispatchers.IO) { fichier.ecrire(json) }

    override suspend fun quarantaine(json: String) {
        withContext(Dispatchers.IO) { fichier.mettreEnQuarantaine() }
    }
}
