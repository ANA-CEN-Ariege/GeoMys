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

// Graphe de navigation : nav_graph.xml de GeoMys-Android, NavigationStack et Route.swift de GeoMys-IOS.

package fr.ariegenature.geomys.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.DefaultNavTransitions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.ariegenature.geomys.InfosApp
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.store.configurationComplete
import fr.ariegenature.geomys.store.envoisEnAttente
import fr.ariegenature.geomys.store.io.mapDepuisJson
import fr.ariegenature.geomys.store.io.mapVersJson
import fr.ariegenature.geomys.ui.accueil.AccueilScreen
import fr.ariegenature.geomys.ui.accueil.ActionsAccueil
import fr.ariegenature.geomys.ui.accueil.EntreeMenuAccueil
import fr.ariegenature.geomys.ui.accueil.EtatAccueil
import fr.ariegenature.geomys.ui.cartes.MapsManagerScreen
import fr.ariegenature.geomys.ui.cartes.MapsManagerViewModel
import fr.ariegenature.geomys.ui.cartes.ZonesEssai
import fr.ariegenature.geomys.ui.cartes.lancerEssaiMapsManager
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.explorer.ExplorerEssai
import fr.ariegenature.geomys.ui.explorer.ExplorerScreen
import fr.ariegenature.geomys.ui.explorer.ExplorerViewModel
import fr.ariegenature.geomys.ui.occtax.MesSaisiesScreen
import fr.ariegenature.geomys.ui.occtax.MesSaisiesViewModel
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.occhab.detailsSessionParDefaut
import fr.ariegenature.geomys.outils.CADRAGE_OCCHAB_ESSAI
import fr.ariegenature.geomys.ui.occhab.CarteOccHabScreen
import fr.ariegenature.geomys.ui.occhab.CarteOccHabViewModel
import fr.ariegenature.geomys.ui.occhab.HabitatOccHabScreen
import fr.ariegenature.geomys.ui.occhab.HabitatOccHabViewModel
import fr.ariegenature.geomys.ui.occhab.MesStationsScreen
import fr.ariegenature.geomys.ui.occhab.MesStationsViewModel
import fr.ariegenature.geomys.ui.occhab.SessionOccHabViewModel
import fr.ariegenature.geomys.ui.occhab.StationOccHabScreen
import fr.ariegenature.geomys.ui.occhab.StationOccHabViewModel
import fr.ariegenature.geomys.occtax.GeometrieFinale
import fr.ariegenature.geomys.occtax.MesSaisies
import fr.ariegenature.geomys.occtax.OuvertureReleve
import fr.ariegenature.geomys.occtax.SaisieMono
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.ui.occtax.CaracterisationScreen
import fr.ariegenature.geomys.ui.occtax.CaracterisationViewModel
import fr.ariegenature.geomys.ui.occtax.DenombrementScreen
import fr.ariegenature.geomys.ui.occtax.DenombrementViewModel
import fr.ariegenature.geomys.ui.occtax.DetailsReleveIntercalaireScreen
import fr.ariegenature.geomys.ui.occtax.DetailsReleveIntercalaireViewModel
import fr.ariegenature.geomys.ui.occtax.ObservationsScreen
import fr.ariegenature.geomys.ui.occtax.ObservationsViewModel
import fr.ariegenature.geomys.ui.occtax.SaisieMonoScreen
import fr.ariegenature.geomys.ui.occtax.SaisieMonoViewModel
import fr.ariegenature.geomys.ui.occtax.SaisieMultiScreen
import fr.ariegenature.geomys.ui.occtax.SaisieReleveScreen
import fr.ariegenature.geomys.ui.occtax.SaisieReleveViewModel
import fr.ariegenature.geomys.ui.occtax.SaisieMultiViewModel
import fr.ariegenature.geomys.ui.occtax.SessionSaisieViewModel
import fr.ariegenature.geomys.ui.occtax.SortieDetailScreen
import fr.ariegenature.geomys.ui.occtax.SortieDetailViewModel
import fr.ariegenature.geomys.ui.parametres.ParametresScreen
import fr.ariegenature.geomys.ui.parametres.ParametresViewModel
import fr.ariegenature.geomys.ui.parametres.TaxonsListeScreen
import fr.ariegenature.geomys.monitoring.FIL_TYPE_ACCUEIL
import fr.ariegenature.geomys.monitoring.FIL_TYPE_MODULE
import fr.ariegenature.geomys.monitoring.FIL_TYPE_SUIVIS
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.monitoring.decoderFil
import fr.ariegenature.geomys.monitoring.encoderFil
import fr.ariegenature.geomys.monitoring.filRacineSuivis
import fr.ariegenature.geomys.ui.suivis.FicheObjetScreen
import fr.ariegenature.geomys.ui.suivis.CarteSuiviScreen
import fr.ariegenature.geomys.ui.suivis.CarteSuiviViewModel
import fr.ariegenature.geomys.ui.suivis.FicheObjetViewModel
import fr.ariegenature.geomys.ui.suivis.MesVisitesScreen
import fr.ariegenature.geomys.ui.suivis.MesVisitesViewModel
import fr.ariegenature.geomys.ui.suivis.NouvelleVisiteScreen
import fr.ariegenature.geomys.ui.suivis.NouvelleVisiteViewModel
import fr.ariegenature.geomys.ui.suivis.OuvertureNouvelleVisite
import fr.ariegenature.geomys.ui.suivis.SuiviDetailScreen
import fr.ariegenature.geomys.ui.suivis.SuiviDetailViewModel
import fr.ariegenature.geomys.ui.suivis.SuivisScreen
import fr.ariegenature.geomys.ui.suivis.SuivisViewModel
import fr.ariegenature.geomys.ui.suivis.resultat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object RouteAccueil

@Serializable
data object RouteParametres

/** « Mes saisies » : les sorties OccTax du store. */
@Serializable
data object RouteMesSaisies

/** « Détail de la saisie » d'une sortie de « Mes saisies ». */
@Serializable
data class RouteSortieDetail(val sortieId: String)

/** Carte de la saisie multi-taxons ; [sortieId] : la sortie reprise depuis « Mes saisies ». */
@Serializable
data class RouteSaisieMulti(val sortieId: String? = null)

/** Saisie d'un relevé : repris ([releveId], ou l'observation seule [obsId]), sinon nouveau, posé à
 *  sa géométrie ([geometrieType], [geometrieCoords], [latitude], [longitude]), avec les champs
 *  additionnels saisis dans l'écran intercalé ([additionnelsJson], le `addReleveJson` d'Android). */
