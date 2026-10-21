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

// Le comportement de CacheManagerFragment de GeoMys-Android @ cdd5196 (majInfos, lancerTelechargement,
// demanderViderCache, gererCartesHorsLigne, confirmerSuppressionMbtiles, cadrerSurProtocole,
// recentrerSurPosition) que porte MapsManagerViewModel ; les paquets de MapLibre et SQLite sont remplacés par
// des factices. GeoMys-Android n'a pas de test de cet écran.

package fr.ariegenature.geomys.ui.cartes

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.carte.CartesMbtiles
import fr.ariegenature.geomys.carte.CiblageProtocole
import fr.ariegenature.geomys.carte.FondCarte
import fr.ariegenature.geomys.carte.FondChoisi
import fr.ariegenature.geomys.carte.LecteurMbtiles
import fr.ariegenature.geomys.carte.SitesProtocole
import fr.ariegenature.geomys.carte.ZoneHorsLigne
import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.monitoring.GeometrieSuivi
import fr.ariegenature.geomys.monitoring.ObjetCarte
import fr.ariegenature.geomys.network.MonitoringModule
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.carte.IssueTelechargement
import fr.ariegenature.geomys.ui.carte.ZonesHorsLigne
import fr.ariegenature.geomys.ui.composants.FluxChoisi
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.SYSTEM

@OptIn(ExperimentalCoroutinesApi::class)
class MapsManagerViewModelTest {

    private val essai = ServicesEssai()
    private val magasin = ViewModelStore()
    private val principal = StandardTestDispatcher()
    private val portee = CoroutineScope(principal)
    private val recus = mutableListOf<EvenementMapsManager>()

    /** Une zone de 2,4 km sur 2,2 km près de Foix, et une de 26 km sur 22. */
    private val zone = Emprise(sud = 42.95, ouest = 1.60, nord = 42.97, est = 1.63)
    private val tropGrande = Emprise(sud = 42.85, ouest = 1.45, nord = 43.05, est = 1.77)

    /** Le téléchargement, le cache et le vidage des paquets de MapLibre. */
    private class Zones : ZonesHorsLigne {
        override val occupation = MutableStateFlow(0L)
        val demandes = mutableListOf<List<Any>>()
        var etapes = listOf<Pair<Long, Long>>()
        var attente: CompletableDeferred<Unit>? = null
        var issue: IssueTelechargement = IssueTelechargement.Termine
        var annulations = 0
        var vidages = 0
        var vidageComplet = true

        override suspend fun telecharger(
            urlStyle: String,
            emprise: Emprise,
            niveauMin: Int,
            niveauMax: Int,
            densite: Float,
            progression: (recues: Long, attendues: Long) -> Unit,
        ): IssueTelechargement {
            demandes += listOf(urlStyle, emprise, niveauMin, niveauMax, densite)
            etapes.forEach { (recues, attendues) -> progression(recues, attendues) }
            try {
                attente?.await()
            } catch (e: CancellationException) {
                annulations++
                throw e
            }
            return issue
        }

        override suspend fun viderTout(): Boolean {
            vidages++
            return vidageComplet
        }
    }

    private val zones = Zones()

    /** Les cartes MBTiles, sur le vrai système de fichiers ; une « carte » est valide si son contenu
     *  commence par « carte », et son nom suit. */
    private val racine = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-maps-${uuidAleatoire()}"
    private val cartes = CartesMbtiles(
        racine,
        FileSystem.SYSTEM,
        object : LecteurMbtiles {
            private fun contenu(f: Path) = FileSystem.SYSTEM.read(f) { readUtf8() }
            override fun aUneTableTiles(fichier: Path) = contenu(fichier).startsWith("carte")
            override fun metadonnees(fichier: Path) = mapOf("name" to contenu(fichier).removePrefix("carte "), "minzoom" to "10", "maxzoom" to "16")
            override fun zoomsDesTuiles(fichier: Path) = 10 to 16
        },
    )

    private val stom = MonitoringModule(idModule = 1, moduleCode = "stom", moduleLabel = "STOM Ariège")
    private var modules: suspend () -> List<MonitoringModule> = { listOf(stom) }
    private var sites: suspend (String) -> SitesProtocole = { SitesProtocole(emptyList(), null) }

