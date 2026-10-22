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

/** Les sélections par défaut des Paramètres (jeu de données, liste de taxons, observateur) : ce
 *  qu'ils PROPOSENT et ce qui reste SÉLECTIONNÉ après une synchro ou un « Vider le cache ». Elles
 *  sont obligatoires, sans valeur d'office : le jeu et la liste sont à choisir, l'observateur est
 *  l'utilisateur connecté. Une sélection absente de ce qui est proposé est un fantôme, que l'écran
 *  purge. */
object ReglesSelectionOcctax {

    /** Jeux de données proposés, ceux du formulaire du web (`active`, `module_code` et `create`
     *  OCCTAX) : actifs, rattachés au module OCCTAX et créables. */
    fun datasetsProposes(datasets: List<GeoNatureDataset>, creables: Set<Int>): List<GeoNatureDataset> =
        datasets.filter { it.actif && "OCCTAX" in it.moduleCodes && it.id in creables }

    /** Libellé d'un jeu de données dans le menu : « nom (id) ». */
    fun libelle(dataset: GeoNatureDataset): String = "${dataset.nom} (${dataset.id})"

    /** Libellé d'une liste de taxons dans le menu : « nom (id) ». */
    fun libelle(liste: GeoNatureListe): String = "${liste.nom} (${liste.id})"

    /** Jeu de données à garder sélectionné : celui de [idStocke] s'il est encore proposé, sinon
     *  null (sélection fantôme à purger, ou aucune). */
    fun datasetValide(idStocke: String, proposes: List<GeoNatureDataset>): GeoNatureDataset? {
        val id = idStocke.toIntOrNull() ?: return null
        return proposes.firstOrNull { it.id == id }
    }

    /** Un jeu de données qui porte un `id_taxa_list` IMPOSE sa liste : on ne montre plus qu'elle
     *  (synthétisée « Liste <id> » si le serveur ne l'expose pas via /biblistes). Sinon, toutes. */
    fun listesProposees(toutes: List<GeoNatureListe>, dataset: GeoNatureDataset?): ListesProposees {
        val idImpose = dataset?.idTaxaList ?: return ListesProposees(toutes, imposee = false)
        val liste = toutes.firstOrNull { it.id == idImpose } ?: GeoNatureListe(idImpose, "Liste $idImpose")
        return ListesProposees(listOf(liste), imposee = true)
    }

    /** Liste à garder sélectionnée : celle de [idStocke] si elle existe encore parmi [toutes], sinon
     *  aucune. Jamais de liste choisie d'office, même unique. Une liste imposée par le jeu de données
     *  ne remplace pas ce choix : elle l'emporte seulement pour les relevés de ce jeu. */
    fun listeChoisie(idStocke: String, toutes: List<GeoNatureListe>): GeoNatureListe? {
        val id = idStocke.toIntOrNull() ?: return null
        return toutes.firstOrNull { it.id == id }
    }

    /** Observateurs proposés : ceux de la liste d'OccTax, plus l'utilisateur connecté
     *  ([idRoleConnecte], [nomConnecte]) s'il n'y figure pas, comme le web le pose toujours. */
    fun observateursProposes(
        observateurs: List<GeoNatureObservateur>,
        idRoleConnecte: Int,
        nomConnecte: String,
    ): List<GeoNatureObservateur> {
        if (idRoleConnecte <= 0 || nomConnecte.isBlank() || observateurs.any { it.idRole == idRoleConnecte }) return observateurs
        return observateurs + GeoNatureObservateur(idRoleConnecte, nomConnecte)
    }

    /** Observateur à montrer sélectionné : celui de [idStocke] s'il est encore proposé, sinon
     *  l'utilisateur CONNECTÉ ([idRoleConnecte]), y compris après un « Vider le cache » : le défaut
     *  retombe toujours sur celui qui saisit. Sinon aucun. */
    fun observateurChoisi(
        idStocke: String,
        proposes: List<GeoNatureObservateur>,
        idRoleConnecte: Int,
    ): GeoNatureObservateur? {
        val id = idStocke.toIntOrNull()
        proposes.firstOrNull { it.idRole == id }?.let { return it }
        val idRole = idRoleConnecte.takeIf { it > 0 } ?: return null
        return proposes.firstOrNull { it.idRole == idRole }
    }
}
