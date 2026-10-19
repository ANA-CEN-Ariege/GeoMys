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

// La liste des protocoles : fragment_suivis.xml, item_suivi_module.xml et SuivisFragment de GeoMys-Android
// @ cdd5196 sous Android, SuivisView de GeoMys-IOS (UI/MonitoringViews.swift) sous iOS.

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.ariegenature.geomys.monitoring.ListeProtocoles
import fr.ariegenature.geomys.network.MonitoringModule
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonContour
import fr.ariegenature.geomys.ui.composants.BoutonIcone
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.GraisseSymbole
import fr.ariegenature.geomys.ui.composants.IndicateurActivite
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_eye
import fr.ariegenature.geomys.ui.ressources.ic_info
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import fr.ariegenature.geomys.ui.theme.FormeContinue

@Composable
fun SuivisScreen(
    vm: SuivisViewModel,
    onAccueil: () -> Unit,
    onDetail: (MonitoringModule) -> Unit,
    onCarte: (MonitoringModule) -> Unit,
    onAttente: () -> Unit,
) {
    LifecycleResumeEffect(vm) {
        vm.rafraichirAttente()
        onPauseOrDispose {}
    }
    val etat = vm.etat
    // Vignette d'environ 128 dp, comme `chargerImagePicto`.
    val cotePx = with(LocalDensity.current) { 128.dp.roundToPx() }
    LaunchedEffect(etat) {
        if (etat is EtatSuivis.Protocoles) vm.chargerPictos(etat.modules, cotePx)
    }
    FondGeoMys {
        if (estIos) ContenuIos(vm, onAccueil, onDetail, onCarte, onAttente)
        else ContenuAndroid(vm, onAccueil, onDetail, onCarte, onAttente)
    }
}

// ─── Android : fragment_suivis.xml ───

@Composable
private fun ContenuAndroid(
    vm: SuivisViewModel,
    onAccueil: () -> Unit,
    onDetail: (MonitoringModule) -> Unit,
    onCarte: (MonitoringModule) -> Unit,
    onAttente: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).union(WindowInsets.ime))
            .padding(16.dp),
    ) {
        BandeauNavigation(ListeProtocoles.TITRE, onAccueil, Modifier.padding(bottom = 12.dp))
        Text(ListeProtocoles.TITRE, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            ListeProtocoles.SOUS_TITRE,
            color = CouleursGeoMys.grisFonce,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        if (vm.nbEnAttente > 0) {
            BoutonContour(
                ListeProtocoles.libelleAttente(vm.nbEnAttente),
                onAttente,
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
        when (val etat = vm.etat) {
            EtatSuivis.Chargement -> IndicateurActivite(
                CouleursGeoMys.orange,
                Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp),
                tailleAndroid = 32f,
            )
            is EtatSuivis.Message -> Text(
                etat.texte,
                color = CouleursGeoMys.grisFonce,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            )
            is EtatSuivis.Protocoles -> Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                etat.modules.forEach { m ->
                    LigneAndroid(m, vm.pictos[m.moduleCode], { onDetail(m) }, { onCarte(m) })
                }
            }
        }
    }
}

/** item_suivi_module.xml : picto 36 dp, nom et description, boutons « i » et « œil » de 40 dp. */
@Composable
private fun LigneAndroid(module: MonitoringModule, picto: Picto?, onDetail: () -> Unit, onCarte: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tant que l'image se charge, sa place est réservée (ImageView visible) ; sans image ni
        // emoji, plus rien.
        when (picto) {
            null -> Spacer(Modifier.size(36.dp))
            is Picto.Image -> Image(picto.image, "Picto du module", Modifier.size(36.dp), contentScale = ContentScale.Fit)
            is Picto.Emoji -> Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text(picto.texte, fontSize = 22.sp) }
            Picto.Aucun -> {}
        }
        Column(Modifier.weight(1f).padding(8.dp)) {
            Text(module.moduleLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            module.moduleDesc?.let {
                Text(it, color = CouleursGeoMys.grisFonce, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        BoutonIcone(Res.drawable.ic_info, CouleursGeoMys.jauneClair, "Détails du protocole", onDetail, cible = DpSize(40.dp, 40.dp))
        BoutonIcone(Res.drawable.ic_eye, CouleursGeoMys.jauneClair, "Voir tous les sites sur carte", onCarte, cible = DpSize(40.dp, 40.dp))
    }
}

// ─── iOS : SuivisView ───

@Composable
private fun ContenuIos(
    vm: SuivisViewModel,
    onAccueil: () -> Unit,
    onDetail: (MonitoringModule) -> Unit,
    onCarte: (MonitoringModule) -> Unit,
    onAttente: () -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BandeauNavigation(ListeProtocoles.TITRE, onAccueil)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(ListeProtocoles.TITRE, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(ListeProtocoles.SOUS_TITRE, fontSize = 13.sp, color = CouleursGeoMys.blanc70)
        }
        if (vm.nbEnAttente > 0) {
            // Bouton contour propre à cet écran dans GeoMys-IOS : coins de 10 pt, trait de 1,5 pt.
            BoutonIos(onAttente, Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp), opaciteAppui = 0.7f) {
                Text(
                    ListeProtocoles.libelleAttente(vm.nbEnAttente),
                    color = CouleursGeoMys.jauneClair,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CouleursGeoMys.jauneClair, FormeContinue(10.dp))
                        .padding(vertical = 10.dp),
                )
            }
        }
        when (val etat = vm.etat) {
            EtatSuivis.Chargement -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                IndicateurActivite(CouleursGeoMys.jauneClair, grandIos = true)
            }
            is EtatSuivis.Message -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    etat.texte,
                    fontSize = 14.sp,
                    color = CouleursGeoMys.blanc70,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            is EtatSuivis.Protocoles -> Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                etat.modules.forEach { m ->
                    LigneIos(m, vm.pictos[m.moduleCode], { onDetail(m) }, { onCarte(m) })
                }
            }
        }
    }
}

@Composable
private fun LigneIos(module: MonitoringModule, picto: Picto?, onDetail: () -> Unit, onCarte: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            when (picto) {
                is Picto.Image -> Image(picto.image, "Picto du module", Modifier.size(36.dp), contentScale = ContentScale.Fit)
                is Picto.Emoji -> Text(picto.texte, fontSize = 22.sp)
                null, Picto.Aucun -> {}
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(module.moduleLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            module.moduleDesc?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, color = CouleursGeoMys.blanc70) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BoutonSymboleIos("info.circle.fill", "Détails du protocole", onDetail)
            BoutonSymboleIos("eye.fill", "Voir tous les sites sur carte", onCarte)
        }
    }
}

/** `boutonIconeModule` de GeoMys-IOS : symbole SF de 20 pt demi-gras, cible de 40 pt. */
@Composable
private fun BoutonSymboleIos(nom: String, description: String, onClick: () -> Unit) {
    BoutonIos(onClick, Modifier.size(40.dp), opaciteAppui = 0.3f) {
        SymboleSf(nom, 20f, listOf(CouleursGeoMys.jauneClair), graisse = GraisseSymbole.DEMI_GRASSE, description = description)
    }
}
