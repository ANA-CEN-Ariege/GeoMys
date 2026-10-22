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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/ExplorerFragment.kt) : la
// garde de largeur, le regroupement des marqueurs, le filtre par groupe et les textes de l'écran. La requête
// sur l'emprise visible est GeoNatureBrowse.recupererObsExplorer (lot 2).

package fr.ariegenature.geomys.explorer

import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.model.ObsExplorer
import fr.ariegenature.geomys.model.Taxon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong

/** Les observations d'une cellule d'environ 110 m : un seul marqueur, à la position de la première,
 *  de la couleur de son groupe, l'effectif écrit dessus s'il y en a plusieurs. */
data class GroupeObs(val cle: String, val latitude: Double, val longitude: Double, val observations: List<ObsExplorer>) {
    val premiere: ObsExplorer get() = observations.first()

    /** Le groupe du marqueur : celui de la première observation qui en a un, la couleur d'avant les
     *  observations sans groupe ; null si aucune n'en a (icône neutre). */
    val taxon: Taxon? get() = observations.firstNotNullOfOrNull { it.taxon }
}

/** L'Explorer : les observations de la synthèse GeoNature sur la zone visible. */
object Explorer {

    /** Au-delà de cette largeur de carte, rien n'est demandé au serveur. */
    const val LARGEUR_MAX_KM = 100.0

    /** Délai entre le dernier mouvement de la carte et la requête. */
    const val DELAI_MS = 800L

    const val TITRE = "Explorer"
    const val NON_CONFIGURE = "Configurez GeoNature pour explorer les observations"
    const val CHARGEMENT = "Chargement des observations…"
    val ZONE_TROP_LARGE = "Zoomez pour charger les observations (zone > ${LARGEUR_MAX_KM.toInt()} km)"

    fun erreur(message: String?) = "Erreur : $message"

    /** Les groupes de la barre des filtres, dans l'ordre de GeoMys-Android. */
    val GROUPES = listOf(
        Taxon.OISEAU, Taxon.MAMMIFERE, Taxon.REPTILE, Taxon.BATRACIEN, Taxon.MOLLUSQUE,
        Taxon.POISSON, Taxon.INSECTE, Taxon.INVERTEBRES, Taxon.PLANTE, Taxon.FONGE,
    )

    /** Le nom d'un bouton de la barre, lu par TalkBack et VoiceOver. */
    fun descriptionGroupe(taxon: Taxon): String = when (taxon) {
        Taxon.OISEAU -> "Oiseaux"
        Taxon.MAMMIFERE -> "Mammifères"
        Taxon.REPTILE -> "Reptiles"
        Taxon.BATRACIEN -> "Batraciens"
        Taxon.MOLLUSQUE -> "Mollusques"
        Taxon.POISSON -> "Poissons"
        Taxon.INSECTE -> "Insectes"
        Taxon.INVERTEBRES -> "Autres invertébrés"
        Taxon.PLANTE -> "Plantes"
        Taxon.FONGE -> "Fonge"
    }

    // ─── Position de départ ───

    /** Sans position connue : le centre de la France, au zoom 6. */
    const val LATITUDE_FRANCE = 46.5
    const val LONGITUDE_FRANCE = 2.5
    const val ZOOM_FRANCE = 6.0

    /** Sur la dernière position connue du téléphone, au lancement. */
    const val ZOOM_DERNIERE_POSITION = 12.0

    /** Sur la première position reçue, quand la carte partait du centre de la France. */
    const val ZOOM_PREMIERE_POSITION = 16.0

    /** Largeur est-ouest de l'[emprise], en km : Δlon × 111 km × cos(latitude moyenne). */
    fun largeurKm(emprise: Emprise): Double =
        (emprise.est - emprise.ouest) * 111.0 * cos((emprise.nord + emprise.sud) / 2.0 * PI / 180.0)

    /** Les observations par cellule de 0,001° (environ 110 m), quel que soit le zoom ; les cellules dans
     *  l'ordre de leur première observation. */
    fun regrouper(observations: List<ObsExplorer>): List<GroupeObs> =
        observations
            .groupBy { "${(it.latitude * 1000).roundToLong()},${(it.longitude * 1000).roundToLong()}" }
            .map { (cle, groupe) -> GroupeObs(cle, groupe.first().latitude, groupe.first().longitude, groupe) }

    /** Les observations du groupe [taxon], toutes sans filtre (celles sans groupe comprises). */
    fun filtrer(observations: List<ObsExplorer>, taxon: Taxon?): List<ObsExplorer> =
        if (taxon == null) observations else observations.filter { it.taxon == taxon }

    /** La profondeur d'historique du serveur (`area_observation_duration`), en mois quand elle tombe juste :
     *  « 12 mois » pour les 365 jours par défaut. GeoMys-Android écrivait « 12 mois » quelle qu'elle
     *  soit (défaut n° 10 de l'inventaire). */
    fun duree(jours: Int): String = when {
        jours > 0 && jours % 365 == 0 -> "${jours / 365 * 12} mois"
        jours > 0 && jours % 30 == 0 -> "${jours / 30} mois"
        else -> "$jours j"
    }

    /** Le compteur, sous la carte comme en tête de la liste d'une cellule : « 42 obs. (12 mois) ». */
    fun compteur(nombre: Int, jours: Int) = "$nombre obs. (${duree(jours)})"

    /** La liste d'une cellule : la plus récente d'abord. */
    fun liste(groupe: GroupeObs): List<ObsExplorer> = groupe.observations.sortedByDescending { it.date }

    /** Nom d'une observation : le vernaculaire, sinon le nom cité, une capitale en tête. */
    fun titre(obs: ObsExplorer): String = obs.nomVern.ifEmpty { obs.nomCite }.replaceFirstChar { it.uppercaseChar() }

    /** « 2026-05-12 · 3 ind. · Parus major » : l'effectif au-delà d'un individu, le nom scientifique s'il
     *  diffère du nom affiché. */
    fun detail(obs: ObsExplorer): String {
        val nom = obs.nomVern.ifEmpty { obs.nomCite }
        return buildString {
            if (obs.date.isNotEmpty()) append(obs.date)
            if (obs.nombre > 1) {
                if (isNotEmpty()) append(" · ")
                append("${obs.nombre} ind.")
            }
            if (obs.nomSci.isNotEmpty() && obs.nomSci != nom) {
                if (isNotEmpty()) append(" · ")
                append(obs.nomSci)
            }
        }
    }
}
