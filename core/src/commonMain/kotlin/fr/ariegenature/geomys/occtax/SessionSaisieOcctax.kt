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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/TraceViewModel.kt) :
// LiveData devient StateFlow, sur des listes non modifiables (un StateFlow n'émet pas une même liste
// mutée). Le store étant suspendu, l'auto-save prend son instantané à chaque modification, comme
// sous Android, puis l'écrit dans une file qui garde l'ordre des modifications.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.location.LocationTracker
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.store.SortieStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** L'état partagé d'une saisie OccTax, mono comme multi-taxons : les observations de la sortie en
 *  cours, son id une fois écrite, la saisie a posteriori et les détails communs. Chaque
 *  modification des observations écrit la sortie dans [sortieStore] (« Mes saisies → À
 *  envoyer »). Les écritures tournent dans [portee]. */
class SessionSaisieOcctax(
    private val sortieStore: SortieStore,
    val locationTracker: LocationTracker,
    portee: CoroutineScope,
) {
    /** Libellé du type de saisie en cours (« Saisie mono-taxon » / « Saisie multi-taxons »),
     *  posé par l'écran d'entrée du flux et lu par tous les écrans de saisie pour leur bandeau
     *  « 🏠 › <type> ». */
    var typeSaisieLabel: String = ""

    private val _observations = MutableStateFlow<List<Observation>>(emptyList())
    val observations: StateFlow<List<Observation>> = _observations.asStateFlow()

    /** Événement : l'auto-save du brouillon a ÉCHOUÉ à écrire sur le disque (stockage plein ?) — la
     *  saisie ne vit plus qu'en mémoire et serait perdue à l'arrêt de l'appli. Les écrans de saisie
     *  affichent une alerte bloquante, puis acquittent via [acquitterEchecEcriture]. Le retour des
     *  stores était ignoré sur tout le chemin OccTax (audit 2026-08-23, constat CRITIQUE). */
    private val _echecEcritureBrouillon = MutableStateFlow(false)
    val echecEcritureBrouillon: StateFlow<Boolean> = _echecEcritureBrouillon.asStateFlow()

    fun acquitterEchecEcriture() {
        _echecEcritureBrouillon.value = false
    }

    /** Id de la sortie en cours d'édition (= reprise depuis l'onglet « À envoyer », ou première
     *  écriture d'une nouvelle saisie). Non nul : la fin de saisie remplace la sortie existante
     *  au lieu d'en créer une nouvelle. Remis à zéro par [reinitialiser]. */
    var sortieEnEditionId: String? = null
        private set

    /** Saisie A POSTERIORI : passe à true dès que l'utilisateur modifie la date/heure d'un relevé
     *  dans « Détails » au cours de cette sortie (≠ saisie en direct sur le terrain).
     *  Conséquences, partagées par toute la sortie :
     *   - les relevés suivants HÉRITENT de [dateDebutReleveAPosteriori]/[dateFinReleveAPosteriori] ;
     *   - au retour sur la carte après un relevé, on garde l'emprise du relevé précédent au lieu de
     *     recentrer sur le GPS (non pertinent quand on saisit des données passées).
     *  En saisie en direct (date non modifiée), reste false → comportement GPS habituel.
     *  Remis à zéro par [reinitialiser] / [reprendreSortie]. */
    var saisieAPosteriori = false
        private set
    var dateDebutReleveAPosteriori: Long? = null
        private set
    var dateFinReleveAPosteriori: Long? = null
        private set

    /** Mémorise la date/heure a posteriori choisie dans « Détails » et arme [saisieAPosteriori]
     *  pour le reste de la sortie. Appelé par la saisie quand l'utilisateur MODIFIE la date. */
    fun definirDateAPosteriori(debut: Long?, fin: Long?) {
        saisieAPosteriori = true
        dateDebutReleveAPosteriori = debut
        dateFinReleveAPosteriori = fin
    }

    /** Détails COMMUNS de la saisie multi-taxons, réglés par le bouton « i » de la carte : jeu de
     *  données, observateurs, date/heure, habitat, commentaire, champs additionnels et form_fields.
     *  CHAQUE nouveau relevé de la sortie en hérite comme valeurs de départ (au lieu des seuls
     *  défauts de la config) ; un relevé peut toujours surcharger par son propre « Détails ». null
     *  tant que l'utilisateur ne les a pas définis. Remis à zéro par reinitialiser /
     *  reprendreSortie. */
    var detailsCommuns: DetailsReleveResult? = null
        private set

    /** La saisie en cours est mono-taxon : posé par l'écran d'entrée du flux (saisie mono ou carte
     *  multi-taxons), retenu par la sortie à sa création ([Sortie.saisieMono]). */
    var saisieMono = false

    /** La date commune n'est retenue que MODIFIÉE : elle arme alors la saisie a posteriori, qui la
     *  transmet aux nouveaux relevés. Validée telle quelle, elle n'était que l'heure d'ouverture du
     *  dialogue, que GeoMys-Android imposait à tous les relevés suivants, par-dessus une date a
     *  posteriori armée ensuite (défaut n° 4 de l'inventaire) : on garde alors la date retenue. */
    fun definirDetailsCommuns(res: DetailsReleveResult) {
        detailsCommuns = if (res.dateModifiee) res else res.copy(dateDebut = detailsCommuns?.dateDebut, dateFin = detailsCommuns?.dateFin)
    }

    /** Jeu de données de la SORTIE en cours = celui des relevés déjà enregistrés (tous les relevés
     *  d'une saisie partagent le même). null si aucun relevé n'en porte encore → l'appelant
     *  retombe alors sur le défaut de la config. Un relevé AJOUTÉ à une saisie existante (y compris
     *  en ré-édition) reprend ainsi le jeu de données de la SAISIE, et non le défaut courant de la
     *  config (qui a pu changer entre-temps). */
    fun datasetSortie(): Int? =
        _observations.value.firstNotNullOfOrNull { it.idDatasetReleve?.takeIf { d -> d > 0 } }

    /** Le jeu de données d'un NOUVEAU relevé de la saisie : celui des détails communs, sinon celui
     *  des relevés déjà enregistrés, sinon celui des Paramètres ([idDatasetParametres]). Ses champs
     *  additionnels sont ceux que le web montre pour lui. */
    fun datasetNouveauReleve(idDatasetParametres: Int?): Int? =
        detailsCommuns?.idDataset ?: datasetSortie() ?: idDatasetParametres?.takeIf { it > 0 }

    fun ajouterObservation(obs: Observation) {
        _observations.update { it + obs }
        persisterBrouillon()
    }

    fun supprimerObservation(id: String) {
        _observations.update { liste -> liste.filterNot { it.id == id } }
        persisterBrouillon()
    }

    /** Supprime toutes les obs partageant un [releveId] donné (= un « relevé » GeoNature au sens
     *  saisie multi-taxons : 1 point + N taxons en une session). */
    fun supprimerReleve(releveId: String) {
        _observations.update { liste -> liste.filterNot { it.releveId == releveId } }
        persisterBrouillon()
    }

    /** Suppression par liste d'IDs — utilisé par l'édition de relevé pour purger les obs retirées
     *  du formulaire entre le chargement et l'enregistrement. */
    fun supprimerObservations(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val ensemble = ids.toHashSet()
        _observations.update { liste -> liste.filterNot { it.id in ensemble } }
        persisterBrouillon()
    }

    /** Remplace en bloc toutes les observations d'un [releveId] par [nouvelles] (qui portent ce
     *  même releveId), en une seule mutation → un seul auto-save. Utilisé par la saisie
     *  multi-taxons pour refléter le lot en cours au fil de l'eau sans réécrire le store une fois
     *  par espèce. Préserve l'ordre des autres relevés.
     *
     *  Purge AUSSI par id (upsert) : une obs MONO-taxon éditée porte releveId=null à l'origine mais
     *  ressort de l'écran d'édition avec le releveId de la session — la purge par seul releveId ne
     *  la retrouvait pas, l'originale survivait et l'espèce apparaissait en DOUBLE sur le point
     *  (avec le même id d'obs, en plus). */
    fun remplacerObservationsDuReleve(releveId: String, nouvelles: List<Observation>) {
        val idsNouvelles = nouvelles.map { it.id }.toHashSet()
        _observations.update { liste ->
            liste.filterNot { it.releveId == releveId || it.id in idsNouvelles } + nouvelles
        }
        persisterBrouillon()
    }

    fun mettreAJourObservationPosition(id: String, lat: Double, lon: Double) {
        mettreAJourObservationsPositions(listOf(id), lat, lon)
    }

    /** Variante BATCH de [mettreAJourObservationPosition] : déplace N obs (relevé multi-taxons
     *  repositionné d'un coup) en UNE mutation → UN seul auto-save. */
    fun mettreAJourObservationsPositions(ids: Collection<String>, lat: Double, lon: Double) {
        if (ids.isEmpty()) return
        modifier(ids) { it.copy(latitude = lat, longitude = lon, champsReleveExtra = it.champsReleveExtra - ALTITUDES) }
    }

    /** Met à jour les sommets d'un relevé ligne/polygone : applique le nouveau geometryCoordsJson et
     *  recentre lat/lon (centroïde) sur toutes les obs ciblées. */
    fun mettreAJourGeometrieReleve(ids: Collection<String>, coordsJson: String, lat: Double, lon: Double) {
        modifier(ids) { it.copy(geometryCoordsJson = coordsJson, latitude = lat, longitude = lon, champsReleveExtra = it.champsReleveExtra - ALTITUDES) }
    }

    private fun modifier(ids: Collection<String>, transformation: (Observation) -> Observation) {
        val ensemble = ids.toHashSet()
        val avant = _observations.value
        if (avant.none { it.id in ensemble }) return
        _observations.value = avant.map { if (it.id in ensemble) transformation(it) else it }
        persisterBrouillon()
    }

    // ── Auto-save ───────────────────────────────────────────────────────────────────────────────

    private val ecritures = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        portee.launch { for (ecriture in ecritures) ecriture() }
    }

    /** Auto-save « au fil de l'eau » de la saisie en cours : à chaque modification des observations
     *  (mono ou multi-taxons), on écrit/met à jour la sortie courante dans [sortieStore] sous une id
     *  stable ([sortieEnEditionId]). Elle apparaît ainsi dans « Mes saisies → À envoyer » et survit
     *  à un arrêt de l'appli ; on la reprend par « Continuer la saisie » (= [reprendreSortie]).
     *
     *  - 1ère obs d'une nouvelle saisie → crée la sortie et fixe [sortieEnEditionId].
     *  - obs suivantes / reprise → met à jour la sortie existante (préserve id + date).
     *  - saisie vidée (plus aucune obs ni trace) → purge le brouillon.
     *  Ne touche jamais une sortie déjà envoyée à GeoNature. */
    private fun persisterBrouillon() {
        val obs = _observations.value
        val parcours = locationTracker.parcours.value
        val distance = locationTracker.distanceTotale.value
        val id = sortieEnEditionId
        if (obs.isEmpty() && parcours.isEmpty()) {
            if (id != null) ecritures.trySend { sortieStore.supprimer(id) }
            return
        }
        if (id != null) {
            ecritures.trySend {
                val existante = sortieStore.charger().firstOrNull { it.id == id }
                if (existante?.envoyeGeoNature != true) {
                    val base = existante ?: Sortie(id = id, saisieMono = saisieMono)
                    val ok = sortieStore.remplacer(
                        id,
                        base.copy(observations = obs, pointsParcours = parcours, distanceTotale = distance),
                    )
                    signalerEchec(ok)
                }
            }
        } else {
            val nouvelle = Sortie(observations = obs, pointsParcours = parcours, distanceTotale = distance, saisieMono = saisieMono)
            // Id fixé même sur échec : la prochaine tentative passera par remplacer (upsert), pas par
            // un nouvel ajout (qui créerait un doublon si l'écriture repart).
            sortieEnEditionId = nouvelle.id
            ecritures.trySend { signalerEchec(sortieStore.ajouter(nouvelle)) }
        }
    }

    /** Échec d'écriture → signale l'écran (alerte bloquante). Chaque écriture retente tout ; un
     *  succès ultérieur ne ré-arme pas le drapeau, l'écran l'a déjà acquitté. */
    private fun signalerEchec(ok: Boolean) {
        if (!ok) _echecEcritureBrouillon.value = true
    }

    /** Persiste la sortie en cours (observations + trace). Appelée quand on QUITTE le flux de
     *  saisie sans passer par « Terminer » (ex. retour à l'accueil par le fil d'Ariane) : sans ça,
     *  la trace accumulée depuis le dernier ajout d'observation — voire une trace sans aucune
     *  observation — serait perdue. Le store garde la sortie même si la session est ensuite
     *  réinitialisée au prochain démarrage de saisie. */
    fun sauvegarderBrouillon() = persisterBrouillon()

    /** Attend que les écritures déjà demandées soient faites. Avant de relire le store pour une
     *  décision (fin de la saisie, envoi) : sous Android, chaque écriture était faite avant de
     *  rendre la main. */
    suspend fun attendreEcritures() {
        val fait = CompletableDeferred<Unit>()
        ecritures.send { fait.complete(Unit) }
        fait.await()
    }

    fun reinitialiser() {
        _observations.value = emptyList()
        locationTracker.reinitialiser()
        sortieEnEditionId = null
        reinitialiserAPosteriori()
    }

    /** Repart en mode « saisie en direct » (GPS) : au démarrage d'une nouvelle sortie et à la
     *  reprise d'une sortie existante. */
    private fun reinitialiserAPosteriori() {
        saisieAPosteriori = false
        dateDebutReleveAPosteriori = null
        dateFinReleveAPosteriori = null
        detailsCommuns = null
    }

    /** Force la prochaine ouverture de l'écran trace à RECHARGER la sortie depuis le store et à
     *  recadrer la carte dessus. À appeler à l'entrée depuis « Mes saisies » : sans ça, rééditer
     *  une sortie déjà éditée dans la session laissait [sortieEnEditionId] inchangé, donc la reprise
     *  (et le recadrage) était sautée → carte centrée sur le GPS au lieu de la géométrie. */
    fun forcerRepriseAuProchainEcran() {
        sortieEnEditionId = null
    }

    /** Reprend une sortie sauvegardée : recharge les observations et la trace dans le tracker,
     *  mémorise l'id pour que la sauvegarde finale remplace la sortie existante (au lieu d'en créer
     *  une nouvelle). N'active pas le suivi GPS — c'est à l'utilisateur de relancer la trace s'il
     *  veut la continuer. */
    fun reprendreSortie(sortie: Sortie) {
        _observations.value = sortie.observations.toList()
        locationTracker.restaurerParcours(sortie.pointsParcours)
        locationTracker.definirDistance(sortie.distanceTotale)
        sortieEnEditionId = sortie.id
        reinitialiserAPosteriori()
    }

    /** Fin de la session (onCleared du ViewModel d'Android) : la réception des positions s'arrête. */
    fun fermer() {
        locationTracker.arreter()
    }
}

/** Les altitudes d'un relevé, que le web recalcule (MNT du serveur) à chaque changement de sa
 *  géométrie : un relevé déplacé les perd, l'envoi les redemande au serveur. */
private val ALTITUDES = setOf("altitude_min", "altitude_max")