    @AfterTest
    fun nettoyer() {
        portee.cancel()
        magasin.clear()
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        FileSystem.SYSTEM.deleteRecursively(racine)
        essai.nettoyer()
    }

    private fun vm(): MapsManagerViewModel {
        Dispatchers.setMain(principal)
        val vm = ViewModelProvider.create(
            magasin,
            viewModelFactory {
                initializer {
                    MapsManagerViewModel(
                        essai.services,
                        zones,
                        modules = { modules() },
                        sites = { sites(it) },
                        cartes = cartes,
                        horloge = { 1_000L },
                        fichiers = principal,
                    )
                }
            },
        )[MapsManagerViewModel::class]
        portee.launch { vm.evenements.collect { recus += it } }
        principal.scheduler.advanceUntilIdle()
        return vm
    }

    private fun avancer() = principal.scheduler.advanceUntilIdle()

    @Test
    fun le_panneau_compte_les_niveaux_de_tuiles_depuis_le_zoom_de_carte_plus_un() {
        val vm = vm()
        assertNull(vm.estimation)
        vm.choisirFond(FondChoisi.EnLigne(FondCarte.TOPO))
        vm.carteBougee(zone, 14.3)
        val estimation = assertNotNull(vm.estimation)
        assertEquals("Surface : 5,4 km² · zooms 15–17", estimation.surface)
        assertTrue(estimation.tuiles.endsWith("fond : IGN Topo"), estimation.tuiles)
        assertNull(estimation.avertissement)
        assertTrue(estimation.telechargeable)
    }

    @Test
    fun une_zone_trop_large_ne_se_telecharge_pas() {
        val vm = vm()
        vm.choisirFond(FondChoisi.EnLigne(FondCarte.TOPO))
        vm.carteBougee(tropGrande, 11.0)
        val estimation = assertNotNull(vm.estimation)
        assertEquals("Zone trop large (max 200 km²) — zoomer pour réduire.", estimation.avertissement)
        assertFalse(estimation.telechargeable)
        vm.charger(2f)
        avancer()
        assertTrue(zones.demandes.isEmpty())
        assertNull(vm.dialogue)
    }

    /** La décision 13 (exclure OSM) est écartée : tous les fonds se téléchargent, comme sous GeoMys-Android. */
    @Test
    fun openstreetmap_se_telecharge_comme_les_autres_fonds() {
        val vm = vm()
        assertEquals(FondChoisi.EnLigne(FondCarte.OSM), vm.fond)
        vm.carteBougee(zone, 14.0)
        val estimation = assertNotNull(vm.estimation)
        assertNull(estimation.avertissement)
        assertTrue(estimation.telechargeable)
        vm.charger(2f)
        avancer()
        assertEquals("geomys://styles/osm.json", zones.demandes.single().first())
    }

    @Test
    fun annuler_arrete_le_telechargement_et_garde_les_tuiles_recues() {
        val vm = vm()
        // Sans téléchargement en cours, rien à annuler.
        vm.annulerTelechargement()
        avancer()
        assertTrue(recus.isEmpty())

        vm.choisirFond(FondChoisi.EnLigne(FondCarte.ESRI))
        vm.carteBougee(zone, 14.0)
        zones.etapes = listOf(12L to 40L)
        zones.attente = CompletableDeferred()
        vm.charger(2f)
        avancer()
        assertEquals(DialogueCartes.Telechargement("12 / 40", 0.3f), vm.dialogue)
        vm.annulerTelechargement()
        avancer()
        assertNull(vm.dialogue)
        assertEquals(1, zones.annulations)
        assertEquals(
            listOf<EvenementMapsManager>(EvenementMapsManager.Toast("Téléchargement annulé : les tuiles reçues restent en cache.", longue = true)),
            recus,
        )
        // Un nouveau téléchargement reste possible.
        zones.attente = null
        vm.charger(2f)
        avancer()
        assertEquals(2, zones.demandes.size)
        assertTrue(vm.dialogue is DialogueCartes.Resultat)
    }

