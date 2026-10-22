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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/OccHabUpload.kt) :
// l'objet devient une classe injectée (trouverIdParUuid, pure, en compagnon) ; HttpURLConnection
// remplacé par ClientsGeoNature, SimpleDateFormat par DateEnvoi, Log par Journal.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.get
import fr.ariegenature.geomys.network.http.postJson
import fr.ariegenature.geomys.occhab.FusionStation
import fr.ariegenature.geomys.occhab.origine
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.util.AnaEval
import fr.ariegenature.geomys.util.DateEnvoi
import io.ktor.client.statement.bodyAsText
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

/** Résultat de l'envoi d'une station OccHab (`OccHabUpload.envoyer`). */
data class OccHabEnvoiResult(
    /** id_station attribué par le serveur (extrait de la réponse GeoJSON). null si non retrouvé. */
    val idStationServeur: Int?,
    /** Nombre d'habitats effectivement envoyés (ceux avec un cd_hab renseigné). */
    val nbHabitats: Int,
    /** true si la station existait DÉJÀ côté serveur (créée lors d'une tentative précédente au
     *  statut incertain) : aucun POST n'a été refait — anti-doublon. */
    val dejaPresente: Boolean = false,
    /** La station telle qu'elle est partie : fusionnée avec la version du serveur pour une mise à
     *  jour, ses habitats munis des ids que le serveur leur a donnés. null si rien n'est parti. */
    val envoyee: OccHabStation? = null,
)

/**
 * Envoi d'une station OccHab (station + habitats) vers `POST /api/occhab/stations/`.
 *
 * Contrairement à Occtax (relevé puis N occurrences + médias), OccHab envoie TOUT en un seul POST :
 * le corps est un Feature GeoJSON dont les `properties` portent les champs de la station, les
 * `observers` (tableau d'`{id_role}`) et les `habitats` imbriqués. Les nomenclatures sont envoyées
 * en `id_nomenclature_*` (le modèle les stocke déjà ainsi). Pas de médias.
 *
 * MISE À JOUR : quand la station porte un `idStationServeur` (importée du serveur, ou déjà envoyée
 * puis rééditée), le POST vise `/stations/<id>/` avec le MÊME corps — le serveur (blueprint
 * `create_or_update_station`) recharge l'instance et Marshmallow la met à jour : un habitat portant
 * son `id_habitat` est mis à jour, un habitat sans id est créé, un habitat omis est SUPPRIMÉ.
 * Permission CRUVED U vérifiée par instance côté serveur.
 */
class OccHabUpload(private val auth: GeoNatureAuth, private val clients: ClientsGeoNature) {

