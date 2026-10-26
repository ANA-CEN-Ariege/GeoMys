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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/CacheManagerFragment.kt,
// res/layout/fragment_cache_manager.xml). Allure iOS de GeoMys-IOS @ 5fda84c (UI/Map/CacheManagerView.swift,
// UI/Map/CartesMbtilesView.swift).

package fr.ariegenature.geomys.ui.cartes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.ariegenature.geomys.carte.CartesMbtiles
import fr.ariegenature.geomys.carte.CiblageProtocole
import fr.ariegenature.geomys.carte.ZoneHorsLigne
import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.monitoring.GeometrieSuivi
import fr.ariegenature.geomys.monitoring.ObjetCarte
import fr.ariegenature.geomys.ui.carte.AttributionFond
import fr.ariegenature.geomys.ui.carte.BoutonCarte
import fr.ariegenature.geomys.ui.carte.BulleCarte
import fr.ariegenature.geomys.ui.carte.CarteGeoMys
import fr.ariegenature.geomys.ui.carte.DialogueChoixFond
import fr.ariegenature.geomys.ui.carte.GoutteIos
import fr.ariegenature.geomys.ui.carte.PointPosition
import fr.ariegenature.geomys.ui.carte.StyleBoutonCarte
import fr.ariegenature.geomys.ui.carte.animerCamera
import fr.ariegenature.geomys.ui.carte.collectionGeometries
import fr.ariegenature.geomys.ui.carte.enPosition
import fr.ariegenature.geomys.ui.carte.rememberStyleFond
import fr.ariegenature.geomys.ui.composants.AlerteSysteme
import fr.ariegenature.geomys.ui.composants.BalayageRetourIosDesactive
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonDialogueIos
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonPlein
import fr.ariegenature.geomys.ui.composants.BoutonTexte
import fr.ariegenature.geomys.ui.composants.DialogueMaterialIos
import fr.ariegenature.geomys.ui.composants.DialogueProgression
import fr.ariegenature.geomys.ui.composants.DialogueSystemeAndroid
import fr.ariegenature.geomys.ui.composants.FeuilleActionsSysteme
import fr.ariegenature.geomys.ui.composants.GraisseSymbole
import fr.ariegenature.geomys.ui.composants.IndicateurActivite
import fr.ariegenature.geomys.ui.composants.LigneCorbeille
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.Progression
import fr.ariegenature.geomys.ui.composants.Relief
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.fondEnRelief
import fr.ariegenature.geomys.ui.composants.rememberSelecteurCarte
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_add
import fr.ariegenature.geomys.ui.ressources.ic_delete
import fr.ariegenature.geomys.ui.ressources.ic_list
import fr.ariegenature.geomys.ui.ressources.ic_location_on
import fr.ariegenature.geomys.ui.ressources.ic_map
import fr.ariegenature.geomys.ui.ressources.ic_remove
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FormeContinue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.camera.CameraFit
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
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.DpPadding
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.BoundingBox

/** L'orange des sites d'un protocole ciblé (#FF6F00), et son voile de surface : 33 % (0x55). */
private val ORANGE_PROTOCOLE = Color(0xFFFF6F00)
private val ORANGE_SURFACE = Color(0x55FF6F00)

/** `badge_bg` : blanc 25 % aux coins de 10. */
private val FOND_BADGE = Color(0x40FFFFFF)

/** Rouge de la corbeille sous Android (`app:tint`) ; `Couleurs.rougeFonce` sous iOS. */
private val ROUGE_CORBEILLE = Color(0xFFC62828)

/** `bg_accueil` (dégradé diagonal de l'accueil), fond du panneau des deux allures. */
private val DEGRADE = Brush.linearGradient(listOf(CouleursGeoMys.fondHaut, CouleursGeoMys.fondBas), start = Offset.Zero, end = Offset.Infinite)