    @Test
    fun le_telechargement_suit_sa_progression_puis_donne_son_issue() {
        val vm = vm()
        vm.choisirFond(FondChoisi.EnLigne(FondCarte.SCAN25))
        vm.carteBougee(zone, 14.6)
        zones.etapes = listOf(0L to 0L, 5L to 20L)
        zones.attente = CompletableDeferred()
        vm.charger(2.625f)
        assertEquals(DialogueCartes.Telechargement(ZoneHorsLigne.PREPARATION, 0f), vm.dialogue)
        avancer()
        // Le style du fond affiché, des niveaux 15 à 17, à la densité de l'écran.
        assertEquals(listOf<Any>("geomys://styles/scan25.json", zone, 15, 17, 2.625f), zones.demandes.single())
        assertEquals(DialogueCartes.Telechargement("5 / 20", 0.25f), vm.dialogue)
        // Rien d'autre ne s'ouvre ni ne se relance pendant le téléchargement.
        vm.demanderVidage()
        vm.ouvrirProtocoles()
        vm.charger(2.625f)
        avancer()
        assertTrue(vm.dialogue is DialogueCartes.Telechargement)
        assertEquals(1, zones.demandes.size)
        zones.attente?.complete(Unit)
        avancer()
        assertEquals(
            DialogueCartes.Resultat(
                "Téléchargement terminé",
                "Les tuiles du fond IGN Scan25 sont disponibles hors-ligne pour la zone affichée.",
            ),
            vm.dialogue,
        )
        vm.fermer(vm.dialogue!!)
        assertNull(vm.dialogue)
    }

    @Test
    fun un_telechargement_incomplet_ou_trop_gros_le_dit() {
        val vm = vm()
        vm.choisirFond(FondChoisi.EnLigne(FondCarte.ORTHO))
        vm.carteBougee(zone, 14.0)
        zones.issue = IssueTelechargement.Incomplet("HTTP 503")
        vm.charger(2f)
        avancer()
        assertEquals(DialogueCartes.Resultat(ZoneHorsLigne.TITRE_INCOMPLET, ZoneHorsLigne.MESSAGE_INCOMPLET), vm.dialogue)
        vm.fermer(vm.dialogue!!)
        zones.issue = IssueTelechargement.Limite(6_000)
        vm.charger(2f)
        avancer()
        assertEquals(DialogueCartes.Resultat(ZoneHorsLigne.TITRE_INCOMPLET, ZoneHorsLigne.messageLimite(6_000)), vm.dialogue)
    }

    @Test
    fun l_occupation_du_cache_suit_les_paquets_et_rougit_a_90_pourcent() {
        val vm = vm()
        assertEquals("Cache : 0 / 1024 MB (0 %)" to false, vm.occupation)
        zones.occupation.value = 950L * 1024 * 1024
        avancer()
        assertEquals("Cache : 950 / 1024 MB (93 %)" to true, vm.occupation)
    }

    @Test
    fun vider_le_cache_demande_confirmation_puis_le_dit() {
        val vm = vm()
        vm.demanderVidage()
        assertEquals(DialogueCartes.Vider, vm.dialogue)
        vm.vider()
        avancer()
        assertNull(vm.dialogue)
        assertEquals(1, zones.vidages)
        zones.vidageComplet = false
        vm.vider()
        avancer()
        assertEquals(listOf<EvenementMapsManager>(EvenementMapsManager.Toast("Cache vidé"), EvenementMapsManager.Toast("Vidage partiel")), recus)
    }

    @Test
    fun une_carte_importee_rouvre_la_liste_et_un_fichier_invalide_est_refuse() {
        val vm = vm()
        vm.ouvrirCartes()
        avancer()
        assertEquals(DialogueCartes.Mbtiles(emptyList()), vm.dialogue)
        vm.fermer(vm.dialogue!!)

        vm.importer(FluxChoisi(Buffer().writeUtf8("pas une carte"), "photo.jpg"))
        assertEquals(DialogueCartes.Import, vm.dialogue)
        avancer()
        assertNull(vm.dialogue)
        assertEquals(EvenementMapsManager.Toast(CartesMbtiles.IMPORT_ECHOUE, longue = true), recus.last())

        vm.importer(FluxChoisi(Buffer().writeUtf8("carte Plan de Foix"), "foix.mbtiles"))
        avancer()
        assertEquals(EvenementMapsManager.Toast("Carte importée : Plan de Foix", longue = true), recus.last())
        val liste = (vm.dialogue as DialogueCartes.Mbtiles).cartes
        assertEquals(listOf(CarteImportee("foix.mbtiles", "Plan de Foix", "zoom 10–16 · 0 Mo")), liste)

        // Sélecteur refermé sans choix : rien.
        vm.importer(null)
        assertTrue(vm.dialogue is DialogueCartes.Mbtiles)
    }

