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

// Les widgets du formulaire des Suivis : creerLigne et les creerChamp* du FormulaireRenderer de GeoMys-Android
// @ cdd5196 (monitoring/form/FormulaireRenderer.kt) sous Android, FormulaireRendererView, WidgetTaxonView et
// WidgetSelectMultipleView de GeoMys-IOS (UI/Monitoring/FormulaireRenderer.swift) sous iOS : un `Form` SwiftUI,
// dont chaque ligne est un champ. L'état, les règles et les valeurs sont dans le socle (monitoring/form).

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import fr.ariegenature.geomys.monitoring.form.CARACTERES_TAXON
import fr.ariegenature.geomys.monitoring.form.EditableField
import fr.ariegenature.geomys.monitoring.form.FormulaireEtat
import fr.ariegenature.geomys.monitoring.form.PropositionTaxon
import fr.ariegenature.geomys.monitoring.form.SelectionDateHeure
import fr.ariegenature.geomys.monitoring.form.StatutTaxon
import fr.ariegenature.geomys.monitoring.form.ViewType
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonPlein
import fr.ariegenature.geomys.ui.composants.ChampDateMonitoring
import fr.ariegenature.geomys.ui.composants.ChampTexteFormulaire
import fr.ariegenature.geomys.ui.composants.ClavierNombresEtPonctuation
import fr.ariegenature.geomys.ui.composants.ClavierNumeriqueSigne
import fr.ariegenature.geomys.ui.composants.DialogueChoixMultiple
import fr.ariegenature.geomys.ui.composants.FOND_LIGNE_FORMULAIRE_IOS
import fr.ariegenature.geomys.ui.composants.Interrupteur
import fr.ariegenature.geomys.ui.composants.ListeDeroulante
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.MenuSysteme
import fr.ariegenature.geomys.ui.composants.ModeDateHeure
import fr.ariegenature.geomys.ui.composants.PopupListeAndroid
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.TexteHtmlRiche
import fr.ariegenature.geomys.ui.composants.barreChampObligatoire
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.nomMedia
import fr.ariegenature.geomys.ui.composants.rememberValeurChamp
import fr.ariegenature.geomys.ui.composants.styleSaisieIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FormeContinue


/** `.secondary` de SwiftUI en mode sombre : libellés, invites et symboles éteints sous iOS. */
private val SECONDAIRE_IOS = Color(0x99EBEBF5)


/** Le formulaire de [etat] : ses champs visibles, dans l'ordre, chacun avec son libellé (« * » s'il
 *  est requis), son aide, et la barre rouge des obligatoires encore vides s'il est dans [barres].
 *  [onAjouterPhotos] reçoit le code d'un champ MEDIA dont on touche « Ajouter des photos »,
 *  [onChoixMultiple] celui d'un SELECT_MULTIPLE à ouvrir (le dialogue est posé à la racine de
 *  l'écran : [DialogueChoixMultipleFormulaire]).
 *  - Android : les lignes de 8 dp de marge verticale du FormulaireRenderer, 16 dp sous le contexte ;
 *  - iOS : la section d'un `Form` SwiftUI (fond #1C1C1E aux coins de 10 pt, lignes séparées).
 *  L'état du socle n'étant pas observable champ par champ, chaque révision recompose le formulaire,
 *  et chaque ligne avec lui : les composables qui lisent l'état ne sont pas sautables (l'état et ses
 *  champs restent les mêmes instances d'une révision à l'autre). */
@Composable
internal fun FormulaireMonitoring(
    etat: FormulaireEtat,
    barres: Set<String>,
    onAjouterPhotos: (String) -> Unit,
    onChoixMultiple: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val revision by etat.revision.collectAsState()
    // Lue ici : chaque révision recompose la liste.
    revision.let {}
    val visibles = etat.champs.filter { etat.estVisible(it.code) }
    if (estIos) {
        if (visibles.isEmpty()) return
        Column(modifier.fillMaxWidth().background(FOND_LIGNE_FORMULAIRE_IOS, FormeContinue(10.dp))) {
            visibles.forEachIndexed { i, f ->
                key(f.code) { LigneChampIos(f, etat, f.code in barres, onAjouterPhotos, onChoixMultiple) }
                if (i < visibles.lastIndex) SeparateurIos(Modifier.padding(start = 16.dp))
            }
        }
    } else {
        Column(modifier.fillMaxWidth().padding(top = 16.dp)) {
            visibles.forEach { f -> key(f.code) { LigneChampAndroid(f, etat, f.code in barres, onAjouterPhotos, onChoixMultiple) } }
        }
    }
}

