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

// Les dialogues du Maps Manager de GeoMys-Android @ cdd5196 (ui/CacheManagerFragment.kt) : AlertDialog du
// système, liste des cartes MBTiles (`gererCartesHorsLigne`) et `DialogueProgression`, construits comme là-bas.

package fr.ariegenature.geomys.ui.composants

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_delete
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun DialogueSystemeAndroid(
    visible: Boolean,
    titre: String,
    onFermer: () -> Unit,
    message: String?,
    elements: List<String>,
    onElement: (Int) -> Unit,
    lignes: List<LigneCorbeille>?,
    onCorbeille: (Int) -> Unit,
    progression: Progression?,
    positif: BoutonAlerte?,
    negatif: BoutonAlerte?,
    annulable: Boolean,
) {
    if (!visible) return
    val contexte = LocalContext.current
    val cycle = LocalLifecycleOwner.current
    val sauvegarde = LocalSavedStateRegistryOwner.current
    val densite = LocalDensity.current
    val couleurs = MaterialTheme.colorScheme
    val corbeille = painterResource(Res.drawable.ic_delete)
    val fermer by rememberUpdatedState(onFermer)
    val element by rememberUpdatedState(onElement)
    val supprimer by rememberUpdatedState(onCorbeille)
    val actionPositive by rememberUpdatedState(positif?.action)
    val actionNegative by rememberUpdatedState(negatif?.action)
    // Une progression change sans refaire le dialogue : son message et son avancement.
    val avancement = remember { mutableStateOf(progression?.avancement) }
    val vueMessage = remember { arrayOfNulls<TextView>(1) }
    SideEffect {
        avancement.value = progression?.avancement
        if (progression != null) vueMessage[0]?.text = message
    }
    val cleMessage = if (progression == null) message else null
    DisposableEffect(titre, cleMessage, elements, lignes, progression != null, positif?.texte, negatif?.texte, annulable) {
        val dp = { v: Int -> (v * densite.density).roundToInt() }
        var parNous = false
        lateinit var dialogue: AlertDialog
        val constructeur = AlertDialog.Builder(contexte).setTitle(titre).setCancelable(annulable)
        when {
            progression != null -> {
                // DialogueProgression : le message (15 sp), puis la barre, à 20 dp des bords.
                val vue = LinearLayout(contexte).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(12), dp(20), 0)
                    addView(TextView(contexte).apply {
                        text = message
                        textSize = 15f
                        vueMessage[0] = this
                    })
                    addView(
                        ComposeView(contexte).apply {
                            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                            setContent {
                                val valeur = avancement.value
                                if (valeur == null) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = couleurs.primary, trackColor = couleurs.secondaryContainer)
                                } else {
                                    LinearProgressIndicator({ valeur }, Modifier.fillMaxWidth(), color = couleurs.primary, trackColor = couleurs.secondaryContainer)
                                }
                            }
                        },
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) },
                    )
                }
                constructeur.setView(vue)
            }
            lignes != null && lignes.isNotEmpty() -> {
                // Une ligne par carte (nom, zooms et taille), sa corbeille rouge à droite.
                val image = corbeille.enBitmap(dp(24), densite, couleurs.error)
                val conteneur = LinearLayout(contexte).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(8), dp(8), dp(8))
                }
                lignes.forEachIndexed { i, ligne ->
                    conteneur.addView(LinearLayout(contexte).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(6), 0, dp(6))
                        addView(TextView(contexte).apply {
                            text = ligne.texte
                            textSize = 15f
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(ImageButton(contexte).apply {
                            setImageDrawable(BitmapDrawable(contexte.resources, image))
                            background = null
                            contentDescription = ligne.descriptionCorbeille
                            val marge = dp(10)
                            setPadding(marge, marge, marge, marge)
                            setOnClickListener {
                                dialogue.dismiss()
                                supprimer(i)
                            }
                        })
                    })
                }
                constructeur.setView(ScrollView(contexte).apply { addView(conteneur) })
            }
            elements.isNotEmpty() -> constructeur.setItems(elements.toTypedArray()) { _, i -> element(i) }
            message != null -> constructeur.setMessage(message)
        }
        positif?.let { constructeur.setPositiveButton(it.texte) { _, _ -> actionPositive?.invoke() } }
        negatif?.let { constructeur.setNegativeButton(it.texte) { _, _ -> actionNegative?.invoke() } }
        dialogue = constructeur.create()
        // La barre est une vue Compose : elle trouve son cycle de vie dans la fenêtre du dialogue.
        dialogue.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(cycle)
            decor.setViewTreeSavedStateRegistryOwner(sauvegarde)
        }
        dialogue.setOnDismissListener { if (!parNous) fermer() }
        dialogue.show()
        onDispose {
            parNous = true
            vueMessage[0] = null
            dialogue.dismiss()
        }
    }
}

/** L'icône dessinée dans une image de [cote] pixels, teintée de [teinte] (`setColorFilter`). */
private fun Painter.enBitmap(cote: Int, densite: Density, teinte: Color): Bitmap {
    val image = ImageBitmap(cote, cote)
    CanvasDrawScope().draw(densite, LayoutDirection.Ltr, Canvas(image), Size(cote.toFloat(), cote.toFloat())) {
        with(this@enBitmap) { draw(size, colorFilter = ColorFilter.tint(teinte)) }
    }
    return image.asAndroidBitmap()
}
