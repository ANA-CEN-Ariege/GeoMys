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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/saisie/AdditionalFieldsRenderer.kt) :
// la logique des champs additionnels OccTax — valeurs au rendu, encodage relu par `collecter`,
// champs obligatoires vides — sort des vues, où elle vivait dans des tags. Gson devient kotlinx,
// org.json son portage commun. Les widgets deviennent des composables, qui lisent cet état.

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.store.io.listeDepuisJson
import fr.ariegenature.geomys.util.DateHeureDefaut

/** Les champs additionnels GeoNature (gn_commons.t_additional_fields) des saisies OccTax : relevé,
 *  occurrence et dénombrement. */
object ChampsAdditionnels {

    /** Le cache JSON des définitions (`GeoNatureConfig.additionalFieldsOcctaxJson`) ; vide s'il est
     *  absent ou illisible. */
    fun fromJson(json: String): List<AdditionalFieldDef> = listeDepuisJson(AdditionalFieldDef.serializer(), json)

    /** Vrai s'il existe au moins un champ additionnel de relevé visible **obligatoire ET SANS valeur
     *  par défaut** — seul cas qui justifie d'intercaler l'écran « Détails du relevé » avant la
     *  saisie. Un champ requis AVEC défaut est déjà satisfait, et tous ces champs restent éditables
     *  par le bouton « Détails » de l'écran de saisie. */
    fun aDesChampsReleveRequisSansDefaut(additionalFieldsOcctaxJson: String, idDataset: Int?): Boolean =
        fromJson(additionalFieldsOcctaxJson)
            .filter { it.appliqueA(AdditionalFieldsObject.RELEVE) }
            .filter { it.visiblePour(idDataset, emptyList()) }
            .any { it.required && it.defaultValue.isNullOrBlank() }

    /** Valeurs par défaut des champs additionnels de relevé visibles (`fieldName` → defaultValue non
     *  vide). Sert à pré-remplir la session quand on SAUTE l'écran « Détails du relevé » : un champ
     *  avec défaut (notamment requis) porte ainsi sa valeur jusqu'à l'envoi sans qu'on ait à ouvrir
     *  « Détails ». */
    fun defautsChampsReleve(additionalFieldsOcctaxJson: String, idDataset: Int?): Map<String, String> =
        fromJson(additionalFieldsOcctaxJson)
            .filter { it.appliqueA(AdditionalFieldsObject.RELEVE) }
            .filter { it.visiblePour(idDataset, emptyList()) }
            // Pas de pré-remplissage des CHECKBOX depuis un default_value scalaire : le web ne le fait
            // pas pour les champs additionnels (une case se retrouverait cochée et envoyée à tort).
            .filter { it.widget != WidgetType.CHECKBOX }
            .mapNotNull { def -> def.defaultValue?.takeIf { it.isNotBlank() }?.let { def.fieldName to it } }
            .toMap()

    /** Défaut date/heure des widgets serveur `date`, `time` et `datetime` (rendus en champ texte) :
     *  date du jour, heure actuelle, ou les deux. Null pour les autres widgets. */
    fun defautDateHeurePour(widgetServeur: String): String? = when (widgetServeur.lowercase()) {
        "date" -> DateHeureDefaut.dateDuJour()
        "time" -> DateHeureDefaut.heureActuelle()
        "datetime", "date-time", "timestamp" -> DateHeureDefaut.dateHeureActuelle()
        else -> null
    }

