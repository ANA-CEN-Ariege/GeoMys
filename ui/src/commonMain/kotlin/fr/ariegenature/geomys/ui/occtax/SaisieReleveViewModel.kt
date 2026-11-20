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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SaisieObservationFragment.kt) :
// l'état de l'écran (palette, champ espèce, propositions, dictée) autour du relevé lui-même,
// ReleveEnCours (core/occtax). Le ViewModel survit au passage par les sous-écrans, comme les champs du
// fragment survivaient à la recréation de sa vue.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.occtax.DetailsReleveResult
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.occtax.OuvertureReleve
import fr.ariegenature.geomys.occtax.ReleveEnCours
import fr.ariegenature.geomys.occtax.RequisEspece
import fr.ariegenature.geomys.occtax.VerificationProfil
import fr.ariegenature.geomys.occtax.libelleChampEspece
import fr.ariegenature.geomys.store.ModeSaisie
import fr.ariegenature.geomys.store.NidificationOiseaux
import fr.ariegenature.geomys.store.SuggestionTaxon
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.util.moisCourant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Ce que l'écran du relevé doit faire une fois. */
sealed interface EvenementReleve {
    data class Message(val texte: String) : EvenementReleve

    /** Ouvrir la fiche de l'observation [index], qui porte tous ses champs, dans les deux modes
     *  (demandes du 16 novembre 2026). [nidification] : en mode pro, ouverte d'elle-même pour un
     *  oiseau nicheur, la section « Avancé », qui porte le comportement, est dépliée. */
    data class FicheEspece(val index: Int, val nidification: Boolean = false) : EvenementReleve

    /** Le relevé est terminé : retour à l'écran précédent. */
    data object Termine : EvenementReleve
}

