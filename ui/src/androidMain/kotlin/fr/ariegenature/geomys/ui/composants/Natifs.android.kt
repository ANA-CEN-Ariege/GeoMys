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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Pas de symboles SF sous Android : les écrans y affichent les drawables de GeoMys-Android. */
@Composable
actual fun SymboleSf(
    nom: String,
    taille: Float,
    couleurs: List<Color>,
    modifier: Modifier,
    graisse: GraisseSymbole,
    description: String?,
) = Unit

/** MaterialSwitch de Material Components 1.12 : le Switch Material 3 de Compose suit la même
 *  spécification (piste 52 × 32 dp, couleurs primary / outline du thème). */
@Composable
actual fun Interrupteur(coche: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier, description: String?) {
    Switch(
        checked = coche,
        onCheckedChange = onChange,
        modifier = if (description != null) modifier.semantics { contentDescription = description } else modifier,
    )
}

/** ProgressBar indéterminée du thème Material : un arc de 4/48 de la boîte, teinté
 *  colorControlActivated (l'orange de GeoMys en mode nuit), extrémités carrées. */
@Composable
actual fun IndicateurActivite(couleur: Color, modifier: Modifier, tailleAndroid: Float, petitIos: Boolean) {
    Box(modifier.size(tailleAndroid.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size((tailleAndroid * 42f / 48f).dp),
            color = couleur,
            strokeWidth = (tailleAndroid * 4f / 48f).dp,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Square,
        )
    }
}

/** Sous Android, les alertes sont des dialogues Compose (DialogueAlerte). */
@Composable
actual fun AlerteSysteme(
    visible: Boolean,
    titre: String,
    message: String,
    boutons: List<BoutonAlerte>,
    onFermee: () -> Unit,
) = Unit
