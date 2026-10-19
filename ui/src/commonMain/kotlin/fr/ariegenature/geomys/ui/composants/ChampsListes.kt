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

// Extrait du dialogue « Détails du relevé » du lot 6 (ui/occtax/DialogueDetailsReleve.kt), dont le dialogue
// des détails d'une station OccHab reprend les champs : le champ « menu déroulant » à recherche de
// GeoMys-Android (champ_dropdown_releve.xml) et la ligne de menu de DetailsReleveView de GeoMys-IOS.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.mdc_fleche_bas
import fr.ariegenature.geomys.ui.ressources.mdc_fleche_haut
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import org.jetbrains.compose.resources.painterResource

// ─── Android ───

/** Le champ « menu déroulant » (champ_dropdown_releve.xml : TextInputLayout OutlinedBox
 *  ExposedDropdownMenu, 4 dp sous son titre). Toucher vide le texte et déroule toute la liste ; la
 *  frappe la filtre (accents et casse ignorés) ; un choix ferme le clavier. Refermée sans choix, la
 *  liste rend au champ le libellé choisi si [restaurer]. */
@Composable
internal fun DeroulantAndroid(indication: String, libelles: List<String>, texteChoisi: String, restaurer: Boolean, onChoix: (Int) -> Unit) {
    val focus = LocalFocusManager.current
    val choisi by rememberUpdatedState(texteChoisi)
    var texte by remember(texteChoisi) { mutableStateOf(texteChoisi) }
    var ouvert by remember { mutableStateOf(false) }
    var ancre by remember { mutableStateOf<Rect?>(null) }
    val positions = remember(libelles, texte) { DetailsReleveFormulaire.filtrer(libelles, texte) }
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(interactions) {
        interactions.interactions.collect {
            if (it is PressInteraction.Release) {
                texte = ""
                ouvert = true
            }
        }
    }
    fun fermer() {
        ouvert = false
        if (restaurer) texte = choisi
    }
    Box(Modifier.padding(top = 4.dp)) {
        OutlinedTextField(
            value = texte,
            onValueChange = {
                texte = it
                // Seuil de 1 : la liste filtrée se déroule dès le premier caractère.
                ouvert = it.isNotEmpty()
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { ancre = Rect(it.positionInWindow(), it.size.toSize()) }
                .onFocusChanged { if (!it.isFocused && ouvert) fermer() }
                .onInterceptKeyBeforeSoftKeyboard { touche ->
                    if (touche.key != Key.Back || !ouvert) return@onInterceptKeyBeforeSoftKeyboard false
                    if (touche.type == KeyEventType.KeyUp) fermer()
                    true
                },
            label = { Text(indication) },
            textStyle = styleSaisieMaterial(),
            singleLine = true,
            interactionSource = interactions,
            trailingIcon = {
                Icon(painterResource(if (ouvert) Res.drawable.mdc_fleche_haut else Res.drawable.mdc_fleche_bas), contentDescription = null)
            },
        )
        val zone = ancre
        if (ouvert && positions.isNotEmpty() && zone != null) {
            PopupListeAndroid(zone, positions, onFermer = ::fermer) { i ->
                LigneListeAndroid(libelles[i]) {
                    ouvert = false
                    texte = if (restaurer) libelles[i] else ""
                    focus.clearFocus()
                    onChoix(i)
                }
            }
        }
    }
}

/** Ligne de la liste d'un AutoCompleteTextView (`simple_dropdown_item_1line`). */
@Composable
internal fun LigneListeAndroid(texte: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(texte, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Le sélecteur multiple d'observateurs (`construireSelecteurMultiObservateurs`), partagé par les
 *  détails d'un relevé OccTax et ceux d'une station OccHab : un champ de recherche qui AJOUTE à la
 *  liste affichée dessous, chaque entrée retirable par « ✕ ». Sans [proposes] (cache non chargé),
 *  les observateurs [choisis] restent affichés, retirables, sans champ d'ajout — « — » si rien. */
@Composable
internal fun ObservateursAndroid(
    proposes: List<Pair<Int, String>>,
    choisis: List<Pair<Int, String>>,
    onAjouter: (Int) -> Unit,
    onRetirer: (Int) -> Unit,
) {
    if (proposes.isEmpty()) {
        if (choisis.isEmpty()) Text("—", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        DeroulantAndroid("Ajouter un observateur", proposes.map { it.second }, "", restaurer = false, onChoix = onAjouter)
    }
    val erreur = MaterialTheme.colorScheme.error
    choisis.forEach { (id, nom) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nom, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(
                "✕",
                fontSize = 16.sp,
                color = erreur,
                modifier = Modifier
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onRetirer(id) }
                    .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            )
        }
    }
}

// ─── iOS ───

/** `ligneDeroulante` de DetailsReleveView : libellé (blanc 70 % pour une invite), chevron, fond
 *  blanc 8 % aux coins de 8 pt ; le toucher ouvre le menu du système. */
@Composable
internal fun LigneMenuIos(texte: String, invite: Boolean, options: List<String>, selection: Int, onChoix: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                texte,
                fontSize = 17.sp,
                color = if (invite) CouleursGeoMys.blanc70 else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            SymboleSf("chevron.down", 12f, listOf(CouleursGeoMys.blanc70), graisse = GraisseSymbole.DEMI_GRASSE)
        }
        MenuSysteme(options, selection, onChoix, Modifier.matchParentSize())
    }
}

/** Bouton « ＋ » / « ✕ » de la date de fin : symbole de 14 pt gras dans un cadre de 40 × 36 pt
 *  au filet de sa couleur à 60 %, coins de 8 pt. */
@Composable
internal fun BoutonCadreIos(symbole: String, couleur: Color, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp, 36.dp)
            .border(1.dp, couleur.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SymboleSf(symbole, 14f, listOf(couleur), graisse = GraisseSymbole.GRASSE, description = description)
    }
}
