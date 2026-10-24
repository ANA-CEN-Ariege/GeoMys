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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/TraceFragment.kt) : ce que
// la carte de saisie multi-taxons calcule — relevés à dessiner, géométrie validée, cadrage d'une
// reprise, textes du positionnement et des options d'un relevé — sorti des overlays osmdroid.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.location.LocationTracker
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.PointTrace
import fr.ariegenature.geomys.util.FormatDate
import fr.ariegenature.geomys.util.GeoJsonCoords

/** Mode de saisie de la géométrie d'un nouveau relevé. POINT par défaut. */
enum class ModeGeom { POINT, LINE, POLYGON }

/** Un relevé ponctuel sur la carte : un seul marqueur pour toutes ses observations. */
data class RelevePoint(val cle: String, val position: LatLon, val titre: String, val observations: List<Observation>)

/** Un relevé ligne ou polygone : sa forme, ses sommets déplaçables, les obs qu'ils portent. */
data class ReleveForme(val cle: String, val type: String, val sommets: List<LatLon>, val ids: List<String>)

/** Géométrie validée d'un nouveau relevé : type GeoJSON, sommets (`[[lon,lat], …]`, null pour un
 *  point) et position (le point, ou la moyenne des sommets). */
data class GeometrieFinale(val type: String, val coordsJson: String?, val latitude: Double, val longitude: Double)

/** Cadrage de la carte sur une saisie reprise. */
sealed interface CadrageReprise {
    /** Un seul point distinct (cas fréquent en multi-taxons) : centrage, zoom 18. */
    data class Point(val centre: LatLon) : CadrageReprise

    /** Emprise, épaissie sur un axe trop plat. */
    data class Emprise(val sud: Double, val ouest: Double, val nord: Double, val est: Double) : CadrageReprise
}

object CarteSaisie {

    /** Identité stable d'un relevé (insensible aux changements de sommets). */
    fun cleReleve(obs: Observation): String = obs.releveId?.takeIf { it.isNotEmpty() } ?: obs.id

    private fun aUneForme(obs: Observation): Boolean =
        (obs.geometryType == "Polygon" || obs.geometryType == "LineString") && !obs.geometryCoordsJson.isNullOrEmpty()

    /** UN SEUL marqueur par RELEVÉ (point) — clé = identité du relevé. Les obs d'un même releveId
     *  partagent exactement le même point ; dessiner N marqueurs superposés (1 par taxon) faisait
     *  apparaître « plusieurs points au même endroit ». Les relevés ligne/polygone n'ont pas de
     *  marqueur. */
    fun relevesPoints(observations: List<Observation>): List<RelevePoint> =
        observations.filterNot(::aUneForme).groupBy(::cleReleve).map { (cle, obsReleve) ->
            val rep = obsReleve.first()
            val titre = if (obsReleve.size > 1) "${obsReleve.size} espèces" else rep.espece
            RelevePoint(cle, LatLon(rep.latitude, rep.longitude), titre, obsReleve)
        }

    /** Les relevés ligne ou polygone, avec leurs sommets ; une forme sans assez de sommets n'est pas
     *  dessinée. */
    fun relevesFormes(observations: List<Observation>): List<ReleveForme> =
        observations.filter(::aUneForme).groupBy(::cleReleve).mapNotNull { (cle, obsReleve) ->
            val rep = obsReleve.first()
            val type = rep.geometryType!!
            val sommets = GeoJsonCoords.parse(rep.geometryCoordsJson)
            if (type == "Polygon" && sommets.size < 3) return@mapNotNull null
            if (type == "LineString" && sommets.size < 2) return@mapNotNull null
            ReleveForme(cle, type, sommets, obsReleve.map { it.id })
        }

    /** Les obs déplacées avec un marqueur : toutes celles de son relevé multi-taxons (un relevé =
     *  un point côté OCCTAX), sinon l'obs seule. */
    fun obsDuReleve(toutes: List<Observation>, obs: Observation): List<Observation> =
        if (!obs.releveId.isNullOrEmpty()) toutes.filter { it.releveId == obs.releveId } else listOf(obs)

    /** Géométrie au moment de la validation, à partir des sommets posés au doigt. En mode POINT
     *  sans sommet, repli sur le centre de la carte ([centre]). Null s'il manque des sommets (au
     *  moins 2 pour une ligne, 3 pour un polygone, cf. [sommetsMinimum]). */
    fun geometrieFinale(mode: ModeGeom, sommets: List<LatLon>, centre: LatLon): GeometrieFinale? = when (mode) {
        ModeGeom.POINT -> {
            val p = sommets.firstOrNull() ?: centre
            GeometrieFinale("Point", null, p.latitude, p.longitude)
        }
        ModeGeom.LINE, ModeGeom.POLYGON -> {
            if (sommets.size < sommetsMinimum(mode)) {
                null
            } else {
                GeometrieFinale(
                    type = if (mode == ModeGeom.LINE) "LineString" else "Polygon",
                    coordsJson = GeoJsonCoords.format(sommets),
                    latitude = sommets.map { it.latitude }.average(),
                    longitude = sommets.map { it.longitude }.average(),
                )
            }
        }
    }

    fun sommetsMinimum(mode: ModeGeom): Int = if (mode == ModeGeom.LINE) 2 else 3

