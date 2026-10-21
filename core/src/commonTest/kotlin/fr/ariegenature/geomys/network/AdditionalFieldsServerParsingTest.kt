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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/AdditionalFieldsServerParsingTest.kt) :
// org.json remplacé par son portage, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.model.WidgetType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parsing du JSON brut /api/gn_commons/additional_fields — tolère les formes variables
 *  selon la version GeoNature (type_widget objet/string, field_values string/{value,label},
 *  objects[].code_object, datasets ids nus/objets). */
class AdditionalFieldsServerParsingTest {

    @Test
    fun type_widget_en_objet() {
        val arr = JSONArray(
            """
            [{"id_field":1,"field_name":"alt","field_label":"Altitude",
              "type_widget":{"widget_name":"number"},
              "objects":[{"code_object":"OCCTAX_RELEVE"}]}]
            """.trimIndent(),
        )
        val defs = AdditionalFieldsApi.parserChamps(arr)
        assertEquals(1, defs.size)
        assertEquals(WidgetType.NUMBER, defs[0].widget)
        assertEquals("alt", defs[0].fieldName)
        assertTrue("OCCTAX_RELEVE" in defs[0].objectsCode)
    }

    @Test
    fun type_widget_en_string() {
        val arr = JSONArray("""[{"id_field":2,"field_name":"note","type_widget":"textarea"}]""")
        assertEquals(WidgetType.TEXTAREA, AdditionalFieldsApi.parserChamps(arr)[0].widget)
    }

    @Test
    fun widget_inconnu_devient_INCONNU() {
        val arr = JSONArray("""[{"id_field":3,"field_name":"x","type_widget":"exotique"}]""")
        assertEquals(WidgetType.INCONNU, AdditionalFieldsApi.parserChamps(arr)[0].widget)
    }

    @Test
    fun field_values_string_et_objet() {
        val arr = JSONArray(
            """
            [{"id_field":4,"field_name":"meteo","type_widget":"select",
              "field_values":["soleil", {"value":"p","label":"Pluie"}]}]
            """.trimIndent(),
        )
        assertEquals(listOf("soleil", "Pluie"), AdditionalFieldsApi.parserChamps(arr)[0].fieldValues)
    }

    @Test
    fun datasets_ids_nus_ou_objets() {
        val arr = JSONArray(
            """
            [{"id_field":5,"field_name":"a","type_widget":"text","datasets":[10, {"id_dataset":20}]}]
            """.trimIndent(),
        )
        assertEquals(listOf(10, 20), AdditionalFieldsApi.parserChamps(arr)[0].datasetsIds)
    }

    @Test
    fun id_list_et_required() {
        val arr = JSONArray(
            """[{"id_field":6,"field_name":"b","type_widget":"text","id_list":100,"required":true}]""",
        )
        val d = AdditionalFieldsApi.parserChamps(arr)[0]
        assertEquals(100, d.idList)
        assertTrue(d.required)
    }

    @Test
    fun entrees_invalides_ignorees() {
        // id_field absent ou field_name vide → l'entrée est sautée.
        val arr = JSONArray(
            """
            [{"field_name":"sans_id","type_widget":"text"},
             {"id_field":7,"field_name":"","type_widget":"text"},
             {"id_field":8,"field_name":"ok","type_widget":"text"}]
            """.trimIndent(),
        )
        val defs = AdditionalFieldsApi.parserChamps(arr)
        assertEquals(1, defs.size)
        assertEquals("ok", defs[0].fieldName)
    }

    @Test
    fun widgets_du_formulaire_web() {
        val noms = listOf(
            "date" to WidgetType.DATE, "time" to WidgetType.TIME, "multiselect" to WidgetType.MULTISELECT,
            "bool_checkbox" to WidgetType.BOOL_CHECKBOX, "bool_radio" to WidgetType.BOOL_RADIO, "taxonomy" to WidgetType.TAXONOMY,
            "observers" to WidgetType.OBSERVERS, "observers-text" to WidgetType.OBSERVERS_TEXT, "html" to WidgetType.HTML,
            // Le web ne rend pas ces types : ni champ ni libellé.
            "integer" to WidgetType.INCONNU, "areas" to WidgetType.INCONNU, "medias" to WidgetType.INCONNU,
        )
        for ((nom, attendu) in noms) {
            val arr = JSONArray("""[{"id_field":1,"field_name":"c","type_widget":{"widget_name":"$nom"}}]""")
            assertEquals(attendu, AdditionalFieldsApi.parserChamps(arr)[0].widget, nom)
        }
        // Une datalist sans `values` est servie par une API : pas hors ligne.
        val api = JSONArray("""[{"id_field":1,"field_name":"c","type_widget":"datalist","additional_attributes":{"api":"x"}}]""")
        assertEquals(WidgetType.INCONNU, AdditionalFieldsApi.parserChamps(api)[0].widget)
    }

    @Test
    fun valeurs_typees_et_attributs_du_web() {
        val arr = JSONArray(
            """
            [{"id_field":1,"field_name":"c","type_widget":"select","required":false,
              "field_values":[{"value":1,"label":"Un"},"deux",{"value":true,"label":"Vrai"}],
              "additional_attributes":{"required":true,"help":"Aide","definition":"Déf","pattern":"[0-9]+",
                "pattern_message":"Chiffres","max_length":4,"rows":3,"noNullOption":true,"hidden":true,"disabled":true}},
             {"id_field":2,"field_name":"d","type_widget":"datalist",
              "additional_attributes":{"values":[{"code":"a","nom":"A"}],"keyValue":"code","keyLabel":"nom","multiple":true,"nullDefault":true}},
             {"id_field":3,"field_name":"n","type_widget":"nomenclature","code_nomenclature_type":"STATUT_OBS",
              "additional_attributes":{"multi_select":true}},
             {"id_field":4,"field_name":"h","type_widget":"html","additional_attributes":{"html":"<b>Texte</b>"}}]
            """.trimIndent(),
        )
        val (select, datalist, nomenclature, html) = AdditionalFieldsApi.parserChamps(arr)
        assertEquals(listOf("Un", "deux", "Vrai"), select.fieldValues)
        assertEquals(listOf("1", "deux", "true"), select.fieldValueCodes)
        assertEquals(listOf("1", "\"deux\"", "true"), select.valeursBrutes, "chaque valeur garde son type JSON")
        assertTrue(select.required, "additional_attributes l'emporte, comme le web l'étale")
        assertEquals("Aide", select.aide)
        assertEquals("Déf", select.definition)
        assertEquals("[0-9]+", select.motif)
        assertEquals("Chiffres", select.messageMotif)
        assertEquals(4, select.longueurMax)
        assertEquals(3, select.lignes)
        assertTrue(select.sansOptionVide && select.masque && select.desactive)
        assertEquals(WidgetType.DATALIST, datalist.widget)
        assertEquals(listOf("A"), datalist.fieldValues)
        assertEquals(listOf("a"), datalist.fieldValueCodes)
        assertTrue(datalist.choixMultiple && datalist.avecAucun)
        assertEquals("STATUT_OBS", nomenclature.codeNomenclatureType)
        assertTrue(nomenclature.choixMultiple)
        assertEquals("<b>Texte</b>", html.html)
    }

    @Test
    fun label_defaut_sur_field_name_si_absent() {
        val arr = JSONArray("""[{"id_field":9,"field_name":"code_zh","type_widget":"text"}]""")
        assertEquals("code_zh", AdditionalFieldsApi.parserChamps(arr)[0].fieldLabel)
    }
}