@Composable
fun MapsManagerScreen(vm: MapsManagerViewModel, onAccueil: () -> Unit) {
    val ios = estIos
    val toasts = LocalToasts.current
    val portee = rememberCoroutineScope()
    // Sous iOS, le balayage depuis le bord ne quitte pas la carte : la maison du bandeau ramène à l'accueil.
    BalayageRetourIosDesactive()
    LifecycleResumeEffect(vm) {
        vm.reprendre()
        onPauseOrDispose { vm.suspendre() }
    }
    // Le nom d'un site touché, dans sa bulle : au-dessus de l'épingle d'un point, là où le doigt s'est posé
    // sur une ligne ou une surface (le titre des Marker, Polyline et Polygon d'osmdroid).
    var bulle by remember { mutableStateOf<BulleSite?>(null) }
    val dernierToucher = remember { arrayOfNulls<LatLon>(1) }
    val hauteurEpingle = if (ios) 38.dp else 24.dp
    val toucherSite = { i: Int? ->
        val objet = i?.let { vm.objetsProtocole.getOrNull(it) }
        val point = (objet?.geometries?.singleOrNull() as? GeometrieSuivi.Point)?.position
        val ou = point ?: dernierToucher[0]
        if (objet == null || ou == null) {
            ClickResult.Pass
        } else {
            bulle = BulleSite(ou, if (point != null) hauteurEpingle else 0.dp, objet.label)
            ClickResult.Consume
        }
    }
    val etat = rememberMapState(
        baseStyle = rememberStyleFond(vm.fond),
        initialCameraPosition = CameraPosition(
            center = LatLon(ZoneHorsLigne.LATITUDE_DEPART, ZoneHorsLigne.LONGITUDE_DEPART).enPosition(),
            zoom = MapsManagerViewModel.ZOOM_DEPART,
        ),
    ) { SitesProtocole(vm, toucherSite) }
    LaunchedEffect(vm, etat) {
        vm.evenements.collect { evenement ->
            when (evenement) {
                is EvenementMapsManager.Toast -> toasts.afficher(evenement.texte, evenement.longue)
                is EvenementMapsManager.Centrer -> portee.launch {
                    etat.animerCamera(CameraUpdate(center = evenement.position.enPosition(), zoom = evenement.zoom))
                }
                is EvenementMapsManager.Cadrer -> portee.launch { etat.cadrerSur(evenement.emprise) }
            }
        }
    }
    // Le panneau suit chaque mouvement de la carte.
    LaunchedEffect(etat) {
        etat.awaitViewport()
        snapshotFlow { etat.cameraPosition }.collect { camera ->
            etat.getVisibleBounds()?.let { b -> vm.carteBougee(Emprise(sud = b.south, ouest = b.west, nord = b.north, est = b.east), camera.zoom) }
        }
    }
    val position by vm.tracker.position.collectAsState()
    // Dès la première position, la carte s'y recentre, au zoom de travail du téléchargement.
    LaunchedEffect(position != null) {
        val p = position ?: return@LaunchedEffect
        if (vm.centreeSurPosition) return@LaunchedEffect
        vm.marquerCentree()
        etat.animerCamera(CameraUpdate(center = LatLon(p.latitude, p.longitude).enPosition(), zoom = MapsManagerViewModel.ZOOM_POSITION))
    }
    val choisirFichier = rememberSelecteurCarte(vm::importer)

    Box(Modifier.fillMaxSize().background(DEGRADE)) {
        // Sous iOS, la carte s'arrête aux marges de sûreté ; le dégradé paraît derrière la barre d'état.
        val marges = if (ios) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)) else Modifier
        CarteGeoMys(
            etat,
            Modifier.fillMaxSize().then(marges),
            // Sous iOS, toucher la carte ferme la bulle, comme celles de MapKit ; sous Android, la toucher.
            onClic = if (ios) { _ -> (bulle != null).also { bulle = null } } else null,
            onAvantClic = { ou, _ ->
                dernierToucher[0] = ou
                false
            },
            bornesZoom = MapsManagerViewModel.ZOOM_MIN..MapsManagerViewModel.ZOOM_MAX,
        ) {
            position?.let { PointPosition(LatLon(it.latitude, it.longitude)) }
            bulle?.let { b -> BulleCarte(b.position, b.decalage, b.nom, "", onFermer = { bulle = null }) }
        }
        if (ios) EcranIos(vm, etat, onAccueil) else EcranAndroid(vm, etat, onAccueil)
    }
    DialogueChoixFond(vm.choixFondVisible, vm.fond, vm::choisirFond, vm::fermerChoixFond)
    Dialogues(vm, choisirFichier)
}

