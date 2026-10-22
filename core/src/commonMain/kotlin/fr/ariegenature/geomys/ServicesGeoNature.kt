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

import fr.ariegenature.geomys.carte.CartesMbtiles
import fr.ariegenature.geomys.carte.LecteurMbtilesSqlite
import fr.ariegenature.geomys.location.LocationTracker
import fr.ariegenature.geomys.network.AdditionalFieldsApi
import fr.ariegenature.geomys.network.EnvoiOccHab
import fr.ariegenature.geomys.network.EnvoiSortie
import fr.ariegenature.geomys.network.GeoNatureAuth
import fr.ariegenature.geomys.network.GeoNatureBrowse
import fr.ariegenature.geomys.network.GeoNatureSync
import fr.ariegenature.geomys.network.GeoNatureUpload
import fr.ariegenature.geomys.network.HabitatService
import fr.ariegenature.geomys.network.InvalidationCaches
import fr.ariegenature.geomys.network.MonitoringApi
import fr.ariegenature.geomys.network.MonitoringDatalists
import fr.ariegenature.geomys.network.MonitoringEnvoi
import fr.ariegenature.geomys.network.MonitoringModules
import fr.ariegenature.geomys.network.MonitoringObjets
import fr.ariegenature.geomys.network.MonitoringSchemas
import fr.ariegenature.geomys.network.MonitoringSync
import fr.ariegenature.geomys.network.OccHabApi
import fr.ariegenature.geomys.network.OccHabUpload
import fr.ariegenature.geomys.network.OutboxEnvoi
import fr.ariegenature.geomys.network.SyncRunner
import fr.ariegenature.geomys.network.TaxRefService
import fr.ariegenature.geomys.network.VerificationConnexion
import fr.ariegenature.geomys.occhab.altitudesMnt
import fr.ariegenature.geomys.plateforme.exclureDesSauvegardes
import fr.ariegenature.geomys.plateforme.rendreAuxSauvegardes
import fr.ariegenature.geomys.store.CachesSynchronises
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.HabitatCacheStore
import fr.ariegenature.geomys.store.MediasOrphelins
import fr.ariegenature.geomys.store.MonitoringCache
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.OccHabStore
import fr.ariegenature.geomys.store.OutboxMonitoring
import fr.ariegenature.geomys.store.PictoCache
import fr.ariegenature.geomys.store.PreferencesGeoMys
import fr.ariegenature.geomys.store.RelevesOrphelins
import fr.ariegenature.geomys.store.SortieStore
import fr.ariegenature.geomys.store.StationsServeurCache
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.io.FichierAtomique
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Les services de l'appli, uniques dans le processus. Chaque plateforme les construit une fois
 *  (servicesGeoNature) sur son [Socle] : dossiers, réglages, coffre, moteurs HTTP, travaux longs.
 *  Les objets globaux de GeoMys-Android y deviennent des instances, sur les mêmes dossiers et
 *  noms de réglages. */
class ServicesGeoNature(val socle: Socle) {
    private val clients = socle.clients
    private val fichiers = socle.racines.fichiers

    val config = GeoNatureConfig(socle.reglages.reglages("gn_config"), socle.coffre)
    val preferences = PreferencesGeoMys(socle.reglages.reglages("GeoMys_prefs"))

    init {
        // Le jeton et les cookies ne partent que vers ce serveur.
        clients.origineInstance = { config.urlServeur.takeIf { it.isNotBlank() } }
    }
    val auth = GeoNatureAuth(clients)
    val verificationConnexion = VerificationConnexion(auth, clients)

    // Position : le récepteur de la plateforme, lu par les cartes, la saisie et le suivi en
    // arrière-plan (GeoMysApplication.locationTracker sous Android).
    val locationTracker = LocationTracker(socle.positions)

    /** Démarre le suivi GPS des écrans de saisie, maintenu quand l'appli passe en arrière-plan
     *  (service au premier plan et sa notification sous Android). */
    fun demarrerSuiviGps() = socle.suiviGps.demarrer(locationTracker)

    fun arreterSuiviGps() = socle.suiviGps.arreter(locationTracker)

    // Référentiels : filesDir/taxref, filesDir/monitoring… comme sous Android.
    val taxRef = TaxRefCache(fichiers / "taxref", socle.reglages.reglages("taxref_cache"), socle.systeme)

