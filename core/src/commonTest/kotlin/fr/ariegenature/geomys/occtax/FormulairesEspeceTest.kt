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

// Propre au portage : ces règles vivaient dans les écrans et le renderer de GeoMys-Android @ cdd5196, sans
// test. Les attendus sont ceux de leur code.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.store.GroupeTaxonomique
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.store.OcctaxFieldsConfig
import fr.ariegenature.geomys.store.TaxrefRestriction
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [FormulairesEspece] : entrées des nomenclatures, caractérisation, effectifs. */
class FormulairesEspeceTest {

    private val t = ServicesDeTest()
    private val nomenclatures get() = t.services.nomenclatures

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private val methObs = OcctaxFieldsConfig.parCode.getValue("METH_OBS")
    private val oiseau = GroupeTaxonomique("Animalia", "Oiseaux", null)
    private val tous = listOf(TaxrefRestriction("all", "all"))

    @Test
    fun sans_nomenclature_en_cache_aucune_valeur_n_est_inventee() {
        val champ = FormulairesEspece.champ(methObs, nomenclatures, oiseau, filtresActifs = true)
        assertEquals(listOf(FormulairesEspece.VIDE), champ.libelles)
        assertEquals(listOf(""), champ.codes)
        assertTrue(champ.sansValeurs)
        // Champ requis du web : l'entrée vide n'est pas une réponse, et le message invite à recharger.
        assertEquals(
            "« Technique d'observation » : liste vide, rechargez les données",
            FormulairesEspece.manque(listOf(champ), mapOf("METH_OBS" to "")),
        )
    }

    @Test
    fun toutes_les_valeurs_du_serveur_meme_de_meme_libelle_et_l_entree_vide_a_part() {
        nomenclatures.setAll(
            mapOf(
                "METH_OBS" to listOf(
                    NomValeur(41, "Vu", tous),
                    NomValeur(42, "Écouté", tous),
                    NomValeur(43, "Non renseigné", tous),
                    NomValeur(44, "Vu", tous),
                    NomValeur(45, "Piégé", listOf(TaxrefRestriction("Animalia", "Insectes"))),
                ),
            ),
        )
        nomenclatures.setDefauts(mapOf("METH_OBS" to "41"))
        val champ = FormulairesEspece.champ(methObs, nomenclatures, oiseau, filtresActifs = true)
        // La vraie « Non renseigné » du serveur reste proposée, distincte de l'entrée vide.
        assertEquals(listOf(FormulairesEspece.VIDE, "Écouté", "Non renseigné", "Vu", "Vu"), champ.libelles)
        assertEquals(listOf("", "42", "43", "41", "44"), champ.codes)
        assertTrue(champ.obligatoire)
        // Sans valeur portée, le défaut du serveur.
        assertEquals(3, FormulairesEspece.positionInitiale(champ, "", nomenclatures))
        assertEquals(1, FormulairesEspece.positionInitiale(champ, "42", nomenclatures))
        assertEquals("Veuillez indiquer la technique d'observation", FormulairesEspece.manque(listOf(champ), mapOf("METH_OBS" to "")))
        assertNull(FormulairesEspece.manque(listOf(champ), mapOf("METH_OBS" to "43")))
    }

    @Test
    fun les_listes_du_web_affichent_label_fr_triees_par_label_default() {
        nomenclatures.setAll(
            mapOf(
                "STATUT_OBS" to listOf(
                    NomValeur(1, "Présent", emptyList(), "Pr", labelFr = "Présent (fr)"),
                    NomValeur(2, "Absent", emptyList(), "No", labelFr = "Zéro absent"),
                ),
                "METH_OBS" to listOf(NomValeur(41, "Vu", listOf(TaxrefRestriction("Plantae", "all")))),
            ),
        )
        // Statut d'observation : pas de filtre par taxon sur le web (aucune ligne taxonomique ici).
        val statut = FormulairesEspece.champ(OcctaxFieldsConfig.parCode.getValue("STATUT_OBS"), nomenclatures, oiseau, filtresActifs = true)
        assertEquals(listOf(FormulairesEspece.VIDE, "Zéro absent", "Présent (fr)"), statut.libelles, "label_fr, dans l'ordre de label_default")
        // La technique d'observation, elle, suit le taxon.
        assertEquals(listOf(FormulairesEspece.VIDE), FormulairesEspece.champ(methObs, nomenclatures, oiseau, filtresActifs = true).libelles)
    }

