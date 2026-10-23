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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SortiesFragment.kt) : ce que
// le fragment posait sur ses vues devient un état observable ; les règles sont dans
// occtax/MesSaisies, l'envoi groupé dans envoi/EnvoiGroupe.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.envoi.EnvoiGroupe
import fr.ariegenature.geomys.gpx.TAILLE_MAX_GPX
import fr.ariegenature.geomys.gpx.importerGPX
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.occtax.MesSaisies
import fr.ariegenature.geomys.occtax.OngletMesSaisies
import fr.ariegenature.geomys.occtax.TextesProfils
import fr.ariegenature.geomys.occtax.VerificationProfil
import fr.ariegenature.geomys.ui.composants.FichierChoisi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un dialogue d'information : titre, message, bouton « OK ». */
data class Information(val titre: String, val message: String)

/** Message bref à afficher une fois. */
data class MessageBref(val texte: String, val longue: Boolean = false)

/** Plafond des fichiers GPX importés, pour le sélecteur de fichiers. */
const val TAILLE_MAX_IMPORT_GPX: Long = TAILLE_MAX_GPX

/** L'écran « Mes saisies » (`SortiesFragment`) : les sorties OccTax du store, par onglet, leur
 *  envoi, leur suppression et l'import GPX. */
class MesSaisiesViewModel(private val services: ServicesGeoNature) : ViewModel() {
    private val store = services.sortieStore
    private val config = services.config

    private val _messages = Channel<MessageBref>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    var toutes by mutableStateOf<List<Sortie>>(emptyList())
        private set
    var onglet by mutableStateOf(OngletMesSaisies.A_ENVOYER)
        private set

    /** Un envoi (groupé ou unitaire) est en cours : bouton « Tout envoyer » masqué, nouveaux envois
     *  et suppressions refusés. */
    var envoiEnCours by mutableStateOf(false)
        private set

    /** Progression d'envoi INLINE, sous les onglets : la liste reste consultable pendant l'envoi. */
    var messageEnvoi by mutableStateOf<String?>(null)
        private set

    // ─── Dialogues ───
    var suppressionDemandee by mutableStateOf<Sortie?>(null)
        private set
    /** Les sorties à envoyer, figées à la demande de confirmation. */
    var confirmationToutEnvoyer by mutableStateOf<List<Sortie>?>(null)
        private set
    var information by mutableStateOf<Information?>(null)
        private set
    var especesDe by mutableStateOf<Sortie?>(null)
        private set
    var echecEcritureImport by mutableStateOf(false)
        private set

    /** Une saisie dont le serveur signale des données exceptionnelles, et ces données : l'envoi attend
     *  « Envoyer quand même ». */
    var confirmationExceptionnelles by mutableStateOf<Pair<Sortie, List<Pair<Observation, VerificationProfil>>>?>(null)
        private set

    /** Ce que la confirmation de « Tout envoyer » ajoute sur les données exceptionnelles. */
    var complementToutEnvoyer by mutableStateOf("")
        private set

    // ─── Profils de taxons (« Donnée exceptionnelle ») ───

    /** Les vérifications des profils de taxons, que l'écran suit. */
    val profils: StateFlow<Map<String, VerificationProfil>> get() = services.profils.resultats

    private var verificationProfils: Job? = null

    /** Les données exceptionnelles de [sortie] d'après [resultats]. */
    fun exceptionnelles(sortie: Sortie, resultats: Map<String, VerificationProfil>): List<Pair<Observation, VerificationProfil>> =
        if (sortie.envoyeGeoNature) emptyList() else services.profils.exceptionnelles(sortie.observations, resultats)

    /** À chaque entrée dans l'écran : les saisies à envoyer sont vérifiées en arrière-plan, celles
     *  qui ne l'ont pas encore été (pas de réseau pendant la saisie, observation modifiée depuis). */
    private fun verifierProfils(sorties: List<Sortie>) {
        if (verificationProfils?.isActive == true) return
        verificationProfils = viewModelScope.launch {
            services.profils.oublierSauf(sorties.flatMap { s -> s.observations.map { it.id } }.toSet())
            services.profils.verifier(MesSaisies.filtrer(sorties, OngletMesSaisies.A_ENVOYER).flatMap { it.observations })
        }
    }

