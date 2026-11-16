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
import fr.ariegenature.geomys.network.TaxRefService
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.occtax.groupeDeDepart
import fr.ariegenature.geomys.occtax.groupesNonVides
import fr.ariegenature.geomys.saisie.RechercheTaxRef
import fr.ariegenature.geomys.saisie.filtrerSuggestions
import fr.ariegenature.geomys.saisie.nomsModeleDictee
import fr.ariegenature.geomys.saisie.vocabulaireDictee
import fr.ariegenature.geomys.store.SuggestionTaxon
import fr.ariegenature.geomys.store.TaxRefEntry
import fr.ariegenature.geomys.store.champFormVisible
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Les propositions d'un périmètre (groupe × mode × liste) et leurs clés normalisées. */
private class Propositions(val liste: List<SuggestionTaxon>, val normalisees: List<Pair<String, SuggestionTaxon>>)

/** Les espèces qui ressemblent à ce qu'une dictée non reconnue a [entendu]. [enCours] : la phrase
 *  n'est pas finie, ces espèces ne servent que si le texte entendu ne commence aucun nom proposé. */
private class Vocaux(val entendu: String, val liste: List<SuggestionTaxon>, val enCours: Boolean = false)

/** Le premier nom français d'un taxon reconnu (le cache en garde parfois plusieurs, séparés par des
 *  virgules), null s'il n'en a pas. */
internal fun premierNomFrancais(statut: TaxRefStatut.Trouve): String? =
    statut.nomFrancais?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

/** Le champ espèce d'une saisie : palette des groupes chargés, mode « nom scientifique » (mémorisé),
 *  texte tapé, propositions filtrées à chaque frappe hors du fil de l'interface ([calcul]), ligne
 *  de statut TaxRef. [libelle] : l'invite du champ selon le groupe et le mode. Les espèces sont
 *  celles de la liste du relevé, comme sur le web : celle de son jeu de données ([idDataset]), sinon
 *  celle de la configuration d'OccTax, sinon toutes. */
