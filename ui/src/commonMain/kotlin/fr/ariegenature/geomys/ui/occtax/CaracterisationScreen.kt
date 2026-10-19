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

// La caractérisation d'une occurrence : fragment_caracterisation.xml et CaracterisationFragment de
// GeoMys-Android @ cdd5196 sous Android, CaracterisationView de GeoMys-IOS sous iOS.

package fr.ariegenature.geomys.ui.occtax

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
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.ui.composants.ChampLibelle
import fr.ariegenature.geomys.ui.composants.ChampTexteFormulaire
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.estIos

/** android.R.color.holo_blue_dark : « ▾ Plus de champs ». */
private val BLEU_LIEN = Color(0xFF0099CC)

/** colorPrimaryVariant de GeoMys-IOS : « Plus de champs ». */
private val BLEU_VARIANTE = Color(0xFF1565C0)

/** La caractérisation de l'occurrence ; [onValide] reçoit l'espèce caractérisée. */
@Composable
fun CaracterisationScreen(
    vm: CaracterisationViewModel,
    typeSaisieLabel: String,
    onAccueil: () -> Unit,
    onValide: (EspeceReleve) -> Unit,
) {
    val ios = estIos
    val toasts = LocalToasts.current
    EcranFormulaireEspece(
        titre = "Caractérisation de l'occurrence",
        sousTitre = null,
        typeSaisieLabel = typeSaisieLabel,
        onAccueil = onAccueil,
        onValider = {
            val (espece, message) = vm.valider()
            if (espece != null) onValide(espece) else if (message != null) toasts.afficher(message, true)
        },
    ) {
        for (champ in vm.basiques) {
            ChampNomenclatureVue(champ, vm.positions[champ.champ.code] ?: 0, { vm.positions[champ.champ.code] = it })
        }
        if (vm.aDesChampsAvances) {
            // UN seul repli pour toute la section avancée, comme la gn-advanced-section du web.
            if (ios) {
                Row(
                    Modifier.clickable { vm.avanceOuvert = !vm.avanceOuvert },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SymboleSf(if (vm.avanceOuvert) "chevron.up" else "chevron.down", 13f, listOf(BLEU_VARIANTE))
                    Text(if (vm.avanceOuvert) "Moins de champs" else "Plus de champs", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = BLEU_VARIANTE)
                }
            } else {
                Text(
                    if (vm.avanceOuvert) "▴ Moins de champs" else "▾ Plus de champs",
                    fontSize = 13.sp,
                    color = BLEU_LIEN,
                    modifier = Modifier.padding(top = 12.dp).clickable { vm.avanceOuvert = !vm.avanceOuvert },
                )
            }
            if (vm.avanceOuvert) {
                vm.determinateur?.let { valeur ->
                    ChampLibelle("Déterminateur") {
                        ChampTexteFormulaire(valeur, { vm.determinateur = it }, clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    }
                }
                for (champ in vm.avances) {
                    ChampNomenclatureVue(champ, vm.positions[champ.champ.code] ?: 0, { vm.positions[champ.champ.code] = it })
                }
                for ((cle, libelle, estUrl) in vm.preuves) {
                    ChampLibelle(libelle) {
                        ChampTexteFormulaire(
                            vm.textesPreuves[cle].orEmpty(),
                            { vm.textesPreuves[cle] = it },
                            uneLigne = estUrl,
                            clavier = if (estUrl) KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri) else KeyboardOptions.Default,
                        )
                    }
                }
                vm.commentaire?.let { valeur ->
                    ChampLibelle("Commentaire") {
                        ChampTexteFormulaire(valeur, { vm.commentaire = it }, lignesMin = 3, uneLigne = false, clavier = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    }
                }
            }
        }
        val additionnels = vm.additionnels
        if (additionnels != null && additionnels.defs.isNotEmpty()) {
            Column {
                Text(
                    "Champs additionnels (occurrence)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ios) Color.White else Color.Unspecified,
                    modifier = Modifier.padding(top = if (ios) 4.dp else 20.dp, bottom = if (ios) 14.dp else 2.dp),
                )
                ChampsAdditionnelsVue(additionnels)
            }
        }
    }
}
