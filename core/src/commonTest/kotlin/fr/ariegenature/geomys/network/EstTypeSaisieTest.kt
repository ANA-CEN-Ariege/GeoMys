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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/EstTypeSaisieTest.kt) :
// JUnit remplacé par kotlin.test ; estObjetDeNiveauVisite, fonction du paquet ui d'Android, est dans
// monitoring/NouvelleVisite (lot 7).

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.monitoring.NouvelleVisite.estObjetDeNiveauVisite
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Heuristique de nommage qui décide si un object_type est une « saisie » (visite, obs…)
 *  — pilote l'affichage des boutons « + Nouveau X » et le pré-chargement offline. */
class EstTypeSaisieTest {

    @Test
    fun types_de_saisie_reconnus() {
        listOf(
            "visite", "visites", "visit", "visits",
            "observation", "observations", "obs",
            "occurrence", "occurrences",
            "releve", "releves", "relevé", "relevés",
            "denombrement", "dénombrements",
        ).forEach { assertTrue(MonitoringSync.estTypeSaisie(it), "'$it' devrait être une saisie") }
    }

    @Test
    fun insensible_a_la_casse_et_aux_espaces() {
        assertTrue(MonitoringSync.estTypeSaisie("  VISITE "))
        assertTrue(MonitoringSync.estTypeSaisie("Observation"))
    }

    @Test
    fun types_non_saisie_rejetes() {
        listOf("site", "sites_group", "station", "module", "", "point_ecoute")
            .forEach { assertFalse(MonitoringSync.estTypeSaisie(it), "'$it' ne devrait pas être une saisie") }
    }

    // ── Niveau d'un objet monitoring (décision terrain 2026-09-03) ─────────────────────
    // Objet de niveau VISITE = placé directement sous le protocole (visite, transect,
    // pelouse…). Pilote DEUX comportements qui doivent rester cohérents : l'enregistrement
    // incomplet (autorisé sur eux seuls — champs connus à la fin) et le compteur de
    // « Mes visites » (qui ne totalise qu'eux, pas les espèces).

    @Test
    fun objet_rattache_a_un_site_est_de_niveau_visite() {
        assertTrue(estObjetDeNiveauVisite("site"))
        assertTrue(estObjetDeNiveauVisite("sites_group"))
        // Sans parent connu (route ancienne) : on reste sur le cas courant = une visite.
        assertTrue(estObjetDeNiveauVisite(null))
        assertTrue(estObjetDeNiveauVisite(""))
        assertTrue(estObjetDeNiveauVisite("   "))
    }

    @Test
    fun saisie_d_espece_n_est_pas_de_niveau_visite() {
        // Parent = une saisie (la visite) ⇒ l'objet courant est une observation : ni
        // enregistrable incomplet, ni compté comme une visite.
        listOf("visite", "visit", "VISITE", " visites ", "releve", "occurrence")
            .forEach {
                assertFalse(estObjetDeNiveauVisite(it), "un objet enfant de « $it » n'est pas de niveau visite")
            }
    }
}
