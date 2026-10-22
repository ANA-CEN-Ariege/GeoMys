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

// Propre au portage : AdditionalFieldsRenderer de GeoMys-Android @ cdd5196 n'a pas de test de
// collecter ni des contrôles (ils lisaient des vues). Depuis le 20 octobre, les valeurs attendues sont
// celles du formulaire dynamique du web (dynamic-form.component et dynamic-form.service de GeoNature
// 2.17.5).

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
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

/** Les valeurs des champs additionnels OccTax : rendu initial, encodage, contrôles et envoi. */
class ChampsAdditionnelsTest {

    private fun def(
        nom: String,
        widget: WidgetType,
        libelles: List<String> = emptyList(),
        codes: List<String> = emptyList(),
        requis: Boolean = false,
        defaut: String? = null,
        nomenclature: List<OptionNomenclature> = emptyList(),
        objets: List<String> = listOf(AdditionalFieldsObject.RELEVE),
        datasets: List<Int> = emptyList(),
        brutes: List<String> = emptyList(),
    ) = AdditionalFieldDef(
        idField = 1, fieldName = nom, fieldLabel = nom.uppercase(), widget = widget,
        fieldValues = libelles, fieldValueCodes = codes, required = requis, defaultValue = defaut,
        objectsCode = objets, datasetsIds = datasets, nomenclatureOptions = nomenclature, valeursBrutes = brutes,
    )

    private fun etat(vararg defs: AdditionalFieldDef, valeurs: Map<String, String>? = null) =
        EtatChampsAdditionnels(defs.toList(), valeurs)

    @Test
    fun select_entree_vide_du_web_et_valeur_typee() {
        val meteo = def("meteo", WidgetType.SELECT, listOf("Soleil", "Pluie"), listOf("1", "2"), brutes = listOf("1", "2"))
        assertEquals("-", ChampsAdditionnels.optionVide(meteo))
        assertNull(ChampsAdditionnels.optionVide(meteo.copy(sansOptionVide = true)), "noNullOption")
        assertEquals(listOf("1" to "Soleil", "2" to "Pluie"), ChampsAdditionnels.options(meteo))
        val e = etat(meteo, valeurs = mapOf("meteo" to "2"))
        assertEquals("2", e.choix("meteo"))
        e.choisir("meteo", null)
        assertEquals("", e.collecter()["meteo"])
        e.choisir("meteo", "1")
        // Envoyée avec son type JSON : un nombre, pas « "1" ».
        assertEquals(1, ChampsAdditionnels.versJson(e.collecter(), listOf(meteo)).get("meteo"))
        // Une valeur hors des options reste, grisée, comme sur le web.
        val hors = etat(meteo, valeurs = mapOf("meteo" to "9"))
        assertTrue(hors.horsListe("meteo"))
        hors.choisir("meteo", "1")
        assertEquals("9", hors.collecter()["meteo"])
    }

    @Test
    fun nomenclature_label_fr_affiche_label_default_envoye() {
        val statut = def(
            "statut", WidgetType.NOMENCLATURE,
            nomenclature = listOf(OptionNomenclature("85", "Présent", "Present", "1"), OptionNomenclature("86", "Absent (fr)", "Absent", "2")),
        )
        assertEquals(listOf("Present" to "Présent", "Absent" to "Absent (fr)"), ChampsAdditionnels.options(statut))
        assertNull(ChampsAdditionnels.optionVide(statut), "pas d'entrée vide : une croix")
        assertTrue(ChampsAdditionnels.effacable(statut))
        assertEquals("Absent", etat(statut, valeurs = mapOf("statut" to "Absent")).collecter()["statut"])
        // Une saisie d'une version antérieure porte l'id_nomenclature : retrouvée par son option.
        assertEquals("Absent", etat(statut, valeurs = mapOf("statut" to "86")).collecter()["statut"])
        assertEquals("Present", etat(statut.copy(defaultValue = "Present")).collecter()["statut"])
        // Restreinte à ses `cd_nomenclatures`.
        assertEquals(listOf("Absent" to "Absent (fr)"), ChampsAdditionnels.options(statut.copy(cdNomenclatures = listOf("2"))))
        // multi_select : un tableau de label_default.
        val multi = statut.copy(choixMultiple = true)
        val e = etat(multi)
        e.basculer("statut", "Absent", true)
        assertEquals(JSONArray(listOf("Absent")).toString(), ChampsAdditionnels.versJson(e.collecter(), listOf(multi)).get("statut").toString())
    }

