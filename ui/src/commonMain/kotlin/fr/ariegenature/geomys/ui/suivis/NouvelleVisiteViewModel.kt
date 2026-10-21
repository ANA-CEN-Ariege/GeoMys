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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/NouvelleVisiteFragment.kt) :
// l'ouverture (contexte restitué d'une saisie rouverte), l'état du bouton d'enregistrement, l'enregistrement
// dans la file d'envoi, la garde de sortie et la complétion de la visite parente. Les boutons et
// l'enchaînement des saisies sont ceux du web (monitoring-form de gn_module_monitoring, 20 octobre 2026).
// Les règles et les textes sont dans monitoring/NouvelleVisite ; l'écran traduit les événements en
// navigation.

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.MediaSuivi
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.monitoring.ActionValider
import fr.ariegenature.geomys.monitoring.DroitsSuivis
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.monitoring.FormulairePrepare
import fr.ariegenature.geomys.monitoring.NouvelleVisite
import fr.ariegenature.geomys.monitoring.PreparationFormulaire
import fr.ariegenature.geomys.monitoring.SuiteEnregistrement
import fr.ariegenature.geomys.monitoring.form.FormulaireEtat
import fr.ariegenature.geomys.monitoring.form.ValeursFormulaire
import fr.ariegenature.geomys.monitoring.sourcesFormulaire
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Ce avec quoi s'ouvre le formulaire (les arguments de `nouvelleVisiteFragment`). [editUuid] : la
 *  saisie en attente à modifier ; [modeCompletion] : ouverte pour compléter ses champs obligatoires ;
 *  [messageEphemere] : affiché une fois le formulaire à l'écran. */
data class OuvertureNouvelleVisite(
    val moduleCode: String,
    val parentObjectType: String = "",
    val parentId: Int = -1,
    val titreSite: String = "",
    val editUuid: String = "",
    val fil: List<FilSegment> = emptyList(),
    val childObjectType: String = "",
    val parentUuidLocal: String = "",
    val modeCompletion: Boolean = false,
    val messageEphemere: String = "",
)

/** Les dialogues de l'écran. */
sealed interface DialogueNouvelleVisite {
    /** Retour sur un formulaire modifié : enregistrer ([enregistrable]), quitter, ou continuer. */
    data class GardeSortie(val titre: String, val message: String, val enregistrable: Boolean, val continuer: String) : DialogueNouvelleVisite

    /** Fin de visite : la visite parente enregistrée « à compléter ». */
    data class Completer(val parent: SaisieEnAttente) : DialogueNouvelleVisite

    /** Échec d'écriture de la file d'envoi. */
    data class EcritureImpossible(val message: String) : DialogueNouvelleVisite
}

/** Ce que l'écran doit faire. */
sealed interface EvenementNouvelleVisite {
    data class Toast(val texte: String, val longue: Boolean) : EvenementNouvelleVisite

    /** Retour à l'écran précédent. */
    data object Retour : EvenementNouvelleVisite

    /** Un niveau du fil d'Ariane, ou la maison. */
    data class Niveau(val cible: List<FilSegment>) : EvenementNouvelleVisite

    /** Un autre formulaire remplace celui-ci : saisie enfant, ou complétion de la visite parente. */
    data class Remplacer(val ouverture: OuvertureNouvelleVisite) : EvenementNouvelleVisite

    /** La fiche du parent serveur remplace le formulaire. */
    data class Fiche(val moduleCode: String, val objectType: String, val id: Int, val fil: List<FilSegment>) : EvenementNouvelleVisite
}

