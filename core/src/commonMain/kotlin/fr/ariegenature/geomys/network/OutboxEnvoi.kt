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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/OutboxEnvoi.kt) :
// l'objet devient une classe sur la file, l'envoi des Suivis et l'envoi des médias, uniques dans
// ServicesGeoNature ; ConcurrentHashMap cède la place à un SynchronizedObject, java.util.UUID à
// uuidAleatoire, Log à Journal.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.plateforme.ExecutionLongue
import fr.ariegenature.geomys.plateforme.proteger
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.OutboxMonitoring
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Orchestrateur de l'envoi des saisies Suivis en attente.
 *
 *  Stratégie :
 *  1. Détermine l'ordre d'envoi en respectant les dépendances parent → enfant : une obs qui pointe
 *     sur une visite locale ([SaisieEnAttente.parentUuidLocal]) doit être envoyée APRÈS sa visite
 *     parente, sinon on n'a pas d'id serveur à utiliser comme clé étrangère. Tri topologique
 *     simple : on émet d'abord les saisies sans `parentUuidLocal`, puis on les rajoute
 *     itérativement quand leur parent est SENT.
 *  2. Pour chaque saisie PENDING/ERROR : POST par [MonitoringEnvoi.envoyerVisite]. Sur succès →
 *     marque SENT + stocke l'idServeur. Sur échec → marque ERROR + stocke le message.
 *  3. Au fur et à mesure, met à jour les enfants qui pointaient vers le parent local :
 *     `parentIdServeur` reçoit l'id serveur résolu.
 *
 *  Rappel `progression(envoyees, total, dernierMessage)` pour piloter l'affichage de la
 *  progression sur l'écran « Saisies en attente ». */
