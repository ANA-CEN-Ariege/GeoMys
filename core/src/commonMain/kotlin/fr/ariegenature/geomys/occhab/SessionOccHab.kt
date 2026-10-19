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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabViewModel.kt) : les
// détails de session et l'état de la saisie OccHab, sans ViewModel (l'écran le tient). Gson devient
// kotlinx.serialization (format JSON_STOCKAGE), Calendar kotlinx-datetime, java.util.UUID uuidAleatoire.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import fr.ariegenature.geomys.util.debutDuJour
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlinx.serialization.Serializable

/**
 * Détails de STATION communs à toute une session OccHab (jeu de données, observateurs, dates,
 * méthode de calcul de surface, nature objet géo, commentaire). Conservés d'une station à
 * l'autre tant qu'on reste dans OccHab ; édités via le bouton « Détails ». Réinitialisés aux
 * défauts serveur au démarrage d'une nouvelle session (tuile OccHab de l'accueil).
 * NB : altitudes et surface ne sont PAS ici — ce sont des propriétés PAR STATION (dérivées de
 * sa géométrie : surface auto-calculée, altitudes MNT), portées par [OccHabStation] directement.
 */
@Serializable
data class OccHabDetailsSession(
    var idDataset: Int? = null,
    var nomDataset: String? = null,
    var observateursIds: List<Int> = emptyList(),
    var observateursNoms: List<String> = emptyList(),
    var observateursTxt: String? = null,
    var dateMin: Long? = null,
    var dateMax: Long? = null,
    var idNomCalculSurface: Int? = null,
    var idNomObjetGeographique: Int? = null,
    var comment: String? = null,
    // L'interrupteur « Afficher mes stations déjà sur GeoNature » de GeoMys-Android (champ
    // `masquerStationsServeur`) est supprimé (décision du 19 octobre) : les stations du serveur
    // s'affichent toujours. Le champ d'un relevé mémorisé auparavant est ignoré à la relecture.
) {
    companion object {
        /** Les détails relus de [json] (`Gson.fromJson`) ; null s'il est illisible. */
        fun depuisJson(json: String): OccHabDetailsSession? =
            try {
                JSON_STOCKAGE.decodeFromString(serializer(), json)
            } catch (_: Exception) {
                null
            }
    }

    fun versJson(): String = JSON_STOCKAGE.encodeToString(serializer(), this)
}

/** Détails de session par défaut (défauts serveur) : observateur par défaut, dates début ET fin
 *  = date du JOUR (sans heure — le serveur OccHab attend des dates yyyy-MM-dd), nomenclatures
 *  par défaut OccHab. Le JDD reste null (saisi au formulaire de la 1ʳᵉ station). */
fun detailsSessionParDefaut(config: GeoNatureConfig, aujourdhui: Long = debutDuJour()): OccHabDetailsSession {
    val obsId = config.observateurDefautId.trim().toIntOrNull()?.takeIf { it > 0 }
        ?: config.idRoleUtilisateur.takeIf { it > 0 }
    val obsNom = config.observateurDefautNom.ifBlank { config.nomUtilisateur }
    // RELEVÉ PRÉCÉDENT : le formulaire des informations obligatoires repart des valeurs
    // validées la dernière fois (JDD, observateurs, nomenclatures — demande terrain
    // 2026-08-26) ; seules les DATES repartent systématiquement du jour.
    config.occhabDetailsPrecedentsJson.takeIf { it.isNotEmpty() }
        ?.let(OccHabDetailsSession::depuisJson)
        ?.let { precedent -> return precedent.copy(dateMin = aujourdhui, dateMax = aujourdhui) }
    return OccHabDetailsSession(
        idDataset = null,
        observateursIds = listOfNotNull(obsId),
        observateursNoms = if (obsId != null && obsNom.isNotBlank()) listOf(obsNom) else emptyList(),
        dateMin = aujourdhui,
        dateMax = aujourdhui,
        idNomCalculSurface = config.occhabDefautNomenclature("METHOD_CALCUL_SURFACE"),
        idNomObjetGeographique = config.occhabDefautNomenclature("NAT_OBJ_GEO"),
    )
}

