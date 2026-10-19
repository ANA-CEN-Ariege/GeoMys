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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/TraceFragment.kt,
// showConfirmTerminer et terminerSortie ; SaisieRapideFragment.kt, terminerSaisie) : la fin d'une
// saisie OccTax, commune aux deux écrans.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.store.SortieStore
import fr.ariegenature.geomys.util.maintenantMillis

/** La fin d'une saisie : « Terminer la saisie » et ses choix, puis l'écriture de la sortie. */
object FinSaisie {
    const val TITRE = "Terminer la saisie"
    const val ENREGISTRER_QUITTER = "Enregistrer et quitter"
    const val ENREGISTRER_ENVOYER = "Enregistrer et envoyer à GeoNature"
    const val CONTINUER = "Continuer la saisie"

    /** Échec d'écriture à la fin : la saisie reste à l'écran, ni abandonnée ni envoyée. */
    const val CONSIGNE_ECHEC =
        "Libérez de l'espace (photos, cache de cartes) puis refaites « Terminer » — la saisie est conservée à l'écran en attendant."

    /** Échec de l'auto-save au fil de l'eau : on reste dans la saisie. */
    const val CONSIGNE_ECHEC_BROUILLON =
        "Libérez de l'espace (photos, cache de cartes) SANS quitter la saisie : chaque nouvelle observation retente l'enregistrement complet."

    /** Les choix du dialogue « Terminer la saisie » ; l'envoi n'est proposé que si [peutEnvoyer]. */
    fun options(peutEnvoyer: Boolean): List<String> =
        listOfNotNull(ENREGISTRER_QUITTER, ENREGISTRER_ENVOYER.takeIf { peutEnvoyer }, CONTINUER)

    /** Résultat de l'écriture finale : la sortie écrite (à envoyer s'il y a lieu), ou l'échec. */
    class Resultat(val sortie: Sortie, val ecrite: Boolean)

    /** Fin d'une saisie multi-taxons : la sortie reprend la trace et les observations de la
     *  session. En reprise, on garde l'id et la date d'origine, pour que la sortie reste à sa place
     *  chronologique dans la liste. La session n'est remise à zéro que si l'écriture a réussi :
     *  réinitialiser après un échec perdrait la seule copie (mémoire) de la saisie, et envoyer sans
     *  trace dans le store rendrait les marquages anti-doublon inopérants (audit 2026-08-23). */
    suspend fun terminerMulti(session: SessionSaisieOcctax, store: SortieStore): Resultat {
        val tracker = session.locationTracker
        tracker.arreterParcours()
        session.attendreEcritures()
        val idReprise = session.sortieEnEditionId
        val existante = idReprise?.let { id -> store.charger().firstOrNull { it.id == id } }
        val parcours = tracker.parcours.value
        val obs = session.observations.value
        val distance = tracker.distanceTotale.value
        val sortie = existante?.copy(pointsParcours = parcours, observations = obs, distanceTotale = distance)
            ?: Sortie(date = maintenantMillis(), pointsParcours = parcours, observations = obs, distanceTotale = distance)
        if (sortie.pointsParcours.isNotEmpty() || sortie.observations.isNotEmpty()) {
            val ok = if (idReprise != null) store.remplacer(idReprise, sortie) else store.ajouter(sortie)
            if (!ok) return Resultat(sortie, ecrite = false)
        }
        session.reinitialiser()
        return Resultat(sortie, ecrite = true)
    }

    /** Fin d'une saisie mono-taxon : la saisie, déjà auto-sauvée au fil de l'eau sous l'id de la
     *  session, est mise à jour à cette même entrée (id, date et place dans la liste préservés) au
     *  lieu d'en créer une nouvelle ; vidée, elle disparaît. */
    suspend fun terminerMono(session: SessionSaisieOcctax, store: SortieStore): Resultat {
        session.attendreEcritures()
        val id = session.sortieEnEditionId
        val existante = id?.let { sid -> store.charger().firstOrNull { it.id == sid } }
        val sortie = (existante ?: Sortie()).copy(observations = session.observations.value)
        if (sortie.observations.isNotEmpty()) {
            val ok = if (id != null) store.remplacer(id, sortie) else store.ajouter(sortie)
            if (!ok) return Resultat(sortie, ecrite = false)
        } else {
            id?.let { store.supprimer(it) }
        }
        session.reinitialiser()
        return Resultat(sortie, ecrite = true)
    }
}