/** Cadre la carte sur [emprise], avec une marge de 30 (`zoomToBoundingBox(…, 60 px)`), en l'animant. */
private suspend fun MapState.cadrerSur(emprise: Emprise) {
    try {
        animateCameraToBounds(
            BoundingBox(west = emprise.ouest, south = emprise.sud, east = emprise.est, north = emprise.nord),
            CameraFit(fitPadding = DpPadding(30.dp, 30.dp, 30.dp, 30.dp)),
        )
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
    }
}

/** Le nom d'un site touché, et où le poser. */
private class BulleSite(val position: LatLon, val decalage: Dp, val nom: String)

/** Les sites du protocole ciblé, en orange : surfaces voilées et cerclées, lignes, épingles ; [onToucher]
 *  reçoit l'indice du site touché.
 *  - Android : traits de 4 et 6 px, `ic_location_pin` teinté (24 dp) ;
 *  - iOS : traits de 4 et 6 pt, la goutte cerclée de blanc. */
@Composable
@MaplibreComposable
private fun SitesProtocole(vm: MapsManagerViewModel, onToucher: (Int?) -> ClickResult) {
    val ios = estIos
    val densite = LocalDensity.current
    val px = { n: Float -> with(densite) { n.toDp() } }
    val objets = vm.objetsProtocole
    val epingle = painterResource(Res.drawable.ic_location_on)
    val goutte = remember { GoutteIos(ORANGE_PROTOCOLE) }
    val tous = { _: ObjetCarte -> true }
    val surfaces = rememberGeoJsonSource(GeoJsonData.JsonString(remember(objets) { collectionGeometries(objets, tous) { it is GeometrieSuivi.Polygone } }))
    val lignes = rememberGeoJsonSource(GeoJsonData.JsonString(remember(objets) { collectionGeometries(objets, tous) { it is GeometrieSuivi.Ligne } }))
    val points = rememberGeoJsonSource(GeoJsonData.JsonString(remember(objets) { collectionGeometries(objets, tous) { it is GeometrieSuivi.Point } }))
    FillLayer(id = "protocole-surfaces", source = surfaces, color = const(ORANGE_SURFACE), onClick = { entites -> onToucher(entites.firstOrNull()?.properties?.get("i")?.jsonPrimitive?.int) })
    LineLayer(id = "protocole-contours", source = surfaces, color = const(ORANGE_PROTOCOLE), width = const(if (ios) 4.dp else px(4f)))
    LineLayer(
        id = "protocole-lignes",
        source = lignes,
        color = const(ORANGE_PROTOCOLE),
        width = const(if (ios) 6.dp else px(6f)),
        cap = const(LineCap.Round),
        hitPadding = 8.dp,
        onClick = { entites -> onToucher(entites.firstOrNull()?.properties?.get("i")?.jsonPrimitive?.int) },
    )
    SymbolLayer(
        id = "protocole-points",
        source = points,
        iconImage = if (ios) {
            image(goutte, size = DpSize(30.dp, 38.dp))
        } else {
            image(epingle, size = DpSize(24.dp, 24.dp), colorFilter = ColorFilter.tint(ORANGE_PROTOCOLE))
        },
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        onClick = { entites -> onToucher(entites.firstOrNull()?.properties?.get("i")?.jsonPrimitive?.int) },
    )
}

// ─── Android : fragment_cache_manager.xml ───

