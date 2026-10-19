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

// Porté de GeoMys-Android @ d72b8da (app/src/test/java/fr/ariegenature/geomys/DefautsPariteLot2Test.kt,
// aucune_passerelle_entre_saisie_mono_et_multi). DÉCISION PRODUIT (2026-10-17) : on choisit son type de saisie à
// l'accueil, et on n'en change plus — un appui long sur « Observation » ouvrait la saisie mono SUR LA MÊME
// SESSION, et « Terminer » y clôturait le relevé multi. La garde Android lisait les sources ; ici, les sorties
// des deux écrans sont énumérées sans `else` : une passerelle (un nouvel événement) ne compilera pas sans
// revenir sur cette décision.

package fr.ariegenature.geomys.ui.occtax

import kotlin.test.Test
import kotlin.test.assertEquals

class PasserelleMonoMultiTest {

    /** Où mène chaque événement de la carte multi-taxons. */
    private fun destination(e: EvenementSaisieMulti): String = when (e) {
        is EvenementSaisieMulti.Message -> "aucune"
        is EvenementSaisieMulti.OuvrirReleve -> "relevé"
        EvenementSaisieMulti.OuvrirListe -> "observations"
        EvenementSaisieMulti.Quitter -> "écran précédent"
    }

    /** Où mène chaque événement de la saisie mono-taxon. */
    private fun destination(e: EvenementSaisieMono): String = when (e) {
        is EvenementSaisieMono.Message -> "aucune"
        EvenementSaisieMono.Caracterisation -> "caractérisation"
        EvenementSaisieMono.Denombrement -> "dénombrement"
        is EvenementSaisieMono.EditerObservation -> "relevé"
        EvenementSaisieMono.Quitter -> "écran précédent"
    }

    @Test
    fun la_carte_multi_n_ouvre_pas_la_saisie_mono() {
        val sorties = listOf(
            EvenementSaisieMulti.Message("x"),
            EvenementSaisieMulti.OuvrirReleve(null, "r", null, ecranReleve = false),
            EvenementSaisieMulti.OuvrirListe,
            EvenementSaisieMulti.Quitter,
        ).map(::destination)
        assertEquals(listOf("aucune", "relevé", "observations", "écran précédent"), sorties)
    }

    @Test
    fun la_saisie_mono_n_ouvre_pas_la_carte_multi() {
        val sorties = listOf(
            EvenementSaisieMono.Message("x"),
            EvenementSaisieMono.Caracterisation,
            EvenementSaisieMono.Denombrement,
            EvenementSaisieMono.EditerObservation("o"),
            EvenementSaisieMono.Quitter,
        ).map(::destination)
        assertEquals(listOf("aucune", "caractérisation", "dénombrement", "relevé", "écran précédent"), sorties)
    }
}
