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

// Propre au portage : le contrat des valeurs du FormulaireRenderer de GeoMys-Android @ cdd5196
// (lireValeurs, valeurs initiales des creerChamp*, appliquerValeur, lireValeursPourEnvoi), champ par
// champ. Les valeurs attendues sont celles que rend le code Android, lu ligne à ligne.

package fr.ariegenature.geomys.monitoring.form

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.monitoring.NouvelleVisite
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
        assertEquals("9", v["selInconnu"], "une valeur hors des options reste, comme sur le web")
        assertEquals("2", v["selInt"], "une valeur numérique désigne son option")
        assertEquals("2", v["radio"], "un RADIO lit toString() de la valeur")
        assertEquals("", v["radioVide"])
        assertEquals(listOf("2", "9"), v["multi"], "liste reprise telle quelle")
        assertEquals(listOf("1"), v["multiStr"])
        assertNull(v["multiVide"], "liste vide → null")
        assertEquals(listOf("1", "2"), v["cases"], "cases dans l'ordre des options, valeurs inconnues écartées")
        assertNull(v["casesVides"])
    }

    @Test
    fun les_vides_envoyes_sont_ceux_du_web() = runTest {
        val etat = formulaire(
            EditableField("jamais", ViewType.TEXT, "A"),
            EditableField("vide", ViewType.TEXT, "B", value = "x"),
            EditableField("espaces", ViewType.TEXT, "C"),
            EditableField("liste", ViewType.SELECT_MULTIPLE, "D", value = listOf("1"), values = options("1", "2")),
            EditableField("listeJamais", ViewType.SELECT_MULTIPLE, "E", values = options("1", "2")),
            EditableField("choix", ViewType.SELECT, "F", values = options("1", "2")),
            EditableField("heure", ViewType.TIME, "G", value = "08:00"),
        )
        etat.saisirTexte("vide", "")
        etat.saisirTexte("espaces", "  a ")
        etat.validerChoixMultiple("liste", emptySet())
        etat.effacerDateHeure("heure")
        etat.flushChangementsEnAttente()
        val v = etat.lireValeursPourEnvoi()
        assertNull(v["jamais"], "jamais touché : null")
        assertEquals("", v["vide"], "vidé : \"\"")
        assertEquals("  a ", v["espaces"], "sans retirer d'espaces")
        assertEquals(emptyList<Any?>(), v["liste"], "liste vidée : []")
        assertNull(v["listeJamais"])
        assertNull(v["choix"], "un choix vide : null")
        assertEquals("", v["heure"], "heure vidée : \"\"")
    }

    @Test
    fun motif_et_longueur_max_du_web() = runTest {
        val etat = formulaire(
            EditableField("code", ViewType.TEXT, "Code", motif = "[A-Z]{2}[0-9]+", messageMotif = "Deux lettres puis des chiffres"),
            EditableField("court", ViewType.TEXTAREA, "Court", longueurMax = 3),
            EditableField("multi", ViewType.SELECT_MULTIPLE, "Multi", values = options("1", "2", "3"), longueurMax = 2),
        )
        assertEquals(emptyList(), etat.champsInvalides(), "vides : valides")
        etat.saisirTexte("code", "ab12")
        etat.saisirTexte("court", "abcd")
        etat.validerChoixMultiple("multi", setOf("1", "2", "3"))
        etat.flushChangementsEnAttente()
        assertEquals(listOf("code", "court", "multi"), etat.champsInvalides())
        assertEquals("Deux lettres puis des chiffres", etat.erreurValidation("code"))
        etat.saisirTexte("code", "AB12")
        etat.saisirTexte("court", "abc")
        etat.validerChoixMultiple("multi", setOf("1"))
        etat.flushChangementsEnAttente()
        assertEquals(emptyList(), etat.champsInvalides())
        assertNull(etat.erreurValidation("code"))
    }

    @Test
    fun un_select_garde_sa_valeur_hors_liste_grisee() = runTest {
        val etat = formulaire(EditableField("sel", ViewType.SELECT, "A", value = "9", values = options("1", "2"), selectSimple = true))
        assertEquals("9", etat.valeurHorsListe("sel"))
        etat.choisirOption("sel", 0)
        assertEquals("9", etat.lireValeursPourEnvoi()["sel"], "grisé : on ne le change pas")
    }

    @Test
    fun case_a_cocher_a_trois_etats() = runTest {
        // Comme le contrôle du formulaire web : null tant que rien n'y est posé, false une fois
        // décochée ou posée à faux.
        val etat = formulaire(
            EditableField("b", ViewType.CHECKBOX, "A", value = true),
            EditableField("s", ViewType.CHECKBOX, "B", value = "1"),
            EditableField("f", ViewType.CHECKBOX, "C", value = "false"),
            EditableField("z", ViewType.CHECKBOX, "D", value = 0),
            EditableField("n", ViewType.CHECKBOX, "E"),
            EditableField("t", ViewType.CHECKBOX, "F"),
        )
        etat.cocher("t", true)
        etat.cocher("t", false)
        val v = etat.lireValeurs()
        assertEquals(true, v["b"])
        assertEquals(true, v["s"])
        assertEquals(false, v["f"], "posée à faux")
        assertEquals(false, v["z"])
        assertNull(v["n"], "jamais touchée")
        assertEquals(false, v["t"], "cochée puis décochée")
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
    fun seule_une_information_de_fin_obligatoire_met_la_visite_a_completer() = runTest {
        // 22 octobre : « si l'heure de fin n'est pas obligatoire, ne pas enregistrer la visite à
        // compléter », et de même pour les autres informations de fin. L'heure de fin et la durée
        // facultatives de chronoventaire, et l'heure de fin des POP, requise seulement quand le site
        // est accessible.
        val etat = formulaire(
            EditableField("time_end", ViewType.TIME, "Heure de fin du relevé"),
            EditableField("duration", ViewType.TIME, "Durée de la détermination"),
            EditableField("accessibility", ViewType.RADIO, "Accessibilité", value = "Non", values = options("Oui", "Non")),
            EditableField("Heure_fin", ViewType.TIME, "Heure de fin", obligatoireExpr = "({value}) => value.accessibility === 'Oui'"),
        )
        assertEquals(emptyList(), NouvelleVisite.manquantsACompleter(etat.champsObligatoiresManquants(), etat.champs), "aucune information de fin requise : pas « à compléter »")
        etat.choisirRadio("accessibility", 0)
        runCurrent()
        assertEquals(listOf("Heure_fin"), NouvelleVisite.manquantsACompleter(etat.champsObligatoiresManquants(), etat.champs))
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
    fun jeu_de_donnees_unique_masque_par_meta_dataset_mais_envoye() = runTest {
        // Le web masque le jeu de données quand il n'y en a qu'un (`meta.dataset`) ; requis et choisi
        // d'office, il part quand même, comme tout champ masqué.
        val expr = "({meta}) => meta.dataset && Object.keys(meta.dataset).length == 1"
        val unique = formulaire(
            EditableField("id_dataset", ViewType.SELECT, "JDD", value = "12", values = options("12"), obligatoire = true, hiddenExpr = expr),
            EditableField("note", ViewType.TEXT, "Note", value = "x", hiddenExpr = expr),
        )
        assertEquals("12", unique.lireValeurs()["id_dataset"], "la valeur est lue")
        assertFalse(unique.estVisible("id_dataset"))
        val envoi = unique.lireValeursPourEnvoi()
        assertEquals("12", envoi["id_dataset"])
        assertEquals("x", envoi["note"])
        val deux = formulaire(
            EditableField("id_dataset", ViewType.SELECT, "JDD", values = options("12", "13"), obligatoire = true, hiddenExpr = expr),
        )
        assertTrue(deux.estVisible("id_dataset"))
    }

    @Test
    fun une_option_part_avec_le_type_json_de_sa_valeur() = runTest {
        // Le web envoie la valeur de l'option telle que le schéma ou la liste la donne : « 1 » reste
        // un texte, l'id d'une nomenclature reste un nombre.
        val v = formulaire(
            EditableField("nb_adulte", ViewType.SELECT, "Adultes", value = "1", values = listOf(PropertyValue("1", "1", brut = "1"))),
            EditableField("id_nomenclature_sexe", ViewType.RADIO, "Sexe", value = "171", values = listOf(PropertyValue("171", "Femelle", brut = 171))),
            EditableField(
                "methodes", ViewType.SELECT_MULTIPLE, "Méthodes", value = listOf("2", "3"),
                values = listOf(PropertyValue("2", "Vue", brut = 2), PropertyValue("3", "Ouïe", brut = "3")),
            ),
            EditableField("hors_liste", ViewType.SELECT, "Hors liste", value = "9", values = listOf(PropertyValue("9", "9"))),
        ).lireValeursPourEnvoi()
        assertEquals("1", v["nb_adulte"])
        assertEquals(171, v["id_nomenclature_sexe"])
        assertEquals(listOf(2, "3"), v["methodes"])
        assertEquals("9", v["hors_liste"], "une option connue par son seul texte reste un texte")
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
    fun une_regle_pose_un_taxon_qui_part_meme_masque() = runTest {
        // POPAmphibien : « rien vu » pose le taxon Amphibiens dans le champ espèce, alors masqué. La
        // règle était sans effet sur un champ espèce : l'observation partait sans cd_nom.
        taxRef.vider()
        taxRef.remplacerTout(mapOf(TaxRefCache.normaliser("Amphibiens, batraciens") to TaxRefEntry(914450, "Amphibia", "Amphibiens, batraciens")))
        val etat = formulaire(
            EditableField("presence", ViewType.RADIO, "Présence", value = "Oui", values = options("Oui", "Non")),
            EditableField(
                "cd_nom", ViewType.TAXON, "Espèce",
                hiddenExpr = "({value}) => value.presence === 'Non'", obligatoireExpr = "({value}) => value.presence === 'Oui'",
            ),
            EditableField("count_min", ViewType.NUMBER, "Nombre minimum"),
        )
        etat.setReglesChange(listOf(
            "({objForm, meta}) => {",
            "if (objForm.value.presence === 'Non') {",
            "objForm.patchValue({count_min : 0, cd_nom : {'cd_nom': 914450, 'lb_nom': 'Amphibia', 'nom_vern' : 'Amphibiens, batraciens'}}, {emitEvent : false})",
            "}",
            "}",
        ))
        assertNull(etat.lireValeurs()["cd_nom"])
        etat.choisirRadio("presence", 1)
        runCurrent()
        assertFalse(etat.estVisible("cd_nom"))
        assertEquals("Amphibiens, batraciens", etat.texte("cd_nom"))
        val envoi = etat.lireValeursPourEnvoi()
        assertEquals(914450, envoi["cd_nom"])
        assertEquals(0, envoi["count_min"])
        assertTrue(etat.champsInvalides().isEmpty())
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
