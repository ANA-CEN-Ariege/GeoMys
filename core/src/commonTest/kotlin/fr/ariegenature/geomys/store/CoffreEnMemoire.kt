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

package fr.ariegenature.geomys.store

/** Un coffre en mémoire, qu'on peut rendre indisponible (Keystore ou trousseau en panne). */
internal class CoffreEnMemoire(var operationnel: Boolean = true) : CoffreMotDePasse {
    var contenu: String? = null
        private set

    override fun lire(): String? = if (operationnel) contenu else null

    override fun ecrire(mdp: String): Boolean {
        if (!operationnel) return false
        contenu = mdp
        return true
    }

    override fun effacer() {
        contenu = null
    }

    override fun disponible(): Boolean = operationnel
}
