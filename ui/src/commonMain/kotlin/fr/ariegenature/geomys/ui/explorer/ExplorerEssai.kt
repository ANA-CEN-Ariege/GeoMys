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

package fr.ariegenature.geomys.ui.explorer

import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.ObsExplorer
import fr.ariegenature.geomys.model.Taxon
import kotlin.math.roundToLong

/** Les observations FICTIVES de l'essai `ecran=explorer-essai`, pour l'émulateur et le simulateur, sans
 *  serveur : dix cellules d'une à 150 observations, une par groupe, autour du centre de la zone demandée
 *  (arrondi au centième de degré : les marqueurs ne bougent pas avec la carte). */
internal object ExplorerEssai {

    /** Le départ de l'essai : près de Foix, au zoom 15. */
    val DEPART = LatLon(42.9649, 1.6056) to 15.0

    private class Espece(val taxon: Taxon, val nomVern: String, val nomSci: String)

    private val especes = listOf(
        Espece(Taxon.OISEAU, "merle noir", "Turdus merula"),
        Espece(Taxon.MAMMIFERE, "écureuil roux", "Sciurus vulgaris"),
        Espece(Taxon.REPTILE, "lézard des murailles", "Podarcis muralis"),
        Espece(Taxon.BATRACIEN, "crapaud commun", "Bufo bufo"),
        Espece(Taxon.MOLLUSQUE, "escargot de Bourgogne", "Helix pomatia"),
        Espece(Taxon.POISSON, "truite commune", "Salmo trutta"),
        Espece(Taxon.INSECTE, "machaon", "Papilio machaon"),
        Espece(Taxon.INVERTEBRES, "épeire diadème", "Araneus diadematus"),
        Espece(Taxon.PLANTE, "gentiane jaune", "Gentiana lutea"),
        Espece(Taxon.FONGE, "", "Amanita muscaria"),
    )

    /** L'effectif de chaque cellule : un, deux et trois chiffres. */
    private val effectifs = listOf(1, 3, 12, 1, 150, 2, 1, 7, 1, 45)

    fun observations(emprise: Emprise): List<ObsExplorer> {
        val lat0 = ((emprise.sud + emprise.nord) / 2 * 100).roundToLong() / 100.0
        val lon0 = ((emprise.ouest + emprise.est) / 2 * 100).roundToLong() / 100.0
        return especes.flatMapIndexed { i, espece ->
            val lat = lat0 + (i / 5 - 0.5) * 0.004
            val lon = lon0 + (i % 5 - 2) * 0.003
            List(effectifs[i]) { n ->
                // Une cellule sur deux mêle un second groupe, pour la liste.
                val e = if (n % 4 == 3 && i % 2 == 0) especes[(i + 1) % especes.size] else espece
                ObsExplorer(
                    nomCite = e.nomSci,
                    nomVern = e.nomVern,
                    nomSci = e.nomSci,
                    latitude = lat,
                    longitude = lon,
                    date = "2026-${deuxChiffres(1 + (n + i) % 9)}-${deuxChiffres(1 + (n * 7 + i) % 28)}",
                    nombre = 1 + n % 4,
                    taxon = e.taxon,
                )
            }
        }
    }

    private fun deuxChiffres(n: Int) = n.toString().padStart(2, '0')
}
