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

// Extrait tel quel de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/ObservationsFragment.kt).

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.Observation

/** Un relevé = un point + un instant + une session de saisie (mono-taxon ou multi-taxons).
 *  Les obs sans releveId (saisie rapide, import GPX) deviennent chacune un relevé solo. */
data class Releve(
    /** Clé de groupage. Null pour les solos (saisie mono-taxon historique). */
    val releveId: String?,
    val observations: List<Observation>,
) {
    val premier: Observation get() = observations.first()
    val date: Long get() = observations.minOf { it.date }
    val totalIndividus: Int get() = observations.sumOf { it.totalIndividus }
}

/** Regroupe une liste d'obs par releveId. Préserve l'ordre — les obs sans releveId restent
 *  des relevés solos, celles avec le même releveId fusionnent en un seul. */
fun List<Observation>.grouperEnReleves(): List<Releve> {
    val groupes = LinkedHashMap<String, MutableList<Observation>>()
    val solos = mutableListOf<Releve>()
    forEach { obs ->
        val rid = obs.releveId
        if (rid.isNullOrEmpty()) solos.add(Releve(null, listOf(obs)))
        else groupes.getOrPut(rid) { mutableListOf() }.add(obs)
    }
    val groupes2 = groupes.map { (rid, obs) -> Releve(rid, obs) }
    return (groupes2 + solos).sortedByDescending { it.date }
}
