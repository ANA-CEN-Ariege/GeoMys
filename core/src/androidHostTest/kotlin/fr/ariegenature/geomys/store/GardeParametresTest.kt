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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/ListeIncompleteTest.kt,
// parametres_n_ouvre_aucune_porte_derobee_vers_les_saisies) : la garde textuelle lisait le fragment et
// son layout ; elle lit désormais l'écran Compose et son ViewModel (module ui), d'où sa place parmi
// les tests JVM, qui ont accès aux sources.

package fr.ariegenature.geomys.store

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Décision produit du 2026-09-18 (GeoMys-Android), rappelée depuis : le blocage de Paramètres est
 *  ENTIER, sans porte dérobée vers « Mes saisies / Mes visites / Mes stations », quelle qu'en soit
 *  la cause. Deux audits ont proposé l'inverse ; cette garde empêche la proposition de repasser en
 *  douce. Le texte du bandeau est vérifié à part (ParametresGeoNatureTest). */
class GardeParametresTest {

    private val dossier = File("../ui/src/commonMain/kotlin/fr/ariegenature/geomys/ui/parametres")

    @Test
    fun parametres_n_ouvre_aucune_porte_derobee_vers_les_saisies() {
        val sources = listOf("ParametresScreen.kt", "ParametresViewModel.kt").map { File(dossier, it) }
        for (source in sources) assertTrue(source.isFile, "source introuvable : ${source.path}")
        val texte = sources.joinToString("\n") { it.readText() }
        for (interdit in listOf("Mes saisies", "Mes visites", "Mes stations", "RouteAVenir", "EntreeMenuAccueil")) {
            assertFalse(
                texte.contains(interdit),
                "Paramètres ne doit pas mener à « $interdit » : les accès de secours ont été retirés " +
                    "sur demande explicite (2026-09-18).",
            )
        }
        assertFalse(texte.contains("ci-dessous"), "le bandeau ne doit pas renvoyer à des écrans accessibles « ci-dessous »")
    }
}
