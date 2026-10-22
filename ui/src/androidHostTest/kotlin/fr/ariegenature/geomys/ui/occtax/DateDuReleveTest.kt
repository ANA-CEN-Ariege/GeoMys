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

package fr.ariegenature.geomys.ui.occtax

import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.occtax.DonneesDetailsReleve
import fr.ariegenature.geomys.ui.ServicesEssai
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.MainScope

/** La date d'un relevé quand le serveur la veut vide au départ (`OCCTAX.DATE_FORM_WITH_TODAY` à faux,
 *  comme sur un serveur essayé le 21 octobre) : à choisir, avec la barre rouge, et exigée pour valider. */
class DateDuReleveTest {

    private val essai = ServicesEssai()

    @AfterTest
    fun nettoyer() = essai.nettoyer()

    private fun etat(dateDuJour: Boolean, dateDebut: Long? = null) = EtatDetailsReleve(
        DonneesDetailsReleve(
            titre = "Détails du relevé",
            datasets = listOf(7 to "Jeu 7"), idDataset = 7, nomDataset = null,
            observateurs = listOf(3 to "MARTIN Alice"), idsObservateurs = listOf(3), nomsObservateurs = listOf("MARTIN Alice"),
            defsReleve = emptyList(), additionnels = emptyMap(), formFieldsJson = "", typGrp = "", commentaire = "",
            dateDebut = dateDebut, dateFin = null, heures = false, avecFin = true, cdHab = null, habitatLabel = null,
            champsExtra = emptyMap(), dateDuJour = dateDuJour,
        ),
        essai.services.nomenclatures,
        MainScope(),
    ) { emptyList() }

    @Test
    fun sans_la_date_du_jour_elle_reste_a_choisir_et_s_exige() {
        val e = etat(dateDuJour = false)
        assertNull(e.debut)
        assertTrue(e.dateManquante, "la barre rouge")
        assertEquals(null to DetailsReleveFormulaire.CHOISIR_DATE_DEBUT, e.valider())
        e.changerDebut(JOUR)
        assertFalse(e.dateManquante)
        assertEquals(JOUR to JOUR, e.valider().first?.let { it.dateDebut to it.dateFin }, "la fin suit le début")
    }

    @Test
    fun par_defaut_la_date_du_jour_et_un_releve_date_garde_la_sienne() {
        val e = etat(dateDuJour = true)
        assertNotNull(e.debut)
        assertFalse(e.dateManquante)
        assertEquals(JOUR, etat(dateDuJour = false, dateDebut = JOUR).debut)
    }

    private companion object {
        const val JOUR = 1_792_400_000_000L
    }
}
