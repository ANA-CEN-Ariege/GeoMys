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

// Extrait de GeoMys-Android @ d72b8da (app/src/main/java/fr/ariegenature/geomys/ui/saisie/AdditionalFieldsRenderer.kt) :
// la logique des champs additionnels OccTax — valeurs au rendu, encodage relu par `collecter`,
// champs obligatoires vides — sort des vues, où elle vivait dans des tags. Gson devient kotlinx,
// org.json son portage commun. Les widgets deviennent des composables, qui lisent cet état. Depuis le
// 20 octobre, les widgets, les valeurs (avec leur type JSON) et les contrôles sont ceux du formulaire
// dynamique du web (dynamic-form.component et dynamic-form.service de GeoNature 2.17.5).

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.json.JSONTokener
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.monitoring.form.PropositionTaxon
import fr.ariegenature.geomys.monitoring.form.PropositionsTaxon
import fr.ariegenature.geomys.monitoring.form.SelectionDateHeure
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.io.listeDepuisJson
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/** Les champs additionnels GeoNature (gn_commons.t_additional_fields) des saisies OccTax : relevé,
 *  occurrence et dénombrement. */
object ChampsAdditionnels {

    /** Le cache JSON des définitions (`GeoNatureConfig.additionalFieldsOcctaxJson`) ; vide s'il est
     *  absent ou illisible. */
    fun fromJson(json: String): List<AdditionalFieldDef> = listeDepuisJson(AdditionalFieldDef.serializer(), json)

    /** Les champs de l'objet [codeObjet] (relevé, occurrence, dénombrement) que le web montre pour le
     *  jeu de données [idDataset] du relevé : les champs globaux, puis ceux du jeu, chacun dans l'ordre
     *  du serveur (`field_order`), comme le web, qui demande les uns puis les autres. */
    fun defsPour(additionalFieldsOcctaxJson: String, codeObjet: String, idDataset: Int?): List<AdditionalFieldDef> {
        val champs = fromJson(additionalFieldsOcctaxJson).filter { it.appliqueA(codeObjet) && it.visiblePour(idDataset) }
        return champs.filter { it.datasetsIds.isEmpty() } + champs.filter { it.datasetsIds.isNotEmpty() }
    }

    /** Vrai s'il existe au moins un champ additionnel de relevé visible **obligatoire ET SANS valeur
     *  par défaut** — seul cas qui justifie d'intercaler l'écran « Détails du relevé » avant la
     *  saisie. Un champ requis AVEC défaut est déjà satisfait, et tous ces champs restent éditables
     *  par le bouton « Détails » de l'écran de saisie. */
    fun aDesChampsReleveRequisSansDefaut(additionalFieldsOcctaxJson: String, idDataset: Int?): Boolean =
        defsPour(additionalFieldsOcctaxJson, AdditionalFieldsObject.RELEVE, idDataset)
            .any { it.aUneValeur && !it.desactive && it.required && it.defaultValue.isNullOrBlank() }

    /** Les champs de [codeObjet] (occurrence, dénombrement) que l'utilisateur doit remplir pour le jeu
     *  [idDataset] : obligatoires, montrés, sans valeur par défaut. Le web n'enregistre pas le taxon
     *  sans eux ; l'appli les montre comme tels (21 octobre). */
    fun requisARemplir(additionalFieldsOcctaxJson: String, codeObjet: String, idDataset: Int?): List<AdditionalFieldDef> =
        defsPour(additionalFieldsOcctaxJson, codeObjet, idDataset)
            .filter { it.aUneValeur && !it.desactive && !it.masque && it.required && it.defaultValue.isNullOrBlank() }

    /** Un champ de [requis] reste vide dans [valeurs] (une liste vide comprise). */
    fun unRequisVide(requis: List<AdditionalFieldDef>, valeurs: Map<String, String>): Boolean =
        requis.any { def -> valeurs[def.fieldName].let { it.isNullOrBlank() || it == "[]" } }

