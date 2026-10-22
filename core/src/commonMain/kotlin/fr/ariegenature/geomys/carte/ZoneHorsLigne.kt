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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/CacheManagerFragment.kt) :
// l'estimation de la zone visible (`majInfos`), ses plafonds, et les textes du Maps Manager. Le
// téléchargement passe par les paquets hors ligne de MapLibre, au lieu du CacheManager d'osmdroid.

package fr.ariegenature.geomys.carte

import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.geo.TuileMath
import fr.ariegenature.geomys.util.formaterDecimal
import fr.ariegenature.geomys.util.formaterMilliers
import kotlin.math.floor

/** Ce que le panneau du Maps Manager dit de la zone visible. [avertissement] : la ligne rouge, s'il
 *  y en a une. [telechargeable] : le bouton « Charger les tuiles » est actif. */
data class EstimationZone(
    val surface: String,
    val tuiles: String,
    val avertissement: String?,
    val telechargeable: Boolean,
)

/** Le Maps Manager : la zone visible à télécharger, du zoom courant au zoom [ZOOM_MAX_DEFAUT], pour le
 *  seul fond affiché. Les niveaux de zoom sont ceux des tuiles de 256 px (osmdroid). */
object ZoneHorsLigne {

    /** Plafond de surface, pour les quotas des serveurs (IGN, clé gratuite) et la place sur l'appareil. */
    const val SURFACE_MAX_KM2 = 200.0

    /** Dernier niveau téléchargé : sentiers et bâti. */
    const val ZOOM_MAX_DEFAUT = 17

    /** Occupation de référence, celle du cache d'osmdroid (1 Go). */
    const val CACHE_MAX_OCTETS = 1L shl 30

    /** Au-delà de ce taux d'occupation, la ligne du cache passe en rouge. */
    const val SEUIL_ALERTE_CACHE = 90.0

    const val TITRE = "Maps Manager"
    const val BADGE = "Cache des fonds"
    const val CHARGER = "Charger les tuiles"
    const val CARTES_HORS_LIGNE = "Cartes hors-ligne (MBTiles)…"
    const val POSITION_INDISPONIBLE = "Position GPS pas encore disponible"

    /** Position de départ, au centre de l'Ariège, en attendant la position du téléphone. */
    const val LATITUDE_DEPART = 42.96
    const val LONGITUDE_DEPART = 1.43
    const val ZOOM_DEPART = 12.0

    /** Zoom du recadrage sur la position du téléphone. */
    const val ZOOM_POSITION = 15.0

    /** Poids moyen d'une tuile, en Ko : 25 pour les fonds en PNG, 50 pour les photos et le SCAN 25. Il
     *  ne sert qu'à l'estimation affichée. */
    fun poidsTuileKo(fond: FondCarte): Int = when (fond) {
        FondCarte.OSM, FondCarte.TOPO, FondCarte.OPENTOPO -> 25
        FondCarte.SCAN25, FondCarte.ORTHO, FondCarte.ESRI -> 50
    }

    /** Nom court du fond, dans le panneau et les messages. */
    fun nomFond(fond: FondCarte): String = when (fond) {
        FondCarte.OSM -> "OSM"
        FondCarte.OPENTOPO -> "OpenTopoMap"
        FondCarte.TOPO -> "IGN Topo"
        FondCarte.SCAN25 -> "IGN Scan25"
        FondCarte.ORTHO -> "IGN Ortho"
        FondCarte.ESRI -> "Esri Imagery"
    }

    /** Premier niveau téléchargé : le zoom affiché, tronqué, au moins 1. */
    fun zoomMin(zoomAffiche: Double): Int = floor(zoomAffiche).toInt().coerceAtLeast(1)

    /** Ce que le panneau affiche pour le [fond] et la zone visible [emprise], au zoom [zoomAffiche]. Un fond
     *  MBTiles est déjà hors ligne : rien à télécharger. */
    fun estimation(fond: FondChoisi, emprise: Emprise, zoomAffiche: Double): EstimationZone {
        val enLigne = (fond as? FondChoisi.EnLigne)?.fond
            ?: return EstimationZone("Fond hors-ligne (MBTiles)", "Déjà disponible hors-ligne — rien à télécharger", null, false)
        val surface = TuileMath.surfaceKm2(emprise)
        val zoomMin = zoomMin(zoomAffiche)
        val nombre = TuileMath.nombreDeTuiles(emprise, zoomMin, ZOOM_MAX_DEFAUT)
        val poidsMo = nombre * poidsTuileKo(enLigne) / 1024.0
        val depasse = surface > SURFACE_MAX_KM2
        return EstimationZone(
            surface = "Surface : ${formaterDecimal(surface, 1)} km² · zooms $zoomMin–$ZOOM_MAX_DEFAUT",
            tuiles = "≈ ${formaterMilliers(nombre.toLong())} tuiles · ~${formaterDecimal(poidsMo, 0)} MB · fond : ${nomFond(enLigne)}",
            avertissement = if (depasse) "Zone trop large (max ${SURFACE_MAX_KM2.toInt()} km²) — zoomer pour réduire." else null,
            telechargeable = !depasse && nombre > 0,
        )
    }

    /** « Cache : 120 / 1024 MB (12 %) », et vrai au-delà de [SEUIL_ALERTE_CACHE] : la ligne passe en rouge. */
    fun occupation(utilises: Long, max: Long = CACHE_MAX_OCTETS): Pair<String, Boolean> {
        val pourcent = if (max > 0) utilises * 100.0 / max else 0.0
        val texte = "Cache : ${utilises / (1024 * 1024)} / ${max / (1024 * 1024)} MB (${formaterDecimal(pourcent, 0)} %)"
        return texte to (pourcent >= SEUIL_ALERTE_CACHE)
    }

    // ─── Téléchargement ───

    const val TITRE_TELECHARGEMENT = "Téléchargement des tuiles"
    const val PREPARATION = "Préparation…"
    const val TELECHARGEMENT_EN_COURS = "Téléchargement en cours…"

    /** « 120 / 380 » : ressources reçues sur ressources attendues (MapLibre ne détaille pas les niveaux). */
    fun progression(recues: Long, attendues: Long): String = "$recues / $attendues"

    /** Le téléchargement s'annule (GeoMys-Android ne le permettait pas) ; les tuiles reçues restent. */
    const val ANNULER = "Annuler"
    const val TELECHARGEMENT_ANNULE = "Téléchargement annulé : les tuiles reçues restent en cache."

    const val TITRE_TERMINE = "Téléchargement terminé"
    fun messageTermine(fond: FondCarte): String =
        "Les tuiles du fond ${nomFond(fond)} sont disponibles hors-ligne pour la zone affichée."

    const val TITRE_INCOMPLET = "Téléchargement incomplet"
    const val MESSAGE_INCOMPLET = "Des tuiles n'ont pas pu être récupérées (serveur, réseau ou quota). Les autres sont en cache."
    fun messageLimite(limite: Long): String =
        "La limite de ${formaterMilliers(limite)} tuiles est atteinte : réduisez la zone. Les tuiles reçues sont en cache."

    // ─── Vidage ───

    const val TITRE_VIDER = "Vider le cache des tuiles ?"
    const val MESSAGE_VIDER = "Toutes les tuiles téléchargées (tous les fonds confondus) seront supprimées. " +
        "Tu devras les retélécharger pour un usage hors-ligne."
    const val TOUT_VIDER = "Tout vider"
    const val CACHE_VIDE = "Cache vidé"
    const val VIDAGE_PARTIEL = "Vidage partiel"
}
