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

// La dictée vocale du champ espèce : SpeechToTextHelper de GeoMys-Android (SpeechRecognizer), DicteeVocale de
// GeoMys-IOS (SFSpeechRecognizer). Chaque plateforme garde sa reconnaissance et ses messages d'erreur ;
// ce qu'on fait des hypothèses est commun.

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable

/** La dictée vocale française d'un champ. */
interface Dictee {
    /** L'écoute est en cours. */
    val enCours: Boolean

    /** Le micro est touché : demande les autorisations au besoin, puis écoute. Sous Android, une
     *  écoute en cours est relancée ; sous iOS, elle s'arrête. [vocabulaire] : les noms attendus,
     *  soufflés à la reconnaissance d'iOS. */
    fun basculer(vocabulaire: List<String>)

    /** Arrête l'écoute : une espèce vient d'être choisie à la main. */
    fun arreter()
}

/** La dictée d'un écran. [onPartiel] reçoit la transcription en cours, [onFinal] les hypothèses
 *  finales (jusqu'à 5, la meilleure d'abord), [onErreur] un message à afficher en toast ([longue] :
 *  toast long). */
@Composable
expect fun rememberDictee(
    onPartiel: (String) -> Unit,
    onFinal: (List<String>) -> Unit,
    onErreur: (message: String, longue: Boolean) -> Unit,
): Dictee
