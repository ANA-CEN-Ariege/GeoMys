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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabCarteFragment.kt,
// StationsServeurOverlay.kt et StationsSessionOverlay.kt) : ce que la carte OccHab dessine, les cibles
// d'aimantage, le bandeau d'instructions, les messages et les dialogues — sans osmdroid.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.geo.airePolygoneM2
import fr.ariegenature.geomys.geo.empriseZoomerSur
import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.util.FormatDate
import fr.ariegenature.geomys.util.GeoJsonCoords
import kotlin.math.roundToLong

/** Les stations du serveur, une fois classées : celles qu'on dessine en violet (importables d'un
 *  toucher), et le nombre de celles dont une copie non envoyée vit dans une AUTRE saisie. */
data class StationsServeurClassees(val violettes: List<OccHabStation>, val nbReprisesAilleurs: Int)

/** Où ouvrir la carte OccHab. */
sealed interface CadrageOccHab {
    /** Sur la géométrie en cours d'édition (`cadrerSur`). */
    data class SurEmprise(val emprise: Emprise) : CadrageOccHab

    /** Un centre et un zoom. [impose] : le cadrage laissé en quittant la carte, que le premier
     *  point GPS ne doit pas défaire ; faux pour l'Ariège, cadrage par défaut. */
    data class Centre(val centre: LatLon, val zoom: Double, val impose: Boolean) : CadrageOccHab
}

/** Les règles et les textes de la carte OccHab. */
object CarteOccHab {

    /** Le bandeau « 🏠 › OccHab » des écrans OccHab. */
    const val LIBELLE = "OccHab"

    // ─── Bandeau d'instructions ───

    /** Texte du MODE courant (même texte que la saisie multi-taxons) : au clic sur les boutons
     *  Point/Polygone, à la sélection d'une station à modifier, et au 1er tap de saisie. */
    fun texteMode(mode: ModeGeometrie, chargee: Boolean): String = when {
        mode == ModeGeometrie.POLYGONE && chargee ->
            "Poignée + : ajouter un sommet · appui long sur un sommet : déplacer · « Annuler » : quitter l'édition"
        mode == ModeGeometrie.POINT && chargee ->
            "Appui long sur le point : déplacer · « Annuler » : quitter l'édition"
        mode == ModeGeometrie.POINT -> "Touchez pour placer le point · appui long pour le déplacer"
        else -> "Touchez pour ajouter des sommets (≥ 3) · appui long pour déplacer"
    }

    /** À l'arrivée sur la carte, s'il y a DÉJÀ des stations et qu'aucune géométrie n'est en cours,
     *  le bandeau propose les deux gestes possibles au lieu du texte de mode (demande terrain
     *  2026-08-25). La variante d'Android qui parlait de stations « orange » n'est pas reprise :
     *  aucune station n'est plus dessinée en orange. */
    fun texteSelection(stationsServeurChargees: Boolean): String =
        if (stationsServeurChargees) {
            "Touchez une station pour la modifier (violette = déjà sur GeoNature), ou saisissez une nouvelle station"
        } else {
            "Sélectionnez une station pour la modifier, ou saisissez une nouvelle station"
        }

    // ─── Messages ───

    const val AUCUNE_STATION = "Aucune station enregistrée — appuyez sur « + »"
    const val ACQUISITION_GPS = "Acquisition GPS en cours…"
    const val STATION_CHARGEE = "Station chargée — déplacez ses sommets ; « Annuler » pour quitter l'édition"
    const val ENVOYEE_NON_MODIFIABLE = "Station déjà envoyée — non modifiable."
    const val SOMMET_TROU_HORS_POLYGONE = "Ce sommet du trou est sorti du polygone — GeoNature peut refuser cette géométrie."
    const val CHOISIR_JDD = "Choisissez d'abord un jeu de données"

    /** Consignes de l'alerte d'écriture échouée (disque plein ?), selon le geste à refaire. */
    const val CONSIGNE_DETAILS = "Libérez de l'espace (photos, cache de cartes) puis revalidez les détails."
    const val CONSIGNE_SOMMET = "Libérez de l'espace (photos, cache de cartes) puis re-déplacez le sommet."
    const val CONSIGNE_GEOMETRIE = "Libérez de l'espace (photos, cache de cartes) puis revalidez la géométrie."

    // ─── Dialogues ───

