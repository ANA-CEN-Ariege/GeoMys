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

// Carte OccHab : fragment_occhab_carte.xml, OccHabCarteFragment, EditeurGeometrie, StationsServeurOverlay,
// StationsSessionOverlay et IconesEditionCarte de GeoMys-Android @ cdd5196 sous Android ; OccHabCarteView et
// OccHabMapView de GeoMys-IOS sous iOS.

package fr.ariegenature.geomys.ui.occhab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.occhab.CarteOccHab
import fr.ariegenature.geomys.occhab.GeometrieEdition
import fr.ariegenature.geomys.occhab.ModeGeometrie
import fr.ariegenature.geomys.occtax.CadrageSortie
import fr.ariegenature.geomys.ui.carte.ANCRE_GOUTTE_IOS
import fr.ariegenature.geomys.ui.carte.AttributionFond
import fr.ariegenature.geomys.ui.carte.BoutonBoussole
import fr.ariegenature.geomys.ui.carte.BoutonCarte
import fr.ariegenature.geomys.ui.carte.CarteGeoMys
import fr.ariegenature.geomys.ui.carte.CerclePrecision
import fr.ariegenature.geomys.ui.carte.DialogueChoixFond
import fr.ariegenature.geomys.ui.carte.GoutteIos
import fr.ariegenature.geomys.ui.carte.MarqueurDeplacable
import fr.ariegenature.geomys.ui.carte.PointPosition
import fr.ariegenature.geomys.ui.carte.SommetDeplacable
import fr.ariegenature.geomys.ui.carte.StyleBoutonCarte
import fr.ariegenature.geomys.ui.carte.SuiviCap
import fr.ariegenature.geomys.ui.carte.animerCamera
import fr.ariegenature.geomys.ui.carte.cadrer
import fr.ariegenature.geomys.ui.carte.enPosition
import fr.ariegenature.geomys.ui.carte.rememberStyleFond
import fr.ariegenature.geomys.ui.composants.AlerteEchecEcriture
import fr.ariegenature.geomys.ui.composants.BalayageRetourIosDesactive
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonPlein
import fr.ariegenature.geomys.ui.composants.BoutonVert
import fr.ariegenature.geomys.ui.composants.DialogueActions
import fr.ariegenature.geomys.ui.composants.DialogueAlerte
import fr.ariegenature.geomys.ui.composants.DialogueProgression
import fr.ariegenature.geomys.ui.composants.GraisseSymbole
import fr.ariegenature.geomys.ui.composants.HAUTEUR_BANDEAU_IOS
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.afficher
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.occtax.BLEU_APPUYE
import fr.ariegenature.geomys.ui.occtax.BoutonRondAndroid
import fr.ariegenature.geomys.ui.occtax.BoutonRondIos
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.coche
import fr.ariegenature.geomys.ui.ressources.ic_add
import fr.ariegenature.geomys.ui.ressources.ic_geom_polygon
import fr.ariegenature.geomys.ui.ressources.ic_info
import fr.ariegenature.geomys.ui.ressources.ic_location_on
import fr.ariegenature.geomys.ui.ressources.ic_map
import fr.ariegenature.geomys.ui.ressources.ic_pin_drop
import fr.ariegenature.geomys.ui.ressources.ic_remove
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import kotlin.math.hypot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource

// Couleurs des overlays de GeoMys-Android (StationsSessionOverlay, StationsServeurOverlay, EditeurGeometrie),
// que GeoMys-IOS reprend pour ses stations (OccHabMapView).
private val ROUGE = Color(0xFFD32F2F)
private val ROUGE_SURFACE = Color(0x33D32F2F)
private val VIOLET = Color(0xFF8E24AA)
private val VIOLET_SURFACE = Color(0x268E24AA)
private val BLEU_SURFACE = Color(0x552196F3)
private val BLEU_CONTOUR = Color(0xFF1976D2)

/** `bandeau_dark` : noir 65 % des bandeaux d'instructions. */
private val FOND_BANDEAU = Color(0xA6000000)

@Composable
fun CarteOccHabScreen(vm: CarteOccHabViewModel, onAccueil: () -> Unit, onStation: () -> Unit) {
    LifecycleResumeEffect(vm) {
        vm.reprendre()
        onPauseOrDispose { vm.suspendre() }
    }
    // GeoMys-IOS masque le bouton de retour : pas de balayage, la carte garde ses gestes.
    if (estIos) BalayageRetourIosDesactive()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EcranCarte(vm, onAccueil, onStation)
        Dialogues(vm)
    }
}

