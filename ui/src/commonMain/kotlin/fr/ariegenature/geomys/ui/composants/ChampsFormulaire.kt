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

// Les champs des formulaires de saisie (caractérisation, dénombrement, détails du relevé) : les vues que
// GeoMys-Android crée dans ses écrans (TextView de titre, EditText), ChampAvecLibelle et ChampTexteSaisie de
// GeoMys-IOS (UI/Saisie/ComposantsFormulaireSaisie.swift).

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** android.R.color.darker_gray : titres des champs des formulaires de saisie Android. */
val GRIS_TITRE_CHAMP = Color(0xFFAAAAAA)

/** Un champ de formulaire de saisie et son [libelle] au-dessus.
 *  - Android : TextView de 13 sp gris, [margeHautAndroid] au-dessus ;
 *  - iOS : `ChampAvecLibelle` de GeoMys-IOS (13 pt blanc 70 %, 4 pt au-dessus du champ). */
@Composable
fun ChampLibelle(
    libelle: String,
    modifier: Modifier = Modifier,
    margeHautAndroid: Int = 12,
    tailleAndroid: Int = 13,
    champ: @Composable () -> Unit,
) {
    if (estIos) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(libelle, fontSize = 13.sp, color = CouleursGeoMys.blanc70)
            champ()
        }
    } else {
        Column(modifier.padding(top = margeHautAndroid.dp)) {
            Text(libelle, fontSize = tailleAndroid.sp, color = GRIS_TITRE_CHAMP)
            champ()
        }
    }
}

/** Champ de texte d'un formulaire de saisie.
 *  - Android : EditText souligné (18 sp ; trait de 1 dp colorControlNormal, 2 dp orange avec le
 *    focus, retiré de 4 dp des côtés et de 7 dp du bas), au moins [lignesMin] lignes ; [compactAndroid] :
 *    marges remplacées par 8 dp sous le texte (`setPadding(0, 0, 0, 8 dp)` des dialogues), sans
 *    hauteur minimale ;
 *  - iOS : `ChampTexteSaisie` de GeoMys-IOS (texte blanc sur blanc 8 %, coins de 8 pt), de
 *    [lignesMin] à [lignesMax] lignes ; sous Android, [lignesMaxAndroid] au plus (`setLines`).
 *  [indication] : le texte d'invite du champ vide. [interactions] : pour suivre le focus et les
 *  touchers. */
@Composable
fun ChampTexteFormulaire(
    valeur: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    lignesMin: Int = 1,
    uneLigne: Boolean = true,
    clavier: KeyboardOptions = KeyboardOptions.Default,
    compactAndroid: Boolean = false,
    indication: String? = null,
    lignesMax: Int = Int.MAX_VALUE,
    lignesMaxAndroid: Int = Int.MAX_VALUE,
    interactions: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    if (estIos) {
        BasicTextField(
            value = valeur,
            onValueChange = onChange,
            singleLine = uneLigne,
            minLines = lignesMin,
            maxLines = if (uneLigne) 1 else lignesMax,
            textStyle = styleSaisieIos,
            cursorBrush = SolidColor(CouleursGeoMys.jauneClair),
            keyboardOptions = clavier,
            interactionSource = interactions,
            modifier = modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { champ ->
                Box {
                    if (indication != null && valeur.isEmpty()) Text(indication, style = styleSaisieIos.copy(color = INVITE_IOS))
                    champ()
                }
            },
        )
    } else {
        val focalise by interactions.collectIsFocusedAsState()
        val trait = if (focalise) CouleursGeoMys.orange else MaterialTheme.colorScheme.onSurfaceVariant
        val epaisseur = if (focalise) 2.dp else 1.dp
        val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        val couleurInvite = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        Box(
            modifier
                .fillMaxWidth()
                .then(if (compactAndroid) Modifier else Modifier.defaultMinSize(minHeight = 48.dp))
                .drawBehind {
                    val e = epaisseur.toPx()
                    val retrait = 4.dp.toPx()
                    val y = size.height - 7.dp.toPx() - e / 2
                    drawLine(trait, Offset(retrait, y), Offset(size.width - retrait, y), e)
                }
                .padding(if (compactAndroid) PaddingValues(bottom = 8.dp) else PaddingValues(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 14.dp)),
        ) {
            BasicTextField(
                value = valeur,
                onValueChange = onChange,
                singleLine = uneLigne,
                minLines = lignesMin,
                maxLines = if (uneLigne) 1 else lignesMaxAndroid,
                textStyle = style,
                cursorBrush = SolidColor(CouleursGeoMys.orange),
                keyboardOptions = clavier,
                interactionSource = interactions,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { champ ->
                    Box {
                        if (indication != null && valeur.isEmpty()) Text(indication, style = style.copy(color = couleurInvite))
                        champ()
                    }
                },
            )
        }
    }
}

/** Invite d'un `TextField` SwiftUI vide, en mode sombre (placeholderText). */
internal val INVITE_IOS = Color(0x4DEBEBF5)