    init {
        // Un TaxRef chargé avant le suivi de son format, par une version qui rangeait déjà les
        // groupes 3 et les noms valides (données en version 3 ou plus), est au format courant :
        // inutile de le retélécharger.
        if (taxRef.versionFormat == null && taxRef.versionSauvegardee != null && config.versionDonneesChargees >= 3) {
            taxRef.versionFormat = TaxRefCache.VERSION_FORMAT
        }
    }
    val nomenclatures = NomenclatureCache(socle.reglages.reglages("nomenclature_cache"), taxRef)
    // Les autres caches retéléchargeables, à part, dans filesDir/caches : leur exclusion des
    // sauvegardes, posée jusqu'au 20 octobre sur la racine des données, en retirait aussi sous iOS
    // les saisies et leurs médias.
    private val caches = fichiers / "caches"
    // Les deux recherches d'habitat du web passent par le même `ilike` du serveur.
    val habitatsOcctax = HabitatCacheStore(caches, HABITATS_OCCTAX, sensibleAuxAccents = true, systeme = socle.systeme)
    val habitatsOccHab = HabitatCacheStore(caches, HABITATS_OCCHAB, sensibleAuxAccents = true, systeme = socle.systeme)
    val stationsServeur = StationsServeurCache(caches, socle.systeme)

    init {
        rangerCachesDeLaRacine()
    }
    val monitoringCache = MonitoringCache(fichiers / "monitoring", socle.systeme)
    val pictos = PictoCache(fichiers / "monitoring" / "pictos", clients, socle.systeme)

    // Saisies : fichiers atomiques à écriture durable (les SharedPreferences `sorties_store` et
    // `occhab_store` de GeoMys-Android, et son filesDir/monitoring_outbox.json).
    val sortieStore = SortieStore(FichierAtomique(socle.systeme, fichiers / "sorties_store.json", "SortieStore"))
    val occHabStore = OccHabStore(FichierAtomique(socle.systeme, fichiers / "occhab_store.json", "OccHabStore"))
    val outbox = OutboxMonitoring(FichierAtomique(socle.systeme, fichiers / "monitoring_outbox.json", "OutboxMonitoring"))
    val relevesOrphelins = RelevesOrphelins(socle.reglages.reglages("releves_orphelins"))
    /** Les photos et sons importés dans les saisies (`filesDir/medias/` de GeoMys-Android). */
    val dossierMedias = fichiers / "medias"

    /** [dossierMedias] en chemin, pour l'interface. */
    val cheminMedias: String get() = dossierMedias.toString()
    val mediasOrphelins = MediasOrphelins(socle.systeme, dossierMedias, sortieStore, outbox)

    /** Les cartes MBTiles importées (`filesDir/mbtiles/` de GeoMys-Android, exclu des sauvegardes). */
    val cartesMbtiles = CartesMbtiles(fichiers / "mbtiles", socle.systeme, LecteurMbtilesSqlite())

    /** Installations d'avant le 20 octobre : la racine des données rendue aux sauvegardes, et les
     *  caches qu'elle contenait rangés dans [caches]. Au mieux : la synchro suivante les réécrit. */
    private fun rangerCachesDeLaRacine() {
        val systeme = socle.systeme
        try {
            if (!systeme.exists(fichiers)) return
            rendreAuxSauvegardes(fichiers)
            for (nom in listOf(HABITATS_OCCTAX, HABITATS_OCCHAB, StationsServeurCache.NOM_FICHIER)) {
                val ancien = fichiers / nom
                if (!systeme.exists(ancien)) continue
                systeme.createDirectories(caches)
                exclureDesSauvegardes(caches)
                if (systeme.exists(caches / nom)) systeme.delete(ancien) else systeme.atomicMove(ancien, caches / nom)
            }
        } catch (_: Exception) {
        }
    }

    /** Supprime la copie locale d'un média retiré d'une saisie, au mieux. */
    fun supprimerMedia(uri: String) {
        val chemin = GeoNatureUpload.cheminDepuisUri(uri) ?: return
        try {
            socle.systeme.delete(GeoNatureUpload.fichierMedia(socle.systeme, chemin, dossierMedias))
        } catch (_: Exception) {
        }
    }

    val taxRefLocal = TaxRefLocal(taxRef, nomenclatures)
    val taxRefService = TaxRefService(taxRef, taxRefLocal)
    val habitats = HabitatService(habitatsOcctax, habitatsOccHab)

