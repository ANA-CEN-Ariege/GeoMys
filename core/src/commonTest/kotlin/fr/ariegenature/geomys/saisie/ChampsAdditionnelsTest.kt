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

// Propre au portage : AdditionalFieldsRenderer de GeoMys-Android @ cdd5196 n'a pas de test de
// collecter ni de champsObligatoiresVides (ils lisaient des vues). Les valeurs attendues sont celles
// que ses widgets rendaient.

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.model.OptionNomenclature
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.store.io.listeVersJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Les valeurs des champs additionnels OccTax : rendu initial, encodage envoyé, champs requis. */
class ChampsAdditionnelsTest {

    private fun def(
        nom: String,
        widget: WidgetType,
        libelles: List<String> = emptyList(),
        codes: List<String> = emptyList(),
        requis: Boolean = false,
        defaut: String? = null,
        widgetServeur: String = "",
        nomenclature: List<OptionNomenclature> = emptyList(),
        objets: List<String> = listOf(AdditionalFieldsObject.RELEVE),
        datasets: List<Int> = emptyList(),
    ) = AdditionalFieldDef(
        idField = 1, fieldName = nom, fieldLabel = nom.uppercase(), widget = widget,
        fieldValues = libelles, fieldValueCodes = codes, required = requis, defaultValue = defaut,
        objectsCode = objets, datasetsIds = datasets, widgetServeur = widgetServeur,
        nomenclatureOptions = nomenclature,
    )

    private fun etat(vararg defs: AdditionalFieldDef, valeurs: Map<String, String>? = null) =
        EtatChampsAdditionnels(defs.toList(), valeurs) { null }

    @Test
    fun liste_deroulante_envoie_le_code_serveur_sinon_le_libelle() {
        val meteo = def("meteo", WidgetType.SELECT, listOf("Soleil", "Pluie"), listOf("1", "2"))
        val ancien = def("ancien", WidgetType.SELECT, listOf("soleil", "pluie"))
        val desaligne = def("desaligne", WidgetType.SELECT, listOf("soleil", "pluie"), listOf("1"))
        val e = etat(meteo, ancien, desaligne, valeurs = mapOf("meteo" to "2", "ancien" to "pluie", "desaligne" to "1"))
        assertEquals(listOf("(non renseigné)", "Soleil", "Pluie"), ChampsAdditionnels.libellesListe(meteo))
        assertEquals(listOf("", "1", "2"), ChampsAdditionnels.codesListeDeroulante(meteo))
        assertEquals(2, e.position("meteo"))
        assertEquals(
            mapOf("meteo" to "2", "ancien" to "pluie", "desaligne" to ""),
            e.collecter(),
            "codes désalignés : repli sur les libellés, où « 1 » n'est pas",
        )
        e.choisirPosition("meteo", 0)
        e.choisirPosition("desaligne", 1)
        assertEquals(mapOf("meteo" to "", "ancien" to "pluie", "desaligne" to "soleil"), e.collecter())
    }

    @Test
    fun nomenclature_envoie_l_id_de_nomenclature() {
        val statut = def("statut", WidgetType.NOMENCLATURE, nomenclature = listOf(OptionNomenclature("85", "Présent"), OptionNomenclature("86", "Absent")))
        assertEquals(listOf("(non renseigné)", "Présent", "Absent"), ChampsAdditionnels.libellesListe(statut))
        assertEquals("86", etat(statut, valeurs = mapOf("statut" to "86")).collecter()["statut"])
        assertEquals("", etat(statut, valeurs = mapOf("statut" to "Absent")).collecter()["statut"])
        assertEquals("85", etat(statut.copy(defaultValue = "85")).collecter()["statut"])
    }

    @Test
    fun bouton_radio_decoche_au_second_clic() {
        val e = etat(def("abondance", WidgetType.RADIO, listOf("Rare", "Commun"), listOf("r", "c")), valeurs = mapOf("abondance" to "c"))
        assertEquals("c", e.selectionRadio("abondance"))
        assertEquals("c", e.collecter()["abondance"])
        e.cliquerRadio("abondance", "c")
        assertNull(e.selectionRadio("abondance"))
        assertEquals("", e.collecter()["abondance"])
        e.cliquerRadio("abondance", "r")
        e.cliquerRadio("abondance", "c")
        assertEquals("c", e.collecter()["abondance"])
        val inconnu = etat(def("abondance", WidgetType.RADIO, listOf("Rare", "Commun"), listOf("r", "c")), valeurs = mapOf("abondance" to "x"))
        assertEquals("", inconnu.collecter()["abondance"], "aucun bouton ne porte la valeur")
    }

