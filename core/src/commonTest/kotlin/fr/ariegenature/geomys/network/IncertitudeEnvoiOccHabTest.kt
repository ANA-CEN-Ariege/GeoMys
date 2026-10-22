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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/DefautsPariteLot2Test.kt, les
// trois cas d'A12) : Robolectric remplacé par ServicesDeTest, JUnit par kotlin.test. La garde qui lisait le
// texte d'EnvoiOccHab (son catch non typé appelle marquerStationErreurSansConclure) devient un envoi
// actionné, dont le POST lève une exception non typée.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.OccHabStation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * A12 — un échec survenu AVANT l'émission (login impossible hors ligne, connexion refusée)
 * effaçait l'incertitude posée par un envoi précédent resté sans réponse. L'essai suivant
 * re-POSTait donc sans vérifier par uuid, et le module OccHab n'ayant aucune contrainte d'unicité,
 * une seconde station apparaissait sur GeoNature.
 */
class IncertitudeEnvoiOccHabTest {

    private val t = ServicesDeTest()
    private val store get() = t.services.occHabStore

    @AfterTest
    fun nettoyer() = t.nettoyer()

    /** Une station dont un envoi précédent est resté sans réponse ; `upsertStation` crée la saisie à
     *  la première station (chemin réel de la saisie OccHab). Rend (saisie, station). */
    private suspend fun stationIncertaine(): Pair<String, String> {
        val station = OccHabStation(envoiIncertain = true)
        store.upsertStation("saisie-test", station)
        return "saisie-test" to station.id
    }

    private suspend fun station(ids: Pair<String, String>) = store.stationsDeSaisie(ids.first).first { it.id == ids.second }

    @Test
    fun un_echec_avant_emission_ne_leve_pas_l_incertitude() = runTest {
        val ids = stationIncertaine()
        store.marquerStationErreurSansConclure(ids.first, ids.second, "Pas de réseau")
        val s = station(ids)
        assertTrue(
            s.envoiIncertain,
            "l'incertitude d'un envoi précédent doit survivre : sans elle, l'essai suivant re-POSTe sans " +
                "vérifier par uuid et crée une seconde station",
        )
        assertEquals("Pas de réseau", s.derniereErreurEnvoi)
    }

    /** Contre-épreuve : un rejet FRANC du serveur (4xx) lève bien l'incertitude — sinon chaque envoi
     *  ultérieur paierait une vérification par uuid inutile. */
    @Test
    fun un_rejet_franc_leve_bien_l_incertitude() = runTest {
        val ids = stationIncertaine()
        store.marquerStationErreur(ids.first, ids.second, "400 Bad Request")
        assertFalse(station(ids).envoiIncertain)
    }

    @Test
    fun l_envoi_occhab_ne_conclut_rien_sur_une_exception_non_typee() = runTest {
        val ids = stationIncertaine()
        val saisie = store.charger().first { it.id == ids.first }
        val res = t.services.envoiOccHab.envoyerSaisieOccHabVersGeoNature(saisie, t.services.config) { _, _ ->
            throw IllegalStateException("Connexion refusée")
        }
        assertFalse(res.succes)
        assertTrue(
            station(ids).envoiIncertain,
            "une erreur AVANT émission ne dit rien de ce que le serveur a fait : l'incertitude reste",
        )
    }
}
