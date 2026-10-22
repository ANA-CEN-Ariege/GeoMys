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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/FiabiliteEnvoiTest.kt) :
// Robolectric remplacé par ServicesDeTest, JUnit par kotlin.test. Le cas des exceptions java.net,
// propres à la JVM, est dans androidHostTest (network/FiabiliteEnvoiJvmTest.kt).

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Lot B de l'audit 2026-08-27 (fiabilité de l'envoi) — briques unitaires : marquages du SortieStore
 * désormais booléens + statut incertain d'une occurrence, session PictoCache limitée à l'origine du
 * serveur.
 */
class FiabiliteEnvoiTest {

    private val t = ServicesDeTest()
    private val store get() = t.services.sortieStore

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun obs(id: String) = Observation(id = id, espece = "Merle", cdNom = 4001, latitude = 42.9, longitude = 1.4, nombre = 1)

    @Test
    fun marquages_renvoient_le_succes_et_l_incertitude_est_effacee_a_la_creation() = runTest {
        val sortie = Sortie(observations = listOf(obs("a"), obs("b")))
        store.ajouter(sortie)
        assertTrue(store.marquerObservationIncertaine(sortie.id, "a", 55))
        assertEquals(55, store.charger().single().observations.first { it.id == "a" }.idReleveIncertain)
        assertTrue(store.marquerObservationsEnvoyees(sortie.id, listOf("a")))
        val a = store.charger().single().observations.first { it.id == "a" }
        assertTrue(a.envoyeeServeur)
        assertNull(a.idReleveIncertain, "créée → plus d'incertitude")
        assertTrue(store.marquerObservationsEnvoyees(sortie.id, emptyList()), "liste vide = succès")
        assertTrue(store.marquerEnvoyee(sortie.id))
        assertTrue(store.charger().single().envoyeGeoNature)
    }

    @Test
    fun uuid_occurrence_stable_et_regenere_pour_un_ancien_json() = runTest {
        val o = obs("a")
        assertTrue(o.uuidOccurrence.isNotBlank())
        store.ajouter(Sortie(observations = listOf(o)))
        assertEquals(o.uuidOccurrence, store.charger().single().observations.single().uuidOccurrence)
    }

    @Test
    fun session_picto_uniquement_vers_l_origine_du_serveur() {
        val pictos = t.services.pictos
        val base = "https://geonature.example.org/geonature"
        assertTrue(pictos.memeOrigine("https://geonature.example.org/api/media/x/img.jpg", base))
        assertTrue(pictos.memeOrigine("HTTPS://GEONATURE.example.org/x.png", base))
        assertFalse(pictos.memeOrigine("https://cdn.example.net/x.png", base), "autre hôte")
        assertFalse(pictos.memeOrigine("http://geonature.example.org/x.png", base), "http nu vers un serveur https")
        assertFalse(pictos.memeOrigine("https://geonature.example.org/x.png", null), "sans base : jamais de session")
        assertFalse(pictos.memeOrigine("pas une url", base), "URL illisible")
    }
}
