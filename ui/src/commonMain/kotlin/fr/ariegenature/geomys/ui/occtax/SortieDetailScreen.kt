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

// Écran « Détail de la saisie » : fragment_sortie_detail.xml et infowindow_obs.xml de GeoMys-Android
// @ cdd5196 sous Android, SortieDetailView de GeoMys-IOS sous iOS.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.carte.FondCarte
import fr.ariegenature.geomys.carte.FondChoisi
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.occtax.FormeReleve
import fr.ariegenature.geomys.occtax.MesSaisies
import fr.ariegenature.geomys.occtax.ReleveAffiche
import fr.ariegenature.geomys.ui.carte.BoutonCarte
import fr.ariegenature.geomys.ui.carte.BulleCarte
import fr.ariegenature.geomys.ui.carte.CarteGeoMys
import fr.ariegenature.geomys.ui.carte.MarqueurReleve
import fr.ariegenature.geomys.ui.carte.StyleBoutonCarte
import fr.ariegenature.geomys.ui.carte.cadrer
import fr.ariegenature.geomys.ui.carte.cameraInitiale
import fr.ariegenature.geomys.ui.carte.hauteurMarqueur
import fr.ariegenature.geomys.ui.carte.style
import fr.ariegenature.geomys.ui.composants.BandeauNavigation
import fr.ariegenature.geomys.ui.composants.BoutonAlerte
import fr.ariegenature.geomys.ui.composants.DialogueAlerte
import fr.ariegenature.geomys.ui.composants.DialogueChoixUnique
import fr.ariegenature.geomys.ui.composants.DialogueElements
import fr.ariegenature.geomys.ui.composants.IndicateurActivite
import fr.ariegenature.geomys.ui.composants.estIos
import fr.ariegenature.geomys.ui.composants.rememberPartageFichier
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.ui.ressources.ic_list
import fr.ariegenature.geomys.ui.ressources.ic_map
import fr.ariegenature.geomys.ui.ressources.ic_send
import fr.ariegenature.geomys.ui.ressources.ic_share
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource

/** Bulle ouverte sur la carte. */
private class Bulle(val position: LatLon, val decalage: Dp, val titre: String, val contenu: String)

/** Couleurs des tracés : bleu translucide sous Android, vert (colorSecondary) sous GeoMys-IOS. */
private val BLEU_TRACE = Color(0xCC2196F3)
private val BLEU_SURFACE = Color(0x402196F3)

@Composable
fun SortieDetailScreen(vm: SortieDetailViewModel, onAccueil: () -> Unit, onRetour: () -> Unit) {
    val ios = estIos
    LaunchedEffect(vm.introuvable) { if (vm.introuvable) onRetour() }
    val partager = rememberPartageFichier()
    Box(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        if (vm.sortie != null) CarteSortie(vm)

        // Bandeau « 🏠 › Détail de la saisie » : flottant en haut à gauche sous Android, bande de
        // fil d'Ariane sous iOS.
        if (ios) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                BandeauNavigation("Détail de la saisie", onAccueil)
            }
        } else {
            BandeauNavigation(
                "Détail de la saisie",
                onAccueil,
                Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp),
            )
        }

        // Haut droite : Envoyer (disque bleu plein), puis Partager le GPX (disque blanc).
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .then(
                    if (ios) Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 56.dp, end = 12.dp)
                    else Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp),
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (vm.boutonEnvoiVisible) {
                BoutonCarte(Res.drawable.ic_send, "Envoyer vers GeoNature", StyleBoutonCarte.PRINCIPAL, { if (!vm.envoiEnCours) vm.envoyer() })
            }
            BoutonCarte(Res.drawable.ic_share, "Partager (GPX)", StyleBoutonCarte.SECONDAIRE, { vm.fichierGpx()?.let(partager) })
        }

        // Bas droite : changer de fond, puis la liste des espèces.
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .then(
                    if (ios) Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 24.dp, end = 12.dp)
                    else Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            BoutonCarte(Res.drawable.ic_map, "Changer fond", StyleBoutonCarte.CONTROLE, vm::montrerChoixFond)
            if (vm.aDesEspeces) {
                BoutonCarte(Res.drawable.ic_list, "Liste des espèces", StyleBoutonCarte.CONTROLE, vm::montrerEspeces)
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

        val sortie = vm.sortie
        DialogueElements(
            visible = vm.especesVisibles,
            titre = sortie?.let { MesSaisies.titreEspeces(MesSaisies.especesAgregees(it).size) }.orEmpty(),
            lignes = sortie?.let(MesSaisies::lignesEspeces).orEmpty(),
            onFermer = vm::fermerEspeces,
        )
        val fonds = FondCarte.entries
        DialogueChoixUnique(
            visible = vm.choixFondVisible,
            titre = "Fond de carte",
            options = fonds.map { it.libelle },
            selection = fonds.indexOfFirst { (vm.fond as? FondChoisi.EnLigne)?.fond == it }.coerceAtLeast(0),
            onChoisir = { vm.choisirFond(FondChoisi.EnLigne(fonds[it])) },
            onFermer = vm::fermerChoixFond,
        )
        val info = vm.information
        DialogueAlerte(
            visible = info != null,
            titre = info?.titre.orEmpty(),
            message = info?.message.orEmpty(),
            boutons = listOf(BoutonAlerte("OK")),
            onFermer = vm::fermerInformation,
        )
    }
}

