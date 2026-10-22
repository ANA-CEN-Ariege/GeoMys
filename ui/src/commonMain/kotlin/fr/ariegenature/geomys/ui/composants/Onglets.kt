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

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** Onglets des listes « Mes saisies », « Mes visites », « Mes stations » : fond transparent (le
 *  dégradé passe dessous), onglets de même largeur, texte et soulignement de l'onglet actif en
 *  jaune clair.
 *  - Android : TabLayout de Material 3 (`Widget.Material3.TabLayout`) : onglets de 48 dp, texte
 *    TitleSmall (14 sp medium) colorOnSurfaceVariant, indicateur de 3 dp aux coins hauts arrondis,
 *    de la largeur du texte ;
 *  - iOS : onglets de GeoMys-IOS : texte 13 pt (demi-gras s'il est actif, blanc 70 % sinon), trait
 *    de 2 pt sur toute la largeur de l'onglet. */
@Composable
fun OngletsListe(titres: List<String>, selection: Int, onChoisir: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (estIos) {
        Row(modifier.fillMaxWidth()) {
            titres.forEachIndexed { i, titre ->
                val actif = i == selection
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChoisir(i) }
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        titre,
                        fontSize = 13.sp,
                        fontWeight = if (actif) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (actif) CouleursGeoMys.jauneClair else CouleursGeoMys.blanc70,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (actif) CouleursGeoMys.jauneClair else Color.Transparent),
                    )
                }
            }
        }
    } else {
        val inactif = MaterialTheme.colorScheme.onSurfaceVariant
        Row(modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            titres.forEachIndexed { i, titre ->
                val actif = i == selection
                val couleur = if (actif) CouleursGeoMys.jauneClair else inactif
                var largeurTexte by remember { mutableIntStateOf(0) }
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, color = couleur),
                            role = Role.Tab,
                        ) { onChoisir(i) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        titre,
                        color = couleur,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.1.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.onSizeChanged { largeurTexte = it.width },
                    )
                    if (actif) {
                        val largeur = with(LocalDensity.current) { largeurTexte.toDp() }
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .width(largeur - 4.dp)
                                .height(3.dp)
                                .background(CouleursGeoMys.jauneClair, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                        )
                    }
                }
            }
        }
    }
}
