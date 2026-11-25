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

// Demandes du 24 novembre 2026 : « quand on réédite une saisie mono-taxons, il ne faut pas aller sur l'écran
// du relevé mais directement sur la carte de saisie des obs », puis les valeurs de la série, réglées dans la
// fiche de l'espèce, gardées avec la saisie et reprises telles quelles par « Continuer la saisie ». Le
// 25 novembre 2026, « Modifier » quitte le panneau de la carte, et le « i » de la carte rouvre la fiche.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.occtax.DetailsReleveResult
import fr.ariegenature.geomys.outils.ajouterTaxRefEssai
import fr.ariegenature.geomys.store.SuggestionTaxon
import fr.ariegenature.geomys.store.io.listeVersJson
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class RepriseSaisieMonoTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services
    private val magasin = ViewModelStore()

    private val merle = Observation(id = "a", espece = "Merle noir", taxon = Taxon.OISEAU, cdNom = 4117, latitude = 42.9, longitude = 1.6, date = 1_000L)

    /** Le dernier « + », modifié à part ensuite : deux mâles. */
    private val machaon = Observation(id = "b", espece = "Machaon", taxon = Taxon.INSECTE, cdNom = 54303, latitude = 42.91, longitude = 1.61, date = 2_000L, nombre = 2, sexe = "M")
    private val rougegorge = Observation(id = "c", espece = "Rougegorge familier", taxon = Taxon.OISEAU, cdNom = 4001, latitude = 42.8, longitude = 1.5, date = 500L)

    /** Les valeurs d'une série de Machaon gardées avec la saisie : quatre femelles. */
    private val serieMachaon = Observation(espece = "Machaon", taxon = Taxon.INSECTE, cdNom = 54303, latitude = 0.0, longitude = 0.0, nombre = 4, sexe = "F")

    @BeforeTest
    fun preparer() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun nettoyer() {
        magasin.clear()
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    private inline fun <reified T : ViewModel> vm(noinline creer: () -> T): T =
        ViewModelProvider.create(magasin, viewModelFactory { initializer { creer() } })[T::class]

    /** Les écritures d'état publiées, et ce qui tourne hors de l'horloge du test (les entrées du champ
     *  espèce) attendu jusqu'à [condition]. */
    private fun TestScope.avancerJusqua(condition: () -> Boolean) {
        repeat(500) {
            Snapshot.sendApplyNotifications()
            testScheduler.advanceUntilIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("la condition n'est jamais remplie")
    }

    /** Une saisie mono-taxon enregistrée avec [observations] et les valeurs de série [modele], reprise
     *  dans la session comme par « Continuer la saisie » (jeu de données par défaut des Paramètres), puis
     *  l'écran ouvert, en reprise ou non. [dateAChoisir] : le serveur veut la date vide au départ ;
     *  [codeLufObligatoire] : un champ additionnel d'occurrence obligatoire, sans valeur par défaut. */
    private suspend fun ouvrir(
        vararg observations: Observation,
        modele: Observation? = null,
        reprise: Boolean = true,
        dateAChoisir: Boolean = false,
        codeLufObligatoire: Boolean = false,
    ): SaisieMonoViewModel {
        services.ajouterTaxRefEssai()
        services.config.apply {
            datasetsCacheJson = """[{"id":12,"nom":"JDD","moduleCodes":["OCCTAX"]}]"""
            datasetsCreablesOcctax = setOf(12)
            idDataset = "12"
            occtaxDateDuJour = !dateAChoisir
            if (codeLufObligatoire) {
                additionalFieldsOcctaxJson = listeVersJson(
                    AdditionalFieldDef.serializer(),
                    listOf(AdditionalFieldDef(1, "code_luf", "Code LUF", WidgetType.NUMBER, required = true, objectsCode = listOf("OCCTAX_OCCURENCE"), datasetsIds = listOf(12))),
                )
            }
        }
        services.preferences.memoiserTaxon(Taxon.OISEAU)
        val session = vm { SessionSaisieViewModel(services) }
        session.session.reprendreSortie(Sortie(id = "s1", saisieMono = true, observations = observations.toList(), modeleMono = modele))
        return vm { SaisieMonoViewModel(services, session, null, reprise) }
    }

    @Test
    fun continuer_reprend_les_valeurs_gardees_de_la_serie_et_ouvre_la_carte() = runTest {
        // La dernière observation a été modifiée à part : la série garde ses propres valeurs.
        val mono = ouvrir(merle, machaon, modele = serieMachaon)
        assertTrue(mono.modeActif, "la carte, sans les réglages")
        assertNull(mono.details)
        val m = mono.saisie.modele.value
        assertEquals("Machaon" to 54303, m.espece to m.cdNom)
        assertEquals(Taxon.INSECTE, m.taxon)
        assertEquals(4 to "F", m.nombre to m.sexe)
        assertEquals(LatLon(42.91, 1.61), mono.cibleDemarrage, "sans position GPS, la carte s'ouvre sur le dernier « + »")

        // Le « + » suivant continue la série, avec ses valeurs.
        mono.enregistrerIci(LatLon(42.92, 1.62))
        val nouvelle = mono.session.observations.value.last()
        assertEquals(Triple("Machaon", 4, "F"), Triple(nouvelle.espece, nouvelle.nombre, nouvelle.sexe))
        assertEquals(Taxon.INSECTE, nouvelle.taxon)
        assertEquals(3, mono.session.observations.value.size, "les observations reprises restent")
    }

    @Test
    fun sans_valeurs_gardees_la_serie_reprend_celles_du_dernier_plus() = runTest {
        // Saisie d'avant les valeurs gardées : le « + » le plus récent, ni le premier ni le dernier de la liste.
        val mono = ouvrir(merle, machaon, rougegorge)
        assertTrue(mono.modeActif)
        val m = mono.saisie.modele.value
        assertEquals("Machaon" to 54303, m.espece to m.cdNom)
        assertEquals(2 to "M", m.nombre to m.sexe)

        // Le champ espèce montre l'espèce de la série, prête pour « Démarrer » si l'on se retrouve sur les
        // réglages (détails ou fiche demandés d'abord, refermés).
        assertEquals("Machaon", mono.champ.texte)
        assertEquals(Taxon.INSECTE, mono.champ.taxon)
        assertEquals(54303, (mono.champ.recherche.statut as TaxRefStatut.Trouve).cdNom)
    }

    /** Le compteur du panneau reprend toutes les observations de la saisie, comme « Mes saisies » (sans
     *  le « relevé sans espèce », qui n'en est pas une), puis compte les « + » suivants (demande du
     *  25 novembre 2026 : il repartait de 0). */
    @Test
    fun le_compteur_reprend_toutes_les_observations_de_la_saisie() = runTest {
        val sansEspece = Observation(id = "d", espece = "Relevé sans espèce", latitude = 42.9, longitude = 1.6, releveSansEspece = true)
        val mono = ouvrir(merle, machaon, sansEspece, modele = serieMachaon)
        assertTrue(mono.modeActif)
        assertEquals(2, mono.nombreObservations())
        mono.enregistrerIci(LatLon(42.92, 1.62))
        assertEquals(3, mono.nombreObservations())
    }

    /** Le ✓ de la fiche rouverte par le « i » de la carte ramène à la carte telle qu'on l'a laissée : ni
     *  recentrage sur la position ou le dernier « + », ni suivi de position réarmé. Les nouvelles
     *  valeurs partent avec la saisie. */
    @Test
    fun le_i_de_la_carte_y_ramene_sans_la_recentrer() = runTest {
        val mono = ouvrir(merle, machaon, modele = serieMachaon)
        assertEquals(LatLon(42.91, 1.61), mono.cibleDemarrage)
        // La carte ouverte a pris sa cible ; puis on l'a déplacée au doigt, ce qui coupe le suivi.
        mono.cibleDemarrage = null
        mono.suivrePosition = false
        // Le champ espèce change de groupe de lui-même (groupe absent de la liste de taxons) : hors des
        // réglages, la série garde le sien.
        mono.choisirTaxon(Taxon.OISEAU)
        mono.ouvrirFicheSerie()
        assertEquals(EvenementSaisieMono.FicheSerie, mono.evenements.first())
        // La fiche couvre la carte, qui garde en partant son centre et son zoom (onDispose de CarteMono).
        mono.centreMemorise = LatLon(42.95, 1.65)
        mono.zoomMemorise = 17.0
        mono.validerFicheSerie(mono.saisie.modele.value.copy(nombre = 6))
        assertTrue(mono.modeActif)
        assertNull(mono.cibleDemarrage, "pas de recentrage")
        assertFalse(mono.suivrePosition, "le suivi reste coupé")
        assertEquals(Taxon.INSECTE, mono.saisie.modele.value.taxon, "le groupe de la série")
        val gardees = mono.session.modeleMono
        assertEquals(listOf<Any?>("Machaon", Taxon.INSECTE, 6, "F"), listOf(gardees?.espece, gardees?.taxon, gardees?.nombre, gardees?.sexe))
    }

    /** Les détails du relevé de la série (observateurs, date a posteriori, heures, commentaire, habitat,
     *  champs du relevé) sont repris de son dernier « + » : la carte n'ouvre plus « Détails » pour les
     *  reposer (relecture du 25 novembre 2026). Sans ses altitudes, propres à sa position. Une date
     *  reprise n'est plus à choisir. */
    @Test
    fun les_details_du_releve_de_la_serie_sont_repris_du_dernier_plus() = runTest {
        val detaille = machaon.copy(
            observateursReleveIds = listOf(7, 8),
            observateursReleveNoms = listOf("A", "B"),
            dateDebutReleve = 86_400_000L,
            heureDebutReleve = "08:00",
            commentReleve = "Haie",
            cdHabReleve = 123,
            habitatReleveLabel = "Haie bocagère",
            champsReleveExtra = mapOf("altitude_min" to "300", "altitude_max" to "310", "place_name" to "Le pré"),
        )
        val mono = ouvrir(merle, detaille, modele = serieMachaon, dateAChoisir = true)
        assertTrue(mono.modeActif, "la date reprise : la carte, sans « Détails »")
        assertNull(mono.details)
        mono.enregistrerIci(LatLon(42.92, 1.62))
        val nouvelle = mono.session.observations.value.last()
        assertEquals(listOf(7, 8) to listOf("A", "B"), nouvelle.observateursReleveIds to nouvelle.observateursReleveNoms)
        assertEquals(86_400_000L to "08:00", nouvelle.dateDebutReleve to nouvelle.heureDebutReleve)
        assertEquals(Triple("Haie", 123, "Haie bocagère"), Triple(nouvelle.commentReleve, nouvelle.cdHabReleve, nouvelle.habitatReleveLabel))
        assertEquals(mapOf("place_name" to "Le pré"), nouvelle.champsReleveExtra, "sans les altitudes du dernier « + »")
    }

    /** La fiche demandée à la reprise, refermée sans ✓ : on reste sur les réglages. La même espèce y garde
     *  les valeurs de la série ; une autre repart des valeurs par défaut, comme une espèce ajoutée en
     *  multi-taxons, et l'espèce de la série, touchée de nouveau, retrouve les siennes (celles gardées
     *  avec la saisie), même après cette autre espèce abandonnée. */
    @Test
    fun fiche_refermee_a_la_reprise_la_meme_espece_garde_ses_valeurs_une_autre_repart_des_defauts() = runTest {
        val mono = ouvrir(merle, machaon, modele = serieMachaon, codeLufObligatoire = true)
        assertEquals(EvenementSaisieMono.FicheSerie, mono.evenements.first())
        mono.ficheSerieAbandonnee()
        assertFalse(mono.modeActif)
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(4 to "F", mono.saisie.modele.value.let { it.nombre to it.sexe }, "la même espèce")
        mono.ficheSerieAbandonnee()
        mono.choisirSuggestion(SuggestionTaxon("Flambé", 54317))
        assertEquals(Triple("Flambé", 1, ""), mono.saisie.modele.value.let { Triple(it.espece, it.nombre, it.sexe) }, "une autre")
        mono.ficheSerieAbandonnee()
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(Triple("Machaon", 4, "F"), mono.saisie.modele.value.let { Triple(it.espece, it.nombre, it.sexe) }, "l'espèce de la série")
    }

    /** Le statut du champ espèce est affiné une fois ses entrées chargées : le nom scientifique du cd_nom,
     *  comme un choix dans la liste. */
    @Test
    fun le_champ_espece_de_la_reprise_porte_le_nom_scientifique() = runTest {
        val mono = ouvrir(machaon)
        avancerJusqua { (mono.champ.recherche.statut as? TaxRefStatut.Trouve)?.nomScientifique == "Papilio machaon" }
        assertEquals(TaxRefStatut.Trouve(54303, "Papilio machaon", "Machaon"), mono.champ.recherche.statut)
    }

    /** L'événement est envoyé dès l'ouverture de l'écran : sa navigation ne passe donc pas par la garde
     *  « écran au premier plan » (naviguerDepuis), qui l'aurait ignoré (relecture du 24 novembre 2026). */
    @Test
    fun un_champ_obligatoire_vide_ouvre_d_abord_la_fiche_de_la_serie() = runTest {
        val mono = ouvrir(merle, machaon, modele = serieMachaon, codeLufObligatoire = true)
        assertFalse(mono.modeActif)
        assertEquals(EvenementSaisieMono.FicheSerie, mono.evenements.first(), "le code LUF d'abord, dans la fiche de la série")
        mono.validerFicheSerie(mono.saisie.modele.value.copy(additionalFieldsOccurrence = mapOf("code_luf" to "12")))
        assertTrue(mono.modeActif)
        mono.enregistrerIci(LatLon(42.92, 1.62))
        assertEquals(mapOf("code_luf" to "12"), mono.session.observations.value.last().additionalFieldsOccurrence)
    }

    /** Détails d'abord (date à choisir), puis la fiche (code LUF manquant), puis la carte : sans GPS, sur
     *  le dernier « + », comme une reprise directe. */
    @Test
    fun une_reprise_par_les_details_et_la_fiche_ouvre_la_carte_sur_le_dernier_plus() = runTest {
        val mono = ouvrir(merle, machaon, modele = serieMachaon, dateAChoisir = true, codeLufObligatoire = true)
        assertNotNull(mono.details)
        mono.validerDetails(DetailsReleveResult(12, emptyList(), emptyList(), emptyMap(), dateDebut = 1_000L))
        assertEquals(EvenementSaisieMono.FicheSerie, mono.evenements.first())
        assertFalse(mono.modeActif)
        mono.validerFicheSerie(mono.saisie.modele.value.copy(additionalFieldsOccurrence = mapOf("code_luf" to "12")))
        assertTrue(mono.modeActif)
        assertEquals(LatLon(42.91, 1.61), mono.cibleDemarrage)
    }

    /** Une saisie qui a gardé les valeurs de sa série rouvre sa carte, même sans observation d'espèce. */
    @Test
    fun des_valeurs_gardees_rouvrent_la_carte_meme_sans_observation_d_espece() = runTest {
        val sansEspece = Observation(id = "d", espece = "Relevé sans espèce", latitude = 42.9, longitude = 1.6, releveSansEspece = true)
        val mono = ouvrir(sansEspece, modele = serieMachaon)
        assertTrue(mono.modeActif)
        assertEquals("Machaon", mono.saisie.modele.value.espece)
        assertNull(mono.cibleDemarrage, "ni GPS ni « + » : la carte s'ouvre où elle était")
    }

    @Test
    fun des_valeurs_gardees_completes_ouvrent_la_carte_malgre_un_champ_obligatoire() = runTest {
        val mono = ouvrir(machaon, modele = serieMachaon.copy(additionalFieldsOccurrence = mapOf("code_luf" to "7")), codeLufObligatoire = true)
        assertTrue(mono.modeActif)
    }

    @Test
    fun sans_espece_a_reprendre_les_reglages_s_ouvrent() = runTest {
        val sansEspece = Observation(id = "d", espece = "Relevé sans espèce", latitude = 42.9, longitude = 1.6, releveSansEspece = true)
        assertFalse(ouvrir(sansEspece).modeActif, "pas d'espèce pour la série : ses réglages")
    }

    @Test
    fun sans_reprise_les_reglages_s_ouvrent() = runTest {
        assertFalse(ouvrir(machaon, modele = serieMachaon, reprise = false).modeActif, "une saisie ouverte de l'accueil commence par ses réglages")
    }

    @Test
    fun date_a_choisir_la_carte_s_ouvre_a_la_validation_des_details() = runTest {
        val mono = ouvrir(machaon, dateAChoisir = true)
        assertFalse(mono.modeActif)
        assertNotNull(mono.details, "la date d'abord, comme au démarrage d'une série")
        mono.validerDetails(DetailsReleveResult(12, emptyList(), emptyList(), emptyMap(), dateDebut = 1_000L))
        assertTrue(mono.modeActif)
        assertEquals("Machaon", mono.saisie.modele.value.espece)
    }

    @Test
    fun details_refermes_sans_validation_la_reprise_reste_sur_ses_reglages() = runTest {
        val mono = ouvrir(machaon, dateAChoisir = true)
        mono.fermerDetails()
        assertFalse(mono.modeActif)
        assertEquals("Machaon", mono.champ.texte, "l'espèce posée, pour « Démarrer »")
        // Plus tard, les détails validés par « Démarrer » ne rouvrent pas la carte d'eux-mêmes.
        mono.demarrer()
        assertNotNull(mono.details)
        mono.validerDetails(DetailsReleveResult(12, emptyList(), emptyList(), emptyMap(), dateDebut = 1_000L))
        assertFalse(mono.modeActif)
        mono.demarrer()
        assertTrue(mono.modeActif)
    }
}
