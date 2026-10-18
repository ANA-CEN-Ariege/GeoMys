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

package fr.ariegenature.geomys.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/** Couleurs de GeoMys : res/values/colors.xml et ui/Couleurs.kt de GeoMys-Android, dont
 *  Theme/Couleurs.swift de GeoMys-IOS reprend les valeurs. */
object CouleursGeoMys {
    /** colorPrimary. */
    val primaire = Color(0xFF1B6CA8)
    /** colorSecondary du thème clair (values) : sous iOS, le vert des pastilles et des coches. */
    val vert = Color(0xFF43A047)
    /** colorSecondary du thème sombre (values-night), forcé sous Android : l'accent du thème. */
    val orange = Color(0xFFFF6D00)
    /** green_85 : fond translucide des boutons de l'accueil. */
    val vert85 = Color(0xD943A047)
    /** white_70 : textes et icônes secondaires sur le dégradé. */
    val blanc70 = Color(0xB3FFFFFF)
    /** accueil_top, haut du dégradé de fond. */
    val fondHaut = Color(0xFF14402E)
    /** accueil_bottom, bas du dégradé de fond. */
    val fondBas = Color(0xFF0D264C)
    /** jaune_clair : liens, boutons texte et fil d'Ariane. */
    val jauneClair = Color(0xFFFFE082)
    /** rouge_pastille (indicator_red) : éléments en attente d'envoi, configuration incomplète. */
    val rougePastille = Color(0xFFF44336)
    /** indicator_green : configuration valide, sur l'accueil Android. */
    val vertIndicateur = Color(0xFF4CAF50)
    /** couleurSucces() : envoyé, test de connexion réussi. */
    val succes = Color(0xFF2E7D32)
    /** couleurEnCours() : envoi en cours. */
    val enCours = Color(0xFFFFB300)
    /** couleurAvertissement() : état à traiter, non bloquant. */
    val avertissement = Color(0xFFE65100)
    /** android.R.color.holo_red_dark : « Vider le cache ». */
    val holoRougeFonce = Color(0xFFCC0000)
    /** android.R.color.holo_orange_dark : avertissements de Paramètres. */
    val holoOrangeFonce = Color(0xFFFF8800)
}

/** Thème Material 3 sombre de Material Components 1.12 (mêmes valeurs que les défauts de Compose),
 *  avec les couleurs de Theme.GeoMys (values-night). */
private val schemaSombre = darkColorScheme(
    primary = CouleursGeoMys.primaire,
    onPrimary = Color.White,
    secondary = CouleursGeoMys.orange,
    onSecondary = Color.White,
)

/** Réglage de rendu du texte propre à la plateforme : sous Android, la marge haute et basse
 *  des TextView (includeFontPadding), que Compose n'applique plus par défaut. */
internal expect val styleTextePlateforme: PlatformTextStyle?

/** Thème sombre sur les deux plateformes, comme GeoMys (mode nuit forcé sous Android, schéma
 *  sombre imposé sous iOS). Pose l'allure [apparence] et le style de texte par défaut :
 *  - Android : celui d'une TextView (14 sp, textColorPrimary) ;
 *  - iOS : celui d'un `Text` SwiftUI en mode sombre (17 pt, blanc), à taille FIXE comme les
 *    `.font(.system(size:))` de GeoMys-IOS, qui ne suivent pas la taille de texte du système. */
@Composable
fun GeoMysTheme(apparence: Apparence = apparencePlateforme, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schemaSombre) {
        val ios = apparence == Apparence.IOS
        val texte = if (ios) {
            TextStyle(color = Color.White, fontSize = 17.sp)
        } else {
            TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, platformStyle = styleTextePlateforme)
        }
        val densite = LocalDensity.current
        CompositionLocalProvider(
            LocalApparence provides apparence,
            LocalTextStyle provides texte,
            LocalDensity provides if (ios) Density(densite.density, fontScale = 1f) else densite,
            content = content,
        )
    }
}

/** Fond commun des écrans : dégradé diagonal du haut-gauche vers le bas-droite
 *  (`bg_accueil` de GeoMys-Android, angle 315° ; `degradeAccueil` de GeoMys-IOS). */
@Composable
fun FondGeoMys(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(CouleursGeoMys.fondHaut, CouleursGeoMys.fondBas),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
        content = content,
    )
}
