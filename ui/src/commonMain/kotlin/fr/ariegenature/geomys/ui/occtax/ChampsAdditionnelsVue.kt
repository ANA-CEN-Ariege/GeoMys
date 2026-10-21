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

// Les widgets des champs additionnels OccTax : buildWidget d'AdditionalFieldsRenderer (GeoMys-Android
// @ cdd5196, ui/saisie/AdditionalFieldsRenderer.kt) sous Android, AdditionalFieldsRenderer de GeoMys-IOS
// (UI/Saisie/AdditionalFieldsRenderer.swift) sous iOS. L'état et l'encodage des valeurs sont dans le socle
// (saisie/ChampsAdditionnels.kt). Dans la carte des détails d'un relevé, sous iOS, chaque champ a sa section,
// comme les champs d'une station OccHab (demande du 20 octobre). Depuis le 20 octobre, les widgets, leurs
// libellés (sans « * »), l'aide et l'entrée vide des listes sont ceux du formulaire dynamique du web
// (dynamic-form.component.html de GeoNature 2.17.5).

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.monitoring.form.PropositionTaxon
import fr.ariegenature.geomys.monitoring.form.SelectionDateHeure
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.saisie.EtatChampsAdditionnels
import fr.ariegenature.geomys.ui.composants.ChampDateMonitoring
import fr.ariegenature.geomys.ui.composants.ClavierNombresEtPonctuation
import fr.ariegenature.geomys.ui.composants.ClavierNumeriqueSigne
import fr.ariegenature.geomys.ui.composants.DialogueChoixMultiple
import fr.ariegenature.geomys.ui.composants.FOND_LIGNE_FEUILLE_IOS
import fr.ariegenature.geomys.ui.composants.GRIS_TITRE_CHAMP
import fr.ariegenature.geomys.ui.composants.INVITE_IOS
import fr.ariegenature.geomys.ui.composants.Interrupteur
import fr.ariegenature.geomys.ui.composants.LigneChoixIos
import fr.ariegenature.geomys.ui.composants.LigneIos
import fr.ariegenature.geomys.ui.composants.LigneListeAndroid
import fr.ariegenature.geomys.ui.composants.LigneNombreIos
import fr.ariegenature.geomys.ui.composants.LigneTexteIos
import fr.ariegenature.geomys.ui.composants.LigneTexteSeulIos
import fr.ariegenature.geomys.ui.composants.ListeDeroulante
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.ModeDateHeure
import fr.ariegenature.geomys.ui.composants.PopupListeAndroid
import fr.ariegenature.geomys.ui.composants.SectionIos
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.TexteHtmlRiche
import fr.ariegenature.geomys.ui.composants.Toasts
import fr.ariegenature.geomys.ui.composants.barreChampObligatoire
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.styleSaisieIos
import fr.ariegenature.geomys.ui.composants.styleSaisieMaterial
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.util.maintenantMillis

/** `.secondary` de SwiftUI en mode sombre : libellés et aides des champs additionnels. */
private val SECONDAIRE_IOS = Color(0x99EBEBF5)

/** Les champs additionnels [etat], dans l'ordre de leurs définitions. L'état du socle n'étant pas
 *  observable, chaque saisie fait relire les champs : la liste se recompose, et chaque champ avec
 *  elle (ils ne sont pas sautables, leurs paramètres ne changeant pas). */
@Composable
fun ChampsAdditionnelsVue(etat: EtatChampsAdditionnels, modifier: Modifier = Modifier) {
    var revision by remember(etat) { mutableIntStateOf(0) }
    val relire: () -> Unit = { revision++ }
    // Lu ici : chaque saisie recompose la liste.
    revision.let {}
    if (estIos) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (def in etat.defsAffiches) ChampAdditionnelIos(def, etat, relire)
        }
    } else {
        Column(modifier) {
            for (def in etat.defsAffiches) ChampAdditionnelAndroid(def, etat, relire)
        }
    }
}

