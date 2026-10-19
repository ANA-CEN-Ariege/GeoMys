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
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.store.OcctaxFieldsConfig
import fr.ariegenature.geomys.store.TaxrefRestriction
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [FormulairesEspece] : entrées des nomenclatures, caractérisation, effectifs. */
class FormulairesEspeceTest {

    private val t = ServicesDeTest()
    private val nomenclatures get() = t.services.nomenclatures

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private val methObs = OcctaxFieldsConfig.parCode.getValue("METH_OBS")

    @Test
    fun sans_nomenclature_le_registre_sert_de_repli() {
        val champ = FormulairesEspece.champ(methObs, nomenclatures, setOf("Oiseaux"), "Animalia")
        assertEquals("Non renseignée", champ.libelles.first())
        assertEquals("", champ.codes.first())
        // Tri alphabétique français, l'entrée vide restant en tête.
        assertEquals(listOf("Non renseignée", "Chant", "Entendu", "Indices de présence", "Vu", "Vu et entendu"), champ.libelles)
        assertEquals("4", champ.code(champ.position("4")))
        assertEquals(0, champ.position("inconnu"), "un code absent retombe sur « Non renseignée »")
    }

    @Test
    fun les_valeurs_du_serveur_sont_filtrees_dedoublonnees_et_triees() {
        nomenclatures.setAll(
            mapOf(
                "METH_OBS" to listOf(
                    NomValeur(41, "Vu"),
                    NomValeur(42, "Écouté"),
                    NomValeur(43, "Non renseigné"),
                    NomValeur(44, "vu"),
                    NomValeur(45, "Piégé", listOf(TaxrefRestriction("Animalia", "Insectes"))),
                ),
            ),
        )
        nomenclatures.setDefauts(mapOf("METH_OBS" to "41"))
        val champ = FormulairesEspece.champ(methObs, nomenclatures, setOf("Oiseaux"), "Animalia")
        assertEquals(listOf("Non renseigné", "Écouté", "Vu"), champ.libelles)
        assertEquals(listOf("", "42", "41"), champ.codes)
        // Sans valeur portée, le défaut du serveur.
        assertEquals(2, FormulairesEspece.positionInitiale(champ, "", nomenclatures))
        assertEquals(1, FormulairesEspece.positionInitiale(champ, "42", nomenclatures))
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

    @Test
    fun le_groupe_inpn_vient_du_cache_sinon_de_la_palette() {
        val merle = EspeceReleve(Taxon.OISEAU, "Merle noir", 4117)
        assertEquals("Oiseaux", FormulairesEspece.groupe2Inpn(merle, emptyMap()))
        assertEquals("Passereaux", FormulairesEspece.groupe2Inpn(merle, mapOf("4117" to "Passereaux")))
        assertNull(FormulairesEspece.groupe2Inpn(EspeceReleve(Taxon.PLANTE, "Ortie", null), emptyMap()))
    }
}
