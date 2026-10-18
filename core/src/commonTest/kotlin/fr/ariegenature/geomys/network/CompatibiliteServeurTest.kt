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

// Les cas qui suivent « Portés de GeoMys-Android » viennent de GeoMys-Android @ cdd5196
// (app/src/test/java/fr/ariegenature/geomys/CompatibiliteServeurTest.kt) : MockWebServer remplacé
// par ServeurSimule, Robolectric par des réglages en mémoire et un dossier temporaire, les objets
// par des instances, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
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

/** Tourne sur la JVM (tests host Android) et sur le simulateur iOS : vérifie au passage que le
 *  code commun se comporte pareil sur les deux plateformes.
 *
 *  Garde-fous de compatibilité serveur : version de l'instance GeoNature relevée au test
 *  de connexion (`/api/gn_commons/config`, best-effort — absent des vieilles versions) et
 *  avertissement sur les types de nomenclatures obligatoires absents après sync (sans quoi
 *  les envois omettent ces champs sans aucun signal utilisateur). */
class CompatibiliteServeurTest {

    @Test
    fun les_versions_se_comparent_par_segment_et_non_comme_du_texte() {
        assertTrue(comparerVersions("2.9", "2.15") < 0)
        assertTrue(comparerVersions("2.15.1", "2.15") > 0)
        assertTrue(comparerVersions("10.0", "9.9") > 0)
    }

    @Test
    fun un_segment_manquant_vaut_zero() {
        assertEquals(0, comparerVersions("2.15", "2.15.0"))
        assertEquals(0, comparerVersions(" 2.15 ", "2.15"))
    }

    @Test
    fun un_suffixe_non_numerique_est_ignore() {
        assertEquals(0, comparerVersions("2.15.0-rc1", "2.15"))
        assertEquals(0, comparerVersions("2.x", "2.0"))
    }

    @Test
    fun seule_une_version_au_moins_egale_a_la_minimale_est_supportee() {
        assertTrue(versionGeoNatureSupportee(VERSION_GEONATURE_MINIMALE))
        assertTrue(versionGeoNatureSupportee("2.16.3"))
        assertFalse(versionGeoNatureSupportee("2.14.2"))
    }

    // ── Portés de GeoMys-Android ───────────────────────────────────────────────────

    private val serveur = ServeurSimule()
    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val taxRef = TaxRefCache(dossier, MapSettings())
    private val nomenclatures = NomenclatureCache(MapSettings(), taxRef)
    private val auth = GeoNatureAuth(serveur.clients)
    private val sync = GeoNatureSync(
        auth, serveur.clients, taxRef, nomenclatures, GeoNatureBrowse(auth, serveur.clients, taxRef),
    )

    private val typesObligatoires = listOf(
        "METH_OBS", "STATUT_OBS", "SEXE", "STADE_VIE", "STATUT_BIO", "ETA_BIO",
        "PREUVE_EXIST", "OBJ_DENBR", "TYP_DENBR", "OCC_COMPORTEMENT", "METH_DETERMIN", "TYPE_MEDIA",
        "NATURALITE", "TYP_GRP", "NAT_OBJ_GEO",
    )

