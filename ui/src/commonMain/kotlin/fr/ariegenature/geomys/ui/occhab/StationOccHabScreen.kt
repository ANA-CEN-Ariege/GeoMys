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

// L'écran d'une station OccHab : fragment_occhab_station.xml et OccHabStationFragment de GeoMys-Android
// @ cdd5196 sous Android, OccHabStationView de GeoMys-IOS sous iOS.

package fr.ariegenature.geomys.ui.occhab

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.ariegenature.geomys.occhab.CarteOccHab
import fr.ariegenature.geomys.occhab.StationOccHab
import fr.ariegenature.geomys.ui.composants.AlerteEchecEcriture
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonContour
import fr.ariegenature.geomys.ui.composants.BoutonIcone
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonPlein
import fr.ariegenature.geomys.ui.composants.GraisseSymbole
import fr.ariegenature.geomys.ui.composants.HAUTEUR_BANDEAU_IOS
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.afficher
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.coche
import fr.ariegenature.geomys.ui.ressources.ic_delete
import fr.ariegenature.geomys.ui.ressources.ic_edit
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import fr.ariegenature.geomys.ui.theme.FormeContinue
import org.jetbrains.compose.resources.painterResource

/** `?attr/colorControlHighlight` du thème sombre : filets de l'écran. */
private val SURBRILLANCE = Color(0x33FFFFFF)

/** `android.R.color.holo_red_dark` : la poubelle d'un habitat. */
private val HOLO_ROUGE_FONCE = Color(0xFFCC0000)

/** L'écran d'une station. Le retour système (Android) ou le balayage depuis le bord (iOS) ramène à
 *  la carte, la station enregistrée telle quelle. */
@Composable
fun StationOccHabScreen(vm: StationOccHabViewModel, onAccueil: () -> Unit, onHabitat: (String?) -> Unit, onCarteSuivante: () -> Unit) {
    val ios = estIos
    val toasts = LocalToasts.current
    LaunchedEffect(vm) {
        vm.ouverture()
        vm.evenements.collect { evenement ->
            when (evenement) {
                is EvenementStationOccHab.Message -> toasts.afficher(evenement.texte, evenement.longue)
                is EvenementStationOccHab.Habitat -> onHabitat(evenement.id)
                EvenementStationOccHab.CarteSuivante -> onCarteSuivante()
                EvenementStationOccHab.Accueil -> onAccueil()
            }
        }
    }
    LifecycleResumeEffect(vm) {
        vm.rafraichir()
        onPauseOrDispose {}
    }
    FondGeoMys {
        if (ios) ContenuIos(vm, onAccueil) else ContenuAndroid(vm, onAccueil)
        DialogueDetailsStation(vm.details, vm::annulerDetails, vm::validerDetails)
        AlerteEchecEcriture(vm.echecEcriture, vm::fermerEchecEcriture, StationOccHab.CONSIGNE_STATION)
    }
}

// ─── Android ───

