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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/MonitoringDatalistDatasetTest.kt) :
// MockWebServer remplacé par ServeurSimule, Robolectric par MonitoringDeTest, les objets par des
// instances, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import io.ktor.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** Datalist `dataset` ([MonitoringApi.chargerOptionsDatalist]) : le filtre `create=` (CRUVED
 *  action C) renvoie vide sur certaines instances alors que des jeux de données sont bien
 *  rattachés au module — bug terrain « rien dans le champ jeu de données » (Point écoute
 *  avifaune), qui bloquait toute saisie (champ requis). On retente alors sans `create`. */
class MonitoringDatalistDatasetTest {

    private val m = MonitoringDeTest()

    /** Corps des POST /meta/datasets reçus, dans l'ordre. */
    private val corpsRecus = mutableListOf<String>()

    @AfterTest
    fun nettoyer() = m.nettoyer()

    @BeforeTest
    fun setup() {
        m.config.idDataset = "12"
        m.config.datasetsCacheJson = ""
        router(createRenvoieVide = true)
    }

    /** [createRenvoieVide] : le filtre `create` renvoie VIDE (objet de permission absent du
     *  protocole sur cette instance) ; sans lui, le dataset du module sort. */
    private fun router(createRenvoieVide: Boolean) {
        m.serveur.routerRequetes { requete ->
            val path = requete.url.encodedPathAndQuery
            when {
                path.startsWith("/api/auth/login") -> json("""{"access_token":"t","user":{"id_role":1}}""")
                path.startsWith("/api/meta/datasets") -> {
                    assertEquals(HttpMethod.Post, requete.method, "filtres du backend lus dans le corps JSON")
                    val corps = requete.texte
                    corpsRecus.add(corps)
                    if (createRenvoieVide && corps.contains("\"create\"")) json("[]")
                    else json("""[{"id_dataset":12,"dataset_name":"Suivi avifaune ANA"}]""")
                }
                else -> statut(404)
            }
        }
    }

    private fun propDataset() = MonitoringApi.MonitoringPropertySchema(
        nom = "id_dataset",
        typeWidget = "dataset",
        label = "Jeu de données",
        obligatoire = true,
        apiUrl = "meta/datasets?module_code=pt_ecoute_avifaune&active=true&create=pt_ecoute_avifaune.MONITORINGS_VISITES",
        keyLabel = "dataset_name",
        keyValue = "id_dataset",
    )

    @Test
    fun dataset_vide_avec_create_est_retente_sans_create() = runTest {
        val options = m.api.chargerOptionsDatalist(m.config, propDataset())
        assertEquals(listOf("12" to "Suivi avifaune ANA"), options?.map { it.value to it.label },
            "le dataset du module doit sortir via le repli sans create")
        assertEquals(2, corpsRecus.size, "2 tentatives : avec create (vide) puis sans")
        assertEquals(true, corpsRecus[0].contains("\"create\""), "la 1re porte le filtre create")
        assertEquals(false, corpsRecus[1].contains("\"create\""), "la 2e ne le porte plus")
    }

    @Test
    fun dataset_non_vide_avec_create_ne_retente_pas() = runTest {
        // Le serveur honore le filtre create : une seule tentative suffit.
        router(createRenvoieVide = false)
        val options = m.api.chargerOptionsDatalist(m.config, propDataset())
        assertEquals(1, options?.size)
        assertEquals(1, corpsRecus.size, "une seule tentative")
    }

    // Propre au portage : le corps porte les filtres de la requête, décodés et typés.
    @Test
    fun les_filtres_de_l_url_passent_dans_le_corps_json() = runTest {
        router(createRenvoieVide = false)
        m.api.chargerOptionsDatalist(m.config, propDataset())
        assertEquals(
            """{"module_code":"pt_ecoute_avifaune","active":true,"create":"pt_ecoute_avifaune.MONITORINGS_VISITES"}""",
            corpsRecus.single(),
        )
        assertEquals("/api/meta/datasets", m.serveur.chemins.last())
    }
}
