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

package fr.ariegenature.geomys

import fr.ariegenature.geomys.network.AdditionalFieldsApi
import fr.ariegenature.geomys.network.GeoNatureAuth
import fr.ariegenature.geomys.network.GeoNatureBrowse
import fr.ariegenature.geomys.network.GeoNatureSync
import fr.ariegenature.geomys.network.HabitatService
import fr.ariegenature.geomys.network.InvalidationCaches
import fr.ariegenature.geomys.network.MonitoringApi
import fr.ariegenature.geomys.network.MonitoringDatalists
import fr.ariegenature.geomys.network.MonitoringModules
import fr.ariegenature.geomys.network.MonitoringObjets
import fr.ariegenature.geomys.network.MonitoringSchemas
import fr.ariegenature.geomys.network.MonitoringSync
import fr.ariegenature.geomys.network.OccHabApi
import fr.ariegenature.geomys.network.SyncRunner
import fr.ariegenature.geomys.network.TaxRefService
import fr.ariegenature.geomys.network.VerificationConnexion
import fr.ariegenature.geomys.store.CachesSynchronises
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.HabitatCacheStore
import fr.ariegenature.geomys.store.MonitoringCache
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.PictoCache
import fr.ariegenature.geomys.store.StationsServeurCache
import fr.ariegenature.geomys.store.TaxRefCache

/** Les services de l'appli, uniques dans le processus. Chaque plateforme les construit une fois
 *  (servicesGeoNature) sur son [Socle] : dossiers, réglages, coffre, moteurs HTTP, travaux longs.
 *  Les objets globaux de GeoMys-Android y deviennent des instances, sur les mêmes dossiers et
 *  noms de réglages. */
class ServicesGeoNature(val socle: Socle) {
    private val clients = socle.clients
    private val fichiers = socle.racines.fichiers

    val config = GeoNatureConfig(socle.reglages.reglages("gn_config"), socle.coffre)
    val auth = GeoNatureAuth(clients)
    val verificationConnexion = VerificationConnexion(auth, clients)

    // Référentiels : filesDir/taxref, filesDir/monitoring… comme sous Android.
    val taxRef = TaxRefCache(fichiers / "taxref", socle.reglages.reglages("taxref_cache"), socle.systeme)
    val nomenclatures = NomenclatureCache(socle.reglages.reglages("nomenclature_cache"), taxRef)
    val habitatsOcctax = HabitatCacheStore(fichiers, "habitats_v1.json", systeme = socle.systeme)
    val habitatsOccHab = HabitatCacheStore(fichiers, "habitats_occhab_v1.json", sensibleAuxAccents = true, systeme = socle.systeme)
    val stationsServeur = StationsServeurCache(fichiers, socle.systeme)
    val monitoringCache = MonitoringCache(fichiers / "monitoring", socle.systeme)
    val pictos = PictoCache(fichiers / "monitoring" / "pictos", clients, socle.systeme)

    val taxRefLocal = TaxRefLocal(taxRef, nomenclatures)
    val taxRefService = TaxRefService(taxRef, taxRefLocal)
    val habitats = HabitatService(clients, habitatsOcctax, habitatsOccHab)

    // Réseau : navigation, champs additionnels, OccHab, synchro des référentiels.
    val browse = GeoNatureBrowse(auth, clients, taxRef)
    val additionalFields = AdditionalFieldsApi(auth, clients, nomenclatures)
    val occHab = OccHabApi(auth, clients)
    val geoNatureSync = GeoNatureSync(auth, clients, taxRef, nomenclatures, browse)

    // Suivis (gn_module_monitoring).
    val monitoringModules = MonitoringModules(auth, clients, monitoringCache)
    val monitoringSchemas = MonitoringSchemas(auth, clients, monitoringCache)
    val monitoringObjets = MonitoringObjets(auth, clients, monitoringCache)
    val monitoringDatalists = MonitoringDatalists(auth, clients, monitoringCache, taxRef)
    val monitoring = MonitoringApi(monitoringModules, monitoringSchemas, monitoringObjets, monitoringDatalists)
    val monitoringSync = MonitoringSync(auth, monitoring, monitoringDatalists, pictos)

    // Purges et synchro globale (« Recharger les données »).
    val cachesSynchronises = CachesSynchronises(
        taxRef, nomenclatures, habitatsOcctax, habitatsOccHab, stationsServeur, monitoringCache, pictos, monitoring,
    )
    val invalidationCaches = InvalidationCaches(auth, monitoring, nomenclatures, stationsServeur)
    val synchro = SyncRunner(
        config, browse, additionalFields, geoNatureSync, monitoring, monitoringSync, occHab,
        habitatsOcctax, habitatsOccHab, stationsServeur, cachesSynchronises, taxRef, socle.executionLongue,
    )
}
