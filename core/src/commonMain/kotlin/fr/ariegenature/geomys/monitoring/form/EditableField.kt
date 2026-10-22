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

// Porté tel quel de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/monitoring/form/EditableField.kt),
// les commentaires qui décrivaient des vues Android ramenés à l'état du formulaire.

package fr.ariegenature.geomys.monitoring.form

/** Type de widget d'un champ de formulaire dynamique gn_module_monitoring. La correspondance
 *  avec le `type_widget` déclaré dans `/api/monitorings/config/<code>` est faite dans
 *  [mapperViewType] (WidgetMapping.kt). */
enum class ViewType {
    TEXT,
    TEXTAREA,
    NUMBER,
    DATE,
    /** Sélecteur d'heure. Valeur renvoyée : "HH:MM". */
    TIME,
    /** Date + heure (widget serveur `datetime`) : un choix de date puis d'heure. Valeur renvoyée :
     *  "yyyy-MM-dd HH:mm:ss" (format accepté par le backend GeoNature, cf. formatDateTime de
     *  gn_mobile_monitoring). */
    DATETIME,
    /** Autocomplétion TaxRef pour saisir un cd_nom, sur le cache local TaxRef ; la valeur renvoyée
     *  est le `cd_nom` (Int) résolu par TaxRefCache, ou null si le texte ne désigne aucun taxon. */
    TAXON,
    SELECT,
    /** Choix unique en boutons radio (widget serveur `radio`). Mêmes `values` qu'un SELECT, affiché
     *  comme un groupe de boutons (horizontal si peu d'options courtes, ex. Oui/Non) plutôt qu'une
     *  liste déroulante. Valeur renvoyée = la `value` de l'option cochée. */
    RADIO,
    /** Choix multiple : datalists `multiple: true` (observateurs, jeu de données multiple, types de
     *  sites) et widgets serveur `multiselect`. Dialogue à cases à cocher, valeur renvoyée =
     *  List<String>. */
    SELECT_MULTIPLE,
    /** Booléen : widgets serveur `bool_checkbox` / `bool` / `boolean`. Case à cocher, valeur
     *  renvoyée = Boolean. */
    CHECKBOX,
    /** Choix MULTIPLE en groupe de cases à cocher affichées DIRECTEMENT (widget serveur `checkbox` :
     *  une case par valeur de `values`, plusieurs cochables à la fois). À la différence de
     *  SELECT_MULTIPLE (dialogue), les cases sont visibles directement, comme sur le web GeoNature.
     *  Valeur renvoyée = List<String> des `value` cochées (null si aucune). */
    CHECKBOX_MULTIPLE,
    /** Pièces jointes (photos) — widget serveur `medias`. Valeur renvoyée par lireValeurs : la
     *  liste des URI des fichiers copiés localement. L'envoi effectif vers /api/gn_commons/media a
     *  lieu à l'envoi de la file des Suivis (OutboxEnvoi), après création de l'objet parent (le
     *  serveur attend uuid_attached_row pour l'y rattacher). */
    MEDIA,
    /** Texte du protocole (widget serveur `html`), affiché comme sur le web, sans saisie : il n'a pas
     *  de valeur et ne part pas au serveur. */
    HTML,
}

/** Valeur d'une option d'un widget SELECT, RADIO ou à cases. [value] est la valeur technique
 *  envoyée à l'API, [label] ce que voit l'utilisateur. */
data class PropertyValue(
    val value: String,
    val label: String,
    /** `cd_nomenclature` de l'option (datalists nomenclature uniquement, null sinon). Exposé au
     *  moteur d'expressions sous `<code>__cd` pour évaluer les conditions
     *  `meta.nomenclatures[value.X].cd_nomenclature == '…'` des schémas (loutre, blaireau…). */
    val cdNomenclature: String? = null,
    /** [value] avec son type JSON (nombre, booléen, texte), celui qu'envoie le formulaire web ;
     *  null quand l'option n'est connue que par son texte. */
    val brut: Any? = null,
)

