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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/EnvoiSortie.kt) :
// la fonction devient une classe sur l'envoi et le store, uniques dans ServicesGeoNature ; les objets
// EnvoisNonPersistes et VerrousEnvoiSortie deviennent ses membres (même portée : le processus) ;
// ConcurrentHashMap et synchronizedSet cèdent la place à un SynchronizedObject.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.SortieStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** Résultat d'un envoi de sortie OCCTAX : [succes] + [message] humanisé prêt à afficher. */
data class ResultatEnvoiSortie(val succes: Boolean, val message: String)

/**
 * Occurrences TRANSMISES à GeoNature dont le marquage local a ÉCHOUÉ (écriture disque refusée :
 * espace plein) — filet mémoire jusqu'à la mort du processus : un ré-envoi de la même saisie dans
 * cette session ne les re-poste pas (audit 2026-08-27). Après redémarrage, seul l'avertissement
 * affiché à l'utilisateur protège (d'où son insistance).
 */
internal class EnvoisNonPersistes {
    private val verrou = SynchronizedObject()
    private val obs = mutableSetOf<String>()
    fun ajouter(ids: Collection<String>) {
        synchronized(verrou) { obs.addAll(ids) }
    }
    fun contient(id: String): Boolean = synchronized(verrou) { id in obs }
    fun vider() {
        synchronized(verrou) { obs.clear() }
    }
}

/**
 * Un verrou d'envoi PAR SAISIE, pour tout le processus — le pendant Occtax de `OutboxEnvoi.mutexEnvoi`
 * (Suivis).
 *
 * Les cinq points d'appel d'un envoi Occtax (« Tout envoyer » et flèche de ligne dans Mes saisies,
 * écran de détail, fin de trace multi-taxons, saisie rapide) n'avaient pour toute garde que des
 * drapeaux `envoiEnCours` d'ÉCRAN : aveugles d'un écran à l'autre. Cas réel : l'envoi est lancé
 * depuis l'écran de détail, l'utilisateur revient à la liste — qui n'a jamais posé son drapeau —, la
 * saisie y est toujours « À envoyer » avec sa flèche, et un second envoi part sans même un toast.
 * Chacun crée son propre relevé : doublons en base régionale puis en synthèse nationale, qu'aucune
 * contrainte serveur n'arrête (`unique_id_occurence_occtax` n'est pas unique côté GeoNature, et
 * `unique_id_sinp_occtax`, qui l'est, n'est jamais envoyé).
 *
 * `tryLock` et non `lock` : un second envoi est REFUSÉ et le dit, plutôt que d'attendre en silence
 * derrière un premier qui peut durer des minutes (photos, réseau de terrain).
 */
private class VerrousEnvoiSortie {
    private val acces = SynchronizedObject()
    private val verrous = mutableMapOf<String, Mutex>()
    fun pour(sortieId: String): Mutex = synchronized(acces) { verrous.getOrPut(sortieId) { Mutex() } }
}

private const val AVERT_PERSISTANCE =
    "\n\n⚠ Transmis à GeoNature mais l'enregistrement local a ÉCHOUÉ (espace disque ?) : libérez " +
        "de l'espace AVANT tout nouvel envoi de cette saisie — un ré-envoi après redémarrage " +
        "créerait des doublons."

/** L'envoi d'une saisie OCCTAX et la tenue du store, partagés par tous les écrans d'envoi. */
class EnvoiSortie(private val upload: GeoNatureUpload, private val sortieStore: SortieStore) {

    internal val envoisNonPersistes = EnvoisNonPersistes()
    private val verrous = VerrousEnvoiSortie()

    /** Envoie [sortie] vers GeoNature et tient le store à jour : marquée envoyée (erreur précédente
     *  effacée) en cas de succès ; erreur HUMANISÉE persistée sinon (→ cadre rouge dans « Mes
     *  saisies »). Factorise le flux qui était copié dans 4 écrans (liste des saisies, détail, fin de
     *  trace multi-taxons, saisie rapide). L'affichage (dialogue, voile, navigation) reste à
     *  l'appelant.
     *
     *  Robustesse (audit 2026-08-27) : le bloc POST + marquages tourne en [NonCancellable] — une
     *  annulation de coroutine (vue détruite pendant l'envoi : retour, navigation) ne peut plus
     *  jeter le résultat d'un POST abouti (occurrences créées côté serveur non marquées → doublons
     *  au ré-envoi). Le marquage AU FIL DE L'EAU ([MarqueurEnvoiOcctax]) couvre en plus la mort du
     *  processus en plein lot, et un échec de persistance est SIGNALÉ au lieu d'être ignoré. */
    suspend fun envoyerSortieVersGeoNature(
        sortie: Sortie,
        config: GeoNatureConfig,
    ): ResultatEnvoiSortie = withContext(NonCancellable) {
        val verrou = verrous.pour(sortie.id)
        if (!verrou.tryLock()) {
            return@withContext ResultatEnvoiSortie(
                false,
                "Un envoi de cette saisie est déjà en cours — attendez qu'il se termine avant d'en " +
                    "relancer un (sinon les observations partiraient en double sur GeoNature).",
            )
        }
        try {
            envoyerSousVerrou(sortie, config)
        } finally {
            verrou.unlock()
        }
    }

