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

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import io.ktor.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** Les options d'une datalist demandées comme le web (getDataList) : l'api du serveur pour
 *  `GeoNature`, celle de TaxHub pour `TaxHub`, l'adresse brute sinon, en POST avec le corps `data` ;
 *  les clés par défaut d'une datalist sont `value` et `label`. Relues hors ligne. */
class MonitoringDatalistApplicationTest {

    private val m = MonitoringDeTest()
    private val requetes = mutableListOf<Triple<HttpMethod, String, String>>()

    @BeforeTest
    fun setup() {
        m.serveur.routerRequetes { requete ->
            val path = requete.url.encodedPathAndQuery
            if (path.startsWith("/api/auth/login")) return@routerRequetes json("""{"access_token":"t","user":{"id_role":1}}""")
            requetes.add(Triple(requete.method, path, if (requete.method == HttpMethod.Post) requete.texte else ""))
            when {
                path.startsWith("/api/taxhub/api/bdc_statuts") -> json("""[{"value":"EN","label":"En danger"}]""")
                path.startsWith("/api/habitats/liste") -> json("""[{"value":1,"label":"Prairie"},{"value":2,"label":"Lande"}]""")
                path.startsWith("/externe/liste") -> json("""{"items":[{"value":"a","label":"A"}]}""")
                else -> statut(404)
            }
        }
    }

    @AfterTest
    fun nettoyer() = m.nettoyer()

    private fun prop(api: String, application: String?, donnees: String? = null) = MonitoringApi.MonitoringPropertySchema(
        nom = "liste",
        typeWidget = "datalist",
        label = "Liste",
        obligatoire = false,
        apiUrl = api,
        application = application,
        donneesPost = donnees,
    )

    @Test
    fun taxhub_sur_l_api_de_taxhub() = runTest {
        val p = prop("bdc_statuts?code=EN", "TaxHub")
        assertEquals(listOf("En danger"), m.api.chargerOptionsDatalist(m.config, p)?.map { it.label })
        assertEquals(HttpMethod.Get to "/api/taxhub/api/bdc_statuts?code=EN", requetes.single().let { it.first to it.second })
        assertEquals(listOf("EN"), m.api.lireOptionsDatalist(m.config, p)?.map { it.value }, "relue hors ligne")
    }

    @Test
    fun data_en_post_avec_son_corps() = runTest {
        val p = prop("habitats/liste", "GeoNature", """{"cd_typo":7}""")
        assertEquals(listOf("Prairie", "Lande"), m.api.chargerOptionsDatalist(m.config, p)?.map { it.label })
        val (methode, chemin, corps) = requetes.single()
        assertEquals(HttpMethod.Post, methode)
        assertEquals("/api/habitats/liste", chemin)
        assertEquals("""{"cd_typo":7}""", corps)
        assertEquals(listOf("1", "2"), m.api.lireOptionsDatalist(m.config, p)?.map { it.value })
    }

    @Test
    fun adresse_brute_sans_application() = runTest {
        val p = prop("${m.serveur.base}/externe/liste", null).copy(dataPath = "items")
        assertEquals(listOf("A"), m.api.chargerOptionsDatalist(m.config, p)?.map { it.label })
        assertEquals("/externe/liste", requetes.single().second)
    }
}
