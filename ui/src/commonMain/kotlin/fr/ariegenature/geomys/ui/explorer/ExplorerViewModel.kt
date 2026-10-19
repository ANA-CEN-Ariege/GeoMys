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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/ExplorerFragment.kt) : ce que
// le fragment gardait dans ses champs devient un état observable ; les règles sont dans explorer/Explorer.

package fr.ariegenature.geomys.ui.explorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.carte.FondChoisi
import fr.ariegenature.geomys.carte.chargerFondChoisi
import fr.ariegenature.geomys.carte.enregistrerFondChoisi
import fr.ariegenature.geomys.explorer.Explorer
import fr.ariegenature.geomys.explorer.GroupeObs
import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.ObsExplorer
import fr.ariegenature.geomys.model.Taxon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** L'Explorer : la carte des observations de la synthèse GeoNature sur la zone visible. [source] : la
 *  synthèse (`/synthese/for_web`). L'essai sans serveur ([ExplorerEssai]) impose sa [source], [configure] et
 *  son [departImpose]. */
class ExplorerViewModel(
    private val services: ServicesGeoNature,
    private val source: suspend (Emprise) -> List<ObsExplorer> = { e ->
        services.browse.recupererObsExplorer(services.config, minLon = e.ouest, minLat = e.sud, maxLon = e.est, maxLat = e.nord)
    },
    /** Sans serveur configuré, rien n'est demandé : un message le dit. */
    val configure: Boolean = services.config.connexionConfiguree,
    departImpose: Pair<LatLon, Double>? = null,
) : ViewModel() {
    private val config = services.config
    val tracker = services.locationTracker

    var fond by mutableStateOf(chargerFondChoisi(services.preferences, services.cartesMbtiles::existe))
        private set
    var choixFondVisible by mutableStateOf(false)
        private set

    /** Les observations reçues, avant le filtre. */
    var observations by mutableStateOf<List<ObsExplorer>>(emptyList())
        private set
    var groupeFiltre by mutableStateOf<Taxon?>(null)
        private set

    /** Les marqueurs : les observations du groupe choisi, par cellule. */
    val groupes: List<GroupeObs> get() = Explorer.regrouper(Explorer.filtrer(observations, groupeFiltre))

    /** Le compteur, sous la carte, une fois des observations reçues. */
    var compteurVisible by mutableStateOf(false)
        private set

    /** La profondeur d'historique du serveur, dans le compteur et en tête des listes. */
    val dureeJours: Int get() = config.dureeObservationJours
    val compteur: String get() = Explorer.compteur(Explorer.filtrer(observations, groupeFiltre).size, dureeJours)

    /** Le message sous la carte (non configuré, zone trop large, erreur), à la place du compteur. */
    var message by mutableStateOf(if (configure) null else Explorer.NON_CONFIGURE)
        private set
    var chargement by mutableStateOf(false)
        private set

    /** La cellule touchée, dont la liste est ouverte. */
    var groupeOuvert by mutableStateOf<GroupeObs?>(null)
        private set

    /** La carte est-elle déjà centrée sur une vraie position ? Sinon, la première reçue la recentre. */
    var centreeSurPosition = departImpose != null || tracker.positionConnue() != null
        private set

    /** Le départ : la dernière position connue au zoom 12, sinon le centre de la France au zoom 6. */
    val depart: Pair<LatLon, Double> = departImpose
        ?: tracker.positionConnue()?.let { LatLon(it.latitude, it.longitude) to Explorer.ZOOM_DERNIERE_POSITION }
        ?: (LatLon(Explorer.LATITUDE_FRANCE, Explorer.LONGITUDE_FRANCE) to Explorer.ZOOM_FRANCE)

    private var attente: Job? = null
    private var requete: Job? = null
    private var numeroRequete = 0

    /** La carte a bougé : les observations de [emprise] seront demandées après [Explorer.DELAI_MS]. */
    fun carteBougee(emprise: Emprise) {
        if (!configure) return
        attente?.cancel()
        attente = viewModelScope.launch {
            delay(Explorer.DELAI_MS)
            charger(emprise)
        }
    }

    /** Demande les observations de [emprise], sauf au-delà de [Explorer.LARGEUR_MAX_KM] de large. La
     *  requête précédente est abandonnée. Une erreur laisse les observations déjà affichées. */
    fun charger(emprise: Emprise) {
        if (!configure) return
        requete?.cancel()
        if (Explorer.largeurKm(emprise) > Explorer.LARGEUR_MAX_KM) {
            observations = emptyList()
            compteurVisible = false
            message = Explorer.ZONE_TROP_LARGE
            chargement = false
            return
        }
        // Le répartiteur principal immédiat peut finir la requête avant que `launch` rende la main : son
        // numéro, pris avant, dit si elle est toujours la dernière.
        val numero = ++numeroRequete
        requete = viewModelScope.launch {
            chargement = true
            try {
                observations = source(emprise)
                compteurVisible = true
                message = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = Explorer.erreur(e.message)
            } finally {
                // Une requête abandonnée ne coupe pas l'indicateur de la suivante.
                if (numero == numeroRequete) chargement = false
            }
        }
    }

    /** Toucher un groupe le choisit ; le toucher de nouveau les remontre tous. */
    fun basculerFiltre(taxon: Taxon) {
        groupeFiltre = if (groupeFiltre == taxon) null else taxon
    }

    fun ouvrir(groupe: GroupeObs) {
        groupeOuvert = groupe
    }

    fun fermerListe() {
        groupeOuvert = null
    }

    /** La première position reçue en direct a recentré la carte. */
    fun marquerCentree() {
        centreeSurPosition = true
    }

    /** Où recentrer : la position reçue en direct, sinon la dernière connue du système. */
    fun positionPourCentrer(): LatLon? =
        (tracker.position.value ?: tracker.positionConnue())?.let { LatLon(it.latitude, it.longitude) }

    private var positionDemarree = false

    /** La position s'affiche tant que la carte est à l'écran. Un parcours en cours (saisie OccTax) garde
     *  son récepteur : la carte ne l'arrête pas. */
    fun reprendre() {
        if (tracker.estEnCours.value) return
        tracker.demarrer()
        positionDemarree = true
    }

    fun suspendre() {
        if (!positionDemarree || tracker.estEnCours.value) return
        tracker.arreter()
        positionDemarree = false
    }

    fun montrerChoixFond() {
        choixFondVisible = true
    }

    fun fermerChoixFond() {
        choixFondVisible = false
    }

    fun choisirFond(choisi: FondChoisi) {
        fond = choisi
        choixFondVisible = false
        enregistrerFondChoisi(services.preferences, choisi)
    }
}
