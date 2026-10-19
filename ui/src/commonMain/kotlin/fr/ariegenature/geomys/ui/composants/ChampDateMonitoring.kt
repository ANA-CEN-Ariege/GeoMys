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

// Les champs DATE, TIME et DATETIME du formulaire des Suivis : creerChampDate, creerChampTime et
// creerChampDateTime du FormulaireRenderer de GeoMys-Android @ cdd5196 (TextView au contour bg_carte_contour,
// DatePickerDialog et TimePickerDialog), le DatePicker compact du FormulaireRendererView de GeoMys-IOS.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import fr.ariegenature.geomys.monitoring.form.SelectionDateHeure

/** Fond des lignes d'un `Form` SwiftUI du mode sombre (secondarySystemGroupedBackground) : celles
 *  du formulaire des Suivis sous iOS, et le fond des sélecteurs natifs qui y sont posés. */
internal val FOND_LIGNE_FORMULAIRE_IOS = Color(0xFF1C1C1E)

/** Ce que choisit un champ de date ou d'heure des Suivis. */
enum class ModeDateHeure(
    /** L'invite d'un champ encore vide. */
    val invite: String,
) {
    DATE("Choisir une date…"),
    HEURE("Choisir une heure…"),
    DATE_HEURE("Choisir date et heure…"),
}

/** Un champ de date ou d'heure : [affichage] est le texte de la valeur (null tant que rien n'est
 *  choisi), [initiale] ce sur quoi s'ouvrent les sélecteurs ; [onChoix] reçoit le choix.
 *  - Android : TextView de 16 sp aux marges de 8 × 12 dp, contour gris translucide de 1 dp aux coins
 *    de 8 dp, l'invite en couleur secondaire ; le toucher ouvre DatePickerDialog, TimePickerDialog
 *    (24 h), ou l'un puis l'autre ;
 *  - iOS : `UIDatePicker` compact (le `DatePicker` de SwiftUI) teinté jaune clair ; vide, l'invite
 *    d'Android dans la même pastille, que toucher remplit de maintenant. */
@Composable
expect fun ChampDateMonitoring(
    mode: ModeDateHeure,
    affichage: String?,
    initiale: SelectionDateHeure,
    onChoix: (SelectionDateHeure) -> Unit,
    modifier: Modifier = Modifier,
)
