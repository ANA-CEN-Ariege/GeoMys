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

// Propre au portage : le contrat des valeurs du FormulaireRenderer de GeoMys-Android @ cdd5196
// (lireValeurs, valeurs initiales des creerChamp*, appliquerValeur, lireValeursPourEnvoi), champ par
// champ. Les valeurs attendues sont celles que rend le code Android, lu ligne à ligne.

package fr.ariegenature.geomys.monitoring.form

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.TaxRefEntry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Ce que [FormulaireEtat] rend pour chaque type de champ : c'est ce qui part au serveur. */
@OptIn(ExperimentalCoroutinesApi::class)
class ContratValeursTest {

    private val t = ServicesDeTest()
    private val taxRef get() = t.services.taxRef

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun TestScope.formulaire(vararg champs: EditableField, dirty: Set<String> = emptySet()) =
        FormulaireEtat(champs.toList(), taxRef, backgroundScope, dirty)

    private fun options(vararg valeurs: String) = valeurs.map { PropertyValue(it, "Libellé $it") }

    @Test
    fun texte_vide_null_ou_nombre() = runTest {
        val v = formulaire(
            EditableField("a", ViewType.TEXT, "A"),
            EditableField("b", ViewType.TEXT, "B", value = "null"),
            EditableField("c", ViewType.TEXTAREA, "C", value = 5),
        ).lireValeurs()
        assertEquals("", v["a"])
        assertEquals("", v["b"], "la chaîne \"null\" vaut un champ vide")
        assertEquals("5", v["c"])
    }

    @Test
    fun nombre_entier_decimal_ou_illisible() = runTest {
        val v = formulaire(
            EditableField("dec", ViewType.NUMBER, "D", value = "12,5", decimal = true),
            EditableField("ent", ViewType.NUMBER, "E", value = "12,5"),
            EditableField("txt", ViewType.NUMBER, "T", value = "abc"),
            EditableField("int", ViewType.NUMBER, "I", value = 3),
            EditableField("dbl", ViewType.NUMBER, "Db", value = 2.0),
            EditableField("dbld", ViewType.NUMBER, "Dd", value = 2.0, decimal = true),
            EditableField("neg", ViewType.NUMBER, "N", value = "-4"),
        ).lireValeurs()
        assertEquals(12.5, v["dec"])
        assertEquals(12, v["ent"], "widget entier : tronqué")
        assertNull(v["txt"])
        assertEquals(3, v["int"])
        assertEquals(2, v["dbl"], "« 2.0 » relu par un widget entier")
        assertEquals(2.0, v["dbld"])
        assertEquals(-4, v["neg"])
    }

    @Test
    fun date_lue_avec_tolerance_et_rendue_sans_zeros() = runTest {
        val etat = formulaire(
            EditableField("iso", ViewType.DATE, "A", value = "2026-05-21"),
            EditableField("debord", ViewType.DATE, "B", value = "2026-02-30"),
            EditableField("heure", ViewType.DATE, "C", value = "2026-05-21T10:00:00"),
            EditableField("fr", ViewType.DATE, "D", value = "21/05/2026"),
            EditableField("vide", ViewType.DATE, "E"),
            EditableField("nombre", ViewType.DATE, "F", value = 20260521),
        )
        val v = etat.lireValeurs()
        assertEquals("2026-5-21", v["iso"])
        assertEquals("2026-3-2", v["debord"], "le 30 février se reporte au 2 mars, comme SimpleDateFormat")
        assertEquals("2026-5-21", v["heure"])
        assertEquals("", v["fr"], "format non reconnu : rien n'est choisi")
        assertEquals("", v["vide"])
        assertEquals("", v["nombre"], "seule une chaîne est lue")
        assertEquals("21/05/2026", etat.affichageDateHeure("iso"))
        assertNull(etat.affichageDateHeure("fr"))
    }

