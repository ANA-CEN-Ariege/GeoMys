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

// Demande du 24 novembre 2026 : « en saisie mono-taxons, applique la même chose que en multi-taxons. Après la
// saisie de l'espèce, présenter la fiche du relevé. Les valeurs saisies dans cet écran seront valables pour
// tout le relevé. Des modifications pourront être faites ponctuellement au niveau d'une obs en cliquant sur
// cette obs ». Plus de boutons « Caractérisation » et « Dénombrement » ; le ✓ de la fiche ouvre la carte.
// Demande du 25 novembre 2026 : « supprime le bouton Modifier. Ajoute un bouton "i" sur la carte avec le même
// style que la carte dans multi-taxons et qui permet d'accéder à l'écran de la fiche du relevé. Les valeurs
// saisies dans cet écran seront valables pour toute la suite du relevé ».

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.occtax.DetailsReleveResult
import fr.ariegenature.geomys.outils.ajouterTaxRefEssai
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.store.SuggestionTaxon
import fr.ariegenature.geomys.store.TaxrefRestriction
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
class FicheSerieMonoTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services
    private val magasin = ViewModelStore()

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

    /** Les écritures d'état publiées, et ce qui tourne hors de l'horloge du test (les propositions du
     *  champ espèce) attendu jusqu'à [condition]. */
    private fun TestScope.avancerJusqua(condition: () -> Boolean) {
        repeat(500) {
            Snapshot.sendApplyNotifications()
            testScheduler.advanceUntilIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("la condition n'est jamais remplie")
    }

    /** Les réglages d'une nouvelle saisie mono-taxon (jeu de données par défaut des Paramètres, groupe
     *  des insectes). [codeLufObligatoire] : un champ additionnel d'occurrence obligatoire, sans valeur
     *  par défaut ; [dateAChoisir] : le serveur veut la date vide au départ. */
    private suspend fun reglages(codeLufObligatoire: Boolean = false, dateAChoisir: Boolean = false): SaisieMonoViewModel {
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
        services.preferences.memoiserTaxon(Taxon.INSECTE)
        val session = vm { SessionSaisieViewModel(services) }
        return vm { SaisieMonoViewModel(services, session, null) }
    }

    /** Les listes que la fiche simplifiée montre pour un insecte (comme SaisieSimplifieeEcransTest). */
    private fun nomenclaturesDeLaFicheSimplifiee() {
        val tous = listOf(TaxrefRestriction("all", "all"))
        services.nomenclatures.setAll(
            mapOf(
                "STADE_VIE" to listOf(NomValeur(1, "Inconnu", tous), NomValeur(2, "Adulte", tous)),
                "SEXE" to listOf(NomValeur(5, "Indéterminé", tous), NomValeur(3, "Mâle", tous)),
                "ETA_BIO" to listOf(NomValeur(4, "Vivant", tous)),
                "METH_OBS" to listOf(NomValeur(7, "Vu", tous)),
            ),
        )
        services.nomenclatures.setDefauts(mapOf("STADE_VIE" to "1", "SEXE" to "5", "ETA_BIO" to "4", "METH_OBS" to "7"))
    }

    @Test
    fun l_espece_choisie_ouvre_sa_fiche_dont_le_valide_ouvre_la_carte_et_vaut_pour_chaque_plus() = runTest {
        val mono = reglages()
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(EvenementSaisieMono.FicheSerie, mono.evenements.first(), "la fiche de l'espèce, comme en multi-taxons")
        assertFalse(mono.modeActif)
        val ouverte = mono.saisie.modele.value
        assertEquals(Triple("Machaon", 54303, Taxon.INSECTE), Triple(ouverte.espece, ouverte.cdNom, ouverte.taxon))

        // ✓ de la fiche du mode pro : trois mâles, avec une photo et un second dénombrement photographié.
        val fiche = ouverte.copy(
            nombre = 3,
            sexe = "M",
            mediaUrisCounting0 = listOf("photo.jpg"),
            denombrementsAdditionnels = listOf(Denombrement(nombreMin = 1, mediaUris = listOf("son.m4a"))),
        )
        mono.validerFicheSerie(fiche)
        assertTrue(mono.modeActif, "le ✓ de la fiche ouvre la carte")
        mono.enregistrerIci(LatLon(42.9, 1.6))
        mono.enregistrerIci(LatLon(42.91, 1.61))
        val observations = mono.session.observations.value
        assertEquals(2, observations.size)
        for (o in observations) {
            assertEquals(Triple(3, "M", 4), Triple(o.nombre, o.sexe, o.totalIndividus), "les valeurs de la série, à chaque « + »")
            assertTrue(o.mediaUrisCounting0.isEmpty() && o.denombrementsAdditionnels.all { it.mediaUris.isEmpty() }, "sans les photos ni les sons de la fiche")
        }

        // Gardées avec la saisie, pour « Continuer la saisie ».
        assertEquals(3, mono.session.modeleMono?.nombre)
        mono.session.attendreEcritures()
        val gardees = services.sortieStore.charger().single().modeleMono
        assertEquals(Triple("Machaon", 3, "M"), Triple(gardees?.espece, gardees?.nombre, gardees?.sexe))
    }

    /** Le « i » de la carte rouvre la fiche de la série. Refermée sans ✓, rien ne change ; validée, ses
     *  valeurs valent pour les « + » suivants et partent avec la saisie, et l'on reste sur la carte. Les
     *  observations déjà enregistrées gardent les leurs : chacune se modifie par son marqueur. */
    @Test
    fun le_i_de_la_carte_rouvre_la_fiche_dont_les_valeurs_valent_pour_la_suite() = runTest {
        val mono = reglages()
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(EvenementSaisieMono.FicheSerie, mono.evenements.first())
        mono.validerFicheSerie(mono.saisie.modele.value.copy(nombre = 3, sexe = "M"))
        assertTrue(mono.modeActif)
        mono.enregistrerIci(LatLon(42.9, 1.6))

        mono.ouvrirFicheSerie()
        assertEquals(EvenementSaisieMono.FicheSerie, mono.evenements.first(), "le « i » ouvre la fiche de la série")
        mono.ficheSerieAbandonnee()
        assertTrue(mono.modeActif, "refermée sans ✓ : la carte")

        mono.ouvrirFicheSerie()
        assertEquals(EvenementSaisieMono.FicheSerie, mono.evenements.first())
        mono.validerFicheSerie(mono.saisie.modele.value.copy(nombre = 5, sexe = "F"))
        assertTrue(mono.modeActif, "validée : la carte")
        assertEquals("Machaon" to 54303, mono.saisie.modele.value.let { it.espece to it.cdNom }, "la même espèce")
        mono.enregistrerIci(LatLon(42.91, 1.61))
        val (premiere, suivante) = mono.session.observations.value
        assertEquals(3 to "M", premiere.nombre to premiere.sexe, "déjà enregistrée : inchangée")
        assertEquals(5 to "F", suivante.nombre to suivante.sexe, "les nouvelles valeurs, pour la suite")
        assertEquals(2, mono.nombreObservations())

        // Gardées avec la saisie, pour « Continuer la saisie ».
        mono.session.attendreEcritures()
        val gardees = services.sortieStore.charger().single().modeleMono
        assertEquals(5 to "F", gardees?.nombre to gardees?.sexe)
    }

    /** La fiche du mode simplifié rouverte par le « i » : ses valeurs, au ✓, valent pour la suite et
     *  partent avec la saisie. */
    @Test
    fun le_i_de_la_carte_et_la_fiche_simplifiee() = runTest {
        nomenclaturesDeLaFicheSimplifiee()
        val mono = reglages()
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        mono.validerFicheSerie(mono.saisie.modele.value.copy(nombre = 3))
        mono.enregistrerIci(LatLon(42.9, 1.6))
        mono.ouvrirFicheSerie()
        val fiche = FicheEspeceViewModel(services, mono.saisie.ficheSerie, 0, avecMedias = false)
        fiche.saisirMin("8")
        assertNull(fiche.valider())
        mono.ficheSerieValidee()
        assertTrue(mono.modeActif)
        mono.enregistrerIci(LatLon(42.91, 1.61))
        assertEquals(listOf(3, 8), mono.session.observations.value.map { it.nombre })
        assertEquals(8, mono.session.modeleMono?.nombre)
    }

    /** Une espèce tapée en entier, sans toucher la liste : « Démarrer » ouvre d'abord sa fiche. */
    @Test
    fun une_espece_tapee_puis_demarrer_ouvre_sa_fiche() = runTest {
        val mono = reglages()
        mono.saisir("Machaon")
        mono.champ.recherche.ligne.first { (it.statut as? TaxRefStatut.Trouve)?.cdNom == 54303 }
        mono.demarrer()
        assertFalse(mono.modeActif)
        assertEquals(EvenementSaisieMono.FicheSerie, mono.evenements.first(), "sa fiche d'abord")
        mono.validerFicheSerie(mono.saisie.modele.value.copy(nombre = 2))
        assertTrue(mono.modeActif)
        assertEquals(2, mono.saisie.modele.value.nombre)
    }

    /** ✓ de la fiche quand la date reste à choisir : « Détails » d'abord, et leur validation ouvre la carte. */
    @Test
    fun une_fiche_validee_sans_date_ouvre_la_carte_apres_les_details() = runTest {
        val mono = reglages(dateAChoisir = true)
        mono.fermerDetails()
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        mono.validerFicheSerie(mono.saisie.modele.value.copy(nombre = 2))
        assertFalse(mono.modeActif)
        assertNotNull(mono.details, "la date d'abord")
        mono.validerDetails(DetailsReleveResult(12, emptyList(), emptyList(), emptyMap(), dateDebut = 1_000L))
        assertTrue(mono.modeActif)
        assertEquals(2, mono.saisie.modele.value.nombre)
    }

    /** Sa fiche refermée sans ✓, le champ espèce des réglages propose de nouveau l'espèce : la toucher
     *  rouvre sa fiche, sans rien retaper. */
    @Test
    fun une_fiche_refermee_le_champ_espece_repropose_l_espece() = runTest {
        val mono = reglages()
        mono.saisir("Mach")
        avancerJusqua { mono.champ.suggestions.any { it.cdNom == 54303 } }
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        avancerJusqua { mono.champ.suggestions.isEmpty() }
        mono.ficheSerieAbandonnee()
        assertFalse(mono.modeActif)
        avancerJusqua { mono.champ.suggestions.any { it.cdNom == 54303 } }
        assertEquals(54303, (mono.champ.recherche.statut as TaxRefStatut.Trouve).cdNom, "le statut reste")
    }

    @Test
    fun demarrer_avec_un_champ_obligatoire_vide_ouvre_la_fiche_de_la_serie() = runTest {
        val mono = reglages(codeLufObligatoire = true)
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(EvenementSaisieMono.FicheSerie, mono.evenements.first())
        // La fiche refermée sans ✓ : « Démarrer » la rouvre, le code LUF manque.
        mono.demarrer()
        assertFalse(mono.modeActif)
        assertEquals(EvenementSaisieMono.FicheSerie, mono.evenements.first(), "le champ obligatoire, dans la fiche")
        mono.validerFicheSerie(mono.saisie.modele.value.copy(additionalFieldsOccurrence = mapOf("code_luf" to "12")))
        assertTrue(mono.modeActif)
        mono.enregistrerIci(LatLon(42.9, 1.6))
        assertEquals(mapOf("code_luf" to "12"), mono.session.observations.value.single().additionalFieldsOccurrence)
    }

    /** Une fiche validée ouvre la carte : elle ne se rouvre pas pour un champ obligatoire qu'elle ne
     *  montrerait pas (pas de boucle). */
    @Test
    fun une_fiche_validee_ne_se_rouvre_pas() = runTest {
        val mono = reglages(codeLufObligatoire = true)
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        mono.validerFicheSerie(mono.saisie.modele.value)
        assertTrue(mono.modeActif)
    }

    /** La fiche du mode simplifié écrit elle-même les valeurs de la série ; elle n'y propose ni photo ni
     *  son, que chaque « + » recopierait. */
    @Test
    fun la_fiche_simplifiee_ecrit_les_valeurs_de_la_serie_sans_photo_ni_son() = runTest {
        nomenclaturesDeLaFicheSimplifiee()
        val mono = reglages()
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertTrue(services.config.occtaxMediasActifs)
        val fiche = FicheEspeceViewModel(services, mono.saisie.ficheSerie, 0, avecMedias = false)
        assertFalse(fiche.mediasVisibles, "ni « Photo » ni « Audio »")
        assertTrue(FicheEspeceViewModel(services, mono.saisie.ficheSerie, 0).mediasVisibles, "témoin : le serveur active les médias")
        fiche.saisirMin("5")
        assertNull(fiche.valider())
        assertEquals(5, mono.saisie.modele.value.nombre)
        mono.ficheSerieValidee()
        assertTrue(mono.modeActif)
        mono.enregistrerIci(LatLon(42.9, 1.6))
        assertEquals(5, mono.session.observations.value.single().nombre)
    }
}
