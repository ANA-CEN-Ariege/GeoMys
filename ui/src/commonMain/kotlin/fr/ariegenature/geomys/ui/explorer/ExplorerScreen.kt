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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/ExplorerFragment.kt,
// res/layout/fragment_explorer.xml, bottom_sheet_obs_explorer.xml, item_obs_explorer.xml). Allure iOS de
// GeoMys-IOS @ 5fda84c (UI/ExplorerView.swift, TaxonMarker de UI/Map/MapView.swift).

package fr.ariegenature.geomys.ui.explorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.ariegenature.geomys.explorer.Explorer
import fr.ariegenature.geomys.explorer.GroupeObs
import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.ui.carte.AttributionFond
import fr.ariegenature.geomys.ui.carte.BoutonBoussole
import fr.ariegenature.geomys.ui.carte.BoutonCarte
import fr.ariegenature.geomys.ui.carte.CarteGeoMys
import fr.ariegenature.geomys.ui.carte.DialogueChoixFond
import fr.ariegenature.geomys.ui.carte.PointPosition
import fr.ariegenature.geomys.ui.carte.StyleBoutonCarte
import fr.ariegenature.geomys.ui.carte.animerCamera
import fr.ariegenature.geomys.ui.carte.enPosition
import fr.ariegenature.geomys.ui.carte.rememberCapAppareil
import fr.ariegenature.geomys.ui.carte.rememberStyleFond
import fr.ariegenature.geomys.ui.composants.BalayageRetourIosDesactive
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.IndicateurActivite
import fr.ariegenature.geomys.ui.composants.RetourSysteme
import fr.ariegenature.geomys.ui.composants.SeparateurIos
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.rememberDemandeLocalisation
import fr.ariegenature.geomys.ui.occtax.couleur
import fr.ariegenature.geomys.ui.occtax.icone
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_add
import fr.ariegenature.geomys.ui.ressources.ic_eye
import fr.ariegenature.geomys.ui.ressources.ic_location_on
import fr.ariegenature.geomys.ui.ressources.ic_map
import fr.ariegenature.geomys.ui.ressources.ic_remove
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.MapOverlayScope

/** `bandeau_dark` : noir à 65 %, coins de 20 (compteur, messages). */
private val FOND_SOMBRE = Color(0xA6000000)

/** `bandeau_filtres_translucide` : noir à 25 %, coins de 20, sous la barre des groupes. */
private val FOND_FILTRES = Color(0x40000000)

/** Fond d'une feuille du mode sombre d'iOS (systemBackground élevé). */
private val FOND_FEUILLE_IOS = Color(0xFF1C1C1E)

/** `.secondary` d'iOS en mode sombre. */
private val SECONDAIRE_IOS = Color(0x99EBEBF5)

/** Côté d'un marqueur, en dp sous Android, en points sous iOS. */
private const val TAILLE_MARQUEUR = 42f

/** Le marqueur d'observations sans groupe connu (taxon absent du téléphone) : gris 600 de Material,
 *  assez sombre pour l'icône et les chiffres blancs. */
private val COULEUR_SANS_GROUPE = Color(0xFF757575)

@Composable
fun ExplorerScreen(vm: ExplorerViewModel, onAccueil: () -> Unit) {
    // Comme onResume : la position est demandée (et l'autorisation, si besoin) à chaque retour.
    val demandeLocalisation = rememberDemandeLocalisation { vm.reprendre() }
    // Sous iOS, le balayage depuis le bord ne quitte pas la carte : la maison du bandeau ramène à l'accueil.
    BalayageRetourIosDesactive()
    LifecycleResumeEffect(vm) {
        demandeLocalisation()
        onPauseOrDispose { vm.suspendre() }
    }
    FondGeoMys {
        CarteExplorer(vm)
        Haut(vm, onAccueil)
    }
    ListeObservations(vm)
    DialogueChoixFond(vm.choixFondVisible, vm.fond, vm::choisirFond, vm::fermerChoixFond)
}

