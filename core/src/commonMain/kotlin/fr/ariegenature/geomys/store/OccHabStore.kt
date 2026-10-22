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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/OccHabStore.kt) :
// backend fichier au lieu des SharedPreferences (lot 1) ; API suspend ; la normalisation post-Gson
// devient le filtre accepter / preparer, avec la règle des stations de RelectureOccHab. La purge
// unique de l'ancien format (`stations_sauvegardees`) n'a pas d'objet : rien n'est repris d'Android.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.store.io.FichierAtomique
import fr.ariegenature.geomys.store.io.StoreFichierJson
import fr.ariegenature.geomys.store.io.sansNulExplicite
import fr.ariegenature.geomys.store.io.transformerTableau
import kotlinx.serialization.json.JsonObject

/**
 * Stockage local des SAISIES OccHab (« Mes stations »), comme [SortieStore] : cache mémoire,
 * quarantaine, écriture durable, normalisation à la relecture, lire-modifier-écrire atomique. Une
 * saisie ([OccHabSaisie]) regroupe les stations saisies dans une même session.
 *
 * Ne contient QUE des saisies créées localement. Les stations lues depuis le serveur (consultation)
 * ne sont pas persistées ici.
 */
class OccHabStore(fichier: FichierAtomique) :
    StoreFichierJson<OccHabSaisie>(OccHabSaisie.serializer(), fichier, "OccHabStore") {

    /** Une saisie sans id ou sans liste de stations est écartée ; ses stations sans id ou sans
     *  liste d'habitats aussi, et les habitats sans id. */
    override fun accepter(brut: JsonObject): Boolean = brut.sansNulExplicite("id", "stations")

    override fun preparer(brut: JsonObject): JsonObject = brut.transformerTableau("stations", ::stationNettoyee)

    /** Supprime toute la saisie [id] (cascade sur ses stations). */
    suspend fun supprimer(id: String) {
        muter { liste -> liste.removeAll { it.id == id } }
    }

    /** Sauvegarde AU FIL DE L'EAU d'une station dans la saisie [saisieId] : insère ou remplace la
     *  station (par son id), crée la saisie si elle n'existe pas encore (1ʳᵉ station de la session).
     *
     *  Station importée du serveur ou remise en édition dont le contenu est IDENTIQUE à son origine
     *  ([OccHabStation.empreinteOrigine]) : elle n'entre dans « Mes stations » qu'à la première
     *  modification réelle (demande terrain 2026-08-27) — l'appel est ignoré (succès). Et si, une
     *  fois modifiée, elle REVIENT à l'identique (Annuler de la carte, valeur rétablie…), il n'y a
     *  plus rien à envoyer (demande terrain 2026-08-30) : une copie d'IMPORT est retirée de « Mes
     *  stations », une copie ENVOYÉE remise en édition ([OccHabStation.origineEnvoyee]) redevient
     *  « envoyée ». Seule exception : un envoi a déjà été TENTÉ avec le contenu modifié → la copie
     *  reste « à envoyer » (elle renverra le contenu d'origine en mise à jour — jamais de
     *  disparition d'une station peut-être modifiée côté serveur). */
    suspend fun upsertStation(saisieId: String, station: OccHabStation): Boolean {
        // Une station appartient à UNE SEULE saisie, celle où elle est née. Si on la réenregistre
        // depuis une autre, on met à jour la sienne — sans quoi « Mes stations » gagnerait un
        // relevé de plus à chaque modification. Le cas vient de la carte, qui affiche les stations
        // de toutes les saisies (constat terrain 2026-10-18).
        val saisieDOrigine = charger().firstOrNull { s -> s.stations.any { it.id == station.id } }?.id
        if (saisieDOrigine != null && saisieDOrigine != saisieId) {
            return upsertStation(saisieDOrigine, station)
        }
        if (!estIntacte(saisieId, station)) return upsertStationBrut(saisieId, station)
        val existante = stationsDeSaisie(saisieId).firstOrNull { it.id == station.id }
        return when {
            existante == null || existante.envoyeGeoNature -> true // jamais entrée / copie envoyée intacte
            station.origineEnvoyee -> upsertStationBrut(saisieId, station.copy(envoyeGeoNature = true))
            else -> supprimerStation(saisieId, station.id)
        }
    }

    /** Vrai si [station] (importée / remise en édition) a encore — ou de nouveau — le contenu de
     *  son origine sans envoi tenté depuis : [upsertStation] ne laissera alors AUCUNE copie « à
     *  envoyer » — permet aux écrans d'adapter leurs messages (« aucune modification » plutôt
     *  que « enregistrée »). */
    suspend fun estIntacte(saisieId: String, station: OccHabStation): Boolean {
        val origine = station.empreinteOrigine ?: return false
        if (station.envoyeGeoNature || station.empreinteContenu() != origine) return false
        val existante = stationsDeSaisie(saisieId).firstOrNull { it.id == station.id } ?: return true
        return existante.envoyeGeoNature || !(existante.envoiIncertain || existante.derniereErreurEnvoi != null)
    }

    private suspend fun upsertStationBrut(saisieId: String, station: OccHabStation): Boolean = muter { liste ->
        val idx = liste.indexOfFirst { it.id == saisieId }
        if (idx < 0) {
            liste.add(0, OccHabSaisie(id = saisieId, stations = listOf(station)))
        } else {
            val stations = liste[idx].stations.toMutableList()
            val j = stations.indexOfFirst { it.id == station.id }
            if (j >= 0) stations[j] = station else stations.add(station)
            liste[idx] = recalc(liste[idx].copy(stations = stations))
        }
    }

    /** Retire une station de la saisie ; supprime la saisie si elle devient vide. */
    suspend fun supprimerStation(saisieId: String, stationId: String): Boolean = muter { liste ->
        val idx = liste.indexOfFirst { it.id == saisieId }
        if (idx >= 0) {
            val stations = liste[idx].stations.filterNot { it.id == stationId }
            if (stations.isEmpty()) liste.removeAt(idx)
            else liste[idx] = recalc(liste[idx].copy(stations = stations))
        }
    }

    /** Stations d'une saisie (pour la carte de réédition). Vide si la saisie est inconnue. */
    suspend fun stationsDeSaisie(saisieId: String): List<OccHabStation> =
        charger().firstOrNull { it.id == saisieId }?.stations ?: emptyList()

    /**
     * INVARIANT (2026-08-26) : une station du serveur n'a jamais plus d'UNE copie locale non
     * envoyée. Sans cela, deux imports de la même station donnaient deux copies « à envoyer »,
     * chacune renvoyée en MISE À JOUR sur le même `id_station` — la dernière envoyée écrasant
     * silencieusement les modifications de l'autre.
     *
     * Renvoie la copie locale NON ENVOYÉE de la station serveur [idStationServeur] (avec sa
     * saisie), toutes saisies confondues, ou null si aucune n'est en attente. Une copie en envoi
     * INCERTAIN compte comme en attente.
     */
    suspend fun copieLocaleNonEnvoyee(idStationServeur: Int): Pair<OccHabSaisie, OccHabStation>? =
        copiesLocalesNonEnvoyees()[idStationServeur]

    /** Toutes les copies locales non envoyées de stations serveur, indexées par
     *  `idStationServeur` (cf. [copieLocaleNonEnvoyee]). En cas de doublon hérité, la 1ʳᵉ
     *  rencontrée (saisie la plus récente) fait foi. */
    suspend fun copiesLocalesNonEnvoyees(): Map<Int, Pair<OccHabSaisie, OccHabStation>> {
        val index = LinkedHashMap<Int, Pair<OccHabSaisie, OccHabStation>>()
        charger().forEach { saisie ->
            saisie.stations.forEach { st ->
                val id = st.idStationServeur ?: return@forEach
                if (id > 0 && !st.envoyeGeoNature && !index.containsKey(id)) index[id] = saisie to st
            }
        }
        return index
    }

    /** Marque une station envoyée et recalcule l'état de la saisie (envoyée = toutes envoyées).
     *  [contenu] : la station telle qu'elle est partie (fusionnée avec le serveur, habitats munis de
     *  leurs ids), qui remplace alors la copie. */
    suspend fun marquerStationEnvoyee(saisieId: String, stationId: String, idStationServeur: Int?, contenu: OccHabStation? = null): Boolean =
        majStation(saisieId, stationId) {
            (contenu?.copy(id = it.id) ?: it).copy(
                envoyeGeoNature = true,
                idStationServeur = idStationServeur ?: it.idStationServeur,
                derniereErreurEnvoi = null,
                envoiIncertain = false, // envoi confirmé.
                empreinteOrigine = null, // une remise en édition reprendra une empreinte fraîche.
                origineEnvoyee = false,
                origineJson = null,
            )
        }

    /** Échec NET d'une station (rejet serveur / non émise) : la station n'a PAS été créée. */
    suspend fun marquerStationErreur(saisieId: String, stationId: String, message: String): Boolean =
        majStation(saisieId, stationId) {
            it.copy(derniereErreurEnvoi = message.take(200), envoiIncertain = false)
        }

    /** Échec qui ne dit RIEN de ce que le serveur a fait : erreur AVANT émission (login impossible
     *  hors ligne, connexion refusée) ou exception non typée. On enregistre le message SANS toucher
     *  à [OccHabStation.envoiIncertain] : une incertitude posée par un envoi PRÉCÉDENT — POST émis,
     *  réponse jamais revenue — doit survivre, sinon l'essai suivant re-POSTe sans vérifier par
     *  uuid et crée une seconde station (le module OccHab n'a aucune contrainte d'unicité).
     *  Constat du portage iOS (A12), 2026-10-17. */
    suspend fun marquerStationErreurSansConclure(saisieId: String, stationId: String, message: String): Boolean =
        majStation(saisieId, stationId) {
            it.copy(derniereErreurEnvoi = message.take(200))
        }

    /** Envoi INCERTAIN d'une station (réseau coupé après émission — peut-être créée). */
    suspend fun marquerStationIncertain(saisieId: String, stationId: String, message: String): Boolean =
        majStation(saisieId, stationId) {
            it.copy(derniereErreurEnvoi = message.take(200), envoiIncertain = true)
        }

    /** Message d'erreur AU NIVEAU SAISIE (résumé d'un envoi partiel). N'affecte pas l'envoi station
     *  par station (déjà persisté). */
    suspend fun marquerErreurSaisie(saisieId: String, message: String) {
        muter { liste ->
            val idx = liste.indexOfFirst { it.id == saisieId }
            if (idx >= 0) liste[idx] = liste[idx].copy(
                derniereErreurEnvoi = message.take(200),
                envoyeGeoNature = false,
            )
        }
    }

    /** Applique [transform] à une station et recalcule l'état de la saisie. Renvoie le succès de la
     *  PERSISTANCE (false = écriture échouée → l'appelant peut requalifier en incertain). */
    private suspend fun majStation(saisieId: String, stationId: String, transform: (OccHabStation) -> OccHabStation): Boolean =
        muter { liste ->
            val idx = liste.indexOfFirst { it.id == saisieId }
            if (idx >= 0) {
                val stations = liste[idx].stations.toMutableList()
                val j = stations.indexOfFirst { it.id == stationId }
                if (j >= 0) {
                    stations[j] = transform(stations[j])
                    liste[idx] = recalc(liste[idx].copy(stations = stations))
                }
            }
        }

    /** Recalcule l'état DÉRIVÉ d'une saisie : envoyée ssi toutes ses stations le sont ; l'erreur
     *  saisie reflète la 1ʳᵉ station restant à envoyer en erreur (effacée si tout est parti). */
    private fun recalc(saisie: OccHabSaisie): OccHabSaisie {
        val toutesEnvoyees = saisie.stations.isNotEmpty() && saisie.stations.all { it.envoyeGeoNature }
        val erreur = if (toutesEnvoyees) null else saisie.stations
            .firstOrNull { !it.envoyeGeoNature && it.derniereErreurEnvoi != null }?.derniereErreurEnvoi
        return saisie.copy(envoyeGeoNature = toutesEnvoyees, derniereErreurEnvoi = erreur)
    }
}
