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

/** Le coffre du mot de passe GeoNature : Android Keystore (MdpChiffre) ou trousseau iOS
 *  (CoffreKeychain). Ses opérations ne lèvent jamais d'exception : un coffre indisponible fait
 *  garder le mot de passe en mémoire seulement (GeoNatureConfig.motDePasse), jamais en clair. */
interface CoffreMotDePasse {
    /** Le mot de passe, ou null s'il n'y en a pas ou si le coffre est illisible. */
    fun lire(): String?

    /** Range [mdp] ; false si le coffre est indisponible (rien n'est alors écrit). */
    fun ecrire(mdp: String): Boolean

    fun effacer()

    /** true si le coffre est opérationnel. */
    fun disponible(): Boolean
}
