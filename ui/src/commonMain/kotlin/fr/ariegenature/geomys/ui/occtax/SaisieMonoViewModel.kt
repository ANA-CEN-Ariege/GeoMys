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

// Porté de GeoMys-Android @ d72b8da (app/src/main/java/fr/ariegenature/geomys/ui/SaisieRapideFragment.kt) : l'état
// de l'écran (réglages, puis carte) autour de la saisie elle-même, SaisieMono (core/occtax). Le ViewModel survit
// au passage par les sous-écrans, comme les champs du fragment survivaient à la recréation de sa vue.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.carte.FondChoisi
import fr.ariegenature.geomys.carte.chargerFondChoisi
import fr.ariegenature.geomys.carte.enregistrerFondChoisi
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.occtax.CarteSaisie
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.occtax.DetailsReleveResult
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.occtax.FinSaisie
import fr.ariegenature.geomys.occtax.SaisieMono
import fr.ariegenature.geomys.occtax.libelleChampEspeceMono
import fr.ariegenature.geomys.occtax.nomSingulier
import fr.ariegenature.geomys.store.SuggestionTaxon
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Ce que l'écran de la saisie mono doit faire une fois. */
sealed interface EvenementSaisieMono {
    data class Message(val texte: String) : EvenementSaisieMono

    data object Caracterisation : EvenementSaisieMono

    data object Denombrement : EvenementSaisieMono

    /** Modifier une observation enregistrée, dans l'écran du relevé. */
    data class EditerObservation(val obsId: String) : EvenementSaisieMono

    data object Quitter : EvenementSaisieMono
}

/** La saisie mono-taxon : réglages de la série (espèce, caractérisation, dénombrement, détails),
 *  puis la carte où chaque « + » enregistre une observation. [additionnels] : les champs de relevé
 *  de l'écran intercalé. */
