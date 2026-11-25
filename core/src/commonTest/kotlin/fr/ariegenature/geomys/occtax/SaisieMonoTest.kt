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
import fr.ariegenature.geomys.util.FormatDate
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
        // GeoMys-Android, le résumé n'affichait que le n° 0 (« × 2 »).
        assertEquals("Renard roux × 3", mono.resume())
        assertEquals("M", mono.indicateurDetails())
        assertEquals("2 obs. enregistrées", SaisieMono.compteur(2))
        assertEquals("1 obs. enregistrée", SaisieMono.compteur(1))
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

    /** 24 novembre 2026 : « Continuer la saisie » rouvre la carte sur le dernier « + » qui a une espèce
     *  de TaxRef. Une espèce ajoutée ensuite par l'écran de son relevé n'est pas un « + ». */
    @Test
    fun la_reprise_continue_l_espece_du_dernier_plus() {
        val merle = Observation(id = "a", espece = "Merle noir", taxon = Taxon.OISEAU, cdNom = 4117, latitude = 42.9, longitude = 1.6, date = 1_000L)
        val machaon = Observation(id = "b", espece = "Machaon", taxon = Taxon.INSECTE, cdNom = 54303, latitude = 42.9, longitude = 1.6, date = 3_000L)
        val rougegorge = Observation(id = "e", espece = "Rougegorge familier", taxon = Taxon.OISEAU, cdNom = 4001, latitude = 42.9, longitude = 1.6, date = 500L)
        val sansEspece = Observation(id = "c", espece = "Relevé sans espèce", latitude = 42.9, longitude = 1.6, date = 5_000L, releveSansEspece = true)
        val horsTaxRef = Observation(id = "d", espece = "Inconnu", latitude = 42.9, longitude = 1.6, date = 4_000L)
        assertEquals(machaon, SaisieMono.observationAReprendre(listOf(machaon, merle, sansEspece, horsTaxRef)), "la plus récente, en tête")
        assertEquals(machaon, SaisieMono.observationAReprendre(listOf(merle, machaon, sansEspece, horsTaxRef)), "dans l'ordre d'enregistrement")
        assertEquals(machaon, SaisieMono.observationAReprendre(listOf(merle, machaon, rougegorge)), "la plus récente, au milieu")
        assertEquals(merle, SaisieMono.observationAReprendre(listOf(merle)))
        assertNull(SaisieMono.observationAReprendre(listOf(sansEspece, horsTaxRef)), "rien à reprendre : les réglages")
        assertNull(SaisieMono.observationAReprendre(emptyList()))

        // Le Machaon modifié seul (son id pour relevé, § 78), puis un Pinson ajouté à son relevé : la
        // série reprend le Machaon, pas l'espèce ajoutée après lui.
        val machaonEdite = machaon.copy(releveId = machaon.id)
        val pinson = Observation(id = "f", espece = "Pinson des arbres", taxon = Taxon.OISEAU, cdNom = 3762, latitude = 42.9, longitude = 1.6, date = 9_000L, releveId = machaon.id)
        assertEquals(machaonEdite, SaisieMono.observationAReprendre(listOf(merle, machaonEdite, pinson)))
        // Le « + » mis à la corbeille dans son relevé : l'espèce restante est reprise.
        assertEquals(pinson, SaisieMono.observationAReprendre(listOf(merle, pinson)))
    }

    /** 24 novembre 2026 : les valeurs de la série (sa fiche) ne portent ni photo ni son, que chaque « + »
     *  recopierait ; elles partent avec la saisie au démarrage, et au ✓ de la fiche rouverte par le « i »
     *  de la carte (25 novembre 2026), et se reprennent telles quelles. */
    @Test
    fun les_valeurs_de_la_serie_sans_media_gardees_avec_la_saisie_et_reprises() = runTest {
        val session = session()
        val mono = SaisieMono(session, config, Taxon.INSECTE)
        mono.choisirEspece(Taxon.INSECTE, "Machaon", 54303)
        mono.modifier {
            it.copy(nombre = 3, sexe = "M", mediaUrisCounting0 = listOf("photo.jpg"), denombrementsAdditionnels = listOf(Denombrement(nombreMin = 1, mediaUris = listOf("son.m4a"))))
        }
        val m = mono.modele.value
        assertTrue(m.mediaUrisCounting0.isEmpty() && m.denombrementsAdditionnels.single().mediaUris.isEmpty())
        assertNull(session.modeleMono, "rien d'écrit avant le démarrage de la série")
        mono.demarrer("Machaon", 54303)
        val gardees = session.modeleMono
        assertEquals(Triple("Machaon", 3, "M"), Triple(gardees?.espece, gardees?.nombre, gardees?.sexe))
        val obs = mono.enregistrer(42.9, 1.6)
        assertEquals(4, obs.totalIndividus)
        assertTrue(obs.mediaUrisCounting0.isEmpty())

        // La fiche rouverte par le « i » de la carte (25 novembre 2026) : ses valeurs repartent avec la saisie
        // à son ✓ seulement, et l'observation déjà enregistrée garde les siennes.
        mono.modifier { it.copy(nombre = 5, sexe = "F") }
        assertEquals(3, session.modeleMono?.nombre, "pas avant le ✓")
        mono.garderAvecLaSaisie()
        assertEquals(Triple("Machaon", 5, "F"), session.modeleMono.let { Triple(it?.espece, it?.nombre, it?.sexe) })
        assertEquals(3, session.observations.value.single().nombre)
        assertEquals(5, mono.enregistrer(42.91, 1.61).nombre, "le « + » suivant")

        // « Continuer la saisie » : une nouvelle série reprend les valeurs gardées en dernier, celles du ✓
        // du « i » (sans média, même s'il y en avait).
        val reprise = SaisieMono(session, config, Taxon.OISEAU)
        reprise.reprendreModele(session.modeleMono!!.copy(mediaUrisCounting0 = listOf("autre.jpg")))
        val r = reprise.modele.value
        assertEquals(listOf<Any?>("Machaon", 54303, Taxon.INSECTE, 5, "F"), listOf(r.espece, r.cdNom, r.taxon, r.nombre, r.sexe))
        assertTrue(r.mediaUrisCounting0.isEmpty())

        // Une autre espèce repart des valeurs par défaut ; la même les garde.
        reprise.choisirEspece(Taxon.INSECTE, "Machaon", 54303)
        assertEquals(5, reprise.modele.value.nombre)
        reprise.choisirEspece(Taxon.INSECTE, "Flambé", 54317)
        assertEquals(1 to "", reprise.modele.value.let { it.nombre to it.sexe })
    }

    /** 24 novembre 2026 : un marqueur touché ouvre le popup de SON observation (éditer, supprimer), à
     *  l'heure de son enregistrement ; la suppression se confirme. */
    @Test
    fun le_popup_d_une_observation_touchee_et_la_confirmation_de_sa_suppression() {
        val date = 1_763_970_000_000L
        val heure = FormatDate.heure(date)
        val merle = Observation(id = "a", espece = "Merle noir", latitude = 42.9, longitude = 1.4, date = date, nombre = 3, notes = "Chante")
        assertEquals("Merle noir" to "Enregistrée à $heure · 3 individus\nChante", SaisieMono.optionsObservation(merle))
        assertEquals(
            "Merle noir" to "Enregistrée à $heure · 1 individu",
            SaisieMono.optionsObservation(merle.copy(nombre = 1, notes = "", dateDebutReleve = date - 3_600_000L)),
            "l'heure de l'enregistrement, pas celle des détails de la série",
        )
        val sansEspece = merle.copy(espece = ReleveEnCours.ESPECE_SANS_ESPECE, releveSansEspece = true, nombre = 0)
        assertEquals("Relevé sans espèce" to "Enregistré à $heure", SaisieMono.optionsObservation(sansEspece))
        assertEquals("Supprimer l'observation ?", SaisieMono.TITRE_SUPPRESSION)
        assertEquals("« Merle noir », enregistrée à $heure, sera supprimée avec ce qui a été saisi pour elle.", SaisieMono.messageSuppression(merle))
        assertEquals(
            "« Merle noir », enregistrée à $heure, sera supprimée avec ce qui a été saisi pour elle. Elle a déjà été transmise à GeoNature, où elle restera.",
            SaisieMono.messageSuppression(merle.copy(envoyeeServeur = true)),
        )
        assertEquals("Le relevé sans espèce enregistré à $heure sera supprimé.", SaisieMono.messageSuppression(sansEspece))
    }

    /** Plusieurs « + » sur la même position : leurs marqueurs se recouvrent, on choisit d'abord
     *  laquelle ; des observations séparées sur la carte ouvrent leur popup aussitôt. */
    @Test
    fun les_observations_au_meme_point_se_choisissent_d_abord() {
        val a = Observation(id = "a", espece = "Machaon", latitude = 42.9, longitude = 1.4, date = 1L)
        val b = a.copy(id = "b", date = 2L)
        val proche = a.copy(id = "c", latitude = 42.90005, date = 3L)
        val toutes = listOf(a, b, proche)
        assertEquals(listOf("b", "a"), SaisieMono.observationsAuPoint(a, toutes).map { it.id }, "la plus récente d'abord")
        assertEquals(listOf("c"), SaisieMono.observationsAuPoint(proche, toutes).map { it.id }, "à 5 m : son popup seul")
        assertEquals(listOf("c"), SaisieMono.observationsAuPoint(proche, emptyList()).map { it.id })
        assertEquals("2 obs. à ce point", SaisieMono.titreObservationsAuPoint(2))
        assertTrue(SaisieMono.ligneObservationAuPoint(a.copy(nombre = 3, notes = "Mâle")).endsWith("  Machaon · 3 ind.\n   Mâle"))
    }

    /** « Éditer » ouvre la fiche par le relevé de l'observation, s'il en a un, à son rang. */
    @Test
    fun editer_ouvre_la_fiche_par_le_releve_de_l_observation() {
        val seule = Observation(id = "a", espece = "Machaon", latitude = 42.9, longitude = 1.4)
        val premiere = seule.copy(id = "b", releveId = "R")
        val ajoutee = seule.copy(id = "c", espece = "Flambé", releveId = "R")
        val toutes = listOf(premiere, seule, ajoutee)
        assertEquals(null to 0, SaisieMono.ficheAOuvrir(seule, toutes))
        assertEquals("R" to 0, SaisieMono.ficheAOuvrir(premiere, toutes))
        assertEquals("R" to 1, SaisieMono.ficheAOuvrir(ajoutee, toutes))
        assertEquals(null to 0, SaisieMono.ficheAOuvrir(seule.copy(releveId = ""), toutes))
    }
}
