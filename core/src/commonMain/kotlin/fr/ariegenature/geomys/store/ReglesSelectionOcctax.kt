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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/ConfigGeoNatureFragment.kt :
// peuplerSpinnerDatasets, appliquerRestrictionListeDataset, peuplerSpinnerListes, peuplerSpinnerObservateurs).
// Sous Android, ces règles vivaient dans les adaptateurs des menus déroulants ; elles deviennent des
// fonctions pures, communes aux deux allures de l'écran Paramètres et testables sans écran.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureListe
import fr.ariegenature.geomys.model.GeoNatureObservateur

/** Les listes proposées pour un jeu de données, et si ce jeu de données impose la sienne. */
data class ListesProposees(val listes: List<GeoNatureListe>, val imposee: Boolean)

/** Ce que les Paramètres PROPOSENT (jeu de données, liste de taxons, observateur par défaut) et ce
 *  qui reste SÉLECTIONNÉ après une synchro, un changement de jeu de données ou un « Vider le cache ».
 *  Une sélection absente de ce qui est proposé est un fantôme : l'écran la purge, sans quoi un id
 *  périmé validait la configuration à tort. */
object ReglesSelectionOcctax {

    /** Jeux de données proposés : actifs, rattachés au module OCCTAX — ou sans module déclaré,
     *  pour les instances qui n'exposent pas ce tableau — et créables (CRUVED C, comme le web ;
     *  ensemble vide = pas de restriction). */
    fun datasetsProposes(datasets: List<GeoNatureDataset>, creables: Set<Int>): List<GeoNatureDataset> =
        datasets.filter {
            it.actif && (it.moduleCodes.isEmpty() || "OCCTAX" in it.moduleCodes) &&
                (creables.isEmpty() || it.id in creables)
        }

    /** Libellé d'un jeu de données dans le menu : « nom (id) ». */
    fun libelle(dataset: GeoNatureDataset): String = "${dataset.nom} (${dataset.id})"

    /** Libellé d'une liste de taxons dans le menu : « nom (id) ». */
    fun libelle(liste: GeoNatureListe): String = "${liste.nom} (${liste.id})"

    /** Jeu de données à garder sélectionné : celui de [idStocke] s'il est encore proposé, sinon
     *  null (sélection fantôme à purger). */
    fun datasetValide(idStocke: String, proposes: List<GeoNatureDataset>): GeoNatureDataset? {
        val id = idStocke.toIntOrNull() ?: return null
        return proposes.firstOrNull { it.id == id }
    }

    /** Un jeu de données qui porte un `id_taxa_list` IMPOSE sa liste : on ne propose plus qu'elle
     *  (synthétisée « Liste <id> » si le serveur ne l'expose pas via /biblistes). Sinon, toutes. */
    fun listesProposees(toutes: List<GeoNatureListe>, dataset: GeoNatureDataset?): ListesProposees {
        val idImpose = dataset?.idTaxaList ?: return ListesProposees(toutes, imposee = false)
        val liste = toutes.firstOrNull { it.id == idImpose } ?: GeoNatureListe(idImpose, "Liste $idImpose")
        return ListesProposees(listOf(liste), imposee = true)
    }

    /** Id de la liste à sélectionner : l'imposée d'abord, puis la sélection stockée si elle est
     *  encore proposée, puis la liste UNIQUE — sauf juste après un « Vider le cache »
     *  ([selectionsReinitialisees]), où l'on repart d'une ardoise vierge — sinon aucune (""). */
    fun listeChoisie(idStocke: String, proposees: ListesProposees, selectionsReinitialisees: Boolean): String {
        if (proposees.imposee) return proposees.listes.first().id.toString()
        val id = idStocke.toIntOrNull()
        if (id != null && proposees.listes.any { it.id == id }) return id.toString()
        if (proposees.listes.size == 1 && !selectionsReinitialisees) return proposees.listes[0].id.toString()
        return ""
    }

    /** Observateur à sélectionner : la sélection stockée si elle est encore dans la liste, sinon
     *  l'utilisateur CONNECTÉ ([idRoleConnecte], id_role relevé au login) s'il y figure — y compris
     *  après un « Vider le cache » : contrairement au jeu de données et à la liste, l'observateur
     *  retombe toujours sur celui qui saisit. Sinon aucun. */
    fun observateurChoisi(
        idStocke: String,
        observateurs: List<GeoNatureObservateur>,
        idRoleConnecte: Int,
    ): GeoNatureObservateur? {
        val id = idStocke.toIntOrNull()
        observateurs.firstOrNull { it.idRole == id }?.let { return it }
        val idRole = idRoleConnecte.takeIf { it > 0 } ?: return null
        return observateurs.firstOrNull { it.idRole == idRole }
    }
}
