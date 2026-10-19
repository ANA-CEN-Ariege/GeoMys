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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
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
 *  [annulable] faux (`setCancelable(false)`) : ni le voile ni Retour ne la ferment, seulement ses
 *  boutons (une alerte d'iOS ne se ferme jamais autrement).
 *  À poser en dernier dans la racine de l'écran : sous Android, la carte couvre tout l'écran. */
@Composable
fun DialogueAlerte(
    visible: Boolean,
    titre: String,
    message: String,
    boutons: List<BoutonAlerte>,
    onFermer: () -> Unit,
    annulable: Boolean = true,
) {
    if (estIos) {
        AlerteSysteme(visible, titre, message, boutons, onFermer)
        return
    }
    CoucheDialogueAndroid(visible, if (annulable) onFermer else ({})) {
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

/** Confirmation d'une action (« Supprimer ce relevé ? ») : titre, message, boutons.
 *  - Android : AlertDialog d'AppCompat, comme [DialogueAlerte] ;
 *  - iOS : feuille d'actions avec le message, le bouton [BoutonAlerte.destructif] en rouge, comme le
 *    `confirmationDialog` de GeoMys-IOS. */
@Composable
fun DialogueConfirmation(visible: Boolean, titre: String, message: String, boutons: List<BoutonAlerte>, onFermer: () -> Unit) {
    if (estIos) FeuilleActionsSysteme(visible, titre, boutons, onFermer, message)
    else DialogueAlerte(visible, titre, message, boutons, onFermer)
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

/** Liste de lignes à lire (« 3 espèces » : une espèce par ligne), fermée par « Fermer ».
 *  - Android : AlertDialog d'AppCompat avec `setItems` (lignes de 48 dp, marges de 24 dp) ; toucher
 *    une ligne ferme aussi le dialogue ;
 *  - iOS : alerte système, les lignes en message, comme le `.alert` de GeoMys-IOS. */
@Composable
fun DialogueElements(visible: Boolean, titre: String, lignes: List<String>, onFermer: () -> Unit) {
    if (estIos) {
        AlerteSysteme(visible, titre, lignes.joinToString("\n"), listOf(BoutonAlerte("Fermer", annulation = true)), onFermer)
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
            lignes.forEach { ligne ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onFermer)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(ligne, style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                }
            }
        }
        BarreBoutonsAndroid(listOf(BoutonAlerte("Fermer")), onFermer)
    }
}

/** Android : AlertDialog d'AppCompat avec `setItems` et « Fermer » (« 2 obs. ici — appuyer pour
 *  modifier ») : lignes de 48 dp au moins, marges de 24 dp, sur plusieurs lignes si le texte en
 *  porte ; toucher une ligne la choisit et ferme le dialogue. Sans effet sous iOS, où l'écran
 *  présente sa propre liste. */
@Composable
fun DialogueChoixLignesAndroid(visible: Boolean, titre: String, lignes: List<String>, onChoisir: (Int) -> Unit, onFermer: () -> Unit) {
    if (estIos) return
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
            lignes.forEachIndexed { i, ligne ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            onFermer()
                            onChoisir(i)
                        }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(ligne, style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                }
            }
        }
        BarreBoutonsAndroid(listOf(BoutonAlerte("Fermer")), onFermer)
    }
}

/** Choix d'une action (« Terminer la saisie ») ; chaque action ferme le dialogue.
 *  - Android : AlertDialog d'AppCompat avec `setItems` (lignes de 48 dp, texte 16 sp), sans bouton ;
 *  - iOS : feuille d'actions, l'action [BoutonAlerte.annulation] en bas (rôle `.cancel`), comme le
 *    `confirmationDialog` de GeoMys-IOS. */
