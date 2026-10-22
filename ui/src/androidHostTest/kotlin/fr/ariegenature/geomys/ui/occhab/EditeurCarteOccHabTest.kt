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

// Sur le modèle d'OccHabEditeurTests de GeoMys-IOS @ 5fda84c (le moteur d'édition de la carte OccHab : ce
// qu'un tap produit, quand « Valider » devient possible, ce que « Annuler » défait), adapté au comportement de
// GeoMys-Android @ cdd5196 que porte CarteOccHabViewModel : « Annuler » au départ d'une saisie l'abandonne, une
// station sélectionnée se quitte par « Annuler » avant d'en prendre une autre. Les cas purs (poignées,
// trous, géométrie, aimantage) sont dans GeometrieEditionTest et CarteOccHabTest, côté socle. S'y ajoutent
// les corrections des défauts n° 16 et 19 de l'inventaire, présents dans GeoMys-Android.

package fr.ariegenature.geomys.ui.occhab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.occhab.CarteOccHab
import fr.ariegenature.geomys.occhab.ModeGeometrie
import fr.ariegenature.geomys.occhab.TopologiePolygone
import fr.ariegenature.geomys.outils.ajouterOccHabEssai
import fr.ariegenature.geomys.ui.ServicesEssai
import fr.ariegenature.geomys.ui.laisserFinirLeTravailDeFond
import fr.ariegenature.geomys.util.GeoJsonCoords
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class EditeurCarteOccHabTest {

    private val essai = ServicesEssai()
    private val services get() = essai.services
    private val magasin = ViewModelStore()

    /** Le fil principal de la carte : ses coroutines n'avancent que lorsque le test le pompe, comme
     *  dans l'appli où tout repasse par le fil principal. */
    private val principal = StandardTestDispatcher()
    private val portee = CoroutineScope(principal)

    /** Les événements reçus de la carte, et combien le test en a déjà lu. */
    private val recus = mutableListOf<EvenementCarteOccHab>()
    private var lus = 0

    // Des points du fond de carte, loin des stations d'essai.
    private val p1 = LatLon(42.9400, 1.6000)
    private val p2 = LatLon(42.9410, 1.6010)
    private val p3 = LatLon(42.9420, 1.6000)

    @BeforeTest
    fun preparer() {
        Dispatchers.setMain(principal)
        runBlocking { services.ajouterOccHabEssai() }
    }

    @AfterTest
    fun nettoyer() {
        portee.cancel()
        magasin.clear()
        laisserFinirLeTravailDeFond()
        Dispatchers.resetMain()
        essai.nettoyer()
    }

    private inline fun <reified T : ViewModel> vm(noinline creer: () -> T): T =
        ViewModelProvider.create(magasin, viewModelFactory { initializer { creer() } })[T::class]

    /** Pompe le fil principal jusqu'à [condition] : la carte lit le stockage sur d'autres fils. */
    private fun attendre(quoi: String, condition: () -> Boolean) {
        val limite = System.currentTimeMillis() + 10_000
        while (true) {
            principal.scheduler.advanceUntilIdle()
            if (condition()) return
            if (System.currentTimeMillis() > limite) fail("attendu : $quoi")
            Thread.sleep(10)
        }
    }

    /** Le prochain événement non lu que [cherche] reconnaît (les précédents sont passés). */
    private fun attendreEvenement(quoi: String, cherche: (EvenementCarteOccHab) -> Boolean) {
        attendre(quoi) {
            val i = (lus until recus.size).firstOrNull { cherche(recus[it]) }
            if (i != null) lus = i + 1
            i != null
        }
    }

    /** Le chargement des stations du serveur fini (hors ligne : repli sur le cache). */
    private fun attendreChargement() =
        attendreEvenement("le chargement des stations du serveur") { it is EvenementCarteOccHab.Message && "station(s)" in it.texte }

    /** La carte rouverte sur la saisie d'essai à envoyer (deux stations : aucune ne s'impose), en
     *  CONSULTATION, ses stations dessinées. */
    private fun carteEnConsultation(): CarteOccHabViewModel {
        val session = vm { SessionOccHabViewModel() }
        val saisie = runBlocking { services.occHabStore.charger() }.first { it.id == SAISIE }
        session.session.reprendreSaisie(saisie)
        val carte = vm { CarteOccHabViewModel(services, session) }
        portee.launch { carte.evenements.collect { recus += it } }
        carte.ouverture()
        attendreChargement()
        attendre("les stations dessinées") { carte.stationsLocales.isNotEmpty() }
        return carte
    }

    /** « Ajouter une station », ses informations obligatoires validées telles quelles. */
    private fun carteEnSaisie(): CarteOccHabViewModel {
        val carte = carteEnConsultation()
        carte.ajouterStation()
        carte.validerDetails(requireNotNull(carte.details).saisies())
        attendreChargement()
        return carte
    }

    private fun selectionner(carte: CarteOccHabViewModel, id: String) {
        carte.toucherStationLocale(id)
        attendre("la station $id sélectionnée") { carte.geometrieChargee }
    }

    // ─── Consultation et saisie ───

    @Test
    fun a_l_ouverture_la_carte_est_en_consultation_et_un_tap_ne_dessine_rien() {
        val carte = carteEnConsultation()
        assertFalse(carte.enCreation)
        assertFalse(carte.toucherCarte(p1, null), "un tap dans le vide ne crée rien")
        assertTrue(carte.edition.vide)
        assertFalse(carte.peutAnnuler, "« Annuler » n'a rien à faire au repos")
        assertFalse(carte.toucherAvantCouches(p1), "pas d'aimantage hors du dessin")
    }

    @Test
    fun les_informations_validees_ouvrent_la_saisie_et_le_bandeau_bascule_au_premier_geste() {
        val carte = carteEnSaisie()
        assertTrue(carte.enCreation)
        assertTrue(carte.dessinEnCours)
        assertEquals(CarteOccHab.texteSelection(true), carte.bandeau, "une station voisine reste sélectionnable")
        assertTrue(carte.toucherCarte(p1, null))
        assertEquals(p1, carte.edition.point)
        assertEquals(CarteOccHab.texteMode(ModeGeometrie.POINT, false), carte.bandeau)
    }

    /** Divergence assumée : sous Android, « Annuler » au départ d'une saisie l'abandonne (GeoMys-IOS
     *  n'y a rien à annuler). */
    @Test
    fun annuler_sans_geste_abandonne_la_saisie() {
        val carte = carteEnSaisie()
        assertTrue(carte.peutAnnuler)
        carte.annuler()
        assertFalse(carte.enCreation)
        assertTrue(carte.edition.vide)
        assertFalse(carte.peutAnnuler)
        assertFalse(carte.toucherCarte(p1, null), "retour à la consultation")
    }

    // ─── Point et polygone ───

    @Test
    fun un_tap_pose_le_point_un_second_le_deplace_et_annuler_les_defait_un_a_un() {
        val carte = carteEnSaisie()
        assertFalse(carte.geometrieEngagee)
        carte.toucherCarte(p1, null)
        assertTrue(carte.geometrieEngagee, "un point posé fige le type")
        assertTrue(carte.peutValider)
        carte.toucherCarte(p2, null)
        assertEquals(p2, carte.edition.point)
        carte.annuler()
        assertEquals(p1, carte.edition.point, "« Annuler » défait le dernier geste")
        carte.annuler()
        assertTrue(carte.edition.vide)
        assertFalse(carte.geometrieEngagee, "le type redevient libre")
        assertTrue(carte.enCreation, "la saisie reste ouverte")
        assertEquals(null, carte.edition.surfaceM2(), "un point n'a pas de surface")
    }

    @Test
    fun trois_sommets_sont_necessaires_pour_valider_un_polygone() {
        val carte = carteEnSaisie()
        carte.changerMode(ModeGeometrie.POLYGONE)
        assertEquals(CarteOccHab.texteMode(ModeGeometrie.POLYGONE, false), carte.bandeau)
        carte.toucherCarte(p1, null)
        assertTrue(carte.geometrieEngagee, "un sommet posé fige le type")
        carte.toucherCarte(p2, null)
        assertFalse(carte.peutValider)
        carte.toucherCarte(p3, null)
        assertTrue(carte.peutValider)
        assertEquals(listOf(p1, p2, p3), carte.edition.sommets)
        assertTrue(carte.edition.poignees(carte.geometrieChargee).isEmpty(), "pas de poignée pendant le dessin d'un nouveau polygone")
        assertTrue(carte.edition.surfaceM2()!! > 0)
    }

    @Test
    fun une_saisie_neuve_change_de_mode_librement() {
        val carte = carteEnSaisie()
        carte.changerMode(ModeGeometrie.POLYGONE)
        assertEquals(ModeGeometrie.POLYGONE, carte.edition.mode)
        carte.changerMode(ModeGeometrie.POINT)
        assertEquals(ModeGeometrie.POINT, carte.edition.mode)
        assertFalse(carte.geometrieEngagee)
    }

    @Test
    fun valider_vide_la_pile_d_annulation() {
        val carte = carteEnSaisie()
        carte.changerMode(ModeGeometrie.POLYGONE)
        listOf(p1, p2, p3).forEach { carte.toucherCarte(it, null) }
        carte.valider()
        attendreEvenement("l'écran de la station") { it == EvenementCarteOccHab.OuvrirStation }
        // Plus rien à défaire : « Annuler » abandonne la saisie au lieu de retirer le dernier sommet.
        carte.annuler()
        assertFalse(carte.enCreation)
    }

    // ─── Aimantage ───

    @Test
    fun les_stations_dessinees_sont_les_cibles_et_l_aimantage_passe_avant_la_selection() {
        val carte = carteEnSaisie()
        val cibles = carte.ciblesAimantage
        assertTrue(LatLon(42.954, 1.618) in cibles, "sommet d'une station rouge")
        assertTrue(LatLon(42.9525, 1.615) in cibles, "station ponctuelle rouge")
        assertTrue(LatLon(42.957, 1.627) in cibles, "sommet d'une station violette")
        carte.changerMode(ModeGeometrie.POLYGONE)
        assertTrue(carte.toucherAvantCouches(LatLon(42.954, 1.618)), "pendant le dessin, la cible proche l'emporte")
        assertEquals(listOf(LatLon(42.954, 1.618)), carte.edition.sommets)
        assertFalse(carte.toucherAvantCouches(null), "sans cible, le tap suit son cours")
    }

    // ─── Station sélectionnée ───

    @Test
    fun selectionner_une_station_la_met_en_edition() {
        val carte = carteEnConsultation()
        selectionner(carte, PELOUSE)
        assertTrue(carte.enCreation, "sélectionner une station ouvre la saisie")
        assertEquals(ModeGeometrie.POLYGONE, carte.edition.mode)
        assertEquals(4, carte.edition.sommets.size)
        assertTrue(carte.geometrieEngagee, "un polygone en édition ne passe pas en point")
        assertTrue(carte.peutAnnuler)
        assertFalse(carte.dessinEnCours)
        assertEquals(CarteOccHab.texteMode(ModeGeometrie.POLYGONE, true), carte.bandeau)
        assertEquals(4, carte.edition.poignees(true).size, "une poignée par arête, fermeture comprise")
        attendre("la pelouse retirée des stations rouges") { carte.stationsLocales.none { it.id == PELOUSE } }
        assertFalse(LatLon(42.954, 1.618) in carte.ciblesAimantage, "ses propres sommets ne sont plus des cibles")
    }

    @Test
    fun un_tap_ne_modifie_pas_une_station_selectionnee() {
        val carte = carteEnConsultation()
        selectionner(carte, PELOUSE)
        val avant = carte.edition
        assertFalse(carte.toucherCarte(p1, null))
        assertFalse(carte.toucherAvantCouches(p1))
        assertEquals(avant, carte.edition, "aucun sommet ajouté")
    }

    @Test
    fun les_gestes_se_defont_avant_la_selection_et_un_glisser_d_un_coup() {
        val carte = carteEnConsultation()
        selectionner(carte, PELOUSE)
        val avant = carte.edition
        carte.debutGlisser()
        listOf(LatLon(42.953, 1.617), LatLon(42.952, 1.616), LatLon(42.951, 1.615)).forEach { carte.glisserSommet(0, it) }
        carte.finGlisserSommet(0, LatLon(42.951, 1.615))
        assertEquals(LatLon(42.951, 1.615), carte.edition.sommets[0])
        carte.annuler()
        assertEquals(avant, carte.edition, "un glisser ne remplit pas la pile : il se défait d'un coup")
        assertTrue(carte.geometrieChargee, "la station reste sélectionnée")
        carte.annuler()
        assertFalse(carte.geometrieChargee)
        assertFalse(carte.enCreation, "la station lâchée, la carte revient à la consultation")
        assertTrue(carte.edition.vide)
        assertFalse(carte.peutAnnuler)
        assertFalse(carte.geometrieEngagee, "le mode redevient libre")
        attendre("la pelouse de nouveau rouge") { carte.stationsLocales.any { it.id == PELOUSE } }
    }

    @Test
    fun une_insertion_s_annule() {
        val carte = carteEnConsultation()
        selectionner(carte, PELOUSE)
        val avant = carte.edition
        carte.insererSommet(-1, 0)
        assertEquals(5, carte.edition.sommets.size)
        assertEquals(TopologiePolygone.milieu(avant.sommets[0], avant.sommets[1]), carte.edition.sommets[1], "le sommet s'insère à sa place")
        carte.annuler()
        assertEquals(avant, carte.edition)
    }

    @Test
    fun prendre_une_autre_station_repart_d_une_pile_vide() {
        val carte = carteEnConsultation()
        selectionner(carte, PELOUSE)
        carte.debutGlisser()
        carte.finGlisserSommet(0, LatLon(42.951, 1.615))
        carte.annuler()
        carte.annuler()
        attendre("la pelouse de nouveau rouge") { carte.stationsLocales.any { it.id == PELOUSE } }
        selectionner(carte, POINT)
        carte.annuler()
        assertFalse(carte.geometrieChargee, "rien à défaire sur la nouvelle station : « Annuler » la lâche")
        assertFalse(carte.enCreation)
    }

    @Test
    fun le_point_d_une_station_ne_se_replace_pas_d_un_tap() {
        val carte = carteEnConsultation()
        selectionner(carte, POINT)
        val avant = carte.edition.point
        assertEquals(CarteOccHab.texteMode(ModeGeometrie.POINT, true), carte.bandeau)
        assertFalse(carte.toucherCarte(p1, null))
        assertEquals(avant, carte.edition.point)
        assertTrue(carte.geometrieEngagee, "un point en édition ne passe pas en polygone")
        carte.debutGlisser()
        carte.glisserPoint(p1)
        carte.finGlisserPoint(p1)
        assertEquals(p1, carte.edition.point, "il se déplace au glisser")
        carte.annuler()
        assertEquals(avant, carte.edition.point)
    }

    // ─── Défauts n° 16 et 19 de l'inventaire, corrigés ───

    @Test
    fun une_station_dessinee_s_annonce_nouvelle() {
        val carte = carteEnSaisie()
        carte.toucherCarte(p1, null)
        carte.valider()
        attendreEvenement("l'écran de la station") { it == EvenementCarteOccHab.OuvrirStation }
        val ecran = vm { StationOccHabViewModel(services, vm { SessionOccHabViewModel() }) }
        ecran.ouverture()
        assertEquals("Nouvelle station", ecran.titre)
    }

    @Test
    fun une_station_reprise_s_annonce_a_modifier() {
        val carte = carteEnConsultation()
        selectionner(carte, PELOUSE)
        carte.valider()
        attendreEvenement("l'écran de la station") { it == EvenementCarteOccHab.OuvrirStation }
        val ecran = vm { StationOccHabViewModel(services, vm { SessionOccHabViewModel() }) }
        ecran.ouverture()
        assertEquals("Modifier la station", ecran.titre)
    }

    /** La copie d'une autre saisie, envoyée entre-temps : la question « Modifier cette station déjà
     *  envoyée ? » se pose avant toute édition. */
    @Test
    fun refuser_de_modifier_une_station_envoyee_laisse_la_carte_en_consultation() {
        val carte = carteEnConsultation()
        val session = vm { SessionOccHabViewModel() }.session
        val envoyee = runBlocking { services.occHabStore.charger() }.first { it.id == SAISIE_ENVOYEE }
        carte.ouvrirCopieLocale(envoyee, envoyee.stations.single())
        attendre("la question sur la station envoyée") { carte.dialogue is DialogueCarteOccHab.ModifierEnvoyee }
        assertFalse(session.station.geometrieDefinie(), "la session ne tient pas la station envoyée : aucun tracé ne la remplacera")
        carte.fermerDialogue()
        assertFalse(carte.enCreation, "refusée, la carte reste en consultation")
        assertFalse(carte.toucherCarte(p1, null), "un tap n'y commence rien")

        carte.ouvrirCopieLocale(envoyee, envoyee.stations.single())
        attendre("la question sur la station envoyée") { carte.dialogue is DialogueCarteOccHab.ModifierEnvoyee }
        carte.modifierEnvoyee((carte.dialogue as DialogueCarteOccHab.ModifierEnvoyee).station)
        attendre("la station envoyée en édition") { carte.geometrieChargee }
        assertTrue(carte.enCreation, "acceptée, la station s'ouvre en édition")
    }

    @Test
    fun une_station_du_serveur_importee_garde_ses_dates() {
        // Comme le web (décision de l'utilisateur du 21 octobre) ; GeoMys-Android lui donnait les
        // dates de la session, qui remplaçaient les siennes à l'envoi.
        val carte = carteEnConsultation()
        val session = vm { SessionOccHabViewModel() }.session
        val prairie = carte.stationsViolettes.first { it.idStationServeur == 501 }
        assertNotEquals(prairie.dateMin, session.defautsSession.dateMin, "l'essai n'aurait rien prouvé")
        carte.toucherStationServeur(prairie.id)
        attendre("la proposition d'import") { carte.dialogue is DialogueCarteOccHab.Import }
        carte.importer((carte.dialogue as DialogueCarteOccHab.Import).station)
        attendre("la prairie sélectionnée") { carte.geometrieChargee }
        assertEquals(prairie.dateMin to prairie.dateMax, session.station.dateMin to session.station.dateMax)
    }

    // ─── Trous (station du serveur importée) ───

    @Test
    fun les_trous_arrivent_avec_la_station_et_leurs_gestes_s_annulent() {
        val carte = carteEnConsultation()
        val prairie = carte.stationsViolettes.first { it.idStationServeur == 501 }
        carte.toucherStationServeur(prairie.id)
        attendre("la proposition d'import") { carte.dialogue is DialogueCarteOccHab.Import }
        carte.importer((carte.dialogue as DialogueCarteOccHab.Import).station)
        attendre("la prairie sélectionnée") { carte.geometrieChargee }
        assertEquals(1, carte.edition.trous.size)
        assertEquals(8, carte.edition.poignees(true).size, "une poignée par arête du contour et du trou")
        val avant = carte.edition

        carte.debutGlisser()
        carte.glisserSommetTrou(0, 1, LatLon(42.9552, 1.6252))
        carte.finGlisserSommetTrou(0, 1, LatLon(42.9552, 1.6252))
        assertEquals(LatLon(42.9552, 1.6252), carte.edition.trous[0][1])
        assertEquals(avant.sommets, carte.edition.sommets, "le contour ne bouge pas")
        carte.annuler()
        assertEquals(avant, carte.edition, "le déplacement d'un sommet de trou s'annule")

        carte.insererSommet(0, 2)
        assertEquals(5, carte.edition.trous[0].size)
        carte.annuler()
        assertEquals(avant, carte.edition, "l'insertion dans un trou s'annule")

        carte.debutGlisser()
        carte.finGlisserSommetTrou(0, 0, LatLon(43.0, 1.0))
        attendreEvenement("l'avertissement du sommet sorti") {
            it == EvenementCarteOccHab.Message(CarteOccHab.SOMMET_TROU_HORS_POLYGONE, longue = true)
        }
    }

    // ─── Topologie partagée (demande de l'utilisateur du 20 octobre) ───
    // La pelouse à envoyer et la prairie du serveur partagent l'arête B (42,9540 ; 1,6220) –
    // C (42,9570 ; 1,6220) : sommets 1 et 2 de la pelouse, dernier et premier de la prairie.

    private val sommetB = LatLon(42.9540, 1.6220)
    private val sommetC = LatLon(42.9570, 1.6220)

    /** Le store relu sur le fil principal de la carte, sans le bloquer : une écriture de la carte peut
     *  y tenir son verrou, qu'un `runBlocking` attendrait sans fin. */
    private fun relireStore(): List<OccHabSaisie> {
        var lu: List<OccHabSaisie>? = null
        portee.launch { lu = services.occHabStore.charger() }
        attendre("le store relu") { lu != null }
        return requireNotNull(lu)
    }

    /** La copie locale de la prairie (id serveur 501) dans le store, et sa saisie. */
    private fun prairieEnregistree() = relireStore()
        .flatMap { s -> s.stations.map { s.id to it } }
        .firstOrNull { (_, st) -> st.idStationServeur == 501 }

    @Test
    fun un_sommet_partage_deplace_entraine_la_prairie_du_serveur() {
        val carte = carteEnConsultation()
        assertTrue(carte.stationsViolettes.any { it.idStationServeur == 501 }, "la prairie est du serveur")
        selectionner(carte, PELOUSE)
        assertEquals(sommetB, carte.edition.sommets[1])
        val deplace = LatLon(42.9535, 1.6225)
        carte.debutGlisser()
        carte.glisserSommet(1, deplace)
        carte.finGlisserSommet(1, deplace)
        attendre("la prairie importée dans la saisie") { prairieEnregistree() != null }
        val (saisie, prairie) = requireNotNull(prairieEnregistree())
        assertEquals(SAISIE, saisie, "importée dans la saisie courante, elle partira avec elle")
        val anneau = GeoJsonCoords.parse(prairie.geometryCoordsJson)
        assertEquals(deplace, anneau.first(), "son sommet partagé a suivi")
        assertEquals(sommetC, anneau.last(), "les autres ne bougent pas")
        assertFalse(prairie.envoyeGeoNature, "à envoyer, en mise à jour")
        assertTrue(prairie.empreinteOrigine != null)
        attendre("la prairie redessinée en rouge") {
            carte.stationsLocales.any { it.idStationServeur == 501 } && carte.stationsViolettes.none { it.idStationServeur == 501 }
        }
        attendreEvenement("le message des voisines") { it == EvenementCarteOccHab.Message(CarteOccHab.messageVoisinesModifiees(1)) }
        // « Annuler » ne défait que la pelouse (décision du 17 octobre) : la prairie garde son sommet.
        carte.annuler()
        assertEquals(sommetB, carte.edition.sommets[1])
        principal.scheduler.advanceUntilIdle()
        assertEquals(deplace, GeoJsonCoords.parse(requireNotNull(prairieEnregistree()).second.geometryCoordsJson).first())
    }

    @Test
    fun un_sommet_insere_sur_l_arete_commune_l_est_aussi_dans_la_prairie() {
        val carte = carteEnConsultation()
        selectionner(carte, PELOUSE)
        carte.insererSommet(-1, 1)
        val milieu = TopologiePolygone.milieu(sommetB, sommetC)
        assertEquals(milieu, carte.edition.sommets[2])
        attendre("la prairie importée dans la saisie") { prairieEnregistree() != null }
        val anneau = GeoJsonCoords.parse(requireNotNull(prairieEnregistree()).second.geometryCoordsJson)
        assertEquals(5, anneau.size)
        assertEquals(milieu, anneau.last(), "inséré sur l'arête de fermeture C → B")
    }

    @Test
    fun une_voisine_d_une_autre_saisie_y_reste_et_partira_avec_l_envoi() {
        // Une station rouge d'une autre saisie, raccordée au sommet A (42,9540 ; 1,6180) de la pelouse.
        val autre = "essai-occhab-autre"
        val voisine = OccHabStation(
            id = "essai-occhab-voisine", geometryType = "Polygon", latitude = 42.9530, longitude = 1.6170,
            geometryCoordsJson = GeoJsonCoords.format(listOf(LatLon(42.9520, 1.6160), LatLon(42.9520, 1.6180), LatLon(42.9540, 1.6180))),
        )
        runBlocking { services.occHabStore.upsertStation(autre, voisine) }
        val session = vm { SessionOccHabViewModel() }
        session.session.reprendreSaisie(runBlocking { services.occHabStore.charger() }.first { it.id == SAISIE })
        val carte = vm { CarteOccHabViewModel(services, session) }
        portee.launch { carte.evenements.collect { recus += it } }
        carte.ouverture()
        attendre("les stations dessinées") { carte.stationsLocales.any { it.id == voisine.id } }
        selectionner(carte, PELOUSE)
        val deplace = LatLon(42.9542, 1.6178)
        carte.debutGlisser()
        carte.finGlisserSommet(0, deplace)
        attendre("la voisine déplacée dans sa saisie") {
            relireStore().single { it.id == autre }.stations.single().geometryCoordsJson?.let(GeoJsonCoords::parse)?.last() == deplace
        }
        assertEquals(mapOf(autre to mutableSetOf(voisine.id)), session.session.voisinesAEnvoyer)
    }

    /** Constat sous Android le 20 octobre : une station ajoutée puis terminée, la carte suivante ne
     *  montrait plus les stations des autres jeux de données — le chargement du jeu de la saisie
     *  croisait celui de la consultation, et le dernier arrivé l'emportait. */
    @Test
    fun une_station_ajoutee_ne_fait_pas_disparaitre_les_autres() {
        val toutes = listOf(501, 502, 503, 504)
        val session = vm { SessionOccHabViewModel() }
        session.session.reprendreSaisie(runBlocking { services.occHabStore.charger() }.first { it.id == SAISIE })
        val carte = vm { CarteOccHabViewModel(services, session) }
        portee.launch { carte.evenements.collect { recus += it } }
        carte.ouverture()
        attendreChargement()
        attendre("toutes les stations à l'ouverture") { carte.stationsViolettes.mapNotNull { it.idStationServeur }.sorted() == toutes }
        carte.ajouterStation()
        carte.validerDetails(requireNotNull(carte.details).saisies())
        attendreChargement()
        carte.changerMode(ModeGeometrie.POLYGONE)
        listOf(p1, p2, p3).forEach { carte.toucherCarte(it, null) }
        carte.valider()
        attendreEvenement("l'écran de la station") { it == EvenementCarteOccHab.OuvrirStation }
        // L'écran de la station terminé : une nouvelle station, sur une NOUVELLE carte.
        session.session.nouvelleStation()
        val autreMagasin = ViewModelStore()
        try {
            val suivante = ViewModelProvider.create(autreMagasin, viewModelFactory { initializer { CarteOccHabViewModel(services, session) } })[CarteOccHabViewModel::class]
            portee.launch { suivante.evenements.collect {} }
            suivante.ouverture()
            attendre("toutes les stations sur la carte suivante") {
                suivante.stationsViolettes.mapNotNull { it.idStationServeur }.sorted() == toutes && suivante.stationsLocales.size == 3
            }
            // Le chargement du jeu de la saisie fini (son message), rien n'a disparu.
            repeat(5) {
                principal.scheduler.advanceUntilIdle()
                Thread.sleep(20)
            }
            assertEquals(toutes, suivante.stationsViolettes.mapNotNull { it.idStationServeur }.sorted())
        } finally {
            autreMagasin.clear()
        }
    }

    private companion object {
        const val SAISIE = "essai-occhab-a-envoyer"
        const val PELOUSE = "essai-occhab-pelouse"
        const val POINT = "essai-occhab-point"
        const val SAISIE_ENVOYEE = "essai-occhab-envoyee"
    }
}
