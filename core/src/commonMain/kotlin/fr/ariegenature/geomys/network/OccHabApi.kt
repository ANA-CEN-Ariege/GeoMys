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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/OccHabApi.kt) :
// HttpURLConnection remplacé par ClientsGeoNature, org.json par son portage, SimpleDateFormat par
// kotlinx-datetime, AtomicInteger par celui d'atomicfu, android.util.Log par Journal. L'objet
// devient une classe ; ses constantes et le parsing, pur, passent dans son compagnon.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.get
import fr.ariegenature.geomys.network.http.postJson
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.util.AnaEval
import fr.ariegenature.geomys.util.maintenantMillis
import fr.ariegenature.geomys.util.uuidAleatoire
import io.ktor.client.statement.bodyAsText
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.atomicfu.AtomicInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** Droits de l'utilisateur sur le module OccHab (issus du cruved de `gn_commons/modules`). */
data class OccHabAcces(
    /** Module installé sur le serveur ET visible par l'utilisateur (au moins un droit). */
    val disponible: Boolean,
    /** cruved C > 0 : peut créer des stations. */
    val peutCreer: Boolean,
    /** cruved R > 0 : peut lire les stations. */
    val peutLire: Boolean,
) {
    companion object {
        val ABSENT = OccHabAcces(disponible = false, peutCreer = false, peutLire = false)
    }
}

/**
 * Accès réseau au module OccHab : détection du module (droits), et lecture des stations
 * existantes côté serveur (consultation lecture seule). L'envoi vit dans [OccHabUpload].
 */
