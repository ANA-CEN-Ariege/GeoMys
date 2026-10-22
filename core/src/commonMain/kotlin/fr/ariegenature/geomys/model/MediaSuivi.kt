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

package fr.ariegenature.geomys.model

import kotlinx.serialization.Serializable

/** Un média d'une saisie des Suivis : son fichier local, et les champs du formulaire média du web
 *  (media-form-definition.ts de GeoNature) : titre (obligatoire), auteur et description, sans
 *  valeur au départ. [champ] : le champ `medias` qui le porte ; [schemaDotTable] : la table à
 *  laquelle il se rattache, celle de ce champ. Les noms forment le format du fichier de la file
 *  d'envoi : ne pas les renommer. */
@Serializable
data class MediaSuivi(
    val chemin: String,
    val titre: String = "",
    val auteur: String = "",
    val description: String = "",
    val champ: String = "",
    val schemaDotTable: String? = null,
)
