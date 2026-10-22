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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/monitoring/form/ValidationExpr.kt) :
// Log devient Journal ; les expressions régulières sont compilées une fois.

package fr.ariegenature.geomys.monitoring.form

import fr.ariegenature.geomys.journal.Journal

/** Évaluateur pragmatique des bornes numériques `min`/`max` envoyées par gn_module_monitoring.
 *  Inspiré de `_resolveNumericBound` de gn_mobile_monitoring Flutter
 *  ([dynamic_form_builder.dart:1287]) : trois formes de borne sont acceptées :
 *
 *  - un littéral numérique (`0`, `100`, `3.14`) ;
 *  - une chaîne lisible comme nombre (`"0"`, `"3.14"`) ;
 *  - une lambda JS qui pointe vers un autre champ du formulaire :
 *    `({value}) => value.<champ>` ou `(value) => value.<champ>` ou `value.<champ>`.
 *
 *  Comme [HiddenExpr] et [ChangeRules], on ne tente PAS d'évaluer du JS arbitraire — les
 *  expressions non reconnues renvoient null (= « borne absente », pas de contrainte).
 *
 *  Cas d'usage typique : un couple `count_min` / `count_max` où le schéma déclare
 *  `count_min.max = "({value}) => value.count_max"` pour forcer la cohérence min ≤ max. */
object ValidationExpr {

    private val LAMBDA_PARENTHESES = Regex("""^\(\s*\{?\s*value\s*\}?\s*\)\s*=>\s*value\.(\w+)\s*$""")
    private val LAMBDA_NUE = Regex("""^value\s*=>\s*value\.(\w+)\s*$""")
    private val LAMBDA_CROCHETS = Regex("""^\(\s*\{?\s*value\s*\}?\s*\)\s*=>\s*value\[\s*['"](\w+)['"]\s*\]\s*$""")
    private val FORME_NUE = Regex("""^value\.(\w+)\s*$""")

    /** Résout la borne [brut] contre les [valeurs] courantes du formulaire.
     *  @return la borne sous forme Double, ou null si elle ne s'applique pas (entrée absente,
     *          expression non reconnue, ou champ référencé vide ou non numérique). */
    fun resoudreBorne(brut: String?, valeurs: Map<String, Any?>): Double? {
        if (brut.isNullOrBlank()) return null
        val s = brut.trim()
        // Littéral direct : 0, -3.14, 100, etc.
        s.toDoubleOrNull()?.let { return it }
        // Lambda fléchée → on extrait `value.<champ>` (ou `value["<champ>"]`) et on lit la valeur
        // courante du champ référencé dans le formulaire.
        val champRef = extraireChampReference(s) ?: return null
        return numerique(valeurs[champRef])
    }

    /** Reconnaît les variantes vues sur les protocoles GeoNature et renvoie le nom du champ
     *  référencé, ou null si la forme n'est pas comprise. */
    internal fun extraireChampReference(expr: String): String? {
        // `({value}) => value.<champ>` ou `(value) => value.<champ>`
        LAMBDA_PARENTHESES.matchEntire(expr)?.let { return it.groupValues[1] }
        // `value => value.<champ>` (sans parenthèses)
        LAMBDA_NUE.matchEntire(expr)?.let { return it.groupValues[1] }
        // `({value}) => value["<champ>"]` (notation crochets, plus rare)
        LAMBDA_CROCHETS.matchEntire(expr)?.let { return it.groupValues[1] }
        // Forme nue `value.<champ>` (parfois déclarée sans la lambda).
        FORME_NUE.matchEntire(expr)?.let { return it.groupValues[1] }
        Journal.w("ValidationExpr", "Borne non reconnue, contrainte ignorée : $expr")
        return null
    }

    /** Une valeur de formulaire en Double si possible. Cohérent avec [FormulaireEtat.lireValeurs],
     *  qui peut renvoyer Int (NUMBER), String (TEXT) ou null (champ vide). */
    private fun numerique(v: Any?): Double? = when (v) {
        null -> null
        is Number -> v.toDouble()
        is String -> v.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        else -> null
    }

    /** Une violation de contrainte sur un champ. [type] qualifie laquelle des deux bornes a été
     *  franchie, pour afficher un message adapté. */
    data class Violation(val type: Type, val borne: Double) {
        enum class Type { TROP_PETIT, TROP_GRAND }
    }

    /** Évalue les bornes contre une [valeurCourante] (sortie de lireValeurs). Renvoie une Violation
     *  si la valeur est non vide et hors bornes, null sinon (vide ou conforme). Un champ vide ne
     *  déclenche pas de violation : c'est `obligatoire` qui s'en occupe. */
    fun violation(
        valeurCourante: Any?,
        minBrut: String?,
        maxBrut: String?,
        valeursFormulaire: Map<String, Any?>,
    ): Violation? {
        val v = numerique(valeurCourante) ?: return null
        val min = resoudreBorne(minBrut, valeursFormulaire)
        val max = resoudreBorne(maxBrut, valeursFormulaire)
        // Comme le web (setControl) : des bornes croisées (min > max) ne s'appliquent ni l'une ni
        // l'autre.
        if (min != null && max != null && min > max) return null
        if (min != null && v < min) return Violation(Violation.Type.TROP_PETIT, min)
        if (max != null && v > max) return Violation(Violation.Type.TROP_GRAND, max)
        return null
    }
}