/** L'emprise visible de la carte ; nulle tant que la carte n'a pas de taille. */
private fun MapState.emprise(): Emprise? = getVisibleBounds()?.let { Emprise(sud = it.south, ouest = it.west, nord = it.north, est = it.east) }

@Composable
private fun BoxScope.CarteExplorer(vm: ExplorerViewModel) {
    val portee = rememberCoroutineScope()
    val (centre, zoomDepart) = vm.depart
    val etat = rememberMapState(baseStyle = rememberStyleFond(vm.fond), initialCameraPosition = CameraPosition(center = centre.enPosition(), zoom = zoomDepart)) {}
    // Le premier cadrage charge tout de suite ; chaque mouvement ensuite, après le délai.
    LaunchedEffect(etat) {
        etat.awaitViewport()
        etat.emprise()?.let(vm::charger)
        snapshotFlow { etat.cameraPosition }.drop(1).collect { etat.emprise()?.let(vm::carteBougee) }
    }
    val position by vm.tracker.position.collectAsState()
    // La carte partait du centre de la France : la première position reçue la recentre, au zoom 16.
    LaunchedEffect(position != null) {
        val p = position ?: return@LaunchedEffect
        if (vm.centreeSurPosition) return@LaunchedEffect
        vm.marquerCentree()
        etat.animerCamera(CameraUpdate(center = LatLon(p.latitude, p.longitude).enPosition(), zoom = Explorer.ZOOM_PREMIERE_POSITION))
    }
    CarteGeoMys(etat, Modifier.fillMaxSize()) {
        vm.groupes.forEach { groupe -> key(groupe.cle) { MarqueurGroupe(groupe) { vm.ouvrir(groupe) } } }
        position?.let { PointPosition(LatLon(it.latitude, it.longitude)) }
    }

    val zoomer = { pas: Int -> portee.launch { etat.animerCamera(CameraUpdate(zoom = etat.cameraPosition.zoom + pas)) } }
    val boutonsZoom: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoutonCarte(Res.drawable.ic_add, "Zoom avant", StyleBoutonCarte.CONTROLE_SAISIE, { zoomer(1) })
            BoutonCarte(Res.drawable.ic_remove, "Zoom arrière", StyleBoutonCarte.CONTROLE_SAISIE, { zoomer(-1) })
        }
    }
    // De haut en bas : centrer, boussole, fond. La boussole montre où est le nord, d'après le cap de
    // l'appareil, sans faire tourner la carte ; elle ne réagit pas au toucher.
    val cap = rememberCapAppareil()
    val controles: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoutonCarte(Res.drawable.ic_location_on, "Centrer sur ma position", StyleBoutonCarte.CONTROLE, {
                vm.positionPourCentrer()?.let { p -> portee.launch { etat.animerCamera(CameraUpdate(center = p.enPosition())) } }
            }, teinteIos = CouleursGeoMys.primaire)
            BoutonBoussole(-(cap ?: 0.0), onClick = {})
            BoutonCarte(Res.drawable.ic_map, "Changer fond", StyleBoutonCarte.CONTROLE, vm::montrerChoixFond)
        }
    }
    if (estIos) {
        // Une seule pastille, au-dessus de la rangée des contrôles.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PastilleIos(vm)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.padding(start = 12.dp)) { boutonsZoom() }
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) { AttributionFond(vm.fond, Modifier.padding(horizontal = 4.dp), lignesMax = 2) }
                Box(Modifier.padding(end = 12.dp)) { controles() }
            }
        }
    } else {
        val bas = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        Box(Modifier.align(Alignment.BottomStart).then(bas).padding(start = 12.dp, bottom = 12.dp)) { boutonsZoom() }
        Box(Modifier.align(Alignment.BottomEnd).then(bas).padding(12.dp)) { controles() }
        // La mention des données, sous le compteur, entre les boutons.
        AttributionFond(vm.fond, Modifier.align(Alignment.BottomCenter).then(bas).padding(horizontal = 60.dp))
        PastillesAndroid(vm, bas)
    }
}

