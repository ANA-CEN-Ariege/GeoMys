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

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureListe
import fr.ariegenature.geomys.model.GeoNatureObservateur
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Règles de sélection des Paramètres, telles que les appliquait ConfigGeoNatureFragment
 *  (GeoMys-Android) dans ses menus déroulants. */
class ReglesSelectionOcctaxTest {

    private val occtax = GeoNatureDataset(1, "Occtax", moduleCodes = listOf("OCCTAX"))
    private val sansModule = GeoNatureDataset(2, "Sans module")
    private val autreModule = GeoNatureDataset(3, "Suivi", moduleCodes = listOf("MONITORING"))
    private val inactif = GeoNatureDataset(4, "Ancien", actif = false, moduleCodes = listOf("OCCTAX"))
    private val listes = listOf(GeoNatureListe(100, "Saisie"), GeoNatureListe(200, "Flore"))
    private val observateurs = listOf(GeoNatureObservateur(7, "DUPONT Jean"), GeoNatureObservateur(9, "MARTIN Lea"))

    @Test
    fun seuls_les_jeux_actifs_rattaches_a_occtax_ou_sans_module_sont_proposes() {
        val proposes = ReglesSelectionOcctax.datasetsProposes(listOf(occtax, sansModule, autreModule, inactif), emptySet())
        assertEquals(listOf(occtax, sansModule), proposes)
    }

    @Test
    fun les_jeux_creables_restreignent_la_proposition() {
        val proposes = ReglesSelectionOcctax.datasetsProposes(listOf(occtax, sansModule), creables = setOf(2))
        assertEquals(listOf(sansModule), proposes)
    }

    @Test
    fun libelles_nom_et_id() {
        assertEquals("Occtax (1)", ReglesSelectionOcctax.libelle(occtax))
        assertEquals("Flore (200)", ReglesSelectionOcctax.libelle(listes[1]))
    }

    @Test
    fun un_jeu_stocke_qui_n_est_plus_propose_est_un_fantome() {
        assertEquals(occtax, ReglesSelectionOcctax.datasetValide("1", listOf(occtax)))
        assertNull(ReglesSelectionOcctax.datasetValide("4", listOf(occtax)))
        assertNull(ReglesSelectionOcctax.datasetValide("", listOf(occtax)))
    }

    @Test
    fun un_jeu_qui_impose_sa_liste_ne_laisse_qu_elle() {
        val impose = occtax.copy(idTaxaList = 200)
        assertEquals(ListesProposees(listOf(listes[1]), imposee = true), ReglesSelectionOcctax.listesProposees(listes, impose))
        assertEquals(ListesProposees(listes, imposee = false), ReglesSelectionOcctax.listesProposees(listes, occtax))
        assertEquals(ListesProposees(listes, imposee = false), ReglesSelectionOcctax.listesProposees(listes, null))
    }

    @Test
    fun une_liste_imposee_absente_du_serveur_est_synthetisee() {
        val proposees = ReglesSelectionOcctax.listesProposees(listes, occtax.copy(idTaxaList = 300))
        assertEquals(listOf(GeoNatureListe(300, "Liste 300")), proposees.listes)
        assertTrue(proposees.imposee)
    }

    @Test
    fun la_liste_imposee_l_emporte_sur_la_selection_stockee() {
        val proposees = ListesProposees(listOf(listes[1]), imposee = true)
        assertEquals("200", ReglesSelectionOcctax.listeChoisie("100", proposees, selectionsReinitialisees = true))
    }

    @Test
    fun la_selection_stockee_est_gardee_si_elle_est_proposee() {
        val proposees = ListesProposees(listes, imposee = false)
        assertEquals("200", ReglesSelectionOcctax.listeChoisie("200", proposees, selectionsReinitialisees = false))
        assertEquals("", ReglesSelectionOcctax.listeChoisie("999", proposees, selectionsReinitialisees = false))
    }

    @Test
    fun une_liste_unique_est_choisie_d_office_sauf_apres_vider_le_cache() {
        val unique = ListesProposees(listOf(listes[0]), imposee = false)
        assertEquals("100", ReglesSelectionOcctax.listeChoisie("", unique, selectionsReinitialisees = false))
        assertEquals("", ReglesSelectionOcctax.listeChoisie("", unique, selectionsReinitialisees = true))
    }

    @Test
    fun l_observateur_stocke_puis_l_utilisateur_connecte() {
        assertEquals(observateurs[1], ReglesSelectionOcctax.observateurChoisi("9", observateurs, idRoleConnecte = 7))
        assertEquals(observateurs[0], ReglesSelectionOcctax.observateurChoisi("", observateurs, idRoleConnecte = 7))
        assertEquals(observateurs[0], ReglesSelectionOcctax.observateurChoisi("42", observateurs, idRoleConnecte = 7))
    }

    @Test
    fun sans_utilisateur_connecte_connu_aucun_observateur() {
        assertNull(ReglesSelectionOcctax.observateurChoisi("", observateurs, idRoleConnecte = 0))
        assertNull(ReglesSelectionOcctax.observateurChoisi("", observateurs, idRoleConnecte = 42))
        assertFalse(observateurs.any { it.idRole == 42 })
    }
}
