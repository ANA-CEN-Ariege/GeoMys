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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/RelevesOrphelins.kt) :
// l'objet devient une classe sur les réglages `releves_orphelins`, mêmes clés et même format.

package fr.ariegenature.geomys.store

import com.russhwolf.settings.Settings

/** Relevés OccTax créés côté serveur mais restés VIDES (aucune occurrence) et dont le DELETE de
 *  rollback a échoué — typiquement une coupure réseau totale (mode avion) qui fait échouer le POST
 *  de l'occurrence PUIS le DELETE du relevé. Mémorisés par serveur pour retenter leur suppression
 *  automatiquement au prochain envoi, au lieu de demander à l'utilisateur de les supprimer à la
 *  main dans l'interface web. */
class RelevesOrphelins(private val prefs: Settings) {

    /** Clé par identité serveur : un id de relevé n'a de sens que sur l'instance qui l'a créé. */
    private fun cle(base: String) = "orphelins_" + base.trim().trimEnd('/')

    fun liste(base: String): List<Int> =
        prefs.getString(cle(base), "").split(",").mapNotNull { it.trim().toIntOrNull() }

    fun ajouter(base: String, ids: Collection<Int>) {
        if (ids.isEmpty()) return
        val fusion = (liste(base) + ids).distinct()
        prefs.putString(cle(base), fusion.joinToString(","))
    }

    fun retirer(base: String, id: Int) {
        val restants = liste(base) - id
        if (restants.isEmpty()) prefs.remove(cle(base))
        else prefs.putString(cle(base), restants.joinToString(","))
    }
}