@Composable
private fun EcranCarte(vm: CarteOccHabViewModel, onAccueil: () -> Unit, onStation: () -> Unit) {
    val ios = estIos
    val toasts = LocalToasts.current
    val portee = rememberCoroutineScope()
    // À chaque affichage (onViewCreated) : mode, géométrie de la station, stations, cadrage.
    val ouverture = remember { vm.ouverture() }
    val etat = Carte(vm, ouverture)

    LaunchedEffect(vm, etat) {
        vm.evenements.collect { evenement ->
            when (evenement) {
                is EvenementCarteOccHab.Message -> toasts.afficher(evenement.texte, evenement.longue)
                is EvenementCarteOccHab.Camera -> appliquer(etat, evenement.ordre)
                EvenementCarteOccHab.OuvrirStation -> onStation()
                EvenementCarteOccHab.Accueil -> onAccueil()
            }
        }
    }

    val densite = LocalDensity.current
    val haut = Modifier.windowInsetsPadding(if (ios) WindowInsets.safeDrawing else WindowInsets.statusBars)
    val bas = Modifier.windowInsetsPadding(if (ios) WindowInsets.safeDrawing else WindowInsets.navigationBars)
    Box(Modifier.fillMaxSize()) {
        // ─── Haut : « 🏠 › OccHab », instructions, coche « Terminer » ───
        if (ios) {
            Column(haut.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BandeauNavigation(CarteOccHab.LIBELLE, onAccueil)
                Text(
                    vm.bandeau,
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            BoutonIos(vm::terminer, Modifier.align(Alignment.TopEnd).then(haut).padding(end = 12.dp).height(HAUTEUR_BANDEAU_IOS), opaciteAppui = 0.6f) {
                SymboleSf("checkmark.circle.fill", 30f, listOf(Color.White, CouleursGeoMys.vert), description = "Terminer la saisie")
            }
        } else {
            BandeauNavigation(CarteOccHab.LIBELLE, onAccueil, haut.padding(12.dp))
            Box(haut.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    vm.bandeau,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.background(FOND_BANDEAU, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            // Zone de 48 dp (gants, froid), coche de 32 dp.
            Image(
                painterResource(Res.drawable.coche),
                contentDescription = "Terminer la saisie",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .then(haut)
                    .padding(8.dp)
                    .size(48.dp)
                    .clickable(onClick = vm::terminer)
                    .padding(8.dp),
            )
        }

        // ─── Gauche, au milieu : point ou polygone (création seulement) ───
        if (vm.enCreation) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BoutonMode(ModeGeometrie.POINT, vm, "Saisir un point")
                BoutonMode(ModeGeometrie.POLYGONE, vm, "Saisir un polygone")
            }
        }

        // ─── Bas : zoom à gauche ; « i », centrer, boussole, fond à droite ; panneau ───
        val zoom: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(if (ios) 8.dp else 12.dp)) {
                BoutonCarte(Res.drawable.ic_add, "Zoom avant", StyleBoutonCarte.CONTROLE_SAISIE, {
                    portee.launch { etat.animerCamera(CameraUpdate(zoom = etat.cameraPosition.zoom + 1)) }
                })
                BoutonCarte(Res.drawable.ic_remove, "Zoom arrière", StyleBoutonCarte.CONTROLE_SAISIE, {
                    portee.launch { etat.animerCamera(CameraUpdate(zoom = etat.cameraPosition.zoom - 1)) }
                })
            }
        }
        val controles: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(if (ios) 8.dp else 12.dp)) {
                // Le « i » n'agit que sur une station sélectionnée : il n'est là qu'en création.
                if (vm.enCreation) BoutonCarte(Res.drawable.ic_info, "Détails de la station", StyleBoutonCarte.CONTROLE_SAISIE, vm::ouvrirInfos)
                BoutonCarte(
                    Res.drawable.ic_location_on,
                    "Centrer",
                    StyleBoutonCarte.CONTROLE_SAISIE,
                    onClick = { vm.centrer()?.let { p -> portee.launch { etat.animerCamera(CameraUpdate(center = p.enPosition())) } } },
                    teinteIos = CouleursGeoMys.primaire,
                )
                BoutonBoussole(-etat.cameraPosition.bearing, vm.carteSuitBoussole, vm::basculerBoussole)
                BoutonCarte(Res.drawable.ic_map, "Changer fond", StyleBoutonCarte.CONTROLE, vm::montrerChoixFond)
            }
        }
        if (ios) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                    zoom()
                    Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) { AttributionFond(vm.fond, Modifier.padding(horizontal = 4.dp), lignesMax = 2) }
                    controles()
                }
                PanneauIos(vm)
            }
        } else {
            Box(Modifier.align(Alignment.BottomStart).then(bas).padding(start = 12.dp, bottom = 100.dp)) { zoom() }
            Box(Modifier.align(Alignment.BottomEnd).then(bas).padding(end = 12.dp, bottom = 100.dp)) { controles() }
            // La mention des données, au-dessus du panneau, entre les boutons.
            var hautPanneau by remember { mutableStateOf(0.dp) }
            AttributionFond(vm.fond, Modifier.align(Alignment.BottomCenter).padding(bottom = hautPanneau).padding(horizontal = 60.dp))
            PanneauAndroid(vm, Modifier.align(Alignment.BottomCenter).onSizeChanged { hautPanneau = with(densite) { it.height.toDp() } }.then(bas))
        }
    }
}

