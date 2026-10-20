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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/EditeurGeometrie.kt et
// PileAnnulationGeometrie.kt) : l'état de la géométrie en cours d'édition et ses gestes, sans leur rendu
// osmdroid. Les listes mutables de l'éditeur deviennent une valeur immuable : chaque geste en rend une
// nouvelle, que l'écran observe, et la pile d'annulation en garde les précédentes sans les recopier.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.geo.airePolygoneM2
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.util.GeoJsonCoords
import kotlin.math.roundToLong

/** Un point unique, déplaçable, ou un anneau de sommets : les deux façons de poser une station. */
enum class ModeGeometrie { POINT, POLYGONE }

/** Poignée « + » au milieu de l'arête [indexArete] → [indexArete] + 1 (fermeture incluse) de
 *  l'anneau [anneau] : -1 pour le contour extérieur, sinon le rang du trou. */
data class PoigneeArete(val anneau: Int, val indexArete: Int, val position: LatLon)

/** La géométrie posée sur une station : ce que `definirGeometrie` reçoit. */
data class GeometrieStation(
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val coordsJson: String?,
    val trousJson: String?,
)

/**
 * La géométrie EN COURS D'ÉDITION sur la carte OccHab : le [mode], le point posé, l'anneau
 * extérieur ([sommets], DISTINCTS, sans point de fermeture) et ses [trous].
 *
 * Les trous viennent du serveur (stations dessinées sous QGIS) : l'appli n'en CRÉE pas, mais
 * ils sont ÉDITABLES sommet par sommet (demande terrain 2026-08-31) — mêmes gestes que l'anneau
 * extérieur, deux différences assumées : un sommet de trou ne s'AIMANTE pas aux stations voisines,
 * et l'on AVERTIT s'il sort du polygone (géométrie que PostGIS refuserait).
 */
