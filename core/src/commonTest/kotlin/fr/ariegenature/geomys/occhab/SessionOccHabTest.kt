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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/OccHabViewModelTest.kt) :
// OccHabViewModel devient SessionOccHab ; Robolectric retiré, JUnit remplacé par kotlin.test, Gson par la
// relecture JSON de OccHabDetailsSession. Les trois cas de l'interrupteur « Afficher mes stations déjà sur
// GeoNature » ne sont pas repris : il est supprimé (décision du 19 octobre).

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.model.OccHabStation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SessionOccHab — détails PAR STATION (décision terrain 2026-08-27) : [SessionOccHab.details]
 * est le tampon d'édition de la station sélectionnée (chargé à la reprise, réinjecté par
 * stationAEnregistrer), les nouvelles stations partent des DÉFAUTS de session (formulaire de
 * démarrage) ; empreinte d'origine figée sur ce qui partirait ; correction manuelle des
 * altitudes / surface = modification réelle malgré leur exclusion de l'empreinte.
 */
class SessionOccHabTest {

    private fun session() = SessionOccHab().apply {
        demarrerSession(
            OccHabDetailsSession(
                idDataset = 12, nomDataset = "JDD 12",
                observateursIds = listOf(1), observateursNoms = listOf("MOI"),
                dateMin = 1_000L, dateMax = 2_000L,
                idNomCalculSurface = 5, idNomObjetGeographique = 6, comment = "session",
            ),
        )
    }

    private fun stationServeur() = OccHabStation(
        idStationServeur = 42, origineServeur = true,
        geometryType = "Point", latitude = 42.9, longitude = 1.4,
        idDataset = 12, observateursIds = listOf(7, 8), observateursNoms = listOf("DUPONT jean", "MARTIN paul"),
        observateursTxt = "DUPONT jean", dateMin = 500L, dateMax = 600L,
        idNomCalculSurface = 22, idNomObjetGeographique = 23, comment = "Pelouse pâturée",
        altitudeMin = 800, altitudeMax = 850, surface = 1234L,
    )

    @Test
    fun station_reprise_le_tampon_reflete_SES_details_dates_comprises() {
        val vm = session()
        vm.reprendreStation(stationServeur())
        val d = vm.details
        assertEquals(listOf(7, 8), d.observateursIds)
        assertEquals("DUPONT jean", d.observateursTxt)
        assertEquals("Pelouse pâturée", d.comment)
        assertEquals(22, d.idNomCalculSurface)
        assertEquals(23, d.idNomObjetGeographique)
        assertEquals("JDD 12", d.nomDataset)
        assertEquals(500L, d.dateMin, "ses propres dates (celles de session ne sont posées qu'à l'IMPORT, carte)")
        assertEquals(600L, d.dateMax)
        // Ce qui partirait = la station elle-même.
        val s = vm.stationAEnregistrer()
        assertEquals(listOf(7, 8), s.observateursIds)
        assertEquals("Pelouse pâturée", s.comment)
        assertEquals(500L, s.dateMin)
    }

    @Test
    fun modifier_une_station_ne_touche_ni_les_defauts_ni_les_autres_stations() {
        val vm = session()
        vm.reprendreStation(stationServeur().copy(origineServeur = false, idStationServeur = null))
        vm.majDetails {
            it.comment = "revu"
            it.observateursIds = listOf(9)
        }
        assertEquals("revu", vm.stationAEnregistrer().comment)
        // Nouvelle station → défauts de session intacts.
        vm.nouvelleStation()
        assertEquals("session", vm.details.comment)
        assertEquals(listOf(1), vm.details.observateursIds)
        assertEquals(1_000L, vm.details.dateMin)
        assertEquals("session", vm.stationAEnregistrer().comment)
    }

    @Test
    fun formulaire_de_demarrage_redefinit_les_defauts_des_nouvelles_stations() {
        val vm = session()
        vm.majDetails(demarrage = true) {
            it.observateursIds = listOf(3)
            it.idDataset = 15
        }
        assertEquals(listOf(3), vm.defautsSession.observateursIds)
        assertEquals(15, vm.defautsSession.idDataset)
        vm.nouvelleStation()
        assertEquals(listOf(3), vm.details.observateursIds)
    }

    @Test
    fun empreinte_origine_figee_sur_ce_qui_partirait_puis_levee_par_une_correction_manuelle() {
        val vm = session()
        vm.reprendreStation(stationServeur())
        vm.figerEmpreinteOrigine()
        assertEquals(vm.stationAEnregistrer().empreinteContenu(), vm.station.empreinteOrigine)
        val avant = vm.deriveesManuelles()
        vm.forcerPersistanceSiDeriveesChangees(avant)
        assertNotNull(vm.station.empreinteOrigine, "rien n'a changé → empreinte conservée")
        vm.definirAltitudes(800, 900)
        vm.forcerPersistanceSiDeriveesChangees(avant)
        assertNull(vm.station.empreinteOrigine, "altitude corrigée à la main = modification réelle")
    }

    /**
     * Rééditer un relevé qui ne contient QU'UNE station doit ouvrir la carte sur cette station,
     * sélectionnée et cadrée — pas sur une carte sans objet en cours (demande utilisateur
     * 2026-10-18). C'est la station reprise dans la session qui le déclenche : la carte charge
     * sa géométrie à l'ouverture et cadre dessus.
     */
    @Test
    fun reediter_un_releve_a_une_seule_station_la_selectionne() {
        val unique = stationServeur()
        val vm = SessionOccHab().apply { reprendreSaisie(OccHabSaisie(stations = listOf(unique))) }
        assertEquals(unique.id, vm.station.id, "la station unique doit être reprise")
        assertTrue(vm.station.geometrieDefinie(), "donc sa géométrie est définie — la carte l'ouvrira sélectionnée")
    }

    /** CONTRE-ÉPREUVE : à PLUSIEURS stations, aucune ne s'impose — on repart d'une station
     *  vierge et la carte les montre toutes. */
    @Test
    fun reediter_un_releve_a_plusieurs_stations_ne_selectionne_rien() {
        val vm = SessionOccHab().apply { reprendreSaisie(OccHabSaisie(stations = listOf(stationServeur(), stationServeur()))) }
        assertFalse(vm.station.geometrieDefinie(), "aucune station sélectionnée")
    }
}
