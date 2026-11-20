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

// Propre au portage : la fiche d'une espèce de la saisie multi-taxons simplifiée (14 novembre 2026), dans
// le cadre des écrans Caractérisation et Dénombrement. Depuis le 16 novembre, tous ses champs sont sur
// la fiche elle-même : nombre min et max, stade de vie, sexe, état biologique, comportement (avec
// « Chant » pour un oiseau), commentaire, « Photo » et « Audio ».

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonContour
import fr.ariegenature.geomys.ui.composants.BoutonPlein
import fr.ariegenature.geomys.ui.composants.BoutonVert
import fr.ariegenature.geomys.ui.composants.ChampLibelle
import fr.ariegenature.geomys.ui.composants.ChampTexteFormulaire
import fr.ariegenature.geomys.ui.composants.DialogueActions
import fr.ariegenature.geomys.ui.composants.DialogueGardeSortie
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.rememberAjoutMedias
import fr.ariegenature.geomys.ui.composants.rememberGardeSortie
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys

/** Le nom de l'écran (demande du 14 novembre 2026 d'en proposer un) : dernier niveau de son bandeau et
 *  libellé du crayon de la liste du relevé. Son titre est [titreFicheObservation] depuis le 20 novembre
 *  2026. */
internal const val TITRE_FICHE_OBSERVATION = "Fiche de l'observation"

/** Le titre d'une fiche de l'observation, dans les deux modes (demande du 20 novembre 2026) :
 *  « Observation : » suivi, sur la même ligne, du nom de l'espèce, son nom français seul, ou son nom
 *  latin si elle n'en a pas ([SaisieReleveViewModel.nomFrancaisOuLatin]). Le nom latin n'est plus
 *  dessous. */
internal fun titreFicheObservation(nomEspece: String): String = "Observation : $nomEspece"

/** Le bouton « Chant » de la fiche d'un oiseau (demande du 15 novembre 2026), et son libellé une fois
 *  le chant choisi. */
internal const val CHANT = "♪ Chant"
internal const val CHANT_COCHE = "✓ Chant"

/** Le lien qui déplie « Nombre max », replié par défaut. */
internal const val DEPLIER_MAX = "＋ Nombre max"

/** Sous « Photo » et « Audio » d'une observation déjà sur le serveur. */
internal const val MEDIAS_FIGES = "Observation déjà envoyée : une photo ou un son ajouté maintenant ne partirait pas."

/** La fiche de l'observation, titrée « Observation : » et [nomEspece] (à défaut, le nom de l'espèce
 *  dans le relevé). Le bouton flottant ✓, en bas au milieu, écrit les champs dans le relevé et y revient ([onRetour]) ; le retour demande avant
 *  d'abandonner une fiche modifiée. */