/**
 * État de saisie OccHab, partagé entre l'écran géométrie (la carte), l'écran de la station et
 * celui d'un habitat. Distingue :
 *  - [details] : détails de la STATION SÉLECTIONNÉE (voir [OccHabDetailsSession]) ;
 *  - [station] : la station en cours (géométrie + habitats), qui reçoit les [details] à l'envoi ;
 *  - [jddDefini] : le jeu de données a-t-il été saisi dans cette session (sinon on l'affiche).
 */
class SessionOccHab {

    var details = OccHabDetailsSession()
        private set
    var jddDefini = false
        private set
    var station = OccHabStation()
        private set

    /** La station courante est NOUVELLE : sa géométrie a été dessinée de zéro sur la carte, et non
     *  reprise d'une station existante (de la saisie, d'une autre saisie ou du serveur). Son écran
     *  s'intitule alors « Nouvelle station ». Sous GeoMys-Android, enregistrée avant l'ouverture de
     *  cet écran, elle s'y annonçait toujours « Modifier la station » (défaut n° 16 de l'inventaire). */
    var stationNouvelle = false
        private set

    /** Id de la SAISIE OccHab en cours (groupe des stations de la session). Une station enregistrée
     *  au fil de l'eau est écrite dans cette saisie (cf. `OccHabStore.upsertStation`). Nouvelle
     *  saisie à chaque [demarrerSession] ; reprise à l'identique en réédition ([reprendreSaisie]). */
    var saisieId: String = uuidAleatoire()
        private set

    /** DÉFAUTS de la session (formulaire de démarrage) : ce dont hérite chaque NOUVELLE station.
     *  Décision terrain 2026-08-27 : les détails sont PAR STATION — [details] n'est que le tampon
     *  d'édition de la station sélectionnée (chargé par [reprendreStation], écrit par le « i »),
     *  et revient à ces défauts à chaque [nouvelleStation]. Modifier une station n'en touche
     *  jamais une autre. */
    var defautsSession = OccHabDetailsSession()
        private set

    /** Démarre une nouvelle SESSION (tuile OccHab) = une nouvelle SAISIE : détails aux défauts
     *  serveur, JDD à saisir, station vierge. */
    fun demarrerSession(defauts: OccHabDetailsSession) {
        defautsSession = defauts
        details = defauts.copy()
        jddDefini = false
        station = OccHabStation()
        stationNouvelle = false
        saisieId = uuidAleatoire()
    }

    /** Dernier cadrage de la carte OccHab (latitude, longitude, zoom), mémorisé en quittant
     *  l'écran et restauré au retour : terminer une station ne doit pas renvoyer l'utilisateur
     *  sur sa position GPS alors qu'il travaillait ailleurs. Null tant que la carte n'a pas
     *  encore été quittée. */
    var dernierCadrageCarte: Triple<Double, Double, Double>? = null

    /** Nouvelle station dans la MÊME saisie : garde [saisieId], repart d'une géométrie et
     *  d'habitats vierges et des DÉFAUTS de session (détails par station). */
    fun nouvelleStation() {
        station = OccHabStation()
        stationNouvelle = false
        details = defautsSession.copy()
    }

    /** La carte valide une géométrie DESSINÉE, et non reprise : la station est nouvelle jusqu'à la
     *  suivante. */
    fun marquerStationNouvelle() {
        stationNouvelle = true
    }

    /** Pose la géométrie de la station courante. [trousJson] = anneaux intérieurs à CONSERVER
     *  (station à trou lue du serveur, dont on déplace un sommet extérieur) ; **null = polygone
     *  plein**, ce qui est le cas de tout tracé REDESSINÉ — les trous de l'ancienne géométrie ne
     *  doivent pas survivre à un nouveau dessin. Chaque appelant le passe explicitement. */
    fun definirGeometrie(type: String, lat: Double, lon: Double, coordsJson: String?, trousJson: String? = null) {
        station = station.copy(
            geometryType = type,
            latitude = lat,
            longitude = lon,
            geometryCoordsJson = coordsJson,
            geometryTrousJson = if (type == "Polygon") trousJson else null,
        )
    }

