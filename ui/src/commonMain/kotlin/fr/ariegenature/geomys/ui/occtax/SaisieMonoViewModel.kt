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
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.occtax.CarteSaisie
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.occtax.DetailsReleveResult
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.occtax.FinSaisie
import fr.ariegenature.geomys.occtax.MesSaisies
import fr.ariegenature.geomys.occtax.SaisieMono
import fr.ariegenature.geomys.occtax.libelleChampEspeceMono
import fr.ariegenature.geomys.store.SuggestionTaxon
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Ce que l'écran de la saisie mono doit faire une fois. */
sealed interface EvenementSaisieMono {
    data class Message(val texte: String) : EvenementSaisieMono

    /** La fiche de l'espèce de la série, ouverte à son choix comme en multi-taxons : ses valeurs valent
     *  pour chaque « + », et son ✓ ouvre la carte (demande du 24 novembre 2026). Le « i » de la carte la
     *  rouvre, et son ✓ y ramène (25 novembre 2026). */
    data object FicheSerie : EvenementSaisieMono

    /** Modifier une observation enregistrée directement dans sa fiche, sans l'écran du relevé : son
     *  marqueur touché, puis « Éditer » (demande du 24 novembre 2026). Depuis le 25 novembre 2026, plus
     *  rien n'ouvre l'écran du relevé depuis la carte : « Modifier » de la confirmation qui suivait chaque
     *  « + » (`EditerObservation`) est retiré avec elle. La fiche est celle du rang
     *  [index] du relevé [releveId] qui la porte, ou de l'observation [obsId] seule
     *  ([SaisieMono.ficheAOuvrir]). */
    data class OuvrirFiche(val obsId: String, val releveId: String? = null, val index: Int = 0) : EvenementSaisieMono

    data object Quitter : EvenementSaisieMono
}

/** La saisie mono-taxon : réglages de la série (l'espèce, dont la fiche règle les valeurs de la série,
 *  et ses détails), puis la carte où chaque « + » enregistre une observation. [additionnels] : les
 *  champs de relevé de l'écran intercalé. [reprise] : « Continuer la saisie » d'une saisie déjà
 *  reprise dans la session, qui s'ouvre sur la carte avec les valeurs de sa série. */