/** Le libellé du web, sans « * » ; un champ obligatoire vide se repère à sa barre rouge. */
@Suppress("UNUSED_PARAMETER")
private fun libelle(f: EditableField, etat: FormulaireEtat) = f.label

/** Les entrées d'une liste à choix unique et la position choisie : l'entrée vide du web en tête
 *  s'il en a une ([EditableField.optionVide]), sinon aucune (-1 sans choix). */
private fun entreesListe(f: EditableField, etat: FormulaireEtat): Pair<List<String>, Int> {
    val vide = f.optionVide
    val choisi = etat.indexChoisi(f.code)
    return (listOfNotNull(vide) + f.values.map { it.label }) to (if (vide != null) choisi + 1 else choisi)
}

/** La position choisie dans [entreesListe] rapportée à l'option. */
private fun optionDe(f: EditableField, position: Int): Int = if (f.optionVide != null) position - 1 else position

/** La barre de la ligne d'un champ : celle d'un champ médias rempli passe sur les titres de ses
 *  médias, que le web exige (cf. [ChampMediasAndroid]). */
private fun barreDeLigne(f: EditableField, etat: FormulaireEtat, barre: Boolean): Boolean =
    barre && !(f.viewType == ViewType.MEDIA && etat.medias(f.code).isNotEmpty())

/** L'étiquette de test de ce que borde la barre rouge d'un champ (Android : le champ seul). */
internal fun etiquetteChamp(code: String) = "champ:$code"


private fun modeDateHeure(type: ViewType) = when (type) {
    ViewType.TIME -> ModeDateHeure.HEURE
    ViewType.DATETIME -> ModeDateHeure.DATE_HEURE
    else -> ModeDateHeure.DATE
}

private fun choisirDateHeure(etat: FormulaireEtat, f: EditableField, s: SelectionDateHeure) = when (f.viewType) {
    ViewType.TIME -> etat.choisirHeure(f.code, s.heure, s.minute)
    ViewType.DATETIME -> etat.choisirDateHeure(f.code, s.annee, s.mois, s.jour, s.heure, s.minute)
    else -> etat.choisirDate(f.code, s.annee, s.mois, s.jour)
}

/** Un nombre tapé : chiffres, signe, et séparateur décimal pour un widget décimal (la virgule des
 *  claviers français vaut le point à la lecture) — le DigitsKeyListener d'un EditText numérique. */
private fun filtrerNombre(texte: String, decimal: Boolean) =
    texte.filter { it.isDigit() || it == '-' || (decimal && (it == '.' || it == ',')) }

/** Le choix multiple d'un SELECT_MULTIPLE : les libellés des valeurs choisies, joints. */
private fun libellesChoisis(f: EditableField, etat: FormulaireEtat): List<String> {
    val choisies = etat.choixMultiples(f.code).map { it.toString() }.toSet()
    return f.values.filter { it.value in choisies }.map { it.label }
}

/** Le dialogue de choix multiple du champ [code] de [etat] (null : fermé), à poser à la racine de
 *  l'écran ; le dernier champ ouvert reste composé le temps de la disparition. */
@Composable
internal fun DialogueChoixMultipleFormulaire(etat: FormulaireEtat, code: String?, onFermer: () -> Unit) {
    var dernier by remember { mutableStateOf<String?>(null) }
    code?.let { dernier = it }
    val f = dernier?.let { c -> etat.champs.firstOrNull { it.code == c } } ?: return
    val choisies = etat.choixMultiples(f.code).map { it.toString() }.toSet()
    DialogueChoixMultiple(
        visible = code != null,
        titre = f.label,
        options = f.values.map { it.label },
        coches = f.values.indices.filter { f.values[it].value in choisies }.toSet(),
        onValider = { indices -> etat.validerChoixMultiple(f.code, indices.map { f.values[it].value }.toSet()) },
        onFermer = onFermer,
    )
}

// ─── Android : FormulaireRenderer ───

/** Une ligne : libellé de 13 sp (colorOnSurfaceVariant), le champ, puis le taxon retenu, l'erreur de
 *  bornes et l'aide (12 sp, italique) ; la barre rouge de 3 dp borde le seul champ. */
