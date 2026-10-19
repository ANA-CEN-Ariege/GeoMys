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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/TraceFragment.kt) : les champs
// du fragment, qui survivaient à la recréation de sa vue, deviennent l'état de ce ViewModel, qui
// survit de même au passage par les écrans de relevé. Les calculs sont dans occtax/CarteSaisie.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.carte.FondChoisi
import fr.ariegenature.geomys.carte.chargerFondChoisi
import fr.ariegenature.geomys.carte.enregistrerFondChoisi
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.occtax.CadrageReprise
import fr.ariegenature.geomys.occtax.CarteSaisie
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.occtax.DetailsReleveResult
import fr.ariegenature.geomys.occtax.FinSaisie
import fr.ariegenature.geomys.occtax.GeometrieFinale
import fr.ariegenature.geomys.occtax.ModeGeom
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.util.GeoJsonCoords
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Où la carte s'ouvre à chaque affichage de l'écran. */
sealed interface OuvertureCarte {
    /** Sur la position (zoom donné), en suivant le GPS si [suivre]. */
    data class Centre(val centre: LatLon, val zoom: Double, val suivre: Boolean) : OuvertureCarte

    /** Saisie reprise depuis « Mes saisies » : cadrée sur ses obs et sa trace, sans suivi GPS. */
    data class Reprise(val cadrage: CadrageReprise, val zoom: Double) : OuvertureCarte
}

/** Ce que l'écran doit faire une fois. */
sealed interface EvenementSaisieMulti {
    data class Message(val texte: String) : EvenementSaisieMulti

    /** Ouvrir la saisie d'un relevé : nouveau (géométrie validée), ou existant. */
    data class OuvrirReleve(val geometrie: GeometrieFinale?, val releveId: String?, val obsId: String?, val ecranReleve: Boolean) :
        EvenementSaisieMulti

    data object OuvrirListe : EvenementSaisieMulti

    /** Quitter la carte (fin de la saisie). */
    data object Quitter : EvenementSaisieMulti
}

/** La carte de la saisie multi-taxons : trace GPS, pose des relevés (point, ligne, polygone),
 *  déplacement des relevés existants, fin de la saisie. */