@Composable
fun FicheEspeceScreen(
    vm: FicheEspeceViewModel,
    typeSaisieLabel: String,
    nomEspece: String?,
    onAccueil: () -> Unit,
    onRetour: () -> Unit,
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
    var mediaASupprimer by remember { mutableStateOf<String?>(null) }
    val medias = rememberAjoutMedias(vm.dossierMedias, onImportes = vm::ajouterMedias, onErreur = { toasts.afficher(it, true) })
    val secondaire = if (ios) CouleursGeoMys.blanc70 else MaterialTheme.colorScheme.onSurfaceVariant
    val garde = rememberGardeSortie(vm::modifie, onRetour)
    EcranFormulaireEspece(
        titre = titreFicheObservation(nomEspece ?: espece.espece),
        sousTitre = null,
        typeSaisieLabel = typeSaisieLabel,
        onAccueil = { garde.demander(onAccueil) },
        onValider = {
            val refus = vm.valider()
            if (refus == null) onRetour() else toasts.afficher(refus, true)
        },
        valideEnBas = true,
        titreBandeau = TITRE_FICHE_OBSERVATION,
    ) {
        // Effectifs : « − » | champ | « ＋ », comme à l'écran Dénombrement.
        if (vm.minVisible || vm.maxVisible) {
            // « Nombre max » replié par défaut : un seul champ « Nombre », sur la moitié de la
            // largeur, et un lien discret pour déplier le max (demande du 18 novembre 2026).
            val replie = vm.maxVisible && !vm.maxDeplie
            Row(
                Modifier.padding(top = if (ios) 0.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (vm.minVisible) Compteur(if (vm.maxDeplie) "Nombre min" else "Nombre", vm.min, vm::saisirMin, vm::ajusterMin, Modifier.weight(1f))
                when {
                    replie -> Box(Modifier.weight(1f).defaultMinSize(minHeight = 40.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            DEPLIER_MAX,
                            fontSize = 13.sp,
                            color = if (ios) CouleursGeoMys.jauneClair else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(role = Role.Button, onClick = vm::deplierMax).padding(vertical = 8.dp),
                        )
                    }
                    vm.maxVisible -> Compteur("Nombre max", vm.max, vm::saisirMax, vm::ajusterMax, Modifier.weight(1f))
                    else -> Spacer(Modifier.weight(1f))
                }
            }
        }
        // Le comportement et « Chant » juste sous les effectifs (demande du 18 novembre 2026).
        vm.champComportement?.let { champ ->
            ChampNomenclatureVue(champ, vm.positions[champ.champ.code] ?: 0, { vm.positions[champ.champ.code] = it }, effacable = false)
            // Oiseau : « Chant » choisit ou retire ce comportement d'un toucher. Ce n'est pas un écran
            // à ouvrir mais une coche : en contour tant que le chant n'est pas choisi, plein et vert
            // une fois choisi (demande du 15 novembre 2026).
            if (vm.chantVisible) {
                val modifier = Modifier.fillMaxWidth().padding(top = if (ios) 0.dp else 8.dp)
                if (vm.chante) BoutonVert(CHANT_COCHE, vm::basculerChant, modifier) else BoutonContour(CHANT, vm::basculerChant, modifier)
            }
        }
        // Sexe et stade de vie, type d'observation et état biologique : deux par ligne, comme sur la
        // fiche du mode pro (demande du 20 novembre 2026). Un champ absent (le sexe d'une plante)
        // décale les suivants ; le dernier d'un nombre impair prend toute la largeur.
        ChampsNomenclatureEnColonnes(vm.champsDetails, { vm.positions[it.champ.code] ?: 0 }) { champ, choix -> vm.positions[champ.champ.code] = choix }
        vm.additionnelsOccurrence?.let { if (it.defsAffiches.isNotEmpty()) ChampsAdditionnelsVue(it) }
        vm.additionnelsDenombrement?.let { if (it.defsAffiches.isNotEmpty()) ChampsAdditionnelsVue(it) }
        if (vm.commentaireVisible) {
            ChampLibelle("Commentaire") {
                ChampTexteFormulaire(
                    vm.commentaire,
                    { vm.commentaire = it },
                    lignesMin = 3,
                    uneLigne = false,
                    clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    indication = "Commentaire",
                )
            }
        }
        if (vm.mediasVisibles) {
            Column(Modifier.padding(top = if (ios) 6.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val figes = vm.mediasFiges(espece)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BoutonPlein("Photo", {
                        // Sans appareil photo (simulateur), la galerie directement.
                        if (medias.appareilPhoto) choixPhoto = true else medias.choisirPhotos()
                    }, Modifier.weight(1f), actif = !figes)
                    BoutonPlein("Audio", medias::choisirSons, Modifier.weight(1f), actif = !figes)
                }
                if (figes) Text(MEDIAS_FIGES, fontSize = 13.sp, color = secondaire, modifier = Modifier.padding(horizontal = 4.dp))
                for (uri in espece.mediaUrisCounting0) LigneMedia(uri) { mediaASupprimer = uri }
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
    DialogueSuppressionMedia(mediaASupprimer, onConfirmer = { mediaASupprimer?.let(vm::retirerMedia) }, onFermer = { mediaASupprimer = null })
    DialogueGardeSortie(garde, "Fiche non enregistrée")
}