    const val TITRE_MODIFIER_ENVOYEE = "Modifier cette station déjà envoyée ?"
    const val MESSAGE_MODIFIER_ENVOYEE =
        "Si vous la modifiez, elle sera renvoyée à GeoNature en MISE À JOUR au prochain envoi (pas de doublon)."
    const val MODIFIER = "Modifier"

    const val TITRE_NON_MODIFIABLE = "Station non modifiable dans l'appli"

    /** Le texte du web pour une station d'un cadre d'acquisition fermé. */
    const val CADRE_FERME = "Cadre d'acquisition fermé, action impossible"

    /** Le texte du web pour une station que l'utilisateur ne peut pas modifier. */
    const val SANS_DROIT_MODIFICATION = "Vous n'avez pas les permissions requises pour modifier cette station !"

    /** Une station du serveur que l'appli n'importe pas : cadre d'acquisition fermé ou sans droit
     *  de modification (refus du web), ou géométrie en plusieurs parties (l'importer puis la
     *  renvoyer supprimerait les autres). */
    fun messageNonModifiable(st: OccHabStation): String = when {
        st.cadreFerme -> nomEnTete(st) + CADRE_FERME
        st.nonModifiableServeur -> nomEnTete(st) + SANS_DROIT_MODIFICATION
        else -> nomEnTete(st) + "Sa géométrie est composée de plusieurs parties : l'appli n'en affiche " +
            "que la première. La modifier ici supprimerait les autres parties sur " +
            "GeoNature — utilisez QGIS pour cette station."
    }

    /** La station du serveur ne s'importe pas ([messageNonModifiable]). */
    fun nonImportable(st: OccHabStation): Boolean = st.geometryPartielle || st.cadreFerme || st.nonModifiableServeur

    const val TITRE_IMPORT = "Modifier cette station du serveur ?"

    fun messageImport(st: OccHabStation): String {
        val nbHab = st.habitats.size
        return nomEnTete(st) + "La station" + (if (nbHab > 0) " et ses $nbHab habitat(s)" else "") +
            " sera ouverte dans la saisie en cours. Elle n'entrera dans Mes stations " +
            "qu'une fois modifiée, puis repartira à GeoNature en MISE À JOUR à l'envoi " +
            "(pas de doublon)."
    }

    const val TITRE_COPIE_LOCALE = "Station déjà en cours de modification"
    const val OUVRIR_SAISIE = "Ouvrir cette saisie"

    /** Une copie non envoyée de cette station vit dans la saisie du [dateSaisie] : jamais de
     *  seconde copie. [traceEnCours] : un tracé non validé sera abandonné. */
    fun messageCopieLocale(copie: OccHabStation, dateSaisie: Long, traceEnCours: Boolean): String =
        nomEnTete(copie) +
            "Cette station est déjà reprise dans la saisie du ${FormatDate.jourHeure(dateSaisie)} (Mes stations), pas " +
            "encore envoyée. Pour ne pas créer deux versions concurrentes, poursuivez vos " +
            "modifications dans cette saisie." +
            (if (traceEnCours) "\n\nLe tracé en cours (non validé) sera abandonné." else "")

    private fun nomEnTete(st: OccHabStation): String = st.stationName?.let { "« $it »\n\n" } ?: ""

    // ─── Terminer la saisie (coche verte) ───

    const val TITRE_TERMINER = "Terminer la saisie OccHab"
    const val ENREGISTRER_QUITTER = "Enregistrer et quitter"
    const val ENREGISTRER_ENVOYER = "Enregistrer et envoyer sur GeoNature"
    const val CONTINUER = "Continuer la saisie"
    const val ENVOI_EN_COURS = "Envoi de la saisie vers GeoNature…"

    /** Envoi proposé seulement si utile ET autorisé (config OK, CRUVED C, au moins une station) —
     *  même garde que le bouton d'envoi de « Mes stations ». Les habitats sont FACULTATIFS. */
    fun peutEnvoyer(config: GeoNatureConfig, saisie: OccHabSaisie): Boolean =
        config.estConfiguree && config.occhabPeutCreer && !saisie.envoyeGeoNature && saisie.stations.isNotEmpty()

    fun optionsTerminer(peutEnvoyer: Boolean): List<String> =
        listOfNotNull(ENREGISTRER_QUITTER, ENREGISTRER_ENVOYER.takeIf { peutEnvoyer }, CONTINUER)

    // ─── Ce qui est dessiné ───