    /** Valeurs par défaut des champs additionnels de relevé visibles (`fieldName` → defaultValue non
     *  vide). Sert à pré-remplir la session quand on SAUTE l'écran « Détails du relevé » : un champ
     *  avec défaut (notamment requis) porte ainsi sa valeur jusqu'à l'envoi sans qu'on ait à ouvrir
     *  « Détails ». */
    fun defautsChampsReleve(additionalFieldsOcctaxJson: String, idDataset: Int?): Map<String, String> =
        defsPour(additionalFieldsOcctaxJson, AdditionalFieldsObject.RELEVE, idDataset)
            .filter { it.aUneValeur && it.widget != WidgetType.CHECKBOX }
            .mapNotNull { def -> def.defaultValue?.takeIf { it.isNotBlank() }?.let { def.fieldName to it } }
            .toMap()

    // ── Valeurs JSON ────────────────────────────────────────────────────────────────────────────

    /** Une valeur JSON en texte, telle que l'appli la garde : une chaîne telle quelle, un nombre sans
     *  `.0`, rien pour null. */
    fun texteValeur(valeur: Any?): String = when (valeur) {
        null, JSONObject.NULL -> ""
        is String -> valeur
        is Number -> JSONObject.numberToString(valeur)
        else -> valeur.toString()
    }

    /** Une valeur JSON écrite en JSON : `"a"`, `1`, `true`, `null`, un objet ou un tableau. */
    fun jsonLitteral(valeur: Any?): String = when (valeur) {
        null, JSONObject.NULL -> "null"
        is String -> JSONObject.quote(valeur)
        is Number -> JSONObject.numberToString(valeur)
        else -> valeur.toString()
    }

    /** La valeur JSON que [litteral] écrit ; un texte qui n'est pas du JSON reste ce texte. */
    fun depuisLitteral(litteral: String): Any? = try {
        JSONTokener(litteral).nextValue()
    } catch (_: Exception) {
        litteral
    }

    /** Le jour (minuit, heure du téléphone) d'une date « aaaa-mm-jj » du web ; null si illisible. */
    fun jourDepuisDate(texte: String): Long? = try {
        LocalDate.parse(texte.trim()).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    } catch (_: Exception) {
        null
    }

    /** La date « aaaa-mm-jj » du jour [millis], celle qu'envoie le web. */
    fun dateDepuisJour(millis: Long): String =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    /** Ce sur quoi s'ouvre le sélecteur d'une date ou d'une heure ([heure]) : sa [valeur], sinon
     *  [maintenant]. */
    fun selectionDateHeure(heure: Boolean, valeur: String, maintenant: Long): SelectionDateHeure {
        val m = Instant.fromEpochMilliseconds(maintenant).toLocalDateTime(TimeZone.currentSystemDefault())
        val depart = SelectionDateHeure(m.year, m.month.number, m.day, m.hour, m.minute)
        if (heure) {
            val (h, mn) = valeur.split(':').map { it.trim().toIntOrNull() }
                .let { (it.getOrNull(0) ?: depart.heure) to (it.getOrNull(1) ?: depart.minute) }
            return depart.copy(heure = h, minute = mn)
        }
        val jour = jourDepuisDate(valeur) ?: return depart
        val d = Instant.fromEpochMilliseconds(jour).toLocalDateTime(TimeZone.currentSystemDefault())
        return depart.copy(annee = d.year, mois = d.month.number, jour = d.day)
    }

    /** La valeur d'une heure choisie (« hh:mm ») ou d'une date (« aaaa-mm-jj »), celle du web. */
    fun valeurDateHeure(heure: Boolean, s: SelectionDateHeure): String {
        fun deux(n: Int) = n.toString().padStart(2, '0')
        return if (heure) "${deux(s.heure)}:${deux(s.minute)}" else "${s.annee.toString().padStart(4, '0')}-${deux(s.mois)}-${deux(s.jour)}"
    }

    // ── Options ─────────────────────────────────────────────────────────────────────────────────

    /** L'entrée vide d'un `<select>` du web, absente avec `noNullOption`. */
    const val OPTION_VIDE = "-"

    /** L'entrée vide d'une datalist à `nullDefault`, quand elle n'est pas requise. */
    const val OPTION_AUCUN = "-- Aucun --"

    /** L'invite des observateurs du web (observers.component.html). */
    const val INVITE_OBSERVATEURS = "Observateur(s)"

    /** Le libellé d'un champ `observers-text`, celui du web, quel que soit le libellé du champ. */
    const val LIBELLE_OBSERVATEURS_TEXTE = "Observateurs"

