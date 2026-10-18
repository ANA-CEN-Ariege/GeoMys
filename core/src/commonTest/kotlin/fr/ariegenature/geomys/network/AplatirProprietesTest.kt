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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/AplatirProprietesTest.kt) :
// org.json remplacé par son portage, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Aplatissement des `properties` d'un objet serveur en Map<String,String> (scalaires
 *  uniquement) — base de l'extraction de nom et de l'affichage. */
class AplatirProprietesTest {

    @Test
    fun props_null_renvoie_map_vide() {
        assertTrue(MonitoringObjets.aplatirProprietes(null).isEmpty())
    }

    @Test
    fun scalaires_conserves_et_stringifies() {
        val props = JSONObject("""{"nom":"Forêt","altitude":1200,"actif":true}""")
        val map = MonitoringObjets.aplatirProprietes(props)
        assertEquals("Forêt", map["nom"])
        assertEquals("1200", map["altitude"])
        assertEquals("true", map["actif"])
    }

    @Test
    fun chaine_vide_et_null_ignores() {
        val props = JSONObject("""{"vide":"","absent":null,"ok":"x"}""")
        val map = MonitoringObjets.aplatirProprietes(props)
        assertFalse(map.containsKey("vide"))
        assertFalse(map.containsKey("absent"))
        assertEquals("x", map["ok"])
    }

    @Test
    fun objets_et_tableaux_imbriques_ignores() {
        val props = JSONObject("""{"geom":{"type":"Point"},"tags":[1,2],"nom":"Site"}""")
        val map = MonitoringObjets.aplatirProprietes(props)
        assertFalse(map.containsKey("geom"))
        assertFalse(map.containsKey("tags"))
        assertEquals("Site", map["nom"])
    }
}
