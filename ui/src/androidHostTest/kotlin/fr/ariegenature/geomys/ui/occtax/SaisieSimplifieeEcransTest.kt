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

// Propre au portage : la saisie multi-taxons simplifiée (demande du 14 novembre 2026), de l'espèce ajoutée
// à son écran, et de « Détails » à l'espèce enregistrée.

package fr.ariegenature.geomys.ui.occtax

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.occtax.GeometrieFinale
import fr.ariegenature.geomys.occtax.OuvertureReleve
import fr.ariegenature.geomys.outils.ajouterTaxRefEssai
import fr.ariegenature.geomys.store.ModeSaisie
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.store.SuggestionTaxon
import fr.ariegenature.geomys.store.TaxrefRestriction
import fr.ariegenature.geomys.store.io.listeVersJson
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SaisieSimplifieeEcransTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services
    private val magasin = ViewModelStore()

    @BeforeTest
    fun preparer() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { services.ajouterTaxRefEssai() }
        // Pas d'oiseau : la période de nidification lirait les ressources de l'appli.
        services.preferences.memoiserTaxon(Taxon.INSECTE)
        services.config.apply {
            datasetsCacheJson = """[{"id":12,"nom":"JDD","moduleCodes":["OCCTAX"]}]"""
            datasetsCreablesOcctax = setOf(12)
            idDataset = "12"
        }
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

    private fun releve(mode: ModeSaisie): SaisieReleveViewModel {
        services.preferences.modeSaisie = mode
        val session = vm { SessionSaisieViewModel(services) }
        return vm { SaisieReleveViewModel(services, session, OuvertureReleve(geometrie = GeometrieFinale("Point", null, 42.9, 1.6))) }
    }

    /** Un champ additionnel d'occurrence obligatoire, sans valeur par défaut, pour le jeu 12. */
    private fun champObligatoire() {
        services.config.additionalFieldsOcctaxJson = listeVersJson(
            AdditionalFieldDef.serializer(),
            listOf(AdditionalFieldDef(1, "code_luf", "Code LUF", WidgetType.NUMBER, required = true, objectsCode = listOf("OCCTAX_OCCURENCE"), datasetsIds = listOf(12))),
        )
    }

    /** Les valeurs des nomenclatures de « Détails » et du comportement, pour tous les taxons, et leurs
     *  défauts. */
    private fun nomenclatures() {
        val tous = listOf(TaxrefRestriction("all", "all"))
        fun v(id: Int, libelle: String) = NomValeur(id, libelle, tous)
        services.nomenclatures.setAll(
            mapOf(
                "STADE_VIE" to listOf(v(1, "Inconnu"), v(2, "Adulte")),
                "SEXE" to listOf(v(5, "Indéterminé"), v(3, "Mâle")),
                "ETA_BIO" to listOf(v(4, "Vivant"), v(6, "Mort")),
                "OCC_COMPORTEMENT" to listOf(v(12, "Chant")),
            ),
        )
        services.nomenclatures.setDefauts(mapOf("STADE_VIE" to "1", "SEXE" to "5", "ETA_BIO" to "4"))
    }

    @Test
    fun en_mode_simplifie_l_espece_ajoutee_ouvre_son_ecran() = runTest {
        val releve = releve(ModeSaisie.SIMPLIFIE)
        assertTrue(releve.simplifie)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(EvenementReleve.FicheEspece(0), releve.evenements.first())
        // Ajoutée ensuite, une deuxième espèce ouvre aussi sa fiche.
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(EvenementReleve.FicheEspece(1), releve.evenements.first())
        releve.ouvrirFiche(0)
        assertEquals(EvenementReleve.FicheEspece(0), releve.evenements.first(), "le crayon : la fiche de l'observation")
    }

    @Test
    fun un_champ_obligatoire_n_ouvre_rien_d_autre_que_la_fiche_qui_le_porte() = runTest {
        champObligatoire()
        val releve = releve(ModeSaisie.SIMPLIFIE)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(EvenementReleve.FicheEspece(0), releve.evenements.first())
        assertNotNull(FicheEspeceViewModel(services, releve.releve, 0).additionnelsOccurrence)
    }

    /** 16 novembre 2026 : en mode pro aussi, la fiche s'ouvre à chaque espèce ; elle réunit dénombrements
     *  et caractérisation, écrits ensemble à son ✓. */
    @Test
    fun en_mode_pro_la_fiche_reunit_denombrement_et_caracterisation() = runTest {
        nomenclatures()
        services.nomenclatures.setAll(
            listOf("METH_OBS", "OBJ_DENBR").associateWith { listOf(NomValeur(90, "Vu", listOf(TaxrefRestriction("all", "all")))) } +
                listOf("STADE_VIE" to (1 to "Inconnu"), "SEXE" to (5 to "Indéterminé"), "ETA_BIO" to (4 to "Vivant"))
                    .associate { (code, v) -> code to listOf(NomValeur(v.first, v.second, listOf(TaxrefRestriction("all", "all")))) },
        )
        services.nomenclatures.setDefauts(mapOf("STADE_VIE" to "1", "SEXE" to "5", "ETA_BIO" to "4", "METH_OBS" to "90", "OBJ_DENBR" to "90"))
        val releve = releve(ModeSaisie.PRO)
        assertFalse(releve.simplifie)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(EvenementReleve.FicheEspece(0), releve.evenements.first(), "la fiche s'ouvre à chaque espèce, comme en mode simplifié")
        releve.ouvrirFiche(0)
        assertEquals(EvenementReleve.FicheEspece(0), releve.evenements.first(), "le crayon : la fiche de l'observation")

        champObligatoire()
        releve.choisirSuggestion(SuggestionTaxon("Flambé", 54496))
        assertEquals(EvenementReleve.FicheEspece(1), releve.evenements.first())

        val espece = releve.releve.especes.value[1]
        val denombrement = DenombrementViewModel(services, espece, 12)
        val caracterisation = CaracterisationViewModel(services, espece, comportementSeul = false, idDataset = 12)
        denombrement.saisirMin(denombrement.cartes[0], "3")
        val (refusee, message) = validerFichePro(caracterisation, denombrement)
        assertNull(refusee)
        assertTrue(message.orEmpty().contains("Code LUF"), message)
        caracterisation.additionnels!!.saisirTexte("code_luf", "7")
        caracterisation.commentaire = "Sur un prunellier"
        val (validee, _) = validerFichePro(caracterisation, denombrement)
        val saisie = assertNotNull(validee)
        assertEquals(3, saisie.nombre)
        assertEquals("7", saisie.additionalFieldsOccurrence["code_luf"])
        assertEquals("Sur un prunellier", saisie.notes)
        assertEquals("90", saisie.techniqueObs, "la caractérisation complète du mode pro")
    }

    /** 16 novembre 2026 : tous les champs sont sur la fiche, écrits à son ✓. */
    @Test
    fun la_fiche_porte_tous_les_champs_et_les_ecrit_a_sa_validation() = runTest {
        nomenclatures()
        champObligatoire()
        val releve = releve(ModeSaisie.SIMPLIFIE)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        val fiche = FicheEspeceViewModel(services, releve.releve, 0)
        assertEquals(listOf("STADE_VIE", "SEXE", "ETA_BIO"), fiche.champsDetails.map { it.champ.code })
        assertTrue(fiche.minVisible && fiche.maxVisible, "nombre min et nombre max")
        assertEquals("1" to "1", fiche.min to fiche.max)
        assertTrue(fiche.comportementVisible)
        assertTrue(fiche.commentaireVisible, "comment_occ est montré par défaut, comme sur le web")
        assertFalse(fiche.modifie())

        // Le max suit le min tant qu'il n'est pas saisi à part.
        fiche.saisirMin("3")
        assertEquals("3", fiche.max)
        fiche.ajusterMax(2)
        fiche.ajusterMin(1)
        assertEquals("4" to "5", fiche.min to fiche.max)
        fiche.positions["STADE_VIE"] = fiche.champsDetails[0].position("2")
        fiche.positions["SEXE"] = fiche.champsDetails[1].position("3")
        fiche.positions["OCC_COMPORTEMENT"] = fiche.champComportement!!.position("12")
        fiche.commentaire = "Sur une ombellifère "
        assertTrue(fiche.modifie())
        assertTrue(fiche.valider().orEmpty().contains("Code LUF"), "le code LUF est obligatoire")
        assertEquals(1, releve.releve.especes.value.single().nombre, "refusée : rien n'est écrit")
        fiche.additionnelsOccurrence!!.saisirTexte("code_luf", "7")
        // Une photo rattachée pendant que la fiche est ouverte : son ✓ ne l'écrase pas (M50).
        fiche.ajouterMedias(listOf("file:///essai/pendant-la-fiche.jpg"))
        assertNull(fiche.valider())

        val apres = releve.releve.especes.value.single()
        assertEquals(4, apres.nombre)
        assertEquals(5, apres.nombreMax)
        assertEquals("2", apres.stadeVie)
        assertEquals("3", apres.sexe)
        assertEquals("4", apres.etaBio, "laissé au défaut : le défaut est retenu, comme en mode pro")
        assertEquals("12", apres.comportement)
        assertEquals("Sur une ombellifère", apres.notes)
        assertEquals("7", apres.additionalFieldsOccurrence["code_luf"])
        assertEquals(listOf("file:///essai/pendant-la-fiche.jpg"), apres.mediaUrisCounting0, "la photo arrivée entre-temps reste")
        assertEquals("", apres.techniqueObs, "hors de la fiche : rien n'est écrit")
        // Enregistré au fil de l'eau : l'observation de la session porte la fiche.
        val obs = releve.session.observations.value.single { it.releveId == releve.releve.releveId }
        assertEquals(4, obs.nombre)
        assertEquals(5, obs.nombreMax)
        assertEquals("2", obs.stadeVie)
        assertEquals("Sur une ombellifère", obs.notes, "le commentaire de l'occurrence")
        assertNull(obs.commentReleve, "pas celui du relevé")
    }

    @Test
    fun un_maximum_sous_le_minimum_est_refuse_et_une_fiche_intacte_n_ecrit_rien() = runTest {
        nomenclatures()
        val releve = releve(ModeSaisie.SIMPLIFIE)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        val intacte = FicheEspeceViewModel(services, releve.releve, 0)
        assertNull(intacte.valider())
        assertEquals("", releve.releve.especes.value.single().etaBio, "jamais saisi : le serveur y mettra son défaut")

        val fiche = FicheEspeceViewModel(services, releve.releve, 0)
        fiche.saisirMin("5")
        fiche.saisirMax("3")
        assertEquals("Le dénombrement maximum ne peut pas être inférieur au dénombrement minimum", fiche.valider())
        fiche.saisirMax("")
        assertEquals("Renseignez le nombre max", fiche.valider())
    }

    /** 14 novembre 2026 : pour une plante, ni sexe, ni comportement. */
    @Test
    fun une_plante_n_a_ni_sexe_ni_comportement() = runTest {
        nomenclatures()
        services.preferences.memoiserTaxon(Taxon.PLANTE)
        val releve = releve(ModeSaisie.SIMPLIFIE)
        releve.choisirSuggestion(SuggestionTaxon("Ortie dioïque", 128268))
        val ortie = releve.releve.especes.value.single()
        assertEquals(Taxon.PLANTE, ortie.taxon)
        val fiche = FicheEspeceViewModel(services, releve.releve, 0)
        assertEquals(listOf("STADE_VIE", "ETA_BIO"), fiche.champsDetails.map { it.champ.code })
        assertFalse(fiche.comportementVisible)

        // Un insecte du même relevé garde tout.
        releve.choisirTaxon(Taxon.INSECTE)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        val machaon = FicheEspeceViewModel(services, releve.releve, 1)
        assertEquals(listOf("STADE_VIE", "SEXE", "ETA_BIO"), machaon.champsDetails.map { it.champ.code })
        assertTrue(machaon.comportementVisible)
    }

    @Test
    fun photo_et_audio_s_ajoutent_a_l_espece_enregistree() = runTest {
        val releve = releve(ModeSaisie.SIMPLIFIE)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        val fiche = FicheEspeceViewModel(services, releve.releve, 0)
        // Une vraie copie locale : retirée de l'espèce, elle n'est pas effacée (M51).
        val dossier = File(services.cheminMedias).apply { mkdirs() }
        val photo = File(dossier, "photo.jpg").apply { writeText("jpeg") }
        val uriPhoto = "file://${photo.absolutePath}"
        fiche.ajouterMedias(listOf(uriPhoto, "file:///essai/chant.m4a"))
        assertEquals(listOf(uriPhoto, "file:///essai/chant.m4a"), releve.releve.especes.value.single().mediaUrisCounting0)
        fiche.retirerMedia(uriPhoto)
        val obs = releve.session.observations.value.single { it.releveId == releve.releve.releveId }
        assertEquals(listOf("file:///essai/chant.m4a"), obs.mediaUrisCounting0)
        assertTrue(photo.exists(), "la copie locale reste : le balayage des orphelins s'en chargera")
    }

    /** M52 : la mise à jour d'une occurrence déjà transmise ne porte pas de média. */
    @Test
    fun photo_et_audio_sont_figes_sur_une_observation_deja_envoyee() = runTest {
        val releve = releve(ModeSaisie.SIMPLIFIE)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        val fiche = FicheEspeceViewModel(services, releve.releve, 0)
        val espece = releve.releve.especes.value.single()
        assertFalse(fiche.mediasFiges(espece))
        val obs = releve.session.observations.value.single { it.releveId == releve.releve.releveId }
        releve.session.remplacerObservationsDuReleve(releve.releve.releveId, listOf(obs.copy(envoyeeServeur = true)))
        assertTrue(fiche.mediasFiges(espece))
    }

    /** M58 : la corbeille touche le crayon ; rien ne part sans le « Supprimer » du dialogue. */
    @Test
    fun la_corbeille_d_une_espece_attend_sa_confirmation() = runTest {
        val releve = releve(ModeSaisie.SIMPLIFIE)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        releve.choisirSuggestion(SuggestionTaxon("Flambé", 54496))
        releve.demanderSuppression(0)
        assertEquals("Machaon", releve.suppressionDemandee?.espece)
        assertEquals(2, releve.releve.especes.value.size, "rien n'est supprimé avant la confirmation")
        releve.annulerSuppression()
        assertEquals(null, releve.suppressionDemandee)
        assertEquals(2, releve.releve.especes.value.size)

        releve.demanderSuppression(0)
        releve.confirmerSuppression()
        assertEquals(listOf("Flambé"), releve.releve.especes.value.map { it.espece })
        assertEquals(null, releve.suppressionDemandee)
        // Sans demande, la confirmation ne supprime rien.
        releve.confirmerSuppression()
        assertEquals(1, releve.releve.especes.value.size)
    }

    /** Demande du 15 novembre 2026 : en saisie simplifiée, les détails du relevé ne laissent ni ajouter
     *  un observateur ni choisir un habitat ; le mode pro garde les deux. */
    @Test
    fun les_details_du_releve_n_ont_ni_ajout_d_observateur_ni_habitat() = runTest {
        services.config.apply {
            observateursCacheJson = """[{"idRole":8,"nomComplet":"MARTIN Paul"},{"idRole":9,"nomComplet":"DURAND Zoé"}]"""
            observateurDefautId = "8"
            observateurDefautNom = "MARTIN Paul"
        }
        val simplifie = releve(ModeSaisie.SIMPLIFIE)
        simplifie.ouvrirDetails()
        val details = assertNotNull(simplifie.details)
        assertEquals(listOf(8 to "MARTIN Paul"), details.observateurs.toList())
        assertTrue(details.observateursFiges)
        assertFalse(details.habitatVisible)
        assertNull(details.valider().second, "le relevé reste validable")

        magasin.clear()
        val pro = releve(ModeSaisie.PRO)
        pro.ouvrirDetails()
        val detailsPro = assertNotNull(pro.details)
        assertFalse(detailsPro.observateursFiges)
        assertTrue(detailsPro.habitatVisible)
    }

    /** Demande du 15 novembre 2026 : pour un oiseau, « Chant » pose ou enlève ce comportement d'un
     *  toucher ; les autres groupes n'ont pas le bouton. */
    @Test
    fun le_bouton_chant_d_un_oiseau_pose_et_enleve_le_comportement() = runTest {
        nomenclatures()
        val releve = releve(ModeSaisie.SIMPLIFIE)
        // Ajouté sans passer par l'écran : la période de nidification lirait les ressources de l'appli.
        val merle = releve.releve.ajouter(Taxon.OISEAU, "Merle noir", null)
        val fiche = FicheEspeceViewModel(services, releve.releve, merle)
        fun espece() = releve.releve.especes.value[merle]
        assertTrue(fiche.chantVisible)
        assertFalse(fiche.chante)

        // « Chant » choisit la valeur dans le champ « Comportement » ; le ✓ de la fiche l'écrit.
        fiche.basculerChant()
        assertTrue(fiche.chante)
        assertEquals(fiche.champComportement!!.position("12"), fiche.positions["OCC_COMPORTEMENT"])
        assertEquals("", espece().comportement, "rien n'est écrit avant le ✓")
        assertNull(fiche.valider())
        assertEquals("12", espece().comportement)
        val obs = releve.session.observations.value.single { it.releveId == releve.releve.releveId }
        assertEquals("12", obs.comportement, "le comportement de l'observation enregistrée")

        val rouverte = FicheEspeceViewModel(services, releve.releve, merle)
        assertTrue(rouverte.chante)
        rouverte.basculerChant()
        assertFalse(rouverte.chante)
        assertNull(rouverte.valider())
        assertEquals("", espece().comportement)

        // Un insecte garde « Comportement », sans « Chant ».
        releve.choisirTaxon(Taxon.INSECTE)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        val machaon = FicheEspeceViewModel(services, releve.releve, 1)
        assertTrue(machaon.comportementVisible)
        assertFalse(machaon.chantVisible)

        // Un serveur sans valeur « Chant » : pas de bouton.
        services.nomenclatures.setAll(mapOf("OCC_COMPORTEMENT" to listOf(NomValeur(13, "Repos", listOf(TaxrefRestriction("all", "all"))))))
        assertFalse(FicheEspeceViewModel(services, releve.releve, merle).chantVisible)
    }

    /** Serveur aux codes atlas : en période de nidification, « Chant » pose l'indice « Mâle chanteur » ;
     *  hors période, le chant simple. L'un comme l'autre s'enlève au second toucher. */
    @Test
    fun en_periode_de_nidification_le_chant_pose_l_indice_male_chanteur() = runTest {
        val tous = listOf(TaxrefRestriction("all", "all"))
        services.nomenclatures.setAll(
            mapOf(
                "STADE_VIE" to listOf(NomValeur(1, "Inconnu", tous)),
                "SEXE" to listOf(NomValeur(5, "Indéterminé", tous)),
                "ETA_BIO" to listOf(NomValeur(4, "Vivant", tous)),
                "OCC_COMPORTEMENT" to listOf(
                    NomValeur(12, "Chant", tous, cd = "18"),
                    NomValeur(30, "2 - Mâle chanteur (ou cris de nidification) en période de reproduction", tous, cd = "CA2"),
                ),
            ),
        )
        services.nomenclatures.setDefauts(mapOf("STADE_VIE" to "1", "SEXE" to "5", "ETA_BIO" to "4"))
        val releve = releve(ModeSaisie.SIMPLIFIE)
        val merle = releve.releve.ajouter(Taxon.OISEAU, "Merle noir", 4117)
        fun espece() = releve.releve.especes.value[merle]

        val auPrintemps = FicheEspeceViewModel(services, releve.releve, merle) { true }
        auPrintemps.basculerChant()
        assertTrue(auPrintemps.chante)
        assertNull(auPrintemps.valider())
        assertEquals("30", espece().comportement)

        // Hors période, la fiche reconnaît encore l'indice posé, et l'enlève ; puis pose le chant simple.
        val enHiver = FicheEspeceViewModel(services, releve.releve, merle) { false }
        assertTrue(enHiver.chante)
        enHiver.basculerChant()
        assertFalse(enHiver.chante)
        enHiver.basculerChant()
        assertNull(enHiver.valider())
        assertEquals("12", espece().comportement)

        // Un serveur qui n'a que l'indice : pas de bouton hors période.
        services.nomenclatures.setAll(mapOf("OCC_COMPORTEMENT" to listOf(NomValeur(30, "2 - Mâle chanteur", tous, cd = "CA2"))))
        assertFalse(FicheEspeceViewModel(services, releve.releve, merle) { false }.chantVisible)
        assertTrue(FicheEspeceViewModel(services, releve.releve, merle) { true }.chantVisible)
    }
}