/** Un marqueur : un disque de la couleur du groupe de la première observation, l'effectif en blanc gras
 *  s'il y en a plusieurs, sinon l'icône du groupe en blanc (56 % du disque). Sans groupe connu : disque
 *  gris et œil, icône neutre.
 *  - Android : `creerIconeMarqueur` (disque de 42 dp moins 2 px, chiffres de 21, 17 puis 13 dp) ;
 *  - iOS : `TaxonMarker` (disque plein de 42 pt, chiffres de 17, 13 puis 11 pt selon leur nombre). */
@Composable
private fun MapOverlayScope.MarqueurGroupe(groupe: GroupeObs, onClick: () -> Unit) {
    val ios = estIos
    val taxon = groupe.taxon
    val nombre = groupe.observations.size
    val densite = LocalDensity.current
    val marge = if (ios) 0.dp else with(densite) { 2f.toDp() }
    Box(
        Modifier
            .placedAt(LatLon(groupe.latitude, groupe.longitude).enPosition(), Alignment.Center)
            .size(TAILLE_MARQUEUR.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(marge)
            .background(taxon?.couleur ?: COULEUR_SANS_GROUPE, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (nombre > 1) {
            val chiffres = nombre.toString().length
            val rapport = if (ios) {
                when {
                    chiffres <= 2 -> 0.40f
                    chiffres == 3 -> 0.32f
                    else -> 0.26f
                }
            } else {
                // Le rayon, puis 82 % et 62 % du rayon.
                when {
                    nombre < 10 -> 0.5f
                    nombre < 100 -> 0.41f
                    else -> 0.31f
                }
            }
            // Dessinés dans une image : les chiffres ne suivent pas la taille de texte du système.
            val taille = with(densite) { (TAILLE_MARQUEUR * rapport).dp.toSp() }
            Text(nombre.toString(), color = Color.White, fontSize = taille, fontWeight = FontWeight.Bold, maxLines = 1)
        } else {
            Image(
                painterResource(taxon?.icone ?: Res.drawable.ic_eye),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size((TAILLE_MARQUEUR * 0.56f).dp),
            )
        }
    }
}

/** Le bandeau de navigation, puis la barre des groupes, flottante et centrée. */
@Composable
private fun Haut(vm: ExplorerViewModel, onAccueil: () -> Unit) {
    if (estIos) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BandeauNavigation(Explorer.TITRE, onAccueil)
            BarreGroupes(vm, Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp))
        }
    } else {
        val haut = Modifier.windowInsetsPadding(WindowInsets.statusBars)
        BandeauNavigation(Explorer.TITRE, onAccueil, haut.padding(start = 12.dp, top = 14.dp))
        Box(haut.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 96.dp), contentAlignment = Alignment.TopCenter) {
            BarreGroupes(vm, Modifier)
        }
    }
}

/** Les dix groupes ; toucher le groupe choisi les remontre tous.
 *  - Android : MaterialButton sans texte (88 × 48 dp au moins, icône de 24 dp, coins de 8 dp) ;
 *  - iOS : boutons de 36 pt espacés de 8, icône de 22 pt. */
@Composable
private fun BarreGroupes(vm: ExplorerViewModel, modifier: Modifier) {
    val ios = estIos
    Row(
        modifier
            .background(FOND_FILTRES, RoundedCornerShape(20.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (ios) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Explorer.GROUPES.forEach { taxon ->
            val choisi = vm.groupeFiltre == taxon
            val toucher = if (ios) {
                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.basculerFiltre(taxon) }.size(36.dp)
            } else {
                Modifier.clickable { vm.basculerFiltre(taxon) }.defaultMinSize(minWidth = 88.dp, minHeight = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp)
            }
            Box(
                Modifier
                    .background(if (choisi) taxon.couleur else Color.Transparent, RoundedCornerShape(8.dp))
                    .then(toucher)
                    .semantics { contentDescription = Explorer.descriptionGroupe(taxon) },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(taxon.icone),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(if (ios) 22.dp else 24.dp),
                )
            }
        }
    }
}

/** Android : le compteur en bas au centre, à 12 dp ; le chargement, ou le message (non configuré, zone
 *  trop large, erreur), à 32 dp. */