    /** La géométrie éditée sur la carte ([GeometrieEdition.geometrie]). */
    fun definirGeometrie(g: GeometrieStation) = definirGeometrie(g.type, g.latitude, g.longitude, g.coordsJson, g.trousJson)

    /** Surface (m²) de la STATION courante — auto-calculée à la validation de la géométrie
     *  (parité web : patchGeoValue → area arrondie), écrasée à chaque re-validation. */
    fun definirSurface(m2: Long?) {
        station = station.copy(surface = m2)
    }

    /** Altitudes min/max (MNT serveur, /geo/info) de la STATION courante — remplies en
     *  best-effort quand il y a du réseau (parité web : patchGeoValue → getGeoInfo). */
    fun definirAltitudes(min: Int?, max: Int?) {
        station = station.copy(altitudeMin = min, altitudeMax = max)
    }

    /** Bloc ANA-EVAL de la STATION courante (section du dialogue « Détails »). */
    fun definirAnaEval(json: String?) {
        station = station.copy(anaEvalJson = json)
    }

    fun ajouterOuMajHabitat(h: OccHabHabitat) {
        val liste = station.habitats.toMutableList()
        val idx = liste.indexOfFirst { it.id == h.id }
        if (idx >= 0) liste[idx] = h else liste.add(h)
        station = station.copy(habitats = liste)
    }

    fun supprimerHabitat(id: String) {
        station = station.copy(habitats = station.habitats.filterNot { it.id == id })
    }

    /** Applique une modification aux détails de la STATION SÉLECTIONNÉE (bouton « i » /
     *  « Détails »). [demarrage] = formulaire de démarrage du relevé : ses valeurs deviennent
     *  aussi les DÉFAUTS des nouvelles stations de la session. */
    fun majDetails(demarrage: Boolean = false, bloc: (OccHabDetailsSession) -> Unit) {
        bloc(details)
        if (demarrage) defautsSession = details.copy()
        if (details.idDataset != null) jddDefini = true
    }

    /** Reprend une SAISIE existante pour réédition (depuis « Mes stations ») : fixe la [saisieId]
     *  courante, hérite les détails de sa 1ʳᵉ station (pour une station AJOUTÉE), et repart d'une
     *  station vierge (la carte affichera les stations existantes, « Valider » en ajoute une). */
    fun reprendreSaisie(saisie: OccHabSaisie) {
        saisieId = saisie.id
        val premiere = saisie.stations.firstOrNull()
        // Relevé à UNE SEULE station : on la REPREND directement, si bien que la carte l'ouvre
        // sélectionnée et cadrée dessus au lieu d'une carte sans objet en cours (demande
        // utilisateur 2026-10-18). À plusieurs stations, aucune ne s'impose : on repart d'une
        // station vierge et la carte les montre toutes.
        station = if (saisie.stations.size == 1 && premiere != null) {
            premiere.copy(habitats = premiere.habitats.map { it.copy() })
        } else {
            OccHabStation()
        }
        stationNouvelle = false
        defautsSession = if (premiere == null) OccHabDetailsSession() else detailsDe(premiere)
        details = defautsSession.copy()
        jddDefini = premiere?.idDataset != null
    }

    /** Détails PROPRES à une station (tampon d'édition du « i »). Un JDD / des dates absents
     *  (station ancienne) retombent sur les défauts de session. */
    private fun detailsDe(s: OccHabStation) = OccHabDetailsSession(
        idDataset = s.idDataset ?: defautsSession.idDataset,
        nomDataset = if (s.idDataset == null || s.idDataset == defautsSession.idDataset) defautsSession.nomDataset else null,
        observateursIds = s.observateursIds,
        observateursNoms = s.observateursNoms,
        observateursTxt = s.observateursTxt,
        dateMin = s.dateMin ?: defautsSession.dateMin,
        dateMax = s.dateMax ?: defautsSession.dateMax,
        // altitudes/surface : par STATION, portées par [station] (cf. stationAEnregistrer).
        idNomCalculSurface = s.idNomCalculSurface,
        idNomObjetGeographique = s.idNomObjetGeographique,
        comment = s.comment,
    )

