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

import kotlinx.coroutines.flow.Flow

/** Mène à terme un travail long (la synchro, les envois) quand l'utilisateur quitte l'écran ou met
 *  l'appareil en veille, comme les applis d'origine :
 *  - Android : service au premier plan de type `dataSync` et sa notification, qui affiche
 *    [avancement] (SyncForegroundService de GeoMys-Android) ; le module androidApp le fournit ;
 *  - iOS : tâche d'arrière-plan nommée [nom] et veille de l'écran suspendue tant que le travail
 *    dure (Network/SyncRunner.swift de GeoMys-IOS). */
fun interface ExecutionLongue {
    /** Lance [travail] hors du cycle de vie de l'écran et rend la main aussitôt. */
    fun lancer(nom: String, avancement: Flow<String>, travail: suspend () -> Unit)

    /** Mène [travail] dans la coroutine de l'appelant, qui en suit l'avancement et en reçoit le
     *  résultat (un envoi), en le gardant de la mise en veille tant qu'il dure : tâche
     *  d'arrière-plan nommée [nom] sous iOS, comme TacheArrierePlan dans GeoMys-IOS (sans elle, iOS
     *  suspendait l'appli quelques secondes après le verrouillage, et l'envoi restait partiel ou
     *  incertain). Rien de plus sous Android, où GeoMys-Android ne protégeait pas ses envois. */
    suspend fun <T> pendant(nom: String, travail: suspend () -> T): T = travail()
}

/** [travail] gardé de la mise en veille par cette [ExecutionLongue], s'il y en a une. */
internal suspend fun <T> ExecutionLongue?.proteger(nom: String, travail: suspend () -> T): T =
    if (this == null) travail() else pendant(nom, travail)
