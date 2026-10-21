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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/GeoNatureBrowseTest.kt) :
// MockWebServer remplacé par ServeurSimule et son routage, le faux Context par des réglages en
// mémoire, JUnit par kotlin.test. Les cas après chargerTousHabitats sont propres au portage.

package fr.ariegenature.geomys.network

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.explorer.Explorer
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import fr.ariegenature.geomys.store.CoffreEnMemoire
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.TaxRefEntry
import fr.ariegenature.geomys.util.uuidAleatoire
import io.ktor.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.SYSTEM

/**
 * Navigation GeoNature ([GeoNatureBrowse]) sur un serveur simulé : parsing des jeux de données,
 * des listes de taxons et des observateurs + construction des requêtes (chemins, méthode,
 * en-tête Bearer, corps POST) et comportement sur erreur (HTTP 500, JSON malformé).
 */
class GeoNatureBrowseTest {

    private val serveur = ServeurSimule()
    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val taxRef = TaxRefCache(dossier, MapSettings())
    private val browse = GeoNatureBrowse(GeoNatureAuth(serveur.clients), serveur.clients, taxRef)

    // Un service d'authentification neuf par test : plus besoin de vider le cache d'auth, qui
    // était global sous Android.
    private val config = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
        urlServeur = serveur.base
        login = "alice"
        motDePasse = "pwd"
    }

    /** Réponse du POST /api/auth/login (surchargée par les tests d'échec d'auth). */
    private var reponseLogin: () -> ServeurSimule.Reponse = { json("""{"access_token":"t","user":{"id_role":1}}""") }

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    /** Routage par préfixe de chemin ; le login est servi d'office via [reponseLogin]. */
    private fun router(vararg routes: Pair<String, () -> ServeurSimule.Reponse>) {
        serveur.router("/api/auth/login" to { reponseLogin() }, *routes)
    }

    private val chemins get() = serveur.chemins

    // ------------------------------------------------------------------ chargerDatasets

    @Test
    fun datasets_parsing_complet_entrees_invalides_ignorees_tri_francais() = runTest {
        router(
            "/api/meta/datasets" to {
                json(
                    """
                    [
                      {"id_dataset": 2, "dataset_name": "Zoo", "active": false, "id_taxa_list": 0,
                       "modules": [{"module_code": "OCCTAX"}, {"module_code": "MONITORINGS"}]},
                      {"id_dataset": 1, "dataset_name": "Épervier", "active": true, "id_taxa_list": 100,
                       "modules": ["OCCTAX"]},
                      {"id_dataset": 3, "dataset_name": "abeille"},
                      {"dataset_name": "sans id"},
                      {"id_dataset": 9}
                    ]
                    """.trimIndent(),
                )
            },
        )

        val datasets = browse.chargerDatasets(config)

        // Entrées sans id ou sans nom écartées ; tri Collator France (accents + casse ignorés).
        assertEquals(listOf("abeille", "Épervier", "Zoo"), datasets.map { it.nom })

        val epervier = datasets.first { it.nom == "Épervier" }
        assertEquals(1, epervier.id)
        assertEquals(100, epervier.idTaxaList)
        assertTrue(epervier.actif)
        assertEquals(listOf("OCCTAX"), epervier.moduleCodes) // format tableau de chaînes

        val zoo = datasets.first { it.nom == "Zoo" }
        assertFalse(zoo.actif, "active=false conservé (filtrage à l'affichage)")
        assertNull(zoo.idTaxaList, "id_taxa_list=0 → pas de liste imposée")
        assertEquals(listOf("OCCTAX", "MONITORINGS"), zoo.moduleCodes) // format objets module_code

        val abeille = datasets.first { it.nom == "abeille" }
        assertTrue(abeille.actif, "active absent → actif par défaut")
        assertTrue(abeille.moduleCodes.isEmpty())

        // Construction des requêtes : login PUIS GET filtré + fields=modules, avec le Bearer.
        val login = serveur.requetes[0]
        assertEquals(HttpMethod.Post, login.method)
        assertEquals("/api/auth/login", login.url.encodedPathAndQuery)
        val get = serveur.requetes[1]
        assertEquals(HttpMethod.Get, get.method)
        assertEquals("/api/meta/datasets?active=true&module_code=OCCTAX&fields=modules", get.url.encodedPathAndQuery)
        assertEquals("Bearer t", get.headers["Authorization"])
    }

    @Test
    fun datasets_reponse_enveloppee_sous_data_acceptee() = runTest {
        router("/api/meta/datasets" to { json("""{"data":[{"id_dataset":3,"dataset_name":"Alpha"}]}""") })
        val datasets = browse.chargerDatasets(config)
        assertEquals(listOf(GeoNatureDataset(3, "Alpha")), datasets)
    }

    @Test
    fun datasets_module_code_propage_dans_l_url() = runTest {
        router("/api/meta/datasets" to { json("[]") })
        browse.chargerDatasets(config, moduleCode = "OCCHAB")
        assertTrue(chemins.any { it == "/api/meta/datasets?active=true&module_code=OCCHAB&fields=modules" })
    }

    @Test
    fun datasets_http_500_leve_envoi_echoue() = runTest {
        router("/api/meta/datasets" to { statut(500) })
        val e = assertFailsWith<GNErreur.EnvoiEchoue>("EnvoiEchoue attendue") { browse.chargerDatasets(config) }
        assertEquals(500, e.code)
    }

    @Test
    fun datasets_200_mais_html_json_malforme_leve_envoi_echoue() = runTest {
        // Un portail captif/proxy qui répond 200 en HTML levait une MalformedJsonException Gson
        // brute : l'appelant doit voir une GNErreur lisible (« format JSON inattendu »),
        // comme pour chargerListesTaxons.
        router("/api/meta/datasets" to { statut(200, "<html>portail captif</html>", "Content-Type" to "text/html") })
        val e = assertFailsWith<GNErreur.EnvoiEchoue>("EnvoiEchoue attendue") { browse.chargerDatasets(config) }
        assertTrue(e.msg.contains("format JSON inattendu"))
    }

    @Test
    fun datasets_login_refuse_leve_auth_echouee() = runTest {
        reponseLogin = { statut(401) }
        router("/api/meta/datasets" to { json("[]") })
        val e = assertFailsWith<GNErreur.AuthEchouee>("AuthEchouee attendue") { browse.chargerDatasets(config) }
        assertEquals(401, e.code)
        assertTrue(chemins.none { it.startsWith("/api/meta") }, "le endpoint datasets ne doit pas être appelé sans auth")
    }

    // ------------------------------------------------------------------ chargerIdsDatasetsCreables

    @Test
    fun datasets_creables_post_create_module_et_ids_extraits() = runTest {
        router("/api/meta/datasets" to { json("""[{"id_dataset":5,"dataset_name":"A"},{"id_dataset":7,"dataset_name":"B"}]""") })
        val ids = browse.chargerIdsDatasetsCreables(config)
        assertEquals(setOf(5, 7), ids)

        val post = serveur.requetes[1] // après le login
        assertEquals(HttpMethod.Post, post.method)
        assertEquals("/api/meta/datasets?active=true", post.url.encodedPathAndQuery)
        // Même filtre CRUVED « C » que le formulaire web (cf. gn_mobile_core).
        assertEquals("""{"create":"OCCTAX"}""", post.texte)
    }

    @Test
    fun datasets_creables_http_500_renvoie_null() = runTest {
        // Échec : l'appelant garde les créables précédents.
        router("/api/meta/datasets" to { statut(500) })
        assertNull(browse.chargerIdsDatasetsCreables(config))
    }

    @Test
    fun datasets_creables_login_refuse_renvoie_null_sans_appel_au_endpoint() = runTest {
        reponseLogin = { statut(401) }
        router("/api/meta/datasets" to { json("[]") })
        assertNull(browse.chargerIdsDatasetsCreables(config))
        assertTrue(chemins.none { it.startsWith("/api/meta") })
    }

    @Test
    fun datasets_creables_reponse_vide_fait_foi() = runTest {
        router("/api/meta/datasets" to { json("[]") })
        assertEquals(emptySet(), browse.chargerIdsDatasetsCreables(config))
    }

    // ------------------------------------------------------------------ chargerListesTaxons

    @Test
    fun listes_taxons_parsing_tri_par_nom_et_url_taxhub_deduite() = runTest {
        router(
            "/api/taxhub/api/biblistes" to {
                json(
                    """
                    {"data":[
                      {"id_liste": 2, "nom_liste": "Oiseaux"},
                      {"id_liste": 1, "nom_liste": "Flore"},
                      {"id_liste": -1, "nom_liste": "invalide"},
                      {"nom_liste": "sans id"}
                    ]}
                    """.trimIndent(),
                )
            },
        )
        val listes = browse.chargerListesTaxons(config)
        assertEquals(listOf(1 to "Flore", 2 to "Oiseaux"), listes.map { it.id to it.nom })
        // taxhubUrlCache vide → base déduite `<serveur>/api/taxhub` (endpoint public, sans auth).
        assertEquals(listOf("/api/taxhub/api/biblistes"), chemins)
    }

    @Test
    fun listes_taxons_http_500_leve_envoi_echoue() = runTest {
        router("/api/taxhub/api/biblistes" to { statut(500) })
        val e = assertFailsWith<GNErreur.EnvoiEchoue>("EnvoiEchoue attendue") { browse.chargerListesTaxons(config) }
        assertEquals(500, e.code)
    }

    @Test
    fun listes_taxons_200_mais_html_json_malforme_leve_envoi_echoue() = runTest {
        // Un portail captif/proxy qui répond 200 en HTML ne doit pas produire une liste vide
        // silencieuse : l'appelant doit voir l'erreur (« format JSON inattendu »).
        router("/api/taxhub/api/biblistes" to { statut(200, "<html>portail captif</html>", "Content-Type" to "text/html") })
        val e = assertFailsWith<GNErreur.EnvoiEchoue>("EnvoiEchoue attendue") { browse.chargerListesTaxons(config) }
        assertTrue(e.msg.contains("format JSON inattendu"))
    }

    // ------------------------------------------------------------------ chargerObservateurs

    @Test
    fun observateurs_liste_configuree_du_module_servie_par_users_menu() = runTest {
        router(
            "/api/gn_commons/config" to { json("""{"OCCTAX":{"id_observers_list":9}}""") },
            "/api/users/menu/9" to {
                json(
                    """
                    [
                      {"id_role": 3, "nom_complet": "DUPONT jean"},
                      {"id_role": 4, "nom_complet": "AUBRY anna"},
                      {"id_role": 0, "nom_complet": "invalide"},
                      {"id_role": 5, "nom_role": "MARTIN", "prenom_role": "paul"}
                    ]
                    """.trimIndent(),
                )
            },
        )
        val obs = browse.chargerObservateurs(config)
        // Tri insensible à la casse ; repli nom_role+prenom_role quand nom_complet manque.
        assertEquals(
            listOf(4 to "AUBRY anna", 3 to "DUPONT jean", 5 to "MARTIN paul"),
            obs.map { it.idRole to it.nomComplet },
        )
        assertTrue(chemins.none { it.startsWith("/api/users/roles") }, "la liste curée dispense d'appeler /users/roles")
    }

    // Le repli sur /users/roles est retiré (21 octobre) : le web n'affiche que la liste d'OccTax.

    @Test
    fun observateurs_sans_liste_configuree_leve_une_erreur_sans_users_roles() = runTest {
        // /api/gn_commons/config absent (404) : la synchro gardera la liste du cache.
        router("/api/users/roles" to { json("""[{"id_role":1,"nom_complet":"SEULE alice"}]""") })
        assertFailsWith<GNErreur.EnvoiEchoue> { browse.chargerObservateurs(config) }
        assertTrue(chemins.none { it.startsWith("/api/users/roles") }, "plus de repli sur tous les rôles")
    }

    @Test
    fun observateurs_menu_vide_rend_une_liste_vide_comme_le_web() = runTest {
        router(
            "/api/gn_commons/config" to { json("""{"OCCTAX":{"id_observers_list":9}}""") },
            "/api/users/menu/9" to { json("[]") },
            "/api/users/roles" to { json("""[{"id_role":1,"nom_complet":"SEULE alice"}]""") },
        )
        assertEquals(emptyList(), browse.chargerObservateurs(config))
        assertTrue(chemins.none { it.startsWith("/api/users/roles") }, "plus de repli sur tous les rôles")
    }

    @Test
    fun observateurs_menu_en_erreur_leve_envoi_echoue() = runTest {
        router(
            "/api/gn_commons/config" to { json("""{"OCCTAX":{"id_observers_list":9}}""") },
            "/api/users/menu/9" to { statut(500) },
        )
        assertFailsWith<GNErreur.EnvoiEchoue>("EnvoiEchoue attendue") { browse.chargerObservateurs(config) }
    }

    // ------------------------------------------------------------------ chargerTousHabitats

    @Test
    fun habitats_liste_du_module_honoree_et_parsing_avec_repli_lb_code() = runTest {
        router(
            "/api/gn_commons/config" to { json("""{"OCCTAX":{"ID_LIST_HABITAT":55}}""") },
            "/api/habref/habitats/autocomplete" to {
                json(
                    """
                    [
                      {"cd_hab": 616, "search_name": "Prairies humides", "lb_code": "37.2", "cd_typo": 22},
                      {"cd_hab": 0, "search_name": "invalide"},
                      {"cd_hab": 17, "lb_code": "62.1"}
                    ]
                    """.trimIndent(),
                )
            },
        )
        val habitats = browse.chargerTousHabitats(config)
        assertEquals(
            listOf(
                Triple(616, "Prairies humides", 22),
                Triple(17, "62.1", null), // search_name absent → repli lb_code
            ),
            habitats.map { Triple(it.cdHab, it.libelle, it.cdTypo) },
        )
        assertTrue(
            chemins.any { it == "/api/habref/habitats/autocomplete?limit=200000&id_list=55" },
            "la liste configurée doit filtrer l'autocomplete",
        )
    }

    @Test
    fun habitats_http_500_renvoie_une_liste_vide() = runTest {
        // Best-effort.
        router("/api/habref/habitats/autocomplete" to { statut(500) })
        assertTrue(browse.chargerTousHabitats(config).isEmpty())
    }

    // ------------------------------------------------------------------ propres au portage

    @Test
    fun datasets_valeurs_de_types_inattendus_lues_comme_le_jsonreader_de_gson() = runTest {
        // Id en chaîne ou en 13.0, active en chaîne (« TRUE »), nom numérique, modules mêlés : les
        // lireIntOuNull, lireStringOuVide et lireBoolDefaut d'Android. Écarts voulus : un id 15.5 ou
        // un élément qui n'est pas un objet faisaient échouer toute la lecture sous Gson ; ici,
        // seule l'entrée est écartée.
        router(
            "/api/meta/datasets" to {
                json(
                    """
                    [
                      {"id_dataset": "12", "dataset_name": "Douze", "active": "TRUE",
                       "modules": ["", 7, null, {"module_code": 8}, {"autre": "X"}, "OCCTAX"]},
                      {"id_dataset": 13.0, "dataset_name": 2026, "active": "non", "id_taxa_list": "100"},
                      {"id_dataset": 14, "dataset_name": true},
                      {"id_dataset": 15.5, "dataset_name": "Fraction"},
                      {"id_dataset": 16, "dataset_name": "Seize", "active": null, "id_taxa_list": {"x": 1}},
                      "pas un objet"
                    ]
                    """.trimIndent(),
                )
            },
        )
        val datasets = browse.chargerDatasets(config)
        assertEquals(
            listOf(
                GeoNatureDataset(13, "2026", idTaxaList = 100, actif = false),
                GeoNatureDataset(12, "Douze", actif = true, moduleCodes = listOf("8", "OCCTAX")),
                GeoNatureDataset(16, "Seize", actif = true),
            ),
            datasets,
        )
    }

    @Test
    fun habitats_reponse_html_renvoie_une_liste_vide() = runTest {
        router("/api/habref/habitats/autocomplete" to { statut(200, "<html>portail captif</html>", "Content-Type" to "text/html") })
        assertTrue(browse.chargerTousHabitats(config).isEmpty())
    }

    @Test
    fun explorer_corps_de_la_requete_et_classement_des_observations() = runTest {
        // 2026-10-18 12:00 UTC ; 365 jours avant : 2025-10-18, quel que soit le fuseau de la France.
        val navigation = GeoNatureBrowse(GeoNatureAuth(serveur.clients), serveur.clients, taxRef, horloge = { 1_792_324_800_000L })
        taxRef.remplacerTout(mapOf("herisson" to TaxRefEntry(999, "Erinaceus europaeus", "Hérisson d'Europe")))
        taxRef.ajouterGroupes(mapOf(999 to "Mammifères"))
        taxRef.ajouterVerns(mapOf(999 to listOf("Hérisson d'Europe")))
        router(
            "/api/synthese/for_web" to {
                json(
                    """
                    {"type":"FeatureCollection","features":[
                      {"geometry":{"type":"Point","coordinates":[1.5,42.9]},
                       "properties":{"group2_inpn":"Oiseaux","nom_cite":"Tichodrome","lb_nom":"Tichodroma muraria",
                                     "nom_vern":"Tichodrome échelette","date_min":"2026-05-01","count_min":3,"cd_nom":4494}},
                      {"geometry":{"type":"Point","coordinates":[5.0,42.9]},
                       "properties":{"group2_inpn":"Oiseaux","nom_cite":"Hors emprise"}},
                      {"geometry":{"type":"Point","coordinates":[1.6,43.0]},
                       "properties":{"cd_nom":999,"nom_cite":"Erinaceus europaeus","count_min":0}},
                      {"geometry":{"type":"Point","coordinates":[1.7,43.1]},
                       "properties":{"cd_nom":12345,"nom_cite":"Inconnu du cache"}}
                    ]}
                    """.trimIndent(),
                )
            },
        )

        val obs = navigation.recupererObsExplorer(config, minLon = 1.0, minLat = 42.5, maxLon = 2.0, maxLat = 43.5)

        assertEquals(listOf("Tichodrome", "Erinaceus europaeus", "Inconnu du cache"), obs.map { it.nomCite })
        assertEquals(Taxon.OISEAU, obs[0].taxon)
        assertEquals(3, obs[0].nombre)
        assertEquals("Tichodroma muraria", obs[0].nomSci)
        // Sans groupe dans la Synthèse : celui du cache TaxRef, et le nom vernaculaire du cache.
        assertEquals(Taxon.MAMMIFERE, obs[1].taxon)
        assertEquals("Hérisson d'Europe", obs[1].nomVern)
        assertEquals(1, obs[1].nombre, "count_min 0 ramené à 1")
        // Taxon absent du téléphone : gardé, sans groupe (icône neutre), et non plus écarté (21 octobre).
        assertNull(obs[2].taxon)

        val post = serveur.requetes.last()
        assertEquals(HttpMethod.Post, post.method)
        assertEquals("/api/synthese/for_web?limit=1000", post.url.encodedPathAndQuery)
        assertTrue(post.texte.contains(""""date_min":"2025-10-18""""), post.texte)
        assertTrue(post.texte.contains("""[[[1.0,42.5],[2.0,42.5],[2.0,43.5],[1.0,43.5],[1.0,42.5]]]"""), post.texte)
    }

    @Test
    fun explorer_le_groupe_vient_de_la_synthese_ou_du_cache_sinon_il_manque() = runTest {
        // 21 octobre 2026 : GeoMys-Android écartait les taxons absents du cache (171 des 195 observations
        // d'une emprise du serveur de démonstration) et rangeait aux oiseaux ceux du cache sans groupe.
        val navigation = GeoNatureBrowse(GeoNatureAuth(serveur.clients), serveur.clients, taxRef, horloge = { 1_792_324_800_000L })
        taxRef.remplacerTout(
            mapOf(
                "epeire" to TaxRefEntry(10, "Araneus diadematus"),
                "carex" to TaxRefEntry(11, "Carex pendula"),
                "sans groupe" to TaxRefEntry(12, "Taxon sans groupe"),
            ),
        )
        taxRef.ajouterGroupes(mapOf(10 to "Arachnides", 11 to "Angiospermes"))
        taxRef.ajouterGroupes1etRegnes(emptyMap(), mapOf(10 to "Animalia", 11 to "Plantae"))
        fun point(proprietes: String) = """{"geometry":{"type":"Point","coordinates":[1.5,42.9]},"properties":{$proprietes}}"""
        val points = listOf(
            point(""""cd_nom":10,"nom_cite":"epeire""""),
            point(""""cd_nom":11,"nom_cite":"carex""""),
            point(""""cd_nom":12,"nom_cite":"en cache sans groupe""""),
            point(""""cd_nom":13,"nom_cite":"absent du telephone""""),
            point(""""cd_nom":14,"group2_inpn":"Angiospermes","nom_cite":"groupe sans regne connu""""),
            point(""""cd_nom":15,"group2_inpn":"Insectes","nom_cite":"groupe de la synthese""""),
            point(""""nom_cite":"sans cd_nom""""),
        )
        router("/api/synthese/for_web" to { json("""{"type":"FeatureCollection","features":[${points.joinToString(",")}]}""") })
        val obs = navigation.recupererObsExplorer(config, minLon = 1.0, minLat = 42.5, maxLon = 2.0, maxLat = 43.5)
        assertEquals(
            listOf(
                "epeire" to Taxon.INVERTEBRES,
                "carex" to Taxon.PLANTE,
                "en cache sans groupe" to null,
                "absent du telephone" to null,
                "groupe sans regne connu" to null,
                "groupe de la synthese" to Taxon.INSECTE,
                "sans cd_nom" to null,
            ),
            obs.map { it.nomCite to it.taxon },
        )
    }

    @Test
    fun explorer_le_nom_que_for_web_envoie_par_defaut() = runTest {
        // GeoNature 2.17 n'envoie par défaut ni nom_cite ni lb_nom, mais nom_vern_or_lb_nom (serveur de
        // démonstration, 21 octobre) : un taxon sans nom français en cache restait « Espèce inconnue ».
        val navigation = GeoNatureBrowse(GeoNatureAuth(serveur.clients), serveur.clients, taxRef, horloge = { 1_792_324_800_000L })
        taxRef.remplacerTout(mapOf("carex pendula" to TaxRefEntry(5, "Carex pendula")))
        taxRef.ajouterGroupes(mapOf(5 to "Angiospermes"))
        router(
            "/api/synthese/for_web" to {
                json(
                    """{"type":"FeatureCollection","features":[{"geometry":{"type":"Point","coordinates":[1.5,42.9]},
                       "properties":{"cd_nom":5,"nom_vern_or_lb_nom":"Carex pendula","date_min":"2026-05-01","id_synthese":1}}]}""",
                )
            },
        )
        val obs = navigation.recupererObsExplorer(config, minLon = 1.0, minLat = 42.5, maxLon = 2.0, maxLat = 43.5).single()
        assertEquals("Carex pendula", obs.nomCite)
        assertEquals("Carex pendula", Explorer.titre(obs))
    }
}