class SaisieMultiViewModel(
    private val services: ServicesGeoNature,
    private val sessionVm: SessionSaisieViewModel,
    sortieIdReprise: String?,
) : ViewModel() {
    val session = sessionVm.session
    val tracker = services.locationTracker
    private val config = services.config

    private val _evenements = Channel<EvenementSaisieMulti>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    /** Interrupteur « Enregistrer la trace GPS » de l'accueil : sans lui, ni bouton ▶ ni barre
     *  d'info. */
    val enregistrerTrace = services.preferences.enregistrerTrace

    var suivrePosition by mutableStateOf(true)
    var modePositionnement by mutableStateOf(false)
        private set
    var modeGeom by mutableStateOf(ModeGeom.POINT)
        private set

    /** Sommets posés au doigt (LINE / POLYGON), ou le point (POINT). */
    var sommets by mutableStateOf<List<LatLon>>(emptyList())
        private set

    /** false (défaut) = carte figée nord en haut ; true = carte tournée par la boussole. */
    var carteSuitBoussole by mutableStateOf(false)
        private set

    var fond by mutableStateOf(chargerFondChoisi(services.preferences) { false })
        private set
    var choixFondVisible by mutableStateOf(false)
        private set
    var confirmerTerminer by mutableStateOf(false)
        private set
    var envoiEnCours by mutableStateOf(false)
        private set

    /** Résultat de l'envoi : « OK » quitte la carte. */
    var resultatEnvoi by mutableStateOf<Information?>(null)
        private set

    /** Échec d'écriture à la fin de la saisie : on reste sur la carte. */
    var echecFin by mutableStateOf(false)
        private set

    /** Relevé touché sur la carte : ses observations, pour le dialogue d'options. */
    var optionsReleve by mutableStateOf<List<Observation>?>(null)
        private set

    /** Caméra quittée (onPause d'Android) : centre et zoom, repris au retour. */
    var centreMemorise: LatLon? = null
    var zoomMemorise: Double = 18.0

    private var doitCentrerSurObs = false

    /** La reprise d'une sortie est chargée : la carte peut s'ouvrir. */
    var repriseChargee by mutableStateOf(false)
        private set

    init {
        // Reprise éventuelle d'une sortie sauvegardée (depuis l'onglet « À envoyer » → édition).
        val id = sortieIdReprise?.takeIf { it.isNotEmpty() }
        if (id != null && session.sortieEnEditionId != id) {
            viewModelScope.launch {
                try {
                    services.sortieStore.charger().firstOrNull { it.id == id }?.let { sortie ->
                        session.reprendreSortie(sortie)
                        // Reprise depuis « Mes saisies » : on cadrera la carte sur les obs/trace déjà
                        // saisies (pas sur la position courante du téléphone).
                        doitCentrerSurObs = sortie.observations.isNotEmpty() || sortie.pointsParcours.isNotEmpty()
                    }
                } finally {
                    repriseChargee = true
                }
            }
        } else {
            repriseChargee = true
        }
        // Écran d'entrée du flux multi-taxons : tous les écrans de saisie en aval affichent le même
        // bandeau « 🏠 › Saisie multi-taxons ».
        session.typeSaisieLabel = LIBELLE
    }

    /** À chaque affichage de la carte (onViewCreated + setupMap d'Android) : où l'ouvrir. Au retour
     *  d'un relevé tout juste validé, on repart directement en positionnement, prêt à placer le
     *  relevé suivant — comme un clic sur « + ». */
    fun ouverture(): OuvertureCarte {
        val reprendreDerniereEmprise = sessionVm.demarrerSaisieSuivante.also { sessionVm.demarrerSaisieSuivante = false }
        val ouverture = when {
            doitCentrerSurObs -> {
                doitCentrerSurObs = false
                suivrePosition = false
                CarteSaisie.cadrageReprise(session.observations.value, tracker.parcours.value)
                    ?.let { OuvertureCarte.Reprise(it, zoomMemorise) }
                    ?: OuvertureCarte.Centre(centreMemorise ?: FRANCE, zoomMemorise, suivre = false)
            }
            // Fin d'un relevé en SAISIE A POSTERIORI : on garde l'emprise du relevé précédent, le GPS
            // n'est pas pertinent quand on saisit des données passées.
            reprendreDerniereEmprise && session.saisieAPosteriori && centreMemorise != null -> {
                suivrePosition = false
                OuvertureCarte.Centre(centreMemorise!!, zoomMemorise, suivre = false)
            }
            // Sinon on recentre sur la position du téléphone et on réactive le suivi.
            else -> {
                val position = tracker.position.value?.let { LatLon(it.latitude, it.longitude) }
                suivrePosition = true
                OuvertureCarte.Centre(position ?: centreMemorise ?: FRANCE, zoomMemorise, suivre = true)
            }
        }
        if (reprendreDerniereEmprise) {
            suivrePosition = false
            modePositionnement = true
        }
        return ouverture
    }

    // ─── Suivi GPS ───

    /** Autorisation de localisation accordée : le suivi démarre (service au premier plan sous
     *  Android). */
    fun demarrerSuivi() {
        services.demarrerSuiviGps()
    }

    /** « Centrer » : réarme le suivi ; rend la position connue, où recentrer la carte, ou le dit.
     *  Demande aussi un point frais : si la position dormait, elle arrive par le suivi. */
    fun centrer(): LatLon? {
        suivrePosition = true
        val position = tracker.positionConnue()
        if (position == null) _evenements.trySend(EvenementSaisieMulti.Message("Acquisition GPS en cours…"))
        tracker.rafraichirPosition()
        return position?.let { LatLon(it.latitude, it.longitude) }
    }

    // ─── Trace ───

    /** ▶ / ⏸ : arrêter, sinon CONTINUER une trace non vide (un aller-retour par l'accueil la met en
     *  pause ; la recommencer effacerait le parcours de la journée), sinon démarrer. */
    fun basculerParcours() = CarteSaisie.basculerParcours(tracker)

    // ─── Positionnement d'un nouveau relevé ───

    fun nouveauReleve() {
        suivrePosition = false
        modePositionnement = true
    }

    fun toucherCarte(p: LatLon): Boolean {
        if (!modePositionnement) return false
        sommets = CarteSaisie.poserSommet(modeGeom, sommets, p)
        return true
    }

    fun deplacerSommet(index: Int, p: LatLon) {
        if (index in sommets.indices) sommets = sommets.toMutableList().also { it[index] = p }
    }

    fun changerMode(nouveau: ModeGeom) {
        if (modeGeom == nouveau) return
        modeGeom = nouveau
        sommets = emptyList()
    }

    fun annulerPositionnement() {
        modePositionnement = false
        sommets = emptyList()
    }

    /** Valide la géométrie (le centre de la carte [centre] tient lieu de point s'il n'y en a pas). On
     *  n'intercale l'écran « Détails du relevé » que s'il existe un champ additionnel de relevé
     *  obligatoire SANS valeur par défaut. */
    fun validerPosition(centre: LatLon) {
        if (!modePositionnement) return
        val geometrie = CarteSaisie.geometrieFinale(modeGeom, sommets, centre)
        if (geometrie == null) {
            _evenements.trySend(EvenementSaisieMulti.Message("Au moins ${CarteSaisie.sommetsMinimum(modeGeom)} sommets requis"))
            return
        }
        modePositionnement = false
        sommets = emptyList()
        val ecranReleve = ChampsAdditionnels.aDesChampsReleveRequisSansDefaut(
            config.additionalFieldsOcctaxJsonActif,
            config.idDataset.toIntOrNull(),
        )
        _evenements.trySend(EvenementSaisieMulti.OuvrirReleve(geometrie, null, null, ecranReleve))
    }

    // ─── Relevés existants ───

    fun toucherReleve(observations: List<Observation>) {
        if (observations.isNotEmpty()) optionsReleve = observations
    }

    fun fermerOptionsReleve() {
        optionsReleve = null
    }

    /** « Éditer » : ouvre la saisie du relevé (par son releveId, ou l'obs seule). */
    fun editerReleve() {
        val premiere = optionsReleve?.firstOrNull() ?: return
        optionsReleve = null
        val rid = premiere.releveId?.takeIf { it.isNotEmpty() }
        _evenements.trySend(EvenementSaisieMulti.OuvrirReleve(null, rid, if (rid == null) premiere.id else null, ecranReleve = false))
    }

    /** Marqueur d'un relevé ponctuel lâché : tout le relevé se déplace (un seul auto-save). */
    fun deplacerReleve(observation: Observation, p: LatLon) {
        val ids = CarteSaisie.obsDuReleve(session.observations.value, observation).map { it.id }
        session.mettreAJourObservationsPositions(ids, p.latitude, p.longitude)
    }

    /** Sommet d'un relevé ligne/polygone lâché : nouvelle forme, centroïde recalculé. */
    fun deplacerSommetReleve(ids: List<String>, sommetsForme: List<LatLon>) {
        val lat = sommetsForme.map { it.latitude }.average()
        val lon = sommetsForme.map { it.longitude }.average()
        session.mettreAJourGeometrieReleve(ids, GeoJsonCoords.format(sommetsForme), lat, lon)
    }

    fun ouvrirListe() {
        _evenements.trySend(EvenementSaisieMulti.OuvrirListe)
    }

    // ─── Détails communs ───

    /** Le dialogue « Détails des relevés », s'il est ouvert. */
    var detailsCommuns by mutableStateOf<EtatDetailsReleve?>(null)
        private set

    /** « i » : les détails communs déjà réglés, sinon les défauts de la configuration. */
    fun ouvrirDetailsCommuns() {
        detailsCommuns = EtatDetailsReleve(DetailsReleveFormulaire.communs(session.detailsCommuns, config), services.nomenclatures, viewModelScope) { terme ->
            services.habitats.rechercher(config.urlServeur, terme)
        }
    }

    fun fermerDetailsCommuns() {
        detailsCommuns?.fermer()
        detailsCommuns = null
    }

    /** « Valider » : les détails s'appliqueront aux prochains relevés ; une date changée fait de la
     *  saisie une saisie a posteriori. */
    fun validerDetailsCommuns(resultat: DetailsReleveResult) {
        session.definirDetailsCommuns(resultat)
        if (resultat.dateModifiee) session.definirDateAPosteriori(resultat.dateDebut, resultat.dateFin)
        fermerDetailsCommuns()
        _evenements.trySend(EvenementSaisieMulti.Message("Détails communs enregistrés — appliqués aux prochains relevés"))
    }

    // ─── Carte ───

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

    // ─── Fin de la saisie ───

    /** ✓ ou retour : sans observation, on quitte (le GPS est libéré) ; sinon, « Terminer la
     *  saisie ». */
    fun demanderTerminer() {
        if (session.observations.value.isEmpty()) {
            tracker.arreterParcours()
            services.arreterSuiviGps()
            _evenements.trySend(EvenementSaisieMulti.Quitter)
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
            val fin = FinSaisie.terminerMulti(session, services.sortieStore)
            if (!fin.ecrite) {
                echecFin = true
                return@launch
            }
            if (!envoyer) {
                _evenements.trySend(EvenementSaisieMulti.Quitter)
                return@launch
            }
            envoiEnCours = true
            try {
                val res = services.envoiSortie.envoyerSortieVersGeoNature(fin.sortie, config)
                resultatEnvoi = Information(if (res.succes) "GeoNature" else "Erreur d'envoi", res.message)
            } finally {
                envoiEnCours = false
            }
        }
    }

    fun fermerEchecFin() {
        echecFin = false
    }

    /** Résultat d'envoi fermé sans « OK » (toucher hors du dialogue) : on reste sur la carte. */
    fun fermerResultatEnvoi() {
        resultatEnvoi = null
    }

    /** « OK » du résultat d'envoi : retour à l'écran précédent. */
    fun quitter() {
        _evenements.trySend(EvenementSaisieMulti.Quitter)
    }

    companion object {
        const val LIBELLE = "Saisie multi-taxons"
        val FRANCE = LatLon(46.5, 2.5)
    }
}
