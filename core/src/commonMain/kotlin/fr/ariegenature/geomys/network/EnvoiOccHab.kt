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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/EnvoiOccHab.kt) :
// la fonction devient une classe sur l'envoi, le store et le cache des stations serveur, uniques dans
// ServicesGeoNature ; l'objet VerrousEnvoiOccHab devient son membre ; ConcurrentHashMap cède la place
// à un SynchronizedObject.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.OccHabStore
import fr.ariegenature.geomys.store.StationsServeurCache
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** Un verrou d'envoi PAR SAISIE, pour tout le processus — pendant OccHab de `VerrousEnvoiSortie`
 *  (EnvoiSortie.kt) et de `OutboxEnvoi.mutexEnvoi` (Suivis). `tryLock` : un second envoi est REFUSÉ
 *  et le dit, plutôt que d'attendre en silence derrière un premier qui peut durer des minutes.
 *  Audit 2026-09-14, instruction du 2026-09-16. */
private class VerrousEnvoiOccHab {
    private val acces = SynchronizedObject()
    private val verrous = mutableMapOf<String, Mutex>()
    fun pour(saisieId: String): Mutex = synchronized(acces) { verrous.getOrPut(saisieId) { Mutex() } }
}

/** Résultat d'un envoi de saisie OccHab : [succes] + [message] humanisé prêt à afficher. */
data class ResultatEnvoiOccHab(val succes: Boolean, val message: String)

/** L'envoi d'une saisie OccHab et la tenue du store, partagés par les écrans d'envoi.
 *  [altitudesMnt] : les altitudes min/max du MNT du serveur pour une station, null hors ligne ou
 *  en erreur. */