    /** Le message d'une case à cocher multiple requise et vide (DynamicForm.Errors.CheckBoxError). */
    const val AU_MOINS_UNE_OPTION = "Veuillez choisir au moins une option"

    /** Les valeurs des options d'une liste (SELECT, RADIO, CHECKBOX, MULTISELECT, DATALIST), en texte :
     *  les `value` du serveur quand elles sont alignées sur les libellés, sinon les libellés. */
    fun codesListe(def: AdditionalFieldDef): List<String> =
        if (def.fieldValueCodes.size == def.fieldValues.size) def.fieldValueCodes else def.fieldValues

    /** La valeur de l'option [code] avec son type JSON ([AdditionalFieldDef.valeursBrutes]) ; une
     *  valeur absente des options (ancien cache, liste changée) reste son texte. */
    fun valeurOption(def: AdditionalFieldDef, code: String): Any? {
        val i = codesListe(def).indexOf(code)
        val brute = def.valeursBrutes.getOrNull(i) ?: return code
        return depuisLitteral(brute)
    }

    /** Les options d'un champ à choix : (valeur en texte, libellé). Une nomenclature propose ses
     *  `label_default` (seules ses `cd_nomenclatures`) sous leur `label_fr` ; des observateurs, ceux
     *  de [observateurs] (id_role, nom complet) ; un `bool_radio`, vrai puis faux sous les deux
     *  libellés de ses `values`. */
    fun options(def: AdditionalFieldDef, observateurs: List<Pair<Int, String>> = emptyList()): List<Pair<String, String>> = when (def.widget) {
        WidgetType.NOMENCLATURE -> def.optionsProposees.map { it.valeur to it.label }
        WidgetType.OBSERVERS -> observateurs.map { it.first.toString() to it.second }
        WidgetType.BOOL_RADIO -> listOf("true", "false").zip(def.fieldValues.take(2))
        else -> codesListe(def).zip(def.fieldValues)
    }

    /** Le libellé de l'entrée vide d'une liste déroulante à choix unique, quand le web en met une : le
     *  « - » d'un `select` (sauf `noNullOption`), le « -- Aucun -- » d'une datalist à `nullDefault`
     *  non requise. Les nomenclatures et les observateurs n'en ont pas : une croix vide le choix. */
    fun optionVide(def: AdditionalFieldDef): String? = when (def.widget) {
        WidgetType.SELECT -> OPTION_VIDE.takeUnless { def.sansOptionVide }
        WidgetType.DATALIST -> OPTION_AUCUN.takeIf { def.avecAucun && !def.required }
        else -> null
    }

    /** Le champ se vide par une croix (`clearable` des listes du web) : nomenclature, observateurs. */
    fun effacable(def: AdditionalFieldDef): Boolean = def.widget == WidgetType.NOMENCLATURE || def.widget == WidgetType.OBSERVERS

    /** Le champ est un choix multiple. */
    fun estMultiple(def: AdditionalFieldDef): Boolean = when (def.widget) {
        WidgetType.CHECKBOX, WidgetType.MULTISELECT -> true
        WidgetType.NOMENCLATURE, WidgetType.OBSERVERS, WidgetType.DATALIST -> def.choixMultiple
        else -> false
    }

    /** Le champ est un choix unique dans une liste ou des boutons radio. */
    fun estChoixUnique(def: AdditionalFieldDef): Boolean = !estMultiple(def) && def.widget in CHOIX

    private val CHOIX = setOf(
        WidgetType.SELECT, WidgetType.RADIO, WidgetType.BOOL_RADIO, WidgetType.DATALIST, WidgetType.NOMENCLATURE, WidgetType.OBSERVERS,
    )

    /** Codes d'un choix multiple enregistré : un tableau JSON (`["1","3"]`, `[1,3]`), un objet ou un
     *  tableau d'objets d'observateurs (leurs `id_role`) ; une ancienne case unique « true » coche sa
     *  seule option. */
    internal fun codesEnregistres(def: AdditionalFieldDef, courant: String): List<String> {
        if (courant.isBlank()) return emptyList()
        return when (val v = depuisLitteral(courant)) {
            is JSONArray -> (0 until v.length()).mapNotNull { codeEnregistre(v.opt(it)) }
            is JSONObject -> listOfNotNull(codeEnregistre(v))
            is Boolean -> if (v) listOfNotNull(codesListe(def).firstOrNull()) else emptyList()
            else -> listOfNotNull(texteValeur(v).takeIf { it.isNotEmpty() && it != "false" })
        }
    }