@NonSkippableComposable
@Composable
private fun LigneChampAndroid(
    f: EditableField,
    etat: FormulaireEtat,
    barre: Boolean,
    onAjouterPhotos: (String) -> Unit,
    onChoixMultiple: (String) -> Unit,
) {
    val couleurs = MaterialTheme.colorScheme
    if (f.viewType == ViewType.HTML) {
        // Le texte du protocole, sans libellé, comme le web.
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            TexteHtmlRiche(f.html.orEmpty(), 14.sp, couleurs.onSurface, couleurs.primary)
            // Le `help` du web, sous le bloc.
            f.aide?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = couleurs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)) }
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(libelle(f, etat), fontSize = 13.sp, color = couleurs.onSurfaceVariant)
            // L'info-bulle `definition` du web : touchée, elle s'affiche en message.
            f.definition?.takeIf { it.isNotEmpty() }?.let { definition ->
                val toasts = LocalToasts.current
                Text(
                    " ⓘ",
                    fontSize = 13.sp,
                    color = couleurs.onSurfaceVariant,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, null, role = Role.Button) { toasts.afficher(definition, longue = true) },
                )
            }
        }
        Box(Modifier.fillMaxWidth().testTag(etiquetteChamp(f.code)).barreChampObligatoire(barreDeLigne(f, etat, barre))) {
            ChampAndroid(f, etat, onAjouterPhotos, onChoixMultiple)
        }
        if (f.viewType == ViewType.TAXON) {
            etat.statutTaxon(f.code)?.let { statut ->
                val couleur = when (statut) {
                    is StatutTaxon.Retenu -> CouleursGeoMys.succes
                    is StatutTaxon.PasDeDonnees -> CouleursGeoMys.avertissement
                    is StatutTaxon.Refus -> couleurs.error
                }
                Text(statut.texte, fontSize = 12.sp, color = couleur, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (f.viewType == ViewType.NUMBER || f.viewType == ViewType.DATE || f.motif != null) {
            etat.erreurValidation(f.code)?.let { Text(it, fontSize = 12.sp, color = couleurs.error, modifier = Modifier.padding(top = 2.dp)) }
        }
        f.aide?.takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = couleurs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@NonSkippableComposable
@Composable
private fun ChampAndroid(f: EditableField, etat: FormulaireEtat, onAjouterPhotos: (String) -> Unit, onChoixMultiple: (String) -> Unit) {
    val code = f.code
    val couleurs = MaterialTheme.colorScheme
    when (f.viewType) {
        ViewType.TEXT -> ChampTexteFormulaire(etat.texte(code), { etat.saisirTexte(code, it) })
        // TYPE_TEXT_FLAG_MULTI_LINE et setLines(3) : trois lignes, défilantes au-delà.
        ViewType.TEXTAREA -> (f.lignes ?: 3).let { n -> ChampTexteFormulaire(etat.texte(code), { etat.saisirTexte(code, it) }, lignesMin = n, uneLigne = false, lignesMaxAndroid = n) }
        ViewType.NUMBER -> ChampNombreAndroid(f, etat)
        ViewType.DATE, ViewType.TIME, ViewType.DATETIME -> ChampDateMonitoring(
            modeDateHeure(f.viewType),
            etat.affichageDateHeure(code),
            etat.selectionDateHeure(code),
            { choisirDateHeure(etat, f, it) },
            onEffacer = { etat.effacerDateHeure(code) },
        )
        ViewType.TAXON -> ChampTaxonAndroid(f, etat)
        ViewType.SELECT -> etat.valeurHorsListe(code)?.let { horsListe ->
            // Une valeur hors des options d'un `select` : grisée, comme sur le web.
            Text(horsListe, fontSize = 16.sp, color = couleurs.onSurface.copy(alpha = 0.38f), modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
        } ?: run {
            val (libelles, position) = entreesListe(f, etat)
            ListeDeroulante(
                libelles,
                position,
                { etat.choisirOption(code, optionDe(f, it)) },
                onEffacer = if (f.effacable) ({ etat.choisirOption(code, -1) }) else null,
            )
        }
        ViewType.RADIO -> {
            val horizontal = radioEnLigne(f.values.map { it.label })
            val choisi = etat.indexChoisi(code)
            val bouton: @Composable (Int) -> Unit = { i ->
                Row(
                    Modifier
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.RadioButton) { etat.choisirRadio(code, i) }
                        .padding(end = if (horizontal) 16.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = choisi == i,
                        onClick = { etat.choisirRadio(code, i) },
                        colors = RadioButtonDefaults.colors(selectedColor = CouleursGeoMys.orange, unselectedColor = couleurs.onSurfaceVariant),
                    )
                    Text(f.values[i].label, fontSize = 14.sp, color = couleurs.onSurface)
                }
            }
            if (horizontal) Row { f.values.indices.forEach { bouton(it) } } else Column { f.values.indices.forEach { bouton(it) } }
        }
        ViewType.SELECT_MULTIPLE -> {
            val libelles = libellesChoisis(f, etat)
            val vide = f.values.isEmpty()
            Text(
                when {
                    vide -> "(aucune option disponible)"
                    libelles.isEmpty() -> "Choisir…"
                    else -> libelles.joinToString(", ")
                },
                fontSize = 16.sp,
                color = when {
                    vide -> couleurs.onSurface.copy(alpha = 0.38f)
                    libelles.isEmpty() -> couleurs.onSurfaceVariant
                    else -> couleurs.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .cadreCarteAndroid()
                    .clickable(remember { MutableInteractionSource() }, null, enabled = !vide, role = Role.Button) { onChoixMultiple(code) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
        ViewType.CHECKBOX_MULTIPLE -> {
            if (f.values.isEmpty()) {
                Text("(aucune option disponible)", fontSize = 14.sp, color = couleurs.onSurfaceVariant)
            } else {
                val cases = etat.cases(code)
                Column {
                    f.values.forEachIndexed { i, option ->
                        val coche = cases.getOrElse(i) { false }
                        Row(
                            Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null, role = Role.Checkbox) { etat.cocherCase(code, i, !coche) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(coche, { etat.cocherCase(code, i, it) }, colors = couleursCase())
                            Text(option.label, fontSize = 14.sp, color = couleurs.onSurface)
                        }
                    }
                }
            }
        }
        // Le libellé est au-dessus, comme pour les autres champs : la case n'a pas de texte.
        ViewType.CHECKBOX -> Checkbox(etat.cases(code).firstOrNull() == true, { etat.cocher(code, it) }, colors = couleursCase())
        ViewType.MEDIA -> ChampMediasAndroid(code, etat, onAjouterPhotos)
        // Affiché par la ligne, sans libellé.
        ViewType.HTML -> {}
    }
}

@Composable
private fun couleursCase() = CheckboxDefaults.colors(checkedColor = CouleursGeoMys.orange, uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant)

/** `bg_carte_contour` : trait de 1 dp #40888888 aux coins de 8 dp, sans fond. */
private fun Modifier.cadreCarteAndroid(): Modifier = drawBehind {
    val trait = 1.dp.toPx()
    drawRoundRect(
        Color(0x40888888),
        topLeft = Offset(trait / 2, trait / 2),
        size = Size(size.width - trait, size.height - trait),
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(trait),
    )
}

/** Champ NUMBER : clavier numérique signé (décimal pour un widget décimal). Un défaut posé à la
 *  construction est une valeur « fantôme » : il s'efface quand on entre dans le champ encore
 *  intact, et revient si on le quitte vide. */
@NonSkippableComposable
@Composable
private fun ChampNombreAndroid(f: EditableField, etat: FormulaireEtat) {
    val code = f.code
    // Vidé, il reste vide (null), comme sur le web : la valeur par défaut ne revient pas.
    ClavierNumeriqueSigne {
        ChampTexteFormulaire(
            etat.texte(code),
            { etat.saisirTexte(code, filtrerNombre(it, f.decimal)) },
            clavier = KeyboardOptions(keyboardType = if (f.decimal) KeyboardType.Decimal else KeyboardType.Number),
        )
    }
}

/** Champ TAXON : AutoCompleteTextView d'EditText souligné (18 sp), invite « Tapez le nom du
 *  taxon… », propositions à partir de trois caractères, comme le web (nom, et dessous en italique le nom
 *  scientifique) ; quitté sur un nom non reconnu, il porte l'icône d'erreur d'`EditText.setError`. */
@NonSkippableComposable
@Composable
private fun ChampTaxonAndroid(f: EditableField, etat: FormulaireEtat) {
    val code = f.code
    val couleurs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val clavier = LocalSoftwareKeyboardController.current
    var ancre by remember { mutableStateOf<Rect?>(null) }
    // Liste fermée (choix fait, toucher dehors, retour) jusqu'à la frappe suivante.
    var fermee by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val focalise by interactions.collectIsFocusedAsState()
    var etaitFocalise by remember { mutableStateOf(false) }
    val texte = etat.texte(code)
    val (valeur, onValeur) = rememberValeurChamp(texte) {
        fermee = false
        etat.saisirTexteTaxon(code, it)
    }
    val trait = if (focalise) CouleursGeoMys.orange else couleurs.onSurfaceVariant
    val epaisseur = if (focalise) 2.dp else 1.dp
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, color = couleurs.onSurface)
    Box(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .onGloballyPositioned { ancre = Rect(it.positionInWindow(), it.size.toSize()) }
            .drawBehind {
                val e = epaisseur.toPx()
                val retrait = 4.dp.toPx()
                val y = size.height - 7.dp.toPx() - e / 2
                drawLine(trait, Offset(retrait, y), Offset(size.width - retrait, y), e)
            }
            .padding(PaddingValues(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 14.dp)),
    ) {
        BasicTextField(
            value = valeur,
            onValueChange = onValeur,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(CouleursGeoMys.orange),
            interactionSource = interactions,
            modifier = Modifier.fillMaxWidth().onFocusChanged { s ->
                // Sortie du champ : signalement si le nom ne désigne aucun taxon.
                if (etaitFocalise && !s.isFocused) etat.quitterChampTaxon(code)
                etaitFocalise = s.isFocused
            },
            decorationBox = { champ ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (valeur.text.isEmpty()) {
                            Text(etat.indicationTaxon(code), style = style.copy(color = couleurs.onBackground.copy(alpha = 0.6f)), maxLines = 1)
                        }
                        champ()
                    }
                    // indicator_input_error : rond rouge au point d'exclamation blanc.
                    if (etat.erreurTaxon(code) != null) {
                        Box(Modifier.padding(start = 4.dp).size(20.dp).background(couleurs.error, CircleShape), contentAlignment = Alignment.Center) {
                            Text("!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = couleurs.onError)
                        }
                    }
                }
            },
        )
    }
    val zone = ancre
    val propositions = if (focalise && !fermee && texte.length >= CARACTERES_TAXON && zone != null) etat.propositionsTaxon(code, texte) else emptyList()
    if (propositions.isNotEmpty() && zone != null) {
        PopupListeAndroid(zone, propositions, onFermer = { fermee = true }) { p ->
            LignePropositionAndroid(p) {
                etat.choisirPropositionTaxon(code, p)
                fermee = true
                // L'espèce choisie, le clavier se ferme (demande du 22 octobre).
                clavier?.hide()
                focus.clearFocus()
            }
        }
    }
}

/** item_suggestion_taxon : le nom (16 sp), et dessous en italique le nom secondaire (13 sp). */
@Composable
private fun LignePropositionAndroid(p: PropositionTaxon, onChoisir: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onChoisir).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(p.nom, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        p.secondaireAffiche?.let {
            Text(it, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─── iOS : FormulaireRendererView ───

/** Une ligne du `Form` : marges de 16 × 11 pt ; la barre rouge (rougeFonce, 3 pt aux coins de
 *  1,5 pt) borde tout le champ, libellé compris, à 8 pt de lui. */
@NonSkippableComposable
@Composable
private fun LigneChampIos(
    f: EditableField,
    etat: FormulaireEtat,
    barre: Boolean,
    onAjouterPhotos: (String) -> Unit,
    onChoixMultiple: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .barreChampObligatoire(barreDeLigne(f, etat, barre)),
    ) {
        // Le texte du protocole, sans libellé, comme le web.
        if (f.viewType == ViewType.HTML) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TexteHtmlRiche(f.html.orEmpty(), 15.sp, Color.White, CouleursGeoMys.jauneClair)
                f.aide?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = SECONDAIRE_IOS) }
            }
            return@Box
        }
        // Le libellé au-dessus du champ, comme sous Android, cases à cocher comprises (GeoMys-IOS en
        // faisait le libellé de l'interrupteur, à côté de lui).
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(libelle(f, etat), fontSize = 12.sp, color = SECONDAIRE_IOS)
                // L'info-bulle `definition` du web : touchée, elle s'affiche en message.
                f.definition?.takeIf { it.isNotEmpty() }?.let { definition ->
                    val toasts = LocalToasts.current
                    BoutonIos({ toasts.afficher(definition, longue = true) }, opaciteAppui = 0.6f) {
                        SymboleSf("info.circle", 12f, listOf(SECONDAIRE_IOS), description = "Définition")
                    }
                }
            }
            ChampIos(f, etat, onAjouterPhotos, onChoixMultiple)
            if (f.viewType == ViewType.TEXT) etat.erreurValidation(f.code)?.let { Text(it, fontSize = 11.sp, color = CouleursGeoMys.rougePastille) }
            // L'aide (`help` du schéma), sous le champ, comme le web.
            f.aide?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = SECONDAIRE_IOS) }
        }
    }
}

