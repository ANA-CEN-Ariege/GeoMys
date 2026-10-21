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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/AdditionalFieldVisibiliteTest.kt) :
// JUnit remplacé par kotlin.test.

package fr.ariegenature.geomys.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Logique qui décide si un champ additionnel OccTax s'affiche : niveau d'objet (appliqueA)
 *  + restrictions dataset / liste taxonomique (visiblePour). C'est ce qui déclenche (ou non)
 *  l'écran intercalaire « Détails du relevé » en saisie multi-taxons. */
class AdditionalFieldVisibiliteTest {

    private fun champ(
        objectsCode: List<String> = listOf(AdditionalFieldsObject.RELEVE),
        datasetsIds: List<Int> = emptyList(),
        idList: Int? = null,
    ) = AdditionalFieldDef(
        idField = 1, fieldName = "test", fieldLabel = "Test",
        widget = WidgetType.NUMBER, objectsCode = objectsCode,
        datasetsIds = datasetsIds, idList = idList,
    )

    // ── appliqueA (niveau d'objet) ──────────────────────────────────────────────
    @Test
    fun appliqueA_vrai_si_objet_present() {
        assertTrue(champ().appliqueA(AdditionalFieldsObject.RELEVE))
    }

    @Test
    fun appliqueA_faux_pour_un_autre_niveau() {
        val f = champ(objectsCode = listOf(AdditionalFieldsObject.OCCURRENCE))
        assertFalse(f.appliqueA(AdditionalFieldsObject.RELEVE))
        assertTrue(f.appliqueA(AdditionalFieldsObject.OCCURRENCE))
    }

    // ── visiblePour : restriction dataset ──────────────────────────────────────
    @Test
    fun sans_restriction_dataset_visible_partout() {
        assertTrue(champ(datasetsIds = emptyList()).visiblePour(42))
        assertTrue(champ(datasetsIds = emptyList()).visiblePour(null))
    }

    @Test
    fun restreint_a_un_dataset() {
        val f = champ(datasetsIds = listOf(10, 20))
        assertTrue(f.visiblePour(10))
        assertFalse(f.visiblePour(99), "dataset hors liste")
        assertFalse(f.visiblePour(null), "dataset courant inconnu mais champ restreint")
    }

    // ── id_list : paramètre des widgets, pas un filtre d'affichage ──────────────
    @Test
    fun id_list_ne_restreint_pas_l_affichage() {
        // Le serveur ne filtre pas par liste : un champ d'observateurs (`id_list` = liste
        // d'observateurs) s'affiche, au relevé comme ailleurs. L'appli le masquait toujours.
        assertTrue(champ(idList = 100).visiblePour(1))
        assertTrue(champ(datasetsIds = listOf(10), idList = 100).visiblePour(10))
        assertFalse(champ(datasetsIds = listOf(10), idList = 100).visiblePour(11), "mauvais dataset")
    }
}
