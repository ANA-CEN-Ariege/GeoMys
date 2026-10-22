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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.theme.Apparence
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.LocalApparence
import fr.ariegenature.geomys.ui.theme.styleTextePlateforme

/** Forme des boutons des deux allures : pilule (coins à 50 % de Material 3, `Capsule` de SwiftUI). */
val PILULE = RoundedCornerShape(percent = 50)

// ─── Allure Android : MaterialButton de Material Components 1.12 ───

/** Libellé d'un MaterialButton : TextAppearance.Material3.LabelLarge (14 sp, medium, interlettrage
 *  0,00714 em) à la taille [taille] (`android:textSize` des boutons de l'accueil), coupure de lignes
 *  « haute qualité » comme les TextView. */
internal fun styleLibelleMaterial(couleur: Color, taille: Float = 14f) = TextStyle(
    color = couleur,
    fontSize = taille.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = (taille * 0.00714286f).sp,
    textAlign = TextAlign.Center,
    lineBreak = LineBreak.Paragraph,
    platformStyle = styleTextePlateforme,
)

/** Un MaterialButton : hauteur minimale [hauteurMin] (48 dp) dont [retraitVertical] (4 dp) en haut et
 *  en bas (le fond ne couvre que 40 dp, la zone tactile garde 48 dp), largeur minimale [largeurMin]
 *  (88 dp), [forme] (pilule), contenu centré dans les marges intérieures [marges], comptées depuis
 *  les bords de la vue comme sous Android. */
@Composable
internal fun BoutonMaterial(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    fond: Color = Color.Transparent,
    contour: BorderStroke? = null,
    marges: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
    couleurContenu: Color,
    onLongClick: (() -> Unit)? = null,
    hauteurMin: Dp = 48.dp,
    largeurMin: Dp = 88.dp,
    retraitVertical: Dp = 4.dp,
    forme: Shape = PILULE,
    contenu: @Composable () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = largeurMin, minHeight = hauteurMin)
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                enabled = actif,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = retraitVertical)
                .clip(forme)
                .background(fond)
                .then(if (contour != null) Modifier.border(contour, forme) else Modifier)
                .indication(interactions, ripple(color = couleurContenu)),
        )
        Box(Modifier.padding(marges), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides couleurContenu) { contenu() }
        }
    }
}

// ─── Allure iOS : ButtonStyle de GeoMys-IOS (Theme/ButtonStyles.swift) ───

/** Un bouton SwiftUI à style personnalisé : pas d'ondulation, le libellé s'estompe à l'appui
 *  ([opaciteAppui]) et quand il est désactivé ([opaciteInactif]). */
@Composable
internal fun BoutonIos(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    opaciteAppui: Float = 0.85f,
    opaciteInactif: Float = 1f,
    onLongClick: (() -> Unit)? = null,
    contenu: @Composable () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    Box(
        modifier = modifier
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                enabled = actif,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .alpha(if (!actif) opaciteInactif else if (appuye) opaciteAppui else 1f),
        contentAlignment = Alignment.Center,
    ) { contenu() }
}

// ─── Boutons communs ───

/** Bouton contour jaune clair : `Widget.GeoMys.Button.OutlinedButton` (contour 1 dp, marges 24 dp,
 *  ou [margesAndroid] et [hauteurMinAndroid] posées par le layout) sous Android, `GeoMysBoutonOutlineStyle` (pilule pleine largeur, 14 pt medium) sous iOS. Désactivé,
 *  il ne change pas d'aspect, comme dans les deux applis. Sous iOS, [enCours] place l'indicateur
 *  d'activité devant [texteEnCours] (« Test en cours… ») ; sous Android, l'écran pose sa ProgressBar
 *  sous le bouton. */
