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

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.mdc_effacer
import fr.ariegenature.geomys.ui.ressources.mdc_fleche_bas
import fr.ariegenature.geomys.ui.ressources.mdc_fleche_haut
import fr.ariegenature.geomys.ui.ressources.mdc_oeil
import fr.ariegenature.geomys.ui.ressources.mdc_oeil_barre
import fr.ariegenature.geomys.ui.theme.Apparence
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.LocalApparence
import kotlin.math.max
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

/** Nature d'un champ : clavier, majuscules et correction automatiques. URL, identifiant et mot de
 *  passe n'ont ni majuscule ni correction automatique (le clavier iOS changeait « cgross » en
 *  « Cgross »). */
enum class TypeChamp { TEXTE, URL, IDENTIFIANT, MOT_DE_PASSE, NOMBRE }

private fun TypeChamp.clavier() = when (this) {
    TypeChamp.TEXTE -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    TypeChamp.URL -> KeyboardOptions(KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri)
    TypeChamp.IDENTIFIANT -> KeyboardOptions(KeyboardCapitalization.None, autoCorrectEnabled = false)
    TypeChamp.MOT_DE_PASSE -> KeyboardOptions(KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password)
    TypeChamp.NOMBRE -> KeyboardOptions(keyboardType = KeyboardType.Number)
}

/** Texte saisi d'un champ Material 3 : TextAppearance.Material3.BodyLarge (16 sp). */
@Composable
internal fun styleSaisieMaterial() = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)

/** Texte saisi d'un `TextField` SwiftUI sur le dégradé : corps 17 pt, blanc. */
internal val styleSaisieIos = TextStyle(color = Color.White, fontSize = 17.sp)

/** Champ de saisie d'une ligne.
 *  - Android : TextInputLayout OutlinedBox de Material Components (étiquette flottante [libelle],
 *    texte d'aide [aide] sous le cadre, croix d'effacement si [effacable]).
 *  - iOS : `ChampTexteSaisie` de GeoMys-IOS (libellé 13 pt blanc 70 % au-dessus, champ sur fond
 *    blanc 8 % aux coins de 8 pt) ; le texte d'aide est affiché par l'écran, à sa façon. */
@Composable
fun ChampTexte(
    valeur: String,
    onChange: (String) -> Unit,
    libelle: String,
    modifier: Modifier = Modifier,
    type: TypeChamp = TypeChamp.TEXTE,
    aide: String? = null,
    couleurAide: Color = Color.Unspecified,
    effacable: Boolean = false,
) {
    if (LocalApparence.current == Apparence.IOS) {
        ChampAvecLibelleIos(libelle, modifier) {
            BasicTextField(
                value = valeur,
                onValueChange = onChange,
                singleLine = true,
                textStyle = styleSaisieIos,
                cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                keyboardOptions = type.clavier(),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    } else {
        val interactions = remember { MutableInteractionSource() }
        val focalise by interactions.collectIsFocusedAsState()
        OutlinedTextField(
            value = valeur,
            onValueChange = onChange,
            modifier = modifier.fillMaxWidth(),
            label = { Text(libelle) },
            textStyle = styleSaisieMaterial(),
            singleLine = true,
            keyboardOptions = type.clavier(),
            interactionSource = interactions,
            supportingText = aide?.let { { Text(it, color = couleurAide) } },
            trailingIcon = if (effacable && focalise && valeur.isNotEmpty()) {
                {
                    IconButton(onClick = { onChange("") }) {
                        Icon(painterResource(Res.drawable.mdc_effacer), contentDescription = "Effacer le texte")
                    }
                }
            } else null,
        )
    }
}

/** Mot de passe masqué, avec l'œil qui l'affiche : `passwordToggleEnabled` de TextInputLayout sous
 *  Android, bouton œil (« eye » / « eye.slash ») de GeoMys-IOS. */
@Composable
fun ChampMotDePasse(valeur: String, onChange: (String) -> Unit, libelle: String, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    val description = if (visible) "Masquer le mot de passe" else "Afficher le mot de passe"
    if (LocalApparence.current == Apparence.IOS) {
        ChampAvecLibelleIos(libelle, modifier) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = valeur,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = styleSaisieIos,
                    cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                    keyboardOptions = TypeChamp.MOT_DE_PASSE.clavier(),
                    visualTransformation = transformation,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.clickable(onClickLabel = description) { visible = !visible }) {
                    SymboleSf(if (visible) "eye.slash" else "eye", 17f, listOf(CouleursGeoMys.blanc70), description = description)
                }
            }
        }
    } else {
        OutlinedTextField(
            value = valeur,
            onValueChange = onChange,
            modifier = modifier.fillMaxWidth(),
            label = { Text(libelle) },
            textStyle = styleSaisieMaterial(),
            singleLine = true,
            keyboardOptions = TypeChamp.MOT_DE_PASSE.clavier(),
            visualTransformation = transformation,
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        painterResource(if (visible) Res.drawable.mdc_oeil_barre else Res.drawable.mdc_oeil),
                        contentDescription = description,
                    )
                }
            },
        )
    }
}

