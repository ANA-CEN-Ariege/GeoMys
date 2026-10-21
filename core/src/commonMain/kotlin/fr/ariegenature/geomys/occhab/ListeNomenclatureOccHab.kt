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

/** La liste déroulante d'une nomenclature OccHab (détails de la station, habitat), comme le
 *  pnx-dumb-select du web : les valeurs du cache triées par `label_default` (l'`ORDER BY` du
 *  serveur ; français, insensible à la casse et aux accents), affichées par leur `label_fr`. Une
 *  entrée vide en tête seulement si [avecEntreeVide] (`displayNullValue` du web : intérêt
 *  communautaire, abondance) ; sans elle, une valeur vide s'affiche vide (position -1) et ne se
 *  choisit pas. */
class ListeNomenclatureOccHab(valeurs: List<NomValeur>, private val avecEntreeVide: Boolean = false) {

    private val triees = valeurs.sortedWith(compareBy(comparateurFrancais()) { it.label })
    private val decalage = if (avecEntreeVide) 1 else 0

    /** Les libellés, l'entrée vide (texte vide, comme le web) d'abord s'il y en a une. */
    val libelles: List<String> = (if (avecEntreeVide) listOf("") else emptyList()) + triees.map { it.libelle }

    /** Position de [id] dans [libelles] ; s'il n'y est pas, l'entrée vide, à défaut -1 (rien
     *  d'affiché). */
    fun position(id: Int?): Int {
        val i = triees.indexOfFirst { it.id == id }
        return if (i >= 0) i + decalage else if (avecEntreeVide) 0 else -1
    }

    /** L'id_nomenclature choisi à [position], null pour l'entrée vide. */
    fun id(position: Int): Int? = if (position < decalage) null else triees.getOrNull(position - decalage)?.id

    /** La valeur à retenir d'une liste ouverte sur [initiale] et laissée à [position] : celle de la
     *  liste si l'utilisateur a changé de position (l'entrée vide la vide), sinon [initiale], même
     *  absente de la liste en cache. */
    fun retenue(initiale: Int?, position: Int): Int? = if (position != position(initiale)) id(position) else initiale
}
