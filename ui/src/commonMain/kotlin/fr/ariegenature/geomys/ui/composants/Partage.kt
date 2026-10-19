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

import androidx.compose.runtime.Composable

/** Un fichier texte à partager : son nom, son contenu, son type MIME, et le titre du sélecteur
 *  d'applications (Android). */
class FichierAPartager(val nom: String, val contenu: String, val typeMime: String, val titre: String)

/** Partage d'un fichier par le système : écrit dans le cache de l'appli puis proposé par
 *  ACTION_SEND et son sélecteur (FileProvider `<appli>.provider`) sous Android, par la feuille de
 *  partage (UIActivityViewController) sous iOS. Rend la fonction qui partage. */
@Composable
expect fun rememberPartageFichier(): (FichierAPartager) -> Unit
