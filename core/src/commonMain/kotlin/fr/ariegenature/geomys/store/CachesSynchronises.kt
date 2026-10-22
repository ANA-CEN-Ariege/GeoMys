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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/CachesSynchronises.kt) :
// les fonctions deviennent une classe sur les caches qu'elles vident. Le rechargement forcé après une
// mise à jour (armerRechargementSiRequis, et sa purge dans SyncRunner) est retiré le 21 octobre 2026 :
// une réinstallation garde tout et ne demande rien, comme GeoMys-Android, dont la version des données
// n'a jamais changé.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.network.MonitoringApi

class CachesSynchronises(
    private val taxRef: TaxRefCache,
    private val nomenclatures: NomenclatureCache,
    private val habitatsOcctax: HabitatCacheStore,
    private val habitatsOccHab: HabitatCacheStore,
    private val stationsServeur: StationsServeurCache,
    private val monitoringCache: MonitoringCache,
    private val pictos: PictoCache,
    private val monitoring: MonitoringApi,
) {

    /**
     * Purge les caches SYNCHRONISÉS (TaxRef, nomenclatures, habitats, monitoring, pictos) — ce que
     * « Recharger les données » réécrit. Ne touche NI aux saisies locales (Mes saisies / Mes stations /
     * Mes visites), NI aux JSON de config (datasets / listes / observateurs), NI aux sélections.
     * Utilisé par le bouton « Vider le cache » de Paramètres.
     */
    fun viderCachesSynchronises() {
        viderCachesReecritsEnPhaseB()
        // Caches remplis par la PHASE A de SyncRunner. Le bouton « Vider le cache » les efface (c'est
        // son contrat : tout enlever), mais le rechargement après mise à jour NON : cf.
        // [viderCachesReecritsEnPhaseB].
        habitatsOcctax.vider()
        habitatsOccHab.vider()
        stationsServeur.vider()
    }

    /**
     * Purge réservée au RECHARGEMENT APRÈS MISE À JOUR (SyncRunner, une fois le serveur joignable) :
     * n'efface QUE les caches que la phase B réécrit derrière elle — TaxRef, nomenclatures, monitoring,
     * pictogrammes.
     *
     * Les caches de la PHASE A — les deux HABREF et les stations serveur — sont volontairement
     * ÉPARGNÉS : la phase A vient de les remplacer EN BLOC (`remplacerTout`) quelques lignes plus haut,
     * et la phase B ne les réécrit jamais. Les purger ici revenait à effacer ce que la synchro venait
     * tout juste d'écrire : après un rechargement pourtant annoncé réussi, plus aucun habitat proposé
     * hors ligne — donc saisie OccHab IMPOSSIBLE, l'habitat y étant obligatoire — et plus aucune
     * station serveur. Audit 2026-09-14, R1-C2.
     *
     * Si une étape best-effort de la phase A a échoué, son cache garde son contenu précédent : périmé
     * vaut mieux que vide sur une application dont tout l'usage est hors réseau.
     */
    fun viderCachesReecritsEnPhaseB() {
        taxRef.vider()
        nomenclatures.vider()
        monitoringCache.vider()
        pictos.vider()  // pictos de protocole (cache disque)
        // MonitoringCache.vider() n'efface que le DISQUE : la liste des modules est aussi gardée en
        // mémoire par MonitoringApi (dernierChargement) et countModulesEnCache() la renvoie en
        // priorité — sans cette invalidation, le compteur de protocoles reste figé après une purge.
        monitoring.invaliderCaches()
    }
}
