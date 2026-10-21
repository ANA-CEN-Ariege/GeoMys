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

// Propre au portage : NouvelleVisiteFragment de GeoMys-Android @ cdd5196 ne teste ni valeurToJson,
// ni parseValeursJson, ni typerPourField, ni la préparation de l'enregistrement. Les formes JSON
// attendues sont celles qu'écrit l'org.json d'Android (un Double entier sans « .0 », « / » échappé).

package fr.ariegenature.geomys.monitoring.form

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.MediaSuivi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Le passage des valeurs du formulaire au `valeursJson` enregistré dans la file des Suivis, puis
 *  au formulaire d'une saisie rouverte. */
class ValeursFormulaireTest {

    private val t = ServicesDeTest()

    @AfterTest
    fun nettoyer() = t.nettoyer()

    @Test
    fun valeurs_ecrites_avec_leur_type() {
        val json = ValeursFormulaire.valeursJson(linkedMapOf(
            "nom" to "Merle",
            "nb" to 7,
            "taux" to 12.5,
            "entier" to 12.0,
            "vide" to null,
            "coche" to true,
            "obs" to listOf("1", "2"),
            "id_nomenclature" to "123",
            "date" to "2026-5-21",
            "url" to "a/b",
        ))
        assertEquals(
            """{"nom":"Merle","nb":7,"taux":12.5,"entier":12,"vide":null,"coche":true,"obs":["1","2"],""" +
                """"id_nomenclature":"123","date":"2026-5-21","url":"a\/b"}""",
            json,
        )
    }

    @Test
    fun valeurs_relues_avec_le_type_de_la_lecture_json() {
        val v = ValeursFormulaire.parseValeursJson(
            """{"a":1,"b":2.5,"c":"x","d":null,"e":[1,"2",null],"f":true,"g":3000000000}""",
        )
        assertEquals(1, v["a"])
        assertEquals(2.5, v["b"])
        assertEquals("x", v["c"])
        assertTrue(v.containsKey("d") && v["d"] == null)
        assertEquals(listOf("1", "2", "null"), v["e"], "tableaux en List<String>")
        assertEquals(true, v["f"])
        assertEquals(3000000000L, v["g"])
    }

    @Test
    fun valeur_relue_typee_pour_son_champ() {
        fun champ(type: ViewType) = EditableField("x", type, "X")
        assertEquals("12", ValeursFormulaire.typerPourField(champ(ViewType.SELECT), 12))
        assertEquals("2026-5-21", ValeursFormulaire.typerPourField(champ(ViewType.DATE), "2026-5-21"))
        assertEquals(4117, ValeursFormulaire.typerPourField(champ(ViewType.TAXON), "4117"))
        assertEquals(4117, ValeursFormulaire.typerPourField(champ(ViewType.TAXON), 4117L))
        assertNull(ValeursFormulaire.typerPourField(champ(ViewType.TAXON), "Merle"))
        assertEquals(12, ValeursFormulaire.typerPourField(champ(ViewType.NUMBER), 12.0))
        assertEquals(12.5, ValeursFormulaire.typerPourField(champ(ViewType.NUMBER), 12.5))
        assertEquals(3, ValeursFormulaire.typerPourField(champ(ViewType.NUMBER), "3"))
        assertEquals(3.5, ValeursFormulaire.typerPourField(champ(ViewType.NUMBER), "3.5"))
        assertNull(ValeursFormulaire.typerPourField(champ(ViewType.NUMBER), true))
        assertEquals(listOf("1"), ValeursFormulaire.typerPourField(champ(ViewType.SELECT_MULTIPLE), 1))
        assertEquals(listOf("1", "2"), ValeursFormulaire.typerPourField(champ(ViewType.CHECKBOX_MULTIPLE), listOf(1, "2")))
        assertEquals(true, ValeursFormulaire.typerPourField(champ(ViewType.CHECKBOX), true))
        assertNull(ValeursFormulaire.typerPourField(champ(ViewType.TEXT), null))
    }

