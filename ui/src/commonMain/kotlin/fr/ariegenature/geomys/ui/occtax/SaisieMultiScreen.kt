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

// Carte de la saisie multi-taxons : fragment_trace.xml et TraceFragment de GeoMys-Android @ cdd5196 sous
// Android, TraceView de GeoMys-IOS (UI/TraceView.swift) sous iOS.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.occtax.CadrageReprise
import fr.ariegenature.geomys.occtax.CarteSaisie
import fr.ariegenature.geomys.occtax.FinSaisie
import fr.ariegenature.geomys.occtax.ModeGeom
import fr.ariegenature.geomys.occtax.ReleveForme
import fr.ariegenature.geomys.ui.carte.ANCRE_GOUTTE_IOS
import fr.ariegenature.geomys.ui.carte.AttributionFond
import fr.ariegenature.geomys.ui.carte.BoutonBoussole
import fr.ariegenature.geomys.ui.carte.BoutonCarte
import fr.ariegenature.geomys.ui.carte.CarteGeoMys
import fr.ariegenature.geomys.ui.carte.CerclePrecision
import fr.ariegenature.geomys.ui.carte.DialogueChoixFond
import fr.ariegenature.geomys.ui.carte.MarqueurDeplacable
import fr.ariegenature.geomys.ui.carte.PointPosition
import fr.ariegenature.geomys.ui.carte.SommetDeplacable
import fr.ariegenature.geomys.ui.carte.StyleBoutonCarte
import fr.ariegenature.geomys.ui.carte.SuiviCap
import fr.ariegenature.geomys.ui.carte.animerCamera
import fr.ariegenature.geomys.ui.carte.enPosition
import fr.ariegenature.geomys.ui.carte.rememberStyleFond
import fr.ariegenature.geomys.ui.composants.AlerteEchecEcriture
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.BoutonIos
import fr.ariegenature.geomys.ui.composants.BoutonMaterial
import fr.ariegenature.geomys.ui.composants.BoutonPlein
import fr.ariegenature.geomys.ui.composants.DialogueActions
import fr.ariegenature.geomys.ui.composants.DialogueAlerte
import fr.ariegenature.geomys.ui.composants.GraisseSymbole
import fr.ariegenature.geomys.ui.composants.HAUTEUR_BANDEAU_IOS
import fr.ariegenature.geomys.ui.composants.IndicateurActivite
import fr.ariegenature.geomys.ui.composants.LocalToasts
import fr.ariegenature.geomys.ui.composants.RetourSysteme
import fr.ariegenature.geomys.ui.composants.SymboleSf
import fr.ariegenature.geomys.ui.composants.afficher
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.fondEnRelief
import fr.ariegenature.geomys.ui.composants.rememberDemandeLocalisation
import fr.ariegenature.geomys.ui.composants.styleLibelleMaterial
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.coche
import fr.ariegenature.geomys.ui.ressources.ic_add
import fr.ariegenature.geomys.ui.ressources.ic_bird_marker
import fr.ariegenature.geomys.ui.ressources.ic_geom_line
import fr.ariegenature.geomys.ui.ressources.ic_geom_polygon
import fr.ariegenature.geomys.ui.ressources.ic_info
import fr.ariegenature.geomys.ui.ressources.ic_list
import fr.ariegenature.geomys.ui.ressources.ic_location_off
import fr.ariegenature.geomys.ui.ressources.ic_location_on
import fr.ariegenature.geomys.ui.ressources.ic_map
import fr.ariegenature.geomys.ui.ressources.ic_pin_drop
import fr.ariegenature.geomys.ui.ressources.ic_remove
import fr.ariegenature.geomys.ui.ressources.ic_walk
import fr.ariegenature.geomys.ui.ressources.oiseaux
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.util.formaterDecimal
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox

/** Tracés d'osmdroid sous Android : trace et relevés en bleu translucide, géométrie en cours en rouge. */
private val BLEU_TRACE = Color(0xCC2196F3)
private val BLEU_SURFACE = Color(0x402196F3)
private val ROUGE_SAISIE = Color(0xCCFF3333)
private val ROUGE_SURFACE = Color(0x55FF3333)

/** colorPrimaryVariant : disque bleu appuyé (`btn_circle_blue`). */
internal val BLEU_APPUYE = Color(0xFF1565C0)

