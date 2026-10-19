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

// Gardes du lot 7 sur ce que CarteGeometrieFragment de GeoMys-Android @ cdd5196 faisait sans test : la lecture
// des géométries (trous compris, défaut n° 9), le cadrage, les types de la carte d'un protocole, les actions au
// toucher et le fil d'Ariane de ce qu'elles ouvrent.

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CarteSuivisTest {

    private fun type(nom: String, parent: String?, enfants: List<String> = emptyList()) =
        MonitoringSchemaObjet(type = nom, label = nom, labelList = null, nameField = null, parentType = parent, childrenTypes = enfants)

    @Test
    fun un_polygone_garde_ses_trous() {
        val g = CarteSuivis.lire(
            """{"type":"Polygon","coordinates":[[[1.60,42.94],[1.61,42.94],[1.61,42.95],[1.60,42.94]],[[1.601,42.941],[1.602,42.941],[1.602,42.942],[1.601,42.941]]]}""",
        )
        val polygone = g.single() as GeometrieSuivi.Polygone
        assertEquals(2, polygone.anneaux.size, "le trou est un anneau du même polygone, pas un second polygone")
        assertEquals(LatLon(42.94, 1.60), polygone.anneaux[0][0])
    }

    @Test
    fun tous_les_types_geojson_de_la_carte() {
        assertEquals(listOf(GeometrieSuivi.Point(LatLon(42.877, 1.4987))), CarteSuivis.lire("""{"type":"Point","coordinates":[1.4987,42.877]}"""))
        assertEquals(2, CarteSuivis.lire("""{"type":"MultiPoint","coordinates":[[1,42],[2,43]]}""").size)
        assertEquals(
            listOf(GeometrieSuivi.Ligne(listOf(LatLon(42.0, 1.0), LatLon(43.0, 2.0)))),
            CarteSuivis.lire("""{"type":"LineString","coordinates":[[1,42],[2,43]]}"""),
        )
        assertEquals(2, CarteSuivis.lire("""{"type":"MultiPolygon","coordinates":[[[[1,42],[2,42],[2,43],[1,42]]],[[[3,42],[4,42],[4,43],[3,42]]]]}""").size)
        assertTrue(CarteSuivis.lire("""{"type":"GeometryCollection","geometries":[]}""").isEmpty())
        // Coordonnées illisibles : rien plutôt qu'un point faux.
        assertTrue(CarteSuivis.lire("""{"type":"Point","coordinates":["x"]}""").isEmpty())
    }

    @Test
    fun cadrage_d_un_point_ou_d_une_emprise() {
        assertNull(CarteSuivis.emprise(emptyList()))
        assertEquals(EmpriseCarte(41.998, 0.998, 42.002, 1.002), CarteSuivis.emprise(listOf(LatLon(42.0, 1.0))))
        // Points confondus : la boîte du premier.
        assertEquals(EmpriseCarte(41.998, 0.998, 42.002, 1.002), CarteSuivis.emprise(listOf(LatLon(42.0, 1.0), LatLon(42.00001, 1.00001))))
        val e = CarteSuivis.emprise(listOf(LatLon(42.0, 1.0), LatLon(43.0, 2.0)))!!
        assertEquals(41.85, e.sud, 1e-9)
        assertEquals(2.15, e.est, 1e-9)
    }

    @Test
    fun types_de_la_carte_d_un_protocole() {
        val schema = mapOf("module" to type("module", null, listOf("sites_group")), "sites_group" to type("sites_group", "module"), "site" to type("site", "sites_group"))
        assertEquals(listOf("sites_group"), CarteSuivis.typesMacro(listOf("site", "sites_group"), schema))
        // Sans types macro déclarés : on écarte les types dont le parent est présent.
        val sansRacines = schema + ("module" to type("module", null))
        assertEquals(listOf("sites_group"), CarteSuivis.typesMacro(listOf("site", "sites_group"), sansRacines))
        assertEquals(listOf("site", "sites_group"), CarteSuivis.typesMacro(listOf("site", "sites_group"), null))
    }

    @Test
    fun actions_et_fil_d_un_objet_touche() {
        val schema = mapOf("site" to type("site", "module", listOf("visit")), "visit" to type("visit", "site"))
        val site = ObjetCarte("site", 501, "Col de Port", enfant = false, geometries = emptyList())
        assertEquals(
            listOf("Voir la fiche", "Nouvelle visite"),
            CarteSuivis.actions(site, objetCourant = false, schema = schema) { "Nouvelle visite" }.map { it.libelle },
        )
        // L'objet ouvert : pas de fiche (on y est), seulement la saisie.
        assertEquals(listOf("Nouvelle visite"), CarteSuivis.actions(site, objetCourant = true, schema = schema) { "Nouvelle visite" }.map { it.libelle })
        assertTrue(CarteSuivis.actions(ObjetCarte("visit", 9, "", true, emptyList()), true, schema) { "" }.isEmpty())

        val filCarte = filRacineSuivis("STOM Ariège")
        assertEquals(filCarte + FilSegment("site", 501, "Col de Port"), CarteSuivis.filPourObjet(site, false, filCarte, "STOM Ariège"))
        assertEquals(filCarte, CarteSuivis.filPourObjet(site, true, filCarte, "STOM Ariège"))
        assertEquals(filRacineSuivis("STOM") + FilSegment("site", 501, "Col de Port"), CarteSuivis.filPourObjet(site, false, emptyList(), "STOM"))
    }
}
