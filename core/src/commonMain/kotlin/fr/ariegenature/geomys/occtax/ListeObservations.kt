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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/ObservationsFragment.kt) : les
// textes de la liste des relevés de la saisie en cours et de ses confirmations.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.util.FormatDate
import fr.ariegenature.geomys.util.formaterDecimal

object ListeObservations {

    /** « Observations (N) » tant qu'un relevé porte une observation, « R relevés · N obs. » dès
     *  qu'un relevé en porte plusieurs. */
    fun titre(releves: Int, observations: Int): String =
        if (releves == observations) "Observations ($observations)"
        else "$releves relevé${if (releves > 1) "s" else ""} · $observations obs."

    /** La ligne principale : l'espèce d'un relevé solo, « N espèces » sinon. */
    fun espece(releve: Releve): String =
        if (releve.observations.size == 1) releve.premier.espece else "${releve.observations.size} espèces"

    /** La ligne secondaire : les notes d'un relevé solo (null si vides), ses espèces sinon. */
    fun secondaire(releve: Releve): String? =
        if (releve.observations.size == 1) releve.premier.notes.takeIf { it.isNotEmpty() }
        else releve.observations.joinToString(", ") { it.espece }

    /** Le nombre d'individus, s'il dépasse un (« ×N »). */
    fun effectif(releve: Releve): String? = releve.totalIndividus.takeIf { it > 1 }?.let { "×$it" }

    /** `dd/MM HH:mm` de l'instant du relevé. */
    fun date(releve: Releve): String = FormatDate.jourMoisHeure(releve.date)

    /** Les coordonnées de l'observation, à quatre décimales (`"%.4f, %.4f"` d'un appareil en
     *  français). */
    fun coordonnees(observation: Observation): String =
        "${formaterDecimal(observation.latitude, 4)}, ${formaterDecimal(observation.longitude, 4)}"

    const val TITRE_SUPPRESSION = "Supprimer ce relevé ?"

    /** Le message de confirmation de la suppression d'un relevé de plusieurs espèces. */
    fun messageSuppression(releve: Releve): String =
        "${releve.observations.size} espèces seront supprimées :\n" + releve.observations.joinToString("\n") { "• ${it.espece}" }

    const val TITRE_TOUT_EFFACER = "Tout réinitialiser"
    const val MESSAGE_TOUT_EFFACER = "Cette action est irréversible."
    const val BOUTON_TOUT_EFFACER = "Effacer les observations et la trace"
}
