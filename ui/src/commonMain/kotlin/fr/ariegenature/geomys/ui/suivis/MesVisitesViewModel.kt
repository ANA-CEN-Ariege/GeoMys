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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SaisiesEnAttenteFragment.kt) :
// le rafraîchissement de la file, les onglets, l'envoi d'un groupe ou de tout, le réessai, la suppression en
// cascade et l'ouverture d'une saisie dans son formulaire. L'arbre et les textes sont dans monitoring/MesVisites.

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.monitoring.ItemMesVisites
import fr.ariegenature.geomys.monitoring.MesVisites
import fr.ariegenature.geomys.monitoring.NouvelleVisite
import fr.ariegenature.geomys.monitoring.OngletMesVisites
import fr.ariegenature.geomys.monitoring.OptionSaisie
import fr.ariegenature.geomys.monitoring.ResumeOutbox
import fr.ariegenature.geomys.monitoring.cacheSuivis
import fr.ariegenature.geomys.monitoring.resumeOutbox
import fr.ariegenature.geomys.network.OutboxEnvoi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Le menu d'une ligne. */
data class OptionsLigne(val saisie: SaisieEnAttente, val titre: String, val options: List<OptionSaisie>)

/** Une suppression qui emporterait des enfants locaux : à confirmer. */
data class SuppressionDemandee(val saisie: SaisieEnAttente, val titre: String, val message: String)

/** Une alerte à lire (récapitulatif d'envoi, configuration manquante). */
data class InformationMesVisites(val titre: String, val message: String)

sealed interface EvenementMesVisites {
    data class Toast(val texte: String, val longue: Boolean) : EvenementMesVisites

    /** La saisie rouverte dans son formulaire. */
    data class Edition(val ouverture: OuvertureNouvelleVisite) : EvenementMesVisites
}

/** « Mes visites » : la file d'envoi des Suivis. */
class MesVisitesViewModel(private val services: ServicesGeoNature) : ViewModel() {

    private val cache = services.cacheSuivis()

    var onglet by mutableStateOf(OngletMesVisites.A_ENVOYER)
        private set
    var resume by mutableStateOf(ResumeOutbox("", 0, 0))
        private set
    var items by mutableStateOf<List<ItemMesVisites>>(emptyList())
        private set

    /** Ce que « Tout envoyer » ferait partir (0 : bouton masqué). */
    var nbEnvoyables by mutableStateOf(0)
        private set

    /** Pendant un envoi (groupe ou tout) : le bouton d'envoi global est masqué. L'envoi lui-même
     *  est déjà sérialisé (OutboxEnvoi) ; ce drapeau garde l'écran cohérent. */
    var envoiEnCours by mutableStateOf(false)
        private set
    var messageEnvoi by mutableStateOf<String?>(null)
        private set
    var options by mutableStateOf<OptionsLigne?>(null)
        private set
    var suppression by mutableStateOf<SuppressionDemandee?>(null)
        private set
    var confirmationToutEnvoyer by mutableStateOf<Int?>(null)
        private set
    var information by mutableStateOf<InformationMesVisites?>(null)
        private set

    private val _evenements = Channel<EvenementMesVisites>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    private var toutes: List<SaisieEnAttente> = emptyList()

    /** Les droits d'envoi du protocole (toujours accordés, cf. MonitoringModules.moduleAutoriseCreation). */
    fun envoiAutorise(s: SaisieEnAttente) = services.monitoringModules.moduleAutoriseCreation(s.moduleCode)

    fun rafraichir() {
        viewModelScope.launch {
            toutes = services.outbox.tout()
            resume = resumeOutbox(toutes) { NouvelleVisite.estObjetDeNiveauVisite(it) }
            items = MesVisites.items(toutes, onglet, cache)
            nbEnvoyables = MesVisites.nbEnvoyables(toutes) { services.monitoringModules.moduleAutoriseCreation(it) }
        }
    }

    fun choisirOnglet(nouvel: OngletMesVisites) {
        onglet = nouvel
        rafraichir()
    }

    /** Une visite déjà envoyée dont des observations restent à envoyer garde sa flèche. */
    fun aDescendantsAEnvoyer(s: SaisieEnAttente) = MesVisites.aDescendantsAEnvoyer(s, toutes)

    fun ceTypeDe(s: SaisieEnAttente) = MesVisites.ceTypeDe(s, cache)

    // ─── Menu d'une ligne ───

    fun toucher(s: SaisieEnAttente) {
        val liste = MesVisites.options(s, MesVisites.descendants(s, toutes).size, envoiAutorise(s), cache)
        if (liste.isNotEmpty()) options = OptionsLigne(s, MesVisites.titreOptions(s, cache), liste)
    }

    fun fermerOptions() {
        options = null
    }

