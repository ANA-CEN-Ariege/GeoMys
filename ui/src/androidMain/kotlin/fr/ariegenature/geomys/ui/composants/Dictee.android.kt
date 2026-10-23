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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/saisie/SpeechToTextHelper.kt).

package fr.ariegenature.geomys.ui.composants

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberDictee(
    onPartiel: (String) -> Unit,
    onFinal: (List<String>) -> Unit,
    onErreur: (message: String, longue: Boolean) -> Unit,
): Dictee {
    val contexte = LocalContext.current
    val partiel by rememberUpdatedState(onPartiel)
    val final by rememberUpdatedState(onFinal)
    val erreur by rememberUpdatedState(onErreur)
    val dictee = remember(contexte) {
        DicteeAndroid(contexte, { partiel(it) }, { final(it) }, { message, longue -> erreur(message, longue) })
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
        if (accordee) dictee.demarrerEcoute() else erreur("Permission micro refusée", false)
    }
    dictee.demanderMicro = { permission.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(dictee) { onDispose { dictee.detruire() } }
    return dictee
}

/** Dictée française par le SpeechRecognizer du système : résultats partiels et finaux poussés
 *  dans le champ, 5 hypothèses au plus. */
private class DicteeAndroid(
    private val contexte: Context,
    private val onPartiel: (String) -> Unit,
    private val onFinal: (List<String>) -> Unit,
    private val onErreur: (String, Boolean) -> Unit,
) : Dictee {
    override var enCours by mutableStateOf(false)
        private set

    var demanderMicro: () -> Unit = {}
    private var reconnaissance: SpeechRecognizer? = null

    /** Les espèces proposées, soufflées à la reconnaissance (gardées le temps de demander le micro). */
    private var vocabulaire: List<String> = emptyList()

    /** Le micro relance toujours une écoute (rien ne l'arrête à la main). */
    override fun basculer(vocabulaire: List<String>) {
        this.vocabulaire = vocabulaire
        if (!SpeechRecognizer.isRecognitionAvailable(contexte)) {
            onErreur("Reconnaissance vocale non disponible", false)
            return
        }
        if (ContextCompat.checkSelfPermission(contexte, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            demanderMicro()
            return
        }
        demarrerEcoute()
    }

    /** Choisir une espèce n'arrête pas la reconnaissance, qui s'arrête d'elle-même. */
    override fun arreter() {}

    /** Rien à faire : chaque écoute s'arrête d'elle-même à la fin de la phrase, et la suivante,
     *  relancée par le micro, repart de zéro. */
    override fun texteModifie() {}

    /** Hors couverture (zone blanche), la reconnaissance EMBARQUÉE (modèle vocal hors ligne du
     *  téléphone) ; en couverture, en ligne (plus précise sur les noms d'espèces). [forcerHorsLigne]
     *  non nul : repli après un échec réseau, sans nouvelle bascule. */
    fun demarrerEcoute(forcerHorsLigne: Boolean? = null) {
        val horsLigne = forcerHorsLigne ?: !reseauDisponible()
        reconnaissance?.destroy()
        reconnaissance = SpeechRecognizer.createSpeechRecognizer(contexte).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    enCours = true
                }

                override fun onResults(results: Bundle?) {
                    enCours = false
                    val candidats = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.filter { it.isNotBlank() }
                        ?.takeIf { it.isNotEmpty() } ?: return
                    onFinal(candidats)
                }

                override fun onError(error: Int) {
                    enCours = false
                    // Une seule bascule automatique : le mode choisi échoue sur une erreur réseau
                    // (modèle hors ligne absent en couverture, réseau faible en ligne), on retente
                    // dans l'AUTRE mode avant d'abandonner.
                    if (forcerHorsLigne == null &&
                        (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
                    ) {
                        demarrerEcoute(forcerHorsLigne = !horsLigne)
                        return
                    }
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Aucune correspondance — réessayez"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Aucune voix détectée"
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            if (horsLigne) "Dictée hors-ligne indisponible : installez le modèle vocal " +
                                "« Français » hors connexion (paramètres de saisie vocale du téléphone)."
                            else "Erreur réseau — connexion requise"
                        SpeechRecognizer.ERROR_AUDIO -> "Erreur microphone"
                        else -> "Erreur reconnaissance ($error)"
                    }
                    onErreur(message, true)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onPartialResults(partialResults: Bundle?) {
                    val partiel = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                    onPartiel(partiel)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                // FREE_FORM hors ligne (modèle de dictée embarqué) ; WEB_SEARCH en ligne (court,
                // adapté aux noms d'espèces).
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    if (horsLigne) RecognizerIntent.LANGUAGE_MODEL_FREE_FORM else RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, horsLigne)
                // Android 13 et plus : la reconnaissance penche vers les espèces proposées, comme les
                // `contextualStrings` d'iOS. Une reconnaissance qui l'ignore n'en est pas gênée.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && vocabulaire.isNotEmpty()) {
                    putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(vocabulaire))
                }
            })
        }
    }

    private fun reseauDisponible(): Boolean {
        val cm = contexte.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val capacites = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun detruire() {
        reconnaissance?.destroy()
        reconnaissance = null
        enCours = false
    }
}
