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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/WidgetMappingTest.kt) :
// JUnit remplacé par kotlin.test.

package fr.ariegenature.geomys.monitoring.form

import fr.ariegenature.geomys.network.MonitoringApi.MonitoringPropertySchema
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mapping du `type_widget` renvoyé par /config/<code> vers le ViewType du form renderer.
 *  C'est le cœur de l'interprétation du schéma serveur monitoring. */
class WidgetMappingTest {

    private fun prop(
        widget: String,
        nom: String = "champ",
        multiple: Boolean = false,
        typeUtil: String? = null,
        apiUrl: String? = null,
    ) = MonitoringPropertySchema(
        nom = nom,
        typeWidget = widget,
        label = nom,
        obligatoire = false,
        typeUtil = typeUtil,
        apiUrl = apiUrl,
        multiple = multiple,
    )

    @Test
    fun le_widget_number_accepte_les_decimaux() {
        // Audit 2026-07 : sans FLAG_DECIMAL, une mesure décimale (température, pH, RHOMEO…)
        // était intapable au clavier et relue null (toIntOrNull) → perdue en silence. Le web n'a que
        // `number`, un <input type="number"> qui accepte les décimaux.
        assertTrue(estWidgetDecimal("number"))
        assertFalse(estWidgetDecimal("text"))
    }

    @Test
    fun widgets_scalaires_du_formulaire_web_seulement() {
        assertEquals(ViewType.TEXT, mapperViewType(prop("text")))
        assertEquals(ViewType.TEXTAREA, mapperViewType(prop("textarea")))
        assertEquals(ViewType.TEXT, mapperViewType(prop("observers-text")), "le pnx-observers-text du web : une ligne")
        assertEquals(ViewType.NUMBER, mapperViewType(prop("number")))
        assertEquals(ViewType.DATE, mapperViewType(prop("date")))
        assertEquals(ViewType.TIME, mapperViewType(prop("time")))
        assertEquals(ViewType.CHECKBOX, mapperViewType(prop("bool_checkbox")))
        // Le formulaire dynamique du web ne rend pas ces types : ni champ ni libellé.
        for (inconnu in listOf("string", "integer", "float", "decimal", "datetime", "timestamp", "bool", "boolean", "taxon")) {
            assertNull(mapperViewType(prop(inconnu)), inconnu)
        }
    }

    @Test
    fun insensible_a_la_casse() {
        assertEquals(ViewType.TEXT, mapperViewType(prop("TEXT")))
        assertEquals(ViewType.DATE, mapperViewType(prop("Date")))
    }

    @Test
    fun select_et_radio_single_vs_multiple() {
        // Le `<select>` et les radios du web sont toujours à choix unique.
        assertEquals(ViewType.SELECT, mapperViewType(prop("select")))
        assertEquals(ViewType.SELECT, mapperViewType(prop("select", multiple = true)))
        assertEquals(ViewType.RADIO, mapperViewType(prop("radio")))
        assertEquals(ViewType.RADIO, mapperViewType(prop("radio", multiple = true)))
        assertEquals(ViewType.RADIO, mapperViewType(prop("bool_radio")))
        assertEquals(ViewType.SELECT_MULTIPLE, mapperViewType(prop("multiselect")))
    }

    @Test
    fun datalists_unifiees_sur_select() {
        assertEquals(ViewType.SELECT, mapperViewType(prop("datalist")))
        assertEquals(ViewType.SELECT, mapperViewType(prop("nomenclature")))
        assertEquals(ViewType.SELECT, mapperViewType(prop("observers")))
        assertEquals(ViewType.SELECT_MULTIPLE, mapperViewType(prop("dataset", multiple = true)))
    }

    @Test
    fun champ_taxon_le_widget_taxonomy_ou_une_datalist_de_taxons() {
        assertEquals(ViewType.TAXON, mapperViewType(prop("taxonomy")))
        assertEquals(ViewType.TAXON, mapperViewType(prop("datalist", typeUtil = "taxonomy")))
        assertEquals(ViewType.TAXON, mapperViewType(prop("datalist", apiUrl = "taxref/allnamebylist/100")))
        // Le web rend un autre widget tel quel, même nommé cd_nom ou de type_util taxonomy.
        assertEquals(ViewType.TEXT, mapperViewType(prop("text", typeUtil = "taxonomy")))
        assertEquals(ViewType.SELECT, mapperViewType(prop("datalist", nom = "cd_nom")))
    }

    @Test
    fun medias_vers_media() {
        assertEquals(ViewType.MEDIA, mapperViewType(prop("medias")))
    }

    @Test
    fun html_affiche_sans_saisie_comme_sur_le_web() {
        assertEquals(ViewType.HTML, mapperViewType(prop("html")))
    }

    @Test
    fun widget_inconnu_ecarte() {
        // Le formulaire web n'affiche rien pour un widget qu'il ne connaît pas.
        assertNull(mapperViewType(prop("widget_exotique_inconnu")))
    }

    @Test
    fun ordre_des_champs_du_formulaire_web() {
        // L'ordre du schéma, `medias` puis `types_site` renvoyés à la fin, puis ceux de
        // display_properties en tête, dans leur ordre ; les autres gardent leur place.
        assertEquals(
            listOf("visit_date_min", "observers", "num_passage", "comments", "id_dataset", "medias", "types_site"),
            ordreDesChamps(
                listOf("num_passage", "types_site", "medias", "comments", "observers", "visit_date_min", "id_dataset"),
                listOf("visit_date_min", "observers", "absent"),
            ),
        )
        // Un média listé dans display_properties remonte avec eux.
        assertEquals(listOf("medias", "a", "b"), ordreDesChamps(listOf("a", "medias", "b"), listOf("medias")))
    }

    @Test
    fun formulaire_sans_champs_masques_ni_widgets_inconnus() {
        val schema = MonitoringSchemaObjet(
            type = "visit", label = "Visite", labelList = null, nameField = null, parentType = "site", childrenTypes = emptyList(),
            properties = linkedMapOf(
                "medias" to prop("medias", nom = "medias"),
                "id_base_visit" to prop("number", nom = "id_base_visit").copy(hiddenBool = true),
                "carte" to prop("geometry", nom = "carte"),
                "observers_txt" to prop("observers-text", nom = "observers_txt"),
                "nb_observations" to prop("number", nom = "nb_observations"),
                "comments" to prop("text", nom = "comments"),
            ),
            displayProperties = listOf("comments"),
        )
        val construction = construireFormulaire(schema)
        // Un champ redéfini visible par le protocole (observers_txt, nb_observations) est proposé.
        assertEquals(listOf("comments", "observers_txt", "nb_observations", "medias"), construction.fields.map { it.code })
        assertEquals(listOf("carte" to "geometry"), construction.ignores)
    }
}