data class GeometrieEdition(
    val mode: ModeGeometrie = ModeGeometrie.POINT,
    val point: LatLon? = null,
    val sommets: List<LatLon> = emptyList(),
    val trous: List<List<LatLon>> = emptyList(),
) {
    /** Ni point ni sommet posé. */
    val vide: Boolean get() = point == null && sommets.isEmpty()

    /** « Valider » est possible : un point posé, ou trois sommets. */
    val geomOk: Boolean
        get() = (mode == ModeGeometrie.POINT && point != null) || (mode == ModeGeometrie.POLYGONE && sommets.size >= 3)

    /** Tap sur la carte (déjà aimanté) : le point, ou un sommet de plus au bout de l'anneau. */
    fun avecTap(cible: LatLon): GeometrieEdition =
        if (mode == ModeGeometrie.POINT) copy(point = cible) else copy(sommets = sommets + cible)

    fun avecSommet(index: Int, position: LatLon): GeometrieEdition =
        if (index !in sommets.indices) this else copy(sommets = sommets.toMutableList().also { it[index] = position })

    fun avecSommetTrou(anneau: Int, index: Int, position: LatLon): GeometrieEdition {
        if (anneau !in trous.indices || index !in trous[anneau].indices) return this
        return copy(trous = trous.mapIndexed { r, ring -> if (r != anneau) ring else ring.toMutableList().also { it[index] = position } })
    }

    /** Insère un sommet au MILIEU de l'arête [indexArete] → [indexArete] + 1 (fermeture incluse).
     *  L'insertion se fait toujours sur l'arête visée, jamais en fin d'anneau : aucun polygone
     *  auto-croisé possible (mécanisme des candidats de QField). Null si l'arête n'existe pas. */
    fun avecSommetInsere(indexArete: Int): GeometrieEdition? {
        if (indexArete !in sommets.indices) return null
        val milieu = TopologiePolygone.milieu(sommets[indexArete], sommets[(indexArete + 1) % sommets.size])
        return copy(sommets = sommets.toMutableList().also { it.add(indexArete + 1, milieu) })
    }

    /** Même chose sur une arête d'un TROU. */
    fun avecSommetTrouInsere(anneau: Int, indexArete: Int): GeometrieEdition? {
        if (anneau !in trous.indices) return null
        val ring = trous[anneau]
        if (indexArete !in ring.indices) return null
        val milieu = TopologiePolygone.milieu(ring[indexArete], ring[(indexArete + 1) % ring.size])
        return copy(trous = trous.mapIndexed { r, autre -> if (r != anneau) autre else autre.toMutableList().also { it.add(indexArete + 1, milieu) } })
    }

    /** Poignées « + » au milieu de chaque arête — contour extérieur puis trous — sur une station
     *  SÉLECTIONNÉE ([chargee]) d'au moins trois sommets : pendant le tracé d'une NOUVELLE
     *  géométrie, un sommet s'ajoute d'un tap, en bout d'anneau. */
    fun poignees(chargee: Boolean): List<PoigneeArete> {
        if (!chargee || mode != ModeGeometrie.POLYGONE) return emptyList()
        val exterieur = if (sommets.size < 3) emptyList() else sommets.indices.map { i ->
            PoigneeArete(-1, i, TopologiePolygone.milieu(sommets[i], sommets[(i + 1) % sommets.size]))
        }
        val desTrous = trous.flatMapIndexed { r, ring ->
            if (ring.size < 3) emptyList() else ring.indices.map { i -> PoigneeArete(r, i, TopologiePolygone.milieu(ring[i], ring[(i + 1) % ring.size])) }
        }
        return exterieur + desTrous
    }

    /** Sommet de trou sorti de l'anneau extérieur : la géométrie devient invalide au sens PostGIS
     *  et le serveur peut refuser la mise à jour. On avertit, sans bloquer. */
    fun sommetTrouHorsPolygone(p: LatLon): Boolean = sommets.size >= 3 && !TopologiePolygone.pointDansAnneau(p, sommets)

    /** Anneaux intérieurs tels qu'ÉDITÉS → JSON à persister. Null quand il n'y en a pas : un tracé
     *  REDESSINÉ repart plein (la liste est vidée en même temps que [sommets]). */
    fun trousJson(): String? = GeoJsonCoords.formatAnneaux(trous.filter { it.size >= 3 })

    /** Surface NETTE (m², arrondie) d'un polygone : les trous sont déduits, comme ST_Area côté
     *  serveur. Null pour un point. */
    fun surfaceM2(): Long? =
        if (mode == ModeGeometrie.POLYGONE) airePolygoneM2(sommets, trous).roundToLong() else null

    /** La géométrie à poser sur la station ; null tant qu'elle n'est pas valide. Le centroïde d'un
     *  polygone est la moyenne de ses sommets ; ses trous sont conservés tels qu'édités. */
    fun geometrie(): GeometrieStation? = when (mode) {
        ModeGeometrie.POINT -> point?.let { GeometrieStation("Point", it.latitude, it.longitude, null, null) }
        ModeGeometrie.POLYGONE -> {
            if (sommets.size < 3) {
                null
            } else {
                GeoJsonCoords.centroide(sommets)?.let { centre ->
                    GeometrieStation("Polygon", centre.latitude, centre.longitude, GeoJsonCoords.format(sommets), trousJson())
                }
            }
        }
    }

    companion object {
        /** La géométrie d'une station EXISTANTE, chargée pour l'éditer (`preremplirDepuisViewModel`) ;
         *  null si elle n'en a pas. Ses trous sont chargés éditables. */
        fun depuisStation(s: OccHabStation): GeometrieEdition? = when {
            s.geometryType == "Polygon" && !s.geometryCoordsJson.isNullOrEmpty() -> {
                val ring = GeoJsonCoords.parse(s.geometryCoordsJson)
                if (ring.isEmpty()) null else GeometrieEdition(ModeGeometrie.POLYGONE, null, ring, GeoJsonCoords.parseAnneaux(s.geometryTrousJson))
            }
            s.geometryType == "Point" && (s.latitude != 0.0 || s.longitude != 0.0) ->
                GeometrieEdition(ModeGeometrie.POINT, LatLon(s.latitude, s.longitude))
            else -> null
        }
    }
}

/**
 * Historique des opérations de géométrie OccHab — « Annuler » = la DERNIÈRE opération, et non
 * le dernier sommet (demande terrain 2026-08-30).
 *
 * Un instantané est pris AVANT chaque opération (tap, déplacement, insertion de sommet) et
 * n'emporte que la géométrie EN COURS : « Annuler » ne touche pas aux stations voisines, même
 * quand la topologie partagée les a modifiées (décision de l'utilisateur du 17 octobre sous
 * GeoMys-Android, gardée quand la topologie est revenue le 20 octobre) — annuler son propre geste
 * ne doit pas modifier le travail enregistré sur d'autres stations. Un sommet partagé annulé d'un
 * côté seulement désolidarise donc les deux polygones ; un nouvel aimantage les raccorde.
 */
class PileAnnulationGeometrie(private val profondeurMax: Int = 30) {

    private val pile = ArrayDeque<GeometrieEdition>()

    val estVide: Boolean get() = pile.isEmpty()

    /** Capture l'état AVANT une opération. */
    fun empiler(instantane: GeometrieEdition) {
        pile.addLast(instantane)
        // Borne mémoire : une saisie de terrain peut enchaîner des centaines de sommets.
        while (pile.size > profondeurMax) pile.removeFirst()
    }

    fun depiler(): GeometrieEdition? = pile.removeLastOrNull()

    fun vider() = pile.clear()
}