    @Test
    fun heure_et_date_heure() = runTest {
        val etat = formulaire(
            EditableField("h1", ViewType.TIME, "A", value = "8:5"),
            EditableField("h2", ViewType.TIME, "B", value = "10h30"),
            EditableField("h3", ViewType.TIME, "C", value = "25:00"),
            EditableField("h4", ViewType.TIME, "D", value = "10:75"),
            EditableField("h5", ViewType.TIME, "E", value = "10:30:15"),
            EditableField("dt1", ViewType.DATETIME, "F", value = "2026-05-21T10:30"),
            EditableField("dt2", ViewType.DATETIME, "G", value = "2026-05-21 10:30:15"),
            EditableField("dt3", ViewType.DATETIME, "H", value = "2026-05-21"),
            EditableField("dt4", ViewType.DATETIME, "I", value = "2026-02-30 25:00:00"),
        )
        val v = etat.lireValeurs()
        assertEquals("08:05", v["h1"])
        assertEquals("10:30", v["h2"])
        assertEquals("", v["h3"], "heure hors de 0..23 : rien n'est choisi")
        assertEquals("10:00", v["h4"], "minute hors de 0..59 : 0")
        assertEquals("10:30", v["h5"])
        assertEquals("2026-05-21 10:30:00", v["dt1"])
        assertEquals("2026-05-21 10:30:15", v["dt2"])
        assertEquals("", v["dt3"], "une date sans heure n'est pas une date-heure")
        assertEquals("2026-03-03 01:00:00", v["dt4"], "débordements reportés")
        assertEquals("21/05/2026 10:30", etat.affichageDateHeure("dt1"))
    }

    @Test
    fun choix_simples_radio_et_multiples() = runTest {
        val v = formulaire(
            EditableField("sel", ViewType.SELECT, "A", value = "2", values = options("1", "2")),
            EditableField("selInconnu", ViewType.SELECT, "B", value = "9", values = options("1", "2")),
            EditableField("selInt", ViewType.SELECT, "C", value = 2, values = options("1", "2")),
            EditableField("radio", ViewType.RADIO, "D", value = 2, values = options("1", "2")),
            EditableField("radioVide", ViewType.RADIO, "E", values = options("1", "2")),
            EditableField("multi", ViewType.SELECT_MULTIPLE, "F", value = listOf("2", "9"), values = options("1", "2")),
            EditableField("multiStr", ViewType.SELECT_MULTIPLE, "G", value = "1", values = options("1", "2")),
            EditableField("multiVide", ViewType.SELECT_MULTIPLE, "H", values = options("1", "2")),
            EditableField("cases", ViewType.CHECKBOX_MULTIPLE, "I", value = listOf("2", "9", "1"), values = options("1", "2")),
            EditableField("casesVides", ViewType.CHECKBOX_MULTIPLE, "J", values = options("1", "2")),
        ).lireValeurs()
        assertEquals("2", v["sel"])
        assertEquals("", v["selInconnu"])
        assertEquals("", v["selInt"], "un SELECT ne lit qu'une chaîne")
        assertEquals("2", v["radio"], "un RADIO lit toString() de la valeur")
        assertEquals("", v["radioVide"])
        assertEquals(listOf("2", "9"), v["multi"], "liste reprise telle quelle")
        assertEquals(listOf("1"), v["multiStr"])
        assertNull(v["multiVide"], "liste vide → null")
        assertEquals(listOf("1", "2"), v["cases"], "cases dans l'ordre des options, valeurs inconnues écartées")
        assertNull(v["casesVides"])
    }

    @Test
    fun case_a_cocher_vraie_ou_null() = runTest {
        val v = formulaire(
            EditableField("b", ViewType.CHECKBOX, "A", value = true),
            EditableField("s", ViewType.CHECKBOX, "B", value = "1"),
            EditableField("f", ViewType.CHECKBOX, "C", value = "false"),
            EditableField("z", ViewType.CHECKBOX, "D", value = 0),
            EditableField("n", ViewType.CHECKBOX, "E"),
        ).lireValeurs()
        assertEquals(true, v["b"])
        assertEquals(true, v["s"])
        assertNull(v["f"], "jamais false : null")
        assertNull(v["z"])
        assertNull(v["n"])
    }

    @Test
    fun medias_liste_jamais_null_et_copiee_a_chaque_lecture() = runTest {
        val etat = formulaire(
            EditableField("photos", ViewType.MEDIA, "A"),
            EditableField("ancien", ViewType.MEDIA, "B", value = "file:///a.jpg"),
        )
        val avant = etat.lireValeurs()
        assertEquals(emptyList<String>(), avant["photos"], "aucune photo : liste vide, pas null")
        assertEquals(listOf("file:///a.jpg"), avant["ancien"])
        etat.ajouterMedias("photos", listOf("file:///b.jpg", ""))
        // Comme le tag d'Android depuis e69543d : une lecture rend une copie, que les ajouts suivants
        // ne touchent pas.
        assertEquals(emptyList<String>(), avant["photos"])
        assertEquals(listOf("file:///b.jpg"), etat.lireValeurs()["photos"])
        etat.retirerMedia("photos", "file:///b.jpg")
        assertEquals(emptyList<String>(), etat.lireValeurs()["photos"])
    }

