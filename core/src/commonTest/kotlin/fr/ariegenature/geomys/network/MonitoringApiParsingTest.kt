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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/MonitoringApiParsingTest.kt) :
// org.json remplacé par son portage, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parsing du schéma serveur /config/<code> : une propriété de formulaire, le bloc cruved,
 *  et les petites heuristiques (code nomenclature, id liste taxonomique). */
class MonitoringApiParsingTest {

    // ── parserCruved ──────────────────────────────────────────────────────────
    @Test
    fun cruved_null_renvoie_null() {
        assertNull(MonitoringModules.parserCruved(null))
    }

    @Test
    fun cruved_normalise_les_cles_en_majuscules() {
        val obj = JSONObject("""{"C":1,"R":2,"U":0}""")
        val cruved = MonitoringModules.parserCruved(obj)!!
        assertEquals(1, cruved["C"])
        assertEquals(2, cruved["R"])
        assertEquals(0, cruved["U"])
    }

    @Test
    fun cruved_accepte_niveaux_en_string() {
        val cruved = MonitoringModules.parserCruved(JSONObject("""{"c":"3"}"""))!!
        assertEquals(3, cruved["C"])
    }

    @Test
    fun cruved_vide_renvoie_null() {
        assertNull(MonitoringModules.parserCruved(JSONObject("{}")))
    }

    // ── parserUnePropriete ────────────────────────────────────────────────────
    @Test
    fun propriete_texte_basique() {
        val v = JSONObject("""{"type_widget":"text","attribut_label":"Commentaire","required":true}""")
        val p = MonitoringSchemas.parserUnePropriete("comment", v)!!
        assertEquals("comment", p.nom)
        assertEquals("text", p.typeWidget)
        assertEquals("Commentaire", p.label)
        assertTrue(p.obligatoire)
    }

    @Test
    fun seules_les_cles_du_web_comptent() {
        // Le web n'affiche que `attribut_label`, ne lit que `type_widget` et `hidden`.
        val p = MonitoringSchemas.parserUnePropriete("date_min", JSONObject("""{"type_widget":"date","label":"Début"}"""))!!
        assertEquals("", p.label)
        assertFalse(p.obligatoire)
        assertNull(MonitoringSchemas.parserUnePropriete("a", JSONObject("""{"widget":"select"}""")))
        assertNull(MonitoringSchemas.parserUnePropriete("b", JSONObject("""{"type":"number"}""")))
        assertNull(MonitoringSchemas.parserUnePropriete("c", JSONObject("""{"type_widget":"text","display":"x === 1"}"""))!!.hiddenExpr)
    }

    @Test
    fun choix_multiple_lu_comme_le_composant_web_du_widget() {
        fun multiple(json: String) = MonitoringSchemas.parserUnePropriete("m", JSONObject(json))!!.multiple
        assertTrue(multiple("""{"type_widget":"datalist","multiple":true}"""))
        assertFalse(multiple("""{"type_widget":"datalist","multi_select":true}"""))
        assertTrue(multiple("""{"type_widget":"observers","multi_select":true}"""))
        assertFalse(multiple("""{"type_widget":"nomenclature","multiple":true}"""))
        assertFalse(multiple("""{"type_widget":"radio","multiple":true}"""))
        assertTrue(multiple("""{"type_widget":"multiselect"}"""))
    }

    @Test
    fun valeurs_avec_leur_type_json_et_bool_radio() {
        val p = MonitoringSchemas.parserUnePropriete("n", JSONObject("""{"type_widget":"select","values":["1",2,{"label":"Trois","value":3}]}"""))!!
        assertEquals(listOf("1" to "1", "2" to "2", "3" to "Trois"), p.valeurs)
        assertEquals(mapOf<String, Any>("1" to "1", "2" to 2, "3" to 3), p.valeursBrutes)
        val oui = fr.ariegenature.geomys.monitoring.form.construireFormulaire(
            MonitoringApi.MonitoringSchemaObjet(
                type = "visit", label = null, labelList = null, nameField = null, parentType = null, childrenTypes = emptyList(),
                properties = linkedMapOf("b" to MonitoringSchemas.parserUnePropriete("b", JSONObject("""{"type_widget":"bool_radio","attribut_label":"B","values":["Oui","Non"]}"""))!!),
            ),
        ).fields.single().values
        assertEquals(listOf(true, false), oui.map { it.brut })
        assertEquals(listOf("Oui", "Non"), oui.map { it.label })
    }

