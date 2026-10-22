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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/FormulaireRendererTest.kt) : le
// cas de mise en page, laissé de côté au lot 4 (les 14 autres sont dans FormulaireEtatTest, côté socle).
// Robolectric et la hiérarchie des vues deviennent un test Compose sans fenêtre, sur le simulateur iOS,
// l'allure Android imposée : la barre rouge est dessinée par la boîte du seul champ. Propre au portage :
// les champs d'une photo sous l'allure iOS (formulaire média du web, 20 octobre).

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.model.MediaSuivi
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
        var barres by mutableStateOf(e.barresRouges())
        setContent {
            CompositionLocalProvider(LocalApparence provides Apparence.ANDROID) {
                MaterialTheme { FormulaireMonitoring(e, barres, {}, {}) }
            }
        }
        // Le libellé du web, sans « * ».
        val libelle = onNodeWithText("Vide").getBoundsInRoot()
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

    @Test
    fun la_valeur_d_une_liste_est_calee_a_gauche_sous_ios() = runComposeUiTest {
        // 22 octobre : « Tous les champs de formulaires doivent être justifiés à gauche ». La valeur
        // d'une liste était calée à droite, comme le Picker de GeoMys-IOS. Le menu du système, posé
        // par-dessus, ne s'affiche pas sans fenêtre : on regarde ce qu'il recouvre.
        setContent { Box(Modifier.width(361.dp).testTag("ligne")) { ValeurListeIos("Beau temps") } }
        assertEquals(onNodeWithTag("ligne").getBoundsInRoot().left, onNodeWithText("Beau temps").getBoundsInRoot().left)
    }

    @Test
    fun une_photo_a_son_titre_sa_description_et_son_auteur_sous_ios() = runComposeUiTest {
        val photo = MediaSuivi("file:///essai/col-de-port.jpg", champ = "medias", schemaDotTable = "gn_monitoring.t_base_visits")
        val e = etat(listOf(EditableField("medias", ViewType.MEDIA, "Photos", value = listOf(photo))))
        setContent {
            CompositionLocalProvider(LocalApparence provides Apparence.IOS) {
                MaterialTheme { FormulaireMonitoring(e, e.barresRouges(), {}, {}) }
            }
        }
        onNodeWithText("col-de-port.jpg").assertExists()
        // Le titre, la description et l'auteur, dans cet ordre, chacun sous son libellé.
        val champs = onAllNodes(hasSetTextAction())
        val titre = champs[0].getBoundsInRoot()
        val description = champs[1].getBoundsInRoot()
        val auteur = champs[2].getBoundsInRoot()
        assertTrue(onNodeWithText("Titre").getBoundsInRoot().bottom <= titre.top)
        assertTrue(titre.bottom <= onNodeWithText("Description").getBoundsInRoot().top)
        assertTrue(description.bottom <= onNodeWithText("Auteur").getBoundsInRoot().top)
        assertTrue(onNodeWithText("Auteur").getBoundsInRoot().bottom <= auteur.top)
        // Sans titre, requis sur le web, la barre (3 pt, à 8 pt du champ) décale le seul champ du titre.
        assertEquals(11.dp, titre.left - description.left)
        assertEquals(description.left, auteur.left)
        // Le titre saisi, la barre disparaît, et le média le garde pour l'envoi.
        champs[0].performTextInput("Mare au lever du jour")
        waitForIdle()
        assertEquals(onAllNodes(hasSetTextAction())[1].getBoundsInRoot().left, onAllNodes(hasSetTextAction())[0].getBoundsInRoot().left)
        assertEquals("Mare au lever du jour", e.mediasPourEnvoi().single().titre)
    }
}
