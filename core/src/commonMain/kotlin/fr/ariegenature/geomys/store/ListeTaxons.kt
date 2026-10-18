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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/TaxonsListeFragment.kt) :
// la construction de la liste et sa recherche quittent le fragment ; seul le mode « liste » (depuis
// Paramètres › Détails) est repris, le parcours par groupe taxonomique n'ayant plus d'entrée.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.plateforme.sansDiacritiques
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Les taxons d'une liste, triés par nom français (à défaut scientifique), avec leur clé de
 *  recherche « nom français + nom scientifique + cd_nom » normalisée une seule fois : on ne
 *  renormalise pas des milliers de noms à chaque frappe. */
class ListeTaxons private constructor(
    val titre: String,
    val entrees: List<TaxRefEntry>,
    private val cles: List<String>,
) {

    /** Les entrées dont la clé contient CHAQUE mot de [recherche] : « laiche pend » trouve
     *  « Laîche pendante », « 3764 » trouve le taxon de ce cd_nom. Recherche vide : tout. */
    fun filtrer(recherche: String): List<TaxRefEntry> {
        val mots = normaliser(recherche).trim().split(" ").filter { it.isNotEmpty() }
        if (mots.isEmpty()) return entrees
        return entrees.filterIndexed { i, _ -> mots.all { cles[i].contains(it) } }
    }

    companion object {
        /** Même normalisation que l'écran Android : sans diacritiques, en minuscules. */
        fun normaliser(texte: String): String = sansDiacritiques(texte).lowercase()

        // Dernière liste construite, gardée pour tout le processus : rouvrir la même liste ne
        // refait ni la matérialisation, ni le tri, ni la normalisation des clés.
        @Volatile private var derniere: Pair<Int, ListeTaxons>? = null

        /** Construit la liste [idListe] (titre [nomListe]) depuis [taxRef], hors du fil principal :
         *  sur un gros référentiel, c'était plusieurs secondes de blocage. */
        suspend fun construire(taxRef: TaxRefCache, idListe: Int, nomListe: String): ListeTaxons {
            derniere?.let { (id, liste) -> if (id == idListe) return liste }
            return withContext(Dispatchers.Default) {
                val parCdNom = taxRef.entreesParCdNom()
                val entrees = taxRef.cdNomsDansListe(idListe).mapNotNull { parCdNom[it] }
                    .sortedBy { it.nomFrOriginal ?: it.sciNom }
                val cles = entrees.map { normaliser("${it.nomFrOriginal ?: ""} ${it.sciNom} ${it.cdNom}") }
                ListeTaxons(nomListe, entrees, cles).also { derniere = idListe to it }
            }
        }

        /** Oublie la liste gardée (cache vidé ou rechargé). */
        fun oublier() {
            derniere = null
        }
    }
}
