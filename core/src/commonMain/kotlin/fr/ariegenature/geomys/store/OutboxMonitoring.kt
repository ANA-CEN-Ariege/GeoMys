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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/OutboxMonitoring.kt) :
// l'objet devient une classe sur son fichier (unique dans ServicesGeoNature) ; API suspend ; la
// reconstruction post-Gson (normaliser) devient le filtre accepter / preparer, sur le JSON brut. Le
// modèle SaisieEnAttente est dans model/ depuis le lot 1.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.store.io.FichierAtomique
import fr.ariegenature.geomys.store.io.StoreFichierJson
import fr.ariegenature.geomys.store.io.aLesCles
import fr.ariegenature.geomys.store.io.sansElementsNuls
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** File locale des saisies Suivis (visites, observations…) en attente d'envoi, dans
 *  `monitoring_outbox.json` (écriture durable : ces saisies sont irremplaçables). L'envoi est
 *  exclusivement à la demande de l'utilisateur (« Mes visites ») — aucun envoi automatique au
 *  retour du réseau. La file reste petite (quelques dizaines à centaines au plus). */
class OutboxMonitoring(fichier: FichierAtomique) :
    StoreFichierJson<SaisieEnAttente>(SaisieEnAttente.serializer(), fichier, "OutboxMonitoring") {

    /** Une saisie sans uuid, module, type, valeurs ou état est écartée, comme sous Android. Un état
     *  INCONNU aussi : le décodage tolérant le ramènerait à PENDING, et la saisie, peut-être déjà
     *  envoyée, repartirait (doublon). */
    override fun accepter(brut: JsonObject): Boolean =
        brut.aLesCles("uuid", "moduleCode", "objectType", "valeursJson", "etat") &&
            SaisieEnAttente.Etat.entries.any { it.name == brut["etat"]?.jsonPrimitive?.contentOrNull }

    override fun preparer(brut: JsonObject): JsonObject =
        brut.sansElementsNuls("nomsChampsSchema", "champsTexteLibre", "mediaPathsLocal", "mediasEnvoyes", "champsManquants")

    /** Ajoute une saisie. Renvoie false si la persistance disque a échoué (disque plein, E/S) :
     *  la saisie n'existe alors NULLE PART — l'écran doit prévenir l'utilisateur. */
    suspend fun ajouter(saisie: SaisieEnAttente): Boolean {
        Journal.i(nom, "ajouter : uuid=${saisie.uuid}, type=${saisie.objectType}, module=${saisie.moduleCode}")
        return muter { it.add(saisie) }
    }

    suspend fun mettreAJour(uuid: String, transform: (SaisieEnAttente) -> SaisieEnAttente): Boolean =
        muter { liste -> for (i in liste.indices) if (liste[i].uuid == uuid) liste[i] = transform(liste[i]) }

    suspend fun supprimer(uuid: String) {
        muter { liste -> liste.removeAll { it.uuid == uuid } }
    }

    /** Les uuid des descendants locaux d'une saisie (observations rattachées à une visite locale…),
     *  en largeur d'abord : pour supprimer en cascade ou envoyer un groupe. */
    suspend fun descendants(uuid: String): List<String> = descendantsDe(charger(), uuid)

    private fun descendantsDe(tous: List<SaisieEnAttente>, uuid: String): List<String> {
        val resultat = mutableListOf<String>()
        val aExplorer = ArrayDeque<String>().apply { add(uuid) }
        while (aExplorer.isNotEmpty()) {
            val courant = aExplorer.removeFirst()
            tous.filter { it.parentUuidLocal == courant }.forEach {
                resultat.add(it.uuid)
                aExplorer.add(it.uuid)
            }
        }
        return resultat
    }

    /** Supprime la saisie [uuid] ET tous ses descendants locaux. Renvoie le nombre total de
     *  saisies supprimées (1 + descendants). */
    suspend fun supprimerCascade(uuid: String): Int = muterCalcul { liste ->
        val aRetirer = (descendantsDe(liste, uuid) + uuid).toSet()
        liste.removeAll { it.uuid in aRetirer }
        aRetirer.size
    }

    suspend fun tout(): List<SaisieEnAttente> = charger()

    /** Saisies qui restent à envoyer (PENDING et ERROR). Les SENT restent dans la liste (onglet
     *  « Envoyées ») mais ne comptent pas comme « en attente ». */
    suspend fun enAttente(): List<SaisieEnAttente> =
        charger().filter { it.etat == SaisieEnAttente.Etat.PENDING || it.etat == SaisieEnAttente.Etat.ERROR }

    /** Compteur affiché sur l'accueil et dans le menu. */
    suspend fun countEnAttente(): Int = enAttente().size

    /** « Retirer de la liste » : la saisie envoyée [uuid] et ses descendants, s'ils sont TOUS
     *  envoyés — un enfant encore à envoyer a besoin de son parent local, qui porte sa clé et son
     *  groupe. Renvoie le nombre de saisies retirées (0 si le groupe n'est pas entièrement parti).
     *  Les envoyées ne sont jamais retirées d'office (demande terrain du 24 octobre 2026). */
    suspend fun retirerEnvoyee(uuid: String): Int = muterCalcul { liste ->
        val groupe = (descendantsDe(liste, uuid) + uuid).toSet()
        if (liste.any { it.uuid in groupe && it.etat != SaisieEnAttente.Etat.SENT }) return@muterCalcul 0
        liste.removeAll { it.uuid in groupe }
        groupe.size
    }

    suspend fun vider() {
        muter { it.clear() }
    }
}