/** La carte : la trace, les relevés (marqueur, ligne ou polygone), et la bulle du relevé touché. */
@Composable
private fun CarteSortie(vm: SortieDetailViewModel) {
    val ios = estIos
    val sortie = vm.sortie ?: return
    val releves = vm.releves
    val densite = LocalDensity.current
    // Android : traits de 5 px (Paint d'osmdroid) ; iOS : 4 pt pour la trace, 2,5 et 3 pour les relevés.
    val cinqPixels = with(densite) { 5f.toDp() }
    var bulle by remember { mutableStateOf<Bulle?>(null) }
    fun ouvrir(index: Int, position: LatLon, decalage: Dp) {
        releves.getOrNull(index)?.let { bulle = Bulle(position, decalage, it.titre, it.contenu) }
    }

    val etat = rememberMapState(baseStyle = vm.fond.style(), initialCameraPosition = cameraInitiale(vm.cadrage)) {
        if (sortie.pointsParcours.size > 1) {
            val trace = rememberGeoJsonSource(GeoJsonData.JsonString(ligneGeoJson(sortie.pointsParcours.map { LatLon(it.latitude, it.longitude) })))
            LineLayer(
                id = "trace",
                source = trace,
                color = const(if (ios) CouleursGeoMys.vert else BLEU_TRACE),
                width = const(if (ios) 4.dp else cinqPixels),
                cap = const(LineCap.Round),
            )
        }
        val surfaces = rememberGeoJsonSource(GeoJsonData.JsonString(collectionGeoJson(releves) { it is FormeReleve.Polygone }))
        val lignes = rememberGeoJsonSource(GeoJsonData.JsonString(collectionGeoJson(releves) { it is FormeReleve.Ligne }))
        FillLayer(
            id = "releves-surfaces",
            source = surfaces,
            color = const(if (ios) CouleursGeoMys.vert.copy(alpha = 0.18f) else BLEU_SURFACE),
            onClick = { entites ->
                val i = entites.firstOrNull()?.properties?.get("i")?.jsonPrimitive?.int
                val p = position
                if (i == null || p == null) ClickResult.Pass else ClickResult.Consume.also { ouvrir(i, LatLon(p.latitude, p.longitude), 0.dp) }
            },
        )
        LineLayer(
            id = "releves-contours",
            source = surfaces,
            color = const(if (ios) CouleursGeoMys.vert else BLEU_TRACE),
            width = const(if (ios) 2.5.dp else cinqPixels),
        )
        LineLayer(
            id = "releves-lignes",
            source = lignes,
            color = const(if (ios) CouleursGeoMys.vert else BLEU_TRACE),
            width = const(if (ios) 3.dp else cinqPixels),
            cap = const(LineCap.Round),
            hitPadding = 8.dp,
            onClick = { entites ->
                val i = entites.firstOrNull()?.properties?.get("i")?.jsonPrimitive?.int
                val p = position
                if (i == null || p == null) ClickResult.Pass else ClickResult.Consume.also { ouvrir(i, LatLon(p.latitude, p.longitude), 0.dp) }
            },
        )
    }
    LaunchedEffect(vm.cadrage) { etat.cadrer(vm.cadrage) }

    val decalageMarqueur = hauteurMarqueur
    CarteGeoMys(
        etat,
        Modifier.fillMaxSize(),
        // Sous iOS, toucher la carte ferme la bulle, comme celles de MapKit.
        onClic = if (ios) { _ -> (bulle != null).also { bulle = null } } else null,
    ) {
        releves.forEachIndexed { i, releve ->
            val forme = releve.forme
            if (forme is FormeReleve.Point) MarqueurReleve(forme.position) { ouvrir(i, forme.position, decalageMarqueur) }
        }
        bulle?.let { b -> BulleCarte(b.position, b.decalage, b.titre, b.contenu, onFermer = { bulle = null }) }
    }
}

/** Une LineString GeoJSON. */
private fun ligneGeoJson(points: List<LatLon>): String = buildJsonObject {
    put("type", "Feature")
    put("properties", buildJsonObject {})
    put("geometry", buildJsonObject {
        put("type", "LineString")
        put("coordinates", coordonnees(points))
    })
}.toString()

/** Les relevés dont la forme passe [garder], en FeatureCollection, chacun portant son rang (`i`). */
private fun collectionGeoJson(releves: List<ReleveAffiche>, garder: (FormeReleve) -> Boolean): String = buildJsonObject {
    put("type", "FeatureCollection")
    put("features", buildJsonArray {
        releves.forEachIndexed { i, releve ->
            val forme = releve.forme
            if (!garder(forme)) return@forEachIndexed
            add(buildJsonObject {
                put("type", "Feature")
                put("properties", buildJsonObject { put("i", i) })
                put("geometry", when (forme) {
                    is FormeReleve.Polygone -> buildJsonObject {
                        put("type", "Polygon")
                        put("coordinates", JsonArray(listOf(coordonnees(forme.sommets + forme.sommets.first()))))
                    }
                    is FormeReleve.Ligne -> buildJsonObject {
                        put("type", "LineString")
                        put("coordinates", coordonnees(forme.sommets))
                    }
                    is FormeReleve.Point -> buildJsonObject {}
                })
            })
        }
    })
}.toString()

private fun coordonnees(points: List<LatLon>): JsonArray =
    JsonArray(points.map { JsonArray(listOf(JsonPrimitive(it.longitude), JsonPrimitive(it.latitude))) })
