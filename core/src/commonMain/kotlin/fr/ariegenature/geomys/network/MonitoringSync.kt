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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/MonitoringSync.kt) :
// l'objet devient une classe sur l'authentification, la façade Monitoring, ses datalists et le
// cache des pictos ; runCatching remplacé par essayer, qui laisse passer l'annulation ;
// putIfAbsent (JVM) par getOrPut. Constantes et heuristique de nommage passent dans son
// compagnon.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.PictoCache
import fr.ariegenature.geomys.util.essayer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Pré-chargement de l'**arborescence structurelle** du module Suivis (gn_module_monitoring)
 *  pour usage hors-réseau.
 *
 *  Stratégie : BFS qui descend tant qu'on est dans des types "structurels" (sites_group,
 *  site, point, dalle, secteur…) et s'arrête à l'entrée d'un type "saisie" (visite,
 *  observation, releve…). Rationale : pour saisir une nouvelle visite offline il faut
 *  pouvoir naviguer jusqu'au parent de la saisie (le site/point) ; les visites et
 *  observations passées sont consultables uniquement en ligne (fetch réseau + cache
 *  write-through automatique côté [MonitoringApi]). Sans ce filtre, un protocole avec
 *  ~10 ans d'historique fait facilement 30000+ fiches inutiles à la saisie.
 *
 *  Concurrence bornée à [CHUNK_SIZE] requêtes parallèles pour ne pas saturer l'instance
 *  GeoNature. La profondeur max [PROFONDEUR_MAX] sert de garde-fou contre les cycles. */
