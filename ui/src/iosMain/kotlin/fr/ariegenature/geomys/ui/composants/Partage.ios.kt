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

// Repris de GeoMys-IOS (GeoMys-IOS/UI/SortieDetailView.swift, partagerGpx et ShareSheet) : fichier dans le
// dossier temporaire, feuille de partage du système.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import fr.ariegenature.geomys.journal.Journal
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPartageFichier(): (FichierAPartager) -> Unit {
    val controleur = LocalUIViewController.current
    return remember(controleur) {
        { fichier ->
            val chemin = NSTemporaryDirectory() + fichier.nom
            @Suppress("CAST_NEVER_SUCCEEDS")
            val ecrit = (fichier.contenu as NSString).writeToFile(chemin, atomically = true, encoding = NSUTF8StringEncoding, error = null)
            if (!ecrit) {
                Journal.w("Partage", "Écriture de ${fichier.nom} impossible")
            } else {
                val feuille = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(chemin)), applicationActivities = null)
                // Sur iPad, la feuille s'ouvre en bulle : il lui faut une origine.
                feuille.popoverPresentationController?.let { bulle ->
                    bulle.sourceView = controleur.view
                    controleur.view.bounds.useContents { bulle.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
                }
                var sommet = controleur
                while (sommet.presentedViewController != null) sommet = sommet.presentedViewController!!
                sommet.presentViewController(feuille, animated = true, completion = null)
            }
        }
    }
}