@Composable
private fun BoxScope.PastillesAndroid(vm: ExplorerViewModel, bas: Modifier) {
    val message = vm.message
    if (vm.compteurVisible && message == null) {
        Text(
            vm.compteur,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(bas)
                .padding(12.dp)
                .background(FOND_SOMBRE, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    val pastille = Modifier
        .align(Alignment.BottomCenter)
        .then(bas)
        .padding(bottom = 32.dp)
        .background(FOND_SOMBRE, RoundedCornerShape(20.dp))
        .padding(horizontal = 16.dp, vertical = 10.dp)
    if (vm.chargement) {
        Row(pastille, verticalAlignment = Alignment.CenterVertically) {
            IndicateurActivite(Color.White, tailleAndroid = 18f)
            Spacer(Modifier.width(10.dp))
            Text(Explorer.CHARGEMENT, color = Color.White, fontSize = 13.sp)
        }
    } else if (message != null) {
        Text(message, color = Color.White, fontSize = 13.sp, modifier = pastille)
    }
}

/** iOS : une pastille, l'indicateur en tête pendant le chargement. */
@Composable
private fun PastilleIos(vm: ExplorerViewModel) {
    val texte = when {
        vm.chargement -> Explorer.CHARGEMENT
        vm.message != null -> vm.message
        vm.compteurVisible -> vm.compteur
        else -> null
    } ?: return
    Row(
        Modifier
            .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            .background(FOND_SOMBRE, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.chargement) IndicateurActivite(Color.White, petitIos = true)
        Text(texte, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** La liste d'une cellule, en feuille du bas : « 3 obs. (12 mois) », puis chaque observation, la plus
 *  récente d'abord. Toucher le voile ou Retour la ferme. */
@Composable
private fun ListeObservations(vm: ExplorerViewModel) {
    val groupe = vm.groupeOuvert
    val ios = estIos
    if (groupe != null) RetourSysteme(onRetour = vm::fermerListe)
    AnimatedVisibility(groupe != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (ios) 0.4f else 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = vm::fermerListe),
        )
    }
    // La feuille garde sa liste pendant qu'elle redescend.
    val derniere = remember { arrayOfNulls<GroupeObs>(1) }
    if (groupe != null) derniere[0] = groupe
    val cellule = groupe ?: derniere[0]
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val hauteur = if (ios) maxHeight / 2 else maxHeight - maxWidth * 9 / 16
        AnimatedVisibility(groupe != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            if (cellule != null) FeuilleObservations(cellule, vm.dureeJours, hauteur)
        }
    }
}

/** - Android : BottomSheetDialog de Material 3 (coins de 28 dp, surfaceContainerLow), à la hauteur de la
 *    liste, au plus celle que lui donne BottomSheetBehavior (l'écran moins 9/16 de sa largeur) ;
 *  - iOS : feuille à mi-hauteur (détente `.medium`) avec sa poignée, liste simple séparée de filets. */
@Composable
private fun FeuilleObservations(cellule: GroupeObs, jours: Int, hauteur: Dp) {
    val liste = Explorer.liste(cellule)
    val titre = Explorer.compteur(liste.size, jours)
    val bloque = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    if (estIos) {
        Column(Modifier.fillMaxWidth().height(hauteur).background(FOND_FEUILLE_IOS, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).then(bloque)) {
            Box(Modifier.fillMaxWidth().padding(top = 5.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 5.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.5.dp)))
            }
            Text(
                titre,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars)) {
                liste.forEachIndexed { i, obs ->
                    if (i > 0) SeparateurIos(Modifier.padding(start = 16.dp))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(Explorer.titre(obs), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text(Explorer.detail(obs), fontSize = 13.sp, color = SECONDAIRE_IOS)
                    }
                }
            }
        }
    } else {
        val couleurs = MaterialTheme.colorScheme
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = hauteur)
                .background(couleurs.surfaceContainerLow, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .then(bloque)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Text(
                titre,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = couleurs.onSurface,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            )
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                liste.forEach { obs ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(Explorer.titre(obs), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = couleurs.onSurface)
                        Text(Explorer.detail(obs), fontSize = 13.sp, color = couleurs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}
