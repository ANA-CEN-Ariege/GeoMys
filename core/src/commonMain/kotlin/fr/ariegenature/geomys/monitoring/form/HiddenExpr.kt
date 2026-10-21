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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/monitoring/form/HiddenExpr.kt) :
// RegexOption.DOT_MATCHES_ALL (absente du Regex commun) devient le drapeau « (?s) », Log devient
// Journal. Les expressions régulières sont compilées une fois, et non à chaque évaluation : même
// grammaire, mais un formulaire réévalue tous ses champs à chaque saisie.

package fr.ariegenature.geomys.monitoring.form

import fr.ariegenature.geomys.journal.Journal

/** Évaluateur des expressions conditionnelles envoyées par gn_module_monitoring dans les clés
 *  `hidden`/`display`/`required` du schéma (lambdas JS `({value}) => …`). On ne peut pas évaluer
 *  du JS arbitraire dans l'appli — on couvre la grammaire observée sur l'ensemble des protocoles
 *  de l'instance (audit des 33 schémas, 2026-06) :
 *
 *  - champ seul / négation / parenthèses : `!value.x`, `!(value.x == 2)`
 *  - comparaisons : `==`, `===`, `!=`, `!==`, `>`, `<`, `>=`, `<=` (champ, littéral entre
 *    guillemets, nombre, true/false, null de chaque côté) ; pour l'ordre, la conversion du JS (un
 *    champ vide vaut 0, un texte non numérique ne se compare à aucun nombre)
 *  - `meta.dataset` : le nombre de jeux de données proposés ([NB_JEUX_DE_DONNEES])
 *  - combinaisons : `&&`, `||` (précédence JS : && lie plus fort)
 *  - `['a','b',null].includes(value.x)`
 *  - chemins imbriqués aplatis : `value.cd_nom.cd_nom` → valeur du champ `cd_nom` (les valeurs de
 *    formulaire de l'appli sont scalaires — le cd_nom du taxon, etc.)
 *
 *  Les expressions hors grammaire (typiquement `meta.nomenclatures[...]`) sont traitées comme
 *  `false` (champ visible / non requis) et tracées dans le journal. La propagation est stricte :
 *  un sous-terme non reconnu rend l'expression entière non reconnue — on préfère montrer un champ
 *  en trop que d'en cacher un à tort.
 *
 *  Conventions :
 *  - « Vrai » pour un Boolean = `true`, pour une String = non vide et différente de "false"/"0",
 *    pour un Number ≠ 0, pour une Collection = non vide.
 *  - [masquer] renvoie `true` quand le champ doit être **masqué** (sémantique `hidden`). */
object HiddenExpr {

    /** Le pseudo-champ du nombre de jeux de données proposés, que fournit l'état du formulaire :
     *  `meta.dataset` (jeux connus du web) y est ramené. */
    const val NB_JEUX_DE_DONNEES = "__meta_dataset_nb"

