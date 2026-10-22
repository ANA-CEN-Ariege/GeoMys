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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/DatalistHorsLigneTest.kt) :
// le serveur éteint (MockWebServer arrêté) devient un ServeurSimule en panne, Robolectric
// MonitoringDeTest, les objets des instances, JUnit kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.network.ServeurSimule.Companion.panne
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

/**
 * Chargement des options d'un champ `datalist` SANS RÉSEAU — le chemin de l'écran « Nouvelle
 * visite » et de la complétion d'une visite.
 *
 * RÉGRESSION COUVERTE (audit 2026-09-14, R7-C2) : depuis le lot B3 (audit 2026-08-27),
 * `GeoNatureAuth.loginAvecCookies` LÈVE l'IOException hors-ligne au lieu de renvoyer null — `null`
 * y est désormais réservé au refus d'authentification. `chargerOptionsDatalist` ne traitait que le
 * `null` : l'exception traversait `enrichirAvecOptions` (un `awaitAll` d'`async`) puis remontait
 * jusqu'au `lifecycleScope.launch` de NouvelleVisiteFragment, sans aucun try/catch sur le chemin.
 * L'application PLANTAIT à l'ouverture d'une visite hors réseau — et donc aussi à la complétion
 * d'une visite enregistrée incomplète la veille. Le module Monitoring était inutilisable sur le
 * terrain, ce qui est précisément l'usage de cette application.
 */
class DatalistHorsLigneTest {

    // Toute requête échoue comme un téléphone hors couverture (une IOException).
    private val m = MonitoringDeTest(ServeurSimule().apply { router { panne() } })
    private val chemin = "monitorings/list/GENERIC/observer?id_list=3"

    @AfterTest
    fun nettoyer() = m.nettoyer()

    private fun propriete() = MonitoringApi.MonitoringPropertySchema(
        nom = "observers",
        typeWidget = "datalist",
        label = "Observateurs",
        obligatoire = false,
        apiUrl = chemin,
        keyLabel = "nom_complet",
        keyValue = "id_role",
    )

    @Test
    fun hors_ligne_le_cache_est_servi_et_AUCUNE_exception_ne_remonte() = runTest {
        m.cache.setJson(
            m.cache.keyOptionsDatalist(chemin),
            """[{"id_role":7,"nom_complet":"DUPONT jean"},{"id_role":8,"nom_complet":"MARTIN claire"}]""",
        )
        // Le test échoue par exception (et non par assertion) si la régression revient : c'est
        // exactement ce que voyait l'utilisateur, un crash.
        val options = m.api.chargerOptionsDatalist(m.config, propriete())
        assertNotNull(options, "hors-ligne, les options doivent venir du cache, pas planter")
        assertEquals(listOf("7", "8"), options.map { it.value })
        assertEquals(listOf("DUPONT jean", "MARTIN claire"), options.map { it.label })
    }

    @Test
    fun hors_ligne_sans_cache_renvoie_null_sans_planter() = runTest {
        // Aucune option en cache : le formulaire doit se rendre avec une liste vide (l'appelant
        // injecte alors la valeur sauvegardée en édition), surtout pas faire tomber l'écran.
        val options = m.api.chargerOptionsDatalist(m.config, propriete())
        assertEquals(null, options)
    }
}
