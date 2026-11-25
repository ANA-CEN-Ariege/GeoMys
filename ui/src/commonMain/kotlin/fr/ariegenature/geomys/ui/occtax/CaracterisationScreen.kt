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

// La caractérisation d'une occurrence : fragment_caracterisation.xml et CaracterisationFragment de
// GeoMys-Android @ cdd5196 sous Android, CaracterisationView de GeoMys-IOS sous iOS.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.ui.composants.ChampLibelle
import fr.ariegenature.geomys.ui.composants.ChampTexteFormulaire
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.estIos

/** android.R.color.holo_blue_dark : le bouton « Avancé ». */
private val BLEU_LIEN = Color(0xFF0099CC)

/** colorPrimaryVariant de GeoMys-IOS : le bouton « Avancé ». */
private val BLEU_VARIANTE = Color(0xFF1565C0)

/** Le bouton de la section avancée du web (`Advanced` du fr.json du cœur). */
/** Le titre de la section avancée (« Avancé » sur le web et jusqu'au 19 novembre 2026). */
private const val LIBELLE_AVANCE = "Plus de détails"

/** Les champs de la caractérisation, dans la fiche de l'observation du mode pro, d'un relevé ou d'une
 *  série mono-taxon. Ils ont eu leur propre écran, Caractérisation, que la saisie mono-taxon a ouvert
 *  jusqu'au 24 novembre 2026. */
@Composable
internal fun ContenuCaracterisation(vm: CaracterisationViewModel) {
    EnTeteCaracterisation(vm)
    SuiteCaracterisation(vm)
}

/** Le haut de la caractérisation, sur deux colonnes (demandes du 19 novembre 2026) : le comportement
 *  et le statut biologique, sortis de la section « Avancé », puis la technique d'observation et l'état
 *  biologique. La fiche de l'observation du mode pro le pose avant les dénombrements. */
@Composable
internal fun EnTeteCaracterisation(vm: CaracterisationViewModel) {
    ChampsNomenclatureEnColonnes(vm.enTete, { vm.positions[it.champ.code] ?: 0 }) { champ, choix -> vm.positions[champ.champ.code] = choix }
    ChampsNomenclatureEnColonnes(vm.basiques, { vm.positions[it.champ.code] ?: 0 }) { champ, choix -> vm.positions[champ.champ.code] = choix }
}

/** Le reste de la caractérisation : la section « Avancé » et les champs additionnels. */
@Composable
internal fun SuiteCaracterisation(vm: CaracterisationViewModel) {
    val ios = estIos
    // Déplier « Avancé » amène son titre en haut de l'écran (demande du 19 novembre 2026) : on demande
    // à voir, depuis le titre, une zone haute comme la fenêtre ; le défilement l'aligne donc sur son
    // haut. Deux images d'attente : les champs dépliés doivent être mesurés avant de défiler.
    val versLeHaut = remember { BringIntoViewRequester() }
    val hauteurFenetre = LocalWindowInfo.current.containerSize.height.toFloat()
    val marge = with(LocalDensity.current) { 8.dp.toPx() }
    var depliages by remember { mutableStateOf(0) }
    LaunchedEffect(depliages) {
        if (depliages == 0) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        versLeHaut.bringIntoView(Rect(0f, -marge, 1f, hauteurFenetre))
    }
    val basculer = {
        vm.avanceOuvert = !vm.avanceOuvert
        if (vm.avanceOuvert) depliages++
    }
    if (vm.aDesChampsAvances) {
        // UN seul repli pour toute la section avancée, comme la gn-advanced-section du web : le
        // même bouton « Avancé » à roue dentée l'ouvre et la ferme.
        if (ios) {
            Row(
                Modifier.bringIntoViewRequester(versLeHaut).clickable(onClick = basculer),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SymboleSf("gearshape.fill", 14f, listOf(BLEU_VARIANTE))
                Text(LIBELLE_AVANCE, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = BLEU_VARIANTE)
            }
        } else {
            Text(
                "⚙ $LIBELLE_AVANCE",
                fontSize = 14.sp,
                color = BLEU_LIEN,
                modifier = Modifier.padding(top = 12.dp).bringIntoViewRequester(versLeHaut).clickable(onClick = basculer),
            )
        }
        if (vm.avanceOuvert) {
            vm.determinateur?.let { valeur ->
                ChampLibelle("Déterminateur") {
                    ChampTexteFormulaire(valeur, { vm.determinateur = it }, clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                }
            }
            for (champ in vm.avances) {
                ChampNomenclatureVue(champ, vm.positions[champ.champ.code] ?: 0, { vm.positions[champ.champ.code] = it }, effacable = false)
            }
            // Les preuves, seulement quand la preuve d'existence vaut « Oui », comme le web.
            if (vm.preuvesOuvertes) for ((cle, libelle, estUrl) in vm.preuves) {
                ChampLibelle(libelle) {
                    ChampTexteFormulaire(
                        vm.textesPreuves[cle].orEmpty(),
                        { vm.textesPreuves[cle] = it },
                        uneLigne = estUrl,
                        clavier = if (estUrl) KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri) else KeyboardOptions.Default,
                    )
                }
            }
            // Le commentaire sans libellé, son invite seule, sur deux lignes, comme le web.
            vm.commentaire?.let { valeur ->
                ChampTexteFormulaire(
                    valeur,
                    { vm.commentaire = it },
                    Modifier.padding(top = if (ios) 0.dp else 10.dp),
                    lignesMin = 2,
                    uneLigne = false,
                    clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    indication = "Commentaire",
                )
            }
        }
    }
    // Les champs additionnels hors de la section avancée, sans titre, comme le web.
    val additionnels = vm.additionnels
    if (additionnels != null && additionnels.defsAffiches.isNotEmpty()) ChampsAdditionnelsVue(additionnels)
}
