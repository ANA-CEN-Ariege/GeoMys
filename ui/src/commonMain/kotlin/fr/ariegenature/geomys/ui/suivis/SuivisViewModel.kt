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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SuivisFragment.kt) : ce que le
// fragment posait sur ses vues (chargement, message, liste, compteur des saisies en attente, pictos) devient
// un état observable.

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.monitoring.ListeProtocoles
import fr.ariegenature.geomys.monitoring.PictoMonitoring
import fr.ariegenature.geomys.network.MonitoringModule
import fr.ariegenature.geomys.ui.composants.lireMiniature
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ce que montre la liste des protocoles. */
sealed interface EtatSuivis {
    data object Chargement : EtatSuivis
    /** Erreur de chargement, ou liste vide : un message centré. */
    data class Message(val texte: String) : EtatSuivis
    data class Protocoles(val modules: List<MonitoringModule>) : EtatSuivis
}

/** Le picto d'un protocole : l'image du serveur, sinon l'emoji de son code FontAwesome. */
sealed interface Picto {
    data class Image(val image: ImageBitmap) : Picto
    data class Emoji(val texte: String) : Picto
    /** Ni image ni code : rien n'est affiché (pas de place réservée, comme sous Android). */
    data object Aucun : Picto
}

/** L'écran « Monitoring » (`SuivisFragment`) : les protocoles que la dernière synchro a mis en cache,
 *  sans requête (décisions du 20 octobre, comme GeoMys-IOS ; GeoMys-Android interrogeait le serveur à
 *  chaque ouverture). Rien avant la première synchro. */
class SuivisViewModel(private val services: ServicesGeoNature) : ViewModel() {

    var etat by mutableStateOf<EtatSuivis>(EtatSuivis.Chargement)
        private set

    /** Saisies des Suivis en attente ou en échec d'envoi (bouton masqué à zéro). */
    var nbEnAttente by mutableStateOf(0)
        private set

    /** Pictos par code de protocole ; absent tant que son chargement n'est pas fini. */
    val pictos = mutableStateMapOf<String, Picto>()

    init {
        charger()
    }

    private fun charger() {
        etat = EtatSuivis.Chargement
        viewModelScope.launch {
            etat = try {
                val modules = services.monitoring.chargerModulesDepuisCache()
                when {
                    modules == null -> EtatSuivis.Message(ListeProtocoles.AUCUN_PROTOCOLE_EN_CACHE)
                    modules.isEmpty() -> EtatSuivis.Message(ListeProtocoles.messageAucunProtocole(services.config.login))
                    else -> EtatSuivis.Protocoles(modules)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EtatSuivis.Message(ListeProtocoles.messageErreur(e))
            }
        }
    }

    /** À chaque retour sur l'écran : le nombre change après une saisie, un envoi ou une suppression. */
    fun rafraichirAttente() {
        viewModelScope.launch { nbEnAttente = services.outbox.countEnAttente() }
    }

    /** Les pictos des protocoles, du cache de la synchro, réduits à [cotePx] (une vignette). En
     *  l'absence d'image en cache, l'emoji du code FontAwesome. */
    fun chargerPictos(modules: List<MonitoringModule>, cotePx: Int) {
        modules.filter { it.moduleCode !in pictos }.forEach { m ->
            viewModelScope.launch {
                val image = withContext(Dispatchers.IO) {
                    try {
                        services.cheminPicto(m.moduleCode)?.let { lireMiniature(it, cotePx) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                val emoji = m.modulePicto?.let(PictoMonitoring::faEnEmoji)
                pictos[m.moduleCode] = when {
                    image != null -> Picto.Image(image)
                    emoji != null -> Picto.Emoji(emoji)
                    else -> Picto.Aucun
                }
            }
        }
    }
}
