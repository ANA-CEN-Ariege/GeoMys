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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/StationsServeurCache.kt) :
// l'objet devient une classe sur le dossier des fichiers ; java.io remplacé par okio et
// FichierAtomique, Gson par kotlinx.serialization (même format), @Synchronized par un verrou
// d'atomicfu. La normalisation après lecture devient un filtre avant décodage (stationDepuisJson).

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.plateforme.exclureDesSauvegardes
import fr.ariegenature.geomys.store.io.FichierAtomique
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import fr.ariegenature.geomys.util.maintenantMillis
import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * Cache local des stations OccHab DU SERVEUR appartenant à l'utilisateur (numérisateur ou
 * observateur — le filtre client de `OccHabApi.chargerStations` est appliqué AVANT écriture :
 * on ne persiste jamais les stations d'autrui), pour que la carte OccHab puisse les afficher,
 * les aimanter et les IMPORTER **hors ligne**.
 *
 * Alimenté à deux moments :
 *  - « Recharger les données » ([fr.ariegenature.geomys.network.SyncRunner], best-effort) :
 *    [remplacerTout] avec les stations de TOUS les jeux de données ;
 *  - chargement réseau réussi depuis la carte (un seul JDD) : [remplacerJdd] au fil de l'eau.
 *
 * La carte lit par JDD via [lire] (invariant carte : compte ET jeu de données de la saisie).
 * Purgé par « Vider le cache » ([viderCachesSynchronises]) et au changement d'identité serveur
 * ([fr.ariegenature.geomys.network.invaliderCachesSession] — URL/login/mdp : les id_station et
 * id_dataset sont propres à chaque instance, et les stations d'un autre compte n'ont rien à
 * faire ici). Fichier (trop gros pour SharedPreferences), écriture atomique comme
 * [HabitatCacheStore]. Une seule instance dans l'appli, sur le dossier des caches retéléchargeables
 * [dir] (`filesDir/caches`), exclu des sauvegardes.
 */
class StationsServeurCache(private val dir: Path, private val systeme: FileSystem = FileSystem.SYSTEM) {

    private val verrou = SynchronizedObject()

    /** Contenu du fichier : version du format, date du dernier remplacement (epoch millis, pour
     *  informer l'utilisateur de la fraîcheur en mode hors-ligne) + stations filtrées utilisateur.
     *  [versionFormat] vaut 0 sur un fichier écrit avant l'introduction du champ. */
    @Serializable
    private data class Contenu(
        val dateChargement: Long = 0L,
        val stations: List<OccHabStation>? = emptyList(),
        val versionFormat: Int = 0,
    ) {
        val liste: List<OccHabStation> get() = stations ?: emptyList()
    }

    /** Le fichier tel qu'il est lu : les stations restent brutes jusqu'au filtre de relecture. */
    @Serializable
    private data class ContenuBrut(
        val dateChargement: Long = 0L,
        val stations: List<JsonElement>? = emptyList(),
        val versionFormat: Int = 0,
    )

    @Volatile private var mem: Contenu? = null

    private fun fichier(): Path = dir / NOM_FICHIER

    private fun charger(): Contenu {
        mem?.let { return it }
        val f = fichier()
        if (!systeme.exists(f)) return Contenu()
        return try {
            val brut = JSON_STOCKAGE.decodeFromString(ContenuBrut.serializer(), systeme.read(f) { readUtf8() })
            // Fichier écrit par une version dont le parseur de géométrie ne savait pas tout lire :
            // on l'ignore et on le supprime. Le prochain chargement carte (ou la prochaine synchro)
            // le réécrira au format courant ; d'ici là, l'utilisateur hors ligne n'a simplement pas
            // de station serveur — ce qui vaut mille fois mieux que d'en afficher une amputée de
            // ses trous et de la renvoyer ainsi au serveur (audit 2026-09-14, R3-C1).
            if (brut.versionFormat != VERSION_FORMAT) {
                runCatching { systeme.delete(f) }
                return Contenu().also { mem = it }
            }
            // Station par station : une station structurellement invalide (fichier tronqué, écrit
            // par une autre version ou édité à la main) est écartée sans perdre les autres (R2-m1).
            val stations = brut.stations.orEmpty().mapNotNull(::stationDepuisJson)
            Contenu(brut.dateChargement, stations, brut.versionFormat).also { mem = it }
        } catch (_: Exception) { Contenu() }
    }

