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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabStationFragment.kt et
// OccHabStationsFragment.kt) : les textes de l'écran d'une station et les règles de « Mes stations ».

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.util.FormatDate
import fr.ariegenature.geomys.util.GeoJsonCoords
import fr.ariegenature.geomys.util.formaterDecimal

/** L'écran d'une station : ses habitats, ses détails, « Terminer ». */
object StationOccHab {

    const val DETAILS = "Détails"
    const val HABITATS = "Habitats"
    const val AUCUN_HABITAT = "Aucun habitat pour l'instant — il est facultatif."
    const val AJOUTER_HABITAT = "＋ Ajouter un habitat à la station"
    const val SAISIE_INTERROMPUE = "Saisie interrompue (application relancée) — reprenez depuis « Mes stations »"
    const val AUCUNE_MODIFICATION = "Aucune modification : la station reste telle quelle sur GeoNature"
    const val ENREGISTREE = "Votre station est enregistrée dans « Mes stations »"
    const val CONSIGNE_STATION = "Libérez de l'espace (photos, cache de cartes) puis réessayez sans quitter cet écran."
    const val CONSIGNE_HABITAT = "Libérez de l'espace (photos, cache de cartes) puis revalidez cet habitat."

    /** « Nouvelle station » pour une géométrie dessinée de zéro ([nouvelle], cf.
     *  [SessionOccHab.stationNouvelle]), « Modifier la station » pour une station reprise : de la
     *  saisie, d'une autre saisie ou du serveur. */
    fun titre(nouvelle: Boolean): String = if (nouvelle) "Nouvelle station" else "Modifier la station"

    /** « Géométrie : polygone (5 sommets, 1 trou conservé) » ou « Géométrie : point (42,93000,
     *  1,40000) » — les trous sont signalés pour que l'utilisateur sache qu'ils sont CONSERVÉS. */
    fun resumeGeometrie(s: OccHabStation): String = when (s.geometryType) {
        "Polygon" -> {
            val n = GeoJsonCoords.parse(s.geometryCoordsJson).size
            val nbTrous = GeoJsonCoords.parseAnneaux(s.geometryTrousJson).size
            val pluriel = if (nbTrous > 1) "s" else ""
            "Géométrie : polygone ($n sommets" + (if (nbTrous > 0) ", $nbTrous trou$pluriel conservé$pluriel" else "") + ")"
        }
        else -> "Géométrie : point (${formaterDecimal(s.latitude, 5)}, ${formaterDecimal(s.longitude, 5)})"
    }

    /** « Jeu de données : Prairies (12) », sinon son numéro, sinon « (à choisir) ». */
    fun jeuDeDonnees(details: OccHabDetailsSession, datasets: List<Pair<Int, String>>): String {
        val nom = details.nomDataset ?: datasets.firstOrNull { it.first == details.idDataset }?.second
        return "Jeu de données : " + (nom ?: details.idDataset?.toString() ?: "(à choisir)")
    }

    /** Libellé d'un habitat, celui du tableau du web : le nom cité, sinon HABREF, sinon son code. */
    fun libelleHabitat(h: OccHabHabitat): String = h.nomCite.ifBlank { h.habitatLabel }.ifBlank { "Habitat ${h.cdHab}" }

    /** Une ligne du tableau des habitats du web : « N°. nom cité », puis l'abondance (`label_default`)
     *  si elle est connue. */
    fun ligneHabitat(numero: Int, h: OccHabHabitat, abondances: List<NomValeur>): Pair<String, String?> =
        "$numero. ${libelleHabitat(h)}" to h.idNomAbondance?.let { id -> abondances.firstOrNull { it.id == id }?.label }
}

/** Les onglets de « Mes stations ». */
enum class OngletMesStations(val titre: String, val messageVide: String) {
    A_ENVOYER("À envoyer", "Aucune saisie en attente d'envoi.\nCréez-en une depuis « OccHab » sur l'accueil."),
    ENVOYEES("Envoyées", "Aucune saisie envoyée."),
}

/** « Mes stations » : les SAISIES OccHab de l'appareil (chacune regroupe 1..N stations). */
object MesStations {

    const val TITRE = "Mes stations"
    const val ENVOI_EN_COURS = "Un envoi est en cours — réessayez ensuite."
    const val ENVOI_SAISIE = "Envoi de la saisie vers GeoNature…"

    fun appartient(saisie: OccHabSaisie, onglet: OngletMesStations): Boolean = when (onglet) {
        OngletMesStations.A_ENVOYER -> !saisie.envoyeGeoNature
        OngletMesStations.ENVOYEES -> saisie.envoyeGeoNature
    }

    /** Les saisies de l'onglet, la plus récente en tête. */
    fun filtrer(toutes: List<OccHabSaisie>, onglet: OngletMesStations): List<OccHabSaisie> =
        toutes.sortedByDescending { it.date }.filter { appartient(it, onglet) }

    /** « À envoyer (3) ». */
    fun titreOnglet(toutes: List<OccHabSaisie>, onglet: OngletMesStations): String =
        "${onglet.titre} (${toutes.count { appartient(it, onglet) }})"

    /** Saisies réellement envoyables : à envoyer + au moins une station (les habitats sont
     *  FACULTATIFS — une station sans habitat est envoyable). */
    fun envoyables(toutes: List<OccHabSaisie>): List<OccHabSaisie> =
        toutes.filter { !it.envoyeGeoNature && it.stations.isNotEmpty() }

    /** Une saisie déjà envoyée ne se modifie plus d'ici. */
    fun peutEditer(saisie: OccHabSaisie): Boolean = !saisie.envoyeGeoNature

    /** Flèche d'envoi : saisie modifiable, droit de création OCCHAB (CRUVED C, détecté à la
     *  synchro — sans lui, le POST partirait en 403) et au moins une station. */
    fun peutEnvoyer(saisie: OccHabSaisie, envoiAutorise: Boolean): Boolean =
        peutEditer(saisie) && envoiAutorise && saisie.stations.isNotEmpty()

    /** Première ligne du dernier échec d'envoi, ou null. */
    fun ligneErreur(saisie: OccHabSaisie): String? = saisie.derniereErreurEnvoi?.lineSequence()?.first()

    /** « 19 oct. 2026 14:30 ». */
    fun dateLigne(saisie: OccHabSaisie): String = FormatDate.jourMoisLettresHeure(saisie.date)

    /** « 3 stations ». */
    fun nbStations(saisie: OccHabSaisie): String {
        val n = saisie.stations.size
        return "$n station${if (n > 1) "s" else ""}"
    }

    /** « Supprimer la saisie du 19/10/2026 14:30 (3 stations) ? Cette action est définitive. » */
    fun messageSuppression(saisie: OccHabSaisie): String {
        val date = FormatDate.jourHeure(saisie.date)
        val n = saisie.stations.size
        val descr = if (n == 1) "saisie du $date (1 station)" else "saisie du $date ($n stations)"
        return "Supprimer la $descr ? Cette action est définitive."
    }
}
