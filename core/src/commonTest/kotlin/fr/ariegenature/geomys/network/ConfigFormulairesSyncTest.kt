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

// Propre au portage (21 octobre 2026) : GeoMys-Android ne lisait gn_commons/config qu'après une réponse
// 200 de t_mobile_apps.

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

/** Le formulaire du web (`gn_commons/config`) se lit même quand `t_mobile_apps` échoue : un serveur
 *  qui y répond 500 (une appli mobile enregistrée sans son settings.json) ne fige plus les champs
 *  OccTax, le filtre taxonomique des nomenclatures ni les droits des Suivis. */
class ConfigFormulairesSyncTest {

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
        login = "alice"; motDePasse = "pwd"
    }

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    @Test
    fun la_config_du_web_se_lit_meme_si_t_mobile_apps_echoue() = runTest {
        serveur.router(
            "/api/auth/login" to { json("""{"access_token":"t","user":{"id_role":1}}""") },
            "/api/gn_commons/t_mobile_apps" to { statut(500) },
            "/api/gn_commons/config" to {
                json(
                    """{"ENABLE_NOMENCLATURE_TAXONOMIC_FILTERS":false,
                        "OCCTAX":{"form_fields":{"date_max":false},"id_taxon_list":100},
                        "MONITORINGS":{"PERMISSION_LEVEL":{"site":"MONITORINGS_SITES"}}}""",
                )
            },
        )
        assertEquals("t_mobile_apps HTTP 500", geoNatureSync.synchroniserSettingsOcctax(config).second)
        assertTrue("date_max" in config.formFieldsJson, config.formFieldsJson)
        assertFalse(config.dateFinVisible, "form_fields du serveur appliqué")
        assertFalse(config.nomenclaturesFiltreesParTaxon)
        assertEquals(100, config.occtaxIdListeTaxons)
        assertTrue("MONITORINGS_SITES" in config.niveauxPermissionSuivisJson)
    }

    @Test
    fun le_taxhub_du_web_passe_avant_l_adresse_publiee_pour_les_mobiles() = runTest {
        // Un serveur essayé le 21 octobre : l'adresse publiée avait un certificat invalide, l'intégrée répondait.
        serveur.router(
            "/api/auth/login" to { json("""{"access_token":"t","user":{"id_role":1}}""") },
            "/api/gn_commons/config" to { json("""{"OCCTAX":{"DATE_FORM_WITH_TODAY":false}}""") },
            "/api/taxhub/api/biblistes" to { json("""{"data":[{"id_liste":100,"nom_liste":"Faune"}]}""") },
            "/api/gn_commons/t_mobile_apps" to { json("""[{"app_code":"OCCTAX","settings":{"sync":{"taxhub_url":"https://taxhub.ailleurs.test"}}}]""") },
        )
        geoNatureSync.synchroniserSettingsOcctax(config)
        assertEquals("${serveur.base}/api/taxhub", config.urlTaxhub)
        assertFalse(config.occtaxDateDuJour, "OCCTAX.DATE_FORM_WITH_TODAY lu")
    }

    @Test
    fun l_adresse_publiee_sert_quand_le_taxhub_integre_ne_repond_pas() = runTest {
        serveur.router(
            "/api/auth/login" to { json("""{"access_token":"t","user":{"id_role":1}}""") },
            "/api/gn_commons/t_mobile_apps" to { json("""[{"app_code":"OCCTAX","settings":{"sync":{"taxhub_url":"https://taxhub.ailleurs.test"}}}]""") },
        )
        geoNatureSync.synchroniserSettingsOcctax(config)
        assertEquals("https://taxhub.ailleurs.test", config.urlTaxhub)
        assertTrue(config.occtaxDateDuJour, "sans réglage, la date du jour")
    }
}
