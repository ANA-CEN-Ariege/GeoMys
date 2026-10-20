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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/CacheManagerFragment.kt) : ce
// que le fragment gardait dans ses champs et ses dialogues devient un état observable. Les règles et les textes
// sont dans carte/ZoneHorsLigne, carte/CartesMbtiles et carte/CiblageProtocole.

package fr.ariegenature.geomys.ui.cartes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.carte.CartesMbtiles
import fr.ariegenature.geomys.carte.CiblageProtocole
import fr.ariegenature.geomys.carte.EstimationZone
import fr.ariegenature.geomys.carte.FondCarte
import fr.ariegenature.geomys.carte.FondChoisi
import fr.ariegenature.geomys.carte.SitesProtocole
import fr.ariegenature.geomys.carte.ZoneHorsLigne
import fr.ariegenature.geomys.carte.chargerFondChoisi
import fr.ariegenature.geomys.carte.enregistrerFondChoisi
import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.monitoring.ObjetCarte
import fr.ariegenature.geomys.network.MonitoringModule
import fr.ariegenature.geomys.ui.carte.ConfigurationCarte
import fr.ariegenature.geomys.ui.carte.IssueTelechargement
import fr.ariegenature.geomys.ui.carte.ZonesHorsLigne
import fr.ariegenature.geomys.ui.carte.ZonesMapLibre
import fr.ariegenature.geomys.ui.carte.id
import fr.ariegenature.geomys.ui.composants.FluxChoisi
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Une carte importée, dans la liste du Maps Manager : son fichier, son nom affiché, et sa ligne
 *  (« nom », puis « zoom 8–16 · 120 Mo »). */
data class CarteImportee(val fichier: String, val nom: String, val detail: String)

/** Ce que le Maps Manager ouvre par-dessus la carte, un à la fois. */
sealed interface DialogueCartes {
    /** « Cartes hors-ligne (MBTiles) » : les cartes importées (le message d'invitation s'il n'y en a pas). */
    data class Mbtiles(val cartes: List<CarteImportee>) : DialogueCartes

    /** « Supprimer cette carte hors-ligne du téléphone ? » */
    data class Suppression(val carte: CarteImportee) : DialogueCartes

    /** « Import de la carte » : la copie du fichier choisi. */
    data object Import : DialogueCartes

    /** Le téléchargement de la zone, que seul « Annuler » interrompt : son [message] et son [avancement]. */
    data class Telechargement(val message: String, val avancement: Float) : DialogueCartes

    /** L'issue du téléchargement. */
    data class Resultat(val titre: String, val message: String) : DialogueCartes

    /** « Vider le cache des tuiles ? » */
    data object Vider : DialogueCartes

    /** « Cibler un protocole » : les protocoles accessibles. */
    data class Protocoles(val modules: List<MonitoringModule>) : DialogueCartes

    /** « Recherche des sites… » du protocole [moduleCode]. */
    data class Recherche(val moduleCode: String) : DialogueCartes
}

sealed interface EvenementMapsManager {
    data class Toast(val texte: String, val longue: Boolean = false) : EvenementMapsManager

    /** Recentrer la carte sur [position], au zoom de carte [zoom]. */
    data class Centrer(val position: LatLon, val zoom: Double) : EvenementMapsManager

    /** Cadrer la carte sur les sites d'un protocole. */
    data class Cadrer(val emprise: Emprise) : EvenementMapsManager
}

/** Le Maps Manager : la zone visible du fond affiché se télécharge pour le hors-ligne, du zoom courant au
 *  niveau 17 ; on y gère aussi les cartes MBTiles importées.
 *
 *  Les zooms de la carte (MapLibre, tuiles de 512 px) sont décalés d'un cran sur les niveaux de tuiles de
 *  256 px d'osmdroid, ceux qu'affiche le panneau : au zoom de carte 14, il compte « zooms 15–17 ». Le
 *  départ, le recadrage sur la position et les bornes de zoom de GeoMys-Android sont donc pris un cran
 *  plus bas. [modules] : les protocoles accessibles (droits CRUVED) ; [sites] : ceux d'un protocole. */
