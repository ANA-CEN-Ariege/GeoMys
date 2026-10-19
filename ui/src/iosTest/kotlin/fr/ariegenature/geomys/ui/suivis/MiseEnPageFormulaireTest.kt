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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/FormulaireRendererTest.kt) : le
// cas de mise en page, laissé de côté au lot 4 (les 14 autres sont dans FormulaireEtatTest, côté socle).
// Robolectric et la hiérarchie des vues deviennent un test Compose sans fenêtre, sur le simulateur iOS,
// l'allure Android imposée : la barre rouge est dessinée par la boîte du seul champ.

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.monitoring.form.EditableField
import fr.ariegenature.geomys.monitoring.form.FormulaireEtat
import fr.ariegenature.geomys.monitoring.form.ViewType
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.ui.theme.Apparence
import fr.ariegenature.geomys.ui.theme.LocalApparence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import okio.FileSystem
import okio.SYSTEM
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MiseEnPageFormulaireTest {

    private val portee = CoroutineScope(Job() + Dispatchers.Unconfined)

    // Un champ texte ne lit pas le référentiel : un cache vide suffit.
    private fun etat(champs: List<EditableField>) = FormulaireEtat(
        champs,
        TaxRefCache(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-ui-test-taxref", MapSettings()),
        portee,
    )

    @AfterTest
    fun fermer() {
        portee.cancel()
    }

    @Test
    fun la_barre_est_accolee_au_champ_pas_au_libelle() = runComposeUiTest {
        // Demande terrain : la barre appartient au CHAMP (elle en a la hauteur), le libellé
        // n'est pas souligné. Elle doit donc border le champ seul, pas le bloc du libellé.
        val e = etat(listOf(EditableField("vide", ViewType.TEXT, "Vide", obligatoire = true)))
        var barres by mutableStateOf(e.barresRouges(modeCompletion = true))
        setContent {
            CompositionLocalProvider(LocalApparence provides Apparence.ANDROID) {
                MaterialTheme { FormulaireMonitoring(e, barres, {}, {}) }
            }
        }
        val libelle = onNodeWithText("Vide *").getBoundsInRoot()
        val bordure = onNodeWithTag(etiquetteChamp("vide")).getBoundsInRoot()
        val avecBarre = onNode(hasSetTextAction()).getBoundsInRoot().left - bordure.left
        // Le libellé est au-dessus de ce que borde la barre, et n'est pas décalé par elle.
        assertTrue(bordure.top >= libelle.bottom, "le libellé ne doit pas être dans ce que borde la barre")
        assertEquals(bordure.left, libelle.left)
        // Le champ, lui, commence après la barre de 3 dp ; sans barre, elle ne prend aucune place.
        barres = emptySet()
        waitForIdle()
        val sansBarre = onNode(hasSetTextAction()).getBoundsInRoot().left - onNodeWithTag(etiquetteChamp("vide")).getBoundsInRoot().left
        assertEquals(3.dp, avecBarre - sansBarre)
    }
}