    fun choisir(s: SaisieEnAttente, option: OptionSaisie) {
        when (option) {
            OptionSaisie.Reessayer -> viewModelScope.launch {
                services.outbox.mettreAJour(s.uuid) { it.copy(etat = SaisieEnAttente.Etat.PENDING, messageErreur = null) }
                rafraichir()
            }
            is OptionSaisie.EnvoyerGroupe -> envoyerGroupe(s)
            is OptionSaisie.Supprimer -> demanderSuppression(s)
            OptionSaisie.Retirer -> viewModelScope.launch {
                services.outbox.supprimer(s.uuid)
                rafraichir()
            }
        }
    }

    // ─── Suppression ───

    /** Sans enfant local, la saisie part sans confirmation ; avec, on prévient : sans leur parent,
     *  ses enfants ne pourraient plus être envoyés. */
    fun demanderSuppression(s: SaisieEnAttente) {
        val nbEnfants = MesVisites.descendants(s, toutes).size
        if (nbEnfants == 0) {
            viewModelScope.launch {
                services.outbox.supprimer(s.uuid)
                rafraichir()
            }
            return
        }
        suppression = SuppressionDemandee(s, MesVisites.titreSuppression(s, cache), MesVisites.messageSuppression(s, nbEnfants, cache))
    }

    fun annulerSuppression() {
        suppression = null
    }

    fun supprimerEnCascade() {
        val s = suppression?.saisie ?: return
        suppression = null
        viewModelScope.launch {
            val n = services.outbox.supprimerCascade(s.uuid)
            _evenements.send(EvenementMesVisites.Toast(MesVisites.messageSupprimees(n), longue = false))
            rafraichir()
        }
    }

    // ─── Édition ───

    /** La saisie dans son formulaire ; [messageEphemere] y est affiché une fois rendu. [completion] :
     *  on vient finir ses champs obligatoires — l'enregistrement ramène ici, sans enchaîner. */
    fun editer(s: SaisieEnAttente, messageEphemere: String = "", completion: Boolean = false) {
        _evenements.trySend(
            EvenementMesVisites.Edition(
                OuvertureNouvelleVisite(
                    moduleCode = s.moduleCode,
                    editUuid = s.uuid,
                    fil = MesVisites.filPourEdition(s, cache),
                    modeCompletion = completion,
                    messageEphemere = messageEphemere,
                ),
            ),
        )
    }

    // ─── Envoi ───

    fun demanderToutEnvoyer() {
        if (envoiEnCours) {
            _evenements.trySend(EvenementMesVisites.Toast(MesVisites.MESSAGE_ENVOI_EN_COURS, longue = false))
            return
        }
        if (!services.config.estConfiguree) {
            information = InformationMesVisites(MesVisites.TITRE_CONFIGURATION, MesVisites.MESSAGE_CONFIGURATION)
            return
        }
        val nb = toutes.count { it.etat == SaisieEnAttente.Etat.PENDING || it.etat == SaisieEnAttente.Etat.ERROR }
        if (nb > 0) confirmationToutEnvoyer = nb
    }

    fun annulerToutEnvoyer() {
        confirmationToutEnvoyer = null
    }

    /** Toute la file, d'un coup, parent avant enfant ; l'anti-doublon tient à `objetCree`. */
    fun toutEnvoyer() {
        confirmationToutEnvoyer = null
        envoyer(MesVisites.PREPARATION_TOUT, groupe = false) { progression -> services.outboxEnvoi.envoyerTout(services.config, progression) }
    }

    /** Le groupe de [racine] (elle et ses descendants locaux). Une racine « à compléter » ouvre son
     *  formulaire, avec le message des champs obligatoires, au lieu d'être envoyée. */
    fun envoyerGroupe(racine: SaisieEnAttente) {
        val actuelle = toutes.firstOrNull { it.uuid == racine.uuid } ?: racine
        if (actuelle.aCompleter) {
            editer(actuelle, NouvelleVisite.MESSAGE_CHAMPS_OBLIGATOIRES, completion = true)
            return
        }
        envoyer(MesVisites.PREPARATION_GROUPE, groupe = true) { progression ->
            services.outboxEnvoi.envoyerGroupe(services.config, racine.uuid, progression)
        }
    }

    private fun envoyer(
        preparation: String,
        groupe: Boolean,
        envoi: suspend ((Int, Int, String) -> Unit) -> OutboxEnvoi.Resultat,
    ) {
        envoiEnCours = true
        messageEnvoi = preparation
        viewModelScope.launch {
            try {
                // La progression arrive du fil de l'envoi : elle est reposée sur celui de l'écran.
                val resultat = envoi { envoyees, total, message ->
                    viewModelScope.launch {
                        messageEnvoi = MesVisites.progression(envoyees, total, message)
                        rafraichir()
                    }
                }
                information = InformationMesVisites(MesVisites.TITRE_RECAPITULATIF, MesVisites.recapitulatif(resultat, groupe))
            } finally {
                // Une sortie de l'écran pendant l'envoi annule la coroutine : sans ce finally, l'écran
                // resterait « envoi en cours » et refuserait tout nouvel envoi.
                envoiEnCours = false
                messageEnvoi = null
                rafraichir()
            }
        }
    }

    fun fermerInformation() {
        information = null
    }
}
