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

// Propre au portage (demande du 15 novembre 2026 : « Serin cini » dicté revenait en « sera signé ») : le
// modèle de langue sur mesure d'iOS 17 (SFSpeechLanguageModel), fait des noms d'espèces proposables. Les
// `contextualStrings` ne portent que cent expressions ; le modèle les porte toutes.
//
// Les données d'entraînement (SFCustomLanguageModelData) n'existent qu'en Swift : l'appli hôte les écrit
// ([ExportModeleLangue], posé par iOSApp.swift). Le reste, compilation et usage, est ici.

@file:OptIn(ExperimentalForeignApi::class)

package fr.ariegenature.geomys.ui.composants

import fr.ariegenature.geomys.journal.Journal
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Speech.SFSpeechLanguageModel
import platform.Speech.SFSpeechLanguageModelConfiguration
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Écrit les données d'entraînement d'un modèle de langue : implémenté en Swift par l'appli hôte. */
interface ExportModeleLangue {
    /** Écrit dans le fichier [chemin] les données faites des [phrases], puis appelle [fin]. */
    fun exporter(phrases: List<String>, chemin: String, fin: ExportFini)
}

interface ExportFini {
    fun fini(reussi: Boolean)
}

/** Les modèles de langue de la dictée, un par ensemble de noms. Tout se passe sur la file principale. */
object ModeleLangueDictee {
    /** Posé au lancement par l'appli hôte ; null : pas de modèle de langue, la dictée fait sans. */
    var exportateur: ExportModeleLangue? = null

    /** Change quand la façon de faire le modèle change : les anciens fichiers ne servent plus. */
    private const val VERSION = 1

    private val prets = HashMap<String, SFSpeechLanguageModelConfiguration>()
    private val enCours = HashSet<String>()
    private val ecartes = HashSet<String>()

    private fun cle(noms: List<String>): String =
        "v$VERSION-${noms.size}-${noms.joinToString("\n").hashCode().toUInt().toString(16)}"

    /** Le modèle de ces [noms], s'il est prêt. */
    fun pret(noms: List<String>): SFSpeechLanguageModelConfiguration? = if (noms.isEmpty()) null else prets[cle(noms)]

    /** Ce modèle a fait échouer une écoute : il ne sert plus jusqu'au prochain lancement. */
    fun ecarter(noms: List<String>) {
        val cle = cle(noms)
        prets.remove(cle)
        ecartes.add(cle)
        Journal.w("ModeleLangueDictee", "modèle $cle écarté après une écoute en échec")
    }

    /** Prépare en tâche de fond le modèle de ces [noms] (rien à faire s'il est prêt, en cours ou écarté).
     *  iOS garde le modèle compilé : d'un lancement à l'autre, la préparation est courte. */
    fun preparer(noms: List<String>) {
        val export = exportateur ?: return
        if (noms.isEmpty()) return
        val cle = cle(noms)
        if (cle in prets || cle in enCours || cle in ecartes) return
        val dossier = dossier() ?: return
        enCours.add(cle)
        val donnees = "$dossier/$cle.bin"
        val configuration = SFSpeechLanguageModelConfiguration(
            languageModel = NSURL.fileURLWithPath("$dossier/$cle.lm"),
            vocabulary = NSURL.fileURLWithPath("$dossier/$cle.vocab"),
        )
        fun echec(raison: String) {
            enCours.remove(cle)
            ecartes.add(cle)
            Journal.w("ModeleLangueDictee", "modèle $cle non préparé ($raison)")
        }
        fun compiler() {
            SFSpeechLanguageModel.prepareCustomLanguageModelForUrl(
                asset = NSURL.fileURLWithPath(donnees),
                clientIdentifier = NSBundle.mainBundle.bundleIdentifier ?: "geomys",
                configuration = configuration,
            ) { erreur ->
                dispatch_async(dispatch_get_main_queue()) {
                    if (erreur != null) {
                        echec(erreur.localizedDescription)
                    } else {
                        enCours.remove(cle)
                        prets[cle] = configuration
                    }
                }
            }
        }
        // Les données déjà écrites pour ces noms servent telles quelles.
        if (NSFileManager.defaultManager.fileExistsAtPath(donnees)) {
            compiler()
            return
        }
        export.exporter(noms, donnees, object : ExportFini {
            override fun fini(reussi: Boolean) {
                dispatch_async(dispatch_get_main_queue()) { if (reussi) compiler() else echec("données non écrites") }
            }
        })
    }

    private fun dossier(): String? {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String ?: return null
        val dossier = "$caches/modeles-dictee"
        return if (NSFileManager.defaultManager.createDirectoryAtPath(dossier, true, null, null)) dossier else null
    }
}
