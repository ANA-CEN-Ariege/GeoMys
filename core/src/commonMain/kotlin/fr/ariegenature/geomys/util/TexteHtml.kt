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

// Propre au portage : le texte du widget `html` d'un formulaire des Suivis, que le web affiche tel
// quel (innerHTML) et que l'appli montre en texte simple.

package fr.ariegenature.geomys.util

/** Un fragment HTML en texte simple : retours à la ligne aux fins de paragraphe, de bloc et de
 *  ligne, puces aux éléments de liste, balises retirées, entités décodées. */
object TexteHtml {

    private val FINS_DE_LIGNE = Regex("(?i)<br\\s*/?>|</(p|div|h[1-6]|li|tr|ul|ol)\\s*>")
    private val DEBUT_D_ELEMENT = Regex("(?i)<li(\\s[^>]*)?>")
    private val BALISE = Regex("<[^>]*>")
    private val ENTITE = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);")
    private val ESPACES = Regex("[ \\t\\u00A0]+")
    private val LIGNES_VIDES = Regex("\\n{3,}")

    private val ENTITES = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "euml" to "ë", "agrave" to "à", "acirc" to "â",
        "ccedil" to "ç", "icirc" to "î", "iuml" to "ï", "ocirc" to "ô", "ugrave" to "ù", "ucirc" to "û",
        "uuml" to "ü", "Eacute" to "É", "Egrave" to "È", "Agrave" to "À", "Ccedil" to "Ç",
        "laquo" to "«", "raquo" to "»", "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”",
        "hellip" to "…", "deg" to "°", "ndash" to "–", "mdash" to "—", "middot" to "·",
    )

    fun enTexte(html: String): String {
        val sansBalises = html
            .replace(Regex("\\r\\n?"), "\n")
            .replace(Regex("\\s*\\n\\s*"), " ")
            .replace(DEBUT_D_ELEMENT, "• ")
            .replace(FINS_DE_LIGNE, "\n")
            .replace(BALISE, "")
        return decoderEntites(sansBalises)
            .lines()
            .joinToString("\n") { it.replace(ESPACES, " ").trim() }
            .replace(LIGNES_VIDES, "\n\n")
            .trim()
    }

    /** [texte] aux entités HTML décodées (`&eacute;`, `&#233;`, `&#xE9;`). */
    fun decoderEntites(texte: String): String = ENTITE.replace(texte) { m ->
        val nom = m.groupValues[1]
        when {
            nom.startsWith("#x") || nom.startsWith("#X") -> nom.drop(2).toIntOrNull(16)?.let(::caractere)
            nom.startsWith("#") -> nom.drop(1).toIntOrNull()?.let(::caractere)
            else -> ENTITES[nom]
        } ?: m.value
    }

    private fun caractere(code: Int): String? = when {
        code in 0..0xFFFF -> code.toChar().toString()
        code in 0x10000..0x10FFFF -> {
            val c = code - 0x10000
            charArrayOf((0xD800 + (c shr 10)).toChar(), (0xDC00 + (c and 0x3FF)).toChar()).concatToString()
        }
        else -> null
    }
}
