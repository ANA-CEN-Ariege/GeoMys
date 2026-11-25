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

// Propre au portage (24 novembre 2026) : la fiche d'une espèce sert aussi à la série mono-taxon, dont les
// valeurs valent pour chaque « + ». Elle modifie alors l'espèce de la série au lieu d'une espèce de relevé.

package fr.ariegenature.geomys.occtax

import kotlinx.coroutines.flow.StateFlow

/** Ce qu'une fiche d'espèce lit et modifie : les espèces d'un relevé ([ReleveEnCours]), ou l'espèce
 *  d'une série mono-taxon ([SaisieMono.ficheSerie]), dont les valeurs valent pour chaque « + ». */
interface EspecesModifiables {
    /** Les espèces, que la fiche désigne par leur rang. */
    val especes: StateFlow<List<EspeceReleve>>

    /** L'espèce [index] transformée par [transformation], aussitôt enregistrée. */
    fun modifier(index: Int, transformation: (EspeceReleve) -> EspeceReleve)

    /** Les champs additionnels obligatoires à remplir d'une espèce, pour le jeu de données. */
    fun requis(): RequisEspece

    /** L'observation de [espece] est déjà sur le serveur : une mise à jour n'y porte pas de média. */
    fun dejaTransmise(espece: EspeceReleve): Boolean
}
