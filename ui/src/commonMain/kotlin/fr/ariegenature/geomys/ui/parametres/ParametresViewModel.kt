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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/ConfigGeoNatureFragment.kt) :
// l'état que le fragment posait sur ses vues (visibilité, textes, couleurs) devient un état
// observable ; les règles pures et ce qui touche aux caches sont passés au socle
// (store/ReglesSelectionOcctax, store/ParametresGeoNature).

package fr.ariegenature.geomys.ui.parametres

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureListe
import fr.ariegenature.geomys.model.GeoNatureObservateur
import fr.ariegenature.geomys.store.ChangementConnexion
import fr.ariegenature.geomys.store.CompteursCache
import fr.ariegenature.geomys.store.ListeTaxons
import fr.ariegenature.geomys.store.ListesProposees
import fr.ariegenature.geomys.store.MessageListe
import fr.ariegenature.geomys.store.ReglesSelectionOcctax
import fr.ariegenature.geomys.store.TaxonsParListes
import fr.ariegenature.geomys.store.compteursCache
import fr.ariegenature.geomys.store.configurationComplete
import fr.ariegenature.geomys.store.enregistrerConnexion
import fr.ariegenature.geomys.store.io.listeDepuisJson
import fr.ariegenature.geomys.store.messageListe
import fr.ariegenature.geomys.store.reinitialiserSelectionsOcctax
import fr.ariegenature.geomys.store.taxonsParListes
import fr.ariegenature.geomys.store.texteBandeauRechargement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Nature d'un message de l'écran ; chaque allure lui donne sa couleur. */
enum class NatureMessage {
    /** Texte courant (progression de la synchro, version TaxRef). */
    NEUTRE,
    /** Test de connexion réussi. */
    SUCCES,
    /** Test de connexion échoué. */
    ERREUR,
    /** État à traiter (connexion non testée). */
    AVERTISSEMENT,
    /** Synchro terminée sans erreur. */
    TERMINE,
    /** Synchro terminée avec au moins une étape en échec. */
    ECHEC,
    /** Version TaxRef plus récente disponible sur le serveur. */
    MAJ_DISPONIBLE,
}

data class MessageColore(val texte: String, val nature: NatureMessage)

/** Ce que l'écran doit faire une fois : messages brefs, fermeture, permission de notification. */
sealed interface EvenementParametres {
    data class Toast(val texte: String, val longue: Boolean) : EvenementParametres
    data object Fermer : EvenementParametres
    /** Android 13 et suivants : la notification de la synchro demande une permission. */
    data object DemanderNotifications : EvenementParametres
}

/** L'écran Paramètres (`ConfigGeoNatureFragment`) : connexion au serveur, chargement des données,
 *  sélections par défaut. Tant que la configuration n'est pas complète, on ne peut pas en sortir. */
class ParametresViewModel(private val services: ServicesGeoNature) : ViewModel() {
    private val config = services.config
    private val taxRef = services.taxRef

    private val _evenements = Channel<EvenementParametres>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    // ─── Connexion ───
    var url by mutableStateOf(config.urlServeur)
        private set
    var login by mutableStateOf(config.login)
        private set
    var motDePasse by mutableStateOf(config.motDePasse)
        private set
    var testEnCours by mutableStateOf(false)
        private set
    var resultatTest by mutableStateOf<MessageColore?>(null)
        private set
    var versionServeur by mutableStateOf<String?>(null)
        private set

    // ─── Chargement des données ───
    var sectionChargerVisible by mutableStateOf(false)
        private set
    var resumeCacheVisible by mutableStateOf(false)
        private set
    var libelleCharger by mutableStateOf("Charger les données")
        private set
    var synchroEnCours by mutableStateOf(false)
        private set
    var texteSynchro by mutableStateOf<MessageColore?>(null)
        private set
    var compteurs by mutableStateOf<CompteursCache?>(null)
        private set
    var versionTaxRef by mutableStateOf<MessageColore?>(null)
        private set
    /** Ligne « Stations OccHab » du résumé : seulement si le module existe sur le serveur. */
    val occhabDisponible: Boolean get() = config.occhabDisponible

    // ─── Sélections par défaut ───
    /** Des données de la connexion courante sont chargées. */
    private var donneesChargees by mutableStateOf(false)

    /** La boîte « Sélections par défaut » : seulement une fois le chargement des données terminé. */
    val sectionDonneesVisible: Boolean get() = donneesChargees && !synchroEnCours

