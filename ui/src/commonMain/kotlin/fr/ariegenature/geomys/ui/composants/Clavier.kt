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

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable

/** Clavier chiffres et ponctuation (`.numbersAndPunctuation` des champs nombre de GeoMys-IOS) : le
 *  signe moins et la virgule, que le pavé décimal d'iOS n'a pas. Sous Android, qui n'a pas ce
 *  clavier, le clavier texte. */
expect val ClavierNombresEtPonctuation: KeyboardOptions

/** Le drapeau `TYPE_NUMBER_FLAG_SIGNED` des champs nombre de GeoMys-Android, que les types de
 *  clavier de Compose ne posent pas : sans lui, des claviers Android n'offrent pas le signe moins.
 *  Il s'ajoute à chaque champ numérique de [contenu]. Sous iOS, rien. */
@Composable
expect fun ClavierNumeriqueSigne(contenu: @Composable () -> Unit)