@Serializable
data class RouteReleve(
    val releveId: String? = null,
    val obsId: String? = null,
    val geometrieType: String? = null,
    val geometrieCoords: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val additionnelsJson: String? = null,
) {
    fun ouverture() = OuvertureReleve(
        releveId = releveId,
        obsId = obsId,
        geometrie = geometrieType?.let { GeometrieFinale(it, geometrieCoords, latitude, longitude) },
        additionnelsIntercales = additionnelsJson?.let(::mapDepuisJson),
    )
}

/** Les relevés de la saisie en cours (bouton liste de la carte). */
@Serializable
data object RouteObservations

/** Saisie mono-taxon, avec les champs de relevé de l'écran intercalé ([additionnelsJson]). */
@Serializable
data class RouteSaisieMono(val additionnelsJson: String? = null)

/** Les champs additionnels obligatoires d'un nouveau relevé, avant ses espèces, posé à sa géométrie ;
 *  [mono] : avant la saisie mono-taxon, dont ils seront le défaut. */
@Serializable
data class RouteDetailsReleve(
    val geometrieType: String? = null,
    val geometrieCoords: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val mono: Boolean = false,
)

/** Caractérisation de l'espèce [index] du relevé ouvert ; [comportementSeul] : ouverte d'elle-même pour un
 *  oiseau nicheur ; [mono] : les réglages de la saisie mono-taxon. */
@Serializable
data class RouteCaracterisation(val index: Int, val comportementSeul: Boolean = false, val mono: Boolean = false)

/** Dénombrements de l'espèce [index] du relevé ouvert ; [mono] : les réglages de la saisie mono-taxon. */
@Serializable
data class RouteDenombrement(val index: Int, val mono: Boolean = false)

/** Contenu d'une liste de taxons du cache (Paramètres › Détails). */
@Serializable
data class RouteTaxonsListe(val idListe: Int, val nomListe: String)

/** « Monitoring » : les protocoles de suivi du serveur. */
@Serializable
data object RouteSuivis

/** Détail d'un protocole de suivi : ses sites (ou groupes de sites). Unique dans la pile : le fil
 *  d'Ariane y remonte. */
@Serializable
data class RouteSuiviDetail(val moduleCode: String)

/** Fiche d'un objet de suivi (site, visite, observation…) ; [fil] : le fil d'Ariane encodé
 *  (`encoderFil`), dont le dernier niveau est l'objet lui-même. */
@Serializable
data class RouteFicheObjet(val moduleCode: String, val objectType: String, val id: Int, val fil: String = "")

/** Nouvelle saisie des Suivis (visite, observation…) sous un parent serveur ([parentId]) ou local
 *  ([parentUuidLocal]), ou saisie en attente rouverte ([editUuid]) : les arguments de
 *  `nouvelleVisiteFragment` (cf. [OuvertureNouvelleVisite]) ; [fil] encodé (`encoderFil`). */
@Serializable
data class RouteNouvelleVisite(
    val moduleCode: String,
    val parentObjectType: String = "",
    val parentId: Int = -1,
    val titreSite: String = "",
    val editUuid: String = "",
    val fil: String = "",
    val childObjectType: String = "",
    val parentUuidLocal: String = "",
    val modeCompletion: Boolean = false,
    val messageEphemere: String = "",
) {
    fun ouverture() = OuvertureNouvelleVisite(
        moduleCode, parentObjectType, parentId, titreSite, editUuid, decoderFil(fil), childObjectType, parentUuidLocal, modeCompletion, messageEphemere,
    )
}

private fun OuvertureNouvelleVisite.route() = RouteNouvelleVisite(
    moduleCode, parentObjectType, parentId, titreSite, editUuid, encoderFil(fil), childObjectType, parentUuidLocal, modeCompletion, messageEphemere,
)

/** Carte des géométries d'un objet de suivi et de ses enfants ([objectType] « module » : les sites
 *  du protocole) ; [titre] : son nom, en badge ; [fil] encodé, dont il est le dernier niveau. */
@Serializable
data class RouteCarteSuivi(val moduleCode: String, val objectType: String, val id: Int, val titre: String = "", val fil: String = "")

/** « Mes visites » : la file d'envoi des Suivis. */
@Serializable
data object RouteMesVisites

/** La carte OccHab : la session OccHab est posée avant d'y venir (nouvelle, ou saisie reprise). */
@Serializable
data object RouteOccHabCarte

/** La station courante de la session OccHab : son résumé et ses habitats. */
@Serializable
data object RouteOccHabStation

/** Le formulaire d'un habitat de la station courante ; [habitatId] null : un nouvel habitat. */
@Serializable
data class RouteOccHabHabitat(val habitatId: String? = null)

/** « Mes stations » : les saisies OccHab du store. */
@Serializable
data object RouteMesStations

/** L'Explorer ; [essai] : sur les observations fictives d'[ExplorerEssai], sans serveur. */
@Serializable
data class RouteExplorer(val essai: Boolean = false)

/** Le Maps Manager ; [essai] : un essai d'affichage (`lancerEssaiMapsManager`). */
@Serializable
data class RouteMapsManager(val essai: String = "")


/** Le protocole des essais d'affichage du lot 7 (MonitoringEssai). */
private const val PROTOCOLE_ESSAI = "stom_ariege"
/** La saisie OccHab d'essai à envoyer, et sa station polygonale (OccHabEssai). */
private const val SAISIE_OCCHAB_ESSAI = "essai-occhab-a-envoyer"
private const val STATION_OCCHAB_ESSAI = "essai-occhab-pelouse"

/** Les écrans du flux de saisie OccTax (DESTINATIONS_SAISIE de MainActivity). */
private val DESTINATIONS_SAISIE = listOf(
    RouteSaisieMulti::class,
    RouteSaisieMono::class,
    RouteObservations::class,
    RouteDetailsReleve::class,
    RouteReleve::class,
    RouteCaracterisation::class,
    RouteDenombrement::class,
)

/** Navigue seulement si l'écran [depuis] est au premier plan : un double toucher n'empile pas deux
 *  fois l'écran suivant (`naviguerSur` de GeoMys-Android). */
private fun NavHostController.naviguerDepuis(depuis: NavBackStackEntry, route: Any, options: NavOptionsBuilder.() -> Unit = {}) {
    if (depuis.lifecycle.currentState == Lifecycle.State.RESUMED) navigate(route, options)
}

