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

package fr.ariegenature.geomys.network

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.store.CoffreEnMemoire
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.MonitoringCache
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.util.uuidAleatoire
import okio.FileSystem
import okio.SYSTEM

/** Les services Monitoring d'un test, montés sur un serveur simulé, un dossier temporaire et des
 *  réglages en mémoire : ce que donnaient les objets globaux et Robolectric sous Android. Le test
 *  appelle [nettoyer] à la fin. */
internal class MonitoringDeTest(val serveur: ServeurSimule = ServeurSimule()) {
    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    val cache = MonitoringCache(dossier / "monitoring")
    val taxRef = TaxRefCache(dossier / "taxref", MapSettings())
    val auth = GeoNatureAuth(serveur.clients)
    val modules = MonitoringModules(auth, serveur.clients, cache)
    val schemas = MonitoringSchemas(auth, serveur.clients, cache)
    val objets = MonitoringObjets(auth, serveur.clients, cache)
    val datalists = MonitoringDatalists(auth, serveur.clients, cache, taxRef)
    val api = MonitoringApi(modules, schemas, objets, datalists)
    val config = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
        urlServeur = serveur.base
        login = "alice"
        motDePasse = "pwd"
    }

    fun nettoyer() {
        systeme.deleteRecursively(dossier)
    }
}