    /** Tous les jeux de données du cache, et ceux qui sont proposés. Aucun n'est choisi d'office. */
    var tousDatasets by mutableStateOf(emptyList<GeoNatureDataset>())
        private set
    var datasets by mutableStateOf(emptyList<GeoNatureDataset>())
        private set
    var datasetChoisi by mutableStateOf<GeoNatureDataset?>(null)
        private set
    /** Les listes de taxons proposées (celle qu'impose le jeu choisi, sinon toutes) ; aucune n'est
     *  choisie d'office. */
    var listes by mutableStateOf(ListesProposees(emptyList(), imposee = false))
        private set
    var listeChoisie by mutableStateOf<GeoNatureListe?>(null)
        private set
    /** Les observateurs proposés ; l'utilisateur connecté est l'observateur par défaut tant qu'aucun
     *  autre n'est choisi. */
    var observateurs by mutableStateOf(emptyList<GeoNatureObservateur>())
        private set
    var observateurChoisi by mutableStateOf<GeoNatureObservateur?>(null)
        private set
    /** Ce que le cache a de la liste choisie (ou imposée par le jeu choisi). */
    var messageListe by mutableStateOf<MessageListe?>(null)
        private set

    /** Les sélections obligatoires encore vides, marquées de la barre rouge : le jeu de données,
     *  la liste quand le jeu n'en impose pas et qu'il y en a à choisir, l'observateur. */
    val jddManquant: Boolean get() = datasetChoisi == null
    val listeManquante: Boolean get() = !listes.imposee && listes.listes.isNotEmpty() && listeChoisie == null
    val observateurManquant: Boolean get() = observateurChoisi == null
    private var toutesListes = emptyList<GeoNatureListe>()

    // ─── Bandeau, statut, dialogue ───
    var bandeau by mutableStateOf<String?>(null)
        private set
    var configurationComplete by mutableStateOf(false)
        private set
    var taxonsParListes by mutableStateOf<TaxonsParListes?>(null)
        private set


    init {
        // État initial des sections selon les données en cache : la boîte « Chargement des
        // données » reste visible hors ligne (compteurs consultables) ; en première configuration,
        // elle attend une connexion réussie. Tout s'affiche sans attendre TaxRef, dont le
        // chargement complet prend de 10 à 30 s sur un téléphone d'entrée de gamme : seuls les
        // compteurs et le message de la liste l'attendent, à part.
        viewModelScope.launch {
            val donneesPresentes = withContext(Dispatchers.Default) { taxRef.aDesDonnees }
            sectionChargerVisible = donneesPresentes
            resumeCacheVisible = donneesPresentes
            donneesChargees = donneesPresentes
            libelleCharger = if (donneesPresentes) "Recharger les données" else "Charger les données"
            restaurerCaches()
            majStatut()
            afficherVersionGeoNature()
            bandeau = texteBandeauRechargement(config, taxRef)
            relancerCompteurs()
            launch { majMessageListe() }
            verifierVersionTaxRef()
        }
        // Reflète une synchro qui tourne ou s'est terminée en arrière-plan, y compris quand on
        // revient sur l'écran en cours de route.
        viewModelScope.launch {
            services.synchro.etat.collect { etat ->
                when {
                    etat.enCours -> {
                        synchroEnCours = true
                        texteSynchro = MessageColore(etat.texte, NatureMessage.NEUTRE)
                        relancerCompteurs()
                    }
                    etat.termine -> terminerSynchro(etat.resume ?: etat.texte, etat.succes)
                }
            }
        }
    }

    /** Version TaxRef du serveur, si la connexion est configurée : un serveur qui répond révèle
     *  « Chargement des données » ; on signale discrètement une version plus récente. */
    private suspend fun verifierVersionTaxRef() {
        if (!config.connexionConfiguree) return
        val version = services.geoNatureSync.verifierVersionTaxRef(config) ?: return
        sectionChargerVisible = true
        val chargee = taxRef.versionSauvegardee ?: return
        versionTaxRef = if (chargee != version) {
            MessageColore("TaxRef v$chargee — maj v$version disponible", NatureMessage.MAJ_DISPONIBLE)
        } else {
            MessageColore("TaxRef v$chargee", NatureMessage.NEUTRE)
        }
    }

    private fun afficherVersionGeoNature() {
        versionServeur = config.versionGeoNatureServeur.takeIf { it.isNotEmpty() }?.let { "Serveur GeoNature v$it" }
    }

    // ─── Saisie de la connexion ───

