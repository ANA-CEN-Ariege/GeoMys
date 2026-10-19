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

// La carte des géométries des Suivis : fragment_carte_geometrie.xml et CarteGeometrieFragment de GeoMys-Android
// @ cdd5196 sous Android (carte plein écran, fil flottant, badge du titre, zoom en bas à gauche, fond en bas à
// droite), CarteGeometrieView de GeoMys-IOS (UI/Monitoring/CarteGeometrieView.swift) sous iOS (fil en bande, gouttes
// cerclées de blanc, polygones translucides).

package fr.ariegenature.geomys.ui.suivis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.monitoring.ActionCarte
import fr.ariegenature.geomys.monitoring.CarteSuivis
import fr.ariegenature.geomys.monitoring.FilSegment
import fr.ariegenature.geomys.monitoring.GeometrieSuivi
import fr.ariegenature.geomys.monitoring.ObjetCarte
import fr.ariegenature.geomys.occtax.CadrageSortie
import fr.ariegenature.geomys.ui.carte.AttributionFond
import fr.ariegenature.geomys.ui.carte.BoutonCarte
import fr.ariegenature.geomys.ui.carte.CarteGeoMys
import fr.ariegenature.geomys.ui.carte.CerclePrecision
import fr.ariegenature.geomys.ui.carte.DialogueChoixFond
import fr.ariegenature.geomys.ui.carte.GoutteIos
import fr.ariegenature.geomys.ui.carte.PointPosition
import fr.ariegenature.geomys.ui.carte.StyleBoutonCarte
import fr.ariegenature.geomys.ui.carte.animerCamera
import fr.ariegenature.geomys.ui.carte.cadrer
import fr.ariegenature.geomys.ui.carte.collectionGeometries
import fr.ariegenature.geomys.ui.carte.enPosition
import fr.ariegenature.geomys.ui.carte.rememberStyleFond
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.DialogueActions
import fr.ariegenature.geomys.ui.composants.DialogueChoixLignesAndroid
import fr.ariegenature.geomys.ui.composants.FilArianeSuivis
import fr.ariegenature.geomys.ui.composants.IndicateurActivite
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.afficher
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_add
import fr.ariegenature.geomys.ui.ressources.ic_location_on
import fr.ariegenature.geomys.ui.ressources.ic_map
import fr.ariegenature.geomys.ui.ressources.ic_remove
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import kotlinx.coroutines.launch
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource

// Couleurs de CarteGeometrieFragment (et de CouleurCarte, qui les reprend sous iOS).
private val ROUGE_PARENT = Color(0xFFD32F2F)
private val ORANGE_ENFANT = Color(0xFFFF9800)
private val BLEU_SURFACE = Color(0x552196F3)
private val BLEU_CONTOUR = Color(0xFF1976D2)
private val BLEU_LIGNE = Color(0xCC2196F3)
private val ORANGE_SURFACE = Color(0x44FF9800)
private val ORANGE_CONTOUR = Color(0xFFE65100)
private val ORANGE_LIGNE = Color(0xCCFF9800)

/** `badge_bg` : blanc 25 % aux coins de 10 dp. */
private val FOND_BADGE = Color(0x40FFFFFF)