/** `ChampAvecLibelle` de GeoMys-IOS : libellé 13 pt blanc 70 % au-dessus du champ, à 4 pt. */
@Composable
internal fun ChampAvecLibelleIos(libelle: String, modifier: Modifier = Modifier, champ: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(libelle, fontSize = 13.sp, color = CouleursGeoMys.blanc70)
        champ()
    }
}

/** Filtre de l'ArrayAdapter d'Android : un élément est proposé si son libellé, ou l'un de ses
 *  mots, commence par le texte saisi (casse ignorée). */
internal fun <T> filtreArrayAdapter(elements: List<T>, libelle: (T) -> String, saisie: String): List<T> {
    val prefixe = saisie.lowercase()
    if (prefixe.isEmpty()) return elements
    return elements.filter { e ->
        val texte = libelle(e).lowercase()
        texte.startsWith(prefixe) || texte.split(" ").any { it.startsWith(prefixe) }
    }
}

/** Filtre d'`AutocompleteFilter` de GeoMys-IOS : libellés qui contiennent la saisie (casse
 *  ignorée), dans l'ordre, 20 au plus ; rien tant que la saisie est vide. */
internal fun <T> filtreAutocompleteIos(elements: List<T>, libelle: (T) -> String, saisie: String): List<T> {
    val q = saisie.trim()
    if (q.isEmpty()) return emptyList()
    return elements.filter { libelle(it).contains(q, ignoreCase = true) }.take(20)
}

/** Choix d'un élément parmi [elements] (jeu de données, liste, observateur), avec son titre de
 *  section [titre].
 *  - Android : titre 14 sp gras puis TextInputLayout OutlinedBox.ExposedDropdownMenu : la saisie
 *    filtre la liste (ArrayAdapter, dès un caractère), toucher le champ ouvre la liste entière —
 *    en vidant d'abord le texte si [viderAuToucher] (jeux de données et observateurs). Désactivé
 *    si [actif] est faux (liste imposée par le jeu de données). La liste est celle d'un
 *    AutoCompleteTextView (ListeDeroulanteAndroid).
 *  - iOS : `AutocompleteRow` de GeoMys-IOS : la sélection en pastille avec sa croix, sinon un champ
 *    de recherche aux suggestions déroulées en dessous. */
