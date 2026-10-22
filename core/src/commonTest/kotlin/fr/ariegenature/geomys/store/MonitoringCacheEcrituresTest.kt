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

// Propre au portage : le défaut n° 11 de l'inventaire (deux écritures simultanées de la même clé du cache
// Monitoring partageaient le même fichier temporaire), présent dans GeoMys-Android, corrigé ici. Sur le
// vrai système de fichiers, plusieurs fils à la fois.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

    @Test
    fun une_adresse_trop_longue_pour_un_nom_de_fichier_se_met_en_cache() {
        // Le « Code phénologique » de pt_ecoute_avifaune sur le serveur de démonstration (21 octobre) :
        // 17 codes dans l'adresse, 449 octets de nom de fichier, que rien ne pouvait écrire.
        val codes = listOf("1") + (1..16).map { "CA_" + it.toString().padStart(2, '0') }
        val adresse = "nomenclatures/nomenclature/OCC_COMPORTEMENT?orderby=label_default&" +
            codes.joinToString("&") { "cd_nomenclature=$it" }
        val cache = MonitoringCache(dossier, FileSystem.SYSTEM)
        val cle = cache.keyOptionsDatalist(adresse)
        assertTrue((cle + ".corrupt").encodeToByteArray().size <= 255, "${cle.length} caractères")
        cache.setJson(cle, """{"values":[]}""")
        assertEquals("""{"values":[]}""", MonitoringCache(dossier, FileSystem.SYSTEM).getJson(cle), "relu par un nouveau processus")
        // Deux adresses au même long début restent distinctes ; une adresse courte garde son nom.
        assertNotEquals(cle, cache.keyOptionsDatalist(adresse.replace("CA_16", "CA_17")))
        assertEquals("datalist_nomenclatures_nomenclature_SEXE.json", cache.keyOptionsDatalist("nomenclatures/nomenclature/SEXE"))
    }
}
