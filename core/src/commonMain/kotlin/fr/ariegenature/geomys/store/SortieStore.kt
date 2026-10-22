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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/SortieStore.kt) :
// backend fichier (StoreFichierJson) au lieu des SharedPreferences, comme décidé au lot 1 ; API
// suspend ; la normalisation post-Gson devient un filtre sur le JSON brut (accepter, preparer).

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.store.io.FichierAtomique
import fr.ariegenature.geomys.store.io.StoreFichierJson
import fr.ariegenature.geomys.store.io.avecDefaut
import fr.ariegenature.geomys.store.io.estNul
import fr.ariegenature.geomys.store.io.sansElementsNuls
import fr.ariegenature.geomys.store.io.sansNulExplicite
import fr.ariegenature.geomys.store.io.transformerTableau
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Store local des sorties Occtax (« Mes saisies ») : cache mémoire, quarantaine, normalisation à
 *  la relecture, lire-modifier-écrire atomique. Unique dans le processus (ServicesGeoNature). */
class SortieStore(fichier: FichierAtomique) : StoreFichierJson<Sortie>(Sortie.serializer(), fichier, "SortieStore") {

    // ── Normalisation à la relecture ──
    // Une sortie dont l'id, la liste d'observations ou le parcours est explicitement null était
    // écartée ; absents, Gson leur donnait leur valeur par défaut (un id neuf, des listes vides), et
    // kotlinx fait de même. Une observation sans id était écartée (Observation n'a pas de
    // constructeur sans argument : absent ou nul, l'id restait null) ; un dénombrement, seulement
    // si son id est explicitement null. Les éléments nuls des listes sont retirés. Les autres champs
    // absents prennent leur valeur par défaut, dont un uuid d'occurrence neuf, qui se fige au
    // prochain enregistrement.

    override fun accepter(brut: JsonObject): Boolean = brut.sansNulExplicite("id", "observations", "pointsParcours")

    override fun preparer(brut: JsonObject): JsonObject =
        brut.transformerTableau("pointsParcours") { p ->
            (p as? JsonObject)?.avecDefaut("latitude", JsonPrimitive(0.0))?.avecDefaut("longitude", JsonPrimitive(0.0))
        }.transformerTableau("observations") { o -> (o as? JsonObject)?.takeUnless { it["id"].estNul() }?.let(::preparerObservation) }

    private fun preparerObservation(o: JsonObject): JsonObject =
        o.avecDefaut("espece", JsonPrimitive(""))
            .avecDefaut("latitude", JsonPrimitive(0.0))
            .avecDefaut("longitude", JsonPrimitive(0.0))
            .sansElementsNuls("mediaUrisCounting0", "observateursReleveIds", "observateursReleveNoms")
            .transformerTableau("denombrementsAdditionnels") { d ->
                (d as? JsonObject)?.takeUnless { it["id"] is JsonNull }?.sansElementsNuls("mediaUris")
            }

    suspend fun ajouter(sortie: Sortie): Boolean = muter { it.add(0, sortie) }

    /** Remplace une sortie, en PRÉSERVANT les acquis d'envoi observation par observation. Si l'id
     *  n'existe pas, ajoute en tête (= [ajouter]).
     *
     *  Les écrans d'édition (« Continuer la saisie », fin de trace, saisie rapide) réécrivent la
     *  sortie avec la liste d'observations de leur état d'écran, un instantané pris à la reprise.
     *  Sans cette fusion, toute observation marquée `envoyeeServeur` par un envoi en cours APRÈS
     *  cette reprise repassait à false SUR LE DISQUE : le doublon survivait au redémarrage et
     *  l'anti-doublon restait désarmé pour tous les envois suivants (audit 2026-09-14).
     *
     *  UNION, jamais écrasement, et le sens du risque le commande : un faux `false` crée un doublon
     *  irrécupérable en base régionale, un faux `true` perdrait une observation. Or `envoyeeServeur`
     *  n'est jamais posé qu'après un 2xx du serveur : l'union ne peut pas fabriquer de faux `true`.
     *  Même raisonnement pour `idReleveIncertain`, qui déclenche la vérification anti-doublon. */
    suspend fun remplacer(id: String, sortieMaj: Sortie): Boolean = muter { liste ->
        val idx = liste.indexOfFirst { it.id == id }
        if (idx < 0) {
            liste.add(0, sortieMaj)
            return@muter
        }
        val acquis = liste[idx].observations.associateBy { it.id }
        liste[idx] = sortieMaj.copy(observations = sortieMaj.observations.map { o ->
            val ancienne = acquis[o.id] ?: return@map o
            o.copy(
                envoyeeServeur = o.envoyeeServeur || ancienne.envoyeeServeur,
                idReleveIncertain = o.idReleveIncertain ?: ancienne.idReleveIncertain,
                releveTente = o.releveTente || ancienne.releveTente,
            )
        })
    }

    suspend fun supprimer(id: String) {
        muter { liste -> liste.removeAll { it.id == id } }
    }

    /** Renvoie le succès de la PERSISTANCE (false = écriture disque échouée : la sortie est
     *  transmise mais l'appareil ne le sait pas — l'appelant DOIT le signaler, sinon un ré-envoi
     *  créerait des doublons ; audit 2026-08-27). Efface aussi l'erreur d'un échec précédent. */
    suspend fun marquerEnvoyee(id: String): Boolean = muter { liste ->
        val idx = liste.indexOfFirst { it.id == id }
        if (idx >= 0) liste[idx] = liste[idx].copy(envoyeGeoNature = true, derniereErreurEnvoi = null)
    }

