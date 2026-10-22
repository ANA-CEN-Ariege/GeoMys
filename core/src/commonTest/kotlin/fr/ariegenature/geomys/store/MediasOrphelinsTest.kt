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

// Propre au portage : GeoMys-Android @ cdd5196 ne teste pas MediasOrphelins.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.util.maintenantMillis
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.SYSTEM

/** Balayage des médias orphelins au démarrage : seuls les fichiers que plus aucun store ne
 *  référence ET vieux de plus de 48 h sont supprimés. */
class MediasOrphelinsTest {

    private val t = ServicesDeTest()
    private val systeme = FileSystem.SYSTEM
    private val dossier = t.dossier / "fichiers" / "medias"

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun fichier(nom: String): String {
        systeme.createDirectories(dossier)
        systeme.write(dossier / nom) { writeUtf8(nom) }
        return "file://${dossier / nom}"
    }

    /** Le balayage, vu depuis [decalageMs] après maintenant. */
    private fun balayage(decalageMs: Long) =
        MediasOrphelins(systeme, dossier, t.services.sortieStore, t.services.outbox) { maintenantMillis() + decalageMs }

    private suspend fun referencer(occtax: String, suivis: String) {
        t.services.sortieStore.ajouter(Sortie(observations = listOf(
            Observation(espece = "Merle", latitude = 42.9, longitude = 1.4, mediaUrisCounting0 = listOf(occtax)),
        )))
        t.services.outbox.ajouter(SaisieEnAttente(
            moduleCode = "STOC", objectType = "visite", valeursJson = "{}", mediaPathsLocal = listOf(suivis),
        ))
    }

    @Test
    fun seuls_les_fichiers_non_references_et_anciens_sont_supprimes() = runTest {
        referencer(occtax = fichier("a.jpg"), suivis = fichier("b.jpg"))
        fichier("c.jpg")

        val supprimes = balayage(decalageMs = 49L * 3600 * 1000).purger()

        assertEquals(1, supprimes)
        assertTrue(systeme.exists(dossier / "a.jpg"), "référencé par une observation OccTax")
        assertTrue(systeme.exists(dossier / "b.jpg"), "référencé par une saisie des Suivis")
        assertFalse(systeme.exists(dossier / "c.jpg"), "orphelin depuis plus de 48 h")
    }

    @Test
    fun un_fichier_recent_n_est_jamais_supprime() = runTest {
        // Tout juste importé : il n'est peut-être référencé que par un formulaire EN COURS.
        fichier("c.jpg")
        assertEquals(0, balayage(decalageMs = 0).purger())
        assertTrue(systeme.exists(dossier / "c.jpg"))
    }

    @Test
    fun sans_dossier_de_medias_rien_a_faire() = runTest {
        assertEquals(0, balayage(decalageMs = 49L * 3600 * 1000).purger())
    }
}