    @Test
    fun hidden_true_conserve_le_champ_en_technique() {
        val p = MonitoringSchemas.parserUnePropriete("id_module", JSONObject("""{"hidden":true}"""))!!
        assertTrue(p.hiddenBool)
    }

    @Test
    fun hidden_expression_string_devient_hiddenExpr() {
        // Une valeur `hidden` de type String est une expression d'affichage conditionnel
        // (stockée telle quelle dans hiddenExpr), pas un masquage technique (hiddenBool).
        val v = JSONObject("""{"type_widget":"text","hidden":"statut === 'X'"}""")
        val p = MonitoringSchemas.parserUnePropriete("c", v)!!
        assertFalse(p.hiddenBool)
        assertEquals("statut === 'X'", p.hiddenExpr)
    }

    @Test
    fun champ_sans_widget_ni_hidden_est_ignore() {
        assertNull(MonitoringSchemas.parserUnePropriete("parasite", JSONObject("""{"foo":"bar"}""")))
    }

    @Test
    fun champ_sans_widget_ecarte_meme_en_specific() {
        // Comme le formulaire web, qui ne garde que les définitions munies d'un type_widget.
        assertNull(MonitoringSchemas.parserUnePropriete("x", JSONObject("{}"), enSpecific = true))
        assertNull(MonitoringSchemas.parserUnePropriete("d", JSONObject("""{"type_util":"date"}"""), enSpecific = true))
        // Masqué, il reste au schéma pour le corps envoyé.
        assertEquals("", MonitoringSchemas.parserUnePropriete("t", JSONObject("""{"hidden":true}"""))!!.typeWidget)
    }

    // ── heuristiques pures ─────────────────────────────────────────────────────
    @Test
    fun infere_code_nomenclature_depuis_api_ou_nom() {
        assertEquals("STADE_VIE", MonitoringSchemas.infererCodeNomenclature("x", "ref/nomenclatures/nomenclature/STADE_VIE"))
        assertEquals("stade_vie", MonitoringSchemas.infererCodeNomenclature("id_nomenclature_stade_vie", null))
        assertNull(MonitoringSchemas.infererCodeNomenclature("autre", null))
    }

    @Test
    fun le_champ_taxonomy_prend_son_id_list_sinon_la_liste_de_son_api() {
        // PAPALTB à l'ANA : une api vers la liste 102, sans id_list. Le web cherche dans tout TaxRef ;
        // l'appli dans la liste 102 (décision du 26 octobre 2026).
        val papaltb = MonitoringSchemas.parserUnePropriete(
            "cd_nom", JSONObject("""{"type_widget":"taxonomy","api":"taxref/allnamebylist/102","application":"TaxHub"}"""),
        )!!
        assertEquals(102, papaltb.idListTaxonomie)
        val sansRien = MonitoringSchemas.parserUnePropriete("cd_nom", JSONObject("""{"type_widget":"taxonomy"}"""))!!
        assertNull(sansRien.idListTaxonomie)
        val avecIdList = MonitoringSchemas.parserUnePropriete(
            "cd_nom", JSONObject("""{"type_widget":"taxonomy","id_list":103,"api":"taxref/allnamebylist/103"}"""),
        )!!
        assertEquals(103, avecIdList.idListTaxonomie)
        // Une datalist de taxons garde la liste de son api.
        val datalist = MonitoringSchemas.parserUnePropriete(
            "espece", JSONObject("""{"type_widget":"datalist","api":"taxref/allnamebylist/100","application":"TaxHub"}"""),
        )!!
        assertEquals(100, datalist.idListTaxonomie)
    }

    @Test
    fun extrait_id_liste_taxonomique_depuis_api() {
        assertEquals(100, MonitoringSchemas.extraireIdListeAllnamebylist("taxref/allnamebylist/100"))
        assertNull(MonitoringSchemas.extraireIdListeAllnamebylist("taxref/autre/100"))
    }
}
