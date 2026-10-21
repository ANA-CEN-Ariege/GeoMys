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

// Le cadre des écrans Caractérisation et Dénombrement : fragment_caracterisation.xml et
// fragment_denombrement.xml de GeoMys-Android @ cdd5196 (bandeau, titre, coche de 32 dp) sous Android,
// EcranFormulaireSaisie de GeoMys-IOS (UI/Saisie/ComposantsFormulaireSaisie.swift) sous iOS.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.occtax.ChampNomenclature
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.ChampLibelle
import fr.ariegenature.geomys.ui.composants.DialogueGardeSortie
import fr.ariegenature.geomys.ui.composants.GRIS_TITRE_CHAMP
import fr.ariegenature.geomys.ui.composants.GardeSortie
import fr.ariegenature.geomys.ui.composants.HAUTEUR_BANDEAU_IOS
import fr.ariegenature.geomys.ui.composants.ListeDeroulante
import fr.ariegenature.geomys.ui.composants.RetourSysteme
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.barreChampObligatoire
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.coche
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import org.jetbrains.compose.resources.painterResource

/** Un écran de formulaire d'espèce : bandeau du flux ([typeSaisieLabel]), [titre] (et [sousTitre],
 *  le nom de l'espèce du dénombrement), [contenu] défilant, coche de validation ([onValider]) en
 *  haut à droite. */
@Composable
fun EcranFormulaireEspece(
    titre: String,
    sousTitre: String?,
    typeSaisieLabel: String,
    onAccueil: () -> Unit,
    onValider: () -> Unit,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    FondGeoMys {
        if (estIos) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                BandeauNavigation(typeSaisieLabel, onAccueil, titreIos = titre)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.padding(end = 44.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(titre, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        if (!sousTitre.isNullOrEmpty()) Text(sousTitre, fontSize = 14.sp, fontStyle = FontStyle.Italic, color = CouleursGeoMys.blanc70)
                    }
                    contenu()
                }
            }
            BoutonIos(onValider, Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(end = 12.dp).height(HAUTEUR_BANDEAU_IOS), opaciteAppui = 0.6f) {
                SymboleSf("checkmark.circle.fill", 30f, listOf(Color.White, CouleursGeoMys.vert), description = "Valider")
            }
        } else {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    BandeauNavigation(typeSaisieLabel, onAccueil, Modifier.padding(bottom = 12.dp))
                    Text(
                        titre,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 64.dp, bottom = if (sousTitre != null) 4.dp else 16.dp),
                    )
                    if (sousTitre != null) {
                        Text(
                            sousTitre,
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            color = GRIS_TITRE_CHAMP,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        )
                    }
                    contenu()
                }
                // ImageButton de 32 dp à 16 dp du coin, ondulation sans bord.
                Image(
                    painterResource(Res.drawable.coche),
                    contentDescription = "Valider",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(32.dp)
                        .clickable(remember { MutableInteractionSource() }, ripple(bounded = false), role = Role.Button, onClick = onValider),
                )
            }
        }
    }
}

/** Un formulaire en écran plein posé sur l'écran qui l'ouvre, sans passer par la navigation : les
 *  détails d'un relevé ou d'une station (demandes du 21 octobre). C'est le cadre
 *  d'[EcranFormulaireEspece], la coche appelant [onValider]. Sous iOS, il arrive et repart comme un
 *  écran poussé ; sous Android, sans animation, comme la navigation. Il arrête les touchers destinés à
 *  l'écran dessous. Le retour (bouton d'Android, balayage depuis le bord sous iOS) l'annule
 *  ([onAnnuler]) ; la maison l'annule puis mène à l'accueil. Tous deux passent par la garde de sortie :
 *  « Quitter sans enregistrer ? » ([titreGarde]) s'il est [modifie]. */
@Composable
fun FormulaireEnEcranPlein(
    visible: Boolean,
    titre: String,
    typeSaisieLabel: String,
    titreGarde: String,
    modifie: () -> Boolean,
    onAccueil: () -> Unit,
    onAnnuler: () -> Unit,
    onValider: () -> Unit,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val ios = estIos
    val garde = remember { GardeSortie() }
    garde.modifie = modifie
    RetourSysteme(actif = visible) { garde.demander(onAnnuler) }
    AnimatedVisibility(
        visible,
        enter = if (ios) slideInHorizontally(tween(350)) { it } else EnterTransition.None,
        exit = if (ios) slideOutHorizontally(tween(300)) { it } else ExitTransition.None,
    ) {
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}) {
            EcranFormulaireEspece(
                titre = titre,
                sousTitre = null,
                typeSaisieLabel = typeSaisieLabel,
                onAccueil = {
                    garde.demander {
                        onAnnuler()
                        onAccueil()
                    }
                },
                onValider = onValider,
                contenu = contenu,
            )
        }
    }
    DialogueGardeSortie(garde, titreGarde)
}

/** Un champ de nomenclature : son libellé et sa liste déroulante (buildField d'OcctaxFieldsRenderer,
 *  NomenclaturePicker de GeoMys-IOS). Comme le `pnx-nomenclature` du web, la liste n'a pas
 *  d'entrée vide : le libellé du champ en invite, une croix pour vider le choix (position 0) ; une
 *  liste obligatoire vide porte la barre rouge, la bordure rouge du web. */
@Composable
fun ChampNomenclatureVue(champ: ChampNomenclature, position: Int, onChoix: (Int) -> Unit, modifier: Modifier = Modifier) {
    ChampLibelle(champ.libelle, modifier) {
        Box(Modifier.fillMaxWidth().barreChampObligatoire(champ.obligatoire && position == 0)) {
            ListeDeroulante(champ.libelles.drop(1), position - 1, { onChoix(it + 1) }, invite = champ.libelle, onEffacer = { onChoix(0) })
        }
    }
}
