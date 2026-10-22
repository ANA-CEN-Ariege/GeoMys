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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabStationFragment.kt) : ce
// que le fragment posait sur ses vues devient un état observable ; les textes sont dans occhab/StationOccHab.

package fr.ariegenature.geomys.ui.occhab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.occhab.DetailsStation
import fr.ariegenature.geomys.occhab.HabitatOccHab
import fr.ariegenature.geomys.occhab.SaisiesDetailsStation
import fr.ariegenature.geomys.occhab.SessionOccHab
import fr.ariegenature.geomys.occhab.StationOccHab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface EvenementStationOccHab {
    data class Message(val texte: String, val longue: Boolean = false) : EvenementStationOccHab

    /** L'écran d'un habitat ([id] null : un nouvel habitat). */
    data class Habitat(val id: String?) : EvenementStationOccHab

    /** La station est enregistrée : une carte vierge pour la suivante. */
    data object CarteSuivante : EvenementStationOccHab

    /** Saisie interrompue (application relancée) : retour à l'accueil. */
    data object Accueil : EvenementStationOccHab
}

/** L'écran d'une station OccHab : sa géométrie et son jeu de données en résumé, ses habitats (tap
 *  sur le crayon = éditer, poubelle = supprimer), « Détails » et la coche « Terminer ». La station
 *  est enregistrée AU FIL DE L'EAU. */
class StationOccHabViewModel(
    private val services: ServicesGeoNature,
    private val sessionVm: SessionOccHabViewModel,
) : ViewModel() {
    private val session: SessionOccHab get() = sessionVm.session
    private val store = services.occHabStore
    private val config = services.config

    /** « Nouvelle station » ou « Modifier la station ». */
    var titre by mutableStateOf(StationOccHab.titre(session.stationNouvelle))
        private set
    var geometrie by mutableStateOf("")
        private set
    var jeuDeDonnees by mutableStateOf("")
        private set
    var habitats by mutableStateOf<List<OccHabHabitat>>(emptyList())
        private set
    var details by mutableStateOf<EtatDetailsStation?>(null)
        private set
    private var deriveesAvant: List<Any?> = emptyList()

    /** Écriture du store échouée (disque plein ?) : on RESTE sur l'écran. */
    var echecEcriture by mutableStateOf(false)
        private set

    private val _evenements = Channel<EvenementStationOccHab>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    /** À chaque affichage (`onViewCreated`) : le titre, ou le retour à l'accueil si la station n'a
     *  pas de géométrie — mort du process pendant la saisie (session reconstruite VIERGE) : on ne
     *  crée PAS une station fantôme (0,0) envoyable (audit 2026-08-27). */
    fun ouverture() {
        if (!session.station.geometrieDefinie()) {
            _evenements.trySend(EvenementStationOccHab.Message(StationOccHab.SAISIE_INTERROMPUE, longue = true))
            _evenements.trySend(EvenementStationOccHab.Accueil)
            return
        }
        titre = StationOccHab.titre(session.stationNouvelle)
    }

    /** Les lignes du tableau des habitats du web : « N°. nom cité » et l'abondance. */
    fun lignesHabitats(): List<Pair<OccHabHabitat, Pair<String, String?>>> {
        val abondances = services.nomenclatures.get(HabitatOccHab.ABONDANCE.type)
        return habitats.mapIndexed { i, h -> h to StationOccHab.ligneHabitat(i + 1, h, abondances) }
    }

    /** Au retour sur l'écran (`onResume`) : le résumé et les habitats. */
    fun rafraichir() {
        geometrie = StationOccHab.resumeGeometrie(session.station)
        jeuDeDonnees = StationOccHab.jeuDeDonnees(session.details, DetailsStation.datasets(config))
        habitats = session.station.habitats
    }

    fun ajouterHabitat() {
        _evenements.trySend(EvenementStationOccHab.Habitat(null))
    }

    fun editerHabitat(id: String) {
        _evenements.trySend(EvenementStationOccHab.Habitat(id))
    }

    /** Une station SANS habitat reste valide et envoyable (décision terrain 2026-08-24) : la
     *  suppression du dernier habitat est permise. */
    fun supprimerHabitat(id: String) {
        session.supprimerHabitat(id)
        rafraichir()
        viewModelScope.launch { sauvegarder() }
    }

    // ─── Détails ───

    fun ouvrirDetails() {
        // Altitudes / surface sont éditables mais EXCLUES de l'empreinte : comparées avant/après.
        deriveesAvant = session.deriveesManuelles()
        details = EtatDetailsStation(demarrage = false, session.details, session.station, config, services.nomenclatures)
    }

    fun annulerDetails() {
        details = null
    }

    fun validerDetails(saisies: SaisiesDetailsStation) {
        details = null
        DetailsStation.appliquer(session, saisies, demarrage = false)
        session.forcerPersistanceSiDeriveesChangees(deriveesAvant)
        rafraichir()
        // Les détails modifiés s'appliquent aussi à la station courante déjà sauvée.
        viewModelScope.launch { sauvegarder() }
    }

    // ─── Terminer ───

    /** Coche verte : la station est (ré)écrite, puis une carte vierge pour la suivante. Écriture
     *  échouée : on RESTE sur la station courante — enchaîner jetterait la seule copie mémoire. */
    fun terminer() {
        viewModelScope.launch {
            // Station importée ENCORE (ou DE NOUVEAU) intacte : aucune copie « à envoyer » ne
            // subsiste, le message ne doit pas prétendre le contraire.
            val intacte = store.estIntacte(session.saisieId, session.stationAEnregistrer())
            if (!sauvegarder()) return@launch
            _evenements.send(EvenementStationOccHab.Message(if (intacte) StationOccHab.AUCUNE_MODIFICATION else StationOccHab.ENREGISTREE, longue = true))
            session.nouvelleStation()
            _evenements.send(EvenementStationOccHab.CarteSuivante)
        }
    }

    /** Enregistrement AU FIL DE L'EAU de la station courante, détails fusionnés ; rend le succès
     *  de l'écriture disque (sur échec, l'alerte bloquante). */
    private suspend fun sauvegarder(): Boolean {
        val ok = store.upsertStation(session.saisieId, session.stationAEnregistrer())
        if (!ok) echecEcriture = true
        return ok
    }

    fun fermerEchecEcriture() {
        echecEcriture = false
    }
}
