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

// Propre au portage.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Le widget `html` des formulaires, mis en forme comme le web. */
class TexteHtmlRicheTest {

    @Test
    fun gras_liens_et_puces() {
        val texte = annoterHtml(
            "<p>Lire <b>attentivement</b> le <a href=\"https://exemple.org/protocole?a=1&amp;b=2\">protocole</a>.</p><ul><li>Un</li><li>Deux &eacute;</li></ul>",
            Color.Blue,
        )
        assertEquals("Lire attentivement le protocole.\n• Un\n• Deux é\n", texte.text)
        val gras = texte.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("attentivement", texte.text.substring(gras.start, gras.end))
        val lien = texte.getLinkAnnotations(0, texte.length).single()
        assertEquals("https://exemple.org/protocole?a=1&b=2", (lien.item as LinkAnnotation.Url).url)
        assertEquals("protocole", texte.text.substring(lien.start, lien.end))
    }

    @Test
    fun une_balise_mal_fermee_ne_casse_rien() {
        val texte = annoterHtml("<i>penché <b>et gras</i> fin<br/>suite <img src=\"x.png\">", Color.Blue)
        assertEquals("penché et gras fin\nsuite ", texte.text)
        assertTrue(texte.spanStyles.isNotEmpty())
    }
}
