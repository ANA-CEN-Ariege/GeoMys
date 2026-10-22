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

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable

/** Présente la feuille d'actions d'iOS (UIAlertController en `.actionSheet`, comme le
 *  `confirmationDialog` de SwiftUI) : un titre, un [message] éventuel, des boutons, « Annuler ».
 *  [onFermee] est appelé après chaque bouton. Sans effet sous Android, où les choix passent par des
 *  dialogues Compose. */
@Composable
expect fun FeuilleActionsSysteme(visible: Boolean, titre: String, boutons: List<BoutonAlerte>, onFermee: () -> Unit, message: String? = null)
