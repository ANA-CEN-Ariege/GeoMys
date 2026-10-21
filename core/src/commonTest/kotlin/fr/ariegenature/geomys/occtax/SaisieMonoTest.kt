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

// Sous GeoMys-Android @ d72b8da, ces règles vivaient dans SaisieRapideFragment, sans test ; le garde de
// parité mono/multi (CaracterisationPariteMonoMultiTest, cad466b) lisait le texte des deux fragments. Ici, il
// vérifie les observations elles-mêmes.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.store.OcctaxFieldsConfig
import fr.ariegenature.geomys.store.io.listeVersJson
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** [SaisieMono] : l'observation de chaque « + », les détails de la série, la parité avec le
 *  relevé multi-taxons. */
class SaisieMonoTest {

    private val t = ServicesDeTest()
    private val config get() = t.services.config

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun TestScope.session() = SessionSaisieOcctax(t.services.sortieStore, t.services.locationTracker, backgroundScope)

    /** La valeur d'un champ de caractérisation dans l'observation envoyée. */
    private fun valeur(obs: Observation, champ: OcctaxFieldsConfig.OcctaxField): String? =
        if (champ.formFieldKey.isNotEmpty()) obs.champsOccExtra[champ.formFieldKey]
        else when (champ.svKey) {
            "statutObs" -> obs.statutObs
            "techniqueObs" -> obs.techniqueObs
            "etaBio" -> obs.etaBio
            "comportement" -> obs.comportement
            "statutBio" -> obs.statutBio
            "methDetermin" -> obs.methDetermin
            "naturalite" -> obs.naturalite
            "preuveExist" -> obs.preuveExist
            else -> throw AssertionError("champ d'occurrence inconnu du test : ${champ.svKey}")
        }

    @Test
    fun chaque_champ_de_la_caracterisation_part_avec_les_deux_saisies() = runTest {
        val champs = OcctaxFieldsConfig.REGISTRE.filter { it.niveau == OcctaxFieldsConfig.Niveau.INFORMATION }
        assertTrue(champs.size >= 10, "registre vide : le test ne garde plus rien")
        val valeurs = champs.associate { it.code to "v-${it.code}" }
        val caracteriser = { e: EspeceReleve ->
            FormulairesEspece.appliquerCaracterisation(e, valeurs, "Alice", "lisière", mapOf("digital_proof" to "https://ex.org/p.jpg"), mapOf("a" to "1"))
        }

        val session = session()
        val mono = SaisieMono(session, config, Taxon.OISEAU)
        mono.demarrer("Merle noir", 4117)
        mono.modifier(caracteriser)
        val obsMono = mono.observation(42.9, 1.6)

        val releve = ReleveEnCours(session, config, OuvertureReleve(geometrie = GeometrieFinale("Point", null, 42.9, 1.6)), Taxon.OISEAU)
        val index = releve.ajouter(Taxon.OISEAU, "Merle noir", 4117)
        releve.modifier(index, caracteriser)
        val obsMulti = session.observations.value.single { it.releveId == releve.releveId }

        for (champ in champs) {
            assertEquals("v-${champ.code}", valeur(obsMono, champ), "mono : « ${champ.label} » perdu")
            assertEquals("v-${champ.code}", valeur(obsMulti, champ), "multi : « ${champ.label} » perdu")
        }
        for (obs in listOf(obsMono, obsMulti)) {
            assertEquals("Alice", obs.determinateur)
            assertEquals("lisière", obs.notes)
            assertEquals("https://ex.org/p.jpg", obs.champsOccExtra["digital_proof"])
            assertEquals(mapOf("a" to "1"), obs.additionalFieldsOccurrence)
        }
    }

