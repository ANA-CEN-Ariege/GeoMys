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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/saisie/SpeciesAutocomplete.kt) :
// le filtre des deux ArrayAdapter d'autocomplétion (createAutocompleteAdapter et
// createSpeciesAutocompleteAdapter), identique dans les deux, devient une fonction du socle. La
// liste déroulante elle-même est un composable.

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.store.TaxRefCache

/** Filtre d'autocomplétion des espèces, insensible aux accents et à la casse : les suggestions
 *  qui COMMENCENT par la requête d'abord, puis celles qui la CONTIENNENT, chacune dans l'ordre de
 *  la liste. [normalises] porte les clés normalisées (TaxRefCache.normaliser) PRÉCALCULÉES une
 *  fois, hors du fil de l'interface : la liste compte de 15 000 à 50 000 noms. Requête vide → la
 *  liste entière, [tous]. */
fun <T> filtrerSuggestions(normalises: List<Pair<String, T>>, tous: List<T>, requete: String?): List<T> {
    if (requete.isNullOrEmpty()) return tous
    val q = TaxRefCache.normaliser(requete)
    val commencent = ArrayList<T>()
    val contiennent = ArrayList<T>()
    for ((cle, item) in normalises) {
        when {
            cle.startsWith(q) -> commencent.add(item)
            cle.contains(q) -> contiennent.add(item)
        }
    }
    return commencent + contiennent
}