    /** Stations LOCALES dessinées en rouge : celles qui restent à envoyer, toutes saisies
     *  confondues, hors la station en cours d'édition. Les copies DÉJÀ ENVOYÉES ne le sont pas :
     *  si la station existe encore sur GeoNature, le cache serveur la porte dans sa version
     *  serveur ; si elle n'y est plus, l'afficher serait un fantôme (constat terrain 2026-10-18). */
    fun stationsLocales(saisies: List<OccHabSaisie>, stationEditeeId: String): List<OccHabStation> =
        saisies.flatMap { it.stations }.filterNot { it.envoyeGeoNature }.filter { it.id != stationEditeeId }

    /** Les points d'une station : l'anneau extérieur d'un polygone, ou son point ; rien sans
     *  géométrie. */
    fun points(st: OccHabStation): List<LatLon> = when {
        st.geometryType == "Polygon" && !st.geometryCoordsJson.isNullOrEmpty() -> GeoJsonCoords.parse(st.geometryCoordsJson)
        st.latitude != 0.0 || st.longitude != 0.0 -> listOf(LatLon(st.latitude, st.longitude))
        else -> emptyList()
    }

    /** Classe les stations du serveur. Invariant « une seule copie locale non envoyée par
     *  station serveur » :
     *  - station SANS copie locale en attente → VIOLET, un tap propose l'IMPORT ;
     *  - copie en attente (toutes saisies), ou station en cours d'édition → PAS redessinée ici :
     *    elle est déjà rouge (ou bleue), avec ses modifications, et se modifie là.
     *  [idsNonEnvoyes] : ids serveur des copies locales restant à envoyer ; [copiesLocales] : la
     *  saisie de chacune ; [idServeurEdite] : l'id serveur de la station éditée. */
    fun classerServeur(
        stations: List<OccHabStation>,
        idsNonEnvoyes: Set<Int>,
        copiesLocales: Map<Int, Pair<OccHabSaisie, OccHabStation>>,
        idServeurEdite: Int?,
        saisieId: String,
    ): StationsServeurClassees {
        var nbAilleurs = 0
        val violettes = stations.filter { st ->
            val id = st.idStationServeur
            val enEdition = id != null && id == idServeurEdite
            val copieEnAttente = id != null && id in idsNonEnvoyes
            if (enEdition || copieEnAttente) {
                val copie = copiesLocales[id]
                if (copie != null && copie.first.id != saisieId) nbAilleurs++
                false
            } else {
                true
            }
        }
        return StationsServeurClassees(violettes, nbAilleurs)
    }

    /** Les cibles d'aimantage : les sommets des stations locales (rouges) et ceux des stations
     *  du serveur dessinées (violettes). Jamais la station en cours : elle n'est dans aucune. */
    fun ciblesAimantage(locales: List<OccHabStation>, violettes: List<OccHabStation>): List<LatLon> =
        (locales + violettes).flatMap(::points)

    /** Une station VOISINE dont la topologie partagée a changé l'anneau extérieur en [anneau] :
     *  centroïde et surface recalculés (ses trous, inchangés, déduits), altitudes effacées —
     *  redemandées à l'envoi. Déjà envoyée, elle repasse « à envoyer » en mise à jour par son id
     *  serveur, avec l'empreinte de sa version envoyée : revenue à l'identique, elle redeviendrait
     *  « envoyée » si une saisie en garde la copie ([copieLocale]), et quitterait sinon « Mes
     *  stations » (station du serveur importée par la topologie). Ses dates ne changent pas : elle
     *  n'a pas été revue sur le terrain. */
    fun voisineDeplacee(voisine: OccHabStation, anneau: List<LatLon>, copieLocale: Boolean): OccHabStation {
        val centre = GeoJsonCoords.centroide(anneau)
        val deplacee = voisine.copy(
            latitude = centre?.latitude ?: voisine.latitude,
            longitude = centre?.longitude ?: voisine.longitude,
            geometryCoordsJson = GeoJsonCoords.format(anneau),
            surface = airePolygoneM2(anneau, GeoJsonCoords.parseAnneaux(voisine.geometryTrousJson)).roundToLong(),
            altitudeMin = null,
            altitudeMax = null,
        )
        if (!voisine.envoyeGeoNature) return deplacee
        return deplacee.copy(
            envoyeGeoNature = false,
            envoiIncertain = false,
            derniereErreurEnvoi = null,
            empreinteOrigine = voisine.empreinteOrigine ?: voisine.empreinteContenu(),
            origineEnvoyee = copieLocale,
            origineJson = voisine.origineJson ?: voisine.versOrigineJson(),
        )
    }

