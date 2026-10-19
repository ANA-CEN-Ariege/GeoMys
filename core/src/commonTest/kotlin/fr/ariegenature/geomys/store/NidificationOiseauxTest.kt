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

// Propre au portage : NidificationOiseaux n'a pas de test dans GeoMys-Android @ cdd5196.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Période de nidification d'une espèce d'oiseau, d'après son nom scientifique dans TaxRef. */
class NidificationOiseauxTest {

    private val t = ServicesDeTest()
    private val taxRef get() = t.services.taxRef

    private val fichier = """
        {"version": 1, "source": "oiseaux_nicheurs_france.xlsx", "especes": [
          {"fr": "Geai des chênes", "sci": "Garrulus glandarius", "mois": [0,0,0,1,1,1,1,0,0,0,0,0]},
          {"fr": "Incomplet", "sci": "Avis incompleta", "mois": [1,1,1]}
        ]}
    """.trimIndent()

    @AfterTest
    fun nettoyer() = t.nettoyer()

    @BeforeTest
    fun setup() {
        taxRef.vider()
        taxRef.remplacerTout(mapOf(
            TaxRefCache.normaliser("Geai des chênes") to TaxRefEntry(3967, "Garrulus glandarius", "Geai des chênes"),
            // Nom scientifique suivi de son auteur dans TaxRef : repli sur genre + espèce.
            TaxRefCache.normaliser("Geai d'auteur") to TaxRefEntry(3968, "Garrulus glandarius (Linnaeus, 1758)", null),
            TaxRefCache.normaliser("Incomplet") to TaxRefEntry(1, "Avis incompleta", null),
        ))
    }

    @Test
    fun periode_selon_le_mois() = runTest {
        val nidification = NidificationOiseaux(taxRef) { fichier }
        assertTrue(nidification.estEnPeriode(3967, 4))
        assertTrue(nidification.estEnPeriode(3967, 7))
        assertFalse(nidification.estEnPeriode(3967, 1))
        assertFalse(nidification.estEnPeriode(3967, 8))
        assertTrue(nidification.estEnPeriode(3968, 5), "repli genre + espèce")
    }

    @Test
    fun hors_bornes_inconnu_ou_incomplet() = runTest {
        val nidification = NidificationOiseaux(taxRef) { fichier }
        assertFalse(nidification.estEnPeriode(3967, 0))
        assertFalse(nidification.estEnPeriode(3967, 13))
        assertFalse(nidification.estEnPeriode(999, 5), "cd_nom absent du cache")
        assertFalse(nidification.estEnPeriode(1, 1), "espèce aux mois incomplets ignorée")
    }

    @Test
    fun fichier_illisible_aucun_declenchement() = runTest {
        assertFalse(NidificationOiseaux(taxRef) { "pas du json" }.estEnPeriode(3967, 5))
        assertFalse(NidificationOiseaux(taxRef) { error("fichier absent") }.estEnPeriode(3967, 5))
    }
}
