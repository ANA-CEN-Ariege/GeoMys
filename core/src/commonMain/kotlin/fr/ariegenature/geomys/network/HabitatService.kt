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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/HabitatService.kt) :
// l'objet devient une classe sur les deux caches HABREF ; HabitatSuggestion passe dans model/. La
// recherche sur le serveur, quand le cache était vide, a disparu le 20 octobre : les écrans ne lisent que
// le cache (décision de l'utilisateur), que la synchro remplit de tout le référentiel.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.model.HabitatSuggestion
import fr.ariegenature.geomys.store.HabitatCacheAcces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** Recherche d'habitats (référentiel HABREF) pour le champ `cd_hab` du relevé Occtax et le
 *  formulaire d'habitat OccHab, dans le cache que la synchro remplit (`/api/habref/habitats/
 *  autocomplete`, tout le référentiel). Terme trop court ou cache vide → liste vide. */
class HabitatService(
    private val habitatsOcctax: HabitatCacheAcces,
    private val habitatsOccHab: HabitatCacheAcces,
) {

    /** Recherche d'habitats : dans le cache OCCTAX, ou le cache DÉDIÉ OccHab si [occhab] (jamais le
     *  cache OCCTAX ; recherche sensible aux accents = mêmes résultats que le web, restreinte à la
     *  liste `OCCHAB.ID_LIST_HABITAT` chargée par la synchro). */
    suspend fun rechercher(terme: String, limite: Int = 20, occhab: Boolean = false, cdTypo: Int? = null): List<HabitatSuggestion> =
        withContext(Dispatchers.IO) {
            val t = terme.trim()
            if (t.length < 2) return@withContext emptyList()
            val cache = if (occhab) habitatsOccHab else habitatsOcctax
            if (cache.estDisponible) cache.rechercher(t, limite, cdTypo) else emptyList()
        }
}