@Composable
private fun BoxScope.EcranAndroid(vm: MapsManagerViewModel, etat: MapState, onAccueil: () -> Unit) {
    val haut = Modifier.windowInsetsPadding(WindowInsets.statusBars)
    // « 🏠 › Maps Manager » à 12 dp du bord et 14 dp du haut ; le badge 8 dp dessous, au centre.
    Column(haut.fillMaxWidth()) {
        BandeauNavigation(ZoneHorsLigne.TITRE, onAccueil, Modifier.padding(start = 12.dp, top = 14.dp, end = 64.dp))
        Badge(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
    }
    // Vider le cache, en haut à droite : 48 dp, disque blanc, corbeille rouge.
    Box(
        haut
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(48.dp)
            .fondEnRelief(Color.White, CircleShape, Relief.DOUX)
            .clickable(onClickLabel = "Vider le cache", onClick = vm::demanderVidage),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(Res.drawable.ic_delete), "Vider le cache", colorFilter = ColorFilter.tint(ROUGE_CORBEILLE))
    }
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(8.dp)) { BoutonsZoom(vm, etat) }
                Column(Modifier.align(Alignment.BottomEnd), verticalArrangement = Arrangement.spacedBy(8.dp)) { Controles(vm) }
            }
            // La mention des données, au bord du panneau, entre les boutons.
            AttributionFond(vm.fond, Modifier.align(Alignment.BottomCenter).padding(horizontal = 60.dp))
        }
        PanneauAndroid(vm)
    }
}