/** Les champs additionnels [etat] dans la carte des détails d'un relevé, sous iOS : une [SectionIos]
 *  par champ, son libellé sur le fond, son aide dessous. Les widgets d'[ChampAdditionnelIos], en
 *  lignes de formulaire. */
@Composable
internal fun ChampsAdditionnelsSectionsIos(etat: EtatChampsAdditionnels) {
    var revision by remember(etat) { mutableIntStateOf(0) }
    val relire: () -> Unit = { revision++ }
    // Lu ici : chaque saisie recompose les sections.
    revision.let {}
    for (def in etat.defsAffiches) SectionChampAdditionnelIos(def, etat, relire)
}

// ─── Communs ───

/** Le libellé du web : celui du champ, « Observateurs » pour un `observers-text`. */
private fun libelleWeb(def: AdditionalFieldDef): String =
    if (def.widget == WidgetType.OBSERVERS_TEXT) ChampsAdditionnels.LIBELLE_OBSERVATEURS_TEXTE else def.fieldLabel

/** Le libellé est-il au-dessus du champ ? Pas pour un bloc `html`, ni pour une case seule
 *  (`bool_checkbox`, `checkbox` d'une option), que le web libelle à côté de la case. */
private fun libelleAuDessus(def: AdditionalFieldDef, etat: EtatChampsAdditionnels): Boolean = when (def.widget) {
    WidgetType.HTML, WidgetType.BOOL_CHECKBOX -> false
    WidgetType.CHECKBOX -> etat.options(def).size != 1
    else -> true
}

/** Le message du motif d'un texte qui ne le suit pas, sous le champ, comme le web. */
private fun erreurMotif(def: AdditionalFieldDef, etat: EtatChampsAdditionnels): String? {
    val motif = def.motif ?: return null
    if (def.widget != WidgetType.TEXT) return null
    val texte = etat.texte(def.fieldName)
    if (texte.isEmpty()) return null
    val regex = try { Regex("^(?:$motif)$") } catch (_: Exception) { return null }
    return if (regex.matches(texte)) null else def.messageMotif
}

/** Le texte d'une date ou d'une heure : « jj/mm/aaaa », « hh:mm » ; null sans valeur. */
private fun affichageDateHeure(def: AdditionalFieldDef, valeur: String): String? {
    if (valeur.isEmpty()) return null
    if (def.widget == WidgetType.TIME) return valeur
    val (a, m, j) = valeur.split('-').takeIf { it.size == 3 } ?: return valeur
    return "$j/$m/$a"
}

/** Ce sur quoi s'ouvre le sélecteur d'une date ou d'une heure : la valeur, sinon maintenant. */
private fun selectionDateHeure(def: AdditionalFieldDef, valeur: String): SelectionDateHeure =
    ChampsAdditionnels.selectionDateHeure(def.widget == WidgetType.TIME, valeur, maintenantMillis())

/** La valeur d'une date (« aaaa-mm-jj ») ou d'une heure (« hh:mm ») choisie, celle du web. */
private fun valeurDateHeure(def: AdditionalFieldDef, s: SelectionDateHeure): String =
    ChampsAdditionnels.valeurDateHeure(def.widget == WidgetType.TIME, s)

/** Un champ à choix multiple affiché en liste à cocher (`checkbox`), les autres s'ouvrant dans un
 *  dialogue (`multiselect`, nomenclature, observateurs, datalist multiples). */
private fun casesALaSuite(def: AdditionalFieldDef): Boolean = def.widget == WidgetType.CHECKBOX

/** Le dialogue de choix multiple d'un champ ([ouvert]) ; les choix gardent l'ordre des options. */
@Composable
private fun DialogueMultiple(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, ouvert: Boolean, relire: () -> Unit, onFermer: () -> Unit) {
    val options = etat.options(def)
    val choisis = etat.choixMultiples(def.fieldName)
    DialogueChoixMultiple(
        visible = ouvert,
        titre = libelleWeb(def),
        options = options.map { it.second },
        coches = options.indices.filter { options[it].first in choisis }.toSet(),
        onValider = { indices ->
            etat.choisirMultiples(def.fieldName, indices.sorted().map { options[it].first })
            relire()
            onFermer()
        },
        onFermer = onFermer,
    )
}

