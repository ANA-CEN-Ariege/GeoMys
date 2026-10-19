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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/MapUtils.kt) : les fonds,
// leurs libellés et leur mémorisation. Les sources de tuiles restent dans la couche carte de
// l'interface (ui/carte/SourcesFonds.kt).

package fr.ariegenature.geomys.carte

import fr.ariegenature.geomys.store.PreferencesGeoMys

/** Les fonds de carte EN LIGNE. Le nom de l'énumération est la valeur mémorisée (`fond_carte`). */
enum class FondCarte(
    /** Libellé lisible, pour le menu de choix. */
    val libelle: String,
) {
    OSM("OpenStreetMap"),
    OPENTOPO("OpenTopoMap (relief)"),
    TOPO("IGN Plan"),
    SCAN25("IGN SCAN 25 (topo)"),
    ORTHO("IGN Photo aérienne"),
    ESRI("Esri (satellite)"),
}

/** Fond de carte sélectionné : soit un fond EN LIGNE ([FondCarte]), soit un fichier MBTILES local
 *  importé par l'utilisateur (hors-ligne). Partagé entre toutes les cartes. */
sealed interface FondChoisi {
    data class EnLigne(val fond: FondCarte) : FondChoisi

    /** Carte hors ligne, par son nom de fichier dans le dossier des cartes MBTiles. */
    data class Mbtiles(val fichier: String) : FondChoisi
}

private const val PREFIXE_MBTILES = "mbtiles:"

/** Charge le dernier fond choisi (en ligne ou MBTiles). Repli sur OSM si aucun choix, si la valeur
 *  est inconnue, ou si le fichier MBTiles mémorisé a disparu ([mbtilesExiste]). */
fun chargerFondChoisi(preferences: PreferencesGeoMys, mbtilesExiste: (String) -> Boolean): FondChoisi {
    val v = preferences.fondCarte ?: return FondChoisi.EnLigne(FondCarte.OSM)
    if (v.startsWith(PREFIXE_MBTILES)) {
        val fichier = v.removePrefix(PREFIXE_MBTILES)
        return if (mbtilesExiste(fichier)) FondChoisi.Mbtiles(fichier) else FondChoisi.EnLigne(FondCarte.OSM)
    }
    return FondChoisi.EnLigne(runCatching { FondCarte.valueOf(v) }.getOrDefault(FondCarte.OSM))
}

/** Mémorise le fond choisi (en ligne : nom de l'énumération ; MBTiles : `mbtiles:<nom de fichier>`). */
fun enregistrerFondChoisi(preferences: PreferencesGeoMys, fond: FondChoisi) {
    preferences.fondCarte = when (fond) {
        is FondChoisi.EnLigne -> fond.fond.name
        is FondChoisi.Mbtiles -> PREFIXE_MBTILES + fond.fichier
    }
}
