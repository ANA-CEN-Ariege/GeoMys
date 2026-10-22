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

// Porté de GeoMys-IOS (UI/Saisie/MediaPickerView.swift) : appareil photo par UIImagePickerController, galerie par
// PHPickerViewController (le `PhotosPicker` de SwiftUI), sons par UIDocumentPickerViewController (le
// `.fileImporter`), photos recompressées comme CompressionPhoto.

@file:OptIn(ExperimentalForeignApi::class)

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Côté long maximal d'une photo importée, en pixels (COTE_MAX de MediaImport). */
private const val COTE_MAX_PX = 2048.0

@Composable
actual fun rememberAjoutMedias(dossier: String, onImportes: (List<String>) -> Unit, onErreur: (String) -> Unit): AjoutMedias {
    val controleur = LocalUIViewController.current
    val importes by rememberUpdatedState(onImportes)
    val erreur by rememberUpdatedState(onErreur)
    return remember(controleur) {
        AjoutMediasIos(controleur, dossier, { importes(it) }, { erreur(it) })
    }
}

private class AjoutMediasIos(
    private val controleur: UIViewController,
    private val dossier: String,
    private val onImportes: (List<String>) -> Unit,
    private val onErreur: (String) -> Unit,
) : AjoutMedias {
    // Les sélecteurs ne gardent qu'une référence faible sur leur délégué.
    private val delegueAppareil = DelegueAppareil()
    private val delegueGalerie = DelegueGalerie()
    private val delegueSons = DelegueSons()

    override val appareilPhoto: Boolean =
        UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

    override fun prendrePhoto() {
        val selecteur = UIImagePickerController()
        selecteur.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        selecteur.delegate = delegueAppareil
        presenter(selecteur)
    }

    override fun choisirPhotos() {
        val configuration = PHPickerConfiguration()
        configuration.filter = PHPickerFilter.imagesFilter
        configuration.selectionLimit = 0
        val selecteur = PHPickerViewController(configuration)
        selecteur.delegate = delegueGalerie
        presenter(selecteur)
    }

    override fun choisirSons() {
        // Copie dans la boîte de l'appli : pas d'accès sécurisé à ouvrir puis refermer.
        val selecteur = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeAudio), asCopy = true)
        selecteur.allowsMultipleSelection = true
        selecteur.delegate = delegueSons
        presenter(selecteur)
    }

    private fun presenter(vue: UIViewController) {
        var sommet = controleur
        while (sommet.presentedViewController != null) sommet = sommet.presentedViewController!!
        sommet.presentViewController(vue, animated = true, completion = null)
    }

    private fun horodatage(): Long = Clock.System.now().toEpochMilliseconds()

    /** Écrit [donnees] dans le dossier des médias ; rend son URI `file:`, ou null. */
    private fun ecrire(donnees: NSData, nom: String): String? {
        NSFileManager.defaultManager.createDirectoryAtPath(dossier, withIntermediateDirectories = true, attributes = null, error = null)
        val chemin = "$dossier/$nom"
        if (!donnees.writeToFile(chemin, atomically = true)) {
            dispatch_async(dispatch_get_main_queue()) { onErreur("Écriture impossible : $nom") }
            return null
        }
        return NSURL.fileURLWithPath(chemin).absoluteString
    }

    /** Ramène le côté long à 2048 px au plus (en pixels, à l'échelle 1 : celle de l'écran sortirait
     *  des photos de 4096 à 6144 px), redresse l'orientation, réencode en JPEG 85 %. */
    private fun photoCompressee(image: UIImage, index: Int): String? {
        val (largeur, hauteur) = image.size.useContents { width * image.scale to height * image.scale }
        val actuel = max(largeur, hauteur)
        val finale = if (actuel <= COTE_MAX_PX) image else {
            val ratio = COTE_MAX_PX / actuel
            val l = (largeur * ratio).roundToLong().toDouble()
            val h = (hauteur * ratio).roundToLong().toDouble()
            val format = UIGraphicsImageRendererFormat.defaultFormat()
            format.scale = 1.0
            UIGraphicsImageRenderer(size = CGSizeMake(l, h), format = format).imageWithActions { _ ->
                image.drawInRect(CGRectMake(0.0, 0.0, l, h))
            }
        }
        val jpeg = UIImageJPEGRepresentation(finale, 0.85) ?: return null
        return ecrire(jpeg, "photo_${horodatage()}_$index.jpg")
    }

    private inner class DelegueAppareil : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
        override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
            picker.dismissViewControllerAnimated(true, completion = null)
            image?.let(::traiter)
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true, completion = null)
        }

        private fun traiter(image: UIImage) {
            photoCompressee(image, 0)?.let { onImportes(listOf(it)) }
        }
    }

    private inner class DelegueGalerie : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, completion = null)
            val resultats = didFinishPicking.filterIsInstance<PHPickerResult>()
            if (resultats.isEmpty()) return
            val uris = arrayOfNulls<String>(resultats.size)
            var restants = resultats.size
            resultats.forEachIndexed { i, resultat ->
                resultat.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { donnees, _ ->
                    val uri = donnees?.let { UIImage(data = it) }?.let { photoCompressee(it, i) }
                    dispatch_async(dispatch_get_main_queue()) {
                        uris[i] = uri
                        restants -= 1
                        // Dans l'ordre du choix, une fois toutes lues.
                        if (restants == 0) uris.filterNotNull().takeIf { it.isNotEmpty() }?.let(onImportes)
                    }
                }
            }
        }
    }

    private inner class DelegueSons : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            val importes = didPickDocumentsAtURLs.filterIsInstance<NSURL>().mapIndexedNotNull { i, url ->
                val extension = url.pathExtension?.lowercase()?.takeIf { it.isNotEmpty() } ?: "m4a"
                val donnees = url.path?.let { NSData.dataWithContentsOfFile(it) } ?: return@mapIndexedNotNull null
                ecrire(donnees, "audio_${horodatage()}_$i.$extension")
            }
            if (importes.isNotEmpty()) onImportes(importes)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {}
    }
}
