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

// Propre au portage : ces règles vivaient dans TraceFragment (GeoMys-Android @ cdd5196), sans test.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.PointTrace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Les calculs de la carte de saisie multi-taxons. */
class CarteSaisieTest {

    private fun obs(id: String, releveId: String? = null, lat: Double = 42.9, lon: Double = 1.4, type: String? = null, coords: String? = null) =
        Observation(id = id, espece = "Esp $id", latitude = lat, longitude = lon, releveId = releveId, geometryType = type, geometryCoordsJson = coords)

    @Test
    fun un_marqueur_par_releve_et_aucun_pour_les_formes() {
        val points = CarteSaisie.relevesPoints(listOf(
            obs("a", "R1"), obs("b", "R1"), obs("solo"),
            obs("forme", "R2", type = "Polygon", coords = "[[1,42],[1.1,42],[1.1,42.1]]"),
        ))
        assertEquals(listOf("R1", "solo"), points.map { it.cle })
        assertEquals("2 espèces", points[0].titre)
        assertEquals("Esp solo", points[1].titre)
    }

    @Test
    fun formes_avec_assez_de_sommets() {
        val formes = CarteSaisie.relevesFormes(listOf(
            obs("poly", "R1", type = "Polygon", coords = "[[1,42],[1.1,42],[1.1,42.1]]"),
            obs("poly2", "R1", type = "Polygon", coords = "[[1,42],[1.1,42],[1.1,42.1]]"),
            obs("maigre", type = "Polygon", coords = "[[1,42],[1.1,42]]"),
            obs("ligne", type = "LineString", coords = "[[1,42],[1.1,42]]"),
        ))
        assertEquals(listOf("R1", "ligne"), formes.map { it.cle })
        assertEquals(listOf("poly", "poly2"), formes[0].ids)
        assertEquals(LatLon(42.0, 1.0), formes[0].sommets[0])
    }

    @Test
    fun geometrie_validee_selon_le_mode() {
        val centre = LatLon(43.0, 1.5)
        assertEquals(GeometrieFinale("Point", null, 43.0, 1.5), CarteSaisie.geometrieFinale(ModeGeom.POINT, emptyList(), centre))
        assertEquals(42.0, CarteSaisie.geometrieFinale(ModeGeom.POINT, listOf(LatLon(42.0, 1.0)), centre)!!.latitude)
        assertNull(CarteSaisie.geometrieFinale(ModeGeom.LINE, listOf(LatLon(42.0, 1.0)), centre))
        assertNull(CarteSaisie.geometrieFinale(ModeGeom.POLYGON, listOf(LatLon(42.0, 1.0), LatLon(42.0, 2.0)), centre))
        val poly = CarteSaisie.geometrieFinale(ModeGeom.POLYGON, listOf(LatLon(42.0, 1.0), LatLon(42.0, 2.0), LatLon(43.0, 1.5)), centre)!!
        assertEquals("Polygon", poly.type)
        assertEquals("[[1,42],[2,42],[1.5,43]]", poly.coordsJson)
        assertEquals(42.333333333333336, poly.latitude, 1e-9)
        assertEquals(1.5, poly.longitude, 1e-9)
        assertEquals(listOf(LatLon(1.0, 1.0)), CarteSaisie.poserSommet(ModeGeom.POINT, listOf(LatLon(0.0, 0.0)), LatLon(1.0, 1.0)))
        assertEquals(2, CarteSaisie.poserSommet(ModeGeom.LINE, listOf(LatLon(0.0, 0.0)), LatLon(1.0, 1.0)).size)
    }

    @Test
    fun cadrage_d_une_reprise() {
        assertNull(CarteSaisie.cadrageReprise(listOf(obs("zero", lat = 0.0, lon = 0.0)), emptyList()))
        assertIs<CadrageReprise.Point>(CarteSaisie.cadrageReprise(listOf(obs("a"), obs("b")), emptyList()))
        // Trace est-ouest plate : épaisseur minimale sur la latitude.
        val emprise = CarteSaisie.cadrageReprise(emptyList(), listOf(PointTrace(42.9, 1.40), PointTrace(42.9, 1.42)))
        assertIs<CadrageReprise.Emprise>(emprise)
        assertEquals(42.9 - 5e-4, emprise.sud, 1e-12)
        assertEquals(42.9 + 5e-4, emprise.nord, 1e-12)
        assertEquals(1.40, emprise.ouest, 1e-12)
        // Les sommets d'un polygone comptent.
        val avecForme = CarteSaisie.cadrageReprise(listOf(obs("p", type = "Polygon", coords = "[[1.0,42.0],[1.2,42.0],[1.2,42.2]]", lat = 42.06, lon = 1.13)), emptyList())
        assertIs<CadrageReprise.Emprise>(avecForme)
        assertEquals(1.2, avecForme.est, 1e-12)
    }

    @Test
    fun textes_du_positionnement_et_des_options() {
        assertEquals("Touchez pour placer le point · appui long pour le déplacer", CarteSaisie.consignePositionnement(false, ModeGeom.POINT))
        assertEquals("Déplacez la carte pour repositionner l'observation", CarteSaisie.consignePositionnement(true, ModeGeom.LINE))
        assertEquals("Valider ce polygone", CarteSaisie.libelleValidation(false, ModeGeom.POLYGON))
        val deux = listOf(obs("a", "R1").copy(nombre = 3, notes = "chant"), obs("b", "R1"))
        val (titre, message) = CarteSaisie.optionsReleve(deux)
        assertTrue(titre.startsWith("2 espèces · "))
        assertEquals("• Esp a × 3 — chant\n• Esp b", message)
        assertTrue(CarteSaisie.optionsReleve(listOf(obs("a"))).first.startsWith("Esp a · "))
    }

    @Test
    fun envoi_propose_si_configuree_et_determinee() {
        assertTrue(CarteSaisie.peutEnvoyer(listOf(obs("a").copy(cdNom = 1)), configuree = true))
        assertTrue(!CarteSaisie.peutEnvoyer(listOf(obs("a")), configuree = true))
        assertTrue(CarteSaisie.peutEnvoyer(listOf(obs("a").copy(releveSansEspece = true)), configuree = true))
        assertTrue(!CarteSaisie.peutEnvoyer(listOf(obs("a").copy(cdNom = 1)), configuree = false))
    }

    /** Le bonhomme de la carte (demande du 24 octobre 2026) dit ce que fait son toucher. */
    @Test
    fun message_du_bonhomme_selon_la_trace() {
        assertEquals("La trace GPS démarre.", CarteSaisie.messageTrace(enCours = false, commencee = false))
        assertEquals("La trace GPS reprend.", CarteSaisie.messageTrace(enCours = false, commencee = true))
        assertEquals("La trace GPS est arrêtée.", CarteSaisie.messageTrace(enCours = true, commencee = true))
    }
}
