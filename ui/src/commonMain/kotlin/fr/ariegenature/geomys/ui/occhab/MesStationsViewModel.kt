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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabStationsFragment.kt) : ce
// que le fragment posait sur ses vues devient un état observable ; les règles sont dans
// occhab/StationOccHab (MesStations), l'envoi groupé dans envoi/EnvoiGroupe.

package fr.ariegenature.geomys.ui.occhab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.envoi.EnvoiGroupe
import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.occhab.MesStations
import fr.ariegenature.geomys.occhab.OngletMesStations
import fr.ariegenature.geomys.ui.occtax.Information
import fr.ariegenature.geomys.ui.occtax.MessageBref
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** « Mes stations » (`OccHabStationsFragment`) : les SAISIES OccHab du store (chacune regroupe
 *  1..N stations), par onglet, leur envoi, leur réédition et leur suppression. */
class MesStationsViewModel(
    private val services: ServicesGeoNature,
    private val sessionVm: SessionOccHabViewModel,
) : ViewModel() {
    private val store = services.occHabStore
    private val config = services.config

    private val _messages = Channel<MessageBref>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Une saisie reprise : la carte s'ouvre sur ses stations. */
    private val _reprises = Channel<Unit>(Channel.BUFFERED)
    val reprises = _reprises.receiveAsFlow()

    var toutes by mutableStateOf<List<OccHabSaisie>>(emptyList())
        private set
    var onglet by mutableStateOf(OngletMesStations.A_ENVOYER)
        private set

    /** Un envoi (groupé ou unitaire) est en cours : bouton « Tout envoyer » masqué, nouveaux envois
     *  et suppressions refusés — la liste reste consultable, pas de modal bloquant. */
    var envoiEnCours by mutableStateOf(false)
        private set

    /** Progression d'envoi INLINE, sous les onglets. */
    var messageEnvoi by mutableStateOf<String?>(null)
        private set

    // ─── Dialogues ───
    var suppressionDemandee by mutableStateOf<OccHabSaisie?>(null)
        private set
    /** Les saisies à envoyer, figées à la demande de confirmation. */
    var confirmationToutEnvoyer by mutableStateOf<List<OccHabSaisie>?>(null)
        private set
    var information by mutableStateOf<Information?>(null)
        private set

    /** CRUVED C du module OCCHAB (détecté à la synchro) : sans droit de création, le POST
     *  partirait en 403 — les boutons d'envoi sont masqués. */
    val envoiAutorise: Boolean get() = config.occhabPeutCreer

    val saisies: List<OccHabSaisie> get() = MesStations.filtrer(toutes, onglet)

    val nbEnvoyables: Int get() = MesStations.envoyables(toutes).size

    val boutonToutEnvoyerVisible: Boolean
        get() = EnvoiGroupe.boutonVisible(onglet == OngletMesStations.A_ENVOYER, envoiAutorise, nbEnvoyables, envoiEnCours)

    fun rafraichir() {
        viewModelScope.launch { toutes = store.charger() }
    }

    fun choisirOnglet(choix: OngletMesStations) {
        onglet = choix
        rafraichir()
    }

    // ─── Réédition ───

    /** Réédition : on reprend la SAISIE puis on passe par la CARTE (ses stations affichées,
     *  « Valider » ajoute une station, taper une station l'édite). */
    fun editer(saisie: OccHabSaisie) {
        sessionVm.session.reprendreSaisie(saisie)
        _reprises.trySend(Unit)
    }

    // ─── Suppression ───

    fun demanderSuppression(saisie: OccHabSaisie) {
        if (envoiEnCours) {
            _messages.trySend(MessageBref(MesStations.ENVOI_EN_COURS))
            return
        }
        suppressionDemandee = saisie
    }

    fun supprimer() {
        val saisie = suppressionDemandee ?: return
        suppressionDemandee = null
        viewModelScope.launch {
            store.supprimer(saisie.id)
            toutes = store.charger()
        }
    }

    fun annulerSuppression() {
        suppressionDemandee = null
    }

    fun fermerInformation() {
        information = null
    }

    // ─── Envoi ───

    /** Envoie TOUTE la saisie (envoi partiel sans perte) ; le résultat s'affiche à la fin. */
    fun envoyer(saisie: OccHabSaisie) {
        if (envoiEnCours) {
            _messages.trySend(MessageBref(EnvoiGroupe.MESSAGE_DEJA_EN_COURS))
            return
        }
        if (!config.estConfiguree) {
            information = Information(EnvoiGroupe.TITRE_CONFIGURATION_REQUISE, EnvoiGroupe.MESSAGE_CONFIGURATION_REQUISE)
            return
        }
        envoiEnCours = true
        messageEnvoi = MesStations.ENVOI_SAISIE
        viewModelScope.launch {
            try {
                val res = services.envoiOccHab.envoyerSaisieOccHabVersGeoNature(saisie, config)
                toutes = store.charger()
                information = Information(if (res.succes) "Envoi" else "Erreur d'envoi", res.message)
            } finally {
                envoiEnCours = false
                messageEnvoi = null
            }
        }
    }

    /** « Tout envoyer » : refus si un envoi tourne ou si la connexion manque, sinon confirmation. */
    fun demanderToutEnvoyer() {
        if (envoiEnCours) {
            _messages.trySend(MessageBref(EnvoiGroupe.MESSAGE_DEJA_EN_COURS))
            return
        }
        if (!config.estConfiguree) {
            information = Information(EnvoiGroupe.TITRE_CONFIGURATION_REQUISE, EnvoiGroupe.MESSAGE_CONFIGURATION_REQUISE)
            return
        }
        viewModelScope.launch {
            val aEnvoyer = MesStations.envoyables(store.charger())
            if (aEnvoyer.isNotEmpty()) confirmationToutEnvoyer = aEnvoyer
        }
    }

    fun annulerToutEnvoyer() {
        confirmationToutEnvoyer = null
    }

    /** Pousse séquentiellement toutes les saisies envoyables (envoi partiel sans perte et
     *  anti-doublon par station) ; la liste se met à jour au fil des envois, le récapitulatif
     *  vient à la fin. */
    fun toutEnvoyer() {
        val aEnvoyer = confirmationToutEnvoyer ?: return
        confirmationToutEnvoyer = null
        envoiEnCours = true
        viewModelScope.launch {
            try {
                val recap = EnvoiGroupe.envoyer(
                    aEnvoyer,
                    progression = { messageEnvoi = it },
                    envoyerUne = { s -> services.envoiOccHab.envoyerSaisieOccHabVersGeoNature(s, config).let { it.succes to it.message } },
                    apresChaque = { rafraichir() },
                )
                information = Information(recap.titre, recap.message)
            } finally {
                envoiEnCours = false
                messageEnvoi = null
                rafraichir()
            }
        }
    }
}
