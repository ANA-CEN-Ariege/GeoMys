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

// La liste déroulante des formulaires de saisie : le Spinner que GeoMys-Android crée dans ses écrans
// (OcctaxFieldsRenderer, AdditionalFieldsRenderer), le `Menu` + `Picker` de GeoMys-IOS
// (NomenclaturePicker, CaracterisationView.swift).

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** Liste déroulante : les [libelles] (le premier : l'entrée vide, sauf avec [invite]) et la
 *  position choisie [selection]. Les listes du web n'ont pas d'entrée vide : hors des [libelles]
 *  (-1), [invite] s'affiche en gris, et [onEffacer] met au bout d'un choix la croix qui le vide.
 *  - Android : Spinner de Material (texte de 16 sp, caret de 24 dp à 12 dp du bord, liste ouverte
 *    par-dessus le champ, lignes de 48 dp) ; inerte et estompé si [actif] est faux ;
 *  - iOS : bouton sur fond blanc 8 % aux coins de 8 pt (libellé blanc, blanc 70 % pour l'entrée
 *    vide, chevron), qui ouvre le menu du système. */
@Composable
fun ListeDeroulante(
    libelles: List<String>,
    selection: Int,
    onChoix: (Int) -> Unit,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    invite: String? = null,
    onEffacer: (() -> Unit)? = null,
) {
    val effacer = onEffacer?.takeIf { actif && selection in libelles.indices }
    if (estIos) ListeDeroulanteIos(libelles, selection, onChoix, modifier, actif, invite, effacer)
    else ListeDeroulanteAndroid(libelles, selection, onChoix, modifier, actif, invite, effacer)
}

@Composable
private fun ListeDeroulanteAndroid(
    libelles: List<String>,
    selection: Int,
    onChoix: (Int) -> Unit,
    modifier: Modifier,
    actif: Boolean,
    invite: String?,
    onEffacer: (() -> Unit)?,
) {
    var ouverte by remember { mutableStateOf(false) }
    var ancre by remember { mutableStateOf<Rect?>(null) }
    val couleurCaret = MaterialTheme.colorScheme.onSurfaceVariant
    val choisi = libelles.getOrNull(selection)
    Box(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { ancre = Rect(it.positionInWindow(), it.size.toSize()) }
            .clickable(enabled = actif) { ouverte = true }
            .alpha(if (actif) 1f else 0.38f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                choisi ?: invite.orEmpty(),
                fontSize = 16.sp,
                color = if (choisi == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (onEffacer != null) CaseDate("✕", couleur = MaterialTheme.colorScheme.error, contour = false, description = "Effacer", onClick = onEffacer)
            // ic_spinner_caret : triangle de 24 dp, à 12 dp du bord, dans une zone de 48 dp.
            Canvas(Modifier.padding(end = 12.dp).size(24.dp)) {
                val u = size.width / 24f
                val triangle = Path().apply {
                    moveTo(7f * u, 10f * u)
                    lineTo(12f * u, 15f * u)
                    lineTo(17f * u, 10f * u)
                    close()
                }
                drawPath(triangle, couleurCaret)
            }
        }
        // Dans la boîte du champ : la liste s'ancre sur lui, pas sur son libellé.
        val zone = ancre
        if (ouverte && zone != null) ListeSpinner(libelles, zone, onChoix, onFermer = { ouverte = false })
    }
}

/** La liste ouverte d'un Spinner : de la largeur du champ, lignes de 48 dp, texte de 16 sp. */
@Composable
private fun ListeSpinner(libelles: List<String>, zone: Rect, onChoix: (Int) -> Unit, onFermer: () -> Unit) {
    val densite = LocalDensity.current
    Popup(
        popupPositionProvider = remember { PositionSpinner },
        onDismissRequest = onFermer,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 8.dp,
            modifier = with(densite) { Modifier.width(zone.width.toDp()).heightIn(max = 400.dp) },
        ) {
            LazyColumn {
                itemsIndexed(libelles) { i, libelle ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                onFermer()
                                onChoix(i)
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(libelle, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** `overlapAnchor` du Spinner : la liste s'ouvre par-dessus le champ, alignée sur son bord gauche,
 *  ramenée dans la fenêtre si elle déborde. */
private object PositionSpinner : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val y = anchorBounds.top.coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun ListeDeroulanteIos(
    libelles: List<String>,
    selection: Int,
    onChoix: (Int) -> Unit,
    modifier: Modifier,
    actif: Boolean,
    invite: String?,
    onEffacer: (() -> Unit)?,
) {
    val choisi = libelles.getOrNull(selection)
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (actif) 1f else 0.4f)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Le menu couvre le libellé et le chevron ; la croix, à côté, garde son toucher.
        Box(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    choisi ?: invite.orEmpty(),
                    fontSize = 17.sp,
                    color = if (choisi == null || (invite == null && selection == 0)) CouleursGeoMys.blanc70 else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                SymboleSf("chevron.down", 12f, listOf(CouleursGeoMys.blanc70), graisse = GraisseSymbole.DEMI_GRASSE)
            }
            if (actif) MenuSysteme(libelles, selection, onChoix, Modifier.matchParentSize())
        }
        if (onEffacer != null) CroixEffacerIos(onEffacer, Modifier.padding(end = 10.dp))
    }
}

/** iOS : la croix des listes du web, qui vide le choix (`xmark` rouge, comme celle des champs à
 *  filtre). */
@Composable
internal fun CroixEffacerIos(onEffacer: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onEffacer),
        contentAlignment = Alignment.Center,
    ) {
        SymboleSf("xmark", 13f, listOf(CouleursGeoMys.rougePastille), graisse = GraisseSymbole.DEMI_GRASSE, description = "Effacer")
    }
}

/** iOS : une zone transparente qui ouvre le menu du système ([options], l'option [selection]
 *  cochée), comme le `Menu` de SwiftUI. Sans effet sous Android. */
@Composable
expect fun MenuSysteme(options: List<String>, selection: Int, onChoix: (Int) -> Unit, modifier: Modifier)
