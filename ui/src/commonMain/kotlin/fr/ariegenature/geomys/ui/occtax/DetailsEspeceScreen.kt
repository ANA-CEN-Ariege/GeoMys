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

// Propre au portage : l'écran « Détails » de la saisie multi-taxons simplifiée (14 novembre 2026), dans
// le cadre et avec les champs des écrans Dénombrement et Caractérisation.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.ui.composants.DialogueGardeSortie
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.rememberGardeSortie

/** « Détails » : nombre, stade de vie, sexe, état biologique, et les champs additionnels obligatoires ;
 *  [onValide] reçoit ce qu'il faut écrire dans l'espèce. */
@Composable
fun DetailsEspeceScreen(
    vm: DetailsEspeceViewModel,
    typeSaisieLabel: String,
    onAccueil: () -> Unit,
    onRetour: () -> Unit,
    onValide: ((EspeceReleve) -> EspeceReleve) -> Unit,
) {
    val toasts = LocalToasts.current
    val garde = rememberGardeSortie(vm::modifie, onRetour)
    EcranFormulaireEspece(
        titre = "Détails",
        sousTitre = vm.nomEspece,
        typeSaisieLabel = typeSaisieLabel,
        onAccueil = { garde.demander(onAccueil) },
        onValider = {
            val (transformation, message) = vm.valider()
            if (transformation != null) onValide(transformation) else if (message != null) toasts.afficher(message, true)
        },
    ) {
        if (vm.nombreVisible) {
            Compteur(
                "Nombre", vm.nombre, vm::saisirNombre, vm::ajusterNombre,
                Modifier.fillMaxWidth().padding(top = if (estIos) 0.dp else 8.dp),
            )
        }
        for (champ in vm.champs) {
            ChampNomenclatureVue(champ, vm.positions[champ.champ.code] ?: 0, { vm.positions[champ.champ.code] = it })
        }
        vm.additionnelsOccurrence?.let { if (it.defsAffiches.isNotEmpty()) ChampsAdditionnelsVue(it) }
        vm.additionnelsDenombrement?.let { if (it.defsAffiches.isNotEmpty()) ChampsAdditionnelsVue(it) }
    }
    DialogueGardeSortie(garde, "Détails non enregistrés")
}
