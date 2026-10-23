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

// Les dictées sont celles relevées sur le terrain (« psychorame » pour « Tichodrome », « rouge-gorge »,
// 23 octobre 2026) et celles que commente GeoMys-IOS (PhonetiqueFr.swift, PropositionsTaxon.swift). Les
// propositions sont des oiseaux de l'Ariège, avec des cd_nom d'essai.

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.store.SuggestionTaxon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [PhonetiqueFr] et [IndexPhonetique] : ce que la dictée a entendu, rapproché des espèces proposées. */
class IndexPhonetiqueTest {

    private val oiseaux = OISEAUX.split(";").mapIndexed { i, nom -> SuggestionTaxon(nom, 1000 + i) }
    private val index = IndexPhonetique(oiseaux)

    private fun cdNom(nom: String) = oiseaux.single { it.nom == nom }.cdNom

    private fun premiers(dictee: String) = index.candidats(listOf(dictee)).map { it.nom }

    @Test
    fun les_orthographes_de_la_dictee_ont_la_cle_du_nom() {
        assertEquals(PhonetiqueFr.cle("Pic épeiche"), PhonetiqueFr.cle("pic et pêche"))
        assertEquals(PhonetiqueFr.cle("Rouge-gorge"), PhonetiqueFr.cle("rouge gorge"))
        assertEquals(PhonetiqueFr.cle("Bruant jaune"), PhonetiqueFr.cle("bruand jaune"))
        assertEquals(PhonetiqueFr.cle("Mésange charbonnière"), PhonetiqueFr.cle("mézange charbonière"))
        // Ajouts du portage : la ligature et la cédille.
        assertEquals(PhonetiqueFr.cle("Héron garde-bœufs"), PhonetiqueFr.cle("héron garde boeufs"))
        assertEquals(PhonetiqueFr.cle("Pinson des arbres"), PhonetiqueFr.cle("pinçon des arbres"))
        assertEquals("bekas", PhonetiqueFr.son("becasse"))
        assertEquals(listOf("pik", "epex"), PhonetiqueFr.motsSignificatifs("Pic épeiche (Le)"))
    }

    @Test
    fun le_meme_son_resout_et_l_ambiguite_ne_resout_rien() {
        assertEquals(cdNom("Pic épeiche"), index.memeSon("pic et pêche", 0)?.cdNom)
        assertNull(index.memeSon("pouillot vélos", 0), "un son de travers : pas à tolérance 0")
        assertEquals(cdNom("Pouillot véloce"), index.memeSon("pouillot vélos", IndexPhonetique.seuilSon("pouillot vélos"))?.cdNom)
        val homophones = IndexPhonetique(listOf(SuggestionTaxon("Grive draine", 1), SuggestionTaxon("Grive drène", 2)))
        assertNull(homophones.memeSon("grive draine", 0), "deux taxons au même son : rien")
        val memeTaxon = IndexPhonetique(listOf(SuggestionTaxon("Grive draine", 1), SuggestionTaxon("Grive drène", 1)))
        assertEquals(1, memeTaxon.memeSon("grive draine", 0)?.cdNom, "deux noms du même taxon ne sont pas une ambiguïté")
        assertNull(index.memeSon("pi", 0), "trop court pour rien désigner")
    }

    @Test
    fun ce_qui_a_ete_mal_entendu_amene_l_espece_en_tete() {
        assertEquals("Tichodrome échelette", premiers("psychorame").first())
        assertEquals("Rougegorge familier", premiers("rouge-gorge").first())
        assertEquals("Pic épeiche", premiers("pic et pêche").first())
        assertEquals("Pouillot véloce", premiers("pouillot vélos").first())
        assertEquals("Fauvette mélanocéphale", premiers("fauvettes mélano acéphale").first())
        assertEquals("Rougequeue noir", premiers("rouge queue noir").first())
        assertEquals("Grand-duc d'Europe", premiers("grand duc").first())
        assertEquals("Sittelle torchepot", premiers("sitèle").first())
    }

    @Test
    fun un_nom_dit_a_moitie_amene_les_especes_qui_le_portent() {
        // « Huppe » ne fait que deux sons : il ne ressemble qu'à lui-même, mais le dire suffit.
        val huppe = premiers("huppe")
        assertEquals("Huppe fasciée", huppe.first())
        assertTrue("Vanneau huppé" in huppe, "$huppe")
        val mesanges = premiers("mésange charbonière")
        assertEquals("Mésange charbonnière", mesanges.first())
        assertTrue("Mésange bleue" in mesanges, "$mesanges")
        assertTrue(premiers("vautour").containsAll(listOf("Vautour fauve", "Vautour percnoptère")))
    }

    @Test
    fun les_hypotheses_se_completent_et_chaque_taxon_ne_vient_qu_une_fois() {
        val rougegorge = cdNom("Rougegorge familier")
        val avecSynonyme = IndexPhonetique(oiseaux + SuggestionTaxon("Rouge-gorge", rougegorge))
        val proches = avecSynonyme.candidats(listOf("rouge gorge", "rouge-gorge", "rougegorge"))
        assertEquals(1, proches.count { it.cdNom == rougegorge }, "$proches")
        assertEquals(rougegorge, proches.first().cdNom)
        // La première hypothèse ne ressemble à rien, la seconde amène l'espèce.
        assertEquals("Tichodrome échelette", index.candidats(listOf("qwxz", "tichodrome")).first().nom)
    }