@Composable
fun DialogueActions(visible: Boolean, titre: String, actions: List<BoutonAlerte>, onFermer: () -> Unit) {
    if (estIos) {
        FeuilleActionsSysteme(visible, titre, actions, onFermer)
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
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            actions.forEach { action ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            action.action()
                            onFermer()
                        }
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(action.texte, fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}

/** Choix d'une option dans une liste (« Fond de carte »), l'option courante [selection] marquée ;
 *  choisir ferme le dialogue.
 *  - Android : AlertDialog d'AppCompat avec `setSingleChoiceItems` (case radio, texte 18 sp, lignes
 *    de 48 dp) et « Annuler » ;
 *  - iOS : feuille d'actions, l'option courante précédée de « ✓ » (la feuille d'iOS ne montre
 *    aucune sélection), comme le `confirmationDialog` de GeoMys-IOS. */
@Composable
fun DialogueChoixUnique(
    visible: Boolean,
    titre: String,
    options: List<String>,
    selection: Int,
    onChoisir: (Int) -> Unit,
    onFermer: () -> Unit,
) {
    if (estIos) {
        val boutons = options.mapIndexed { i, option ->
            BoutonAlerte(if (i == selection) "✓ $option" else option) { onChoisir(i) }
        } + BoutonAlerte("Annuler", annulation = true)
        FeuilleActionsSysteme(visible, titre, boutons, onFermer)
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
            options.forEachIndexed { i, option ->
                val choisie = i == selection
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            onChoisir(i)
                            onFermer()
                        }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // listChoiceIndicatorSingle : cercle de 20 dp, plein d'orange (colorControlActivated) si choisi.
                    val couleur = if (choisie) CouleursGeoMys.orange else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(Modifier.size(20.dp).border(2.dp, couleur, CircleShape), contentAlignment = Alignment.Center) {
                        if (choisie) Box(Modifier.size(10.dp).background(couleur, CircleShape))
                    }
                    Spacer(Modifier.width(20.dp))
                    Text(option, fontSize = 18.sp, color = Color.White)
                }
            }
        }
        BarreBoutonsAndroid(listOf(BoutonAlerte("Annuler", annulation = true)), onFermer)
    }
}

/** Choix de plusieurs [options] (le champ « Observateurs » d'une visite), [coches] cochées à
 *  l'ouverture ; « Valider » rend les options cochées à [onValider], « Annuler » ne change rien.
 *  - Android : AlertDialog d'AppCompat avec `setMultiChoiceItems` (case de 18 dp, orange cochée,
 *    texte 18 sp, lignes de 48 dp), « Annuler » et « Valider » ;
 *  - iOS : la feuille de GeoMys-IOS (`SelectMultipleSheet`) : barre de navigation au titre en ligne,
 *    « Annuler » à gauche, « Valider » demi-gras à droite, liste groupée aux cases SF vertes. */
@Composable
fun DialogueChoixMultiple(
    visible: Boolean,
    titre: String,
    options: List<String>,
    coches: Set<Int>,
    onValider: (Set<Int>) -> Unit,
    onFermer: () -> Unit,
) {
    // La sélection en cours, reprise à chaque ouverture.
    var courante by remember(visible, coches) { mutableStateOf(coches) }
    val basculer: (Int) -> Unit = { i -> courante = if (i in courante) courante - i else courante + i }
    if (estIos) {
        FeuilleChoixMultipleIos(visible, titre, options, courante, basculer, onValider = { onValider(courante) }, onFermer)
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
            options.forEachIndexed { i, option ->
                val cochee = i in courante
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { basculer(i) }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // listChoiceIndicatorMultiple : carré de 18 dp, plein d'orange (colorControlActivated) si coché.
                    CaseACocherAndroid(cochee)
                    Spacer(Modifier.width(20.dp))
                    Text(option, fontSize = 18.sp, color = Color.White)
                }
            }
        }
        BarreBoutonsAndroid(
            listOf(BoutonAlerte("Annuler", annulation = true), BoutonAlerte("Valider") { onValider(courante) }),
            onFermer,
        )
    }
}

/** `abc_btn_check_material` : carré de 18 dp au trait de 2 dp aux coins de 2 dp ; coché, plein
 *  d'orange avec la coche blanche. */
@Composable
private fun CaseACocherAndroid(cochee: Boolean) {
    val bord = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(18.dp)) {
        val trait = 2.dp.toPx()
        val coin = CornerRadius(2.dp.toPx())
        if (cochee) {
            drawRoundRect(CouleursGeoMys.orange, cornerRadius = coin)
            val u = size.width / 18f
            val coche = Path().apply {
                moveTo(4f * u, 9.5f * u)
                lineTo(7.5f * u, 13f * u)
                lineTo(14f * u, 5.5f * u)
            }
            drawPath(coche, Color.White, style = Stroke(width = trait, cap = StrokeCap.Square))
        } else {
            drawRoundRect(bord, topLeft = Offset(trait / 2, trait / 2), size = Size(size.width - trait, size.height - trait), cornerRadius = coin, style = Stroke(trait))
        }
    }
}

