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

// Porté tel quel de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/monitoring/form/WidgetMapping.kt).

package fr.ariegenature.geomys.monitoring.form

import fr.ariegenature.geomys.network.MonitoringApi

/** Un champ espèce : le widget `taxonomy` du web, ou une datalist de taxons (`type_util` taxonomy,
 *  api `taxref/allnamebylist/<id>`), cherchée dans le cache TaxRef ; les valeurs restent les cd_nom
 *  de la liste, comme sur le web. Un autre widget reste ce qu'il est, même nommé `cd_nom`. */
private fun estChampTaxon(prop: MonitoringApi.MonitoringPropertySchema): Boolean {
    val widget = prop.typeWidget.lowercase()
    if (widget == "taxonomy") return true
    return widget == "datalist" && (prop.typeUtil?.lowercase() == "taxonomy" || prop.apiUrl?.contains("allnamebylist/") == true)
}

/** true si le widget numérique accepte les décimaux : `number`, l'`<input type="number">` du web,
 *  qui les accepte. Pilote le clavier ET la lecture de la valeur (cf. EditableField.decimal). */
fun estWidgetDecimal(typeWidget: String): Boolean = typeWidget.lowercase() == "number"

/** Convertit un schéma de propriété gn_module_monitoring vers le [ViewType] du formulaire, pour les
 *  seuls widgets que rend le formulaire dynamique du web (dynamic-form.component.html) ; le choix
 *  multiple est lu comme le composant web du widget ([MonitoringApi.MonitoringPropertySchema.multiple]).
 *  Renvoie null pour les widgets que l'appli ne rend pas. */
fun mapperViewType(prop: MonitoringApi.MonitoringPropertySchema): ViewType? {
    // Taxonomie détectée par plusieurs signaux (cf. estChampTaxon) — prioritaire, car un champ taxon
    // peut être déclaré en `datalist` côté serveur.
    if (estChampTaxon(prop)) return ViewType.TAXON
    return when (prop.typeWidget.lowercase()) {
        "text" -> ViewType.TEXT
        "textarea" -> ViewType.TEXTAREA
        // `pnx-observers-text` : un champ d'une ligne.
        "observers-text" -> ViewType.TEXT
        "number" -> ViewType.NUMBER
        "date" -> ViewType.DATE
        "time" -> ViewType.TIME
        "select" -> ViewType.SELECT
        // `radio` : choix unique en boutons radio, comme le web ; `bool_radio` (déprécié) : vrai pour
        // le premier libellé, faux pour les autres.
        "radio", "bool_radio" -> ViewType.RADIO
        "multiselect" -> ViewType.SELECT_MULTIPLE
        // `checkbox` = GROUPE de cases à cocher à CHOIX MULTIPLE (une case par valeur de `values`,
        // plusieurs cochables), affiché directement comme sur le web GeoNature — et NON un booléen.
        // Le booléen unique, lui, c'est `bool_checkbox` / `bool_radio` (cf. GeoNature
        // dynamic-form.component.html : *ngSwitchCase 'checkbox' rend N cases, 'bool_checkbox' rend
        // UNE case). Rendre `checkbox` en case unique écrasait un choix multiple en un simple
        // oui/non — ex. POPReptile « abondance » (« à vue » / « sur/sous plaques ») n'affichait
        // qu'une case au lieu de deux.
        "checkbox" -> ViewType.CHECKBOX_MULTIPLE
        // Booléen (case unique vrai/faux).
        "bool_checkbox" -> ViewType.CHECKBOX
        // Widgets de type « liste alimentée par API » : tous unifiés sur SELECT(_MULTIPLE). Le
        // préchargement leur donne des `values` toutes prêtes depuis l'API déclarée dans le schéma
        // (api, keyLabel, keyValue, data_path).
        "datalist", "nomenclature", "observers", "dataset" ->
            if (prop.multiple) ViewType.SELECT_MULTIPLE else ViewType.SELECT
        // Taxonomie : déjà captée par estChampTaxon ci-dessus, conservée pour la lisibilité.
        "taxonomy" -> ViewType.TAXON
        // `html` : texte du protocole, affiché comme sur le web, sans saisie.
        "html" -> ViewType.HTML
        // `medias` : pièces jointes — envoi différé vers gn_commons après création de l'objet (cf.
        // OutboxEnvoi.envoyerUne).
        "medias" -> ViewType.MEDIA
        // Widget inconnu : le formulaire web n'affiche rien pour lui (aucun cas du générateur de
        // formulaires) ; il est écarté et signalé.
        else -> null
    }
}

/** Une liste d'[EditableField] construite à partir d'un object_type du schéma serveur, et les
 *  propriétés écartées (widgets non saisissables), pour pouvoir les signaler. */
data class FormulaireConstruction(
    val fields: List<EditableField>,
    val ignores: List<Pair<String, String>>, // (nom de propriété, type_widget)
)