class OccHabApi(
    private val auth: GeoNatureAuth,
    private val clients: ClientsGeoNature,
) {

    /**
     * Altitudes min/max (MNT serveur) d'une géométrie via `POST /api/geo/info` — le même
     * appel que le web quand on dessine une station (form-service.ts `patchGeoValue` →
     * `getGeoInfo`). BEST-EFFORT : null si hors-ligne, non authentifié, erreur ou réponse
     * sans bloc `altitude` — l'appelant laisse alors les champs vides (saisie manuelle).
     * [geometryGeoJson] = objet GeoJSON *geometry* ({"type":"Point"/"Polygon","coordinates":…}),
     * enveloppé ici en Feature comme le fait le client web.
     */
    suspend fun altitudesPourGeometrie(
        config: GeoNatureConfig,
        geometryGeoJson: JSONObject,
    ): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val base = config.urlServeur.trim().trimEnd('/')
            val (token, _, cookies) = auth.loginAvecCookies(base, config.login, config.motDePasse)
                ?: return@withContext null
            val feature = JSONObject()
                .put("type", "Feature")
                .put("geometry", geometryGeoJson)
                .put("properties", JSONObject())
            val reponse = clients.postJson("$base/api/geo/info", feature.toString(), token, cookies)
            if (reponse.status.value != 200) return@withContext null
            val texte = reponse.bodyAsText()
            val alt = JSONObject(texte).optJSONObject("altitude") ?: return@withContext null
            val min = alt.opt("altitude_min")?.toString()?.toDoubleOrNull()?.toInt()
            val max = alt.opt("altitude_max")?.toString()?.toDoubleOrNull()?.toInt()
            if (min == null || max == null) null else min to max
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Détection GÉNÉRIQUE des droits par module via `GET /api/gn_commons/modules` (UN seul
     * appel pour tous les [codes]). N'inclut dans la map QUE les modules trouvés dans la
     * réponse : un module absent (pas installé / aucun droit) n'y figure pas — l'appelant
     * choisit son défaut (ABSENT pour OccHab, « autorisé » pour Occtax afin de ne pas
     * verrouiller l'app sur un serveur qui ne publie pas ce endpoint). Best-effort : map
     * vide sur toute erreur.
     */
    suspend fun detecterModules(
        config: GeoNatureConfig,
        codes: Set<String>,
    ): Map<String, OccHabAcces> = chargerModulesGeoNature(config)?.let { modulesDetectes(it, codes) }.orEmpty()

    /** La réponse brute de `GET /api/gn_commons/modules` : les modules de l'utilisateur, avec ses
     *  droits sur eux et sur leurs objets. Null sur toute erreur. */
    suspend fun chargerModulesGeoNature(config: GeoNatureConfig): String? = withContext(Dispatchers.IO) {
        val base = config.urlServeur.trim().trimEnd('/')
        val (token, _, cookies) = auth.loginAvecCookies(base, config.login, config.motDePasse)
            ?: return@withContext null
        try {
            val reponse = clients.get("$base/api/gn_commons/modules", token, cookies, 15000)
            if (reponse.status.value != 200) return@withContext null
            reponse.bodyAsText()
        } catch (e: CancellationException) {
            throw e // ne PAS avaler l'annulation (sinon un sync annulé réinitialise les drapeaux).
        } catch (_: Exception) {
            null
        }
    }

    /** Les droits des modules [codes] dans [texte], la réponse de `/api/gn_commons/modules` (cf.
     *  [detecterModules]) ; vide si elle est illisible. */
    fun modulesDetectes(texte: String, codes: Set<String>): Map<String, OccHabAcces> = try {
        val arr = JSONArray(texte)
        val resultat = mutableMapOf<String, OccHabAcces>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val code = m.optString("module_code")
            if (code !in codes) continue
            val cruved = m.optJSONObject("cruved")
            val c = cruved?.optInt("C", 0) ?: 0
            val r = cruved?.optInt("R", 0) ?: 0
            resultat[code] = OccHabAcces(disponible = true, peutCreer = c > 0, peutLire = r > 0)
        }
        resultat
    } catch (_: Exception) {
        emptyMap()
    }

    /**
     * Valeurs par défaut des nomenclatures OccHab (`GET /api/occhab/defaultNomenclatures`) :
     * map mnémonique→id_nomenclature (ex. `TECHNIQUE_COLLECT_HAB` → « In situ »). Pré-remplit les
     * sélecteurs d'un nouvel habitat, comme le web. Best-effort : map vide si échec.
     */
    suspend fun chargerDefautsNomenclatures(config: GeoNatureConfig): Map<String, Int> =
        withContext(Dispatchers.IO) {
            val base = config.urlServeur.trim().trimEnd('/')
            val (token, _, cookies) = auth.loginAvecCookies(base, config.login, config.motDePasse)
                ?: return@withContext emptyMap()
            try {
                val reponse = clients.get("$base/api/occhab/defaultNomenclatures", token, cookies, 15000)
                if (reponse.status.value != 200) return@withContext emptyMap()
                val obj = JSONObject(reponse.bodyAsText())
                val out = HashMap<String, Int>()
                val it = obj.keys()
                while (it.hasNext()) {
                    val mnem = it.next()
                    val id = obj.optJSONObject(mnem)?.optInt("id_nomenclature", -1)?.takeIf { v -> v > 0 }
                    if (id != null) out[mnem] = id
                }
                out
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { emptyMap() }
        }

    /**
     * Charge les stations existantes du serveur.
     * `GET /api/occhab/stations/?format=geojson&habitats=1&nomenclatures=1` → FeatureCollection.
     * [idDataset] filtre optionnellement par jeu de données. Les stations renvoyées portent
     * `origineServeur = true` : affichées en consultation (violet) sur la carte, importables dans
     * la saisie courante pour être modifiées puis renvoyées en MISE À JOUR (cf. [OccHabUpload]).
     */
    suspend fun chargerStations(
        config: GeoNatureConfig,
        idDataset: Int? = null,
        /** Voir [parserFeatureCollection] : stations écartées faute de géométrie modélisable. */
        ignorees: AtomicInt? = null,
    ): List<OccHabStation> =
        withContext(Dispatchers.IO) {
            val base = config.urlServeur.trim().trimEnd('/')
            val (token, idRole, cookies) = auth.loginAvecCookies(base, config.login, config.motDePasse)
                ?: throw GNErreur.AuthEchouee(401)
            val url = buildString {
                append("$base/api/occhab/stations/?format=geojson&habitats=1&nomenclatures=1")
                if (idDataset != null && idDataset > 0) append("&id_dataset=$idDataset")
            }
            val reponse = clients.get(url, token, cookies, 30000)
            val code = reponse.status.value
            if (code !in 200..299) {
                val body = try {
                    reponse.bodyAsText()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { null }
                throw GNErreur.EnvoiEchoue(code, body?.take(200) ?: "HTTP $code")
            }
            val text = reponse.bodyAsText()
            // SEULEMENT les stations de l'UTILISATEUR — filtre CLIENT : le backend ne propose
            // AUCUN filtre par personne sur cette route (Station.filter_by_params ne connaît
            // que id_dataset/cd_hab/date_low/date_up/id_import — un id_digitiser en query est
            // ignoré silencieusement, vérifié dans models.py). Un CRUVED « lire » large
            // (portée 2/3) renvoie donc les stations d'autrui : on ne garde que celles où le
            // compte est NUMÉRISATEUR ou OBSERVATEUR (bug terrain 2026-08-26).
            parserFeatureCollection(text, idRoleFiltre = idRole, ignorees = ignorees)
        }

    companion object {

        const val MODULE_CODE = "OCCHAB"

        // Codes mnémoniques des nomenclatures OccHab (contraintes CHECK du module gn_module_occhab).
        // Utilisés pour restreindre la synchro (GeoNatureSync.synchroniserNomenclatures) aux listes
        // pertinentes. NAT_OBJ_GEO est partagé avec Occtax (déjà synchronisé).
        val MNEMONIQUES_STATION = listOf(
            "EXPOSITION",             // id_nomenclature_exposure
            "NAT_OBJ_GEO",            // id_nomenclature_geographic_object
            "METHOD_CALCUL_SURFACE",  // id_nomenclature_area_surface_calculation
            "TYPE_SOL",               // id_nomenclature_type_sol
            "MOSAIQUE_HAB",           // id_nomenclature_type_mosaique_habitat
        )
        val MNEMONIQUES_HABITAT = listOf(
            "DETERMINATION_TYP_HAB",  // id_nomenclature_determination_type
            "TECHNIQUE_COLLECT_HAB",  // id_nomenclature_collection_technique
            "ABONDANCE_HAB",          // id_nomenclature_abundance
            "SENSIBILITE",            // id_nomenclature_sensitivity
            "HAB_INTERET_COM",        // id_nomenclature_community_interest
        )
        val MNEMONIQUES: Set<String> = (MNEMONIQUES_STATION + MNEMONIQUES_HABITAT).toSet()

        /** Les formats `yyyy-MM-dd'T'HH:mm:ss`, `yyyy-MM-dd HH:mm:ss` et `yyyy-MM-dd` des trois
         *  SimpleDateFormat d'Android, essayés dans cet ordre : l'heure est facultative. */
        private val DATE_SERVEUR = Regex("""^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}):(\d{2}))?""")

        /** Comme SimpleDateFormat.parse : heure LOCALE de l'appareil, et la fin de la chaîne
         *  ignorée (« 2026-05-01T10:00:00+02:00 » vaut 10 h locales). Écart : une date impossible
         *  (« 2026-02-30 ») n'est plus reportée au mois suivant, elle donne null. */
        private fun parserDate(s: String?): Long? {
            if (s.isNullOrBlank()) return null
            val (annee, mois, jour, heure, minute, seconde) = DATE_SERVEUR.find(s)?.destructured ?: return null
            return try {
                LocalDateTime(
                    annee.toInt(), mois.toInt(), jour.toInt(),
                    heure.toIntOrNull() ?: 0, minute.toIntOrNull() ?: 0, seconde.toIntOrNull() ?: 0,
                ).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        // ── Lectures sûres des properties (schéma StationSchema, include_fk=True) ──
        // optInt/optString d'org.json confondent « absent », « null » et 0/"null" : on passe par
        // opt() + isNull pour distinguer un champ réellement porté d'un champ vide — indispensable
        // depuis que la réédition RENVOIE ces champs au serveur (rien ne doit être inventé/écrasé).

        private fun entierOuNull(o: JSONObject, cle: String): Int? =
            if (o.isNull(cle)) null else (o.opt(cle) as? Number)?.toInt()

        private fun longOuNull(o: JSONObject, cle: String): Long? =
            if (o.isNull(cle)) null else (o.opt(cle) as? Number)?.toLong()

        private fun texteOuNull(o: JSONObject, cle: String): String? =
            if (o.isNull(cle)) null else (o.opt(cle) as? String)?.takeIf { it.isNotBlank() }

        /** id d'une nomenclature : la COLONNE `id_nomenclature_*` d'abord (toujours dumpée par le
         *  schéma serveur, include_fk=True), sinon l'OBJET imbriqué (présent quand la requête passe
         *  `nomenclatures=1`) — robustesse si un serveur n'expose que l'un des deux. */
        private fun idNomenclature(o: JSONObject, colonne: String, objetImbrique: String): Int? =
            entierOuNull(o, colonne)
                ?: o.optJSONObject(objetImbrique)?.let { entierOuNull(it, "id_nomenclature") }

        /**
         * Parse la FeatureCollection des stations serveur en capturant TOUT ce que la RÉÉDITION doit
         * préserver (une station importée puis renvoyée en mise à jour ne doit RIEN effacer côté
         * serveur) : identifiants (id_station, unique_id_sinp_station, id_habitat,
         * unique_id_sinp_hab), dates, observateurs, altitudes/profondeurs/surface/précision,
         * nomenclatures station et habitat, textes libres.
         */
        internal fun parserFeatureCollection(
            text: String,
            idRoleFiltre: Int? = null,
            /** Compteur des stations ÉCARTÉES faute de géométrie modélisable — pour que l'appelant
             *  puisse le dire à l'utilisateur au lieu d'annoncer un nombre faux. */
            ignorees: AtomicInt? = null,
        ): List<OccHabStation> {
            val root = try { JSONObject(text) } catch (_: Exception) { return emptyList() }
            val features = root.optJSONArray("features") ?: return emptyList()
            val stations = mutableListOf<OccHabStation>()
            for (i in 0 until features.length()) {
                val f = features.optJSONObject(i) ?: continue
                val props = f.optJSONObject("properties") ?: JSONObject()
                if (idRoleFiltre != null && idRoleFiltre > 0) {
                    val estNumerisateur = props.optInt("id_digitiser", -1) == idRoleFiltre
                    val estObservateur = props.optJSONArray("observers")?.let { obs ->
                        (0 until obs.length()).any { j ->
                            obs.optJSONObject(j)?.optInt("id_role", -1) == idRoleFiltre
                        }
                    } ?: false
                    // Propriétaire indéterminable (champs absents du schéma serveur) → on ÉCARTE :
                    // mieux vaut manquer une station que d'afficher celles d'autrui.
                    if (!estNumerisateur && !estObservateur) continue
                }
                // Géométrie non modélisable : la station est ÉCARTÉE plutôt que placée en (0, 0) —
                // une station invisible et non tapable pousse à la redessiner, donc au doublon.
                val geom = parserGeometrie(f.optJSONObject("geometry")) ?: run {
                    ignorees?.incrementAndGet()
                    continue
                }
                val (type, lat, lon, coordsJson) = geom
                // Bloc ANA-EVAL de la station (porté par `comment`) : même extraction que côté
                // habitat — sans bloc exploitable, le commentaire reste strictement inchangé.
                val commentBrut = texteOuNull(props, "comment")
                val anaEvalStation = AnaEval.extraireBlocJson(commentBrut)
                val dateMin = parserDate(props.optString("date_min").takeIf { it.isNotBlank() })
                val dateMax = parserDate(props.optString("date_max").takeIf { it.isNotBlank() })
                // Observateurs : ids + noms complets (UserSchema : nom_complet, sinon NOM prénom) —
                // sans eux, une mise à jour réduirait la liste serveur à l'utilisateur connecté.
                val obsIds = mutableListOf<Int>()
                val obsNoms = mutableListOf<String>()
                props.optJSONArray("observers")?.let { obs ->
                    for (j in 0 until obs.length()) {
                        val o = obs.optJSONObject(j) ?: continue
                        val idRole = entierOuNull(o, "id_role")?.takeIf { it > 0 } ?: continue
                        obsIds.add(idRole)
                        obsNoms.add(
                            texteOuNull(o, "nom_complet")
                                ?: listOfNotNull(texteOuNull(o, "nom_role"), texteOuNull(o, "prenom_role"))
                                    .joinToString(" ").ifBlank { "#$idRole" }
                        )
                    }
                }
                val habitats = mutableListOf<OccHabHabitat>()
                val habArr = props.optJSONArray("habitats")
                if (habArr != null) {
                    for (j in 0 until habArr.length()) {
                        val h = habArr.optJSONObject(j) ?: continue
                        val cdHab = h.optInt("cd_hab", 0)
                        val label = h.optJSONObject("habref")?.optString("lb_hab_fr", "")
                            ?.ifBlank { h.optString("nom_cite", "") } ?: h.optString("nom_cite", "")
                        // Bloc ANA-EVAL du plugin QGIS occhab-qgis : EXTRAIT de technical_precision
                        // (le texte stocké redevient purement humain, le JSON part dans anaEvalJson,
                        // re-fusionné à l'envoi). Pas de bloc exploitable → null, champ INCHANGÉ.
                        val precisionBrute = texteOuNull(h, "technical_precision")
                        val anaEvalHabitat = AnaEval.extraireBlocJson(precisionBrute)
                        val recouvrementColonne = h.optDouble("recovery_percentage").takeIf { !it.isNaN() }
                        habitats.add(OccHabHabitat(
                            // id serveur + UUID SINP : renvoyés à l'update pour que le serveur METTE À
                            // JOUR cet habitat (sinon il le supprimerait et en recréerait un autre).
                            idHabitatServeur = entierOuNull(h, "id_habitat")?.takeIf { it > 0 },
                            uuidHabitat = texteOuNull(h, "unique_id_sinp_hab"),
                            cdHab = cdHab,
                            habitatLabel = label,
                            nomCite = h.optString("nom_cite", ""),
                            determiner = texteOuNull(h, "determiner"),
                            // Recouvrement : champ DOUBLE du plugin (bloc + colonne native) — à la
                            // relecture le BLOC fait foi, repli sur recovery_percentage sans bloc.
                            recouvrement = AnaEval.recouvrementDuBloc(anaEvalHabitat) ?: recouvrementColonne,
                            precisionTechnique = if (anaEvalHabitat == null) precisionBrute
                                else AnaEval.texteHumain(precisionBrute).takeIf { it.isNotBlank() },
                            anaEvalJson = anaEvalHabitat,
                            idNomTypeDetermination = idNomenclature(
                                h, "id_nomenclature_determination_type", "nomenclature_determination_type"),
                            idNomTechniqueCollecte = idNomenclature(
                                h, "id_nomenclature_collection_technique", "nomenclature_collection_technique"),
                            idNomAbondance = idNomenclature(
                                h, "id_nomenclature_abundance", "nomenclature_abundance"),
                            idNomSensibilite = idNomenclature(
                                h, "id_nomenclature_sensitivity", "nomenclature_sensitivity"),
                            idNomInteretCommunautaire = idNomenclature(
                                h, "id_nomenclature_community_interest", "nomenclature_community_interest"),
                        ))
                    }
                }
                stations.add(OccHabStation(
                    // UUID SINP du serveur CONSERVÉ : un update le renvoie tel quel (en générer un
                    // nouveau écraserait unique_id_sinp_station côté serveur).
                    uuidStation = texteOuNull(props, "unique_id_sinp_station")
                        ?: uuidAleatoire(),
                    idStationServeur = props.optInt("id_station", -1).takeIf { it > 0 },
                    date = dateMin ?: maintenantMillis(),
                    geometryType = type,
                    latitude = lat,
                    longitude = lon,
                    geometryCoordsJson = coordsJson,
                    geometryTrousJson = geom.trousJson,
                    geometryPartielle = geom.partielle,
                    // Les droits de la station (`+cruved`) et l'état de son cadre d'acquisition,
                    // que la liste du serveur renvoie et que le web lit pour griser ses boutons.
                    nonModifiableServeur = props.optJSONObject("cruved")?.let { !it.optBoolean("U", true) } ?: false,
                    cadreFerme = props.optJSONObject("dataset")?.optJSONObject("acquisition_framework")?.let { !it.optBoolean("opened", true) } ?: false,
                    idDataset = props.optInt("id_dataset", -1).takeIf { it > 0 },
                    observateursIds = obsIds,
                    observateursNoms = obsNoms,
                    observateursTxt = texteOuNull(props, "observers_txt"),
                    stationName = texteOuNull(props, "station_name"),
                    comment = if (anaEvalStation == null) commentBrut
                        else AnaEval.texteHumain(commentBrut).takeIf { it.isNotBlank() },
                    anaEvalJson = anaEvalStation,
                    dateMin = dateMin,
                    dateMax = dateMax,
                    altitudeMin = entierOuNull(props, "altitude_min"),
                    altitudeMax = entierOuNull(props, "altitude_max"),
                    profondeurMin = entierOuNull(props, "depth_min"),
                    profondeurMax = entierOuNull(props, "depth_max"),
                    surface = longOuNull(props, "area"),
                    precision = entierOuNull(props, "precision"),
                    idNomExposition = idNomenclature(
                        props, "id_nomenclature_exposure", "nomenclature_exposure"),
                    idNomCalculSurface = idNomenclature(
                        props, "id_nomenclature_area_surface_calculation", "nomenclature_area_surface_calculation"),
                    idNomObjetGeographique = idNomenclature(
                        props, "id_nomenclature_geographic_object", "nomenclature_geographic_object"),
                    idNomTypeSol = idNomenclature(
                        props, "id_nomenclature_type_sol", "nomenclature_type_sol"),
                    // NB : le nom de la relation serveur est `type_mosaique_habitat` (sans préfixe).
                    idNomTypeMosaique = idNomenclature(
                        props, "id_nomenclature_type_mosaique_habitat", "type_mosaique_habitat"),
                    habitats = habitats,
                    envoyeGeoNature = true,
                    origineServeur = true,
                ))
            }
            return stations
        }

        /** Résultat du parsing d'une géométrie GeoJSON : type app, point représentatif, et
         *  sommets JSON `[[lon,lat], …]` (polygone/ligne) pour le tracé. */
        private data class GeomParse(
            val type: String, val lat: Double, val lon: Double, val coordsJson: String?,
            /** Anneaux intérieurs (trous) `[[[lon,lat], …], …]` — null si polygone plein. */
            val trousJson: String? = null,
            /** Géométrie tronquée à sa 1ʳᵉ partie (MultiPolygon multi-parties) → non importable. */
            val partielle: Boolean = false,
        )

        /** null = géométrie que l'application ne sait PAS modéliser (type inconnu, coordonnées
         *  illisibles, géométrie absente). Elle renvoyait auparavant un point à (0, 0) : la station
         *  entrait alors dans la liste, était COMPTÉE dans le message affiché, mais n'apparaissait ni
         *  sur la carte ni sous le doigt — au large de l'Afrique. L'utilisateur la croyait absente et
         *  la redessinait : doublon côté serveur, précédé d'un message qui mentait sur le nombre
         *  affiché. Le serveur n'impose aucun type (colonne `Geometry("GEOMETRY")`) : MultiPoint,
         *  MultiLineString et GeometryCollection sont stockables depuis QGIS (audit 2026-09-14,
         *  R6-M2). */
        private fun parserGeometrie(geom: JSONObject?): GeomParse? {
            if (geom == null) return null
            val type = geom.optString("type", "Point")
            val coords = geom.optJSONArray("coordinates") ?: return null
            return try {
                when (type) {
                    "Point" -> GeomParse("Point", coords.getDouble(1), coords.getDouble(0), null)
                    // coordinates = [ anneau extérieur, trou, … ] — un polygone dessiné sous QGIS
                    // peut porter des anneaux INTÉRIEURS (bug terrain 2026-08-31) : ils sont
                    // conservés dans geometryTrousJson et renvoyés tels quels à la mise à jour.
                    "Polygon" -> parserPolygone(coords, partielle = false)
                    "MultiPolygon" -> {
                        // coordinates = [ polygone, … ]. UNE seule partie = un Polygon (cas courant
                        // des couches QGIS multi-parties) → traité comme tel. PLUSIEURS parties :
                        // l'appli n'en modélise qu'une → affichée mais NON IMPORTABLE (la renvoyer
                        // supprimerait les autres parties côté serveur).
                        val premier = coords.optJSONArray(0) ?: return null
                        parserPolygone(premier, partielle = coords.length() > 1)
                    }
                    "LineString" -> {
                        val sommets = JSONArray()
                        var sLat = 0.0; var sLon = 0.0; var n = 0
                        for (k in 0 until coords.length()) {
                            val pt = coords.getJSONArray(k)
                            val lon = pt.getDouble(0); val lat = pt.getDouble(1)
                            sommets.put(JSONArray().put(lon).put(lat))
                            sLat += lat; sLon += lon; n++
                        }
                        GeomParse("LineString", if (n > 0) sLat / n else 0.0, if (n > 0) sLon / n else 0.0, sommets.toString())
                    }
                    else -> {
                        Journal.w("OccHabApi", "Géométrie non prise en charge : $type")
                        null
                    }
                }
            } catch (_: Exception) {
                Journal.w("OccHabApi", "Géométrie illisible (type $type)")
                null
            }
        }

        /**
         * Anneaux GeoJSON d'UN polygone (`[ extérieur, trou, … ]`) → [GeomParse].
         * Le point de FERMETURE de chaque anneau (dernier = premier) est RETIRÉ : ce n'est pas un
         * sommet pour l'éditeur (sinon deux markers superposés au 1er sommet et une poignée « + »
         * sur une arête de longueur nulle — audit 2026-08-27) ; `construireGeometrie` referme tous
         * les anneaux à l'envoi. Le point représentatif (latitude/longitude d'affichage) est le
         * centroïde de l'anneau EXTÉRIEUR. Un trou dégénéré (< 3 sommets) est ignoré.
         */
        private fun parserPolygone(rings: JSONArray, partielle: Boolean): GeomParse {
            val exterieur = anneauSommets(rings.optJSONArray(0))
            if (exterieur.isEmpty()) return GeomParse("Point", 0.0, 0.0, null)
            val sommets = JSONArray()
            exterieur.forEach { (lon, lat) -> sommets.put(JSONArray().put(lon).put(lat)) }
            val trous = JSONArray()
            for (i in 1 until rings.length()) {
                val trou = anneauSommets(rings.optJSONArray(i))
                if (trou.size < 3) continue
                trous.put(JSONArray().apply {
                    trou.forEach { (lon, lat) -> put(JSONArray().put(lon).put(lat)) }
                })
            }
            val n = exterieur.size
            return GeomParse(
                type = "Polygon",
                lat = exterieur.sumOf { it.second } / n,
                lon = exterieur.sumOf { it.first } / n,
                coordsJson = sommets.toString(),
                trousJson = if (trous.length() > 0) trous.toString() else null,
                partielle = partielle,
            )
        }

        /** UN anneau GeoJSON `[[lon,lat], …]` → paires (lon, lat), point de fermeture retiré.
         *  Une paire malformée est ignorée (robustesse : le reste de l'anneau est conservé). */
        private fun anneauSommets(ring: JSONArray?): List<Pair<Double, Double>> {
            if (ring == null) return emptyList()
            val points = mutableListOf<Pair<Double, Double>>()
            for (k in 0 until ring.length()) {
                val pt = ring.optJSONArray(k) ?: continue
                if (pt.length() < 2) continue
                points.add(pt.getDouble(0) to pt.getDouble(1))
            }
            if (points.size >= 4 && points.first() == points.last()) points.removeAt(points.size - 1)
            return points
        }
    }
}