    @Test
    fun cases_multiples_en_tableau_json_des_codes_coches() {
        val milieux = def("milieux", WidgetType.CHECKBOX, listOf("Prairie", "Forêt", "Lande"), listOf("1", "2", "3"))
        val e = etat(milieux, valeurs = mapOf("milieux" to """["1","3"]"""))
        assertEquals(listOf(true, false, true), e.cases("milieux"))
        assertEquals("""["1","3"]""", e.collecter()["milieux"])
        e.cocherCase("milieux", 1, true)
        e.cocherCase("milieux", 7, true)
        assertEquals("""["1","2","3"]""", e.collecter()["milieux"])
        assertEquals(listOf(true, false, true), etat(milieux, valeurs = mapOf("milieux" to "[1,3]")).cases("milieux"))
        // Un scalaire est le défaut serveur, que le web n'applique pas aux champs additionnels.
        assertEquals("[]", etat(milieux.copy(defaultValue = "2")).collecter()["milieux"])
        assertEquals(emptySet(), ChampsAdditionnels.parseCodesCoches("2"))
        assertEquals(listOf("1", "Forêt", "Lande"), ChampsAdditionnels.codesCases(milieux.copy(fieldValueCodes = listOf("1"))))
    }

    @Test
    fun case_unique_vrai_ou_faux() {
        val case = def("vu", WidgetType.CHECKBOX, listOf("Vu"))
        assertFalse(ChampsAdditionnels.estCasesMultiples(case))
        assertEquals("true", etat(case, valeurs = mapOf("vu" to "TRUE")).collecter()["vu"])
        assertEquals("false", etat(case).collecter()["vu"])
        val e = etat(case.copy(defaultValue = "true"))
        assertEquals(listOf(true), e.cases("vu"))
        e.cocher("vu", false)
        assertEquals("false", e.collecter()["vu"])
    }

    @Test
    fun valeur_saisie_puis_defaut_serveur_puis_date_du_jour() {
        val defs = listOf(
            def("jour", WidgetType.TEXT, widgetServeur = "date"),
            def("serveur", WidgetType.TEXT, widgetServeur = "date", defaut = "2026-01-01"),
            def("saisi", WidgetType.TEXT, widgetServeur = "date", defaut = "2026-01-01"),
            def("efface", WidgetType.TEXT, widgetServeur = "date"),
            def("nul", WidgetType.TEXT, defaut = "null"),
        )
        val e = EtatChampsAdditionnels(defs, mapOf("saisi" to "2026-02-02", "efface" to "")) { if (it == "date") "2026-05-21" else null }
        assertEquals(
            mapOf("jour" to "2026-05-21", "serveur" to "2026-01-01", "saisi" to "2026-02-02", "efface" to "", "nul" to ""),
            e.collecter(),
        )
        assertEquals("", e.texte("nul"), "« null » ne s'affiche pas")
        e.saisirTexte("efface", "x")
        assertEquals("x", e.collecter()["efface"])
    }