class SaisieReleveViewModel(
    private val services: ServicesGeoNature,
    private val sessionVm: SessionSaisieViewModel,
    ouverture: OuvertureReleve,
) : ViewModel() {
    private val config = services.config
    private val preferences = services.preferences
    val session = sessionVm.session
    val releve = ReleveEnCours(session, config, ouverture, preferences.dernierTaxon())

    /** La saisie simplifiée (mode choisi dans Paramètres) : chaque espèce ajoutée ouvre sa fiche de
     *  l'observation, au lieu de la caractérisation et du dénombrement. Le mode ne change pas pendant un
     *  relevé : il se choisit dans Paramètres. */
    val simplifie: Boolean = preferences.modeSaisie == ModeSaisie.SIMPLIFIE

    /** Libellé du bandeau : celui du flux de saisie (« Saisie multi-taxons »). */
    val typeSaisieLabel: String get() = session.typeSaisieLabel

    private val nidification = NidificationOiseaux(services.taxRef) { Res.readBytes(FICHIER_NIDIFICATION).decodeToString() }

    private val _evenements = Channel<EvenementReleve>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    /** Le champ espèce : groupe, mode, texte, propositions, statut TaxRef. */
    val champ = EtatChampEspece(services, viewModelScope, releve.taxonInitial, ::libelleChampEspece, idDataset = { releve.idDatasetEffectif() })

    val groupes: List<Taxon>? get() = champ.groupes
    val taxon: Taxon get() = champ.taxon
    val nomScientifique: Boolean get() = champ.nomScientifique
    val texte: String get() = champ.texte
    val suggestions: List<SuggestionTaxon> get() = champ.suggestions
    val recherche get() = champ.recherche
    val libelleChamp: String get() = champ.libelleChamp
    val synchroEnCours: Boolean get() = champ.synchroEnCours

    /** Les vérifications des profils de taxons, que l'écran suit pour [avertissements]. */
    val profils: StateFlow<Map<String, VerificationProfil>> get() = services.profils.resultats

    /** « Donnée exceptionnelle » : ce que le serveur signale pour [espece] d'après [resultats], vide
     *  s'il n'y a rien (ou pas encore de vérification). */
    fun avertissements(espece: EspeceReleve, resultats: Map<String, VerificationProfil>): List<String> {
        val groupe = session.observations.value.filter { it.releveId == releve.releveId }
        val obs = groupe.firstOrNull { it.id == (espece.existingId ?: espece.vmId) } ?: return emptyList()
        return services.profils.resultat(obs, groupe, resultats)?.messages.orEmpty()
    }

    /** Nom scientifique à montrer sous l'espèce : seulement s'il diffère du nom affiché (un nom
     *  français a été choisi). */
    fun nomScientifiqueSous(espece: EspeceReleve): String? =
        espece.cdNom?.let { champ.entrees[it]?.sciNom }?.takeIf { !it.equals(espece.espece, ignoreCase = true) }

    /** Le nom de l'espèce au titre de sa fiche (demande du 20 novembre 2026) : son nom français seul,
     *  sinon son nom latin. Le nom choisi s'il est français ; choisie par son nom latin, l'espèce
     *  prend le nom usuel de TaxRef (le premier de ses noms français), et garde son nom latin si elle
     *  n'en a pas. Tant que le référentiel n'est pas lu ([EtatChampEspece.entrees], un état : le titre
     *  suit), le nom choisi. */
    fun nomFrancaisOuLatin(espece: EspeceReleve): String {
        val cdNom = espece.cdNom ?: return espece.espece
        val sciNom = champ.entrees[cdNom]?.sciNom ?: return espece.espece
        if (!sciNom.equals(espece.espece, ignoreCase = true)) return espece.espece
        return champ.nomsFrancais[cdNom]?.firstOrNull { it.isNotBlank() } ?: espece.espece
    }

    private var dejaAffiche = false

    /** À chaque affichage de l'écran (onViewCreated) : le relevé est enregistré. Au retour de la fiche
     *  d'une espèce, la palette GARDE le groupe choisi (16 novembre 2026) : elle revenait au groupe de
     *  la première espèce, comme sous Android, ce qui défaisait le choix à chaque espèce depuis que la
     *  fiche s'ouvre après chacune. Sans jeu de données connu, le relevé commence par ses détails,
     *  comme le web : le jeu est obligatoire. */
    fun afficher() {
        val premier = !dejaAffiche
        dejaAffiche = true
        releve.synchroniser()
        if (premier && (releve.idDatasetEffectif() == null || releve.dateAChoisir())) ouvrirDetails()
    }

    fun choisirTaxon(nouveau: Taxon) = champ.choisirTaxon(nouveau)

    fun basculerNomScientifique(actif: Boolean) = champ.basculerNomScientifique(actif)

    fun saisir(nouveau: String) = champ.saisir(nouveau)

    /** La phrase en cours de dictée : dans le champ, avec les espèces qui lui ressemblent. */
    fun dicterPartiel(entendu: String) = champ.dicterPartiel(entendu)

    /** Une proposition CHOISIE est ajoutée avec son cd_nom, jamais re-résolue depuis son texte : le
     *  cache ne garde qu'un taxon par nom, la re-résolution pouvait enregistrer un autre taxon. */
    fun choisirSuggestion(suggestion: SuggestionTaxon) {
        ajouter(suggestion.nom, suggestion.cdNom)
    }

    /** Fin de la dictée : ce qui a été entendu dans le champ, et les espèces reconnues ou proches dans
     *  la liste. Aucune n'est ajoutée sans être touchée ([EtatChampEspece.dicter]). */
    fun dicter(candidats: List<String>) = champ.dicter(candidats)

    /** Ajoute l'espèce, même si le taxon est déjà dans le relevé : sans la confirmation du web
     *  (demande du 21 octobre 2026). */
    private fun ajouter(nom: String, cdNom: Int?) {
        val index = releve.ajouter(taxon, nom, cdNom)
        champ.vider()
        ouvrirFicheApresAjout(index)
    }

    /** Les champs additionnels obligatoires à remplir des espèces : leur ligne les montre en rouge. */
    fun requis(): RequisEspece = releve.requis()

    /** L'espèce [index] est un oiseau dont le serveur montre le comportement : sa période de
     *  nidification décide de l'ouverture d'office du comportement. */
    private fun comportementANidifier(index: Int): Boolean {
        val espece = releve.especes.value.getOrNull(index) ?: return false
        return espece.taxon == Taxon.OISEAU && espece.cdNom != null && config.champFormVisible("behaviour")
    }

    /** L'espèce ajoutée ouvre sa fiche, qui porte tous ses champs, dans les deux modes (mode simplifié
     *  depuis le 14 novembre 2026, mode pro depuis le 16 : « la fiche de l'observation doit s'ouvrir
     *  automatiquement à chaque saisie d'espèce comme pour le mode simplifié »). En mode pro, pour un
     *  oiseau en période de nidification, la section « Avancé » est dépliée, pour que le comportement
     *  (indice de nidification) soit sous les yeux ; la période est lue AVANT d'ouvrir la fiche. */
    private fun ouvrirFicheApresAjout(index: Int) {
        val cdNom = releve.especes.value.getOrNull(index)?.cdNom
        if (simplifie || cdNom == null || !comportementANidifier(index)) {
            _evenements.trySend(EvenementReleve.FicheEspece(index))
            return
        }
        viewModelScope.launch {
            _evenements.send(EvenementReleve.FicheEspece(index, nidification = nidification.estEnPeriode(cdNom, moisCourant())))
        }
    }

    /** Le crayon d'une espèce, ou son nom, ouvre la fiche de l'observation, dans les deux modes. */
    fun ouvrirFiche(index: Int) {
        _evenements.trySend(EvenementReleve.FicheEspece(index))
    }

    /** L'espèce dont la corbeille attend sa confirmation, null sinon. La corbeille touche le crayon,
     *  geste principal de la ligne en saisie simplifiée : un doigt décalé supprimait l'espèce avec ses
     *  détails, son commentaire et ses médias (audit du 14 novembre 2026, M58). */
    var suppressionDemandee by mutableStateOf<EspeceReleve?>(null)
        private set

    /** La corbeille de l'espèce [index] : rien n'est supprimé avant [confirmerSuppression]. */
    fun demanderSuppression(index: Int) {
        suppressionDemandee = releve.especes.value.getOrNull(index)
    }

    fun annulerSuppression() {
        suppressionDemandee = null
    }

    /** « Supprimer » : l'espèce demandée, retrouvée par son identité (la liste a pu bouger). */
    fun confirmerSuppression() {
        val demandee = suppressionDemandee ?: return
        suppressionDemandee = null
        val index = releve.especes.value.indexOfFirst { it.vmId == demandee.vmId }
        if (index >= 0) releve.supprimer(index)
    }

    /** Le dialogue « Détails du relevé », s'il est ouvert. */
    var details by mutableStateOf<EtatDetailsReleve?>(null)
        private set

    /** « Détails » : le dialogue sur les valeurs du relevé. */
    fun ouvrirDetails() {
        details = EtatDetailsReleve(DetailsReleveFormulaire.pourReleve(releve, config, simplifie), services.nomenclatures, viewModelScope, services.taxRef, simplifie) { terme ->
            // Restreinte à `OCCTAX.CD_TYPO_HABITAT`, comme le web.
            services.habitats.rechercher(terme, cdTypo = services.config.occtaxTypologieHabitat.takeIf { it > 0 })
        }
    }

    fun fermerDetails() {
        details?.fermer()
        details = null
    }

    /** « Valider » : le relevé prend les détails, et les enregistre aussitôt. */
    fun validerDetails(resultat: DetailsReleveResult) {
        releve.appliquerDetails(resultat)
        fermerDetails()
        // Un autre jeu de données peut imposer une autre liste de taxons.
        champ.actualiserListe()
    }

    /** ✓ : le relevé est enregistré (sans espèce, comme relevé d'absence), puis retour à l'écran
     *  précédent — la carte multi-taxons y enchaîne sur le positionnement du relevé suivant. */
    fun terminer() {
        // Le jeu de données est obligatoire : sans lui, le relevé ne se termine pas.
        if (releve.idDatasetEffectif() == null) {
            _evenements.trySend(EvenementReleve.Message(DetailsReleveFormulaire.CHOISIR_JDD))
            ouvrirDetails()
            return
        }
        // La date aussi, quand le serveur la veut à choisir.
        if (releve.dateAChoisir()) {
            _evenements.trySend(EvenementReleve.Message(DetailsReleveFormulaire.CHOISIR_DATE_DEBUT))
            ouvrirDetails()
            return
        }
        val message = releve.terminer()
        _evenements.trySend(EvenementReleve.Message(message))
        _evenements.trySend(EvenementReleve.Termine)
    }

    /** Les noms soufflés à la dictée : les espèces proposées ici. */
    fun vocabulaire(): List<String> = champ.vocabulaire()

    override fun onCleared() {
        recherche.annuler()
        details?.fermer()
    }

    internal companion object {
        const val FICHIER_NIDIFICATION = "files/nidification_oiseaux.json"
    }
}