    @Test
    fun bouton_radio_decoche_au_second_clic() {
        val abondance = def("abondance", WidgetType.RADIO, listOf("Rare", "Commun"), listOf("r", "c"))
        val e = etat(abondance, valeurs = mapOf("abondance" to "c"))
        assertEquals("c", e.choix("abondance"))
        e.cliquerRadio("abondance", "c")
        assertNull(e.choix("abondance"))
        assertEquals("", e.collecter()["abondance"])
        e.cliquerRadio("abondance", "r")
        e.cliquerRadio("abondance", "c")
        assertEquals("c", e.collecter()["abondance"])
        // bool_radio : vrai pour la première valeur, faux pour la seconde.
        val boolRadio = def("present", WidgetType.BOOL_RADIO, listOf("Oui", "Non"))
        assertEquals(listOf("true" to "Oui", "false" to "Non"), ChampsAdditionnels.options(boolRadio))
        val b = etat(boolRadio)
        b.cliquerRadio("present", "false")
        assertEquals(false, ChampsAdditionnels.versJson(b.collecter(), listOf(boolRadio)).get("present"))
    }

    @Test
    fun cases_a_cocher_en_tableau_des_valeurs_typees() {
        val milieux = def("milieux", WidgetType.CHECKBOX, listOf("Prairie", "Forêt", "Lande"), listOf("1", "2", "3"), brutes = listOf("1", "2", "3"))
        val e = etat(milieux, valeurs = mapOf("milieux" to """["1","3"]"""))
        assertEquals(listOf("1", "3"), e.choixMultiples("milieux"))
        e.basculer("milieux", "2", true)
        e.basculer("milieux", "1", false)
        assertEquals("""["3","2"]""", e.collecter()["milieux"], "dans l'ordre des coches, comme le tableau du web")
        assertEquals("[3,2]", ChampsAdditionnels.versJson(e.collecter(), listOf(milieux)).get("milieux").toString())
        assertEquals(listOf("1", "3"), etat(milieux, valeurs = mapOf("milieux" to "[1,3]")).choixMultiples("milieux"))
        // Une case seule (une option) : le tableau de sa valeur ; une ancienne case « true » la coche.
        val vu = def("vu", WidgetType.CHECKBOX, listOf("Vu"), listOf("oui"))
        assertEquals(listOf("oui"), etat(vu, valeurs = mapOf("vu" to "true")).choixMultiples("vu"))
        assertEquals(emptyList(), etat(vu, valeurs = mapOf("vu" to "false")).choixMultiples("vu"))
        // Requise et vide : le message du web.
        assertEquals("MILIEUX : Veuillez choisir au moins une option", etat(milieux.copy(required = true)).refus())
    }

    @Test
    fun bool_checkbox_nulle_tant_qu_on_n_y_touche_pas() {
        val vu = def("vu", WidgetType.BOOL_CHECKBOX)
        val e = etat(vu)
        assertNull(e.case("vu"))
        assertEquals(JSONObject.NULL, ChampsAdditionnels.versJson(e.collecter(), listOf(vu)).get("vu"))
        e.cocher("vu", true)
        assertEquals(true, ChampsAdditionnels.versJson(e.collecter(), listOf(vu)).get("vu"))
        e.cocher("vu", false)
        assertEquals(false, ChampsAdditionnels.versJson(e.collecter(), listOf(vu)).get("vu"))
        assertEquals("Champs obligatoires à renseigner : VU", etat(vu.copy(required = true)).refus())
    }

    @Test
    fun multiselect_dates_heures_et_texte() {
        val especes = def("especes", WidgetType.MULTISELECT, listOf("A", "B"), listOf("a", "b"), brutes = listOf("\"a\"", "\"b\""))
        val jour = def("jour", WidgetType.DATE)
        val heure = def("heure", WidgetType.TIME)
        val e = etat(especes, jour, heure)
        e.choisirMultiples("especes", listOf("b"))
        e.saisirTexte("jour", "2026-10-20")
        e.saisirTexte("heure", "08:05")
        val json = ChampsAdditionnels.versJson(e.collecter(), listOf(especes, jour, heure))
        assertEquals("""["b"]""", json.get("especes").toString())
        assertEquals("2026-10-20", json.getString("jour"))
        assertEquals("08:05", json.getString("heure"))
        assertEquals("2026-10-20", ChampsAdditionnels.valeurDateHeure(false, ChampsAdditionnels.selectionDateHeure(false, "2026-10-20", 0L)))
        assertEquals("08:05", ChampsAdditionnels.valeurDateHeure(true, ChampsAdditionnels.selectionDateHeure(true, "8:5", 0L)))
    }