/** La feuille de choix multiple de GeoMys-IOS : fond groupé élevé du mode sombre ; lignes de la liste
 *  groupée sur #2C2C2E, cases `checkmark.square.fill` vertes ou `square` grises de 20 pt. */
@Composable
private fun FeuilleChoixMultipleIos(
    visible: Boolean,
    titre: String,
    options: List<String>,
    courante: Set<Int>,
    basculer: (Int) -> Unit,
    onValider: () -> Unit,
    onFermer: () -> Unit,
) {
    FeuilleIos(visible, titre, onAnnuler = onFermer, onValider = { onValider(); onFermer() }) {
        Column(Modifier.fillMaxWidth().background(FOND_LIGNE_FEUILLE_IOS, FormeContinue(10.dp))) {
            LignesChoixMultipleIos(options, courante, basculer)
        }
    }
}

/** Fond groupé élevé du mode sombre : celui d'une feuille. */
private val FOND_FEUILLE_IOS = Color(0xFF1C1C1E)

/** Lignes d'une liste groupée dans une feuille (secondarySystemGroupedBackground élevé). */
internal val FOND_LIGNE_FEUILLE_IOS = Color(0xFF2C2C2E)

/** `.secondary` d'iOS en mode sombre (secondaryLabel). */
internal val SECONDAIRE_FEUILLE_IOS = Color(0x99EBEBF5)

/** Une feuille de GeoMys-IOS (`.sheet` et sa `NavigationStack`) : montée du bas sous la barre
 *  d'état, coins de 10 pt, barre de navigation (« Annuler » à gauche, titre demi-gras au centre,
 *  « Valider » à droite, jaunes clairs), puis [contenu] défilant, à 16 pt des bords, sur [fond].
 *  « Valider » ne ferme pas la feuille : l'écran la ferme si la saisie est acceptée. Toucher le
 *  voile l'annule, sauf si elle n'est pas [annulable] (`interactiveDismissDisabled`). */
@Composable
internal fun FeuilleIos(
    visible: Boolean,
    titre: String,
    onAnnuler: () -> Unit,
    onValider: () -> Unit,
    annulable: Boolean = true,
    fond: Brush = SolidColor(FOND_FEUILLE_IOS),
    contenu: @Composable ColumnScope.() -> Unit,
) {
    if (visible && annulable) RetourSysteme(onRetour = onAnnuler)
    AnimatedVisibility(visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (annulable) onAnnuler() },
        )
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(450, easing = PresentationIos)) { it },
        exit = slideOutVertically(tween(350, easing = PresentationIos)) { it },
    ) {
        val forme = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 10.dp)
                .clip(forme)
                .background(fond)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
                BoutonIos(onAnnuler, Modifier.align(Alignment.CenterStart), opaciteAppui = 0.3f) {
                    Text("Annuler", fontSize = 17.sp, color = CouleursGeoMys.jauneClair)
                }
                Text(
                    titre,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 72.dp),
                )
                BoutonIos(onValider, Modifier.align(Alignment.CenterEnd), opaciteAppui = 0.3f) {
                    Text("Valider", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = CouleursGeoMys.jauneClair)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                content = contenu,
            )
        }
    }
}

/** Une section d'un `Form` groupé de SwiftUI : son en-tête en capitales (13 pt, gris secondaire),
 *  puis ses lignes sur [fond] aux coins continus de 10 pt. */
@Composable
internal fun SectionIos(titre: String?, modifier: Modifier = Modifier, fond: Color = FOND_LIGNE_FEUILLE_IOS, contenu: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (titre != null) {
            Text(titre.uppercase(), fontSize = 13.sp, color = SECONDAIRE_FEUILLE_IOS, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp))
        }
        Column(Modifier.fillMaxWidth().background(fond, FormeContinue(10.dp)), content = contenu)
    }
}

