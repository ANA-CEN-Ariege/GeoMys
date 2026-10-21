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

package fr.ariegenature.geomys.plateforme

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.HabitatSuggestion
import fr.ariegenature.geomys.model.OccHabStation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey

/** L'attribut « exclu de la sauvegarde » d'iOS vaut pour un dossier et tout son contenu : il ne doit
 *  viser que les caches retéléchargeables, jamais la racine des données, qui porte les saisies non
 *  envoyées et leurs médias. */
class SauvegardesIosTest {

    private val t = ServicesDeTest()
    private val racine get() = t.dossier / "fichiers"

    @AfterTest
    fun nettoyer() = t.nettoyer()

    @OptIn(ExperimentalForeignApi::class)
    private fun exclu(chemin: Path): Boolean {
        val valeur = NSURL.fileURLWithPath(chemin.toString())
            .resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), error = null)
            ?.get(NSURLIsExcludedFromBackupKey)
        return when (valeur) {
            is NSNumber -> valeur.boolValue
            is Boolean -> valeur
            else -> false
        }
    }

    private fun remplirLesCaches() {
        t.services.habitatsOccHab.remplacerTout(listOf(HabitatSuggestion(2, "Pelouse")))
        t.services.stationsServeur.remplacerTout(
            listOf(OccHabStation(idStationServeur = 5, idDataset = 10, origineServeur = true, latitude = 42.9, longitude = 1.6)),
        )
    }

    @Test
    fun les_caches_sont_exclus_mais_pas_la_racine_des_saisies() {
        remplirLesCaches()
        assertTrue(exclu(racine / "caches"))
        assertFalse(exclu(racine))
    }

    @Test
    fun une_racine_exclue_par_une_version_precedente_est_rendue_aux_sauvegardes() {
        remplirLesCaches()
        exclureDesSauvegardes(racine)
        assertTrue(exclu(racine), "contre-épreuve : l'attribut se lit bien")

        t.redemarrer()

        assertFalse(exclu(racine))
        assertTrue(exclu(racine / "caches"))
    }
}