/** Un niveau du fil d'Ariane des Suivis touché (le dernier de [cible]) : `naviguerVersFil` de
 *  GeoMys-Android.
 *  - Accueil, liste des protocoles, protocole : on y remonte s'il est dans la pile, sinon on
 *    l'ouvre (le détail d'un protocole y est unique) ;
 *  - objet : sa fiche, en repliant d'un coup les niveaux au-dessus du détail du protocole quand il
 *    est présent ; sinon la fiche s'empile (édition depuis les saisies en attente). */
private fun NavHostController.naviguerVersFil(moduleCode: String, cible: List<FilSegment>) {
    val seg = cible.last()
    when (seg.type) {
        FIL_TYPE_ACCUEIL -> if (!popBackStack(RouteAccueil, inclusive = false)) navigate(RouteAccueil)
        FIL_TYPE_SUIVIS -> if (!popBackStack<RouteSuivis>(inclusive = false)) navigate(RouteSuivis)
        FIL_TYPE_MODULE -> if (!popBackStack<RouteSuiviDetail>(inclusive = false)) navigate(RouteSuiviDetail(moduleCode))
        else -> navigate(RouteFicheObjet(moduleCode, seg.type, seg.id, encoderFil(cible))) {
            popUpTo<RouteSuiviDetail> { inclusive = false }
        }
    }
}

/** La pile des écrans, posée sur l'accueil.
 *  - Transitions : aucune sous Android (le nav_graph de GeoMys-Android n'en déclare pas) ; celles
 *    d'une NavigationStack sous iOS (glissement latéral avec voile), par défaut dans navigation-compose.
 *  - Au lancement : une mise à jour qui exige un rechargement arme son drapeau, puis une
 *    configuration incomplète ouvre Paramètres, d'office et sans animation, comme MainActivity
 *    (GeoMys-Android) et ContentView (GeoMys-IOS). [ecranInitial] « accueil » s'en dispense (essais
 *    d'affichage), « parametres » l'impose ; essais du lot 6 : « mes-saisies » ouvre « Mes saisies »,
 *    « detail-saisie » le détail de la saisie la plus récente, « saisie-multi » une nouvelle saisie
 *    multi-taxons, « saisie-suivante » la même carte en positionnement (comme au retour d'un relevé),
 *    « reprise-saisie » la reprise de la plus récente saisie non envoyée, « releve-essai » son premier
 *    relevé, « releve-nouveau » un nouveau relevé ponctuel, « caracterisation-essai » et
 *    « denombrement-essai » la caractérisation et les dénombrements de la première espèce de ce relevé,
 *    « details-releve » et « details-communs » le nouveau relevé et la carte, leur dialogue de détails
 *    ouvert, « details-intercale » l'écran des champs du relevé intercalé avant ses espèces,
 *    « observations-essai » la liste des relevés de la plus récente saisie non envoyée, « saisie-mono » la
 *    saisie mono-taxon, « saisie-mono-carte » sa carte, démarrée sur le Merle noir (référentiel d'essai) ;
 *    essais du lot 7 : « suivis » la liste des protocoles, « suivi-detail » le détail du protocole d'essai
 *    STOM Ariège (`action=monitoring-essai`), « fiche-objet » la fiche de son site Col de Port,
 *    « nouvelle-visite » une visite de ce site (« nouvelle-visite-observateurs » : le choix des
 *    observateurs ouvert), « nouvelle-observation » une observation de sa visite en attente d'envoi
 *    (chaîne de saisies), « mes-visites » la file d'envoi des Suivis, « carte-site » la carte du Col de Port,
 *    « carte-mares » celle du protocole d'essai des mares (polygones, dont un à trou) ; essais du lot 8
 *    (`action=occhab-essai`) : « occhab-carte » une nouvelle session OccHab (« occhab-infos » : ses
 *    informations obligatoires ouvertes), « occhab-reprise » la reprise de la saisie d'essai à envoyer
 *    (« occhab-edition » : sa station polygonale sélectionnée sur la carte),
 *    « occhab-station » sa station polygonale (« occhab-details » : ses détails ouverts), « occhab-habitat »
 *    le premier habitat de celle-ci, « occhab-habitat-nouveau » un nouvel habitat, « mes-stations » la
 *    liste des saisies OccHab ; essais du lot 9 : « explorer » l'Explorer (« explorer-essai » : sur des
 *    observations fictives, près de Foix), « maps-manager » le Maps Manager (« maps-cartes » : la liste des
 *    cartes MBTiles ouverte ; « maps-telechargement » : un téléchargement simulé, sans réseau, à 40 %). */