    /** CRUVED C du module OCCTAX (détecté à la synchro, comme OccHab) : sans droit de création, le
     *  POST partirait en 403 — les boutons d'envoi sont masqués. */
    val envoiAutorise: Boolean get() = config.occtaxPeutCreer

    val sorties: List<Sortie> get() = MesSaisies.filtrer(toutes, onglet)

    val nbEnvoyables: Int get() = MesSaisies.envoyables(toutes).size

    val boutonToutEnvoyerVisible: Boolean
        get() = EnvoiGroupe.boutonVisible(onglet == OngletMesSaisies.A_ENVOYER, envoiAutorise, nbEnvoyables, envoiEnCours)

    fun rafraichir() {
        viewModelScope.launch { toutes = store.charger() }
    }

    /** L'entrée dans l'écran (et chaque retour) : la liste relue, puis les profils vérifiés. */
    fun afficher() {
        viewModelScope.launch {
            toutes = store.charger()
            verifierProfils(toutes)
        }
    }

    fun choisirOnglet(choix: OngletMesSaisies) {
        onglet = choix
        rafraichir()
    }

    /** « Continuer la saisie » : refusé pendant un envoi. Une saisie marquée envoyée en cours de route
     *  n'enregistrait plus les ajouts, qui ne partaient jamais (audit du 21 octobre, M35). */
    fun peutContinuer(): Boolean {
        if (envoiEnCours) _messages.trySend(MessageBref("Un envoi est en cours — réessayez ensuite."))
        return !envoiEnCours
    }

    // ─── Suppression ───

    fun demanderSuppression(sortie: Sortie) {
        if (envoiEnCours) {
            _messages.trySend(MessageBref("Un envoi est en cours — réessayez ensuite."))
            return
        }
        suppressionDemandee = sortie
    }

    fun supprimer() {
        val sortie = suppressionDemandee ?: return
        suppressionDemandee = null
        viewModelScope.launch {
            store.supprimer(sortie.id)
            toutes = store.charger()
        }
    }

    fun annulerSuppression() {
        suppressionDemandee = null
    }

    // ─── Espèces ───

    fun listerEspeces(sortie: Sortie) {
        especesDe = sortie
    }

    fun fermerEspeces() {
        especesDe = null
    }

    fun fermerInformation() {
        information = null
    }

    fun fermerEchecEcriture() {
        echecEcritureImport = false
    }

    // ─── Envoi ───

