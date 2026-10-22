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

// Repris de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SortieDetailFragment.kt,
// exporterGpx).

package fr.ariegenature.geomys.ui.composants

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberPartageFichier(): (FichierAPartager) -> Unit {
    val contexte = LocalContext.current
    return remember(contexte) {
        { fichier ->
            val file = File(contexte.cacheDir, fichier.nom)
            file.writeText(fichier.contenu)
            val uri = FileProvider.getUriForFile(contexte, "${contexte.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = fichier.typeMime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            contexte.startActivity(Intent.createChooser(intent, fichier.titre))
        }
    }
}
