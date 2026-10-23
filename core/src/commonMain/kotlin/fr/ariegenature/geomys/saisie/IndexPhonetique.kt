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

// Porté de GeoMys-IOS (Store/PropositionsTaxon.swift, `memeSon`, `seuilSon` et `candidatsVocaux`).
// Propre au portage (demande du 23 octobre 2026, la dictée proposait « psychorame » pour « Tichodrome »
// et « rouge-gorge », absents de TaxRef) : le classement des espèces proches compare aussi les sons
// mot à mot, et les mots dits collés ou séparés, là où GeoMys-IOS ne comparait que le nom entier et
// les mots identiques.

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.store.SuggestionTaxon
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Nombre d'espèces proches montrées quand la dictée n'a pas reconnu d'espèce. */
const val MAX_CANDIDATS_VOCAUX = 5

/**
 * Les propositions d'un périmètre (groupe × mode × liste) réduites à leurs SONS ([PhonetiqueFr]). La
 * dictée y cherche ce qu'elle a entendu : seuls les noms proposés peuvent en sortir, jamais le texte
 * entendu. Calculé une fois par périmètre, à la première dictée.
 */
class IndexPhonetique(private val liste: List<SuggestionTaxon>) {

    /** Clé phonétique de chaque proposition. */
    private val cles: Array<String>

    /** Sons distincts des mots significatifs de toutes les propositions : la comparaison mot à mot
     *  se fait une fois par mot, pas une fois par nom (« de », « Europe », « Pic »… reviennent
     *  partout). */
    private val mots: Array<String>

    /** Les mots significatifs de chaque proposition, en indices dans [mots]. */
    private val motsParNom: Array<IntArray>

    init {
        val indices = HashMap<String, Int>()
        val distincts = ArrayList<String>()
        cles = Array(liste.size) { PhonetiqueFr.cle(liste[it].nom) }
        motsParNom = Array(liste.size) { i ->
            PhonetiqueFr.motsSignificatifs(liste[i].nom).map { m ->
                indices.getOrPut(m) { distincts.add(m); distincts.lastIndex }
            }.toIntArray()
        }
        mots = distincts.toTypedArray()
    }

    /**
     * Match PHONÉTIQUE : une proposition qui s'entend comme [texte], à [tolerance] sons près (0 : le
     * même son d'un bout à l'autre). Deux taxons aussi proches l'un que l'autre : rien, une
     * détermination inventée partirait telle quelle sur GeoNature.
     */
    fun memeSon(texte: String, tolerance: Int): SuggestionTaxon? {
        val cle = PhonetiqueFr.cle(texte)
        if (cle.length < 3) return null
        var meilleure: SuggestionTaxon? = null
        var meilleureDistance = Int.MAX_VALUE
        var ambigu = false
        for (i in liste.indices) {
            val d = distance(cle, cles[i], tolerance)
            if (d < 0) continue
            if (d < meilleureDistance) {
                meilleureDistance = d
                meilleure = liste[i]
                ambigu = false
            } else if (d == meilleureDistance && meilleure?.cdNom != liste[i].cdNom) {
                ambigu = true
            }
        }
        return if (ambigu) null else meilleure
    }

