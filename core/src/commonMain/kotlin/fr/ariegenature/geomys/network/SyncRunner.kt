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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/SyncRunner.kt) :
// LiveData remplacé par StateFlow, AtomicBoolean par celui d'atomicfu, Gson par listeVersJson.
// L'objet devient une classe sur la configuration et les services qu'il orchestre ; lancer()
// reprend le rôle de SyncForegroundService.start via ExecutionLongue. Les annulations de coroutine
// ne sont plus avalées par les `catch (_: Exception)` des étapes.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureListe
import fr.ariegenature.geomys.model.GeoNatureObservateur
import fr.ariegenature.geomys.plateforme.ExecutionLongue
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.HabitatCacheStore
import fr.ariegenature.geomys.store.StationsServeurCache
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import fr.ariegenature.geomys.store.io.listeVersJson
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Orchestration du « Recharger les données » découplée de toute UI, pour pouvoir tourner dans
 *  une [ExecutionLongue] (service au premier plan sous Android, tâche d'arrière-plan sous iOS) et
 *  donc CONTINUER même si l'utilisateur quitte l'écran de config, met l'écran en veille ou passe
 *  l'app en arrière-plan. L'état est exposé via [etat] (StateFlow) ; le service et l'écran de
 *  config s'y abonnent tous les deux (notification d'un côté, bandeau de progression de l'autre).
 *
 *  Reprend exactement les 7 étapes de l'ancien `chargerToutesLesDonnees` :
 *  datasets / listes / observateurs / champs additionnels (parallèle) → TaxRef → nomenclatures →
 *  Suivis. Seule la partie réseau + écriture des caches est ici ; le re-peuplement des spinners
 *  reste côté fragment (qui relit les caches JSON à la fin via `restaurerCaches`). */
class SyncRunner(
    private val config: GeoNatureConfig,
    private val browse: GeoNatureBrowse,
    private val additionalFields: AdditionalFieldsApi,
    private val geoNatureSync: GeoNatureSync,
    private val monitoring: MonitoringApi,
    private val monitoringSync: MonitoringSync,
    private val occHab: OccHabApi,
    private val habitatsOcctax: HabitatCacheStore,
    private val habitatsOccHab: HabitatCacheStore,
    private val stationsServeur: StationsServeurCache,
    private val taxRef: TaxRefCache,
    private val executionLongue: ExecutionLongue,
) {

    /** État courant de la synchro globale. */
    data class Etat(
        val enCours: Boolean,
        val texte: String,
        /** Vrai une fois la synchro terminée (succès ou échec partiel). */
        val termine: Boolean = false,
        /** Faux si au moins une étape a échoué. */
        val succes: Boolean = true,
        /** Compte-rendu final affichable (étapes en échec, résumé TaxRef…). */
        val resume: String? = null,
    )

    private val _etat = MutableStateFlow(Etat(enCours = false, texte = ""))
    val etat: StateFlow<Etat> = _etat.asStateFlow()

    /** Vrai tant qu'une synchro est en cours — garde atomique contre un double lancement. */
    private val enCours = atomic(false)
    val actif: Boolean get() = enCours.value

    private fun publier(texte: String) {
        _etat.value = Etat(enCours = true, texte = texte)
    }

    /** Lance [executer] hors du cycle de vie de l'écran : SyncForegroundService.start sous Android,
     *  désormais [ExecutionLongue] sur les deux plateformes. La notification suit [etat]. */
    fun lancer(forcerTaxRef: Boolean = false) {
        executionLongue.lancer(
            nom = "Synchronisation GeoNature",
            avancement = etat.map { it.texte.ifEmpty { "Synchronisation…" } },
        ) { executer(forcerTaxRef) }
    }

    /** Exécute la synchro complète. Idempotent vis-à-vis d'un appel concurrent : si une synchro
     *  est déjà en cours, retourne immédiatement. Ne lève pas — les échecs d'étape sont agrégés
     *  dans l'[Etat] terminal. */
    suspend fun executer(forcerTaxRef: Boolean = false) {
        // Garde atomique : un seul executer() à la fois, même appelé concurremment.
        if (!enCours.compareAndSet(false, true)) return
        try {
            publier("Préparation…")

            // PAS de purge des caches disque ici (audit 2026-08-23) : vider Nomenclature/
            // MonitoringCache avant tout appel réseau détruisait le hors-ligne existant si la
            // synchro échouait (serveur en erreur, coupure, kill) — précisément le scénario
            // terrain où l'on a besoin des anciennes données. Le rafraîchissement se fait par
            // REMPLACEMENT : NomenclatureCache.setAll écrase tout le cache d'un bloc en fin de
            // téléchargement réussi, et MonitoringSync écrit clé à clé (write-through). Coût
            // accepté : d'anciennes clés monitoring orphelines (module retiré du serveur)
            // subsistent sur disque — plus référencées, purgées par « Vider le cache ».
            // TaxRef suit déjà cette discipline (synchroniserTaxRef vide seulement après
            // téléchargement validé). Seuls les caches MÉMOIRE (résolveurs) sont invalidés.
            monitoring.invaliderCaches()

            val echecs = mutableListOf<String>()

            // Étapes 1-4 en parallèle (indépendantes, rapides). Chaque lambda écrit son cache
            // JSON et renvoie un message d'échec ou null. On charge AUSSI ici les modules
            // monitoring (une fois) pour en extraire les listes taxonomiques de protocoles, et les
            // passer à TaxRef — ça évite un chargerModules concurrent quand on lancera TaxRef et
            // Suivis en parallèle juste après.
            publier("Jeux de données, listes, observateurs…")
            var protocolListIds: Set<Int> = emptySet()
            var toutesLesListes = false
            coroutineScope {
                val mod = async {
                    try {
                        val modules = monitoring.chargerModules(config)
                        // + les listes propres à un champ espèce (`id_list`, que le `pnx-taxonomy` du web
                        // interroge) et celles des types d'objets : lues dans le schéma en cache,
                        // téléchargé à la première synchro.
                        listesDesSuivis(
                            modules.map { m ->
                                m.idListTaxonomy to (
                                    monitoring.lireSchemaProtocole(m.moduleCode)
                                        ?: try { monitoring.chargerSchemaProtocole(config, m.moduleCode) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                                    )
                            },
                        )
                    }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { ListesDesSuivis(emptySet(), false) }
                }
                val ds = async {
                    try {
                        val r = browse.chargerDatasets(config)
                        if (r.isNotEmpty()) { config.datasetsCacheJson = listeVersJson(GeoNatureDataset.serializer(), r); null }
                        else "Aucun jeu de données"
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { e.message ?: "Erreur datasets" }
                }
                // Datasets CRÉABLES en Occtax (CRUVED C) — pour aligner la liste proposée sur le web.
                // Best-effort : si indisponible, set vide = pas de restriction.
                val dsCre = async {
                    // Un échec garde les créables précédents : vidés, plus aucun jeu ne serait proposé.
                    browse.chargerIdsDatasetsCreables(config)?.let { config.datasetsCreablesOcctax = it }
                    null
                }
                val li = async {
                    try {
                        val r = browse.chargerListesTaxons(config)
                        if (r.isNotEmpty()) { config.listesCacheJson = listeVersJson(GeoNatureListe.serializer(), r); null }
                        else "Aucune liste de taxons"
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { e.message ?: "Erreur listes" }
                }
                val obs = async {
                    try {
                        val r = browse.chargerObservateurs(config)
                        if (r.isNotEmpty()) { config.observateursCacheJson = listeVersJson(GeoNatureObservateur.serializer(), r); null }
                        else "Aucun observateur"
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { e.message ?: "Erreur observateurs" }
                }
                val add = async {
                    try {
                        // La réponse du serveur fait foi, vide comprise : des champs supprimés sur le
                        // serveur ne doivent plus être proposés. Un échec (exception) garde le cache.
                        val r = additionalFields.charger(config, "OCCTAX")
                        config.additionalFieldsOcctaxJson = listeVersJson(AdditionalFieldDef.serializer(), r)
                        null
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { e.message ?: "Erreur champs additionnels" }
                }
                // Config de visibilité des champs Occtax (settings.json serveur). Best-effort, non
                // bloquant : sans config publiée le registre par défaut s'applique (tous champs visibles).
                val set = async {
                    geoNatureSync.synchroniserSettingsOcctax(config)
                    null
                }
                // Liste COMPLÈTE des habitats HABREF → cache local, pour que le champ habitat marche
                // hors-ligne (cf. HabitatCache). Best-effort : on ne casse pas la synchro si échec, et
                // on n'écrase pas un cache existant si le téléchargement revient vide.
                val hab = async {
                    try {
                        val h = browse.chargerTousHabitats(config)
                        if (h.isNotEmpty()) habitatsOcctax.remplacerTout(h)
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) { /* habitat optionnel */ }
                }
                // Détection des modules OccHab + Occtax (+ droits CRUVED, UN seul appel) →
                // drapeaux de config qui conditionnent tuile d'accueil et boutons d'envoi.
                // Best-effort : module absent/inaccessible = tuile masquée (aucun ajout aux
                // échecs) ; Occtax absent de la réponse → drapeau INCHANGÉ (défaut permissif).
                val occhab = async {
                    try {
                        val texteModules = occHab.chargerModulesGeoNature(config)
                        val modules = texteModules?.let { occHab.modulesDetectes(it, setOf(OccHabApi.MODULE_CODE, "OCCTAX")) }.orEmpty()
                        // Les droits de création des Suivis, par protocole et type d'objet, comme le web.
                        if (texteModules != null) {
                            try { monitoring.enregistrerDroits(texteModules) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                        }
                        // Map VIDE = appel RATÉ (offline/timeout/annulé) → on NE TOUCHE PAS aux
                        // drapeaux : sinon un simple incident de synchro ferait DISPARAÎTRE la tuile
                        // OccHab et bloquerait l'envoi. Non vide (OCCTAX présent sur toute instance
                        // GeoNature) = appel réussi, on peut mettre les droits à jour.
                        if (modules.isNotEmpty()) {
                        modules["OCCTAX"]?.let { config.occtaxPeutCreer = it.peutCreer }
                        val acces = modules[OccHabApi.MODULE_CODE] ?: OccHabAcces.ABSENT
                        config.occhabDisponible = acces.disponible
                        config.occhabPeutCreer = acces.peutCreer
                        // Datasets propres au module OccHab (périmètre serveur distinct d'OCCTAX)
                        // + ids créables — pour ne proposer à la saisie que les bons JDD.
                        if (acces.disponible) {
                            try {
                                // Une liste vide du serveur fait foi, comme sur le web ; un échec garde
                                // le cache.
                                val ds = browse.chargerDatasets(config, "OCCHAB")
                                config.datasetsOcchabCacheJson = listeVersJson(GeoNatureDataset.serializer(), ds)
                            } catch (e: CancellationException) { throw e
                            } catch (_: Exception) { /* datasets OccHab best-effort */ }
                            browse.chargerIdsDatasetsCreables(config, "OCCHAB")?.let { config.datasetsCreablesOcchab = it }
                            // Liste HABREF du module (OCCHAB.ID_LIST_HABITAT) → restreint
                            // l'autocomplétion habitat à la même liste que le web.
                            config.occhabIdListHabitat =
                                browse.chargerIdListHabitatOccHab(config) ?: -1
                            // Cache HABREF DÉDIÉ OccHab → autocomplétion hors-ligne avec les mêmes
                            // valeurs que le web. Best-effort ; on n'écrase pas si le retour est vide.
                            try {
                                val hab = browse.chargerTousHabitats(config, "OCCHAB")
                                if (hab.isNotEmpty())
                                    habitatsOccHab.remplacerTout(hab)
                            } catch (e: CancellationException) { throw e
                            } catch (_: Exception) { /* habitats OccHab optionnels */ }
                            // formConfig (visibilité des champs), observateurs (texte libre ou liste
                            // du module) + défauts de nomenclature (ex. « In situ ») → formulaire
                            // identique au web.
                            try {
                                browse.chargerConfigOccHab(config)?.let { c ->
                                    c.formConfigJson?.let { config.occhabFormConfigJson = it }
                                    config.occhabObservateursEnTexte = c.observateursEnTexte
                                    c.observateurs?.let { config.observateursOcchabCacheJson = listeVersJson(GeoNatureObservateur.serializer(), it) }
                                }
                                val defauts = occHab.chargerDefautsNomenclatures(config)
                                if (defauts.isNotEmpty()) config.occhabDefautsNomencJson = JSON_STOCKAGE.encodeToString(DEFAUTS_NOMENCLATURES, defauts)
                            } catch (e: CancellationException) { throw e
                            } catch (_: Exception) { /* formConfig/défauts OccHab optionnels */ }
                            // Stations serveur de l'UTILISATEUR (tous JDD confondus, filtre client
                            // numérisateur/observateur dans chargerStations) → StationsServeurCache :
                            // la carte OccHab les affiche/aimante/importe HORS-LIGNE (repli du
                            // chargement réseau). Best-effort ; SUCCÈS = remplacement (même vide :
                            // l'utilisateur peut ne plus avoir de station), ÉCHEC (exception) =
                            // cache intact — jamais d'anciennes stations écrasées par un raté.
                            try {
                                stationsServeur
                                    .remplacerTout(occHab.chargerStations(config))
                            } catch (e: CancellationException) { throw e
                            } catch (_: Exception) { /* stations serveur best-effort */ }
                        }
                        } // fin if (modules.isNotEmpty())
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) { /* OccHab optionnel */ }
                }
                val resultatsA = listOf(
                    "Jeux de données" to ds.await(),
                    "Listes de taxons" to li.await(),
                    "Observateurs" to obs.await(),
                    "Champs additionnels" to add.await(),
                    "Config champs OCCTAX" to set.await(),
                    "Datasets créables" to dsCre.await(),
                )
                resultatsA.forEach { (nom, err) -> if (err != null) echecs += "$nom ($err)" }
                mod.await().let {
                    protocolListIds = it.ids
                    toutesLesListes = it.champSansListe
                }
                hab.await() // habitats : best-effort, pas d'ajout aux échecs
                occhab.await() // détection module OccHab : best-effort
            }

            // Plus de purge après une mise à jour de l'application (21 octobre 2026) : la phase B
            // réécrit ses caches, et TaxRef n'est retéléchargé que si sa version a changé sur le
            // serveur ou si le format de son cache a changé (GeoNatureSync.synchroniserTaxRef).

            // Étapes 5-7 EN PARALLÈLE : caches indépendants (TaxRef / nomenclatures / monitoring),
            // et seul Suivis charge les modules → pas de course. On masque ainsi la durée des
            // étapes courtes (nomenclatures, Suivis) sous l'étape longue (TaxRef), qui pilote seule
            // le bandeau de progression (sinon les textes des 3 étapes clignoteraient).
            var nbTaxons = 0
            var msgTaxRef = ""
            var nbNom = 0
            var msgNom = ""
            var nbModulesOk = 0
            var msgSuivis = ""
            // Chaque étape est isolée : une erreur (y compris OutOfMemoryError sur un gros TaxRef,
            // ou un JSON inattendu d'un autre serveur) est convertie en (0, "<Type>: <msg>") et
            // remontée dans `echecs`, au lieu d'aborter toute la synchro ou de planter l'appli.
            // L'annulation de coroutine (CancellationException) est re-levée (jamais avalée).
            fun erreurEtape(e: Throwable): Pair<Int, String> =
                0 to "${e::class.simpleName}: ${e.message?.take(80).orEmpty()}"
            coroutineScope {
                val taxJob = async {
                    try {
                        geoNatureSync.synchroniserTaxRef(config, protocolListIds, forcerTaxRef, toutesLesListes) { fait, listeIdx, listesTotales ->
                            publier(
                                if (listesTotales == 0) "Récupération des taxons…"
                                else "Liste $listeIdx/$listesTotales — $fait taxons cumulés…"
                            )
                        }
                    } catch (c: CancellationException) { throw c
                    } catch (e: Throwable) { erreurEtape(e) }
                }
                val nomJob = async {
                    try { geoNatureSync.synchroniserNomenclatures(config) }
                    catch (c: CancellationException) { throw c }
                    catch (e: Throwable) { erreurEtape(e) }
                }
                val suiviJob = async {
                    try { monitoringSync.synchroniserSuivis(config) { _, _, _ -> } }
                    catch (c: CancellationException) { throw c }
                    catch (e: Throwable) { erreurEtape(e) }
                }
                taxJob.await().let { (n, m) -> nbTaxons = n; msgTaxRef = m }
                nomJob.await().let { (n, m) -> nbNom = n; msgNom = m }
                suiviJob.await().let { (n, m) -> nbModulesOk = n; msgSuivis = m }
            }
            // Aucune liste encore utile (liste à choisir dans Paramètres) n'est pas une erreur.
            if (nbTaxons == 0 && msgTaxRef != GeoNatureSync.AUCUNE_LISTE_UTILE) echecs += "TaxRef (${msgTaxRef.take(80)})"
            if (nbNom == 0) echecs += "Nomenclatures ($msgNom)"
            // "Aucun module monitoring exposé" n'est pas une erreur (instance sans monitoring).
            if (nbModulesOk == 0 && !msgSuivis.startsWith("Aucun")) echecs += "Suivis (${msgSuivis.take(80)})"

            val resume = buildString {
                if (echecs.isNotEmpty()) {
                    append("⚠ Chargement incomplet — étape(s) en échec :\n")
                    echecs.forEach { append("  • $it\n") }
                    append("Vous pouvez relancer « Recharger les données ».\n\n")
                }
                // Détail des compteurs (taxons, protocoles, listes…) affiché dans l'écran Paramètres
                // (boîte « Chargement des données »), plus dans ce message : on ne garde ici que les
                // avertissements éventuels (TaxRef partiel, nomenclatures absentes).
                append(msgTaxRef)
                if (nbTaxons > 0 && nbNom == 0) append("\n⚠ Nomenclatures : $msgNom")
            }
            // Données de CETTE release désormais chargées (le bandeau de Paramètres qui suggère de
            // recharger après une mise à jour s'efface) — dès que TaxRef est en place, même si une
            // étape secondaire a échoué.
            if (taxRef.versionSauvegardee != null) {
                config.versionDonneesChargees = GeoNatureConfig.VERSION_DONNEES_REQUISE
            }
            _etat.value = Etat(enCours = false, texte = "Terminé", termine = true, succes = echecs.isEmpty(), resume = resume)
        } catch (c: CancellationException) {
            // Annulation normale (vue détruite, service arrêté) : on relève, on ne « termine » pas.
            throw c
        } catch (e: Throwable) {
            // Filet de sécurité ULTIME : capture TOUT (y compris les Error comme OutOfMemoryError,
            // que `catch (Exception)` laissait passer → l'appli plantait au chargement sur certains
            // serveurs). On affiche un message identifiant le type d'erreur plutôt que de crasher.
            val type = e::class.simpleName
            val detail = e.message?.take(120)?.let { " — $it" }.orEmpty()
            // OutOfMemoryError n'existe que sur la JVM ; sous iOS, le système termine l'appli.
            val indice = if (type == "OutOfMemoryError") " (données trop volumineuses pour la mémoire)" else ""
            _etat.value =
                Etat(enCours = false, texte = "Échec", termine = true, succes = false,
                    resume = "Chargement interrompu : $type$detail$indice\nVous pouvez réessayer « Recharger les données ».")
        } finally {
            enCours.value = false
        }
    }

    /** Réarme l'état après que le fragment a consommé un résultat terminal — évite de re-traiter
     *  le même « termine » à chaque réabonnement. */
    fun accuserReception() {
        if (_etat.value.termine) _etat.value = Etat(enCours = false, texte = "")
    }

    private companion object {
        /** Défauts de nomenclature OccHab (mnémonique → id), en JSON comme le faisait Gson. */
        val DEFAUTS_NOMENCLATURES = MapSerializer(String.serializer(), Int.serializer())
    }
}

/** Les listes de taxons des Suivis ([ids]) ; [champSansListe] : un champ espèce n'en a aucune. */
data class ListesDesSuivis(val ids: Set<Int>, val champSansListe: Boolean)

/** Les listes de taxons dont les protocoles ([protocoles] : la liste de chaque protocole, et son
 *  schéma) peuvent avoir besoin : celle du protocole, de chaque type d'objet et de chaque champ
 *  espèce. Un champ espèce sans liste propre (ni `id_list` ni api `taxref/allnamebylist/<id>`)
 *  cherche dans tout TaxRef, comme le web, même si son protocole en déclare une (21 octobre) :
 *  [ListesDesSuivis.champSansListe]. Un champ masqué (le `cd_nom` du type « module ») ne compte pas. */
fun listesDesSuivis(protocoles: List<Pair<Int?, Map<String, MonitoringApi.MonitoringSchemaObjet>?>>): ListesDesSuivis {
    val ids = mutableSetOf<Int>()
    var sansListe = false
    for ((listeDeclaree, schema) in protocoles) {
        val listeProtocole = listeDeclaree?.takeIf { it > 0 }
        listeProtocole?.let(ids::add)
        val objets = schema?.values.orEmpty()
        for (objet in objets) {
            objet.idListTaxonomy?.let(ids::add)
            for (p in objet.properties.values) {
                p.idListTaxonomie?.let(ids::add)
                val espece = p.typeWidget.equals("taxonomy", ignoreCase = true)
                if (espece && !p.hiddenBool && p.idListTaxonomie == null) sansListe = true
            }
        }
    }
    return ListesDesSuivis(ids.filter { it > 0 }.toSet(), sansListe)
}