    @Test
    fun taxon_cd_nom_connu_absent_ou_texte() = runTest {
        taxRef.vider()
        taxRef.remplacerTout(mapOf(TaxRefCache.normaliser("Merle noir") to TaxRefEntry(4117, "Turdus merula", "Merle noir")))
        val etat = formulaire(
            EditableField("connu", ViewType.TAXON, "A", value = 4117),
            EditableField("absent", ViewType.TAXON, "B", value = 999999),
            EditableField("texte", ViewType.TAXON, "C", value = "Merle"),
            EditableField("long", ViewType.TAXON, "D", value = 4117L),
        )
        val v = etat.lireValeurs()
        assertEquals(4117, v["connu"])
        assertEquals("Merle noir", etat.texte("connu"))
        assertEquals("✓ Turdus merula  •  cd_nom 4117", etat.statutTaxon("connu")?.texte)
        assertEquals(999999, v["absent"], "cd_nom gardé même absent du cache")
        assertEquals("cd_nom 999999", etat.texte("absent"))
        assertEquals("✓ cd_nom 999999", etat.statutTaxon("absent")?.texte)
        assertNull(v["texte"])
        assertEquals("Merle", etat.texte("texte"))
        assertTrue("texte" in etat.champsInvalides(), "un texte sans taxon retenu bloque")
        assertNull(v["long"], "seul un Int est un cd_nom")
    }

    @Test
    fun taxon_choisi_dans_la_liste_est_fige_tant_que_le_texte_ne_change_pas() = runTest {
        taxRef.vider()
        taxRef.remplacerTout(mapOf(
            TaxRefCache.normaliser("Souci") to TaxRefEntry(190178, "Calendula", "Souci"),
            TaxRefCache.normaliser("Colias crocea") to TaxRefEntry(641941, "Colias crocea", null),
        ))
        val etat = formulaire(EditableField("esp", ViewType.TAXON, "Espèce"))
        // La proposition porte SON taxon : deux taxons partagent le nom « Souci ».
        etat.choisirPropositionTaxon("esp", PropositionTaxon("Souci", 641941, "Colias crocea"))
        assertEquals(641941, etat.lireValeurs()["esp"])
        etat.saisirTexteTaxon("esp", "Souci")
        assertEquals(641941, etat.lireValeurs()["esp"], "texte inchangé : le choix tient")
        etat.saisirTexteTaxon("esp", "Souci ")
        assertEquals(190178, etat.lireValeurs()["esp"], "texte changé : résolution du nom")
    }

    @Test
    fun taxon_refuse_a_la_sortie_du_champ() = runTest {
        taxRef.vider()
        val etat = formulaire(
            EditableField("esp", ViewType.TAXON, "Espèce", idListeTaxonomieRestreinte = 109),
            EditableField("libre", ViewType.TAXON, "Libre"),
        )
        etat.saisirTexteTaxon("esp", "Bidule")
        assertNull(etat.erreurTaxon("esp"), "pendant la frappe, rien")
        etat.quitterChampTaxon("esp")
        assertEquals("Espèce non reconnue dans la liste du protocole — choisissez une suggestion", etat.erreurTaxon("esp"))
        assertEquals("✗ Espèce non reconnue dans la liste du protocole — choisissez une proposition", etat.statutTaxon("esp")?.texte)
        etat.saisirTexteTaxon("libre", "Bidule")
        etat.quitterChampTaxon("libre")
        assertEquals("Espèce non reconnue — choisissez une suggestion", etat.erreurTaxon("libre"))
    }