    @Test
    fun les_effectifs_suivent_les_controles_du_web() {
        assertNull(FormulairesEspece.refusEffectifs("0", "0"))
        assertNull(FormulairesEspece.refusEffectifs("2", "5"))
        assertEquals("Renseignez le nombre min", FormulairesEspece.refusEffectifs("", "5"))
        assertEquals("Renseignez le nombre max", FormulairesEspece.refusEffectifs("2", ""))
        assertEquals("Nombre entier attendu", FormulairesEspece.refusEffectifs("-1", "5"))
        assertEquals("Le dénombrement maximum ne peut pas être inférieur au dénombrement minimum", FormulairesEspece.refusEffectifs("5", "2"))
    }

    @Test
    fun une_valeur_hors_de_la_liste_du_taxon_est_gardee_tant_qu_on_n_y_touche_pas() {
        nomenclatures.setAll(mapOf("METH_OBS" to listOf(NomValeur(41, "Vu", tous), NomValeur(42, "Écouté", tous))))
        val champ = FormulairesEspece.champ(methObs, nomenclatures, oiseau, filtresActifs = true)
        val initiale = FormulairesEspece.positionInitiale(champ, "99", nomenclatures)
        assertEquals(0, initiale, "affichée vide")
        assertEquals("99", FormulairesEspece.valeurRetenue(champ, "99", "41", initiale, initiale))
        assertEquals("42", FormulairesEspece.valeurRetenue(champ, "99", "41", initiale, 1))
        // Sans valeur portée, le défaut du serveur, même absent des valeurs proposées (comme le web).
        assertEquals("41", FormulairesEspece.valeurRetenue(champ, "", "41", 2, 2))
        assertEquals("77", FormulairesEspece.valeurRetenue(champ, "", "77", 0, 0))
        // L'entrée vide choisie vide la valeur.
        assertEquals("", FormulairesEspece.valeurRetenue(champ, "41", "41", 2, 0))
    }

    @Test
    fun le_filtre_taxonomique_du_serveur() {
        // pypnnomenclature : une ligne doit vérifier à la fois le règne, le groupe 2 et le groupe 3 ;
        // une valeur sans ligne est exclue.
        nomenclatures.setAll(
            mapOf(
                "SEXE" to listOf(
                    NomValeur(1, "Inconnu", tous),
                    NomValeur(2, "Femelle", listOf(TaxrefRestriction("Animalia", "Mollusques", "Gastéropodes"))),
                    NomValeur(3, "Hermaphrodite", listOf(TaxrefRestriction("Animalia", "Mollusques", "Bivalves"))),
                    NomValeur(4, "Mâle", listOf(TaxrefRestriction("Animalia", "Oiseaux"))),
                    NomValeur(5, "Thalle protothalle"),
                ),
            ),
        )
        fun libelles(g: GroupeTaxonomique, actifs: Boolean = true) = nomenclatures.filtrerPourTaxon("SEXE", g, actifs).map { it.label }
        val escargot = GroupeTaxonomique("Animalia", "Mollusques", "Gastéropodes")
        assertEquals(listOf("Inconnu", "Femelle"), libelles(escargot))
        assertEquals(listOf("Inconnu", "Mâle"), libelles(GroupeTaxonomique("Animalia", "Oiseaux", null)))
        // Groupe 3 inconnu : pas de filtre sur lui.
        assertEquals(listOf("Inconnu", "Femelle", "Hermaphrodite"), libelles(GroupeTaxonomique("Animalia", "Mollusques", null)))
        // Sans règne, ou filtres désactivés sur le serveur : toutes les valeurs.
        assertEquals(5, libelles(GroupeTaxonomique.AUCUN).size)
        assertEquals(5, libelles(escargot, actifs = false).size)
    }