    private val LAMBDA = Regex("""(?s)^\s*(?:\(\s*\{[^}]*\}\s*\)|\(\s*value\s*\)|value)\s*=>\s*(.+?)\s*$""")
    private val BLOC_RETURN = Regex("""(?s)^\{\s*return\s+(.+?)\s*;?\s*\}$""")
    private val FONCTION = Regex("""(?s)^\s*function\s*\(\s*\{?\s*value\s*\}?\s*\)\s*\{\s*return\s+(.+?)\s*;?\s*\}\s*$""")
    private val NULL_OU_UNDEFINED = Regex("""\(\s*null\s*\|\|\s*undefined\s*\)""")
    private val META_NOMENCLATURE_GARDEE =
        Regex("""\(\s*meta\.nomenclatures\[\s*value\.(\w+)\s*\]\s*\|\|\s*\{\s*\}\s*\)\s*\.cd_nomenclature""")
    private val META_NOMENCLATURE = Regex("""meta\.nomenclatures\[\s*value\.(\w+)\s*\]\s*\.cd_nomenclature""")
    private val META_DATASET_NB = Regex("""Object\.keys\(\s*meta\.dataset\s*\)\.length""")
    private val META_DATASET = Regex("""meta\.dataset\b""")
    private val CHEMIN_IMBRIQUE = Regex("""value\.(\w+)(?:\.\w+)+""")
    private val VALUE_POINT = Regex("""value\.(\w+)""")
    private val VALUE_CROCHETS = Regex("""value\[['"](\w+)['"]\]""")
    private val NOT = Regex("""^not\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val INCLUDES = Regex("""^\[(.*)\]\s*\.includes\(\s*\$\{(\w+)\}\s*\)$""")
    private val CHAMP = Regex("""^\$\{(\w+)\}$""")
    private val QUOTE_SIMPLE = Regex("""^'([^']*)'$""")
    private val QUOTE_DOUBLE = Regex("""^"([^"]*)"$""")
    private val COMPARAISONS = listOf("===", "!==", "==", "!=", ">=", "<=", ">", "<")

    /** Évalue une expression booléenne quelconque du schéma contre les valeurs courantes — même
     *  grammaire que `hidden`, utilisée aussi pour le `required` dynamique
     *  (`({value}) => value.num_passage == 2`). Expression non reconnue → false (champ non
     *  requis : on ne bloque pas la saisie sur une grammaire inconnue). */
    fun evaluerBooleen(expression: String?, valeurs: Map<String, Any?>): Boolean =
        masquer(expression, valeurs)

    /** Évalue l'expression sur l'ensemble des valeurs courantes du formulaire.
     *  @return true → masquer le champ, false → l'afficher. */
    fun masquer(expression: String?, valeurs: Map<String, Any?>): Boolean {
        if (expression.isNullOrBlank()) return false
        val expr = normaliser(expression.trim())
        val resultat = evaluer(expr, valeurs)
        if (resultat == null) {
            Journal.w("HiddenExpr", "Expression non reconnue, champ traité comme visible : $expression")
            return false
        }
        return resultat
    }

    /** Reconnaît plusieurs formats serveur et les ramène à `${champ}` :
     *  - Lambda fléchée : `({value}) => corps`, `({value, meta}) => corps`, `(value) => corps`
     *  - `function(value) { return corps; }`
     *  - Chemins imbriqués `value.a.b` → `${a}` (valeurs scalaires dans l'appli)
     *  - Forme déjà standard `${...}` → inchangée. */
    internal fun normaliser(expr: String): String {
        var corps = expr
        // Extrait le corps des lambdas fléchées, y compris à paramètres déstructurés multiples :
        // `({value}) =>`, `({value, meta}) =>`, `({meta, value}) =>`, `(value) =>`, `value =>`. On
        // supprime aussi le `return` final éventuel.
        val lambda = LAMBDA.matchEntire(expr)
        if (lambda != null) corps = lambda.groupValues[1]
        // Corps en bloc : `{ return X; }`
        BLOC_RETURN.matchEntire(corps.trim())?.let { corps = it.groupValues[1] }
        // function(value) { return X; }
        FONCTION.matchEntire(corps)?.let { corps = it.groupValues[1] }

        // Idiome de générateur de protocole : `(null || undefined)` ≡ null (petite chouette de
        // montagne : `value.cd_nom != (null || undefined) ? … : …`).
        corps = corps.replace(NULL_OU_UNDEFINED, "null")
        // `meta.nomenclatures[value.X].cd_nomenclature` (avec ou sans la garde `|| {}`) → `${X__cd}` :
        // le cd_nomenclature de l'option choisie du champ X, exposé par l'état du formulaire (cf.
        // FormulaireEtat.valeursPourExpressions). Protocoles loutre / blaireau / Camphi : sexe,
        // stade de vie… visibles ou requis selon la technique d'observation. Doit passer AVANT les
        // remplacements `value.X` génériques.
        corps = corps.replace(META_NOMENCLATURE_GARDEE) { "\${${it.groupValues[1]}__cd}" }
        corps = corps.replace(META_NOMENCLATURE) { "\${${it.groupValues[1]}__cd}" }
        // `meta.dataset && Object.keys(meta.dataset).length == 1` (jeu de données masqué quand il n'y
        // en a qu'un) : le nombre de jeux de données proposés, vrai dès qu'il y en a un.
        corps = corps.replace(META_DATASET_NB) { "\${$NB_JEUX_DE_DONNEES}" }
        corps = corps.replace(META_DATASET) { "\${$NB_JEUX_DE_DONNEES}" }
        // Chemins imbriqués `value.cd_nom.cd_nom` → ${cd_nom} : les valeurs du formulaire sont
        // scalaires dans l'appli (le champ taxon PORTE le cd_nom), là où le web manipule un objet.
        // Doit passer AVANT le remplacement simple.
        corps = corps.replace(CHEMIN_IMBRIQUE) { "\${${it.groupValues[1]}}" }
        // Remplace `value.champ` ou `value["champ"]` par `${champ}`.
        corps = corps.replace(VALUE_POINT) { "\${${it.groupValues[1]}}" }
        corps = corps.replace(VALUE_CROCHETS) { "\${${it.groupValues[1]}}" }
        return corps.trim()
    }

    /** Évaluation récursive d'une expression normalisée. Null = forme non reconnue. */
    private fun evaluer(exprBrut: String, valeurs: Map<String, Any?>): Boolean? {
        var expr = exprBrut.trim()
        if (expr.isEmpty()) return null
        // Parenthèses englobantes : `(X)` → X, répété tant qu'elles enveloppent TOUT le corps.
        while (expr.length >= 2 && expr.startsWith("(") && expr.endsWith(")") &&
            ExpressionScanner.parenthesesEquilibrees(expr.substring(1, expr.length - 1))
        ) {
            expr = expr.substring(1, expr.length - 1).trim()
        }

        // Ternaire `cond ? a : b` — précédence la plus basse en JS, donc testé en premier (petite
        // chouette de montagne : `value.cd_nom != null ? value.cd_nom != 3507 : true`).
        val idxQuestion = ExpressionScanner.indexNiveauZero(expr, "?")
        if (idxQuestion != null) {
            val idxDeuxPoints = ExpressionScanner.indexNiveauZero(expr, ":", depuis = idxQuestion + 1)
            if (idxDeuxPoints != null) {
                val condition = evaluer(expr.substring(0, idxQuestion), valeurs) ?: return null
                val branche = if (condition) expr.substring(idxQuestion + 1, idxDeuxPoints)
                else expr.substring(idxDeuxPoints + 1)
                return evaluer(branche, valeurs)
            }
        }

        // `||` puis `&&` (précédence JS : && lie plus fort que ||, donc on découpe sur || d'abord).
        // Découpe au niveau 0 uniquement (hors parenthèses, crochets et guillemets).
        ExpressionScanner.decouperNiveauZero(expr, "||")?.let { parties ->
            val evals = parties.map { evaluer(it, valeurs) ?: return null }
            return evals.any { it }
        }
        ExpressionScanner.decouperNiveauZero(expr, "&&")?.let { parties ->
            val evals = parties.map { evaluer(it, valeurs) ?: return null }
            return evals.all { it }
        }

        // Négation `!X` (et `not X`).
        if (expr.startsWith("!") && !expr.startsWith("!=")) {
            return evaluer(expr.substring(1), valeurs)?.let { !it }
        }
        NOT.matchEntire(expr)?.let { m ->
            return evaluer(m.groupValues[1], valeurs)?.let { !it }
        }

        // `[litt, litt, …].includes(${champ})` — éléments entre guillemets, numériques ou null.
        // NB : un élément `null` correspond à un champ vide (les contrôles Angular non remplis valent
        // null, c'est l'idiome utilisé par les protocoles pour « caché tant que non choisi »).
        INCLUDES.matchEntire(expr)?.let { m ->
            val brutItems = m.groupValues[1]
            val morceaux = ExpressionScanner.decouperNiveauZero(brutItems, ",") ?: listOf(brutItems)
            val items = morceaux.map { resoudreOperande(it.trim(), valeurs) ?: return null }
            val v = valeurs[m.groupValues[2]]
            return items.any { item ->
                when {
                    item.valeur == null -> v == null || v.toString().isEmpty()
                    else -> egalite(Operande(v, true), item)
                }
            }
        }

        // Comparaison binaire au niveau 0 : ===, !==, ==, !=, >=, <=, >, <.
        for (op in COMPARAISONS) {
            val idx = ExpressionScanner.indexNiveauZero(expr, op) ?: continue
            val gauche = resoudreOperande(expr.substring(0, idx).trim(), valeurs) ?: return null
            val droite = resoudreOperande(expr.substring(idx + op.length).trim(), valeurs) ?: return null
            return when (op) {
                "===", "==" -> egalite(gauche, droite)
                "!==", "!=" -> !egalite(gauche, droite)
                else -> {
                    val (va, vb) = gauche.valeur to droite.valeur
                    // Deux textes non numériques : l'ordre des chaînes, comme en JS.
                    val ordre = if (va is String && vb is String && nombreJs(va) == null && nombreJs(vb) == null) {
                        va.compareTo(vb)
                    } else {
                        // Sinon la conversion numérique du JS : un champ vide vaut 0 (`null < 5` est
                        // vrai), un texte non numérique ne se compare à rien (NaN).
                        val g = nombreJs(va) ?: return false
                        val d = nombreJs(vb) ?: return false
                        g.compareTo(d)
                    }
                    when (op) {
                        ">=" -> ordre >= 0
                        "<=" -> ordre <= 0
                        ">" -> ordre > 0
                        else -> ordre < 0
                    }
                }
            }
        }

        // Termes simples : `${champ}` (vérité JS), littéraux booléens.
        CHAMP.matchEntire(expr)?.let { m ->
            return truthy(valeurs[m.groupValues[1]])
        }
        if (expr.equals("true", ignoreCase = true)) return true
        if (expr.equals("false", ignoreCase = true)) return false

        return null
    }

    /** Opérande résolu : valeur + marqueur « reconnu ». */
    private data class Operande(val valeur: Any?, val connu: Boolean)

    /** `${champ}` → valeur du formulaire ; `'litt'`/"litt" → String ; nombre → Double ;
     *  true/false/null → littéraux. Null (Kotlin) = non reconnu. */
    private fun resoudreOperande(brut: String, valeurs: Map<String, Any?>): Operande? {
        val s = brut.trim()
        CHAMP.matchEntire(s)?.let { return Operande(valeurs[it.groupValues[1]], true) }
        QUOTE_SIMPLE.matchEntire(s)?.let { return Operande(it.groupValues[1], true) }
        QUOTE_DOUBLE.matchEntire(s)?.let { return Operande(it.groupValues[1], true) }
        s.toDoubleOrNull()?.let { return Operande(it, true) }
        if (s == "null" || s == "undefined") return Operande(null, true)
        if (s.equals("true", ignoreCase = true)) return Operande(true, true)
        if (s.equals("false", ignoreCase = true)) return Operande(false, true)
        return null
    }

    /** Égalité « à la JS == » : numérique quand les deux côtés sont numérisables ("2" == 2.0),
     *  sinon comparaison de chaînes ; null ne vaut que null ou "". */
    private fun egalite(a: Operande, b: Operande): Boolean {
        val va = a.valeur
        val vb = b.valeur
        if (va == null || vb == null) {
            return (va == null || va.toString().isEmpty()) && (vb == null || vb.toString().isEmpty())
        }
        val na = va.toString().toDoubleOrNull()
        val nb = vb.toString().toDoubleOrNull()
        if (na != null && nb != null) return na == nb
        return va.toString() == vb.toString()
    }

    /** La conversion `Number(v)` du JS, null pour NaN : vide et `null` valent 0, un booléen 1 ou 0,
     *  une liste d'un élément cet élément. */
    private fun nombreJs(v: Any?): Double? = when (v) {
        null -> 0.0
        is Boolean -> if (v) 1.0 else 0.0
        is Number -> v.toDouble()
        is List<*> -> when (v.size) {
            0 -> 0.0
            1 -> nombreJs(v[0]?.toString().orEmpty())
            else -> null
        }
        else -> v.toString().trim().let { t -> if (t.isEmpty()) 0.0 else t.toDoubleOrNull() }
    }

    /** Sémantique JS de la vérité : `false`, `""`, `0`, `null`, listes vides = faux. */
    private fun truthy(v: Any?): Boolean = when (v) {
        null -> false
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        is String -> v.isNotEmpty() && !v.equals("false", ignoreCase = true) && v != "0"
        is Collection<*> -> v.isNotEmpty()
        else -> true
    }
}