/** Applique un ordre de caméra. */
private suspend fun appliquer(etat: MapState, ordre: CameraOccHab) {
    when (ordre) {
        is CameraOccHab.SurEmprise -> etat.cadrer(CadrageSortie.Emprise(ordre.emprise.sud, ordre.emprise.ouest, ordre.emprise.nord, ordre.emprise.est))
        is CameraOccHab.Centre -> etat.animerCamera(
            if (ordre.zoom != null) CameraUpdate(center = ordre.centre.enPosition(), zoom = ordre.zoom) else CameraUpdate(center = ordre.centre.enPosition()),
        )
    }
}

/** La carte : stations du serveur (violettes), stations locales (rouges), géométrie éditée,
 *  position. Rend son état, que les commandes de l'écran pilotent. */
@Composable
private fun Carte(vm: CarteOccHabViewModel, ouverture: CameraOccHab): MapState {
    val ios = estIos
    val densite = LocalDensity.current
    val px = { n: Float -> with(densite) { n.toDp() } }
    val epingle = painterResource(Res.drawable.ic_pin_drop)
    val goutteRouge = remember { GoutteIos(ROUGE, trait = 2.dp) }
    val goutteViolette = remember { GoutteIos(VIOLET, trait = 2.dp) }
    val locales = vm.stationsLocales
    val violettes = vm.stationsViolettes
    val edition = vm.edition
    // Trait des stations : 4 px sous Android ; 1,5 pt sous iOS (OccHabMapView), le double pour la station éditée.
    val trait = if (ios) 1.5.dp else px(4f)

    val camera = when (ouverture) {
        is CameraOccHab.Centre -> CameraPosition(center = ouverture.centre.enPosition(), zoom = ouverture.zoom ?: 15.0)
        is CameraOccHab.SurEmprise -> CameraPosition(
            center = LatLon((ouverture.emprise.sud + ouverture.emprise.nord) / 2, (ouverture.emprise.ouest + ouverture.emprise.est) / 2).enPosition(),
            zoom = 15.0,
        )
    }
    val etat = rememberMapState(baseStyle = rememberStyleFond(vm.fond), initialCameraPosition = camera) {
        // Les stations du serveur, en violet, sous celles de la saisie.
        val surfacesServeur = rememberGeoJsonSource(GeoJsonData.JsonString(remember(violettes) { surfaces(violettes) }))
        val pointsServeur = rememberGeoJsonSource(GeoJsonData.JsonString(remember(violettes) { points(violettes) }))
        FillLayer(
            id = "occhab-serveur-surfaces",
            source = surfacesServeur,
            color = const(if (ios) VIOLET.copy(alpha = 0.15f) else VIOLET_SURFACE),
            onClick = { entites -> entites.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.content?.let { vm.toucherStationServeur(it) }; ClickResult.Consume },
        )
        LineLayer(id = "occhab-serveur-contours", source = surfacesServeur, color = const(VIOLET), width = const(trait))
        if (ios) {
            SymbolLayer(
                id = "occhab-serveur-points",
                source = pointsServeur,
                iconImage = image(goutteViolette, size = DpSize(40.dp, 40.dp)),
                iconAnchor = const(SymbolAnchor.Bottom),
                iconAllowOverlap = const(true),
                iconIgnorePlacement = const(true),
                onClick = { entites -> entites.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.content?.let { vm.toucherStationServeur(it) }; ClickResult.Consume },
            )
        } else {
            // `cercleSommet` : disque de 12 dp au liseré blanc de 2 dp.
            CircleLayer(
                id = "occhab-serveur-points",
                source = pointsServeur,
                radius = const(6.dp),
                color = const(VIOLET),
                strokeColor = const(Color.White),
                strokeWidth = const(2.dp),
                onClick = { entites -> entites.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.content?.let { vm.toucherStationServeur(it) }; ClickResult.Consume },
            )
        }

        // Les stations locales restant à envoyer, en rouge.
        val surfacesLocales = rememberGeoJsonSource(GeoJsonData.JsonString(remember(locales) { surfaces(locales) }))
        val pointsLocaux = rememberGeoJsonSource(GeoJsonData.JsonString(remember(locales) { points(locales) }))
        FillLayer(
            id = "occhab-locales-surfaces",
            source = surfacesLocales,
            color = const(if (ios) ROUGE.copy(alpha = 0.15f) else ROUGE_SURFACE),
            onClick = { entites -> entites.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.content?.let { vm.toucherStationLocale(it) }; ClickResult.Consume },
        )
        LineLayer(id = "occhab-locales-contours", source = surfacesLocales, color = const(ROUGE), width = const(trait))
        if (!ios) {
            // Chaque sommet d'une station rouge matérialisé par un petit cercle, non interactif.
            val sommetsLocaux = rememberGeoJsonSource(GeoJsonData.JsonString(remember(locales) { sommets(locales) }))
            CircleLayer(
                id = "occhab-locales-sommets",
                source = sommetsLocaux,
                radius = const(6.dp),
                color = const(ROUGE),
                strokeColor = const(Color.White),
                strokeWidth = const(2.dp),
            )
        }
        SymbolLayer(
            id = "occhab-locales-points",
            source = pointsLocaux,
            iconImage = if (ios) image(goutteRouge, size = DpSize(40.dp, 40.dp)) else image(epingle, size = DpSize(24.dp, 24.dp)),
            iconAnchor = const(SymbolAnchor.Bottom),
            iconAllowOverlap = const(true),
            iconIgnorePlacement = const(true),
            onClick = { entites -> entites.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.content?.let { vm.toucherStationLocale(it) }; ClickResult.Consume },
        )

        // La géométrie en cours : bleue sous Android, rouge au trait double sous iOS ; trous en creux.
        // Ses couches sont toujours là, vides hors tracé : ajoutées en cours de dessin, la surface
        // insérée sous la ligne au troisième sommet, elles restaient invisibles quand les touchers
        // s'enchaînaient (essai du 19 octobre).
        val forme = rememberGeoJsonSource(GeoJsonData.JsonString(remember(edition) { formeEditee(edition) }))
        FillLayer(
            id = "occhab-edition-surface",
            source = forme,
            color = const(if (ios) ROUGE.copy(alpha = 0.25f) else BLEU_SURFACE),
            onClick = { if (vm.toucherFormeEditee()) ClickResult.Consume else ClickResult.Pass },
        )
        LineLayer(id = "occhab-edition-contour", source = forme, color = const(if (ios) ROUGE else BLEU_CONTOUR), width = const(if (ios) 3.dp else px(4f)))
        val position by vm.tracker.position.collectAsState()
        position?.let { CerclePrecision(LatLon(it.latitude, it.longitude), it.precision.toDouble()) }
    }

    // La carte retrouvée au retour de l'écran de la station garde sa caméra : on la replace où
    // l'ouverture le veut, puis on cadre sur la géométrie éditée.
    LaunchedEffect(etat) {
        etat.setCameraPosition(camera)
        if (ouverture is CameraOccHab.SurEmprise) appliquer(etat, ouverture)
    }
    // Premier point GPS depuis l'affichage (`runOnFirstFix`).
    LaunchedEffect(etat) {
        val p = vm.tracker.position.filterNotNull().first()
        vm.premierePosition(LatLon(p.latitude, p.longitude))
    }
    SuiviCap(etat, vm.carteSuitBoussole)
    // La carte quittée (onPause) : centre et zoom, repris au retour.
    DisposableEffect(etat) {
        onDispose {
            val c = etat.cameraPosition
            vm.memoriserCadrage(LatLon(c.center.latitude, c.center.longitude), c.zoom)
        }
    }

    fun aimanter(p: LatLon): LatLon? {
        val ecran = etat.screenLocationFromPosition(p.enPosition()) ?: return null
        return aimantage(etat, ecran, vm.ciblesAimantage)
    }

    CarteGeoMys(
        etat,
        Modifier.fillMaxSize(),
        onClic = { p -> vm.toucherCarte(p, if (vm.dessinEnCours) aimanter(p) else null) },
        onAvantClic = { _, ecran -> vm.dessinEnCours && vm.toucherAvantCouches(aimantage(etat, ecran, vm.ciblesAimantage)) },
    ) {
        val position by vm.tracker.position.collectAsState()
        // Sous Android, le point de position est sous les marqueurs ; sous iOS, au-dessus (MapKit).
        if (!ios) position?.let { PointPosition(LatLon(it.latitude, it.longitude)) }
        val point = edition.point
        if (edition.mode == ModeGeometrie.POINT && point != null) {
            // Le point : déplacé par appui long ; un toucher est pris sans effet.
            MarqueurDeplacable(
                etat = etat,
                position = point,
                icone = if (ios) Res.drawable.ic_location_on else Res.drawable.ic_pin_drop,
                taille = if (ios) 40.dp else 24.dp,
                ancre = if (ios) ANCRE_GOUTTE_IOS else BiasAlignment(0f, 1f),
                teinte = if (ios) ROUGE else null,
                description = "Point de la station",
                onClic = {},
                onDebut = vm::debutGlisser,
                onDeplacement = vm::glisserPoint,
                onFin = { p -> vm.finGlisserPoint(aimanter(p) ?: p) },
            )
        }
        if (edition.mode == ModeGeometrie.POLYGONE) {
            edition.sommets.forEachIndexed { i, sommet ->
                SommetDeplacable(
                    etat,
                    sommet,
                    onDeplacement = { p -> vm.glisserSommet(i, p) },
                    onFin = { p -> vm.finGlisserSommet(i, aimanter(p) ?: p) },
                    onDebut = vm::debutGlisser,
                    onClic = {},
                    teinteIos = ROUGE,
                )
            }
            // Sommets des trous : mêmes gestes, sans aimantage.
            edition.trous.forEachIndexed { r, anneau ->
                anneau.forEachIndexed { i, sommet ->
                    SommetDeplacable(
                        etat,
                        sommet,
                        onDeplacement = { p -> vm.glisserSommetTrou(r, i, p) },
                        onFin = { p -> vm.finGlisserSommetTrou(r, i, p) },
                        onDebut = vm::debutGlisser,
                        onClic = {},
                        teinteIos = ROUGE,
                    )
                }
            }
            // Poignées « + » au milieu des arêtes (station sélectionnée).
            edition.poignees(vm.geometrieChargee).forEach { poignee ->
                PoigneeAjout(Modifier.placedAt(poignee.position.enPosition(), Alignment.Center)) {
                    vm.insererSommet(poignee.anneau, poignee.indexArete)
                }
            }
        }
        if (ios) position?.let { PointPosition(LatLon(it.latitude, it.longitude)) }
    }
    return etat
}

