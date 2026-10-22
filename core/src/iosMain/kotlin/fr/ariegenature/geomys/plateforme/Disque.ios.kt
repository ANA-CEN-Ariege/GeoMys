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

package fr.ariegenature.geomys.plateforme

import kotlinx.cinterop.ExperimentalForeignApi
import okio.IOException
import okio.Path
import platform.posix.F_FULLFSYNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.fsync
import platform.posix.open

@OptIn(ExperimentalForeignApi::class)
actual fun synchroniserSurDisque(chemin: Path) {
    val descripteur = open(chemin.toString(), O_WRONLY)
    if (descripteur < 0) {
        throw IOException("open($chemin) : errno $errno")
    }
    try {
        // Sur les systèmes d'Apple, fsync peut s'arrêter au cache du disque : F_FULLFSYNC va
        // jusqu'au support. fsync seulement si le système de fichiers ne le connaît pas.
        if (fcntl(descripteur, F_FULLFSYNC) == -1 && fsync(descripteur) == -1) {
            throw IOException("fsync($chemin) : errno $errno")
        }
    } finally {
        close(descripteur)
    }
}
