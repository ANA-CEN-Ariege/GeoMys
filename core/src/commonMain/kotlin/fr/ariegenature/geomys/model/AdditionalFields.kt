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

// Porté de GeoMys-Android @ d72b8da (app/src/main/java/fr/ariegenature/geomys/network/AdditionalFields.kt,
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

/** Les widgets du formulaire dynamique du web (dynamic-form.component.html) que l'appli rend. Un
 *  widget qu'elle ne rend pas ([INCONNU] : areas, municipalities, medias, dataset, file…, ou une
 *  datalist sans `values`, servie par une API) n'est pas affiché, comme un widget inconnu du web, et
 *  sa valeur par défaut part quand même. Un bloc `html` s'affiche sans valeur. */
enum class WidgetType {
    TEXT, TEXTAREA, NUMBER, SELECT, RADIO, CHECKBOX, NOMENCLATURE, HTML, INCONNU,
    DATE, TIME, MULTISELECT, BOOL_CHECKBOX, BOOL_RADIO, TAXONOMY, OBSERVERS, OBSERVERS_TEXT, DATALIST,
}

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
    val nomenclatureOptions: List<OptionNomenclature> = emptyList(),
    /** `additional_attributes.hidden` : champ masqué, sa valeur part quand même, comme sur le web. */
    val masque: Boolean = false,
    /** `additional_attributes.cd_nomenclatures` : les seules valeurs proposées d'une nomenclature. */
    val cdNomenclatures: List<String> = emptyList(),
    /** `additional_attributes.min` et `max` : bornes d'un nombre. */
    val min: Double? = null,
    val max: Double? = null,
    /** La `value` de chaque option, en JSON (`1`, `"a"`, `true`), alignée sur [fieldValues] : le
     *  web enregistre la valeur avec son type. Vide sur un cache d'avant son ajout. */
    val valeursBrutes: List<String> = emptyList(),
    /** `help` : l'aide sous le champ. */
    val aide: String? = null,
    /** `definition` : l'info-bulle du libellé ; [lienDefinition] : son lien (`link_definition`). */
    val definition: String? = null,
    val lienDefinition: String? = null,
    /** `pattern` et `pattern_message` d'un texte ; `max_length`. */
    val motif: String? = null,
    val messageMotif: String? = null,
    val longueurMax: Int? = null,
    /** `rows` d'une zone de texte. */
    val lignes: Int? = null,
    /** `noNullOption` d'une liste : pas d'entrée vide « - ». */
    val sansOptionVide: Boolean = false,
    /** `nullDefault` d'une datalist : l'entrée « -- Aucun -- » quand elle n'est pas requise. */
    val avecAucun: Boolean = false,
    /** Le contenu d'un bloc `html`. */
    val html: String? = null,
    /** Choix multiple : `multi_select` (nomenclature, observateurs), `multiple` (datalist). */
    val choixMultiple: Boolean = false,
    /** `disabled` : champ grisé, que le web n'envoie pas. */
    val desactive: Boolean = false,
) {
    /** Le champ s'affiche : ni masqué, ni d'un widget que l'appli ne rend pas. Un bloc `html`
     *  s'affiche, sans valeur. */
    val affiche: Boolean get() = !masque && widget != WidgetType.INCONNU

    /** Le champ a une valeur (tous, sauf un bloc `html`). */
    val aUneValeur: Boolean get() = widget != WidgetType.HTML

    /** Les options d'une nomenclature, restreintes à [cdNomenclatures]. */
    val optionsProposees: List<OptionNomenclature>
        get() = if (cdNomenclatures.isEmpty()) nomenclatureOptions else nomenclatureOptions.filter { it.cd == null || it.cd in cdNomenclatures }

    fun appliqueA(codeObjet: String): Boolean = objectsCode.contains(codeObjet)

    /** Vrai si ce champ s'affiche pour le jeu de données [idDataset] du relevé, comme sur le web :
     *  un champ sans jeu de données vaut pour tous ; sinon, seulement pour les siens. [idList] ne
     *  restreint rien : le serveur ne filtre pas par liste, c'est un paramètre des widgets taxon et
     *  observateurs. */
    fun visiblePour(idDataset: Int?): Boolean =
        datasetsIds.isEmpty() || (idDataset != null && idDataset in datasetsIds)
}