class SaisieMonoViewModel(
    private val services: ServicesGeoNature,
    sessionVm: SessionSaisieViewModel,
    additionnels: Map<String, String>?,
    reprise: Boolean = false,
) : ViewModel() {
    private val config = services.config
    val session = sessionVm.session
    val tracker = services.locationTracker

    /** Saisie reprise : l'observation de son dernier « + », où la carte s'ouvre sans GPS. */
    private val dernierPlus: Observation? = if (reprise) SaisieMono.observationAReprendre(session.observations.value) else null

    /** Saisie reprise : les valeurs de sa série (demande du 24 novembre 2026), celles qu'elle a gardées
     *  ([fr.ariegenature.geomys.model.Sortie.modeleMono]), sinon celles de son dernier « + ». */
    private val modeleRepris: Observation? = if (reprise) session.modeleMono?.takeIf { it.cdNom != null } ?: dernierPlus else null

    private val taxonDepart = modeleRepris?.let { it.taxon ?: Taxon.OISEAU } ?: services.preferences.dernierTaxon() ?: Taxon.OISEAU

    val saisie = SaisieMono(session, config, taxonDepart, additionnels)

    /** Le champ espèce : groupe, mode, texte, propositions (la liste du jeu de données de la série),
     *  statut TaxRef. */
    val champ = EtatChampEspece(services, viewModelScope, taxonDepart, ::libelleChampEspeceMono, idDataset = { saisie.idDatasetEffectif() })

    private val _evenements = Channel<EvenementSaisieMono>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    /** false : les réglages ; true : la carte, où l'on enregistre. */
    var modeActif by mutableStateOf(false)
        private set

    /** Le compteur du panneau : toutes les observations de la saisie, reprises comprises, comptées comme
     *  dans « Mes saisies » (demande du 25 novembre 2026 ; il ne comptait que celles enregistrées depuis
     *  l'ouverture de l'écran, et repartait de 0 à chaque « Continuer la saisie »). */
    fun nombreObservations(observations: List<Observation> = session.observations.value): Int = MesSaisies.nbObservations(observations)

    var suivrePosition by mutableStateOf(true)

    /** Point posé au doigt pour la PROCHAINE observation ; sans lui, la position GPS. */
    var pointManuel by mutableStateOf<LatLon?>(null)
        private set

    /** false (défaut) = carte figée nord en haut ; true = carte tournée par la boussole. */
    var carteSuitBoussole by mutableStateOf(false)
        private set
    var fond by mutableStateOf(chargerFondChoisi(services.preferences, services.cartesMbtiles::existe))
        private set
    var choixFondVisible by mutableStateOf(false)
        private set

    /** Les observations au même point que le marqueur touché, quand il y en a plusieurs : on choisit
     *  d'abord laquelle, puis son popup s'ouvre. */
    var observationsAuPoint by mutableStateOf<List<Observation>?>(null)
        private set

    /** L'observation dont le marqueur a été touché : son popup propose de l'éditer ou de la supprimer. */
    var observationTouchee by mutableStateOf<Observation?>(null)
        private set

    /** L'observation dont la suppression attend sa confirmation. */
    var suppressionDemandee by mutableStateOf<Observation?>(null)
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

    /** Ce que poursuit la validation des détails obligatoires (jeu de données, date à choisir) demandés
     *  d'abord ([validerDetails]) : la reprise d'une saisie, ou le démarrage d'une série dont la fiche
     *  vient d'être validée. */
    private enum class ApresDetails { RIEN, REPRISE, DEMARRAGE }

    private var apresDetails = ApresDetails.RIEN

    init {
        // Écran d'entrée du flux mono-taxon : les écrans en aval affichent le même bandeau.
        session.typeSaisieLabel = SaisieMono.LIBELLE
        session.saisieMono = true
        // Le modèle suit le groupe de la palette, choisi dans les réglages ; sur la carte, il garde
        // celui de la série démarrée.
        viewModelScope.launch { snapshotFlow { champ.taxon }.collect { if (!modeActif) saisie.choisirTaxon(it) } }
        // Saisie reprise : les détails du relevé de la série, ceux de son dernier « + » (observateurs,
        // date, heures, commentaire, habitat…). La carte n'ouvre plus « Détails » pour les reposer
        // (relecture du 25 novembre 2026) ; une date reprise n'est plus à choisir.
        dernierPlus?.let(saisie::reprendreDetails)
        // Sans jeu de données connu, ou sans date quand le serveur la veut à choisir, la série
        // commence par ses détails, comme le web : l'un et l'autre sont obligatoires.
        if (saisie.idDatasetEffectif() == null || saisie.dateAChoisir()) ouvrirDetails()
        modeleRepris?.let(::reprendre)
    }

    /** « Continuer la saisie » (demande du 24 novembre 2026) : la série reprend l'espèce et les valeurs
     *  de [m], et la carte s'ouvre sans passer par les réglages ; après les détails, s'ils sont demandés
     *  d'abord (jeu de données, date), et après la fiche de la série, s'il manque à ses valeurs un champ
     *  obligatoire. Le champ espèce montre l'espèce : refermer ces détails ou cette fiche laisse les
     *  réglages prêts pour « Démarrer ». */
    private fun reprendre(m: Observation) {
        val cdNom = m.cdNom ?: return
        saisie.reprendreModele(m)
        champ.poserChoix(m.espece)
        champ.recherche.poser(TaxRefStatut.Trouve(cdNom, m.espece, m.espece), m.espece)
        viewModelScope.launch {
            // Le statut d'un choix dans la liste (choisirSuggestion), une fois les entrées du champ
            // chargées : le nom scientifique du cd_nom ; un nom qui l'est déjà n'a pas de nom français.
            val sci = snapshotFlow { champ.entrees }.first { it.isNotEmpty() }[cdNom]?.sciNom ?: return@launch
            val pose = champ.recherche.statut as? TaxRefStatut.Trouve
            if (champ.texte == m.espece && pose?.cdNom == cdNom) {
                champ.recherche.poser(TaxRefStatut.Trouve(cdNom, sci, m.espece.takeIf { it != sci }), m.espece)
            }
        }
        if (details != null) apresDetails = ApresDetails.REPRISE else poursuivreReprise()
    }

    /** La reprise, ses détails obligatoires posés : la fiche de la série s'il manque à ses valeurs un
     *  champ obligatoire, sinon la carte, centrée sur la position, sinon sur le dernier « + ». */
    private fun poursuivreReprise() {
        val m = saisie.modele.value
        val cdNom = m.cdNom ?: return
        if (saisie.modeleIncomplet()) {
            _evenements.trySend(EvenementSaisieMono.FicheSerie)
            return
        }
        ouvrirCarte(m.espece, cdNom, m.taxon, positionGps() ?: dernierPlus?.let { LatLon(it.latitude, it.longitude) })
    }

    /** La série démarre sur l'espèce [nom] ([cdNom], du groupe [taxon]) : la carte s'ouvre — au zoom 19
     *  sur [centre], s'il est connu, avec le suivi. */
    private fun ouvrirCarte(nom: String, cdNom: Int, taxon: Taxon, centre: LatLon?) {
        saisie.choisirTaxon(taxon)
        saisie.demarrer(nom, cdNom)
        modeActif = true
        centre ?: return
        suivrePosition = true
        cibleDemarrage = centre
    }

    private fun positionGps(): LatLon? = tracker.position.value?.let { LatLon(it.latitude, it.longitude) }

    fun demarrerSuivi() {
        services.demarrerSuiviGps()
    }

    // ─── Réglages ───

    fun choisirTaxon(nouveau: Taxon) = champ.choisirTaxon(nouveau)

    fun basculerNomScientifique(actif: Boolean) = champ.basculerNomScientifique(actif)

    fun saisir(texte: String) = champ.saisir(texte)

    /** La phrase en cours de dictée : dans le champ, avec les espèces qui lui ressemblent. */
    fun dicterPartiel(entendu: String) = champ.dicterPartiel(entendu)

    /** Une proposition CHOISIE est prise telle quelle, avec son cd_nom : re-chercher son texte
     *  pouvait désigner un autre taxon du même groupe et de la même liste portant ce nom. Sa fiche
     *  s'ouvre alors, comme pour une espèce ajoutée en multi-taxons : ses valeurs valent pour chaque
     *  « + » de la série (demande du 24 novembre 2026). */
    fun choisirSuggestion(suggestion: SuggestionTaxon) {
        champ.poserChoix(suggestion.nom)
        val sci = champ.entrees[suggestion.cdNom]?.sciNom.orEmpty().ifEmpty { suggestion.nom }
        // En mode « noms scientifiques », le nom choisi EST le nom scientifique.
        val trouve = TaxRefStatut.Trouve(suggestion.cdNom, sci, if (champ.nomScientifique) null else suggestion.nom)
        champ.recherche.poser(trouve, suggestion.nom)
        saisie.choisirEspece(champ.taxon, trouve.nomFrancais ?: trouve.nomScientifique, trouve.cdNom)
        _evenements.trySend(EvenementSaisieMono.FicheSerie)
    }

    /** Fin de la dictée : ce qui a été entendu dans le champ, et les espèces reconnues ou proches dans
     *  la liste. Aucune n'est posée sans être touchée ([EtatChampEspece.dicter]). */
    fun dicter(candidats: List<String>) = champ.dicter(candidats)

    fun vocabulaire(): List<String> = champ.vocabulaire()

    // ─── Fiche de la série ───

    /** Le nom au titre de la fiche de la série : français, sinon latin, comme en multi-taxons. */
    fun nomFicheSerie(): String {
        val m = saisie.modele.value
        val cdNom = m.cdNom ?: return m.espece
        val sciNom = champ.entrees[cdNom]?.sciNom ?: return m.espece
        if (!sciNom.equals(m.espece, ignoreCase = true)) return m.espece
        return champ.nomsFrancais[cdNom]?.firstOrNull { it.isNotBlank() } ?: m.espece
    }

    /** ✓ de la fiche de la série du mode pro : ses valeurs deviennent celles de chaque « + » suivant
     *  (l'espèce reste celle de la série), puis [ficheSerieValidee]. */
    fun validerFicheSerie(fiche: EspeceReleve) {
        saisie.modifier { m -> fiche.copy(taxon = m.taxon, espece = m.espece, cdNom = m.cdNom) }
        ficheSerieValidee()
    }

    /** ✓ de la fiche de la série, ses valeurs écrites (la fiche du mode simplifié les écrit elle-même).
     *  Ouverte des réglages : la carte s'ouvre, après les détails s'ils manquent ; une fiche validée ne
     *  se rouvre pas pour un champ obligatoire qu'elle ne montrerait pas. Ouverte de la carte
     *  ([ouvrirFicheSerie]) : on y revient telle qu'on l'a laissée, sans recentrage ni changement de
     *  groupe, et les nouvelles valeurs partent avec la saisie. */
    fun ficheSerieValidee() {
        apresDetails = ApresDetails.RIEN
        if (modeActif) {
            saisie.garderAvecLaSaisie()
            return
        }
        demarrer(verifierFiche = false)
    }

    /** La fiche de la série refermée sans ✓ : rien ne change. Ouverte des réglages, on y reste, et le
     *  champ espèce la propose de nouveau (toucher l'espèce la rouvre) ; ouverte de la carte, on y
     *  revient. */
    fun ficheSerieAbandonnee() {
        if (!modeActif) champ.libererChoix()
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
        // Détails refermés sans validation : on reste sur les réglages.
        apresDetails = ApresDetails.RIEN
    }

    fun validerDetails(resultat: DetailsReleveResult) {
        val suite = apresDetails
        saisie.appliquerDetails(resultat)
        fermerDetails()
        // Un autre jeu de données peut imposer une autre liste de taxons.
        val liste = champ.listeTaxons
        champ.actualiserListe()
        // La suite, les détails obligatoires posés : la reprise, ou la carte d'une fiche validée. Si la
        // liste de taxons a changé, l'espèce est à revoir : on reste sur les réglages.
        if (champ.listeTaxons != liste || saisie.idDatasetEffectif() == null || saisie.dateAChoisir()) return
        when (suite) {
            ApresDetails.REPRISE -> poursuivreReprise()
            ApresDetails.DEMARRAGE -> demarrer(verifierFiche = false)
            ApresDetails.RIEN -> Unit
        }
    }

    /** « Annuler » des réglages : rien n'est saisi ; le GPS lancé à l'ouverture est libéré. */
    fun annuler() {
        services.arreterSuiviGps()
        _evenements.trySend(EvenementSaisieMono.Quitter)
    }

    /** « Démarrer la saisie » : l'espèce trouvée devient celle de la série, la carte s'ouvre — au
     *  zoom 19 sur la position, si elle est connue, avec le suivi. Sa fiche s'ouvre d'abord pour une
     *  espèce qui ne l'a pas encore eue (tapée sans toucher la liste), et s'il manque aux valeurs de la
     *  série un champ obligatoire de l'espèce, comme « Détails » pour la date : les boutons qui le
     *  signalaient d'une barre rouge, « Caractérisation » et « Dénombrement », n'y sont plus
     *  (24 novembre 2026). */
    fun demarrer() = demarrer(verifierFiche = true)

    /** [verifierFiche] à faux : la fiche de la série vient d'être validée. */
    private fun demarrer(verifierFiche: Boolean) {
        val trouve = champ.recherche.statut as? TaxRefStatut.Trouve ?: return
        // Le jeu de données est obligatoire : sans lui, la série ne démarre pas. Une fiche validée
        // ouvre la carte à la validation des détails.
        if (saisie.idDatasetEffectif() == null) {
            _evenements.trySend(EvenementSaisieMono.Message(DetailsReleveFormulaire.CHOISIR_JDD))
            ouvrirDetails()
            if (!verifierFiche) apresDetails = ApresDetails.DEMARRAGE
            return
        }
        if (saisie.dateAChoisir()) {
            _evenements.trySend(EvenementSaisieMono.Message(DetailsReleveFormulaire.CHOISIR_DATE_DEBUT))
            ouvrirDetails()
            if (!verifierFiche) apresDetails = ApresDetails.DEMARRAGE
            return
        }
        val nom = trouve.nomFrancais ?: trouve.nomScientifique
        // Une espèce dont la fiche n'a pas été présentée : les valeurs de la série sont celles d'une autre.
        val sansFiche = saisie.modele.value.cdNom != trouve.cdNom
        // Une autre espèce que celle des valeurs de la série repart des valeurs par défaut.
        saisie.choisirEspece(champ.taxon, nom, trouve.cdNom)
        if (verifierFiche && (sansFiche || saisie.modeleIncomplet())) {
            _evenements.trySend(EvenementSaisieMono.FicheSerie)
            return
        }
        // Une saisie reprise, dont la carte ne s'est pas encore montrée : sur son dernier « + » sans GPS.
        val centre = positionGps() ?: dernierPlus?.takeIf { centreMemorise == null }?.let { LatLon(it.latitude, it.longitude) }
        ouvrirCarte(nom, trouve.cdNom, champ.taxon, centre)
    }

    // ─── Carte ───

    /** « i » de la carte (demande du 25 novembre 2026), comme celui de la carte multi-taxons : la fiche
     *  de la série. Ses valeurs, validées, valent pour toute la suite de la série ; les observations
     *  déjà enregistrées gardent les leurs, et chacune se modifie par son marqueur (« Éditer »). Il
     *  remplace « Modifier » du panneau, qui ramenait aux réglages pour rouvrir la fiche. */
    fun ouvrirFicheSerie() {
        if (modeActif) _evenements.trySend(EvenementSaisieMono.FicheSerie)
    }

    /** Toucher la carte pose (ou déplace) le point de la prochaine observation. */
    fun toucherCarte(p: LatLon): Boolean {
        if (!modeActif) return false
        pointManuel = p
        return true
    }

    fun deplacerPointManuel(p: LatLon) {
        pointManuel = p
    }

    /** Marqueur touché : le popup de CETTE observation (demande du 24 novembre 2026 ; un toucher
     *  listait celles à 30 m, comme sous GeoMys-Android). Plusieurs au même point, dont les marqueurs se
     *  recouvrent : on choisit d'abord laquelle. */
    fun toucherObservation(observation: Observation) {
        val auPoint = SaisieMono.observationsAuPoint(observation, session.observations.value)
        if (auPoint.size > 1) observationsAuPoint = auPoint else observationTouchee = observation
    }

    fun choisirObservationAuPoint(observation: Observation) {
        observationsAuPoint = null
        observationTouchee = observation
    }

    fun fermerObservationsAuPoint() {
        observationsAuPoint = null
    }

    fun fermerObservationTouchee() {
        observationTouchee = null
    }

    /** « Éditer » : la fiche de l'observation, sans l'écran du relevé ; la refermer ramène à la carte.
     *  Un « relevé sans espèce » n'a pas de fiche : son popup ne propose que « Supprimer ». */
    fun editerObservationTouchee() {
        val observation = observationTouchee ?: return
        observationTouchee = null
        if (observation.releveSansEspece) return
        val (releveId, index) = SaisieMono.ficheAOuvrir(observation, session.observations.value)
        _evenements.trySend(EvenementSaisieMono.OuvrirFiche(observation.id, releveId, index))
    }

    /** « Supprimer » : rien n'est supprimé avant [confirmerSuppression], comme la corbeille d'une
     *  espèce du relevé (un doigt décalé touche l'autre bouton du popup). */
    fun demanderSuppression() {
        suppressionDemandee = observationTouchee ?: return
        observationTouchee = null
    }

    fun annulerSuppression() {
        suppressionDemandee = null
    }

    /** Suppression confirmée : l'observation quitte la saisie, dont le brouillon est réécrit. */
    fun confirmerSuppression() {
        val observation = suppressionDemandee ?: return
        suppressionDemandee = null
        session.supprimerObservation(observation.id)
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
     *  recentrer la carte. Le téléphone vibre, le marqueur apparaît et le compteur avance : depuis le
     *  25 novembre 2026, plus de ligne de confirmation (l'espèce, l'effectif et « Modifier ») trois
     *  secondes sous le résumé. Le compteur compte toutes celles de la saisie ([nombreObservations]). */
    fun enregistrerIci(centre: LatLon): LatLon? {
        val position = tracker.position.value?.let { LatLon(it.latitude, it.longitude) }
        val ici = pointManuel ?: position ?: centre
        saisie.enregistrer(ici.latitude, ici.longitude)
        pointManuel = null
        suivrePosition = true
        return position
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