/** Le sommet voisin le plus proche de [ecran], à moins de 44 dp. Les cibles sont d'abord
 *  filtrées dans l'emprise des ± 44 dp autour du point : les projeter toutes à chaque geste
 *  coûterait cher avec des milliers de sommets du serveur. */
private fun aimantage(etat: MapState, ecran: DpOffset, cibles: List<LatLon>): LatLon? {
    if (cibles.isEmpty()) return null
    val s = CarteOccHab.SEUIL_AIMANTAGE_DP.dp
    val coins = listOf(DpOffset(ecran.x - s, ecran.y - s), DpOffset(ecran.x + s, ecran.y - s), DpOffset(ecran.x - s, ecran.y + s), DpOffset(ecran.x + s, ecran.y + s))
        .mapNotNull { etat.positionFromScreenLocation(it) }
    if (coins.size < 4) return null
    val sud = coins.minOf { it.latitude }
    val nord = coins.maxOf { it.latitude }
    val ouest = coins.minOf { it.longitude }
    val est = coins.maxOf { it.longitude }
    val proches = cibles.filter { it.latitude in sud..nord && it.longitude in ouest..est }
    val distances = proches.map { c ->
        etat.screenLocationFromPosition(c.enPosition())?.let { hypot((it.x - ecran.x).value, (it.y - ecran.y).value) } ?: Float.MAX_VALUE
    }
    return CarteOccHab.plusProche(distances)?.let { proches[it] }
}

