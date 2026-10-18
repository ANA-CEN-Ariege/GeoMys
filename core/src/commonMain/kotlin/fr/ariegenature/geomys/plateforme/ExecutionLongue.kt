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

package fr.ariegenature.geomys.plateforme

import kotlinx.coroutines.flow.Flow

/** Mène à terme un travail long (la synchro, plus tard l'envoi) quand l'utilisateur quitte l'écran
 *  ou met l'appareil en veille, comme les applis d'origine :
 *  - Android : service au premier plan de type `dataSync` et sa notification, qui affiche
 *    [avancement] (SyncForegroundService de GeoMys-Android) ; le module androidApp le fournit ;
 *  - iOS : tâche d'arrière-plan nommée [nom] et veille de l'écran suspendue tant que le travail
 *    dure (Network/SyncRunner.swift de GeoMys-IOS). */
fun interface ExecutionLongue {
    /** Lance [travail] hors du cycle de vie de l'écran et rend la main aussitôt. */
    fun lancer(nom: String, avancement: Flow<String>, travail: suspend () -> Unit)
}
