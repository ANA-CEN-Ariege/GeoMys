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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.theme.Apparence
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.LocalApparence

/** Vrai sous l'allure iOS : raccourci des écrans. */
val estIos: Boolean
    @Composable get() = LocalApparence.current == Apparence.IOS

/** Marges qui n'existent que dans le layout XML d'Android (`layout_marginTop`…) : sous iOS, ce sont
 *  les espacements des piles SwiftUI qui séparent les éléments. */
@Composable
fun Modifier.margesAndroid(haut: Dp = 0.dp, bas: Dp = 0.dp): Modifier =
    if (estIos) this else padding(top = haut, bottom = bas)

/** Fond des cartes Android : colorSurface teinté par l'overlay d'élévation de Material Components
 *  (colorPrimary à (4,5·ln(2 + 1) + 2) % ≈ 18/255 pour 2 dp), soit #141822. */
private fun fondCarteAndroid(surface: Color): Color =
    CouleursGeoMys.primaire.copy(alpha = 18f / 255f).compositeOver(surface)

/** Carte de section des Paramètres.
 *  - Android : MaterialCardView (style M3 Outlined : contour 1 dp colorOutlineVariant) aux coins de
 *    12 dp, élévation 2 dp, marges intérieures 16 dp ; titre 16 sp gras, [margeTitreAndroid] dessous.
 *  - iOS : carte de GeoMys-IOS : fond blanc 7 % aux coins de 12 pt, marges 16 pt, titre 16 pt gras
 *    blanc, éléments espacés de 12 pt. */
@Composable
fun CarteSection(
    titre: String,
    modifier: Modifier = Modifier,
    margeTitreAndroid: Dp = 8.dp,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    if (estIos) {
        Column(
            modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(titre, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            contenu()
        }
    } else {
        val forme = RoundedCornerShape(12.dp)
        val couleurs = MaterialTheme.colorScheme
        Column(
            modifier
                .fillMaxWidth()
                .shadow(2.dp, forme)
                .background(fondCarteAndroid(couleurs.surface), forme)
                .border(1.dp, couleurs.outlineVariant, forme)
                .padding(16.dp),
        ) {
            Text(
                titre,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = margeTitreAndroid),
            )
            contenu()
        }
    }
}

/** Bandeau d'avertissement encadré, jamais sur fond pastel : texte 14 sp gras dans le cadre de
 *  `cadreColore` (2 dp, coins de 8 dp) en couleur d'avertissement sous Android ; `bandeauOrange` de
 *  GeoMys-IOS (14 pt demi-gras, orange). */
@Composable
fun BandeauAvertissement(texte: String, modifier: Modifier = Modifier) {
    val ios = estIos
    val couleur = if (ios) CouleursGeoMys.orange else CouleursGeoMys.avertissement
    Text(
        texte,
        fontSize = 14.sp,
        fontWeight = if (ios) FontWeight.SemiBold else FontWeight.Bold,
        color = couleur,
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, couleur, RoundedCornerShape(8.dp))
            .padding(12.dp),
    )
}

/** Pastille ronde pleine (indicator_red, indicator_green ; `Circle()` de GeoMys-IOS). */
@Composable
fun Pastille(couleur: Color, modifier: Modifier = Modifier, taille: Dp = 10.dp) {
    Box(modifier.size(taille).background(couleur, CircleShape))
}

/** Séparateur de liste iOS (`Divider()`) : un pixel physique, couleur séparateur du mode sombre. */
@Composable
fun SeparateurIos(modifier: Modifier = Modifier) {
    val epaisseur = with(LocalDensity.current) { 1f.toDp() }
    Box(modifier.fillMaxWidth().height(epaisseur).background(Color(0x99545458)))
}

/** Progression d'envoi INLINE (« Mes saisies », « Mes visites ») : ProgressBar de 24 dp et message
 *  12 sp sous Android ; `ProgressView` réduit teinté en jaune clair et message 12 pt blanc 70 % sous
 *  iOS. [margeAndroid] : retrait du bord, celui de la liste de l'écran. */
@Composable
fun ProgressionEnvoi(texte: String, margeAndroid: Dp = 16.dp) {
    if (estIos) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IndicateurActivite(CouleursGeoMys.jauneClair, petitIos = true)
            Text(texte, fontSize = 12.sp, color = CouleursGeoMys.blanc70)
        }
    } else {
        IndicateurActivite(CouleursGeoMys.orange, Modifier.padding(start = margeAndroid, top = 8.dp), tailleAndroid = 24f)
        Text(texte, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).padding(horizontal = margeAndroid))
    }
}
