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

// Propre au portage : sous GeoMys-Android, ces règles vivaient dans SaisieObservationFragment, sans test.
// Les attendus sont ceux de son code @ cdd5196, sauf la date des détails communs (défaut n° 4 de
// l'inventaire, corrigé).

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Taxon
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** [ReleveEnCours] : reprise d'un relevé, valeurs de départ, enregistrement au fil de l'eau. */
class ReleveEnCoursTest {

    private val t = ServicesDeTest()
    private val config get() = t.services.config

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun TestScope.session() = SessionSaisieOcctax(t.services.sortieStore, t.services.locationTracker, backgroundScope)

    private val polygone = GeometrieFinale("Polygon", "[[1.0,42.0],[1.1,42.0],[1.1,42.1]]", 42.03, 1.07)

    @Test
    fun un_nouveau_releve_enregistre_ses_especes_au_fil_de_l_eau() = runTest {
        // Le jeu par défaut des Paramètres, choisi parmi les jeux proposés.
        config.datasetsCacheJson = """[{"id":12,"nom":"JDD","moduleCodes":["OCCTAX"]}]"""
        config.datasetsCreablesOcctax = setOf(12)
        config.idDataset = "12"
        config.nomUtilisateur = "MARTIN Alice"
        val session = session()
        val releve = ReleveEnCours(session, config, OuvertureReleve(geometrie = polygone), dernierTaxon = Taxon.PLANTE)
        assertEquals(Taxon.PLANTE, releve.taxonInitial)
        releve.ajouter(Taxon.OISEAU, "Merle noir", 4117)
        releve.ajouter(Taxon.OISEAU, "Rougegorge familier", 4001)
        val obs = session.observations.value
        assertEquals(listOf("Merle noir", "Rougegorge familier"), obs.map { it.espece })
        assertEquals(setOf(releve.releveId), obs.map { it.releveId }.toSet())
        assertTrue(obs.all { it.geometryType == "Polygon" && it.latitude == 42.03 && it.longitude == 1.07 })
        // Déterminateur : le nom complet de l'utilisateur connecté, comme le web.
        assertTrue(obs.all { it.determinateur == "MARTIN Alice" && it.nombre == 1 })
        // Jeu de données figé au premier enregistrement : le défaut de la config.
        assertTrue(obs.all { it.idDatasetReleve == 12 })
        // Les espèces gardent leur identité d'un enregistrement à l'autre.
        val ids = obs.map { it.id }
        releve.supprimer(0)
        assertEquals(listOf(ids[1]), session.observations.value.map { it.id })
    }

    /** Session vide (appli rouverte après la mort de son processus) : le relevé à reprendre est
     *  introuvable, et rien ne s'enregistre ; un nouveau relevé ne l'est jamais. */
    @Test
    fun un_releve_a_reprendre_absent_de_la_session_est_introuvable() = runTest {
        val session = session()
        assertTrue(ReleveEnCours(session, config, OuvertureReleve(releveId = "R1"), dernierTaxon = null).introuvable)
        assertTrue(ReleveEnCours(session, config, OuvertureReleve(obsId = "o1"), dernierTaxon = null).introuvable)
        assertFalse(ReleveEnCours(session, config, OuvertureReleve(geometrie = polygone), dernierTaxon = null).introuvable)
        assertTrue(session.observations.value.isEmpty())
    }

    @Test
    fun un_releve_repris_garde_ses_observations_leur_date_et_leur_position() = runTest {
        val session = session()
        session.ajouterObservation(
            Observation(id = "o1", espece = "Merle noir", taxon = Taxon.OISEAU, cdNom = 4117, latitude = 42.9, longitude = 1.4,
                date = 1_700_000_000_000L, releveId = "R1", idDatasetReleve = 7, commentReleve = "lisière"),
        )
        session.ajouterObservation(
            Observation(id = "autre", espece = "Pic vert", taxon = Taxon.OISEAU, cdNom = 3611, latitude = 43.0, longitude = 1.5, releveId = "R2"),
        )
        val releve = ReleveEnCours(session, config, OuvertureReleve(releveId = "R1"), dernierTaxon = Taxon.PLANTE)
        assertEquals("R1", releve.releveId)
        assertEquals(Taxon.OISEAU, releve.taxonInitial, "le groupe de la première espèce")
        assertEquals("lisière", releve.commentaire)
        // Date jamais fixée à la main : celle de la création.
        assertEquals(1_700_000_000_000L, releve.dateDebut)
        releve.modifier(0) { it.copy(nombre = 3, notes = "chant") }
        val o1 = session.observations.value.single { it.id == "o1" }
        assertEquals(3, o1.nombre)
        assertEquals("chant", o1.notes)
        assertEquals(1_700_000_000_000L, o1.date)
        assertEquals(42.9, o1.latitude)
        assertEquals(7, o1.idDatasetReleve)
        assertEquals("R2", session.observations.value.single { it.id == "autre" }.releveId)
    }