@Composable
fun <T> ChampSelection(
    titre: String,
    indication: String,
    elements: List<T>,
    libelle: (T) -> String,
    selection: T?,
    onSelection: (T?) -> Unit,
    modifier: Modifier = Modifier,
    margeHautAndroid: Dp = 0.dp,
    actif: Boolean = true,
    viderAuToucher: Boolean = false,
) {
    if (LocalApparence.current == Apparence.IOS) {
        SelectionIos(titre, indication, elements, libelle, selection, onSelection, modifier)
        return
    }
    val focus = LocalFocusManager.current
    var texte by remember(selection) { mutableStateOf(selection?.let(libelle) ?: "") }
    var saisie by remember(selection) { mutableStateOf("") }
    var ouvert by remember { mutableStateOf(false) }
    var ancre by remember { mutableStateOf<Rect?>(null) }
    val propositions = remember(elements, saisie) { filtreArrayAdapter(elements, libelle, saisie) }
    val interactions = remember { MutableInteractionSource() }
    // Toucher le champ ouvre la liste entière (`showDropDown`), après avoir vidé le texte pour les
    // jeux de données et les observateurs (`setText("", false)` de GeoMys-Android).
    LaunchedEffect(interactions, actif, viderAuToucher) {
        interactions.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release && actif) {
                if (viderAuToucher) texte = ""
                saisie = ""
                ouvert = true
            }
        }
    }
    Column(modifier) {
        Text(
            titre,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = margeHautAndroid, bottom = 4.dp),
        )
        Box(Modifier.padding(top = 4.dp)) {
            OutlinedTextField(
                value = texte,
                onValueChange = {
                    texte = it
                    saisie = it
                    // Seuil de l'adaptateur : la liste filtrée s'ouvre dès le premier caractère.
                    ouvert = it.isNotEmpty()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { ancre = Rect(it.positionInWindow(), it.size.toSize()) }
                    .onFocusChanged { if (!it.isFocused) ouvert = false }
                    // Retour ferme d'abord la liste, avant le clavier (`onKeyPreIme` d'AutoCompleteTextView).
                    .onInterceptKeyBeforeSoftKeyboard { touche ->
                        if (touche.key != Key.Back || !ouvert) return@onInterceptKeyBeforeSoftKeyboard false
                        if (touche.type == KeyEventType.KeyUp) ouvert = false
                        true
                    },
                enabled = actif,
                label = { Text(indication) },
                textStyle = styleSaisieMaterial(),
                singleLine = true,
                interactionSource = interactions,
                trailingIcon = {
                    Icon(
                        painterResource(if (ouvert) Res.drawable.mdc_fleche_haut else Res.drawable.mdc_fleche_bas),
                        contentDescription = null,
                    )
                },
            )
            val zone = ancre
            if (ouvert && propositions.isNotEmpty() && zone != null) {
                ListeDeroulanteAndroid(
                    zone, propositions, libelle,
                    onChoix = { element ->
                        texte = libelle(element)
                        saisie = ""
                        ouvert = false
                        focus.clearFocus()
                        onSelection(element)
                    },
                    onFermer = { ouvert = false },
                )
            }
        }
    }
}

/** `dropDownVerticalOffset` du champ M3 OutlinedBox : la liste chevauche le bas du champ. */
private val DecalageListeDeroulante = (-8).dp

/** La liste d'un AutoCompleteTextView (ListPopupWindow) : de la largeur du champ, dessous, ou
 *  dessus s'il y a plus de place, et pas plus haute que l'espace visible de ce côté-là, clavier
 *  déduit (`getMaxAvailableHeight`). Fond colorSurfaceContainer aux coins de 4 dp, élévation 3 dp ;
 *  lignes `simple_dropdown_item_1line` de 48 dp, texte de 16 sp, marges de 8 dp.
 *
 *  Remplace l'ExposedDropdownMenu de Material 3, qui faisait planter l'appli : quand le clavier
 *  s'ouvre, sa position est recalculée avec l'ancienne hauteur de liste dans une fenêtre déjà
 *  réduite, et `coerceIn` reçoit un intervalle vide (IllegalArgumentException « Cannot coerce value
 *  to an empty range », relevée sur le Galaxy A12). */
