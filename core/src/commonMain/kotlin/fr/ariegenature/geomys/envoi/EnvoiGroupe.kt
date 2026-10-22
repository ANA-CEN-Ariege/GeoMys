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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/EnvoiGroupeUi.kt) : la
// boucle d'envoi, ses textes et son récapitulatif. Le bouton, la progression et les dialogues
// restent à l'écran.

package fr.ariegenature.geomys.envoi

/** Récapitulatif d'un envoi : titre et message du dialogue de fin. */
data class RecapEnvoi(val titre: String, val message: String)

/**
 * Bouton « Tout envoyer (N) » d'une liste de saisies en attente : visibilité, libellé, textes de
 * confirmation, envoi séquentiel avec progression et récapitulatif — commun à « Mes saisies »,
 * « Mes stations » et « Mes visites ». L'écran tient le drapeau « envoi en cours », PARTAGÉ avec
 * l'envoi d'une seule saisie (flèche de ligne), pour qu'un seul envoi tourne à la fois.
 */
object EnvoiGroupe {
    const val MESSAGE_DEJA_EN_COURS = "Un envoi est déjà en cours…"
    const val TITRE_CONFIGURATION_REQUISE = "Configuration requise"
    const val MESSAGE_CONFIGURATION_REQUISE =
        "La connexion GeoNature n'est pas configurée. Ouvrez la configuration (⚙️) avant d'envoyer."
    const val TITRE_CONFIRMATION = "Tout envoyer"
    const val BOUTON_CONFIRMATION = "Envoyer"

    /** Visible dans l'onglet d'envoi seulement, avec le droit de création, au moins un élément
     *  envoyable, et hors envoi en cours. */
    fun boutonVisible(ongletEnvoi: Boolean, autorise: Boolean, envoyables: Int, envoiEnCours: Boolean): Boolean =
        ongletEnvoi && autorise && envoyables > 0 && !envoiEnCours

    fun libelleBouton(envoyables: Int): String = "Tout envoyer ($envoyables)"

    fun messageConfirmation(nombre: Int): String = "Envoyer les $nombre saisie(s) en attente vers GeoNature ?"

    fun progression(rang: Int, total: Int): String = "Envoi $rang/$total vers GeoNature…"

    /** Envoie [elements] l'un APRÈS l'autre ([envoyerUne] rend le succès et un message humanisé, et
     *  persiste lui-même son résultat), en annonçant chaque pas par [progression] et en appelant
     *  [apresChaque] (rafraîchir la liste) ; rend le récapitulatif. */
    suspend fun <T> envoyer(
        elements: List<T>,
        progression: (String) -> Unit,
        envoyerUne: suspend (T) -> Pair<Boolean, String>,
        apresChaque: () -> Unit,
    ): RecapEnvoi {
        var succes = 0
        var echecs = 0
        val messages = mutableListOf<String>()
        elements.forEachIndexed { i, element ->
            progression(progression(i + 1, elements.size))
            val (ok, message) = envoyerUne(element)
            if (ok) succes++ else { echecs++; messages.add(message) }
            apresChaque()
        }
        val recap = buildString {
            append("$succes saisie(s) envoyée(s)")
            if (echecs > 0) append(", $echecs échec(s)")
            if (messages.isNotEmpty()) { append("\n\n"); append(messages.joinToString("\n")) }
        }
        return RecapEnvoi(if (echecs == 0) "Envoi" else "Erreur d'envoi", recap)
    }
}