    @Test
    fun observateurs_enregistres_en_objets() {
        val obs = def("obs", WidgetType.OBSERVERS)
        val liste = listOf(3 to "MARTIN Alice", 4 to "DURAND Bob")
        val e = EtatChampsAdditionnels(listOf(obs), null, liste)
        assertEquals(listOf("3" to "MARTIN Alice", "4" to "DURAND Bob"), e.options(obs))
        e.choisir("obs", "4")
        val json = ChampsAdditionnels.versJson(e.collecter(), listOf(obs))
        assertEquals(4, json.getJSONObject("obs").getInt("id_role"))
        assertEquals("DURAND Bob", json.getJSONObject("obs").getString("nom_complet"))
        // Rouvert : l'objet redonne l'observateur.
        assertEquals("4", EtatChampsAdditionnels(listOf(obs), e.collecter(), liste).choix("obs"))
        val multi = obs.copy(choixMultiple = true)
        val m = EtatChampsAdditionnels(listOf(multi), null, liste)
        m.choisirMultiples("obs", listOf("3", "4"))
        assertEquals(2, (ChampsAdditionnels.versJson(m.collecter(), listOf(multi)).get("obs") as JSONArray).length())
    }

    @Test
    fun taxon_enregistre_en_objet() {
        val taxon = def("taxon", WidgetType.TAXONOMY)
        val objet = JSONObject().apply { put("cd_nom", 4001); put("lb_nom", "Erithacus rubecula"); put("nom_vern", "Rougegorge familier") }
        val e = etat(taxon)
        e.choisirTaxon("taxon", objet, "Rougegorge familier")
        assertTrue(e.taxonChoisi("taxon"))
        assertEquals(4001, ChampsAdditionnels.versJson(e.collecter(), listOf(taxon)).getJSONObject("taxon").getInt("cd_nom"))
        // Rouvert, il porte son nom ; la frappe l'oublie.
        val rouvert = etat(taxon, valeurs = e.collecter())
        assertEquals("Rougegorge familier", rouvert.texteTaxon("taxon"))
        rouvert.saisirTaxon("taxon", "Rouge")
        assertFalse(rouvert.taxonChoisi("taxon"))
        assertEquals("Champs obligatoires à renseigner : TAXON", etat(taxon.copy(required = true), valeurs = mapOf("taxon" to "")).refus())
    }

    @Test
    fun controles_du_web_masques_compris() {
        val e = etat(
            def("texte", WidgetType.TEXT, requis = true),
            def("liste", WidgetType.SELECT, listOf("A"), listOf("a"), requis = true),
            def("cache", WidgetType.TEXT, requis = true).copy(masque = true),
            def("rempli", WidgetType.TEXTAREA, requis = true),
            def("grise", WidgetType.TEXT, requis = true).copy(desactive = true),
            valeurs = mapOf("rempli" to "ok"),
        )
        // Un champ masqué compte, comme sur le web ; un champ grisé non.
        assertEquals("Champs obligatoires à renseigner : TEXTE, LISTE, CACHE (masqué)", e.refus())
        e.saisirTexte("texte", "x")
        e.choisir("liste", "a")
        e.saisirTexte("cache", "y")
        assertNull(e.refus())

        val code = def("code", WidgetType.TEXT).copy(motif = "[A-Z]{3}", messageMotif = "Trois majuscules", longueurMax = 5)
        val c = etat(code, valeurs = mapOf("code" to "ab"))
        assertEquals("Trois majuscules", c.refus())
        c.saisirTexte("code", "ABC")
        assertNull(c.refus())
        c.saisirTexte("code", "ABCDEF")
        assertEquals("« CODE » : 5 caractères au plus", c.refus())

        val hauteur = def("hauteur", WidgetType.NUMBER).copy(min = 0.0, max = 10.0)
        val h = etat(hauteur, valeurs = mapOf("hauteur" to "12"))
        assertEquals("Valeurs hors limites : HAUTEUR (entre 0 et 10)", h.refus())
        h.saisirTexte("hauteur", "7,5")
        assertNull(h.refus())
        // min > max : le web ne pose aucune borne.
        assertNull(etat(hauteur.copy(min = 20.0), valeurs = mapOf("hauteur" to "50")).refus())
    }

