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

package fr.ariegenature.geomys.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** Allure des écrans. Les écrans sont communs ; leurs composants (ui/composants) prennent l'allure
 *  de l'appli que les utilisateurs connaissent sur leur téléphone :
 *  - [ANDROID] : celle de GeoMys-Android (layouts XML, Material Components) ;
 *  - [IOS] : celle de GeoMys-IOS (SwiftUI : police système, symboles SF, contrôles natifs).
 *  Le contenu des écrans, leurs textes et leurs comportements suivent GeoMys-Android sur les deux. */
enum class Apparence { ANDROID, IOS }

/** L'allure de la plateforme courante. */
expect val apparencePlateforme: Apparence

/** L'allure en vigueur dans la composition, posée par [GeoMysTheme]. */
val LocalApparence = staticCompositionLocalOf { apparencePlateforme }
