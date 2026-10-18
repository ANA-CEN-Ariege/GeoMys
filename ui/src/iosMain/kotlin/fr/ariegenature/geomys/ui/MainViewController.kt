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

package fr.ariegenature.geomys.ui

import androidx.compose.ui.window.ComposeUIViewController
import fr.ariegenature.geomys.InfosApp
import fr.ariegenature.geomys.servicesGeoNature
import fr.ariegenature.geomys.ui.carte.ConfigurationCarte
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice
import platform.UIKit.UIViewController

/** Point d'entrée iOS, appelé par ContentView.swift (iosApp). */
fun MainViewController(): UIViewController {
    val infos = infosIos()
    ConfigurationCarte.initialiser(infos)
    val lancement = parametresLancement()
    val services = servicesGeoNature()
    return ComposeUIViewController { GeoMysApp(infos, services, lancement) }
}

/** Paramètres d'essai passés en arguments `-cle valeur`, par exemple :
 *  `xcrun simctl launch <appareil> fr.ariegenature.geomys.common -ecran prototype-carte -fond serveur-test` */
private fun parametresLancement(): Map<String, String> {
    val arguments = NSProcessInfo.processInfo.arguments.map { it.toString() }
    return arguments.zipWithNext()
        .filter { (cle, _) -> cle.startsWith("-") && cle.length > 1 }
        .associate { (cle, valeur) -> cle.removePrefix("-") to valeur }
}

private fun infosIos(): InfosApp {
    val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    val appareil = UIDevice.currentDevice
    return InfosApp(
        version = version ?: "?",
        plateforme = "${appareil.systemName} ${appareil.systemVersion}",
    )
}
