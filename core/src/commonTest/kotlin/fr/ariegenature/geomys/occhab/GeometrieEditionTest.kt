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

// Gardes du lot 8 sur ce que EditeurGeometrie et PileAnnulationGeometrie de GeoMys-Android @ cdd5196
// faisaient sans test : les gestes sur la géométrie éditée, les poignées « + », les trous, la géométrie
// posée sur la station et l'annulation. Les deux premiers cas réécrivent en tests de comportement les
// gardes textuelles d'OccHabSommetAimanteIndependantTest (aucune propagation chez une voisine).

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.util.GeoJsonCoords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeometrieEditionTest {

    // Coordonnées exactes en binaire : milieux et centroïdes se comparent sans tolérance.
    private val a = LatLon(42.0, 1.0)
    private val b = LatLon(42.0, 1.5)
    private val c = LatLon(42.5, 1.5)
    private val d = LatLon(42.5, 1.0)
    private val trou = listOf(LatLon(42.125, 1.125), LatLon(42.125, 1.25), LatLon(42.25, 1.25))

    private fun polygone(vararg sommets: LatLon) = GeometrieEdition(ModeGeometrie.POLYGONE, null, sommets.toList())

    /** Polygone voisin, à l'est : il partage l'arête b-c. */
    private fun voisine() = OccHabStation(
        geometryType = "Polygon",
        geometryCoordsJson = GeoJsonCoords.format(listOf(b, LatLon(42.0, 2.0), LatLon(42.5, 2.0), c)),
        latitude = 42.25,
        longitude = 1.75,
    )

    @Test
    fun le_glisser_d_un_sommet_aimante_ne_modifie_que_la_station_editee() {
        val voisine = voisine()
        val jsonAvant = voisine.geometryCoordsJson
        // Le relâcher s'aimante : le sommet prend EXACTEMENT la coordonnée de la cible.
        val cible = CarteOccHab.ciblesAimantage(listOf(voisine), emptyList()).first()
        val editee = polygone(a, LatLon(42.0, 1.499), c, d).avecSommet(1, cible)
        assertEquals(b, editee.sommets[1])
        // Le voisin NE BOUGE PAS, même confondu : déplacer de nouveau le sommet ne le marie pas.
        val deplacee = editee.avecSommet(1, LatLon(41.75, 1.75))
        assertEquals(jsonAvant, voisine.geometryCoordsJson)
        assertEquals(b, GeoJsonCoords.parse(voisine.geometryCoordsJson).first())
        assertEquals(LatLon(41.75, 1.75), deplacee.sommets[1])
    }

    @Test
    fun inserer_un_sommet_sur_une_arete_commune_ne_touche_pas_la_voisine() {
        val voisine = voisine()
        val editee = polygone(a, b, c, d)
        val pile = PileAnnulationGeometrie()
        pile.empiler(editee)
        val apres = editee.avecSommetInsere(1)!!
        assertEquals(listOf(a, b, LatLon(42.25, 1.5), c, d), apres.sommets)
        assertEquals(4, GeoJsonCoords.parse(voisine.geometryCoordsJson).size, "la voisine garde ses quatre sommets")
        // « Annuler » ne rend que la station éditée.
        assertEquals(editee, pile.depiler())
        assertTrue(pile.estVide)
    }

    @Test
    fun le_tap_pose_le_point_ou_ajoute_un_sommet_en_bout_d_anneau() {
        val point = GeometrieEdition().avecTap(a)
        assertEquals(a, point.point)
        assertTrue(point.geomOk)
        val trace = GeometrieEdition(ModeGeometrie.POLYGONE).avecTap(a).avecTap(b)
        assertEquals(listOf(a, b), trace.sommets)
        assertFalse(trace.geomOk, "trois sommets au moins")
        assertTrue(trace.avecTap(c).geomOk)
    }

    @Test
    fun poignees_sur_une_station_selectionnee_seulement_trous_compris() {
        val g = polygone(a, b, c, d).copy(trous = listOf(trou, listOf(LatLon(42.375, 1.375))))
        assertTrue(g.poignees(chargee = false).isEmpty(), "pendant le tracé d'une nouvelle géométrie, pas de poignée")
        val poignees = g.poignees(chargee = true)
        // Quatre arêtes extérieures, fermeture comprise, puis les trois du trou ; le trou dégénéré n'en a pas.
        assertEquals(7, poignees.size)
        assertEquals(PoigneeArete(-1, 3, LatLon(42.25, 1.0)), poignees[3])
        assertEquals(PoigneeArete(0, 0, LatLon(42.125, 1.1875)), poignees[4])
        assertTrue(GeometrieEdition(ModeGeometrie.POLYGONE, null, listOf(a, b)).poignees(chargee = true).isEmpty())
    }

    @Test
    fun sommets_de_trou_editables_et_avertissement_hors_polygone() {
        val g = polygone(a, b, c, d).copy(trous = listOf(trou))
        val deplace = g.avecSommetTrou(0, 1, LatLon(42.375, 1.375))
        assertEquals(LatLon(42.375, 1.375), deplace.trous[0][1])
        assertEquals(trou, g.trous[0], "la géométrie de départ ne change pas")
        assertEquals(4, g.avecSommetTrouInsere(0, 2)!!.trous[0].size)
        assertNull(g.avecSommetTrouInsere(1, 0), "anneau inconnu")
        assertFalse(g.sommetTrouHorsPolygone(LatLon(42.375, 1.375)))
        assertTrue(g.sommetTrouHorsPolygone(LatLon(43.0, 1.25)))
    }

    @Test
    fun geometrie_posee_sur_la_station() {
        val g = polygone(a, b, c, d).copy(trous = listOf(trou, listOf(LatLon(42.375, 1.375))))
        val geo = g.geometrie()!!
        assertEquals("Polygon", geo.type)
        assertEquals(42.25, geo.latitude)
        assertEquals(1.25, geo.longitude)
        assertEquals(1, GeoJsonCoords.parseAnneaux(geo.trousJson).size, "le trou dégénéré n'est pas persisté")
        assertTrue(g.surfaceM2()!! > 0)
        assertNull(polygone(a, b).geometrie(), "pas de polygone sous trois sommets")
        val point = GeometrieEdition(point = a)
        assertEquals(GeometrieStation("Point", 42.0, 1.0, null, null), point.geometrie())
        assertNull(point.surfaceM2(), "un point n'a pas de surface")
    }

    @Test
    fun geometrie_d_une_station_existante() {
        val s = OccHabStation(
            geometryType = "Polygon",
            geometryCoordsJson = "[[1.0,42.0],[1.5,42.0],[1.5,42.5]]",
            geometryTrousJson = "[[[1.125,42.125],[1.25,42.125],[1.25,42.25]]]",
        )
        val g = GeometrieEdition.depuisStation(s)!!
        assertEquals(ModeGeometrie.POLYGONE, g.mode)
        assertEquals(listOf(a, b, c), g.sommets)
        assertEquals(listOf(trou), g.trous)
        assertEquals(LatLon(42.9, 1.4), GeometrieEdition.depuisStation(OccHabStation(latitude = 42.9, longitude = 1.4))!!.point)
        assertNull(GeometrieEdition.depuisStation(OccHabStation()), "station vierge : rien à éditer")
    }

    @Test
    fun la_pile_d_annulation_garde_les_trente_derniers_gestes() {
        val pile = PileAnnulationGeometrie()
        repeat(35) { i -> pile.empiler(GeometrieEdition(point = LatLon(i.toDouble(), 0.0))) }
        val depilees = generateSequence { pile.depiler() }.toList()
        assertEquals(30, depilees.size)
        assertEquals(34.0, depilees.first().point!!.latitude, "la dernière opération d'abord")
        assertEquals(5.0, depilees.last().point!!.latitude, "les cinq plus anciennes sont oubliées")
    }
}