    suspend fun envoyer(locale: OccHabStation, config: GeoNatureConfig): OccHabEnvoiResult = withContext(Dispatchers.IO) {
        val base = config.urlServeur.trim().trimEnd('/')
        val (token, _, cookies) = auth.loginAvecCookies(base, config.login, config.motDePasse)
            ?: throw GNErreur.AuthEchouee(401)

        // MISE À JOUR vs CRÉATION : un id_station serveur connu route l'envoi sur /stations/<id>/
        // (mise à jour) au lieu de /stations/ (création).
        val estMiseAJour = (locale.idStationServeur ?: 0) > 0

        // Mise à jour d'une station dont l'origine est connue (importée, remise en édition, voisine
        // déplacée) : relue sur le serveur et fusionnée avec la copie locale. Renvoyée telle quelle,
        // elle effaçait ce qui avait changé sur le web depuis son origine.
        val origine = locale.origine()
        val station = if (estMiseAJour && origine != null) {
            val serveur = lireStation(base, token, cookies, locale.idStationServeur!!)
                ?: throw GNErreur.EnvoiEchoue(404, "Station supprimée sur GeoNature depuis son import : elle n'est pas recréée.")
            // La géométrie ne se fusionne pas (audit du 21 octobre, C6) : mise en plusieurs parties sur
            // le web (l'appli n'en lit que la première), ou changée des deux côtés depuis l'origine,
            // l'envoi effacerait ce que le web a fait.
            if (serveur.geometryPartielle) throw GNErreur.EnvoiEchoue(0, GEOMETRIE_EN_PLUSIEURS_PARTIES)
            if (!FusionStation.memeGeometrie(locale, origine) && !FusionStation.memeGeometrie(serveur, origine)) {
                throw GNErreur.EnvoiEchoue(0, GEOMETRIE_MODIFIEE_SUR_LE_WEB)
            }
            FusionStation.fusionner(origine, locale, serveur)
        } else {
            locale
        }

        // Jeu de données : celui de la station, obligatoire comme sur le web (le jeu par défaut des
        // Paramètres est celui d'OccTax, il ne vaut pas pour OccHab).
        val datasetId = station.idDataset?.takeIf { it > 0 }
            ?: throw GNErreur.EnvoiEchoue(0, "Jeu de données manquant : choisissez-le dans « Détails de la station ».")
        if (!config.datasetAcceptablePourEnvoi(datasetId)) throw GNErreur.DatasetInvalide(datasetId)

        // Habitats OPTIONNELS : une station peut être envoyée sans aucun habitat — le serveur
        // l'accepte (`habitats` n'est pas `required` dans StationSchema, `validate_habitats` ne
        // contrôle que l'appartenance ; parité web où l'on ajoute les habitats après). On ne garde
        // que ceux portant un code HABREF valide (les incomplets sont ignorés) ; le tableau
        // `habitats` peut donc être vide.
        val habitatsValides = station.habitats.filter { it.cdHab > 0 }

        // Anti-doublon (CRÉATION seulement) : une tentative précédente s'est soldée par un statut
        // INCERTAIN (réseau coupé après l'émission) → la station a peut-être déjà été créée. Le
        // serveur OccHab n'imposant aucune contrainte d'unicité, on interroge par notre UUID stable
        // AVANT de re-POSTer. Trouvée → on renvoie son id sans rien recréer. Absente → on procède au
        // POST. En MISE À JOUR, cette vérification est INUTILE : re-POSTer /stations/<id>/ est
        // idempotent (le serveur recharge la même instance) — après une mise à jour incertaine, le
        // ré-envoi re-POSTe donc directement.
        if (station.envoiIncertain && !estMiseAJour) {
            verifierStationExistante(base, token, cookies, station.uuidStation, datasetId)?.let { idExistant ->
                // Créée par la tentative d'alors : la copie locale, peut-être modifiée depuis, part en
                // mise à jour. Elle était marquée envoyée telle quelle, et ses modifications se perdaient
                // (audit du 21 octobre, C3). Ses habitats prennent l'id de ceux que le serveur a créés,
                // reconnus à leur identifiant SINP : sans lui, ils seraient recréés en double.
                val surLeServeur = lireStation(base, token, cookies, idExistant)
                    ?: throw GNErreur.EnvoiIncertain("Station retrouvée sur GeoNature mais illisible : sa mise à jour sera retentée.")
                val idsParUuid = surLeServeur.habitats.mapNotNull { h ->
                    val uuid = h.uuidHabitat?.lowercase()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    h.idHabitatServeur?.takeIf { it > 0 }?.let { uuid to it }
                }.toMap()
                val aMettreAJour = locale.copy(
                    idStationServeur = idExistant,
                    envoiIncertain = false,
                    habitats = locale.habitats.map { h ->
                        h.uuidHabitat?.lowercase()?.let(idsParUuid::get)?.let { h.copy(idHabitatServeur = it) } ?: h
                    },
                )
                return@withContext envoyer(aMettreAJour, config).copy(dejaPresente = true)
            }
        }

        val dateMin = station.dateMin ?: station.date
        val dateMax = station.dateMax ?: station.dateMin ?: station.date

        // Géométrie : mutualisée avec Occtax (Point / Polygon, [lon,lat], fermeture d'anneau) + TROUS
        // du polygone (anneaux intérieurs lus sur le serveur, renvoyés tels quels : sans eux la mise
        // à jour les supprimait — bug 2026-08-31).
        val geometry = GeoNatureUpload.construireGeometrie(
            type = station.geometryType,
            coordsJson = station.geometryCoordsJson,
            lat = station.latitude,
            lon = station.longitude,
            trousJson = station.geometryTrousJson,
        )

        // Observateurs : tableau d'{id_role}, ceux de la station seulement, comme le web.
        // observers_txt renseigné en plus si la station en porte un.
        val observateurs = station.observateursIds.filter { it > 0 }
        val observersArr = JSONArray().apply {
            observateurs.forEach { put(JSONObject().put("id_role", it)) }
        }

        /** Un champ du formulaire web : sa valeur, ou, vide dans une MISE À JOUR, un null explicite
         *  qui le vide sur le serveur comme le web (absent, le serveur gardait l'ancienne valeur).
         *  Une CRÉATION l'omet : le serveur applique ses défauts. */
        fun JSONObject.champ(cle: String, valeur: Any?) {
            if (valeur != null) put(cle, valeur) else if (estMiseAJour) put(cle, JSONObject.NULL)
        }

        val habitatsArr = JSONArray()
        habitatsValides.forEach { h ->
            habitatsArr.put(JSONObject().apply {
                // MISE À JOUR : id_habitat + unique_id_sinp_hab renvoyés quand connus, pour que le
                // serveur METTE À JOUR l'habitat existant au lieu d'un couple suppression/re-création
                // (ids et UUID SINP préservés). Jamais en CRÉATION : le serveur rejetterait un
                // id_habitat appartenant à une autre station (validate_habitats), et un habitat neuf
                // n'en porte pas.
                if (estMiseAJour) h.idHabitatServeur?.takeIf { it > 0 }?.let { put("id_habitat", it) }
                // L'identifiant SINP, création comprise pour un habitat né dans l'appli : il porte celui
                // qu'elle lui a donné, que le serveur garde. Un renvoi après une réponse perdue le
                // reconnaît ainsi au lieu de le créer en double (audit du 21 octobre, C5). Jamais, en
                // création, celui d'un habitat du serveur : deux habitats auraient le même.
                if (estMiseAJour || (h.idHabitatServeur ?: 0) <= 0) {
                    h.uuidHabitat?.takeIf { it.isNotBlank() }?.let { put("unique_id_sinp_hab", it) }
                }
                put("cd_hab", h.cdHab)
                // nom_cite obligatoire : repli sur le libellé HABREF si l'utilisateur ne l'a pas édité.
                put("nom_cite", h.nomCite.ifBlank { h.habitatLabel }.ifBlank { "Habitat ${h.cdHab}" })
                champ("determiner", h.determiner?.takeIf { it.isNotBlank() })
                champ("recovery_percentage", h.recouvrement)
                // technical_precision : part humaine RE-FUSIONNÉE avec le bloc ANA-EVAL du plugin
                // QGIS (anaEvalJson, extrait à la lecture serveur) ; la clé `recouvrement` du bloc
                // (champ DOUBLE) est réalignée sur la valeur native éditée. anaEvalJson null →
                // comportement standard STRICTEMENT inchangé.
                champ(
                    "technical_precision",
                    AnaEval.avecBloc(h.precisionTechnique, AnaEval.avecRecouvrementNatif(h.anaEvalJson, h.recouvrement)),
                )
                champ("id_nomenclature_determination_type", h.idNomTypeDetermination)
                // collection_technique : obligatoire côté serveur mais avec un défaut serveur — on
                // l'omet si non renseigné pour laisser le serveur appliquer son défaut.
                h.idNomTechniqueCollecte?.let { put("id_nomenclature_collection_technique", it) }
                champ("id_nomenclature_abundance", h.idNomAbondance)
                // La sensibilité n'est pas un champ du formulaire web : jamais vidée.
                h.idNomSensibilite?.let { put("id_nomenclature_sensitivity", it) }
                champ("id_nomenclature_community_interest", h.idNomInteretCommunautaire)
            })
        }

        val properties = JSONObject().apply {
            put("id_dataset", datasetId)
            // UUID SINP stable côté client : ancre d'idempotence pour la vérification d'existence
            // d'un ré-envoi (cf. verifierStationExistante). Le serveur le persiste tel quel (colonne
            // unique_id_sinp_station) au lieu d'en générer un.
            station.uuidStation.takeIf { it.isNotBlank() }?.let { put("unique_id_sinp_station", it) }
            put("date_min", DateEnvoi.date(dateMin))
            put("date_max", DateEnvoi.date(dateMax))
            station.stationName?.takeIf { it.isNotBlank() }?.let { put("station_name", it) }
            // comment : part humaine RE-FUSIONNÉE avec le bloc ANA-EVAL de la station (anaEvalJson)
            // — le bloc survit ainsi au flux des détails de session. anaEvalJson null →
            // comportement standard STRICTEMENT inchangé.
            champ("comment", AnaEval.avecBloc(station.comment, station.anaEvalJson))
            champ("altitude_min", station.altitudeMin)
            champ("altitude_max", station.altitudeMax)
            champ("depth_min", station.profondeurMin)
            champ("depth_max", station.profondeurMax)
            champ("area", station.surface)
            // Précision et mosaïque d'habitats ne sont pas des champs du formulaire web : jamais vidées.
            station.precision?.let { put("precision", it) }
            champ("id_nomenclature_exposure", station.idNomExposition)
            champ("id_nomenclature_area_surface_calculation", station.idNomCalculSurface)
            champ("id_nomenclature_geographic_object", station.idNomObjetGeographique)
            champ("id_nomenclature_type_sol", station.idNomTypeSol)
            station.idNomTypeMosaique?.let { put("id_nomenclature_type_mosaique_habitat", it) }
            champ("observers_txt", station.observateursTxt?.takeIf { it.isNotBlank() })
            put("observers", observersArr)
            put("habitats", habitatsArr)
            // Envoyé par le formulaire web (le serveur l'ignore aujourd'hui).
            put("is_habitat_complex", false)
        }

        val corps = JSONObject()
            .put("type", "Feature")
            .put("geometry", geometry)
            .put("properties", properties)
            .toString()

        // MISE À JOUR (id serveur connu) → POST /stations/<id>/ ; sinon CRÉATION → /stations/.
        val url = if (estMiseAJour) "$base/api/occhab/stations/${station.idStationServeur}/" else "$base/api/occhab/stations/"
        val reponse = try {
            clients.postJson(url, corps, token, cookies, timeoutMs = 30_000)
        } catch (e: IOException) {
            // Coupure réseau autour du POST : impossible de savoir si le serveur a enregistré (la
            // réponse peut se perdre APRÈS création). Statut INCERTAIN → le prochain envoi vérifiera
            // l'existence par UUID avant de recréer (anti-doublon). Ne jamais requalifier ça en échec
            // net : ce serait inviter à un re-POST aveugle.
            throw GNErreur.EnvoiIncertain(
                "Réseau interrompu pendant l'envoi de la station — statut incertain " +
                    "(${e.message ?: e::class.simpleName}). La station a peut-être été enregistrée.")
        }
        val code = reponse.status.value
        if (code !in 200..299) {
            val corpsErreur = try { reponse.bodyAsText() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            throw GNErreur.EnvoiEchoue(code, parseErreur(code, corpsErreur))
        }
        val texteReponse = try { reponse.bodyAsText() } catch (e: CancellationException) { throw e } catch (_: Exception) { "" }

        // Réponse = la station en Feature GeoJSON. id_station en `id` à la racine ou dans properties.
        val idStationLu = try {
            val resp = JSONObject(texteReponse)
            resp.optInt("id", -1).takeIf { it > 0 }
                ?: resp.optJSONObject("properties")?.optInt("id_station", -1)?.takeIf { it > 0 }
        } catch (_: Exception) { null }

        // 2xx mais id ILLISIBLE sur une CRÉATION (corps tronqué, réponse non JSON, id sous un autre
        // nom) : la station EST créée côté serveur, et sans son id elle serait marquée « envoyée »
        // sans rien pour la retrouver — sa réédition en créerait une seconde, le module OccHab
        // n'ayant aucune contrainte d'unicité (audit 2026-09-14, R1-M2). On va donc chercher l'id
        // par notre uuid, immédiatement. Si cette vérification est elle-même impossible,
        // `verifierStationExistante` lève EnvoiIncertain : la station reste incertaine et le prochain
        // envoi vérifiera au lieu de re-POSTer — c'est exactement ce qu'il faut.
        val idStation = idStationLu
            ?: if (!estMiseAJour) {
                Journal.w(TAG, "Création acceptée mais id_station illisible — récupération par uuid")
                verifierStationExistante(base, token, cookies, station.uuidStation, datasetId)
            } else {
                null
            }

        OccHabEnvoiResult(idStationServeur = idStation, nbHabitats = habitatsValides.size, envoyee = avecIdsHabitats(station, texteReponse))
    }

    /** La station [idStation] telle qu'elle est sur le serveur (`GET /stations/<id>/`, une Feature),
     *  lue comme à la synchro ; null si elle n'y est plus (404). Lecture impossible : une erreur,
     *  sans rien envoyer. */
    private suspend fun lireStation(base: String, token: String?, cookies: String, idStation: Int): OccHabStation? {
        val reponse = clients.get("$base/api/occhab/stations/$idStation/", token, cookies, timeoutMs = 30_000)
        val code = reponse.status.value
        if (code == 404) return null
        val texte = reponse.bodyAsText()
        if (code !in 200..299) throw GNErreur.EnvoiEchoue(code, parseErreur(code, texte))
        return OccHabApi.parserFeatureCollection("{\"type\":\"FeatureCollection\",\"features\":[$texte]}").singleOrNull()
            ?: throw GNErreur.EnvoiEchoue(code, "Station illisible sur le serveur : mise à jour non envoyée.")
    }

    /**
     * Cherche une station déjà créée côté serveur portant [uuid] (`unique_id_sinp_station`).
     * `GET /api/occhab/stations/?format=json&id_dataset=<jdd>` (filtre serveur : la liste complète
     * d'une grosse instance faisait tomber la vérification en délai dépassé). Renvoie son id_station
     * si trouvée, null si ABSENTE (réponse lue, uuid non trouvé → le POST est sûr). Vérification
     * IMPOSSIBLE (réseau, délai, HTTP ≠ 2xx — ex. 403 sans CRUVED R) → lève
     * [GNErreur.EnvoiIncertain] : le serveur était joignable lors de la tentative précédente, la
     * station a donc peut-être été créée — re-POSTer à l'aveugle dupliquerait (audit 2026-08-27).
     */
    private suspend fun verifierStationExistante(base: String, token: String?, cookies: String, uuid: String, idDataset: Int?): Int? {
        if (uuid.isBlank()) return null
        val url = buildString {
            append("$base/api/occhab/stations/?format=json")
            if (idDataset != null && idDataset > 0) append("&id_dataset=$idDataset")
        }
        val reponse = try {
            clients.get(url, token, cookies, timeoutMs = 30_000)
        } catch (_: IOException) {
            throw GNErreur.EnvoiIncertain("Vérification anti-doublon impossible (réseau) — " +
                "la station a peut-être déjà été créée ; réessayez avec du réseau.")
        }
        try {
            val code = reponse.status.value
            if (code !in 200..299) {
                throw GNErreur.EnvoiIncertain(
                    "Vérification anti-doublon impossible (HTTP $code) — la station a peut-être déjà " +
                        "été créée ; réessayez.")
            }
            return trouverIdParUuid(reponse.bodyAsText(), uuid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GNErreur) {
            throw e
        } catch (e: Exception) {
            throw GNErreur.EnvoiIncertain("Vérification anti-doublon impossible " +
                "(${e.message ?: e::class.simpleName}) — réessayez.")
        }
    }

    companion object {
        /** Une station mise en plusieurs parties sur le web : l'appli n'en lit que la première. */
        const val GEOMETRIE_EN_PLUSIEURS_PARTIES =
            "Station mise en plusieurs parties sur GeoNature : l'application n'en lit que la première et effacerait les autres. Modifiez-la sur le web."

        /** Une géométrie changée à la fois dans l'appli et sur le web depuis l'import. */
        const val GEOMETRIE_MODIFIEE_SUR_LE_WEB =
            "Contour de la station modifié aussi sur GeoNature depuis son import : l'envoi effacerait cette modification. Réimportez la station ou modifiez-la sur le web."

        private const val TAG = "OccHabUpload"

        /** [station] dont les habitats envoyés reçoivent les ids que le serveur leur a donnés, lus
         *  dans sa réponse (la station en Feature GeoJSON), rang par rang quand chaque rang porte le
         *  même cd_hab ; sinon [station] inchangée. Une réédition fusionnera ainsi ses habitats par
         *  id, au lieu de prendre ceux créés par l'appli pour des nouveaux. */
        internal fun avecIdsHabitats(station: OccHabStation, texteReponse: String): OccHabStation {
            val recus = try {
                JSONObject(texteReponse).optJSONObject("properties")?.optJSONArray("habitats")
            } catch (_: Exception) {
                null
            } ?: return station
            val envoyes = station.habitats.filter { it.cdHab > 0 }
            if (recus.length() != envoyes.size) return station
            val parId = mutableMapOf<String, OccHabHabitat>()
            for ((i, h) in envoyes.withIndex()) {
                val recu = recus.optJSONObject(i) ?: return station
                if (recu.optInt("cd_hab", -1) != h.cdHab) return station
                parId[h.id] = h.copy(
                    idHabitatServeur = recu.optInt("id_habitat", -1).takeIf { it > 0 } ?: h.idHabitatServeur,
                    uuidHabitat = recu.optString("unique_id_sinp_hab").takeIf { it.isNotBlank() && it != "null" } ?: h.uuidHabitat,
                )
            }
            return station.copy(habitats = station.habitats.map { parId[it.id] ?: it })
        }

        /** L'id_station d'une station portant [uuid] dans la réponse de la liste des stations. Gère
         *  le format `json` (tableau d'objets) et un repli `geojson` (FeatureCollection). */
        internal fun trouverIdParUuid(text: String, uuid: String): Int? {
            val cible = uuid.trim().lowercase()
            if (cible.isEmpty()) return null
            val arr = try {
                JSONArray(text)
            } catch (_: Exception) {
                val root = try { JSONObject(text) } catch (_: Exception) { return null }
                root.optJSONArray("features") ?: return null
            }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val props = o.optJSONObject("properties") ?: o
                val u = props.optString("unique_id_sinp_station", "").trim().lowercase()
                if (u.isNotEmpty() && u == cible) {
                    val id = props.optInt("id_station", -1).takeIf { it > 0 }
                        ?: o.optInt("id_station", -1).takeIf { it > 0 }
                        ?: o.optInt("id", -1).takeIf { it > 0 }
                    if (id != null) return id
                }
            }
            return null
        }
    }
}