    /** Le code d'une valeur enregistrée : son texte, l'`id_role` d'un observateur. */
    private fun codeEnregistre(v: Any?): String? = when (v) {
        null, JSONObject.NULL -> null
        is JSONObject -> v.opt("id_role")?.let { texteValeur(it) }
        else -> texteValeur(v)
    }

    /** L'observateur [idRole] tel que le web l'enregistre : l'objet de la liste. */
    internal fun objetObservateur(idRole: String, observateurs: List<Pair<Int, String>>): JSONObject = JSONObject().apply {
        put("id_role", idRole.toIntOrNull() ?: idRole)
        observateurs.firstOrNull { it.first.toString() == idRole }?.let { put("nom_complet", it.second) }
    }

    // ── Envoi ───────────────────────────────────────────────────────────────────────────────────

    /** Les champs additionnels envoyés, comme le web les poste (`JSON.parse(JSON.stringify(form.value))`) :
     *  chaque champ du formulaire ([defs] : ceux de l'objet et du jeu de données), grisé excepté, avec
     *  sa valeur typée, `null` s'il est vide ; un champ jamais ouvert part avec sa valeur par défaut.
     *  Une valeur sans définition (champ retiré du serveur) part typée par heuristique. */
    fun versJson(valeurs: Map<String, String>, defs: List<AdditionalFieldDef>): JSONObject = JSONObject().apply {
        val definis = defs.filter { it.aUneValeur }.associateBy { it.fieldName }
        for (def in definis.values) {
            if (def.desactive) continue
            val v = valeurs[def.fieldName] ?: def.defaultValue.takeIf { def.widget != WidgetType.CHECKBOX }
            put(def.fieldName, valeurTypee(def, v))
        }
        for ((k, v) in valeurs) {
            if (k in definis || v.isEmpty()) continue
            put(k, valeurHeuristique(v))
        }
    }

    /** La valeur envoyée d'un champ, du type que lui donne le widget du web. */
    internal fun valeurTypee(def: AdditionalFieldDef, v: String?): Any {
        if (v.isNullOrEmpty() || v == "null") return JSONObject.NULL
        return when (def.widget) {
            WidgetType.NUMBER -> {
                val nombre = if (v.count { it == ',' } == 1 && !v.contains('.')) v.replace(',', '.') else v
                nombre.toLongOrNull() ?: nombre.toDoubleOrNull() ?: JSONObject.NULL
            }
            WidgetType.BOOL_CHECKBOX, WidgetType.BOOL_RADIO -> when {
                v.equals("true", ignoreCase = true) -> true
                v.equals("false", ignoreCase = true) -> false
                else -> JSONObject.NULL
            }
            WidgetType.SELECT, WidgetType.RADIO, WidgetType.DATALIST, WidgetType.CHECKBOX, WidgetType.MULTISELECT,
            WidgetType.NOMENCLATURE, WidgetType.OBSERVERS ->
                if (estMultiple(def)) {
                    when (val brut = depuisLitteral(v)) {
                        is JSONArray -> JSONArray((0 until brut.length()).map { valeurEnregistree(def, brut.opt(it)) })
                        else -> JSONArray(codesEnregistres(def, v).map { valeurEnregistree(def, it) })
                    }
                } else {
                    valeurEnregistree(def, if (def.widget == WidgetType.OBSERVERS) depuisLitteral(v) else v)
                }
            WidgetType.TAXONOMY -> (depuisLitteral(v) as? JSONObject) ?: v
            else -> v
        }
    }

    /** Une valeur enregistrée d'un choix : l'option typée ; un objet (observateur) tel quel. */
    private fun valeurEnregistree(def: AdditionalFieldDef, v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is JSONObject, is JSONArray -> v
        else -> when (def.widget) {
            WidgetType.NOMENCLATURE -> texteValeur(v)
            WidgetType.OBSERVERS -> JSONObject().apply { put("id_role", texteValeur(v).toIntOrNull() ?: texteValeur(v)) }
            else -> valeurOption(def, texteValeur(v)) ?: JSONObject.NULL
        }
    }

