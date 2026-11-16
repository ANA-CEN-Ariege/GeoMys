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
    fun la_saisie_simplifiee_ne_propose_que_la_liste_de_saisie_d_occtax() {
        val saisie = GeoNatureListe(100, "Saisie Occtax")
        val oiseaux = GeoNatureListe(200, "Oiseaux")
        val toutes = ListesProposees(listOf(oiseaux, saisie), imposee = false)
        assertEquals(ListesProposees(listOf(saisie), imposee = false), ReglesSelectionOcctax.listesSimplifiees(toutes, idListeOcctax = 100))
        // La liste de la configuration d'OccTax passe avant le nom.
        assertEquals(ListesProposees(listOf(oiseaux), imposee = false), ReglesSelectionOcctax.listesSimplifiees(toutes, idListeOcctax = 200))
        // Configuration muette : la liste se reconnaît à son nom.
        val autreCasse = ListesProposees(listOf(oiseaux, GeoNatureListe(7, "Saisie OccTax")), imposee = false)
        assertEquals(listOf(7), ReglesSelectionOcctax.listesSimplifiees(autreCasse, idListeOcctax = -1).listes.map { it.id })
        // Introuvable : tout reste proposé. Imposée par le jeu de données : inchangée.
        val sans = ListesProposees(listOf(oiseaux, GeoNatureListe(300, "Flore")), imposee = false)
        assertEquals(sans, ReglesSelectionOcctax.listesSimplifiees(sans, idListeOcctax = 100))
        val imposee = ListesProposees(listOf(oiseaux), imposee = true)
        assertEquals(imposee, ReglesSelectionOcctax.listesSimplifiees(imposee, idListeOcctax = 100))
    }

    @Test
    fun la_saisie_simplifiee_ne_propose_que_les_jeux_personnels_s_il_y_en_a() {
        val personnel = GeoNatureDataset(7, "Jeu de données personnel de MARTIN Paul", moduleCodes = listOf("OCCTAX"), acteurs = listOf(8))
        val autrui = GeoNatureDataset(9, "Jeu de données personnel de DURAND Anne", moduleCodes = listOf("OCCTAX"), acteurs = listOf(12))
        assertEquals(listOf(personnel), ReglesSelectionOcctax.datasetsPersonnels(listOf(occtax, personnel, autrui), idRoleUtilisateur = 8))
        // Aucun jeu personnel (compte créé par un administrateur, cache d'avant la règle) : tout reste.
        assertEquals(listOf(occtax, autrui), ReglesSelectionOcctax.datasetsPersonnels(listOf(occtax, autrui), idRoleUtilisateur = 8))
        assertEquals(listOf(occtax, personnel), ReglesSelectionOcctax.datasetsPersonnels(listOf(occtax, personnel), idRoleUtilisateur = 0), "utilisateur inconnu")
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