    fun changerUrl(valeur: String) {
        url = valeur
        refleterEditionConnexion()
    }

    fun changerLogin(valeur: String) {
        login = valeur
        refleterEditionConnexion()
    }

    fun changerMotDePasse(valeur: String) {
        motDePasse = valeur
        refleterEditionConnexion()
    }

    /** Avertissement sous l'URL : une adresse en clair `http://`. */
    val urlEnClair: Boolean get() = url.trim().startsWith("http://", ignoreCase = true)

    /** Dès qu'un champ diffère de la connexion ENREGISTRÉE, on « oublie » visuellement l'ancienne :
     *  plus de chargement, de sélections, de résumé ni de versions, et une invitation à tester.
     *  L'invalidation réelle (jeton, caches, sélections) attend l'enregistrement : rien n'est
     *  purgé sur une édition abandonnée. */
    private fun refleterEditionConnexion() {
        if (url == config.urlServeur && login == config.login && motDePasse == config.motDePasse) return
        sectionChargerVisible = false
        donneesChargees = false
        resumeCacheVisible = false
        versionServeur = null
        versionTaxRef = null
        resultatTest = MessageColore("Connexion non testée — cliquez sur « Tester la connexion ».", NatureMessage.AVERTISSEMENT)
    }

    /** `sauvegarderChamps` : enregistre la connexion ; un changement d'identité vide les sélections. */
    private fun enregistrerConnexion() {
        if (services.enregistrerConnexion(url, login, motDePasse) == ChangementConnexion.IDENTITE) {
            viderSelectionsAffichees()
        }
    }

    private fun viderSelectionsAffichees() {
        tousDatasets = emptyList()
        datasets = emptyList()
        datasetChoisi = null
        toutesListes = emptyList()
        listes = ListesProposees(emptyList(), imposee = false)
        listeChoisie = null
        observateurs = emptyList()
        observateurChoisi = null
    }

    fun testerConnexion() {
        enregistrerConnexion()
        testEnCours = true
        resultatTest = null
        viewModelScope.launch {
            val (succes, message) = services.auth.testerConnexion(config)
            testEnCours = false
            resultatTest = MessageColore(message, if (succes) NatureMessage.SUCCES else NatureMessage.ERREUR)
            majStatut()
            // Connexion réussie : seul « Charger les données » apparaît, sans chargement
            // automatique. Les sélections et le résumé ne reviennent que si les données de cette
            // connexion sont chargées (pas après un changement d'identité, qui vide leurs caches).
            if (succes) {
                sectionChargerVisible = true
                afficherVersionGeoNature()
                val donnees = withContext(Dispatchers.Default) { taxRef.aDesDonnees } && config.datasetsCacheJson.isNotEmpty()
                donneesChargees = donnees
                resumeCacheVisible = donnees
                if (donnees) restaurerCaches()
            }
        }
    }

    // ─── Chargement des données ───

    /** « Charger les données » ; [forcerTaxRef] : appui long, rechargement complet des taxons même
     *  à version inchangée. La synchro tourne hors de l'écran (service au premier plan sous
     *  Android, tâche de fond sous iOS) ; l'écran suit son état. */
    fun charger(forcerTaxRef: Boolean = false) {
        enregistrerConnexion()
        if (forcerTaxRef) _evenements.trySend(EvenementParametres.Toast("Rechargement complet forcé (taxons)", longue = false))
        _evenements.trySend(EvenementParametres.DemanderNotifications)
        services.synchro.accuserReception()
        synchroEnCours = true
        texteSynchro = MessageColore(
            if (forcerTaxRef) "Rechargement complet (taxons forcés)…" else "Démarrage de la synchronisation…",
            NatureMessage.NEUTRE,
        )
        services.synchro.lancer(forcerTaxRef)
    }

