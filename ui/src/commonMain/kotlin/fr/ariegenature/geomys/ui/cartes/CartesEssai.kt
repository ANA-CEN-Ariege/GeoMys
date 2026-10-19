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

package fr.ariegenature.geomys.ui.cartes

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.carte.FondCarte
import fr.ariegenature.geomys.carte.FondChoisi
import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.ui.carte.IssueTelechargement
import fr.ariegenature.geomys.ui.carte.ZonesHorsLigne
import fr.ariegenature.geomys.ui.ressources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okio.Buffer

/** L'essai `action=cartes-essai` : la carte MBTiles de test du prototype (tuiles colorées autour de Foix,
 *  zooms 10 à 16) importée comme le ferait le sélecteur de fichiers. Émulateur et simulateur seulement. */
suspend fun ServicesGeoNature.importerCarteEssai() {
    val octets = Res.readBytes("files/carte-test.mbtiles")
    val fichier = withContext(Dispatchers.IO) { cartesMbtiles.importer(Buffer().write(octets), "carte-test.mbtiles", 0L) }
    println("CARTES_ESSAI ${fichier?.name ?: "échec"}")
}

/** Les essais d'affichage du Maps Manager (le simulateur iOS ne touche pas) : « cartes » ouvre la liste des
 *  cartes MBTiles ; « telechargement » lance, sur [ZonesEssai], le téléchargement d'une petite zone près de
 *  Foix au fond OpenTopoMap, qui reste à 40 %. */
internal fun lancerEssaiMapsManager(vm: MapsManagerViewModel, essai: String) {
    when (essai) {
        "cartes" -> vm.ouvrirCartes()
        "telechargement" -> {
            vm.choisirFond(FondChoisi.EnLigne(FondCarte.OPENTOPO))
            vm.carteBougee(Emprise(sud = 42.955, ouest = 1.595, nord = 42.975, est = 1.62), 14.0)
            vm.charger(2f)
        }
    }
}

/** Un téléchargement simulé, sans réseau : 48 ressources reçues sur 120, puis plus rien. */
internal class ZonesEssai : ZonesHorsLigne {
    override val occupation = MutableStateFlow(312L * 1024 * 1024)

    override suspend fun telecharger(
        urlStyle: String,
        emprise: Emprise,
        niveauMin: Int,
        niveauMax: Int,
        densite: Float,
        progression: (recues: Long, attendues: Long) -> Unit,
    ): IssueTelechargement {
        progression(48, 120)
        awaitCancellation()
    }

    override suspend fun viderTout() = true
}
