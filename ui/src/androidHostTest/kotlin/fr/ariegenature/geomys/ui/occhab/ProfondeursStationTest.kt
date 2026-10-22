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

package fr.ariegenature.geomys.ui.occhab

import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.occhab.DetailsStation
import fr.ariegenature.geomys.occhab.OccHabDetailsSession
import fr.ariegenature.geomys.ui.ServicesEssai
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Les profondeurs d'une station derrière le « + » qui suit les altitudes, comme sur le web : le
 *  bouton suit `OCCHAB.formConfig`, le bloc est replié à chaque ouverture, et replié il garde les
 *  valeurs de la station. */
class ProfondeursStationTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services

    @AfterTest
    fun nettoyer() = essai.nettoyer()

    private fun etat(demarrage: Boolean = false) = EtatDetailsStation(
        demarrage = demarrage,
        details = OccHabDetailsSession(idDataset = 12, profondeurMin = 2, profondeurMax = 5),
        station = OccHabStation(),
        config = services.config,
        nomenclatures = services.nomenclatures,
    )

    @Test
    fun le_plus_ouvre_et_referme_les_profondeurs_repliees_a_l_ouverture() {
        val e = etat()
        assertTrue(e.profondeursProposees, "défaut du serveur : depth_min et depth_max montrés")
        assertFalse(e.profondeursDepliees, "replié à l'ouverture, même quand la station en porte")
        assertEquals(DetailsStation.AFFICHER_PROFONDEURS, e.descriptionProfondeurs)
        assertEquals("2" to "5", e.saisies().let { it.profondeurMin to it.profondeurMax }, "repliées, elles gardent leur valeur")

        e.basculerProfondeurs()
        assertTrue(e.profondeursDepliees)
        assertEquals(DetailsStation.MASQUER_PROFONDEURS, e.descriptionProfondeurs)
        e.profondeurMin = "3"

        e.basculerProfondeurs()
        assertFalse(e.profondeursDepliees, "le « + » referme, comme sur le web")
        assertEquals("3", e.saisies().profondeurMin)
    }

    @Test
    fun les_details_ne_sont_modifies_qu_une_fois_une_saisie_changee() {
        // Écran plein depuis le 21 octobre : le retour passe par la garde de sortie.
        val e = etat()
        assertFalse(e.modifie(), "intact")
        e.basculerProfondeurs()
        assertFalse(e.modifie(), "le « + » ne change aucune valeur")
        e.profondeurMin = "3"
        assertTrue(e.modifie())
        assertTrue(etat().apply { commentaire = "Pâturage" }.modifie())
        assertTrue(etat().apply { choisirDateMin(1_792_400_000_000L) }.modifie())
        assertTrue(etat(demarrage = true).apply { effacerDataset() }.modifie(), "aussi les informations obligatoires")
    }

    @Test
    fun le_plus_et_les_champs_suivent_la_config_du_serveur() {
        services.config.occhabFormConfigJson = """{"depth_min": true, "depth_max": false}"""
        val une = etat()
        assertTrue(une.profondeursProposees)
        assertTrue(une.profondeurMinVisible)
        assertFalse(une.profondeurMaxVisible)
        assertNull(une.saisies().profondeurMax, "masquée : la valeur de la station est gardée")

        services.config.occhabFormConfigJson = """{"depth_min": false, "depth_max": false}"""
        val aucune = etat()
        assertFalse(aucune.profondeursProposees, "ni champ ni « + », comme sur le web")
        assertNull(aucune.saisies().profondeurMin)

        services.config.occhabFormConfigJson = ""
        assertFalse(etat(demarrage = true).profondeursProposees, "pas dans les informations obligatoires")
    }
}
