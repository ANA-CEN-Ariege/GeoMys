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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/TopologiePolygoneTest.kt) : les
// trois cas de pointDansAnneau, sur des LatLon ; Robolectric retiré, JUnit remplacé par kotlin.test. Le cas
// de memePoint n'est pas repris avec la fonction, restée sans appelant depuis le retrait de la topologie
// partagée.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.geo.LatLon
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Outils géométriques de la carte OccHab : appartenance d'un point à un anneau (avertissement
 *  « sommet de trou sorti du polygone »).
 *
 *  La topologie PARTAGÉE entre polygones aimantés a été retirée le 2026-10-18 : un geste ne
 *  modifie plus que la station en cours d'édition, donc plus rien n'est répercuté chez un
 *  voisin — ni un déplacement de sommet, ni une insertion sur une arête commune. */
class TopologiePolygoneTest {

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