@Composable
fun NavigationGeoMys(infos: InfosApp, services: ServicesGeoNature, ecranInitial: String? = null) {
    val nav = rememberNavController()
    val ios = estIos
    var demarrage by remember { mutableStateOf(true) }
    // Essai d'affichage : le dialogue des détails ouvert d'emblée (le simulateur iOS ne touche pas).
    var essaiDetails by remember { mutableStateOf(ecranInitial == "details-releve" || ecranInitial == "details-communs") }
    var essaiMonoCarte by remember { mutableStateOf(ecranInitial == "saisie-mono-carte") }
    var essaiObservateurs by remember { mutableStateOf(ecranInitial == "nouvelle-visite-observateurs") }
    var essaiInfosOccHab by remember { mutableStateOf(ecranInitial == "occhab-infos") }
    var essaiEditionOccHab by remember { mutableStateOf(ecranInitial == "occhab-edition") }
    var essaiDetailsStation by remember { mutableStateOf(ecranInitial == "occhab-details") }
    // La session de saisie OccTax, partagée par tous les écrans du flux comme le TraceViewModel
    // (`activityViewModels()`) de GeoMys-Android : elle vit au-dessus du graphe.
    val sessionSaisie = viewModel { SessionSaisieViewModel(services) }
    // La session OccHab, partagée de même par la carte, la station, l'habitat et « Mes stations »
    // (OccHabViewModel, `activityViewModels()`).
    val sessionOccHab = viewModel { SessionOccHabViewModel() }
    // Quitter le flux de saisie DANS l'appli (et non passer en arrière-plan) : sur une vraie
    // transition saisie → hors saisie seulement.
    LaunchedEffect(nav) {
        var dansFlux = false
        nav.currentBackStackEntryFlow.collect { entree ->
            val dansSaisie = DESTINATIONS_SAISIE.any { entree.destination.hasRoute(it) }
            if (dansFlux && !dansSaisie) sessionSaisie.quitterFluxSaisie()
            dansFlux = dansSaisie
        }
    }
    LaunchedEffect(Unit) {
        val ouvrirParametres = when (ecranInitial) {
            "accueil", "mes-saisies", "detail-saisie", "saisie-multi", "saisie-suivante", "reprise-saisie", "releve-essai", "releve-nouveau",
            "caracterisation-essai", "denombrement-essai", "details-releve", "details-communs", "details-intercale",
            "observations-essai", "saisie-mono", "saisie-mono-carte", "suivis", "suivi-detail", "fiche-objet",
            "nouvelle-visite", "nouvelle-visite-observateurs", "nouvelle-observation", "mes-visites", "carte-site", "carte-mares",
            "occhab-carte", "occhab-infos", "occhab-reprise", "occhab-edition", "occhab-station", "occhab-details", "occhab-habitat", "occhab-habitat-nouveau",
            "mes-stations", "explorer", "explorer-essai", "maps-manager", "maps-cartes", "maps-telechargement" -> false
            "parametres", "parametres-selections" -> true
            else -> !configurationComplete(services.config, services.taxRef)
        }
        if (ouvrirParametres) nav.navigate(RouteParametres)
        val essaisSuivis = setOf(
            "suivis", "suivi-detail", "fiche-objet", "nouvelle-visite", "nouvelle-visite-observateurs", "nouvelle-observation", "carte-site", "carte-mares",
        )
        if (ecranInitial in essaisSuivis) nav.navigate(RouteSuivis)
        if (ecranInitial == "carte-mares") {
            resultat { services.monitoring.chargerModulesDepuisCache() }
            nav.navigate(RouteCarteSuivi("mares_ana", "module", 102, "Suivi des mares", encoderFil(filRacineSuivis("Suivi des mares"))))
        }
        if (ecranInitial in essaisSuivis - "suivis" - "carte-mares") {
            // Le détail lit le protocole dans la liste chargée : on la charge d'abord.
            resultat { services.monitoring.chargerModulesDepuisCache() }
            nav.navigate(RouteSuiviDetail(PROTOCOLE_ESSAI))
        }
        if (ecranInitial in essaisSuivis - "suivis" - "suivi-detail" - "carte-mares") {
            val fil = filRacineSuivis("STOM Ariège") + FilSegment("site", 501, "Col de Port")
            nav.navigate(RouteFicheObjet(PROTOCOLE_ESSAI, "site", 501, encoderFil(fil)))
            if (ecranInitial == "carte-site") nav.navigate(RouteCarteSuivi(PROTOCOLE_ESSAI, "site", 501, "Col de Port", encoderFil(fil)))
            if (ecranInitial == "nouvelle-visite" || ecranInitial == "nouvelle-visite-observateurs") {
                nav.navigate(RouteNouvelleVisite(PROTOCOLE_ESSAI, "site", 501, "Col de Port", fil = encoderFil(fil), childObjectType = "visit"))
            }
            if (ecranInitial == "nouvelle-observation") {
                nav.navigate(
                    RouteNouvelleVisite(
                        PROTOCOLE_ESSAI, "visit", titreSite = "Visite (local)", fil = encoderFil(fil),
                        childObjectType = "observation", parentUuidLocal = "essai-visite-col-de-port",
                    ),
                )
            }
        }
        if (ecranInitial == "mes-saisies" || ecranInitial == "detail-saisie") nav.navigate(RouteMesSaisies)
        if (ecranInitial == "mes-visites") nav.navigate(RouteMesVisites)
        if (ecranInitial == "mes-stations") nav.navigate(RouteMesStations)
        if (ecranInitial == "explorer" || ecranInitial == "explorer-essai") nav.navigate(RouteExplorer(essai = ecranInitial == "explorer-essai"))
        when (ecranInitial) {
            "maps-manager" -> nav.navigate(RouteMapsManager())
            "maps-cartes" -> nav.navigate(RouteMapsManager(essai = "cartes"))
            "maps-telechargement" -> nav.navigate(RouteMapsManager(essai = "telechargement"))
        }
        if (ecranInitial == "occhab-carte" || ecranInitial == "occhab-infos") {
            sessionOccHab.session.demarrerSession(detailsSessionParDefaut(services.config))
            sessionOccHab.session.dernierCadrageCarte = CADRAGE_OCCHAB_ESSAI
            nav.navigate(RouteOccHabCarte)
        }
        if (ecranInitial in setOf("occhab-reprise", "occhab-edition", "occhab-station", "occhab-details", "occhab-habitat", "occhab-habitat-nouveau")) {
            services.occHabStore.charger().firstOrNull { it.id == SAISIE_OCCHAB_ESSAI }?.let { saisie ->
                val session = sessionOccHab.session
                session.reprendreSaisie(saisie)
                session.dernierCadrageCarte = CADRAGE_OCCHAB_ESSAI
                nav.navigate(RouteOccHabCarte)
                val station = saisie.stations.firstOrNull { it.id == STATION_OCCHAB_ESSAI }
                if (ecranInitial != "occhab-reprise" && ecranInitial != "occhab-edition" && station != null) {
                    session.reprendreStation(station)
                    nav.navigate(RouteOccHabStation)
                    if (ecranInitial == "occhab-habitat") nav.navigate(RouteOccHabHabitat(station.habitats.firstOrNull()?.id))
                    if (ecranInitial == "occhab-habitat-nouveau") nav.navigate(RouteOccHabHabitat())
                }
            }
        }
        if (ecranInitial == "detail-saisie") services.sortieStore.charger().firstOrNull()?.let { nav.navigate(RouteSortieDetail(it.id)) }
        if (ecranInitial == "saisie-multi" || ecranInitial == "saisie-suivante" || ecranInitial == "details-communs") {
            sessionSaisie.demarrerSaisieSuivante = ecranInitial == "saisie-suivante"
            nav.navigate(RouteSaisieMulti())
        }
        if (ecranInitial == "reprise-saisie" || ecranInitial == "observations-essai") {
            services.sortieStore.charger().firstOrNull { !it.envoyeGeoNature }?.let { sortie ->
                nav.navigate(RouteSaisieMulti(sortie.id))
                if (ecranInitial == "observations-essai") {
                    // La liste montre la session : on attend que la sortie y soit reprise.
                    while (sessionSaisie.session.sortieEnEditionId != sortie.id) withFrameNanos {}
                    nav.navigate(RouteObservations)
                }
            }
        }
        if (ecranInitial in setOf("releve-essai", "caracterisation-essai", "denombrement-essai")) {
            services.sortieStore.charger().firstOrNull { !it.envoyeGeoNature && it.observations.isNotEmpty() }?.let { sortie ->
                nav.navigate(RouteSaisieMulti(sortie.id))
                // Le relevé ne s'ouvre qu'une fois la sortie reprise dans la session.
                while (sessionSaisie.session.sortieEnEditionId != sortie.id) withFrameNanos {}
                val premiere = sortie.observations.first()
                nav.navigate(RouteReleve(releveId = premiere.releveId, obsId = premiere.id.takeIf { premiere.releveId == null }))
                if (ecranInitial == "caracterisation-essai") nav.navigate(RouteCaracterisation(0))
                if (ecranInitial == "denombrement-essai") nav.navigate(RouteDenombrement(0))
            }
        }
        if (ecranInitial == "saisie-mono" || ecranInitial == "saisie-mono-carte") {
            sessionSaisie.session.reinitialiser()
            nav.navigate(RouteSaisieMono())
        }
        if (ecranInitial == "details-intercale") {
            sessionSaisie.session.typeSaisieLabel = SaisieMultiViewModel.LIBELLE
            nav.navigate(RouteSaisieMulti())
            nav.navigate(RouteDetailsReleve("Point", latitude = 42.9649, longitude = 1.6056))
        }
        if (ecranInitial == "releve-nouveau" || ecranInitial == "details-releve") {
            // La carte, sautée, aurait posé le libellé du flux.
            sessionSaisie.session.typeSaisieLabel = SaisieMultiViewModel.LIBELLE
            nav.navigate(RouteSaisieMulti())
            nav.navigate(RouteReleve(geometrieType = "Point", latitude = 42.9649, longitude = 1.6056))
        }
        // Les transitions reprennent une fois la redirection posée.
        withFrameNanos {}
        withFrameNanos {}
        demarrage = false
    }
    NavHost(
        navController = nav,
        startDestination = RouteAccueil,
        enterTransition = { if (ios && !demarrage) DefaultNavTransitions.enterTransition(this) else EnterTransition.None },
        exitTransition = { if (ios && !demarrage) DefaultNavTransitions.exitTransition(this) else ExitTransition.None },
        popEnterTransition = {
            if (ios) DefaultNavTransitions.popEnterTransition(DefaultNavTransitions.enterTransition)(this) else EnterTransition.None
        },
        popExitTransition = {
            if (ios) DefaultNavTransitions.popExitTransition(DefaultNavTransitions.exitTransition)(this) else ExitTransition.None
        },
    ) {
        composable<RouteAccueil> { EcranAccueil(infos, services, sessionSaisie, sessionOccHab, nav) }
        composable<RouteParametres> {
            val vm = viewModel { ParametresViewModel(services) }
            ParametresScreen(
                vm,
                onFermer = { nav.popBackStack() },
                onTaxonsListe = { id, nom -> nav.navigate(RouteTaxonsListe(id, nom)) },
                defilerEnBas = ecranInitial == "parametres-selections",
            )
        }
        composable<RouteTaxonsListe> { entree ->
            val route = entree.toRoute<RouteTaxonsListe>()
            TaxonsListeScreen(services, route.idListe, route.nomListe, onRetour = { nav.popBackStack() })
        }
        composable<RouteSuivis> { entree ->
            val vm = viewModel { SuivisViewModel(services) }
            SuivisScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onDetail = { m -> nav.naviguerDepuis(entree, RouteSuiviDetail(m.moduleCode)) },
                onCarte = { m ->
                    nav.naviguerDepuis(entree, RouteCarteSuivi(m.moduleCode, "module", m.idModule, m.moduleLabel, encoderFil(filRacineSuivis(m.moduleLabel))))
                },
                onAttente = { nav.naviguerDepuis(entree, RouteMesVisites) },
            )
        }
        composable<RouteSuiviDetail> { entree ->
            val route = entree.toRoute<RouteSuiviDetail>()
            val vm = viewModel { SuiviDetailViewModel(services, route.moduleCode) }
            SuiviDetailScreen(
                vm,
                onNiveau = { cible -> nav.naviguerVersFil(route.moduleCode, cible) },
                onFiche = { l -> nav.naviguerDepuis(entree, RouteFicheObjet(route.moduleCode, l.type, l.id, encoderFil(vm.filDe(l)))) },
                onCarte = { l -> nav.naviguerDepuis(entree, RouteCarteSuivi(route.moduleCode, l.type, l.id, l.nom, encoderFil(vm.filDe(l)))) },
                onNouvelle = { l ->
                    nav.naviguerDepuis(
                        entree,
                        RouteNouvelleVisite(
                            route.moduleCode, l.type, l.id, l.nom, fil = encoderFil(vm.filDe(l)), childObjectType = l.saisieEnfant.orEmpty(),
                        ),
                    )
                },
            )
        }
        composable<RouteFicheObjet> { entree ->
            val route = entree.toRoute<RouteFicheObjet>()
            if (route.id <= 0) {
                LaunchedEffect(Unit) { nav.popBackStack() }
                return@composable
            }
            val vm = viewModel { FicheObjetViewModel(services, route.moduleCode, route.objectType, route.id, decoderFil(route.fil)) }
            FicheObjetScreen(
                vm,
                onNiveau = { cible -> nav.naviguerVersFil(route.moduleCode, cible) },
                onFiche = { e -> nav.naviguerDepuis(entree, RouteFicheObjet(route.moduleCode, e.type, e.id, encoderFil(e.fil))) },
                onCarte = { e -> nav.naviguerDepuis(entree, RouteCarteSuivi(route.moduleCode, e.type, e.id, e.nom, encoderFil(e.fil))) },
                onNouvelle = { n ->
                    nav.naviguerDepuis(
                        entree,
                        RouteNouvelleVisite(
                            route.moduleCode, n.parentObjectType, n.parentId, n.titreSite, fil = encoderFil(n.fil),
                            childObjectType = n.childObjectType, parentUuidLocal = n.parentUuidLocal.orEmpty(),
                        ),
                    )
                },
            )
        }
        composable<RouteCarteSuivi> { entree ->
            val route = entree.toRoute<RouteCarteSuivi>()
            val vm = viewModel { CarteSuiviViewModel(services, route.moduleCode, route.objectType, route.id, route.titre, decoderFil(route.fil)) }
            CarteSuiviScreen(
                vm,
                onNiveau = { cible -> nav.naviguerVersFil(route.moduleCode, cible) },
                onFiche = { type, id, fil -> nav.naviguerDepuis(entree, RouteFicheObjet(route.moduleCode, type, id, encoderFil(fil))) },
                onNouvelleSaisie = { o -> nav.naviguerDepuis(entree, o.route()) },
            )
        }
        composable<RouteMesVisites> { entree ->
            val vm = viewModel { MesVisitesViewModel(services) }
            MesVisitesScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onEditer = { o -> nav.naviguerDepuis(entree, o.route()) },
            )
        }
        composable<RouteNouvelleVisite> { entree ->
            val route = entree.toRoute<RouteNouvelleVisite>()
            val vm = viewModel { NouvelleVisiteViewModel(services, route.ouverture()) }
            // Essai d'affichage : le choix des observateurs ouvert d'emblée (le simulateur iOS ne touche pas).
            val choixInitial = remember { if (essaiObservateurs) "observers" else null }
            if (essaiObservateurs) LaunchedEffect(vm) { essaiObservateurs = false }
            // Le formulaire suivant ou la fiche du parent REMPLACENT celui-ci : « retour » ramène au fil.
            NouvelleVisiteScreen(
                vm,
                onNiveau = { cible -> nav.naviguerVersFil(vm.moduleCodeCourant, cible) },
                onRetour = { if (nav.currentBackStackEntry?.id == entree.id) nav.popBackStack() },
                onRemplacer = { o -> nav.navigate(o.route()) { popUpTo<RouteNouvelleVisite> { inclusive = true } } },
                onFiche = { f ->
                    nav.navigate(RouteFicheObjet(f.moduleCode, f.objectType, f.id, encoderFil(f.fil))) {
                        popUpTo<RouteNouvelleVisite> { inclusive = true }
                    }
                },
                choixMultipleInitial = choixInitial,
            )
        }
        composable<RouteMesSaisies> {
            val vm = viewModel { MesSaisiesViewModel(services) }
            MesSaisiesScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onDetail = { id -> nav.navigate(RouteSortieDetail(id)) },
                onContinuer = { sortie ->
                    if (sortie.saisieMono) {
                        // Saisie mono-taxon : elle se rouvre en mono, reprise dans la session.
                        sessionSaisie.session.reprendreSortie(sortie)
                        val additionnels = MesSaisies.additionnelsPourContinuerEnMono(sortie)
                        nav.navigate(RouteSaisieMono(additionnels.takeIf { it.isNotEmpty() }?.let(::mapVersJson)))
                    } else {
                        // Recharger la sortie et recadrer la carte dessus, même déjà éditée dans la session.
                        sessionSaisie.session.forcerRepriseAuProchainEcran()
                        nav.navigate(RouteSaisieMulti(sortie.id))
                    }
                },
            )
        }
        composable<RouteSaisieMulti> { entree ->
            val route = entree.toRoute<RouteSaisieMulti>()
            val vm = viewModel { SaisieMultiViewModel(services, sessionSaisie, route.sortieId) }
            if (essaiDetails && ecranInitial == "details-communs") {
                LaunchedEffect(vm) {
                    essaiDetails = false
                    vm.ouvrirDetailsCommuns()
                }
            }
            SaisieMultiScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onOuvrirReleve = { releve ->
                    val g = releve.geometrie
                    nav.naviguerDepuis(
                        entree,
                        if (releve.ecranReleve && g != null) RouteDetailsReleve(g.type, g.coordsJson, g.latitude, g.longitude)
                        else RouteReleve(releve.releveId, releve.obsId, g?.type, g?.coordsJson, g?.latitude ?: 0.0, g?.longitude ?: 0.0),
                    )
                },
                onOuvrirListe = { nav.naviguerDepuis(entree, RouteObservations) },
                onQuitter = { nav.popBackStack() },
            )
        }
        composable<RouteSortieDetail> { entree ->
            val route = entree.toRoute<RouteSortieDetail>()
            val vm = viewModel(key = route.sortieId) { SortieDetailViewModel(services, route.sortieId) }
            SortieDetailScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onRetour = { nav.popBackStack() },
            )
        }
        composable<RouteObservations> { entree ->
            val vm = viewModel { ObservationsViewModel(services, sessionSaisie) }
            ObservationsScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onFermer = { nav.popBackStack() },
                onEditer = { releve ->
                    val rid = releve.releveId
                    nav.naviguerDepuis(entree, if (!rid.isNullOrEmpty()) RouteReleve(releveId = rid) else RouteReleve(obsId = releve.premier.id))
                },
            )
        }
        composable<RouteSaisieMono> { entree ->
            val route = entree.toRoute<RouteSaisieMono>()
            val vm = viewModel { SaisieMonoViewModel(services, sessionSaisie, route.additionnelsJson?.let(::mapDepuisJson)) }
            if (essaiMonoCarte) {
                LaunchedEffect(vm) {
                    essaiMonoCarte = false
                    vm.champ.saisir("Merle noir")
                    vm.champ.recherche.poser(TaxRefStatut.Trouve(4117, "Turdus merula", "Merle noir"), "Merle noir")
                    vm.demarrer()
                }
            }
            SaisieMonoScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onCaracterisation = { nav.naviguerDepuis(entree, RouteCaracterisation(0, mono = true)) },
                onDenombrement = { nav.naviguerDepuis(entree, RouteDenombrement(0, mono = true)) },
                onEditerObservation = { id -> nav.naviguerDepuis(entree, RouteReleve(obsId = id)) },
                onQuitter = { nav.popBackStack() },
            )
        }
        composable<RouteDetailsReleve> { entree ->
            val route = entree.toRoute<RouteDetailsReleve>()
            val vm = viewModel { DetailsReleveIntercalaireViewModel(services, sessionSaisie) }
            DetailsReleveIntercalaireScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onAnnuler = { nav.popBackStack() },
                onContinuer = { valeurs ->
                    // L'écran quitte la pile : le retour depuis la saisie ramène à l'écran d'avant.
                    val suite = if (route.mono) {
                        RouteSaisieMono(additionnelsJson = mapVersJson(valeurs))
                    } else {
                        RouteReleve(
                            geometrieType = route.geometrieType,
                            geometrieCoords = route.geometrieCoords,
                            latitude = route.latitude,
                            longitude = route.longitude,
                            additionnelsJson = mapVersJson(valeurs),
                        )
                    }
                    nav.naviguerDepuis(entree, suite) { popUpTo<RouteDetailsReleve> { inclusive = true } }
                },
            )
        }
        composable<RouteReleve> { entree ->
            val route = entree.toRoute<RouteReleve>()
            val vm = viewModel { SaisieReleveViewModel(services, sessionSaisie, route.ouverture()) }
            if (vm.releve.introuvable) {
                LaunchedEffect(Unit) { nav.popBackStack() }
                return@composable
            }
            if (essaiDetails && ecranInitial == "details-releve") {
                LaunchedEffect(vm) {
                    essaiDetails = false
                    vm.ouvrirDetails()
                }
            }
            SaisieReleveScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onCaracterisation = { index, seul -> nav.naviguerDepuis(entree, RouteCaracterisation(index, seul)) },
                onDenombrement = { nav.naviguerDepuis(entree, RouteDenombrement(it)) },
                onTermine = {
                    // Comme le drapeau que GeoMys-Android pose sur l'écran précédent : seule la carte
                    // multi-taxons repart en positionnement (pas la saisie mono, ni la liste).
                    if (nav.previousBackStackEntry?.destination?.hasRoute<RouteSaisieMulti>() == true) {
                        sessionSaisie.demarrerSaisieSuivante = true
                    }
                    nav.popBackStack()
                },
            )
        }
        composable<RouteCaracterisation> { entree ->
            val route = entree.toRoute<RouteCaracterisation>()
            if (route.mono) {
                val mono = saisieMonoOuverte(nav, entree, services, sessionSaisie)
                val espece = remember(mono) { mono?.especeFormulaires() }
                if (mono == null || espece == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                    return@composable
                }
                val vm = viewModel { CaracterisationViewModel(services, espece, comportementSeul = false, mono.saisie.idDatasetEffectif()) }
                CaracterisationScreen(
                    vm,
                    sessionSaisie.session.typeSaisieLabel,
                    onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                    onRetour = { if (nav.currentBackStackEntry?.id == entree.id) nav.popBackStack() },
                    onValide = { caracterisee ->
                        mono.appliquerCaracterisation(caracterisee)
                        nav.popBackStack()
                    },
                )
                return@composable
            }
            val releve = releveOuvert(nav, entree, services, sessionSaisie)
            val espece = remember(releve) { releve?.releve?.especes?.value?.getOrNull(route.index) }
            if (releve == null || espece == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
                return@composable
            }
            val vm = viewModel { CaracterisationViewModel(services, espece, route.comportementSeul, releve.releve.idDatasetEffectif()) }
            CaracterisationScreen(
                vm,
                releve.typeSaisieLabel,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onRetour = { if (nav.currentBackStackEntry?.id == entree.id) nav.popBackStack() },
                onValide = { caracterisee ->
                    releve.releve.modifier(route.index) { caracterisee }
                    nav.popBackStack()
                },
            )
        }
        composable<RouteDenombrement> { entree ->
            val route = entree.toRoute<RouteDenombrement>()
            if (route.mono) {
                val mono = saisieMonoOuverte(nav, entree, services, sessionSaisie)
                val espece = remember(mono) { mono?.especeFormulaires() }
                if (mono == null || espece == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                    return@composable
                }
                val vm = viewModel { DenombrementViewModel(services, espece, mono.saisie.idDatasetEffectif()) }
                DenombrementScreen(
                    vm,
                    sessionSaisie.session.typeSaisieLabel,
                    onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                    onRetour = { if (nav.currentBackStackEntry?.id == entree.id) nav.popBackStack() },
                    onValide = { liste ->
                        mono.appliquerDenombrements(liste)
                        nav.popBackStack()
                    },
                )
                return@composable
            }
            val releve = releveOuvert(nav, entree, services, sessionSaisie)
            val espece = remember(releve) { releve?.releve?.especes?.value?.getOrNull(route.index) }
            if (releve == null || espece == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
                return@composable
            }
            val vm = viewModel { DenombrementViewModel(services, espece, releve.releve.idDatasetEffectif()) }
            DenombrementScreen(
                vm,
                releve.typeSaisieLabel,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onRetour = { if (nav.currentBackStackEntry?.id == entree.id) nav.popBackStack() },
                onValide = { liste ->
                    releve.releve.modifier(route.index) { it.avecDenombrements(liste) }
                    nav.popBackStack()
                },
            )
        }
        composable<RouteOccHabCarte> { entree ->
            val vm = viewModel { CarteOccHabViewModel(services, sessionOccHab) }
            // Essai d'affichage : les informations obligatoires ouvertes d'emblée (le simulateur iOS ne touche pas).
            if (essaiInfosOccHab) {
                LaunchedEffect(vm) {
                    vm.ajouterStation()
                    essaiInfosOccHab = false
                }
            }
            // Essai d'affichage : la station polygonale d'essai sélectionnée, une fois dessinée.
            if (essaiEditionOccHab) {
                LaunchedEffect(vm) {
                    snapshotFlow { vm.stationsLocales }.first { locales -> locales.any { it.id == STATION_OCCHAB_ESSAI } }
                    vm.toucherStationLocale(STATION_OCCHAB_ESSAI)
                    essaiEditionOccHab = false
                }
            }
            CarteOccHabScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onStation = { nav.naviguerDepuis(entree, RouteOccHabStation) },
            )
        }
        composable<RouteOccHabStation> { entree ->
            val vm = viewModel { StationOccHabViewModel(services, sessionOccHab) }
            // Essai d'affichage : les détails de la station ouverts d'emblée.
            if (essaiDetailsStation) {
                LaunchedEffect(vm) {
                    vm.ouvrirDetails()
                    essaiDetailsStation = false
                }
            }
            StationOccHabScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onHabitat = { id -> nav.naviguerDepuis(entree, RouteOccHabHabitat(id)) },
                // Carte VIERGE pour la station suivante, sans empiler les écrans précédents.
                onCarteSuivante = { nav.navigate(RouteOccHabCarte) { popUpTo(RouteAccueil) { inclusive = false } } },
            )
        }
        composable<RouteOccHabHabitat> { entree ->
            val route = entree.toRoute<RouteOccHabHabitat>()
            val vm = viewModel { HabitatOccHabViewModel(services, sessionOccHab, route.habitatId) }
            HabitatOccHabScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                // Habitat validé : retour à la station (l'écran de l'habitat quitte la pile).
                onRetour = { if (nav.currentBackStackEntry?.id == entree.id) nav.popBackStack() },
            )
        }
        composable<RouteMesStations> { entree ->
            val vm = viewModel { MesStationsViewModel(services, sessionOccHab) }
            MesStationsScreen(
                vm,
                onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) },
                onCarte = { nav.naviguerDepuis(entree, RouteOccHabCarte) },
            )
        }
        composable<RouteExplorer> { entree ->
            val route = entree.toRoute<RouteExplorer>()
            val vm = viewModel {
                if (route.essai) {
                    ExplorerViewModel(services, source = { ExplorerEssai.observations(it) }, configure = true, departImpose = ExplorerEssai.DEPART)
                } else {
                    ExplorerViewModel(services)
                }
            }
            ExplorerScreen(vm, onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) })
        }
        composable<RouteMapsManager> { entree ->
            val route = entree.toRoute<RouteMapsManager>()
            val vm = viewModel {
                if (route.essai == "telechargement") MapsManagerViewModel(services, zones = ZonesEssai()) else MapsManagerViewModel(services)
            }
            if (route.essai.isNotEmpty()) LaunchedEffect(vm) { lancerEssaiMapsManager(vm, route.essai) }
            MapsManagerScreen(vm, onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) })
        }
    }
}