    /** Marque [obsIds] comme créées côté serveur ([fr.ariegenature.geomys.model.Observation.envoyeeServeur])
     *  AU FIL DE L'EAU pendant l'envoi et après un envoi PARTIEL : au prochain envoi de la sortie,
     *  elles ne seront pas re-postées (anti-doublon). Efface l'éventuelle incertitude. À appeler
     *  AVANT tout marquage global de la sortie. Renvoie le succès de la persistance. */
    suspend fun marquerObservationsEnvoyees(
        id: String,
        obsIds: Collection<String>,
        /** Par observation : les empreintes de l'occurrence et du relevé transmis (cf. [enregistrerEmpreintes]). */
        empreintes: Map<String, Pair<String?, String?>> = emptyMap(),
    ): Boolean {
        if (obsIds.isEmpty()) return true
        val ids = obsIds.toSet()
        return muter { liste ->
            val idx = liste.indexOfFirst { it.id == id }
            if (idx >= 0) liste[idx] = liste[idx].copy(observations = liste[idx].observations.map { o ->
                if (o.id in ids) o.copy(envoyeeServeur = true, idReleveIncertain = null).avecEmpreintes(empreintes[o.id]) else o
            })
        }
    }

    /** Les empreintes de ce qui vient d'être transmis en mise à jour (occurrence, relevé) : elles
     *  deviennent celles de l'envoi. Une composante nulle laisse l'empreinte en place. */
    suspend fun enregistrerEmpreintes(id: String, empreintes: Map<String, Pair<String?, String?>>): Boolean {
        if (empreintes.isEmpty()) return true
        return muter { liste ->
            val idx = liste.indexOfFirst { it.id == id }
            if (idx >= 0) liste[idx] = liste[idx].copy(observations = liste[idx].observations.map { o ->
                o.avecEmpreintes(empreintes[o.id])
            })
        }
    }

    private fun Observation.avecEmpreintes(empreintes: Pair<String?, String?>?): Observation =
        if (empreintes == null) this
        else copy(empreinteEnvoi = empreintes.first ?: empreinteEnvoi, empreinteReleveEnvoi = empreintes.second ?: empreinteReleveEnvoi)

    /** POST de l'occurrence [obsId] émis sans réponse dans le relevé serveur [idReleve] : elle y a
     *  peut-être été créée. Le prochain envoi vérifiera par son uuid client avant de re-POSTer
     *  (cf. GeoNatureUpload). Renvoie le succès de la persistance. */
    suspend fun marquerObservationIncertaine(id: String, obsId: String, idReleve: Int): Boolean =
        marquerObservationsIncertaines(id, listOf(obsId), idReleve)

    /** PRÉ-MARQUAGE d'un groupe : les occurrences de [obsIds] vont être postées dans le relevé
     *  serveur [idReleve] — tant qu'aucune réponse n'est revenue, chacune a PEUT-ÊTRE été créée.
     *
     *  Écrit AVANT le premier POST du groupe : si le processus meurt entre l'émission et la
     *  réponse, l'occurrence existe côté serveur, et c'est ce marquage qui déclenchera la
     *  vérification par uuid au ré-envoi (audit 2026-09-14, R1-C1). En UNE seule écriture pour
     *  tout le groupe : le store réécrit toute la saison à chaque écriture. Renvoie le succès de
     *  la persistance. */
    suspend fun marquerObservationsIncertaines(id: String, obsIds: Collection<String>, idReleve: Int): Boolean {
        if (obsIds.isEmpty()) return true
        val ids = obsIds.toSet()
        return muter { liste ->
            val idx = liste.indexOfFirst { it.id == id }
            if (idx >= 0) liste[idx] = liste[idx].copy(observations = liste[idx].observations.map { o ->
                if (o.id in ids && !o.envoyeeServeur) o.copy(idReleveIncertain = idReleve) else o
            })
        }
    }

    /** PRÉ-MARQUAGE du RELEVÉ d'un groupe : sa création va être tentée (POST émis). Posé AVANT le
     *  POST — si le processus meurt pendant, le relevé existe peut-être côté serveur sans trace
     *  locale, et le prochain envoi doit interroger l'uuid déterministe du groupe au lieu d'en
     *  créer un second. [tente] = false lève le marquage sur un rejet franc (4xx : rien n'a été
     *  créé). Renvoie le succès de la persistance. */
    suspend fun marquerReleveTente(id: String, obsIds: Collection<String>, tente: Boolean = true): Boolean {
        if (obsIds.isEmpty()) return true
        val ids = obsIds.toSet()
        return muter { liste ->
            val idx = liste.indexOfFirst { it.id == id }
            if (idx >= 0) liste[idx] = liste[idx].copy(observations = liste[idx].observations.map { o ->
                if (o.id in ids) o.copy(releveTente = tente) else o
            })
        }
    }

    /** Le serveur a REJETÉ franchement l'occurrence [obsId] (4xx) : rien n'a été créé, l'incertitude
     *  posée par le pré-marquage est levée. Sans cela, chaque envoi ultérieur paierait une
     *  vérification par uuid inutile. */
    suspend fun effacerIncertitudeObservation(id: String, obsId: String): Boolean = muter { liste ->
        val idx = liste.indexOfFirst { it.id == id }
        if (idx >= 0) liste[idx] = liste[idx].copy(observations = liste[idx].observations.map { o ->
            if (o.id == obsId) o.copy(idReleveIncertain = null) else o
        })
    }

    /** Mémorise l'échec du dernier envoi (message humanisé) — affiché en cadre rouge dans « Mes
     *  saisies » pour que l'échec reste visible après la fermeture du dialogue. */
    suspend fun marquerErreurEnvoi(id: String, message: String) {
        muter { liste ->
            val idx = liste.indexOfFirst { it.id == id }
            if (idx >= 0) liste[idx] = liste[idx].copy(derniereErreurEnvoi = message.take(200))
        }
    }
}
