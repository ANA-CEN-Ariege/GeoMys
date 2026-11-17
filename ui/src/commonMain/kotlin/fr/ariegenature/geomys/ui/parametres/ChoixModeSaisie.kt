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

// Propre au portage : le choix du mode de saisie, en tête des Paramètres (demandes du 14 novembre 2026 :
// d'abord dans une page « Personnalisation » du menu, puis déplacé ici). Rien n'y dépend du serveur.

package fr.ariegenature.geomys.ui.parametres

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.store.ModeSaisie
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** Les choix du mode de saisie : libellé et explication. */
private val MODES = listOf(
    Triple(ModeSaisie.PRO, "Pro", "Tous les modules et tous les champs de GeoNature."),
    // Texte demandé le 16 novembre 2026 (il détaillait les boutons de la fiche, disparus depuis).
    Triple(ModeSaisie.SIMPLIFIE, "Simplifié", "Saisie multi-taxons seulement, allégée."),
)

/** Le contenu de la carte « Mode de saisie » : un choix par mode. */
@Composable
internal fun ChoixModeSaisie(mode: ModeSaisie, onChoix: (ModeSaisie) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(if (estIos) 14.dp else 4.dp)) {
        for ((valeur, libelle, explication) in MODES) {
            ChoixMode(libelle, explication, mode == valeur) { onChoix(valeur) }
        }
    }
}

/** Un mode : bouton radio (Material sous Android, « largecircle.fill.circle » sous iOS), libellé et
 *  explication. Toute la ligne se touche. */
@Composable
private fun ChoixMode(libelle: String, explication: String, choisi: Boolean, onChoix: () -> Unit) {
    val ios = estIos
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onChoix),
        horizontalArrangement = Arrangement.spacedBy(if (ios) 10.dp else 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (ios) {
            SymboleSf(
                if (choisi) "largecircle.fill.circle" else "circle", 20f,
                listOf(if (choisi) CouleursGeoMys.vert else CouleursGeoMys.blanc70),
                description = if (choisi) "$libelle, choisi" else libelle,
            )
        } else {
            RadioButton(
                selected = choisi,
                onClick = onChoix,
                colors = RadioButtonDefaults.colors(selectedColor = CouleursGeoMys.orange, unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        Column(Modifier.padding(top = if (ios) 0.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(libelle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (ios) Color.White else Color.Unspecified)
            Text(explication, fontSize = 13.sp, color = if (ios) CouleursGeoMys.blanc70 else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
