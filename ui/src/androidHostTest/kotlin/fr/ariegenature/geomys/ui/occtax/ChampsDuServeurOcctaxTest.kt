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

import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.model.WidgetType
import fr.ariegenature.geomys.occtax.DonneesDetailsReleve
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.store.TaxrefRestriction
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Les règles du formulaire OccTax du web que les écrans d'une espèce et les détails du relevé
 *  appliquent désormais : listes requises, preuves d'existence, champs additionnels du jeu de
 *  données du relevé. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChampsDuServeurOcctaxTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services
    private val merle = EspeceReleve(Taxon.OISEAU, "Merle noir", 4117)
    private val tous = listOf(TaxrefRestriction("all", "all"))

    @BeforeTest
    fun preparer() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.nomenclatures.setAll(
            mapOf(
                "METH_OBS" to listOf(NomValeur(21, "Vu", tous, "0")),
                "ETA_BIO" to listOf(NomValeur(31, "Observé vivant", tous, "2")),
                "PREUVE_EXIST" to listOf(NomValeur(41, "Oui", tous, "1"), NomValeur(42, "Non", tous, "2")),
                "SEXE" to listOf(NomValeur(51, "Femelle", tous, "2")),
                "STADE_VIE" to listOf(NomValeur(61, "Adulte", tous, "2")),
                "OBJ_DENBR" to listOf(NomValeur(71, "Individu", tous, "IND")),
            ),
        )
    }

    @AfterTest
    fun nettoyer() {
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    private fun caracterisation(espece: EspeceReleve = merle) =
        CaracterisationViewModel(services, espece, comportementSeul = false, idDataset = null)

    @Test
    fun le_determinateur_par_defaut_est_l_utilisateur_connecte() {
        // Demande du 21 octobre 2026 : l'utilisateur connecté, comme le web, et non l'observateur
        // par défaut des Paramètres.
        services.config.nomUtilisateur = "MARTIN Alice"
        services.config.idRoleUtilisateur = 13
        services.config.observateurDefautId = "8"
        services.config.observateurDefautNom = "DUPONT Paul"
        assertEquals("MARTIN Alice", caracterisation().determinateur)
        assertEquals("LEBLANC Marc", caracterisation(merle.copy(determinateur = "LEBLANC Marc")).determinateur, "celui déjà saisi reste")
        services.config.nomUtilisateur = ""
        services.config.login = "amartin"
        assertEquals("amartin", caracterisation().determinateur, "sans nom connu, l'identifiant")
    }

    @Test
    fun la_technique_d_observation_et_l_etat_biologique_sont_requis() {
        val vm = caracterisation()
        assertEquals("Veuillez indiquer la technique d'observation", vm.valider().second)
        vm.positions["METH_OBS"] = 1
        assertEquals("Veuillez indiquer l'état biologique", vm.valider().second)
        vm.positions["ETA_BIO"] = 1
        assertNull(vm.valider().second)
    }

    @Test
    fun les_preuves_ne_valent_que_si_l_existence_est_prouvee() {
        val vm = caracterisation(merle.copy(techniqueObs = "21", etaBio = "31", champsOccExtra = mapOf("digital_proof" to "photo.jpg")))
        val oui = vm.avances.first { it.champ.code == "PREUVE_EXIST" }.codes.indexOf("41")
        val non = vm.avances.first { it.champ.code == "PREUVE_EXIST" }.codes.indexOf("42")

        // « Non » : les preuves sont masquées, et vidées à la validation.
        vm.positions["PREUVE_EXIST"] = non
        assertFalse(vm.preuvesOuvertes)
        val (sansPreuve, _) = vm.valider()
        assertNull(assertNotNull(sansPreuve).champsOccExtra["digital_proof"])

        // « Oui » : une preuve est exigée, et la preuve numérique doit être une adresse.
        vm.positions["PREUVE_EXIST"] = oui
        assertTrue(vm.preuvesOuvertes)
        assertEquals(CaracterisationViewModel.PREUVE_ADRESSE, vm.valider().second)
        vm.textesPreuves["digital_proof"] = ""
        assertEquals(CaracterisationViewModel.PREUVE_REQUISE, vm.valider().second)
        vm.textesPreuves["digital_proof"] = "https://exemple.org/photo.jpg"
        assertEquals("https://exemple.org/photo.jpg", vm.valider().first?.champsOccExtra?.get("digital_proof"))
    }

    @Test
    fun le_denombrement_exige_stade_sexe_et_objet() {
        val vm = DenombrementViewModel(services, merle, idDataset = null)
        val carte = vm.cartes.single()
        // Le premier champ requis vide, dans l'ordre du web : l'objet du dénombrement, au-dessus des
        // effectifs ; puis le stade de vie et le sexe, dessous.
        assertEquals(listOf("OBJ_DENBR", "TYP_DENBR"), vm.champsAvantEffectifs.map { it.champ.code })
        assertEquals(listOf("STADE_VIE", "SEXE"), vm.champsApresEffectifs.map { it.champ.code })
        assertEquals("Veuillez indiquer l'objet du dénombrement", vm.valider().second)
        carte.positions["OBJ_DENBR"] = 1
        assertEquals("Veuillez indiquer le stade de vie", vm.valider().second)
        vm.champs.forEach { carte.positions[it.champ.code] = 1 }
        // Effectifs requis, et le max pas sous le min : un message, pas de correction.
        vm.saisirMin(carte, "")
        assertEquals("Renseignez le nombre min", vm.valider().second)
        vm.saisirMin(carte, "5")
        carte.max = "3"
        assertEquals("Le dénombrement maximum ne peut pas être inférieur au dénombrement minimum", vm.valider().second)
        carte.max = "5"
        // Effectif 0 permis, comme le web ; le max suit le min qu'il égalait.
        vm.saisirMin(carte, "0")
        val (liste, message) = vm.valider()
        assertNull(message)
        assertEquals(0, assertNotNull(liste).single().nombreMin)
        assertEquals(0, liste.single().nombreMax)
    }

    @Test
    fun les_champs_du_releve_suivent_le_jeu_de_donnees_choisi() {
        fun champ(nom: String, datasets: List<Int>) = AdditionalFieldDef(
            idField = nom.length, fieldName = nom, fieldLabel = nom, widget = WidgetType.TEXT, required = true,
            objectsCode = listOf(AdditionalFieldsObject.RELEVE), datasetsIds = datasets,
        )
        val donnees = DonneesDetailsReleve(
            titre = "Détails du relevé",
            datasets = listOf(7 to "Jeu 7", 12 to "Jeu 12"), idDataset = 7, nomDataset = null,
            observateurs = listOf(3 to "MARTIN Alice"), idsObservateurs = listOf(3), nomsObservateurs = listOf("MARTIN Alice"),
            defsReleve = listOf(champ("commun", emptyList()), champ("hauteur_eau", listOf(12))),
            additionnels = emptyMap(), formFieldsJson = "", typGrp = "", commentaire = "",
            dateDebut = null, dateFin = null, heures = false, avecFin = false, cdHab = null, habitatLabel = null,
            champsExtra = emptyMap(),
        )
        val etat = EtatDetailsReleve(donnees, services.nomenclatures, MainScope()) { emptyList() }
        assertEquals(listOf("commun"), etat.additionnels?.defsAffiches?.map { it.fieldName })
        etat.additionnels?.saisirTexte("commun", "gardé")

        etat.choisirDataset(1)

        assertEquals(listOf("commun", "hauteur_eau"), etat.additionnels?.defsAffiches?.map { it.fieldName })
        assertEquals("gardé", etat.additionnels?.collecter()?.get("commun"), "un champ commun garde sa valeur")
        assertEquals("Champs obligatoires à renseigner : hauteur_eau", etat.valider().second)
    }

    private fun donneesReleve() = DonneesDetailsReleve(
        titre = "Détails du relevé",
        datasets = listOf(7 to "Jeu 7"), idDataset = 7, nomDataset = null,
        observateurs = listOf(3 to "MARTIN Alice"), idsObservateurs = listOf(3), nomsObservateurs = listOf("MARTIN Alice"),
        defsReleve = emptyList(), additionnels = emptyMap(), formFieldsJson = "", typGrp = "", commentaire = "",
        dateDebut = JOUR_1, dateFin = null, heures = true, avecFin = true, cdHab = null, habitatLabel = null,
        champsExtra = emptyMap(),
    )

    @Test
    fun dates_et_heures_se_recopient_tant_qu_on_n_y_a_pas_touche() {
        val etat = EtatDetailsReleve(donneesReleve(), services.nomenclatures, MainScope()) { emptyList() }
        // Le « + » du web, fermé au départ : la date de fin et les heures dessous.
        assertFalse(etat.plusOuvert)
        assertEquals(JOUR_1, etat.fin)
        etat.changerDebut(JOUR_2)
        assertEquals(JOUR_2, etat.fin, "synchronizeMax : la fin suit le début")
        etat.basculerPlus()
        assertTrue(etat.plusOuvert)
        etat.changerFin(JOUR_3)
        etat.changerDebut(JOUR_1)
        assertEquals(JOUR_3, etat.fin, "la fin touchée ne suit plus")
        etat.saisirHeureDebut("8:30")
        assertEquals("8:30", etat.heureFin)
        etat.saisirHeureFin("9:00")
        etat.saisirHeureDebut("8:45")
        assertEquals("9:00", etat.heureFin)
        val (resultat, message) = etat.valider()
        assertNull(message)
        assertEquals("8:45", assertNotNull(resultat).heureDebut)
        assertEquals("9:00", resultat.heureFin)
    }

    @Test
    fun le_jeu_de_donnees_efface_bloque_comme_sur_le_web() {
        val etat = EtatDetailsReleve(donneesReleve(), services.nomenclatures, MainScope()) { emptyList() }
        etat.effacerDataset()
        assertEquals("Choisissez un jeu de données", etat.valider().second)
    }

    @Test
    fun aucun_habitat_trouve_s_affiche_a_partir_de_trois_lettres() = runTest {
        val etat = EtatDetailsReleve(donneesReleve(), services.nomenclatures, this) { emptyList() }
        etat.saisirHabitat("pr")
        advanceTimeBy(401)
        runCurrent()
        assertTrue(etat.aucunHabitat)
        assertFalse(etat.aucunHabitatAffiche, "le web ne le dit qu'à partir de 3 caractères")
        etat.saisirHabitat("pra")
        advanceTimeBy(399)
        runCurrent()
        assertFalse(etat.aucunHabitatAffiche, "pas pendant l'attente de 400 ms")
        advanceTimeBy(2)
        runCurrent()
        assertTrue(etat.aucunHabitatAffiche)
        etat.fermer()
    }

    private companion object {
        const val JOUR_1 = 1_792_400_000_000L
        const val JOUR_2 = JOUR_1 + 86_400_000L
        const val JOUR_3 = JOUR_1 + 2 * 86_400_000L
    }
}