    /**
     * Les propositions qui RESSEMBLENT le plus à ce qui a été dicté ([hypotheses] de la
     * reconnaissance), de la plus proche à la moins proche, une par taxon, [maximum] au plus : à
     * montrer quand la dictée n'a pas reconnu d'espèce.
     *
     * Une proposition vient par le nom entier (à quelques sons près, selon sa longueur) ou par ses
     * mots : chaque mot dit rapproché du mot du nom qui lui ressemble le plus, ou toute la phrase
     * rapprochée d'un seul mot du nom (« rouge gorge » de « Rougegorge »). À égalité, le nom dont la
     * dictée couvre le plus de mots, puis celui dont elle a dit le premier mot, puis le plus court.
     */
    fun candidats(hypotheses: List<String>, maximum: Int = MAX_CANDIDATS_VOCAUX): List<SuggestionTaxon> {
        val meilleurParTaxon = HashMap<Int, Pair<Classement, SuggestionTaxon>>()
        // k : la phrase entendue, réduite à ses sons ; dits : ses mots significatifs.
        for ((k, dits) in hypotheses.map { PhonetiqueFr.cle(it) to PhonetiqueFr.motsSignificatifs(it) }.distinct()) {
            if (k.length < 2) continue
            val seuil = when {
                k.length < 6 -> 1
                k.length < 10 -> 2
                else -> 3
            }
            // Chaque mot connu comparé une fois à chaque mot dit, et à la phrase entière.
            val ecartsDits = Array(dits.size) { w -> DoubleArray(mots.size) { ecart(dits[w], mots[it]) } }
            val ecartsPhrase = DoubleArray(mots.size) { ecart(k, mots[it]) }
            val couvert = BooleanArray(mots.size) { j ->
                ecartsPhrase[j] <= ECART_COUVERT || ecartsDits.any { it[j] <= ECART_COUVERT }
            }
            for (i in liste.indices) {
                val motsNom = motsParNom[i]
                if (motsNom.isEmpty()) continue
                var note = Double.MAX_VALUE
                val d = distance(k, cles[i], seuil)
                if (d >= 0) note = d.toDouble()
                val parMots = if (dits.isEmpty()) 1.0 else dits.indices.sumOf { w ->
                    min(1.0, motsNom.minOf { ecartsDits[w][it] })
                } / dits.size
                val colle = motsNom.minOf { ecartsPhrase[it] }
                val s = min(parMots, colle)
                if (s <= ECART_MOTS) note = min(note, NOTE_MOTS + 2 * s)
                if (note == Double.MAX_VALUE) continue
                val classement = Classement(
                    note = (note * 1000).roundToInt(),
                    couverture = motsNom.count { couvert[it] }.toDouble() / motsNom.size,
                    premierDit = couvert[motsNom[0]],
                    nom = liste[i].nom,
                )
                val deja = meilleurParTaxon[liste[i].cdNom]
                if (deja == null || classement < deja.first) meilleurParTaxon[liste[i].cdNom] = classement to liste[i]
            }
        }
        return meilleurParTaxon.values.sortedWith { a, b -> a.first.compareTo(b.first) }.take(maximum).map { it.second }
    }

    /** Ordre des espèces proches : note (0 à 3 pour le nom entier, 3 à 4 par les mots), puis la part
     *  des mots du nom que la dictée couvre, puis le premier mot dit, puis le nom le plus court. */
    private class Classement(val note: Int, val couverture: Double, val premierDit: Boolean, val nom: String) : Comparable<Classement> {
        override fun compareTo(other: Classement): Int = compareValuesBy(
            this, other,
            { it.note }, { -it.couverture }, { if (it.premierDit) 0 else 1 }, { it.nom.length }, { it.nom },
        )
    }

    companion object {
        /** Note d'une proposition venue par ses mots : derrière tout nom entier à 3 sons près. */
        private const val NOTE_MOTS = 3.0

        /** Écart moyen des mots au-delà duquel un nom ne ressemble plus à ce qui a été dit. */
        private const val ECART_MOTS = 0.5

        /** Écart au-dessous duquel un mot du nom compte pour dit. */
        private const val ECART_COUVERT = 0.34

        /** Sons de travers tolérés en dernier recours, à la mesure du nom : sur « Fauvette
         *  mélanocéphale », la dictée perd ou ajoute volontiers une syllabe ; sur un nom court, la même
         *  largesse confondrait deux espèces. L'ambiguïté reste refusée, quel que soit le seuil. */
        fun seuilSon(texte: String): Int {
            val longueur = PhonetiqueFr.cle(texte).length
            return when {
                longueur < 8 -> 1
                longueur < 16 -> 2
                else -> 3
            }
        }

        /** Écart relatif de deux mots (0 : le même son, 1 : rien de commun). Un mot de deux sons ne
         *  ressemble qu'à lui-même : « up » (huppe) n'est pas à moitié « ap ». */
        internal fun ecart(a: String, b: String): Double {
            if (min(a.length, b.length) <= 2) return if (a == b) 0.0 else 1.0
            return distance(a, b, max(a.length, b.length)).toDouble() / max(a.length, b.length)
        }

        /** Distance de Levenshtein, abandonnée (-1) dès qu'elle dépasse [maximum]. */
        internal fun distance(a: String, b: String, maximum: Int): Int {
            val n = a.length
            val m = b.length
            if (abs(n - m) > maximum) return -1
            var precedente = IntArray(m + 1) { it }
            var courante = IntArray(m + 1)
            for (i in 1..n) {
                courante[0] = i
                var minLigne = i
                val ca = a[i - 1]
                for (j in 1..m) {
                    val cout = if (ca == b[j - 1]) 0 else 1
                    courante[j] = minOf(precedente[j] + 1, courante[j - 1] + 1, precedente[j - 1] + cout)
                    if (courante[j] < minLigne) minLigne = courante[j]
                }
                if (minLigne > maximum) return -1
                val t = precedente
                precedente = courante
                courante = t
            }
            return if (precedente[m] > maximum) -1 else precedente[m]
        }
    }
}
