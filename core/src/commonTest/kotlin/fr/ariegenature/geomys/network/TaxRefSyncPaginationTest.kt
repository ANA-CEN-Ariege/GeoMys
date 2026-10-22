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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/TaxRefSyncPaginationTest.kt) :
// MockWebServer remplacé par ServeurSimule (la coupure de socket par une panne du moteur),
// Robolectric par des réglages en mémoire et un dossier temporaire, les objets par des instances,
// JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.panne
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import fr.ariegenature.geomys.store.CoffreEnMemoire
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.SYSTEM

/** Synchro TaxRef paginée ([GeoNatureSync.synchroniserTaxRef]) face aux interruptions :
 *  une liste dont la pagination est coupée à mi-parcours (réseau, 500) ne doit JAMAIS être
 *  marquée synchronisée — sinon le cache passe pour exhaustif et l'autocomplétion hors-ligne
 *  perd silencieusement des taxons. La page size de l'implémentation est 1000 : les pages
 *  pleines font donc 1000 items générés. */
class TaxRefSyncPaginationTest {

    private val serveur = ServeurSimule()
    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val taxRef = TaxRefCache(dossier, MapSettings())
    private val auth = GeoNatureAuth(serveur.clients)
    private val geoNatureSync = GeoNatureSync(
        auth, serveur.clients, taxRef, NomenclatureCache(MapSettings(), taxRef),
        GeoNatureBrowse(auth, serveur.clients, taxRef),
    )

