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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/monitoring/form/FormulaireRenderer.kt) :
// l'état que les vues Android portaient dans leurs `tag`, les règles et le champ espèce. Les
// widgets deviennent des composables, qui lisent cet état et lui passent les saisies. L'ordre des
// recalculs est celui des écouteurs Android, y compris le onItemSelected DIFFÉRÉ des listes
// déroulantes (Spinner), reproduit par une coroutine de [portee].

package fr.ariegenature.geomys.monitoring.form

import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.store.TaxRefCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * L'état d'un formulaire dynamique des Suivis : les valeurs des [champs], ce qui en dérive (champs
 * masqués, requis, invalides, messages) et les règles du schéma (`hidden`, `required` dynamique,
 * `change`, bornes min/max).
 *
 * Le cycle est celui du FormulaireRenderer de GeoMys-Android :
 * 1. la construction pose les valeurs initiales des champs (`EditableField.value`) — c'est le
 *    `rendre` d'Android ; [champsDirtyInitiaux] désigne les champs pré-remplis depuis une saisie
 *    EXISTANTE (mode édition), « modifiés par l'utilisateur » d'emblée ;
 * 2. [setReglesChange] déclare les règles `change` et les applique une première fois ;
 * 3. l'écran passe chaque saisie de l'utilisateur ([saisirTexte], [choisirOption]…), puis lit
 *    [lireValeursPourEnvoi] au moment d'enregistrer, après un [flushChangementsEnAttente].
 *
 * Les valeurs suivent EXACTEMENT le contrat de `lireValeurs` d'Android, dont l'envoi dépend : Int
 * contre Double, `""` contre `null`, `true` ou `null` pour une case, liste vide → `null`.
 *
 * [portee] porte l'anti-rebond de la frappe (220 ms), les notifications différées et le calcul des
 * propositions des champs espèce : celle de l'écran, pour qu'ils s'arrêtent avec lui. Tout le reste
 * est synchrone, sur le fil de l'interface. [revision] change à chaque modification de l'état.
 */