@Composable
private fun <T> ListeDeroulanteAndroid(
    ancre: Rect,
    elements: List<T>,
    libelle: (T) -> String,
    onChoix: (T) -> Unit,
    onFermer: () -> Unit,
) {
    PopupListeAndroid(ancre, elements, onFermer) { element ->
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onChoix(element) }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                libelle(element),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Le cadre de la liste d'un AutoCompleteTextView (cf. [ListeDeroulanteAndroid]), chaque élément
 *  dessiné par [ligne]. */
@Composable
internal fun <T> PopupListeAndroid(
    ancre: Rect,
    elements: List<T>,
    onFermer: () -> Unit,
    ligne: @Composable (T) -> Unit,
) {
    val densite = LocalDensity.current
    val decalage = with(densite) { DecalageListeDeroulante.roundToPx() }
    val hautVisible = WindowInsets.statusBars.getTop(densite)
    val basVisible = LocalWindowInfo.current.containerSize.height -
        max(WindowInsets.ime.getBottom(densite), WindowInsets.navigationBars.getBottom(densite))
    val dessous = basVisible - ancre.bottom.roundToInt() - decalage
    val dessus = ancre.top.roundToInt() - hautVisible + decalage
    val hauteurMax = max(max(dessous, dessus), 0)
    Popup(
        popupPositionProvider = remember(decalage, hautVisible) { PositionListeDeroulante(decalage, hautVisible) },
        onDismissRequest = onFermer,
        // Pas de focus : le clavier reste au champ, qui continue de filtrer pendant la frappe.
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 3.dp,
            modifier = with(densite) { Modifier.width(ancre.width.toDp()).heightIn(max = hauteurMax.toDp()) },
        ) {
            LazyColumn {
                items(elements) { element -> ligne(element) }
            }
        }
    }
}

/** Position de la liste déroulante, règle de `PopupWindow.findDropDownPosition` : alignée sur le
 *  bord gauche du champ ; dessous si elle y tient (le décalage la fait chevaucher le bas du champ),
 *  sinon dessus si elle y tient (le décalage la laisse alors à 8 dp du haut du champ), sinon
 *  ramenée dans la fenêtre. Toujours un résultat, même pendant que le clavier anime la fenêtre. Sous
 *  Android, la hauteur de fenêtre transmise s'arrête à la barre d'état (b/256233441, contourné de
 *  même par Material 3) : on la rajoute. */
private class PositionListeDeroulante(private val decalage: Int, private val hautFenetre: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val bas = windowSize.height + hautFenetre
        val hauteur = popupContentSize.height
        val x = anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val dessous = anchorBounds.bottom + decalage
        val dessus = anchorBounds.top + decalage - hauteur
        val y = when {
            dessous + hauteur <= bas -> dessous
            dessus >= hautFenetre -> dessus
            else -> dessous.coerceAtMost(bas - hauteur).coerceAtLeast(hautFenetre)
        }
        return IntOffset(x, y)
    }
}

/** `AutocompleteRow` de GeoMys-IOS (ConfigGeoNatureView.swift). */
@Composable
private fun <T> SelectionIos(
    titre: String,
    indication: String,
    elements: List<T>,
    libelle: (T) -> String,
    selection: T?,
    onSelection: (T?) -> Unit,
    modifier: Modifier,
) {
    var requete by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Column(modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titre, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (selection != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    libelle(selection),
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.clickable(onClickLabel = "Effacer la sélection") {
                        onSelection(null)
                        requete = ""
                    }
                ) { SymboleSf("xmark.circle.fill", 17f, listOf(CouleursGeoMys.blanc70)) }
            }
        } else {
            val filtres = filtreAutocompleteIos(elements, libelle, requete)
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(10.dp),
                ) {
                    if (requete.isEmpty()) Text(indication, style = styleSaisieIos.copy(color = Color(0x4DEBEBF5)))
                    BasicTextField(
                        value = requete,
                        onValueChange = { requete = it },
                        singleLine = true,
                        textStyle = styleSaisieIos,
                        cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (filtres.isNotEmpty()) {
                    LazyColumn(
                        Modifier
                            .padding(top = 4.dp)
                            .heightIn(max = 200.dp)
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp)),
                    ) {
                        itemsIndexed(filtres) { i, element ->
                            Text(
                                libelle(element),
                                fontSize = 15.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        requete = ""
                                        focus.clearFocus()
                                        onSelection(element)
                                    }
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            if (i < filtres.lastIndex) SeparateurIos()
                        }
                    }
                }
            }
            if (requete.isNotBlank() && filtres.isEmpty()) {
                Text("Aucun résultat", fontSize = 13.sp, color = CouleursGeoMys.blanc70, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