@Composable
private fun ContenuAndroid(vm: StationOccHabViewModel, onAccueil: () -> Unit) {
    val secondaire = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
        // En-tête : bandeau de navigation et coche verte « terminer » (zone de 48 dp, coche de 32).
        Box(Modifier.fillMaxWidth()) {
            BandeauNavigation(CarteOccHab.LIBELLE, onAccueil, Modifier.align(Alignment.CenterStart).padding(start = 12.dp, top = 12.dp))
            Image(
                painterResource(Res.drawable.coche),
                contentDescription = "Terminer la station",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp, top = 4.dp)
                    .size(48.dp)
                    .clickable(remember { MutableInteractionSource() }, ripple(bounded = false), role = Role.Button, onClick = vm::terminer)
                    .padding(8.dp),
            )
        }
        // Titre et « Détails » sur la même ligne.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(vm.titre, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            BoutonContour(
                StationOccHab.DETAILS,
                vm::ouvrirDetails,
                margesAndroid = PaddingValues(horizontal = 16.dp),
                hauteurMinAndroid = 36.dp,
            )
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(1.dp).background(SURBRILLANCE))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(vm.geometrie, fontSize = 13.sp, color = secondaire, modifier = Modifier.padding(top = 8.dp))
            Text(vm.jeuDeDonnees, fontSize = 13.sp, color = secondaire, modifier = Modifier.padding(top = 4.dp))
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(SURBRILLANCE))
            Text(StationOccHab.HABITATS, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            vm.habitats.forEach { h ->
                // La ligne n'est PAS cliquable : l'édition passe par le crayon, la suppression par la poubelle.
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(StationOccHab.libelleHabitat(h), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    BoutonIcone(Res.drawable.ic_edit, CouleursGeoMys.primaire, "Modifier l'habitat", { vm.editerHabitat(h.id) }, cible = DpSize(36.dp, 36.dp))
                    BoutonIcone(Res.drawable.ic_delete, HOLO_ROUGE_FONCE, "Supprimer l'habitat", { vm.supprimerHabitat(h.id) }, cible = DpSize(36.dp, 36.dp))
                }
            }
            if (vm.habitats.isEmpty()) Text(StationOccHab.AUCUN_HABITAT, fontSize = 13.sp, color = secondaire, modifier = Modifier.padding(top = 4.dp))
            BoutonPlein(StationOccHab.AJOUTER_HABITAT, vm::ajouterHabitat, Modifier.padding(top = 8.dp))
        }
    }
}

// ─── iOS : OccHabStationView de GeoMys-IOS ───

@Composable
private fun ContenuIos(vm: StationOccHabViewModel, onAccueil: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            BandeauNavigation(CarteOccHab.LIBELLE, onAccueil)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.titre, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                    BoutonIos(vm::ouvrirDetails, opaciteAppui = 0.6f) {
                        Text(
                            StationOccHab.DETAILS,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CouleursGeoMys.jauneClair,
                            modifier = Modifier
                                .border(1.dp, CouleursGeoMys.jauneClair, FormeContinue(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Text(vm.geometrie, fontSize = 13.sp, color = CouleursGeoMys.blanc70)
                Text(vm.jeuDeDonnees, fontSize = 13.sp, color = CouleursGeoMys.blanc70)
                Box(Modifier.fillMaxWidth().height(with(LocalDensity.current) { 1f.toDp() }).background(Color.White.copy(alpha = 0.15f)))
                Text(StationOccHab.HABITATS, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (vm.habitats.isEmpty()) Text(StationOccHab.AUCUN_HABITAT, fontSize = 13.sp, color = CouleursGeoMys.blanc70)
                vm.habitats.forEach { h ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(StationOccHab.libelleHabitat(h), fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
                        BoutonIos({ vm.editerHabitat(h.id) }, Modifier.size(40.dp), opaciteAppui = 0.6f) {
                            SymboleSf("pencil", 16f, listOf(CouleursGeoMys.jauneClair), graisse = GraisseSymbole.DEMI_GRASSE, description = "Modifier l'habitat")
                        }
                        BoutonIos({ vm.supprimerHabitat(h.id) }, Modifier.size(40.dp), opaciteAppui = 0.6f) {
                            SymboleSf("trash", 16f, listOf(CouleursGeoMys.rougePastille), graisse = GraisseSymbole.DEMI_GRASSE, description = "Supprimer l'habitat")
                        }
                    }
                }
                BoutonIos(vm::ajouterHabitat, Modifier.fillMaxWidth().padding(top = 6.dp), opaciteAppui = 0.6f) {
                    Box(
                        Modifier.fillMaxWidth().background(CouleursGeoMys.primaire, FormeContinue(10.dp)).padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(StationOccHab.AJOUTER_HABITAT, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
        // La coche de fin, par-dessus le bandeau, en haut à droite.
        BoutonIos(vm::terminer, Modifier.align(Alignment.TopEnd).padding(end = 12.dp).height(HAUTEUR_BANDEAU_IOS), opaciteAppui = 0.6f) {
            SymboleSf("checkmark.circle.fill", 30f, listOf(Color.White, CouleursGeoMys.vert), description = "Terminer la station")
        }
    }
}