    /** Rattache la session à la saisie [id] — celle qui CONTIENT la station qu'on va reprendre.
     *  La carte affiche les stations de toutes les saisies : sans ce rattachement, valider une
     *  station venue d'une autre saisie l'enregistrerait dans la saisie courante, créant un
     *  second relevé dans « Mes stations » au lieu de modifier le sien (terrain 2026-10-18). */
    fun rattacherASaisie(id: String) {
        if (id.isNotBlank()) saisieId = id
    }

    /** Reprend une STATION existante de la saisie courante pour l'éditer (garde la [saisieId]) :
     *  sa géométrie, ses habitats, et ses détails dans le tampon [details] (détails PAR STATION,
     *  décision terrain 2026-08-27) — le « i » modifie CETTE station et aucune autre ;
     *  [stationAEnregistrer] réinjecte ce tampon dans la station. Une station importée du
     *  serveur reçoit ses dates de session À L'IMPORT (carte), pas ici : ses propres dates,
     *  éventuellement corrigées via « i », sont conservées à la resélection. */
    fun reprendreStation(existante: OccHabStation) {
        station = existante.copy(habitats = existante.habitats.map { it.copy() })
        stationNouvelle = false
        details = detailsDe(existante)
        if (details.idDataset != null) jddDefini = true
    }

    /** Valeurs « dérivées » saisissables à la main dans le dialogue Détails (altitudes, surface),
     *  EXCLUES de l'empreinte de contenu : à photographier avant le dialogue pour
     *  [forcerPersistanceSiDeriveesChangees]. */
    fun deriveesManuelles(): List<Any?> = listOf(station.altitudeMin, station.altitudeMax, station.surface)

    /** Après validation du dialogue Détails : une correction MANUELLE des altitudes / de la
     *  surface est une modification réelle même si l'empreinte ne la voit pas (audit 2026-08-27)
     *  → on lève l'empreinte d'origine pour que la prochaine persistance écrive la station. */
    fun forcerPersistanceSiDeriveesChangees(avant: List<Any?>) {
        if (station.empreinteOrigine != null && avant != deriveesManuelles()) {
            station = station.copy(empreinteOrigine = null)
        }
    }

    /** Fige l'empreinte d'ORIGINE de la station courante = son contenu tel qu'il serait persisté
     *  MAINTENANT (détails de session fusionnés, cf. [stationAEnregistrer]). Appelé juste après
     *  l'import d'une station serveur ou la remise en édition d'une station envoyée : tant que
     *  ni la géométrie, ni les habitats, ni les détails ne changent, `OccHabStore.upsertStation`
     *  ne l'écrit pas dans « Mes stations » (demande terrain 2026-08-27). */
    fun figerEmpreinteOrigine() {
        station = station.copy(empreinteOrigine = stationAEnregistrer().empreinteContenu())
    }

    /** Station à enregistrer/envoyer = géométrie + habitats (station courante) fusionnés avec le
     *  tampon [details] — qui est PAR STATION : chargé de la station à sa sélection, ou des
     *  défauts de session pour une nouvelle station. Conserve l'id de [station]. */
    fun stationAEnregistrer(): OccHabStation = station.copy(
        idDataset = details.idDataset,
        observateursIds = details.observateursIds,
        observateursNoms = details.observateursNoms,
        observateursTxt = details.observateursTxt,
        dateMin = details.dateMin,
        dateMax = details.dateMax,
        // altitudes/surface : PAS de fusion session — propriétés PAR STATION (dérivées de la
        // géométrie : surface auto-calculée à la validation de la carte, altitudes MNT serveur),
        // déjà portées par [station]. Les fusionner écrasait la valeur d'une station par celle
        // de la dernière éditée.
        idNomCalculSurface = details.idNomCalculSurface,
        idNomObjetGeographique = details.idNomObjetGeographique,
        comment = details.comment,
    )
}