class OutboxEnvoi(
    private val outbox: OutboxMonitoring,
    private val monitoringEnvoi: MonitoringEnvoi,
    private val upload: GeoNatureUpload,
    private val objets: MonitoringObjets,
    /** Garde l'envoi de la mise en veille (tâche d'arrière-plan sous iOS). */
    private val executionLongue: ExecutionLongue? = null,
) {

    data class Resultat(val succes: Int, val echecs: Int, val messages: List<String>)

    /** Sérialise les envois : sans ça, deux déclenchements concurrents (« Envoyer tout » + « Envoyer
     *  ce groupe », double appui, relance) pourraient traiter la même saisie en parallèle
     *  (transition PENDING→SENDING non atomique) et créer des doublons côté serveur. */
    private val mutexEnvoi = Mutex()

    /** Envoie un sous-ensemble de la file : la saisie [uuidRacine] + tous ses descendants locaux.
     *  Délègue à [envoyerSelection]. Utilisé par le bouton « Envoyer ce groupe » d'une visite locale
     *  (= envoie la visite + ses obs sans toucher au reste de la file). */
    suspend fun envoyerGroupe(
        config: GeoNatureConfig,
        uuidRacine: String,
        progression: (envoyees: Int, total: Int, message: String) -> Unit,
    ): Resultat {
        val groupe = (outbox.descendants(uuidRacine) + uuidRacine).toSet()
        return envoyerSelection(config, groupe, progression)
    }

    suspend fun envoyerTout(
        config: GeoNatureConfig,
        progression: (envoyees: Int, total: Int, message: String) -> Unit,
    ): Resultat = envoyerSelection(
        config = config,
        uuidsACibler = null, // null = toute la file
        progression = progression,
    )

    /** Cœur de l'algorithme : envoie toutes les saisies PENDING dont l'uuid est dans
     *  [uuidsACibler], ou toute la file si null. Ordre parent → enfant par tri topologique
     *  itératif. */
    private suspend fun envoyerSelection(
        config: GeoNatureConfig,
        uuidsACibler: Set<String>?,
        progression: (envoyees: Int, total: Int, message: String) -> Unit,
    ): Resultat = executionLongue.proteger("Envoi des Suivis") { envoyerSelectionSansVeille(config, uuidsACibler, progression) }

    private suspend fun envoyerSelectionSansVeille(
        config: GeoNatureConfig,
        uuidsACibler: Set<String>?,
        progression: (envoyees: Int, total: Int, message: String) -> Unit,
    ): Resultat = withContext(Dispatchers.IO) {
        mutexEnvoi.withLock {
            // Saisies restées SENDING d'un envoi précédent : le verrou garantit qu'aucun autre envoi
            // ne tourne, donc un SENDING ici vient forcément d'un plantage ou d'un arrêt forcé en
            // plein envoi. On les bascule en ERROR : visibles, et requalifiables comme les autres
            // erreurs ci-dessous. Le risque de doublon au nouvel essai est couvert par
            // [SaisieEnAttente.objetCree], persisté dès la confirmation du POST : si l'objet avait
            // été créé avant le plantage, le nouvel essai ne re-POSTe pas (médias seulement).
            outbox.tout()
                .filter { it.etat == SaisieEnAttente.Etat.SENDING }
                .forEach { s ->
                    outbox.mettreAJour(s.uuid) {
                        it.copy(
                            etat = SaisieEnAttente.Etat.ERROR,
                            messageErreur = "Envoi interrompu (application fermée ?)",
                        )
                    }
                }
            // L'action « envoyer » (flèche d'un groupe / envoyer tout) vaut « Réessayer » pour les
            // saisies en erreur ciblées : on les requalifie PENDING pour qu'elles repartent avec le
            // groupe — sinon la flèche sur une visite en échec ne faisait RIEN (seuls les PENDING
            // étaient traités) et il fallait « Réessayer » entrée par entrée, en perdant les obs.
            outbox.tout()
                .filter {
                    it.etat == SaisieEnAttente.Etat.ERROR &&
                        (uuidsACibler == null || it.uuid in uuidsACibler)
                }
                .forEach { s ->
                    outbox.mettreAJour(s.uuid) {
                        it.copy(etat = SaisieEnAttente.Etat.PENDING, messageErreur = null)
                    }
                }
            // Saisies « À COMPLÉTER » (champs obligatoires laissés vides — typiquement les infos de
            // FIN de visite, cf. SaisieEnAttente.champsManquants) : ÉCARTÉES de l'envoi, ici et pour
            // tous les chemins (flèche d'un groupe, « Tout envoyer », relance). Le serveur les
            // rejetterait de toute façon ; on préfère un message explicite. Leurs enfants restent
            // naturellement bloqués : leur parent local ne recevra pas d'id serveur.
            val aCompleter = outbox.enAttente()
                .filter { (uuidsACibler == null || it.uuid in uuidsACibler) && it.aCompleter }
            val messages = mutableListOf<String>()
            aCompleter.forEach { s ->
                messages += "À compléter — ${s.manquants().size} champ" +
                    (if (s.manquants().size > 1) "s" else "") + " obligatoire" +
                    (if (s.manquants().size > 1) "s" else "") + " : ${s.manquants().joinToString(", ")}"
            }
            val uuidsACompleter = aCompleter.map { it.uuid }.toSet()

            val initiales = outbox.enAttente()
                .filter { (uuidsACibler == null || it.uuid in uuidsACibler) && it.uuid !in uuidsACompleter }
            if (initiales.isEmpty()) return@withLock Resultat(0, aCompleter.size, messages)

            val total = initiales.size
            var envoyees = 0
            var succes = 0
            var echecs = 0

            // Boucle : à chaque tour on ne traite que les PENDING (= jamais tentées dans cet envoi).
            // Les ERROR du tour courant restent ERROR — pour les rejouer il faut le « Réessayer »
            // explicite à l'écran. On itère tant qu'on a réussi à envoyer au moins une saisie au tour
            // précédent : ça débloque les enfants qui attendaient leur parent local. Quand un tour
            // n'envoie rien de nouveau → fini.
            var nouvelleVagueSucces = true
            while (nouvelleVagueSucces) {
                nouvelleVagueSucces = false
                // ⚠ Filtrer AUSSI par [uuidsACibler] ici : sinon « Envoyer ce groupe » envoyait tous
                // les PENDING de la file au lieu du seul sous-arbre ciblé (le filtre n'était appliqué
                // qu'au calcul de `total`, pas à la boucle d'envoi).
                // ⚠ Même piège pour les saisies « à compléter » : les écarter du seul calcul de
                // `total` ne suffit pas, c'est CETTE liste qui pilote les envois.
                val restantes = outbox.tout()
                    .filter {
                        it.etat == SaisieEnAttente.Etat.PENDING &&
                            (uuidsACibler == null || it.uuid in uuidsACibler) &&
                            !it.aCompleter
                    }
                if (restantes.isEmpty()) break

                for (saisie in restantes) {
                    // Parent local pas encore envoyé → on saute, il sera repris quand le parent
                    // passera en SENT au tour suivant.
                    if (saisie.parentUuidLocal != null) {
                        val parent = outbox.tout().firstOrNull { it.uuid == saisie.parentUuidLocal }
                        // NB : parentUuidLocal est CONSERVÉ après résolution de la clé étrangère —
                        // c'est lui qui groupe visuellement les obs sous leur visite dans « Mes
                        // visites » et qui empêche de retirer le parent SENT tant qu'il reste des
                        // obs à envoyer.
                        when {
                            // Clé déjà résolue (envoi précédent) : l'enfant est envoyable tel quel.
                            (saisie.parentIdServeur ?: 0) > 0 -> Unit
                            // Parent CRÉÉ côté serveur (SENT, ou ERROR « médias seulement ») : sa clé
                            // est connue → l'enfant est envoyable même si les photos du parent ont
                            // échoué (elles se rejouent indépendamment par « Réessayer »).
                            parent != null && parent.objetCree && (parent.idServeur ?: 0) > 0 -> {
                                outbox.mettreAJour(saisie.uuid) {
                                    it.copy(parentIdServeur = parent.idServeur)
                                }
                            }
                            // Parent introuvable ou définitivement en échec (non créé) : l'enfant ne
                            // sera jamais envoyable dans cet envoi (on ne rejoue pas les ERROR). On le
                            // bascule en ERROR au lieu de le laisser PENDING indéfiniment (cf. audit
                            // B3 — compteur « en attente » trompeur). L'utilisateur corrigera le
                            // parent puis « Réessayer ».
                            parent == null || parent.etat == SaisieEnAttente.Etat.ERROR -> {
                                val raison = if (parent == null) "parent introuvable" else "parent en échec"
                                outbox.mettreAJour(saisie.uuid) {
                                    it.copy(etat = SaisieEnAttente.Etat.ERROR, messageErreur = raison)
                                }
                                echecs++
                                envoyees++
                                messages.add("⚠ ${saisie.objectType} : $raison")
                                progression(envoyees, total, "")
                                continue
                            }
                            // Parent encore PENDING/SENDING : repris quand il passera en SENT.
                            parent.etat != SaisieEnAttente.Etat.SENT -> continue
                            (parent.idServeur ?: 0) > 0 -> outbox.mettreAJour(saisie.uuid) {
                                it.copy(parentIdServeur = parent.idServeur)
                            }
                            else -> {
                                // Parent SENT mais id serveur inconnu ou illisible (réponse du POST
                                // perdue ou illisible) : sans clé, l'enfant partirait ORPHELIN côté
                                // GeoNature (la garde parentId > 0 d'envoyerVisite omettrait le
                                // champ) ou en 500. ERROR explicite plutôt qu'un envoi bancal ; un
                                // « Réessayer » retentera la récupération de l'id par uuid.
                                outbox.mettreAJour(saisie.uuid) {
                                    it.copy(etat = SaisieEnAttente.Etat.ERROR,
                                        messageErreur = "id serveur du parent inconnu — réessayez")
                                }
                                echecs++
                                envoyees++
                                messages.add("⚠ ${saisie.objectType} : id serveur du parent inconnu")
                                progression(envoyees, total, "")
                                continue
                            }
                        }
                    }
                    val etatPersiste = outbox.mettreAJour(saisie.uuid) {
                        // dejaTentee persisté AVANT le POST : si la réponse se perd, le prochain envoi
                        // saura qu'il doit vérifier l'existence côté serveur (anti-doublon).
                        // uuidPayload : les saisies créées avant la génération systématique (uuid
                        // réservé aux médias à l'époque) en reçoivent un ici, à la première tentative
                        // — sans identité client, l'anti-doublon est impossible.
                        it.copy(
                            etat = SaisieEnAttente.Etat.SENDING, messageErreur = null, dejaTentee = true,
                            uuidPayload = it.uuidPayload
                                ?: if (it.uuidFieldName != null) uuidAleatoire() else null,
                        )
                    }
                    if (!etatPersiste) {
                        // Impossible d'écrire « tentée » (espace disque ?) : on N'ENVOIE PAS — un POST
                        // dont l'état ne peut pas être persisté finirait en doublon au nouvel essai
                        // (audit 2026-08-27).
                        echecs++
                        envoyees++
                        messages.add("⚠ ${saisie.objectType} : impossible d'enregistrer l'état d'envoi " +
                            "(espace disque ?) — non envoyée, libérez de l'espace")
                        progression(envoyees, total, "")
                        continue
                    }
                    progression(envoyees, total, "Envoi de ${saisie.objectType}…")

                    val saisieAJour = outbox.tout().firstOrNull { it.uuid == saisie.uuid } ?: continue
                    // dejaTentee est lu sur l'instantané d'AVANT la transition SENDING : la valeur
                    // fraîche (saisieAJour) est toujours true — elle ne distingue plus une première
                    // tentative d'un nouvel envoi.
                    // NonCancellable : le POST et sa réponse d'un bloc, comme OccTax et OccHab — quitter
                    // « Mes visites » pendant l'envoi ne laisse plus une visite créée sans son id.
                    val res = withContext(NonCancellable) { envoyerUne(config, saisieAJour, dejaTenteeAvant = saisie.dejaTentee) }
                    envoyees++
                    val envoi = res.getOrNull()
                    if (envoi != null) {
                        // L'objet est créé côté serveur → ses enfants sont débloqués dans tous les cas
                        // (même si ses médias ont échoué) → nouveau tour de boucle.
                        nouvelleVagueSucces = true
                        reporterDansLeCache(saisieAJour, envoi.idServeur)
                        // Résout la clé de TOUS les enfants pointant ce parent local — y compris ceux
                        // qui ne sont pas traités dans cet envoi (ex. en erreur d'un envoi précédent).
                        // parentUuidLocal est conservé : il porte le groupement visuel et empêche de
                        // retirer le parent tant qu'un enfant reste.
                        if (envoi.idServeur > 0) {
                            outbox.tout()
                                .filter { it.parentUuidLocal == saisie.uuid }
                                .forEach { enfant ->
                                    outbox.mettreAJour(enfant.uuid) {
                                        it.copy(parentIdServeur = envoi.idServeur)
                                    }
                                }
                        }
                        if (envoi.erreurMedia == null) {
                            succes++
                            val sentPersiste = outbox.mettreAJour(saisie.uuid) {
                                it.copy(etat = SaisieEnAttente.Etat.SENT, idServeur = envoi.idServeur,
                                    objetCree = true, messageErreur = null)
                            }
                            messages.add(buildString {
                                append("✅ ${saisie.objectType} #${envoi.idServeur}")
                                if (!sentPersiste) {
                                    append(" — ⚠ transmise mais état local non enregistré (espace " +
                                        "disque ?) : libérez de l'espace, ne pas recréer")
                                }
                                if (envoi.doublonPossible) {
                                    append(" — ⚠ renvoyée sans vérification anti-doublon " +
                                        "possible : vérifier l'absence de doublon sur GeoNature")
                                }
                            })
                        } else {
                            // Objet créé MAIS médias non envoyés (ex. mode avion pendant le transfert
                            // de la photo) : la saisie reste visible en erreur ré-essayable —
                            // « Réessayer » ne renverra QUE les médias (objetCree déjà posé par
                            // envoyerUne). Avant ce correctif, la saisie passait SENT et les photos
                            // étaient perdues en silence.
                            echecs++
                            // La promesse de ce message est désormais TENUE : les fichiers déjà
                            // transmis sont mémorisés (mediasEnvoyes) et ne repartent pas (R1-M3).
                            val msg = ("Objet créé (#${envoi.idServeur}) mais média(s) non envoyé(s) : " +
                                "${envoi.erreurMedia} — « Réessayer » ne renverra que les médias " +
                                "manquants.").take(300)
                            outbox.mettreAJour(saisie.uuid) {
                                it.copy(etat = SaisieEnAttente.Etat.ERROR, messageErreur = msg)
                            }
                            messages.add("⚠ ${saisie.objectType} #${envoi.idServeur} : médias non envoyés")
                        }
                    } else {
                        val e = res.exceptionOrNull() ?: GNErreur.EnvoiEchoue(0, "échec inconnu")
                        echecs++
                        val msg = humaniserErreurReseau(e).take(200)
                        outbox.mettreAJour(saisie.uuid) {
                            it.copy(etat = SaisieEnAttente.Etat.ERROR, messageErreur = msg)
                        }
                        messages.add("⚠ ${saisie.objectType} : $msg")
                    }
                    progression(envoyees, total, "")
                }
            }
            // Les saisies SENT restent dans la file : « Mes visites » les montre sous « Envoyées »
            // jusqu'à ce que l'utilisateur les retire (demande terrain du 24 octobre 2026 ; la
            // purge de GeoMys-Android laissait cet onglet toujours vide).
            // Les « à compléter » écartées comptent comme des échecs : le récapitulatif doit dire que
            // tout n'est pas parti (leurs libellés sont déjà dans `messages`).
            Resultat(succes, echecs + aCompleter.size, messages)
        }
    }

    /** Résultat d'un [envoyerUne] réussi : l'objet est créé (ou l'était déjà) côté serveur ;
     *  [erreurMedia] non null si l'envoi d'au moins un média a échoué ensuite.
     *  [doublonPossible] : la saisie avait déjà été tentée mais la vérification anti-doublon était
     *  IMPOSSIBLE (pas d'uuid client / parent serveur inconnu) → on a re-POSTé à l'aveugle,
     *  l'utilisateur doit vérifier l'absence de doublon côté GeoNature. */
    private data class EnvoiUne(
        val idServeur: Int,
        val erreurMedia: String?,
        val doublonPossible: Boolean = false,
    )

    /** Objets CRÉÉS côté serveur dont le marquage `objetCree` n'a pas pu être persisté (écriture
     *  disque refusée) — uuid local → id serveur. Filet mémoire jusqu'à la mort du processus : un
     *  nouvel essai dans cette session ne re-POSTe pas (audit 2026-08-27). */
    private val verrouCrees = SynchronizedObject()
    private val creesNonPersistes = mutableMapOf<String, Int>()

    /** Envoie une saisie isolée. Lit les valeurs depuis valeursJson, reconstitue la Map attendue par
     *  [MonitoringEnvoi.envoyerVisite] et POST, puis envoie les médias éventuels vers gn_commons.
     *  Si la saisie porte déjà [SaisieEnAttente.objetCree] (tentative précédente où seuls les médias
     *  avaient échoué — ex. mode avion pendant le transfert de la photo), le POST est SAUTÉ
     *  (re-POSTer dupliquerait l'objet) : on ne renvoie que les médias. */
    /** Les valeurs enregistrées de [s], typées par la lecture JSON. */
    private fun valeursDe(s: SaisieEnAttente): Map<String, Any?> {
        val valeurs = mutableMapOf<String, Any?>()
        val obj = JSONObject(s.valeursJson)
        val cles = obj.keys()
        while (cles.hasNext()) {
            val k = cles.next()
            valeurs[k] = if (obj.isNull(k)) null else obj.opt(k)
        }
        return valeurs
    }

    private suspend fun envoyerUne(
        config: GeoNatureConfig,
        s0: SaisieEnAttente,
        /** dejaTentee AVANT la tentative courante (le champ de [s0] est déjà posé à true par la
         *  transition SENDING) : true = un POST précédent a pu aboutir sans confirmation. */
        dejaTenteeAvant: Boolean,
    ): Result<EnvoiUne> {
        // Objet créé lors d'une tentative précédente DANS CE PROCESSUS mais dont l'état n'a pas pu
        // être persisté : traité comme objetCree (médias seulement, jamais de re-POST).
        val s = synchronized(verrouCrees) { creesNonPersistes[s0.uuid] }
            ?.let { s0.copy(objetCree = true, idServeur = it) } ?: s0
        return try {
            val idServeur: Int
            var doublonPossible = false
            if (s.objetCree) {
                // Objet déjà créé lors d'une tentative précédente — médias seulement. Si l'id
                // serveur est INCONNU (réponse du POST illisible à l'époque), on tente de le
                // récupérer par l'uuid client : sans lui, les enfants partiraient sans clé.
                val idConnu = (s.idServeur ?: 0).takeIf { it > 0 }
                val idRecupere = if (idConnu == null && s.uuidPayload != null && s.uuidFieldName != null) {
                    monitoringEnvoi.chercherParUuid(
                        config = config,
                        moduleCode = s.moduleCode,
                        objectType = s.objectType,
                        uuidFieldName = s.uuidFieldName,
                        uuid = s.uuidPayload,
                    )?.takeIf { it > 0 }
                } else {
                    null
                }
                if (idRecupere != null) {
                    outbox.mettreAJour(s.uuid) { it.copy(idServeur = idRecupere) }
                }
                idServeur = idConnu ?: idRecupere ?: return Result.failure(
                    GNErreur.EnvoiEchoue(0, "objet créé côté serveur mais id inconnu — " +
                        "réessayez (récupération par uuid) ; ses enfants ne peuvent pas partir sans lui"))
                // Modifiée depuis sa création (« Éditer » après un envoi partiel) : la modification
                // part d'abord, en mise à jour ; elle se perdait (audit du 21 octobre, C3).
                if (s.modifieeApresCreation) {
                    monitoringEnvoi.modifierObjet(
                        config = config,
                        moduleCode = s.moduleCode,
                        objectType = s.objectType,
                        id = idServeur,
                        parentIdField = s.parentIdField,
                        parentId = s.parentIdServeur,
                        valeurs = valeursDe(s),
                        champsTexteLibre = s.champsTexteLibre,
                        champsDate = s.champsDate,
                        champsVidesGardes = s.champsVidesGardes,
                        uuidClient = s.uuidPayload,
                        uuidFieldName = s.uuidFieldName,
                    ).getOrElse { return Result.failure(it) }
                    outbox.mettreAJour(s.uuid) { it.copy(modifieeApresCreation = false) }
                }
            } else {
                // Anti-doublon : une saisie DÉJÀ tentée sans confirmation (réponse perdue en pleine
                // coupure) peut exister côté serveur. Si elle porte un uuid client, on la cherche par
                // lui AVANT de re-POSTer. Une erreur réseau pendant la vérification fait échouer
                // l'envoi (pas de re-POST aveugle).
                val peutVerifier = s.uuidPayload != null && s.uuidFieldName != null
                val idExistant = if (dejaTenteeAvant && peutVerifier) {
                    monitoringEnvoi.chercherParUuid(
                        config = config,
                        moduleCode = s.moduleCode,
                        objectType = s.objectType,
                        uuidFieldName = s.uuidFieldName,
                        uuid = s.uuidPayload,
                    )
                } else {
                    null
                }
                if (idExistant != null && idExistant <= 0) {
                    // Objet retrouvé côté serveur mais id illisible : pas de re-POST (doublon) ET pas
                    // de succès (les enfants partiraient sans clé) — échec explicite.
                    outbox.mettreAJour(s.uuid) { it.copy(objetCree = true) }
                    return Result.failure(GNErreur.EnvoiEchoue(0,
                        "objet déjà créé côté serveur mais id illisible — réessayez"))
                }
                if (idExistant != null) {
                    Journal.i(TAG, "Anti-doublon : ${s.objectType} ${s.uuidPayload} déjà créé côté serveur (#$idExistant) — pas de re-POST")
                    outbox.mettreAJour(s.uuid) { it.copy(objetCree = true, idServeur = idExistant) }
                    idServeur = idExistant
                } else {
                    // Re-POST d'une saisie déjà tentée SANS vérification possible (pas d'uuid client
                    // — vieux protocole sans uuid_field_name — ou parent serveur inconnu) : un
                    // doublon est possible si la 1re tentative avait en réalité abouti. Signalé à
                    // l'utilisateur par le message de fin d'envoi.
                    doublonPossible = dejaTenteeAvant && !peutVerifier
                    val valeurs = valeursDe(s)
                    val resVisite = monitoringEnvoi.envoyerVisite(
                        config = config,
                        moduleCode = s.moduleCode,
                        objectType = s.objectType,
                        parentIdField = s.parentIdField,
                        parentId = s.parentIdServeur,
                        valeurs = valeurs,
                        nomsChampsSchema = s.nomsChampsSchema,
                        champsTexteLibre = s.champsTexteLibre,
                        champsDate = s.champsDate,
                        champsVidesGardes = s.champsVidesGardes,
                        uuidClient = s.uuidPayload,
                        uuidFieldName = s.uuidFieldName,
                    )
                    val idRecu = resVisite.getOrElse { return Result.failure(it) }
                    if (idRecu <= 0) {
                        // POST 2xx mais id illisible dans la réponse : l'objet EXISTE côté serveur.
                        // objetCree est posé pour interdire tout re-POST (doublon), mais l'envoi reste
                        // un ÉCHEC — renvoyer l'id 0 en « succès » faisait partir les enfants sans clé
                        // (orphelins, 500). Au prochain essai, la branche objetCree ci-dessus
                        // récupérera l'id par l'uuid client.
                        outbox.mettreAJour(s.uuid) { it.copy(objetCree = true) }
                        return Result.failure(GNErreur.EnvoiEchoue(0,
                            "objet créé mais id serveur illisible — réessayez (récupération par uuid)"))
                    }
                    idServeur = idRecu
                    // Marque « créé côté serveur » IMMÉDIATEMENT (avant l'envoi des médias) : si
                    // l'appli meurt ou si les médias échouent, on sait qu'il ne faut plus re-POSTer
                    // cet objet.
                    if (!outbox.mettreAJour(s.uuid) { it.copy(objetCree = true, idServeur = idServeur) }) {
                        // Créé côté serveur mais IMPOSSIBLE à persister (espace disque ?) : filet
                        // mémoire + échec EXPLICITE (un « succès » laisserait croire que tout est en
                        // ordre alors qu'un nouvel essai après redémarrage dupliquerait).
                        synchronized(verrouCrees) { creesNonPersistes[s.uuid] = idServeur }
                        return Result.failure(GNErreur.EnvoiEchoue(0,
                            "objet créé côté serveur (#$idServeur) mais enregistrement local " +
                                "impossible (espace disque ?) — libérez de l'espace avant de " +
                                "réessayer ; ne pas recréer la saisie"))
                    }
                }
            }
            // Envoi des médias (plusieurs fichiers) après création. Un échec ne remet PAS en cause
            // la création de l'objet : il est remonté par EnvoiUne.erreurMedia pour que l'appelant
            // marque la saisie en ERREUR ré-essayable (médias seulement) — avant, il n'était que
            // journalisé et les photos étaient perdues en silence.
            var erreurMedia: String? = null
            // Seuls les médias PAS ENCORE transmis repartent : sans cette soustraction, chaque
            // « Réessayer » après un échec partiel renvoyait la liste complète et empilait les
            // doublons dans gn_commons.t_medias — trois essais échouant sur la 3ᵉ photo laissaient
            // trois exemplaires des deux premières (audit 2026-09-14, R1-M3).
            val medias = s.mediasDetaillesARenvoyer()
            if (medias.isNotEmpty() && s.uuidPayload != null) {
                val resMedia = upload.uploaderMediaMonitoring(
                    config = config,
                    medias = medias,
                    uuidAttachedRow = s.uuidPayload,
                    titreParDefaut = "${s.objectType} ${s.uuidPayload.take(8)}",
                    auteurParDefaut = config.nomUtilisateur.ifEmpty { config.login },
                    // NOUVEL ESSAI : l'objet existait déjà, ou des médias ont déjà été acquis. On
                    // demande alors au serveur ce qu'il détient avant de renvoyer quoi que ce soit —
                    // un POST dont la RÉPONSE s'est perdue n'a laissé aucune trace locale, et c'est
                    // exactement lui qui repartait en double (terrain, 2026-09-16).
                    reconcilier = s.objetCree || s.mediasEnvoyes != null,
                )
                // L'ACQUIS d'abord, et même quand l'ensemble échoue : les fichiers réellement
                // transmis sont persistés AVANT tout traitement d'erreur, faute de quoi l'essai
                // suivant les renverrait. Un échec d'écriture ici n'est pas bloquant (le pire est de
                // revenir au comportement d'avant : un doublon de média, pas une perte).
                if (resMedia.transmis.isNotEmpty()) {
                    outbox.mettreAJour(s.uuid) { saisie ->
                        saisie.copy(mediasEnvoyes = (saisie.mediasEnvoyes ?: emptyList()) + resMedia.transmis)
                    }
                }
                if (!resMedia.ok) {
                    erreurMedia = resMedia.message ?: "upload média échoué"
                    Journal.w(TAG, "Upload média échoué pour ${s.uuid} (objet créé OK) : ${resMedia.message}")
                }
            }
            Result.success(EnvoiUne(idServeur, erreurMedia, doublonPossible))
        } catch (e: CancellationException) {
            // Jamais avalée : une rotation ou une sortie d'écran pendant l'envoi annulait la portée
            // et marquait ERROR (« Erreur réseau : …cancelled ») toutes les saisies restantes.
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** L'objet [s] créé sur le serveur sous l'id [id], reporté dans le cache des Suivis pour que la
     *  fiche de son parent le montre sans attendre la synchro suivante (décision de l'utilisateur du
     *  20 octobre : ce que l'appli modifie est répercuté dans le cache). Ses propriétés : les valeurs
     *  envoyées, avec la clé de son parent et son uuid client. Un échec n'affecte pas l'envoi : la
     *  synchro suivante l'apportera. */
    private fun reporterDansLeCache(s: SaisieEnAttente, id: Int) {
        try {
            val proprietes = JSONObject(s.valeursJson)
            val idParent = s.parentIdServeur ?: -1
            if (!s.parentIdField.isNullOrEmpty() && idParent > 0) proprietes.put(s.parentIdField, idParent)
            if (!s.uuidFieldName.isNullOrEmpty() && !s.uuidPayload.isNullOrEmpty()) proprietes.put(s.uuidFieldName, s.uuidPayload)
            objets.reporterObjetCree(s.moduleCode, s.objectType, id, proprietes, null, s.parentObjectType, idParent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Journal.w(TAG, "${s.objectType} #$id envoyé mais pas reporté dans le cache : ${e.message}")
        }
    }

    private companion object {
        const val TAG = "OutboxEnvoi"
    }
}
