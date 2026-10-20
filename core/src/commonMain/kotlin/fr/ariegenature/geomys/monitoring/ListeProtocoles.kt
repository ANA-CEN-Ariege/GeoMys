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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SuivisFragment.kt et
// res/layout/fragment_suivis.xml) : les textes de la liste des protocoles.

package fr.ariegenature.geomys.monitoring

/** Les textes de l'écran « Monitoring » (liste des protocoles). */
object ListeProtocoles {
    const val TITRE = "Monitoring"
    const val SOUS_TITRE = "Protocoles disponibles sur le serveur"

    /** Le bouton vers les saisies non envoyées, montré seulement s'il y en a. */
    fun libelleAttente(nombre: Int): String = "Données en attente d'envoi ($nombre)"

    fun messageErreur(erreur: Throwable): String = "Erreur de chargement : ${erreur.message}"

    /** Aucune liste en cache : la synchro des Suivis n'a jamais abouti (les écrans ne lisent que le
     *  cache, décision de l'utilisateur du 20 octobre). */
    const val AUCUN_PROTOCOLE_EN_CACHE = "Aucun protocole en cache — lancez « Recharger les données »."

    /** Liste vide : neuf fois sur dix, un droit CRUVED à 0 sur le serveur. */
    fun messageAucunProtocole(login: String): String =
        "Aucun protocole accessible pour $login.\n" +
            "Vérifie que gn_module_monitoring est installé sur le serveur " +
            "et que l'utilisateur a au moins un droit CRUVED > 0 sur les modules concernés."
}
