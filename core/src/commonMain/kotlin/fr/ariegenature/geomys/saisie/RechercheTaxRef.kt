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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/saisie/TaxRefLookup.kt) : la
// recherche différée du nom tapé et la ligne de statut qu'elle tient à jour (la ProgressBar et le
// TextView y deviennent un état).

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.network.TaxRefStatut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** La ligne sous le champ espèce : la roue pendant une recherche ([roue]), puis le [statut] du nom
 *  ([texteVisible] : la ligne de texte est affichée). */
data class LigneStatutTaxRef(
    val statut: TaxRefStatut? = null,
    val roue: Boolean = false,
    val texteVisible: Boolean = false,
)

/** Recherche TaxRef différée (500 ms) du nom saisi, qui tient à jour [ligne]. [rechercher] résout
 *  un nom dans le périmètre COURANT (groupe, mode d'affichage, liste) : il peut changer entre deux
 *  frappes. */
class RechercheTaxRef(
    private val portee: CoroutineScope,
    private val resoudre: suspend (String) -> TaxRefStatut,
) {
    private val _ligne = MutableStateFlow(LigneStatutTaxRef())
    val ligne: StateFlow<LigneStatutTaxRef> = _ligne.asStateFlow()

    val statut: TaxRefStatut? get() = _ligne.value.statut

    private var job: Job? = null

    /** Texte pour lequel le taxon a été CHOISI dans la liste de propositions : tant qu'il est
     *  affiché tel quel, aucune recherche ne vient le réécrire (cf. [poser]). */
    private var nomFige: String? = null

    /** Aucune proposition disponible (liste de taxons absente du cache) : le message reste
     *  affiché, la frappe et les remises à zéro ne l'effacent pas. */
    private var pasDeDonnees = false

    /** Signale l'absence de données de saisie (ou la lève avec [actif] à false). */
    fun signalerPasDeDonnees(actif: Boolean) {
        if (pasDeDonnees == actif) return
        pasDeDonnees = actif
        job?.cancel()
        afficher(if (actif) TaxRefStatut.PasDeDonnees else null)
    }

    /** Fixe le taxon CHOISI dans la liste de propositions, sans le re-chercher (audit 2026-09-17,
     *  C2) : la suggestion connaît son `cd_nom`, le retrouver à partir de son seul texte pouvait
     *  désigner un autre taxon du même groupe et de la même liste portant ce nom. La recherche
     *  déclenchée par la frappe est annulée, et ignorée tant que le texte ne change pas. */
    fun poser(trouve: TaxRefStatut.Trouve, pourTexte: String) {
        job?.cancel()
        nomFige = pourTexte
        afficher(trouve)
    }

    /** Relance la résolution du texte affiché dans un périmètre qui vient de CHANGER (groupe ou
     *  mode). Le statut courant est invalidé IMMÉDIATEMENT : il désigne un taxon de l'ancien
     *  périmètre. Un choix figé dans la liste ([poser]) est levé pour la même raison. */
    fun relancer(nom: String) {
        nomFige = null
        afficher(null)
        rechercher(nom)
    }

    /** Annule la recherche en cours et en lance une nouvelle après 500 ms si [nom] fait au moins
     *  2 caractères ; sinon réinitialise. */
    fun rechercher(nom: String) {
        if (pasDeDonnees) {
            afficher(_ligne.value.statut)
            return
        }
        // Texte inchangé depuis un choix dans la liste : le taxon est déjà connu.
        if (nom == nomFige && _ligne.value.statut is TaxRefStatut.Trouve) {
            afficher(_ligne.value.statut)
            return
        }
        nomFige = null
        job?.cancel()
        if (nom.length < 2) {
            afficher(null)
            return
        }
        job = portee.launch {
            delay(500)
            if (!isActive) return@launch
            // La roue remplace la ligne de texte pendant la recherche.
            _ligne.update { it.copy(roue = true, texteVisible = false) }
            val s = resoudre(nom)
            _ligne.update { it.copy(statut = s, roue = false) }
            afficher(s)
        }
    }

    fun reset() {
        job?.cancel()
        nomFige = null
        afficher(if (pasDeDonnees) _ligne.value.statut else null)
    }

    fun annuler() {
        job?.cancel()
    }

    /** updateUI d'Android : un statut montre sa ligne (la roue reste comme elle est) ; aucun
     *  statut masque la ligne et la roue. */
    private fun afficher(statut: TaxRefStatut?) {
        _ligne.update {
            if (statut == null) LigneStatutTaxRef() else it.copy(statut = statut, texteVisible = true)
        }
    }
}
