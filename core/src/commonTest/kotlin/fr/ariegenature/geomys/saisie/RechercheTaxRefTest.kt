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

// Propre au portage : TaxRefLookupController de GeoMys-Android @ cdd5196 n'était pas testé ; les attendus
// sont ceux de son code.

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.network.TaxRefStatut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** [RechercheTaxRef] : délai de frappe, choix figé, périmètre changé, absence de données. */
@OptIn(ExperimentalCoroutinesApi::class)
class RechercheTaxRefTest {

    private val merle = TaxRefStatut.Trouve(4117, "Turdus merula", "Merle noir")

    @Test
    fun la_recherche_part_500_ms_apres_la_derniere_frappe() = runTest {
        val demandes = mutableListOf<String>()
        val recherche = RechercheTaxRef(backgroundScope) { nom -> demandes += nom; merle }
        recherche.rechercher("Me")
        advanceTimeBy(300)
        recherche.rechercher("Mer")
        advanceTimeBy(499)
        runCurrent()
        assertTrue(demandes.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("Mer"), demandes)
        assertEquals(LigneStatutTaxRef(merle, roue = false, texteVisible = true), recherche.ligne.value)
        // Moins de deux caractères : plus rien.
        recherche.rechercher("M")
        assertEquals(LigneStatutTaxRef(), recherche.ligne.value)
    }

    @Test
    fun la_roue_remplace_la_ligne_pendant_la_recherche() = runTest {
        val reponse = CompletableDeferred<TaxRefStatut>()
        val recherche = RechercheTaxRef(backgroundScope) { reponse.await() }
        recherche.rechercher("Merle")
        advanceTimeBy(501)
        runCurrent()
        assertTrue(recherche.ligne.value.roue)
        assertFalse(recherche.ligne.value.texteVisible)
        reponse.complete(TaxRefStatut.NonTrouve)
        runCurrent()
        assertEquals(LigneStatutTaxRef(TaxRefStatut.NonTrouve, roue = false, texteVisible = true), recherche.ligne.value)
    }

    @Test
    fun un_choix_dans_la_liste_n_est_pas_recherche_de_nouveau() = runTest {
        val demandes = mutableListOf<String>()
        val recherche = RechercheTaxRef(backgroundScope) { nom -> demandes += nom; TaxRefStatut.NonTrouve }
        recherche.poser(merle, "Merle noir")
        recherche.rechercher("Merle noir")
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(demandes.isEmpty())
        assertEquals(merle, recherche.statut)
        // Un périmètre changé lève le choix et relance la résolution.
        recherche.relancer("Merle noir")
        assertNull(recherche.statut, "invalidé tout de suite")
        advanceTimeBy(501)
        runCurrent()
        assertEquals(listOf("Merle noir"), demandes)
        assertEquals(TaxRefStatut.NonTrouve, recherche.statut)
    }

    @Test
    fun sans_donnees_le_message_reste() = runTest {
        val demandes = mutableListOf<String>()
        val recherche = RechercheTaxRef(backgroundScope) { nom -> demandes += nom; merle }
        recherche.signalerPasDeDonnees(true)
        recherche.rechercher("Merle")
        recherche.reset()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(demandes.isEmpty())
        assertEquals(LigneStatutTaxRef(TaxRefStatut.PasDeDonnees, roue = false, texteVisible = true), recherche.ligne.value)
        recherche.signalerPasDeDonnees(false)
        assertEquals(LigneStatutTaxRef(), recherche.ligne.value)
    }
}
