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

// Propre au portage : l'écran d'une espèce de la saisie multi-taxons simplifiée (14 novembre 2026), dans
// le cadre des écrans Caractérisation et Dénombrement : « Détails », « Comportement », « Commentaire »,
// « Photo » et « Audio », chacun avec ce qu'il porte déjà.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonPlein
import fr.ariegenature.geomys.ui.composants.DialogueActions
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.rememberAjoutMedias
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** Le nom de l'écran (demande du 14 novembre 2026 d'en proposer un), titre et libellé du crayon de la
 *  liste du relevé. */
internal const val TITRE_FICHE_OBSERVATION = "Fiche de l'observation"

/** La fiche de l'observation : le nom de l'espèce sous le titre, et [sousTitre], son nom scientifique,
 *  s'il diffère ; [onVolet] ouvre « Détails », « Comportement » ou « Commentaire » ; ✓ et le retour ([onRetour]) reviennent au relevé, tout étant déjà enregistré. */
@Composable
fun FicheEspeceScreen(
    vm: FicheEspeceViewModel,
    typeSaisieLabel: String,
    sousTitre: String?,
    onAccueil: () -> Unit,
    onRetour: () -> Unit,
    onVolet: (VoletEspece) -> Unit,
) {
    val ios = estIos
    val toasts = LocalToasts.current
    val especes by vm.releve.especes.collectAsState()
    val espece = vm.espece(especes)
    if (espece == null) {
        LaunchedEffect(Unit) { onRetour() }
        return
    }
    var choixPhoto by remember { mutableStateOf(false) }
    val medias = rememberAjoutMedias(vm.dossierMedias, onImportes = vm::ajouterMedias, onErreur = { toasts.afficher(it, true) })
    val secondaire = if (ios) CouleursGeoMys.blanc70 else MaterialTheme.colorScheme.onSurfaceVariant
    EcranFormulaireEspece(
        titre = TITRE_FICHE_OBSERVATION,
        sousTitre = espece.espece + (sousTitre?.let { " ($it)" } ?: ""),
        typeSaisieLabel = typeSaisieLabel,
        onAccueil = onAccueil,
        onValider = onRetour,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (vm.detailsVisible) {
                Volet("Détails", vm.resumeDetails(espece), secondaire) { onVolet(VoletEspece.DETAILS) }
                if (vm.detailsIncomplets(espece)) {
                    Text(
                        "Des champs obligatoires sont à remplir",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (ios) CouleursGeoMys.rougePastille else CouleursGeoMys.holoRougeFonce,
                    )
                }
            }
            if (vm.comportementVisible) {
                Volet("Comportement", vm.resumeComportement(espece), secondaire) { onVolet(VoletEspece.COMPORTEMENT) }
            }
            if (vm.commentaireVisible) {
                Volet("Commentaire", vm.resumeCommentaire(espece), secondaire) { onVolet(VoletEspece.COMMENTAIRE) }
            }
            if (vm.mediasVisibles) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BoutonPlein("Photo", {
                            // Sans appareil photo (simulateur), la galerie directement.
                            if (medias.appareilPhoto) choixPhoto = true else medias.choisirPhotos()
                        }, Modifier.weight(1f))
                        BoutonPlein("Audio", medias::choisirSons, Modifier.weight(1f))
                    }
                    for (uri in espece.mediaUrisCounting0) LigneMedia(uri) { vm.retirerMedia(uri) }
                }
            }
        }
    }
    DialogueActions(
        visible = choixPhoto,
        titre = "Ajouter une photo",
        actions = listOfNotNull(
            BoutonAlerte("Prendre une photo") { medias.prendrePhoto() },
            BoutonAlerte("Choisir dans la galerie") { medias.choisirPhotos() },
            if (ios) BoutonAlerte("Annuler", annulation = true) else null,
        ),
        onFermer = { choixPhoto = false },
    )
}

/** Un bouton de l'écran de l'espèce, et dessous ce qu'il porte déjà. */
@Composable
private fun Volet(libelle: String, resume: String, couleur: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoutonPlein(libelle, onClick, Modifier.fillMaxWidth())
        if (resume.isNotEmpty()) {
            Text(resume, fontSize = 13.sp, color = couleur, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}
