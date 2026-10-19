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

package fr.ariegenature.geomys.ui.carte.prototype

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import fr.ariegenature.geomys.carte.FondCarte
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.geo.LissageCap
import fr.ariegenature.geomys.geo.ecartAngulaire
import fr.ariegenature.geomys.ui.carte.ConfigurationCarte
import fr.ariegenature.geomys.ui.carte.PoigneesSommets
import fr.ariegenature.geomys.ui.carte.enPosition
import fr.ariegenature.geomys.ui.carte.id
import fr.ariegenature.geomys.ui.carte.styleRaster
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.location.HeadingRequest
import org.maplibre.compose.location.rememberDefaultHeadingProvider
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.OfflinePack
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.OfflineStorageState
import org.maplibre.compose.offline.offlineStorage
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.rememberMbtilesUrl
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees

/** Prototype du lot 0 : valide sur Android et iOS ce que demandent les écrans carte de GeoMys
 *  (INVENTAIRE.md § 5) : fonds raster en ligne, fond MBTiles, zone hors-ligne, User-Agent
 *  (vérifié avec le serveur de tuiles de test, outils/prototype-carte/), cap, et sommets
 *  déplaçables avec la forme redessinée en direct. Écran provisoire, à retirer ensuite. */
@Composable
fun PrototypeCarteScreen(
    onRetour: () -> Unit,
    fondInitial: String? = null,
    zoomInitial: Double? = null,
    telechargerAuDemarrage: Boolean = false,
) {
    var choix by remember {
        mutableStateOf(CHOIX_FONDS.firstOrNull { it.id == fondInitial } ?: ChoixFond.EnLigne(FondCarte.TOPO))
    }
    val sommets = remember { mutableStateListOf(*SOMMETS_INITIAUX) }
    val style = styleDe(choix)

    val etatCarte = rememberMapState(
        baseStyle = style,
        initialCameraPosition = CameraPosition(center = CENTRE_FOIX.enPosition(), zoom = zoomInitial ?: 14.5),
    ) {
        // Nouvelles données à chaque déplacement de sommet : la source est reconstruite et la forme
        // suit le doigt. synchronousTiling accélère l'affichage d'une petite source mise à jour souvent.
        val polygone = rememberGeoJsonSource(GeoJsonData.JsonString(polygoneGeoJson(sommets))) {
            synchronousTiling = true
        }
        FillLayer(id = "polygone-remplissage", source = polygone, color = const(ROUGE), opacity = const(0.3f))
        LineLayer(id = "polygone-contour", source = polygone, color = const(ROUGE), width = const(2.dp))
    }

    Box(Modifier.fillMaxSize()) {
        MaplibreMap(
            state = etatCarte,
            // Comme GeoMys-Android : pas de rotation ni d'inclinaison au doigt (la rotation vient du cap).
            interactions = MapInteractions {
                camera {
                    rotate { enabled = false }
                    pitch { enabled = false }
                }
            },
        )
        PoigneesSommets(
            etatCarte = etatCarte,
            sommets = sommets,
            onDeplacement = { index, position -> sommets[index] = position },
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Surface(color = Color.Black.copy(alpha = 0.55f), shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    TextButton(onClick = onRetour) { Text("← Accueil", color = CouleursGeoMys.jauneClair) }
                    Text("Prototype carte", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
            Box(Modifier.weight(1f))
            PanneauEssais(
                choix = choix,
                onChoix = { choix = it },
                etatCarte = etatCarte,
                premierSommet = sommets.first(),
                telechargerAuDemarrage = telechargerAuDemarrage,
            )
        }
    }
}

@Composable
private fun PanneauEssais(
    choix: ChoixFond,
    onChoix: (ChoixFond) -> Unit,
    etatCarte: MapState,
    premierSommet: LatLon,
    telechargerAuDemarrage: Boolean,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CHOIX_FONDS.forEach { candidat ->
                    FilterChip(
                        selected = candidat == choix,
                        onClick = { onChoix(candidat) },
                        label = { Text(candidat.libelle) },
                    )
                }
            }
            EssaiHorsLigne(choix, etatCarte, telechargerAuDemarrage)
            EssaiCap(etatCarte)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Zoom au bouton : comme GeoMys, et utile là où le pincement ne se simule pas.
                TextButton(onClick = { etatCarte.zoomer(-1.0) }) { Text("−", color = CouleursGeoMys.jauneClair) }
                TextButton(onClick = { etatCarte.zoomer(+1.0) }) { Text("+", color = CouleursGeoMys.jauneClair) }
                Text(
                    "Sommet 1 : ${arrondi(premierSommet.latitude)}, ${arrondi(premierSommet.longitude)} " +
                        "· zoom ${arrondi(etatCarte.cameraPosition.zoom, 1)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Télécharge un paquet hors-ligne pour l'emprise visible, du zoom courant à deux niveaux de plus. */
@Composable
private fun EssaiHorsLigne(choix: ChoixFond, etatCarte: MapState, telechargerAuDemarrage: Boolean) {
    val stockage = remember { DefaultMapRuntime.instance.offlineStorage }
    val etatStockage by stockage.state.collectAsState()
    var pack by remember { mutableStateOf<OfflinePack?>(null) }
    val portee = rememberCoroutineScope()
    val densite = LocalDensity.current.density
    val urlStyle = choix.urlStyleHorsLigne

    suspend fun telecharger(styleUrl: String) {
        val bornes = etatCarte.awaitViewport().let { etatCarte.getVisibleBounds() } ?: return
        val zoom = floor(etatCarte.cameraPosition.zoom)
        val nouveau = stockage.create(
            definition = OfflinePackDefinition.TilePyramid(
                styleUrl = styleUrl,
                bounds = BoundingBox(
                    west = bornes.southwest.longitude,
                    south = bornes.southwest.latitude,
                    east = bornes.northeast.longitude,
                    north = bornes.northeast.latitude,
                ),
                pixelRatio = densite,
                minZoom = zoom,
                maxZoom = min(zoom + 2, 17.0),
            ),
            metadata = choix.libelle.encodeToByteArray(),
        )
        stockage.resume(nouveau)
        pack = nouveau
    }

    // Essai automatisé (paramètre de lancement action=hors-ligne) : une fois le stockage prêt.
    LaunchedEffect(telechargerAuDemarrage, etatStockage is OfflineStorageState.Ready) {
        if (telechargerAuDemarrage && etatStockage is OfflineStorageState.Ready && pack == null) {
            urlStyle?.let { telecharger(it) }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = etatStockage is OfflineStorageState.Ready && urlStyle != null,
            onClick = {
                val styleUrl = urlStyle ?: return@Button
                portee.launch { telecharger(styleUrl) }
            },
        ) { Text("Zone hors-ligne") }
        val progression = pack?.downloadProgress?.collectAsState()?.value
        Text(
            text = when {
                urlStyle == null -> "Indisponible pour ce fond"
                etatStockage is OfflineStorageState.Failed -> "Stockage hors-ligne indisponible"
                progression == null -> "Aucun téléchargement"
                progression is DownloadProgress.Healthy ->
                    "${progression.completedTileCount} tuiles · ${progression.completedResourceCount}/" +
                        "${progression.requiredResourceCount} · ${progression.status}"
                progression is DownloadProgress.Error -> "Erreur : ${progression.message}"
                progression is DownloadProgress.TileLimitExceeded -> "Limite de ${progression.limit} tuiles atteinte"
                else -> "En attente…"
            },
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Fait tourner la carte selon le cap de l'appareil, lu par le fournisseur de maplibre-compose. */
@Composable
private fun EssaiCap(etatCarte: MapState) {
    var suivreCap by remember { mutableStateOf(false) }
    var dernierCap by remember { mutableStateOf<String?>(null) }
    val fournisseurCap = rememberDefaultHeadingProvider()

    LaunchedEffect(suivreCap) {
        if (!suivreCap) {
            etatCarte.setCameraPosition(etatCarte.cameraPosition.copy(bearing = 0.0))
            return@LaunchedEffect
        }
        // Toutes les mesures du capteur : le réglage par défaut n'en garde qu'une par seconde, ce
        // qui faisait tourner la carte par à-coups.
        var cible: Double? = null
        launch {
            fournisseurCap.updates(HeadingRequest { minimumInterval = Duration.ZERO }).collect { mesure ->
                val degres = (mesure.bearing - Bearing.North).inDegrees
                cible = degres
                dernierCap = "${degres.roundToInt()}° (${mesure.reference})"
            }
        }
        // Puis, à chaque image, le cap affiché glisse vers la dernière mesure (LissageCap) : la
        // rotation est continue, et le bruit du capteur ne fait pas trembler la carte.
        val lissage = LissageCap()
        var imagePrecedente = 0L
        while (true) {
            withFrameNanos { maintenant ->
                val ecoule = if (imagePrecedente == 0L) Duration.ZERO else (maintenant - imagePrecedente).nanoseconds
                imagePrecedente = maintenant
                val capVise = cible ?: return@withFrameNanos
                val cap = lissage.avancer(capVise, ecoule)
                val position = etatCarte.cameraPosition
                if (abs(ecartAngulaire(position.bearing, cap)) > 0.05) {
                    etatCarte.setCameraPosition(position.copy(bearing = cap))
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = suivreCap, onCheckedChange = { suivreCap = it })
        Text(
            text = if (suivreCap) "Carte orientée selon le cap : ${dernierCap ?: "en attente du capteur…"}"
            else "Carte orientée au nord",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Fonds proposés par le prototype : les fonds de l'application, plus deux fonds d'essai. */
private sealed interface ChoixFond {
    /** Identifiant du paramètre de lancement `fond`. */
    val id: String
    val libelle: String

    /** Style à télécharger pour le hors-ligne, ou null si ce fond ne s'y prête pas. */
    val urlStyleHorsLigne: String?

    data class EnLigne(val fond: FondCarte) : ChoixFond {
        override val id: String get() = fond.id
        override val libelle: String get() = fond.libelle

        // Pas de téléchargement en masse des tuiles openstreetmap.org (politique d'usage d'OSM).
        override val urlStyleHorsLigne: String?
            get() = if (fond == FondCarte.OSM) null else ConfigurationCarte.urlStyle(fond.id)
    }

    /** Fichier MBTiles embarqué : tuiles colorées générées par outils/prototype-carte/. */
    data object MbtilesTest : ChoixFond {
        override val id = "mbtiles-test"
        override val libelle = "MBTiles de test"
        override val urlStyleHorsLigne: String? = null
    }

    /** Serveur de tuiles local (outils/prototype-carte/) : il journalise le User-Agent reçu, et
     *  l'arrêter permet de vérifier qu'une zone téléchargée s'affiche sans réseau. */
    data object ServeurTest : ChoixFond {
        override val id = "serveur-test"
        override val libelle = "Serveur de test"
        override val urlStyleHorsLigne: String = ConfigurationCarte.urlStyle(ConfigurationCarte.ID_STYLE_TEST_LOCAL)
    }
}

private val CHOIX_FONDS: List<ChoixFond> =
    FondCarte.entries.map { ChoixFond.EnLigne(it) } + ChoixFond.MbtilesTest + ChoixFond.ServeurTest

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun styleDe(choix: ChoixFond): BaseStyle = when (choix) {
    is ChoixFond.EnLigne -> BaseStyle.Uri(ConfigurationCarte.urlStyle(choix.fond.id))
    ChoixFond.ServeurTest -> BaseStyle.Uri(ConfigurationCarte.urlStyle(ConfigurationCarte.ID_STYLE_TEST_LOCAL))
    ChoixFond.MbtilesTest -> {
        // MapLibre lit le MBTiles par SQLite : rememberMbtilesUrl copie la ressource embarquée
        // dans un fichier, puis renvoie son URL mbtiles://.
        val url by rememberMbtilesUrl(Res.getUri("files/carte-test.mbtiles"))
        url?.let { BaseStyle.Json(styleRaster(listOf(it), zoomMax = 16, attribution = "MBTiles de test")) }
            ?: BaseStyle.Empty
    }
}

private val ROUGE = Color(0xFFFF3333)
private val CENTRE_FOIX = LatLon(latitude = 42.9653, longitude = 1.6075)
private val SOMMETS_INITIAUX = arrayOf(
    LatLon(latitude = 42.9683, longitude = 1.6035),
    LatLon(latitude = 42.9683, longitude = 1.6115),
    LatLon(latitude = 42.9623, longitude = 1.6115),
    LatLon(latitude = 42.9623, longitude = 1.6035),
)

private fun MapState.zoomer(ecart: Double) {
    setCameraPosition(cameraPosition.copy(zoom = cameraPosition.zoom + ecart))
}

private fun polygoneGeoJson(sommets: List<LatLon>): String {
    val anneau = (sommets + sommets.first()).joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[$anneau]]}}"""
}

private fun arrondi(valeur: Double, decimales: Int = 5): String {
    var facteur = 1.0
    repeat(decimales) { facteur *= 10 }
    return ((valeur * facteur).roundToLong() / facteur).toString()
}
