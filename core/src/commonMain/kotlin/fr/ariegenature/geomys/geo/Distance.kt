/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifié pour GeoMys-Common (ANA - CEN Ariège, 2026) : traduit de Java en Kotlin multiplateforme
 * depuis les sources du SDK Android (android-36.1, android/location/Location.java,
 * computeDistanceAndBearing), réduit au calcul de la distance. Texte de la licence :
 * licences/Apache-2.0.txt.
 */

package fr.ariegenature.geomys.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Distance en mètres entre deux points WGS84, sur l'ellipsoïde : le calcul de
 *  `Location.distanceBetween` et `Location.distanceTo` d'Android, qu'appelait GeoMys-Android (trace
 *  GPS, import GPX, observations proches). Les deux plateformes comptent ainsi les mêmes mètres.
 *  Comme sous Android, le résultat passe par un Float. */
fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    // Based on http://www.ngs.noaa.gov/PUBS_LIB/inverse.pdf
    // using the "Inverse Formula" (section 4)

    // Convert lat/long to radians
    val phi1 = lat1 * (PI / 180.0)
    val phi2 = lat2 * (PI / 180.0)
    val lambda1 = lon1 * (PI / 180.0)
    val lambda2 = lon2 * (PI / 180.0)

    val a = 6378137.0 // WGS84 major axis
    val b = 6356752.3142 // WGS84 semi-major axis
    val f = (a - b) / a
    val aSqMinusBSqOverBSq = (a * a - b * b) / (b * b)

    val l = lambda2 - lambda1
    var aA = 0.0
    val u1 = atan((1.0 - f) * tan(phi1))
    val u2 = atan((1.0 - f) * tan(phi2))

    val cosU1 = cos(u1)
    val cosU2 = cos(u2)
    val sinU1 = sin(u1)
    val sinU2 = sin(u2)
    val cosU1cosU2 = cosU1 * cosU2
    val sinU1sinU2 = sinU1 * sinU2

    var sigma = 0.0
    var deltaSigma = 0.0

    var lambda = l // initial guess
    for (iter in 0 until 20) {
        val lambdaOrig = lambda
        val cosLambda = cos(lambda)
        val sinLambda = sin(lambda)
        val t1 = cosU2 * sinLambda
        val t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda
        val sinSqSigma = t1 * t1 + t2 * t2
        val sinSigma = sqrt(sinSqSigma)
        val cosSigma = sinU1sinU2 + cosU1cosU2 * cosLambda
        sigma = atan2(sinSigma, cosSigma)
        val sinAlpha = if (sinSigma == 0.0) 0.0 else cosU1cosU2 * sinLambda / sinSigma
        val cosSqAlpha = 1.0 - sinAlpha * sinAlpha
        val cos2SM = if (cosSqAlpha == 0.0) 0.0 else cosSigma - 2.0 * sinU1sinU2 / cosSqAlpha

        val uSquared = cosSqAlpha * aSqMinusBSqOverBSq
        aA = 1 + (uSquared / 16384.0) * (4096.0 + uSquared * (-768 + uSquared * (320.0 - 175.0 * uSquared)))
        val bB = (uSquared / 1024.0) * (256.0 + uSquared * (-128.0 + uSquared * (74.0 - 47.0 * uSquared)))
        val cC = (f / 16.0) * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha))
        val cos2SMSq = cos2SM * cos2SM
        deltaSigma = bB * sinSigma * (
            cos2SM + (bB / 4.0) * (
                cosSigma * (-1.0 + 2.0 * cos2SMSq) -
                    (bB / 6.0) * cos2SM * (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0 + 4.0 * cos2SMSq)
                )
            )

        lambda = l + (1.0 - cC) * f * sinAlpha * (
            sigma + cC * sinSigma * (cos2SM + cC * cosSigma * (-1.0 + 2.0 * cos2SM * cos2SM))
            )

        val delta = (lambda - lambdaOrig) / lambda
        if (abs(delta) < 1.0e-12) break
    }

    return (b * aA * (sigma - deltaSigma)).toFloat().toDouble()
}
