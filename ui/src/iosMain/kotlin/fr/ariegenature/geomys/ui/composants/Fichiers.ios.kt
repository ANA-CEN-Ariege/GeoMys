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

// Repris de GeoMys-IOS (GeoMys-IOS/UI/PlaceholderViews.swift, SortiesView : `.fileImporter` et importerGpx) :
// mêmes types de fichiers, taille vérifiée avant lecture.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import okio.FileSystem
import okio.ForwardingSource
import okio.Path.Companion.toPath
import okio.SYSTEM
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeXML
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
actual fun rememberSelecteurGpx(tailleMax: Long, onChoisi: (FichierChoisi) -> Unit): () -> Unit {
    val controleur = LocalUIViewController.current
    val rappel by rememberUpdatedState(onChoisi)
    // Le sélecteur ne garde qu'une référence faible sur son délégué.
    val delegue = remember { DelegueSelecteur(tailleMax) { rappel(it) } }
    return {
        val types = listOfNotNull(UTType.typeWithFilenameExtension("gpx"), UTTypeXML)
        // Copie dans la boîte de l'appli : pas d'accès sécurisé à ouvrir puis refermer.
        val selecteur = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        selecteur.delegate = delegue
        selecteur.allowsMultipleSelection = false
        var sommet = controleur
        while (sommet.presentedViewController != null) sommet = sommet.presentedViewController!!
        sommet.presentViewController(selecteur, animated = true, completion = null)
    }
}

private class DelegueSelecteur(
    private val tailleMax: Long,
    private val surFichier: (FichierChoisi) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        // Le fichier serait lu ENTIÈREMENT en mémoire : au-delà de la limite, refus avant lecture.
        val taille = url.path?.let { chemin ->
            (NSFileManager.defaultManager.attributesOfItemAtPath(chemin, null)?.get(NSFileSize) as? NSNumber)?.longLongValue
        } ?: 0L
        if (taille > tailleMax) {
            surFichier(FichierChoisi.TropGrosOuIllisible)
            return
        }
        val donnees = NSData.dataWithContentsOfURL(url)
        surFichier(if (donnees == null) FichierChoisi.TropGrosOuIllisible else FichierChoisi.Lu(donnees.versOctets()))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {}
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.versOctets(): ByteArray {
    val octets = ByteArray(length.toInt())
    if (octets.isNotEmpty()) octets.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return octets
}

/** Repris de GeoMys-IOS (CartesMbtilesView, `.fileImporter`) : tout fichier, copié dans la boîte de
 *  l'appli, lu en flux ; la copie est effacée quand le flux se ferme. */
@Composable
actual fun rememberSelecteurCarte(onChoisi: (FluxChoisi?) -> Unit): () -> Unit {
    val controleur = LocalUIViewController.current
    val rappel by rememberUpdatedState(onChoisi)
    // Le sélecteur ne garde qu'une référence faible sur son délégué.
    val delegue = remember { DelegueSelecteurCarte { rappel(it) } }
    return {
        val selecteur = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
        selecteur.delegate = delegue
        selecteur.allowsMultipleSelection = false
        var sommet = controleur
        while (sommet.presentedViewController != null) sommet = sommet.presentedViewController!!
        sommet.presentViewController(selecteur, animated = true, completion = null)
    }
}

private class DelegueSelecteurCarte(private val surFichier: (FluxChoisi?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val chemin = url.path?.toPath()
        val source = chemin?.let { runCatching { FileSystem.SYSTEM.source(it) }.getOrNull() }
        surFichier(
            source?.let {
                val copie = object : ForwardingSource(it) {
                    override fun close() {
                        super.close()
                        runCatching { FileSystem.SYSTEM.delete(chemin) }
                    }
                }
                FluxChoisi(copie, url.lastPathComponent)
            },
        )
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {}
}
