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

// Propre au portage (demande du 24 octobre 2026) : le flou des matériaux de SwiftUI sous le menu de l'accueil.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FlouArrierePlanTest {

    /** Dessous : rouge en haut (100 pt), bleu en bas. Le menu, sans teinte, couvre la frontière de
     *  50 pt de part et d'autre. Juste au-dessus de la frontière, le flou mêle le bleu au rouge ; sans
     *  flou (ou mal placé), le rouge resterait pur. */
    @Test
    fun le_fond_du_menu_est_le_dessous_floute_a_sa_place() = runComposeUiTest {
        setContent {
            val flou = rememberEtatFlou()
            Box(Modifier.size(200.dp)) {
                Column(Modifier.fillMaxSize().sourceFlou(flou).background(Color.Blue)) {
                    Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Red))
                }
                Box(Modifier.offset(y = 50.dp).size(100.dp).testTag("menu").fondFlou(flou, RectangleShape, Color.Transparent, rayon = 12.dp))
            }
        }
        val image = onNodeWithTag("menu").captureToImage().toPixelMap()
        val parPt = image.height / 100f
        // 4 pt au-dessus de la frontière (46 pt dans le menu) : mélangé.
        val pres = image[image.width / 2, (46 * parPt).toInt()]
        assertTrue(pres.blue > 0.15f && pres.red > 0.4f, "rouge flouté de bleu attendu, reçu $pres")
        // Loin de la frontière (5 pt dans le menu) : rouge.
        val loin = image[image.width / 2, (5 * parPt).toInt()]
        assertTrue(loin.red > 0.9f && loin.blue < 0.1f, "rouge attendu, reçu $loin")
    }
}
