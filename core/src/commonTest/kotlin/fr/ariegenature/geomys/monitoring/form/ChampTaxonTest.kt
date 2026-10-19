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

// Propre au portage, sauf le dernier cas : la garde textuelle
// ResolutionPurementLocaleTest.aucun_chemin_de_saisie_n_ecrit_dans_le_cache_taxref de GeoMys-Android
// @ cdd5196 lisait monitoring/form/FormulaireRenderer.kt ; sa part « formulaire des Suivis » devient
// ici un test de comportement (inventaire/7-tests.md § 7.3).

package fr.ariegenature.geomys.monitoring.form

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.TaxRefEntry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/** Les propositions et la ligne de statut d'un champ espèce. */
class ChampTaxonTest {

    private val t = ServicesDeTest()
    private val taxRef get() = t.services.taxRef
    private val steRF = 109

    @AfterTest
    fun nettoyer() = t.nettoyer()

    @BeforeTest
    fun setup() {
        // « Souci » est la clé de la plante (Calendula) ; le papillon de la liste 109 la partage.
        taxRef.vider()
        taxRef.remplacerTout(mapOf(
            TaxRefCache.normaliser("Souci") to TaxRefEntry(190178, "Calendula", "Souci"),
            TaxRefCache.normaliser("Colias crocea") to TaxRefEntry(641941, "Colias crocea", null),
            TaxRefCache.normaliser("Genette") to TaxRefEntry(60831, "Genetta genetta", "Genette"),
        ))
        taxRef.ajouterVerns(mapOf(190178 to listOf("Souci"), 641941 to listOf("Souci"), 60831 to listOf("Genette")))
        taxRef.ajouterListesParCdNom(mapOf(641941 to listOf(100, steRF), 190178 to listOf(100), 60831 to listOf(100)))
    }

    @Test
    fun liste_du_protocole_propositions_porteuses_de_leur_taxon() {
        val p = PropositionsTaxon.construire(taxRef, steRF)
        assertFalse(p.listeVide)
        assertEquals(setOf("Souci", "Colias crocea"), p.propositions.map { it.nom }.toSet())
        val souci = p.propositions.single { it.nom == "Souci" }
        assertEquals(641941, souci.cdNom, "le papillon, pas la plante")
        assertEquals("Colias crocea", souci.secondaireAffiche)
        assertEquals(listOf(souci), p.filtrer("SOU"))
        assertEquals("liste=$steRF, 2 suggestions", p.diagnostic)
    }

    @Test
    fun sans_liste_les_cles_du_cache_sans_taxon() {
        val p = PropositionsTaxon.construire(taxRef, null)
        assertFalse(p.listeVide)
        assertEquals(setOf("souci", "colias crocea", "genette"), p.propositions.map { it.nom }.toSet())
        assertTrue(p.propositions.all { it.cdNom == null && it.secondaire == null })
        assertEquals("liste=null (toutes), 3 suggestions", p.diagnostic)
    }

    @Test
    fun liste_absente_du_cache_aucune_proposition() {
        val p = PropositionsTaxon.construire(taxRef, 999)
        assertTrue(p.listeVide)
        assertEquals(emptyList(), p.propositions)
        assertEquals(emptyList(), p.filtrer(null))
        assertEquals(emptyList(), p.filtrer("sou"))
        assertEquals("liste=999 ABSENTE du cache — aucune proposition", p.diagnostic)
    }

    @Test
    fun nom_secondaire_affiche_seulement_s_il_apporte_quelque_chose() {
        assertEquals("Colias crocea", PropositionTaxon("Souci", 641941, "Colias crocea").secondaireAffiche)
        assertNull(PropositionTaxon("Souci", 641941, "Souci").secondaireAffiche)
        assertNull(PropositionTaxon("Souci", 641941, "").secondaireAffiche)
        assertNull(PropositionTaxon("Souci", null, null).secondaireAffiche)
        assertEquals("Souci", PropositionTaxon("Souci", 641941, "Colias crocea").toString(), "texte recopié dans le champ")
    }

    @Test
    fun textes_de_la_ligne_de_statut() {
        assertEquals("✓ Turdus merula  •  cd_nom 4117", StatutTaxon.Retenu(4117, "Turdus merula").texte)
        assertEquals("✓ cd_nom 4117", StatutTaxon.Retenu(4117, "").texte)
        assertEquals("Pas de données — rechargez les données", StatutTaxon.PasDeDonnees(false).texte)
        assertEquals("Chargement des données en cours…", StatutTaxon.PasDeDonnees(true).texte)
        assertEquals("✗ Espèce non reconnue dans la liste du protocole — choisissez une proposition", StatutTaxon.Refus(true).texte)
        assertEquals("✗ Espèce non reconnue — choisissez une proposition", StatutTaxon.Refus(false).texte)
    }

    /** Pendant une synchro, la liste absente se réécrit : ce n'est pas à l'utilisateur de recharger. */
    @Test
    fun liste_absente_pendant_une_synchro() = runTest {
        val etat = FormulaireEtat(
            listOf(EditableField("esp", ViewType.TAXON, "Espèce", idListeTaxonomieRestreinte = 999)),
            taxRef, backgroundScope, synchroActive = { true },
        )
        etat.revision.first { etat.indicationTaxon("esp") != FormulaireEtat.INDICATION_TAXON }
        assertEquals("Chargement des données en cours…", etat.indicationTaxon("esp"))
        assertEquals("Chargement des données en cours…", etat.statutTaxon("esp")?.texte)
    }

    /** Aucun chemin du champ espèce n'écrit dans le cache TaxRef : frappe, choix d'une proposition,
     *  nom inconnu refusé à la sortie, champ libre ou restreint à la liste du protocole. */
    @Test
    fun le_champ_espece_n_ecrit_jamais_dans_le_cache() = runTest {
        val dossierCache = t.dossier / "fichiers" / "taxref"
        val avant = contenu(dossierCache)
        assertTrue(avant.isNotEmpty())
        val etat = FormulaireEtat(
            listOf(
                EditableField("esp", ViewType.TAXON, "Espèce", idListeTaxonomieRestreinte = steRF),
                EditableField("libre", ViewType.TAXON, "Libre", value = 60831),
            ),
            taxRef, backgroundScope,
        )
        etat.revision.first { etat.propositionsTaxon("esp", null).isNotEmpty() && etat.propositionsTaxon("libre", null).isNotEmpty() }
        etat.saisirTexteTaxon("esp", "Sou")
        etat.choisirPropositionTaxon("esp", etat.propositionsTaxon("esp", "Sou").single())
        assertEquals(641941, etat.lireValeurs()["esp"])
        etat.saisirTexteTaxon("esp", "Mésange inconnue")
        etat.quitterChampTaxon("esp")
        etat.saisirTexteTaxon("libre", "Souci")
        assertEquals(190178, etat.lireValeurs()["libre"])
        etat.saisirTexteTaxon("libre", "Merle inconnu")
        etat.quitterChampTaxon("libre")
        etat.flushChangementsEnAttente()
        assertEquals(avant, contenu(dossierCache))
    }

    private fun contenu(dossier: Path): Map<Path, String> =
        FileSystem.SYSTEM.listRecursively(dossier)
            .filter { FileSystem.SYSTEM.metadata(it).isRegularFile }
            .associateWith { FileSystem.SYSTEM.read(it) { readByteString().hex() } }
}
