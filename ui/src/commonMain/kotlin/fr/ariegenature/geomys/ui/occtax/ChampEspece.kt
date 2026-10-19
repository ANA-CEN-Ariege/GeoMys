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

// Le champ espèce des saisies OccTax : til_espece, et_espece, taxref_progress et tv_taxref_statut de
// fragment_saisie_observation.xml (liste item_suggestion_taxon.xml) sous Android, EspeceAutocompleteField
// et AutocompleteField de GeoMys-IOS sous iOS.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.saisie.LigneStatutTaxRef
import fr.ariegenature.geomys.store.SuggestionTaxon
import fr.ariegenature.geomys.ui.composants.Dictee
import fr.ariegenature.geomys.ui.composants.IndicateurActivite
import fr.ariegenature.geomys.ui.composants.PopupListeAndroid
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.rememberValeurChamp
import fr.ariegenature.geomys.ui.composants.styleSaisieIos
import fr.ariegenature.geomys.ui.composants.styleSaisieMaterial
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_mic
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import org.jetbrains.compose.resources.painterResource

/** android.R.color.holo_green_dark : taxon reconnu. */
private val VERT_TROUVE = Color(0xFF669900)

/** android.R.color.holo_orange_dark : nom non reconnu. */
private val ORANGE_NON_TROUVE = Color(0xFFFF8800)

/** Le champ espèce : [libelle] rappelle le groupe, les [suggestions] se déroulent dès un caractère,
 *  le micro lance la [dictee] ([onMicro]) ; la [ligne] de statut dit si le nom désigne un taxon. */
@Composable
fun ChampEspece(
    texte: String,
    onTexte: (String) -> Unit,
    libelle: String,
    suggestions: List<SuggestionTaxon>,
    onChoisir: (SuggestionTaxon) -> Unit,
    ligne: LigneStatutTaxRef,
    synchroEnCours: Boolean,
    dictee: Dictee,
    onMicro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (estIos) {
        ChampEspeceIos(texte, onTexte, libelle, suggestions, onChoisir, ligne, synchroEnCours, dictee, onMicro, modifier)
    } else {
        ChampEspeceAndroid(texte, onTexte, libelle, suggestions, onChoisir, ligne, synchroEnCours, onMicro, modifier)
    }
}