/** `panel_bg` : bleu nuit opaque (accueil_bottom) des panneaux du bas de la carte. */
internal val FOND_PANNEAU = Color(0xFF0D264C)

/** `@android:color/darker_gray` : bouton « Annuler » du positionnement. */
private val GRIS_FONCE = Color(0xFFAAAAAA)

/** `Couleurs.iconeCarteInactive` de GeoMys-IOS : goutte de « Centrer » quand le suivi est coupé. */
internal val GRIS_INACTIF_IOS = Color(0xFF9E9E9E)

/** Disque blanc appuyé (#E0E0E0, comme `btn_circle_white`). */
private val GRIS_APPUYE = Color(0xFFE0E0E0)

/** Le gris des icônes Material (#757575) : le bonhomme éteint. */
private val GRIS_ICONE_ANDROID = Color(0xFF757575)

/** La carte de la saisie multi-taxons. [onOuvrirReleve] ouvre la saisie d'un relevé, [onOuvrirListe]
 *  la liste des observations, « i » le dialogue des détails communs de la saisie ; [onQuitter]
 *  revient à l'écran précédent. */
@Composable
fun SaisieMultiScreen(
    vm: SaisieMultiViewModel,
    onAccueil: () -> Unit,
    onOuvrirReleve: (EvenementSaisieMulti.OuvrirReleve) -> Unit,
    onOuvrirListe: () -> Unit,
    onQuitter: () -> Unit,
) {
    val toasts = LocalToasts.current
    LaunchedEffect(vm) {
        vm.evenements.collect { evenement ->
            when (evenement) {
                is EvenementSaisieMulti.Message -> toasts.afficher(evenement.texte)
                is EvenementSaisieMulti.OuvrirReleve -> onOuvrirReleve(evenement)
                EvenementSaisieMulti.OuvrirListe -> onOuvrirListe()
                EvenementSaisieMulti.Quitter -> onQuitter()
            }
        }
    }
    // Localisation demandée à chaque affichage (onViewCreated), puis suivi GPS.
    val demandeLocalisation = rememberDemandeLocalisation { vm.demarrerSuivi() }
    LaunchedEffect(Unit) { demandeLocalisation() }
    // Le retour système passe par « Terminer la saisie », pour libérer le GPS comme la coche.
    RetourSysteme { vm.demanderTerminer() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (vm.repriseChargee) EcranCarte(vm, onAccueil)

        val releve = vm.optionsReleve
        val (titreReleve, messageReleve) = releve?.let(CarteSaisie::optionsReleve) ?: ("" to "")
        DialogueAlerte(
            visible = releve != null,
            titre = titreReleve,
            message = messageReleve,
            boutons = listOf(BoutonAlerte("Éditer", action = vm::editerReleve), BoutonAlerte("Fermer", annulation = true)),
            onFermer = vm::fermerOptionsReleve,
        )
        DialogueActions(
            visible = vm.confirmerTerminer,
            titre = FinSaisie.TITRE,
            actions = FinSaisie.options(vm.peutEnvoyer).map { option ->
                when (option) {
                    FinSaisie.ENREGISTRER_QUITTER -> BoutonAlerte(option) { vm.terminer(envoyer = false) }
                    FinSaisie.ENREGISTRER_ENVOYER -> BoutonAlerte(option) { vm.terminer(envoyer = true) }
                    else -> BoutonAlerte(option, annulation = true)
                }
            },
            onFermer = vm::fermerTerminer,
        )
        DialogueChoixFond(vm.choixFondVisible, vm.fond, vm::choisirFond, vm::fermerChoixFond)
        val resultat = vm.resultatEnvoi
        DialogueAlerte(
            visible = resultat != null,
            titre = resultat?.titre.orEmpty(),
            message = resultat?.message.orEmpty(),
            boutons = listOf(BoutonAlerte("OK", action = vm::quitter)),
            onFermer = vm::fermerResultatEnvoi,
        )
        EcranDetailsReleve(vm.detailsCommuns, SaisieMultiViewModel.LIBELLE, onAccueil, vm::fermerDetailsCommuns, vm::validerDetailsCommuns)
        AlerteEchecEcriture(vm.echecFin, vm::fermerEchecFin, FinSaisie.CONSIGNE_ECHEC)
        // Échec de l'auto-save du brouillon (disque plein ?) : alerte BLOQUANTE — sans elle, la
        // saisie affichée ne vit qu'en mémoire.
        val echecBrouillon by vm.session.echecEcritureBrouillon.collectAsState()
        AlerteEchecEcriture(echecBrouillon, vm.session::acquitterEchecEcriture, FinSaisie.CONSIGNE_ECHEC_BROUILLON)
    }
}