class MonitoringSync(
    private val auth: GeoNatureAuth,
    private val api: MonitoringApi,
    private val datalists: MonitoringDatalists,
    private val pictos: PictoCache,
) {

    /** Charge tout le module Suivis pour usage offline.
     *  Callback `progression(moduleIdx, modulesTotaux, objetsCharges)` — `moduleIdx==0` avant le
     *  démarrage (pendant le fetch de la liste des modules), puis 1..N pendant l'itération. */
    suspend fun synchroniserSuivis(
        config: GeoNatureConfig,
        progression: (Int, Int, Int) -> Unit,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        progression(0, 0, 0)

        val modules = try { api.chargerModules(config) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { return@withContext Pair(0, "Modules monitoring : ${e.message}") }

        if (modules.isEmpty()) return@withContext Pair(0, "Aucun module monitoring exposé par le serveur.")

        var objetsCharges = 0
        var modulesOk = 0
        val modulesEnEchec = mutableListOf<String>()
        // Collecte les `id_list_observer` distincts pour fetcher chaque liste UsersHub
        // une seule fois à la fin (et pas N fois si plusieurs modules pointent dessus).
        val listesObserversAFetch = mutableSetOf<Int>()
        // Options des datalists/nomenclatures des formulaires, dédupliquées par apiUrl → préfetchées
        // en fin de synchro pour que la saisie d'une nouvelle visite marche HORS-LIGNE.
        val propsDatalistAPrefetch = mutableMapOf<String, MonitoringApi.MonitoringPropertySchema>()

        for ((idx, module) in modules.withIndex()) {
            progression(idx + 1, modules.size, objetsCharges)
            try {
                // Le schéma + les enfants directs sont indépendants → fetch en parallèle pour
                // gagner ~1 RTT par module.
                val (schema, enfantsParType) = coroutineScope {
                    val schemaDef = async { essayer { api.chargerSchemaProtocole(config, module.moduleCode) }.getOrNull() }
                    val enfantsDef = async { essayer { api.chargerEnfants(config, module.moduleCode) }.getOrDefault(emptyMap()) }
                    schemaDef.await() to enfantsDef.await()
                }
                // Le `id_list_observer` est porté par le type "module" du schéma. On le
                // mémoise pour le fetch groupé en fin de sync.
                schema?.get("module")?.idListObserver?.takeIf { it > 0 }
                    ?.let { listesObserversAFetch.add(it) }

                // Collecte des datalists/nomenclatures de tous les types du schéma (dédup par apiUrl).
                // Une liste d'observateurs propre à un champ (`users/menu/<id>`) rejoint celles du
                // module : le formulaire ne lit plus que le cache (décision du 20 octobre).
                schema?.values?.forEach { objet ->
                    objet.properties.values.forEach { p ->
                        p.apiUrl?.let { propsDatalistAPrefetch.getOrPut(it) { p } }
                        p.apiUrl?.let { LISTE_OBSERVATEURS.find(it) }?.groupValues?.get(1)?.toIntOrNull()
                            ?.takeIf { it > 0 }?.let { listesObserversAFetch.add(it) }
                    }
                }

                // BFS sur l'arborescence STRUCTURELLE — on filtre les types de saisie pour
                // ne pas précharger l'historique des visites/obs. Chaque "tâche" = (type, id)
                // à charger via chargerObjet.
                val visitees = HashSet<Pair<String, Int>>() // dédoublonne (type, id) si réfs croisés
                var niveau: List<Pair<String, Int>> = enfantsParType
                    .filterKeys { !estSaisie(it, schema) }
                    .flatMap { (type, l) -> l.map { type to it.id } }
                    .filter { visitees.add(it) }

                var profondeur = 0
                while (niveau.isNotEmpty() && profondeur < PROFONDEUR_MAX) {
                    profondeur++
                    val prochain = mutableListOf<Pair<String, Int>>()
                    niveau.chunked(CHUNK_SIZE).forEach { chunk ->
                        val fiches = coroutineScope {
                            chunk.map { (type, id) ->
                                async {
                                    essayer {
                                        api.chargerObjet(config, module.moduleCode, type, id)
                                    }.getOrNull()
                                }
                            }.awaitAll()
                        }
                        for (fiche in fiches) {
                            if (fiche == null) continue
                            objetsCharges++
                            // Sous-enfants depth=1 — déjà inclus dans la fiche. On skippe
                            // les types de saisie : ils seront fetchés à la volée en ligne
                            // si l'utilisateur les consulte (cache write-through prend le
                            // relais ensuite pour l'offline ultérieur).
                            for ((sousType, liste) in fiche.enfants) {
                                if (estSaisie(sousType, schema)) continue
                                for (enfant in liste) {
                                    val cle = sousType to enfant.id
                                    if (visitees.add(cle)) prochain.add(cle)
                                }
                            }
                        }
                        // Progression mise à jour à chaque chunk pour rester réactif sur les
                        // gros modules (la BFS d'un protocole STOM peut faire 500+ objets).
                        progression(idx + 1, modules.size, objetsCharges)
                    }
                    niveau = prochain
                }
                modulesOk++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                modulesEnEchec.add(module.moduleCode)
            }
        }

        // Fetch + persiste chaque liste d'observateurs distincte rencontrée pendant la BFS.
        // Échecs avalés : le sync principal est déjà OK, les observateurs sont un bonus.
        var observateursListes = 0
        if (listesObserversAFetch.isNotEmpty()) {
            coroutineScope {
                listesObserversAFetch.chunked(CHUNK_SIZE).forEach { chunk ->
                    chunk.map { idListe ->
                        async {
                            val arr = essayer {
                                datalists.chargerObservateursDeListe(config, idListe)
                            }.getOrNull()
                            if (arr != null) 1 else 0
                        }
                    }.awaitAll().sum().also { observateursListes += it }
                }
            }
        }

        // Prefetch des OPTIONS des datalists/nomenclatures des formulaires → saisie d'une nouvelle
        // visite possible HORS-LIGNE (sinon listes déroulantes vides = saisie bloquée). Dédup par
        // apiUrl ; observateurs exclus (déjà préchargés ci-dessus, cache dédié). chargerOptionsDatalist
        // écrit le cache en write-through. Best-effort, en parallèle borné, n'affecte pas le résultat.
        if (propsDatalistAPrefetch.isNotEmpty()) {
            coroutineScope {
                propsDatalistAPrefetch.values
                    .filterNot { it.apiUrl?.contains("users/menu/") == true }
                    .chunked(CHUNK_SIZE)
                    .forEach { chunk ->
                        chunk.map { p ->
                            async { essayer { api.chargerOptionsDatalist(config, p) } }
                        }.awaitAll()
                    }
            }
        }

        // Prefetch des pictogrammes de protocole → affichage instantané et HORS-LIGNE dans l'écran
        // Suivis (sinon re-téléchargés à chaque ouverture, et absents hors réseau). Best-effort :
        // n'affecte pas le résultat de la synchro ; re-écrit (rafraîchit) les images existantes.
        essayer {
            // Auth opportuniste : la session mise en cache par les appels du sync (login déjà
            // fait pendant la BFS) est réutilisée si encore fraîche — AUCUN login supplémentaire
            // n'est déclenché ici. Sans session, prefetch sans auth (OK serveur ANA).
            val session = auth.sessionEnCache(
                config.urlServeur.trim().trimEnd('/'), config.login)
            pictos.prefetch(
                config.urlServeur, modules.map { it.moduleCode to it.modulePicto },
                session?.first, session?.third)
        }

        val msg = buildString {
            append("$modulesOk/${modules.size} protocoles préchargés, $objetsCharges objets en cache")
            if (observateursListes > 0) append(", $observateursListes liste(s) d'observateurs")
            if (modulesEnEchec.isNotEmpty()) append("\n⚠ Modules en échec : ${modulesEnEchec.joinToString(",")}")
        }
        Pair(modulesOk, msg)
    }

    companion object {

        /** Types reconnus comme "saisie" par NOMMAGE — repli quand le schéma serveur est
         *  indisponible (la BFS de pré-chargement préfère désormais le signal `geometry_type` du
         *  schéma, cf. `estSaisie` dans [synchroniserSuivis]). Encore utilisé tel quel par les
         *  écrans qui affichent les boutons « + Nouveau X » à partir d'un simple nom de type.
         *  Insensible à la casse. Couvre les conventions OCCTAX (releve/occurrence/denombrement)
         *  et gn_module_monitoring (visite/observation), au pluriel pour les variantes serveur. */
        private val TYPES_SAISIE = setOf(
            "visite", "visites", "visit", "visits",
            "observation", "observations", "obs",
            "occurrence", "occurrences",
            "releve", "releves", "relevé", "relevés",
            "denombrement", "denombrements", "dénombrement", "dénombrements",
        )

        /** Garde-fou contre les schémas mal formés (cycle parent_type / children). En pratique
         *  les arborescences réelles structurelles dépassent rarement 4 niveaux. */
        private const val PROFONDEUR_MAX = 8
        /** Concurrence par niveau — empirique : 5 RTT en parallèle, c'est confortable sans
         *  faire monter la charge serveur. */
        private const val CHUNK_SIZE = 5

        /** L'api d'un champ observateurs : `users/menu/<id_liste>`. */
        private val LISTE_OBSERVATEURS = Regex("""users/menu/(\d+)""")

        /** Discriminateur structurel/saisie piloté par le SCHÉMA serveur : un objet structurel
         *  (sites_group, site, secteur, dalle…) porte une `geometry_type`, alors qu'un
         *  enregistrement (visite, observation) n'en a pas. On préfère ce signal serveur au
         *  nommage ; on ne retombe sur l'heuristique [estTypeSaisie] que si le type est absent du
         *  schéma (vieux protocole / schéma indisponible). La synchro ne charge que les objets
         *  structurels ; la fiche d'une saisie se met à jour depuis le serveur à son ouverture. */
        fun estSaisie(type: String, schema: Map<String, MonitoringApi.MonitoringSchemaObjet>?): Boolean {
            val obj = schema?.get(type)
            return if (obj != null) obj.geometryType == null else estTypeSaisie(type)
        }

        /** Vrai si [type] est un type de "saisie" (visite, observation, occurrence, …).
         *  Exposé pour que les écrans qui décident d'afficher un bouton "+ Nouveau X" puissent
         *  s'aligner sur la même heuristique de nommage. */
        fun estTypeSaisie(type: String): Boolean = type.trim().lowercase() in TYPES_SAISIE
    }
}
