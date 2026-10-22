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

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.HabitatSuggestion
import fr.ariegenature.geomys.model.OccHabStation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.FileSystem
import okio.SYSTEM

/** Les caches HABREF et les stations du serveur vivent dans filesDir/caches : leur exclusion des
 *  sauvegardes visait jusqu'au 20 octobre la racine des données, saisies comprises. Les
 *  installations existantes y sont rangées au démarrage. */
class CachesHorsDeLaRacineTest {

    private val t = ServicesDeTest()
    private val systeme = FileSystem.SYSTEM
    private val racine get() = t.dossier / "fichiers"
    private val caches get() = racine / "caches"

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun remplir() {
        t.services.habitatsOcctax.remplacerTout(listOf(HabitatSuggestion(1, "Lande")))
        t.services.habitatsOccHab.remplacerTout(listOf(HabitatSuggestion(2, "Pelouse")))
        t.services.stationsServeur.remplacerTout(
            listOf(OccHabStation(idStationServeur = 5, idDataset = 10, origineServeur = true, latitude = 42.9, longitude = 1.6)),
        )
    }

    @Test
    fun les_caches_s_ecrivent_dans_leur_dossier_et_pas_a_la_racine() {
        remplir()
        for (nom in NOMS) {
            assertTrue(systeme.exists(caches / nom), "$nom dans caches/")
            assertFalse(systeme.exists(racine / nom), "$nom hors de la racine")
        }
    }

    @Test
    fun une_installation_existante_retrouve_ses_caches_au_demarrage() {
        remplir()
        // La disposition des versions précédentes : les trois fichiers à la racine.
        for (nom in NOMS) systeme.atomicMove(caches / nom, racine / nom)

        t.redemarrer()

        for (nom in NOMS) {
            assertTrue(systeme.exists(caches / nom), "$nom rangé dans caches/")
            assertFalse(systeme.exists(racine / nom), "$nom retiré de la racine")
        }
        assertEquals(1, t.services.habitatsOcctax.count)
        assertEquals(listOf(2), t.services.habitatsOccHab.rechercher("Pelouse").map { it.cdHab })
        assertEquals(listOf(5), t.services.stationsServeur.lire(10).map { it.idStationServeur })
    }

    @Test
    fun un_cache_deja_range_l_emporte_sur_une_copie_restee_a_la_racine() {
        remplir()
        // Une vieille copie à la racine, à côté du cache à jour.
        systeme.write(racine / "habitats_occhab_v1.json") { writeUtf8("[]") }

        t.redemarrer()

        assertFalse(systeme.exists(racine / "habitats_occhab_v1.json"))
        assertEquals(listOf(2), t.services.habitatsOccHab.rechercher("Pelouse").map { it.cdHab })
    }

    private companion object {
        val NOMS = listOf("habitats_v1.json", "habitats_occhab_v1.json", StationsServeurCache.NOM_FICHIER)
    }
}