@Composable
private fun Badge(modifier: Modifier = Modifier) {
    Text(
        ZoneHorsLigne.BADGE,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier.background(FOND_BADGE, if (estIos) FormeContinue(10.dp) else RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun BoutonsZoom(vm: MapsManagerViewModel, etat: MapState) {
    val portee = rememberCoroutineScope()
    val zoomer = { pas: Int -> portee.launch { etat.animerCamera(CameraUpdate(zoom = etat.cameraPosition.zoom + pas)) } }
    BoutonCarte(Res.drawable.ic_add, "Zoom avant", StyleBoutonCarte.CONTROLE_SAISIE, { zoomer(1) })
    BoutonCarte(Res.drawable.ic_remove, "Zoom arrière", StyleBoutonCarte.CONTROLE_SAISIE, { zoomer(-1) })
}

/** De haut en bas : recentrer, fond, et « Cibler un protocole » s'il y a des protocoles accessibles. */
@Composable
private fun Controles(vm: MapsManagerViewModel) {
    BoutonCarte(Res.drawable.ic_location_on, "Recentrer sur ma position", StyleBoutonCarte.CONTROLE, vm::recentrer, teinteIos = CouleursGeoMys.primaire)
    BoutonCarte(Res.drawable.ic_map, "Changer le fond de carte", StyleBoutonCarte.CONTROLE, vm::montrerChoixFond)
    if (vm.protocoles.isNotEmpty()) BoutonCarte(Res.drawable.ic_list, CiblageProtocole.TITRE, StyleBoutonCarte.CONTROLE, vm::ouvrirProtocoles)
}

/** Le panneau du bas, sur le fond de fenêtre (`bg_accueil`) : surface et zooms (14 sp gras), tuiles et
 *  poids (12 sp, blanc 70 %), occupation du cache (12 sp, colorOnSurfaceVariant, colorError au-delà de
 *  90 %), avertissement (#C62828), puis « Charger les tuiles » et « Cartes hors-ligne (MBTiles)… ». */
@Composable
private fun PanneauAndroid(vm: MapsManagerViewModel) {
    val couleurs = MaterialTheme.colorScheme
    val densite = LocalDensity.current
    val estimation = vm.estimation
    val (occupation, alerte) = vm.occupation
    Column(
        Modifier
            .fillMaxWidth()
            .background(DEGRADE)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
            .padding(12.dp),
    ) {
        Text(estimation?.surface.orEmpty(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(estimation?.tuiles.orEmpty(), color = CouleursGeoMys.blanc70, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Text(occupation, color = if (alerte) couleurs.error else couleurs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        estimation?.avertissement?.let { Text(it, color = ROUGE_CORBEILLE, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        BoutonPlein(
            ZoneHorsLigne.CHARGER,
            onClick = { vm.charger(densite.density) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            actif = estimation?.telechargeable == true,
        )
        BoutonTexte(ZoneHorsLigne.CARTES_HORS_LIGNE, Color.White, vm::ouvrirCartes, Modifier.fillMaxWidth())
    }
}

// ─── iOS : CacheManagerView ───

@Composable
private fun BoxScope.EcranIos(vm: MapsManagerViewModel, etat: MapState, onAccueil: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        BandeauNavigation(ZoneHorsLigne.TITRE, onAccueil)
        // Le badge au centre ; la corbeille à droite, loin de « Charger ».
        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Badge(Modifier.align(Alignment.Center))
            BoutonIos(vm::demanderVidage, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), opaciteAppui = 0.6f) {
                Box(Modifier.size(44.dp).fondEnRelief(Color.White, CircleShape, Relief.DOUX), contentAlignment = Alignment.Center) {
                    SymboleSf("trash", 18f, listOf(CouleursGeoMys.holoRougeFonce), graisse = GraisseSymbole.DEMI_GRASSE, description = "Vider le cache")
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { BoutonsZoom(vm, etat) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) { AttributionFond(vm.fond, Modifier.padding(horizontal = 4.dp), lignesMax = 2) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Controles(vm) }
        }
        PanneauIos(vm)
    }
    RechercheIos(vm)
}

/** Le panneau de GeoMys-IOS (marges de 16, dégradé opaque) : les lignes de la zone (14 pt gras, 12 pt
 *  blanc 70 %, rouge foncé), « Charger les tuiles » (15 pt demi-gras sur bleu, coins de 10, estompé à
 *  35 % s'il est inactif), « Cartes hors-ligne (MBTiles)… » (14 pt demi-gras). */
@Composable
private fun PanneauIos(vm: MapsManagerViewModel) {
    val densite = LocalDensity.current
    val estimation = vm.estimation
    val (occupation, alerte) = vm.occupation
    val actif = estimation?.telechargeable == true
    Column(Modifier.fillMaxWidth().background(DEGRADE).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (estimation != null) {
                Text(estimation.surface, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(estimation.tuiles, color = CouleursGeoMys.blanc70, fontSize = 12.sp)
            }
            Text(occupation, color = if (alerte) CouleursGeoMys.holoRougeFonce else CouleursGeoMys.blanc70, fontSize = 12.sp)
            estimation?.avertissement?.let { Text(it, color = CouleursGeoMys.holoRougeFonce, fontSize = 12.sp) }
        }
        BoutonIos({ vm.charger(densite.density) }, Modifier.fillMaxWidth(), actif = actif) {
            Text(
                ZoneHorsLigne.CHARGER,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CouleursGeoMys.primaire.copy(alpha = if (actif) 1f else 0.35f), FormeContinue(10.dp))
                    .padding(vertical = 12.dp),
            )
        }
        BoutonIos(vm::ouvrirCartes, Modifier.fillMaxWidth()) {
            Text(ZoneHorsLigne.CARTES_HORS_LIGNE, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** « Recherche des sites… » de GeoMys-IOS : indicateur et texte blancs sur noir à 65 %, au centre ; la
 *  carte ne répond plus pendant la recherche. */
@Composable
private fun BoxScope.RechercheIos(vm: MapsManagerViewModel) {
    val recherche = vm.dialogue as? DialogueCartes.Recherche
    AnimatedVisibility(recherche != null, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IndicateurActivite(Color.White)
                Text(CiblageProtocole.RECHERCHE, color = Color.White, fontSize = 13.sp)
                recherche?.let { Text(CiblageProtocole.messageModule(it.moduleCode), color = CouleursGeoMys.blanc70, fontSize = 12.sp) }
            }
        }
    }
}

// ─── Dialogues ───

@Composable
private fun Dialogues(vm: MapsManagerViewModel, choisirFichier: () -> Unit) {
    if (estIos) DialoguesIos(vm, choisirFichier) else DialoguesAndroid(vm, choisirFichier)
}

/** Les AlertDialog du système de GeoMys-Android. */
@Composable
private fun DialoguesAndroid(vm: MapsManagerViewModel, choisirFichier: () -> Unit) {
    val ouvert = vm.dialogue ?: return
    val fermer = { vm.fermer(ouvert) }
    val annuler = BoutonAlerte("Annuler")
    when (ouvert) {
        is DialogueCartes.Mbtiles -> DialogueSystemeAndroid(
            visible = true,
            titre = CartesMbtiles.TITRE,
            onFermer = fermer,
            message = if (ouvert.cartes.isEmpty()) CartesMbtiles.AUCUNE else null,
            lignes = ouvert.cartes.map { LigneCorbeille("${it.nom}\n${it.detail}", "Supprimer ${it.nom}") },
            onCorbeille = { vm.demanderSuppression(ouvert.cartes[it]) },
            positif = BoutonAlerte(CartesMbtiles.IMPORTER) { choisirFichier() },
            negatif = BoutonAlerte(CartesMbtiles.FERMER),
        )
        is DialogueCartes.Suppression -> DialogueSystemeAndroid(
            visible = true,
            titre = ouvert.carte.nom,
            onFermer = fermer,
            message = CartesMbtiles.CONFIRMER_SUPPRESSION,
            positif = BoutonAlerte(CartesMbtiles.SUPPRIMER) { vm.supprimer(ouvert.carte) },
            negatif = annuler,
        )
        DialogueCartes.Import -> DialogueSystemeAndroid(
            visible = true,
            titre = CartesMbtiles.TITRE_IMPORT,
            onFermer = fermer,
            message = CartesMbtiles.COPIE,
            progression = Progression(null),
            annulable = false,
        )
        // Seul « Annuler » l'arrête : Retour et le voile restent sans effet, pour ne pas le perdre par mégarde.
        is DialogueCartes.Telechargement -> DialogueSystemeAndroid(
            visible = true,
            titre = ZoneHorsLigne.TITRE_TELECHARGEMENT,
            onFermer = fermer,
            message = ouvert.message,
            progression = Progression(ouvert.avancement),
            negatif = BoutonAlerte(ZoneHorsLigne.ANNULER) { vm.annulerTelechargement() },
            annulable = false,
        )
        is DialogueCartes.Resultat -> DialogueSystemeAndroid(
            visible = true,
            titre = ouvert.titre,
            onFermer = fermer,
            message = ouvert.message,
            positif = BoutonAlerte("OK"),
        )
        DialogueCartes.Vider -> DialogueSystemeAndroid(
            visible = true,
            titre = ZoneHorsLigne.TITRE_VIDER,
            onFermer = fermer,
            message = ZoneHorsLigne.MESSAGE_VIDER,
            positif = BoutonAlerte(ZoneHorsLigne.TOUT_VIDER) { vm.vider() },
            negatif = annuler,
        )
        is DialogueCartes.Protocoles -> DialogueSystemeAndroid(
            visible = true,
            titre = CiblageProtocole.TITRE,
            onFermer = fermer,
            elements = ouvert.modules.map(CiblageProtocole::libelle),
            onElement = { vm.cibler(ouvert.modules[it]) },
            negatif = annuler,
        )
        is DialogueCartes.Recherche -> DialogueSystemeAndroid(
            visible = true,
            titre = CiblageProtocole.RECHERCHE,
            onFermer = fermer,
            message = CiblageProtocole.messageModule(ouvert.moduleCode),
            progression = Progression(null),
            annulable = false,
        )
    }
}

/** Les alertes, feuilles et dialogues de GeoMys-IOS. */
@Composable
private fun DialoguesIos(vm: MapsManagerViewModel, choisirFichier: () -> Unit) {
    val ouvert = vm.dialogue
    val annuler = BoutonAlerte("Annuler", annulation = true)
    // Les cartes importées : DialogueMaterial de CartesMbtilesView.
    val mbtiles = ouvert as? DialogueCartes.Mbtiles
    DialogueMaterialIos(
        visible = mbtiles != null,
        titre = CartesMbtiles.TITRE,
        onFond = { mbtiles?.let(vm::fermer) },
        boutons = { BoutonDialogueIos(CartesMbtiles.FERMER, CouleursGeoMys.blanc70) { mbtiles?.let(vm::fermer) } },
    ) {
        val cartes = mbtiles?.cartes.orEmpty()
        if (cartes.isEmpty()) {
            Text(CartesMbtiles.AUCUNE, color = CouleursGeoMys.blanc70, fontSize = 13.sp)
        } else {
            cartes.forEach { carte ->
                key(carte.fichier) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(carte.nom, color = Color.White, fontSize = 15.sp)
                            Text(carte.detail, color = CouleursGeoMys.blanc70, fontSize = 12.sp)
                        }
                        BoutonIos({ vm.demanderSuppression(carte) }, opaciteAppui = 0.4f) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                SymboleSf("trash", 16f, listOf(CouleursGeoMys.rougePastille), graisse = GraisseSymbole.DEMI_GRASSE, description = "Supprimer ${carte.nom}")
                            }
                        }
                    }
                }
            }
        }
        BoutonIos({
            mbtiles?.let(vm::fermer)
            choisirFichier()
        }, Modifier.padding(top = 6.dp), opaciteAppui = 0.4f) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SymboleSf("square.and.arrow.down", 15f, listOf(CouleursGeoMys.jauneClair), graisse = GraisseSymbole.DEMI_GRASSE)
                Text(CartesMbtiles.IMPORTER, color = CouleursGeoMys.jauneClair, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    val suppression = ouvert as? DialogueCartes.Suppression
    AlerteSysteme(
        visible = suppression != null,
        titre = suppression?.carte?.nom.orEmpty(),
        message = CartesMbtiles.CONFIRMER_SUPPRESSION,
        boutons = listOf(BoutonAlerte(CartesMbtiles.SUPPRIMER, destructif = true) { suppression?.let { vm.supprimer(it.carte) } }, annuler),
        onFermee = { suppression?.let(vm::fermer) },
    )
    DialogueProgression(ouvert == DialogueCartes.Import, CartesMbtiles.COPIE)
    TelechargementIos(ouvert as? DialogueCartes.Telechargement, vm::annulerTelechargement)
    val resultat = ouvert as? DialogueCartes.Resultat
    AlerteSysteme(
        visible = resultat != null,
        titre = resultat?.titre.orEmpty(),
        message = resultat?.message.orEmpty(),
        boutons = listOf(BoutonAlerte("OK")),
        onFermee = { resultat?.let(vm::fermer) },
    )
    AlerteSysteme(
        visible = ouvert == DialogueCartes.Vider,
        titre = ZoneHorsLigne.TITRE_VIDER,
        message = ZoneHorsLigne.MESSAGE_VIDER,
        boutons = listOf(BoutonAlerte(ZoneHorsLigne.TOUT_VIDER, destructif = true) { vm.vider() }, annuler),
        onFermee = { vm.fermer(DialogueCartes.Vider) },
    )
    val protocoles = ouvert as? DialogueCartes.Protocoles
    FeuilleActionsSysteme(
        visible = protocoles != null,
        titre = CiblageProtocole.TITRE,
        boutons = protocoles?.modules.orEmpty().map { m -> BoutonAlerte(CiblageProtocole.libelle(m)) { vm.cibler(m) } } + annuler,
        onFermee = { protocoles?.let(vm::fermer) },
    )
}

/** Le téléchargement, dans le DialogueMaterial de GeoMys-IOS : son message (11 pt, blanc 70 %) et la
 *  barre de `ProgressView` (4 pt, teintée de vert, colorSecondary). Seul « Annuler » le ferme. */
@Composable
private fun TelechargementIos(telechargement: DialogueCartes.Telechargement?, onAnnuler: () -> Unit) {
    DialogueMaterialIos(
        visible = telechargement != null,
        titre = ZoneHorsLigne.TITRE_TELECHARGEMENT,
        onFond = {},
        boutons = { BoutonDialogueIos(ZoneHorsLigne.ANNULER, CouleursGeoMys.blanc70, onAnnuler) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x5C787880), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(telechargement?.avancement ?: 0f).height(4.dp).background(CouleursGeoMys.vert, RoundedCornerShape(2.dp)))
            }
            Text(telechargement?.message.orEmpty(), color = CouleursGeoMys.blanc70, fontSize = 11.sp)
        }
    }
}