/** L'info-bulle `definition` du web : touchée, elle s'affiche en message. */
private fun montrerDefinition(def: AdditionalFieldDef, toasts: Toasts) {
    def.definition?.let { toasts.afficher(it, longue = true) }
}

// ─── Android : les vues d'AdditionalFieldsRenderer ───

@NonSkippableComposable
@Composable
private fun ChampAdditionnelAndroid(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, relire: () -> Unit) {
    val nom = def.fieldName
    val couleurs = MaterialTheme.colorScheme
    val couleursCase = CheckboxDefaults.colors(checkedColor = CouleursGeoMys.orange, uncheckedColor = couleurs.onSurfaceVariant)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (libelleAuDessus(def, etat)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(libelleWeb(def), fontSize = 13.sp, color = GRIS_TITRE_CHAMP)
                if (def.definition != null) {
                    val toasts = LocalToasts.current
                    Text(
                        " ⓘ",
                        fontSize = 13.sp,
                        color = GRIS_TITRE_CHAMP,
                        modifier = Modifier.clickable(remember { MutableInteractionSource() }, null, role = Role.Button) { montrerDefinition(def, toasts) },
                    )
                }
            }
        }
        // La barre rouge d'un champ obligatoire vide ou refusé, la bordure rouge du web.
        Column(Modifier.fillMaxWidth().barreChampObligatoire(etat.invalide(def))) {
            when (def.widget) {
                WidgetType.HTML -> TexteHtmlRiche(def.html.orEmpty(), 14.sp, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary)
                WidgetType.SELECT, WidgetType.DATALIST, WidgetType.NOMENCLATURE, WidgetType.OBSERVERS ->
                    if (ChampsAdditionnels.estMultiple(def)) ChoixMultipleAndroid(def, etat, relire) else ListeAndroid(def, etat, relire)
                WidgetType.RADIO, WidgetType.BOOL_RADIO -> etat.options(def).forEach { (code, libelle) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { etat.cliquerRadio(nom, code); relire() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = etat.choix(nom) == code,
                            onClick = { etat.cliquerRadio(nom, code); relire() },
                            colors = RadioButtonDefaults.colors(selectedColor = CouleursGeoMys.orange, unselectedColor = couleurs.onSurfaceVariant),
                        )
                        Text(libelle, fontSize = 14.sp)
                    }
                }
                WidgetType.CHECKBOX, WidgetType.MULTISELECT -> if (casesALaSuite(def)) {
                    val choisis = etat.choixMultiples(nom)
                    val options = etat.options(def)
                    options.forEach { (code, libelle) ->
                        val coche = code in choisis
                        Row(
                            Modifier.fillMaxWidth().clickable { etat.basculer(nom, code, !coche); relire() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(coche, { etat.basculer(nom, code, it); relire() }, colors = couleursCase)
                            // Une case seule porte le libellé du champ, comme le web.
                            Text(if (options.size == 1) libelleWeb(def) else libelle, fontSize = 14.sp)
                        }
                    }
                } else {
                    ChoixMultipleAndroid(def, etat, relire)
                }
                WidgetType.BOOL_CHECKBOX -> Row(
                    Modifier.fillMaxWidth().clickable { etat.cocher(nom, etat.case(nom) != true); relire() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(etat.case(nom) == true, { etat.cocher(nom, it); relire() }, colors = couleursCase)
                    Text(libelleWeb(def), fontSize = 14.sp)
                }
                WidgetType.DATE, WidgetType.TIME -> {
                    val valeur = etat.texte(nom)
                    ChampDateMonitoring(
                        if (def.widget == WidgetType.TIME) ModeDateHeure.HEURE else ModeDateHeure.DATE,
                        affichageDateHeure(def, valeur),
                        selectionDateHeure(def, valeur),
                        { etat.saisirTexte(nom, valeurDateHeure(def, it)); relire() },
                        onEffacer = { etat.saisirTexte(nom, ""); relire() },
                    )
                }
                WidgetType.TAXONOMY -> ChampTaxonAndroid(def, etat, relire)
                // Nombre : le clavier signé et décimal d'AdditionalFieldsRenderer.
                else -> ClavierNumeriqueSigne {
                    OutlinedTextField(
                        value = etat.texte(nom),
                        onValueChange = { etat.saisirTexte(nom, it); relire() },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = styleSaisieMaterial(),
                        singleLine = def.widget != WidgetType.TEXTAREA,
                        minLines = if (def.widget == WidgetType.TEXTAREA) def.lignes ?: 2 else 1,
                        keyboardOptions = when (def.widget) {
                            WidgetType.NUMBER -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            WidgetType.OBSERVERS_TEXT -> KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                            else -> KeyboardOptions.Default
                        },
                    )
                }
            }
        }
        erreurMotif(def, etat)?.let { Text(it, fontSize = 12.sp, color = couleurs.error) }
        def.aide?.let { Text(it, fontSize = 11.sp, color = GRIS_TITRE_CHAMP) }
    }
}