/** Texte de la ligne de statut (TaxRefLookupController de GeoMys-Android). */
private fun texteStatut(statut: TaxRefStatut, synchroEnCours: Boolean, ios: Boolean): String = when (statut) {
    // Sous iOS, la coche est l'icône de la ligne.
    is TaxRefStatut.Trouve -> (if (ios) "" else "✓ ") + "${statut.nomScientifique}  •  cd_nom ${statut.cdNom}"
    TaxRefStatut.NonTrouve -> "Nom invalide — choisissez parmi les propositions"
    // Une synchro réécrit le cache pendant une quinzaine de secondes : dire « rechargez les
    // données » à quelqu'un qui recharge n'aide pas.
    TaxRefStatut.PasDeDonnees -> if (synchroEnCours) "Chargement des données en cours…" else "Pas de données — rechargez les données"
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChampEspeceAndroid(
    texte: String,
    onTexte: (String) -> Unit,
    libelle: String,
    suggestions: List<SuggestionTaxon>,
    onChoisir: (SuggestionTaxon) -> Unit,
    ligne: LigneStatutTaxRef,
    synchroEnCours: Boolean,
    onMicro: () -> Unit,
    modifier: Modifier,
) {
    var focalise by remember { mutableStateOf(false) }
    var ancre by remember { mutableStateOf<Rect?>(null) }
    // Liste fermée (toucher dehors, retour) jusqu'à la frappe suivante.
    var fermee by remember { mutableStateOf(false) }
    val (valeur, onValeur) = rememberValeurChamp(texte) {
        fermee = false
        onTexte(it)
    }
    Column(modifier) {
        OutlinedTextField(
            value = valeur,
            onValueChange = onValeur,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { ancre = Rect(it.positionInWindow(), it.size.toSize()) }
                .onFocusChanged { focalise = it.isFocused }
                // Retour ferme d'abord la liste, avant le clavier.
                .onInterceptKeyBeforeSoftKeyboard { touche ->
                    if (touche.key != Key.Back || fermee || suggestions.isEmpty()) return@onInterceptKeyBeforeSoftKeyboard false
                    if (touche.type == KeyEventType.KeyUp) fermee = true
                    true
                },
            label = { Text(libelle) },
            textStyle = styleSaisieMaterial(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            trailingIcon = {
                IconButton(onClick = onMicro) { Icon(painterResource(Res.drawable.ic_mic), contentDescription = "Dictée vocale") }
            },
        )
        val zone = ancre
        if (focalise && !fermee && texte.isNotEmpty() && suggestions.isNotEmpty() && zone != null) {
            PopupListeAndroid(zone, suggestions, onFermer = { fermee = true }) { suggestion ->
                // item_suggestion_taxon : le nom, et dessous en italique le nom scientifique (le
                // nom français en mode « noms scientifiques »).
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChoisir(suggestion) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(suggestion.nom, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val secondaire = suggestion.secondaire?.takeIf { it.isNotEmpty() && it != suggestion.toString() }
                    if (secondaire != null) {
                        Text(
                            secondaire,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (ligne.roue) IndicateurActivite(CouleursGeoMys.orange, Modifier.padding(top = 4.dp), tailleAndroid = 24f)
        val statut = ligne.statut
        if (ligne.texteVisible && statut != null) {
            Text(
                texteStatut(statut, synchroEnCours, ios = false),
                fontSize = 13.sp,
                color = when (statut) {
                    is TaxRefStatut.Trouve -> VERT_TROUVE
                    TaxRefStatut.NonTrouve -> ORANGE_NON_TROUVE
                    TaxRefStatut.PasDeDonnees -> CouleursGeoMys.avertissement
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ChampEspeceIos(
    texte: String,
    onTexte: (String) -> Unit,
    libelle: String,
    suggestions: List<SuggestionTaxon>,
    onChoisir: (SuggestionTaxon) -> Unit,
    ligne: LigneStatutTaxRef,
    synchroEnCours: Boolean,
    dictee: Dictee,
    onMicro: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(10.dp),
                ) {
                    if (texte.isEmpty()) Text(libelle, style = styleSaisieIos.copy(color = Color(0x4DEBEBF5)), maxLines = 1)
                    val (valeur, onValeur) = rememberValeurChamp(texte, onTexte)
                    BasicTextField(
                        value = valeur,
                        onValueChange = onValeur,
                        singleLine = true,
                        textStyle = styleSaisieIos,
                        cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Micro : vert pendant l'écoute.
                Box(Modifier.clickable(onClickLabel = "Dictée vocale", onClick = onMicro).padding(10.dp)) {
                    SymboleSf(
                        if (dictee.enCours) "mic.fill" else "mic",
                        17f,
                        listOf(if (dictee.enCours) CouleursGeoMys.vert else CouleursGeoMys.blanc70),
                        description = "Dictée vocale",
                    )
                }
            }
            if (suggestions.isNotEmpty()) {
                LazyColumn(
                    Modifier
                        .padding(top = 4.dp)
                        .heightIn(max = 200.dp)
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp)),
                ) {
                    itemsIndexed(suggestions) { i, suggestion ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onChoisir(suggestion) }
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(suggestion.nom, fontSize = 16.sp, color = Color.White)
                            val secondaire = suggestion.secondaire
                            if (!secondaire.isNullOrEmpty()) {
                                Text(secondaire, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = CouleursGeoMys.blanc70)
                            }
                        }
                        if (i < suggestions.lastIndex) SeparateurIos()
                    }
                }
            }
        }
        if (ligne.roue) IndicateurActivite(CouleursGeoMys.blanc70, petitIos = true)
        val statut = ligne.statut
        if (ligne.texteVisible && statut != null) {
            val (icone, couleur) = when (statut) {
                is TaxRefStatut.Trouve -> "checkmark.circle.fill" to CouleursGeoMys.vert
                TaxRefStatut.NonTrouve -> "xmark.circle.fill" to CouleursGeoMys.rougePastille
                TaxRefStatut.PasDeDonnees -> "exclamationmark.triangle.fill" to CouleursGeoMys.orange
            }
            Row(
                Modifier.padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SymboleSf(icone, 12f, listOf(couleur))
                Text(texteStatut(statut, synchroEnCours, ios = true), fontSize = 12.sp, color = couleur)
            }
        }
    }
}
