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

package fr.ariegenature.geomys.plateforme

/** [texte] en forme normale de décomposition canonique (NFD) : « é » devient « e » suivi de
 *  l'accent combinant. `java.text.Normalizer` sous Android, `decomposedStringWithCanonicalMapping`
 *  sous iOS. */
expect fun decomposerNfd(texte: String): String

/** [texte] sans ses diacritiques (« Forêts » → « Forets ») : NFD, puis retrait des marques
 *  combinantes (catégorie Unicode Mn), comme `Normalizer.normalize(…, NFD).replace("\\p{Mn}+", "")`
 *  sous Android. */
fun sansDiacritiques(texte: String): String =
    decomposerNfd(texte).filter { it.category != CharCategory.NON_SPACING_MARK }

/** Comparateur de tri à la française au niveau PRIMAIRE : ni la casse ni les accents ne départagent
 *  (« Éclair » = « eclair »). `java.text.Collator` (Locale.FRANCE, PRIMARY) sous Android,
 *  `compare(_:options:range:locale:)` insensible à la casse et aux accents sous iOS. Une instance
 *  par tri : le Collator d'Android n'est pas fait pour être partagé entre fils. */
expect fun comparateurFrancais(): Comparator<String>