    @Test
    fun au_plus_cinq_et_rien_pour_ce_qui_ne_ressemble_a_rien() {
        assertEquals(MAX_CANDIDATS_VOCAUX, index.candidats(listOf("pic")).size)
        assertEquals(emptyList(), index.candidats(listOf("")))
        assertEquals(emptyList(), index.candidats(listOf("a")))
        assertEquals(emptyList(), IndexPhonetique(emptyList()).candidats(listOf("psychorame")))
    }

    private companion object {
        const val OISEAUX = "Tichodrome échelette;Rougegorge familier;Pic épeiche;Pic épeichette;Pic mar;Pic noir;Pic vert;" +
            "Pic à dos blanc;Pouillot véloce;Pouillot de Bonelli;Pouillot fitis;Pouillot siffleur;Fauvette à tête noire;" +
            "Fauvette des jardins;Fauvette grisette;Fauvette mélanocéphale;Fauvette pitchou;Fauvette babillarde;" +
            "Mésange charbonnière;Mésange bleue;Mésange nonnette;Mésange huppée;Mésange noire;Mésange boréale;" +
            "Mésange à longue queue;Merle noir;Merle à plastron;Grive musicienne;Grive draine;Grive mauvis;Grive litorne;" +
            "Rougequeue noir;Rougequeue à front blanc;Rossignol philomèle;Troglodyte mignon;Accenteur mouchet;" +
            "Accenteur alpin;Roitelet huppé;Roitelet à triple bandeau;Grimpereau des jardins;Grimpereau des bois;" +
            "Sittelle torchepot;Pinson des arbres;Pinson du Nord;Verdier d'Europe;Chardonneret élégant;Tarin des aulnes;" +
            "Linotte mélodieuse;Serin cini;Bouvreuil pivoine;Grosbec casse-noyaux;Bec-croisé des sapins;" +
            "Venturon montagnard;Niverolle alpine;Bruant jaune;Bruant zizi;Bruant fou;Bruant ortolan;Bruant proyer;" +
            "Moineau domestique;Moineau friquet;Étourneau sansonnet;Loriot d'Europe;Geai des chênes;Pie bavarde;" +
            "Choucas des tours;Corneille noire;Grand Corbeau;Crave à bec rouge;Chocard à bec jaune;Hirondelle rustique;" +
            "Hirondelle de fenêtre;Hirondelle de rochers;Martinet noir;Martinet à ventre blanc;Alouette des champs;" +
            "Alouette lulu;Pipit des arbres;Pipit farlouse;Pipit spioncelle;Bergeronnette grise;" +
            "Bergeronnette des ruisseaux;Bergeronnette printanière;Cincle plongeur;Martin-pêcheur d'Europe;" +
            "Huppe fasciée;Guêpier d'Europe;Coucou gris;Pigeon ramier;Pigeon biset;Tourterelle turque;" +
            "Tourterelle des bois;Chouette hulotte;Chouette effraie;Chevêche d'Athéna;Chevêchette d'Europe;" +
            "Hibou moyen-duc;Grand-duc d'Europe;Petit-duc scops;Engoulevent d'Europe;Buse variable;Milan noir;" +
            "Milan royal;Épervier d'Europe;Autour des palombes;Faucon crécerelle;Faucon pèlerin;Faucon hobereau;" +
            "Aigle royal;Circaète Jean-le-Blanc;Gypaète barbu;Vautour fauve;Vautour percnoptère;Bondrée apivore;" +
            "Busard Saint-Martin;Busard cendré;Héron cendré;Aigrette garzette;Héron garde-bœufs;Cigogne blanche;" +
            "Cigogne noire;Grand Cormoran;Grèbe castagneux;Grèbe huppé;Canard colvert;Sarcelle d'hiver;" +
            "Foulque macroule;Gallinule poule-d'eau;Râle d'eau;Mouette rieuse;Goéland leucophée;Sterne pierregarin;" +
            "Chevalier guignette;Bécasse des bois;Bécassine des marais;Vanneau huppé;Pluvier doré;Perdrix rouge;" +
            "Perdrix grise;Caille des blés;Faisan de Colchide;Lagopède alpin;Tétras lyre;Grand Tétras;" +
            "Gélinotte des bois;Pie-grièche écorcheur;Pie-grièche grise;Pie-grièche à tête rousse;Gobemouche gris;" +
            "Gobemouche noir;Tarier pâtre;Tarier des prés;Traquet motteux;Monticole bleu;Monticole de roche;" +
            "Rousserolle effarvatte;Rousserolle turdoïde;Hypolaïs polyglotte;Locustelle tachetée;Bouscarle de Cetti;" +
            "Cisticole des joncs;Panure à moustaches;Rémiz penduline;Torcol fourmilier;Bihoreau gris;Butor étoilé;" +
            "Blongios nain;Martinet pâle;Jaseur boréal;Sizerin flammé;Ibis falcinelle;Spatule blanche;Flamant rose;" +
            "Échasse blanche;Avocette élégante;Courlis cendré;Barge à queue noire;Combattant varié;" +
            "Gravelot à collier interrompu;Petit Gravelot;Grand Gravelot;Cormoran huppé;Harle bièvre;" +
            "Fuligule milouin;Fuligule morillon;Garrot à oeil d'or;Oie cendrée;Cygne tuberculé;Tadorne de Belon"
    }
}