class SaisieMonoViewModel(
    private val services: ServicesGeoNature,
    sessionVm: SessionSaisieViewModel,
    additionnels: Map<String, String>?,
) : ViewModel() {
    private val config = services.config
    val session = sessionVm.session
    val tracker = services.locationTracker

    private val taxonDepart = services.preferences.dernierTaxon() ?: Taxon.OISEAU

    val saisie = SaisieMono(session, config, taxonDepart, additionnels)

    /** Le champ espèce : groupe, mode, texte, propositions (la liste du jeu de données de la série),
     *  statut TaxRef. */
    val champ = EtatChampEspece(services, viewModelScope, taxonDepart, ::libelleChampEspeceMono, idDataset = { saisie.idDatasetEffectif() })

    private val _evenements = Channel<EvenementSaisieMono>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    /** false : les réglages ; true : la carte, où l'on enregistre. */
    var modeActif by mutableStateOf(false)
        private set

    /** Les observations enregistrées depuis l'ouverture de l'écran. */
    var compteur by mutableStateOf(0)
        private set

    var suivrePosition by mutableStateOf(true)

    /** Point posé au doigt pour la PROCHAINE observation ; sans lui, la position GPS. */
    var pointManuel by mutableStateOf<LatLon?>(null)
        private set

    /** La dernière observation enregistrée, confirmée trois secondes sous le résumé. */
    var confirmation by mutableStateOf<Observation?>(null)
        private set
    private var tacheConfirmation: Job? = null

    /** false (défaut) = carte figée nord en haut ; true = carte tournée par la boussole. */
    var carteSuitBoussole by mutableStateOf(false)
        private set
    var fond by mutableStateOf(chargerFondChoisi(services.preferences, services.cartesMbtiles::existe))
        private set
    var choixFondVisible by mutableStateOf(false)
        private set

    /** Les observations proches d'un marqueur touché, à rouvrir. */
    var observationsIci by mutableStateOf<List<Observation>?>(null)
        private set

    /** Le dialogue « Détails du relevé », s'il est ouvert. */
    var details by mutableStateOf<EtatDetailsReleve?>(null)
        private set

    var confirmerTerminer by mutableStateOf(false)
        private set

    /** Résultat de l'envoi : « OK » quitte l'écran. */
    var resultatEnvoi by mutableStateOf<Information?>(null)
        private set

    /** Échec d'écriture à la fin de la saisie : on reste sur la carte. */
    var echecFin by mutableStateOf(false)
        private set

    /** Caméra quittée (onPause d'Android) : centre et zoom, repris au retour d'un sous-écran. */
    var centreMemorise: LatLon? = null
    var zoomMemorise: Double? = null

    /** « Démarrer » avec une position connue : la carte s'y ouvre au zoom 19. */
    var cibleDemarrage: LatLon? = null

    init {
        // Écran d'entrée du flux mono-taxon : les écrans en aval affichent le même bandeau.
        session.typeSaisieLabel = SaisieMono.LIBELLE
        session.saisieMono = true
        // Le modèle suit le groupe de la palette.
        viewModelScope.launch { snapshotFlow { champ.taxon }.collect { saisie.choisirTaxon(it) } }
        // Sans jeu de données connu, ou sans date quand le serveur la veut à choisir, la série
        // commence par ses détails, comme le web : l'un et l'autre sont obligatoires.
        if (saisie.idDatasetEffectif() == null || saisie.dateAChoisir()) ouvrirDetails()
    }

    fun demarrerSuivi() {
        services.demarrerSuiviGps()
    }

    // ─── Réglages ───

    fun choisirTaxon(nouveau: Taxon) = champ.choisirTaxon(nouveau)

    fun basculerNomScientifique(actif: Boolean) = champ.basculerNomScientifique(actif)

    fun saisir(texte: String) = champ.saisir(texte)

    /** Une proposition CHOISIE est prise telle quelle, avec son cd_nom : re-chercher son texte
     *  pouvait désigner un autre taxon du même groupe et de la même liste portant ce nom. */
    fun choisirSuggestion(suggestion: SuggestionTaxon) {
        champ.poserChoix(suggestion.nom)
        val sci = champ.entrees[suggestion.cdNom]?.sciNom.orEmpty().ifEmpty { suggestion.nom }
        // En mode « noms scientifiques », le nom choisi EST le nom scientifique.
        champ.recherche.poser(TaxRefStatut.Trouve(suggestion.cdNom, sci, if (champ.nomScientifique) null else suggestion.nom), suggestion.nom)
    }

    /** Fin de la dictée : la première hypothèse dans le champ, puis toutes essayées contre TaxRef ;
     *  la meilleure remplace le texte, qui est résolu à son tour. Rien n'est enregistré ici : la
     *  saisie démarre au bouton. */
    fun dicter(candidats: List<String>) {
        champ.saisir(candidats.first())
        viewModelScope.launch {
            val (statut, gagnant) = services.taxRefService.rechercherParmiCandidats(candidats, champ.taxon, champ.listeTaxons, champ.nomScientifique)
            if (statut is TaxRefStatut.Trouve && gagnant != null && gagnant != champ.texte) champ.saisir(gagnant)
        }
    }

    fun vocabulaire(): List<String> = champ.vocabulaire()

    /** Ce qu'ouvrent Caractérisation et Dénombrement : le modèle, avec le groupe et l'espèce du
     *  champ (avant même « Démarrer » : ils décrivent la série qu'on s'apprête à enchaîner). */
    fun especeFormulaires(): EspeceReleve {
        val trouve = champ.recherche.statut as? TaxRefStatut.Trouve
        return saisie.modele.value.copy(taxon = champ.taxon, cdNom = trouve?.cdNom, espece = champ.texte.ifEmpty { champ.taxon.nomSingulier })
    }

    fun ouvrirCaracterisation() {
        _evenements.trySend(EvenementSaisieMono.Caracterisation)
    }

    fun ouvrirDenombrement() {
        _evenements.trySend(EvenementSaisieMono.Denombrement)
    }

    /** Retour de la caractérisation : ses champs seulement (l'espèce reste celle de la série). */
    fun appliquerCaracterisation(caracterisee: EspeceReleve) {
        saisie.modifier { m -> caracterisee.copy(taxon = m.taxon, espece = m.espece, cdNom = m.cdNom) }
    }

    fun appliquerDenombrements(liste: List<Denombrement>) {
        saisie.modifier { it.avecDenombrements(liste) }
    }

    fun ouvrirDetails() {
        details = EtatDetailsReleve(saisie.donneesDetails(), services.nomenclatures, viewModelScope, services.taxRef) { terme ->
            // Restreinte à `OCCTAX.CD_TYPO_HABITAT`, comme le web.
            services.habitats.rechercher(terme, cdTypo = services.config.occtaxTypologieHabitat.takeIf { it > 0 })
        }
    }

    fun fermerDetails() {
        details?.fermer()
        details = null
    }

    fun validerDetails(resultat: DetailsReleveResult) {
        saisie.appliquerDetails(resultat)
        fermerDetails()
        // Un autre jeu de données peut imposer une autre liste de taxons.
        champ.actualiserListe()
    }

    /** « Annuler » des réglages : rien n'est saisi ; le GPS lancé à l'ouverture est libéré. */
    fun annuler() {
        services.arreterSuiviGps()
        _evenements.trySend(EvenementSaisieMono.Quitter)
    }

    /** « Démarrer la saisie » : l'espèce trouvée devient celle de la série, la carte s'ouvre — au
     *  zoom 19 sur la position, si elle est connue, avec le suivi. */
    fun demarrer() {
        val trouve = champ.recherche.statut as? TaxRefStatut.Trouve ?: return
        // Le jeu de données est obligatoire : sans lui, la série ne démarre pas.
        if (saisie.idDatasetEffectif() == null) {
            _evenements.trySend(EvenementSaisieMono.Message(DetailsReleveFormulaire.CHOISIR_JDD))
            ouvrirDetails()
            return
        }
        if (saisie.dateAChoisir()) {
            _evenements.trySend(EvenementSaisieMono.Message(DetailsReleveFormulaire.CHOISIR_DATE_DEBUT))
            ouvrirDetails()
            return
        }
        saisie.demarrer(trouve.nomFrancais ?: trouve.nomScientifique, trouve.cdNom)
        modeActif = true
        masquerConfirmation()
        val position = tracker.position.value ?: return
        suivrePosition = true
        cibleDemarrage = LatLon(position.latitude, position.longitude)
    }

    /** « Modifier » du panneau : retour aux réglages. */
    fun modifierReglages() {
        modeActif = false
        masquerConfirmation()
    }

    // ─── Carte ───

    /** Toucher la carte pose (ou déplace) le point de la prochaine observation. */
    fun toucherCarte(p: LatLon): Boolean {
        if (!modeActif) return false
        pointManuel = p
        return true
    }

    fun deplacerPointManuel(p: LatLon) {
        pointManuel = p
    }

    fun toucherObservation(observation: Observation) {
        observationsIci = SaisieMono.observationsIci(observation, session.observations.value)
    }

    fun fermerObservationsIci() {
        observationsIci = null
    }

    fun editerObservation(obsId: String) {
        observationsIci = null
        _evenements.trySend(EvenementSaisieMono.EditerObservation(obsId))
    }

    /** Marqueur lâché : la position corrigée est enregistrée. */
    fun deplacerObservation(observation: Observation, p: LatLon) {
        session.mettreAJourObservationPosition(observation.id, p.latitude, p.longitude)
    }

    /** « Centrer » : réarme le suivi ; rend la position connue, où recentrer la carte, ou le dit.
     *  Demande aussi un point frais : si la position dormait, elle arrive par le suivi. */
    fun centrer(): LatLon? {
        suivrePosition = true
        val position = tracker.positionConnue()
        if (position == null) _evenements.trySend(EvenementSaisieMono.Message("Acquisition GPS en cours…"))
        tracker.rafraichirPosition()
        return position?.let { LatLon(it.latitude, it.longitude) }
    }

    fun basculerBoussole() {
        carteSuitBoussole = !carteSuitBoussole
    }

    fun montrerChoixFond() {
        choixFondVisible = true
    }

    fun fermerChoixFond() {
        choixFondVisible = false
    }

    fun choisirFond(choisi: FondChoisi) {
        fond = choisi
        enregistrerFondChoisi(services.preferences, choisi)
    }

    // ─── Enregistrement ───

    /** « + » : une observation au point posé, sinon à la position GPS, sinon au centre de la carte
     *  [centre]. Le point posé ne vaut que pour elle, et le suivi reprend : rend la position où
     *  recentrer la carte. */
    fun enregistrerIci(centre: LatLon): LatLon? {
        val position = tracker.position.value?.let { LatLon(it.latitude, it.longitude) }
        val ici = pointManuel ?: position ?: centre
        val observation = saisie.enregistrer(ici.latitude, ici.longitude)
        compteur++
        montrerConfirmation(observation)
        pointManuel = null
        suivrePosition = true
        return position
    }

    private fun montrerConfirmation(observation: Observation) {
        tacheConfirmation?.cancel()
        confirmation = observation
        tacheConfirmation = viewModelScope.launch {
            delay(3000)
            confirmation = null
        }
    }

    private fun masquerConfirmation() {
        tacheConfirmation?.cancel()
        confirmation = null
    }

    /** « Modifier » de la confirmation : la dernière observation, dans l'écran du relevé. */
    fun modifierDerniere() {
        val id = confirmation?.id ?: return
        masquerConfirmation()
        _evenements.trySend(EvenementSaisieMono.EditerObservation(id))
    }

    // ─── Fin de la saisie ───

    /** ✓ ou retour : sans observation, on quitte (le GPS est libéré) ; sinon, « Terminer la
     *  saisie ». */
    fun demanderTerminer() {
        if (session.observations.value.isEmpty()) {
            services.arreterSuiviGps()
            _evenements.trySend(EvenementSaisieMono.Quitter)
            return
        }
        confirmerTerminer = true
    }

    fun fermerTerminer() {
        confirmerTerminer = false
    }

    val peutEnvoyer: Boolean get() = CarteSaisie.peutEnvoyer(session.observations.value, config.estConfiguree)

    fun terminer(envoyer: Boolean) {
        confirmerTerminer = false
        services.arreterSuiviGps()
        viewModelScope.launch {
            val fin = FinSaisie.terminerMono(session, services.sortieStore)
            if (!fin.ecrite) {
                echecFin = true
                return@launch
            }
            if (!envoyer) {
                _evenements.trySend(EvenementSaisieMono.Quitter)
                return@launch
            }
            val res = services.envoiSortie.envoyerSortieVersGeoNature(fin.sortie, config)
            resultatEnvoi = Information(if (res.succes) "GeoNature" else "Erreur d'envoi", res.message)
        }
    }

    fun fermerEchecFin() {
        echecFin = false
    }

    fun fermerResultatEnvoi() {
        resultatEnvoi = null
    }

    /** « OK » du résultat d'envoi : retour à l'écran précédent. */
    fun quitter() {
        _evenements.trySend(EvenementSaisieMono.Quitter)
    }

    override fun onCleared() {
        champ.recherche.annuler()
        details?.fermer()
    }
}
