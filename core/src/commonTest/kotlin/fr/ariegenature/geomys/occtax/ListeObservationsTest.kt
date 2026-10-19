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

// Propre au portage : ces textes vivaient dans l'écran de GeoMys-Android @ cdd5196
// (ui/ObservationsFragment.kt), sans test. Les attendus sont ceux de son code.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.store.io.mapDepuisJson
import fr.ariegenature.geomys.store.io.mapVersJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [ListeObservations] et le passage des champs de l'écran intercalé au relevé. */
class ListeObservationsTest {

    private fun obs(id: String, espece: String, releveId: String? = null, nombre: Int = 1, notes: String = "") =
        Observation(id = id, espece = espece, latitude = 42.96491, longitude = 1.60555, releveId = releveId, nombre = nombre, notes = notes)

    @Test
    fun le_titre_compte_les_releves_des_qu_un_releve_porte_plusieurs_especes() {
        assertEquals("Observations (3)", ListeObservations.titre(3, 3))
        assertEquals("2 relevés · 3 obs.", ListeObservations.titre(2, 3))
        assertEquals("1 relevé · 2 obs.", ListeObservations.titre(1, 2))
    }

    @Test
    fun un_releve_solo_montre_son_espece_et_ses_notes() {
        val solo = Releve(null, listOf(obs("a", "Merle noir", notes = "chant")))
        assertEquals("Merle noir", ListeObservations.espece(solo))
        assertEquals("chant", ListeObservations.secondaire(solo))
        assertNull(ListeObservations.effectif(solo), "un individu : pas de pastille")
        assertNull(ListeObservations.secondaire(Releve(null, listOf(obs("b", "Pic vert")))))
        assertEquals("42,9649, 1,6056", ListeObservations.coordonnees(solo.premier), "quatre décimales, virgule française")
    }

    @Test
    fun un_releve_multi_liste_ses_especes() {
        val multi = Releve("r1", listOf(obs("a", "Merle noir", "r1", nombre = 2), obs("b", "Pic vert", "r1")))
        assertEquals("2 espèces", ListeObservations.espece(multi))
        assertEquals("Merle noir, Pic vert", ListeObservations.secondaire(multi))
        assertEquals("×3", ListeObservations.effectif(multi))
        assertEquals("2 espèces seront supprimées :\n• Merle noir\n• Pic vert", ListeObservations.messageSuppression(multi))
    }

    @Test
    fun les_champs_intercales_passent_en_json() {
        val valeurs = mapOf("meteo" to "Ensoleillé", "vent" to "Fort", "vide" to "")
        assertEquals(valeurs, mapDepuisJson(mapVersJson(valeurs)))
        assertEquals(emptyMap(), mapDepuisJson(""))
        assertEquals(emptyMap(), mapDepuisJson("pas du json"))
    }
}
