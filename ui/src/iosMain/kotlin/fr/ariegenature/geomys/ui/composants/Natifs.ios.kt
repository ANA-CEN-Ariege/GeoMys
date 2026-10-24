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

@file:OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class, BetaInteropApi::class)

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.compose.resources.decodeToImageBitmap
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIActivityIndicatorView
import platform.UIKit.accessibilityLabel
import platform.UIKit.UIActivityIndicatorViewStyleLarge
import platform.UIKit.UIActivityIndicatorViewStyleMedium
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolWeightBold
import platform.UIKit.UIImageSymbolWeightMedium
import platform.UIKit.UIImageSymbolWeightRegular
import platform.UIKit.UIImageSymbolWeightSemibold
import platform.UIKit.UIScreen
import platform.UIKit.UISwitch
import platform.UIKit.UIVisualEffectView
import platform.darwin.NSObject
import platform.posix.memcpy

/** La couleur Compose en UIColor (sRGB). */
internal fun Color.versUIColor(): UIColor =
    UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())

// ─── Symboles SF ───

private data class CleSymbole(val nom: String, val taille: Float, val graisse: GraisseSymbole, val couleurs: List<Color>)

/** Un symbole rendu en image, et sa taille en points. */
private class SymboleRendu(val image: ImageBitmap, val largeur: Double, val hauteur: Double)

// Les symboles déjà rendus. Lus et écrits dans la composition, donc sur le fil principal.
private val symbolesRendus = HashMap<CleSymbole, SymboleRendu?>()

/** Rend le symbole comme le ferait `Image(systemName:)` : configuration de taille et de graisse,
 *  teinte (ou palette), puis dessin à l'échelle de l'écran pour un affichage pixel pour pixel. */
private fun rendreSymbole(cle: CleSymbole): SymboleRendu? {
    val poids = when (cle.graisse) {
        GraisseSymbole.NORMALE -> UIImageSymbolWeightRegular
        GraisseSymbole.MOYENNE -> UIImageSymbolWeightMedium
        GraisseSymbole.DEMI_GRASSE -> UIImageSymbolWeightSemibold
        GraisseSymbole.GRASSE -> UIImageSymbolWeightBold
    }
    var configuration = UIImageSymbolConfiguration.configurationWithPointSize(cle.taille.toDouble(), weight = poids)
    if (cle.couleurs.size > 1) {
        configuration = configuration.configurationByApplyingConfiguration(
            UIImageSymbolConfiguration.configurationWithPaletteColors(cle.couleurs.map { it.versUIColor() })
        )
    }
    val base = UIImage.systemImageNamed(cle.nom, withConfiguration = configuration) ?: return null
    val image = if (cle.couleurs.size == 1) {
        base.imageWithTintColor(cle.couleurs[0].versUIColor(), renderingMode = UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal)
    } else base
    val (largeur, hauteur) = image.size.useContents { width to height }
    val format = UIGraphicsImageRendererFormat.preferredFormat().apply {
        scale = UIScreen.mainScreen.scale
        opaque = false
    }
    val rendu = UIGraphicsImageRenderer(size = CGSizeMake(largeur, hauteur), format = format).imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, largeur, hauteur))
    }
    val png = UIImagePNGRepresentation(rendu) ?: return null
    val octets = ByteArray(png.length.toInt())
    if (octets.isNotEmpty()) octets.usePinned { memcpy(it.addressOf(0), png.bytes, png.length) }
    return SymboleRendu(octets.decodeToImageBitmap(), largeur, hauteur)
}

@Composable
actual fun SymboleSf(
    nom: String,
    taille: Float,
    couleurs: List<Color>,
    modifier: Modifier,
    graisse: GraisseSymbole,
    description: String?,
) {
    val cle = CleSymbole(nom, taille, graisse, couleurs)
    val rendu = remember(cle) { symbolesRendus.getOrPut(cle) { rendreSymbole(cle) } } ?: return
    Image(
        bitmap = rendu.image,
        contentDescription = description,
        modifier = modifier.size(rendu.largeur.dp, rendu.hauteur.dp),
    )
}

// ─── Interrupteur ───

/** Cible de l'action « valeur changée » de l'UISwitch : UIKit ne retient pas ses cibles, la
 *  composition la garde donc en vie. */
private class CibleInterrupteur(var onChange: (Boolean) -> Unit) : NSObject() {
    @ObjCAction
    fun basculer(sender: UISwitch) {
        onChange(sender.isOn())
    }
}