    @Test
    fun chaque_plus_enregistre_une_copie_du_modele_relevee_a_part() = runTest {
        // Le jeu par défaut des Paramètres, choisi parmi les jeux proposés.
        config.datasetsCacheJson = """[{"id":12,"nom":"JDD","moduleCodes":["OCCTAX"]}]"""
        config.datasetsCreablesOcctax = setOf(12)
        config.idDataset = "12"
        val session = session()
        val mono = SaisieMono(session, config, Taxon.MAMMIFERE)
        mono.demarrer("Renard roux", 60585)
        mono.modifier { it.avecDenombrements(listOf(Denombrement(nombreMin = 2, nombreMax = 3, sexe = "M"), Denombrement(nombreMin = 1))) }
        val a = mono.enregistrer(42.9, 1.6)
        val b = mono.enregistrer(43.0, 1.7)
        assertEquals(listOf(a.id, b.id), session.observations.value.map { it.id })
        assertTrue(a.id != b.id)
        for (obs in listOf(a, b)) {
            assertNull(obs.releveId, "mono : chaque observation est son propre relevé")
            assertEquals("Renard roux", obs.espece)
            assertEquals(Taxon.MAMMIFERE, obs.taxon)
            assertEquals(60585, obs.cdNom)
            assertEquals(2, obs.nombre)
            assertEquals(3, obs.nombreMax)
            assertEquals("M", obs.sexe)
            assertEquals(1, obs.denombrementsAdditionnels.size)
            assertEquals(12, obs.idDatasetReleve, "jeu de données des paramètres")
            assertNull(obs.determinateur, "sans passage par la caractérisation, pas de déterminateur (comme Android)")
        }
        assertEquals(43.0 to 1.7, b.latitude to b.longitude)
        // Total de tous les dénombrements (2 + 1), défaut n° 5 de l'inventaire corrigé : sous
        // GeoMys-Android, le résumé et la confirmation n'affichaient que le n° 0 (« × 2 »).
        assertEquals("Renard roux × 3", mono.resume())
        assertEquals("M", mono.resumeDetails())
        assertEquals("M", mono.indicateurDetails())
        assertEquals("2 obs. enregistrées", SaisieMono.compteur(2))
        assertEquals("1 obs. enregistrée", SaisieMono.compteur(1))
        assertEquals("Renard roux × 3", SaisieMono.confirmation(a))
    }

    @Test
    fun sans_espece_le_nom_du_groupe_et_sans_details_les_defauts_du_serveur() = runTest {
        config.additionalFieldsOcctaxJson = listeVersJson(
            AdditionalFieldDef.serializer(),
            listOf(AdditionalFieldDef(1, "meteo", "Météo", WidgetType.TEXT, defaultValue = "Soleil", objectsCode = listOf(AdditionalFieldsObject.RELEVE))),
        )
        val session = session()
        val mono = SaisieMono(session, config, Taxon.INVERTEBRES)
        val obs = mono.observation(42.9, 1.6)
        assertEquals("Autre invertébré", obs.espece)
        assertEquals(mapOf("meteo" to "Soleil"), obs.additionalFieldsReleve)
        assertNull(mono.indicateurDetails())
        // Les champs saisis dans l'écran intercalé l'emportent, pour toute la série.
        val intercalee = SaisieMono(session, config, Taxon.OISEAU, mapOf("meteo" to "Pluie"))
        assertEquals(mapOf("meteo" to "Pluie"), intercalee.observation(42.9, 1.6).additionalFieldsReleve)
    }

    @Test
    fun les_details_valides_valent_pour_toute_la_serie_sans_saisie_a_posteriori() = runTest {
        val session = session()
        val mono = SaisieMono(session, config, Taxon.OISEAU)
        mono.appliquerDetails(
            DetailsReleveResult(
                idDataset = 4, idsObservateurs = listOf(7), nomsObservateurs = listOf("Alice"), additionnels = mapOf("vent" to "Fort"),
                comment = "lisière", cdHab = 9, habitatLabel = "Pelouses", typGrp = "OBS", dateDebut = 1L, dateFin = 2L,
                champsExtra = mapOf("place_name" to "Col"), dateModifiee = true,
            ),
        )
        assertTrue(!session.saisieAPosteriori, "la saisie mono ne passe pas en saisie a posteriori")
        val obs = mono.observation(42.9, 1.6)
        assertEquals(4, obs.idDatasetReleve)
        assertEquals(listOf(7), obs.observateursReleveIds)
        assertEquals(mapOf("vent" to "Fort"), obs.additionalFieldsReleve)
        assertEquals("lisière", obs.commentReleve)
        assertEquals(9, obs.cdHabReleve)
        assertEquals("OBS", obs.typGrpReleve)
        assertEquals(1L to 2L, obs.dateDebutReleve to obs.dateFinReleve)
        assertEquals(mapOf("place_name" to "Col"), obs.champsReleveExtra)
        val donnees = mono.donneesDetails()
        assertEquals(4, donnees.idDataset)
        assertEquals("lisière", donnees.commentaire)
        assertEquals(1L, donnees.dateDebut)
    }
}
