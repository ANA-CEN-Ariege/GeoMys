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

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable
import okio.Source

/** Ce qu'a donné le fichier choisi par l'utilisateur. */
sealed interface FichierChoisi {
    /** Le contenu entier du fichier. */
    class Lu(val octets: ByteArray) : FichierChoisi

    /** Rien de lisible, ou plus gros que la limite : il n'a pas été lu. */
    data object TropGrosOuIllisible : FichierChoisi

    /** La lecture a échoué ([message] de l'exception). */
    class Erreur(val message: String?) : FichierChoisi
}

/** Le sélecteur de fichiers du système, pour un fichier GPX : ACTION_OPEN_DOCUMENT (GPX, XML ou texte)
 *  sous Android, UIDocumentPickerViewController (GPX ou XML) sous iOS, comme le `.fileImporter` de
 *  GeoMys-IOS. Le fichier n'est lu que s'il ne dépasse pas [tailleMax] octets. Rien n'arrive si
 *  l'utilisateur annule. Rend la fonction qui ouvre le sélecteur. */
@Composable
expect fun rememberSelecteurGpx(tailleMax: Long, onChoisi: (FichierChoisi) -> Unit): () -> Unit

/** Un fichier choisi, à lire en flux : une carte MBTiles peut peser des centaines de Mo. [nom] : son nom
 *  d'origine, s'il est connu. */
class FluxChoisi(val source: Source, val nom: String?)

/** Le sélecteur de fichiers du système, pour une carte MBTiles : ACTION_OPEN_DOCUMENT de tout type sous
 *  Android (les .mbtiles n'ont pas de type MIME reconnu), UIDocumentPickerViewController de tout fichier
 *  sous iOS, copié dans la boîte de l'appli puis effacé une fois lu. [onChoisi] reçoit le flux, ou null si
 *  le fichier ne s'ouvre pas ; rien n'arrive si l'utilisateur annule. Rend la fonction qui ouvre le
 *  sélecteur. */
@Composable
expect fun rememberSelecteurCarte(onChoisi: (FluxChoisi?) -> Unit): () -> Unit
