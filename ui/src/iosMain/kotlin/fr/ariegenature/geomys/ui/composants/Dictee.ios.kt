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

// Porté de GeoMys-IOS (UI/Saisie/DicteeVocale.swift) : SFSpeechRecognizer (fr-FR) et AVAudioEngine, la
// transcription en direct poussée dans le champ, les hypothèses remises à la fin.

@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLocale
import platform.Foundation.NSURLErrorDomain
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognitionTaskHintSearch
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.Speech.SFTranscription
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberDictee(
    onPartiel: (String) -> Unit,
    onFinal: (List<String>) -> Unit,
    onErreur: (message: String, longue: Boolean) -> Unit,
): Dictee {
    val partiel by rememberUpdatedState(onPartiel)
    val final by rememberUpdatedState(onFinal)
    val erreur by rememberUpdatedState(onErreur)
    val dictee = remember { DicteeIos({ partiel(it) }, { final(it) }, { message -> erreur(message, false) }) }
    DisposableEffect(dictee) { onDispose { dictee.fermer() } }
    return dictee
}

/** Nombre d'hypothèses exploitées (EXTRA_MAX_RESULTS d'Android). */
private const val MAX_HYPOTHESES = 5

private class DicteeIos(
    private val onPartiel: (String) -> Unit,
    private val onFinal: (List<String>) -> Unit,
    private val onErreur: (String) -> Unit,
) : Dictee {
    override var enCours by mutableStateOf(false)
        private set

    private val reconnaissance = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = "fr-FR"))
    private val moteur = AVAudioEngine()
    private var requete: SFSpeechAudioBufferRecognitionRequest? = null
    private var tache: SFSpeechRecognitionTask? = null

    /** Session d'écoute en cours. L'arrêter l'invalide : `endAudio()` fait livrer un dernier
     *  résultat APRÈS coup, qui ne doit plus rien déclencher (sinon l'espèce qu'on vient de choisir
     *  à la main serait ajoutée une seconde fois par la dictée qu'on croyait close). */
    private var session = 0
    private var transcription = ""

    /** Réseau disponible, suivi en continu : il décide du modèle vocal. */
    private var reseau = true
    private val moniteur = nw_path_monitor_create().also { m ->
        nw_path_monitor_set_update_handler(m) { chemin -> reseau = nw_path_get_status(chemin) == nw_path_status_satisfied }
        nw_path_monitor_set_queue(m, dispatch_get_main_queue())
        nw_path_monitor_start(m)
    }

    override fun basculer(vocabulaire: List<String>) {
        if (enCours) arreter() else demarrer(vocabulaire, forcerSurLAppareil = null)
    }

    /** Autorisations de reconnaissance et de micro, puis écoute. [forcerSurLAppareil] n'est posé
     *  que par le repli automatique : une fois replié, on ne rebascule pas. */
    private fun demarrer(vocabulaire: List<String>, forcerSurLAppareil: Boolean?) {
        if (enCours) return
        SFSpeechRecognizer.requestAuthorization { statut ->
            dispatch_async(dispatch_get_main_queue()) {
                if (statut != SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
                    onErreur("Reconnaissance vocale non autorisée (Réglages).")
                    return@dispatch_async
                }
                AVAudioApplication.requestRecordPermissionWithCompletionHandler { micOk ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (!micOk) onErreur("Micro non autorisé (Réglages).") else ecouter(vocabulaire, forcerSurLAppareil)
                    }
                }
            }
        }
    }

    private fun ecouter(vocabulaire: List<String>, forcerSurLAppareil: Boolean?) {
        if (enCours) return
        if (!reconnaissance.available) {
            onErreur("Dictée indisponible.")
            return
        }
        val audio = AVAudioSession.sharedInstance()
        val erreurAudio = memScoped {
            val e = alloc<ObjCObjectVar<NSError?>>()
            if (!audio.setCategory(AVAudioSessionCategoryRecord, AVAudioSessionModeMeasurement, AVAudioSessionCategoryOptionDuckOthers, e.ptr)) return@memScoped e.value
            if (!audio.setActive(true, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, e.ptr)) return@memScoped e.value
            null
        }
        if (erreurAudio != null) {
            onErreur("Audio indisponible : ${erreurAudio.localizedDescription}")
            return
        }

        val req = SFSpeechAudioBufferRecognitionRequest()
        req.shouldReportPartialResults = true
        // Un nom d'espèce est une recherche courte, pas une phrase (modèle « WEB_SEARCH »
        // d'Android).
        req.taskHint = SFSpeechRecognitionTaskHintSearch
        // Les espèces du périmètre : le modèle cesse de choisir entre des mots français courants.
        req.contextualStrings = vocabulaire
        // Modèle RÉSEAU dès qu'il y a du réseau, plus précis sur les noms d'espèces ; hors
        // couverture, celui de l'appareil.
        val embarque = reconnaissance.supportsOnDeviceRecognition
        val surLAppareil = embarque && (forcerSurLAppareil ?: !reseau)
        req.requiresOnDeviceRecognition = surLAppareil
        requete = req

        val noeud = moteur.inputNode
        val format = noeud.outputFormatForBus(0uL)
        noeud.installTapOnBus(0uL, 1024u, format) { tampon, _ -> if (tampon != null) req.appendAudioPCMBuffer(tampon) }
        moteur.prepare()
        val erreurMoteur = memScoped {
            val e = alloc<ObjCObjectVar<NSError?>>()
            if (moteur.startAndReturnError(e.ptr)) null else e.value
        }
        if (erreurMoteur != null) {
            onErreur("Micro : ${erreurMoteur.localizedDescription}")
            noeud.removeTapOnBus(0uL)
            nettoyer()
            return
        }

        transcription = ""
        enCours = true
        session += 1
        val jeton = session
        tache = reconnaissance.recognitionTaskWithRequest(req) { resultat, erreur ->
            val texte = resultat?.bestTranscription?.formattedString
            val alternatives = resultat?.transcriptions.orEmpty()
                .take(MAX_HYPOTHESES)
                .mapNotNull { (it as? SFTranscription)?.formattedString }
            val final = resultat?.final ?: false
            val fini = erreur != null || final
            dispatch_async(dispatch_get_main_queue()) {
                // Session close (espèce choisie à la main, micro recoupé) : ce résultat arrive
                // trop tard pour compter.
                if (session != jeton) return@dispatch_async
                if (texte != null) {
                    transcription = texte
                    if (texte.isNotEmpty()) onPartiel(texte)
                }
                // Le réseau a lâché sans rien transcrire : on rouvre le micro sur le modèle
                // embarqué plutôt que d'abandonner (comme Android).
                if (erreur != null && !surLAppareil && embarque && forcerSurLAppareil == null &&
                    transcription.isEmpty() && erreur.domain == NSURLErrorDomain
                ) {
                    arreter()
                    demarrer(vocabulaire, forcerSurLAppareil = true)
                    return@dispatch_async
                }
                if (erreur != null && transcription.isEmpty()) onErreur(message(erreur))
                // Les hypothèses ne valent qu'au résultat FINAL : les partielles changent à chaque
                // syllabe.
                if (final && alternatives.isNotEmpty()) onFinal(alternatives)
                if (fini) arreter()
            }
        }
    }

    override fun arreter() {
        if (!enCours && !moteur.running) return
        // La session est close AVANT de couper : `endAudio()` et `cancel()` font encore remonter un
        // résultat, qui ne doit plus rien déclencher.
        session += 1
        moteur.stop()
        moteur.inputNode.removeTapOnBus(0uL)
        requete?.endAudio()
        tache?.cancel()
        nettoyer()
    }

    fun fermer() {
        arreter()
        nw_path_monitor_cancel(moniteur)
    }

    private fun nettoyer() {
        requete = null
        tache = null
        enCours = false
        AVAudioSession.sharedInstance().setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, null)
    }

    /** Message affichable. 203 « no speech » / 1110 « no match » : rien d'exploitable n'a été dit. */
    private fun message(erreur: NSError): String = when {
        erreur.domain == "kAFAssistantErrorDomain" && (erreur.code == 203L || erreur.code == 1110L) -> "Aucune voix détectée — réessayez."
        erreur.domain == NSURLErrorDomain -> "Dictée : erreur réseau — connexion requise pour ce modèle vocal."
        else -> "Dictée interrompue : ${erreur.localizedDescription}"
    }
}
