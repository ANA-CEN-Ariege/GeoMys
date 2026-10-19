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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabCarteFragment.kt,
// StationsServeurOverlay.kt et StationsSessionOverlay.kt) : ce que le fragment et ses deux overlays tenaient
// dans leurs champs devient un état observable, leurs dialogues et messages des états et des événements.
// Les règles et les textes sont dans occhab/CarteOccHab, la géométrie éditée dans occhab/GeometrieEdition.

package fr.ariegenature.geomys.ui.occhab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.carte.FondChoisi
import fr.ariegenature.geomys.carte.chargerFondChoisi
import fr.ariegenature.geomys.carte.enregistrerFondChoisi
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.network.humaniserErreurReseau
import fr.ariegenature.geomys.occhab.CadrageOccHab
import fr.ariegenature.geomys.occhab.CarteOccHab
import fr.ariegenature.geomys.occhab.DetailsStation
import fr.ariegenature.geomys.occhab.GeometrieEdition
import fr.ariegenature.geomys.occhab.ModeGeometrie
import fr.ariegenature.geomys.occhab.PileAnnulationGeometrie
import fr.ariegenature.geomys.occhab.SaisiesDetailsStation
import fr.ariegenature.geomys.occhab.SessionOccHab
import fr.ariegenature.geomys.occhab.chargerStationsDuJdd
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Un ordre de caméra pour l'écran. */
sealed interface CameraOccHab {
    /** Englober une emprise (`zoomerSur`). */
    data class SurEmprise(val emprise: fr.ariegenature.geomys.geo.Emprise) : CameraOccHab

    /** Centrer, au [zoom] donné ou en gardant le zoom courant (null). */
    data class Centre(val centre: LatLon, val zoom: Double?) : CameraOccHab
}

sealed interface EvenementCarteOccHab {
    data class Message(val texte: String, val longue: Boolean = false) : EvenementCarteOccHab
    data class Camera(val ordre: CameraOccHab) : EvenementCarteOccHab

    /** La géométrie validée : l'écran de la station (ses habitats, ses détails). */
    data object OuvrirStation : EvenementCarteOccHab

    /** Regagner l'accueil : les stations validées sont déjà persistées au fil de l'eau. */
    data object Accueil : EvenementCarteOccHab
}

/** Les dialogues de la carte. */
sealed interface DialogueCarteOccHab {
    data class ModifierEnvoyee(val station: OccHabStation) : DialogueCarteOccHab
    data class NonModifiable(val station: OccHabStation) : DialogueCarteOccHab
    data class Import(val station: OccHabStation) : DialogueCarteOccHab
    data class CopieLocale(val saisie: OccHabSaisie, val copie: OccHabStation, val traceEnCours: Boolean) : DialogueCarteOccHab
    data class Terminer(val saisie: OccHabSaisie, val options: List<String>) : DialogueCarteOccHab
    data class ResultatEnvoi(val titre: String, val message: String) : DialogueCarteOccHab
}

/** La carte OccHab : consultation des stations (locales en rouge, du serveur en violet), puis
 *  création ou réédition de la géométrie d'une station (point ou polygone, avec ses trous). */
