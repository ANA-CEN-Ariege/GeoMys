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

package fr.ariegenature.geomys.ui.carte

import fr.ariegenature.geomys.InfosApp
import kotlin.concurrent.Volatile
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.resource.MapRequestInterceptor
import org.maplibre.compose.resource.MapResourceProvider

/** Réglages globaux de MapLibre, à appliquer une fois au démarrage, avant la première carte
 *  (MainActivity sur Android, MainViewController sur iOS). */
object ConfigurationCarte {

    private const val SCHEMA = "geomys"

    /** Style du serveur de tuiles local du prototype (voir PrototypeCarteScreen). */
    const val ID_STYLE_TEST_LOCAL = "test-local"

    // Lu par le fournisseur de ressources sur les fils du moteur de carte.
    @Volatile
    private var styles: Map<String, String> = emptyMap()
    private var initialisee = false

    /** URL d'un style servi par l'application elle-même. Le même URL sert à l'affichage et aux
     *  paquets hors-ligne, qui retrouvent ainsi les mêmes URL de tuiles. */
    fun urlStyle(id: String): String = "$SCHEMA://styles/$id.json"

    fun initialiser(infos: InfosApp) {
        if (initialisee) return
        initialisee = true

        styles = FondCarte.entries.associate { fond ->
            fond.id to styleRaster(fond.tuiles, fond.zoomMax, fond.attribution)
        } + (ID_STYLE_TEST_LOCAL to styleRaster(
            tuiles = listOf("http://${hoteServeurTest(infos)}:8765/tuiles/{z}/{x}/{y}.png"),
            zoomMax = 19,
            attribution = "Serveur de test",
        ))

        // Les serveurs de tuiles (OSM en particulier) demandent un User-Agent qui identifie
        // l'application. ATTENTION : sans effet avec maplibre-compose 0.20.0. Le prototype carte
        // (PROTOTYPE-CARTE.md) a montré qu'aucun en-tête de l'intercepteur ne part, ni sur Android
        // (le moteur envoie « MapLibreNative/1.0 ») ni sur iOS (« GeoMysCommon/<version>
        // MapLibreNative/… », composé par MapLibre à partir du bundle). Gardé pour le jour où la
        // bibliothèque l'appliquera : à revérifier à chaque montée de version.
        val userAgent = "GeoMysCommon/${infos.version} (${infos.plateforme})"
        DefaultMapRuntime.configure {
            requestInterceptor = MapRequestInterceptor(headers = { mapOf("User-Agent" to userAgent) })
            resourceProvider = MapResourceProvider(scheme = SCHEMA) { requete ->
                val id = requete.url.substringAfter("$SCHEMA://styles/").substringBefore(".json")
                val style = styles[id] ?: error("Style inconnu : ${requete.url}")
                style.encodeToByteArray()
            }
        }
    }

    /** Le Mac hôte, vu depuis l'émulateur Android (10.0.2.2) ou depuis le simulateur iOS, qui
     *  partage son réseau (localhost). Sert seulement au prototype. */
    private fun hoteServeurTest(infos: InfosApp): String =
        if (infos.plateforme.startsWith("Android")) "10.0.2.2" else "localhost"
}