    // Réseau : navigation, champs additionnels, OccHab, synchro des référentiels.
    val browse = GeoNatureBrowse(auth, clients, taxRef)
    val additionalFields = AdditionalFieldsApi(auth, clients)
    val occHab = OccHabApi(auth, clients)
    val geoNatureSync = GeoNatureSync(auth, clients, taxRef, nomenclatures, browse)

    // Suivis (gn_module_monitoring).
    val monitoringModules = MonitoringModules(auth, clients, monitoringCache)
    val monitoringSchemas = MonitoringSchemas(auth, clients, monitoringCache)
    val monitoringObjets = MonitoringObjets(auth, clients, monitoringCache)
    val monitoringDatalists = MonitoringDatalists(auth, clients, monitoringCache, taxRef)
    val monitoring = MonitoringApi(monitoringModules, monitoringSchemas, monitoringObjets, monitoringDatalists)
    val monitoringSync = MonitoringSync(auth, monitoring, monitoringDatalists, pictos)

    /** Le picto d'un protocole (`chargerImagePicto` de SuivisFragment) : celui que la synchro a mis
     *  en cache, sans requête (décision de l'utilisateur du 20 octobre : rien n'est téléchargé hors
     *  synchro). Rend son chemin, pour l'interface ; null si le protocole n'a pas d'image en cache. */
    fun cheminPicto(moduleCode: String): String? = pictos.fichierLocal(moduleCode)?.toString()

    // Envois : OccTax (relevés, occurrences, médias), file des Suivis, OccHab.
    val upload = GeoNatureUpload(
        auth, clients, nomenclatures, geoNatureSync, relevesOrphelins, socle.systeme, dossierMedias,
        nomScientifique = { taxRef.entreesParCdNom()[it]?.sciNom },
    )
    val envoiSortie = EnvoiSortie(upload, sortieStore, socle.executionLongue)
    val monitoringEnvoi = MonitoringEnvoi(auth, clients)
    val outboxEnvoi = OutboxEnvoi(outbox, monitoringEnvoi, upload, monitoringObjets, socle.executionLongue)
    val occHabUpload = OccHabUpload(auth, clients)
    val envoiOccHab = EnvoiOccHab(occHabUpload, occHabStore, stationsServeur, socle.executionLongue) { station -> altitudesMnt(station) }

    // Purges et synchro globale (« Recharger les données »).
    val cachesSynchronises = CachesSynchronises(
        taxRef, nomenclatures, habitatsOcctax, habitatsOccHab, stationsServeur, monitoringCache, pictos, monitoring,
    )
    val invalidationCaches = InvalidationCaches(auth, monitoring, upload, nomenclatures, stationsServeur)
    val synchro = SyncRunner(
        config, browse, additionalFields, geoNatureSync, monitoring, monitoringSync, occHab,
        habitatsOcctax, habitatsOccHab, stationsServeur, taxRef, socle.executionLongue,
    )

    private val demarrageLance = atomic(false)

    /** Les travaux de fond du démarrage, une seule fois par processus (GeoMysApplication.onCreate
     *  sous Android), appelés par la racine de l'interface. D'abord la préchauffe des vues
     *  mémoïsées de TaxRef : sans elle, le premier appel aux suggestions lit le disque et parcourt
     *  15 000 entrées et plus, assez lentement pour qu'un utilisateur qui tape « M » dès l'ouverture
     *  de l'écran de saisie tombe sur une liste encore vide. Puis le balayage des médias orphelins
     *  (`medias/` grossissait sans borne — audit 2026-08-23, M-N2), qui charge les mêmes stores. Au
     *  mieux : une erreur est avalée, le balayage retentera au prochain démarrage. */
    fun lancerTravauxDeDemarrage() {
        if (!demarrageLance.compareAndSet(expect = false, update = true)) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                taxRef.entreesParCdNom()
                taxRef.vernsParCdNom()
                taxRef.tousLesGroupes()
                taxRef.tousLesGroupes1()
                taxRef.tousLesRegnes()
                // Lus au premier taxon reconnu (« TAXREF - Nom valide ») : décodés ici plutôt que sur
                // le fil de l'interface (audit du 21 octobre, M40).
                taxRef.nomValideParCdNom(0)
            }
            runCatching { mediasOrphelins.purger() }
        }
    }
}

/** Les caches HABREF d'OccTax et d'OccHab, dans filesDir/caches. */
private const val HABITATS_OCCTAX = "habitats_v1.json"
private const val HABITATS_OCCHAB = "habitats_occhab_v1.json"
