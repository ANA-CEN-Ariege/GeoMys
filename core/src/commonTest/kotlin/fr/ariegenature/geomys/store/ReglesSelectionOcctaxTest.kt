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

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureListe
import fr.ariegenature.geomys.model.GeoNatureObservateur
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Les sélections par défaut des Paramètres, facultatives : les jeux du formulaire du web, les
 *  listes, l'observateur (l'utilisateur connecté au départ) et la purge des sélections fantômes. */
class ReglesSelectionOcctaxTest {

    private val occtax = GeoNatureDataset(1, "Occtax", moduleCodes = listOf("OCCTAX"))
    private val sansModule = GeoNatureDataset(2, "Sans module")
    private val autreModule = GeoNatureDataset(3, "Suivi", moduleCodes = listOf("MONITORING"))
    private val inactif = GeoNatureDataset(4, "Ancien", actif = false, moduleCodes = listOf("OCCTAX"))

    @Test
    fun seuls_les_jeux_actifs_occtax_et_creables_sont_proposes_comme_sur_le_web() {
        val proposes = ReglesSelectionOcctax.datasetsProposes(listOf(occtax, sansModule, autreModule, inactif), setOf(1, 2, 3, 4))
        assertEquals(listOf(occtax), proposes)
        assertEquals(emptyList(), ReglesSelectionOcctax.datasetsProposes(listOf(occtax), emptySet()), "sans liste des créables, aucun")
    }

    @Test
    fun libelles_nom_et_id() {
        assertEquals("Occtax (1)", ReglesSelectionOcctax.libelle(occtax))
    }

    @Test
    fun un_jeu_stocke_qui_n_est_plus_propose_est_un_fantome() {
        assertEquals(occtax, ReglesSelectionOcctax.datasetValide("1", listOf(occtax)))
        assertNull(ReglesSelectionOcctax.datasetValide("4", listOf(occtax)))
        assertNull(ReglesSelectionOcctax.datasetValide("", listOf(occtax)))
    }

    private val liste100 = GeoNatureListe(100, "Saisie")
    private val liste200 = GeoNatureListe(200, "Flore")

    @Test
    fun aucune_liste_n_est_choisie_d_office_meme_unique() {
        assertNull(ReglesSelectionOcctax.listeChoisie("", listOf(liste100)))
        assertEquals(liste200, ReglesSelectionOcctax.listeChoisie("200", listOf(liste100, liste200)))
        assertNull(ReglesSelectionOcctax.listeChoisie("300", listOf(liste100)), "liste fantôme")
    }

    @Test
    fun un_jeu_qui_porte_une_liste_l_impose() {
        val jeu = GeoNatureDataset(1, "Occtax", idTaxaList = 200, moduleCodes = listOf("OCCTAX"))
        assertEquals(ListesProposees(listOf(liste200), imposee = true), ReglesSelectionOcctax.listesProposees(listOf(liste100, liste200), jeu))
        assertEquals(ListesProposees(listOf(liste100, liste200), imposee = false), ReglesSelectionOcctax.listesProposees(listOf(liste100, liste200), occtax))
        assertEquals(
            ListesProposees(listOf(GeoNatureListe(300, "Liste 300")), imposee = true),
            ReglesSelectionOcctax.listesProposees(listOf(liste100), jeu.copy(idTaxaList = 300)),
            "liste absente de /biblistes : synthétisée",
        )
    }

    private val alice = GeoNatureObservateur(3, "DUPONT Alice")
    private val paul = GeoNatureObservateur(8, "MARTIN Paul")

    @Test
    fun l_utilisateur_connecte_est_propose_et_choisi_par_defaut() {
        val proposes = ReglesSelectionOcctax.observateursProposes(listOf(paul), 3, "DUPONT Alice")
        assertEquals(listOf(paul, alice), proposes, "ajouté s'il manque à la liste, comme le web")
        assertEquals(alice, ReglesSelectionOcctax.observateurChoisi("", proposes, 3))
        assertEquals(paul, ReglesSelectionOcctax.observateurChoisi("8", proposes, 3))
        assertEquals(alice, ReglesSelectionOcctax.observateurChoisi("99", proposes, 3), "fantôme : retour à l'utilisateur connecté")
        assertEquals(listOf(paul, alice), ReglesSelectionOcctax.observateursProposes(listOf(paul, alice), 3, "DUPONT Alice"))
        assertNull(ReglesSelectionOcctax.observateurChoisi("", listOf(paul), -1), "inconnu tant qu'on n'est pas connecté")
    }
}