    @Test
    fun enregistrement_sans_les_medias_mais_avec_leurs_fichiers() {
        val champs = listOf(
            EditableField("comments", ViewType.TEXTAREA, "Commentaire"),
            EditableField("nb", ViewType.NUMBER, "Nombre"),
            EditableField("photos_vides", ViewType.MEDIA, "Photos", schemaDotTable = "gn_monitoring.t_base_sites"),
            EditableField("photos", ViewType.MEDIA, "Photos", schemaDotTable = "gn_monitoring.t_base_visits"),
            EditableField("titre", ViewType.TEXT, "Titre"),
        )
        // Les médias de TOUS les champs partent, chacun avec ses titre, auteur et description.
        val medias = listOf(
            MediaSuivi("file:///a.jpg", "Mare", "DUPONT Alice", "Vue nord", "photos", "gn_monitoring.t_base_visits"),
            MediaSuivi("file:///b.jpg", "Site", champ = "photos_vides", schemaDotTable = "gn_monitoring.t_base_sites"),
        )
        val e = ValeursFormulaire.preparerEnregistrement(champs, linkedMapOf(
            "comments" to "42",
            "nb" to 3,
            "photos_vides" to listOf("file:///b.jpg"),
            "photos" to listOf("file:///a.jpg"),
            "titre" to "T",
        ), medias = medias)
        assertEquals("""{"comments":"42","nb":3,"titre":"T"}""", e.valeursJson, "aucun champ MEDIA dans le corps")
        assertEquals(listOf("file:///a.jpg", "file:///b.jpg"), e.mediaPaths)
        assertEquals(medias, e.medias)
        assertEquals("gn_monitoring.t_base_visits", e.mediaSchemaDotTable)
        assertEquals(listOf("comments", "titre"), e.champsTexteLibre)
    }

    @Test
    fun saisie_rouverte_avec_ses_valeurs_et_ses_fichiers() {
        val champs = listOf(
            EditableField("taxon", ViewType.TAXON, "Espèce"),
            EditableField("comments", ViewType.TEXT, "Commentaire"),
            EditableField("photos", ViewType.MEDIA, "Photos"),
            EditableField("autres", ViewType.MEDIA, "Autres"),
        )
        val valeurs = mapOf("taxon" to "4117", "comments" to "")
        // Chaque média revient dans son champ ; celui d'une saisie ancienne, sans champ, dans le premier.
        val ancien = MediaSuivi("file:///a.jpg")
        val autre = MediaSuivi("file:///b.jpg", "Titre", champ = "autres")
        val rouverts = ValeursFormulaire.champsPreremplis(champs, valeurs, listOf(ancien, autre))
        assertEquals(4117, rouverts[0].value)
        assertEquals("", rouverts[1].value)
        assertEquals(listOf(ancien), rouverts[2].value, "média ancien sur le premier champ MEDIA")
        assertEquals(listOf(autre), rouverts[3].value)
        assertNull(ValeursFormulaire.champsPreremplis(champs, valeurs, listOf(autre))[2].value)
        assertEquals(setOf("taxon"), ValeursFormulaire.champsDirtyInitiaux(valeurs), "les valeurs vides ne comptent pas")
        assertTrue(ValeursFormulaire.champsDirtyInitiaux(null).isEmpty())
    }

    /** L'aller-retour complet : une saisie enregistrée puis rouverte retrouve exactement les mêmes
     *  valeurs, donc le même corps envoyé. */
    @Test
    fun une_saisie_rouverte_rend_les_memes_valeurs() = runTest {
        val champs = listOf(
            EditableField("nom", ViewType.TEXT, "Nom", value = "Merle"),
            EditableField("nb", ViewType.NUMBER, "Nombre", value = "7"),
            EditableField("taux", ViewType.NUMBER, "Taux", value = "12,5", decimal = true),
            EditableField("date", ViewType.DATE, "Date", value = "2026-05-21"),
            EditableField("heure", ViewType.TIME, "Heure", value = "8:05"),
            EditableField("dt", ViewType.DATETIME, "Date-heure", value = "2026-05-21T08:05"),
            EditableField("sel", ViewType.SELECT, "Choix", value = "b", values = listOf(PropertyValue("a", "A"), PropertyValue("b", "B"))),
            EditableField("multi", ViewType.SELECT_MULTIPLE, "Multi", value = listOf("a", "b"), values = listOf(PropertyValue("a", "A"), PropertyValue("b", "B"))),
            EditableField("case", ViewType.CHECKBOX, "Case", value = true),
            EditableField("faux", ViewType.CHECKBOX, "Faux", value = false),
            EditableField("vide", ViewType.CHECKBOX, "Vide"),
        )
        val premier = FormulaireEtat(champs, t.services.taxRef, backgroundScope)
        val json = ValeursFormulaire.preparerEnregistrement(champs, premier.lireValeursPourEnvoi()).valeursJson
        val relues = ValeursFormulaire.parseValeursJson(json)
        val rouverts = ValeursFormulaire.champsPreremplis(champs.map { it.copy(value = null) }, relues, emptyList())
        val second = FormulaireEtat(rouverts, t.services.taxRef, backgroundScope, ValeursFormulaire.champsDirtyInitiaux(relues))
        assertEquals(premier.lireValeursPourEnvoi(), second.lireValeursPourEnvoi())
        assertEquals(json, ValeursFormulaire.preparerEnregistrement(rouverts, second.lireValeursPourEnvoi()).valeursJson)
    }
}