    private val config = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
        urlServeur = serveur.base
        login = "alice"; motDePasse = "pwd"; idDataset = "12"
        versionGeoNatureServeur = ""
    }

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    /** Routes login + config serveur. [configServeur] null = endpoint absent (404, vieux GeoNature). */
    private fun router(configServeur: String?, taxonomy: String? = null) {
        // Routes par préfixe : « taxonomy » se reconnaît aux trois chemins essayés par la synchro.
        val routesTaxonomy = listOf(
            "/api/nomenclatures/nomenclatures/taxonomy",
            "/api/nomenclatures/nomenclature/taxonomy",
            "/api/nomenclatures/taxonomy",
        ).map { chemin -> chemin to { taxonomy?.let { json(it) } ?: statut(404) } }
        serveur.router(
            "/api/auth/login" to { json("""{"access_token":"t","user":{"id_role":1}}""") },
            "/api/gn_commons/config" to { configServeur?.let { json(it) } ?: statut(404) },
            *routesTaxonomy.toTypedArray(),
            "/api/occtax/defaultNomenclatures" to { json("{}") },
            "/api/OCCTAX/defaultNomenclatures" to { json("{}") },
        )
    }

    /** Payload taxonomy pour les [types] donnés (1 valeur par type). */
    private fun payloadTaxonomy(types: List<String>): String = types.joinToString(",", "[", "]") {
        """{"mnemonique":"$it","nomenclatures":[{"id_nomenclature":1,"label_default":"x"}]}"""
    }

    // Version de l'instance GeoNature

    @Test
    fun test_connexion_releve_et_memorise_la_version_du_serveur() = runTest {
        router(configServeur = """{"GEONATURE_VERSION":"2.16.4"}""")
        val (ok, msg) = auth.testerConnexion(config)
        assertTrue(ok)
        // La version n'est plus RÉPÉTÉE dans le message (elle est affichée séparément dans l'UI,
        // ligne « Serveur GeoNature v… ») : message épuré. Ce qui compte = elle est MÉMORISÉE.
        assertEquals("Connexion réussie", msg)
        assertEquals("2.16.4", config.versionGeoNatureServeur)
    }

    @Test
    fun vieux_serveur_sans_endpoint_config_reste_un_succes_avec_note() = runTest {
        router(configServeur = null)  // 404 — endpoint absent des vieilles versions / proxy filtrant
        val (ok, msg) = auth.testerConnexion(config)
        assertTrue(ok, "l'absence de version ne doit pas faire échouer le test (bénéfice du doute)")
        assertTrue(msg.contains("non détectée"), "une note doit signaler la version non détectée : $msg")
        assertEquals("", config.versionGeoNatureServeur)
        assertTrue(config.serveurCompatible, "bénéfice du doute : la config reste utilisable")
    }

    @Test
    fun config_html_ou_cle_inconnue_ne_casse_pas_le_test_de_connexion() = runTest {
        router(configServeur = """{"autre_cle":"valeur"}""")
        val (ok, msg) = auth.testerConnexion(config)
        assertTrue(ok)
        assertFalse(msg.contains("GeoNature v2"))
    }

    // Version minimale supportée

    @Test
    fun version_trop_ancienne_fait_echouer_le_test_et_invalide_la_config() = runTest {
        router(configServeur = """{"GEONATURE_VERSION":"2.10.5"}""")
        val (ok, msg) = auth.testerConnexion(config)
        assertFalse(ok, "une version sous le minimum doit faire échouer le test")
        assertTrue(msg.contains("2.10.5") && msg.contains(VERSION_GEONATURE_MINIMALE),
            "le message doit citer la version détectée et le minimum : $msg")
        assertFalse(config.serveurCompatible)
        // Invalidation effective : toutes les gardes de config tombent.
        assertFalse(config.connexionConfiguree, "connexionConfiguree doit être invalidée")
        assertFalse(config.estConfiguree, "estConfiguree doit être invalidée")
        // La version reste mémorisée pour l'affichage diagnostique dans Paramètres.
        assertEquals("2.10.5", config.versionGeoNatureServeur)
    }

    @Test
    fun nouveau_test_contre_un_serveur_a_jour_retablit_la_config() = runTest {
        // 1er test : serveur trop ancien → config invalidée.
        router(configServeur = """{"GEONATURE_VERSION":"2.10.5"}""")
        auth.testerConnexion(config)
        assertFalse(config.connexionConfiguree)
        // Serveur mis à jour → un nouveau test réussi rétablit tout.
        router(configServeur = """{"GEONATURE_VERSION":"${VERSION_GEONATURE_MINIMALE}.1"}""")
        val (ok, _) = auth.testerConnexion(config)
        assertTrue(ok)
        assertTrue(config.serveurCompatible)
        assertTrue(config.connexionConfiguree)
    }

    @Test
    fun comparaison_de_versions_numerique_pas_lexicographique() {
        assertTrue(comparerVersions("2.9", "2.15") < 0, "2.9 < 2.15 (numérique, pas alphabétique)")
        assertTrue(comparerVersions("2.15", "2.15.0") == 0)
        assertTrue(comparerVersions("2.15.1", "2.15") > 0)
        assertTrue(comparerVersions("3.0", "2.99") > 0)
        assertTrue(comparerVersions("2.15.0-rc1", "2.15") == 0, "suffixe non numérique ignoré")
        assertTrue(versionGeoNatureSupportee("2.15"))
        assertTrue(versionGeoNatureSupportee("2.16.4"))
        assertFalse(versionGeoNatureSupportee("2.14.2"))
    }

    // Types de nomenclatures obligatoires

    @Test
    fun sync_complete_des_types_sans_avertissement() = runTest {
        router(configServeur = null, taxonomy = payloadTaxonomy(typesObligatoires))
        val (total, msg) = sync.synchroniserNomenclatures(config)
        assertEquals(typesObligatoires.size, total)
        assertFalse(msg.contains("⚠"), "aucun avertissement attendu : $msg")
    }

    @Test
    fun types_manquants_apres_sync_sont_signales_explicitement() = runTest {
        // Serveur incomplet : seuls SEXE et STATUT_OBS exposés.
        router(configServeur = null, taxonomy = payloadTaxonomy(listOf("SEXE", "STATUT_OBS")))
        val (total, msg) = sync.synchroniserNomenclatures(config)
        assertEquals(2, total)
        assertTrue(msg.contains("⚠"), "avertissement attendu : $msg")
        assertTrue(msg.contains("METH_OBS") && msg.contains("TYPE_MEDIA"),
            "les types manquants doivent être listés : $msg")
        assertTrue(msg.contains("omis"), "le risque doit être explicité : $msg")
        assertFalse(msg.substringAfter("⚠").contains("SEXE,"),
            "les types présents ne sont pas dans la liste des manquants")
    }
}
