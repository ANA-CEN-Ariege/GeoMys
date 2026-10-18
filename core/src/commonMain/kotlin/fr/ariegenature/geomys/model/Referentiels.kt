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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/GeoNatureBrowse.kt,
// modèles) : passés dans model/ et @Serializable, sous les mêmes noms de propriétés, pour les caches
// JSON des réglages (Gson sous Android).

package fr.ariegenature.geomys.model

import kotlinx.serialization.Serializable

/** Dataset GeoNature.
 *  - [idTaxaList] (`id_taxa_list` côté `gn_meta.t_datasets`) : liste UsersHub imposée.
 *  - [actif] : flag `active` côté serveur. On charge même les inactifs (un protocole de
 *    monitoring peut référencer un dataset inactif côté OCCTAX) — le filtrage est fait
 *    à l'affichage selon le contexte (config OCCTAX = actif seulement, monitoring = libre).
 *  - [moduleCodes] : codes des modules auxquels le dataset est rattaché côté serveur
 *    (tableau `modules` du payload). Vide si l'instance n'expose pas cette info. */
@Serializable
data class GeoNatureDataset(
    val id: Int,
    val nom: String,
    val idTaxaList: Int? = null,
    val actif: Boolean = true,
    val moduleCodes: List<String> = emptyList(),
)

@Serializable
data class GeoNatureListe(val id: Int, val nom: String)

/** Observateur (utilisateur GeoNature) chargé via `/api/users/menu/<id_liste>`. */
@Serializable
data class GeoNatureObservateur(val idRole: Int, val nomComplet: String)
