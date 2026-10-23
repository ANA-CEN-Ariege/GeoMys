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

// Porté de GeoMys-IOS (Util/PhonetiqueFr.swift). Ajouts : « œ » et « æ » dépliés en deux lettres
// (« bœuf » s'entend comme la dictée l'écrit, « boeuf »), et « ç » lu « s » avant que la
// normalisation n'en fasse un « c » dur.

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.store.TaxRefCache

/**
 * Clé PHONÉTIQUE française d'un nom d'espèce : ce que l'oreille en retient, l'orthographe en moins.
 *
 * La dictée rend ce qu'elle a entendu, pas le nom du taxon : « Pic épeiche » revient en « pic et
 * pêche », « Pouillot véloce » en « pouillot vélos ». Orthographes différentes, mêmes sons : une
 * comparaison lettre à lettre les rate, une comparaison de sons les réunit.
 *
 * Ce n'est pas une transcription savante (ni alphabet phonétique, ni liaisons, ni nasales) : c'est une
 * réduction grossière mais stable, appliquée des deux côtés de la comparaison. Deux taxons qu'elle
 * confondrait ne sont jamais départagés au hasard : la résolution les écarte (garde d'ambiguïté).
 */
object PhonetiqueFr {

    /** Clé d'un nom entier : chaque mot réduit à ses sons, puis collés. « Rouge-gorge » et « rouge
     *  gorge » donnent la même clé, comme « pic épeiche » et « pic et pêche ». */
    fun cle(texte: String): String = decouper(texte).joinToString("") { son(it) }

    /** Sons des mots qui PORTENT le nom (deux sons au moins) : « pic » dans « Pic épeiche ». Les
     *  miettes (« et », « à ») n'y sont pas : elles rapprocheraient n'importe quoi de n'importe quoi. */
    fun motsSignificatifs(texte: String): List<String> = decouper(texte).map { son(it) }.filter { it.length >= 2 }

    /** Réduction d'UN mot (minuscules, sans accents) à ses sons. Les règles sont lues de gauche à
     *  droite, chacune consommant ce qu'elle reconnaît, jamais ce qu'elle vient de produire : le « x »
     *  qui note le son « ch » ne repasse pas pour la lettre x. */
    fun son(mot: String): String {
        if (mot.isEmpty()) return ""
        val sortie = StringBuilder(mot.length)
        var i = 0
        while (i < mot.length) {
            val c = mot[i]
            val suivant = mot.getOrNull(i + 1)
            val suivant2 = mot.getOrNull(i + 2)
            // Consonne finale muette : « bruant » s'entend « bruan », « vélos » « vélo ». C'est ce qui
            // réunit ce que la dictée écrit autrement (« bruand », « vélauce »…).
            if (i == mot.lastIndex && c in FINALES_MUETTES) break
            when (c) {
                'a' -> when (suivant) {
                    'u' -> { sortie.append('o'); i += 2 }
                    'i', 'y' -> { sortie.append('e'); i += 2 }
                    else -> { sortie.append('a'); i += 1 }
                }
                'e' -> when {
                    suivant == 'a' && suivant2 == 'u' -> { sortie.append('o'); i += 3 }
                    suivant == 'i' || suivant == 'y' || suivant == 'u' -> { sortie.append('e'); i += 2 }
                    else -> { sortie.append('e'); i += 1 }
                }
                'o' -> when {
                    suivant == 'u' -> { sortie.append('u'); i += 2 }
                    suivant == 'i' -> { sortie.append("wa"); i += 2 }
                    suivant == 'e' && suivant2 == 'u' -> { sortie.append('e'); i += 3 }
                    else -> { sortie.append('o'); i += 1 }
                }
                'y' -> { sortie.append('i'); i += 1 }
                // « ch » note un son à lui seul (ici « x ») ; « ce/ci/cy » siffle, le reste claque.
                'c' -> when {
                    suivant == 'h' -> { sortie.append('x'); i += 2 }
                    suivant != null && suivant in VOYELLES_DOUCES -> { sortie.append('s'); i += 1 }
                    suivant == 'k' -> { sortie.append('k'); i += 2 }
                    else -> { sortie.append('k'); i += 1 }
                }
                'p' -> if (suivant == 'h') { sortie.append('f'); i += 2 } else { sortie.append('p'); i += 1 }
                'q' -> if (suivant == 'u') { sortie.append('k'); i += 2 } else { sortie.append('k'); i += 1 }
                'g' -> when {
                    suivant == 'n' -> { sortie.append('n'); i += 2 }
                    suivant == 'u' && suivant2 != null && suivant2 in VOYELLES_DOUCES -> { sortie.append('g'); i += 2 }
                    suivant != null && suivant in VOYELLES_DOUCES -> { sortie.append('j'); i += 1 }
                    else -> { sortie.append('g'); i += 1 }
                }
                // Entre deux voyelles, le « s » s'entend « z » : « mésange », « mézange ».
                's' -> {
                    val entreVoyelles = i > 0 && mot[i - 1] in VOYELLES && suivant != null && suivant in VOYELLES
                    sortie.append(if (entreVoyelles) 'z' else 's')
                    i += 1
                }
                'x' -> { sortie.append("ks"); i += 1 }
                'w' -> { sortie.append('v'); i += 1 }
                'h' -> i += 1 // muet : « ch » et « ph » ont déjà été lus
                else -> { sortie.append(c); i += 1 }
            }
        }
        // « e » final muet : « pêche » s'entend « pèch ».
        if (sortie.length > 1 && sortie.last() == 'e') sortie.setLength(sortie.length - 1)
        return sansDoublons(sortie)
    }

    private const val VOYELLES = "aeiouy"
    private const val VOYELLES_DOUCES = "eiy"

    /** Consonnes muettes en fin de mot français ; « r », « f », « l » et « c », eux, s'entendent
     *  (« Pic », « Martinet noir »). */
    private const val FINALES_MUETTES = "stdxzpg"

    /** Mots du nom, sans accents ni ponctuation (le suffixe d'article de l'INPN retiré). */
    private fun decouper(texte: String): List<String> {
        val brut = TaxRefCache.nettoyerSuffixeArticle(texte)
            .replace("œ", "oe").replace("Œ", "oe")
            .replace("æ", "ae").replace("Æ", "ae")
            .replace('ç', 's').replace('Ç', 's')
        return TaxRefCache.normaliser(brut).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    }

    /** Un son répété ne s'entend qu'une fois : « bécasse » s'entend « békas ». */
    private fun sansDoublons(sons: CharSequence): String {
        val out = StringBuilder(sons.length)
        for (s in sons) if (out.isEmpty() || out.last() != s) out.append(s)
        return out.toString()
    }
}