class CarteOccHabViewModel(
    private val services: ServicesGeoNature,
    private val sessionVm: SessionOccHabViewModel,
) : ViewModel() {
    private val session: SessionOccHab get() = sessionVm.session
    private val store = services.occHabStore
    private val config = services.config

    // ─── État de l'écran ───

    /** false = CONSULTATION (vue d'entrée) : la carte montre ce qui existe déjà et les taps ne
     *  dessinent rien ; seul « Ajouter une station » engage une création. true = CRÉATION : tap
     *  = point ou sommet, « Annuler » / « Valider ». */
    var enCreation by mutableStateOf(false)
        private set

    /** La géométrie d'édition vient d'une STATION EXISTANTE (sélectionnée pour modification) :
     *  le tap sur la carte n'a plus d'effet, les poignées « + » apparaissent. */
    var geometrieChargee by mutableStateOf(false)
        private set

    /** La géométrie en cours d'édition. */
    var edition by mutableStateOf(GeometrieEdition())
        private set

    private val pile = PileAnnulationGeometrie()
    private var pileVide by mutableStateOf(true)

    /** Le bandeau d'instructions. */
    var bandeau by mutableStateOf("")
        private set

    /** Stations LOCALES restant à envoyer (rouges), toutes saisies, hors station éditée. */
    var stationsLocales by mutableStateOf<List<OccHabStation>>(emptyList())
        private set

    /** Stations du serveur dessinées en violet (importables). */
    var stationsViolettes by mutableStateOf<List<OccHabStation>>(emptyList())
        private set

    /** Stations du serveur chargées (cache ou réseau) et le jeu de données du chargement. */
    private var stationsServeur: List<OccHabStation> = emptyList()
    private var jddCharge: Int? = null
    private var nbReprisesAilleurs = 0

    /** Un cadrage a été imposé (géométrie courante, emprise mémorisée, position) : le premier
     *  point GPS ne recentre plus. */
    private var cadrageInitialFait = false

    var fond by mutableStateOf(chargerFondChoisi(services.preferences) { false })
        private set
    var choixFondVisible by mutableStateOf(false)
        private set
    var carteSuitBoussole by mutableStateOf(false)
        private set

    var dialogue by mutableStateOf<DialogueCarteOccHab?>(null)
        private set
    var details by mutableStateOf<EtatDetailsStation?>(null)
        private set
    private var apresDetails: ((SaisiesDetailsStation) -> Unit)? = null
    var envoiEnCours by mutableStateOf(false)
        private set

    /** Écriture du store échouée (disque plein ?) : la consigne de l'alerte bloquante. */
    var echecEcriture by mutableStateOf<String?>(null)
        private set

    val tracker = services.locationTracker
    private var positionDemarree = false

    private val _evenements = Channel<EvenementCarteOccHab>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    // ─── Ce que l'écran déduit ───

    /** « Valider » : un point posé, ou trois sommets. */
    val peutValider: Boolean get() = edition.geomOk

    /** « Annuler » : un geste à défaire, ou la sélection / la création à abandonner. */
    val peutAnnuler: Boolean get() = !pileVide || geometrieChargee || enCreation

    /** Le TYPE de géométrie se choisit avant de dessiner : dès qu'une station est reprise ou
     *  qu'un premier point ou sommet est posé, les boutons de mode sont inactifs (demande
     *  utilisateur 2026-10-18) ; « Annuler » rend la main. */
    val geometrieEngagee: Boolean get() = geometrieChargee || !edition.vide

    /** On dessine une NOUVELLE géométrie : l'aimantage passe avant la sélection d'une voisine. */
    val dessinEnCours: Boolean get() = enCreation && !geometrieChargee

    /** Les cibles d'aimantage : sommets des stations rouges et violettes. */
    val ciblesAimantage: List<LatLon> get() = CarteOccHab.ciblesAimantage(stationsLocales, stationsViolettes)

    private val aucuneGeometrieEnCours: Boolean get() = !geometrieChargee && edition.vide
    private val traceEnCoursNonValide: Boolean get() = !geometrieChargee && !edition.vide
    private val serveurCharge: Boolean get() = stationsServeur.isNotEmpty()

    // ─── Ouverture ───

    /** À chaque affichage de la carte (`onViewCreated`) : le mode, la géométrie de la station
     *  courante, les stations ; rend le cadrage d'ouverture. */
    fun ouverture(): CameraOccHab {
        // Retour sur la carte alors qu'une station est en cours (Valider → station → retour) : on
        // reprend en CRÉATION — sinon le panneau proposerait « Ajouter une station » par-dessus.
        if (session.station.geometrieDefinie()) enCreation = true
        val chargee = GeometrieEdition.depuisStation(session.station)
        edition = chargee ?: edition.copy(point = null, sommets = emptyList(), trous = emptyList())
        // Géométrie issue d'une station déjà définie → le tap ne dessine plus rien.
        geometrieChargee = chargee != null
        bandeau = CarteOccHab.texteMode(edition.mode, geometrieChargee)
        val cadrage = CarteOccHab.cadrageOuverture(chargee?.let(::pointsDe).orEmpty(), session.dernierCadrageCarte)
        var camera = when (cadrage) {
            is CadrageOccHab.SurEmprise -> CameraOccHab.SurEmprise(cadrage.emprise)
            is CadrageOccHab.Centre -> CameraOccHab.Centre(cadrage.centre, cadrage.zoom)
        }
        cadrageInitialFait = cadrage is CadrageOccHab.SurEmprise || (cadrage as CadrageOccHab.Centre).impose
        // CONSULTATION sans cadrage mémorisé : on centre sur la position si elle est déjà connue.
        val position = tracker.position.value
        if (!enCreation && !cadrageInitialFait && position != null) {
            cadrageInitialFait = true
            camera = CameraOccHab.Centre(LatLon(position.latitude, position.longitude), CarteOccHab.ZOOM_POSITION)
        }
        val consultation = !enCreation
        viewModelScope.launch {
            val locales = rafraichirLocales()
            // Stations serveur déjà chargées (retour de l'écran de la station) : redessinées. Sinon,
            // une saisie reprise avec un JDD connu les charge dès l'ouverture (demande terrain
            // 2026-08-31) ; hors ligne, repli sur le cache.
            if (serveurCharge) classer() else if (session.jddDefini) chargerServeur()
            // Des stations existent et aucune géométrie n'est en cours : le bandeau propose
            // sélection OU nouvelle saisie.
            if (locales.isNotEmpty() && edition.vide) bandeau = CarteOccHab.texteSelection(serveurCharge)
            if (consultation) {
                // VUE D'ENSEMBLE : toutes les stations, serveur (cache, tous JDD) et locales (toutes
                // saisies).
                val nbServeur = afficherToutLeCache()
                if (nbServeur == 0 && locales.isEmpty()) message(CarteOccHab.AUCUNE_STATION)
            }
        }
        return camera
    }

    /** Premier point GPS reçu depuis l'affichage : recentre si aucun cadrage n'a été imposé et
     *  qu'aucune géométrie n'est en cours. */
    fun premierePosition(p: LatLon) {
        if (!cadrageInitialFait && edition.vide) camera(CameraOccHab.Centre(p, CarteOccHab.ZOOM_POSITION))
    }

    /** La carte quittée : centre et zoom, repris au retour. */
    fun memoriserCadrage(centre: LatLon, zoom: Double) {
        session.dernierCadrageCarte = Triple(centre.latitude, centre.longitude, zoom)
    }

    // ─── Stations ───

    private fun pointsDe(g: GeometrieEdition): List<LatLon> = g.point?.let { listOf(it) } ?: g.sommets

    private suspend fun rafraichirLocales(): List<OccHabStation> {
        val locales = CarteOccHab.stationsLocales(store.charger(), session.station.id)
        stationsLocales = locales
        return locales
    }

    /** (Re)classe les stations du serveur : violettes (importables), ou déjà dessinées ailleurs. */
    private suspend fun classer() {
        val saisies = store.charger()
        val idsNonEnvoyes = saisies.flatMap { it.stations }.filterNot { it.envoyeGeoNature }.mapNotNull { it.idStationServeur }.toSet()
        val classees = CarteOccHab.classerServeur(stationsServeur, idsNonEnvoyes, store.copiesLocalesNonEnvoyees(), session.station.idStationServeur, session.saisieId)
        stationsViolettes = classees.violettes
        nbReprisesAilleurs = classees.nbReprisesAilleurs
    }

    /** Toutes les stations du cache, tous jeux de données confondus et SANS réseau — vue
     *  d'entrée dans OccHab, où aucun JDD n'est encore choisi. Rend leur nombre. */
    private suspend fun afficherToutLeCache(): Int {
        stationsServeur = services.stationsServeur.tout
        classer()
        return stationsServeur.size
    }

    /** Charge les stations de l'utilisateur DÉJÀ sur le serveur pour le JEU DE DONNÉES de la
     *  station (demande terrain 2026-08-27). Hors-ligne ou en erreur : REPLI sur le cache, avec sa
     *  date ; cache vide : l'erreur humanisée. [conserverLesAutresJdd] : les stations des autres
     *  JDD restent affichées depuis le cache (demande utilisateur 2026-10-18). L'emprise n'est
     *  jamais touchée : elle ne change que sur un geste de l'utilisateur. */
    private fun chargerServeur(conserverLesAutresJdd: Boolean = false) {
        val idJdd = session.details.idDataset
        if (idJdd == null || idJdd <= 0) {
            message(CarteOccHab.CHOISIR_JDD)
            return
        }
        viewModelScope.launch {
            var dateCache: Long? = null
            var nbIgnorees = 0
            val chargees = try {
                services.chargerStationsDuJdd(idJdd).also { nbIgnorees = it.nbIgnorees }.stations
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val cache = services.stationsServeur.lire(idJdd)
                if (cache.isEmpty()) {
                    message(humaniserErreurReseau(e), longue = true)
                    return@launch
                }
                dateCache = services.stationsServeur.dateChargement
                cache
            }
            stationsServeur = if (conserverLesAutresJdd) chargees + services.stationsServeur.tout.filter { it.idDataset != idJdd } else chargees
            jddCharge = idJdd
            classer()
            if (chargees.isNotEmpty()) {
                // Les stations qui ont déjà une copie locale à envoyer ne sont pas redessinées : le
                // message le dit.
                val idsNonEnvoyes = store.charger().flatMap { it.stations }.filterNot { it.envoyeGeoNature }.mapNotNull { it.idStationServeur }.toSet()
                val nbIci = chargees.count { st -> st.idStationServeur?.let { it in idsNonEnvoyes } == true }
                message(CarteOccHab.messageChargement(chargees.size, dateCache, nbIci, nbReprisesAilleurs, nbIgnorees), longue = true)
                if (aucuneGeometrieEnCours) bandeau = CarteOccHab.texteSelection(true)
                rafraichirLocales()
            }
            // Aucune station serveur pour ce compte et ce JDD : silence — la carte montre déjà ce
            // qu'il y a (constat terrain 2026-10-18).
        }
    }

    /** Le JDD de la station a pu changer (« i », bascule de saisie) : l'affichage reste celui du
     *  compte ET du JDD courant. JDD inchangé → simple re-classement ; changé → on efface, et on
     *  recharge. */
    private suspend fun resynchroniserAuJdd() {
        if (stationsServeur.isEmpty() && jddCharge == null) return
        val jdd = session.details.idDataset
        if (jdd == jddCharge) {
            classer()
            return
        }
        stationsServeur = emptyList()
        jddCharge = null
        classer()
        chargerServeur()
    }

    // ─── Touchers ───

    /** Toucher AVANT les couches, pendant le dessin d'une nouvelle géométrie : à portée d'un
     *  sommet voisin ([cible]), l'aimantage passe avant la sélection de la station touchée (choix
     *  de GeoMys-IOS, retenu par l'inventaire). Rend vrai s'il a servi. */
    fun toucherAvantCouches(cible: LatLon?): Boolean {
        if (!dessinEnCours || cible == null) return false
        return toucherCarte(cible, cible)
    }

    /** Tap sur la carte (`singleTapConfirmedHelper`), [cible] : le sommet voisin où s'aimanter. */
    fun toucherCarte(position: LatLon, cible: LatLon?): Boolean {
        // CONSULTATION : un tap dans le vide ne crée rien.
        if (!enCreation) return false
        // STATION EN ÉDITION : un tap n'a AUCUN effet (demande utilisateur 2026-10-18).
        if (geometrieChargee) return false
        memoriser()
        edition = edition.avecTap(cible ?: position)
        bandeau = CarteOccHab.texteMode(edition.mode, geometrieChargee)
        return true
    }

    /** Le corps du polygone édité avale le tap en réédition ; pendant le tracé d'un nouveau
     *  polygone, il le laisse passer pour ajouter un sommet à l'intérieur. */
    fun toucherFormeEditee(): Boolean = geometrieChargee

    /** Tap sur une station rouge : on l'édite, sauf si une station est déjà en édition (on en sort
     *  d'abord par « Annuler »). */
    fun toucherStationLocale(id: String) {
        if (geometrieChargee) return
        val st = stationsLocales.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { editer(st) }
    }

    /** Tap sur une station violette : confirmation avant IMPORT dans la saisie courante. */
    fun toucherStationServeur(id: String) {
        val st = stationsViolettes.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { proposerImport(st) }
    }

    // ─── Gestes sur la géométrie éditée ───

    fun debutGlisser() = memoriser()

    fun glisserPoint(p: LatLon) {
        edition = edition.copy(point = p)
    }

    /** Fin du glisser du point, [cible] déjà aimantée. */
    fun finGlisserPoint(cible: LatLon) {
        edition = edition.copy(point = cible)
        persisterSelection()
    }

    fun glisserSommet(index: Int, p: LatLon) {
        edition = edition.avecSommet(index, p)
    }

    /** Fin du glisser d'un sommet, aimanté sur le sommet d'une AUTRE station : le voisin, lui, ne
     *  bouge pas (demande utilisateur 2026-10-18) — l'aimantage pose un sommet exactement sur un
     *  autre, il ne les marie pas. */
    fun finGlisserSommet(index: Int, cible: LatLon) {
        edition = edition.avecSommet(index, cible)
        persisterSelection()
    }

    fun glisserSommetTrou(anneau: Int, index: Int, p: LatLon) {
        edition = edition.avecSommetTrou(anneau, index, p)
    }

    /** Fin du glisser d'un sommet de TROU : ni aimantage, mais un avertissement s'il sort du
     *  polygone (géométrie que PostGIS refuserait). */
    fun finGlisserSommetTrou(anneau: Int, index: Int, p: LatLon) {
        edition = edition.avecSommetTrou(anneau, index, p)
        if (edition.sommetTrouHorsPolygone(p)) message(CarteOccHab.SOMMET_TROU_HORS_POLYGONE, longue = true)
        persisterSelection()
    }

    /** Poignée « + » : un sommet au milieu de l'arête, persisté. Aucune station voisine n'est
     *  touchée, même sur une arête commune. */
    fun insererSommet(anneau: Int, indexArete: Int) {
        val apres = (if (anneau < 0) edition.avecSommetInsere(indexArete) else edition.avecSommetTrouInsere(anneau, indexArete)) ?: return
        memoriser()
        edition = apres
        persisterSelection()
    }

    fun changerMode(mode: ModeGeometrie) {
        if (mode != edition.mode) viderPile()
        edition = edition.copy(mode = mode)
        bandeau = CarteOccHab.texteMode(edition.mode, geometrieChargee)
    }

    // ─── Annuler, Valider ───

    private fun memoriser() {
        pile.empiler(edition)
        pileVide = false
    }

    private fun viderPile() {
        pile.vider()
        pileVide = true
    }

    /** « Annuler » = la DERNIÈRE opération sur la station en cours (demande terrain 2026-08-30,
     *  restreinte à cette station le 2026-10-18) ; plus rien à défaire : on abandonne la station
     *  sélectionnée ou la création engagée, et la carte revient à la vue d'ensemble. */
    fun annuler() {
        if (pile.estVide && (geometrieChargee || enCreation)) {
            abandonnerCreation()
            return
        }
        val instantane = pile.depiler() ?: return
        pileVide = pile.estVide
        edition = instantane
        bandeau = CarteOccHab.texteMode(edition.mode, geometrieChargee)
        if (geometrieChargee) persisterSelection()
    }

    /** Retour à l'état d'ENTRÉE : plus de station sélectionnée ni de tracé, « Ajouter une
     *  station », et la vue d'ensemble — stations serveur de tous les JDD, locales de toutes les
     *  saisies. */
    private fun abandonnerCreation() {
        deselectionner()
        enCreation = false
        viewModelScope.launch {
            afficherToutLeCache()
            rafraichirLocales()
        }
    }

    private fun deselectionner() {
        viderPile()
        // Station vierge, même saisie : l'ex-sélection redevient rouge (ou violette, si c'était un
        // import resté intact) et cliquable.
        session.nouvelleStation()
        edition = edition.copy(point = null, sommets = emptyList(), trous = emptyList())
        geometrieChargee = false
        viewModelScope.launch {
            rafraichirLocales()
            if (serveurCharge) classer()
            bandeau = CarteOccHab.texteSelection(serveurCharge)
        }
    }

    /** « Valider » : la géométrie (surface nette, altitudes MNT) est enregistrée AU FIL DE L'EAU,
     *  puis l'écran de la station — ses détails et ses habitats, FACULTATIFS. */
    fun valider() {
        val g = edition.geometrie() ?: return
        // Dessinée de zéro (et non reprise) : l'écran de la station l'annoncera « Nouvelle station ».
        if (!geometrieChargee) session.marquerStationNouvelle()
        session.definirGeometrie(g)
        // Surface AUTO (parité web, arrondie au m²), calculée localement, trous déduits.
        session.definirSurface(edition.surfaceM2())
        sessionVm.remplirAltitudes()
        viewModelScope.launch {
            if (!store.upsertStation(session.saisieId, session.stationAEnregistrer())) {
                echecEcriture = CarteOccHab.CONSIGNE_GEOMETRIE
                return@launch
            }
            viderPile()
            _evenements.send(EvenementCarteOccHab.OuvrirStation)
        }
    }

    /** Station SÉLECTIONNÉE : chaque geste est persisté au relâcher (surface recalculée,
     *  altitudes MNT) — « Valider » enchaîne sur la station, il n'enregistre pas seul. */
    private fun persisterSelection() {
        if (!geometrieChargee) return
        val g = edition
        if (g.mode == ModeGeometrie.POINT) {
            val p = g.point ?: return
            session.definirGeometrie("Point", p.latitude, p.longitude, null)
            session.definirSurface(null)
        } else {
            val geo = g.geometrie() ?: return
            session.definirGeometrie(geo)
            session.definirSurface(g.surfaceM2())
        }
        sessionVm.remplirAltitudes()
        ecrire(CarteOccHab.CONSIGNE_SOMMET)
    }

    private fun ecrire(consigne: String) {
        val station = session.stationAEnregistrer()
        val saisieId = session.saisieId
        viewModelScope.launch {
            if (!store.upsertStation(saisieId, station)) echecEcriture = consigne
        }
    }

    fun fermerEchecEcriture() {
        echecEcriture = null
    }

    // ─── Édition d'une station existante, import ───

    /** Recharge une station EXISTANTE pour l'éditer, sur place : sa géométrie devient l'objet en
     *  cours, les autres stations restent en lecture seule. */
    private suspend fun editer(st: OccHabStation) {
        // Station déjà envoyée : rééditable SEULEMENT si son id serveur est connu — elle repartira
        // en MISE À JOUR. Invariant « une seule copie locale à envoyer par station serveur ».
        if (st.envoyeGeoNature) {
            // La saisie rouverte n'a parfois que cette station, que la session a reprise : on
            // repart d'une station vierge, pour qu'aucun tracé ne remplace la géométrie envoyée.
            if (session.station.id == st.id) session.nouvelleStation()
            val idServeur = st.idStationServeur ?: 0
            if (idServeur > 0) {
                val copie = store.copieLocaleNonEnvoyee(idServeur)
                if (copie != null) {
                    if (copie.first.id == session.saisieId) editer(copie.second)
                    else dialogue = DialogueCarteOccHab.CopieLocale(copie.first, copie.second, traceEnCoursNonValide)
                    return
                }
                dialogue = DialogueCarteOccHab.ModifierEnvoyee(st)
            } else {
                message(CarteOccHab.ENVOYEE_NON_MODIFIABLE)
            }
            return
        }
        // La saisie ne s'ouvre qu'ici, une fois la station prise en édition. GeoMys-Android l'ouvrait
        // dès l'entrée : la question sur une station envoyée refusée, la carte restait en saisie
        // sans géométrie, et un tap y commençait une station sans ses informations obligatoires
        // (défaut n° 19 de l'inventaire).
        enCreation = true
        // La carte montre les stations de TOUTES les saisies : on rattache la session à celle de
        // la station, pour que détails, enregistrement et envoi portent sur la bonne.
        store.charger().firstOrNull { s -> s.stations.any { it.id == st.id } }?.let { session.rattacherASaisie(it.id) }
        session.reprendreStation(st)
        viderPile()
        cadrageInitialFait = false
        val chargee = GeometrieEdition.depuisStation(session.station)
        edition = chargee ?: GeometrieEdition(mode = edition.mode)
        geometrieChargee = chargee != null
        rafraichirLocales()
        // st sort du violet (en édition) ; une ex-sélection importée INTACTE y revient.
        if (serveurCharge) classer()
        bandeau = CarteOccHab.texteMode(edition.mode, geometrieChargee)
        // Emprise sur la station reprise, et sur rien d'autre.
        chargee?.let(::pointsDe)?.let(CarteOccHab::emprise)?.let {
            cadrageInitialFait = true
            camera(CameraOccHab.SurEmprise(it))
        }
        message(CarteOccHab.STATION_CHARGEE, longue = true)
    }

    /** « Modifier » une station déjà envoyée : elle repasse « à envoyer » — mais n'entre dans
     *  « Mes stations » qu'à sa première modification réelle (empreinte d'origine), et redevient
     *  « envoyée » si elle revient à l'identique. */
    fun modifierEnvoyee(st: OccHabStation) {
        dialogue = null
        viewModelScope.launch {
            editer(st.copy(envoyeGeoNature = false, envoiIncertain = false, derniereErreurEnvoi = null, origineEnvoyee = true))
            session.figerEmpreinteOrigine()
        }
    }

    private suspend fun proposerImport(st: OccHabStation) {
        // MULTI-PARTIES : l'appli n'en modélise que la 1ʳᵉ partie, la renvoyer supprimerait les
        // autres côté serveur.
        if (st.geometryPartielle) {
            dialogue = DialogueCarteOccHab.NonModifiable(st)
            return
        }
        // Filet : le store a pu changer depuis le dessin ; jamais de 2ᵉ copie.
        val copie = st.idStationServeur?.let { store.copieLocaleNonEnvoyee(it) }
        if (copie != null) {
            if (copie.first.id == session.saisieId) editer(copie.second)
            else dialogue = DialogueCarteOccHab.CopieLocale(copie.first, copie.second, traceEnCoursNonValide)
            return
        }
        dialogue = DialogueCarteOccHab.Import(st)
    }

    /** IMPORTE une station serveur dans la saisie courante, en CONSERVANT son id serveur (envoi en
     *  mise à jour). Rien n'est écrit : l'empreinte d'origine est figée. Seules ses DATES prennent
     *  celles de la session (une reprise sur le terrain est une nouvelle observation). */
    fun importer(st: OccHabStation) {
        dialogue = null
        val defauts = session.defautsSession
        viewModelScope.launch {
            editer(st.copy(envoyeGeoNature = false, envoiIncertain = false, dateMin = defauts.dateMin ?: st.dateMin, dateMax = defauts.dateMax ?: st.dateMax))
            session.figerEmpreinteOrigine()
            classer()
        }
    }

    /** Bascule sur la saisie qui détient la copie de la station, et l'y ouvre en édition. */
    fun ouvrirCopieLocale(saisie: OccHabSaisie, copie: OccHabStation) {
        dialogue = null
        viewModelScope.launch {
            // Version FRAÎCHE de la station (le store a pu bouger depuis le dessin).
            val aOuvrir = store.stationsDeSaisie(saisie.id).firstOrNull { it.id == copie.id } ?: copie
            session.reprendreSaisie(saisie)
            editer(aOuvrir)
            resynchroniserAuJdd()
        }
    }

    fun fermerDialogue() {
        dialogue = null
    }

    // ─── Détails de la station ───

    /** « Ajouter une station » : la création COMMENCE par les informations obligatoires (jeu de
     *  données en tête), pré-remplies avec celles du relevé précédent. Validées, la carte passe en
     *  dessin ; annulées, on reste sur la vue de consultation. */
    fun ajouterStation() {
        ouvrirDetails(demarrage = true) { saisies ->
            DetailsStation.appliquer(session, saisies, demarrage = true)
            DetailsStation.memoriser(config, session.details)
            enCreation = true
            // Le JDD est connu : ses stations sont rafraîchies, les autres restent affichées.
            chargerServeur(conserverLesAutresJdd = true)
            viewModelScope.launch { rafraichirLocales() }
            bandeau = CarteOccHab.texteMode(edition.mode, geometrieChargee)
        }
    }

    /** « i » : le formulaire COMPLET des détails de la station SÉLECTIONNÉE ; sans effet sinon. */
    fun ouvrirInfos() {
        if (!geometrieChargee) return
        // Altitudes / surface sont éditables mais EXCLUES de l'empreinte : comparées avant/après.
        val avant = session.deriveesManuelles()
        ouvrirDetails(demarrage = false) { saisies ->
            DetailsStation.appliquer(session, saisies, demarrage = false)
            session.forcerPersistanceSiDeriveesChangees(avant)
            // Persistance AU FIL DE L'EAU : pour une station importée intacte, c'est sa première
            // modification (bug terrain 2026-08-27).
            val station = session.stationAEnregistrer()
            val saisieId = session.saisieId
            viewModelScope.launch {
                if (!store.upsertStation(saisieId, station)) echecEcriture = CarteOccHab.CONSIGNE_DETAILS
                // Le JDD de la station a pu changer : les stations serveur le suivent.
                resynchroniserAuJdd()
            }
        }
    }

    private fun ouvrirDetails(demarrage: Boolean, puis: (SaisiesDetailsStation) -> Unit) {
        details = EtatDetailsStation(demarrage, annulable = !demarrage, session.details, session.station, config, services.nomenclatures)
        apresDetails = puis
    }

    fun validerDetails(saisies: SaisiesDetailsStation) {
        val puis = apresDetails
        details = null
        apresDetails = null
        puis?.invoke(saisies)
    }

    fun annulerDetails() {
        details = null
        apresDetails = null
    }

    // ─── Terminer la saisie ───

    /** Coche verte : TERMINER la saisie OccHab. Sans station enregistrée, on quitte ; sinon,
     *  quitter, envoyer, ou continuer. */
    fun terminer() {
        viewModelScope.launch {
            val saisie = store.charger().firstOrNull { it.id == session.saisieId }
            if (saisie == null || saisie.stations.isEmpty()) {
                _evenements.send(EvenementCarteOccHab.Accueil)
                return@launch
            }
            dialogue = DialogueCarteOccHab.Terminer(saisie, CarteOccHab.optionsTerminer(CarteOccHab.peutEnvoyer(config, saisie)))
        }
    }

    fun choisirFin(option: String, saisie: OccHabSaisie) {
        dialogue = null
        when (option) {
            CarteOccHab.ENREGISTRER_QUITTER -> _evenements.trySend(EvenementCarteOccHab.Accueil)
            CarteOccHab.ENREGISTRER_ENVOYER -> envoyer(saisie)
        }
    }

    /** Envoie TOUTE la saisie (envoi partiel sans perte), puis regagne l'accueil. */
    private fun envoyer(saisie: OccHabSaisie) {
        envoiEnCours = true
        viewModelScope.launch {
            try {
                val res = services.envoiOccHab.envoyerSaisieOccHabVersGeoNature(saisie, config)
                dialogue = DialogueCarteOccHab.ResultatEnvoi(if (res.succes) "Envoi" else "Erreur d'envoi", res.message)
            } finally {
                envoiEnCours = false
            }
        }
    }

    /** « OK » du résultat de l'envoi. */
    fun quitterApresEnvoi() {
        dialogue = null
        _evenements.trySend(EvenementCarteOccHab.Accueil)
    }

    // ─── Commandes de la carte ───

    /** « Centrer » : la position si elle est connue, sinon un message. */
    fun centrer(): LatLon? {
        val p = tracker.position.value
        if (p == null) {
            message(CarteOccHab.ACQUISITION_GPS)
            return null
        }
        return LatLon(p.latitude, p.longitude)
    }

    fun basculerBoussole() {
        carteSuitBoussole = !carteSuitBoussole
    }

    fun montrerChoixFond() {
        choixFondVisible = true
    }

    fun fermerChoixFond() {
        choixFondVisible = false
    }

    fun choisirFond(choisi: FondChoisi) {
        fond = choisi
        enregistrerFondChoisi(services.preferences, choisi)
    }

    /** La position s'affiche tant que la carte est à l'écran. Un parcours en cours (saisie OccTax)
     *  garde son récepteur : la carte ne l'arrête pas. */
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

    private fun camera(ordre: CameraOccHab) {
        _evenements.trySend(EvenementCarteOccHab.Camera(ordre))
    }

    private fun message(texte: String, longue: Boolean = false) {
        _evenements.trySend(EvenementCarteOccHab.Message(texte, longue))
    }
}