    private suspend fun envoyerSousVerrou(sortie: Sortie, config: GeoNatureConfig): ResultatEnvoiSortie {
        // RELECTURE SOUS VERROU. Le verrou seul ne corrigerait rien : il sérialiserait les deux
        // envois, après quoi le second repartirait de l'instantané que son écran a chargé à
        // l'ouverture — donc reposterait tout ce que le premier vient de créer. Les cinq chemins
        // d'appel persistent la sortie dans le store sous son id AVANT d'envoyer, la relecture est
        // donc sûre ; on se replie sur l'objet reçu si l'id est absent (cas dégradé : écriture disque
        // refusée), pour ne jamais transformer une saisie en envoi vide.
        // UNION des acquis, et pas simple préférence au disque : `envoyeeServeur` n'étant posé
        // qu'après un 2xx, l'union ne peut pas fabriquer de faux positif, et elle rattrape les
        // marquages qu'une réécriture concurrente aurait effacés.
        val passees = sortie.observations.associateBy { it.id }
        val sortieRelue = sortieStore.charger().find { it.id == sortie.id }?.let { s ->
            s.copy(observations = s.observations.map { o ->
                val p = passees[o.id] ?: return@map o
                o.copy(
                    envoyeeServeur = o.envoyeeServeur || p.envoyeeServeur,
                    idReleveIncertain = o.idReleveIncertain ?: p.idReleveIncertain,
                )
            })
        } ?: sortie
        // Filet mémoire : occurrences transmises lors d'un envoi précédent de CE processus dont le
        // marquage disque avait échoué → exclues comme si elles étaient marquées. Il couvre ce
        // qu'aucune relecture ne verra jamais, puisque justement rien n'a pu être écrit.
        val sortieEff = if (sortieRelue.observations.any { envoisNonPersistes.contient(it.id) }) {
            sortieRelue.copy(observations = sortieRelue.observations.map {
                if (envoisNonPersistes.contient(it.id)) it.copy(envoyeeServeur = true) else it
            })
        } else {
            sortieRelue
        }
        var marquageEchoue = false
        val marqueur = object : MarqueurEnvoiOcctax {
            override suspend fun occurrenceCreee(obsId: String) {
                if (!sortieStore.marquerObservationsEnvoyees(sortie.id, listOf(obsId))) {
                    marquageEchoue = true
                    envoisNonPersistes.ajouter(listOf(obsId))
                }
            }
            override suspend fun occurrenceIncertaine(obsId: String, idReleve: Int) {
                sortieStore.marquerObservationIncertaine(sortie.id, obsId, idReleve)
            }
            override suspend fun occurrencesATenter(obsIds: List<String>, idReleve: Int) {
                // Échec d'écriture NON bloquant, contrairement aux Suivis qui refusent de poster :
                // ici le POST part quand même, et c'est l'avertissement de persistance déjà en place
                // (AVERT_PERSISTANCE, via marquageEchoue) qui prévient l'utilisateur. Refuser l'envoi
                // sur un disque plein empêcherait de transmettre une journée de terrain — le remède
                // serait pire que le mal.
                if (!sortieStore.marquerObservationsIncertaines(sortie.id, obsIds, idReleve)) {
                    marquageEchoue = true
                }
            }
            override suspend fun occurrenceEchecNet(obsId: String) {
                sortieStore.effacerIncertitudeObservation(sortie.id, obsId)
            }
            override suspend fun releveATenter(obsIds: List<String>) {
                if (!sortieStore.marquerReleveTente(sortie.id, obsIds, true)) marquageEchoue = true
            }
            override suspend fun releveEchecNet(obsIds: List<String>) {
                sortieStore.marquerReleveTente(sortie.id, obsIds, false)
            }
        }
        return try {
            val res = upload.envoyer(sortieEff, config, marqueur)
            // L'ACQUIS d'abord (redondant avec le marqueur, idempotent) : les occurrences créées
            // pendant cet envoi sont marquées AVANT tout autre traitement.
            if (res.obsCreesIds.isNotEmpty() &&
                !sortieStore.marquerObservationsEnvoyees(sortie.id, res.obsCreesIds)
            ) {
                marquageEchoue = true
                envoisNonPersistes.ajouter(res.obsCreesIds)
            }
            if (res.nbCrees == res.nbTotal) {
                // Envoi COMPLET (nbTotal == 0 : tout avait déjà été transmis lors d'un envoi
                // partiel précédent — on clôture simplement).
                //
                // SAUF si des observations ont été AJOUTÉES pendant l'envoi : le verrou interdit deux
                // envois simultanés, pas une édition concurrente, et un envoi avec photos dure assez
                // longtemps pour qu'on ajoute une espèce entre-temps. Ces observations n'entrent dans
                // aucun total de cet envoi (ils viennent de l'instantané relu au départ) ; marquer la
                // saisie envoyée les enterrerait — elle sortirait de « Mes saisies » sans qu'elles
                // soient jamais parties. On laisse donc la saisie à envoyer : les occurrences déjà
                // transmises portent leur marquage individuel, seules les nouvelles repartiront.
                val idsDeCetEnvoi = sortieEff.observations.map { it.id }.toSet()
                val ajouteesPendantEnvoi = sortieStore.charger().find { it.id == sortie.id }
                    ?.observations.orEmpty()
                    .filter { it.id !in idsDeCetEnvoi && !it.envoyeeServeur }
                if (ajouteesPendantEnvoi.isEmpty()) {
                    if (!sortieStore.marquerEnvoyee(sortie.id)) marquageEchoue = true
                }
                val msg = buildString {
                    if (res.nbTotal == 0) {
                        append("Toutes les observations avaient déjà été transmises — saisie marquée envoyée.")
                    } else {
                        append("${res.nbCrees}/${res.nbTotal} relevé")
                        if (res.nbTotal > 1) append("s")
                        append(" créé")
                        if (res.nbCrees > 1) append("s")
                        append(" sur GeoNature")
                        if (res.nbDejaEnvoyees > 0) {
                            append(" (+ ${res.nbDejaEnvoyees} déjà transmis précédemment)")
                        }
                    }
                    if (ajouteesPendantEnvoi.isNotEmpty()) {
                        append("\n⚠ ${ajouteesPendantEnvoi.size} observation(s) ajoutée(s) pendant l'envoi : ")
                        append("la saisie reste à envoyer, relancez l'envoi pour les transmettre.")
                    }
                    res.premierIdReleve?.let { append("\nPremier id_releve_occtax : $it") }
                    if (res.mediasOK > 0) append("\n${res.mediasOK} média(s) uploadé(s)")
                    if (res.mediasKO > 0) {
                        // Ici, seuls des échecs média LOCAUX (fichier disparu) : un échec réseau
                        // aurait retenu l'observation entière (envoi partiel, branche ci-dessous).
                        append("\n⚠ ${res.mediasKO} média(s) introuvable(s) sur l'appareil — envoyé(s) sans photo")
                        res.mediaErreurMsg?.let { append(" : $it") }
                    }
                    if (res.relevesOrphelins.isNotEmpty()) {
                        append("\n⚠ ${res.relevesOrphelins.size} relevé(s) vide(s) côté GeoNature ")
                        append("(id : ${res.relevesOrphelins.joinToString(", ")}) — ")
                        append("suppression retentée automatiquement au prochain envoi.")
                    }
                    if (marquageEchoue) append(AVERT_PERSISTANCE)
                }
                // Marquage échoué = l'appareil croit encore la saisie « à envoyer » : on le rend
                // VISIBLE (échec) plutôt que de laisser un succès trompeur.
                ResultatEnvoiSortie(!marquageEchoue, msg)
            } else {
                // Envoi PARTIEL (réseau tombé entre deux groupes, photo non transmise…) : la sortie
                // N'EST PAS marquée envoyée — elle reste visible et ré-envoyable dans « Mes
                // saisies », et seules les obs restantes partiront au prochain essai (les créées
                // sont marquées ci-dessus). Avant ce garde, UNE seule occurrence créée suffisait à
                // verrouiller toute la sortie : les observations restantes étaient perdues.
                val total = res.nbTotal + res.nbDejaEnvoyees
                val transmises = res.nbCrees + res.nbDejaEnvoyees
                val msg = buildString {
                    append("Envoi partiel : $transmises/$total observation(s) transmise(s)")
                    res.messageDerniereErreur?.let { append("\nDernière erreur : $it") }
                    if (res.obsIncertaines.isNotEmpty()) {
                        append("\n${res.obsIncertaines.size} observation(s) au statut incertain (réponse perdue) : " +
                            "vérifiée(s) automatiquement au prochain envoi, pas de doublon.")
                    }
                    append("\n→ Ré-envoyez la saisie : seules les observations restantes partiront.")
                    if (marquageEchoue) append(AVERT_PERSISTANCE)
                }
                sortieStore.marquerErreurEnvoi(sortie.id, msg)
                ResultatEnvoiSortie(false, msg)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = humaniserErreurReseau(e)
            sortieStore.marquerErreurEnvoi(sortie.id, msg)
            ResultatEnvoiSortie(false, msg)
        }
    }
}
