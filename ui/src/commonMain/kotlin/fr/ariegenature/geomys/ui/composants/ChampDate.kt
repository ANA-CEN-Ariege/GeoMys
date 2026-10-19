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

// Le jour et l'heure d'un relevé : les cases et les DatePickerDialog / TimePickerDialog du dialogue « Détails
// du relevé » de GeoMys-Android, le DatePicker compact de DetailsReleveView (GeoMys-IOS).

package fr.ariegenature.geomys.ui.composants

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** Le jour (et l'heure si [heures]) de [millis], choisis par les sélecteurs du système, jamais dans
 *  le futur ; [onChange] reçoit le nouvel instant.
 *  - Android : cases au contour gris de 6 dp (« dd/MM/yyyy », « HH:mm », 15 sp), qui ouvrent
 *    DatePickerDialog et TimePickerDialog (24 h) ;
 *  - iOS : UIDatePicker compact (le `DatePicker` de SwiftUI), sur toute la largeur offerte (dans une
 *    rangée : `Modifier.weight`), ses pastilles à gauche, sur [fondIos] : la vue native est sous
 *    la toile de Compose, qui la laisse voir à travers une découpe ; sans fond, c'est celui de la
 *    fenêtre qui paraîtrait autour des pastilles. */
@Composable
expect fun ChampDateHeure(
    millis: Long,
    heures: Boolean,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    fondIos: Color = CouleursGeoMys.fondBas,
)

/** Android : une case du dialogue « Détails du relevé » (jour, heure, « ＋ » de la date de fin) :
 *  TextView de 15 sp, marges de 10 × 8 dp, contour de 1 dp colorOnSurfaceVariant aux coins de
 *  6 dp, sans effet au toucher ; sans contour pour le « ✕ » ([contour] faux). */
@Composable
internal fun CaseDate(
    texte: String,
    modifier: Modifier = Modifier,
    couleur: Color = Color.Unspecified,
    contour: Boolean = true,
    description: String? = null,
    onClick: () -> Unit,
) {
    val bord = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        texte,
        fontSize = 15.sp,
        color = couleur,
        modifier = modifier
            .then(if (contour) Modifier.border(1.dp, bord, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = description,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
