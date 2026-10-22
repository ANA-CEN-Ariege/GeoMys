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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/MediaImport.kt, PhotoCapture.kt et
// les sélecteurs de DenombrementFragment.kt). androidx.exifinterface devient android.media.ExifInterface
// (API 24, la version minimale).

package fr.ariegenature.geomys.ui.composants

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberAjoutMedias(dossier: String, onImportes: (List<String>) -> Unit, onErreur: (String) -> Unit): AjoutMedias {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    val importes by rememberUpdatedState(onImportes)
    val erreur by rememberUpdatedState(onErreur)
    // Fichier temporaire de la prise de vue : gardé si le processus meurt derrière l'appareil photo.
    var capture by rememberSaveable { mutableStateOf<String?>(null) }

    /** Importe et RECOMPRESSE hors du fil de l'interface (plusieurs photos le gèleraient), puis
     *  [apres] (suppression du fichier de prise de vue, une fois lu). */
    fun importer(uris: List<Uri>, mimeParDefaut: String, apres: () -> Unit = {}) {
        val appli = contexte.applicationContext
        portee.launch {
            val locales = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { i, u -> MediaImport.importer(appli, u, mimeParDefaut, i, File(dossier)) }
            }
            apres()
            if (locales.size < uris.size) erreur("Import média échoué")
            if (locales.isNotEmpty()) importes(locales)
        }
    }

    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) importer(uris, "image/jpeg")
    }
    val sons = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) importer(uris, "audio/mp4")
    }
    // FLAG_GRANT_WRITE_URI_PERMISSION : sans lui, l'appareil photo de certains constructeurs ne peut
    // pas écrire dans l'URI du FileProvider (SecurityException).
    val appareil = rememberLauncherForActivityResult(
        object : ActivityResultContracts.TakePicture() {
            override fun createIntent(context: Context, input: Uri): Intent =
                super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        },
    ) { succes ->
        val fichier = capture?.let(::File)
        capture = null
        if (succes && fichier != null) importer(listOf(PhotoCapture.uri(contexte, fichier)), "image/jpeg") { fichier.delete() }
        else fichier?.delete()
    }

    return remember(contexte) {
        object : AjoutMedias {
            override val appareilPhoto = true

            override fun prendrePhoto() {
                val fichier = PhotoCapture.nouvelleCible(contexte)
                capture = fichier.absolutePath
                try {
                    appareil.launch(PhotoCapture.uri(contexte, fichier))
                } catch (_: Exception) {
                    capture = null
                    fichier.delete()
                    erreur("Aucune application appareil photo")
                }
            }

            override fun choisirPhotos() = galerie.launch("image/*")

            override fun choisirSons() = sons.launch("audio/*")
        }
    }
}

/** Prise de vue par l'appareil photo du système : la photo est écrite en pleine résolution dans un
 *  fichier temporaire (`cacheDir/captures/`, couvert par file_provider_paths.xml), que l'import
 *  recompresse comme une photo de la galerie. */
private object PhotoCapture {
    fun nouvelleCible(contexte: Context): File {
        val dossier = File(contexte.cacheDir, "captures").apply { mkdirs() }
        return File(dossier, "capture_${System.currentTimeMillis()}.jpg")
    }

    fun uri(contexte: Context, fichier: File): Uri = FileProvider.getUriForFile(contexte, "${contexte.packageName}.provider", fichier)
}

/** Import d'un média choisi vers le dossier des médias : une copie locale, qui survit à l'URI
 *  temporaire du sélecteur. Les IMAGES sont recompressées (côté long de [COTE_MAX] px, orientation
 *  EXIF appliquée, JPEG [QUALITE_JPEG]) : une photo de 4 à 10 Mo devient 0,3 à 1 Mo, décisif pour
 *  l'envoi en zone blanche. Les autres types, et les images illisibles, sont copiés tels quels. */
private object MediaImport {
    private const val COTE_MAX = 2048
    private const val QUALITE_JPEG = 85

    /** L'URI `file:` de la copie locale de [source], ou null en échec. [index] départage les fichiers
     *  importés dans la même milliseconde. */
    fun importer(contexte: Context, source: Uri, mimeParDefaut: String, index: Int, dossier: File): String? {
        val mime = contexte.contentResolver.getType(source) ?: mimeParDefaut
        dossier.mkdirs()
        val horodatage = System.currentTimeMillis()
        return try {
            if (mime.startsWith("image/")) {
                comprimerImage(contexte, source, File(dossier, "photo_${horodatage}_$index.jpg"))?.let { return it }
                // Image illisible (format exotique) : copie brute ci-dessous.
            }
            val (prefixe, extension) = prefixeEtExtension(mime)
            val destination = File(dossier, "${prefixe}_${horodatage}_$index.$extension")
            contexte.contentResolver.openInputStream(source)?.use { entree ->
                destination.outputStream().use { sortie -> entree.copyTo(sortie) }
            } ?: return null
            destination.toURI().toString()
        } catch (e: Exception) {
            android.util.Log.w("MediaImport", "Import média échoué : $source", e)
            null
        }
    }

    private fun comprimerImage(contexte: Context, source: Uri, destination: File): String? {
        // 1) Dimensions seules, pour le sous-échantillonnage.
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contexte.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bornes) } ?: return null
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) return null
        var echantillon = 1
        while (maxOf(bornes.outWidth, bornes.outHeight) / (echantillon * 2) >= COTE_MAX) echantillon *= 2
        // 2) Décodage sous-échantillonné, puis redimensionnement exact.
        val options = BitmapFactory.Options().apply { inSampleSize = echantillon }
        var image = contexte.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val coteLong = maxOf(image.width, image.height)
        if (coteLong > COTE_MAX) {
            val ratio = COTE_MAX.toFloat() / coteLong
            val redim = Bitmap.createScaledBitmap(
                image,
                (image.width * ratio).toInt().coerceAtLeast(1),
                (image.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
            if (redim !== image) {
                image.recycle()
                image = redim
            }
        }
        // 3) Orientation EXIF : le réencodage perdrait le marqueur, on tourne les pixels.
        val rotation = runCatching {
            contexte.contentResolver.openInputStream(source)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (rotation != 0f) {
            val matrice = Matrix().apply { postRotate(rotation) }
            val pivotee = Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrice, true)
            if (pivotee !== image) {
                image.recycle()
                image = pivotee
            }
        }
        destination.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, QUALITE_JPEG, it) }
        image.recycle()
        return destination.toURI().toString()
    }

    /** Préfixe de nom et extension des médias copiés bruts. */
    private fun prefixeEtExtension(mime: String): Pair<String, String> = when {
        mime.startsWith("image/") -> "photo" to when (mime) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/heic", "image/heif" -> "heic"
            "image/webp" -> "webp"
            else -> mime.substringAfter("/").ifEmpty { "jpg" }
        }
        mime.startsWith("audio/") -> "audio" to when (mime) {
            "audio/mpeg" -> "mp3"
            "audio/mp4" -> "m4a"
            "audio/aac" -> "aac"
            "audio/ogg" -> "ogg"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> mime.substringAfter("/").ifEmpty { "mp3" }
        }
        else -> "media" to mime.substringAfter("/").ifEmpty { "bin" }
    }
}