    private val config = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
        urlServeur = serveur.base
        login = "alice"; motDePasse = "pwd"; idDataset = "12"
        // Biblistes lues depuis ce cache (peuplé par SyncRunner en conditions réelles) —
        // pas d'appel /biblistes. Datasets vides → pas de liste « privée » supplémentaire.
        listesCacheJson = """[{"id":7,"nom":"Liste test"}]"""
        taxaListeId = "7"
        datasetsCacheJson = """[]"""
    }

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    /** Page JSON de [nb] taxons, cd_nom consécutifs à partir de [depuis]. */
    private fun page(depuis: Int, nb: Int): String {
        val items = (depuis until depuis + nb).joinToString(",") {
            """{"cd_nom":$it,"lb_nom":"Taxon $it","nom_vern":"Vern $it","group2_inpn":"Oiseaux","regne":"Animalia"}"""
        }
        return """{"items":[$items]}"""
    }

    /** Route /taxref par numéro de page : reponsesParPage[n] sert la page n (1-indexée). Le cache
     *  « [] » des datasets fait aussi appeler /meta/datasets (cf. GeoNatureSync) : sans route,
     *  404, comme sous Android le reste des chemins. */
    private fun router(reponsesParPage: Map<Int, ServeurSimule.Reponse>) {
        serveur.router { path ->
            when {
                path.contains("/api/taxhub/api/taxref/version") -> statut(404)
                path.contains("/api/taxhub/api/taxref") -> {
                    val numPage = Regex("page=(\\d+)").find(path)?.groupValues?.get(1)?.toInt() ?: 1
                    reponsesParPage[numPage] ?: statut(404)
                }
                else -> statut(404)
            }
        }
    }

    private suspend fun sync(): Pair<Int, String> = geoNatureSync.synchroniserTaxRef(config) { _, _, _ -> }

    @Test
    fun pagination_complete_marque_la_liste_synchronisee() = runTest {
        // Page 1 pleine (1000) + page 2 partielle (5) = fin normale de pagination.
        router(mapOf(1 to json(page(1, 1000)), 2 to json(page(1001, 5))))
        val (nb, msg) = sync()
        assertEquals(2010, nb, "1005 taxons × 2 clés (nom scientifique + vernaculaire)")
        // Le message ne contient plus le détail « X/Y listes » (déplacé dans l'écran Paramètres) ;
        // la liste synchronisée se vérifie directement, et aucun avertissement n'est attendu.
        assertFalse(msg.contains("⚠"), "aucun avertissement attendu : $msg")
        assertEquals(listOf(7), taxRef.listesSynchronisees)
    }

    @Test
    fun coupure_reseau_en_page_2_ne_marque_pas_la_liste_synchronisee() = runTest {
        router(mapOf(
            1 to json(page(1, 1000)),
            2 to panne(),
        ))
        val (nb, msg) = sync()
        // Les 1000 taxons de la page 1 restent utiles (autocomplétion) et sont conservés…
        assertEquals(2000, nb)
        // …mais la liste TRONQUÉE ne doit pas passer pour exhaustive : avertissement + pas
        // d'inscription dans listesSynchronisees (le prochain sync la retentera en entier).
        assertTrue(msg.contains("⚠"), "avertissement de liste partielle attendu : $msg")
        assertTrue(msg.contains("partiellement"))
        assertTrue(taxRef.listesSynchronisees.isEmpty(), "liste 7 non marquée synchronisée")
    }

    @Test
    fun erreur_500_en_page_2_ne_marque_pas_la_liste_synchronisee() = runTest {
        router(mapOf(1 to json(page(1, 1000)), 2 to statut(500)))
        val (_, msg) = sync()
        assertTrue(msg.contains("⚠"), "avertissement attendu : $msg")
        assertTrue(taxRef.listesSynchronisees.isEmpty())
    }

    @Test
    fun echec_total_premiere_page_ne_touche_pas_au_cache_existant() = runTest {
        // Cache pré-existant d'un sync précédent : un sync en zone blanche ne doit pas l'écraser.
        taxRef.listesSynchronisees = listOf(7)
        router(mapOf(1 to statut(500)))
        val (nb, msg) = sync()
        assertEquals(0, nb)
        assertTrue(msg.contains("Aucun taxon"), "message d'échec attendu : $msg")
        assertEquals(listOf(7), taxRef.listesSynchronisees, "le marqueur du sync précédent doit survivre")
    }

    @Test
    fun seules_les_listes_utiles_se_telechargent() = runTest {
        // 21 octobre : la liste choisie, et non toutes celles du serveur (plus de 400 000 taxons
        // cumulés sur certains).
        config.listesCacheJson = """[{"id":7,"nom":"Liste test"},{"id":8,"nom":"Très grosse liste"}]"""
        val demandees = mutableSetOf<String>()
        serveur.router { path ->
            Regex("id_liste=(\\d+)").find(path)?.groupValues?.get(1)?.let(demandees::add)
            when {
                path.contains("/api/taxhub/api/taxref/version") -> statut(404)
                path.contains("/api/taxhub/api/taxref") -> json(page(1, 3))
                else -> statut(404)
            }
        }
        sync()
        assertEquals(setOf("7"), demandees)
        assertEquals(listOf(7), taxRef.listesSynchronisees)
    }

    @Test
    fun un_champ_espece_sans_liste_fait_charger_toutes_les_listes() = runTest {
        // Le web y propose tout TaxRef : toutes les listes publiques, comme avant le 21 octobre.
        config.listesCacheJson = """[{"id":7,"nom":"Liste test"},{"id":8,"nom":"Autre liste"}]"""
        val demandees = mutableSetOf<String>()
        serveur.router { path ->
            Regex("id_liste=(\\d+)").find(path)?.groupValues?.get(1)?.let(demandees::add)
            when {
                path.contains("/api/taxhub/api/taxref/version") -> statut(404)
                path.contains("/api/taxhub/api/taxref") -> json(page(1, 3))
                else -> statut(404)
            }
        }
        geoNatureSync.synchroniserTaxRef(config, toutesLesListes = true) { _, _, _ -> }
        assertEquals(setOf("7", "8"), demandees)
    }

    @Test
    fun les_listes_des_suivis_et_le_champ_espece_sans_liste() {
        fun prop(nom: String, widget: String, liste: Int? = null) =
            MonitoringApi.MonitoringPropertySchema(nom = nom, typeWidget = widget, label = nom, obligatoire = false, idListTaxonomie = liste)
        fun type(nom: String, liste: Int? = null, vararg props: MonitoringApi.MonitoringPropertySchema) = MonitoringApi.MonitoringSchemaObjet(
            type = nom, label = nom, labelList = null, nameField = null, parentType = null, childrenTypes = emptyList(),
            properties = props.associateBy { it.nom }, idListTaxonomy = liste,
        )
        // STOM : la liste du protocole, que le serveur pose aussi sur son champ espèce (`id_list` du
        // générique, `__MODULE.ID_LIST_TAXONOMY`) ; un autre champ a sa propre liste.
        val stom = 101 to mapOf("observation" to type("observation", null, prop("cd_nom", "taxonomy", liste = 101)))
        val propre = null to mapOf("observation" to type("observation", null, prop("cd_nom", "taxonomy", liste = 205)))
        assertEquals(ListesDesSuivis(setOf(101, 205), champSansListe = false), listesDesSuivis(listOf(stom, propre)))
        // Comme le web (21 octobre) : un champ espèce sans `id_list` cherche dans tout TaxRef, même si
        // le protocole ou un autre type déclare une liste (PAPALTB, pelouses_seches à l'ANA).
        val autreType = 103 to mapOf("module" to type("module", 103), "observation" to type("observation", null, prop("cd_nom", "taxonomy")))
        assertTrue(listesDesSuivis(listOf(autreType)).champSansListe)
        // Le `cd_nom` masqué du type « module » ne compte pas.
        val masque = prop("cd_nom", "taxonomy").copy(hiddenBool = true)
        val moduleMasque = 101 to mapOf("module" to type("module", 101, masque), "observation" to type("observation", null, prop("cd_nom", "taxonomy", liste = 101)))
        assertFalse(listesDesSuivis(listOf(moduleMasque)).champSansListe)
        // RHOMEOOrthoptere sur le serveur de l'ANA : aucune liste nulle part.
        val sansListe = null to mapOf("observation" to type("observation", null, prop("cd_nom", "taxonomy"), prop("nb", "number")))
        assertTrue(listesDesSuivis(listOf(stom, sansListe)).champSansListe)
    }
}