    private suspend fun terminerSynchro(texte: String, succes: Boolean) {
        // Le menu se repeuple depuis les caches neufs.
        ListeTaxons.oublier()
        restaurerCaches()
        listeEnAttente?.let { (choisie, precedente) ->
            listeEnAttente = null
            if (precedente != null && listeChoisie?.id == choisie.id && choisie.id !in taxRef.listesSynchronisees) {
                config.taxaListeId = precedente.id.toString()
                listeChoisie = precedente
                _evenements.trySend(
                    EvenementParametres.Toast("Liste « ${choisie.nom} » non chargée (pas de réseau ?) : la liste « ${precedente.nom} » reste choisie.", longue = true),
                )
            }
        }
        synchroEnCours = false
        texteSynchro = MessageColore(
            texte.trim().ifBlank { "Chargement terminé" },
            if (succes) NatureMessage.TERMINE else NatureMessage.ECHEC,
        )
        majStatut()
        if (withContext(Dispatchers.Default) { taxRef.aDesDonnees }) {
            resumeCacheVisible = true
            donneesChargees = true
            libelleCharger = "Recharger les données"
        }
        bandeau = texteBandeauRechargement(config, taxRef)
        // L'état terminal est consommé : il ne se rejouera pas à la prochaine ouverture.
        services.synchro.accuserReception()
        // Ce qui lit TaxRef en entier (non rechargé, il n'est peut-être pas encore en mémoire), à part.
        relancerCompteurs()
        viewModelScope.launch { majMessageListe() }
    }

    /** La question « Vider le cache ? » est posée. */
    var confirmationVidage by mutableStateOf(false)
        private set

    /** « Vider le cache » : une question d'abord. Un toucher effaçait tout, et sans réseau, plus
     *  aucune saisie n'était possible avant un nouveau chargement. */
    fun demanderVidage() {
        confirmationVidage = true
    }

    fun fermerVidage() {
        confirmationVidage = false
    }

    /** Vidage confirmé : caches synchronisés et sélections, comme un retour à zéro. Les saisies en
     *  attente d'envoi restent. */
    fun viderLeCache() {
        confirmationVidage = false
        services.cachesSynchronises.viderCachesSynchronises()
        ListeTaxons.oublier()
        config.reinitialiserSelectionsOcctax()
        viderSelectionsAffichees()
        libelleCharger = "Charger les données"
        resumeCacheVisible = false
        donneesChargees = false
        viewModelScope.launch {
            majCompteurs()
            majMessageListe()
            majStatut()
        }
    }

    /** « Détails » : le contenu du cache regroupé par liste, ou un message s'il n'y a rien. */
    fun montrerTaxonsParListes() {
        viewModelScope.launch {
            val contenu = services.taxonsParListes()
            if (contenu == null) {
                _evenements.send(EvenementParametres.Toast("Aucun taxon en cache (ou appartenance aux listes non synchronisée).", longue = true))
            } else {
                taxonsParListes = contenu
            }
        }
    }

    fun fermerTaxonsParListes() {
        taxonsParListes = null
    }

    // ─── Sélections ───

    /** Le jeu de données par défaut, obligatoire ; null le retire (croix de la pastille sous iOS). */
    fun choisirDataset(dataset: GeoNatureDataset?) {
        config.idDataset = dataset?.id?.toString() ?: ""
        config.nomDataset = dataset?.nom ?: ""
        datasetChoisi = dataset
        appliquerListes()
        viewModelScope.launch {
            majMessageListe()
            majStatut()
        }
    }

    /** La liste choisie qui attend son chargement, et la précédente, chargée : si le chargement échoue
     *  (hors réseau), la précédente revient. Sans elle, la configuration restait incomplète et
     *  Paramètres ne se quittait plus (audit du 21 octobre, M30). */
    private var listeEnAttente: Pair<GeoNatureListe, GeoNatureListe?>? = null

    /** La liste de taxons par défaut, obligatoire ; null la retire (croix de la pastille sous iOS). */
    fun choisirListe(liste: GeoNatureListe?) {
        val precedente = listeChoisie
        config.taxaListeId = liste?.id?.toString() ?: ""
        listeChoisie = liste
        // La synchro ne charge que les listes utiles (21 octobre) : une liste choisie qui n'est pas
        // sur le téléphone se télécharge aussitôt, avec le reste des données.
        if (liste != null && !synchroEnCours && liste.id !in taxRef.listesSynchronisees) {
            listeEnAttente = liste to precedente?.takeIf { it.id in taxRef.listesSynchronisees }
            _evenements.trySend(EvenementParametres.Toast("Chargement de la liste « ${liste.nom} »…", longue = false))
            charger()
            return
        }
        viewModelScope.launch {
            majMessageListe()
            majStatut()
        }
    }

    /** L'observateur par défaut ; null le ramène à l'utilisateur connecté. */
    fun choisirObservateur(observateur: GeoNatureObservateur?) {
        config.observateurDefautId = observateur?.idRole?.toString() ?: ""
        config.observateurDefautNom = observateur?.nomComplet ?: ""
        observateurChoisi = ReglesSelectionOcctax.observateurChoisi(config.observateurDefautId, observateurs, config.idRoleUtilisateur)
        viewModelScope.launch { majStatut() }
    }

