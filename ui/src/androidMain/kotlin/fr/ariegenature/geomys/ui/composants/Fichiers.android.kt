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

// Repris de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SortiesFragment.kt,
// lancerImport et importLauncher, et gpx/GpxUtils.kt, lireBorne).

package fr.ariegenature.geomys.ui.composants

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberSelecteurGpx(tailleMax: Long, onChoisi: (FichierChoisi) -> Unit): () -> Unit {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    val rappel by rememberUpdatedState(onChoisi)
    val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        portee.launch {
            val resultat = withContext(Dispatchers.IO) {
                try {
                    // Lecture BORNÉE : un fichier énorme ne doit pas faire tomber l'appli.
                    val octets = contexte.contentResolver.openInputStream(uri)?.use { lireBorne(it, tailleMax) }
                    if (octets == null) FichierChoisi.TropGrosOuIllisible else FichierChoisi.Lu(octets)
                } catch (e: Exception) {
                    FichierChoisi.Erreur(e.message)
                }
            }
            rappel(resultat)
        }
    }
    return { lanceur.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "text/plain")) }
}

/** Lit [input] jusqu'à [max] octets ; renvoie null si le flux dépasse cette borne (fichier énorme
 *  reçu par mail → OOM évité, audit 2026-08-27). */
private fun lireBorne(input: InputStream, max: Long): ByteArray? {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > max) return null
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
