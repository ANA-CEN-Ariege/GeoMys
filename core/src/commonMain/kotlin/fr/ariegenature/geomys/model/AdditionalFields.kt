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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/AdditionalFields.kt,
// modèles) : passés dans model/ et @Serializable, sous les mêmes noms de propriétés, pour le cache
// JSON des réglages (Gson sous Android ; cf. listeVersJson et listeDepuisJson).

package fr.ariegenature.geomys.model

import kotlinx.serialization.Serializable

/** Niveaux où un champ additionnel peut s'appliquer (table `objects.code_object` côté GN).
 *  ⚠ Le code OCCTAX_OCCURENCE est typo côté serveur (un seul R) — c'est la valeur réellement
 *  stockée dans gn_commons.bib_tables_location, confirmée par gn_mobile_occtax officiel. */
object AdditionalFieldsObject {
    const val RELEVE = "OCCTAX_RELEVE"
    const val OCCURRENCE = "OCCTAX_OCCURENCE"
    const val COUNTING = "OCCTAX_DENOMBREMENT"
}

/** Types de widgets supportés (couvrent ~95% des configs Occtax réelles).
 *  Les autres widgets serveur sont rendus en texte libre (best-effort) avec un hint. */
enum class WidgetType { TEXT, TEXTAREA, NUMBER, SELECT, RADIO, CHECKBOX, NOMENCLATURE, INCONNU }

/** Définition d'un champ additionnel récupérée du serveur GeoNature.
 *  Sérialisable pour le cache des réglages. */
@Serializable
data class AdditionalFieldDef(
    val idField: Int,
    val fieldName: String,
    val fieldLabel: String,
    val widget: WidgetType,
    /** Libellés proposés pour SELECT / CHECKBOX multi-valeurs. */
    val fieldValues: List<String> = emptyList(),
    /** Codes (valeurs réelles) alignés sur [fieldValues] — extraits de `field_values[i].value` quand
     *  le serveur fournit des objets `{value, label}` (sinon = le libellé). Utilisés pour l'envoi. */
    val fieldValueCodes: List<String> = emptyList(),
    val required: Boolean = false,
    val description: String? = null,
    val defaultValue: String? = null,
    /** Codes des objets concernés (OCCTAX_RELEVE / OCCTAX_OCCURRENCE / OCCTAX_DENOMBREMENT). */
    val objectsCode: List<String> = emptyList(),
    /** Liste des id_dataset où ce champ s'applique. Vide = tous les datasets. */
    val datasetsIds: List<Int> = emptyList(),
    /** id_list UsersHub auquel ce champ se restreint (null = aucune restriction). */
    val idList: Int? = null,
    /** Nom du widget tel que renvoyé par le serveur (utile pour debug si type non supporté). */
    val widgetServeur: String = "",
    /** Pour widget=`nomenclature` : code mnémonique du type de nomenclature serveur
     *  (ex. `STATUT_OBS`, `METH_DETERMIN`). Sert à fetcher /api/nomenclatures/nomenclature/<code>. */
    val codeNomenclatureType: String? = null,
    /** Options résolues pour widget=`nomenclature` : (id_nomenclature, label_fr). */
    val nomenclatureOptions: List<Pair<String, String>> = emptyList(),
) {
    fun appliqueA(codeObjet: String): Boolean = objectsCode.contains(codeObjet)

    /** Vrai si ce champ doit s'afficher pour le dataset courant et le taxon observé.
     *  - `idDataset` : id du dataset courant (gnConfig.idDataset)
     *  - `listesDuTaxon` : id_liste UsersHub auxquelles appartient le cd_nom observé
     *    (récupéré via TaxRefCache.listesPourCdNom(cdNom))
     *
     *  Règles :
     *  - datasets vide → applies à tous les datasets
     *  - idList null  → applies à tous les taxons (pas de restriction par liste)
     *  - sinon : il faut que le taxon appartienne à cette liste */
    fun visiblePour(idDataset: Int?, listesDuTaxon: Collection<Int>): Boolean {
        if (datasetsIds.isNotEmpty() && idDataset != null && idDataset !in datasetsIds) return false
        if (datasetsIds.isNotEmpty() && idDataset == null) return false
        if (idList != null && idList !in listesDuTaxon) return false
        return true
    }
}
