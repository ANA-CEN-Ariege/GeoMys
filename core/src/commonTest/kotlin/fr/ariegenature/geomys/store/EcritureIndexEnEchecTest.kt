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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/EcritureIndexEnEchecTest.kt) :
// Robolectric remplacé par un dossier temporaire et des réglages en mémoire, l'objet TaxRefCache
// par une instance, JUnit par kotlin.test. La garde textuelle sur la source de la synchro devient
// un vrai test de la synchro, désormais portée.

package fr.ariegenature.geomys.store

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.network.GeoNatureAuth
import fr.ariegenature.geomys.network.GeoNatureBrowse
import fr.ariegenature.geomys.network.GeoNatureSync
import fr.ariegenature.geomys.network.ServeurSimule
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.SYSTEM

/**
 * UN INDEX DE TAXONS QUI NE S'ÉCRIT PAS DOIT FAIRE ÉCHOUER LA SYNCHRO (audit 2026-09-18, T8).
 *
 * `ecrireFichier` avalait toute exception : disque plein, et la synchro se déclarait réussie avec
 * un référentiel troué. Le coût n'a rien de théorique — sans `listes_v1.json`, `cdNomsDansListe`
 * rend un ensemble vide et la saisie monitoring refuse TOUS les taxons du protocole ; sans
 * `verns_v1.json`, le mode « noms français » ne propose plus rien. Le cache principal, lui, était
 * déjà contrôlé (audit 2026-08-27) : les index annexes suivent la même règle.
 *
 * L'échec est provoqué ici en occupant le chemin du fichier temporaire par un RÉPERTOIRE : plus
 * fidèle qu'un jeu sur les permissions, et déterministe sur toutes les machines.
 */
class EcritureIndexEnEchecTest {

    // Les objets d'Android deviennent des instances neuves par test, dans un dossier temporaire
    // et des réglages en mémoire (Robolectric sous Android).
    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val taxRef = TaxRefCache(dossier, MapSettings())

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    @BeforeTest
    fun setup() {
        taxRef.vider()
    }

    /** Rend l'écriture de [fichier] impossible. À rappeler avant chaque écriture : le code de
     *  production nettoie le temporaire derrière lui, ce qui libère le chemin. */
    private fun bloquer(fichier: String) {
        systeme.createDirectories(dossier / "$fichier.tmp")
    }

    @Test
    fun index_vernaculaire_non_ecrit_est_signale_et_rien_nest_memorise() {
        bloquer("verns_v1.json")
        assertFalse(taxRef.ajouterVerns(mapOf(4319 to listOf("Gobemouche gris"))))
        assertTrue(
            taxRef.vernsParCdNom().isEmpty(), "le mémo ne doit pas prétendre détenir ce que le disque n'a pas")
    }

    @Test
    fun index_scientifique_non_ecrit_est_signale() {
        bloquer("sci_v1.json")
        assertFalse(taxRef.ajouterSciNoms(mapOf(4319 to "Muscicapa striata")))
    }

    @Test
    fun groupes_non_ecrits_sont_signales_et_rien_nest_memorise() {
        bloquer("groupes_v1.json")
        assertFalse(taxRef.ajouterGroupes(mapOf(4319 to "Oiseaux")))
        assertTrue(taxRef.tousLesGroupes().isEmpty())
    }

    /** Les deux fichiers sont indépendants : celui qui passe est conservé, et l'appel rend compte
     *  de l'échec de l'autre. */
    @Test
    fun un_seul_des_deux_fichiers_en_echec_suffit_a_signaler_lechec() {
        bloquer("regnes_v1.json")
        assertFalse(
            taxRef.ajouterGroupes1etRegnes(mapOf(54468 to "Insectes"), mapOf(54468 to "Animalia")))
        assertEquals(mapOf("54468" to "Insectes"), taxRef.tousLesGroupes1())
        assertTrue(taxRef.tousLesRegnes().isEmpty())
    }

    @Test
    fun appartenance_aux_listes_non_ecrite_est_signalee() {
        bloquer("listes_v1.json")
        assertFalse(taxRef.ajouterListesParCdNom(mapOf(4319 to listOf(100))))
        assertTrue(
            taxRef.cdNomsDansListe(100).isEmpty(), "sans cet index, la saisie refuserait tous les taxons de la liste")
    }

    @Test
    fun index_par_groupe_non_ecrit_est_signale_et_rien_nest_memorise() {
        bloquer("index_taxon_v1.json")
        assertFalse(taxRef.setIndexParTaxon(mapOf(Taxon.OISEAU to listOf(4319))))
        assertNull(taxRef.indexParTaxon(Taxon.OISEAU))
    }

