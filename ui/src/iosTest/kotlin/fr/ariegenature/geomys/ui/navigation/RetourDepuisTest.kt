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

// Propre au portage : la garde des retours de la pile (retourDepuis, NavigationGeoMys) et celle du fil
// d'Ariane des Suivis (naviguerVersFil), éprouvées sur une vraie pile de navigation, que seuls les tests
// d'écran du simulateur savent faire tourner. Sur le Galaxy A12, le 25 novembre 2026, deux touchers de la
// coche de la carte multi-taxons quittée sans observation ont dépilé la carte, puis l'accueil : la pile
// vide ne montrait plus que le fond de la fenêtre, sans un bouton.
package fr.ariegenature.geomys.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.ariegenature.geomys.monitoring.FIL_TYPE_ACCUEIL
import fr.ariegenature.geomys.monitoring.FilSegment
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
internal data object AccueilEssaiRetour

@Serializable
internal data object CarteEssaiRetour

@Serializable
internal data object ReleveEssaiRetour

/** L'hôte des écrans, au premier plan et avec ses ViewModels, comme la fenêtre de l'appli. */
private class HoteEssaiRetour : LifecycleOwner, ViewModelStoreOwner {
    private val registre = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registre
    override val viewModelStore = ViewModelStore()
}

@OptIn(ExperimentalTestApi::class)
class RetourDepuisTest {
    private lateinit var nav: NavHostController

    private fun ComposeUiTest.hote(ecrans: @Composable () -> Unit) {
        setContent {
            val hote = remember { HoteEssaiRetour() }
            CompositionLocalProvider(LocalLifecycleOwner provides hote, LocalViewModelStoreOwner provides hote) { ecrans() }
        }
    }

    /** L'accueil, puis la carte et le relevé qu'on y empile. */
    private fun ComposeUiTest.pile() = hote {
        nav = rememberNavController()
        NavHost(nav, startDestination = AccueilEssaiRetour) {
            composable<AccueilEssaiRetour> { Text("accueil") }
            composable<CarteEssaiRetour> { Text("carte") }
            composable<ReleveEssaiRetour> { Text("relevé") }
        }
    }

    /** L'accueil, puis les écrans des Suivis qui portent le fil d'Ariane. */
    private fun ComposeUiTest.pileSuivis() = hote {
        nav = rememberNavController()
        NavHost(nav, startDestination = RouteAccueil) {
            composable<RouteAccueil> { Text("accueil") }
            composable<RouteSuivis> { Text("protocoles") }
            composable<RouteSuiviDetail> { Text("protocole") }
            composable<RouteFicheObjet> { Text("fiche") }
        }
    }

    @Test
    fun un_second_retour_de_la_carte_ne_depile_pas_l_accueil() = runComposeUiTest {
        pile()
        runOnIdle { nav.navigate(CarteEssaiRetour) }
        onNodeWithText("carte").assertExists()
        val carte = runOnIdle { nav.currentBackStackEntry!! }
        // Deux touchers de la coche : le second arrive avant que l'accueil se dessine.
        runOnIdle {
            assertTrue(nav.retourDepuis(carte))
            assertFalse(nav.retourDepuis(carte))
        }
        onNodeWithText("accueil").assertExists()
        runOnIdle { assertTrue(nav.currentBackStackEntry?.destination?.hasRoute<AccueilEssaiRetour>() == true) }
    }

    @Test
    fun l_accueil_ne_se_depile_jamais() = runComposeUiTest {
        pile()
        onNodeWithText("accueil").assertExists()
        // La pile elle-même : vidée, elle aussi rendait faux, et l'accueil restait affiché le temps de
        // sa sortie (relecture du 25 novembre 2026).
        runOnIdle {
            val accueil = nav.currentBackStackEntry!!
            assertFalse(nav.retourDepuis(accueil))
            assertEquals(accueil.id, nav.currentBackStackEntry?.id)
        }
        onNodeWithText("accueil").assertExists()
    }

    @Test
    fun un_ecran_recouvert_ne_depile_pas_celui_du_dessus() = runComposeUiTest {
        pile()
        runOnIdle { nav.navigate(CarteEssaiRetour) }
        val carte = runOnIdle { nav.currentBackStackEntry!! }
        runOnIdle { nav.navigate(ReleveEssaiRetour) }
        onNodeWithText("relevé").assertExists()
        // « Quitter » de la carte, reçu après l'ouverture du relevé : le relevé reste.
        runOnIdle { assertFalse(nav.retourDepuis(carte)) }
        onNodeWithText("relevé").assertExists()
        // Le relevé refermé, la carte se quitte, et l'on revient à l'accueil.
        val releve = runOnIdle { nav.currentBackStackEntry!! }
        runOnIdle { assertTrue(nav.retourDepuis(releve)) }
        onNodeWithText("carte").assertExists()
        runOnIdle { assertTrue(nav.retourDepuis(carte)) }
        onNodeWithText("accueil").assertExists()
    }

    @Test
    fun un_second_toucher_de_la_maison_du_fil_n_empile_pas_un_second_accueil() = runComposeUiTest {
        pileSuivis()
        runOnIdle {
            nav.navigate(RouteSuivis)
            nav.navigate(RouteSuiviDetail("stom"))
            nav.navigate(RouteFicheObjet("stom", "site", 501))
        }
        onNodeWithText("fiche").assertExists()
        val fiche = runOnIdle { nav.currentBackStackEntry!! }
        val maison = listOf(FilSegment(FIL_TYPE_ACCUEIL, -1, "Accueil"))
        // Deux touchers de la maison : le second, reçu avant que l'accueil se dessine, ne trouvait plus
        // rien à dépiler et ouvrait un second accueil.
        runOnIdle {
            nav.naviguerVersFil(fiche, "stom", maison)
            nav.naviguerVersFil(fiche, "stom", maison)
        }
        onNodeWithText("accueil").assertExists()
        runOnIdle {
            assertTrue(nav.currentBackStackEntry?.destination?.hasRoute<RouteAccueil>() == true)
            assertNull(nav.previousBackStackEntry, "un seul accueil dans la pile")
        }
    }
}
