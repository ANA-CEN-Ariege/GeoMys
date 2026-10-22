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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Bouton icône des lignes de liste (supprimer, envoyer, éditer…), sur une cible de [cible]
 *  (44 × 48 par défaut).
 *  - Android : ImageButton à fond `selectableItemBackgroundBorderless` (ondulation sans bord) ;
 *    l'icône garde sa taille de 24 dp, centrée (`scaleType="center"` des ImageButton) ;
 *  - iOS : bouton de GeoMys-IOS, icône de [tailleIconeIos] (24 pt) au centre, estompée à l'appui. */
@Composable
fun BoutonIcone(
    icone: DrawableResource,
    teinte: Color,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cible: DpSize = DpSize(44.dp, 48.dp),
    tailleIconeIos: Dp = 24.dp,
) {
    if (estIos) {
        BoutonIos(onClick, modifier.size(cible), opaciteAppui = 0.3f) {
            Image(
                painterResource(icone),
                contentDescription = description,
                colorFilter = ColorFilter.tint(teinte),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(tailleIconeIos),
            )
        }
    } else {
        Box(
            modifier
                .size(cible)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(icone),
                contentDescription = description,
                colorFilter = ColorFilter.tint(teinte),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Petite statistique d'une ligne : icône de 14 dp teintée, puis son texte 12 sp, dans [couleur]. */
@Composable
fun Statistique(icone: DrawableResource, texte: String, couleur: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (estIos) 4.dp else 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(icone),
            contentDescription = null,
            colorFilter = ColorFilter.tint(couleur),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(14.dp),
        )
        Text(texte, color = couleur, fontSize = 12.sp)
    }
}
