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
import fr.ariegenature.geomys.occtax.libelleChampEspece
import fr.ariegenature.geomys.store.NidificationOiseaux
import fr.ariegenature.geomys.store.SuggestionTaxon
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.util.moisCourant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Ce que l'écran du relevé doit faire une fois. */
sealed interface EvenementReleve {
    data class Message(val texte: String) : EvenementReleve

    /** Ouvrir la caractérisation de l'espèce [index] ; [comportementSeul] : ouverture automatique
     *  (nidification), réduite au comportement. */
    data class Caracterisation(val index: Int, val comportementSeul: Boolean) : EvenementReleve

    data class Denombrement(val index: Int) : EvenementReleve

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

    /** Libellé du bandeau : celui du flux de saisie (« Saisie multi-taxons »). */
    val typeSaisieLabel: String get() = session.typeSaisieLabel

    private val nidification = NidificationOiseaux(services.taxRef) { Res.readBytes(FICHIER_NIDIFICATION).decodeToString() }

    private val _evenements = Channel<EvenementReleve>(Channel.BUFFERED)
    val evenements = _evenements.receiveAsFlow()

    /** Le champ espèce : groupe, mode, texte, propositions, statut TaxRef. */
    val champ = EtatChampEspece(services, viewModelScope, releve.taxonInitial, ::libelleChampEspece)

    val groupes: List<Taxon>? get() = champ.groupes
    val taxon: Taxon get() = champ.taxon
    val nomScientifique: Boolean get() = champ.nomScientifique
    val texte: String get() = champ.texte
    val suggestions: List<SuggestionTaxon> get() = champ.suggestions
    val recherche get() = champ.recherche
    val libelleChamp: String get() = champ.libelleChamp
    val synchroEnCours: Boolean get() = champ.synchroEnCours

    /** Nom scientifique à montrer sous l'espèce : seulement s'il diffère du nom affiché (un nom
     *  français a été choisi). */
    fun nomScientifiqueSous(espece: EspeceReleve): String? =
        espece.cdNom?.let { champ.entrees[it]?.sciNom }?.takeIf { !it.equals(espece.espece, ignoreCase = true) }

    private var dejaAffiche = false

    /** À chaque affichage de l'écran (onViewCreated) : le relevé est enregistré ; au retour d'un
     *  sous-écran, la palette revient au groupe de la première espèce, comme sous Android. */
    fun afficher() {
        if (dejaAffiche) champ.replacerGroupe(releve.especes.value.firstOrNull()?.taxon ?: Taxon.OISEAU)
        dejaAffiche = true
        releve.synchroniser()
    }

    fun choisirTaxon(nouveau: Taxon) = champ.choisirTaxon(nouveau)

    fun basculerNomScientifique(actif: Boolean) = champ.basculerNomScientifique(actif)

    fun saisir(nouveau: String) = champ.saisir(nouveau)

    /** Une proposition CHOISIE est ajoutée avec son cd_nom, jamais re-résolue depuis son texte : le
     *  cache ne garde qu'un taxon par nom, la re-résolution pouvait enregistrer un autre taxon. */
    fun choisirSuggestion(suggestion: SuggestionTaxon) {
        ajouter(suggestion.nom, suggestion.cdNom)
    }

    /** Fin de la dictée : la première hypothèse dans le champ, puis TOUTES essayées contre TaxRef
     *  (recherche étendue) ; reconnue, l'espèce est ajoutée, sinon le champ dit « Nom invalide ». */
    fun dicter(candidats: List<String>) {
        saisir(candidats.first())
        viewModelScope.launch {
            val (statut, _) = services.taxRefService.rechercherParmiCandidats(candidats, taxon, config, nomScientifique)
            if (statut is TaxRefStatut.Trouve) {
                // Le premier nom français du taxon, sinon son nom scientifique.
                val nomFrancais = statut.nomFrancais?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ajouter(nomFrancais ?: statut.nomScientifique, statut.cdNom)
            } else {
                recherche.rechercher(texte)
            }
        }
    }

    private fun ajouter(nom: String, cdNom: Int?) {
        val index = releve.ajouter(taxon, nom, cdNom)
        champ.vider()
        declencherCaracterisationSiNidification(index)
    }

    /** Oiseaux seulement : une espèce en période de nidification ce mois-ci ouvre d'elle-même sa
     *  caractérisation, réduite au comportement (indice de nidification). */
    private fun declencherCaracterisationSiNidification(index: Int) {
        val espece = releve.especes.value.getOrNull(index) ?: return
        if (espece.taxon != Taxon.OISEAU) return
        val cdNom = espece.cdNom ?: return
        viewModelScope.launch {
            if (nidification.estEnPeriode(cdNom, moisCourant())) {
                _evenements.send(EvenementReleve.Caracterisation(index, comportementSeul = true))
            }
        }
    }

    fun supprimer(index: Int) = releve.supprimer(index)

    fun ouvrirCaracterisation(index: Int) {
        _evenements.trySend(EvenementReleve.Caracterisation(index, comportementSeul = false))
    }

    fun ouvrirDenombrement(index: Int) {
        _evenements.trySend(EvenementReleve.Denombrement(index))
    }

    /** Le dialogue « Détails du relevé », s'il est ouvert. */
    var details by mutableStateOf<EtatDetailsReleve?>(null)
        private set

    /** « Détails » : le dialogue sur les valeurs du relevé. */
    fun ouvrirDetails() {
        details = EtatDetailsReleve(DetailsReleveFormulaire.pourReleve(releve, config), services.nomenclatures, viewModelScope) { terme ->
            services.habitats.rechercher(terme)
        }
    }

    fun fermerDetails() {
        details?.fermer()
        details = null
    }

    /** « Valider » : le relevé prend les détails ; ils sont enregistrés avec la prochaine
     *  modification du relevé, comme sous Android. */
    fun validerDetails(resultat: DetailsReleveResult) {
        releve.appliquerDetails(resultat)
        fermerDetails()
    }

    /** ✓ : le relevé est enregistré (sans espèce, comme relevé d'absence), puis retour à l'écran
     *  précédent — la carte multi-taxons y enchaîne sur le positionnement du relevé suivant. */
    fun terminer() {
        val message = releve.terminer()
        _evenements.trySend(EvenementReleve.Message(message))
        _evenements.trySend(EvenementReleve.Termine)
    }

    /** Les noms soufflés à la dictée d'iOS : les espèces proposées ici. */
    fun vocabulaire(): List<String> = champ.vocabulaire()

    override fun onCleared() {
        recherche.annuler()
        details?.fermer()
    }

    private companion object {
        const val FICHIER_NIDIFICATION = "files/nidification_oiseaux.json"
    }
}
