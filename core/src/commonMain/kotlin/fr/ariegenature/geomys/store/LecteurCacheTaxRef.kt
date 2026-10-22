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

// Le lecteur à la main du cache principal de TaxRef, comme celui de GeoMys-Android @ cdd5196
// (store/TaxRefCache.kt, chargerInterne : un JsonReader de Gson parcouru champ par champ). Le décodeur
// générique de kotlinx-serialization, en flux, était quatre fois plus lent (mesure du 21 octobre 2026 sur
// la JVM, 75 000 noms : 59 ms contre 15) ; sur un téléphone d'entrée de gamme, le chargement de TaxRef,
// qui durait de 10 à 30 s, retenait les compteurs de Paramètres et les premières propositions d'espèces.

package fr.ariegenature.geomys.store

/** Lit le cache principal de TaxRef (`cache_v3.json` : un objet nom normalisé → entrée), tel que
 *  l'écrit [TaxRefCache.remplacerTout] : `{"nom":{"cdNom":1,"sciNom":"…","nomFrOriginal":"…"},…}`,
 *  un champ null omis. Les octets du fichier sont parcourus une fois, sans arbre JSON intermédiaire ;
 *  un champ inconnu est sauté, comme `skipValue` de Gson. Un fichier mal formé lève une exception. */
internal object LecteurCacheTaxRef {

    fun lire(octets: ByteArray): HashMap<String, TaxRefEntry> = Lecture(octets).cache()

    private class Lecture(private val o: ByteArray) {
        private var i = 0

        fun cache(): HashMap<String, TaxRefEntry> {
            val cache = HashMap<String, TaxRefEntry>(1 shl 16)
            attendre('{')
            if (suivant() == '}') return cache.also { i++ }
            while (true) {
                val nom = chaine()
                attendre(':')
                cache[nom] = entree()
                if (separateur('}')) return cache
            }
        }

        /** `{"cdNom":…,"sciNom":…,"nomFrOriginal":…}`, dans n'importe quel ordre. */
        private fun entree(): TaxRefEntry {
            attendre('{')
            var cdNom = 0
            var sciNom = ""
            var nomFr: String? = null
            if (suivant() == '}') {
                i++
            } else {
                while (true) {
                    val champ = chaine()
                    attendre(':')
                    when (champ) {
                        "cdNom" -> cdNom = entier()
                        "sciNom" -> sciNom = chaineOuNull().orEmpty()
                        // « nomFr » : l'ancien nom du champ chez GeoMys-Android.
                        "nomFrOriginal", "nomFr" -> nomFr = chaineOuNull()
                        else -> sauterValeur()
                    }
                    if (separateur('}')) break
                }
            }
            return TaxRefEntry(cdNom, sciNom, nomFr)
        }

        /** Le prochain caractère utile, sans l'avancer. */
        private fun suivant(): Char {
            while (true) {
                when (o[i].toInt()) {
                    0x20, 0x0A, 0x0D, 0x09 -> i++
                    else -> return o[i].toInt().toChar()
                }
            }
        }

        private fun attendre(c: Char) {
            if (suivant() != c) erreur("« $c » attendu")
            i++
        }

        /** Après une valeur : une virgule (vrai : encore un élément), ou la [fin] du bloc (faux). */
        private fun separateur(fin: Char): Boolean = when (suivant()) {
            ',' -> { i++; false }
            fin -> { i++; true }
            else -> erreur("« , » ou « $fin » attendu")
        }

        private fun entier(): Int {
            suivant()
            val debut = i
            if (o[i].toInt() == 0x2D) i++
            var n = 0L
            while (i < o.size && o[i] in 0x30..0x39) {
                n = n * 10 + (o[i] - 0x30)
                i++
            }
            if (i == debut || (i == debut + 1 && o[debut].toInt() == 0x2D)) erreur("nombre attendu")
            return (if (o[debut].toInt() == 0x2D) -n else n).toInt()
        }

        private fun chaineOuNull(): String? {
            if (suivant() == 'n') {
                litteral("null")
                return null
            }
            return chaine()
        }

        /** Une chaîne JSON : les octets UTF-8 décodés d'un bloc, ou caractère par caractère s'il y a
         *  des échappements (`\"`, `\\`, `\n`, `\uXXXX`…). */
        private fun chaine(): String {
            attendre('"')
            val debut = i
            var echappements = false
            while (true) {
                when (o[i].toInt()) {
                    0x22 -> break
                    0x5C -> { echappements = true; i += 2 }
                    else -> i++
                }
            }
            val fin = i
            i++
            if (!echappements) return o.decodeToString(debut, fin)
            val sb = StringBuilder(fin - debut)
            var j = debut
            while (j < fin) {
                if (o[j].toInt() != 0x5C) {
                    var k = j
                    while (k < fin && o[k].toInt() != 0x5C) k++
                    sb.append(o.decodeToString(j, k))
                    j = k
                    continue
                }
                when (val e = o[j + 1].toInt().toChar()) {
                    '"', '\\', '/' -> sb.append(e)
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        sb.append(o.decodeToString(j + 2, j + 6).toInt(16).toChar())
                        j += 4
                    }
                    else -> erreur("échappement « \\$e » inconnu")
                }
                j += 2
            }
            return sb.toString()
        }

        private fun litteral(mot: String) {
            for (c in mot) {
                if (o[i].toInt().toChar() != c) erreur("« $mot » attendu")
                i++
            }
        }

        /** Saute une valeur quelconque : chaîne, nombre, littéral, objet ou tableau. */
        private fun sauterValeur() {
            when (suivant()) {
                '"' -> chaine()
                '{' -> {
                    i++
                    if (suivant() == '}') { i++; return }
                    while (true) {
                        chaine()
                        attendre(':')
                        sauterValeur()
                        if (separateur('}')) return
                    }
                }
                '[' -> {
                    i++
                    if (suivant() == ']') { i++; return }
                    while (true) {
                        sauterValeur()
                        if (separateur(']')) return
                    }
                }
                't' -> litteral("true")
                'f' -> litteral("false")
                'n' -> litteral("null")
                else -> {
                    // Un nombre, entier ou décimal.
                    val debut = i
                    while (i < o.size && o[i].toInt().toChar().let { it.isDigit() || it in "+-.eE" }) i++
                    if (i == debut) erreur("valeur attendue")
                }
            }
        }

        private fun erreur(message: String): Nothing =
            throw IllegalArgumentException("Cache TaxRef illisible à l'octet $i : $message")
    }
}