    private fun ecrire(contenu: Contenu): Unit = synchronized(verrou) {
        mem = contenu
        try {
            systeme.createDirectories(dir)
            exclureDesSauvegardes(dir)
            FichierAtomique(systeme, fichier(), "StationsServeurCache")
                .ecrire(JSON_STOCKAGE.encodeToString(Contenu.serializer(), contenu))
        } catch (_: Exception) { /* cache best-effort */ }
    }

    /** Remplace TOUT le cache (chemin de synchro, tous JDD confondus). Une liste VIDE est un
     *  résultat valable (l'utilisateur n'a plus de station) : l'appelant ne doit appeler qu'après
     *  un chargement réseau RÉUSSI — sur échec (exception), il ne touche pas au cache. */
    fun remplacerTout(stations: List<OccHabStation>): Unit = synchronized(verrou) {
        ecrire(Contenu(maintenantMillis(), stations, VERSION_FORMAT))
    }

    /** Remplace les stations d'UN jeu de données (chargement réseau réussi depuis la carte),
     *  en conservant celles des autres JDD (chargées par la synchro). */
    fun remplacerJdd(idJdd: Int, stations: List<OccHabStation>): Unit = synchronized(verrou) {
        val autres = charger().liste.filter { it.idDataset != idJdd }
        ecrire(Contenu(maintenantMillis(), autres + stations, VERSION_FORMAT))
    }

    /** Ajoute ou REMPLACE une seule station, identifiée par son id serveur — appelé après un
     *  envoi réussi (création comme mise à jour).
     *
     *  Sans cela, le cache n'était alimenté que par la synchro et par le chargement réseau de la
     *  carte : une station que l'on venait de créer n'y figurait pas, et une station modifiée puis
     *  envoyée y restait dans sa version PRÉCÉDENTE — en rouvrant OccHab, la carte réaffichait
     *  l'ancienne géométrie (constat terrain 2026-10-17).
     *
     *  La date de chargement n'est PAS rafraîchie : elle date l'instantané du serveur dans son
     *  ensemble (c'est elle qu'affiche le repli hors-ligne), et une station envoyée ne dit rien
     *  des autres. */
    fun upsert(station: OccHabStation): Unit = synchronized(verrou) {
        val id = station.idStationServeur ?: return
        val courant = charger()
        val autres = courant.liste.filter { it.idStationServeur != id }
        val date = courant.dateChargement.takeIf { it > 0L } ?: maintenantMillis()
        ecrire(Contenu(date, autres + station, VERSION_FORMAT))
    }

    /** TOUTES les stations en cache, tous jeux de données confondus — vue d'entrée dans OccHab,
     *  où aucun JDD n'est encore choisi (il l'est à la création d'une station). */
    val tout: List<OccHabStation> get() = charger().liste

    /** Stations en cache pour ce jeu de données (repli hors-ligne de la carte). */
    fun lire(idJdd: Int): List<OccHabStation> =
        charger().liste.filter { it.idDataset == idJdd }

    /** Date (epoch millis) du dernier remplacement, null si cache jamais écrit. */
    val dateChargement: Long?
        get() = charger().dateChargement.takeIf { it > 0L }

    /** Nombre TOTAL de stations en cache, tous JDD confondus (compteur de Paramètres). */
    val count: Int get() = charger().liste.size

    fun vider(): Unit = synchronized(verrou) {
        runCatching { systeme.delete(fichier()) }
        mem = null
    }

    /** Réinitialise l'état mémoire (tests : chaque test repart du disque). */
    fun resetPourTests() { mem = null }

    internal companion object {
        const val NOM_FICHIER = "stations_serveur_occhab_v1.json"

        /** Version du FORMAT des stations écrites dans ce fichier — à incrémenter dès qu'un champ
         *  de géométrie apparaît ou change de sens dans [OccHabStation] (2 : anneaux intérieurs et
         *  MultiPolygon, comme GeoMys-Android 1.4.0). Un fichier d'une autre version est ignoré. */
        const val VERSION_FORMAT = 2
    }
}