    /** Le taxon [cdNom] tel que le web l'enregistre (l'objet d'`allnamebylist`), avec ce que le cache
     *  en connaît : cd_nom, lb_nom, nom_vern, règne, groupes INPN ; null s'il est inconnu. */
    fun objetTaxon(taxRef: TaxRefCache, cdNom: Int): JSONObject? {
        val lbNom = taxRef.sciNomsParCdNom()[cdNom] ?: return null
        val cle = cdNom.toString()
        return JSONObject().apply {
            put("cd_nom", cdNom)
            put("lb_nom", lbNom)
            taxRef.getVernaculaireParCdNom(cdNom)?.let { put("nom_vern", it) }
            taxRef.tousLesRegnes()[cle]?.let { put("regne", it) }
            taxRef.tousLesGroupes1()[cle]?.let { put("group1_inpn", it) }
            taxRef.tousLesGroupes()[cle]?.let { put("group2_inpn", it) }
            taxRef.tousLesGroupes3()[cle]?.let { put("group3_inpn", it) }
        }
    }

    /** Une valeur sans définition, typée comme avant : booléen, nombre (virgule décimale comprise),
     *  tableau JSON, sinon texte. */
    internal fun valeurHeuristique(v: String): Any {
        val vNum = if (v.count { it == ',' } == 1 && !v.contains('.')) v.replace(',', '.') else v
        return when {
            v.equals("true", ignoreCase = true) -> true
            v.equals("false", ignoreCase = true) -> false
            v.toIntOrNull() != null -> v.toInt()
            vNum.toDoubleOrNull() != null -> vNum.toDouble()
            v.startsWith("[") && v.endsWith("]") -> try { JSONArray(v) } catch (_: Exception) { v }
            else -> v
        }
    }
}

/**
 * L'état des champs additionnels d'un écran de saisie OccTax ([defs], dans leur ordre), à partir des
 * [valeurs] déjà saisies (`fieldName` → valeur, null toléré).
 *
 * La valeur d'un champ au rendu : la valeur saisie, sinon le `default_value` du serveur, comme le
 * web ; la chaîne "null" vaut une valeur vide. Un champ masqué (`additional_attributes.hidden`) ou
 * d'un widget que l'appli ne rend pas garde sa valeur, part avec les autres et compte dans les
 * contrôles, comme sur le web ; un bloc `html` s'affiche sans valeur.
 *
 * [collecter] rend les valeurs encodées en texte, que l'envoi type ([ChampsAdditionnels.versJson]) :
 * - texte, nombre, date (aaaa-mm-jj), heure (hh:mm), widget non rendu : le texte ;
 * - choix unique : la valeur de l'option en texte (le `label_default` d'une nomenclature), l'objet
 *   JSON d'un observateur ; "" si aucun ;
 * - choix multiple : le tableau JSON des valeurs (`["1","3"]`), des objets d'observateurs ;
 * - `bool_checkbox` : "true", "false", "" tant que personne n'y a touché ;
 * - taxon : l'objet JSON du taxon choisi.
 * [observateurs] : les observateurs proposés aux champs `observers` (id_role, nom complet).
 */