/** Une liste à choix unique : l'entrée vide du web en tête quand il en a une, la croix d'effacement
 *  d'une nomenclature ou d'observateurs ; une valeur hors des options d'un `select` reste grisée. */
@Composable
private fun ListeAndroid(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, relire: () -> Unit) {
    val nom = def.fieldName
    if (etat.horsListe(nom)) {
        Text(etat.choix(nom).orEmpty(), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), modifier = Modifier.padding(8.dp))
        return
    }
    val options = etat.options(def)
    val vide = ChampsAdditionnels.optionVide(def)
    val libelles = listOfNotNull(vide) + options.map { it.second }
    val decalage = if (vide != null) 1 else 0
    val i = options.indexOfFirst { it.first == etat.choix(nom) }
    ListeDeroulante(
        libelles,
        if (i >= 0) i + decalage else if (vide != null) 0 else -1,
        { p -> etat.choisir(nom, options.getOrNull(p - decalage)?.first); relire() },
        invite = if (def.widget == WidgetType.OBSERVERS) ChampsAdditionnels.INVITE_OBSERVATEURS else libelleWeb(def).takeIf { def.widget == WidgetType.NOMENCLATURE },
        onEffacer = if (ChampsAdditionnels.effacable(def)) ({ etat.choisir(nom, null); relire() }) else null,
    )
}

/** Un choix multiple ouvert dans un dialogue : les choix, séparés de virgules. */
@Composable
private fun ChoixMultipleAndroid(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, relire: () -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    val couleurs = MaterialTheme.colorScheme
    val options = etat.options(def)
    val choisis = etat.choixMultiples(def.fieldName).mapNotNull { c -> options.firstOrNull { it.first == c }?.second }
    Text(
        if (choisis.isEmpty()) "Choisir…" else choisis.joinToString(", "),
        fontSize = 16.sp,
        color = if (choisis.isEmpty()) couleurs.onSurfaceVariant else couleurs.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, couleurs.outline, RoundedCornerShape(4.dp))
            .clickable(remember { MutableInteractionSource() }, null, enabled = options.isNotEmpty(), role = Role.Button) { ouvert = true }
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
    DialogueMultiple(def, etat, ouvert, relire) { ouvert = false }
}

