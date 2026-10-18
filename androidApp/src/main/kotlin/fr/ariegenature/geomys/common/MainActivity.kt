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

package fr.ariegenature.geomys.common

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.ariegenature.geomys.InfosApp
import fr.ariegenature.geomys.ui.GeoMysApp
import fr.ariegenature.geomys.ui.carte.ConfigurationCarte

private val CLES_LANCEMENT = listOf("ecran", "fond", "zoom", "action", "taxons", "lecture")

/** Point d'entrée Android : il ne fait qu'afficher l'interface commune (module ui). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Thème toujours sombre : icônes claires dans les barres système, quel que soit le réglage
        // du téléphone.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val infos = InfosApp(
            version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?",
            plateforme = "Android ${Build.VERSION.RELEASE}",
        )
        ConfigurationCarte.initialiser(infos)
        // Paramètres d'essai : adb shell am start … --es ecran prototype-carte --es fond serveur-test
        val lancement = CLES_LANCEMENT.mapNotNull { cle -> intent.getStringExtra(cle)?.let { cle to it } }.toMap()
        setContent { GeoMysApp(infos, lancement) }
    }
}