/** L'UISwitch de UIKit, comme le `Toggle` de SwiftUI, teinté du bleu primaire (`.tint(colorPrimary)`
 *  de GeoMys-IOS). Placé sous le canevas Compose (découpe transparente) : le dégradé de fond est
 *  repeint derrière par la vue SwiftUI hôte (iosApp), pour que ses coins arrondis restent justes.
 *  Sur une ligne de formulaire opaque, ce dégradé paraîtrait à travers la piste translucide de
 *  l'interrupteur éteint et dans ses coins : [fondIos], la couleur de la ligne, est alors peinte
 *  derrière lui. */
@Composable
actual fun Interrupteur(coche: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier, description: String?, teinteIos: Color, fondIos: Color) {
    val rappel by rememberUpdatedState(onChange)
    val cible = remember { CibleInterrupteur { rappel(it) } }
    // La taille où UIKit le dessine : sous iOS 26, 63 × 28 pt, quand sa taille intrinsèque annonce
    // 61 pt de large ; à 61, le bout de la piste et du bouton était rogné.
    val (largeur, hauteur) = remember { UISwitch().sizeThatFits(CGSizeMake(0.0, 0.0)).useContents { width to height } }
    UIKitView(
        factory = {
            UISwitch().apply {
                onTintColor = teinteIos.versUIColor()
                setOn(coche, animated = false)
                addTarget(cible, NSSelectorFromString("basculer:"), UIControlEventValueChanged)
                accessibilityLabel = description
            }
        },
        modifier = modifier.size(largeur.dp, hauteur.dp),
        update = { interrupteur ->
            if (fondIos.isSpecified) interrupteur.backgroundColor = fondIos.versUIColor()
            if (interrupteur.isOn() != coche) interrupteur.setOn(coche, animated = true)
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
    )
}

// ─── Indicateur d'activité ───

/** UIActivityIndicatorView « medium » (20 pt), celle du `ProgressView()` de SwiftUI ; réduite d'un
 *  cinquième pour `.controlSize(.small)`. Posée au-dessus du canevas : son fond reste transparent
 *  sur les boutons colorés. */
@Composable
actual fun IndicateurActivite(couleur: Color, modifier: Modifier, tailleAndroid: Float, petitIos: Boolean, grandIos: Boolean) {
    UIKitView(
        factory = {
            UIActivityIndicatorView(activityIndicatorStyle = if (grandIos) UIActivityIndicatorViewStyleLarge else UIActivityIndicatorViewStyleMedium).apply {
                color = couleur.versUIColor()
                if (petitIos) transform = CGAffineTransformMakeScale(0.8, 0.8)
                startAnimating()
            }
        },
        modifier = modifier.size(if (grandIos) 37.dp else 20.dp).semantics { contentDescription = "Chargement" },
        update = { it.color = couleur.versUIColor() },
        properties = UIKitInteropProperties(interactionMode = null, placedAsOverlay = true),
    )
}

// ─── Alerte système ───

/** UIAlertController, comme `.alert` de SwiftUI, boutons teintés du jaune de GeoMys-IOS
 *  (`.tint(jauneClair)` à la racine de l'appli). */
@Composable
actual fun AlerteSysteme(
    visible: Boolean,
    titre: String,
    message: String,
    boutons: List<BoutonAlerte>,
    onFermee: () -> Unit,
) {
    val controleur = LocalUIViewController.current
    val fermer by rememberUpdatedState(onFermee)
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        val alerte = UIAlertController.alertControllerWithTitle(titre, message, UIAlertControllerStyleAlert)
        for (bouton in boutons) {
            val style = when {
                bouton.annulation -> UIAlertActionStyleCancel
                bouton.destructif -> UIAlertActionStyleDestructive
                else -> UIAlertActionStyleDefault
            }
            alerte.addAction(UIAlertAction.actionWithTitle(bouton.texte, style) { _ ->
                bouton.action()
                fermer()
            })
        }
        alerte.view.tintColor = CouleursGeoMys.jauneClair.versUIColor()
        var sommet = controleur
        while (sommet.presentedViewController != null) sommet = sommet.presentedViewController!!
        sommet.presentViewController(alerte, animated = true, completion = null)
    }
}

// ─── Matériau ───

/** UIVisualEffectView au matériau ultra-fin du système (sombre : l'appli l'est, Info.plist), posée sous
 *  le canevas de Compose : elle floute la carte, vue native elle aussi. Elle ne prend pas les touchers,
 *  qui restent au bouton dessiné par-dessus. */
@Composable
actual fun MateriauUltraFin(modifier: Modifier, rayonCoins: Dp) {
    UIKitView(
        factory = {
            UIVisualEffectView(effect = UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemUltraThinMaterial)).apply {
                layer.cornerRadius = rayonCoins.value.toDouble()
                clipsToBounds = true
                userInteractionEnabled = false
            }
        },
        modifier = modifier,
        properties = UIKitInteropProperties(interactionMode = null),
    )
}