@Composable
fun BoutonContour(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    enCours: Boolean = false,
    texteEnCours: String = texte,
    margesAndroid: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
    hauteurMinAndroid: Dp = 48.dp,
) {
    if (LocalApparence.current == Apparence.IOS) {
        BoutonIos(onClick, modifier, actif, opaciteAppui = 0.7f) {
            // `.frame(maxWidth: .infinity)` du style : la pilule occupe toute la largeur offerte.
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, CouleursGeoMys.jauneClair, PILULE)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (enCours) IndicateurActivite(CouleursGeoMys.jauneClair, petitIos = true)
                Text(
                    if (enCours) texteEnCours else texte,
                    color = CouleursGeoMys.jauneClair,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        BoutonMaterial(
            onClick = onClick,
            modifier = modifier,
            actif = actif,
            contour = BorderStroke(1.dp, CouleursGeoMys.jauneClair),
            marges = margesAndroid,
            couleurContenu = CouleursGeoMys.jauneClair,
            hauteurMin = hauteurMinAndroid,
        ) { Text(texte, style = styleLibelleMaterial(CouleursGeoMys.jauneClair)) }
    }
}

/** Bouton plein bleu primaire : `Widget.Material3.Button` sous Android (grisé quand il est
 *  désactivé), `GeoMysBoutonBleuStyle` sous iOS (pilule pleine largeur, 15 pt medium, estompé à
 *  40 % quand il est désactivé). Sous iOS, [enCours] place l'indicateur devant [texteEnCours]. */
@Composable
fun BoutonPlein(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    enCours: Boolean = false,
    texteEnCours: String = texte,
) {
    if (LocalApparence.current == Apparence.IOS) {
        // `LongPressGesture(minimumDuration: 0.6)` de GeoMys-IOS.
        val base = LocalViewConfiguration.current
        val appuiLong = remember(base) { object : ViewConfiguration by base { override val longPressTimeoutMillis = 600L } }
        CompositionLocalProvider(LocalViewConfiguration provides appuiLong) {
            BoutonIos(onClick, modifier, actif, opaciteInactif = 0.4f, onLongClick = onLongClick) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(CouleursGeoMys.primaire, PILULE)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (enCours) IndicateurActivite(Color.White, petitIos = true)
                    Text(
                        if (enCours) texteEnCours else texte,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    } else {
        val couleurs = MaterialTheme.colorScheme
        val contenu = if (actif) couleurs.onPrimary else couleurs.onSurface.copy(alpha = 0.38f)
        BoutonMaterial(
            onClick = onClick,
            modifier = modifier,
            actif = actif,
            fond = if (actif) couleurs.primary else couleurs.onSurface.copy(alpha = 0.12f),
            couleurContenu = contenu,
            onLongClick = onLongClick,
        ) { Text(texte, style = styleLibelleMaterial(contenu)) }
    }
}

/** Bouton plein vert translucide (green_85), texte blanc : un `Button` Material 3 à fond
 *  `backgroundTint="@color/green_85"` sous Android, `GeoMysBoutonVertStyle` sous iOS (pilule pleine
 *  largeur, 15 pt medium, marges 24 pt × [margeVerticaleIos], estompé à 40 % quand il est désactivé),
 *  précédé du symbole [symboleIos] s'il y en a un (`Label` de SwiftUI). */
@Composable
fun BoutonVert(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    margeVerticaleIos: Dp = 12.dp,
    symboleIos: String? = null,
) {
    if (LocalApparence.current == Apparence.IOS) {
        BoutonIos(onClick, modifier, actif, opaciteInactif = 0.4f) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(CouleursGeoMys.vert85, PILULE)
                    .padding(horizontal = 24.dp, vertical = margeVerticaleIos),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (symboleIos != null) SymboleSf(symboleIos, 15f, listOf(Color.White), graisse = GraisseSymbole.MOYENNE)
                Text(
                    texte,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        BoutonMaterial(
            onClick = onClick,
            modifier = modifier,
            actif = actif,
            fond = CouleursGeoMys.vert85,
            couleurContenu = Color.White,
        ) { Text(texte, style = styleLibelleMaterial(Color.White)) }
    }
}

/** Bouton texte : `Widget.GeoMys.Button.TextButton` sous Android (marges 12 dp), simple bouton
 *  SwiftUI sous iOS ([tailleIos] pt medium, estompé à l'appui). La couleur ne change pas quand il
 *  est désactivé, comme dans les deux applis (couleur fixe, sans état). */
@Composable
fun BoutonTexte(
    texte: String,
    couleur: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    tailleIos: Float = 15f,
) {
    if (LocalApparence.current == Apparence.IOS) {
        BoutonIos(onClick, modifier, actif, opaciteAppui = 0.3f, opaciteInactif = 0.3f) {
            Text(texte, color = couleur, fontSize = tailleIos.sp, fontWeight = FontWeight.Medium)
        }
    } else {
        BoutonMaterial(
            onClick = onClick,
            modifier = modifier,
            actif = actif,
            marges = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            couleurContenu = couleur,
        ) { Text(texte, style = styleLibelleMaterial(couleur)) }
    }
}
