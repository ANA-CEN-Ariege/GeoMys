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

package fr.ariegenature.geomys.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TexteHtmlTest {

    @Test
    fun paragraphes_listes_balises_et_entites() {
        val html = "<p>Comptez les <b>adultes</b>&nbsp;:</p>\n<ul><li>mâles</li><li>femelles &amp; jeunes</li></ul>Fin<br/>&#233;t&#xE9; &laquo;ok&raquo;"
        assertEquals("Comptez les adultes :\n• mâles\n• femelles & jeunes\n\nFin\nété «ok»", TexteHtml.enTexte(html))
    }

    @Test
    fun une_entite_inconnue_reste_telle_quelle() {
        assertEquals("a &zz; b", TexteHtml.enTexte("a &zz; b"))
    }
}
