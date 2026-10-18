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

/** Refus de sortie de Paramètres après une mise à jour qui exige un rechargement. */
const val MESSAGE_RECHARGEMENT_REQUIS =
    "Après cette mise à jour, rechargez les données (« Charger les données ») pour accéder à l'application."

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
    var sectionDonneesVisible by mutableStateOf(false)
        private set
    /** Tous les jeux de données du cache, et ceux qui sont proposés. */
    var tousDatasets by mutableStateOf(emptyList<GeoNatureDataset>())
        private set
    var datasets by mutableStateOf(emptyList<GeoNatureDataset>())
        private set
    var datasetChoisi by mutableStateOf<GeoNatureDataset?>(null)
        private set
    var listes by mutableStateOf(ListesProposees(emptyList(), imposee = false))
        private set
    var listeChoisie by mutableStateOf<GeoNatureListe?>(null)
        private set
    var observateurs by mutableStateOf(emptyList<GeoNatureObservateur>())
        private set
    var observateurChoisi by mutableStateOf<GeoNatureObservateur?>(null)
        private set
    var messageListe by mutableStateOf<MessageListe?>(null)
        private set

    // ─── Bandeau, statut, dialogue ───
    var bandeau by mutableStateOf<String?>(null)
        private set
    var configurationComplete by mutableStateOf(false)
        private set
    var taxonsParListes by mutableStateOf<TaxonsParListes?>(null)
        private set

    /** Vrai après un « Vider le cache » ou un changement d'identité, jusqu'au prochain chargement :
     *  on repart d'une ardoise vierge, sans resélectionner d'office la liste unique. */
    private var selectionsReinitialisees = false
    private var toutesListes = emptyList<GeoNatureListe>()

    init {
        // État initial des sections selon les données en cache : la boîte « Chargement des
        // données » reste visible hors ligne (compteurs consultables) et après une mise à jour qui
        // exige un rechargement ; en première configuration, elle attend une connexion réussie.
        viewModelScope.launch {
            val donneesPresentes = withContext(Dispatchers.Default) { taxRef.count > 0 }
            sectionChargerVisible = donneesPresentes || config.rechargementRequisApresMaj
            resumeCacheVisible = donneesPresentes
            sectionDonneesVisible = donneesPresentes
            libelleCharger = if (donneesPresentes) "Recharger les données" else "Charger les données"
            restaurerCaches()
            majStatut()
            majCompteurs()
            majMessageListe()
            afficherVersionGeoNature()
            bandeau = texteBandeauRechargement(config, taxRef)
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
                        majCompteurs()
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
        sectionDonneesVisible = false
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
        selectionsReinitialisees = true
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
            // automatique. Les sélections et le résumé ne reviennent que si des données sont
            // chargées ET qu'une sélection tient encore (pas après un changement d'identité).
            if (succes) {
                sectionChargerVisible = true
                afficherVersionGeoNature()
                val donnees = withContext(Dispatchers.Default) { taxRef.count > 0 } && config.idDataset.trim().isNotEmpty()
                sectionDonneesVisible = donnees
                resumeCacheVisible = donnees
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
        // Les menus se repeuplent depuis les caches neufs AVANT de lever le mode « ardoise
        // vierge » (sinon la liste unique se resélectionnerait d'office).
        ListeTaxons.oublier()
        restaurerCaches()
        selectionsReinitialisees = false
        synchroEnCours = false
        texteSynchro = MessageColore(
            texte.trim().ifBlank { "Chargement terminé" },
            if (succes) NatureMessage.TERMINE else NatureMessage.ECHEC,
        )
        majCompteurs()
        majMessageListe()
        majStatut()
        if (withContext(Dispatchers.Default) { taxRef.count > 0 }) {
            resumeCacheVisible = true
            sectionDonneesVisible = true
            libelleCharger = "Recharger les données"
        }
        bandeau = texteBandeauRechargement(config, taxRef)
        // L'état terminal est consommé : il ne se rejouera pas à la prochaine ouverture.
        services.synchro.accuserReception()
    }

    /** « Vider le cache » : caches synchronisés et sélections, comme un retour à zéro. */
    fun viderLeCache() {
        services.cachesSynchronises.viderCachesSynchronises()
        ListeTaxons.oublier()
        config.reinitialiserSelectionsOcctax()
        viderSelectionsAffichees()
        libelleCharger = "Charger les données"
        resumeCacheVisible = false
        sectionDonneesVisible = false
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

    fun choisirListe(liste: GeoNatureListe?) {
        config.taxaListeId = liste?.id?.toString() ?: ""
        listeChoisie = liste
        viewModelScope.launch {
            majMessageListe()
            majStatut()
        }
    }

    fun choisirObservateur(observateur: GeoNatureObservateur?) {
        config.observateurDefautId = observateur?.idRole?.toString() ?: ""
        config.observateurDefautNom = observateur?.nomComplet ?: ""
        observateurChoisi = observateur
        viewModelScope.launch { majStatut() }
    }

    /** Repeuple les trois choix depuis les caches JSON des réglages et purge les sélections
     *  fantômes. Un cache vide (rien chargé) ne purge rien. */
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
            appliquerListes()
        }
        val observateursCache = listeDepuisJson(GeoNatureObservateur.serializer(), config.observateursCacheJson)
        if (observateursCache.isNotEmpty()) {
            observateurs = observateursCache
            val choisi = ReglesSelectionOcctax.observateurChoisi(config.observateurDefautId, observateursCache, config.idRoleUtilisateur)
            config.observateurDefautId = choisi?.idRole?.toString() ?: ""
            config.observateurDefautNom = choisi?.nomComplet ?: ""
            observateurChoisi = choisi
        }
    }

    /** Listes proposées pour le jeu de données choisi (la seule qu'il impose, ou toutes) et liste
     *  sélectionnée en conséquence. */
    private fun appliquerListes() {
        if (toutesListes.isEmpty() && datasetChoisi?.idTaxaList == null) return
        val proposees = ReglesSelectionOcctax.listesProposees(toutesListes, datasetChoisi)
        listes = proposees
        val choisie = ReglesSelectionOcctax.listeChoisie(config.taxaListeId, proposees, selectionsReinitialisees)
        if (choisie != config.taxaListeId) config.taxaListeId = choisie
        listeChoisie = proposees.listes.firstOrNull { it.id.toString() == choisie }
    }

    // ─── Statut ───

    private suspend fun majStatut() {
        configurationComplete = withContext(Dispatchers.Default) { configurationComplete(config, taxRef) }
    }

    private suspend fun majCompteurs() {
        compteurs = services.compteursCache()
    }

    private suspend fun majMessageListe() {
        val id = config.taxaListeId.trim().toIntOrNull()
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
                        if (config.rechargementRequisApresMaj) MESSAGE_RECHARGEMENT_REQUIS
                        else "Configuration incomplète : renseignez la connexion, chargez les données, " +
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
                        if (config.rechargementRequisApresMaj) MESSAGE_RECHARGEMENT_REQUIS
                        else "Complétez la configuration (connexion, données, sélections) pour accéder à l'application.",
                        longue = true,
                    )
                )
            }
        }
    }
}
