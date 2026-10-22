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

import androidx.compose.ui.graphics.ImageBitmap

/** Une image du disque réduite à une vignette, le côté long autour de [coteMaxPx] : une image de
 *  serveur peut faire plusieurs milliers de pixels, et décodée entière elle pèserait des dizaines
 *  de mégaoctets.
 *  - Android : `BitmapFactory` sous-échantillonné par puissances de deux (`chargerImagePicto` de
 *    SuivisFragment) ;
 *  - iOS : vignette de `CGImageSource` (`PictoCache.miniature` de GeoMys-IOS).
 *  Null si le fichier n'est pas une image lisible. À appeler hors du fil de l'interface. */
expect fun lireMiniature(chemin: String, coteMaxPx: Int): ImageBitmap?
