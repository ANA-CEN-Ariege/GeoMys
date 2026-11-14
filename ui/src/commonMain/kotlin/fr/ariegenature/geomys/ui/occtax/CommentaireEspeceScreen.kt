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

// Propre au portage : l'écran « Commentaire » de la saisie multi-taxons simplifiée (demande du
// 14 novembre 2026 : le commentaire de l'observation manquait au mode simplifié). C'est le commentaire de
// l'occurrence, que la caractérisation range sous « Avancé » en mode pro, comme le web.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.ViewModel
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.occtax.SaisieSimplifiee
import fr.ariegenature.geomys.ui.composants.ChampTexteFormulaire
import fr.ariegenature.geomys.ui.composants.DialogueGardeSortie
import fr.ariegenature.geomys.ui.composants.rememberGardeSortie

/** Le commentaire de l'espèce [espece]. */
class CommentaireEspeceViewModel(private val espece: EspeceReleve) : ViewModel() {
    /** Le nom de l'espèce, sous le titre. */
    val nomEspece = espece.espece

    var texte by mutableStateOf(espece.notes)

    /** L'écran a changé depuis son ouverture. */
    fun modifie(): Boolean = texte != espece.notes

    /** ✓ : ce qu'il faut écrire dans l'espèce, telle qu'elle est au moment du ✓ (audit du 14 novembre
     *  2026, M50). */
    fun valider(): (EspeceReleve) -> EspeceReleve {
        val saisi = texte
        return { courante -> SaisieSimplifiee.appliquerCommentaire(courante, saisi) }
    }
}

/** « Commentaire » : un champ sur plusieurs lignes ; [onValide] reçoit ce qu'il faut écrire dans l'espèce. */
@Composable
fun CommentaireEspeceScreen(
    vm: CommentaireEspeceViewModel,
    typeSaisieLabel: String,
    onAccueil: () -> Unit,
    onRetour: () -> Unit,
    onValide: ((EspeceReleve) -> EspeceReleve) -> Unit,
) {
    val garde = rememberGardeSortie(vm::modifie, onRetour)
    EcranFormulaireEspece(
        titre = "Commentaire",
        sousTitre = vm.nomEspece,
        typeSaisieLabel = typeSaisieLabel,
        onAccueil = { garde.demander(onAccueil) },
        onValider = { onValide(vm.valider()) },
    ) {
        ChampTexteFormulaire(
            vm.texte,
            { vm.texte = it },
            lignesMin = 4,
            uneLigne = false,
            clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            indication = "Commentaire",
        )
    }
    DialogueGardeSortie(garde, "Commentaire non enregistré")
}