@NonSkippableComposable
@Composable
private fun ChampIos(f: EditableField, etat: FormulaireEtat, onAjouterPhotos: (String) -> Unit, onChoixMultiple: (String) -> Unit) {
    val code = f.code
    when (f.viewType) {
        ViewType.TEXT -> BasicTextField(
            value = etat.texte(code),
            onValueChange = { etat.saisirTexte(code, it) },
            singleLine = true,
            textStyle = styleSaisieIos,
            cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        // TextEditor de 60 à 120 pt de haut ; `rows` lignes au moins s'il est donné.
        ViewType.TEXTAREA -> BasicTextField(
            value = etat.texte(code),
            onValueChange = { etat.saisirTexte(code, it) },
            textStyle = styleSaisieIos,
            cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = f.lignes ?: 1,
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 120.dp),
        )
        ViewType.NUMBER -> {
            BasicTextField(
                value = etat.texte(code),
                onValueChange = { etat.saisirTexte(code, filtrerNombre(it, f.decimal)) },
                singleLine = true,
                textStyle = styleSaisieIos,
                cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                keyboardOptions = ClavierNombresEtPonctuation,
                modifier = Modifier.fillMaxWidth(),
            )
            etat.erreurValidation(code)?.let { Text(it, fontSize = 11.sp, color = CouleursGeoMys.rougePastille) }
        }
        ViewType.DATE, ViewType.TIME, ViewType.DATETIME -> {
            ChampDateMonitoring(
                modeDateHeure(f.viewType),
                etat.affichageDateHeure(code),
                etat.selectionDateHeure(code),
                { choisirDateHeure(etat, f, it) },
                onEffacer = { etat.effacerDateHeure(code) },
            )
            etat.erreurValidation(code)?.let { Text(it, fontSize = 11.sp, color = CouleursGeoMys.rougePastille) }
        }
        ViewType.TAXON -> ChampTaxonIos(f, etat)
        // Picker en menu d'une ligne de Form.
        ViewType.SELECT -> etat.valeurHorsListe(code)?.let { horsListe ->
            // Une valeur hors des options d'un `select` : grisée, comme sur le web.
            Text(horsListe, fontSize = 17.sp, color = SECONDAIRE_IOS.copy(alpha = 0.4f))
        } ?: run {
            val (libelles, position) = entreesListe(f, etat)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ValeurListeIos(libelles.getOrNull(position).orEmpty())
                    MenuSysteme(libelles, position, { etat.choisirOption(code, optionDe(f, it)) }, Modifier.matchParentSize())
                }
                // La croix d'une nomenclature, des observateurs, d'un jeu de données.
                if (f.effacable && etat.indexChoisi(code) >= 0) {
                    Spacer(Modifier.width(8.dp))
                    BoutonIos({ etat.choisirOption(code, -1) }, opaciteAppui = 0.6f) {
                        SymboleSf("xmark", 13f, listOf(CouleursGeoMys.rougePastille), description = "Effacer")
                    }
                }
            }
        }
        ViewType.RADIO -> {
            val choisi = etat.indexChoisi(code)
            val pastille: @Composable (Int) -> Unit = { i ->
                val selectionne = choisi == i
                BoutonIos({ etat.choisirRadio(code, i) }, opaciteAppui = 0.6f) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SymboleSf(if (selectionne) "largecircle.fill.circle" else "circle", 18f, listOf(if (selectionne) CouleursGeoMys.vert else SECONDAIRE_IOS))
                        Text(f.values[i].label, fontSize = 17.sp, color = Color.White)
                    }
                }
            }
            // La règle d'Android (demande du 21 octobre).
            if (radioEnLigne(f.values.map { it.label })) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { f.values.indices.forEach { pastille(it) } }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { f.values.indices.forEach { pastille(it) } }
            }
        }
        ViewType.SELECT_MULTIPLE -> {
            val libelles = libellesChoisis(f, etat)
            val vide = f.values.isEmpty()
            BoutonIos({ onChoixMultiple(code) }, Modifier.fillMaxWidth(), actif = !vide, opaciteAppui = 0.6f) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            vide -> "(aucune option disponible)"
                            libelles.isEmpty() -> "Choisir…"
                            else -> libelles.joinToString(", ")
                        },
                        fontSize = 17.sp,
                        color = if (libelles.isEmpty()) SECONDAIRE_IOS else Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    SymboleSf("chevron.down", 12f, listOf(SECONDAIRE_IOS))
                }
            }
        }
        ViewType.CHECKBOX_MULTIPLE -> {
            if (f.values.isEmpty()) {
                Text("(aucune option disponible)", fontSize = 15.sp, color = SECONDAIRE_IOS)
            } else {
                val cases = etat.cases(code)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    f.values.forEachIndexed { i, option ->
                        val coche = cases.getOrElse(i) { false }
                        BoutonIos({ etat.cocherCase(code, i, !coche) }, opaciteAppui = 0.6f) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                SymboleSf(if (coche) "checkmark.square.fill" else "square", 17f, listOf(if (coche) CouleursGeoMys.vert else SECONDAIRE_IOS))
                                Text(option.label, fontSize = 17.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
        ViewType.CHECKBOX -> Interrupteur(
            etat.cases(code).firstOrNull() == true,
            { etat.cocher(code, it) },
            teinteIos = CouleursGeoMys.jauneClair,
            fondIos = FOND_LIGNE_FORMULAIRE_IOS,
        )
        // MediaPickerView : nom du fichier en chasse fixe coupé au milieu, corbeille rouge ; dessous, les
        // champs du formulaire média du web.
        ViewType.MEDIA -> {
            val medias = etat.medias(code)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (medias.isEmpty()) Text("Aucune photo", fontSize = 15.sp, color = SECONDAIRE_IOS)
                medias.forEach { m ->
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { SymboleSf("photo", 17f, listOf(SECONDAIRE_IOS)) }
                        Text(
                            nomMedia(m.chemin),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                            modifier = Modifier.weight(1f),
                        )
                        BoutonIos({ etat.retirerMedia(code, m.chemin) }, opaciteAppui = 0.6f) {
                            SymboleSf("trash", 17f, listOf(CouleursGeoMys.rougePastille), description = "Supprimer le média")
                        }
                    }
                    ChampMediaIos(LIBELLE_TITRE_MEDIA, m.titre, obligatoire = true) { etat.modifierMedia(code, m.chemin, titre = it) }
                    ChampMediaIos(LIBELLE_DESCRIPTION_MEDIA, m.description) { etat.modifierMedia(code, m.chemin, description = it) }
                    ChampMediaIos(LIBELLE_AUTEUR_MEDIA, m.auteur) { etat.modifierMedia(code, m.chemin, auteur = it) }
                }
                // BoutonTexteJaune : 15 pt medium, pleine largeur.
                BoutonIos({ onAjouterPhotos(code) }, Modifier.fillMaxWidth(), opaciteAppui = 0.6f) {
                    Text("Ajouter des photos", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = CouleursGeoMys.jauneClair, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
        // Affiché par la ligne, sans libellé.
        ViewType.HTML -> {}
    }
}

/** Ce que montre une liste sous iOS, sous son menu : la valeur et ses chevrons en secondaire, calés
 *  à gauche comme tous les champs (demande du 22 octobre ; ils étaient à droite, comme le Picker de
 *  GeoMys-IOS). Une valeur trop longue finit en « … », les chevrons restent visibles. */
@Composable
internal fun ValeurListeIos(valeur: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            valeur,
            fontSize = 17.sp,
            color = SECONDAIRE_IOS,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(4.dp))
        SymboleSf("chevron.up.chevron.down", 13f, listOf(SECONDAIRE_IOS))
    }
}

/** Champ TAXON : `AutocompleteField` de GeoMys-IOS (fond blanc 10 % aux coins de 6 pt, propositions
 *  en liste sous le champ), puis la ligne du taxon retenu, son symbole en tête. */
@NonSkippableComposable
@Composable
private fun ChampTaxonIos(f: EditableField, etat: FormulaireEtat) {
    val code = f.code
    val focus = LocalFocusManager.current
    val clavier = LocalSoftwareKeyboardController.current
    var focalise by remember { mutableStateOf(false) }
    var fermee by remember { mutableStateOf(false) }
    val texte = etat.texte(code)
    val (valeur, onValeur) = rememberValeurChamp(texte) {
        fermee = false
        etat.saisirTexteTaxon(code, it)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp)).padding(10.dp)) {
            if (texte.isEmpty()) Text(etat.indicationTaxon(code), style = styleSaisieIos.copy(color = Color(0x4DEBEBF5)), maxLines = 1)
            BasicTextField(
                value = valeur,
                onValueChange = onValeur,
                singleLine = true,
                textStyle = styleSaisieIos,
                cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth().onFocusChanged { s ->
                    if (focalise && !s.isFocused) etat.quitterChampTaxon(code)
                    focalise = s.isFocused
                },
            )
        }
        val propositions = if (focalise && !fermee && texte.length >= CARACTERES_TAXON) etat.propositionsTaxon(code, texte) else emptyList()
        if (propositions.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 200.dp).background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp))) {
                itemsIndexed(propositions) { i, p ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                etat.choisirPropositionTaxon(code, p)
                                fermee = true
                                // L'espèce choisie, le clavier se ferme (demande du 22 octobre).
                                clavier?.hide()
                                focus.clearFocus()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(p.nom, fontSize = 16.sp, color = Color.White)
                        p.secondaireAffiche?.let { Text(it, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = CouleursGeoMys.blanc70) }
                    }
                    if (i < propositions.lastIndex) SeparateurIos()
                }
            }
        }
        etat.statutTaxon(code)?.let { statut ->
            val (symbole, couleur) = when (statut) {
                is StatutTaxon.Retenu -> "checkmark.circle.fill" to CouleursGeoMys.vert
                is StatutTaxon.Refus -> "xmark.circle.fill" to CouleursGeoMys.rougePastille
                is StatutTaxon.PasDeDonnees -> "exclamationmark.triangle.fill" to CouleursGeoMys.orange
            }
            Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SymboleSf(symbole, 12f, listOf(couleur))
                // Sous iOS, le symbole tient lieu de « ✓ » et de « ✗ ».
                Text(statut.texte.removePrefix("✓ ").removePrefix("✗ "), fontSize = 12.sp, color = couleur)
            }
        }
    }
}