/** Dialogue de saisie aux champs libres (« Détails de la station ») : un titre, un contenu
 *  défilant, « Annuler » et « Valider ». « Valider » ne ferme pas le dialogue : l'écran le ferme si
 *  la saisie est acceptée. [annulable] faux (`setCancelable(false)`) : ni le voile ni Retour ne
 *  le ferment.
 *  - Android : AlertDialog d'AppCompat dont la vue est fournie par l'écran (`setView` d'une
 *    ScrollView), comme [DialogueAlerte] ;
 *  - iOS : la feuille de GeoMys-IOS ([FeuilleIos]) sur le dégradé d'accueil. */
@Composable
fun DialogueFeuilleFormulaire(
    visible: Boolean,
    titre: String,
    onAnnuler: () -> Unit,
    onValider: () -> Unit,
    annulable: Boolean = true,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    if (estIos) {
        val degrade = Brush.linearGradient(listOf(CouleursGeoMys.fondHaut, CouleursGeoMys.fondBas), Offset.Zero, Offset.Infinite)
        FeuilleIos(visible, titre, onAnnuler, onValider, annulable, degrade) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp), content = contenu)
        }
        return
    }
    CoucheDialogueAndroid(visible, if (annulable) onAnnuler else ({})) {
        Text(
            titre,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp),
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = contenu)
        BarreBoutonsAndroid(
            listOf(BoutonAlerte("Annuler", annulation = true, action = onAnnuler), BoutonAlerte("Valider", action = onValider)),
            onFermer = {},
        )
    }
}

/** Les lignes de la liste groupée de [FeuilleChoixMultipleIos]. */
@Composable
private fun LignesChoixMultipleIos(options: List<String>, courante: Set<Int>, basculer: (Int) -> Unit) {
    options.forEachIndexed { i, option ->
        val cochee = i in courante
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { basculer(i) }
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymboleSf(
                if (cochee) "checkmark.square.fill" else "square",
                20f,
                listOf(if (cochee) CouleursGeoMys.vert else Color(0x99EBEBF5)),
            )
            Text(option, fontSize = 17.sp, color = Color.White)
        }
        if (i < options.lastIndex) SeparateurIos(Modifier.padding(start = 16.dp))
    }
}

/** Progression bloquante d'un envoi (« Envoi de la saisie vers GeoNature… ») : rien ne la ferme.
 *  - Android : AlertDialog d'AppCompat non annulable, sa vue en ligne (ProgressBar puis le texte à
 *    16 dp, marges de 20 dp) ;
 *  - iOS : voile sombre, grand indicateur et texte blancs, comme l'envoi de la saisie multi-taxons. */
@Composable
fun DialogueProgression(visible: Boolean, texte: String) {
    if (estIos) {
        AnimatedVisibility(visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IndicateurActivite(Color.White, grandIos = true)
                Text(texte, color = Color.White, fontSize = 16.sp)
            }
        }
        return
    }
    CoucheDialogueAndroid(visible, onFermer = {}) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IndicateurActivite(MaterialTheme.colorScheme.primary, tailleAndroid = 48f)
            Text(texte, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

/** Dialogue de formulaire (« Détails du relevé ») : un titre, un contenu défilant, « Annuler » et
 *  « Valider ». « Valider » ne ferme pas le dialogue : l'écran le ferme si la saisie est acceptée ;
 *  toucher le voile, Retour ou « Annuler » appellent [onAnnuler].
 *  - Android : MaterialAlertDialog de Material 3, le contenu ([contenu]) sans marge propre ;
 *  - iOS : `DialogueMaterial` de GeoMys-IOS, « Annuler » blanc 70 % et « Valider » jaune clair. */
@Composable
fun DialogueFormulaire(
    visible: Boolean,
    titre: String,
    onAnnuler: () -> Unit,
    onValider: () -> Unit,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    if (estIos) {
        DialogueMaterialIos(visible, titre, onAnnuler, boutons = {
            BoutonDialogueIos("Annuler", CouleursGeoMys.blanc70, onAnnuler)
            BoutonDialogueIos("Valider", CouleursGeoMys.jauneClair, onValider)
        }, contenu = contenu)
        return
    }
    if (visible) RetourSysteme(onRetour = onAnnuler)
    AnimatedVisibility(visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        // Fenêtre flottante : au moins 95 % de la largeur de l'écran (windowMinWidthMinor), dont la
        // carte est retirée de 24 dp à gauche et à droite, de 80 dp en haut et en bas
        // (backgroundInset*). Le clavier (adjustPan) réduit seulement la place disponible.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAnnuler)
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
        ) {
            val largeur = maxWidth * 0.95f - 48.dp
            val hauteurMax = maxHeight - 160.dp
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime), contentAlignment = Alignment.Center) {
                val forme = RoundedCornerShape(28.dp)
                Column(
                    Modifier
                        .animateEnterExit(enter = scaleIn(tween(150), initialScale = 0.9f), exit = scaleOut(tween(150), targetScale = 0.9f))
                        .width(largeur)
                        .heightIn(max = hauteurMax)
                        .shadow(6.dp, forme)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, forme)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    // Titre : HeadlineSmall (24 sp, interligne 32 sp), une ligne, 18 dp au-dessus.
                    Text(
                        titre,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp),
                    )
                    // customPanel (48 dp au moins) : la vue de l'écran défile, sans marge ajoutée.
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .heightIn(min = 48.dp)
                            .verticalScroll(rememberScrollState()),
                        content = contenu,
                    )
                    // Barre de boutons M3 : marges 24 × 14 dp, « Annuler » puis « Valider » à droite.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        BoutonTexteM3("Annuler", onAnnuler)
                        BoutonTexteM3("Valider", onValider)
                    }
                }
            }
        }
    }
}

