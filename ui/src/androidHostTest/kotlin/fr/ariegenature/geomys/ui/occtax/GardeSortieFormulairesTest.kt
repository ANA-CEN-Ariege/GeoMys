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

package fr.ariegenature.geomys.ui.occtax

import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.occtax.DonneesDetailsReleve
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import fr.ariegenature.geomys.ui.occhab.HabitatOccHabViewModel
import fr.ariegenature.geomys.ui.occhab.SessionOccHabViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

/** La garde de sortie des formulaires : elle ne demande rien pour un formulaire intact, et un
 *  dénombrement abandonné rend à l'espèce les médias qu'on lui avait retirés. Le retour, le
 *  balayage et la maison quittaient ces écrans sans rien demander, et le dénombrement effaçait le
 *  fichier d'un média avant même sa validation. */
@OptIn(ExperimentalCoroutinesApi::class)
class GardeSortieFormulairesTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services
    private val systeme = FileSystem.SYSTEM
    private val merle = EspeceReleve(Taxon.OISEAU, "Merle noir", 4117)

    @BeforeTest
    fun preparer() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun nettoyer() {
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    private fun media(nom: String): String {
        systeme.createDirectories(services.dossierMedias)
        val fichier = services.dossierMedias / nom
        systeme.write(fichier) { writeUtf8("photo") }
        return fichier.toString()
    }

    private fun existe(uri: String) = systeme.exists(uri.toPath())

    @Test
    fun une_caracterisation_n_est_modifiee_qu_une_fois_un_champ_change() {
        val vm = CaracterisationViewModel(services, merle, comportementSeul = false, idDataset = null)
        assertFalse(vm.modifie())
        vm.commentaire = "Chante perché"
        assertTrue(vm.modifie())
    }

    @Test
    fun les_details_du_releve_ne_sont_modifies_qu_une_fois_une_saisie_changee() {
        // Devenus un écran plein le 21 octobre, ils passent par la même garde que la caractérisation.
        val donnees = DonneesDetailsReleve(
            titre = "Détails du relevé",
            datasets = listOf(7 to "Jeu 7", 12 to "Jeu 12"), idDataset = 7, nomDataset = null,
            observateurs = listOf(3 to "MARTIN Alice", 4 to "DUPONT Paul"), idsObservateurs = listOf(3), nomsObservateurs = listOf("MARTIN Alice"),
            defsReleve = emptyList(), additionnels = emptyMap(), formFieldsJson = "", typGrp = "", commentaire = "",
            dateDebut = 1_792_400_000_000L, dateFin = null, heures = true, avecFin = true, cdHab = null, habitatLabel = null,
            champsExtra = emptyMap(),
        )
        fun etat() = EtatDetailsReleve(donnees, services.nomenclatures, MainScope()) { emptyList() }
        val e = etat()
        assertFalse(e.modifie(), "intact")
        e.basculerPlus()
        assertFalse(e.modifie(), "le « + » ne change aucune valeur")
        e.choisirDataset(1)
        assertTrue(e.modifie())
        assertTrue(etat().apply { ajouterObservateur(1) }.modifie())
        assertTrue(etat().apply { commentaire = "Vent fort" }.modifie())
        assertTrue(etat().apply { saisirHeureDebut("8:30") }.modifie())
    }

    @Test
    fun un_denombrement_n_est_modifie_qu_une_fois_un_effectif_change() {
        val vm = DenombrementViewModel(services, merle, idDataset = null)
        assertFalse(vm.modifie())
        vm.saisirMin(vm.cartes[0], "3")
        assertTrue(vm.modifie())
    }

    @Test
    fun un_habitat_n_est_modifie_qu_une_fois_un_champ_change() {
        val vm = HabitatOccHabViewModel(services, SessionOccHabViewModel(), habitatId = null)
        assertFalse(vm.modifie())
        vm.saisirHabitat("P")
        assertTrue(vm.modifie())
    }

    @Test
    fun non_renseigne_vide_la_valeur_d_un_habitat_edite() {
        // « — Non renseigné — » choisi dans une liste affichée : la valeur est vidée. L'habitat
        // gardait l'ancienne, faute de distinguer un champ vidé d'un champ masqué.
        services.nomenclatures.setAll(mapOf("ABONDANCE_HAB" to listOf(NomValeur(80, "Abondant", cd = "1"))))
        val sessionVm = SessionOccHabViewModel()
        val habitat = OccHabHabitat(id = "h1", cdHab = 629, habitatLabel = "Prairie", idNomTechniqueCollecte = 60, idNomAbondance = 80)
        sessionVm.session.reprendreStation(OccHabStation(id = "s1", latitude = 42.9, longitude = 1.4, habitats = listOf(habitat)))
        val vm = HabitatOccHabViewModel(services, sessionVm, habitatId = "h1")
        val abondance = assertNotNull(vm.abondance)
        assertEquals("Abondant", abondance.liste.libelles[abondance.position])

        abondance.position = 0
        vm.valider()

        assertNull(sessionVm.session.station.habitats.single().idNomAbondance)
    }

    @Test
    fun un_media_retire_n_est_efface_qu_a_la_validation() {
        // Sans nomenclatures en cache, les listes requises du dénombrement bloqueraient la validation.
        services.config.formFieldsJson = """{"life_stage":false,"sex":false,"obj_count":false}"""
        val photo = media("m1.jpg")
        val vm = DenombrementViewModel(services, merle.copy(mediaUrisCounting0 = listOf(photo)), idDataset = null)

        vm.retirerMedia(vm.cartes[0], photo)
        assertTrue(existe(photo), "le fichier reste tant que rien n'est validé")
        assertTrue(vm.modifie())

        assertNotNull(vm.valider().first)
        assertFalse(existe(photo), "validé, le média retiré s'efface")
    }

    @Test
    fun quitter_sans_enregistrer_rend_les_medias_retires_et_efface_les_importes() {
        val ancienne = media("m1.jpg")
        val nouvelle = media("m2.jpg")
        val vm = DenombrementViewModel(services, merle.copy(mediaUrisCounting0 = listOf(ancienne)), idDataset = null)
        vm.retirerMedia(vm.cartes[0], ancienne)
        vm.ajouterMedias(0, listOf(nouvelle))

        vm.abandonner()

        assertTrue(existe(ancienne), "le média retiré reste à l'espèce")
        assertFalse(existe(nouvelle), "le média importé ici s'efface")
    }
}