/** Un taxon : le champ, ses propositions dessous tant qu'il a le focus (dès trois lettres). */
@Composable
private fun ChampTaxonAndroid(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, relire: () -> Unit) {
    val nom = def.fieldName
    val focus = LocalFocusManager.current
    val interactions = remember { MutableInteractionSource() }
    val focalise by interactions.collectIsFocusedAsState()
    var ancre by remember { mutableStateOf<Rect?>(null) }
    var propositions by remember { mutableStateOf(emptyList<PropositionTaxon>()) }
    Box(Modifier.onGloballyPositioned { ancre = Rect(it.positionInWindow(), it.size.toSize()) }) {
        OutlinedTextField(
            value = etat.texteTaxon(nom),
            onValueChange = {
                etat.saisirTaxon(nom, it)
                propositions = etat.propositionsTaxon(def, it)
                relire()
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = styleSaisieMaterial(),
            singleLine = true,
            interactionSource = interactions,
        )
        val zone = ancre
        if (focalise && propositions.isNotEmpty() && zone != null) {
            PopupListeAndroid(zone, propositions, onFermer = { propositions = emptyList() }) { p ->
                LigneListeAndroid(p.nom) {
                    etat.choisirProposition(nom, p)
                    propositions = emptyList()
                    focus.clearFocus()
                    relire()
                }
            }
        }
    }
}

// ─── iOS : les vues d'AdditionalFieldsRenderer de GeoMys-IOS ───

@NonSkippableComposable
@Composable
private fun ChampAdditionnelIos(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, relire: () -> Unit) {
    val nom = def.fieldName
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (libelleAuDessus(def, etat)) LibelleIos(def, 12f)
        // La barre rouge d'un champ obligatoire vide ou refusé, la bordure rouge du web.
        Column(Modifier.fillMaxWidth().barreChampObligatoire(etat.invalide(def)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (def.widget) {
                WidgetType.HTML -> TexteHtmlRiche(def.html.orEmpty(), 15.sp, Color.White, CouleursGeoMys.jauneClair)
                WidgetType.SELECT, WidgetType.DATALIST, WidgetType.NOMENCLATURE, WidgetType.OBSERVERS ->
                    if (ChampsAdditionnels.estMultiple(def)) ChoixMultipleIos(def, etat, relire) else ListeIos(def, etat, relire)
                WidgetType.RADIO, WidgetType.BOOL_RADIO -> {
                    val options = etat.options(def)
                    val pastille: @Composable (Pair<String, String>) -> Unit = { (code, libelle) ->
                        val choisi = etat.choix(nom) == code
                        Row(
                            Modifier.clickable { etat.cliquerRadio(nom, code); relire() },
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SymboleSf(if (choisi) "largecircle.fill.circle" else "circle", 18f, listOf(if (choisi) CouleursGeoMys.vert else SECONDAIRE_IOS))
                            Text(libelle, color = Color.White)
                        }
                    }
                    // Une option par ligne, comme sous Android (demande du 21 octobre : sur une même
                    // ligne, les libellés longs s'écrasaient).
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { pastille(it) } }
                }
                WidgetType.CHECKBOX, WidgetType.MULTISELECT -> if (casesALaSuite(def)) {
                    val choisis = etat.choixMultiples(nom)
                    val options = etat.options(def)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { (code, libelle) ->
                            val coche = code in choisis
                            Row(
                                Modifier.clickable { etat.basculer(nom, code, !coche); relire() },
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SymboleSf(if (coche) "checkmark.square.fill" else "square", 17f, listOf(if (coche) CouleursGeoMys.vert else SECONDAIRE_IOS))
                                Text(if (options.size == 1) libelleWeb(def) else libelle, color = Color.White)
                            }
                        }
                    }
                } else {
                    ChoixMultipleIos(def, etat, relire)
                }
                WidgetType.BOOL_CHECKBOX -> {
                    val coche = etat.case(nom) == true
                    Row(
                        Modifier.clickable { etat.cocher(nom, !coche); relire() },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SymboleSf(if (coche) "checkmark.square.fill" else "square", 17f, listOf(if (coche) CouleursGeoMys.vert else SECONDAIRE_IOS))
                        Text(libelleWeb(def), color = Color.White)
                    }
                }
                WidgetType.DATE, WidgetType.TIME -> {
                    val valeur = etat.texte(nom)
                    ChampDateMonitoring(
                        if (def.widget == WidgetType.TIME) ModeDateHeure.HEURE else ModeDateHeure.DATE,
                        affichageDateHeure(def, valeur),
                        selectionDateHeure(def, valeur),
                        { etat.saisirTexte(nom, valeurDateHeure(def, it)); relire() },
                        onEffacer = { etat.saisirTexte(nom, ""); relire() },
                    )
                }
                WidgetType.TAXONOMY -> ChampTaxonIos(def, etat, relire, enLigne = false)
                WidgetType.TEXTAREA -> BasicTextField(
                    value = etat.texte(nom),
                    onValueChange = { etat.saisirTexte(nom, it); relire() },
                    textStyle = styleSaisieIos,
                    cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = def.lignes ?: 1,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 120.dp).background(Color.Black).padding(4.dp),
                )
                WidgetType.NUMBER -> BasicTextField(
                    value = etat.texte(nom),
                    // La virgule des claviers français est gardée : l'envoi la convertit en point.
                    onValueChange = { v -> etat.saisirTexte(nom, v.filter { it in "0123456789.,-" }); relire() },
                    singleLine = true,
                    textStyle = styleSaisieIos,
                    cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                    keyboardOptions = ClavierNombresEtPonctuation,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> BasicTextField(
                    value = etat.texte(nom),
                    onValueChange = { etat.saisirTexte(nom, it); relire() },
                    singleLine = true,
                    textStyle = styleSaisieIos,
                    cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                    keyboardOptions = KeyboardOptions(
                        capitalization = if (def.widget == WidgetType.OBSERVERS_TEXT) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        erreurMotif(def, etat)?.let { Text(it, fontSize = 12.sp, color = CouleursGeoMys.rougePastille) }
        def.aide?.let { Text(it, fontSize = 11.sp, color = SECONDAIRE_IOS) }
    }
}

/** Le libellé d'un champ et, s'il a une `definition`, le « ⓘ » qui la montre. */
@Composable
private fun LibelleIos(def: AdditionalFieldDef, taille: Float, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(libelleWeb(def), fontSize = taille.sp, color = SECONDAIRE_IOS)
        if (def.definition != null) {
            val toasts = LocalToasts.current
            Box(Modifier.clickable(remember { MutableInteractionSource() }, null, role = Role.Button) { montrerDefinition(def, toasts) }) {
                SymboleSf("info.circle", taille, listOf(SECONDAIRE_IOS), description = "Définition")
            }
        }
    }
}

/** Une liste à choix unique en menu du système (cf. [ListeAndroid]). */
@Composable
private fun ListeIos(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, relire: () -> Unit) {
    val nom = def.fieldName
    if (etat.horsListe(nom)) {
        Text(etat.choix(nom).orEmpty(), fontSize = 17.sp, color = SECONDAIRE_IOS)
        return
    }
    val options = etat.options(def)
    val vide = ChampsAdditionnels.optionVide(def)
    val decalage = if (vide != null) 1 else 0
    val i = options.indexOfFirst { it.first == etat.choix(nom) }
    ListeDeroulante(
        listOfNotNull(vide) + options.map { it.second },
        if (i >= 0) i + decalage else if (vide != null) 0 else -1,
        { p -> etat.choisir(nom, options.getOrNull(p - decalage)?.first); relire() },
        invite = if (def.widget == WidgetType.OBSERVERS) ChampsAdditionnels.INVITE_OBSERVATEURS else libelleWeb(def).takeIf { def.widget == WidgetType.NOMENCLATURE },
        onEffacer = if (ChampsAdditionnels.effacable(def)) ({ etat.choisir(nom, null); relire() }) else null,
    )
}

/** Un choix multiple ouvert dans la feuille de choix (cf. [ChoixMultipleAndroid]). */
@Composable
private fun ChoixMultipleIos(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, relire: () -> Unit, enLigne: Boolean = false) {
    var ouvert by remember { mutableStateOf(false) }
    val options = etat.options(def)
    val choisis = etat.choixMultiples(def.fieldName).mapNotNull { c -> options.firstOrNull { it.first == c }?.second }
    val contenu: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null, enabled = options.isNotEmpty(), role = Role.Button) { ouvert = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (choisis.isEmpty()) "Choisir…" else choisis.joinToString(", "),
                fontSize = 17.sp,
                color = if (choisis.isEmpty()) SECONDAIRE_IOS else Color.White,
                modifier = Modifier.weight(1f),
            )
            SymboleSf("chevron.right", 13f, listOf(SECONDAIRE_IOS))
        }
    }
    if (enLigne) LigneIos { contenu() } else contenu()
    DialogueMultiple(def, etat, ouvert, relire) { ouvert = false }
}

