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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_home
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import org.jetbrains.compose.resources.painterResource

/** Séparateur des niveaux du fil, identique dans les deux applis (`FIL_SEPARATEUR`). */
const val FIL_SEPARATEUR = " › "

/** Bandeau de navigation des écrans ouverts depuis l'accueil : « 🏠 › [libelle] », la maison
 *  ramenant à l'accueil.
 *  - Android : `appliquerBandeauNavigation` sur `view_bandeau_saisie` (pastille aux coins de 12 dp,
 *    colorSurfaceVariant, élévation 6 dp, 13 sp gras, maison `ic_home` jaune de 2,1 fois le texte,
 *    calée sur le bas de la ligne). L'écran le place à 12 dp du coin haut-gauche.
 *  - iOS : `FilAriane` de GeoMys-IOS (bande pleine largeur, noir 25 %, 13 pt, maison « house.fill »
 *    de 1,68 fois le texte, niveau courant demi-gras blanc). */
@Composable
fun BandeauNavigation(libelle: String, onAccueil: () -> Unit, modifier: Modifier = Modifier) {
    if (estIos) {
        val maison = 13f * 1.68f
        val texte = buildAnnotatedString {
            appendInlineContent("maison", "Accueil")
            withStyle(SpanStyle(color = CouleursGeoMys.blanc70, fontSize = 13.sp)) { append(FIL_SEPARATEUR) }
            withStyle(SpanStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)) { append(libelle) }
        }
        val icone = InlineTextContent(Placeholder(maison.sp, maison.sp, PlaceholderVerticalAlign.TextCenter)) {
            Box(Modifier.fillMaxSize().clickable(onClickLabel = "Accueil", onClick = onAccueil), contentAlignment = Alignment.Center) {
                SymboleSf("house.fill", maison, listOf(CouleursGeoMys.jauneClair))
            }
        }
        Text(
            texte,
            inlineContent = mapOf("maison" to icone),
            modifier = modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    } else {
        val couleurs = MaterialTheme.colorScheme
        val maison = 13f * 2.10f
        val texte = buildAnnotatedString {
            appendInlineContent("maison", "Accueil")
            append(FIL_SEPARATEUR)
            append(libelle)
        }
        val icone = InlineTextContent(Placeholder(maison.sp, maison.sp, PlaceholderVerticalAlign.Bottom)) {
            Image(
                painterResource(Res.drawable.ic_home),
                contentDescription = "Accueil",
                colorFilter = ColorFilter.tint(CouleursGeoMys.jauneClair),
                modifier = Modifier.fillMaxSize().clickable(onClick = onAccueil),
            )
        }
        val forme = RoundedCornerShape(12.dp)
        Text(
            texte,
            inlineContent = mapOf("maison" to icone),
            color = couleurs.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .shadow(6.dp, forme)
                .background(couleurs.surfaceVariant, forme)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