/** L'ordre des champs du formulaire web (monitoring-form.component.ts, initObjFormDefiniton puis
 *  sortObjFormDefinition) : l'ordre du schéma (specific, puis generic), les champs `medias` et
 *  `types_site` renvoyés à la fin, puis ceux de `display_properties` en tête, dans leur ordre ; un
 *  tri stable laisse les autres à leur place. */
internal fun ordreDesChamps(noms: List<String>, displayProperties: List<String>): List<String> {
    val enFin = setOf("medias", "types_site")
    val parSchema = noms.filter { it !in enFin } + listOf("medias", "types_site").filter { it in noms }
    val rang = displayProperties.withIndex().associate { (i, nom) -> nom to i }
    return parSchema.sortedBy { rang[it] ?: Int.MAX_VALUE }
}

fun construireFormulaire(schemaObjet: MonitoringApi.MonitoringSchemaObjet): FormulaireConstruction {
    // Comme le formulaire web : toutes les propriétés du schéma fusionné generic+specific qui ont un
    // type_widget (le parseur écarte les autres), sauf celles masquées, dans l'ordre du web
    // (display_form n'est PAS utilisé). Le champ du parent est retiré par l'appelant.
    val ordre = ordreDesChamps(schemaObjet.properties.keys.toList(), schemaObjet.displayProperties)
    val fields = mutableListOf<EditableField>()
    val ignores = mutableListOf<Pair<String, String>>()
    ordre.forEach { nom ->
        val prop = schemaObjet.properties[nom] ?: return@forEach
        val widget = prop.typeWidget.lowercase()
        // Les propriétés `hidden:true` du schéma serveur restent dans le MonitoringSchemaObjet pour
        // le corps du POST (le serveur Marshmallow les attend) mais ne sont pas affichées.
        if (prop.hiddenBool) return@forEach
        val viewType = mapperViewType(prop)
        if (viewType == null) {
            ignores.add(prop.nom to prop.typeWidget)
            return@forEach
        }
        fields.add(
            EditableField(
                code = prop.nom,
                viewType = viewType,
                // `pnx-observers-text` porte son propre libellé, « Observateurs ».
                label = if (widget == "observers-text") LIBELLE_OBSERVATEURS_TEXTE else prop.label,
                values = if (prop.typeWidget.equals("bool_radio", ignoreCase = true)) {
                    prop.valeurs.mapIndexed { i, (_, l) -> PropertyValue((i == 0).toString(), l, brut = i == 0) }
                } else {
                    prop.valeurs.map { (v, l) -> PropertyValue(v, l, brut = prop.valeursBrutes[v]) }
                },
                obligatoire = prop.obligatoire,
                obligatoireExpr = prop.obligatoireExpr,
                aide = prop.aide,
                definition = prop.definition,
                motif = prop.motif.takeIf { widget == "text" },
                messageMotif = prop.messageMotif,
                longueurMax = prop.longueurMax,
                lignes = prop.lignes,
                optionVide = when {
                    viewType != ViewType.SELECT -> null
                    widget == "select" -> "-".takeUnless { prop.sansOptionVide }
                    widget == "datalist" -> "-- Aucun --".takeIf { prop.avecAucun && !prop.obligatoire }
                    else -> null
                },
                effacable = viewType == ViewType.SELECT && widget in setOf("nomenclature", "observers", "dataset"),
                selectSimple = viewType == ViewType.SELECT && widget == "select",
                hiddenExpr = prop.hiddenExpr,
                // Pour les widgets TAXON, la liste portée par le champ lui-même, seule, comme le web :
                // l'autocomplétion s'y limite. Sans elle, tout TaxRef (le téléphone charge alors toutes
                // les listes, cf. listesDesSuivis) ; ni celle du type d'objet ni celle du module ne la
                // remplacent plus (21 octobre).
                idListeTaxonomieRestreinte = if (viewType == ViewType.TAXON) prop.idListTaxonomie else null,
                // Bornes numériques (NUMBER uniquement — ignorées sinon), transmises brutes : un
                // littéral est résolu immédiatement, une expression `(value) => value.X` contre les
                // valeurs courantes au moment de la validation.
                minValue = if (viewType == ViewType.NUMBER) prop.minValue else null,
                maxValue = if (viewType == ViewType.NUMBER) prop.maxValue else null,
                // MEDIA uniquement : table cible dans gn_commons pour résoudre id_table_location à
                // l'envoi du fichier, après création de l'objet parent.
                schemaDotTable = if (viewType == ViewType.MEDIA) prop.schemaDotTable else null,
                decimal = viewType == ViewType.NUMBER && estWidgetDecimal(prop.typeWidget),
                html = if (viewType == ViewType.HTML) prop.html.orEmpty() else null,
            )
        )
    }
    return FormulaireConstruction(fields, ignores)
}

/** Le libellé d'un champ `observers-text` sur le web (la clé « Observers »). */
const val LIBELLE_OBSERVATEURS_TEXTE = "Observateurs"