/** Un taxon : le champ nu, ses propositions en lignes dessous tant qu'il a le focus (dès trois
 *  lettres) ; [enLigne] : dans une section de formulaire. */
@Composable
private fun ChampTaxonIos(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, relire: () -> Unit, enLigne: Boolean) {
    val nom = def.fieldName
    val focus = LocalFocusManager.current
    var focalise by remember { mutableStateOf(false) }
    var propositions by remember { mutableStateOf(emptyList<PropositionTaxon>()) }
    val champ: @Composable () -> Unit = {
        BasicTextField(
            value = etat.texteTaxon(nom),
            onValueChange = {
                etat.saisirTaxon(nom, it)
                propositions = etat.propositionsTaxon(def, it)
                relire()
            },
            singleLine = true,
            textStyle = styleSaisieIos,
            cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focalise = it.isFocused },
            decorationBox = { interieur ->
                Box {
                    if (etat.texteTaxon(nom).isEmpty()) Text("Tapez les premières lettres…", style = styleSaisieIos.copy(color = INVITE_IOS))
                    interieur()
                }
            },
        )
    }
    if (enLigne) LigneIos { champ() } else champ()
    if (focalise) {
        propositions.forEach { p ->
            if (enLigne) SeparateurIos(Modifier.padding(start = 16.dp))
            val ligne: @Composable () -> Unit = {
                Text(p.nom, fontSize = 17.sp, color = Color.White, modifier = Modifier.fillMaxWidth().clickable {
                    etat.choisirProposition(nom, p)
                    propositions = emptyList()
                    focus.clearFocus()
                    relire()
                }.padding(vertical = if (enLigne) 0.dp else 6.dp))
            }
            if (enLigne) LigneIos { ligne() } else ligne()
        }
    }
}