/** La carte et ses commandes, une fois la sortie reprise chargée. */
@Composable
private fun EcranCarte(vm: SaisieMultiViewModel, onAccueil: () -> Unit) {
    val ios = estIos
    // À chaque affichage (setupMap), avant toute lecture de l'état qu'elle règle : où ouvrir la carte.
    val ouverture = remember { vm.ouverture() }
    val portee = rememberCoroutineScope()
    val etat = CarteMulti(vm, ouverture)

    val enCours by vm.tracker.estEnCours.collectAsState()
    val distance by vm.tracker.distanceTotale.collectAsState()
    val observations by vm.session.observations.collectAsState()
    val densite = LocalDensity.current
    val haut = if (ios) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier.windowInsetsPadding(WindowInsets.statusBars)
    val bas = if (ios) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier.windowInsetsPadding(WindowInsets.navigationBars)

    Box(Modifier.fillMaxSize()) {
        // ─── Haut : « 🏠 › Saisie multi-taxons », consigne du positionnement, coche « Terminer » ───
        if (ios) {
            Column(haut.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BandeauNavigation(SaisieMultiViewModel.LIBELLE, onAccueil)
                if (vm.modePositionnement) {
                    Text(
                        CarteSaisie.consignePositionnement(false, vm.modeGeom),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            // Masquée pendant le positionnement, pour ne pas la confondre avec la validation.
            if (!vm.modePositionnement) {
                BoutonIos(vm::demanderTerminer, Modifier.align(Alignment.TopEnd).then(haut).padding(end = 12.dp).height(HAUTEUR_BANDEAU_IOS), opaciteAppui = 0.6f) {
                    SymboleSf("checkmark.circle.fill", 30f, listOf(Color.White, CouleursGeoMys.vert), description = "Terminer la saisie")
                }
            }
        } else {
            BandeauNavigation(SaisieMultiViewModel.LIBELLE, onAccueil, haut.padding(12.dp))
            if (vm.modePositionnement) {
                Box(haut.fillMaxWidth().padding(top = 84.dp), contentAlignment = Alignment.Center) {
                    Text(
                        CarteSaisie.consignePositionnement(false, vm.modeGeom),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .background(Color(0xA6000000), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
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
                    .clickable(onClick = vm::demanderTerminer)
                    .padding(8.dp),
            )
        }

        // ─── Gauche, au milieu : point, ligne ou polygone (positionnement seulement) ───
        if (vm.modePositionnement) {
            Column(
                Modifier.align(Alignment.CenterStart).then(if (ios) haut else Modifier).padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (ios) 12.dp else 6.dp),
            ) {
                BoutonMode(ModeGeom.POINT, vm.modeGeom, Res.drawable.ic_pin_drop, "mappin.and.ellipse", "Saisir un point", vm::changerMode)
                BoutonMode(ModeGeom.LINE, vm.modeGeom, Res.drawable.ic_geom_line, "scribble", "Saisir une ligne", vm::changerMode)
                BoutonMode(ModeGeom.POLYGON, vm.modeGeom, Res.drawable.ic_geom_polygon, "hexagon", "Saisir un polygone", vm::changerMode)
            }
        }

        // ─── Bas droite : détails communs, centrer, boussole, fond ; bas gauche : zoom ───
        Column(
            Modifier.align(Alignment.BottomEnd).then(bas).padding(end = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoutonCarte(Res.drawable.ic_info, "Détails communs de la saisie", StyleBoutonCarte.CONTROLE_SAISIE, vm::ouvrirDetailsCommuns)
            BoutonCarte(
                if (vm.suivrePosition || ios) Res.drawable.ic_location_on else Res.drawable.ic_location_off,
                "Centrer",
                StyleBoutonCarte.CONTROLE_SAISIE,
                onClick = {
                    val cible = vm.centrer()
                    if (cible != null) portee.launch { etat.animerCamera(CameraUpdate(center = cible.enPosition())) }
                },
                teinteIos = if (vm.suivrePosition) CouleursGeoMys.primaire else GRIS_INACTIF_IOS,
            )
            Boussole(etat, vm::basculerBoussole)
            BoutonCarte(Res.drawable.ic_map, "Changer fond", StyleBoutonCarte.CONTROLE, vm::montrerChoixFond)
        }
        Column(
            Modifier.align(Alignment.BottomStart).then(bas).padding(start = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoutonCarte(Res.drawable.ic_add, "Zoom avant", StyleBoutonCarte.CONTROLE_SAISIE, {
                portee.launch { etat.animerCamera(CameraUpdate(zoom = etat.cameraPosition.zoom + 1)) }
            })
            BoutonCarte(Res.drawable.ic_remove, "Zoom arrière", StyleBoutonCarte.CONTROLE_SAISIE, {
                portee.launch { etat.animerCamera(CameraUpdate(zoom = etat.cameraPosition.zoom - 1)) }
            })
        }

        // ─── Bas : barre d'info du parcours, puis le panneau de commandes ou de validation ───
        // Sous Android, le panneau de commandes (transparent) chevauche la barre d'info, comme dans
        // fragment_trace.xml ; sous iOS, la barre devient la pastille au-dessus des boutons.
        val infoVisible = enCours
        if (!ios && infoVisible) BarreInfo(distance, observations.size, Modifier.align(Alignment.BottomCenter).then(bas))
        // La mention des données, au-dessus des commandes, entre les boutons.
        var hautPanneau by remember { mutableStateOf(0.dp) }
        AttributionFond(vm.fond, Modifier.align(Alignment.BottomCenter).padding(bottom = hautPanneau).padding(horizontal = 60.dp))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { hautPanneau = with(densite) { it.height.toDp() } }.then(bas)) {
            if (vm.modePositionnement) {
                PanneauValidation(vm) { vm.validerPosition(etat.cameraPosition.center.let { LatLon(it.latitude, it.longitude) }) }
            } else {
                PanneauCommandes(vm, enCours, infoVisible, distance, observations.size)
            }
        }

        // Voile d'envoi : l'envoi peut durer, rien ne doit laisser croire qu'on peut le relancer.
        if (vm.envoiEnCours) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                verticalArrangement = Arrangement.spacedBy(if (ios) 14.dp else 16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IndicateurActivite(Color.White, tailleAndroid = 48f)
                Text("Envoi vers GeoNature…", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

/** La carte : trace, relevés (marqueur déplaçable, ou forme aux sommets déplaçables), géométrie en
 *  cours de saisie, position. Rend son état, que les commandes de l'écran pilotent. */
@Composable
private fun CarteMulti(vm: SaisieMultiViewModel, ouverture: OuvertureCarte): MapState {
    val ios = estIos
    val densite = LocalDensity.current
    val px = { n: Float -> with(densite) { n.toDp() } }
    val observations by vm.session.observations.collectAsState()
    val formes = remember(observations) { CarteSaisie.relevesFormes(observations) }
    val points = remember(observations) { CarteSaisie.relevesPoints(observations) }
    // Sommet d'un relevé en cours de glisser : la forme suit le doigt avant d'être écrite.
    var formeGlissee by remember { mutableStateOf<Pair<String, List<LatLon>>?>(null) }
    fun sommetsDe(forme: ReleveForme) = formeGlissee?.takeIf { it.first == forme.cle }?.second ?: forme.sommets
    fun toucherForme(cle: String?): ClickResult {
        val obsReleve = observations.filter { CarteSaisie.cleReleve(it) == cle }
        if (obsReleve.isEmpty()) return ClickResult.Pass
        vm.toucherReleve(obsReleve)
        return ClickResult.Consume
    }

    val camera = when (ouverture) {
        is OuvertureCarte.Centre -> CameraPosition(center = ouverture.centre.enPosition(), zoom = ouverture.zoom)
        is OuvertureCarte.Reprise -> when (val c = ouverture.cadrage) {
            is CadrageReprise.Point -> CameraPosition(center = c.centre.enPosition(), zoom = 18.0)
            is CadrageReprise.Emprise -> CameraPosition(
                center = LatLon((c.sud + c.nord) / 2, (c.ouest + c.est) / 2).enPosition(),
                zoom = ouverture.zoom,
            )
        }
    }
    val etat = rememberMapState(baseStyle = rememberStyleFond(vm.fond), initialCameraPosition = camera) {
        // Ordre d'osmdroid : formes des relevés tout en bas, puis la trace, puis la géométrie en cours.
        val surfaces = rememberGeoJsonSource(GeoJsonData.JsonString(formesGeoJson(formes.filter { it.type == "Polygon" }, ::sommetsDe, ferme = true)))
        val lignes = rememberGeoJsonSource(GeoJsonData.JsonString(formesGeoJson(formes.filter { it.type == "LineString" }, ::sommetsDe, ferme = false)))
        FillLayer(
            id = "releves-surfaces",
            source = surfaces,
            color = const(if (ios) CouleursGeoMys.vert.copy(alpha = 0.18f) else BLEU_SURFACE),
            onClick = { entites -> toucherForme(entites.firstOrNull()?.properties?.get("cle")?.jsonPrimitive?.content) },
        )
        LineLayer(
            id = "releves-contours",
            source = surfaces,
            color = const(if (ios) CouleursGeoMys.vert else BLEU_TRACE),
            width = const(if (ios) 2.5.dp else px(5f)),
        )
        LineLayer(
            id = "releves-lignes",
            source = lignes,
            color = const(if (ios) CouleursGeoMys.vert else BLEU_TRACE),
            width = const(if (ios) 3.dp else px(5f)),
            cap = const(LineCap.Round),
            hitPadding = 8.dp,
            onClick = { entites -> toucherForme(entites.firstOrNull()?.properties?.get("cle")?.jsonPrimitive?.content) },
        )
        val parcours by vm.tracker.parcours.collectAsState()
        if (parcours.size > 1) {
            val trace = rememberGeoJsonSource(GeoJsonData.JsonString(ligneGeoJson(parcours.map { LatLon(it.latitude, it.longitude) })))
            LineLayer(
                id = "trace",
                source = trace,
                color = const(if (ios) CouleursGeoMys.vert else BLEU_TRACE),
                width = const(if (ios) 4.dp else px(6f)),
                cap = const(LineCap.Round),
            )
        }
        // Géométrie en cours, dès 2 sommets : rouge sous Android, bleu primaire sous iOS.
        val sommets = vm.sommets
        if (vm.modePositionnement && sommets.size >= 2 && vm.modeGeom != ModeGeom.POINT) {
            val polygone = vm.modeGeom == ModeGeom.POLYGON
            val geometrie = rememberGeoJsonSource(GeoJsonData.JsonString(geometrieGeoJson(sommets, polygone)))
            if (polygone) {
                FillLayer(
                    id = "saisie-surface",
                    source = geometrie,
                    color = const(if (ios) CouleursGeoMys.primaire.copy(alpha = 0.2f) else ROUGE_SURFACE),
                )
            }
            LineLayer(
                id = "saisie-contour",
                source = geometrie,
                color = const(if (ios) CouleursGeoMys.primaire else ROUGE_SAISIE),
                width = const(if (ios) (if (polygone) 2.dp else 3.dp) else px(if (polygone) 4f else 6f)),
            )
        }
        val position by vm.tracker.position.collectAsState()
        position?.let { CerclePrecision(LatLon(it.latitude, it.longitude), it.precision.toDouble()) }
    }

    // La carte retrouvée au retour d'un relevé garde sa caméra : on la replace où l'ouverture le
    // veut. Une reprise se cadre ensuite sur ses relevés et sa trace, avec une marge égale à la
    // hauteur du panneau de commandes plus 32 dp de respiration.
    LaunchedEffect(etat) {
        etat.setCameraPosition(camera)
        val emprise = (ouverture as? OuvertureCarte.Reprise)?.cadrage as? CadrageReprise.Emprise ?: return@LaunchedEffect
        etat.awaitViewport()
        val marge = 84.dp + 32.dp
        etat.fitCameraToBounds(
            BoundingBox(west = emprise.ouest, south = emprise.sud, east = emprise.est, north = emprise.nord),
            CameraFit(fitPadding = DpPadding(marge, marge, marge, marge)),
        )
    }
    // Suivi de position : la carte glisse vers chaque nouveau point.
    LaunchedEffect(etat) {
        vm.tracker.position.collect { p ->
            if (p != null && vm.suivrePosition) etat.animerCamera(CameraUpdate(center = LatLon(p.latitude, p.longitude).enPosition()))
        }
    }
    // Déplacer la carte au doigt coupe le suivi de position (hors positionnement).
    LaunchedEffect(etat) {
        snapshotFlow { etat.isCameraMoving && etat.cameraMoveReason == CameraMoveReason.Gesture }.collect { geste ->
            if (geste && !vm.modePositionnement) vm.suivrePosition = false
        }
    }
    SuiviCap(etat, vm.carteSuitBoussole)
    // La carte quittée (onPause) : centre et zoom, repris au retour.
    DisposableEffect(etat) {
        onDispose {
            val c = etat.cameraPosition
            vm.centreMemorise = LatLon(c.center.latitude, c.center.longitude)
            vm.zoomMemorise = c.zoom
        }
    }

    CarteGeoMys(etat, Modifier.fillMaxSize(), onClic = vm::toucherCarte) {
        val position by vm.tracker.position.collectAsState()
        // Sous Android, le point de position est sous les marqueurs ; sous iOS, au-dessus (MapKit).
        if (!ios) position?.let { PointPosition(LatLon(it.latitude, it.longitude)) }
        // Sommets des relevés ligne ou polygone : le glisser redessine la forme, le lâcher l'écrit.
        formes.forEach { forme ->
            sommetsDe(forme).forEachIndexed { i, sommet ->
                SommetDeplacable(
                    etat,
                    sommet,
                    onDeplacement = { p -> formeGlissee = forme.cle to sommetsDe(forme).toMutableList().also { it[i] = p } },
                    onFin = { p ->
                        val nouveaux = sommetsDe(forme).toMutableList().also { it[i] = p }
                        formeGlissee = null
                        vm.deplacerSommetReleve(forme.ids, nouveaux)
                    },
                )
            }
        }
        // Un marqueur par relevé ponctuel : le toucher ouvre ses options, le glisser déplace tout
        // le relevé.
        points.forEach { releve ->
            val premiere = releve.observations.first()
            MarqueurDeplacable(
                etat = etat,
                position = releve.position,
                icone = if (ios) Res.drawable.ic_location_on else Res.drawable.ic_bird_marker,
                taille = if (ios) 40.dp else 32.dp,
                ancre = if (ios) ANCRE_GOUTTE_IOS else BiasAlignment(0f, 1f),
                teinte = if (ios) CouleursGeoMys.rougePastille else null,
                description = releve.titre,
                onClic = { vm.toucherReleve(CarteSaisie.obsDuReleve(observations, premiere)) },
                onFin = { p -> vm.deplacerReleve(premiere, p) },
            )
        }
        // Sommets de la géométrie en cours : le glisser la redessine en direct.
        if (vm.modePositionnement) {
            vm.sommets.forEachIndexed { i, sommet ->
                SommetDeplacable(etat, sommet, onDeplacement = { p -> vm.deplacerSommet(i, p) }, onFin = { p -> vm.deplacerSommet(i, p) })
            }
        }
        if (ios) position?.let { PointPosition(LatLon(it.latitude, it.longitude)) }
    }
    return etat
}

/** La boussole, dont l'aiguille montre le nord de la carte. À part : elle se redessine à chaque
 *  mouvement de la carte. */
@Composable
internal fun Boussole(etat: MapState, onClick: () -> Unit) {
    BoutonBoussole(-etat.cameraPosition.bearing, onClick)
}

/** Bouton de mode de géométrie : actif = disque bleu primaire, icône blanche ; inactif = disque
 *  blanc, icône bleue. En relief (demande du 24 octobre 2026).
 *  - Android : ImageButton sur `btn_map_bg` teinté, icône de 24 dp, sans ombre ni état appuyé ;
 *  - iOS : symbole SF de 18 pt demi-gras sur un disque de 44 pt ombré, estompé à l'appui. */
@Composable
private fun BoutonMode(
    mode: ModeGeom,
    actuel: ModeGeom,
    icone: DrawableResource,
    symbole: String,
    description: String,
    onChoisir: (ModeGeom) -> Unit,
) {
    val actif = mode == actuel
    val fond = if (actif) CouleursGeoMys.primaire else Color.White
    val teinte = if (actif) Color.White else CouleursGeoMys.primaire
    if (estIos) {
        BoutonIos({ onChoisir(mode) }, opaciteAppui = 0.6f) {
            Box(Modifier.size(44.dp).fondEnRelief(fond, CircleShape), contentAlignment = Alignment.Center) {
                SymboleSf(symbole, 18f, listOf(teinte), graisse = GraisseSymbole.DEMI_GRASSE, description = description)
            }
        }
    } else {
        val interactions = remember { MutableInteractionSource() }
        val appuye by interactions.collectIsPressedAsState()
        Box(
            Modifier
                .size(44.dp)
                .fondEnRelief(fond, CircleShape, enfonce = appuye)
                .clickable(interactionSource = interactions, indication = null, role = Role.Button) { onChoisir(mode) },
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(icone), contentDescription = description, colorFilter = ColorFilter.tint(teinte), modifier = Modifier.size(24.dp))
        }
    }
}

/** Barre d'info du parcours (Android) : distance et nombre d'observations, sur colorSurfaceVariant. */
@Composable
private fun BarreInfo(distance: Double, nbObs: Int, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(Res.drawable.ic_walk), null, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.size(16.dp))
        Text("${formaterDecimal(distance, 0)} m", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, end = 24.dp))
        Image(painterResource(Res.drawable.oiseaux), null, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.size(16.dp))
        Text("$nbObs obs.", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Panneau du bas hors positionnement : parcours ▶/■, nouvelle observation « + », liste avec son
 *  compteur.
 *  - Android : trois cases de même largeur, boutons alignés en haut ; sans enregistrement de la
 *    trace, la case du parcours disparaît et les deux autres se partagent la largeur ;
 *  - iOS : boutons ronds de 56 pt espacés de 24, la barre d'info en pastille au-dessus. */
@Composable
private fun PanneauCommandes(vm: SaisieMultiViewModel, enCours: Boolean, infoVisible: Boolean, distance: Double, nbObs: Int) {
    if (estIos) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (infoVisible) {
                Row(
                    Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SymboleSf("figure.walk", 14f, listOf(Color.White), graisse = GraisseSymbole.MOYENNE)
                    Text(
                        "${formaterDecimal(distance, 0)} m",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 6.dp, end = 16.dp),
                    )
                    SymboleSf("bird.fill", 14f, listOf(Color.White), graisse = GraisseSymbole.MOYENNE)
                    Text("$nbObs obs.", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                // Le bonhomme : blanc et gris tant que la trace est arrêtée, vert quand elle enregistre.
                BoutonRondIos(
                    "figure.walk",
                    if (enCours) CouleursGeoMys.vert else Color.White,
                    if (enCours) Color.White else GRIS_INACTIF_IOS,
                    if (enCours) "Arrêter la trace GPS" else "Démarrer la trace GPS",
                    vm::basculerTrace,
                )
                BoutonRondIos("plus", CouleursGeoMys.primaire, Color.White, "Observation", vm::nouveauReleve)
                Box {
                    BoutonRondIos("list.bullet", Color.White, CouleursGeoMys.primaire, "Liste", vm::ouvrirListe)
                    if (nbObs > 0) {
                        Text(
                            "$nbObs",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .background(CouleursGeoMys.vert, CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            // Le bonhomme : blanc et gris tant que la trace est arrêtée, vert quand elle enregistre.
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BoutonRondAndroid(
                    Res.drawable.ic_walk,
                    if (enCours) CouleursGeoMys.vert else Color.White,
                    if (enCours) Color(0xFF2E7D32) else GRIS_APPUYE,
                    52.dp,
                    if (enCours) "Arrêter la trace GPS" else "Démarrer la trace GPS",
                    vm::basculerTrace,
                    teinte = if (enCours) Color.White else GRIS_ICONE_ANDROID,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BoutonRondAndroid(Res.drawable.ic_add, CouleursGeoMys.primaire, BLEU_APPUYE, 48.dp, "Observation", vm::nouveauReleve)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box {
                    // Disque blanc, icône bleue, comme sous iOS (demande du 24 octobre 2026).
                    BoutonRondAndroid(Res.drawable.ic_list, Color.White, GRIS_APPUYE, 44.dp, "Liste", vm::ouvrirListe, teinte = CouleursGeoMys.primaire)
                    if (nbObs > 0) {
                        Text(
                            "$nbObs",
                            color = Color.White,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(CouleursGeoMys.orange, RoundedCornerShape(8.dp))
                                .defaultMinSize(minWidth = 18.dp)
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Panneau du bas en positionnement : annuler ou valider la géométrie.
 *  - Android : « Annuler » gris et « Valider ce point » bleu, sur le bleu nuit de `panel_bg` ;
 *  - iOS : boutons ronds ✕ rouge et ✓ vert. */
@Composable
private fun PanneauValidation(vm: SaisieMultiViewModel, onValider: () -> Unit) {
    val libelle = CarteSaisie.libelleValidation(false, vm.modeGeom)
    if (estIos) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        ) {
            BoutonRondIos("xmark", CouleursGeoMys.rougePastille, Color.White, "Annuler", vm::annulerPositionnement)
            BoutonRondIos("checkmark", CouleursGeoMys.vert, Color.White, libelle, onValider)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().background(FOND_PANNEAU).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Widget.GeoMys.Button.TextButton teinté darker_gray.
            BoutonMaterial(
                onClick = vm::annulerPositionnement,
                fond = GRIS_FONCE,
                marges = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                couleurContenu = Color.White,
            ) { Text("Annuler", style = styleLibelleMaterial(Color.White)) }
            Spacer(Modifier.weight(1f))
            BoutonPlein(libelle, onValider)
        }
    }
}

/** Bouton rond Android (`btn_circle_green`, `btn_circle_blue`) : disque plein, plus foncé appuyé,
 *  icône de 24 dp teintée de [teinte] (blanche par défaut). En relief, enfoncé à l'appui (demande du
 *  24 octobre 2026). */
@Composable
internal fun BoutonRondAndroid(
    icone: DrawableResource,
    fond: Color,
    fondAppuye: Color,
    taille: Dp,
    description: String,
    onClick: () -> Unit,
    teinte: Color = Color.White,
) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    val couleur = if (appuye) fondAppuye else fond
    Box(
        Modifier
            .size(taille)
            .fondEnRelief(couleur, CircleShape, enfonce = appuye)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icone), contentDescription = description, colorFilter = ColorFilter.tint(teinte), modifier = Modifier.size(24.dp))
    }
}

/** `boutonRond` de TraceView : disque de 56 pt ombré, symbole SF de 22 pt demi-gras, estompé à
 *  l'appui. En relief (demande du 24 octobre 2026). */
@Composable
internal fun BoutonRondIos(symbole: String, fond: Color, teinte: Color, description: String, onClick: () -> Unit) {
    BoutonIos(onClick, opaciteAppui = 0.6f) {
        Box(Modifier.size(56.dp).fondEnRelief(fond, CircleShape), contentAlignment = Alignment.Center) {
            SymboleSf(symbole, 22f, listOf(teinte), graisse = GraisseSymbole.DEMI_GRASSE, description = description)
        }
    }
}

private fun ligneGeoJson(points: List<LatLon>): String = buildJsonObject {
    put("type", "Feature")
    put("properties", buildJsonObject {})
    put("geometry", buildJsonObject {
        put("type", "LineString")
        put("coordinates", coordonnees(points))
    })
}.toString()

private fun geometrieGeoJson(sommets: List<LatLon>, polygone: Boolean): String = buildJsonObject {
    put("type", "Feature")
    put("properties", buildJsonObject {})
    put("geometry", buildJsonObject {
        put("type", if (polygone) "Polygon" else "LineString")
        put("coordinates", if (polygone) JsonArray(listOf(coordonnees(sommets + sommets.first()))) else coordonnees(sommets))
    })
}.toString()

/** Les formes des relevés en FeatureCollection, chacune portant sa clé (`cle`). */
private fun formesGeoJson(formes: List<ReleveForme>, sommets: (ReleveForme) -> List<LatLon>, ferme: Boolean): String = buildJsonObject {
    put("type", "FeatureCollection")
    put("features", buildJsonArray {
        formes.forEach { forme ->
            val s = sommets(forme)
            add(buildJsonObject {
                put("type", "Feature")
                put("properties", buildJsonObject { put("cle", forme.cle) })
                put("geometry", buildJsonObject {
                    put("type", if (ferme) "Polygon" else "LineString")
                    put("coordinates", if (ferme) JsonArray(listOf(coordonnees(s + s.first()))) else coordonnees(s))
                })
            })
        }
    })
}.toString()

private fun coordonnees(points: List<LatLon>): JsonArray =
    JsonArray(points.map { JsonArray(listOf(JsonPrimitive(it.longitude), JsonPrimitive(it.latitude))) })