class FormulaireEtat(
    champs: List<EditableField>,
    private val taxRef: TaxRefCache,
    private val portee: CoroutineScope,
    /** Champs porteurs d'une valeur SAUVEGARDÉE (mode édition) : dirty d'emblée. Le drapeau dirty
     *  n'est pas persisté entre deux sessions — au rendu d'une édition, la référence est prise APRÈS
     *  le pré-remplissage, donc tout champ redémarrerait « non touché », et les règles `change`
     *  gardées par `!dirty` recalculeraient et ÉCRASERAIENT la personnalisation dès l'ouverture (ex.
     *  base_site_name concaténé des scripts flore). Vide pour un formulaire de création. */
    private val champsDirtyInitiaux: Set<String> = emptySet(),
    /** Vrai pendant une synchro des données : la liste d'un protocole absente du cache est alors en
     *  cours de réécriture (SyncRunner.actif sous Android). */
    private val synchroActive: () -> Boolean = { false },
) {

    /** Ce que la vue d'un champ portait sous Android. */
    private sealed class Saisie {
        /** TEXT, TEXTAREA, NUMBER : le texte du champ. */
        class Texte(var texte: String) : Saisie()

        /** DATE, TIME, DATETIME : la valeur (null tant que rien n'est choisi) et le texte affiché. */
        class DateHeure(var valeur: String?, var affichage: String?) : Saisie()

        /** TAXON : le texte, le cd_nom retenu, et le choix fait dans la liste avec son texte. */
        class Taxon(var texte: String, var cdNom: Int?, var choixFige: Pair<String, Int>?) : Saisie()

        /** SELECT : l'option choisie, -1 pour « — choisir — ». */
        class Choix(var index: Int) : Saisie()

        /** RADIO : le bouton coché, -1 si aucun. */
        class Radio(var index: Int) : Saisie()

        /** SELECT_MULTIPLE : la liste des valeurs choisies, telle quelle. */
        class ChoixMultiple(var valeurs: List<Any?>) : Saisie()

        /** CHECKBOX_MULTIPLE : une case par option. */
        class Cases(val cochees: BooleanArray) : Saisie()

        /** CHECKBOX. */
        class Case(var cochee: Boolean) : Saisie()

        /** MEDIA : les URI des fichiers, dans l'ordre d'ajout. */
        class Medias(val uris: MutableList<String>) : Saisie()
    }

    private val fieldsParCode = linkedMapOf<String, EditableField>()
    private val saisies = linkedMapOf<String, Saisie>()

    /** Visibilité de chaque champ : `masque` à la construction, puis l'expression `hidden`. */
    private val visibles = mutableMapOf<String, Boolean>()

    /** L'astérisque « obligatoire » de chaque libellé, rafraîchi avec la visibilité. */
    private val requisAffiches = mutableMapOf<String, Boolean>()

    /** Message de validation min/max sous les champs NUMBER qui ont des bornes. */
    private val erreurs = mutableMapOf<String, String?>()
    private val statutsTaxon = mutableMapOf<String, StatutTaxon?>()
    private val erreursTaxon = mutableMapOf<String, String>()
    private val indicationsTaxon = mutableMapOf<String, String>()
    private val propositions = mutableMapOf<String, PropositionsTaxon>()

    private var onChangement: (() -> Unit)? = null

    /** Règles `change` du schéma (remplissage automatique de champs dépendants). */
    private var reglesChange: List<ChangeRules.Regle> = emptyList()

    /** Garde anti-récursion : vrai pendant qu'on applique un patch des règles `change`. */
    private var appliquantChange = false

    /** Approximation du `dirty` Angular (champ modifié par l'UTILISATEUR) : un champ est dirty si sa
     *  valeur courante diffère de la dernière valeur posée par les règles `change` ou, à défaut, de
     *  sa valeur à la construction. Sert aux gardes `!objForm.controls.X.dirty` des scripts `change`
     *  (ex. nom de site généré tant que l'utilisateur ne l'a pas édité à la main). */
    private var valeursAuRendu: Map<String, String> = emptyMap()
    private val dernieresValeursAuto = mutableMapOf<String, String>()

    private var debounceJob: Job? = null
    private val _revision = MutableStateFlow(0)

    /** Change à chaque modification de l'état : l'écran s'y abonne pour se recomposer. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** Les champs, dans l'ordre du formulaire. */
    val champs: List<EditableField> get() = fieldsParCode.values.toList()

    init {
        rendre(champs)
    }

    private fun rendre(champs: List<EditableField>) {
        champs.forEach { f ->
            fieldsParCode[f.code] = f
            // Champ choisi d'office à valeur unique (cf. EditableField.masque) : masqué, mais sa
            // valeur reste lue par lireValeurs.
            visibles[f.code] = !f.masque
            // « * » statique de départ ; le `required` conditionnel est évalué juste après.
            requisAffiches[f.code] = f.obligatoire
            saisies[f.code] = saisieInitiale(f)
            if (f.viewType == ViewType.TAXON) {
                indicationsTaxon[f.code] = INDICATION_TAXON
                chargerPropositions(f)
            }
        }
        // Référence « non touché par l'utilisateur » pour le drapeau dirty — AVANT la première passe
        // des règles change.
        valeursAuRendu = lireValeurs().mapValues { it.value?.toString() ?: "" }
        appliquerVisibiliteConditionnelle()
        appliquerChangeRules()
        appliquerValidations()
        notifier()
        // Sous Android, chaque liste déroulante affichée notifie son choix initial au premier passage
        // de mise en page — après le rendu ET après la première application des règles change, d'où
        // une passe complète de plus par liste.
        if (fieldsParCode.values.any { it.viewType == ViewType.SELECT }) {
            portee.launch {
                val nb = fieldsParCode.values.count { it.viewType == ViewType.SELECT && visibles[it.code] == true }
                repeat(nb) { notifierChangement() }
            }
        }
    }

    private fun saisieInitiale(f: EditableField): Saisie = when (f.viewType) {
        ViewType.TEXT, ViewType.TEXTAREA, ViewType.NUMBER ->
            Saisie.Texte(f.value?.toString()?.takeIf { it.isNotEmpty() && it != "null" } ?: "")
        // DATE pré-remplie : une partie heure ISO éventuelle est retirée, puis lecture tolérante
        // (accepte aussi « yyyy-MM-dd »), cf. FormatsDate.
        ViewType.DATE -> {
            val d = (f.value as? String)?.takeIf { it.isNotBlank() }?.let { FormatsDate.lireDate(it) }
            Saisie.DateHeure(d?.let { FormatsDate.date(it) }, d?.let { FormatsDate.affichageDate(it) })
        }
        // Accepte « HH:MM », « HH:MM:SS », « HHhMM ».
        ViewType.TIME -> {
            val iso = (f.value as? String)?.let { FormatsDate.lireHeure(it) }?.let { (h, m) -> FormatsDate.heure(h, m) }
            Saisie.DateHeure(iso, iso)
        }
        // Séparateur ISO « T » ou espace, avec ou sans secondes.
        ViewType.DATETIME -> {
            val dt = (f.value as? String)?.takeIf { it.isNotBlank() }?.let { FormatsDate.lireDateHeure(it) }
            Saisie.DateHeure(dt?.let { FormatsDate.dateHeure(it) }, dt?.let { FormatsDate.affichageDateHeure(it) })
        }
        ViewType.TAXON -> {
            var texte = ""
            var cd: Int? = null
            // Pré-remplissage si la valeur est un cd_nom déjà connu (édition).
            (f.value as? Int)?.takeIf { it > 0 }?.let { c ->
                val entree = taxRef.entreesParCdNom()[c]
                // cd_nom sauvé mais absent du cache local (référentiel resynchronisé sur une autre
                // liste…) : le cd_nom est gardé QUAND MÊME — sans lui, « Enregistrer les
                // modifications » remplaçait en silence le cd_nom sauvé par null. Affichage brut,
                // faute de nom (l'utilisateur peut le remplacer en tapant).
                texte = if (entree != null) entree.nomFrOriginal ?: entree.sciNom else "cd_nom $c"
                cd = c
                statutsTaxon[f.code] = statutRetenu(c)
            }
            (f.value as? String)?.takeIf { it.isNotEmpty() }?.let { texte = it }
            Saisie.Taxon(texte, cd, null)
        }
        ViewType.SELECT -> Saisie.Choix((f.value as? String)?.let { v -> f.values.indexOfFirst { it.value == v } } ?: -1)
        // Pré-coché sur la valeur fournie (défaut serveur ou édition) ; le dernier bouton qui la
        // porte l'emporte, comme les check() successifs du RadioGroup.
        ViewType.RADIO -> {
            val defaut = f.value?.toString()
            Saisie.Radio(f.values.indexOfLast { it.value == defaut })
        }
        // Liste fournie (édition, défaut), ou une seule chaîne.
        ViewType.SELECT_MULTIPLE -> Saisie.ChoixMultiple(
            (f.value as? List<*>)?.toList()
                ?: (f.value as? String)?.takeIf { it.isNotEmpty() }?.let { listOf(it) }
                ?: emptyList(),
        )
        ViewType.CHECKBOX_MULTIPLE -> {
            val defaut: Set<String> = when (val v = f.value) {
                is List<*> -> v.map { it.toString() }.toSet()
                is String -> if (v.isNotEmpty()) setOf(v) else emptySet()
                else -> emptySet()
            }
            Saisie.Cases(BooleanArray(f.values.size) { f.values[it].value in defaut })
        }
        // Cochée si la valeur est `true`, ou la chaîne "true"/"1" (le serveur envoie parfois une
        // chaîne même pour un booléen).
        ViewType.CHECKBOX -> Saisie.Case(
            when (val v = f.value) {
                is Boolean -> v
                is String -> v.equals("true", ignoreCase = true) || v == "1"
                is Number -> v.toInt() != 0
                else -> false
            },
        )
        // Édition : une liste (plusieurs fichiers) ou, par compatibilité, une chaîne unique.
        ViewType.MEDIA -> Saisie.Medias(
            when (val v = f.value) {
                is List<*> -> v.filterIsInstance<String>().filter { it.isNotEmpty() }.toMutableList()
                is String -> if (v.isNotEmpty()) mutableListOf(v) else mutableListOf()
                else -> mutableListOf()
            },
        )
    }

    // ── Règles ──────────────────────────────────────────────────────────────────────────────────

    /** Déclare les règles `change` du schéma (cf. [ChangeRules]) et les applique une fois. */
    fun setReglesChange(lignes: List<String>) {
        reglesChange = ChangeRules.parser(lignes)
        appliquerChangeRules()
        // RECALCUL COMPLET après la première application : une règle peut, dès son premier passage,
        // renseigner le champ dont dépend une expression `hidden`, ou remplir un obligatoire. Sans
        // lui, le formulaire s'affichait avec une visibilité et un bouton calculés sur l'état
        // d'AVANT la règle (audit 2026-09-14, R4-M3).
        appliquerVisibiliteConditionnelle()
        appliquerValidations()
        notifier()
    }

    /** Évalue les règles `change` contre les valeurs courantes (enrichies des drapeaux `__cd` et
     *  `__dirty`) et applique les champs à mettre à jour. La garde [appliquantChange] empêche la
     *  récursion. */
    private fun appliquerChangeRules() {
        if (reglesChange.isEmpty() || appliquantChange) return
        val maj = ChangeRules.evaluer(reglesChange, valeursPourExpressions())
        if (maj.isEmpty()) return
        var differees = 0
        appliquantChange = true
        try {
            maj.forEach { (code, valeur) ->
                differees += appliquerValeur(code, valeur)
                // Mémorise la valeur posée par le moteur, relue après pose : référence du drapeau
                // dirty — tant que l'utilisateur ne la change pas, le champ reste recalculable.
                dernieresValeursAuto[code] = lireValeurs()[code]?.toString() ?: ""
            }
        } finally {
            appliquantChange = false
        }
        // Liste déroulante changée par la règle : sous Android, son onItemSelected arrive APRÈS, au
        // passage de mise en page, donc hors de la garde — une passe complète de plus.
        repeat(differees) { portee.launch { notifierChangement() } }
    }

    /** Pose [valeur] dans le champ [code] (règles change). Renvoie le nombre de notifications
     *  DIFFÉRÉES qu'elle déclenche ; les immédiates (case, bouton radio) sont faites ici, comme les
     *  écouteurs Android qui se déclenchaient pendant la pose. */
    private fun appliquerValeur(code: String, valeur: Any?): Int {
        val s = saisies[code] ?: return 0
        val f = fieldsParCode.getValue(code)
        when (s) {
            // Le texte n'est remplacé que s'il change réellement.
            is Saisie.Texte -> {
                val nouvelle = valeur?.toString() ?: ""
                if (s.texte != nouvelle) s.texte = nouvelle
            }
            is Saisie.Case -> {
                val coche = when (valeur) {
                    is Boolean -> valeur
                    is Number -> valeur.toInt() != 0
                    is String -> valeur.equals("true", true) || valeur == "1"
                    else -> false
                }
                if (coche != s.cochee) {
                    s.cochee = coche
                    notifierChangement()
                }
            }
            is Saisie.Choix -> {
                val cible = valeur?.toString()
                val idx = f.values.indexOfFirst { it.value == cible }
                if (idx != s.index) {
                    s.index = idx
                    return 1
                }
            }
            is Saisie.Radio -> {
                val cible = valeur?.toString()
                val idx = f.values.indexOfFirst { it.value == cible }
                if (idx != s.index) {
                    s.index = idx
                    notifierChangement()
                }
            }
            // La valeur est posée telle quelle ; le texte affiché n'est remplacé que par une valeur
            // non vide.
            is Saisie.DateHeure -> {
                val texte = valeur?.toString().orEmpty()
                s.valeur = texte
                if (texte.isNotEmpty()) s.affichage = texte
            }
            // Les champs MEDIA ne sont jamais pilotés par les règles (ce sont des fichiers choisis par
            // l'utilisateur) : rien, pour ne pas écraser la liste.
            is Saisie.Medias -> {}
            is Saisie.ChoixMultiple -> s.valeurs = listeDe(valeur)
            is Saisie.Cases -> {
                val liste = listeDe(valeur)
                f.values.forEachIndexed { i, opt ->
                    val coche = opt.value in liste
                    if (coche != s.cochees[i]) {
                        s.cochees[i] = coche
                        notifierChangement()
                    }
                }
            }
            // TAXON : cible improbable d'une règle, non géré.
            is Saisie.Taxon -> {}
        }
        return 0
    }

    private fun listeDe(valeur: Any?): List<String> = when (valeur) {
        is List<*> -> valeur.map { it.toString() }
        null -> emptyList()
        else -> listOf(valeur.toString())
    }

    /** Recalcule la visibilité conditionnelle, applique les règles change, puis les validations, et
     *  prévient l'écran. À chaque modification d'un champ. */
    private fun notifierChangement() {
        appliquerVisibiliteConditionnelle()
        // Remplissage des champs dépendants ; la garde évite la récursion quand c'est le moteur
        // lui-même qui modifie un champ.
        if (!appliquantChange) appliquerChangeRules()
        appliquerValidations()
        notifier()
    }

    /** Version DIFFÉRÉE de [notifierChangement] pour la frappe : tout recalculer à chaque caractère
     *  ralentissait la saisie sur les gros formulaires. Les frappes rapprochées sont regroupées. */
    private fun notifierChangementDebounce() {
        // GARDE CRITIQUE : sans elle, l'anti-rebond défait la protection anti-récursion des règles
        // change (boucle de recalcul perpétuelle, audit 2026-08-23).
        if (appliquantChange) return
        debounceJob?.cancel()
        debounceJob = portee.launch {
            delay(DELAI_FRAPPE_MS)
            notifierChangement()
        }
    }

    /** Applique IMMÉDIATEMENT tout recalcul en attente de l'anti-rebond. À appeler avant de lire
     *  l'état pour une décision (enregistrement, sortie d'écran) : sans lui, un « Enregistrer » dans
     *  les 220 ms suivant la dernière frappe validerait un état périmé (champ requis tout juste
     *  vidé encore compté rempli, règle change non appliquée). */
    fun flushChangementsEnAttente() {
        if (debounceJob?.isActive == true) {
            debounceJob?.cancel()
            debounceJob = null
            notifierChangement()
        }
    }

    /** Enregistre le rappel de chaque modification de champ, et l'appelle aussitôt pour l'état de
     *  départ. Remplace tout rappel précédent. */
    fun setOnChangement(rappel: () -> Unit) {
        onChangement = rappel
        rappel()
    }

    private fun notifier() {
        onChangement?.invoke()
        _revision.value++
    }

    /** Évalue chaque expression `hidden`, puis rafraîchit l'astérisque « obligatoire ». */
    private fun appliquerVisibiliteConditionnelle() {
        val valeurs = valeursPourExpressions()
        fieldsParCode.forEach { (code, field) ->
            field.hiddenExpr?.let { expr -> visibles[code] = !HiddenExpr.masquer(expr, valeurs) }
        }
        rafraichirObligatoires(valeurs)
    }

    /** Le « * » de chaque libellé selon que le champ est RÉELLEMENT requis pour les valeurs
     *  courantes : obligatoire statique, ou `required` conditionnel évalué vrai (ex. POPAmphibien
     *  turbidite/rives, requis seulement si accessibility='Oui' ET num_passage='1er passage'). */
    private fun rafraichirObligatoires(valeurs: Map<String, Any?>) {
        fieldsParCode.forEach { (code, field) -> requisAffiches[code] = estRequisPour(field, valeurs) }
    }

    private fun estRequisPour(field: EditableField, valeurs: Map<String, Any?>): Boolean =
        field.obligatoire || (field.obligatoireExpr != null && HiddenExpr.evaluerBooleen(field.obligatoireExpr, valeurs))

    /** Les messages sous les champs NUMBER qui ont des bornes. Une borne `(value) => value.autre`
     *  doit être réévaluée quand l'autre champ change, pas seulement le champ courant. */
    private fun appliquerValidations() {
        val bornes = fieldsParCode.values.filter {
            it.viewType == ViewType.NUMBER && (it.minValue != null || it.maxValue != null)
        }
        if (bornes.isEmpty()) return
        val valeurs = lireValeurs()
        bornes.forEach { field ->
            val violation = ValidationExpr.violation(valeurs[field.code], field.minValue, field.maxValue, valeurs)
            erreurs[field.code] = violation?.let {
                when (it.type) {
                    ValidationExpr.Violation.Type.TROP_PETIT -> "Valeur ≥ ${formaterBorne(it.borne)} attendue"
                    ValidationExpr.Violation.Type.TROP_GRAND -> "Valeur ≤ ${formaterBorne(it.borne)} attendue"
                }
            }
        }
    }

    /** Valeurs courantes ENRICHIES pour le moteur d'expressions :
     *  - `<code>__dirty` : champ modifié par l'utilisateur (pré-rempli d'une saisie existante =
     *    dirty d'office) ;
     *  - `<code>__cd` : le cd_nomenclature de l'option choisie, pour les champs dont les options en
     *    portent un (datalists nomenclature). C'est la cible des réécritures
     *    `meta.nomenclatures[value.X].cd_nomenclature` → `${X__cd}` de [HiddenExpr.normaliser]
     *    (protocoles loutre/blaireau). Aucun choix → clé absente → comparaison contre null (parité
     *    web). */
    private fun valeursPourExpressions(): Map<String, Any?> {
        val valeurs = lireValeurs().toMutableMap()
        fieldsParCode.forEach { (code, field) ->
            val courantStr = valeurs[code]?.toString() ?: ""
            val reference = dernieresValeursAuto[code] ?: valeursAuRendu[code] ?: ""
            valeurs["${code}__dirty"] = code in champsDirtyInitiaux || courantStr != reference
            val courant = courantStr.takeIf { it.isNotEmpty() } ?: return@forEach
            field.values.firstOrNull { it.value == courant }?.cdNomenclature?.let { valeurs["${code}__cd"] = it }
        }
        return valeurs
    }

    // ── Lecture ─────────────────────────────────────────────────────────────────────────────────

    /** La valeur courante de chaque champ, typée :
     *  - TEXT / TEXTAREA → String (vide si non rempli) ;
     *  - NUMBER → Int pour une valeur entière, Double pour une décimale d'un widget décimal (un
     *    widget `integer` tronque), null si illisible ; la virgule vaut le point ;
     *  - DATE, TIME, DATETIME → String, "" si rien n'est choisi ;
     *  - TAXON → le cd_nom (Int), ou null ;
     *  - SELECT, RADIO → la valeur de l'option, "" si aucune ;
     *  - SELECT_MULTIPLE, CHECKBOX_MULTIPLE → List<String>, null si vide ;
     *  - CHECKBOX → true, ou null (jamais false) ;
     *  - MEDIA → la liste des URI (vide si aucune). C'est la liste du champ elle-même, comme le tag
     *    d'Android : une copie lue plus tôt suit ses ajouts.
     *
     *  Tous les champs y figurent, masqués compris : la valeur d'un champ masqué est conservée (cf.
     *  [lireValeursPourEnvoi]). */
    fun lireValeurs(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((code, s) in saisies) {
            val f = fieldsParCode.getValue(code)
            out[code] = when (s) {
                // Int d'abord (les entiers restent des entiers dans le corps envoyé), Double sinon. La
                // virgule des claviers français vaut le point. Widget `integer` : jamais de Double,
                // une valeur décimale collée ou posée par une règle est tronquée (audit 2026-08-27).
                is Saisie.Texte -> if (f.viewType == ViewType.NUMBER) {
                    s.texte.replace(',', '.').let { it.toIntOrNull() ?: it.toDoubleOrNull()?.let { d -> if (f.decimal) d else d.toInt() } }
                } else {
                    s.texte
                }
                is Saisie.DateHeure -> s.valeur ?: ""
                is Saisie.Taxon -> s.cdNom
                is Saisie.Choix -> if (s.index < 0) "" else f.values.getOrNull(s.index)?.value.orEmpty()
                is Saisie.Radio -> if (s.index < 0) "" else f.values.getOrNull(s.index)?.value ?: ""
                // Liste vide → null (« non renseigné », comme le formulaire web).
                is Saisie.ChoixMultiple -> s.valeurs.takeIf { it.isNotEmpty() }
                is Saisie.Cases -> f.values.filterIndexed { i, _ -> s.cochees[i] }.map { it.value }.takeIf { it.isNotEmpty() }
                // Case non cochée = null (« non renseigné »), cochée = true.
                is Saisie.Case -> if (s.cochee) true else null
                is Saisie.Medias -> s.uris
            }
        }
        return out
    }

    /**
     * Valeurs destinées à L'ENVOI : comme [lireValeurs], mais SANS les champs masqués.
     *
     * Un champ masqué conserve sa valeur — l'utilisateur la retrouve si la condition redevient vraie
     * — mais cette valeur ne doit pas partir au serveur, comme pour le client mobile officiel PnX-SI.
     * Un protocole qui masque « nombre de nids » quand « espèce observée = non » transmettait sinon le
     * nombre saisi avant la bascule : donnée fausse en base, sans aucun signal (audit 2026-09-14,
     * R4-M4).
     *
     * DEUX EXCEPTIONS, qui suivent la règle du client officiel :
     *  - un champ masqué mais REQUIS (`required` conditionnel encore vrai) est envoyé : le serveur le
     *    refuserait autrement, et une incohérence du schéma ne doit pas faire perdre la saisie ;
     *  - un champ POSÉ PAR UNE RÈGLE `change` est envoyé : c'est le moteur qui l'a calculé, sa valeur
     *    est voulue même si le champ n'est pas montré.
     *
     * Le champ `masque` (option unique choisie d'office) est un champ masqué comme les autres : il ne
     * part que s'il est requis ou posé par une règle, comme sous Android.
     */
    fun lireValeursPourEnvoi(): Map<String, Any?> {
        val toutes = lireValeurs()
        val requis = champsRequis()
        return toutes.filterKeys { code ->
            visibles[code] == true || code in requis || code in dernieresValeursAuto
        }
    }

    /** Codes des champs actuellement REQUIS (obligatoires en dur, ou par expression), remplis ou
     *  non, masqués ou non. */
    private fun champsRequis(): Set<String> {
        val valeurs = valeursPourExpressions()
        return fieldsParCode.filterValues { estRequisPour(it, valeurs) }.keys
    }

    /** Codes des champs requis (statiquement ou par expression) dont la valeur est vide ET qui sont
     *  visibles (un champ masqué ne doit pas bloquer l'enregistrement). */
    fun champsObligatoiresManquants(): List<String> {
        val valeurs = valeursPourExpressions()
        return fieldsParCode.filter { (code, field) ->
            if (!estRequisPour(field, valeurs)) return@filter false
            if (visibles[code] != true) return@filter false
            when (val v = valeurs[code]) {
                null -> true
                is String -> v.isBlank()
                is List<*> -> v.isEmpty()
                else -> false
            }
        }.keys.toList()
    }

    /** Codes des champs actuellement INVALIDES : violation de min/max (NUMBER), ou texte d'espèce
     *  qui ne désigne aucun taxon proposable (TAXON). Un champ vide ne compte pas (c'est
     *  `obligatoire` qui s'en charge), un champ masqué non plus. */
    fun champsInvalides(): List<String> {
        val valeurs = lireValeurs()
        return fieldsParCode.mapNotNull { (code, field) ->
            if (visibles[code] != true) return@mapNotNull null
            when (field.viewType) {
                // RÈGLE 2026-09-17 : dans un champ espèce, seuls les noms PROPOSÉS sont acceptés. Un
                // texte qui ne désigne aucun taxon proposable bloque l'enregistrement tant qu'il n'est
                // pas corrigé ou effacé (il partait sans cd_nom, l'espèce saisie disparaissait).
                ViewType.TAXON -> {
                    val s = saisies[code] as Saisie.Taxon
                    if (s.texte.isNotBlank() && s.cdNom == null) code else null
                }
                ViewType.NUMBER ->
                    if (ValidationExpr.violation(valeurs[code], field.minValue, field.maxValue, valeurs) != null) code else null
                else -> null
            }
        }
    }

    /** MODE COMPLÉTION : les champs à marquer d'une barre rouge (obligatoires encore VIDES), pour
     *  repérer d'un coup d'œil ce qu'il reste à saisir (demande terrain 2026-09-03). À relire à
     *  chaque changement : la barre disparaît dès que le champ est rempli, et apparaît sur un champ
     *  rendu obligatoire par un `required` conditionnel. Hors mode complétion, aucune. */
    fun barresRouges(modeCompletion: Boolean): Set<String> =
        if (modeCompletion) champsObligatoiresManquants().toSet() else emptySet()

    // ── Affichage ───────────────────────────────────────────────────────────────────────────────

    fun estVisible(code: String): Boolean = visibles[code] == true

    /** Le libellé porte-t-il l'astérisque « obligatoire » ? */
    fun estRequis(code: String): Boolean = requisAffiches[code] == true

    /** Le texte d'un champ TEXT, TEXTAREA, NUMBER ou TAXON. */
    fun texte(code: String): String = when (val s = saisies[code]) {
        is Saisie.Texte -> s.texte
        is Saisie.Taxon -> s.texte
        else -> ""
    }

    /** La valeur d'un champ DATE, TIME ou DATETIME, null tant que rien n'est choisi. */
    fun valeurDateHeure(code: String): String? = (saisies[code] as? Saisie.DateHeure)?.valeur

    /** Le texte affiché d'un champ DATE, TIME ou DATETIME, null tant que rien n'est choisi
     *  (« Choisir une date… »). */
    fun affichageDateHeure(code: String): String? = (saisies[code] as? Saisie.DateHeure)?.affichage

    /** L'option choisie d'un SELECT ou le bouton coché d'un RADIO, -1 si aucun. */
    fun indexChoisi(code: String): Int = when (val s = saisies[code]) {
        is Saisie.Choix -> s.index
        is Saisie.Radio -> s.index
        else -> -1
    }

    /** Les valeurs choisies d'un SELECT_MULTIPLE. */
    fun choixMultiples(code: String): List<Any?> = (saisies[code] as? Saisie.ChoixMultiple)?.valeurs.orEmpty()

    /** L'état de chaque case d'un CHECKBOX_MULTIPLE, ou d'une CHECKBOX (une seule case). */
    fun cases(code: String): List<Boolean> = when (val s = saisies[code]) {
        is Saisie.Cases -> s.cochees.toList()
        is Saisie.Case -> listOf(s.cochee)
        else -> emptyList()
    }

    /** Les fichiers d'un champ MEDIA. */
    fun medias(code: String): List<String> = (saisies[code] as? Saisie.Medias)?.uris?.toList().orEmpty()

    /** Le message de validation min/max sous un champ NUMBER, ou null. */
    fun erreurValidation(code: String): String? = erreurs[code]

    /** La ligne sous un champ TAXON, ou null (rien à afficher). */
    fun statutTaxon(code: String): StatutTaxon? = statutsTaxon[code]

    /** Le signalement posé sur un champ TAXON à sa sortie, ou null. */
    fun erreurTaxon(code: String): String? = erreursTaxon[code]

    /** L'indication affichée dans un champ TAXON vide. */
    fun indicationTaxon(code: String): String = indicationsTaxon[code] ?: INDICATION_TAXON

    /** Les propositions d'un champ TAXON pour [requete], vides tant qu'elles se calculent. */
    fun propositionsTaxon(code: String, requete: String?): List<PropositionTaxon> =
        propositions[code]?.filtrer(requete).orEmpty()

    // ── Saisies de l'utilisateur ────────────────────────────────────────────────────────────────

    /** Frappe dans un champ TEXT, TEXTAREA ou NUMBER (recalcul différé de 220 ms). */
    fun saisirTexte(code: String, texte: String) {
        val s = saisies[code] as? Saisie.Texte ?: return
        s.texte = texte
        _revision.value++
        notifierChangementDebounce()
    }

    /** Date choisie au sélecteur d'un champ DATE ([mois] de 1 à 12). */
    fun choisirDate(code: String, annee: Int, mois: Int, jour: Int) {
        val s = saisies[code] as? Saisie.DateHeure ?: return
        val d = kotlinx.datetime.LocalDate(annee, mois, jour)
        s.valeur = FormatsDate.date(d)
        s.affichage = FormatsDate.affichageDate(d)
        notifierChangement()
    }

    /** Heure choisie au sélecteur d'un champ TIME. */
    fun choisirHeure(code: String, heure: Int, minute: Int) {
        val s = saisies[code] as? Saisie.DateHeure ?: return
        val iso = FormatsDate.heure(heure, minute)
        s.valeur = iso
        s.affichage = iso
        notifierChangement()
    }

    /** Date puis heure choisies aux sélecteurs d'un champ DATETIME (secondes à zéro). */
    fun choisirDateHeure(code: String, annee: Int, mois: Int, jour: Int, heure: Int, minute: Int) {
        val s = saisies[code] as? Saisie.DateHeure ?: return
        val dt = kotlinx.datetime.LocalDateTime(annee, mois, jour, heure, minute, 0)
        s.valeur = FormatsDate.dateHeure(dt)
        s.affichage = FormatsDate.affichageDateHeure(dt)
        notifierChangement()
    }

    /** Option choisie dans un SELECT ([index] -1 = « — choisir — »). */
    fun choisirOption(code: String, index: Int) {
        val s = saisies[code] as? Saisie.Choix ?: return
        if (index == s.index) return
        s.index = index
        notifierChangement()
    }

    /** Bouton coché d'un RADIO. */
    fun choisirRadio(code: String, index: Int) {
        val s = saisies[code] as? Saisie.Radio ?: return
        if (index == s.index) return
        s.index = index
        notifierChangement()
    }

    /** « Valider » du dialogue d'un SELECT_MULTIPLE : les options cochées, dans leur ordre. */
    fun validerChoixMultiple(code: String, choisies: Set<String>) {
        val s = saisies[code] as? Saisie.ChoixMultiple ?: return
        val f = fieldsParCode.getValue(code)
        s.valeurs = f.values.filter { it.value in choisies }.map { it.value }
        notifierChangement()
    }

    /** Case [index] d'un CHECKBOX_MULTIPLE. */
    fun cocherCase(code: String, index: Int, coche: Boolean) {
        val s = saisies[code] as? Saisie.Cases ?: return
        if (s.cochees.getOrNull(index) == null || s.cochees[index] == coche) return
        s.cochees[index] = coche
        notifierChangement()
    }

    /** Case d'un champ CHECKBOX. */
    fun cocher(code: String, coche: Boolean) {
        val s = saisies[code] as? Saisie.Case ?: return
        if (s.cochee == coche) return
        s.cochee = coche
        notifierChangement()
    }

    /** Fichiers importés pour un champ MEDIA (sélection multiple). */
    fun ajouterMedias(code: String, uris: List<String>) {
        val s = saisies[code] as? Saisie.Medias ?: return
        val ajout = uris.filter { it.isNotEmpty() }
        if (ajout.isEmpty()) return
        s.uris.addAll(ajout)
        notifierChangement()
    }

    /** ✕ d'un fichier d'un champ MEDIA. */
    fun retirerMedia(code: String, uri: String) {
        val s = saisies[code] as? Saisie.Medias ?: return
        s.uris.remove(uri)
        notifierChangement()
    }

    /** Frappe dans un champ TAXON : le texte est résolu à chaque fois (cas d'un nom exact tapé sans
     *  passer par la liste), restreint à la liste du protocole. */
    fun saisirTexteTaxon(code: String, texte: String) {
        val s = saisies[code] as? Saisie.Taxon ?: return
        s.texte = texte
        // Texte inchangé depuis un choix dans la liste : on garde CE taxon.
        val fige = s.choixFige?.takeIf { it.first == texte }?.second
        if (fige == null) s.choixFige = null
        val resolu = fige ?: resoudre(code, texte)
        val changement = resolu != s.cdNom
        if (changement) s.cdNom = resolu
        statutsTaxon[code] = resolu?.let { statutRetenu(it) }
        // La résolution a abouti → efface l'éventuel signalement posé à la sortie du champ.
        if (resolu != null) erreursTaxon.remove(code)
        _revision.value++
        // Un texte devenu (ou redevenu) non reconnu change la validité du formulaire.
        if (changement) notifierChangement()
        // Le champ espèce est aussi un champ texte : la frappe y déclenche le recalcul différé.
        notifierChangementDebounce()
    }

    /** Proposition choisie dans la liste d'un champ TAXON : quand elle porte son taxon (liste du
     *  protocole exploitable), c'est CELUI-LÀ qui est retenu. */
    fun choisirPropositionTaxon(code: String, proposition: PropositionTaxon) {
        val s = saisies[code] as? Saisie.Taxon ?: return
        // La liste recopie le nom dans le champ, comme une frappe…
        saisirTexteTaxon(code, proposition.nom)
        // … puis le choix est fixé.
        val saisi = s.texte
        s.choixFige = proposition.cdNom?.let { saisi to it }
        s.cdNom = proposition.cdNom ?: resoudre(code, saisi)
        statutsTaxon[code] = s.cdNom?.let { statutRetenu(it) }
        if (s.cdNom != null) erreursTaxon.remove(code)
        notifierChangement()
    }

    /** Sortie d'un champ TAXON : signalement VISIBLE si le texte affiché ne désigne aucun taxon.
     *  L'utilisateur voyait un nom écrit et croyait l'espèce saisie, alors que la valeur était null.
     *  À la sortie et non à la frappe : pendant la saisie, un texte partiel non résolu est l'état
     *  normal. Le message nomme le protocole quand c'est lui qui restreint : un nom valide ailleurs
     *  (« Souci » la plante) est refusé ici parce qu'il n'est pas du protocole. */
    fun quitterChampTaxon(code: String) {
        val s = saisies[code] as? Saisie.Taxon ?: return
        if (s.texte.isNotEmpty() && s.cdNom == null) {
            val restreinte = fieldsParCode.getValue(code).idListeTaxonomieRestreinte != null
            erreursTaxon[code] = if (restreinte) {
                "Espèce non reconnue dans la liste du protocole — choisissez une suggestion"
            } else {
                "Espèce non reconnue — choisissez une suggestion"
            }
            statutsTaxon[code] = StatutTaxon.Refus(restreinte)
            _revision.value++
        }
    }

    // ── Champ espèce ────────────────────────────────────────────────────────────────────────────

    /** Résolution du nom saisi, RESTREINTE au périmètre du protocole. Avant (audit 2026-09-17, C1),
     *  c'était le cache ENTIER : sur STERF, « Souci » (Colias crocea, dans la liste) rendait
     *  Calendula, la plante, qui partait ainsi à GeoNature. */
    private fun resoudre(code: String, saisi: String): Int? =
        taxRef.getDansListe(saisi, fieldsParCode.getValue(code).idListeTaxonomieRestreinte)?.cdNom

    private fun statutRetenu(cd: Int): StatutTaxon =
        StatutTaxon.Retenu(cd, taxRef.entreesParCdNom()[cd]?.sciNom.orEmpty())

    /** Les propositions d'un champ espèce, calculées hors du fil de l'interface (la liste d'un
     *  protocole compte jusqu'à 45 000 taxons). */
    private fun chargerPropositions(f: EditableField) {
        portee.launch {
            val p = withContext(Dispatchers.Default) { PropositionsTaxon.construire(taxRef, f.idListeTaxonomieRestreinte) }
            propositions[f.code] = p
            Journal.i(TAG, "Champ TAXON '${f.code}' → ${p.diagnostic}")
            if (p.listeVide) {
                val statut = StatutTaxon.PasDeDonnees(synchroActive())
                indicationsTaxon[f.code] = statut.texte
                statutsTaxon[f.code] = statut
            } else {
                indicationsTaxon[f.code] = INDICATION_TAXON
            }
            _revision.value++
        }
    }

    companion object {
        private const val TAG = "FormulaireEtat"

        /** Délai d'anti-rebond de la frappe avant recalcul. */
        const val DELAI_FRAPPE_MS = 220L

        /** L'indication d'un champ espèce vide. */
        const val INDICATION_TAXON = "Tapez le nom du taxon…"

        /** Une borne pour l'affichage : entière sans décimale (`10`, pas `10.0`) si elle est entière. */
        fun formaterBorne(d: Double): String =
            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
    }
}