    /** Pose ou remplace un sommet : en POINT, chaque toucher donne le nouvel emplacement ; en LINE
     *  et POLYGON, le sommet s'ajoute en fin de liste. */
    fun poserSommet(mode: ModeGeom, sommets: List<LatLon>, p: LatLon): List<LatLon> =
        if (mode == ModeGeom.POINT) listOf(p) else sommets + p

    /** Le cadrage sur les obs et la trace d'une saisie reprise. Les points (0,0) (obs ou trace sans
     *  position fixée) sont écartés, sinon l'emprise s'étirerait jusqu'au golfe de Guinée ; les
     *  sommets des lignes et polygones comptent, sinon on ne cadrerait que leur centroïde. Null
     *  sans aucun point. */
    fun cadrageReprise(observations: List<Observation>, parcours: List<PointTrace>): CadrageReprise? {
        val points = mutableListOf<LatLon>()
        observations.forEach {
            if (it.latitude != 0.0 || it.longitude != 0.0) points.add(LatLon(it.latitude, it.longitude))
            points.addAll(GeoJsonCoords.parse(it.geometryCoordsJson))
        }
        parcours.forEach {
            if (it.latitude != 0.0 || it.longitude != 0.0) points.add(LatLon(it.latitude, it.longitude))
        }
        if (points.isEmpty()) return null
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latSpan = maxLat - minLat
        val lonSpan = maxLon - minLon
        // Emprise quasi nulle : un cadrage sur une boîte d'aire nulle donne un zoom aberrant.
        if (latSpan < 1e-4 && lonSpan < 1e-4) return CadrageReprise.Point(LatLon((minLat + maxLat) / 2, (minLon + maxLon) / 2))
        // Une ligne ~horizontale/verticale donne une boîte d'épaisseur ~0 : épaisseur minimale
        // (~50 m) sur l'axe plat.
        val padLat = if (latSpan < 5e-4) 5e-4 else 0.0
        val padLon = if (lonSpan < 5e-4) 5e-4 else 0.0
        return CadrageReprise.Emprise(minLat - padLat, minLon - padLon, maxLat + padLat, maxLon + padLon)
    }

    /** Consigne du bandeau de positionnement. */
    fun consignePositionnement(repositionnement: Boolean, mode: ModeGeom): String = when {
        repositionnement -> "Déplacez la carte pour repositionner l'observation"
        mode == ModeGeom.LINE -> "Touchez pour ajouter des sommets · appui long pour déplacer"
        mode == ModeGeom.POLYGON -> "Touchez pour ajouter des sommets (≥ 3) · appui long pour déplacer"
        else -> "Touchez pour placer le point · appui long pour le déplacer"
    }

    /** Libellé du bouton de validation. */
    fun libelleValidation(repositionnement: Boolean, mode: ModeGeom): String = when {
        repositionnement -> "Valider ce point"
        mode == ModeGeom.LINE -> "Valider cette ligne"
        mode == ModeGeom.POLYGON -> "Valider ce polygone"
        else -> "Valider ce point"
    }

    /** Titre et message du dialogue d'options d'un relevé touché sur la carte : « Merle noir ·
     *  08:12 » ou « 3 espèces · 08:12 », puis une espèce par ligne. L'heure est celle du relevé
     *  (« Détails », dateDebutReleve) si elle a été fixée, sinon l'heure de saisie. */
    fun optionsReleve(observations: List<Observation>): Pair<String, String> {
        val premiere = observations.first()
        val especes = observations.joinToString("\n") { o ->
            val n = if (o.totalIndividus > 1) " × ${o.totalIndividus}" else ""
            val notes = if (o.notes.isNotEmpty()) " — ${o.notes}" else ""
            "• ${o.espece}$n$notes"
        }
        val heure = FormatDate.heure(premiere.dateDebutReleve ?: premiere.date)
        val titre = if (observations.size == 1) "${premiere.espece} · $heure" else "${observations.size} espèces · $heure"
        return titre to especes
    }

    /** ▶ / ⏸ du parcours : arrêter, sinon CONTINUER une trace non vide (un aller-retour par l'accueil
     *  la met en pause ; la recommencer effacerait le parcours de la journée — défaut du portage
     *  iOS), sinon démarrer. */
    fun basculerParcours(tracker: LocationTracker) {
        when {
            tracker.estEnCours.value -> tracker.arreterParcours()
            tracker.parcours.value.isNotEmpty() -> tracker.reprendreParcours()
            else -> tracker.demarrerParcours()
        }
    }

    /** Le message du bonhomme de la carte multi-taxons (demande du 24 octobre 2026), selon
     *  l'état de la trace AVANT le toucher : [enCours], ou déjà [commencee] (elle reprend alors). */
    fun messageTrace(enCours: Boolean, commencee: Boolean): String = when {
        enCours -> "La trace GPS est arrêtée."
        commencee -> "La trace GPS reprend."
        else -> "La trace GPS démarre."
    }

    /** « Enregistrer et envoyer à GeoNature » n'est proposé qu'avec une appli configurée et au moins
     *  une obs déterminée ou un relevé sans espèce. */
    fun peutEnvoyer(observations: List<Observation>, configuree: Boolean): Boolean =
        configuree && observations.any { it.cdNom != null || it.releveSansEspece }
}
