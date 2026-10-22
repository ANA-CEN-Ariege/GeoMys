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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/monitoring/form/FormulaireRenderer.kt,
// creerChampTaxon) : la construction des propositions, faite dans une coroutine du fragment, et les
// textes de la ligne de statut (dont res/values/strings.xml : taxref_pas_de_donnees,
// taxref_chargement_en_cours) deviennent des objets du socle.

package fr.ariegenature.geomys.monitoring.form

import fr.ariegenature.geomys.saisie.filtrerSuggestions
import fr.ariegenature.geomys.store.TaxRefCache

/** Une proposition du champ espèce. [cdNom] est porté quand la liste du protocole est exploitable :
 *  c'est alors CE taxon qui est retenu au choix, et non celui que donnerait le nom (deux taxons
 *  peuvent partager un nom, la résolution du texte ne rendrait que le plus usuel des deux). */
data class PropositionTaxon(val nom: String, val cdNom: Int?, val secondaire: String?) {
    /** Le nom montré en italique sous le nom principal (nom scientifique sous un nom français…),
     *  ou null : absent, vide, ou identique au nom. */
    val secondaireAffiche: String? get() = secondaire?.takeIf { it.isNotEmpty() && it != nom }

    override fun toString(): String = nom
}

/** Les propositions d'un champ TAXON, construites une fois, hors du fil de l'interface : la liste
 *  d'un protocole compte jusqu'à 45 000 taxons. */
class PropositionsTaxon internal constructor(
    val propositions: List<PropositionTaxon>,
    private val normalises: List<Pair<String, PropositionTaxon>>,
    /** Liste taxonomique imposée par le protocole mais ABSENTE du cache : rien n'est proposé, et
     *  aucun nom n'est accepté. */
    val listeVide: Boolean,
    internal val diagnostic: String,
) {
    /** Les propositions pour [requete] : celles qui commencent par elle, puis celles qui la
     *  contiennent, sans tenir compte des accents ni de la casse. */
    fun filtrer(requete: String?): List<PropositionTaxon> = filtrerSuggestions(normalises, propositions, requete)

    companion object {
        /**
         * Les propositions d'un protocole viennent de l'index de RÉSOLUTION de sa liste : proposé ⇔
         * accepté (règle 2026-09-17). Elles portent la graphie d'origine (« Souci »), là où les clés
         * du cache sont normalisées (« souci »), et incluent les noms qu'un autre taxon a captés dans
         * le cache principal. Propositions porteuses de leur cd_nom et de leur nom scientifique ;
         * faute de liste exploitable, les noms seuls.
         *
         * Liste imposée par le protocole mais ABSENTE du cache : on ne propose RIEN et on le dit
         * (décision produit 2026-09-17). Proposer toutes les espèces, comme avant, laissait saisir des
         * taxons hors protocole ; le cas ne devrait pas se produire, le chargement des données —
         * listes comprises — étant exigé avant d'entrer dans les saisies.
         */
        fun construire(taxRef: TaxRefCache, idListe: Int?): PropositionsTaxon {
            // Sans liste, tout le cache, porteur des cd_nom comme une liste : les clés du cache seules
            // (« peziza sublaricina », en minuscules) ne disaient ni le taxon ni sa graphie.
            val typees = if (idListe != null) taxRef.propositionsListe(idListe) else taxRef.propositionsToutes()
            val restreint = when {
                typees.isNotEmpty() -> typees.map { it.nom }
                idListe != null -> taxRef.nomsProposablesListe(idListe)
                else -> taxRef.nomsSuggestion(null)
            }
            val listeVide = idListe != null && restreint.isEmpty()
            val propositions = when {
                listeVide -> emptyList()
                typees.isNotEmpty() -> typees.map { PropositionTaxon(it.nom, it.cdNom, it.secondaire) }
                else -> restreint.map { PropositionTaxon(it, null, null) }
            }
            val diagnostic = when {
                listeVide -> "liste=$idListe ABSENTE du cache — aucune proposition"
                idListe != null -> "liste=$idListe, ${propositions.size} suggestions"
                else -> "liste=null (toutes), ${propositions.size} suggestions"
            }
            return PropositionsTaxon(
                propositions,
                propositions.map { TaxRefCache.normaliser(it.nom) to it },
                listeVide,
                diagnostic,
            )
        }
    }
}

/** La ligne affichée sous un champ espèce : ce que l'application a réellement retenu, ou pourquoi
 *  rien ne l'est. Le formulaire des Suivis n'affichait RIEN du taxon retenu : une résolution erronée
 *  était indétectable (audit 2026-09-17, C1). */
sealed class StatutTaxon {
    abstract val texte: String

    /** Taxon retenu (couleur de succès). */
    data class Retenu(val cdNom: Int, val nomScientifique: String) : StatutTaxon() {
        override val texte: String
            get() = if (nomScientifique.isEmpty()) "✓ cd_nom $cdNom" else "✓ $nomScientifique  •  cd_nom $cdNom"
    }

    /** Liste du protocole absente du cache : rien ne peut être proposé ni résolu (couleur
     *  d'avertissement). Pendant une synchro, le cache est en cours de réécriture : ce n'est pas à
     *  l'utilisateur de recharger (audit 2026-09-18, T2). */
    data class PasDeDonnees(val chargementEnCours: Boolean) : StatutTaxon() {
        override val texte: String
            get() = if (chargementEnCours) TEXTE_CHARGEMENT_EN_COURS else TEXTE_PAS_DE_DONNEES
    }

    /** Refus explicite, à la sortie du champ (couleur d'erreur) : le bouton d'enregistrement est
     *  grisé tant que ce texte est là, l'utilisateur doit pouvoir lire POURQUOI. */
    data class Refus(val listeRestreinte: Boolean) : StatutTaxon() {
        override val texte: String
            get() = if (listeRestreinte) {
                "✗ Espèce non reconnue dans la liste du protocole — choisissez une proposition"
            } else {
                "✗ Espèce non reconnue — choisissez une proposition"
            }
    }

    companion object {
        const val TEXTE_PAS_DE_DONNEES = "Pas de données — rechargez les données"
        const val TEXTE_CHARGEMENT_EN_COURS = "Chargement des données en cours…"
    }
}