@Composable
fun CarteSuiviScreen(
    vm: CarteSuiviViewModel,
    onNiveau: (List<FilSegment>) -> Unit,
    onFiche: (String, Int, List<FilSegment>) -> Unit,
    onNouvelleSaisie: (OuvertureNouvelleVisite) -> Unit,
) {
    val ios = estIos
    val toasts = LocalToasts.current
    LaunchedEffect(vm) {
        vm.evenements.collect { evenement ->
            when (evenement) {
                is EvenementCarteSuivi.Toast -> toasts.afficher(evenement.texte)
                is EvenementCarteSuivi.Fiche -> onFiche(evenement.objectType, evenement.id, evenement.fil)
                is EvenementCarteSuivi.NouvelleSaisie -> onNouvelleSaisie(evenement.ouverture)
            }
        }
    }
    LifecycleResumeEffect(vm) {
        vm.reprendre()
        onPauseOrDispose { vm.suspendre() }
    }
    FondGeoMys {
        // GeoMys-IOS ne montre la carte qu'une fois les géométries chargées ; Android la montre d'emblée.
        if (!ios || (!vm.chargement && vm.erreur == null)) Carte(vm)
        if (ios) HautIos(vm, onNiveau) else HautAndroid(vm, onNiveau)
        if (vm.chargement) {
            Box(Modifier.align(Alignment.Center)) {
                if (ios) IndicateurActivite(CouleursGeoMys.jauneClair, grandIos = true) else IndicateurActivite(CouleursGeoMys.orange, tailleAndroid = 40f)
            }
        }
        vm.erreur?.let { message ->
            if (ios) {
                // Le `Label` de GeoMys-IOS, sur le dégradé.
                Row(
                    Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SymboleSf("map", 17f, listOf(CouleursGeoMys.blanc70))
                    Text(message, color = CouleursGeoMys.blanc70, textAlign = TextAlign.Center)
                }
            } else {
                Text(
                    message,
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).background(FOND_BADGE, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
    Dialogues(vm)
}

/** Le fil d'Ariane et le badge du titre. Android : fil flottant à 12 dp des bords, le badge 12 dp
 *  sous lui, au centre ; iOS : le fil en bande, le badge en dessous (blanc 25 %, 14 pt gras). */
@Composable
private fun HautAndroid(vm: CarteSuiviViewModel, onNiveau: (List<FilSegment>) -> Unit) {
    val densite = LocalDensity.current
    var basFil by remember { mutableStateOf(64.dp) }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        FilArianeSuivis(
            vm.fil,
            dernierCliquable = false,
            onNiveau,
            Modifier
                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                .onSizeChanged { basFil = 12.dp + with(densite) { it.height.toDp() } + 12.dp },
        )
        if (vm.titre.isNotEmpty()) Badge(vm.titre, Modifier.align(Alignment.TopCenter).padding(top = basFil))
    }
}

@Composable
private fun HautIos(vm: CarteSuiviViewModel, onNiveau: (List<FilSegment>) -> Unit) {
    Column(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilArianeSuivis(vm.fil, dernierCliquable = false, onNiveau)
        if (vm.titre.isNotEmpty()) Badge(vm.titre)
    }
}

@Composable
private fun Badge(titre: String, modifier: Modifier = Modifier) {
    Text(
        titre,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier.background(FOND_BADGE, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** La carte, ses géométries et ses boutons : zoom en bas à gauche, fond en bas à droite. */
@Composable
private fun Carte(vm: CarteSuiviViewModel) {
    val ios = estIos
    val densite = LocalDensity.current
    val px = { n: Float -> with(densite) { n.toDp() } }
    val portee = rememberCoroutineScope()
    val objets = vm.objets
    val epingle = painterResource(Res.drawable.ic_location_on)
    val goutteParent = remember { GoutteIos(ROUGE_PARENT) }
    val goutteEnfant = remember { GoutteIos(ORANGE_ENFANT) }

    fun toucher(i: Int?): ClickResult {
        val objet = i?.let { objets.getOrNull(it) } ?: return ClickResult.Pass
        vm.toucher(objet)
        return ClickResult.Consume
    }

    val etat = rememberMapState(baseStyle = rememberStyleFond(vm.fond), initialCameraPosition = CameraPosition(center = LatLon(46.5, 2.5).enPosition(), zoom = 6.0)) {
        // Les parents, puis leurs enfants par-dessus ; dans chacun, surfaces, lignes, puis points.
        for (enfant in listOf(false, true)) key(enfant) {
            val suffixe = if (enfant) "enfants" else "parents"
            val surfaces = rememberGeoJsonSource(GeoJsonData.JsonString(remember(objets) { collectionGeometries(objets, { it.enfant == enfant }) { it is GeometrieSuivi.Polygone } }))
            val lignes = rememberGeoJsonSource(GeoJsonData.JsonString(remember(objets) { collectionGeometries(objets, { it.enfant == enfant }) { it is GeometrieSuivi.Ligne } }))
            val points = rememberGeoJsonSource(GeoJsonData.JsonString(remember(objets) { collectionGeometries(objets, { it.enfant == enfant }) { it is GeometrieSuivi.Point } }))
            val couleurIos = if (enfant) ORANGE_LIGNE else BLEU_LIGNE
            FillLayer(
                id = "geometries-surfaces-$suffixe",
                source = surfaces,
                color = const(if (ios) couleurIos.copy(alpha = 0.2f) else if (enfant) ORANGE_SURFACE else BLEU_SURFACE),
                onClick = { entites -> toucher(entites.firstOrNull()?.properties?.get("i")?.jsonPrimitive?.int) },
            )
            LineLayer(
                id = "geometries-contours-$suffixe",
                source = surfaces,
                color = const(if (ios) couleurIos else if (enfant) ORANGE_CONTOUR else BLEU_CONTOUR),
                width = const(if (ios) (if (enfant) 3.dp else 5.dp) else px(if (enfant) 2f else 3f)),
            )
            LineLayer(
                id = "geometries-lignes-$suffixe",
                source = lignes,
                color = const(if (ios) couleurIos else if (enfant) ORANGE_LIGNE else BLEU_LIGNE),
                width = const(if (ios) (if (enfant) 3.dp else 5.dp) else px(if (enfant) 3f else 5f)),
                cap = const(LineCap.Round),
                hitPadding = 8.dp,
                onClick = { entites -> toucher(entites.firstOrNull()?.properties?.get("i")?.jsonPrimitive?.int) },
            )
            // ic_location_pin teinté (24 dp) sous Android ; la goutte cerclée de blanc (30 × 38 pt) sous iOS.
            SymbolLayer(
                id = "geometries-points-$suffixe",
                source = points,
                iconImage = if (ios) {
                    image(if (enfant) goutteEnfant else goutteParent, size = DpSize(30.dp, 38.dp))
                } else {
                    image(epingle, size = DpSize(24.dp, 24.dp), colorFilter = ColorFilter.tint(if (enfant) ORANGE_ENFANT else ROUGE_PARENT))
                },
                iconAnchor = const(SymbolAnchor.Bottom),
                iconAllowOverlap = const(true),
                iconIgnorePlacement = const(true),
                onClick = { entites -> toucher(entites.firstOrNull()?.properties?.get("i")?.jsonPrimitive?.int) },
            )
        }
        val position by vm.tracker.position.collectAsState()
        position?.let { CerclePrecision(LatLon(it.latitude, it.longitude), it.precision.toDouble()) }
    }
    LaunchedEffect(vm.emprise) {
        vm.emprise?.let { e -> etat.cadrer(CadrageSortie.Emprise(e.sud, e.ouest, e.nord, e.est)) }
    }
    Box(Modifier.fillMaxSize()) {
        CarteGeoMys(etat, Modifier.fillMaxSize()) {
            val position by vm.tracker.position.collectAsState()
            position?.let { PointPosition(LatLon(it.latitude, it.longitude)) }
        }
        val bas = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp)
        Column(Modifier.align(Alignment.BottomStart).then(bas), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoutonCarte(Res.drawable.ic_add, "Zoom avant", StyleBoutonCarte.CONTROLE_SAISIE, {
                portee.launch { etat.animerCamera(CameraUpdate(zoom = etat.cameraPosition.zoom + 1)) }
            })
            BoutonCarte(Res.drawable.ic_remove, "Zoom arrière", StyleBoutonCarte.CONTROLE_SAISIE, {
                portee.launch { etat.animerCamera(CameraUpdate(zoom = etat.cameraPosition.zoom - 1)) }
            })
        }
        BoutonCarte(Res.drawable.ic_map, "Changer fond", StyleBoutonCarte.CONTROLE, vm::montrerChoixFond, Modifier.align(Alignment.BottomEnd).then(bas))
        // La mention des données, entre les boutons du bas.
        AttributionFond(vm.fond, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 60.dp))
    }
}

@Composable
private fun Dialogues(vm: CarteSuiviViewModel) {
    val menu = vm.menu
    if (estIos) {
        DialogueActions(
            visible = menu != null,
            titre = menu?.objet?.label.orEmpty(),
            actions = menu?.let { m -> m.actions.map { a -> BoutonAlerte(a.libelle) { vm.choisir(m, a) } } + BoutonAlerte("Fermer", annulation = true) }.orEmpty(),
            onFermer = vm::fermerMenu,
        )
    } else {
        DialogueChoixLignesAndroid(
            visible = menu != null,
            titre = menu?.objet?.label.orEmpty(),
            lignes = menu?.actions?.map(ActionCarte::libelle).orEmpty(),
            onChoisir = { i -> menu?.let { m -> vm.choisir(m, m.actions[i]) } },
            onFermer = vm::fermerMenu,
        )
    }
    DialogueChoixFond(vm.choixFondVisible, vm.fond, vm::choisirFond, vm::fermerChoixFond, titre = CarteSuivis.TITRE_FOND)
}