    @Test
    fun terminer_sans_espece_enregistre_un_releve_d_absence() = runTest {
        val session = session()
        val releve = ReleveEnCours(session, config, OuvertureReleve(geometrie = GeometrieFinale("Point", null, 42.5, 1.2)), null)
        val message = releve.terminer()
        assertEquals("Relevé sans espèce enregistré — retrouvez-le dans « Mes saisies »", message)
        val substitut = session.observations.value.single()
        assertEquals(ReleveEnCours.ESPECE_SANS_ESPECE, substitut.espece)
        assertTrue(substitut.releveSansEspece)
        assertNull(substitut.taxon)
        assertNull(substitut.cdNom)
        assertEquals(0, substitut.nombre)
        assertEquals(releve.releveId, substitut.releveId)
        assertEquals(42.5, substitut.latitude)
    }

    @Test
    fun terminer_avec_des_especes_les_compte() = runTest {
        val session = session()
        val releve = ReleveEnCours(session, config, OuvertureReleve(geometrie = polygone), null)
        releve.ajouter(Taxon.OISEAU, "Merle noir", 4117)
        assertEquals("Relevé enregistré (1 espèce) — retrouvez-le dans « Mes saisies »", releve.terminer())
        releve.ajouter(Taxon.OISEAU, "Pic vert", 3611)
        assertEquals("Relevé enregistré (2 espèces) — retrouvez-le dans « Mes saisies »", releve.terminer())
    }

    @Test
    fun un_nouveau_releve_herite_de_la_saisie() = runTest {
        val session = session()
        // Un relevé déjà enregistré fixe le jeu de données de la saisie.
        session.ajouterObservation(Observation(id = "a", espece = "Merle noir", latitude = 0.0, longitude = 0.0, releveId = "R0", idDatasetReleve = 5))
        session.definirDateAPosteriori(1_600_000_000_000L, 1_600_000_360_000L)
        val sansCommuns = ReleveEnCours(session, config, OuvertureReleve(geometrie = polygone), null)
        assertEquals(5, sansCommuns.idDataset)
        assertEquals(1_600_000_000_000L, sansCommuns.dateDebut)
        // Les détails communs priment, sauf sur les champs additionnels de l'écran intercalé.
        session.definirDetailsCommuns(
            DetailsReleveResult(idDataset = 9, idsObservateurs = listOf(3), nomsObservateurs = listOf("Bob"), additionnels = mapOf("a" to "1"), comment = "commun"),
        )
        val avecCommuns = ReleveEnCours(session, config, OuvertureReleve(geometrie = polygone, additionnelsIntercales = mapOf("b" to "2")), null)
        assertEquals(9, avecCommuns.idDataset)
        assertEquals(listOf("Bob"), avecCommuns.nomsObservateurs)
        assertEquals("commun", avecCommuns.commentaire)
        assertEquals(mapOf("b" to "2"), avecCommuns.additionnelsReleve)
        assertEquals(1_600_000_000_000L, avecCommuns.dateDebut, "la date a posteriori n'est pas écrasée par les détails communs")
    }

