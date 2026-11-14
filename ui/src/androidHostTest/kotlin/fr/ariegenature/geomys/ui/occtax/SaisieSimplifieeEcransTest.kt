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
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals(EvenementReleve.FicheEspece(0, volet = null), releve.evenements.first())
        // Ajoutée ensuite, une deuxième espèce ouvre aussi sa fiche.
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(EvenementReleve.FicheEspece(1, volet = null), releve.evenements.first())
        releve.ouvrirFiche(0)
        assertEquals(EvenementReleve.FicheEspece(0), releve.evenements.first(), "le crayon : la fiche de l'observation")
    }

    @Test
    fun un_champ_obligatoire_ouvre_details_par_dessus_l_ecran_de_l_espece() = runTest {
        champObligatoire()
        val releve = releve(ModeSaisie.SIMPLIFIE)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(EvenementReleve.FicheEspece(0, VoletEspece.DETAILS), releve.evenements.first())
    }

    @Test
    fun en_mode_pro_rien_ne_change() = runTest {
        champObligatoire()
        val releve = releve(ModeSaisie.PRO)
        assertFalse(releve.simplifie)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        assertEquals(EvenementReleve.Caracterisation(0, comportementSeul = false), releve.evenements.first())
    }

    @Test
    fun details_ecrit_l_espece_et_l_ecran_de_l_espece_le_resume() = runTest {
        nomenclatures()
        champObligatoire()
        val releve = releve(ModeSaisie.SIMPLIFIE)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        val fiche = FicheEspeceViewModel(services, releve.releve, 0)
        val neuve = releve.releve.especes.value.single()
        assertEquals("1 · Inconnu · Indéterminé · Vivant", fiche.resumeDetails(neuve), "jamais ouverte : les défauts du serveur")
        assertEquals("Non renseigné", fiche.resumeComportement(neuve))
        assertTrue(fiche.detailsIncomplets(neuve))

        val details = DetailsEspeceViewModel(services, neuve, 12)
        assertEquals(listOf("STADE_VIE", "SEXE", "ETA_BIO"), details.champs.map { it.champ.code })
        assertFalse(details.modifie())
        details.saisirNombre("3")
        details.positions["STADE_VIE"] = details.champs[0].position("2")
        details.positions["SEXE"] = details.champs[1].position("3")
        val (refusee, message) = details.valider()
        assertNull(refusee, "le code LUF est obligatoire")
        assertTrue(message.orEmpty().contains("Code LUF"), message)
        details.additionnelsOccurrence!!.saisirTexte("code_luf", "7")
        // Une photo rattachée pendant que « Détails » est ouvert : son ✓ ne l'écrase pas (M50).
        fiche.ajouterMedias(listOf("file:///essai/pendant-details.jpg"))
        val (transformation, _) = details.valider()
        releve.releve.modifier(0) { transformation!!(it) }

        val apres = releve.releve.especes.value.single()
        assertEquals(3, apres.nombre)
        assertEquals("2", apres.stadeVie)
        assertEquals("3", apres.sexe)
        assertEquals("4", apres.etaBio, "laissé au défaut : le défaut est retenu, comme en mode pro")
        assertEquals("7", apres.additionalFieldsOccurrence["code_luf"])
        assertEquals(listOf("file:///essai/pendant-details.jpg"), apres.mediaUrisCounting0, "la photo arrivée entre-temps reste")
        assertEquals("", apres.techniqueObs, "hors des écrans simplifiés : rien n'est écrit")
        assertEquals("3 · Adulte · Mâle · Vivant", fiche.resumeDetails(apres))
        assertFalse(fiche.detailsIncomplets(apres))
        // Enregistré au fil de l'eau : l'observation de la session porte les détails.
        val obs = releve.session.observations.value.single { it.releveId == releve.releve.releveId }
        assertEquals(3, obs.nombre)
        assertEquals("2", obs.stadeVie)
    }

    /** 14 novembre 2026 : pour une plante, ni sexe dans « Détails », ni bouton « Comportement ». */
    @Test
    fun une_plante_n_a_ni_sexe_ni_comportement() = runTest {
        nomenclatures()
        services.preferences.memoiserTaxon(Taxon.PLANTE)
        val releve = releve(ModeSaisie.SIMPLIFIE)
        releve.choisirSuggestion(SuggestionTaxon("Ortie dioïque", 128268))
        val ortie = releve.releve.especes.value.single()
        assertEquals(Taxon.PLANTE, ortie.taxon)
        assertEquals(listOf("STADE_VIE", "ETA_BIO"), DetailsEspeceViewModel(services, ortie, 12).champs.map { it.champ.code })
        val fiche = FicheEspeceViewModel(services, releve.releve, 0)
        assertFalse(fiche.comportementVisible)
        assertEquals("1 · Inconnu · Vivant", fiche.resumeDetails(ortie), "ni sexe dans le résumé")

        // Un insecte du même relevé garde tout.
        releve.choisirTaxon(Taxon.INSECTE)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        val machaon = releve.releve.especes.value[1]
        assertEquals(listOf("STADE_VIE", "SEXE", "ETA_BIO"), DetailsEspeceViewModel(services, machaon, 12).champs.map { it.champ.code })
        assertTrue(FicheEspeceViewModel(services, releve.releve, 1).comportementVisible)
    }

    @Test
    fun le_commentaire_s_ecrit_dans_l_observation_enregistree() = runTest {
        val releve = releve(ModeSaisie.SIMPLIFIE)
        releve.choisirSuggestion(SuggestionTaxon("Machaon", 54303))
        val fiche = FicheEspeceViewModel(services, releve.releve, 0)
        assertTrue(fiche.commentaireVisible, "comment_occ est montré par défaut, comme sur le web")
        assertEquals("Aucun", fiche.resumeCommentaire(releve.releve.especes.value.single()))

        val commentaire = CommentaireEspeceViewModel(releve.releve.especes.value.single())
        assertFalse(commentaire.modifie())
        commentaire.texte = "Sur une ombellifère "
        assertTrue(commentaire.modifie())
        // Idem pour « Commentaire » : il n'écrit que le commentaire de l'espèce courante (M50).
        fiche.ajouterMedias(listOf("file:///essai/pendant-commentaire.m4a"))
        releve.releve.modifier(0, commentaire.valider())

        val apres = releve.releve.especes.value.single()
        assertEquals("Sur une ombellifère", fiche.resumeCommentaire(apres))
        assertEquals(listOf("file:///essai/pendant-commentaire.m4a"), apres.mediaUrisCounting0)
        val obs = releve.session.observations.value.single { it.releveId == releve.releve.releveId }
        assertEquals("Sur une ombellifère", obs.notes, "le commentaire de l'occurrence")
        assertNull(obs.commentReleve, "pas celui du relevé")
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
}
