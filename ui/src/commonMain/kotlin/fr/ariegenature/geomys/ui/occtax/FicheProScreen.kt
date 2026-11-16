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

// Propre au portage : la fiche d'une espèce de la saisie multi-taxons en mode pro (demande du 16 novembre
// 2026 : « fait la même chose pour le mode pro », après la fiche du mode simplifié). Un seul écran porte
// tous les champs des écrans Dénombrement et Caractérisation, qui ne s'ouvrent plus qu'en saisie
// mono-taxon ; les règles restent celles de leurs ViewModels.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.ui.composants.DialogueGardeSortie
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.rememberGardeSortie

/** ✓ de la fiche du mode pro : l'espèce caractérisée et dénombrée, ou le message du premier champ
 *  refusé (la fiche reste). La caractérisation est contrôlée d'abord : le dénombrement efface à sa
 *  validation les fichiers des médias retirés, ce qui ne doit arriver que si tout est accepté. */
internal fun validerFichePro(caracterisation: CaracterisationViewModel, denombrement: DenombrementViewModel): Pair<EspeceReleve?, String?> {
    val (caracterisee, refusCaracterisation) = caracterisation.valider()
    if (caracterisee == null) return null to refusCaracterisation
    val (denombrements, refusDenombrement) = denombrement.valider()
    if (denombrements == null) return null to refusDenombrement
    return caracterisee.avecDenombrements(denombrements) to null
}

/** La fiche de l'observation du mode pro : les dénombrements, puis la caractérisation, sous le nom de
 *  l'espèce. ✓ les contrôle et [onValide] reçoit l'espèce à écrire dans le relevé ; le retour
 *  ([onRetour]) demande avant d'abandonner une fiche modifiée. */
@Composable
fun FicheProScreen(
    denombrement: DenombrementViewModel,
    caracterisation: CaracterisationViewModel,
    typeSaisieLabel: String,
    sousTitre: String,
    onAccueil: () -> Unit,
    onRetour: () -> Unit,
    onValide: (EspeceReleve) -> Unit,
) {
    val ios = estIos
    val toasts = LocalToasts.current
    val etat = rememberMediasDenombrement(denombrement)
    val garde = rememberGardeSortie({ denombrement.modifie() || caracterisation.modifie() }, onRetour)
    EcranFormulaireEspece(
        titre = TITRE_FICHE_OBSERVATION,
        sousTitre = sousTitre,
        typeSaisieLabel = typeSaisieLabel,
        onAccueil = { garde.demander(onAccueil) },
        onValider = {
            val (espece, message) = validerFichePro(caracterisation, denombrement)
            if (espece != null) onValide(espece) else if (message != null) toasts.afficher(message, true)
        },
    ) {
        ContenuDenombrement(denombrement, etat)
        Text(
            "Caractérisation",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (ios) Color.White else Color.Unspecified,
            modifier = Modifier.padding(top = if (ios) 6.dp else 20.dp),
        )
        ContenuCaracterisation(caracterisation)
    }
    DialoguesDenombrement(denombrement, etat)
    DialogueGardeSortie(garde, "Fiche non enregistrée", onAbandon = denombrement::abandonner)
}