    /** Après un geste qui a modifié [n] stations voisines (topologie partagée). */
    fun messageVoisinesModifiees(n: Int): String =
        if (n == 1) "Station voisine modifiée aussi : elle partira avec cette saisie."
        else "$n stations voisines modifiées aussi : elles partiront avec cette saisie."

    /** En tête du résultat de l'envoi des voisines d'autres saisies, envoyées avec la saisie. */
    const val STATIONS_VOISINES = "Stations voisines : "

    /** Une voisine raccordée que l'appli ne peut pas modifier : géométrie en plusieurs parties, ou
     *  station envoyée sans id serveur connu. */
    const val VOISINE_NON_MODIFIABLE =
        "Une station voisine raccordée n'est pas modifiable dans l'application : le raccord est rompu de son côté."

    /** Seuil d'aimantage, en dp à l'écran (constant quel que soit le zoom ; 28 → 44 dp à la
     *  demande terrain 2026-08-27, pour raccorder plus facilement au sommet d'un autre polygone). */
    const val SEUIL_AIMANTAGE_DP = 44f

    /** Index de la cible la PLUS PROCHE sous le [seuil] (distances à l'écran, en dp), ou null. */
    fun plusProche(distances: List<Float>, seuil: Float = SEUIL_AIMANTAGE_DP): Int? {
        var meilleur: Int? = null
        var meilleureDistance = Float.MAX_VALUE
        distances.forEachIndexed { i, d ->
            if (d <= seuil && d < meilleureDistance) {
                meilleureDistance = d
                meilleur = i
            }
        }
        return meilleur
    }

    // ─── Cadrage ───

    /** Cadrage large sur l'Ariège, faute de mieux (le 1er fix GPS recentrera). */
    val ARIEGE = LatLon(42.93, 1.40)
    const val ZOOM_ARIEGE = 11.0

    /** Zoom du recentrage sur la position. */
    const val ZOOM_POSITION = 16.0

    /** Emprise de [points] (`zoomerSur(points, offset = 0.004, scale = 1.8)`). */
    fun emprise(points: List<LatLon>): Emprise? = empriseZoomerSur(points, demiBoite = 0.004, agrandissement = 1.8)

    /** Où ouvrir la carte. EMPRISE sur la géométrie ÉDITÉE quand il y en a une : les autres
     *  stations sont toutes affichées (demande utilisateur 2026-10-18), mais les inclure dans le
     *  cadrage dézoomait jusqu'à l'ensemble du département et on ne voyait plus ce qu'on édite.
     *  SANS géométrie en cours, l'emprise NE BOUGE PAS : on reprend celle qu'on avait en quittant
     *  la carte ([memorise]) — c'est le cas d'« Ajouter une station », qui sautait sinon sur
     *  l'ensemble des stations enregistrées. Cadrer sur les stations n'est JAMAIS un repli : à la
     *  toute première ouverture, l'Ariège, que le premier point GPS recentrera. */
    fun cadrageOuverture(pointsCourant: List<LatLon>, memorise: Triple<Double, Double, Double>?): CadrageOccHab =
        emprise(pointsCourant)?.let { CadrageOccHab.SurEmprise(it) }
            ?: memorise?.let { CadrageOccHab.Centre(LatLon(it.first, it.second), it.third, impose = true) }
            ?: CadrageOccHab.Centre(ARIEGE, ZOOM_ARIEGE, impose = false)

    // ─── Chargement des stations du serveur ───

    /** Le message affiché quand la carte montre les stations du serveur d'un jeu de données, lues dans
     *  le cache de la synchro : « 12 station(s) du serveur affichée(s) — synchro du 18/10/2026 », ou
     *  « 12 station(s) du serveur (dont 2 déjà dans cette saisie) — synchro du 18/10/2026 ». */
    fun messageChargement(nbCharges: Int, dateCache: Long?, nbIci: Int, nbAilleurs: Int): String {
        val detail = buildList {
            if (nbIci > 0) add("$nbIci déjà dans cette saisie")
            if (nbAilleurs > 0) add("$nbAilleurs reprise(s) dans une autre saisie")
        }
        val stations = "$nbCharges station(s) du serveur" +
            if (detail.isEmpty()) " affichée(s)" else " (dont ${detail.joinToString(", ")})"
        return stations + (dateCache?.let { " — synchro du ${FormatDate.jour(it)}" } ?: "")
    }
}
