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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabDetailsDialog.kt,
// construireSpinnerNomenclature) : la liste d'une nomenclature OccHab, sans son Spinner.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.plateforme.comparateurFrancais
import fr.ariegenature.geomys.store.NomValeur

/** La liste déroulante d'une nomenclature OccHab (détails de la station, habitat) : « — Non
 *  renseigné — » en tête, puis les valeurs du cache triées alphabétiquement (français, insensible
 *  à la casse et aux accents), comme les listes de nomenclature d'OccTax. */
class ListeNomenclatureOccHab(valeurs: List<NomValeur>) {

    private val triees = valeurs.sortedWith(compareBy(comparateurFrancais()) { it.label })

    /** Les libellés, l'entrée vide d'abord. */
    val libelles: List<String> = listOf(NON_RENSEIGNE) + triees.map { it.label }

    /** Position de [id] dans [libelles] ; 0 (« — Non renseigné — ») s'il n'y est pas. */
    fun position(id: Int?): Int {
        val i = triees.indexOfFirst { it.id == id }
        return if (i >= 0) i + 1 else 0
    }

    /** L'id_nomenclature choisi à [position], null pour « — Non renseigné — ». */
    fun id(position: Int): Int? = if (position <= 0) null else triees.getOrNull(position - 1)?.id

    companion object {
        const val NON_RENSEIGNE = "— Non renseigné —"
    }
}