class EtatChampEspece(
    private val services: ServicesGeoNature,
    private val portee: CoroutineScope,
    taxonInitial: Taxon,
    private val libelle: (Taxon, Boolean) -> String,
    private val calcul: CoroutineDispatcher = Dispatchers.Default,
    private val idDataset: () -> Int? = { null },
) {
    private val config = services.config
    private val preferences = services.preferences

    /** La liste de taxons du relevé, relue au départ et à chaque changement de jeu de données
     *  ([actualiserListe]). */
    private var idListe: Int? = config.listeTaxonsOcctax(idDataset())

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

    /** Tous les noms proposés ici, pour le modèle de langue de la dictée d'iOS ; vide pendant leur
     *  calcul, ou s'ils sont trop nombreux. */
    var nomsDictee by mutableStateOf<List<String>>(emptyList())
        private set

    /** Les propositions qui correspondent au texte, celles qui commencent par lui d'abord. */
    private var filtrees by mutableStateOf<List<SuggestionTaxon>>(emptyList())

    /** Après une dictée que la résolution n'a pas reconnue, les espèces qui lui ressemblent
     *  (GeoMys-IOS, LigneEspeceView) : ce qui a été entendu n'est pas un nom de taxon, le filtrer ne
     *  proposerait rien. Une frappe, une autre dictée, un choix ou un autre périmètre les effacent. */
    private var vocaux by mutableStateOf<Vocaux?>(null)

    /** La liste déroulante : les espèces proches de la dictée, sinon les propositions filtrées. */
    val suggestions: List<SuggestionTaxon> get() = vocaux?.takeUnless { it.cedeAuFiltre() }?.liste ?: filtrees

    /** Ce que la dictée a entendu, tant que la liste montre les espèces qui lui ressemblent ; la
     *  ligne sous le champ le dit à la place du statut. */
    val entendu: String? get() = vocaux?.takeUnless { it.cedeAuFiltre() }?.entendu

    /** Pendant la dictée, ce qui est entendu et qui commence des noms proposés (« Serin ») montre ces
     *  noms, comme une frappe ; les espèces proches par le son ne viennent que s'il n'y en a pas. */
    private fun Vocaux.cedeAuFiltre(): Boolean = enCours && filtrees.isNotEmpty()

    /** La recherche des espèces proches de la phrase en cours de dictée : la dernière seule compte. */
    private var prochesEnCours: Job? = null

    /** Texte posé par un choix dans la liste : pas de propositions tant qu'il reste tel quel (le
     *  choix referme la liste, comme `performCompletion` d'AutoCompleteTextView). */
    private var texteChoisi by mutableStateOf<String?>(null)

    val recherche = RechercheTaxRef(portee) { nom -> services.taxRefService.rechercher(nom, taxon, idListe, nomScientifique) }

    /** Le jeu de données du relevé a pu changer (« Détails ») : sa liste de taxons fait les
     *  propositions, et le texte tapé est re-résolu. */
    fun actualiserListe() {
        val nouvelle = config.listeTaxonsOcctax(idDataset())
        if (nouvelle == idListe) return
        idListe = nouvelle
        portee.launch {
            val visibles = withContext(calcul) { services.taxRef.groupesNonVides(nouvelle) }
            groupes = visibles
            taxon = groupeDeDepart(taxon, visibles)
            rafraichirPropositions()
            recherche.relancer(texte)
        }
    }

    /** La liste de taxons du relevé, pour la dictée. */
    val listeTaxons: Int? get() = idListe

    val libelleChamp: String get() = libelle(taxon, nomScientifique)

    /** Les entrées du cache par cd_nom (nom scientifique d'un cd_nom). */
    var entrees by mutableStateOf<Map<Int, TaxRefEntry>>(emptyMap())
        private set

    /** Le `nom_valide` TAXREF d'un taxon, que le web montre sous le taxon choisi (« TAXREF - Nom
     *  valide ») quand `form_fields.display_nom_valide` le demande ; null : il n'est pas montré. */
    val nomValide: ((Int) -> String?)? =
        if (champFormVisible(config.formFieldsJson, "display_nom_valide")) services.taxRef::nomValideParCdNom else null

    /** Données de saisie en cours de rechargement : la ligne « pas de données » le dit. */
    val synchroEnCours: Boolean get() = services.synchro.actif

    init {
        portee.launch {
            entrees = withContext(calcul) { services.taxRef.entreesParCdNom() }
        }
        val liste = idListe
        portee.launch {
            val visibles = withContext(calcul) { services.taxRef.groupesNonVides(liste) }
            groupes = visibles
            taxon = groupeDeDepart(taxon, visibles)
            rafraichirPropositions()
        }
        // Filtre des propositions à chaque frappe, hors du fil de l'interface (15 000 à 50 000 noms).
        portee.launch {
            snapshotFlow { Triple(texte, propositions, texteChoisi) }.collectLatest { (requete, p, choisi) ->
                filtrees = if (p == null || requete.isEmpty() || requete == choisi) emptyList()
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

    /** Le texte survit aussi à la bascule ; un nom français affichera « Nom invalide » tant qu'il
     *  n'est pas remplacé. */
    fun basculerNomScientifique(actif: Boolean) {
        nomScientifique = actif
        preferences.memoiserNomSci(actif)
        rafraichirPropositions()
        recherche.relancer(texte)
    }

    fun saisir(nouveau: String) {
        poserTexte(nouveau)
        recherche.rechercher(nouveau)
    }

    private fun poserTexte(nouveau: String) {
        if (nouveau != texteChoisi) texteChoisi = null
        if (nouveau != vocaux?.entendu) vocaux = null
        texte = nouveau
    }

    /** Une proposition choisie : son nom dans le champ, la liste refermée. Le statut est posé par
     *  l'appelant ([RechercheTaxRef.poser]). */
    fun poserChoix(nom: String) {
        vocaux = null
        texteChoisi = nom
        texte = nom
    }

    /** Le champ vidé, sans statut. */
    fun vider() {
        vocaux = null
        texteChoisi = null
        texte = ""
        recherche.reset()
    }

    /**
     * Fin de la dictée : la première hypothèse dans le champ, puis toutes essayées dans le périmètre
     * (recherche étendue et sons, [TaxRefService.rechercherParmiCandidats]). L'espèce reconnue n'est
     * JAMAIS choisie d'office, même seule (demande du 15 novembre 2026 : « ne pas sélectionner l'espèce
     * même s'il n'y en a qu'une, proposer seulement ») : elle vient en tête de la liste déroulante,
     * devant les espèces du périmètre qui ressemblent à ce qui a été entendu. C'est la toucher qui la
     * choisit.
     */
    fun dicter(hypotheses: List<String>) {
        val entendu = hypotheses.firstOrNull() ?: return
        prochesEnCours?.cancel()
        // Le texte entendu dans le champ, sans la recherche de la frappe : un nom dit exactement
        // serait tenu pour choisi.
        poserTexte(entendu)
        recherche.reset()
        val perimetre = Triple(taxon, nomScientifique, idListe)
        portee.launch {
            val (statut, _) = services.taxRefService.rechercherParmiCandidats(hypotheses, perimetre.first, perimetre.third, perimetre.second)
            val proches = withContext(calcul) {
                services.taxRefLocal.candidatsVocaux(hypotheses, perimetre.first, perimetre.second, perimetre.third)
            }
            // Le texte ou le périmètre ont changé entre-temps : ces espèces ne répondent plus à rien.
            if (texte != entendu || perimetre != Triple(taxon, nomScientifique, idListe)) return@launch
            val reconnue = (statut as? TaxRefStatut.Trouve)?.let { trouve ->
                // Telle que la liste la propose ici (nom français ou scientifique selon le mode).
                propositions?.liste?.firstOrNull { it.cdNom == trouve.cdNom }
                    ?: SuggestionTaxon((if (nomScientifique) null else premierNomFrancais(trouve)) ?: trouve.nomScientifique, trouve.cdNom)
            }
            val liste = listOfNotNull(reconnue) + proches.filter { it.cdNom != reconnue?.cdNom }
            // Rien de reconnu : le statut du texte entendu (« Nom invalide »), que la ligne « Entendu »
            // remplace s'il y a des espèces proches.
            if (reconnue == null) recherche.rechercher(entendu)
            if (liste.isNotEmpty()) vocaux = Vocaux(entendu, liste)
        }
    }

    /**
     * La phrase est en cours de dictée (demande du 15 novembre 2026 : « sera signé » restait seul dans
     * le champ jusqu'à la fin de la phrase) : le texte entendu dans le champ, comme une frappe, et les
     * espèces qui lui ressemblent proposées aussitôt, sans attendre le silence. Rien n'est validé, ici
     * comme à la fin de la phrase ([dicter]) : une espèce n'est choisie que touchée dans la liste.
     */
    fun dicterPartiel(entendu: String) {
        // Pas la recherche de la frappe : un nom dit exactement serait tenu pour choisi.
        poserTexte(entendu)
        recherche.reset()
        prochesEnCours?.cancel()
        if (entendu.isBlank()) return
        val perimetre = Triple(taxon, nomScientifique, idListe)
        prochesEnCours = portee.launch {
            val proches = withContext(calcul) {
                services.taxRefLocal.candidatsVocaux(listOf(entendu), perimetre.first, perimetre.second, perimetre.third)
            }
            if (texte != entendu || perimetre != Triple(taxon, nomScientifique, idListe)) return@launch
            if (proches.isNotEmpty()) vocaux = Vocaux(entendu, proches, enCours = true)
        }
    }

    /** Les noms soufflés à la dictée : les espèces proposées ici. */
    fun vocabulaire(): List<String> = propositions?.liste?.let(::vocabulaireDictee).orEmpty()

    private fun rafraichirPropositions() {
        propositions = null
        nomsDictee = emptyList()
        vocaux = null
        val perimetre = Triple(taxon, nomScientifique, idListe)
        portee.launch {
            val (calculees, listeAbsente, noms) = withContext(calcul) {
                val (liste, normalisees) = services.taxRefLocal.getPropositionsNormalisees(perimetre.first, perimetre.second, perimetre.third)
                // Liste de taxons configurée mais absente du cache : rien n'est proposé et rien ne
                // sera accepté — on le dit sous le champ.
                Triple(
                    Propositions(liste, normalisees),
                    perimetre.third != null && services.taxRef.listeAbsenteDuCache(perimetre.third!!),
                    nomsModeleDictee(liste),
                )
            }
            // Un autre périmètre a été choisi entre-temps : ces propositions ne valent plus.
            if (perimetre != Triple(taxon, nomScientifique, idListe)) return@launch
            recherche.signalerPasDeDonnees(listeAbsente)
            propositions = calculees
            nomsDictee = noms
        }
    }
}
