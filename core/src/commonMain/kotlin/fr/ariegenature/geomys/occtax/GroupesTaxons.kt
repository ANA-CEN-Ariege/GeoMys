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

// Extrait de GeoMys-Android @ d72b8da : la palette des groupes (fragment_saisie_observation.xml,
// filtrerBoutonsGroupesNonVides de ui/saisie/TaxonPalette.kt), le libellé du champ espèce
// (updateEspeceHint de ui/SaisieObservationFragment.kt et ui/SaisieRapideFragment.kt) et le nom de
// groupe des observations mono-taxon sans espèce (nomGroupe() de ui/SaisieRapideFragment.kt).

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.store.TaxRefCache

/** Les groupes de la palette, dans l'ordre de ses boutons. */
val ORDRE_PALETTE = listOf(
    Taxon.OISEAU, Taxon.MAMMIFERE, Taxon.REPTILE, Taxon.BATRACIEN, Taxon.MOLLUSQUE,
    Taxon.POISSON, Taxon.INSECTE, Taxon.INVERTEBRES, Taxon.PLANTE, Taxon.FONGE,
)

/** Nom du groupe, rappelé dans le libellé du champ espèce. */
val Taxon.nomGroupe: String
    get() = when (this) {
        Taxon.OISEAU -> "Oiseaux"
        Taxon.MAMMIFERE -> "Mammifères"
        Taxon.REPTILE -> "Reptiles"
        Taxon.BATRACIEN -> "Amphibiens"
        Taxon.POISSON -> "Poissons"
        Taxon.INSECTE -> "Insectes"
        Taxon.FONGE -> "Champignons"
        Taxon.MOLLUSQUE -> "Mollusques"
        Taxon.INVERTEBRES -> "Autres invertébrés"
        Taxon.PLANTE -> "Plantes"
    }

/** Libellé du champ espèce : le champ n'accepte que les espèces du groupe choisi. */
fun libelleChampEspece(taxon: Taxon, scientifique: Boolean): String =
    if (scientifique) "Nom scientifique (${taxon.nomGroupe})" else "Espèce observée (${taxon.nomGroupe})"

/** Libellé du champ espèce de la saisie mono-taxon. */
fun libelleChampEspeceMono(taxon: Taxon, scientifique: Boolean): String =
    if (scientifique) "Nom scientifique (${taxon.nomGroupe})" else "Espèce (${taxon.nomGroupe})"

/** Nom du groupe au singulier : l'espèce d'une observation mono-taxon qui n'en nomme pas. */
val Taxon.nomSingulier: String
    get() = when (this) {
        Taxon.OISEAU -> "Oiseau"
        Taxon.MAMMIFERE -> "Mammifère"
        Taxon.REPTILE -> "Reptile"
        Taxon.BATRACIEN -> "Batracien"
        Taxon.POISSON -> "Poisson"
        Taxon.INSECTE -> "Insecte"
        Taxon.FONGE -> "Champignon"
        Taxon.MOLLUSQUE -> "Mollusque"
        Taxon.INVERTEBRES -> "Autre invertébré"
        Taxon.PLANTE -> "Plante"
    }

/** Les groupes de la palette qui ont au moins un taxon chargé, dans la liste [idListeFiltre] si
 *  elle est donnée : les autres boutons sont masqués. */
fun TaxRefCache.groupesNonVides(idListeFiltre: Int?): List<Taxon> =
    ORDRE_PALETTE.filter { !indexParTaxon(it, idListeFiltre).isNullOrEmpty() }

/** Groupe de départ de la palette : [voulu] s'il y a un bouton, sinon le premier bouton (ou
 *  [voulu], s'il n'y en a aucun). */
fun groupeDeDepart(voulu: Taxon, groupes: List<Taxon>): Taxon =
    if (voulu in groupes) voulu else groupes.firstOrNull() ?: voulu
