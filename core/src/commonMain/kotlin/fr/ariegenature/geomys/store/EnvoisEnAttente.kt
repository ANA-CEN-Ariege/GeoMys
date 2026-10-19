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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/AccueilFragment.kt,
// nbSaisiesEnAttente, nbVisitesEnAttente et nbStationsEnAttente) : les comptes passent au socle, lus
// dans les stores de saisie.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesGeoNature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ce qui reste à envoyer, pour les pastilles de l'accueil. */
data class EnvoisEnAttente(
    /** Saisies OccTax (sorties) non encore envoyées — hors sorties importées. */
    val saisies: Int,
    /** Saisies des Suivis (visites et observations) en attente ou en erreur. */
    val visites: Int,
    /** SAISIES OccHab non encore envoyées (« Mes stations », onglet À envoyer). */
    val stations: Int,
) {
    /** Pastille du bouton menu : au moins une saisie, une visite OU une station reste à envoyer. */
    val auMoinsUn: Boolean get() = saisies > 0 || visites > 0 || stations > 0
}

/** Compte ce qui reste à envoyer dans les trois stores de saisie, hors du fil principal (la
 *  première lecture d'un store décode tout son fichier). */
suspend fun ServicesGeoNature.envoisEnAttente(): EnvoisEnAttente = withContext(Dispatchers.Default) {
    EnvoisEnAttente(
        saisies = sortieStore.charger().count { !it.envoyeGeoNature && !it.estImportee },
        visites = outbox.countEnAttente(),
        stations = occHabStore.charger().count { !it.envoyeGeoNature },
    )
}
