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

// Propre au portage (21 octobre 2026) : sur certains serveurs, des champs
// additionnels d'occurrence ou de dénombrement sont obligatoires ; l'appli les montrait seulement à qui
// ouvrait la caractérisation ou le dénombrement d'une espèce.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.saisie.ChampsAdditionnels

/** Les champs additionnels obligatoires à remplir d'une espèce ([occurrence], sa caractérisation) et
 *  de ses dénombrements ([denombrement]) pour le jeu de données du relevé : ceux que le web exige
 *  avant d'enregistrer le taxon, et que l'appli montre comme tels. */
class RequisEspece(
    val occurrence: List<AdditionalFieldDef>,
    val denombrement: List<AdditionalFieldDef>,
) {
    /** La caractérisation de [e] a un champ obligatoire vide. */
    fun caracterisationIncomplete(e: EspeceReleve): Boolean =
        ChampsAdditionnels.unRequisVide(occurrence, e.additionalFieldsOccurrence)

    /** Un dénombrement de [e] a un champ obligatoire vide. */
    fun denombrementIncomplet(e: EspeceReleve): Boolean =
        denombrement.isNotEmpty() && (
            ChampsAdditionnels.unRequisVide(denombrement, e.additionalFieldsCounting0) ||
                e.denombrementsAdditionnels.any { ChampsAdditionnels.unRequisVide(denombrement, it.additionalFields) }
            )

    companion object {
        /** Ceux du jeu [idDataset], lus dans [additionalFieldsOcctaxJson]. */
        fun pour(additionalFieldsOcctaxJson: String, idDataset: Int?) = RequisEspece(
            ChampsAdditionnels.requisARemplir(additionalFieldsOcctaxJson, AdditionalFieldsObject.OCCURRENCE, idDataset),
            ChampsAdditionnels.requisARemplir(additionalFieldsOcctaxJson, AdditionalFieldsObject.COUNTING, idDataset),
        )
    }
}

/** Le dernier [RequisEspece] calculé, gardé tant que les champs du serveur et le jeu ne changent pas :
 *  la liste d'un relevé le demande à chaque ligne. */
internal class MemoRequis {
    private var cle: Pair<String, Int?>? = null
    private var valeur: RequisEspece? = null

    fun pour(additionalFieldsOcctaxJson: String, idDataset: Int?): RequisEspece {
        val nouvelle = additionalFieldsOcctaxJson to idDataset
        valeur?.let { if (cle == nouvelle) return it }
        return RequisEspece.pour(additionalFieldsOcctaxJson, idDataset).also {
            cle = nouvelle
            valeur = it
        }
    }
}
