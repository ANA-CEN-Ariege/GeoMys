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

// L'ajout de médias à un dénombrement : MediaImport et PhotoCapture de GeoMys-Android @ cdd5196, MediaPickerView
// de GeoMys-IOS. Chaque plateforme garde ses sélecteurs (appareil photo, galerie, fichiers son) ; les photos
// sont recompressées pareil (2048 px au plus, JPEG 85 %) et copiées dans le dossier des médias.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable

/** Les sources d'un média. */
interface AjoutMedias {
    /** L'appareil photo existe (faux sur le simulateur iOS, où la galerie s'ouvre directement). */
    val appareilPhoto: Boolean

    fun prendrePhoto()

    /** Choix de photos dans la galerie, plusieurs à la fois. */
    fun choisirPhotos()

    /** Choix de fichiers son, plusieurs à la fois. */
    fun choisirSons()
}

/** Ajout de médias copiés dans [dossier] : [onImportes] reçoit leurs URI `file:` locales, [onErreur]
 *  le message d'un import manqué. */
@Composable
expect fun rememberAjoutMedias(dossier: String, onImportes: (List<String>) -> Unit, onErreur: (String) -> Unit): AjoutMedias

/** Nom de fichier d'un média (fin de son URI), tel que la liste l'affiche. */
fun nomMedia(uri: String): String = uri.substringBefore('?').substringAfterLast('/')