/** Poignée « + » d'ajout de sommet.
 *  - Android : disque bleu de 18 dp au liseré blanc d'1 dp, `ic_add` blanc, dans une zone de 24 dp ;
 *  - iOS : le disque bleu de 18 pt et sa croix blanche dessinés dans 26 pt (OccHabMapView). */
@Composable
private fun PoigneeAjout(modifier: Modifier, onClick: () -> Unit) {
    val clic = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
    if (estIos) {
        Canvas(modifier.size(26.dp).then(clic)) {
            val centre = Offset(size.width / 2, size.height / 2)
            val rayon = 9.dp.toPx()
            drawCircle(BLEU_CONTOUR, rayon, centre)
            drawCircle(Color.White, rayon, centre, style = Stroke(1.dp.toPx()))
            val bras = 4.dp.toPx()
            val epaisseur = 2.dp.toPx()
            drawLine(Color.White, Offset(centre.x - bras, centre.y), Offset(centre.x + bras, centre.y), epaisseur)
            drawLine(Color.White, Offset(centre.x, centre.y - bras), Offset(centre.x, centre.y + bras), epaisseur)
        }
    } else {
        Box(modifier.size(24.dp).then(clic), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(18.dp).background(BLEU_CONTOUR, CircleShape).border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(Res.drawable.ic_add), contentDescription = "Ajouter un sommet", colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.padding(1.5.dp))
            }
        }
    }
}