    @Test
    fun liste_absente_du_cache_annoncee_sous_le_champ() = runTest {
        taxRef.vider()
        val etat = FormulaireEtat(
            listOf(EditableField("esp", ViewType.TAXON, "Espèce", idListeTaxonomieRestreinte = 109)),
            taxRef, backgroundScope, synchroActive = { false },
        )
        assertEquals(FormulaireEtat.INDICATION_TAXON, etat.indicationTaxon("esp"))
        // Les propositions se calculent hors du fil de l'interface.
        etat.revision.first { etat.indicationTaxon("esp") != FormulaireEtat.INDICATION_TAXON }
        assertEquals("Pas de données — rechargez les données", etat.statutTaxon("esp")?.texte)
        assertEquals("Pas de données — rechargez les données", etat.indicationTaxon("esp"))
        assertTrue(etat.propositionsTaxon("esp", "so").isEmpty())
    }

    @Test
    fun option_unique_masquee_ne_part_que_si_elle_est_requise() = runTest {
        // EditableField.masque : un jeu de données à option unique, choisi d'office et masqué. Comme
        // sous Android, un champ masqué ne part que s'il est requis ou posé par une règle.
        val etat = formulaire(
            EditableField("id_dataset", ViewType.SELECT, "JDD", value = "12", values = options("12"), masque = true),
            EditableField("observers", ViewType.SELECT, "Obs", value = "7", values = options("7"), masque = true, obligatoire = true),
        )
        assertEquals("12", etat.lireValeurs()["id_dataset"], "la valeur est lue")
        assertFalse(etat.estVisible("id_dataset"))
        val envoi = etat.lireValeursPourEnvoi()
        assertFalse("id_dataset" in envoi)
        assertEquals("7", envoi["observers"])
    }

    @Test
    fun champ_pose_par_une_regle_part_meme_masque() = runTest {
        val etat = formulaire(
            EditableField("mode", ViewType.TEXT, "Mode", value = "auto"),
            EditableField("calcule", ViewType.TEXT, "Calculé", hiddenExpr = "true"),
        )
        etat.setReglesChange(listOf(
            "({objForm, meta}) => {",
            "objForm.patchValue({calcule: 'valeur du moteur'})",
            "}",
        ))
        assertFalse(etat.estVisible("calcule"))
        assertEquals("valeur du moteur", etat.lireValeursPourEnvoi()["calcule"])
    }

    @Test
    fun une_regle_qui_change_une_liste_deroulante_relance_une_passe_differee() = runTest {
        // Sous Android, le onItemSelected d'un Spinner changé par une règle arrive APRÈS, au passage
        // de mise en page : la règle 1 voit alors le résultat de la règle 2.
        val script = listOf(
            "({objForm, meta}) => {",
            "if (objForm.value.b === 'x') {",
            "objForm.patchValue({c: 'y'})",
            "}",
            "objForm.patchValue({b: 'x'})",
            "}",
        )
        val etat = formulaire(
            EditableField("b", ViewType.SELECT, "B", values = options("x", "z")),
            EditableField("c", ViewType.TEXT, "C"),
        )
        etat.setReglesChange(script)
        assertEquals("x", etat.lireValeurs()["b"])
        assertEquals("", etat.lireValeurs()["c"], "première passe : la règle 1 a vu b vide")
        runCurrent()
        assertEquals("y", etat.lireValeurs()["c"], "passe différée de la liste déroulante")
    }

    @Test
    fun la_meme_regle_sur_un_champ_texte_n_en_relance_pas() = runTest {
        val script = listOf(
            "({objForm, meta}) => {",
            "if (objForm.value.b === 'x') {",
            "objForm.patchValue({c: 'y'})",
            "}",
            "objForm.patchValue({b: 'x'})",
            "}",
        )
        val etat = formulaire(EditableField("b", ViewType.TEXT, "B"), EditableField("c", ViewType.TEXT, "C"))
        etat.setReglesChange(script)
        runCurrent()
        assertEquals("", etat.lireValeurs()["c"], "un champ texte posé par la règle ne relance rien")
    }

    @Test
    fun une_regle_pose_une_date_telle_quelle() = runTest {
        val etat = formulaire(EditableField("d", ViewType.DATE, "D"))
        etat.setReglesChange(listOf("({objForm, meta}) => {", "objForm.patchValue({d: '2026-05-21'})", "}"))
        assertEquals("2026-05-21", etat.lireValeurs()["d"], "sans normalisation")
        assertEquals("2026-05-21", etat.affichageDateHeure("d"))
    }

