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

// Propre au portage (demande du 23 octobre 2026) : l'encadré « Donnée exceptionnelle » du formulaire OccTax
// du web (profiles.component.html), sans équivalent dans GeoMys-Android ni GeoMys-IOS.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.occtax.TextesProfils
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** android.R.color.holo_orange_dark, celui de « Nom invalide » sous le champ espèce. */
private val ORANGE_ANDROID = Color(0xFFFF8800)

/** L'avertissement d'une observation que le serveur trouve inhabituelle ([messages] non vides) :
 *  « ⚠ Donnée exceptionnelle. Le taxon n'a jamais été observé… », précédé de l'[espece] quand elle
 *  n'est pas à côté. Rien s'il n'y a pas de [messages]. */
@Composable
fun AvertissementProfil(messages: List<String>, modifier: Modifier = Modifier, espece: String? = null) {
    if (messages.isEmpty()) return
    val couleur = if (estIos) CouleursGeoMys.orange else ORANGE_ANDROID
    Text(
        "⚠ " + (espece?.let { "$it — " } ?: "") + TextesProfils.MESSAGE,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = couleur,
        modifier = modifier,
    )
}