class EtatChampsAdditionnels(
    defs: List<AdditionalFieldDef>,
    valeurs: Map<String, String>?,
    private val observateurs: List<Pair<Int, String>> = emptyList(),
    /** Le TaxRef local, qui propose les taxons des champs `taxonomy`. */
    private val taxRef: TaxRefCache? = null,
) {
    /** Les champs qui ont une valeur : tous sauf les blocs `html`, masqués compris. */
    val defs: List<AdditionalFieldDef> = defs.filter { it.aUneValeur }

    /** Les champs affichés, dans l'ordre : ni masqués, ni d'un widget non rendu ; blocs `html`
     *  compris. */
    val defsAffiches: List<AdditionalFieldDef> = defs.filter { it.affiche }

    private sealed class Saisie {
        class Texte(var texte: String) : Saisie()

        /** Choix unique : la valeur de l'option, null si aucune. [horsListe] : une valeur enregistrée
         *  absente des options, que le web montre grisée sans la changer. */
        class Choix(var code: String?, val horsListe: Boolean) : Saisie()

        /** Choix multiple : les valeurs choisies. */
        class ChoixMultiples(val codes: MutableList<String>) : Saisie()

        /** `bool_checkbox` : null tant que personne n'y a touché. */
        class Case(var cochee: Boolean?) : Saisie()

        /** Taxon : le texte du champ et le taxon choisi (objet JSON), null tant qu'aucun ne l'est. */
        class Taxon(var texte: String, var objet: String?) : Saisie()
    }

    private val saisies = linkedMapOf<String, Saisie>()

    init {
        this.defs.forEach { def ->
            val brut = valeurs?.get(def.fieldName) ?: def.defaultValue ?: ""
            val courant = if (brut == "null") "" else brut
            saisies[def.fieldName] = when {
                ChampsAdditionnels.estMultiple(def) -> Saisie.ChoixMultiples(ChampsAdditionnels.codesEnregistres(def, courant).toMutableList())
                ChampsAdditionnels.estChoixUnique(def) -> {
                    val code = when (def.widget) {
                        WidgetType.OBSERVERS -> ChampsAdditionnels.codesEnregistres(def, courant).firstOrNull()
                        // Une saisie d'une version antérieure porte l'id_nomenclature : retrouvée par
                        // son option.
                        WidgetType.NOMENCLATURE -> courant.takeIf { it.isNotEmpty() }?.let { c ->
                            if (def.optionsProposees.any { it.valeur == c }) c else def.optionsProposees.firstOrNull { it.code == c }?.valeur ?: c
                        }
                        else -> courant.takeIf { it.isNotEmpty() }
                    }
                    val codes = ChampsAdditionnels.options(def, observateurs).map { it.first }
                    Saisie.Choix(code, horsListe = code != null && code !in codes && def.widget == WidgetType.SELECT)
                }
                def.widget == WidgetType.BOOL_CHECKBOX -> Saisie.Case(
                    when {
                        courant.equals("true", ignoreCase = true) -> true
                        courant.equals("false", ignoreCase = true) -> false
                        else -> null
                    },
                )
                def.widget == WidgetType.TAXONOMY -> {
                    val objet = ChampsAdditionnels.depuisLitteral(courant) as? JSONObject
                    Saisie.Taxon(objet?.let(::nomTaxon) ?: courant, objet?.toString())
                }
                else -> Saisie.Texte(courant)
            }
        }
    }

    /** Les valeurs courantes, indexées par fieldName. */
    fun collecter(): Map<String, String> = defs.associate { it.fieldName to valeurChamp(it) }

    private fun valeurChamp(def: AdditionalFieldDef): String = when (val s = saisies.getValue(def.fieldName)) {
        is Saisie.Texte -> s.texte
        is Saisie.Choix -> when {
            s.code == null -> ""
            def.widget == WidgetType.OBSERVERS -> ChampsAdditionnels.objetObservateur(s.code!!, observateurs).toString()
            else -> s.code!!
        }
        is Saisie.ChoixMultiples -> if (def.widget == WidgetType.OBSERVERS) {
            JSONArray(s.codes.map { ChampsAdditionnels.objetObservateur(it, observateurs) }).toString()
        } else {
            JSONArray(s.codes).toString()
        }
        is Saisie.Case -> s.cochee?.toString().orEmpty()
        is Saisie.Taxon -> s.objet.orEmpty()
    }

    /** Le champ est vide, au sens du `Validators.required` du web. */
    private fun vide(def: AdditionalFieldDef): Boolean = when (val s = saisies.getValue(def.fieldName)) {
        is Saisie.Texte -> s.texte.isEmpty()
        is Saisie.Choix -> s.code == null
        is Saisie.ChoixMultiples -> s.codes.isEmpty()
        is Saisie.Case -> s.cochee == null
        is Saisie.Taxon -> s.objet == null
    }

    /** Le premier refus des contrôles du web (dynamic-form.service : `required`, `max_length`,
     *  `pattern` d'un texte, `min` et `max` d'un nombre), sur tous les champs, masqués compris ; null
     *  si tout va. Un champ grisé (`disabled`) n'est pas contrôlé. */
    fun refus(): String? {
        val controles = defs.filter { !it.desactive }
        fun nom(def: AdditionalFieldDef) = def.fieldLabel + if (def.affiche) "" else " (masqué)"
        val cases = controles.firstOrNull { it.required && it.widget == WidgetType.CHECKBOX && vide(it) }
        val manquants = controles.filter { it.required && it.widget != WidgetType.CHECKBOX && vide(it) }
        if (manquants.isNotEmpty()) return "Champs obligatoires à renseigner : ${manquants.joinToString(", ") { nom(it) }}"
        if (cases != null) return "${nom(cases)} : ${ChampsAdditionnels.AU_MOINS_UNE_OPTION}"
        for (def in controles) {
            if (def.widget == WidgetType.CHECKBOX) continue
            val texte = valeurChamp(def)
            val longueur = def.longueurMax
            if (longueur != null && longueur > 0 && texte.length > longueur) return "« ${nom(def)} » : $longueur caractères au plus"
            val motif = def.motif
            if (def.widget == WidgetType.TEXT && motif != null && texte.isNotEmpty()) {
                val regex = try { Regex("^(?:$motif)$") } catch (_: Exception) { null }
                if (regex != null && !regex.matches(texte)) return def.messageMotif ?: "« ${nom(def)} » : format non valide"
            }
        }
        val horsBornes = controles.filter { it.widget == WidgetType.NUMBER }.mapNotNull { def ->
            val v = valeurChamp(def).replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            val incoherent = def.min != null && def.max != null && def.min > def.max
            val min = def.min.takeUnless { incoherent }
            val max = def.max.takeUnless { incoherent }
            if ((min != null && v < min) || (max != null && v > max)) {
                "${nom(def)} (entre ${min?.let(::borne) ?: "−∞"} et ${max?.let(::borne) ?: "+∞"})"
            } else null
        }
        if (horsBornes.isNotEmpty()) return "Valeurs hors limites : ${horsBornes.joinToString(", ")}"
        return null
    }

    private fun borne(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    /** Le champ porte la barre rouge, la bordure rouge du web (`ng-invalid`) : requis et vide (une
     *  case au moins pour des cases requises), trop long, hors de son `pattern` ou de ses bornes. Les
     *  contrôles de [refus], champ par champ ; un champ grisé n'est pas contrôlé. */
    fun invalide(def: AdditionalFieldDef): Boolean {
        // Un bloc html, un widget non rendu : rien à saisir, rien à contrôler.
        if (def.desactive || def.fieldName !in saisies) return false
        if (def.required && vide(def)) return true
        if (def.widget == WidgetType.CHECKBOX) return false
        val texte = valeurChamp(def)
        val longueur = def.longueurMax
        if (longueur != null && longueur > 0 && texte.length > longueur) return true
        val motif = def.motif
        if (def.widget == WidgetType.TEXT && motif != null && texte.isNotEmpty()) {
            val regex = try { Regex("^(?:$motif)$") } catch (_: Exception) { null }
            if (regex != null && !regex.matches(texte)) return true
        }
        if (def.widget == WidgetType.NUMBER) {
            val v = texte.replace(',', '.').toDoubleOrNull() ?: return false
            val incoherent = def.min != null && def.max != null && def.min > def.max
            val min = def.min.takeUnless { incoherent }
            val max = def.max.takeUnless { incoherent }
            if ((min != null && v < min) || (max != null && v > max)) return true
        }
        return false
    }

    // ── Affichage ───────────────────────────────────────────────────────────────────────────────

    /** Le texte d'un champ texte, nombre, date ou heure. */
    fun texte(fieldName: String): String = (saisies[fieldName] as? Saisie.Texte)?.texte.orEmpty()

    /** Les options d'un champ à choix : (valeur, libellé). */
    fun options(def: AdditionalFieldDef): List<Pair<String, String>> = ChampsAdditionnels.options(def, observateurs)

    /** La valeur choisie d'un choix unique, null si aucune. */
    fun choix(fieldName: String): String? = (saisies[fieldName] as? Saisie.Choix)?.code

    /** La valeur enregistrée d'un `select`, absente de ses options : le web la montre grisée. */
    fun horsListe(fieldName: String): Boolean = (saisies[fieldName] as? Saisie.Choix)?.horsListe == true

    /** Les valeurs choisies d'un choix multiple. */
    fun choixMultiples(fieldName: String): List<String> = (saisies[fieldName] as? Saisie.ChoixMultiples)?.codes?.toList().orEmpty()

    /** L'état d'une `bool_checkbox`. */
    fun case(fieldName: String): Boolean? = (saisies[fieldName] as? Saisie.Case)?.cochee

    /** Le texte d'un champ taxon ; [taxonChoisi] : un taxon est choisi. */
    fun texteTaxon(fieldName: String): String = (saisies[fieldName] as? Saisie.Taxon)?.texte.orEmpty()

    fun taxonChoisi(fieldName: String): Boolean = (saisies[fieldName] as? Saisie.Taxon)?.objet != null

    // ── Saisies ─────────────────────────────────────────────────────────────────────────────────

    fun saisirTexte(fieldName: String, texte: String) {
        (saisies[fieldName] as? Saisie.Texte)?.texte = texte
    }

    /** Choisit l'option [code] d'un choix unique ; null le vide (entrée vide, croix). */
    fun choisir(fieldName: String, code: String?) {
        val s = saisies[fieldName] as? Saisie.Choix ?: return
        if (s.horsListe) return
        s.code = code
    }

    /** Clic sur le bouton radio [code] : le coche, ou le décoche s'il l'était (onRadioChange). */
    fun cliquerRadio(fieldName: String, code: String) {
        val s = saisies[fieldName] as? Saisie.Choix ?: return
        s.code = if (s.code == code) null else code
    }

    /** Coche ou décoche l'option [code] d'un choix multiple ; les valeurs restent dans l'ordre des
     *  coches, comme le tableau du web. */
    fun basculer(fieldName: String, code: String, coche: Boolean) {
        val s = saisies[fieldName] as? Saisie.ChoixMultiples ?: return
        if (coche && code !in s.codes) s.codes.add(code)
        if (!coche) s.codes.remove(code)
    }

    /** Remplace les valeurs d'un choix multiple (dialogue de choix), dans l'ordre des options. */
    fun choisirMultiples(fieldName: String, codes: List<String>) {
        val s = saisies[fieldName] as? Saisie.ChoixMultiples ?: return
        s.codes.clear()
        s.codes.addAll(codes)
    }

    fun cocher(fieldName: String, coche: Boolean) {
        (saisies[fieldName] as? Saisie.Case)?.cochee = coche
    }

    /** Frappe dans un champ taxon : le taxon choisi est oublié jusqu'au prochain choix. */
    fun saisirTaxon(fieldName: String, texte: String) {
        val s = saisies[fieldName] as? Saisie.Taxon ?: return
        if (texte == s.texte) return
        s.texte = texte
        s.objet = null
    }

    /** Le taxon [objet] choisi dans la liste, sous le nom [nom]. */
    fun choisirTaxon(fieldName: String, objet: JSONObject, nom: String) {
        val s = saisies[fieldName] as? Saisie.Taxon ?: return
        s.texte = nom
        s.objet = objet.toString()
    }

    /** Les propositions d'un champ taxon pour [requete], comme le `pnx-taxonomy` du web : dès trois
     *  caractères, vingt au plus, dans la liste du champ (`id_list`) s'il en a une. Construites une
     *  fois par liste. */
    fun propositionsTaxon(def: AdditionalFieldDef, requete: String): List<PropositionTaxon> {
        val ref = taxRef ?: return emptyList()
        if (requete.trim().length < 3) return emptyList()
        val p = propositions.getOrPut(def.idList) { PropositionsTaxon.construire(ref, def.idList) }
        return p.filtrer(requete).take(20)
    }

    private val propositions = mutableMapOf<Int?, PropositionsTaxon>()

    /** Choisit la [proposition] d'un champ taxon ; faux si son taxon est introuvable dans le cache. */
    fun choisirProposition(fieldName: String, proposition: PropositionTaxon): Boolean {
        val ref = taxRef ?: return false
        val def = defs.firstOrNull { it.fieldName == fieldName } ?: return false
        val cdNom = proposition.cdNom ?: ref.getDansListe(proposition.nom, def.idList)?.cdNom ?: return false
        val objet = ChampsAdditionnels.objetTaxon(ref, cdNom) ?: return false
        choisirTaxon(fieldName, objet, proposition.nom)
        return true
    }

    private companion object {
        /** Le nom à montrer d'un taxon enregistré. */
        fun nomTaxon(objet: JSONObject): String =
            objet.optString("search_name", "").ifEmpty { objet.optString("nom_vern", "") }.ifEmpty { objet.optString("lb_nom", "") }
    }
}