    @Test
    fun en_edition_un_champ_sauvegarde_n_est_pas_ecrase_par_une_regle_gardee() = runTest {
        val flore = listOf(
            "({objForm, meta}) => {",
            "const base_site_name = 'T' + (objForm.value.num_transect) + 'Q' + (objForm.value.num_placette);",
            "if (!objForm.controls.base_site_name.dirty) {",
            "objForm.patchValue({base_site_name})",
            "}",
            "}",
        )
        val champs = arrayOf(
            EditableField("num_transect", ViewType.NUMBER, "Transect", value = 3),
            EditableField("num_placette", ViewType.NUMBER, "Placette", value = 12),
            EditableField("base_site_name", ViewType.TEXT, "Nom", value = "Mon nom"),
        )
        val creation = formulaire(*champs)
        creation.setReglesChange(flore)
        assertEquals("T3Q12", creation.lireValeurs()["base_site_name"], "création : nom calculé")
        val edition = formulaire(*champs, dirty = setOf("base_site_name"))
        edition.setReglesChange(flore)
        assertEquals("Mon nom", edition.lireValeurs()["base_site_name"], "édition : personnalisation gardée")
    }

    @Test
    fun choix_de_l_utilisateur() = runTest {
        val etat = formulaire(
            EditableField("d", ViewType.DATE, "D"),
            EditableField("h", ViewType.TIME, "H"),
            EditableField("dt", ViewType.DATETIME, "DT"),
            EditableField("sel", ViewType.SELECT, "S", values = options("1", "2")),
            EditableField("multi", ViewType.SELECT_MULTIPLE, "M", values = options("1", "2", "3")),
            EditableField("cases", ViewType.CHECKBOX_MULTIPLE, "C", values = options("1", "2")),
            EditableField("case", ViewType.CHECKBOX, "K"),
        )
        etat.choisirDate("d", 2026, 5, 1)
        etat.choisirHeure("h", 7, 3)
        etat.choisirDateHeure("dt", 2026, 5, 1, 7, 3)
        etat.choisirOption("sel", 1)
        etat.validerChoixMultiple("multi", setOf("3", "1"))
        etat.cocherCase("cases", 1, true)
        etat.cocher("case", true)
        val v = etat.lireValeurs()
        assertEquals("2026-5-1", v["d"])
        assertEquals("01/05/2026", etat.affichageDateHeure("d"))
        assertEquals("07:03", v["h"])
        assertEquals("2026-05-01 07:03:00", v["dt"])
        assertEquals("2", v["sel"])
        assertEquals(listOf("1", "3"), v["multi"], "dans l'ordre des options")
        assertEquals(listOf("2"), v["cases"])
        assertEquals(true, v["case"])
        etat.choisirOption("sel", -1)
        assertEquals("", etat.lireValeurs()["sel"], "« — choisir — »")
    }

    @Test
    fun validation_des_bornes_et_message() = runTest {
        val etat = formulaire(
            EditableField("min", ViewType.NUMBER, "Min", value = "5"),
            EditableField("max", ViewType.NUMBER, "Max", value = "3", minValue = "({value}) => value.min"),
            EditableField("pct", ViewType.NUMBER, "Pct", value = "150", maxValue = "100.5", decimal = true),
        )
        assertEquals(listOf("max", "pct"), etat.champsInvalides())
        assertEquals("Valeur ≥ 5 attendue", etat.erreurValidation("max"))
        assertEquals("Valeur ≤ 100.5 attendue", etat.erreurValidation("pct"))
        etat.saisirTexte("max", "8")
        etat.flushChangementsEnAttente()
        assertNull(etat.erreurValidation("max"))
        assertEquals(listOf("pct"), etat.champsInvalides())
    }

    @Test
    fun required_dynamique_et_asterisque() = runTest {
        val etat = formulaire(
            EditableField("num_passage", ViewType.TEXT, "Passage", value = "1"),
            EditableField("veg", ViewType.TEXT, "Végétation", obligatoireExpr = "({value}) => value.num_passage == 2"),
        )
        assertFalse(etat.estRequis("veg"))
        assertTrue(etat.champsObligatoiresManquants().isEmpty())
        etat.saisirTexte("num_passage", "2")
        etat.flushChangementsEnAttente()
        assertTrue(etat.estRequis("veg"))
        assertEquals(listOf("veg"), etat.champsObligatoiresManquants())
    }
}
