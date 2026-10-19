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

// Propre au portage : les comptes de l'accueil de GeoMys-Android @ cdd5196 n'y sont pas testés.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.model.Sortie
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Pastilles de l'accueil : ce qui reste à envoyer dans les trois stores de saisie. */
class EnvoisEnAttenteTest {

    private val t = ServicesDeTest()

    @AfterTest
    fun nettoyer() = t.nettoyer()

    @Test
    fun rien_a_envoyer_au_depart() = runTest {
        val enAttente = t.services.envoisEnAttente()
        assertEquals(EnvoisEnAttente(0, 0, 0), enAttente)
        assertFalse(enAttente.auMoinsUn)
    }

    @Test
    fun chaque_store_compte_selon_les_regles_d_android() = runTest {
        val s = t.services
        // OccTax : envoyées et importées ne comptent pas.
        s.sortieStore.ajouter(Sortie(id = "a"))
        s.sortieStore.ajouter(Sortie(id = "b", envoyeGeoNature = true))
        s.sortieStore.ajouter(Sortie(id = "c", estImportee = true))
        // Suivis : en attente ET en erreur comptent, pas les envoyées.
        s.outbox.ajouter(SaisieEnAttente(uuid = "p", moduleCode = "M", objectType = "visit", valeursJson = "{}"))
        s.outbox.ajouter(SaisieEnAttente(uuid = "e", moduleCode = "M", objectType = "visit", valeursJson = "{}", etat = SaisieEnAttente.Etat.ERROR))
        s.outbox.ajouter(SaisieEnAttente(uuid = "v", moduleCode = "M", objectType = "visit", valeursJson = "{}", etat = SaisieEnAttente.Etat.SENT))
        // OccHab : une saisie dont toutes les stations sont parties n'est plus à envoyer.
        s.occHabStore.upsertStation("sa", OccHabStation(id = "A"))
        s.occHabStore.upsertStation("sb", OccHabStation(id = "B"))
        s.occHabStore.marquerStationEnvoyee("sb", "B", 100)

        val enAttente = s.envoisEnAttente()

        assertEquals(EnvoisEnAttente(saisies = 1, visites = 2, stations = 1), enAttente)
        assertTrue(enAttente.auMoinsUn)
    }
}
