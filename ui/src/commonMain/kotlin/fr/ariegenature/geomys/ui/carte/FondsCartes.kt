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

// Repris de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/MapUtils.kt) : le fond d'une
// carte (`appliquerFond`, un MBTiles servi hors ligne) et le menu de choix (`choisirFondCarte` : fonds en
// ligne, puis cartes importées), communs à tous les écrans-cartes.

package fr.ariegenature.geomys.ui.carte

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ariegenature.geomys.carte.CartesMbtiles
import fr.ariegenature.geomys.carte.FondCarte
import fr.ariegenature.geomys.carte.FondChoisi
import fr.ariegenature.geomys.ui.composants.DialogueChoixUnique
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.maplibre.compose.sources.mbtilesUrl
import org.maplibre.compose.style.BaseStyle

/** Les cartes MBTiles importées, posées par la navigation. Sans elles (aperçus, tests), aucune. */
val LocalCartesMbtiles = staticCompositionLocalOf<CartesMbtiles?> { null }

/** Un style sans source, le temps de lire les métadonnées d'une carte : rien n'est demandé au réseau. */
private const val STYLE_VIDE = """{"version":8,"sources":{},"layers":[]}"""

/** Le style MapLibre du [fond]. Une carte MBTiles est lue par MapLibre dans son fichier (`mbtiles://`) ;
 *  son zoom maximal vient de ses métadonnées, lues hors du fil principal. Une carte disparue cède la
 *  place à OpenStreetMap. */
@Composable
fun rememberStyleFond(fond: FondChoisi): BaseStyle {
    val cartes = LocalCartesMbtiles.current
    val osm = remember { BaseStyle.Uri(ConfigurationCarte.urlStyle(FondCarte.OSM.id)) }
    return when (fond) {
        is FondChoisi.EnLigne -> remember(fond) { BaseStyle.Uri(ConfigurationCarte.urlStyle(fond.fond.id)) }
        is FondChoisi.Mbtiles -> {
            val style by produceState<BaseStyle>(BaseStyle.Json(STYLE_VIDE), fond, cartes) {
                value = withContext(Dispatchers.IO) {
                    if (cartes == null || !cartes.existe(fond.fichier)) osm else styleMbtiles(cartes, fond.fichier)
                }
            }
            style
        }
    }
}

/** Le style d'une carte importée : ses tuiles de 256 px jusqu'à son zoom maximal, son nom en attribution.
 *  `mbtilesUrl` ne copie pas un fichier local : il encode son chemin en URL `mbtiles://`. */
suspend fun styleMbtiles(cartes: CartesMbtiles, nom: String): BaseStyle {
    val fichier = cartes.fichier(nom)
    val info = cartes.info(fichier)
    val url = mbtilesUrl("file://$fichier")
    return BaseStyle.Json(styleRaster(listOf(url), zoomMax = info.zoomMax, attribution = cartes.nomAffichage(fichier)))
}

/** La mention des données du [fond], au bas de la carte : une ligne discrète (9 sp, gris foncé sur
 *  blanc translucide), [lignesMax] au plus. GeoMys-Android et GeoMys-IOS ne l'affichaient pas (défaut n° 8
 *  de l'inventaire), alors que les serveurs de tuiles d'OpenStreetMap, d'OpenTopoMap et d'Esri
 *  l'exigent. Rien pour une carte importée, qui porte ses propres mentions. */
@Composable
fun AttributionFond(fond: FondChoisi, modifier: Modifier = Modifier, lignesMax: Int = 1) {
    val texte = (fond as? FondChoisi.EnLigne)?.fond?.source?.attribution?.takeIf { it.isNotEmpty() } ?: return
    Text(
        texte,
        color = Color(0xFF333333),
        fontSize = 9.sp,
        lineHeight = 11.sp,
        maxLines = lignesMax,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier.background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp),
    )
}

/** Une entrée du menu « Fond de carte ». */
private class OptionFond(val fond: FondChoisi, val libelle: String)

private val optionsEnLigne = FondCarte.entries.map { OptionFond(FondChoisi.EnLigne(it), it.libelle) }

/** Le menu « Fond de carte » : les fonds en ligne, puis les cartes importées (« 📁 nom (hors-ligne) »),
 *  le fond courant coché (le premier s'il n'y est plus). */
@Composable
fun DialogueChoixFond(
    visible: Boolean,
    fond: FondChoisi,
    onChoisir: (FondChoisi) -> Unit,
    onFermer: () -> Unit,
    titre: String = "Fond de carte",
) {
    val cartes = LocalCartesMbtiles.current
    val options by produceState(optionsEnLigne, visible, cartes) {
        if (visible && cartes != null) {
            value = withContext(Dispatchers.IO) {
                optionsEnLigne + cartes.liste().map { OptionFond(FondChoisi.Mbtiles(it.name), CartesMbtiles.libelleFond(cartes.nomAffichage(it))) }
            }
        }
    }
    DialogueChoixUnique(
        visible = visible,
        titre = titre,
        options = options.map { it.libelle },
        selection = options.indexOfFirst { it.fond == fond }.coerceAtLeast(0),
        onChoisir = { onChoisir(options[it].fond) },
        onFermer = onFermer,
    )
}