/** Le ViewModel du relevé ouvert sous un sous-écran (l'`activityViewModels()` de GeoMys-Android) : celui de
 *  l'entrée [RouteReleve] de la pile, null s'il n'y en a pas. */
@Composable
private fun releveOuvert(
    nav: NavHostController,
    entree: NavBackStackEntry,
    services: ServicesGeoNature,
    sessionSaisie: SessionSaisieViewModel,
): SaisieReleveViewModel? {
    val parent = remember(entree) { runCatching { nav.getBackStackEntry<RouteReleve>() }.getOrNull() } ?: return null
    return viewModel(parent) { SaisieReleveViewModel(services, sessionSaisie, parent.toRoute<RouteReleve>().ouverture()) }
}

/** La saisie mono-taxon d'où l'on vient (l'entrée [RouteSaisieMono] de la pile), pour ses réglages. */
@Composable
private fun saisieMonoOuverte(
    nav: NavHostController,
    entree: NavBackStackEntry,
    services: ServicesGeoNature,
    sessionSaisie: SessionSaisieViewModel,
): SaisieMonoViewModel? {
    val parent = remember(entree) { runCatching { nav.getBackStackEntry<RouteSaisieMono>() }.getOrNull() } ?: return null
    return viewModel(parent) {
        SaisieMonoViewModel(services, sessionSaisie, parent.toRoute<RouteSaisieMono>().additionnelsJson?.let(::mapDepuisJson))
    }
}