    /** Envoie une seule sortie vers GeoNature (bouton « Envoyer » de la ligne). Marque la sortie
     *  envoyée en cas de succès et rafraîchit. Des données exceptionnelles (profils de taxons) font
     *  d'abord demander « Envoyer quand même ». */
    fun envoyer(sortie: Sortie) {
        if (envoiEnCours) {
            _messages.trySend(MessageBref(EnvoiGroupe.MESSAGE_DEJA_EN_COURS))
            return
        }
        if (!config.connexionConfiguree) {
            information = Information(EnvoiGroupe.TITRE_CONFIGURATION_REQUISE, EnvoiGroupe.MESSAGE_CONFIGURATION_REQUISE)
            return
        }
        envoiEnCours = true
        messageEnvoi = MESSAGE_VERIFICATION
        viewModelScope.launch {
            val exceptionnelles = try {
                services.profils.avantEnvoi(sortie.observations)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (exceptionnelles.isNotEmpty()) {
                envoiEnCours = false
                messageEnvoi = null
                confirmationExceptionnelles = sortie to exceptionnelles
            } else {
                lancerEnvoi(sortie)
            }
        }
    }

    /** « Envoyer quand même » : la saisie part malgré les données exceptionnelles. */
    fun envoyerQuandMeme() {
        val sortie = confirmationExceptionnelles?.first ?: return
        confirmationExceptionnelles = null
        if (envoiEnCours) return
        envoiEnCours = true
        viewModelScope.launch { lancerEnvoi(sortie) }
    }

    fun annulerEnvoiExceptionnelles() {
        confirmationExceptionnelles = null
    }

    private suspend fun lancerEnvoi(sortie: Sortie) {
        messageEnvoi = "Envoi de la saisie vers GeoNature…"
        try {
            val res = services.envoiSortie.envoyerSortieVersGeoNature(sortie, config)
            toutes = store.charger()
            // « Envoi » : même titre de récap que Mes visites / Mes stations.
            information = Information(if (res.succes) "Envoi" else "Erreur d'envoi", res.message)
        } finally {
            envoiEnCours = false
            messageEnvoi = null
        }
    }

    /** « Tout envoyer » : refus si un envoi tourne ou si la connexion manque, sinon confirmation. */
    fun demanderToutEnvoyer() {
        if (envoiEnCours) {
            _messages.trySend(MessageBref(EnvoiGroupe.MESSAGE_DEJA_EN_COURS))
            return
        }
        if (!config.connexionConfiguree) {
            information = Information(EnvoiGroupe.TITRE_CONFIGURATION_REQUISE, EnvoiGroupe.MESSAGE_CONFIGURATION_REQUISE)
            return
        }
        envoiEnCours = true
        messageEnvoi = MESSAGE_VERIFICATION
        viewModelScope.launch {
            try {
                val aEnvoyer = MesSaisies.envoyables(store.charger())
                if (aEnvoyer.isEmpty()) return@launch
                // Les données exceptionnelles s'ajoutent à la confirmation (profils de taxons).
                complementToutEnvoyer = TextesProfils.complementToutEnvoyer(services.profils.avantEnvoi(aEnvoyer.flatMap { it.observations }))
                confirmationToutEnvoyer = aEnvoyer
            } finally {
                envoiEnCours = false
                messageEnvoi = null
            }
        }
    }

    fun annulerToutEnvoyer() {
        confirmationToutEnvoyer = null
    }

    /** Pousse séquentiellement toutes les sorties envoyables (anti-doublon par observation déjà
     *  partie) ; la liste se met à jour au fil des envois, le récapitulatif vient à la fin. */
    fun toutEnvoyer() {
        val aEnvoyer = confirmationToutEnvoyer ?: return
        confirmationToutEnvoyer = null
        envoiEnCours = true
        viewModelScope.launch {
            try {
                val recap = EnvoiGroupe.envoyer(
                    aEnvoyer,
                    progression = { messageEnvoi = it },
                    envoyerUne = { s -> services.envoiSortie.envoyerSortieVersGeoNature(s, config).let { it.succes to it.message } },
                    apresChaque = { rafraichir() },
                )
                information = Information(recap.titre, recap.message)
            } finally {
                envoiEnCours = false
                messageEnvoi = null
                rafraichir()
            }
        }
    }

    // ─── Import GPX ───

    fun importer(fichier: FichierChoisi) {
        when (fichier) {
            FichierChoisi.TropGrosOuIllisible ->
                _messages.trySend(MessageBref("Fichier GPX illisible ou trop volumineux (plus de 20 Mo)", longue = true))
            is FichierChoisi.Erreur -> information = Information(ERREUR_IMPORT, fichier.message ?: ERREUR_IMPORT_GPX)
            is FichierChoisi.Lu -> viewModelScope.launch {
                val sortie = withContext(Dispatchers.Default) { importerGPX(fichier.octets) }
                if (sortie == null) {
                    information = Information(ERREUR_IMPORT, ERREUR_IMPORT_GPX)
                    return@launch
                }
                // Écriture disque échouée (stockage plein ?) : prévenir au lieu de basculer sur
                // l'onglet Importées comme si tout avait réussi.
                if (!store.ajouter(sortie.copy(estImportee = true))) {
                    echecEcritureImport = true
                    return@launch
                }
                onglet = OngletMesSaisies.IMPORTEES
                toutes = store.charger()
            }
        }
    }

    companion object {
        /** Avant l'envoi, les profils de taxons des observations pas encore vérifiées. */
        const val MESSAGE_VERIFICATION = "Vérification des données auprès de GeoNature…"
        const val ERREUR_IMPORT = "Erreur d'import"
        const val ERREUR_IMPORT_GPX =
            "Impossible de lire ce fichier GPX. Vérifiez qu'il contient un parcours ou des waypoints valides."
        const val CONSIGNE_IMPORT = "Libérez de l'espace puis relancez l'import du fichier GPX."
    }
}
