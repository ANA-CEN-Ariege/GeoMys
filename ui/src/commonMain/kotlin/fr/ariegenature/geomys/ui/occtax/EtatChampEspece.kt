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

// Porté de GeoMys-Android @ d72b8da : le groupe, le mode « nom scientifique », les propositions et la
// recherche TaxRef du champ espèce, que SaisieObservationFragment et SaisieRapideFragment dupliquaient
// (setupTaxonSelector, setupAutocomplete, refreshAutocompleteAdapter, updateEspeceHint). Chaque saisie
// garde ce qu'elle fait d'une espèce choisie.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.occtax.groupeDeDepart
import fr.ariegenature.geomys.occtax.groupesNonVides
import fr.ariegenature.geomys.saisie.RechercheTaxRef
import fr.ariegenature.geomys.saisie.filtrerSuggestions
import fr.ariegenature.geomys.saisie.vocabulaireDictee
import fr.ariegenature.geomys.store.SuggestionTaxon
import fr.ariegenature.geomys.store.TaxRefEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Les propositions d'un périmètre (groupe × mode × liste) et leurs clés normalisées. */
private class Propositions(val liste: List<SuggestionTaxon>, val normalisees: List<Pair<String, SuggestionTaxon>>)

/** Le champ espèce d'une saisie : palette des groupes chargés, mode « nom scientifique » (mémorisé),
 *  texte tapé, propositions filtrées à chaque frappe hors du fil de l'interface ([calcul]), ligne
 *  de statut TaxRef. [libelle] : l'invite du champ selon le groupe et le mode. */
class EtatChampEspece(
    private val services: ServicesGeoNature,
    private val portee: CoroutineScope,
    taxonInitial: Taxon,
    private val libelle: (Taxon, Boolean) -> String,
    private val calcul: CoroutineDispatcher = Dispatchers.Default,
) {
    private val config = services.config
    private val preferences = services.preferences
    private val idListe: Int? get() = config.taxaListeId.trim().toIntOrNull()

    /** Les groupes de la palette (ceux qui ont des taxons chargés), null pendant leur calcul. */
    var groupes by mutableStateOf<List<Taxon>?>(null)
        private set
    var taxon by mutableStateOf(taxonInitial)
        private set
    var nomScientifique by mutableStateOf(preferences.rechercheNomSci())
        private set
    var texte by mutableStateOf("")
        private set

    /** Propositions du périmètre courant, null pendant leur calcul : la liste déroulante est vide
     *  (les propositions de l'ancien périmètre ne répondent plus à la frappe). */
    private var propositions by mutableStateOf<Propositions?>(null)

    /** Les propositions qui correspondent au texte, celles qui commencent par lui d'abord. */
    var suggestions by mutableStateOf<List<SuggestionTaxon>>(emptyList())
        private set

    /** Texte posé par un choix dans la liste : pas de propositions tant qu'il reste tel quel (le
     *  choix referme la liste, comme `performCompletion` d'AutoCompleteTextView). */
    private var texteChoisi by mutableStateOf<String?>(null)

    val recherche = RechercheTaxRef(portee) { nom -> services.taxRefService.rechercher(nom, taxon, config, nomScientifique) }

    val libelleChamp: String get() = libelle(taxon, nomScientifique)

    /** Les entrées du cache par cd_nom (nom scientifique d'un cd_nom). */
    var entrees by mutableStateOf<Map<Int, TaxRefEntry>>(emptyMap())
        private set

    /** Données de saisie en cours de rechargement : la ligne « pas de données » le dit. */
    val synchroEnCours: Boolean get() = services.synchro.actif

    init {
        portee.launch {
            entrees = withContext(calcul) { services.taxRef.entreesParCdNom() }
        }
        portee.launch {
            val visibles = withContext(calcul) { services.taxRef.groupesNonVides(idListe) }
            groupes = visibles
            taxon = groupeDeDepart(taxon, visibles)
            rafraichirPropositions()
        }
        // Filtre des propositions à chaque frappe, hors du fil de l'interface (15 000 à 50 000 noms).
        portee.launch {
            snapshotFlow { Triple(texte, propositions, texteChoisi) }.collectLatest { (requete, p, choisi) ->
                suggestions = if (p == null || requete.isEmpty() || requete == choisi) emptyList()
                else withContext(calcul) { filtrerSuggestions(p.normalisees, p.liste, requete) }
            }
        }
    }

    /** Change de groupe ; [nouveau] est mémorisé pour la prochaine saisie (mono ou multi-taxons).
     *  Le texte tapé est CONSERVÉ et re-résolu dans le nouveau groupe : on tape souvent le nom
     *  avant de s'apercevoir qu'on est dans le mauvais groupe. */
    fun choisirTaxon(nouveau: Taxon) {
        if (nouveau == taxon) return
        taxon = nouveau
        preferences.memoiserTaxon(nouveau)
        rafraichirPropositions()
        recherche.relancer(texte)
    }

    /** Ramène la palette sur [voulu] (ou le premier groupe chargé), sans le mémoriser. */
    fun replacerGroupe(voulu: Taxon) {
        val depart = groupes?.let { groupeDeDepart(voulu, it) } ?: voulu
        if (depart == taxon) return
        taxon = depart
        rafraichirPropositions()
        recherche.relancer(texte)
    }

    /** Le texte survit aussi à la bascule ; un nom français affichera « Nom invalide » tant qu'il
     *  n'est pas remplacé. */
    fun basculerNomScientifique(actif: Boolean) {
        nomScientifique = actif
        preferences.memoiserNomSci(actif)
        rafraichirPropositions()
        recherche.relancer(texte)
    }

    fun saisir(nouveau: String) {
        if (nouveau != texteChoisi) texteChoisi = null
        texte = nouveau
        recherche.rechercher(nouveau)
    }

    /** Une proposition choisie : son nom dans le champ, la liste refermée. Le statut est posé par
     *  l'appelant ([RechercheTaxRef.poser]). */
    fun poserChoix(nom: String) {
        texteChoisi = nom
        texte = nom
    }

    /** Le champ vidé, sans statut. */
    fun vider() {
        texteChoisi = null
        texte = ""
        recherche.reset()
    }

    /** Les noms soufflés à la dictée d'iOS : les espèces proposées ici. */
    fun vocabulaire(): List<String> = propositions?.liste?.let(::vocabulaireDictee).orEmpty()

    private fun rafraichirPropositions() {
        propositions = null
        val perimetre = Triple(taxon, nomScientifique, idListe)
        portee.launch {
            val (calculees, listeAbsente) = withContext(calcul) {
                val (liste, normalisees) = services.taxRefLocal.getPropositionsNormalisees(perimetre.first, perimetre.second, perimetre.third)
                // Liste de taxons configurée mais absente du cache : rien n'est proposé et rien ne
                // sera accepté — on le dit sous le champ.
                Propositions(liste, normalisees) to (perimetre.third != null && services.taxRef.listeAbsenteDuCache(perimetre.third!!))
            }
            // Un autre périmètre a été choisi entre-temps : ces propositions ne valent plus.
            if (perimetre != Triple(taxon, nomScientifique, idListe)) return@launch
            recherche.signalerPasDeDonnees(listeAbsente)
            propositions = calculees
        }
    }
}
