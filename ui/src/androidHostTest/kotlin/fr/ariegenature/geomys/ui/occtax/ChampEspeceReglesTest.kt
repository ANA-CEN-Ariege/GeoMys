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

// Gardes de GeoMys-Android @ d72b8da qui lisaient le texte des écrans de saisie (SaisieObservationFragment,
// SaisieRapideFragment) faute de pouvoir les actionner : ChangementDeGroupeTest (texte gardé et re-résolu au
// changement de groupe et de mode), ProposeEgaleAccepteTest (propositions périmées retirées avant recalcul),
// SuggestionPorteSonCdNomTest (la suggestion choisie est prise telle quelle, sans re-résoudre son texte) et
// ResolutionPurementLocaleTest (aucun chemin de saisie n'écrit dans le cache TaxRef ; la part du formulaire
// des Suivis est dans ChampTaxonTest, côté socle). Ici, les règles sont vérifiées en actionnant l'état du
// champ et les ViewModels.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.occtax.GeometrieFinale
import fr.ariegenature.geomys.occtax.OuvertureReleve
import fr.ariegenature.geomys.occtax.libelleChampEspece
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ChampEspeceReglesTest {

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

    /** Un jeu de données proposé, choisi comme jeu par défaut des Paramètres. */
    private fun jeuParDefaut() = services.config.apply {
        datasetsCacheJson = """[{"id":12,"nom":"JDD","moduleCodes":["OCCTAX"]}]"""
        datasetsCreablesOcctax = setOf(12)
        idDataset = "12"
    }

    private inline fun <reified T : ViewModel> vm(noinline creer: () -> T): T =
        ViewModelProvider.create(magasin, viewModelFactory { initializer { creer() } })[T::class]

    /** Le champ de [taxon] passé à [test], ses coroutines et ses calculs « hors du fil de l'interface »
     *  sur l'horloge du test (pas dans `backgroundScope`, qu'`advanceUntilIdle` laisse de côté). */
    private fun TestScope.avecChamp(taxon: Taxon, test: (EtatChampEspece) -> Unit) {
        val portee = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            test(EtatChampEspece(services, portee, taxon, ::libelleChampEspece, StandardTestDispatcher(testScheduler)))
        } finally {
            portee.cancel()
        }
    }

    /** Les écritures d'état sont publiées (le travail du Recomposer), puis tout avance jusqu'au repos. */
    private fun TestScope.toutAvancer() {
        repeat(3) {
            Snapshot.sendApplyNotifications()
            testScheduler.advanceUntilIdle()
        }
    }

    /** La résolution d'une dictée passe par Dispatchers.IO, hors de l'horloge du test : celle-ci avance
     *  jusqu'à ce que [condition] soit remplie. */
    private fun TestScope.avancerJusqua(condition: () -> Boolean) {
        repeat(500) {
            toutAvancer()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("la dictée n'a pas abouti")
    }

    private val machaon = TaxRefStatut.Trouve(54303, "Papilio machaon", "Machaon")

    @Test
    fun le_texte_survit_au_changement_de_groupe_et_le_taxon_de_l_ancien_groupe_tombe() = runTest {
        services.ajouterTaxRefEssai()
        avecChamp(Taxon.INSECTE) { champ ->
            toutAvancer()
            champ.poserChoix("Machaon")
            champ.recherche.poser(machaon, "Machaon")
            assertEquals(machaon, champ.recherche.statut)

            champ.choisirTaxon(Taxon.OISEAU)
            assertEquals("Machaon", champ.texte, "le texte tapé est conservé")
            assertNull(champ.recherche.statut, "le taxon de l'ancien groupe tombe sur-le-champ (bouton « Démarrer »)")
            assertEquals(Taxon.OISEAU, services.preferences.dernierTaxon(), "le groupe est mémorisé")
        }
    }

    @Test
    fun le_texte_survit_a_la_bascule_des_noms_scientifiques() = runTest {
        services.ajouterTaxRefEssai()
        avecChamp(Taxon.INSECTE) { champ ->
            toutAvancer()
            champ.poserChoix("Machaon")
            champ.recherche.poser(machaon, "Machaon")

            champ.basculerNomScientifique(true)
            assertEquals("Machaon", champ.texte)
            assertNull(champ.recherche.statut, "un nom français n'est plus valide en noms scientifiques")
            assertTrue(services.preferences.rechercheNomSci())
        }
    }

    @Test
    fun les_propositions_perimees_ne_repondent_plus_pendant_le_recalcul() = runTest {
        services.ajouterTaxRefEssai()
        avecChamp(Taxon.OISEAU) { champ ->
            toutAvancer()
            champ.saisir("Me")
            toutAvancer()
            assertTrue(champ.suggestions.any { it.nom == "Merle noir" }, "propositions des oiseaux : ${champ.suggestions}")

            // Changement de groupe : tant que les propositions des mammifères se calculent, la frappe
            // ne fait plus apparaître celles des oiseaux.
            champ.choisirTaxon(Taxon.MAMMIFERE)
            champ.saisir("Ec")
            Snapshot.sendApplyNotifications()
            testScheduler.runCurrent()
            assertEquals(emptyList(), champ.suggestions, "l'ancienne liste ne doit plus répondre")

            toutAvancer()
            assertTrue(champ.suggestions.any { it.nom == "Écureuil roux" }, "propositions des mammifères : ${champ.suggestions}")
        }
    }

    @Test
    fun le_releve_prend_la_suggestion_choisie_sans_re_resoudre_son_texte() = runTest {
        services.ajouterTaxRefEssai()
        // Pas d'oiseau : l'ouverture automatique de la nidification lirait les ressources de l'appli.
        services.preferences.memoiserTaxon(Taxon.INSECTE)
        val session = vm { SessionSaisieViewModel(services) }
        val releve = vm { SaisieReleveViewModel(services, session, OuvertureReleve(geometrie = GeometrieFinale("Point", null, 42.9, 1.6))) }
        // Un cd_nom que la résolution du nom « Machaon » ne donnerait pas (54303 dans le cache).
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 999999))
        val espece = releve.releve.especes.value.single()
        assertEquals("Machaon", espece.espece)
        assertEquals(999999, espece.cdNom, "la suggestion garde son cd_nom")
        assertEquals("", releve.texte, "le champ est vidé pour l'espèce suivante")
    }

    @Test
    fun un_taxon_deja_dans_le_releve_s_ajoute_sans_confirmation() = runTest {
        // Demande du 21 octobre 2026 : plus de confirmation du web.
        services.ajouterTaxRefEssai()
        services.preferences.memoiserTaxon(Taxon.INSECTE)
        val session = vm { SessionSaisieViewModel(services) }
        val releve = vm { SaisieReleveViewModel(services, session, OuvertureReleve(geometrie = GeometrieFinale("Point", null, 42.9, 1.6))) }
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(listOf(54303, 54303), releve.releve.especes.value.map { it.cdNom })
        assertEquals("", releve.texte, "le champ est vidé pour l'espèce suivante")
    }

    @Test
    fun une_dictee_non_reconnue_montre_les_especes_qui_lui_ressemblent() = runTest {
        // Demande du 23 octobre 2026 : jamais le texte entendu comme s'il était une espèce.
        services.ajouterTaxRefEssai()
        avecChamp(Taxon.OISEAU) { champ ->
            toutAvancer()
            champ.dicter(listOf("mésanges")) { fail("« mésanges » ne désigne aucune espèce à lui seul") }
            avancerJusqua { champ.entendu != null }
            assertEquals("mésanges", champ.texte, "le champ garde ce qui a été entendu")
            assertEquals("mésanges", champ.entendu)
            assertEquals(listOf(534742, 3764), champ.suggestions.map { it.cdNom }, "Mésange bleue, puis Mésange charbonnière")

            // La frappe reprend la main : les espèces proches tombent, la liste filtre de nouveau.
            champ.saisir("Mésange c")
            toutAvancer()
            assertNull(champ.entendu)
            assertEquals(listOf(3764), champ.suggestions.map { it.cdNom })
        }
    }

    @Test
    fun les_especes_proches_d_une_dictee_tombent_avec_le_groupe() = runTest {
        services.ajouterTaxRefEssai()
        avecChamp(Taxon.OISEAU) { champ ->
            toutAvancer()
            champ.dicter(listOf("mésanges")) { fail("rien à reconnaître") }
            avancerJusqua { champ.entendu != null }
            champ.choisirTaxon(Taxon.MAMMIFERE)
            assertNull(champ.entendu, "ces oiseaux ne sont pas des mammifères")
            assertTrue(champ.suggestions.none { it.cdNom == 534742 })
        }
    }

    @Test
    fun le_releve_ajoute_l_espece_choisie_parmi_les_proches_de_la_dictee() = runTest {
        services.ajouterTaxRefEssai()
        services.preferences.memoiserTaxon(Taxon.MAMMIFERE)
        val session = vm { SessionSaisieViewModel(services) }
        val releve = vm { SaisieReleveViewModel(services, session, OuvertureReleve(geometrie = GeometrieFinale("Point", null, 42.9, 1.6))) }
        avancerJusqua { releve.vocabulaire().isNotEmpty() }
        releve.dicter(listOf("écureuils"))
        avancerJusqua { releve.champ.entendu != null }
        assertTrue(releve.releve.especes.value.isEmpty(), "rien n'est ajouté sans choix")
        val ecureuil = releve.suggestions.single()
        assertEquals(61153, ecureuil.cdNom)
        releve.choisirSuggestion(ecureuil)
        assertEquals(listOf(61153), releve.releve.especes.value.map { it.cdNom })
        assertNull(releve.champ.entendu)
        assertEquals("", releve.texte, "le champ est vidé pour l'espèce suivante")
    }

    @Test
    fun la_saisie_mono_pose_l_espece_reconnue_au_son_sous_son_nom() = runTest {
        services.ajouterTaxRefEssai()
        jeuParDefaut()
        services.preferences.memoiserTaxon(Taxon.OISEAU)
        val session = vm { SessionSaisieViewModel(services) }
        val mono = vm { SaisieMonoViewModel(services, session, null) }
        avancerJusqua { mono.vocabulaire().isNotEmpty() }
        mono.dicter(listOf("pic et pêche"))
        avancerJusqua { mono.champ.recherche.statut is TaxRefStatut.Trouve }
        assertEquals(3611, (mono.champ.recherche.statut as TaxRefStatut.Trouve).cdNom)
        assertEquals("Pic épeiche", mono.champ.texte, "le nom proposé, pas ce qui a été entendu")
    }

    /** Frappe trouvée ou refusée, choix d'une proposition, dictée, au relevé comme en saisie mono : le
     *  cache TaxRef reste identique, octet pour octet. */
    @Test
    fun aucun_chemin_de_saisie_n_ecrit_dans_le_cache_taxref() = runTest {
        services.ajouterTaxRefEssai()
        jeuParDefaut()
        // Pas d'oiseau : l'ouverture automatique de la nidification lirait les ressources de l'appli.
        services.preferences.memoiserTaxon(Taxon.INSECTE)
        val avant = essai.contenuCacheTaxRef()
        assertTrue(avant.isNotEmpty())
        val session = vm { SessionSaisieViewModel(services) }

        val releve = vm { SaisieReleveViewModel(services, session, OuvertureReleve(geometrie = GeometrieFinale("Point", null, 42.9, 1.6))) }
        releve.saisir("Machaon")
        releve.champ.recherche.ligne.first { (it.statut as? TaxRefStatut.Trouve)?.cdNom == 54303 }
        releve.saisir("Papillon inconnu")
        releve.champ.recherche.ligne.first { it.statut == TaxRefStatut.NonTrouve }
        releve.dicter(listOf("Papillon inconnu", "Machaon"))
        releve.releve.especes.first { it.size == 1 }
        // Le Machaon est déjà dans le relevé : il s'ajoute une seconde fois.
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        releve.releve.especes.first { it.size == 2 }

        val mono = vm { SaisieMonoViewModel(services, session, null) }
        mono.saisir("Papillon inconnu")
        mono.champ.recherche.ligne.first { it.statut == TaxRefStatut.NonTrouve }
        mono.dicter(listOf("Papillon inconnu", "Machaon"))
        mono.champ.recherche.ligne.first { (it.statut as? TaxRefStatut.Trouve)?.cdNom == 54303 }
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        mono.demarrer()
        assertTrue(mono.modeActif)

        assertEquals(avant, essai.contenuCacheTaxRef(), "le cache TaxRef n'a pas bougé")
    }

    @Test
    fun la_saisie_mono_prend_la_suggestion_choisie_sans_re_resoudre_son_texte() = runTest {
        services.ajouterTaxRefEssai()
        jeuParDefaut()
        val session = vm { SessionSaisieViewModel(services) }
        val mono = vm { SaisieMonoViewModel(services, session, null) }
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 999999))
        assertEquals("Machaon", mono.champ.texte)
        assertEquals(999999, (mono.champ.recherche.statut as TaxRefStatut.Trouve).cdNom)
        mono.demarrer()
        assertEquals(999999, mono.saisie.modele.value.cdNom, "la série porte le cd_nom choisi")
        assertTrue(mono.modeActif)
    }

    @Test
    fun sans_jeu_de_donnees_le_releve_commence_par_ses_details_et_ne_se_termine_pas() = runTest {
        services.ajouterTaxRefEssai()
        services.preferences.memoiserTaxon(Taxon.INSECTE)
        val session = vm { SessionSaisieViewModel(services) }
        val releve = vm { SaisieReleveViewModel(services, session, OuvertureReleve(geometrie = GeometrieFinale("Point", null, 42.9, 1.6))) }
        releve.afficher()
        assertTrue(releve.details != null, "les détails s'ouvrent : le jeu est obligatoire")
        releve.fermerDetails()
        releve.terminer()
        assertTrue(releve.details != null, "pas de fin sans jeu : les détails se rouvrent")
        assertEquals(EvenementReleve.Message("Choisissez un jeu de données"), releve.evenements.first())
    }

    @Test
    fun avec_un_jeu_par_defaut_le_releve_s_ouvre_sur_ses_especes() = runTest {
        services.ajouterTaxRefEssai()
        services.preferences.memoiserTaxon(Taxon.INSECTE)
        jeuParDefaut()
        val session = vm { SessionSaisieViewModel(services) }
        val releve = vm { SaisieReleveViewModel(services, session, OuvertureReleve(geometrie = GeometrieFinale("Point", null, 42.9, 1.6))) }
        releve.afficher()
        assertNull(releve.details)
        assertEquals(12, releve.releve.idDatasetEffectif())
    }

    @Test
    fun sans_jeu_de_donnees_la_serie_mono_ne_demarre_pas() = runTest {
        services.ajouterTaxRefEssai()
        val session = vm { SessionSaisieViewModel(services) }
        val mono = vm { SaisieMonoViewModel(services, session, null) }
        assertTrue(mono.details != null, "les détails s'ouvrent d'emblée")
        mono.fermerDetails()
        mono.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        mono.demarrer()
        assertTrue(!mono.modeActif, "la série attend son jeu de données")
        assertTrue(mono.details != null)
    }

    @Test
    fun un_champ_obligatoire_d_espece_ouvre_la_caracterisation_et_se_montre_en_rouge() = runTest {
        // 21 octobre : sur certains serveurs, un champ additionnel d'occurrence est obligatoire, sans
        // valeur par défaut ; le web montre le formulaire du taxon avant de l'enregistrer.
        services.ajouterTaxRefEssai()
        services.preferences.memoiserTaxon(Taxon.INSECTE)
        jeuParDefaut()
        services.config.additionalFieldsOcctaxJson = listeVersJson(
            AdditionalFieldDef.serializer(),
            listOf(
                AdditionalFieldDef(1, "code_luf", "Code LUF", WidgetType.NUMBER, required = true, objectsCode = listOf("OCCTAX_OCCURENCE"), datasetsIds = listOf(12)),
                AdditionalFieldDef(2, "remarque", "Remarque", WidgetType.TEXT, required = true, defaultValue = "RAS", objectsCode = listOf("OCCTAX_DENOMBREMENT")),
            ),
        )
        val session = vm { SessionSaisieViewModel(services) }
        val releve = vm { SaisieReleveViewModel(services, session, OuvertureReleve(geometrie = GeometrieFinale("Point", null, 42.9, 1.6))) }
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(EvenementReleve.Caracterisation(0, comportementSeul = false), releve.evenements.first())
        val requis = releve.requis()
        val espece = releve.releve.especes.value.single()
        assertTrue(requis.caracterisationIncomplete(espece), "le code LUF reste à remplir")
        assertFalse(requis.denombrementIncomplet(espece), "la remarque a une valeur par défaut")
        assertFalse(requis.caracterisationIncomplete(espece.copy(additionalFieldsOccurrence = mapOf("code_luf" to "12"))))
    }
}
