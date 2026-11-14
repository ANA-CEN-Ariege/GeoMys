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

// Propre au portage : l'accueil en saisie simplifiée (demande du 14 novembre 2026) n'a que « Démarrer une
// saisie » ; Monitoring et OccHab n'y sont plus, ni en mode pro tant que les données n'ont pas été
// rechargées pour eux.

package fr.ariegenature.geomys.ui.accueil

import fr.ariegenature.geomys.InfosApp
import fr.ariegenature.geomys.store.ModeSaisie
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.navigation.lireEtatAccueil
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccueilSimplifieTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services
    private val infos = InfosApp("GeoMys", "2.1.2", "android")

    @AfterTest
    fun nettoyer() = essai.nettoyer()

    private fun occhabOuvert() = services.config.apply {
        occhabDisponible = true
        occhabPeutCreer = true
    }

    @Test
    fun en_mode_pro_l_accueil_ne_change_pas() {
        occhabOuvert()
        val etat = lireEtatAccueil(infos, services)
        assertFalse(etat.simplifie)
        assertTrue(etat.occhabDisponible)
    }

    @Test
    fun en_saisie_simplifiee_ni_monitoring_ni_occhab() {
        occhabOuvert()
        services.preferences.modeSaisie = ModeSaisie.SIMPLIFIE
        val etat = lireEtatAccueil(infos, services)
        assertTrue(etat.simplifie)
        assertFalse(etat.occhabDisponible)
        assertFalse(etat.monitoringDisponible)
        assertEquals(
            listOf(EntreeMenuAccueil.MES_SAISIES, EntreeMenuAccueil.EXPLORER, EntreeMenuAccueil.MAPS_MANAGER),
            entreesMenu(etat).map { it.first },
        )
    }

    @Test
    fun en_mode_pro_occhab_attend_le_rechargement_des_donnees_simplifiees() {
        occhabOuvert()
        services.config.donneesSimplifiees = true
        assertFalse(lireEtatAccueil(infos, services).occhabDisponible)
        services.config.donneesSimplifiees = false
        assertTrue(lireEtatAccueil(infos, services).occhabDisponible)
    }

    /** Des visites ou des stations à envoyer restent atteignables, quel que soit le mode. */
    @Test
    fun ce_qui_reste_a_envoyer_garde_son_entree_du_menu() {
        services.preferences.modeSaisie = ModeSaisie.SIMPLIFIE
        val etat = lireEtatAccueil(infos, services).copy(visitesEnAttente = 2, stationsEnAttente = 1)
        assertEquals(
            listOf(
                EntreeMenuAccueil.MES_SAISIES to 0,
                EntreeMenuAccueil.MES_VISITES to 2,
                EntreeMenuAccueil.MES_STATIONS to 1,
                EntreeMenuAccueil.EXPLORER to 0,
                EntreeMenuAccueil.MAPS_MANAGER to 0,
            ),
            entreesMenu(etat),
        )
    }
}
