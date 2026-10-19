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

// Comme `PictoCache.miniature` de GeoMys-IOS, une vignette décodée à sa taille, ici par
// `imageByPreparingThumbnailOfSize` (`preparingThumbnail(of:)`, iOS 15 et plus), puis passée à Compose en PNG.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.compose.resources.decodeToImageBitmap
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun lireMiniature(chemin: String, coteMaxPx: Int): ImageBitmap? = runCatching {
    val image = UIImage.imageWithContentsOfFile(chemin) ?: return null
    val (largeur, hauteur) = image.size.useContents { width * image.scale to height * image.scale }
    if (largeur <= 0.0 || hauteur <= 0.0) return null
    val facteur = minOf(1.0, coteMaxPx / maxOf(largeur, hauteur))
    val vignette = image.imageByPreparingThumbnailOfSize(CGSizeMake(largeur * facteur, hauteur * facteur)) ?: return null
    val png = UIImagePNGRepresentation(vignette) ?: return null
    val octets = ByteArray(png.length.toInt())
    if (octets.isNotEmpty()) octets.usePinned { memcpy(it.addressOf(0), png.bytes, png.length) }
    octets.decodeToImageBitmap()
}.getOrNull()
