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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FormeContinue

/** Alerte modale : titre, message et boutons.
 *  - Android : AlertDialog d'AppCompat sous le thème Material 3 de GeoMys-Android (carte gris 800
 *    aux coins de 2 dp, titre 20 sp medium, message 16 sp, boutons texte bleus, voile à 32 %).
 *  - iOS : alerte système, comme `.alert` de SwiftUI dans GeoMys-IOS.
 *  À poser en dernier dans la racine de l'écran : sous Android, la carte couvre tout l'écran. */
@Composable
fun DialogueAlerte(visible: Boolean, titre: String, message: String, boutons: List<BoutonAlerte>, onFermer: () -> Unit) {
    if (estIos) {
        AlerteSysteme(visible, titre, message, boutons, onFermer)
        return
    }
    CoucheDialogueAndroid(visible, onFermer) {
        Text(
            titre,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp),
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text(message, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp))
        }
        BarreBoutonsAndroid(boutons, onFermer)
    }
}

/** Dialogue de choix dans une liste (« Taxons par listes ») : un titre, un sous-titre, des lignes
 *  à toucher, un bouton « Fermer ».
 *  - Android : AlertDialog d'AppCompat à en-tête personnalisé (titre 20 sp gras, sous-titre 13 sp,
 *    trait #40808080) et `setItems` (lignes de 48 dp, TitleMedium, marges de 24 dp).
 *  - iOS : `DialogueMaterial` de GeoMys-IOS, monté du bas de l'écran comme un `fullScreenCover` :
 *    carte bleu nuit aux coins continus de 12 pt, titre 18 pt, lignes à chevron, voile à 45 %. */
@Composable
fun DialogueListe(
    visible: Boolean,
    titre: String,
    sousTitre: String,
    lignes: List<String>,
    onChoisir: (Int) -> Unit,
    onFermer: () -> Unit,
) {
    if (estIos) {
        DialogueMaterialIos(visible, titre, onFermer, boutons = {
            Text(
                "Fermer",
                color = CouleursGeoMys.blanc70,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onFermer),
            )
        }) {
            Text(sousTitre, fontSize = 13.sp, color = CouleursGeoMys.vert)
            lignes.forEachIndexed { i, ligne ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChoisir(i) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ligne, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
                    SymboleSf("chevron.right", 12f, listOf(CouleursGeoMys.blanc70), graisse = GraisseSymbole.DEMI_GRASSE)
                }
            }
        }
        return
    }
    CoucheDialogueAndroid(visible, onFermer) {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, top = 18.dp, end = 22.dp)) {
            Text(titre, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                sousTitre,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(Color(0x40808080)))
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            lignes.forEachIndexed { i, ligne ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onChoisir(i) }
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(ligne, style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                }
            }
        }
        BarreBoutonsAndroid(listOf(BoutonAlerte("Fermer", annulation = true)), onFermer)
    }
}

// ─── Android : AlertDialog d'AppCompat ───

/** Fenêtre d'un AlertDialog d'AppCompat : voile noir à 32 % (scrim M3), carte centrée de 95 % de la
 *  largeur moins les 16 dp de retrait de chaque côté (`abc_dialog_material_background`), fond
 *  colorBackgroundFloating (gris 800), coins de 2 dp, élévation 6 dp. Toucher le voile ou Retour la
 *  ferme. Entrée en fondu et léger zoom, comme l'animation de dialogue d'Android. */
@Composable
private fun CoucheDialogueAndroid(visible: Boolean, onFermer: () -> Unit, contenu: @Composable ColumnScope.() -> Unit) {
    if (visible) RetourSysteme(onRetour = onFermer)
    AnimatedVisibility(visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFermer)
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            val forme = RoundedCornerShape(2.dp)
            Column(
                Modifier
                    .padding(16.dp)
                    .width(maxWidth * 0.95f - 32.dp)
                    .animateEnterExit(enter = scaleIn(tween(150), initialScale = 0.9f), exit = scaleOut(tween(150), targetScale = 0.9f))
                    .shadow(6.dp, forme)
                    .background(Color(0xFF424242), forme)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                content = contenu,
            )
        }
    }
}

/** Barre de boutons d'AlertDialog (`abc_alert_dialog_button_bar_material`) : marges 12 dp × 4 dp,
 *  boutons négatifs puis positifs alignés à droite, en `Widget.Material3.Button.TextButton.Dialog`
 *  (texte colorPrimary, largeur minimale 64 dp). Chaque bouton ferme le dialogue. */
@Composable
private fun BarreBoutonsAndroid(boutons: List<BoutonAlerte>, onFermer: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        for (bouton in boutons.filter { it.annulation } + boutons.filterNot { it.annulation }) {
            BoutonMaterial(
                onClick = {
                    bouton.action()
                    onFermer()
                },
                modifier = Modifier.widthIn(min = 64.dp),
                marges = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                couleurContenu = MaterialTheme.colorScheme.primary,
            ) { Text(bouton.texte, style = styleLibelleMaterial(MaterialTheme.colorScheme.primary), maxLines = 1) }
        }
    }
}

// ─── iOS : DialogueMaterial de GeoMys-IOS ───

/** Courbe d'une présentation modale UIKit (glissement depuis le bas). */
private val PresentationIos = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

/** `DialogueMaterial` de GeoMys-IOS : voile noir à 45 % (toucher = fermer), carte de 560 pt au plus,
 *  à 20 pt des bords, fond bleu nuit (accueil_bottom), coins continus de 12 pt, filet blanc 15 %,
 *  ombre ; titre 18 pt demi-gras, contenu défilant au-delà de 70 % de l'écran, boutons en bas à
 *  droite. Le tout monte du bas, comme le `fullScreenCover` qui le présente. */
@Composable
private fun DialogueMaterialIos(
    visible: Boolean,
    titre: String,
    onFond: () -> Unit,
    boutons: @Composable () -> Unit,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(450, easing = PresentationIos)) { it },
        exit = slideOutVertically(tween(350, easing = PresentationIos)) { it },
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFond)
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            val forme = FormeContinue(12.dp)
            val hauteurMax = maxHeight * 0.7f
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .shadow(18.dp, forme, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
                    .background(CouleursGeoMys.fondBas, forme)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), forme)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Text(
                    titre,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
                Column(
                    Modifier
                        .heightIn(max = hauteurMax)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = contenu,
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                ) { boutons() }
            }
        }
    }
}
