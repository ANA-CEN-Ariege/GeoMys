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

// Question du 28 novembre 2026 : sur l'iPhone, l'app Mots de passe ne proposait ni l'identifiant ni le
// mot de passe. Compose dessine les champs et ne donne à iOS que le type tiré du clavier : « mot de
// passe » pour le clavier mot de passe, rien pour l'identifiant. Le champ Identifiant se déclare
// désormais nom d'utilisateur.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PlatformImeOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import platform.UIKit.UITextContentTypePassword
import platform.UIKit.UITextContentTypeUsername

@OptIn(ExperimentalComposeUiApi::class)
class ClavierIdentifiantTest {

    /** Compose ne publie pas le type de contenu des options iOS : on les compare à des options
     *  construites, qui se comparent par valeur (deux contre-épreuves vérifient qu'elles le font bien
     *  sur le type de contenu). */
    @Test
    fun l_identifiant_se_declare_nom_d_utilisateur_sans_majuscule_ni_correction() {
        val options = TypeChamp.IDENTIFIANT.clavier()
        assertEquals(PlatformImeOptions { textContentType(UITextContentTypeUsername) }, options.platformImeOptions)
        assertNotEquals(PlatformImeOptions { textContentType(UITextContentTypePassword) }, options.platformImeOptions)
        assertNotEquals(PlatformImeOptions {}, options.platformImeOptions)
        assertEquals(KeyboardCapitalization.None, options.capitalization)
        assertEquals(false, options.autoCorrectEnabled)
        assertEquals(KeyboardType.Unspecified, options.keyboardType)
    }

    @Test
    fun les_autres_champs_ne_donnent_pas_de_type_de_contenu() {
        TypeChamp.entries.filter { it != TypeChamp.IDENTIFIANT }.forEach {
            assertNull(it.clavier().platformImeOptions, "$it")
        }
    }
}