    @Test
    fun la_barre_rouge_marque_chaque_champ_que_le_web_borde_de_rouge() {
        val texte = def("texte", WidgetType.TEXT, requis = true)
        val cases = def("cases", WidgetType.CHECKBOX, listOf("A", "B"), listOf("a", "b"), requis = true)
        val code = def("code", WidgetType.TEXT).copy(motif = "[A-Z]{3}")
        val hauteur = def("hauteur", WidgetType.NUMBER).copy(min = 0.0, max = 10.0)
        val grise = def("grise", WidgetType.TEXT, requis = true).copy(desactive = true)
        val libre = def("libre", WidgetType.TEXT)
        val consignes = def("consignes", WidgetType.HTML).copy(html = "<b>Notez</b>")
        val e = etat(texte, cases, code, hauteur, grise, libre, consignes, valeurs = mapOf("code" to "ab", "hauteur" to "12"))
        assertEquals(listOf("texte", "cases", "code", "hauteur"), listOf(texte, cases, code, hauteur, grise, libre, consignes).filter(e::invalide).map { it.fieldName })
        e.saisirTexte("texte", "x")
        e.basculer("cases", "a", true)
        e.saisirTexte("code", "ABC")
        e.saisirTexte("hauteur", "7")
        assertTrue(listOf(texte, cases, code, hauteur).none(e::invalide), "remplis et valides : plus de barre")
        assertNull(e.refus(), "un bloc html n'a pas de contrôle")
    }

    @Test
    fun blocs_html_affiches_et_widgets_non_rendus_caches() {
        val e = etat(
            def("aide", WidgetType.HTML).copy(html = "<b>Lire</b>"),
            def("zone", WidgetType.INCONNU, defaut = "09"),
            def("note", WidgetType.TEXT),
        )
        assertEquals(listOf("aide", "note"), e.defsAffiches.map { it.fieldName })
        // Le widget non rendu garde son défaut et part ; le bloc html n'a pas de valeur.
        assertEquals(mapOf("zone" to "09", "note" to ""), e.collecter())
    }

    @Test
    fun envoi_comme_le_web() {
        val defs = listOf(
            def("remarque", WidgetType.TEXT),
            def("effectif", WidgetType.NUMBER),
            def("taux", WidgetType.NUMBER),
            def("jamais", WidgetType.TEXT, defaut = "défaut"),
            def("vide", WidgetType.TEXT),
            def("grise", WidgetType.TEXT, defaut = "x").copy(desactive = true),
        )
        val json = ChampsAdditionnels.versJson(
            mapOf("remarque" to "42", "effectif" to "42", "taux" to "12,5", "vide" to "", "ancien" to "true"),
            defs,
        )
        assertEquals("42", json.getString("remarque"), "un texte reste un texte")
        assertEquals(42L, json.get("effectif"))
        assertEquals(12.5, json.getDouble("taux"), 1e-9)
        assertEquals("défaut", json.getString("jamais"), "un champ jamais ouvert part avec son défaut")
        assertEquals(JSONObject.NULL, json.get("vide"))
        assertFalse(json.has("grise"), "un champ grisé ne part pas")
        assertEquals(true, json.get("ancien"), "sans définition : typé par heuristique")
    }

    @Test
    fun champs_globaux_puis_ceux_du_jeu_de_donnees() {
        fun json(vararg defs: AdditionalFieldDef) = listeVersJson(AdditionalFieldDef.serializer(), defs.toList())
        val defs = json(
            def("jeu1", WidgetType.TEXT, datasets = listOf(7)),
            def("global1", WidgetType.TEXT),
            def("jeu2", WidgetType.TEXT, datasets = listOf(7)),
            def("global2", WidgetType.TEXT),
            def("autre", WidgetType.TEXT, datasets = listOf(5)),
        )
        assertEquals(
            listOf("global1", "global2", "jeu1", "jeu2"),
            ChampsAdditionnels.defsPour(defs, AdditionalFieldsObject.RELEVE, 7).map { it.fieldName },
        )
    }

    /** « Détails du relevé » rouvert : les valeurs collectées redonnent le même état. */
    @Test
    fun valeurs_collectees_rouvrent_le_meme_etat() {
        val defs = arrayOf(
            def("meteo", WidgetType.SELECT, listOf("Soleil", "Pluie"), listOf("1", "2")),
            def("abondance", WidgetType.RADIO, listOf("Rare", "Commun"), listOf("r", "c")),
            def("milieux", WidgetType.CHECKBOX, listOf("Prairie", "Forêt"), listOf("1", "2")),
            def("vu", WidgetType.BOOL_CHECKBOX),
            def("note", WidgetType.TEXTAREA),
            def("statut", WidgetType.NOMENCLATURE, nomenclature = listOf(OptionNomenclature("85", "Présent", "Present"))),
        )
        val e = etat(*defs)
        e.choisir("meteo", "2")
        e.cliquerRadio("abondance", "r")
        e.basculer("milieux", "2", true)
        e.cocher("vu", true)
        e.saisirTexte("note", "a/b \"c\"")
        e.choisir("statut", "Present")
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
