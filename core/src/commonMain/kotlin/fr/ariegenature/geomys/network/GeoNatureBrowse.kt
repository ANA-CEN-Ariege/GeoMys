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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/GeoNatureBrowse.kt) :
// HttpURLConnection remplacé par ClientsGeoNature, org.json par son portage, le JsonReader de Gson
// par lireTableauEnFlux (le corps est d'abord reçu en entier, en octets), Collator par
// comparateurFrancais, SimpleDateFormat par kotlinx-datetime. L'objet devient une classe ; les
// modèles passent dans model/. Les annulations de coroutine ne sont plus avalées par les
// `catch (_: Exception)`.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureListe
import fr.ariegenature.geomys.model.GeoNatureObservateur
import fr.ariegenature.geomys.model.HabitatSuggestion
import fr.ariegenature.geomys.model.ObsExplorer
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.corpsEnTampon
import fr.ariegenature.geomys.network.http.get
import fr.ariegenature.geomys.network.http.getEnFlux
import fr.ariegenature.geomys.network.http.postJson
import fr.ariegenature.geomys.network.http.postJsonEnFlux
import fr.ariegenature.geomys.plateforme.comparateurFrancais
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.util.maintenantMillis
import io.ktor.client.statement.bodyAsText
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class GeoNatureBrowse(
    private val auth: GeoNatureAuth,
    private val clients: ClientsGeoNature,
    private val taxRef: TaxRefCache,
    private val horloge: () -> Long = ::maintenantMillis,
) {

    /** IDs des jeux de données sur lesquels l'utilisateur a le droit de **créer** des observations
     *  pour [moduleCode] (CRUVED C), via `POST /api/meta/datasets` body `{"create":"OCCTAX"}` —
     *  exactement le filtre du formulaire web (cf. app officielle gn_mobile_core). Sert à NE proposer
     *  en saisie que les datasets créables. Une réponse vide du serveur fait foi ; null en cas d'échec
     *  (réseau, refus, réponse illisible) : l'appelant garde alors ce qu'il avait, faute de quoi une
     *  synchro hors ligne ne laissait plus aucun jeu à proposer. */
    suspend fun chargerIdsDatasetsCreables(config: GeoNatureConfig, moduleCode: String = "OCCTAX"): Set<Int>? =
        withContext(Dispatchers.IO) {
            try {
                val base = config.urlServeur.trim().trimEnd('/')
                val (token, _) = auth.login(base, config.login, config.motDePasse) ?: return@withContext null
                // Read timeout généreux : sur les serveurs à beaucoup de jeux de données (2500+),
                // la sérialisation côté serveur dépasse largement 10s (cf. chargerDatasets).
                val corps = clients.postJsonEnFlux(
                    "$base/api/meta/datasets?active=true", """{"create":"$moduleCode"}""", token,
                    timeoutMs = 15000, readTimeoutMs = 90000,
                ) { reponse -> if (reponse.status.value != 200) null else reponse.corpsEnTampon() }
                    ?: return@withContext null
                // Lecture en flux (cf. chargerDatasets) : on n'extrait que les id_dataset.
                lireTableauEnFlux(corps, CLES_TABLEAU, ::parseUnDataset).mapTo(HashSet()) { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
        }

    suspend fun chargerDatasets(config: GeoNatureConfig, moduleCode: String = "OCCTAX"): List<GeoNatureDataset> =
        withContext(Dispatchers.IO) {
            val base = config.urlServeur.trim().trimEnd('/')
            val (token, _) = auth.login(base, config.login, config.motDePasse)
                ?: throw GNErreur.AuthEchouee(401)

            // Filtre serveur : `active=true` + `module_code=<module>` (périmètre LECTURE — on garde
            // large pour que le cache serve aussi à la résolution des noms côté monitoring).
            // La restriction aux datasets CRÉABLES (CRUVED C) est appliquée à l'AFFICHAGE via
            // [chargerIdsDatasetsCreables] + GeoNatureConfig.datasetsCreables*. Chaque module
            // (OCCTAX, OCCHAB…) a son propre périmètre de jeux de données côté serveur.
            // `fields=modules` ramène le tableau des modules rattachés (utile au tracking).
            val url = "$base/api/meta/datasets?active=true&module_code=$moduleCode&fields=modules"
            // Read timeout généreux (90s) : `meta/datasets` n'est PAS paginé côté serveur et
            // `fields=modules` ajoute un joinedload + sérialisation imbriquée PAR jeu de données.
            // Sur un serveur à 2500+ jeux, la réponse dépasse les 10s par défaut → « blocage »/timeout.
            val corps = clients.getEnFlux(url, token, timeoutMs = 15000, readTimeoutMs = 90000) { reponse ->
                val code = reponse.status.value
                if (code != 200) throw GNErreur.EnvoiEchoue(code, "Impossible de charger les jeux de données")
                reponse.corpsEnTampon()
            }

            // Lecture en FLUX, élément par élément : on ne matérialise pas toute la réponse en une
            // String géante + JSONArray (crucial sur 2500+ jeux × fields=modules → réponse lourde).
            val result = try {
                lireTableauEnFlux(corps, CLES_TABLEAU, ::parseUnDataset)
            } catch (_: SerializationException) {
                // Corps 200 non-JSON (portail captif, proxy, page d'erreur HTML…) : traduit en
                // GNErreur lisible par l'appelant, comme le fait déjà chargerListesTaxons.
                throw GNErreur.EnvoiEchoue(0, "Datasets : format JSON inattendu")
            }
            // Tri alphabétique insensible à la casse + diacritiques pour un classement
            // intuitif quel que soit l'ordre serveur.
            result.sortedWith(compareBy(comparateurFrancais()) { it.nom })
        }

    /** Un jeu de données de `meta/datasets` ; null s'il n'a pas d'id ou de nom. */
    private fun parseUnDataset(element: JsonElement): GeoNatureDataset? {
        val objet = element as? JsonObject ?: return null
        val id = objet["id_dataset"].intOuNull() ?: -1
        val nom = objet["dataset_name"].chaineOuVide()
        val actif = objet["active"].booleenOuDefaut(true)
        val idTaxaList = objet["id_taxa_list"].intOuNull()?.takeIf { it > 0 }
        val modules = (objet["modules"] as? JsonArray).orEmpty().mapNotNull { module ->
            when {
                module is JsonPrimitive && module.isString ->
                    module.content.takeIf { it.isNotEmpty() }
                module is JsonObject -> module["module_code"].chaineOuVide().takeIf { it.isNotEmpty() }
                else -> null
            }
        }
        return if (id > 0 && nom.isNotEmpty()) GeoNatureDataset(id, nom, idTaxaList, actif, modules) else null
    }

    /** Télécharge la liste COMPLÈTE des habitats HABREF (sans `search_name` → tout le référentiel,
     *  ~29 000 entrées / ~5 Mo). Lecture en FLUX. Pour alimenter [HabitatCacheStore] à la synchro
     *  et rendre le champ habitat utilisable hors-ligne. Best-effort : [] en cas d'échec. */
    suspend fun chargerTousHabitats(config: GeoNatureConfig, moduleCode: String = "OCCTAX"): List<HabitatSuggestion> =
        withContext(Dispatchers.IO) {
            val base = config.urlServeur.trim().trimEnd('/')
            // Si le serveur configure une liste d'habitats (`<module>.ID_LIST_HABITAT`), on s'y
            // restreint comme le web. Absent ou login indisponible → tout HABREF. L'autocomplete
            // étant public, on ne dépend pas du login pour la liste elle-même : il ne sert qu'à
            // lire la config.
            val idList = try {
                auth.loginAvecCookies(base, config.login, config.motDePasse)
                    ?.let { (token, _, cookies) -> idListHabitat(base, token, cookies, moduleCode) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
            val filtreListe = idList?.let { "&id_list=$it" }.orEmpty()
            val url = "$base/api/habref/habitats/autocomplete?limit=200000$filtreListe"
            try {
                val corps = clients.getEnFlux(url, timeoutMs = 20000, readTimeoutMs = 90000) { reponse ->
                    if (reponse.status.value != 200) null else reponse.corpsEnTampon()
                } ?: return@withContext emptyList()
                lireTableauEnFlux(corps, CLES_TABLEAU, ::parseUnHabitat)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { emptyList() }
        }

    /** Un habitat de l'autocomplete HABREF ; null sans `cd_hab` valide. */
    private fun parseUnHabitat(element: JsonElement): HabitatSuggestion? {
        val objet = element as? JsonObject ?: return null
        val cd = objet["cd_hab"].intOuNull() ?: -1
        if (cd <= 0) return null
        val libelle = objet["search_name"].chaineOuVide().ifBlank { objet["lb_code"].chaineOuVide() }
            .trim().ifBlank { cd.toString() }
        return HabitatSuggestion(cd, libelle, objet["cd_typo"].intOuNull()?.takeIf { it > 0 })
    }

    suspend fun chargerListesTaxons(config: GeoNatureConfig): List<GeoNatureListe> =
        withContext(Dispatchers.IO) {
            val reponse = clients.get("${config.urlTaxhub}/api/biblistes", timeoutMs = 10000)
            val code = reponse.status.value
            if (code != 200) throw GNErreur.EnvoiEchoue(code, "Listes taxons : HTTP $code")
            val text = reponse.bodyAsText()
            val array: JSONArray = text.parserTableauJson("data", "items", "results")
                ?: throw GNErreur.EnvoiEchoue(code, "Listes taxons : format JSON inattendu")
            val result = mutableListOf<GeoNatureListe>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.optInt("id_liste", -1)
                val nom = item.optString("nom_liste", "")
                if (id > 0 && nom.isNotEmpty()) result.add(GeoNatureListe(id, nom))
            }
            result.sortedBy { it.nom }
        }

    /** Récupère la liste des observateurs Occtax.
     *
     *  Comme le **formulaire web Occtax** : la LISTE D'OBSERVATEURS configurée du module (menu
     *  UsersHub `OCCTAX.id_observers_list`, via `/api/users/menu/<id>`), et elle seule — liste curée,
     *  donc **sans les doublons de comptes** que renvoie `/api/users/roles` (un même « Prénom Nom »
     *  pouvant correspondre à plusieurs `id_role`). Le repli sur tous les rôles, quand la liste était
     *  vide ou illisible, est retiré (demande du 21 octobre) : une liste illisible lève une erreur,
     *  et la synchro garde alors celle du cache. */
    suspend fun chargerObservateurs(config: GeoNatureConfig): List<GeoNatureObservateur> =
        withContext(Dispatchers.IO) {
            val base = config.urlServeur.trim().trimEnd('/')
            val (token, _, cookies) = auth.loginAvecCookies(base, config.login, config.motDePasse)
                ?: throw GNErreur.AuthEchouee(401)
            val idListe = idListeObservateursOcctax(base, token, cookies)
                ?: throw GNErreur.EnvoiEchoue(0, "Observateurs : OCCTAX.id_observers_list illisible")
            chargerObservateursDeMenu(base, token, cookies, idListe)
                ?: throw GNErreur.EnvoiEchoue(0, "Observateurs : liste $idListe illisible")
        }

    /** Id du menu UsersHub « liste d'observateurs » du module Occtax, lu dans la config serveur
     *  (`/api/gn_commons/config` → `OCCTAX.id_observers_list`) — la MÊME source que le web. null si
     *  absent/illisible. */
    private suspend fun idListeObservateursOcctax(base: String, token: String?, cookies: String): Int? =
        configServeur(base, token, cookies)
            ?.optJSONObject("OCCTAX")?.optInt("id_observers_list", -1)?.takeIf { it > 0 }

    /** Id de la liste d'habitats HABREF d'un module (`/api/gn_commons/config` →
     *  `<moduleCode>.ID_LIST_HABITAT`, défaut `null` côté serveur) — pour restreindre la liste
     *  téléchargée à la même liste que le web. null si absent/illisible (→ tout HABREF). */
    private suspend fun idListHabitat(base: String, token: String?, cookies: String, moduleCode: String): Int? =
        configServeur(base, token, cookies)
            ?.optJSONObject(moduleCode)?.optInt("ID_LIST_HABITAT", -1)?.takeIf { it > 0 }

    /** `/api/gn_commons/config` lu en objet ; null si la réponse n'est pas un 200 JSON ou si le
     *  réseau échoue. Factorise les trois lectures identiques de la version Android. */
    private suspend fun configServeur(base: String, token: String?, cookies: String): JSONObject? =
        try {
            val reponse = clients.get("$base/api/gn_commons/config", token, cookies, 10000)
            if (reponse.status.value != 200) null else JSONObject(reponse.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { null }

    /** Id de la liste d'habitats HABREF du module OccHab (`/api/gn_commons/config` →
     *  `OCCHAB.ID_LIST_HABITAT`) — pour restreindre l'autocomplétion habitat à la MÊME liste
     *  que le formulaire web OccHab. null si absent/illisible (→ tout le référentiel HABREF). */
    suspend fun chargerIdListHabitatOccHab(config: GeoNatureConfig): Int? =
        withContext(Dispatchers.IO) {
            try {
                val base = config.urlServeur.trim().trimEnd('/')
                val (token, _, cookies) =
                    auth.loginAvecCookies(base, config.login, config.motDePasse)
                        ?: return@withContext null
                idListHabitat(base, token, cookies, "OCCHAB")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
        }

    /** Ce que le formulaire OccHab du web lit dans `OCCHAB` (`/gn_commons/config`) : `formConfig`
     *  (map champ→booléen de visibilité) sérialisé, null s'il est absent ; `OBSERVER_AS_TXT`
     *  (observateurs en texte libre, faux par défaut) ; les observateurs de la liste
     *  `OBSERVER_LIST_ID` (1 par défaut), null s'ils n'ont pu être lus. */
    data class ConfigOccHab(
        val formConfigJson: String?,
        val observateursEnTexte: Boolean,
        val observateurs: List<GeoNatureObservateur>?,
    )

    /** La [ConfigOccHab] du serveur, null s'il ne la donne pas. */
    suspend fun chargerConfigOccHab(config: GeoNatureConfig): ConfigOccHab? =
        withContext(Dispatchers.IO) {
            try {
                val base = config.urlServeur.trim().trimEnd('/')
                val (token, _, cookies) =
                    auth.loginAvecCookies(base, config.login, config.motDePasse)
                        ?: return@withContext null
                val occhab = configServeur(base, token, cookies)?.optJSONObject("OCCHAB") ?: return@withContext null
                ConfigOccHab(
                    formConfigJson = occhab.optJSONObject("formConfig")?.toString(),
                    observateursEnTexte = occhab.optBoolean("OBSERVER_AS_TXT", false),
                    observateurs = chargerObservateursDeMenu(base, token, cookies, occhab.optInt("OBSERVER_LIST_ID", 1)),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
        }

    /** Observateurs d'une liste UsersHub (`/api/users/menu/<id>`) : `id_role` + `nom_complet`
     *  (format « NOM Prénom », tel qu'affiché par le web). null si échec → l'appelant retombe sur
     *  `/users/roles`. */
    private suspend fun chargerObservateursDeMenu(
        base: String, token: String?, cookies: String, idListe: Int,
    ): List<GeoNatureObservateur>? =
        try {
            val reponse = clients.get("$base/api/users/menu/$idListe", token, cookies, 10000)
            if (reponse.status.value != 200) null
            else {
                val arr = reponse.bodyAsText()
                    .parserTableauJson("data", "items", "results") ?: JSONArray()
                val res = mutableListOf<GeoNatureObservateur>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val idRole = item.optInt("id_role", -1).takeIf { it > 0 } ?: continue
                    val nomComplet = item.optString("nom_complet", "").trim().ifEmpty {
                        listOf(item.optString("nom_role", "").trim(), item.optString("prenom_role", "").trim())
                            .filter { it.isNotEmpty() }.joinToString(" ")
                    }
                    if (nomComplet.isNotEmpty()) res.add(GeoNatureObservateur(idRole, nomComplet))
                }
                res.sortedBy { it.nomComplet.lowercase() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { null }

    suspend fun recupererObsExplorer(
        config: GeoNatureConfig,
        minLon: Double, minLat: Double, maxLon: Double, maxLat: Double
    ): List<ObsExplorer> = withContext(Dispatchers.IO) {
        val base = config.urlServeur.trim().trimEnd('/')
        val (token, _) = auth.login(base, config.login, config.motDePasse)
            ?: throw GNErreur.AuthEchouee(401)

        // Profondeur d'historique pilotée serveur (settings.area_observation_duration), défaut 365 j.
        // Date du jour de l'appareil, comme le SimpleDateFormat d'Android.
        val dateMin = Instant.fromEpochMilliseconds(horloge() - config.dureeObservationJours * 86_400_000L)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

        // `/synthese/for_web` lit ses filtres dans le CORPS JSON (`request.json`) — comme le web.
        // En GET ils sont IGNORÉS (le serveur renvoyait les 1000 premières obs de TOUTE la synthèse,
        // ensuite filtrées seulement côté client). On POST donc :
        //  - `geoIntersection` : l'emprise visible, en **Feature** GeoJSON (le backend refuse une
        //    géométrie nue : seuls `Feature`/`FeatureCollection` sont acceptés) ;
        //  - `date_min` : borne basse (1 an).
        // `limit` reste en paramètre d'URL (lu dans request.args).
        val emprise = """{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[[$minLon,$minLat],[$maxLon,$minLat],[$maxLon,$maxLat],[$minLon,$maxLat],[$minLon,$minLat]]]}}"""
        val body = """{"geoIntersection":$emprise,"date_min":"$dateMin"}"""

        val reponse = clients.postJson("$base/api/synthese/for_web?limit=1000", body, token, timeoutMs = 20000)

        if (reponse.status.value != 200) throw GNErreur.EnvoiEchoue(reponse.status.value, "Erreur synthèse")

        parseObsExplorer(reponse.bodyAsText(), minLon, minLat, maxLon, maxLat)
    }

    private fun parseObsExplorer(
        text: String,
        minLon: Double, minLat: Double, maxLon: Double, maxLat: Double
    ): List<ObsExplorer> {
        val result = mutableListOf<ObsExplorer>()
        val groupeParCdNom = taxRef.tousLesGroupes()
        val regneParCdNom = taxRef.tousLesRegnes()
        val groupe1ParCdNom = taxRef.tousLesGroupes1()
        try {
            val array: JSONArray = text.parserTableauJson("features", "data", "items", "results")
                ?: return result

            for (i in 0 until array.length()) {
                val feat = array.getJSONObject(i)
                var lat = Double.NaN; var lon = Double.NaN
                feat.optJSONObject("geometry")?.optJSONArray("coordinates")?.let {
                    lon = it.optDouble(0, Double.NaN); lat = it.optDouble(1, Double.NaN)
                }
                val props = feat.optJSONObject("properties") ?: feat
                if (lat.isNaN()) lat = props.optDouble("latitude", Double.NaN).let {
                    if (it.isNaN()) props.optDouble("lat", Double.NaN) else it
                }
                if (lon.isNaN()) lon = props.optDouble("longitude", Double.NaN).let {
                    if (it.isNaN()) props.optDouble("lon", Double.NaN) else it
                }
                if (lat.isNaN() || lon.isNaN()) continue
                if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) continue

                val group = props.optString("group2_inpn").ifEmpty {
                    props.optString("taxonomy_group2_inpn").ifEmpty {
                        props.optString("group_inpn", "")
                    }
                }
                // group1_inpn n'est généralement pas exposé par la synthèse → on retombe
                // sur le cache TaxRef indexé par cd_nom pour discriminer mollusques vs autres invertébrés.
                val group1Synthese = props.optString("group1_inpn", "")
                fun mapperTaxon(group2: String, cdNom: Int): Taxon? {
                    when (group2) {
                        "Oiseaux"    -> return Taxon.OISEAU
                        "Mammifères" -> return Taxon.MAMMIFERE
                        "Reptiles"   -> return Taxon.REPTILE
                        "Amphibiens" -> return Taxon.BATRACIEN
                        "Poissons"   -> return Taxon.POISSON
                        "Insectes"   -> return Taxon.INSECTE
                    }
                    val cdNomStr = cdNom.toString()
                    val group1 = group1Synthese.ifEmpty { groupe1ParCdNom[cdNomStr] ?: "" }
                    if (group1 == "Mollusques") return Taxon.MOLLUSQUE
                    return when (regneParCdNom[cdNomStr]) {
                        "Fungi"   -> Taxon.FONGE
                        "Plantae" -> Taxon.PLANTE
                        null      -> null
                        else      -> Taxon.INVERTEBRES
                    }
                }

                // Le groupe de la Synthèse, sinon celui du cache TaxRef. Un taxon absent du téléphone
                // n'est plus écarté (GeoMys-Android le faisait, et rangeait aux oiseaux ceux du cache
                // sans groupe) : l'observation est gardée sans groupe, icône neutre (21 octobre 2026).
                val cdNom = props.optInt("cd_nom", -1)
                val groupe = group.ifEmpty { groupeParCdNom[cdNom.toString()].orEmpty() }
                val taxon = if (groupe.isEmpty()) null else mapperTaxon(groupe, cdNom)

                result.add(obsFromProps(props, lat, lon, taxon))
            }
        } catch (_: Exception) {}
        return result
    }

    private fun obsFromProps(props: JSONObject, lat: Double, lon: Double, taxon: Taxon?): ObsExplorer {
        val cdNom = props.optInt("cd_nom", -1)
        var nomVern = props.optString("nom_vern").ifEmpty {
            props.optString("nom_vernaculaires").ifEmpty {
                props.optString("common_name", "")
            }
        }

        // Si GeoNature ne donne pas de nom vernaculaire, on cherche dans notre cache local
        if (nomVern.isEmpty() && cdNom > 0) {
            nomVern = taxRef.getVernaculaireParCdNom(cdNom) ?: ""
        }

        return ObsExplorer(
            nomCite = props.optString("nom_cite").ifEmpty {
                props.optString("lb_nom").ifEmpty {
                    // Le nom que `for_web` envoie par défaut (GeoNature 2.17 : le nom vernaculaire, à
                    // défaut le nom latin), sans nom_cite ni lb_nom : vu le 21 octobre sur le serveur de
                    // démonstration, où tout taxon sans nom français en cache restait « Espèce inconnue ».
                    props.optString("nom_vern_or_lb_nom").ifEmpty { props.optString("nom_valide", "Espèce inconnue") }
                }
            },
            nomVern = nomVern,
            nomSci = props.optString("lb_nom").ifEmpty { props.optString("nom_cite", "") },
            latitude = lat,
            longitude = lon,
            date = props.optString("date_min").ifEmpty { props.optString("date_obs", "") },
            nombre = props.optInt("count_min", 1).coerceAtLeast(1),
            taxon = taxon
        )
    }

    private companion object {
        /** Clés sous lesquelles un serveur peut envelopper le tableau d'une réponse. */
        val CLES_TABLEAU = setOf("data", "items", "results")

        // Lecture tolérante d'une valeur d'élément, comme les lireIntOuNull, lireStringOuVide et
        // lireBoolDefaut d'Android sur le JsonReader de Gson.

        /** Nombre entier, ou chaîne qui en contient un ; null sinon. */
        fun JsonElement?.intOuNull(): Int? {
            val valeur = this as? JsonPrimitive ?: return null
            if (valeur is JsonNull) return null
            if (valeur.isString) return valeur.content.toIntOrNull()
            // Gson lit aussi « 12.0 » ou « 1e2 » en entier (mais lève sur 12.5 : ici, null).
            return valeur.content.toIntOrNull()
                ?: valeur.content.toDoubleOrNull()?.let { d -> d.toInt().takeIf { it.toDouble() == d } }
        }

        /** Chaîne ou nombre (en texte) ; "" pour null, un booléen, un objet ou un tableau. */
        fun JsonElement?.chaineOuVide(): String {
            val valeur = this as? JsonPrimitive ?: return ""
            if (valeur is JsonNull) return ""
            return if (valeur.isString || !estBooleen(valeur)) valeur.content else ""
        }

        /** Booléen, ou chaîne « true » (casse ignorée) ; [defaut] pour null ou un autre type. */
        fun JsonElement?.booleenOuDefaut(defaut: Boolean): Boolean {
            val valeur = this as? JsonPrimitive ?: return defaut
            if (valeur is JsonNull) return defaut
            return when {
                valeur.isString -> valeur.content.toBoolean()
                estBooleen(valeur) -> valeur.content == "true"
                else -> defaut
            }
        }

        fun estBooleen(valeur: JsonPrimitive): Boolean = valeur.content == "true" || valeur.content == "false"
    }
}
