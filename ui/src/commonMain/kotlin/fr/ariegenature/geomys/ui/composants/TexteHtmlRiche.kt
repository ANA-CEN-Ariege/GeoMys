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

// Propre au portage : le widget `html` des formulaires (Suivis et champs additionnels OccTax), que le
// web affiche tel quel (innerHTML) : gras, italique, souligné, liens, paragraphes et listes.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import fr.ariegenature.geomys.util.TexteHtml

/** Le texte [html] mis en forme comme sur le web : gras, italique, souligné, liens qu'un toucher
 *  ouvre ([couleurLien], soulignés), paragraphes et puces. */
@Composable
fun TexteHtmlRiche(html: String, taille: TextUnit, couleur: Color, couleurLien: Color, modifier: Modifier = Modifier) {
    val texte = remember(html, couleurLien) { annoterHtml(html, couleurLien) }
    Text(texte, fontSize = taille, color = couleur, modifier = modifier)
}

private val JETON = Regex("<[^>]*>|[^<]+")
private val NOM_BALISE = Regex("^</?\\s*([a-zA-Z0-9]+)")
private val LIEN = Regex("href\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
private val BLOCS = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "tr", "table")

/** [html] en texte annoté : les balises de style ouvrent un style que leur fermeture referme, une
 *  balise mal fermée ne casse rien ; les autres balises (images, tableaux…) ne laissent que leur
 *  texte. */
internal fun annoterHtml(html: String, couleurLien: Color): AnnotatedString = buildAnnotatedString {
    val ouvertes = ArrayList<String>()
    // Le dernier caractère écrit : le constructeur ne le rend pas.
    var dernier: Char? = null
    fun ecrire(texte: String) {
        if (texte.isEmpty()) return
        append(texte)
        dernier = texte.last()
    }
    fun finDeLigne() {
        if (dernier != null && dernier != '\n') ecrire("\n")
    }
    fun fermer(nom: String) {
        val i = ouvertes.lastIndexOf(nom)
        if (i < 0) return
        repeat(ouvertes.size - i) {
            ouvertes.removeAt(ouvertes.lastIndex)
            pop()
        }
    }
    for (jeton in JETON.findAll(html.replace(Regex("\\r\\n?"), "\n"))) {
        val v = jeton.value
        if (!v.startsWith("<")) {
            val texte = TexteHtml.decoderEntites(v.replace(Regex("\\s+"), " "))
            ecrire(if (dernier == null || dernier == '\n') texte.trimStart() else texte)
            continue
        }
        val nom = NOM_BALISE.find(v)?.groupValues?.get(1)?.lowercase() ?: continue
        val fermante = v.startsWith("</")
        when (nom) {
            "b", "strong", "i", "em", "u", "a" -> if (fermante) {
                fermer(nom)
            } else {
                when (nom) {
                    "b", "strong" -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    "i", "em" -> pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    "u" -> pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                    else -> {
                        val href = LIEN.find(v)?.groupValues?.get(1)?.let(TexteHtml::decoderEntites)
                        if (href.isNullOrBlank()) {
                            pushStyle(SpanStyle())
                        } else {
                            pushLink(LinkAnnotation.Url(href, TextLinkStyles(SpanStyle(color = couleurLien, textDecoration = TextDecoration.Underline))))
                        }
                    }
                }
                ouvertes += nom
            }
            "br" -> ecrire("\n")
            "li" -> {
                finDeLigne()
                if (!fermante) ecrire("• ")
            }
            in BLOCS -> finDeLigne()
        }
    }
    while (ouvertes.isNotEmpty()) {
        ouvertes.removeAt(ouvertes.lastIndex)
        pop()
    }
}
