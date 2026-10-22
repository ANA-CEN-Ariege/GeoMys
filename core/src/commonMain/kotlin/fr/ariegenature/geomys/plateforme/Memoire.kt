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

package fr.ariegenature.geomys.plateforme

/** Mémoire occupée par l'application, en octets, telle que compte la ressource qui limite
 *  l'application sur chaque plateforme :
 *  - Android : le tas Java utilisé, borné par [memoireReference] (sans `largeHeap`) ;
 *  - iOS : l'empreinte physique du processus (`phys_footprint`), celle que le système regarde pour
 *    fermer une application qui consomme trop. */
expect fun memoireUtilisee(): Long

/** Point de comparaison, ce que l'application peut occuper au plus :
 *  - Android : la taille maximale du tas Java (`maxMemory`, qui dépend de `largeHeap`) ;
 *  - iOS : la limite d'empreinte au-delà de laquelle le système ferme l'application (occupé +
 *    `os_proc_available_memory`), à défaut (simulateur) la mémoire physique de l'appareil. */
expect fun memoireReference(): Long

/** Fait passer le ramasse-miettes, pour mesurer la mémoire réellement retenue. */
expect fun ramasserMiettes()