/** Un champ éditable d'un formulaire dynamique. Construit à partir du schéma serveur
 *  ([fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet]). Mutable sur [value]
 *  uniquement — les autres champs sont des métadonnées de schéma. [value] est la valeur
 *  INITIALE ; la valeur courante vit dans [FormulaireEtat]. */
data class EditableField(
    val code: String,
    val viewType: ViewType,
    val label: String,
    var value: Any? = null,
    val values: List<PropertyValue> = emptyList(),
    val obligatoire: Boolean = false,
    /** Texte d'aide affiché sous le champ (le `help` du schéma serveur). */
    val aide: String? = null,
    /** Expression `hidden` du schéma serveur (forme Angular `${champ}` etc.). Évaluée par l'état
     *  du formulaire pour masquer ou afficher le champ selon les autres valeurs. */
    val hiddenExpr: String? = null,
    /** Expression `required` DYNAMIQUE du schéma (`({value}) => …`) : le champ n'est obligatoire
     *  que lorsque l'expression est vraie au regard des valeurs courantes (ex. champs végétation
     *  requis seulement au passage 2). Complète [obligatoire]. */
    val obligatoireExpr: String? = null,
    /** Pour les champs TAXON : id_liste UsersHub qui restreint les taxons proposés à
     *  l'autocomplétion. Vient typiquement de `schema.idListTaxonomy` du protocole ou de
     *  `dataset.idTaxaList`. Null = toutes les espèces du cache TaxRef proposées. */
    val idListeTaxonomieRestreinte: Int? = null,
    /** Borne min (brute serveur, cf [fr.ariegenature.geomys.network.MonitoringApi
     *  .MonitoringPropertySchema.minValue]). Évaluée par [ValidationExpr] pour afficher un message
     *  d'erreur sous le champ et bloquer l'enregistrement le cas échéant. */
    val minValue: String? = null,
    /** Borne max (cf [minValue]). */
    val maxValue: String? = null,
    /** Pour les widgets MEDIA : table Postgres à laquelle le média est rattaché dans
     *  gn_commons.t_medias (ex. `gn_monitoring.t_base_visits` pour les médias d'une visite). Lu
     *  depuis `schema_dot_table` du schéma serveur, résolu en id_table_location par
     *  /api/gn_commons/get_id_table_location/<schema_dot_table> au moment de l'envoi. */
    val schemaDotTable: String? = null,
    /** NUMBER uniquement : true si le widget serveur accepte les décimaux (`number`, `float`,
     *  `decimal` — par opposition à `integer`). Pilote le clavier et la lecture de la valeur. Sans
     *  lui, une mesure décimale (température, pH, recouvrement — RHOMEO…) était IMPOSSIBLE à taper,
     *  et une valeur décimale posée par une règle `change` était relue null → perdue en silence. */
    val decimal: Boolean = false,
    /** HTML uniquement : le contenu du widget (`html` du schéma). */
    val html: String? = null,
    /** `definition` du schéma : l'info-bulle du libellé sur le web. ([aide] : le `help`, sous le
     *  champ.) */
    val definition: String? = null,
    /** `pattern` d'un texte (le web l'encadre de `^…$`), son message ; `max_length`. */
    val motif: String? = null,
    val messageMotif: String? = null,
    val longueurMax: Int? = null,
    /** `rows` d'une zone de texte. */
    val lignes: Int? = null,
    /** SELECT : le libellé de l'entrée vide du web (« - » d'un `select`, « -- Aucun -- » d'une
     *  datalist à `nullDefault`), null s'il n'y en a pas. */
    val optionVide: String? = null,
    /** SELECT : une croix vide le choix (`clearable` d'une nomenclature, des observateurs, d'un jeu de
     *  données). */
    val effacable: Boolean = false,
    /** SELECT d'un vrai `select` : une valeur hors de ses options reste, grisée, comme sur le web. */
    val selectSimple: Boolean = false,
)
