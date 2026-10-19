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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/FilAriane.kt) : les segments
// du fil d'Ariane des Suivis et leur encodage dans l'argument de navigation `fil`, mêmes séparateurs. Le
// rendu cliquable est dans l'interface (ui/composants/FilAriane.kt), la navigation dans le graphe.

package fr.ariegenature.geomys.monitoring

/** Marqueurs de [FilSegment.type] pour les niveaux racine fixes (la cible de leur clic
 *  ne dépend pas d'un id d'objet mais d'une destination fixe). Les segments objets, eux,
 *  portent leur vrai `object_type`. */
const val FIL_TYPE_ACCUEIL = "__accueil__"
const val FIL_TYPE_SUIVIS = "__suivis__"
const val FIL_TYPE_MODULE = "__module__"

/** Un niveau du fil. Pour les segments objets, [type]/[id] sont l'`object_type` et l'id
 *  serveur. Pour les racines [FIL_TYPE_SUIVIS]/[FIL_TYPE_MODULE], seul [label] est affiché —
 *  la destination est fixe (liste des protocoles / détail du protocole courant). */
data class FilSegment(val type: String, val id: Int, val label: String)

/** Préfixe racine commun à tous les fils de suivi : "🏠 › Suivis › <protocole>". L'entrée
 *  ACCUEIL est rendue sous forme d'icône maison cliquable pour permettre de revenir à l'écran
 *  d'accueil depuis n'importe quelle profondeur de drill. [moduleLabel] = libellé du protocole
 *  courant. */
fun filRacineSuivis(moduleLabel: String): List<FilSegment> = listOf(
    FilSegment(FIL_TYPE_ACCUEIL, -1, "Accueil"),
    FilSegment(FIL_TYPE_SUIVIS, -1, "Monitoring"),
    FilSegment(FIL_TYPE_MODULE, -1, moduleLabel),
)

// Séparateurs de sérialisation : caractères de contrôle (RS / US) absents des noms.
private const val SEP_SEGMENT = "\u001E"
private const val SEP_CHAMP = "\u001F"

/** Sérialise les segments pour les passer dans un argument de navigation. */
fun encoderFil(segments: List<FilSegment>): String =
    segments.joinToString(SEP_SEGMENT) { "${it.type}$SEP_CHAMP${it.id}$SEP_CHAMP${it.label}" }

/** Inverse de [encoderFil]. Tolère null/vide → liste vide. */
fun decoderFil(encode: String?): List<FilSegment> {
    if (encode.isNullOrEmpty()) return emptyList()
    return encode.split(SEP_SEGMENT).mapNotNull { seg ->
        val parts = seg.split(SEP_CHAMP)
        if (parts.size < 3) return@mapNotNull null
        // Le label peut théoriquement contenir SEP_CHAMP : on rejoint le reste par sécurité.
        FilSegment(parts[0], parts[1].toIntOrNull() ?: -1, parts.drop(2).joinToString(SEP_CHAMP))
    }
}
