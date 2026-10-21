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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/ExtraireDefautTest.kt) :
// org.json remplacé par son portage, JUnit par kotlin.test ; réécrit à la vérification des champs du
// 20 octobre, la règle étant désormais celle du web.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** La valeur par défaut d'un champ additionnel, celle du web (getadditionalFields) : `default_value`,
 *  que remplace `additional_attributes.value`. */
class ExtraireDefautTest {

    private fun defaut(json: String): String? = AdditionalFieldsApi.defautDuChamp(JSONObject(json))

    @Test
    fun default_value_texte_ou_nombre() {
        assertEquals("RAS", defaut("""{"default_value":"RAS"}"""))
        assertEquals("12", defaut("""{"default_value":12}"""))
    }

    @Test
    fun additional_attributes_value_l_emporte() {
        assertEquals("z", defaut("""{"default_value":"a","additional_attributes":{"value":"z"}}"""))
        assertEquals("a", defaut("""{"default_value":"a","additional_attributes":{"min":0}}"""))
    }

    @Test
    fun les_autres_cles_ne_sont_pas_des_defauts() {
        // GeoMys-Android les lisait ; le web non.
        assertNull(defaut("""{"value":12}"""))
        assertNull(defaut("""{"default":3.5}"""))
        assertNull(defaut("""{"field_values":[{"value":"a","label":"A"},{"value":"b","default":true}]}"""))
    }

    @Test
    fun objet_value_ou_id_nomenclature() {
        assertEquals("42", defaut("""{"default_value":{"value":42}}"""))
        assertEquals("7", defaut("""{"default_value":{"id_nomenclature":7}}"""))
    }

    @Test
    fun tableau_garde_toutes_ses_valeurs() {
        // Le défaut d'un choix multiple : le tableau entier, que l'état relit en cases cochées.
        assertEquals("""["premier","second"]""", defaut("""{"default_value":["premier","second"]}"""))
    }

    @Test
    fun null_litteral_et_absence_renvoient_null() {
        assertNull(defaut("""{"default_value":"null"}"""))
        assertNull(defaut("""{"default_value":""}"""))
        assertNull(defaut("""{"autre":"x"}"""))
    }
}
