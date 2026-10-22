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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/ListeIncompleteTest.kt) :
// Robolectric remplacé par ServicesDeTest, configurationComplete passée au socle, JUnit par
// kotlin.test. La garde textuelle sur la synchro devient un vrai test de la synchro ; celle sur
// l'écran Paramètres reviendra avec lui (lot 5).

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * UNE LISTE DE TAXONS PARTIELLEMENT TÉLÉCHARGÉE BLOQUE LA SAISIE (audit 2026-09-18, constat T3).
 *
 * La pagination d'une liste peut s'interrompre (coupure réseau, serveur qui lâche). Les taxons
 * déjà reçus sont conservés — mieux vaut une liste partielle que rien — mais le cache se croit
 * alors complet : depuis la règle « seuls les noms proposés sont acceptés », la saisie refuse les
 * espèces manquantes avec un « Nom invalide » qui met en cause l'observateur.
 *
 * Décision produit du 2026-09-18 : tant qu'une liste est incomplète, la configuration n'est pas
 * complète — l'écran Paramètres se verrouille, avec le bandeau qui nomme la liste et les accès aux
 * saisies déjà faites. C'est un blocage SUBI, au même titre que le rechargement imposé par une
 * mise à jour, d'où les mêmes issues de secours.
 */
class ListeIncompleteTest {

    private val t = ServicesDeTest()
    private val taxRef get() = t.services.taxRef

    @AfterTest
    fun nettoyer() = t.nettoyer()

    @BeforeTest
    fun setup() {
        taxRef.vider()
    }

    /** Configuration par ailleurs valide : connexion, jeu de données (proposé), liste et
     *  observateur en cache et choisis, et des taxons chargés. */
    private fun configValide() = t.services.config.apply {
        urlServeur = "https://exemple.test"
        login = "u"
        motDePasse = "p"
        serveurCompatible = true
        datasetsCacheJson = """[{"id":1,"nom":"JDD","moduleCodes":["OCCTAX"]}]"""
        datasetsCreablesOcctax = setOf(1)
        idDataset = "1"
        listesCacheJson = """[{"id":100,"nom":"Saisie"}]"""
        taxaListeId = "100"
        observateursCacheJson = """[{"idRole":7,"nom":"DUPONT jean"}]"""
        observateurDefautId = "7"
    }

    private fun poserDesTaxons() {
        taxRef.remplacerTout(
            mapOf(TaxRefCache.normaliser("Machaon") to TaxRefEntry(54468, "Papilio machaon", "Machaon"))
        )
        taxRef.versionSauvegardee = "17"
        taxRef.listesSynchronisees = listOf(100)
    }

    @Test
    fun la_liste_de_saisie_doit_etre_sur_le_telephone() {
        // 21 octobre : la synchro ne charge que les listes utiles ; une liste choisie après coup se
        // télécharge à son choix, et la saisie l'attend.
        poserDesTaxons()
        taxRef.listesSynchronisees = listOf(200)
        assertFalse(configurationComplete(configValide(), taxRef), "la liste 100 n'est pas chargée")
        taxRef.listesSynchronisees = listOf(200, 100)
        assertTrue(configurationComplete(configValide(), taxRef))
    }

    @Test
    fun cache_complet_la_configuration_est_complete() {
        poserDesTaxons()
        assertEquals(emptyList<Int>(), taxRef.listesIncompletes)
        assertTrue(configurationComplete(configValide(), taxRef))
    }

    @Test
    fun une_liste_incomplete_rend_la_configuration_incomplete() {
        poserDesTaxons()
        taxRef.listesIncompletes = listOf(109)
        assertFalse(configurationComplete(configValide(), taxRef), "la saisie doit rester bloquée")
    }

    @Test
    fun un_chargement_reussi_leve_le_blocage() {
        poserDesTaxons()
        taxRef.listesIncompletes = listOf(109, 102)
        assertFalse(configurationComplete(configValide(), taxRef))
        // Ce que fait la synchro quand toutes les listes vont à leur terme.
        taxRef.listesIncompletes = emptyList()
        assertTrue(configurationComplete(configValide(), taxRef))
    }

    @Test
    fun le_verdict_survit_au_redemarrage_et_tombe_avec_le_cache() {
        taxRef.listesIncompletes = listOf(105, 109)
        t.redemarrer()
        assertEquals(listOf(105, 109), taxRef.listesIncompletes)
        // « Vider le cache » ou une purge de synchro repart d'une ardoise propre.
        taxRef.vider()
        assertEquals(emptyList<Int>(), taxRef.listesIncompletes)
    }

    /** SITE D'APPEL : la synchro doit écrire ce verdict, sinon le blocage ne se déclenche jamais.
     *  Sous Android, une garde lisait la source de la synchro ; portée, elle se teste. */
    @Test
    fun la_synchro_persiste_le_verdict_et_la_configuration_en_tient_compte() = runTest {
        val cfg = configValide()
        cfg.urlServeur = t.serveur.base
        // La liste 100 s'arrête en page 2 (erreur serveur) après une page 1 pleine.
        val page1 = (1..1000).joinToString(",", """{"items":[""", "]}") {
            """{"cd_nom":$it,"lb_nom":"Taxon $it","group2_inpn":"Oiseaux","regne":"Animalia"}"""
        }
        t.serveur.router { chemin ->
            when {
                chemin.contains("/api/taxhub/api/taxref/version") -> json("""{"version":"17"}""")
                chemin.contains("/api/taxhub/api/taxref") && chemin.contains("page=1") -> json(page1)
                chemin.contains("/api/taxhub/api/taxref") -> statut(500)
                else -> statut(404)
            }
        }
        val (_, msg) = t.services.geoNatureSync.synchroniserTaxRef(cfg) { _, _, _ -> }
        assertTrue(msg.contains("⚠"), msg)
        assertEquals(listOf(100), taxRef.listesIncompletes, "verdict persisté par la synchro")
        assertFalse(configurationComplete(cfg, taxRef))
    }

    // Garde non portée : parametres_n_ouvre_aucune_porte_derobee_vers_les_saisies lisait l'écran
    // Paramètres et son layout ; elle reviendra en test de comportement avec l'écran (lot 5).
}