    @Test
    fun supprimer_la_carte_affichee_ramene_openstreetmap() {
        val vm = vm()
        vm.importer(FluxChoisi(Buffer().writeUtf8("carte Plan de Foix"), "foix.mbtiles"))
        avancer()
        vm.choisirFond(FondChoisi.Mbtiles("foix.mbtiles"))
        val carte = (vm.dialogue as DialogueCartes.Mbtiles).cartes.single()
        vm.demanderSuppression(carte)
        assertEquals(DialogueCartes.Suppression(carte), vm.dialogue)
        vm.supprimer(carte)
        avancer()
        assertFalse(cartes.existe("foix.mbtiles"))
        assertEquals(FondChoisi.EnLigne(FondCarte.OSM), vm.fond)
        assertEquals("OSM", essai.services.preferences.fondCarte)
        assertEquals(EvenementMapsManager.Toast(CartesMbtiles.CARTE_SUPPRIMEE), recus.last())
        assertEquals(DialogueCartes.Mbtiles(emptyList()), vm.dialogue)
    }

    @Test
    fun cibler_un_protocole_dessine_ses_sites_et_cadre_la_carte() {
        val site = ObjetCarte("site", 501, "Col de Port", enfant = false, geometries = listOf(GeometrieSuivi.Point(LatLon(42.88, 1.46))))
        val emprise = Emprise(sud = 42.878, ouest = 1.458, nord = 42.882, est = 1.462)
        val demandes = mutableListOf<String>()
        sites = { code -> demandes += code; SitesProtocole(listOf(site), emprise) }
        val vm = vm()
        assertEquals(listOf(stom), vm.protocoles)
        vm.ouvrirProtocoles()
        assertEquals(DialogueCartes.Protocoles(listOf(stom)), vm.dialogue)
        vm.cibler(stom)
        assertEquals(DialogueCartes.Recherche("stom"), vm.dialogue)
        avancer()
        assertNull(vm.dialogue)
        assertEquals(listOf("stom"), demandes)
        assertEquals(listOf(site), vm.objetsProtocole)
        assertEquals(EvenementMapsManager.Cadrer(emprise), recus.last())
        assertEquals("STOM Ariège (stom)", CiblageProtocole.libelle(stom))
    }

    @Test
    fun un_protocole_sans_site_le_dit_et_garde_la_carte() {
        val vm = vm()
        vm.cibler(stom)
        avancer()
        assertTrue(vm.objetsProtocole.isEmpty())
        assertEquals(EvenementMapsManager.Toast("Aucun site avec géométrie pour ce protocole", longue = true), recus.last())
    }

    @Test
    fun sans_protocole_accessible_le_bouton_disparait() {
        modules = { error("serveur injoignable") }
        val vm = vm()
        assertTrue(vm.protocoles.isEmpty())
        vm.ouvrirProtocoles()
        assertNull(vm.dialogue)
    }

    @Test
    fun recentrer_sans_position_le_dit() {
        val vm = vm()
        vm.recentrer()
        avancer()
        assertEquals(listOf<EvenementMapsManager>(EvenementMapsManager.Toast("Position GPS pas encore disponible")), recus)
    }

    @Test
    fun fermer_ne_ferme_que_le_dialogue_encore_ouvert() {
        val vm = vm()
        vm.demanderVidage()
        vm.fermer(DialogueCartes.Mbtiles(emptyList()))
        assertEquals(DialogueCartes.Vider, vm.dialogue)
        vm.fermer(DialogueCartes.Vider)
        assertNull(vm.dialogue)
    }

    @Test
    fun les_zooms_de_la_carte_sont_ceux_de_geomys_android_moins_un() {
        assertEquals(11.0, MapsManagerViewModel.ZOOM_DEPART)
        assertEquals(14.0, MapsManagerViewModel.ZOOM_POSITION)
        assertEquals(1.0, MapsManagerViewModel.ZOOM_MIN)
        assertEquals(16.0, MapsManagerViewModel.ZOOM_MAX)
    }
}
