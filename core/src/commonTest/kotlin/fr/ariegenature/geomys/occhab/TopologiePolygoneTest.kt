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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/TopologiePolygoneTest.kt) : les
// trois cas de pointDansAnneau, sur des LatLon ; Robolectric retiré, JUnit remplacé par kotlin.test. Les trois
// cas de la topologie partagée viennent de sa version précédente (cdd5196~1), revenue le 20 octobre, à la
// demande de l'utilisateur.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.geo.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Outils géométriques de la carte OccHab : topologie partagée des polygones aimantés (insertion
 *  sur une arête commune, déplacement d'un sommet partagé, tolérance d'égalité) et appartenance d'un
 *  point à un anneau (avertissement « sommet de trou sorti du polygone »). */
class TopologiePolygoneTest {

    private val anneau = listOf(LatLon(42.0, 1.0), LatLon(42.0, 2.0), LatLon(43.0, 2.0))

    @Test
    fun insertion_sur_arete_commune_dans_les_deux_sens_fermeture_incluse() {
        val a = LatLon(42.0, 1.0)
        val b = LatLon(42.0, 2.0)
        val m = TopologiePolygone.milieu(a, b)
        assertEquals(listOf(a, m, b, LatLon(43.0, 2.0)), TopologiePolygone.insererSurArete(anneau, a, b, m))
        // Sens inverse (b → a) : même arête.
        assertEquals(listOf(a, m, b, LatLon(43.0, 2.0)), TopologiePolygone.insererSurArete(anneau, b, a, m))
        // Arête de fermeture (dernier → premier) : inséré après le dernier sommet.
        val c = LatLon(43.0, 2.0)
        assertEquals(LatLon(42.5, 1.5), TopologiePolygone.insererSurArete(anneau, c, a, TopologiePolygone.milieu(c, a))?.get(3))
        // Arête étrangère : rien.
        assertNull(TopologiePolygone.insererSurArete(anneau, LatLon(50.0, 5.0), a, m))
    }

    @Test
    fun deplacement_d_un_sommet_partage() {
        assertEquals(
            listOf(LatLon(42.0, 1.0), LatLon(42.1, 2.1), LatLon(43.0, 2.0)),
            TopologiePolygone.deplacerSommetsConfondus(anneau, LatLon(42.0, 2.0), LatLon(42.1, 2.1)),
        )
        assertNull(TopologiePolygone.deplacerSommetsConfondus(anneau, LatLon(0.0, 0.0), LatLon(1.0, 1.0)), "aucun sommet confondu")
    }

    @Test
    fun meme_point_tolerance_fine() {
        assertTrue(TopologiePolygone.memePoint(LatLon(42.0, 1.0), LatLon(42.0 + 1e-10, 1.0)))
        assertFalse(TopologiePolygone.memePoint(LatLon(42.0, 1.0), LatLon(42.0 + 1e-6, 1.0)))
    }

    /** Carré 0..10 (LatLon = latitude, longitude). */
    private fun carre() = listOf(LatLon(0.0, 0.0), LatLon(0.0, 10.0), LatLon(10.0, 10.0), LatLon(10.0, 0.0))

    @Test
    fun point_dans_anneau_interieur_et_exterieur() {
        assertTrue(TopologiePolygone.pointDansAnneau(LatLon(5.0, 5.0), carre()))
        assertFalse(TopologiePolygone.pointDansAnneau(LatLon(5.0, 20.0), carre()))
        assertFalse(TopologiePolygone.pointDansAnneau(LatLon(-1.0, 5.0), carre()))
    }

    @Test
    fun point_dans_anneau_forme_concave() {
        // « L » : le creux ne doit PAS compter comme intérieur.
        val l = listOf(
            LatLon(0.0, 0.0), LatLon(0.0, 10.0), LatLon(4.0, 10.0),
            LatLon(4.0, 4.0), LatLon(10.0, 4.0), LatLon(10.0, 0.0),
        )
        assertTrue(TopologiePolygone.pointDansAnneau(LatLon(2.0, 2.0), l))
        assertFalse(TopologiePolygone.pointDansAnneau(LatLon(8.0, 8.0), l), "dans le creux du L")
    }

    @Test
    fun anneau_degenere_jamais_interieur() {
        assertFalse(TopologiePolygone.pointDansAnneau(LatLon(1.0, 1.0), emptyList()))
        assertFalse(TopologiePolygone.pointDansAnneau(LatLon(1.0, 1.0), listOf(LatLon(0.0, 0.0), LatLon(2.0, 2.0))))
    }
}