    /** Défaut n° 4 de l'inventaire corrigé : la date des détails communs n'est retenue que
     *  modifiée, et ne remplace jamais une date a posteriori plus récente. */
    @Test
    fun la_date_des_details_communs_n_est_retenue_que_modifiee() = runTest {
        val session = session()
        fun communs(date: Long, modifiee: Boolean) = DetailsReleveResult(
            idDataset = 9, idsObservateurs = emptyList(), nomsObservateurs = emptyList(), additionnels = emptyMap(),
            dateDebut = date, dateFin = date, dateModifiee = modifiee,
        )
        fun nouveau() = ReleveEnCours(session, config, OuvertureReleve(geometrie = polygone), null)
        // Validés sans toucher à la date : l'heure d'ouverture du dialogue n'est pas retenue.
        session.definirDetailsCommuns(communs(1_000L, modifiee = false))
        assertNull(session.detailsCommuns?.dateDebut)
        assertNull(nouveau().dateDebut, "le relevé garde sa propre date")
        // Date commune modifiée : la saisie devient a posteriori (ce que fait la carte), les relevés
        // en héritent.
        session.definirDetailsCommuns(communs(2_000L, modifiee = true))
        session.definirDateAPosteriori(2_000L, 2_000L)
        assertEquals(2_000L, nouveau().dateDebut)
        // Une date a posteriori armée ensuite (« Détails » d'un relevé) l'emporte.
        session.definirDateAPosteriori(3_000L, 3_000L)
        assertEquals(3_000L, nouveau().dateDebut)
        // Rouverts et validés sans toucher à la date : la date commune retenue reste affichée.
        session.definirDetailsCommuns(communs(9_999L, modifiee = false))
        assertEquals(2_000L, session.detailsCommuns?.dateDebut)
    }

    /** Les détails validés s'enregistrent aussitôt : sous GeoMys-Android, ils attendaient la
     *  modification suivante du relevé, et un retour juste après les perdait. */
    @Test
    fun les_details_du_releve_sont_enregistres_des_leur_validation() = runTest {
        val session = session()
        val releve = ReleveEnCours(session, config, OuvertureReleve(geometrie = polygone), null)
        releve.ajouter(Taxon.OISEAU, "Merle noir", 4117)
        releve.appliquerDetails(DetailsReleveResult(idDataset = 4, idsObservateurs = listOf(3), nomsObservateurs = listOf("Bob"), additionnels = emptyMap(), comment = "haie", dateDebut = 1L, dateModifiee = true))
        val obs = session.observations.value.single()
        assertEquals("haie", obs.commentReleve)
        assertEquals(4, obs.idDatasetReleve)
        assertEquals(listOf("Bob"), obs.observateursReleveNoms)
        assertTrue(session.saisieAPosteriori)
    }

    @Test
    fun le_denombrement_numero_zero_revient_dans_les_champs_a_plat() {
        val espece = EspeceReleve(Taxon.OISEAU, "Merle noir", 4117)
        val retour = espece.avecDenombrements(
            listOf(
                Denombrement(nombreMin = 2, nombreMax = 2, sexe = "M"),
                Denombrement(nombreMin = 3, nombreMax = 5),
            ),
        )
        assertEquals(2, retour.nombre)
        assertNull(retour.nombreMax, "un maximum égal au minimum n'est pas gardé")
        assertEquals("M", retour.sexe)
        assertEquals(1, retour.denombrementsAdditionnels.size)
        assertEquals(5, retour.total)
        assertEquals(retour, retour.avecDenombrements(emptyList()))
        assertEquals(listOf(2, 3), retour.denombrements().map { it.nombreMin })
    }

    @Test
    fun la_date_reste_a_choisir_quand_le_serveur_la_veut_vide() = runTest {
        // `OCCTAX.DATE_FORM_WITH_TODAY` à faux (un serveur essayé le 21 octobre) : le relevé s'ouvre sur ses
        // détails et ne se termine pas sans date.
        val session = session()
        assertFalse(ReleveEnCours(session, config, OuvertureReleve(geometrie = polygone), null).dateAChoisir(), "par défaut, la date du jour")
        config.occtaxDateDuJour = false
        val releve = ReleveEnCours(session, config, OuvertureReleve(geometrie = polygone), null)
        assertTrue(releve.dateAChoisir())
        releve.appliquerDetails(DetailsReleveResult(idDataset = 12, idsObservateurs = emptyList(), nomsObservateurs = emptyList(), additionnels = emptyMap(), dateDebut = 1_792_400_000_000L))
        assertFalse(releve.dateAChoisir(), "posée par « Détails »")
    }
}