/** Bouton de mode de géométrie : actif = disque bleu primaire, icône blanche ; inactif = disque
 *  blanc, icône bleue ; estompé à 40 % quand la géométrie est engagée (le type se choisit avant
 *  de dessiner).
 *  - Android : `btn_map_bg` teinté, `ic_pin_drop` / `ic_geom_polygon` ;
 *  - iOS : symboles `mappin` / `triangle` de 18 pt demi-gras. */
@Composable
private fun BoutonMode(mode: ModeGeometrie, vm: CarteOccHabViewModel, description: String) {
    val actif = vm.edition.mode == mode
    val verrouille = vm.geometrieEngagee
    val fond = if (actif) CouleursGeoMys.primaire else Color.White
    val teinte = if (actif) Color.White else CouleursGeoMys.primaire
    if (estIos) {
        BoutonIos({ vm.changerMode(mode) }, actif = !verrouille, opaciteAppui = 0.6f, opaciteInactif = 0.4f) {
            Box(Modifier.size(44.dp).background(fond, CircleShape), contentAlignment = Alignment.Center) {
                SymboleSf(if (mode == ModeGeometrie.POINT) "mappin" else "triangle", 18f, listOf(teinte), graisse = GraisseSymbole.DEMI_GRASSE, description = description)
            }
        }
    } else {
        Box(
            Modifier
                .size(44.dp)
                .alpha(if (verrouille) 0.4f else 1f)
                .background(fond, CircleShape)
                .clickable(remember { MutableInteractionSource() }, indication = null, enabled = !verrouille, role = Role.Button) { vm.changerMode(mode) },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(if (mode == ModeGeometrie.POINT) Res.drawable.ic_pin_drop else Res.drawable.ic_geom_polygon),
                contentDescription = description,
                colorFilter = ColorFilter.tint(teinte),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Le bas de la carte (Android), sans bandeau (demandes du 20 octobre ; GeoMys-Android y avait la bande
 *  `panel_bg`). En consultation, le « + » de la saisie multi-taxons (disque bleu de 48 dp) : une nouvelle
 *  station. En création ou en édition d'une station, « Annuler » et « Valider » seuls, posés sur la
 *  carte. */
@Composable
private fun PanneauAndroid(vm: CarteOccHabViewModel, modifier: Modifier) {
    if (!vm.enCreation) {
        Box(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            BoutonRondAndroid(Res.drawable.ic_add, CouleursGeoMys.primaire, BLEU_APPUYE, 48.dp, "Ajouter une station", vm::ajouterStation)
        }
        return
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoutonPlein("Annuler", vm::annuler, Modifier.weight(1f), actif = vm.peutAnnuler)
        Spacer(Modifier.size(8.dp))
        BoutonVert("Valider", vm::valider, Modifier.weight(1f).alpha(if (vm.peutValider) 1f else 0.5f), actif = vm.peutValider)
    }
}

/** Le bas de la carte (iOS), sans bandeau (demandes du 20 octobre ; GeoMys-IOS y avait son dégradé). En
 *  consultation, le « + » de la saisie multi-taxons (disque bleu ombré de 56 pt) : une nouvelle station.
 *  En création ou en édition d'une station, « Annuler » et « Valider » seuls, posés sur la carte : les
 *  pilules bleue et verte de GeoMys-IOS, estompées quand elles sont inactives (« Annuler », un texte
 *  blanc sur le dégradé, serait illisible sur la carte). */
@Composable
private fun PanneauIos(vm: CarteOccHabViewModel) {
    if (!vm.enCreation) {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
            BoutonRondIos("plus", CouleursGeoMys.primaire, Color.White, "Ajouter une station", vm::ajouterStation)
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoutonPlein("Annuler", vm::annuler, Modifier.weight(1f), actif = vm.peutAnnuler)
        BoutonVert("Valider", vm::valider, Modifier.weight(1f), actif = vm.peutValider)
    }
}

@Composable
private fun Dialogues(vm: CarteOccHabViewModel) {
    DialogueDetailsStation(vm.details, vm::annulerDetails, vm::validerDetails)
    val d = vm.dialogue
    DialogueAlerte(
        visible = d is DialogueCarteOccHab.ModifierEnvoyee,
        titre = CarteOccHab.TITRE_MODIFIER_ENVOYEE,
        message = CarteOccHab.MESSAGE_MODIFIER_ENVOYEE,
        boutons = listOf(
            BoutonAlerte(CarteOccHab.MODIFIER) { (d as? DialogueCarteOccHab.ModifierEnvoyee)?.let { vm.modifierEnvoyee(it.station) } },
            BoutonAlerte("Annuler", annulation = true),
        ),
        onFermer = vm::fermerDialogue,
    )
    DialogueAlerte(
        visible = d is DialogueCarteOccHab.NonModifiable,
        titre = CarteOccHab.TITRE_NON_MODIFIABLE,
        message = (d as? DialogueCarteOccHab.NonModifiable)?.let { CarteOccHab.messageNonModifiable(it.station) }.orEmpty(),
        boutons = listOf(BoutonAlerte("Fermer")),
        onFermer = vm::fermerDialogue,
    )
    DialogueAlerte(
        visible = d is DialogueCarteOccHab.Import,
        titre = CarteOccHab.TITRE_IMPORT,
        message = (d as? DialogueCarteOccHab.Import)?.let { CarteOccHab.messageImport(it.station) }.orEmpty(),
        boutons = listOf(
            BoutonAlerte(CarteOccHab.MODIFIER) { (d as? DialogueCarteOccHab.Import)?.let { vm.importer(it.station) } },
            BoutonAlerte("Annuler", annulation = true),
        ),
        onFermer = vm::fermerDialogue,
    )
    DialogueAlerte(
        visible = d is DialogueCarteOccHab.CopieLocale,
        titre = CarteOccHab.TITRE_COPIE_LOCALE,
        message = (d as? DialogueCarteOccHab.CopieLocale)?.let { CarteOccHab.messageCopieLocale(it.copie, it.saisie.date, it.traceEnCours) }.orEmpty(),
        boutons = listOf(
            BoutonAlerte(CarteOccHab.OUVRIR_SAISIE) { (d as? DialogueCarteOccHab.CopieLocale)?.let { vm.ouvrirCopieLocale(it.saisie, it.copie) } },
            BoutonAlerte("Annuler", annulation = true),
        ),
        onFermer = vm::fermerDialogue,
    )
    val terminer = d as? DialogueCarteOccHab.Terminer
    DialogueActions(
        visible = terminer != null,
        titre = CarteOccHab.TITRE_TERMINER,
        actions = terminer?.options.orEmpty().map { option ->
            if (option == CarteOccHab.CONTINUER) BoutonAlerte(option, annulation = true) else BoutonAlerte(option) { vm.choisirFin(option, terminer!!.saisie) }
        },
        onFermer = vm::fermerDialogue,
    )
    DialogueProgression(vm.envoiEnCours, CarteOccHab.ENVOI_EN_COURS)
    val resultat = d as? DialogueCarteOccHab.ResultatEnvoi
    DialogueAlerte(
        visible = resultat != null,
        titre = resultat?.titre.orEmpty(),
        message = resultat?.message.orEmpty(),
        boutons = listOf(BoutonAlerte("OK", action = vm::quitterApresEnvoi)),
        onFermer = vm::fermerDialogue,
    )
    DialogueChoixFond(vm.choixFondVisible, vm.fond, vm::choisirFond, vm::fermerChoixFond)
    AlerteEchecEcriture(vm.echecEcriture != null, vm::fermerEchecEcriture, vm.echecEcriture.orEmpty())
}

// ─── GeoJSON des couches ───

/** Les polygones des [stations] (trous compris, en creux), chacun portant l'id de sa station. */
private fun surfaces(stations: List<OccHabStation>): String = collection(
    stations.mapNotNull { st ->
        val anneau = CarteOccHab.points(st).takeIf { st.geometryType == "Polygon" && it.size >= 2 } ?: return@mapNotNull null
        val trous = fr.ariegenature.geomys.util.GeoJsonCoords.parseAnneaux(st.geometryTrousJson)
        st.id to polygone(anneau, trous)
    },
)

/** Les stations ponctuelles. */
private fun points(stations: List<OccHabStation>): String = collection(
    stations.filter { it.geometryType != "Polygon" && (it.latitude != 0.0 || it.longitude != 0.0) }
        .map { st -> st.id to buildJsonObject { put("type", "Point"); put("coordinates", coordonnee(LatLon(st.latitude, st.longitude))) } },
)

/** Les sommets des polygones (cercles des stations rouges). */
private fun sommets(stations: List<OccHabStation>): String = collection(
    stations.filter { it.geometryType == "Polygon" }.flatMap { st ->
        CarteOccHab.points(st).takeIf { it.size >= 2 }.orEmpty().map { p ->
            st.id to buildJsonObject { put("type", "Point"); put("coordinates", coordonnee(p)) }
        }
    },
)

/** Le polygone édité : un polygone dès trois sommets (trous d'au moins trois sommets en creux),
 *  une ligne à deux, rien sinon (la surface ne dessine que les polygones). */
private fun formeEditee(edition: GeometrieEdition): String {
    val sommets = edition.sommets
    if (edition.mode != ModeGeometrie.POLYGONE || sommets.size < 2) return collection(emptyList())
    val geometrie = if (sommets.size >= 3) {
        polygone(sommets, edition.trous.filter { it.size >= 3 })
    } else {
        buildJsonObject {
            put("type", "LineString")
            put("coordinates", JsonArray(sommets.map(::coordonnee)))
        }
    }
    return collection(listOf("edition" to geometrie))
}

private fun polygone(anneau: List<LatLon>, trous: List<List<LatLon>>) = buildJsonObject {
    put("type", "Polygon")
    put("coordinates", JsonArray((listOf(anneau) + trous).map { r -> JsonArray((r + r.first()).map(::coordonnee)) }))
}

private fun collection(entites: List<Pair<String, kotlinx.serialization.json.JsonObject>>): String = buildJsonObject {
    put("type", "FeatureCollection")
    put("features", buildJsonArray {
        entites.forEach { (id, geometrie) ->
            add(buildJsonObject {
                put("type", "Feature")
                put("properties", buildJsonObject { put("id", id) })
                put("geometry", geometrie)
            })
        }
    })
}.toString()

private fun coordonnee(p: LatLon) = JsonArray(listOf(JsonPrimitive(p.longitude), JsonPrimitive(p.latitude)))
