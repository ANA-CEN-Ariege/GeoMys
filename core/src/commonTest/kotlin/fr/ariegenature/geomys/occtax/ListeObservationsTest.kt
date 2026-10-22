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

// Propre au portage : ces textes vivaient dans l'écran de GeoMys-Android @ cdd5196
// (ui/ObservationsFragment.kt), sans test. Les attendus sont ceux de son code, sauf le total d'individus
// (défaut n° 5 de l'inventaire, corrigé).

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
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

    /** Défaut n° 5 de l'inventaire corrigé : le total d'individus additionne TOUS les
     *  dénombrements, dans les listes comme dans le relevé (sous GeoMys-Android, les listes ne
     *  comptaient que le n° 0). */
    @Test
    fun le_total_d_individus_additionne_tous_les_denombrements() {
        val o = obs("a", "Merle noir", releveId = "r", nombre = 2).apply {
            denombrementsAdditionnels = listOf(Denombrement(nombreMin = 3), Denombrement(nombreMin = 1))
        }
        assertEquals(6, o.totalIndividus)
        val releve = listOf(o, obs("b", "Pie bavarde", releveId = "r")).grouperEnReleves().single()
        assertEquals("×7", ListeObservations.effectif(releve))
        val sortie = Sortie(observations = listOf(o))
        assertEquals(listOf("Merle noir" to 6), MesSaisies.especesAgregees(sortie))
        assertEquals("Merle noir", AffichageSortie.textes(listOf(o)).first)
        assertEquals(true, AffichageSortie.textes(listOf(o)).second.contains("6 ind."))
        assertEquals(true, CarteSaisie.optionsReleve(listOf(o, obs("b", "Pie bavarde"))).second.contains("Merle noir × 6"))
    }

    @Test
    fun les_champs_intercales_passent_en_json() {
        val valeurs = mapOf("meteo" to "Ensoleillé", "vent" to "Fort", "vide" to "")
        assertEquals(valeurs, mapDepuisJson(mapVersJson(valeurs)))
        assertEquals(emptyMap(), mapDepuisJson(""))
        assertEquals(emptyMap(), mapDepuisJson("pas du json"))
    }
}