    /** Codes pré-cochés d'une case à cocher multiple : UNIQUEMENT depuis une vraie valeur enregistrée
     *  (tableau JSON `["1","3"]`). Une valeur scalaire est le `default_value` du serveur, que le web
     *  N'APPLIQUE PAS comme pré-cochage d'un champ additionnel → rien n'est coché (parité web). */
    fun parseCodesCoches(courant: String): Set<String> {
        if (courant.isBlank()) return emptySet()
        return try {
            val arr = JSONArray(courant)
            (0 until arr.length()).map { arr.get(it).toString() }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** Case à cocher MULTIPLE (comme le web) : plus d'une valeur proposée. Sinon, une case unique
     *  vrai/faux. */
    fun estCasesMultiples(def: AdditionalFieldDef): Boolean = def.widget == WidgetType.CHECKBOX && def.fieldValues.size > 1

    /** Les valeurs envoyées d'une liste (SELECT, RADIO) : les codes serveur (`field_values[i].value`)
     *  quand ils sont alignés sur les libellés, sinon les libellés eux-mêmes (anciens caches,
     *  field_values en chaînes nues). */
    fun codesListe(def: AdditionalFieldDef): List<String> =
        if (def.fieldValueCodes.size == def.fieldValues.size) def.fieldValueCodes else def.fieldValues

    /** Les codes des cases d'une case à cocher multiple, alignés sur les libellés (repli : le
     *  libellé). */
    fun codesCases(def: AdditionalFieldDef): List<String> =
        def.fieldValues.mapIndexed { i, libelle -> def.fieldValueCodes.getOrElse(i) { libelle } }

    /** Les entrées d'une liste déroulante (SELECT, NOMENCLATURE) : « (non renseigné) » d'abord, et
     *  leur code (vide pour la première). */
    fun libellesListe(def: AdditionalFieldDef): List<String> = when (def.widget) {
        WidgetType.NOMENCLATURE -> listOf(NON_RENSEIGNE) + def.nomenclatureOptions.map { it.second }
        else -> listOf(NON_RENSEIGNE) + def.fieldValues
    }

    fun codesListeDeroulante(def: AdditionalFieldDef): List<String> = when (def.widget) {
        WidgetType.NOMENCLATURE -> listOf("") + def.nomenclatureOptions.map { it.first }
        else -> listOf("") + codesListe(def)
    }

    /** L'entrée vide des listes déroulantes. */
    const val NON_RENSEIGNE = "(non renseigné)"
}

/**
 * L'état des champs additionnels d'un écran de saisie OccTax ([defs], dans leur ordre), à partir des
 * [valeurs] déjà saisies (`fieldName` → valeur, null toléré).
 *
 * La valeur d'un champ au rendu : la valeur saisie, sinon le défaut serveur, sinon la date ou
 * l'heure du jour pour les widgets date et heure ; la chaîne "null" vaut une valeur vide (le champ
 * s'affichait sinon pré-rempli avec « null », déroutant).
 *
 * [collecter] rend les valeurs encodées comme AdditionalFieldsRenderer.collecter d'Android, que
 * l'envoi relit (GeoNatureUpload.jsonDepuisMap) :
 * - texte (TEXT, TEXTAREA, NUMBER, widget inconnu) : le texte ;
 * - liste déroulante (SELECT, NOMENCLATURE) et boutons radio : le code choisi, "" si aucun ;
 * - case à cocher multiple : le tableau JSON des codes cochés (`["1","3"]`) ;
 * - case unique : "true" ou "false".
 */
class EtatChampsAdditionnels(
    val defs: List<AdditionalFieldDef>,
    valeurs: Map<String, String>?,
    defautDateHeure: (String) -> String? = ChampsAdditionnels::defautDateHeurePour,
) {

    private sealed class Saisie {
        class Texte(var texte: String) : Saisie()

        /** Liste déroulante : la position dans [ChampsAdditionnels.codesListeDeroulante], 0 = vide. */
        class Liste(var position: Int) : Saisie()

        /** Boutons radio : le code sélectionné. Choix unique MAIS désélectionnable : recliquer la
         *  valeur active la décoche (sémantique « coché / pas coché », cf. toggleValueRadio du web). */
        class Radio(var selection: String?) : Saisie()

        class Cases(val cochees: BooleanArray) : Saisie()

        class Case(var cochee: Boolean) : Saisie()
    }

    private val saisies = linkedMapOf<String, Saisie>()

    init {
        defs.forEach { def ->
            val brut = valeurs?.get(def.fieldName)
                ?: def.defaultValue
                ?: defautDateHeure(def.widgetServeur)
                ?: ""
            val courant = if (brut == "null") "" else brut
            saisies[def.fieldName] = when (def.widget) {
                WidgetType.SELECT, WidgetType.NOMENCLATURE ->
                    Saisie.Liste(ChampsAdditionnels.codesListeDeroulante(def).indexOf(courant).coerceAtLeast(0))
                WidgetType.RADIO -> Saisie.Radio(courant.takeIf { it.isNotEmpty() })
                WidgetType.CHECKBOX -> if (ChampsAdditionnels.estCasesMultiples(def)) {
                    val coches = ChampsAdditionnels.parseCodesCoches(courant)
                    Saisie.Cases(ChampsAdditionnels.codesCases(def).map { it in coches }.toBooleanArray())
                } else {
                    Saisie.Case(courant.equals("true", ignoreCase = true))
                }
                else -> Saisie.Texte(courant)
            }
        }
    }

    /** Les valeurs courantes, indexées par fieldName. */
    fun collecter(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        defs.forEach { def -> result[def.fieldName] = valeurChamp(def) }
        return result
    }

    /** Libellés des champs OBLIGATOIRES laissés vides : bloque la validation OccTax (parité avec la
     *  garde du formulaire des Suivis). Les cases à cocher sont exclues (toujours vrai ou faux, jamais
     *  « vides »). */
    fun champsObligatoiresVides(): List<String> =
        defs.filter { it.required && it.widget != WidgetType.CHECKBOX && valeurChamp(it).isBlank() }.map { it.fieldLabel }

    private fun valeurChamp(def: AdditionalFieldDef): String = when (val s = saisies.getValue(def.fieldName)) {
        is Saisie.Texte -> s.texte
        is Saisie.Liste -> ChampsAdditionnels.codesListeDeroulante(def).getOrNull(s.position) ?: ""
        is Saisie.Radio -> ChampsAdditionnels.codesListe(def).firstOrNull { it == s.selection } ?: ""
        is Saisie.Cases -> {
            val codes = ChampsAdditionnels.codesCases(def)
            JSONArray(codes.filterIndexed { i, _ -> s.cochees[i] }).toString()
        }
        is Saisie.Case -> if (s.cochee) "true" else "false"
    }

    // ── Affichage ───────────────────────────────────────────────────────────────────────────────

    /** Le texte d'un champ texte. */
    fun texte(fieldName: String): String = (saisies[fieldName] as? Saisie.Texte)?.texte.orEmpty()

    /** La position choisie d'une liste déroulante (0 = « (non renseigné) »). */
    fun position(fieldName: String): Int = (saisies[fieldName] as? Saisie.Liste)?.position ?: 0

    /** Le code du bouton radio sélectionné, ou null. */
    fun selectionRadio(fieldName: String): String? = (saisies[fieldName] as? Saisie.Radio)?.selection

    /** L'état des cases (une seule pour une case unique). */
    fun cases(fieldName: String): List<Boolean> = when (val s = saisies[fieldName]) {
        is Saisie.Cases -> s.cochees.toList()
        is Saisie.Case -> listOf(s.cochee)
        else -> emptyList()
    }

    /** L'indication d'un champ de widget inconnu, saisi en texte. */
    fun indication(def: AdditionalFieldDef): String? =
        if (def.widget == WidgetType.INCONNU && def.widgetServeur.isNotEmpty()) "Type ${def.widgetServeur} (saisi en texte)" else null

    // ── Saisies ─────────────────────────────────────────────────────────────────────────────────

    fun saisirTexte(fieldName: String, texte: String) {
        (saisies[fieldName] as? Saisie.Texte)?.texte = texte
    }

    fun choisirPosition(fieldName: String, position: Int) {
        (saisies[fieldName] as? Saisie.Liste)?.position = position
    }

    /** Clic sur le bouton radio [code] : le coche, ou le décoche s'il l'était. */
    fun cliquerRadio(fieldName: String, code: String) {
        val s = saisies[fieldName] as? Saisie.Radio ?: return
        s.selection = if (s.selection == code) null else code
    }

    fun cocherCase(fieldName: String, index: Int, coche: Boolean) {
        val s = saisies[fieldName] as? Saisie.Cases ?: return
        if (index in s.cochees.indices) s.cochees[index] = coche
    }

    fun cocher(fieldName: String, coche: Boolean) {
        (saisies[fieldName] as? Saisie.Case)?.cochee = coche
    }
}
