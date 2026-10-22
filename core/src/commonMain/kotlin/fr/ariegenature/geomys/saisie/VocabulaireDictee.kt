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

// Porté de GeoMys-IOS (UI/Saisie/DicteeVocale.swift, `vocabulaire` et `premiersMots`) : le vocabulaire
// que la dictée d'iOS reçoit (`contextualStrings` de SFSpeechRecognizer). Android n'a pas d'équivalent.

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.store.SuggestionTaxon
import fr.ariegenature.geomys.store.TaxRefCache

/** Au-delà, le vocabulaire n'est plus une aide : une liste trop longue coûte cher à la
 *  reconnaissance sans l'orienter vers grand-chose. */
const val MAX_VOCABULAIRE_DICTEE = 200

/** Les noms que la reconnaissance doit s'attendre à entendre : les espèces proposables ici, sans
 *  doublon, quand elles tiennent en [maximum] ; au-delà, leurs PREMIERS MOTS (« Fauvette »,
 *  « Pic »…), les plus répandus d'abord — c'est le mot que la reconnaissance écorche le plus. */
fun vocabulaireDictee(propositions: List<SuggestionTaxon>, maximum: Int = MAX_VOCABULAIRE_DICTEE): List<String> {
    val vus = HashSet<String>()
    val noms = ArrayList<String>()
    for (s in propositions) {
        val nom = TaxRefCache.nettoyerSuffixeArticle(s.nom)
        if (nom.isNotEmpty() && vus.add(nom)) noms.add(nom)
    }
    if (noms.size <= maximum) return noms
    return premiersMots(noms, maximum)
}

/** Premiers mots (3 lettres au moins) des [noms], du plus répandu au moins répandu ; à égalité,
 *  l'ordre alphabétique, pour que deux appels donnent la même liste. */
fun premiersMots(noms: List<String>, maximum: Int): List<String> {
    val comptes = HashMap<String, Int>()
    for (nom in noms) {
        val premier = nom.split(Regex("[^\\p{L}\\p{N}]+")).firstOrNull { it.isNotEmpty() } ?: continue
        if (premier.length < 3) continue
        comptes[premier] = (comptes[premier] ?: 0) + 1
    }
    return comptes.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(maximum)
        .map { it.key }
}
