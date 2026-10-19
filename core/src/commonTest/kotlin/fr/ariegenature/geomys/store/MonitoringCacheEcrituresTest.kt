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

// Propre au portage : le défaut n° 11 de l'inventaire (deux écritures simultanées de la même clé du cache
// Monitoring partageaient le même fichier temporaire), présent dans GeoMys-Android, corrigé ici. Sur le
// vrai système de fichiers, plusieurs fils à la fois.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.SYSTEM

class MonitoringCacheEcrituresTest {

    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-monitoring-${uuidAleatoire()}"

    @AfterTest
    fun nettoyer() {
        FileSystem.SYSTEM.deleteRecursively(dossier)
    }

    @Test
    fun des_ecritures_simultanees_de_la_meme_cle_ne_se_melent_pas() = runBlocking {
        val cache = MonitoringCache(dossier, FileSystem.SYSTEM)
        // Des contenus volumineux et tous différents : un mélange se verrait.
        val contenus = (0 until 16).map { i -> "[\"" + ('a' + i).toString().repeat(200_000) + "\"]" }
        repeat(5) { tour ->
            coroutineScope {
                contenus.forEach { c -> launch(Dispatchers.Default) { cache.setJson("module_stom", c) } }
            }
            val lu = cache.getJson("module_stom")
            assertTrue(lu in contenus, "tour $tour : le fichier doit être l'une des écritures, entière (${lu?.length} caractères)")
        }
        assertTrue(FileSystem.SYSTEM.list(dossier).none { it.name.endsWith(".tmp") }, "aucun fichier temporaire ne reste")
    }
}