    @Test
    fun defaut_date_heure_selon_le_widget_serveur() {
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(ChampsAdditionnels.defautDateHeurePour("date")!!))
        assertTrue(Regex("""\d{2}:\d{2}""").matches(ChampsAdditionnels.defautDateHeurePour("TIME")!!))
        listOf("datetime", "date-time", "timestamp").forEach {
            assertTrue(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""").matches(ChampsAdditionnels.defautDateHeurePour(it)!!), it)
        }
        assertNull(ChampsAdditionnels.defautDateHeurePour("text"))
    }

    @Test
    fun champs_obligatoires_vides_sauf_les_cases() {
        val e = etat(
            def("texte", WidgetType.TEXT, requis = true),
            def("blanc", WidgetType.NUMBER, requis = true),
            def("liste", WidgetType.SELECT, listOf("A"), requis = true),
            def("radio", WidgetType.RADIO, listOf("A"), requis = true),
            def("case", WidgetType.CHECKBOX, requis = true),
            def("cases", WidgetType.CHECKBOX, listOf("A", "B"), requis = true),
            def("facultatif", WidgetType.TEXT),
            def("rempli", WidgetType.TEXTAREA, requis = true),
            valeurs = mapOf("blanc" to "  ", "rempli" to "ok"),
        )
        assertEquals(listOf("TEXTE", "BLANC", "LISTE", "RADIO"), e.champsObligatoiresVides())
        e.choisirPosition("liste", 1)
        e.cliquerRadio("radio", "A")
        e.saisirTexte("texte", "x")
        e.saisirTexte("blanc", "3")
        assertEquals(emptyList(), e.champsObligatoiresVides())
    }

    @Test
    fun widget_inconnu_saisi_en_texte() {
        val inconnu = def("habitat", WidgetType.INCONNU, widgetServeur = "taxonomy")
        val e = etat(inconnu, valeurs = mapOf("habitat" to "Hêtraie"))
        assertEquals("Type taxonomy (saisi en texte)", e.indication(inconnu))
        assertNull(e.indication(inconnu.copy(widgetServeur = "")))
        assertNull(e.indication(def("t", WidgetType.TEXT, widgetServeur = "text")))
        assertEquals("Hêtraie", e.collecter()["habitat"])
    }

    /** « Détails du relevé » rouvert : les valeurs collectées redonnent le même état. */
    @Test
    fun valeurs_collectees_rouvrent_le_meme_etat() {
        val defs = arrayOf(
            def("meteo", WidgetType.SELECT, listOf("Soleil", "Pluie"), listOf("1", "2")),
            def("abondance", WidgetType.RADIO, listOf("Rare", "Commun"), listOf("r", "c")),
            def("milieux", WidgetType.CHECKBOX, listOf("Prairie", "Forêt"), listOf("1", "2")),
            def("vu", WidgetType.CHECKBOX),
            def("note", WidgetType.TEXTAREA),
            def("statut", WidgetType.NOMENCLATURE, nomenclature = listOf(OptionNomenclature("85", "Présent"))),
        )
        val e = etat(*defs)
        e.choisirPosition("meteo", 2)
        e.cliquerRadio("abondance", "r")
        e.cocherCase("milieux", 1, true)
        e.cocher("vu", true)
        e.saisirTexte("note", "a/b \"c\"")
        e.choisirPosition("statut", 1)
        val collecte = e.collecter()
        assertEquals("""["2"]""", collecte["milieux"])
        assertEquals(collecte, etat(*defs, valeurs = collecte).collecter())
    }

    @Test
    fun releve_requis_sans_defaut_et_defauts_du_releve() {
        fun json(vararg defs: AdditionalFieldDef) = listeVersJson(AdditionalFieldDef.serializer(), defs.toList())
        val requisSansDefaut = def("obs", WidgetType.TEXT, requis = true)
        assertTrue(ChampsAdditionnels.aDesChampsReleveRequisSansDefaut(json(requisSansDefaut), 7))
        assertFalse(ChampsAdditionnels.aDesChampsReleveRequisSansDefaut(json(requisSansDefaut.copy(defaultValue = "x")), 7))
        assertTrue(ChampsAdditionnels.aDesChampsReleveRequisSansDefaut(json(requisSansDefaut.copy(defaultValue = " ")), 7), "un défaut blanc n'en est pas un")
        assertFalse(
            ChampsAdditionnels.aDesChampsReleveRequisSansDefaut(json(requisSansDefaut.copy(objectsCode = listOf(AdditionalFieldsObject.OCCURRENCE))), 7),
            "champ d'occurrence",
        )
        assertFalse(ChampsAdditionnels.aDesChampsReleveRequisSansDefaut(json(requisSansDefaut.copy(datasetsIds = listOf(5))), 7), "autre jeu de données")
        assertFalse(ChampsAdditionnels.aDesChampsReleveRequisSansDefaut(json(requisSansDefaut.copy(datasetsIds = listOf(5))), null))
        assertFalse(ChampsAdditionnels.aDesChampsReleveRequisSansDefaut("", 7))
        assertFalse(ChampsAdditionnels.aDesChampsReleveRequisSansDefaut("pas du json", 7))

        val defauts = ChampsAdditionnels.defautsChampsReleve(
            json(
                def("texte", WidgetType.TEXT, defaut = "x"),
                def("liste", WidgetType.SELECT, listOf("A", "B"), listOf("1", "2"), defaut = "2"),
                def("case", WidgetType.CHECKBOX, defaut = "true"),
                def("blanc", WidgetType.TEXT, defaut = "  "),
                def("sans", WidgetType.TEXT),
                def("occurrence", WidgetType.TEXT, defaut = "y", objets = listOf(AdditionalFieldsObject.OCCURRENCE)),
                def("ailleurs", WidgetType.TEXT, defaut = "z", datasets = listOf(5)),
            ),
            7,
        )
        assertEquals(mapOf("texte" to "x", "liste" to "2"), defauts)
    }
}
