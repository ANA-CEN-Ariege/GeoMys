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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/AdditionalFieldsChargerTest.kt) :
// MockWebServer remplacé par ServeurSimule, Robolectric par des réglages en mémoire et un dossier
// temporaire, JUnit par kotlin.test. Le dernier cas est propre au portage.

package fr.ariegenature.geomys.network

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.model.WidgetType
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
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.SYSTEM

/** Bout-en-bout (auth + GET) du chargement des champs additionnels OccTax sur un serveur simulé.
 *  Vérifie le contrat HTTP : 404 → liste vide, 200 → parsing. */
class AdditionalFieldsChargerTest {

    private val serveur = ServeurSimule()
    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val nomenclatures = NomenclatureCache(MapSettings(), TaxRefCache(dossier, MapSettings()))
    private val api = AdditionalFieldsApi(GeoNatureAuth(serveur.clients), serveur.clients, nomenclatures)

    private val config = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
        urlServeur = serveur.base
        login = "alice"
        motDePasse = "pwd"
    }

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    /** Répond au login pour toutes les requêtes /api/auth/login, et délègue le reste à [routes]. */
    private fun router(vararg routes: Pair<String, () -> ServeurSimule.Reponse>) {
        serveur.router("/api/auth/login" to { json("""{"access_token":"t","user":{"id_role":1}}""") }, *routes)
    }

    @Test
    fun http_404_renvoie_liste_vide() = runTest {
        router()
        val defs = api.charger(config, "OCCTAX")
        assertTrue(defs.isEmpty())
    }

    @Test
    fun http_200_parse_les_champs() = runTest {
        router(
            "/api/gn_commons/additional_fields" to {
                json(
                    """
                    [{"id_field":1,"field_name":"altitude","field_label":"Altitude",
                      "type_widget":"number","objects":[{"code_object":"OCCTAX_RELEVE"}]},
                     {"id_field":2,"field_name":"meteo","type_widget":"select",
                      "field_values":["soleil","pluie"]}]
                    """.trimIndent(),
                )
            },
        )
        val defs = api.charger(config, "OCCTAX")
        assertEquals(2, defs.size)
        assertEquals("altitude", defs[0].fieldName)
        assertEquals(WidgetType.NUMBER, defs[0].widget)
        assertTrue("OCCTAX_RELEVE" in defs[0].objectsCode)
        assertEquals(listOf("soleil", "pluie"), defs[1].fieldValues)
    }

    @Test
    fun reponse_enveloppee_dans_data() = runTest {
        router(
            "/api/gn_commons/additional_fields" to {
                json("""{"data":[{"id_field":3,"field_name":"x","type_widget":"text"}]}""")
            },
        )
        val defs = api.charger(config, "OCCTAX")
        assertEquals(1, defs.size)
        assertEquals("x", defs[0].fieldName)
    }

    @Test
    fun widget_nomenclature_resolu_avec_le_defaut_du_module() = runTest {
        // Options lues sur /api/nomenclatures/nomenclature/<code>, triées par libellé ; défaut
        // repris des nomenclatures par défaut du module quand le champ n'en a pas.
        nomenclatures.setDefauts(mapOf("STATUT_OBS" to "85"))
        router(
            "/api/gn_commons/additional_fields" to {
                json(
                    """
                    [{"id_field":4,"field_name":"statut","field_label":"Statut",
                      "type_widget":"nomenclature","code_nomenclature_type":"STATUT_OBS"},
                     {"id_field":5,"field_name":"note","field_label":"Note","type_widget":"text"}]
                    """.trimIndent(),
                )
            },
            "/api/nomenclatures/nomenclature/STATUT_OBS" to {
                json(
                    """
                    {"values":[{"id_nomenclature":85,"label_fr":"Présent"},
                               {"id_nomenclature":84,"label_default":"Non observé"},
                               {"id_nomenclature":86}]}
                    """.trimIndent(),
                )
            },
        )
        val defs = api.charger(config, "OCCTAX")
        assertEquals(listOf("note", "statut"), defs.map { it.fieldName })
        val statut = defs[1]
        assertEquals(WidgetType.NOMENCLATURE, statut.widget)
        assertEquals(listOf("84" to "Non observé", "85" to "Présent"), statut.nomenclatureOptions)
        assertEquals("85", statut.defaultValue)
        assertTrue(serveur.chemins.any { it == "/api/gn_commons/additional_fields?module_code=OCCTAX" })
    }
}