/** Le formulaire d'une nouvelle saisie des Suivis, ou d'une saisie en attente rouverte. */
class NouvelleVisiteViewModel(
    private val services: ServicesGeoNature,
    private val ouverture: OuvertureNouvelleVisite,
) : ViewModel() {

    // Le contexte, restitué de la saisie rouverte qui l'a enregistré.
    private var moduleCode = ouverture.moduleCode
    private var childObjectType = ouverture.childObjectType
    private var parentObjectType = ouverture.parentObjectType
    private var parentId = ouverture.parentId
    private var parentUuidLocal = ouverture.parentUuidLocal
    private val editUuid = ouverture.editUuid.takeIf { it.isNotEmpty() }
    private val edition = editUuid != null
    val fil: List<FilSegment> = ouverture.fil

    /** Le protocole, celui de la saisie rouverte en édition. */
    val moduleCodeCourant: String get() = moduleCode

    var titre by mutableStateOf(NouvelleVisite.titreProvisoire(edition, null))
        private set
    var contexte by mutableStateOf("")
        private set

    /** Le formulaire, null tant qu'il se prépare (ou s'il n'a pu l'être). */
    var etat by mutableStateOf<FormulaireEtat?>(null)
        private set
    var boutonActif by mutableStateOf(false)
        private set

    /** Les champs obligatoires vides marqués d'une barre rouge. */
    var barres by mutableStateOf<Set<String>>(emptySet())
        private set
    var dialogue by mutableStateOf<DialogueNouvelleVisite?>(null)
        private set

    /** Le formulaire n'a pu être préparé : rien à enregistrer. */
    var impossible by mutableStateOf(false)
        private set

    /** L'interrupteur « Enchaîner les saisies » du web : proposé sur un nouvel objet d'un type
     *  `chained`, éteint à l'ouverture, gardé d'une saisie enchaînée à la suivante. */
    var enchainementPropose by mutableStateOf(false)
        private set
    var enchainer by mutableStateOf(false)

    /** « Valider » ou « Valider et enchaîner les saisies ». */
    val libelleBouton: String get() = NouvelleVisite.libelleBouton(edition, enchainer)

    /** « Valider et saisir des Observations » : l'enfant unique du type, sur un nouvel objet et
     *  hors enchaînement ; null sans ce bouton. */
    val libelleSaisirEnfants: String?
        get() = formulaire?.takeIf { !edition && !enchainer && it.enfantUnique != null }
            ?.let { NouvelleVisite.libelleSaisirEnfants(it.libelleEnfants ?: it.enfantUnique.orEmpty()) }

    /** Le droit de créer l'enfant unique ; sinon son bouton est grisé, comme le « + » du web. */
    var saisirEnfantsPermis by mutableStateOf(true)
        private set

    private val _evenements = Channel<EvenementNouvelleVisite>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    private var formulaire: FormulairePrepare? = null
    private var accords = NouvelleVisite.Accords(null, null)
    private var contexteDeBase = ""
    private var valeursPreremplies: Map<String, Any?>? = null
    private var medias: List<MediaSuivi> = emptyList()
    private var messageEphemere = ouverture.messageEphemere.takeIf { it.isNotBlank() }

    /** Les valeurs juste après la préparation : seule une saisie de l'utilisateur les change. */
    private var valeursApresRendu: Map<String, Any?>? = null

    /** Garde anti double toucher : deux « Enregistrer » rapides créaient deux saisies. */
    private var enCoursEnvoi = false

    /** L'enregistrement INCOMPLET n'est autorisé que sur les infos générales d'une visite. */
    private val incompletAutorise get() = NouvelleVisite.estObjetDeNiveauVisite(parentObjectType)

    val dossierMedias: String get() = services.cheminMedias

    init {
        viewModelScope.launch {
            ouvrir()
            chargerFormulaire()
        }
    }

    private suspend fun ouvrir() {
        // Édition : la saisie de la file porte le contexte de sa création, que l'écran appelant n'a
        // pas à retransmettre, et les valeurs à reprendre.
        editUuid?.let { uuid -> services.outbox.tout().firstOrNull { it.uuid == uuid } }?.let { saisie ->
            moduleCode = saisie.moduleCode
            childObjectType = saisie.objectType
            saisie.parentObjectType?.let { parentObjectType = it }
            saisie.parentIdServeur?.let { parentId = it }
            saisie.parentUuidLocal?.let { parentUuidLocal = it }
            valeursPreremplies = NouvelleVisite.valeursPourEdition(saisie)
            medias = saisie.mediasDetailles()
        }
        titre = NouvelleVisite.titreProvisoire(
            edition,
            if (childObjectType.isNotEmpty() && moduleCode.isNotEmpty()) services.monitoringObjets.libelleNouveau(moduleCode, childObjectType) else null,
        )
        contexteDeBase = NouvelleVisite.contexte(ouverture.titreSite, moduleCode)
        contexte = contexteDeBase
    }

    private suspend fun chargerFormulaire() {
        if (moduleCode.isEmpty()) return
        val preparation = NouvelleVisite.preparer(
            services.sourcesFormulaire(), moduleCode, childObjectType, parentObjectType, valeursPreremplies, medias,
        )
        when (preparation) {
            // Pas de faux formulaire (le mode démo d'Android) : le contexte dit pourquoi, sans rien à enregistrer.
            is PreparationFormulaire.Impossible -> {
                contexte = NouvelleVisite.contexteAvecDiagnostic(contexteDeBase, preparation.messages)
                formulaire = null
                etat = null
                impossible = true
            }
            is PreparationFormulaire.Prete -> {
                val f = preparation.formulaire
                formulaire = f
                enchainementPropose = !edition && f.schemaObjet.enchainable
                saisirEnfantsPermis = f.enfantUnique?.let { services.monitoringModules.peutCreer(services.config, moduleCode, it) } ?: true
                accords = NouvelleVisite.Accords(f.labelType, f.genre)
                titre = if (edition) NouvelleVisite.titreEdition(f.labelType, f.genre) else NouvelleVisite.titreNouvelle(f.labelType, f.genre)
                val nouvel = FormulaireEtat(f.champs, services.taxRef, viewModelScope, f.champsDirtyInitiaux) { services.synchro.actif }
                // Règles `change` du schéma : remplissage des champs dépendants.
                nouvel.setReglesChange(f.schemaObjet.changeRules)
                // Posées dans les champs, les valeurs reprises ne servent plus (formulaire suivant d'une chaîne).
                valeursPreremplies = null
                medias = emptyList()
                // Référence « formulaire vierge », après les règles et les pré-remplissages.
                valeursApresRendu = nouvel.lireValeurs()
                enCoursEnvoi = false
                nouvel.setOnChangement { majBouton(nouvel) }
                etat = nouvel
                contexte = NouvelleVisite.contexteAvecDiagnostic(contexteDeBase, f.avertissements)
                // Une seule fois, le formulaire à l'écran.
                messageEphemere?.let {
                    messageEphemere = null
                    _evenements.trySend(EvenementNouvelleVisite.Toast(it, longue = true))
                }
            }
        }
    }

    /** Seule une valeur INVALIDE (hors bornes, espèce non reconnue) bloque l'enregistrement d'une
     *  visite : ses obligatoires vides la font partir « à compléter » (heure et conditions de fin ne
     *  sont connues qu'en partant). Une saisie d'espèce, elle, doit être complète. Les barres rouges
     *  marquent, comme le web, chaque obligatoire vide et chaque valeur refusée. */
    private fun majBouton(e: FormulaireEtat) {
        if (!enCoursEnvoi) {
            // Une propriété masquée requise sans valeur : le web ne permet jamais l'enregistrement.
            val sansInvalide = e.champsInvalides().isEmpty() && formulaire?.proprietesMasqueesManquantes.isNullOrEmpty()
            boutonActif = if (incompletAutorise) sansInvalide else sansInvalide && e.champsObligatoiresManquants().isEmpty()
        }
        barres = e.barresRouges()
    }

    /** Un champ requis masqué compte, comme sur le web, et il est dit masqué : on ne le voit pas. */
    private fun manquants(e: FormulaireEtat, f: FormulairePrepare): List<String> =
        if (incompletAutorise) {
            NouvelleVisite.libellesManquants(e.champsObligatoiresManquants(), f.champs, f.champs.map { it.code }.filterNot(e::estVisible).toSet())
        } else {
            emptyList()
        }

    /** Le formulaire a-t-il été touché depuis sa préparation ? Le recalcul différé de la frappe est
     *  appliqué d'abord : la décision se prend sur l'état à jour. */
    private fun modifie(): Boolean {
        val e = etat ?: return false
        e.flushChangementsEnAttente()
        return valeursApresRendu != null && e.lireValeurs() != valeursApresRendu
    }

    fun fermerDialogue() {
        dialogue = null
        // Après l'action du bouton : « Continuer » renonce à la sortie demandée par le fil.
        destination = null
    }

    fun ajouterMedias(code: String, uris: List<String>) {
        etat?.ajouterMedias(code, uris)
    }

    /** Le niveau du fil d'Ariane (ou la maison) où aller une fois la garde de sortie passée ; null :
     *  le retour. */
    private var destination: List<FilSegment>? = null

    /** Retour système : formulaire intact, on sort (en proposant la visite parente à compléter) ;
     *  modifié, on demande. */
    fun retour() {
        destination = null
        if (!modifie() || enCoursEnvoi) {
            viewModelScope.launch { terminerOuCompleterLaVisite() }
            return
        }
        dialogue = gardeSortie()
    }

    /** Fil d'Ariane ou maison : formulaire intact, on va au niveau [cible] ; modifié, la même garde
     *  que le retour. Ils quittaient le formulaire sans rien demander. */
    fun allerVers(cible: List<FilSegment>) {
        if (!modifie() || enCoursEnvoi) {
            _evenements.trySend(EvenementNouvelleVisite.Niveau(cible))
            return
        }
        destination = cible
        dialogue = gardeSortie()
    }

    private fun gardeSortie(): DialogueNouvelleVisite.GardeSortie =
        if (boutonActif) {
            DialogueNouvelleVisite.GardeSortie(
                accords.titreNonEnregistre,
                accords.questionEnregistrer(etat?.let { e -> formulaire?.let { manquants(e, it) } }.orEmpty()),
                enregistrable = true,
                continuer = accords.boutonContinuer,
            )
        } else {
            DialogueNouvelleVisite.GardeSortie(accords.titreNonEnregistre, accords.messageIncomplet, enregistrable = false, continuer = accords.boutonContinuer)
        }

    /** « Quitter sans enregistrer » : le retour, ou le niveau du fil choisi. */
    fun quitter() {
        _evenements.trySend(destination?.let { EvenementNouvelleVisite.Niveau(it) } ?: EvenementNouvelleVisite.Retour)
    }

    /** Sortie de la chaîne : si la VISITE PARENTE a été enregistrée « à compléter », son formulaire
     *  est proposé — c'est le moment où l'on connaît enfin ses valeurs de fin. */
    private suspend fun terminerOuCompleterLaVisite() {
        val parent = parentUuidLocal.takeIf { it.isNotEmpty() }?.let { u -> services.outbox.tout().firstOrNull { it.uuid == u } }
        if (parent == null || !parent.aCompleter) {
            _evenements.send(EvenementNouvelleVisite.Retour)
            return
        }
        dialogue = DialogueNouvelleVisite.Completer(parent)
    }

    /** « Compléter » : le formulaire de la visite parente, en complétion, remplace celui-ci. */
    fun completer(parent: SaisieEnAttente) {
        _evenements.trySend(
            EvenementNouvelleVisite.Remplacer(OuvertureNouvelleVisite(moduleCode = parent.moduleCode, editUuid = parent.uuid, fil = fil, modeCompletion = true)),
        )
    }

    /** Enregistre la saisie dans la file d'envoi (l'envoi au serveur se fait à la demande, depuis
     *  « Mes visites »). [action] : le bouton du web touché ; [puisTerminer] : sortie par la garde
     *  de sortie. */
    fun enregistrer(puisTerminer: Boolean = false, action: ActionValider = ActionValider.VALIDER) {
        if (enCoursEnvoi) return
        val e = etat ?: return
        val f = formulaire ?: return
        // Le recalcul différé peut griser le bouton (champ requis vidé dans les 220 ms) : on s'arrête.
        e.flushChangementsEnAttente()
        if (!boutonActif) return
        // Sans le droit de créer, le message du web (notAllowedMessage), comme le « + » grisé.
        if (!edition && !services.monitoringModules.peutCreer(services.config, moduleCode, f.schemaObjet.type)) {
            _evenements.trySend(EvenementNouvelleVisite.Toast(DroitsSuivis.SANS_PERMISSION_EDITION, longue = true))
            return
        }
        if (action == ActionValider.SAISIR_ENFANTS && !saisirEnfantsPermis) {
            _evenements.trySend(EvenementNouvelleVisite.Toast(DroitsSuivis.SANS_PERMISSION, longue = false))
            return
        }
        enCoursEnvoi = true
        boutonActif = false
        // Pour l'envoi : un champ masqué par une règle garde sa valeur à l'écran, mais ne part pas ;
        // les propriétés masquées du schéma partent avec leurs valeurs fixes.
        val enregistrement = ValeursFormulaire.preparerEnregistrement(f.champs, e.lireValeursPourEnvoi(), f.valeursFixes, e.mediasPourEnvoi())
        val manquants = manquants(e, f)
        // « Enregistrer et quitter » vers un niveau du fil : lu avant que la fermeture du dialogue ne
        // l'oublie.
        val vers = destination.takeIf { puisTerminer }
        viewModelScope.launch {
            val suite = if (editUuid != null) {
                val ok = services.outbox.mettreAJour(editUuid) { ancien -> NouvelleVisite.saisieModifiee(ancien, f, enregistrement, manquants) }
                if (!ok) return@launch echecEcriture(e)
                _evenements.send(EvenementNouvelleVisite.Toast(NouvelleVisite.messageModifie(manquants), longue = manquants.isNotEmpty()))
                NouvelleVisite.suiteApresEdition(
                    ouverture.modeCompletion,
                    parentObjectType.takeIf { it.isNotEmpty() }, parentId.takeIf { it > 0 },
                )
            } else {
                val saisie = NouvelleVisite.nouvelleSaisieEnAttente(
                    moduleCode, f, enregistrement,
                    parentObjectType = parentObjectType.takeIf { it.isNotEmpty() },
                    parentIdServeur = parentId.takeIf { it > 0 },
                    parentUuidLocal = parentUuidLocal.takeIf { it.isNotEmpty() },
                    manquants = manquants,
                )
                // Écriture manquée (stockage plein) : la saisie n'existe NULLE PART, l'utilisateur ne
                // doit pas quitter l'écran en croyant le contraire.
                if (!services.outbox.ajouter(saisie)) return@launch echecEcriture(e)
                _evenements.send(EvenementNouvelleVisite.Toast(accords.messageEnregistre(manquants), longue = true))
                NouvelleVisite.suiteApresCreation(saisie, f, action, enchainer, puisTerminer, NouvelleVisite.valeursPourEdition(saisie))
            }
            if (vers != null) _evenements.send(EvenementNouvelleVisite.Niveau(vers)) else suivre(suite)
        }
    }

    private fun echecEcriture(e: FormulaireEtat) {
        enCoursEnvoi = false
        majBouton(e)
        dialogue = DialogueNouvelleVisite.EcritureImpossible(accords.messageEcritureImpossible)
    }

    private suspend fun suivre(suite: SuiteEnregistrement) {
        when (suite) {
            SuiteEnregistrement.Retour -> _evenements.send(EvenementNouvelleVisite.Retour)
            SuiteEnregistrement.TerminerOuCompleter -> terminerOuCompleterLaVisite()
            // La page du parent : son niveau du fil s'il le termine (sans empiler une seconde fiche),
            // sinon sa fiche.
            is SuiteEnregistrement.Fiche -> {
                val dernier = fil.lastOrNull()
                if (dernier != null && dernier.type == suite.objectType && dernier.id == suite.id) {
                    _evenements.send(EvenementNouvelleVisite.Niveau(fil))
                } else {
                    _evenements.send(EvenementNouvelleVisite.Fiche(moduleCode, suite.objectType, suite.id, fil))
                }
            }
            is SuiteEnregistrement.Enchainer -> _evenements.send(
                EvenementNouvelleVisite.Remplacer(
                    OuvertureNouvelleVisite(
                        moduleCode = moduleCode,
                        parentObjectType = suite.parentObjectType,
                        titreSite = suite.titreSite,
                        fil = fil,
                        childObjectType = suite.childObjectType,
                        parentUuidLocal = suite.parentUuidLocal,
                    ),
                ),
            )
            // Saisie suivante sur le même parent : tout est repréparé (défauts, datalists, utilisateur
            // connecté), seuls les champs `keep` repris de la précédente, comme le web (resetObjForm).
            // La ligne « Sur : … — Protocole : … » est reposée avec le nouveau formulaire, ses
            // éventuels diagnostics à la suite (GeoMys-Android `5b981f3` : la purge du bandeau
            // l'emportait). Le bouton reste grisé jusqu'au nouveau formulaire, pour qu'un second
            // toucher n'enregistre pas deux fois la même saisie.
            is SuiteEnregistrement.Reinitialiser -> {
                valeursPreremplies = suite.gardes.takeIf { it.isNotEmpty() }
                chargerFormulaire()
            }
        }
    }
}
