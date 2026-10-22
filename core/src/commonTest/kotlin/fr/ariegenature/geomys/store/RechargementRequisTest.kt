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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/RechargementRequisTest.kt) :
// Robolectric remplacé par ServicesDeTest, les fonctions de CachesSynchronises par sa classe,
// configurationComplete passée au socle, JUnit par kotlin.test.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Une mise à jour de l'application dont la synchro lit des données nouvelles
 * ([GeoNatureConfig.VERSION_DONNEES_REQUISE] au-dessus de la version chargée). Depuis le 21 octobre
 * 2026, elle ne bloque plus rien et ne purge rien, comme sous GeoMys-Android, où une réinstallation
 * gardait tout sans rien demander : les données en place servent, Paramètres suggère de recharger, et
 * la synchro suivante remet la version à jour (PurgeRechargementApresMajTest).
 */
class RechargementRequisTest {

    private val t = ServicesDeTest()
    private val s get() = t.services
    private val cfg get() = s.config

    @AfterTest
    fun nettoyer() = t.nettoyer()

    /** Une installation configurée et chargée par une version précédente de l'application. */
    private fun installationEnRetard() {
        cfg.apply {
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
            versionDonneesChargees = GeoNatureConfig.VERSION_DONNEES_REQUISE - 1
        }
        s.taxRef.remplacerTout(mapOf(TaxRefCache.normaliser("Machaon") to TaxRefEntry(54468, "Papilio machaon", "Machaon")))
        s.taxRef.versionSauvegardee = "taxref-v17"
        s.taxRef.listesSynchronisees = listOf(100)
    }

    @Test
    fun une_mise_a_jour_ne_bloque_rien_ni_ne_purge() {
        installationEnRetard()
        assertTrue(configurationComplete(cfg, s.taxRef), "l'application reste utilisable, sans passer par Paramètres")
        assertEquals("taxref-v17", s.taxRef.versionSauvegardee, "rien n'est purgé")
        assertEquals(54468, s.taxRef.get(TaxRefCache.normaliser("Machaon"))?.cdNom)
        val bandeau = assertNotNull(texteBandeauRechargement(cfg, s.taxRef), "Paramètres suggère de recharger")
        assertTrue(bandeau.contains("L'application reste utilisable"))
    }

    @Test
    fun installation_vierge_ou_a_jour_sans_bandeau() {
        assertNull(texteBandeauRechargement(cfg, s.taxRef), "rien de chargé : la première configuration suit son cours")
        installationEnRetard()
        cfg.versionDonneesChargees = GeoNatureConfig.VERSION_DONNEES_REQUISE
        assertNull(texteBandeauRechargement(cfg, s.taxRef))
    }

    @Test
    fun purge_des_caches_synchronises_vide_taxref() {
        s.taxRef.versionSauvegardee = "taxref-v17"
        s.cachesSynchronises.viderCachesSynchronises()
        assertNull(s.taxRef.versionSauvegardee)
    }
}