    /** Repeuple les trois choix depuis les caches JSON des réglages et purge les sélections
     *  fantômes. Un cache vide (rien chargé) ne purge rien. Rien n'est choisi d'office, sauf
     *  l'observateur : l'utilisateur connecté. */
    private fun restaurerCaches() {
        val tous = listeDepuisJson(GeoNatureDataset.serializer(), config.datasetsCacheJson)
        if (tous.isNotEmpty()) {
            tousDatasets = tous
            datasets = ReglesSelectionOcctax.datasetsProposes(tous, config.datasetsCreablesOcctax)
            val valide = ReglesSelectionOcctax.datasetValide(config.idDataset, datasets)
            if (valide != null) {
                config.nomDataset = valide.nom
            } else if (config.idDataset.isNotEmpty() || config.nomDataset.isNotEmpty()) {
                config.idDataset = ""
                config.nomDataset = ""
            }
            datasetChoisi = valide
        }
        val listesCache = listeDepuisJson(GeoNatureListe.serializer(), config.listesCacheJson)
        if (listesCache.isNotEmpty()) {
            toutesListes = listesCache
            if (ReglesSelectionOcctax.listeChoisie(config.taxaListeId, listesCache) == null) config.taxaListeId = ""
        }
        appliquerListes()
        val observateursCache = listeDepuisJson(GeoNatureObservateur.serializer(), config.observateursCacheJson)
        observateurs = ReglesSelectionOcctax.observateursProposes(observateursCache, config.idRoleUtilisateur, config.nomUtilisateur)
        if (observateursCache.isNotEmpty() && observateurs.none { it.idRole.toString() == config.observateurDefautId }) {
            config.observateurDefautId = ""
            config.observateurDefautNom = ""
        }
        observateurChoisi = ReglesSelectionOcctax.observateurChoisi(config.observateurDefautId, observateurs, config.idRoleUtilisateur)
    }

    /** Les listes proposées pour le jeu choisi (celle qu'il impose, ou toutes) et la liste choisie. */
    private fun appliquerListes() {
        listes = ReglesSelectionOcctax.listesProposees(toutesListes, datasetChoisi)
        listeChoisie = ReglesSelectionOcctax.listeChoisie(config.taxaListeId, toutesListes)
    }

    // ─── Statut ───

    private suspend fun majStatut() {
        configurationComplete = withContext(Dispatchers.Default) { configurationComplete(config, taxRef) }
    }

    private suspend fun majCompteurs() {
        compteurs = services.compteursCache()
    }

    private var tacheCompteurs: Job? = null

    /** Recompte les caches à part, le dernier compte l'emportant : il charge TaxRef s'il ne l'est pas
     *  encore, et ni l'écran ni le suivi de la synchro n'attendent ce chargement. */
    private fun relancerCompteurs() {
        tacheCompteurs?.cancel()
        tacheCompteurs = viewModelScope.launch { majCompteurs() }
    }

    /** Ce que le cache a de la liste choisie, ou de celle qu'impose le jeu choisi ; rien sans choix. */
    private suspend fun majMessageListe() {
        val id = datasetChoisi?.idTaxaList ?: config.taxaListeId.trim().toIntOrNull()
        messageListe = withContext(Dispatchers.Default) { messageListe(taxRef, id) }
    }

    // ─── Sortie ───

    /** Coche « Valider et fermer » : enregistre la connexion, puis ferme si la configuration est
     *  complète, sinon dit ce qui manque. */
    fun valider() {
        enregistrerConnexion()
        viewModelScope.launch {
            majStatut()
            if (configurationComplete) {
                _evenements.send(EvenementParametres.Fermer)
            } else {
                _evenements.send(
                    EvenementParametres.Toast(
                        "Configuration incomplète : renseignez la connexion, chargez les données, " +
                            "puis choisissez jeu de données / liste / observateur.",
                        longue = true,
                    )
                )
            }
        }
    }

    /** Retour système d'Android : bloqué tant que la configuration n'est pas complète. La connexion
     *  saisie n'est pas enregistrée (comme sous Android). */
    fun retour() {
        viewModelScope.launch {
            majStatut()
            if (configurationComplete) {
                _evenements.send(EvenementParametres.Fermer)
            } else {
                _evenements.send(
                    EvenementParametres.Toast(
                        "Complétez la configuration (connexion, données, sélections) pour accéder à l'application.",
                        longue = true,
                    )
                )
            }
        }
    }
}
