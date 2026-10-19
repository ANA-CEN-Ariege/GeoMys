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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.DefaultNavTransitions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.ariegenature.geomys.InfosApp
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.store.configurationComplete
import fr.ariegenature.geomys.store.envoisEnAttente
import fr.ariegenature.geomys.ui.accueil.AccueilScreen
import fr.ariegenature.geomys.ui.accueil.ActionsAccueil
import fr.ariegenature.geomys.ui.accueil.EntreeMenuAccueil
import fr.ariegenature.geomys.ui.accueil.EtatAccueil
import fr.ariegenature.geomys.ui.avenir.EcranAVenir
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.parametres.ParametresScreen
import fr.ariegenature.geomys.ui.parametres.ParametresViewModel
import fr.ariegenature.geomys.ui.parametres.TaxonsListeScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object RouteAccueil

@Serializable
data object RouteParametres

/** Contenu d'une liste de taxons du cache (Paramètres › Détails). */
@Serializable
data class RouteTaxonsListe(val idListe: Int, val nomListe: String)

/** Écran que le portage n'a pas encore atteint : [titre] et le [lot] qui l'apportera. */
@Serializable
data class RouteAVenir(val titre: String, val lot: String)

private const val LOT_OCCTAX = "lot 6 (OccTax)"
private const val LOT_MONITORING = "lot 7 (Monitoring)"
private const val LOT_OCCHAB = "lot 8 (OccHab)"
private const val LOT_CARTES = "lot 9 (Explorer, cartes)"

/** La pile des écrans, posée sur l'accueil.
 *  - Transitions : aucune sous Android (le nav_graph de GeoMys-Android n'en déclare pas) ; celles
 *    d'une NavigationStack sous iOS (glissement latéral avec voile), par défaut dans navigation-compose.
 *  - Au lancement : une mise à jour qui exige un rechargement arme son drapeau, puis une
 *    configuration incomplète ouvre Paramètres, d'office et sans animation, comme MainActivity
 *    (GeoMys-Android) et ContentView (GeoMys-IOS). [ecranInitial] « accueil » s'en dispense (essais
 *    d'affichage), « parametres » l'impose. */
@Composable
fun NavigationGeoMys(infos: InfosApp, services: ServicesGeoNature, ecranInitial: String? = null) {
    val nav = rememberNavController()
    val ios = estIos
    var demarrage by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        services.cachesSynchronises.armerRechargementSiRequis(services.config)
        val ouvrirParametres = when (ecranInitial) {
            "accueil" -> false
            "parametres" -> true
            else -> !configurationComplete(services.config, services.taxRef)
        }
        if (ouvrirParametres) nav.navigate(RouteParametres)
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
        composable<RouteAccueil> { EcranAccueil(infos, services, nav) }
        composable<RouteParametres> {
            val vm = viewModel { ParametresViewModel(services) }
            ParametresScreen(
                vm,
                onFermer = { nav.popBackStack() },
                onTaxonsListe = { id, nom -> nav.navigate(RouteTaxonsListe(id, nom)) },
            )
        }
        composable<RouteTaxonsListe> { entree ->
            val route = entree.toRoute<RouteTaxonsListe>()
            TaxonsListeScreen(services, route.idListe, route.nomListe, onRetour = { nav.popBackStack() })
        }
        composable<RouteAVenir> { entree ->
            val route = entree.toRoute<RouteAVenir>()
            EcranAVenir(route.titre, route.lot, onAccueil = { nav.popBackStack(RouteAccueil, inclusive = false) })
        }
    }
}

/** L'état de l'accueil, relu à chaque retour sur l'écran. Les envois en attente, lus dans les stores
 *  de saisie, sont comptés à part, hors du fil principal ([envoisEnAttente]). */
private fun lireEtatAccueil(infos: InfosApp, services: ServicesGeoNature): EtatAccueil {
    val config = services.config
    return EtatAccueil(
        version = infos.version,
        saisieValide = config.saisieOcctaxValide,
        monitoringDisponible = services.monitoringModules.countModulesEnCache() > 0,
        occhabDisponible = config.occhabDisponible && config.occhabPeutCreer,
        enregistrerTrace = services.preferences.enregistrerTrace,
    )
}

@Composable
private fun EcranAccueil(infos: InfosApp, services: ServicesGeoNature, nav: NavHostController) {
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
    val aVenir = { titre: String, lot: String -> nav.navigate(RouteAVenir(titre, lot)) }
    AccueilScreen(
        etat,
        ActionsAccueil(
            saisieMulti = { aVenir("Saisie multi-taxons", LOT_OCCTAX) },
            saisieMono = { aVenir("Saisie mono-taxons", LOT_OCCTAX) },
            monitoring = { aVenir("Monitoring", LOT_MONITORING) },
            occhab = { aVenir("OccHab", LOT_OCCHAB) },
            parametres = { nav.navigate(RouteParametres) },
            menu = { entree ->
                when (entree) {
                    EntreeMenuAccueil.MES_SAISIES -> aVenir("Mes saisies", LOT_OCCTAX)
                    EntreeMenuAccueil.MES_VISITES -> aVenir("Mes visites", LOT_MONITORING)
                    EntreeMenuAccueil.MES_STATIONS -> aVenir("Mes stations", LOT_OCCHAB)
                    EntreeMenuAccueil.EXPLORER -> aVenir("Explorer", LOT_CARTES)
                    EntreeMenuAccueil.MAPS_MANAGER -> aVenir("Maps Manager", LOT_CARTES)
                }
            },
            enregistrerTrace = { actif ->
                services.preferences.enregistrerTrace = actif
                etat = etat.copy(enregistrerTrace = actif)
            },
        ),
        onTexteLicence = { liens.openUri("https://www.gnu.org/licenses/gpl-3.0.html") },
    )
}