class EnvoiOccHab(
    private val occHabUpload: OccHabUpload,
    private val store: OccHabStore,
    private val stationsServeur: StationsServeurCache,
    private val altitudesMnt: suspend (OccHabStation) -> Pair<Int, Int>?,
) {

    private val verrous = VerrousEnvoiOccHab()

    /**
     * Envoie une SAISIE OccHab (ses stations) vers GeoNature et tient le store à jour, sur le modèle
     * de [EnvoiSortie.envoyerSortieVersGeoNature] pour Occtax. Chaque station est postée par
     * [OccHabUpload.envoyer] (anti-doublon par UUID et statut incertain déjà en place) et son
     * résultat est persisté AU FUR ET À MESURE : l'acquis d'une station survit même si une
     * suivante échoue.
     *
     * - Toutes les stations parties → la saisie est marquée envoyée (par recalcul au store).
     * - Échec PARTIEL (réseau coupé en cours) → la saisie N'EST PAS marquée envoyée : elle reste
     *   visible et ré-envoyable, et seules les stations restantes repartiront au prochain essai
     *   (les déjà créées ne sont pas re-postées).
     * L'affichage (dialogue, progression) reste à l'appelant.
     */
    suspend fun envoyerSaisieOccHabVersGeoNature(
        saisie: OccHabSaisie,
        config: GeoNatureConfig,
        /** Envoi d'UNE station — injectable pour les tests (défaut : le POST réseau réel). */
        envoyer: suspend (OccHabStation, GeoNatureConfig) -> OccHabEnvoiResult = occHabUpload::envoyer,
    ): ResultatEnvoiOccHab = withContext(Dispatchers.IO) {
        // Même verrou que côté Occtax (cf. EnvoiSortie) : les écrans OccHab n'avaient eux aussi que
        // des drapeaux `envoiEnCours` d'instance, aveugles d'un écran à l'autre. « Tentée » est
        // certes persisté avant tout POST de création, mais un second envoi concurrent lit SON
        // instantané, où l'incertitude n'est pas encore posée : il POSTerait sans passer par la
        // vérification anti-doublon.
        val verrou = verrous.pour(saisie.id)
        if (!verrou.tryLock()) {
            return@withContext ResultatEnvoiOccHab(
                false,
                "Un envoi de cette saisie est déjà en cours — attendez qu'il se termine avant d'en " +
                    "relancer un (sinon les stations partiraient en double sur GeoNature).",
            )
        }
        try {
            envoyerSousVerrou(saisie, config, envoyer)
        } finally {
            verrou.unlock()
        }
    }

    /** [station] avec les altitudes du MNT du serveur quand elle n'en a aucune : demandées à l'envoi,
     *  qui est en ligne, et non plus au dessin — les écrans ne demandent rien au serveur (décision de
     *  l'utilisateur du 20 octobre ; un changement de géométrie efface les anciennes). Reportées dans
     *  la saisie. Best-effort : sans elles, la station part quand même. */
    private suspend fun avecAltitudes(saisieId: String, station: OccHabStation): OccHabStation {
        if (station.altitudeMin != null || station.altitudeMax != null) return station
        val (min, max) = altitudesMnt(station) ?: return station
        val completee = station.copy(altitudeMin = min, altitudeMax = max)
        store.upsertStation(saisieId, completee)
        return completee
    }

    private suspend fun envoyerSousVerrou(
        saisie: OccHabSaisie,
        config: GeoNatureConfig,
        envoyer: suspend (OccHabStation, GeoNatureConfig) -> OccHabEnvoiResult,
    ): ResultatEnvoiOccHab {
        // RELECTURE SOUS VERROU : on repart de l'état DISQUE et non de l'instantané de l'écran, sinon
        // le second envoi sérialisé reposterait les stations que le premier vient de marquer. Repli
        // sur l'objet reçu si la saisie est absente du store (cas dégradé : écriture refusée).
        val saisieRelue = store.charger().find { it.id == saisie.id } ?: saisie
        val aEnvoyer = saisieRelue.stations.filter { !it.envoyeGeoNature }
        val dejaEnvoyees = saisieRelue.stations.size - aEnvoyer.size
        val total = saisieRelue.stations.size

        if (aEnvoyer.isEmpty()) {
            // Tout avait déjà été transmis (la saisie est déjà marquée envoyée au store).
            return ResultatEnvoiOccHab(true, "Toutes les stations avaient déjà été transmises.")
        }

        var nbCrees = 0
        var derniereErreur: String? = null
        for (station in aEnvoyer.map { avecAltitudes(saisie.id, it) }) {
            // CRÉATION (pas d'id serveur) : « tentée » (statut INCERTAIN) persisté AVANT le POST — si
            // le processus meurt ou si la coroutine est annulée pendant l'appel, le prochain envoi
            // vérifiera l'existence par UUID au lieu de re-POSTer (audit 2026-08-27, modèle
            // OutboxEnvoi.dejaTentee). Une MISE À JOUR (/stations/<id>/) est idempotente : inutile.
            // Impossible d'écrire cet état (espace disque ?) → on n'envoie PAS (un re-POST aveugle
            // après plantage dupliquerait).
            val creation = (station.idStationServeur ?: 0) <= 0
            if (creation && !store.marquerStationIncertain(saisie.id, station.id,
                    "Envoi interrompu — vérification anti-doublon au prochain envoi")
            ) {
                derniereErreur = "Impossible d'enregistrer l'état d'envoi (espace disque ?) — station non envoyée"
                continue
            }
            try {
                // NonCancellable : POST + marquage d'un bloc — une annulation (vue détruite pendant
                // l'envoi) ne peut plus jeter le résultat d'un POST abouti.
                withContext(NonCancellable) {
                    val res = envoyer(station, config)
                    // FILET : création acceptée mais id serveur toujours inconnu (la récupération par
                    // uuid d'OccHabUpload a elle-même échoué). Marquer « envoyée » ici poserait
                    // envoiIncertain = false avec idStationServeur = null : la station serait
                    // définitivement sans identité serveur, et sa réédition — qui repasse par une
                    // CRÉATION faute d'id — en produirait une seconde sur GeoNature, sans que rien ne
                    // déclenche la vérification anti-doublon (audit 2026-09-14, R1-M2). On la laisse
                    // donc INCERTAINE : le prochain envoi la retrouvera par son uuid.
                    if (creation && res.idStationServeur == null) {
                        store.marquerStationIncertain(saisie.id, station.id,
                            "Station transmise mais le serveur n'a pas renvoyé son identifiant — " +
                                "vérification au prochain envoi (aucun doublon ne sera créé).")
                        derniereErreur = "Identifiant de station non renvoyé par le serveur"
                        return@withContext
                    }
                    // L'ACQUIS d'abord : la station créée est marquée AVANT tout le reste — un
                    // ré-envoi ne la re-postera pas (recalcule aussi l'état de la saisie).
                    val persiste = store.marquerStationEnvoyee(saisie.id, station.id, res.idStationServeur)
                    if (persiste) {
                        nbCrees++
                        // La version qui vient de partir devient l'état de référence du serveur : on
                        // la met dans le cache des stations serveur, à la place de l'ancienne s'il y
                        // en avait une. Sans ça, rouvrir OccHab réaffichait la version PRÉCÉDENTE (le
                        // cache n'était écrit qu'à la synchro et au chargement réseau de la carte),
                        // et une station tout juste créée n'apparaissait pas du tout. Mise en forme
                        // « station serveur », comme le lecteur du chargement : l'empreinte d'origine
                        // est recalculée au prochain import pour édition.
                        stationsServeur.upsert(
                            station.copy(
                                idStationServeur = res.idStationServeur ?: station.idStationServeur,
                                envoyeGeoNature = true,
                                origineServeur = true,
                                envoiIncertain = false,
                                derniereErreurEnvoi = null,
                                empreinteOrigine = null,
                            )
                        )
                    } else {
                        // POST réussi mais écriture disque échouée (disque plein…) : la station EST
                        // créée côté serveur mais non marquée localement → on la passe en INCERTAIN
                        // pour que le ré-envoi vérifie l'existence par UUID au lieu de re-POSTer.
                        store.marquerStationIncertain(saisie.id, station.id,
                            "Station transmise mais enregistrement local incomplet — vérification au prochain envoi.")
                        derniereErreur = "Enregistrement local incomplet"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GNErreur.EnvoiIncertain) {
                store.marquerStationIncertain(saisie.id, station.id, e.msg)
                derniereErreur = e.msg
            } catch (e: GNErreur.EnvoiEchoue) {
                // Un 5xx a PU enregistrer la station côté serveur avant l'échec (500 en sérialisant
                // la réponse, 504 après l'INSERT). Le module OccHab n'a AUCUNE contrainte d'unicité →
                // on la traite comme INCERTAINE (le ré-envoi vérifiera par UUID) et non comme un échec
                // net qui inviterait à re-POSTer → anti-doublon.
                val msg = humaniserErreurReseau(e)
                if (e.code >= 500) store.marquerStationIncertain(saisie.id, station.id, msg)
                else store.marquerStationErreur(saisie.id, station.id, msg)
                derniereErreur = msg
            } catch (e: Exception) {
                // Exception NON TYPÉE : elle couvre aussi — et surtout — les échecs AVANT émission
                // (login impossible hors ligne, connexion refusée). Rien n'a alors été envoyé, mais
                // cela ne prouve RIEN sur l'envoi PRÉCÉDENT : effacer l'incertitude posée par un POST
                // resté sans réponse ferait sauter la vérification par uuid au prochain essai, donc
                // une seconde station sur GeoNature. On enregistre l'erreur sans rien conclure.
                val msg = humaniserErreurReseau(e)
                store.marquerStationErreurSansConclure(saisie.id, station.id, msg)
                derniereErreur = msg
            }
        }

        return if (nbCrees == aEnvoyer.size) {
            // Envoi COMPLET (la saisie a été marquée envoyée par le dernier marquerStationEnvoyee).
            val msg = buildString {
                append("$nbCrees station")
                if (nbCrees > 1) append("s")
                append(" envoyée")
                if (nbCrees > 1) append("s")
                append(" sur GeoNature")
                if (dejaEnvoyees > 0) append(" (+ $dejaEnvoyees déjà transmise(s) précédemment)")
                append(".")
            }
            ResultatEnvoiOccHab(true, msg)
        } else {
            // Envoi PARTIEL : la saisie reste à envoyer, seules les restantes repartiront.
            val transmises = nbCrees + dejaEnvoyees
            val msg = buildString {
                append("Envoi partiel : $transmises/$total station(s) transmise(s)")
                derniereErreur?.let { append("\nDernière erreur : $it") }
                append("\n→ Ré-envoyez la saisie : seules les stations restantes partiront.")
            }
            store.marquerErreurSaisie(saisie.id, msg)
            ResultatEnvoiOccHab(false, msg)
        }
    }
}
