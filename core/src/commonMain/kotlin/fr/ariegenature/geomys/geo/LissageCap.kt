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

package fr.ariegenature.geomys.geo

import kotlin.math.abs
import kotlin.math.exp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Lissage du cap affiché par une carte qui tourne avec l'appareil.
 *
 *  Le capteur donne des mesures irrégulières et bruitées : les appliquer telles quelles fait
 *  tourner la carte par à-coups. Appelé à chaque image avec le dernier cap mesuré, [avancer]
 *  rapproche le cap affiché de cette cible par le plus court chemin (de 350° vers 10°, on tourne de
 *  +20°, pas de −340°), d'autant plus vite que l'écart est grand. [constanteTemps] règle la
 *  vivacité : au bout de ce délai, 63 % de l'écart est rattrapé. En dessous de [zoneMorte] degrés,
 *  le cap ne bouge plus, pour ne pas faire trembler la carte au rythme du bruit du capteur. */
class LissageCap(
    private val constanteTemps: Duration = 150.milliseconds,
    private val zoneMorte: Double = 0.5,
) {
    /** Cap affiché, en degrés dans [0, 360), ou null avant la première mesure. */
    var cap: Double? = null
        private set

    /** Avance le cap affiché vers [cible] (degrés) pendant [ecoule], et le renvoie. */
    fun avancer(cible: Double, ecoule: Duration): Double {
        val actuel = cap ?: return normaliserDegres(cible).also { cap = it }
        val ecart = ecartAngulaire(actuel, cible)
        if (abs(ecart) < zoneMorte) return actuel
        val part = 1.0 - exp(-(ecoule / constanteTemps))
        return normaliserDegres(actuel + ecart * part).also { cap = it }
    }
}

/** Écart signé le plus court pour aller de l'angle [de] à l'angle [vers], dans [−180, 180). */
fun ecartAngulaire(de: Double, vers: Double): Double =
    normaliserDegres(vers - de + 180.0) - 180.0

/** Ramène un angle en degrés dans [0, 360). */
fun normaliserDegres(angle: Double): Double {
    val reste = angle % 360.0
    return if (reste < 0.0) reste + 360.0 else reste
}
