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

// Le nom d'utilisateur déclaré pour l'app Mots de passe d'iOS (28 novembre 2026) ne change rien sous
// Android : le champ Identifiant garde son clavier, sans majuscule ni correction.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClavierIdentifiantAndroidTest {

    @Test
    fun l_identifiant_reste_sans_option_de_plateforme() {
        val options = TypeChamp.IDENTIFIANT.clavier()
        assertNull(options.platformImeOptions)
        assertEquals(KeyboardCapitalization.None, options.capitalization)
        assertEquals(false, options.autoCorrectEnabled)
    }
}
