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

// Propre au portage (26 novembre 2026, avant le dépôt sur l'App Store) : l'accueil en paysage écrasait
// ses boutons, faute de hauteur ; il défile désormais. Test Compose sans fenêtre, sur le simulateur iOS,
// pour les deux allures.

package fr.ariegenature.geomys.ui.accueil

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import fr.ariegenature.geomys.ui.theme.Apparence
import fr.ariegenature.geomys.ui.theme.LocalApparence
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import platform.UIKit.UIViewController

@OptIn(ExperimentalTestApi::class)
class AccueilMiseEnPageTest {

    private val etat = EtatAccueil(
        nomAppli = "GeoMys",
        version = "2.2.2",
        saisieValide = true,
        monitoringDisponible = true,
        occhabDisponible = true,
    )
    private val actions = ActionsAccueil({}, {}, {}, {}, {}, {})

    /** Un écran de téléphone en portrait, puis un iPhone 16 en paysage (852 × 393 pt). La fenêtre du
     *  test ne mesure que 768 pt de haut : le portrait en prend 700, assez pour tout l'accueil.
     *  - Portrait : les boutons restent en bas, sans défilement : le bloc touche le bas de l'écran
     *    sous iOS (sa marge de 60 pt est à l'intérieur), et s'arrête à 60 dp du bas sous Android.
     *  - Paysage : le bloc garde sa hauteur au lieu d'être écrasé, et son dernier bouton se rejoint en
     *    faisant défiler l'accueil. */
    private fun verifier(apparence: Apparence) = runComposeUiTest {
        val portrait = DpSize(393.dp, 700.dp)
        var taille by mutableStateOf(portrait)
        setContent {
            // Les symboles SF de l'allure iOS passent par le contrôleur UIKit de l'écran.
            CompositionLocalProvider(LocalApparence provides apparence, LocalUIViewController provides UIViewController()) {
                Box(Modifier.size(taille)) { AccueilScreen(etat, actions, onTexteLicence = {}) }
            }
        }
        val enPortrait = onNodeWithTag(ETIQUETTE_BOUTONS_ACCUEIL).getBoundsInRoot()
        val basAttendu = if (apparence == Apparence.IOS) 700.dp else 700.dp - 60.dp
        assertTrue(abs((enPortrait.bottom - basAttendu).value) < 1f, "portrait : bas du bloc ${enPortrait.bottom}, attendu $basAttendu ($apparence)")

        // La hauteur mesurée du bloc, et non sa partie visible (getBoundsInRoot est rogné par le défilement).
        // Elle dépend de la largeur (sous Android, « Saisie multi-taxons » tient sur une ligne en
        // paysage) : la référence est prise à la largeur du paysage, sur un écran assez haut.
        fun hauteurBloc() = with(density) { onNodeWithTag(ETIQUETTE_BOUTONS_ACCUEIL).fetchSemanticsNode().size.height.toDp() }
        taille = DpSize(852.dp, 760.dp)
        waitForIdle()
        val hauteurNaturelle = hauteurBloc()

        taille = DpSize(852.dp, 393.dp)
        waitForIdle()
        val hauteurPaysage = hauteurBloc()
        assertTrue(abs((hauteurPaysage - hauteurNaturelle).value) < 1f, "paysage : bloc écrasé, $hauteurPaysage au lieu de $hauteurNaturelle ($apparence)")
        val occhab = onNodeWithText("OccHab")
        occhab.performScrollTo()
        occhab.assertIsDisplayed()
        assertTrue(occhab.getBoundsInRoot().bottom <= 393.dp + 0.5.dp, "paysage : OccHab reste hors de l'écran ($apparence)")
    }

    @Test
    fun l_accueil_garde_ses_boutons_en_portrait_et_defile_en_paysage_sous_ios() = verifier(Apparence.IOS)

    @Test
    fun l_accueil_garde_ses_boutons_en_portrait_et_defile_en_paysage_sous_android() = verifier(Apparence.ANDROID)
}