@NonSkippableComposable
@Composable
private fun SectionChampAdditionnelIos(def: AdditionalFieldDef, etat: EtatChampsAdditionnels, relire: () -> Unit) {
    val nom = def.fieldName
    Column {
        // Le libellé de la section : celui du champ, sauf pour une case seule, libellée sur sa ligne.
        if (libelleAuDessus(def, etat)) LibelleIos(def, 13f, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp))
        SectionIos(null, barre = etat.invalide(def)) {
            when (def.widget) {
                WidgetType.HTML -> LigneIos { TexteHtmlRiche(def.html.orEmpty(), 15.sp, Color.White, CouleursGeoMys.jauneClair) }
                WidgetType.SELECT, WidgetType.DATALIST, WidgetType.NOMENCLATURE, WidgetType.OBSERVERS ->
                    if (ChampsAdditionnels.estMultiple(def)) {
                        ChoixMultipleIos(def, etat, relire, enLigne = true)
                    } else if (etat.horsListe(nom)) {
                        LigneTexteSeulIos(etat.choix(nom).orEmpty(), couleur = SECONDAIRE_IOS)
                    } else {
                        val options = etat.options(def)
                        val vide = ChampsAdditionnels.optionVide(def)
                        val decalage = if (vide != null) 1 else 0
                        val i = options.indexOfFirst { it.first == etat.choix(nom) }
                        LigneChoixIos(
                            "",
                            listOfNotNull(vide) + options.map { it.second },
                            if (i >= 0) i + decalage else if (vide != null) 0 else -1,
                            invite = if (def.widget == WidgetType.OBSERVERS) ChampsAdditionnels.INVITE_OBSERVATEURS else libelleWeb(def).takeIf { def.widget == WidgetType.NOMENCLATURE },
                            onEffacer = if (ChampsAdditionnels.effacable(def)) ({ etat.choisir(nom, null); relire() }) else null,
                        ) { p -> etat.choisir(nom, options.getOrNull(p - decalage)?.first); relire() }
                    }
                WidgetType.RADIO, WidgetType.BOOL_RADIO -> etat.options(def).forEachIndexed { i, (code, libelle) ->
                    if (i > 0) SeparateurIos(Modifier.padding(start = 16.dp))
                    LigneOptionIos(libelle, etat.choix(nom) == code, "largecircle.fill.circle", "circle") {
                        etat.cliquerRadio(nom, code)
                        relire()
                    }
                }
                WidgetType.CHECKBOX, WidgetType.MULTISELECT -> if (casesALaSuite(def)) {
                    val choisis = etat.choixMultiples(nom)
                    val options = etat.options(def)
                    options.forEachIndexed { i, (code, libelle) ->
                        val coche = code in choisis
                        if (i > 0) SeparateurIos(Modifier.padding(start = 16.dp))
                        LigneOptionIos(if (options.size == 1) libelleWeb(def) else libelle, coche, "checkmark.square.fill", "square") {
                            etat.basculer(nom, code, !coche)
                            relire()
                        }
                    }
                } else {
                    ChoixMultipleIos(def, etat, relire, enLigne = true)
                }
                WidgetType.BOOL_CHECKBOX -> LigneIos {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(libelleWeb(def), fontSize = 17.sp, color = Color.White, modifier = Modifier.weight(1f))
                        Interrupteur(
                            etat.case(nom) == true,
                            { etat.cocher(nom, it); relire() },
                            teinteIos = CouleursGeoMys.vert,
                            fondIos = FOND_LIGNE_FEUILLE_IOS,
                        )
                    }
                }
                WidgetType.DATE, WidgetType.TIME -> LigneIos {
                    val valeur = etat.texte(nom)
                    ChampDateMonitoring(
                        if (def.widget == WidgetType.TIME) ModeDateHeure.HEURE else ModeDateHeure.DATE,
                        affichageDateHeure(def, valeur),
                        selectionDateHeure(def, valeur),
                        { etat.saisirTexte(nom, valeurDateHeure(def, it)); relire() },
                        onEffacer = { etat.saisirTexte(nom, ""); relire() },
                    )
                }
                WidgetType.TAXONOMY -> ChampTaxonIos(def, etat, relire, enLigne = true)
                WidgetType.TEXTAREA -> LigneTexteIos(
                    etat.texte(nom),
                    { etat.saisirTexte(nom, it); relire() },
                    "",
                    lignesMin = def.lignes ?: 2,
                    lignesMax = maxOf(def.lignes ?: 2, 5),
                    clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                // La virgule des claviers français est gardée : l'envoi la convertit en point.
                WidgetType.NUMBER -> LigneNombreIos("", etat.texte(nom), { v -> etat.saisirTexte(nom, v.filter { it in "0123456789.,-" }); relire() })
                else -> LigneTexteIos(
                    etat.texte(nom),
                    { etat.saisirTexte(nom, it); relire() },
                    "",
                    clavier = KeyboardOptions(
                        capitalization = if (def.widget == WidgetType.OBSERVERS_TEXT) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
                    ),
                )
            }
        }
        erreurMotif(def, etat)?.let {
            Text(it, fontSize = 13.sp, color = CouleursGeoMys.rougePastille, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        }
        def.aide?.let {
            Text(it, fontSize = 13.sp, color = SECONDAIRE_IOS, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        }
    }
}

/** Une option d'un bouton radio ou d'une case multiple, sur sa ligne : le symbole [plein] (vert) ou
 *  [vide], puis le libellé. */
@Composable
private fun LigneOptionIos(libelle: String, choisie: Boolean, plein: String, vide: String, onClick: () -> Unit) {
    LigneIos(Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SymboleSf(if (choisie) plein else vide, 20f, listOf(if (choisie) CouleursGeoMys.vert else SECONDAIRE_IOS))
            Text(libelle, fontSize = 17.sp, color = Color.White)
        }
    }
}