    /** CONTRE-ÉPREUVE indispensable : sans blocage, tout doit réussir — sinon les tests ci-dessus
     *  passeraient avec une implémentation qui renvoie false en toute circonstance. */
    @Test
    fun sans_blocage_toutes_les_ecritures_reussissent() {
        assertTrue(taxRef.ajouterVerns(mapOf(4319 to listOf("Gobemouche gris"))))
        assertTrue(taxRef.ajouterSciNoms(mapOf(4319 to "Muscicapa striata")))
        assertTrue(taxRef.ajouterGroupes(mapOf(4319 to "Oiseaux")))
        assertTrue(taxRef.ajouterGroupes1etRegnes(mapOf(4319 to "Oiseaux"), mapOf(4319 to "Animalia")))
        assertTrue(taxRef.ajouterListesParCdNom(mapOf(4319 to listOf(100))))
        assertTrue(taxRef.setIndexParTaxon(mapOf(Taxon.OISEAU to listOf(4319))))
        assertEquals(listOf(4319), taxRef.indexParTaxon(Taxon.OISEAU))
    }

    /** Rien à écrire n'est pas un échec : la synchro appelle ces deux-là même sans donnée. */
    @Test
    fun une_ecriture_vide_nest_pas_un_echec() {
        assertTrue(taxRef.ajouterSciNoms(emptyMap()))
        assertTrue(taxRef.ajouterListesParCdNom(emptyMap()))
    }

    // ── La synchro écoute ces échecs ──

    /** Une synchro TaxRef complète sur un serveur simulé : un taxon qui alimente les sept fichiers
     *  d'index (noms, groupes, groupe 1, règne, listes, index par groupe). */
    private suspend fun synchroniser(): Pair<Int, String> {
        val serveur = ServeurSimule()
        val auth = GeoNatureAuth(serveur.clients)
        val sync = GeoNatureSync(
            auth, serveur.clients, taxRef, NomenclatureCache(MapSettings(), taxRef),
            GeoNatureBrowse(auth, serveur.clients, taxRef),
        )
        val config = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
            urlServeur = serveur.base
            listesCacheJson = """[{"id":100,"nom":"Faune"}]"""
            taxaListeId = "100"
            datasetsCacheJson = """[{"id":1,"nom":"JDD"}]"""
        }
        serveur.router(
            "/api/taxhub/api/taxref/version" to { json("""{"version":"17"}""") },
            "/api/taxhub/api/taxref" to {
                json(
                    """{"items":[{"cd_nom":4319,"lb_nom":"Muscicapa striata","nom_vern":"Gobemouche gris",
                       "group2_inpn":"Oiseaux","group1_inpn":"Chordés","regne":"Animalia"}]}""",
                )
            },
        )
        return sync.synchroniserTaxRef(config) { _, _, _ -> }
    }

    /**
     * Les tests ci-dessus prouvent que les écritures RENDENT COMPTE de leur échec — pas que la
     * synchro l'écoute. Or c'est là que se tenait le défaut : `synchroniserTaxRef` ignorait six
     * valeurs de retour. Sous Android, une garde lisait la source de la synchro ; portée, elle se
     * teste pour de bon, chaque fichier d'index bloqué tour à tour.
     */
    @Test
    fun la_synchro_controle_chaque_ecriture_dindex_et_ressort_en_echec() = runTest {
        for (fichier in listOf(
            "verns_v1.json", "sci_v1.json", "groupes_v1.json", "groupes1_v1.json", "regnes_v1.json",
            "listes_v1.json", "index_taxon_v1.json",
        )) {
            taxRef.vider()
            bloquer(fichier)
            val (nb, msg) = synchroniser()
            assertEquals(0, nb, "$fichier bloqué : $msg")
            assertTrue(msg.startsWith("Écriture des index de taxons impossible"), "$fichier bloqué : $msg")
            assertNull(
                taxRef.versionSauvegardee,
                "la synchro doit ressortir en ÉCHEC quand un index n'a pas pu être écrit, sans poser " +
                    "la version du cache — sinon l'appli se croit configurée ($fichier).",
            )
        }
        // Contre-épreuve : sans blocage, la même synchro réussit et pose la version.
        taxRef.vider()
        val (nb, msg) = synchroniser()
        assertEquals(2, nb, msg)
        assertEquals("17", taxRef.versionSauvegardee)
    }
}
