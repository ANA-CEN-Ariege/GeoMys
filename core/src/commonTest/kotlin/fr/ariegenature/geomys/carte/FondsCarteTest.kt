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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/FondCarteTest.kt et
// FondCartePersistanceTest.kt) : les deux fichiers se rejoignent ; Robolectric et ses SharedPreferences
// deviennent un MapSettings, JUnit kotlin.test.

package fr.ariegenature.geomys.carte

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.store.PreferencesGeoMys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fonds de carte : libellés du MENU de choix, et mémorisation du fond choisi (partagé entre
 *  toutes les cartes), fonds EN LIGNE et repli. */
class FondsCarteTest {

    private val reglages = MapSettings()
    private val preferences = PreferencesGeoMys(reglages)
    private val aucunMbtiles = { _: String -> false }

    @Test
    fun chaque_fond_a_un_libelle_non_vide() {
        FondCarte.entries.forEach { assertTrue(it.libelle.isNotBlank(), "libellé vide pour $it") }
    }

    @Test
    fun les_libelles_sont_tous_distincts() {
        val libelles = FondCarte.entries.map { it.libelle }
        assertEquals(libelles.size, libelles.toSet().size, "libellés en double dans le menu")
    }

    @Test
    fun sans_valeur_enregistree_renvoie_osm() {
        assertEquals(FondChoisi.EnLigne(FondCarte.OSM), chargerFondChoisi(preferences, aucunMbtiles))
    }

    @Test
    fun enregistrer_puis_charger_roundtrip_en_ligne() {
        enregistrerFondChoisi(preferences, FondChoisi.EnLigne(FondCarte.ORTHO))
        assertEquals(FondChoisi.EnLigne(FondCarte.ORTHO), chargerFondChoisi(preferences, aucunMbtiles))
        enregistrerFondChoisi(preferences, FondChoisi.EnLigne(FondCarte.SCAN25))
        assertEquals(FondChoisi.EnLigne(FondCarte.SCAN25), chargerFondChoisi(preferences, aucunMbtiles))
        assertEquals("SCAN25", reglages.getStringOrNull("fond_carte"), "même valeur que GeoMys-Android")
    }

    @Test
    fun valeur_corrompue_retombe_sur_osm() {
        reglages.putString("fond_carte", "PAS_UN_FOND")
        assertEquals(FondChoisi.EnLigne(FondCarte.OSM), chargerFondChoisi(preferences, aucunMbtiles))
    }

    @Test
    fun mbtiles_dont_le_fichier_a_disparu_retombe_sur_osm() {
        // Un fond MBTiles mémorisé mais dont le fichier n'existe plus → repli OSM (pas de crash).
        reglages.putString("fond_carte", "mbtiles:inexistant.mbtiles")
        assertEquals(FondChoisi.EnLigne(FondCarte.OSM), chargerFondChoisi(preferences, aucunMbtiles))
        // Propre au portage : le fichier présent est retrouvé.
        assertEquals(FondChoisi.Mbtiles("inexistant.mbtiles"), chargerFondChoisi(preferences) { it == "inexistant.mbtiles" })
    }
}