/** `Widget.Material3.Button.TextButton.Dialog` : 64 × 48 dp au moins (40 dp visibles), marges de
 *  12 × 6 dp, LabelLarge colorPrimary. */
@Composable
private fun BoutonTexteM3(texte: String, onClick: () -> Unit) {
    val couleur = MaterialTheme.colorScheme.primary
    BoutonMaterial(
        onClick = onClick,
        marges = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        couleurContenu = couleur,
        largeurMin = 64.dp,
    ) { Text(texte, style = styleLibelleMaterial(couleur), maxLines = 1) }
}

/** `BoutonDialogue` de GeoMys-IOS : texte de 15 pt demi-gras. */
@Composable
internal fun BoutonDialogueIos(texte: String, teinte: Color, onClick: () -> Unit) {
    BoutonIos(onClick, opaciteAppui = 0.4f) {
        Text(texte, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = teinte)
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
 *  (texte colorPrimary sur une ligne, largeur minimale 64 dp). S'ils ne tiennent pas sur une ligne,
 *  ils s'empilent à droite, le positif en haut (ButtonBarLayout). Chaque bouton ferme le dialogue. */
@Composable
private fun BarreBoutonsAndroid(boutons: List<BoutonAlerte>, onFermer: () -> Unit) {
    val ordonnes = boutons.filter { it.annulation } + boutons.filterNot { it.annulation }
    Layout(
        content = {
            for (bouton in ordonnes) {
                BoutonMaterial(
                    onClick = {
                        bouton.action()
                        onFermer()
                    },
                    modifier = Modifier.widthIn(min = 64.dp),
                    marges = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    couleurContenu = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        bouton.texte,
                        style = styleLibelleMaterial(MaterialTheme.colorScheme.primary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) { mesurables, contraintes ->
        val largeur = contraintes.maxWidth
        val libres = contraintes.copy(minWidth = 0, minHeight = 0)
        val empiles = mesurables.sumOf { it.maxIntrinsicWidth(contraintes.maxHeight) } > largeur
        if (!empiles) {
            val places = mesurables.map { it.measure(libres) }
            val hauteur = places.maxOfOrNull { it.height } ?: 0
            layout(largeur, hauteur) {
                var x = largeur - places.sumOf { it.width }
                places.forEach {
                    it.place(x, (hauteur - it.height) / 2)
                    x += it.width
                }
            }
        } else {
            val places = mesurables.reversed().map { it.measure(libres) }
            layout(largeur, places.sumOf { it.height }) {
                var y = 0
                places.forEach {
                    it.place(largeur - it.width, y)
                    y += it.height
                }
            }
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
internal fun DialogueMaterialIos(
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
