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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SortieDetailFragment.kt,
// setupMap et setupButtons) : ce que la carte de consultation montre d'une sortie, sorti des
// overlays osmdroid.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.util.FormatDate
import fr.ariegenature.geomys.util.GeoJsonCoords

/** Ce qu'un relevé dessine sur la carte de consultation. */
sealed interface FormeReleve {
    /** Marqueur, au point du relevé (ou géométrie absente). */
    data class Point(val position: LatLon) : FormeReleve

    data class Ligne(val sommets: List<LatLon>) : FormeReleve

    data class Polygone(val sommets: List<LatLon>) : FormeReleve
}

/** Un groupe d'observations au même point, avec le texte de sa bulle. */
data class ReleveAffiche(
    val forme: FormeReleve,
    val titre: String,
    /** Contenu de la bulle, vide s'il n'y en a pas. */
    val contenu: String,
)

/** Le cadrage initial de la carte. */
sealed interface CadrageSortie {
    /** Sortie vide : la France entière. */
    data object France : CadrageSortie

    /** Emprise quasi nulle (un seul point) : centrée, zoom 15. */
    data class Point(val centre: LatLon) : CadrageSortie

    /** Emprise de tous les points, agrandie de 40 %. */
    data class Emprise(val sud: Double, val ouest: Double, val nord: Double, val est: Double) : CadrageSortie
}

object AffichageSortie {

    /** Centre de la France (sortie vide), au zoom 6. */
    val CENTRE_FRANCE = LatLon(46.5, 2.5)
    const val ZOOM_FRANCE = 6.0
    const val ZOOM_POINT = 15.0

    /** Agrandissement de l'emprise (`BoundingBox.increaseByScale(1.4f)`). */
    const val MARGE_EMPRISE = 1.4

    /** Les relevés de la carte. Plusieurs obs peuvent partager le même point (saisie multi-taxons
     *  d'un coup) : on regroupe par coordonnées arrondies (1e-6° ≈ 11 cm) — sinon un seul marqueur
     *  est cliquable et masque les autres espèces du même point. */
    fun releves(sortie: Sortie): List<ReleveAffiche> {
        val groupes = sortie.observations.groupBy {
            Pair((it.latitude * 1_000_000).toLong(), (it.longitude * 1_000_000).toLong())
        }
        return groupes.values.map { obsGroup ->
            val rep = obsGroup.first()
            // Géométrie du relevé (ligne / polygone) : redessinée à partir des sommets stockés,
            // sinon on ne verrait que le centroïde (un simple point).
            val sommets = GeoJsonCoords.parse(rep.geometryCoordsJson)
            val forme = when {
                rep.geometryType == "Polygon" && sommets.size >= 3 -> FormeReleve.Polygone(sommets)
                rep.geometryType == "LineString" && sommets.size >= 2 -> FormeReleve.Ligne(sommets)
                else -> FormeReleve.Point(LatLon(rep.latitude, rep.longitude))
            }
            val (titre, contenu) = textes(obsGroup)
            ReleveAffiche(forme, titre, contenu)
        }
    }

    /** Titre et contenu de la bulle d'un groupe. */
    fun textes(obsGroup: List<Observation>): Pair<String, String> {
        val rep = obsGroup.first()
        // Heure du relevé (Détails / dateDebutReleve) si fixée, sinon heure de saisie — même règle
        // que l'envoi (date_min) et le dialogue Détails ; sinon une heure modifiée a posteriori
        // n'apparaîtrait pas sur la carte.
        val heureReleve = FormatDate.heure(rep.dateDebutReleve ?: rep.date)
        return if (obsGroup.size == 1) {
            var sub = heureReleve
            if (rep.totalIndividus > 1) sub += " · ${rep.totalIndividus} ind."
            if (rep.notes.isNotEmpty()) sub += " — ${rep.notes}"
            rep.espece to sub
        } else {
            // Une espèce par ligne : toute la liste s'affiche, même au-delà de 3 espèces.
            "${obsGroup.size} espèces · $heureReleve" to obsGroup.joinToString("\n") { o ->
                val n = if (o.totalIndividus > 1) " × ${o.totalIndividus}" else ""
                val notes = if (o.notes.isNotEmpty()) " — ${o.notes}" else ""
                "• ${o.espece}$n$notes"
            }
        }
    }

    /** Le cadrage : la trace, les observations et les sommets des lignes et polygones. */
    fun cadrage(sortie: Sortie, releves: List<ReleveAffiche> = releves(sortie)): CadrageSortie {
        val sommets = releves.flatMap { r ->
            when (val f = r.forme) {
                is FormeReleve.Ligne -> f.sommets
                is FormeReleve.Polygone -> f.sommets
                is FormeReleve.Point -> emptyList()
            }
        }
        val points = sortie.pointsParcours.map { LatLon(it.latitude, it.longitude) } +
            sortie.observations.map { LatLon(it.latitude, it.longitude) } + sommets
        if (points.isEmpty()) return CadrageSortie.France
        val sud = points.minOf { it.latitude }
        val nord = points.maxOf { it.latitude }
        val ouest = points.minOf { it.longitude }
        val est = points.maxOf { it.longitude }
        if (nord - sud < 0.0001 && est - ouest < 0.0001) return CadrageSortie.Point(points[0])
        val demiLat = (nord - sud) * MARGE_EMPRISE / 2
        val demiLon = (est - ouest) * MARGE_EMPRISE / 2
        val cLat = (nord + sud) / 2
        val cLon = (est + ouest) / 2
        return CadrageSortie.Emprise(cLat - demiLat, cLon - demiLon, cLat + demiLat, cLon + demiLon)
    }

    /** Bouton « Envoyer vers GeoNature » : non envoyée, non importée, appli configurée, droit de
     *  création OCCTAX (CRUVED C — cet écran l'omettait, un utilisateur sans droit se prenait un
     *  403 opaque) et au moins une obs déterminée ou un relevé sans espèce. */
    fun peutEnvoyer(sortie: Sortie, configuree: Boolean, peutCreer: Boolean): Boolean =
        !sortie.envoyeGeoNature && !sortie.estImportee && configuree && peutCreer &&
            sortie.observations.any { it.cdNom != null || it.releveSansEspece }

    /** Nom du fichier GPX exporté. */
    fun nomFichierGpx(sortie: Sortie): String = "sortie_${sortie.id.take(8)}.gpx"
}