/** L'état de l'accueil, relu à chaque retour sur l'écran. Les envois en attente, lus dans les stores
 *  de saisie, sont comptés à part, hors du fil principal ([envoisEnAttente]). */
private fun lireEtatAccueil(infos: InfosApp, services: ServicesGeoNature): EtatAccueil {
    val config = services.config
    return EtatAccueil(
        nomAppli = infos.nom,
        version = infos.version,
        saisieValide = config.saisieOcctaxValide,
        monitoringDisponible = services.monitoringModules.countModulesEnCache() > 0,
        occhabDisponible = config.occhabDisponible && config.occhabPeutCreer,
    )
}

@Composable
private fun EcranAccueil(
    infos: InfosApp,
    services: ServicesGeoNature,
    sessionSaisie: SessionSaisieViewModel,
    sessionOccHab: SessionOccHabViewModel,
    nav: NavHostController,
) {
    var etat by remember { mutableStateOf(lireEtatAccueil(infos, services)) }
    val portee = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        // Les comptes précédents restent affichés jusqu'au nouveau comptage (pas de clignement).
        etat = lireEtatAccueil(infos, services).copy(
            saisiesEnAttente = etat.saisiesEnAttente,
            visitesEnAttente = etat.visitesEnAttente,
            stationsEnAttente = etat.stationsEnAttente,
        )
        val comptage = portee.launch {
            val enAttente = services.envoisEnAttente()
            etat = etat.copy(
                saisiesEnAttente = enAttente.saisies,
                visitesEnAttente = enAttente.visites,
                stationsEnAttente = enAttente.stations,
            )
        }
        onPauseOrDispose { comptage.cancel() }
    }
    val liens = LocalUriHandler.current
    AccueilScreen(
        etat,
        ActionsAccueil(
            saisieMulti = {
                // Nouvelle saisie : ardoise vierge, sans les observations de la précédente.
                sessionSaisie.session.reinitialiser()
                nav.navigate(RouteSaisieMulti())
            },
            saisieMono = {
                // Saisie mono-taxon : ardoise vierge ; l'écran « Détails du relevé » n'est intercalé
                // que s'il existe un champ de relevé obligatoire SANS valeur par défaut.
                sessionSaisie.session.reinitialiser()
                sessionSaisie.session.typeSaisieLabel = SaisieMono.LIBELLE
                val config = services.config
                val ecranReleve = ChampsAdditionnels.aDesChampsReleveRequisSansDefaut(
                    config.additionalFieldsOcctaxJson,
                    sessionSaisie.session.datasetNouveauReleve(config.idDatasetParDefaut),
                )
                nav.navigate(if (ecranReleve) RouteDetailsReleve(mono = true) else RouteSaisieMono())
            },
            monitoring = { nav.navigate(RouteSuivis) },
            occhab = {
                // Nouvelle SESSION OccHab : détails (JDD, observateurs…) réinitialisés aux défauts
                // serveur, JDD à saisir.
                sessionOccHab.session.demarrerSession(detailsSessionParDefaut(services.config))
                nav.navigate(RouteOccHabCarte)
            },
            parametres = { nav.navigate(RouteParametres) },
            menu = { entree ->
                when (entree) {
                    EntreeMenuAccueil.MES_SAISIES -> nav.navigate(RouteMesSaisies)
                    EntreeMenuAccueil.MES_VISITES -> nav.navigate(RouteMesVisites)
                    EntreeMenuAccueil.MES_STATIONS -> nav.navigate(RouteMesStations)
                    EntreeMenuAccueil.EXPLORER -> nav.navigate(RouteExplorer())
                    EntreeMenuAccueil.MAPS_MANAGER -> nav.navigate(RouteMapsManager())
                }
            },
        ),
        onTexteLicence = { liens.openUri("https://www.gnu.org/licenses/gpl-3.0.html") },
    )
}