class MapsManagerViewModel(
    private val services: ServicesGeoNature,
    private val zones: ZonesHorsLigne = ZonesMapLibre(),
    private val modules: suspend () -> List<MonitoringModule> = { services.monitoring.chargerModulesDepuisCache().orEmpty() },
    private val sites: suspend (String) -> SitesProtocole = { code ->
        CiblageProtocole.charger(
            enfants = { services.monitoring.lireEnfants(code).orEmpty() },
            geometrie = { type, id -> services.monitoring.lireObjet(code, type, id)?.geometrieGeoJson },
        )
    },
    private val cartes: CartesMbtiles = services.cartesMbtiles,
    private val horloge: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val fichiers: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val tracker = services.locationTracker

    var fond by mutableStateOf(chargerFondChoisi(services.preferences, cartes::existe))
        private set
    var choixFondVisible by mutableStateOf(false)
        private set

    /** La zone visible et le zoom de la carte, à chaque mouvement. */
    private var emprise by mutableStateOf<Emprise?>(null)
    private var zoomCarte by mutableStateOf(ZOOM_DEPART)

    /** Le panneau (surface, tuiles, poids), recalculé à chaque mouvement : le calcul est local. Nul avant
     *  le premier cadrage. */
    val estimation: EstimationZone?
        get() = emprise?.let { ZoneHorsLigne.estimation(fond, it, zoomCarte + DECALAGE_ZOOM) }

    private var octetsCache by mutableStateOf(0L)

    /** « Cache : 120 / 1024 MB (12 %) », rouge au-delà de 90 %. */
    val occupation: Pair<String, Boolean> get() = ZoneHorsLigne.occupation(octetsCache)

    /** Les protocoles accessibles : le bouton « Cibler un protocole » n'apparaît que s'il y en a. */
    var protocoles by mutableStateOf<List<MonitoringModule>>(emptyList())
        private set

    /** Les sites du dernier protocole ciblé, en orange sur la carte. */
    var objetsProtocole by mutableStateOf<List<ObjetCarte>>(emptyList())
        private set

    var dialogue by mutableStateOf<DialogueCartes?>(null)
        private set

    private var telechargement: Job? = null

    private val _evenements = Channel<EvenementMapsManager>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    /** La carte est-elle déjà centrée sur la position du téléphone ? La première reçue la recentre. */
    var centreeSurPosition = false
        private set

    init {
        viewModelScope.launch { zones.occupation.collect { octetsCache = it } }
        viewModelScope.launch {
            protocoles = try {
                modules()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun carteBougee(visible: Emprise, zoom: Double) {
        emprise = visible
        zoomCarte = zoom
    }

    /** Ferme [ouvert], s'il est encore le dialogue affiché. */
    fun fermer(ouvert: DialogueCartes) {
        if (dialogue == ouvert) dialogue = null
    }

    // ─── Téléchargement ───

    /** Télécharge la zone visible du fond affiché, de son zoom au niveau 17 ; [densite] : celle de l'écran,
     *  pour des tuiles nettes. */
    fun charger(densite: Float) {
        val enLigne = (fond as? FondChoisi.EnLigne)?.fond ?: return
        val zone = emprise ?: return
        if (estimation?.telechargeable != true || dialogue != null) return
        val niveauMin = ZoneHorsLigne.zoomMin(zoomCarte + DECALAGE_ZOOM)
        dialogue = DialogueCartes.Telechargement(ZoneHorsLigne.PREPARATION, 0f)
        telechargement = viewModelScope.launch {
            val issue = zones.telecharger(ConfigurationCarte.urlStyle(enLigne.id), zone, niveauMin, ZoneHorsLigne.ZOOM_MAX_DEFAUT, densite) { recues, attendues ->
                dialogue = DialogueCartes.Telechargement(
                    if (attendues > 0) ZoneHorsLigne.progression(recues, attendues) else ZoneHorsLigne.TELECHARGEMENT_EN_COURS,
                    if (attendues > 0) (recues.toFloat() / attendues).coerceIn(0f, 1f) else 0f,
                )
            }
            dialogue = resultat(issue, enLigne)
        }
    }

    /** Arrête le téléchargement en cours (GeoMys-Android ne le permettait pas). Le paquet est mis en pause :
     *  les tuiles déjà reçues restent en cache, jusqu'au prochain « Vider ». */
    fun annulerTelechargement() {
        if (dialogue !is DialogueCartes.Telechargement) return
        telechargement?.cancel()
        telechargement = null
        dialogue = null
        _evenements.trySend(EvenementMapsManager.Toast(ZoneHorsLigne.TELECHARGEMENT_ANNULE, longue = true))
    }

    private fun resultat(issue: IssueTelechargement, enLigne: FondCarte): DialogueCartes.Resultat = when (issue) {
        IssueTelechargement.Termine -> DialogueCartes.Resultat(ZoneHorsLigne.TITRE_TERMINE, ZoneHorsLigne.messageTermine(enLigne))
        is IssueTelechargement.Incomplet -> DialogueCartes.Resultat(ZoneHorsLigne.TITRE_INCOMPLET, ZoneHorsLigne.MESSAGE_INCOMPLET)
        is IssueTelechargement.Limite -> DialogueCartes.Resultat(ZoneHorsLigne.TITRE_INCOMPLET, ZoneHorsLigne.messageLimite(issue.limite))
    }

    // ─── Vidage du cache ───

    fun demanderVidage() {
        if (dialogue == null) dialogue = DialogueCartes.Vider
    }

    fun vider() {
        fermer(DialogueCartes.Vider)
        viewModelScope.launch {
            val complet = zones.viderTout()
            _evenements.send(EvenementMapsManager.Toast(if (complet) ZoneHorsLigne.CACHE_VIDE else ZoneHorsLigne.VIDAGE_PARTIEL))
        }
    }

    // ─── Cartes MBTiles ───

    /** Ouvre la liste des cartes importées. */
    fun ouvrirCartes() {
        viewModelScope.launch {
            val liste = withContext(fichiers) {
                cartes.liste().map { f ->
                    CarteImportee(f.name, cartes.nomAffichage(f), CartesMbtiles.detail(cartes.info(f), cartes.tailleOctets(f)))
                }
            }
            dialogue = DialogueCartes.Mbtiles(liste)
        }
    }

    /** Copie la carte choisie dans le sélecteur de fichiers ; la liste se rouvre si elle est valide. */
    fun importer(flux: FluxChoisi?) {
        if (flux == null) return
        dialogue = DialogueCartes.Import
        viewModelScope.launch {
            val fichier = withContext(fichiers) { cartes.importer(flux.source, flux.nom, horloge()) }
            fermer(DialogueCartes.Import)
            if (fichier == null) {
                _evenements.send(EvenementMapsManager.Toast(CartesMbtiles.IMPORT_ECHOUE, longue = true))
            } else {
                _evenements.send(EvenementMapsManager.Toast(CartesMbtiles.importee(cartes.nomAffichage(fichier)), longue = true))
                ouvrirCartes()
            }
        }
    }

    fun demanderSuppression(carte: CarteImportee) {
        dialogue = DialogueCartes.Suppression(carte)
    }

    /** Supprime la carte ; si elle était le fond affiché, la carte repasse sur OpenStreetMap. */
    fun supprimer(carte: CarteImportee) {
        fermer(DialogueCartes.Suppression(carte))
        viewModelScope.launch {
            withContext(fichiers) { cartes.supprimer(cartes.fichier(carte.fichier)) }
            if ((fond as? FondChoisi.Mbtiles)?.fichier == carte.fichier) choisirFond(FondChoisi.EnLigne(FondCarte.OSM))
            _evenements.send(EvenementMapsManager.Toast(CartesMbtiles.CARTE_SUPPRIMEE))
            ouvrirCartes()
        }
    }

    // ─── Ciblage d'un protocole ───

    fun ouvrirProtocoles() {
        if (protocoles.isNotEmpty() && dialogue == null) dialogue = DialogueCartes.Protocoles(protocoles)
    }

    /** Dessine les sites du protocole et cadre la carte dessus ; ceux du protocole précédent s'effacent. */
    fun cibler(module: MonitoringModule) {
        val recherche = DialogueCartes.Recherche(module.moduleCode)
        dialogue = recherche
        viewModelScope.launch {
            val trouves = sites(module.moduleCode)
            fermer(recherche)
            val cadre = trouves.emprise
            if (trouves.objets.isEmpty() || cadre == null) {
                _evenements.send(EvenementMapsManager.Toast(CiblageProtocole.AUCUN_SITE, longue = true))
                return@launch
            }
            objetsProtocole = trouves.objets
            _evenements.send(EvenementMapsManager.Cadrer(cadre))
        }
    }

    // ─── Position ───

    /** Recentre sur la position reçue du téléphone, au zoom de travail ; sans position, un message le dit. */
    fun recentrer() {
        val position = tracker.position.value
        if (position == null) {
            _evenements.trySend(EvenementMapsManager.Toast(ZoneHorsLigne.POSITION_INDISPONIBLE))
            return
        }
        _evenements.trySend(EvenementMapsManager.Centrer(LatLon(position.latitude, position.longitude), ZOOM_POSITION))
    }

    /** La première position reçue a recentré la carte. */
    fun marquerCentree() {
        centreeSurPosition = true
    }

    private var positionDemarree = false

    /** La position s'affiche tant que la carte est à l'écran. Un parcours en cours (saisie OccTax) garde son
     *  récepteur : la carte ne l'arrête pas. */
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

    // ─── Fond ───

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

    companion object {
        /** Zoom de carte = niveau de tuile de 256 px − 1. */
        const val DECALAGE_ZOOM = 1.0

        /** Départ au centre de l'Ariège, au niveau 12 de GeoMys-Android. */
        const val ZOOM_DEPART = ZoneHorsLigne.ZOOM_DEPART - DECALAGE_ZOOM

        /** Recadrage sur la position, au niveau 15. */
        const val ZOOM_POSITION = ZoneHorsLigne.ZOOM_POSITION - DECALAGE_ZOOM

        /** Bornes de la carte : niveaux 2 à 17. */
        const val ZOOM_MIN = 2.0 - DECALAGE_ZOOM
        const val ZOOM_MAX = ZoneHorsLigne.ZOOM_MAX_DEFAUT - DECALAGE_ZOOM
    }
}