    @Test
    fun la_caracterisation_ne_reecrit_que_ce_qui_est_affiche() {
        val espece = EspeceReleve(Taxon.OISEAU, "Merle noir", 4117, techniqueObs = "1", naturalite = "3", champsOccExtra = mapOf("blurring" to "7"))
        val complete = FormulairesEspece.appliquerCaracterisation(
            espece,
            nomenclatures = mapOf("METH_OBS" to "2", "STATUT_SOURCE" to "9", "DEE_FLOU" to ""),
            determinateur = "Alice",
            notes = null,
            preuves = mapOf("digital_proof" to " https://ex.org/p.jpg "),
            additionnels = mapOf("a" to "1"),
        )
        assertEquals("2", complete.techniqueObs)
        assertEquals("3", complete.naturalite, "champ non affiché : valeur gardée")
        assertEquals("Alice", complete.determinateur)
        assertEquals(mapOf("source_status" to "9", "digital_proof" to "https://ex.org/p.jpg"), complete.champsOccExtra)
        assertEquals(mapOf("a" to "1"), complete.additionalFieldsOccurrence)
        // Comportement seul : ni les champs additionnels ni les champs extra.
        val comportement = FormulairesEspece.appliquerCaracterisation(espece, mapOf("OCC_COMPORTEMENT" to "12"), null, null, emptyMap(), null)
        assertEquals("12", comportement.comportement)
        assertEquals(espece.champsOccExtra, comportement.champsOccExtra)
    }

    @Test
    fun le_max_suit_le_min_tant_qu_il_le_suivait() {
        assertEquals("3", FormulairesEspece.maxApresMin("2", "3", "2"))
        assertEquals("3", FormulairesEspece.maxApresMin("2", "3", ""))
        assertEquals("10", FormulairesEspece.maxApresMin("2", "3", "10"), "borne saisie volontairement")
        assertEquals("1", FormulairesEspece.ajuster("1", -1, 1), "jamais sous le plancher")
        assertEquals("5", FormulairesEspece.ajuster("x", +1, 4))
        assertEquals(3, FormulairesEspece.plancherMax("3"))
        assertEquals(1 to 1, FormulairesEspece.effectifs("", "0"))
        // Effectif 0, comme le web (« Non observé ») : plancher à 0.
        assertEquals("0", FormulairesEspece.ajuster("1", -1, FormulairesEspece.PLANCHER_MIN))
        assertEquals(0 to 0, FormulairesEspece.effectifs("0", ""))
        assertEquals(0, FormulairesEspece.plancherMax("0"))
        assertEquals(4 to 6, FormulairesEspece.effectifs("4", "6"))
        assertEquals(4 to 4, FormulairesEspece.effectifs("4", "2"))
    }

    @Test
    fun un_denombrement_garde_les_nomenclatures_non_affichees() {
        val d = Denombrement(nombreMin = 1, nombreMax = 1, sexe = "M", stadeVie = "A")
        val relu = FormulairesEspece.appliquerDenombrement(d, "2", "", mapOf("SEXE" to "", "OBJ_DENBR" to "IND"), mapOf("x" to "y"))
        assertEquals(2, relu.nombreMin)
        assertEquals(2, relu.nombreMax)
        assertNull(relu.sexe, "valeur vide : plus de sexe")
        assertEquals("A", relu.stadeVie, "champ non affiché : valeur gardée")
        assertEquals("IND", relu.objDenbr)
        assertEquals(mapOf("x" to "y"), relu.additionalFields)
    }
}