/** Les libellés du formulaire média du web (media-form-definition.ts). */
private const val LIBELLE_TITRE_MEDIA = "Titre"
private const val LIBELLE_DESCRIPTION_MEDIA = "Description"
private const val LIBELLE_AUTEUR_MEDIA = "Auteur"

/** Un champ médias sous Android : chaque fichier (son nom, sa croix) et les champs du formulaire
 *  média du web, titre obligatoire (barre rouge tant qu'il est vide), description et auteur ; puis
 *  « Ajouter des photos ». */
@NonSkippableComposable
@Composable
private fun ChampMediasAndroid(code: String, etat: FormulaireEtat, onAjouterPhotos: (String) -> Unit) {
    val couleurs = MaterialTheme.colorScheme
    val medias = etat.medias(code)
    Column(Modifier.fillMaxWidth()) {
        if (medias.isEmpty()) {
            Text("Aucune photo", fontSize = 14.sp, color = couleurs.onSurfaceVariant)
        } else {
            medias.forEach { m ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(nomMedia(m.chemin), fontSize = 14.sp, color = couleurs.onSurface, modifier = Modifier.weight(1f))
                    // Bouton icône de Material 3 (materialIconButtonStyle) portant « ✕ ».
                    Box(
                        Modifier
                            .size(48.dp)
                            .clickable(remember { MutableInteractionSource() }, ripple(bounded = false, radius = 20.dp), role = Role.Button) {
                                etat.retirerMedia(code, m.chemin)
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", fontSize = 14.sp, color = couleurs.onSurfaceVariant) }
                }
                Column(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp)) {
                    Text(LIBELLE_TITRE_MEDIA, fontSize = 12.sp, color = couleurs.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().barreChampObligatoire(m.titre.isBlank())) {
                        ChampTexteFormulaire(m.titre, { etat.modifierMedia(code, m.chemin, titre = it) })
                    }
                    Text(LIBELLE_DESCRIPTION_MEDIA, fontSize = 12.sp, color = couleurs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    ChampTexteFormulaire(m.description, { etat.modifierMedia(code, m.chemin, description = it) })
                    Text(LIBELLE_AUTEUR_MEDIA, fontSize = 12.sp, color = couleurs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    ChampTexteFormulaire(m.auteur, { etat.modifierMedia(code, m.chemin, auteur = it) })
                }
            }
        }
        BoutonPlein("Ajouter des photos", { onAjouterPhotos(code) })
    }
}

/** Un champ du formulaire média sous iOS : son libellé, puis le texte sur blanc 8 % aux coins de
 *  8 pt ; [obligatoire] et vide, la barre rouge. */
@Composable
private fun ChampMediaIos(libelle: String, valeur: String, obligatoire: Boolean = false, onValeur: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 34.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(libelle, fontSize = 12.sp, color = SECONDAIRE_IOS)
        BasicTextField(
            value = valeur,
            onValueChange = onValeur,
            singleLine = true,
            textStyle = styleSaisieIos,
            cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .barreChampObligatoire(obligatoire && valeur.isBlank())
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** Les options d'un champ radio sur une seule ligne : peu d'options, et courtes (Oui/Non), comme
 *  gn_mobile_monitoring ; sinon une par ligne. Sous iOS aussi depuis le 21 octobre : les libellés
 *  longs (la fiabilité d'une visite STERF-eBMS) s'y écrasaient sur une ligne. */
internal fun radioEnLigne(libelles: List<String>): Boolean =
    libelles.size <= 4 && libelles.all { it.length <= 15 } && libelles.sumOf { it.length } <= 40
