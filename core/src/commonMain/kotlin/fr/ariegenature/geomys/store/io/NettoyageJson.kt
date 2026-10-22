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

package fr.ariegenature.geomys.store.io

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Outils de la relecture des stores (JsonCollectionStore.accepter et preparer) : la normalisation de
// GeoMys-Android, faite sur les objets que Gson rendait à moitié nuls, se fait ici sur le JSON brut,
// avant un décodage qui, lui, ne tolère aucun nul là où le modèle n'en admet pas.
//
// Ce que Gson laissait à null dépend de la classe. Une classe dont TOUS les paramètres ont une valeur
// par défaut (Sortie, Denombrement, OccHabSaisie, OccHabStation, OccHabHabitat) a un constructeur
// sans argument, que Gson utilise : une clé ABSENTE y prend sa valeur par défaut (un id neuf, une
// liste vide), et seule une clé explicitement `null` laisse le champ à null — c'est elle qui fait
// écarter l'entrée ([sansNulExplicite]). Une classe à paramètres obligatoires (Observation,
// PointTrace, SaisieEnAttente) est instanciée sans constructeur : là, une clé absente vaut null
// comme une clé nulle ([aLesCles]).

/** Vrai pour une clé absente ou nulle. */
internal fun JsonElement?.estNul(): Boolean = this == null || this is JsonNull

/** Vrai si toutes les [cles] sont présentes et non nulles. */
internal fun JsonObject.aLesCles(vararg cles: String): Boolean = cles.all { !this[it].estNul() }

/** Vrai si aucune des [cles] n'est explicitement `null` (absente, elle prendra sa valeur par défaut). */
internal fun JsonObject.sansNulExplicite(vararg cles: String): Boolean = cles.none { this[it] is JsonNull }

/** [cle] transformée, élément par élément, par [transformer] (null = élément retiré). Inchangé si la
 *  clé n'est pas un tableau. */
internal fun JsonObject.transformerTableau(cle: String, transformer: (JsonElement) -> JsonElement?): JsonObject {
    val tableau = this[cle] as? JsonArray ?: return this
    return JsonObject(this + (cle to JsonArray(tableau.mapNotNull(transformer))))
}

/** [cles] sans leurs éléments nuls (les `filterNotNull()` de la normalisation d'Android). */
internal fun JsonObject.sansElementsNuls(vararg cles: String): JsonObject =
    cles.fold(this) { objet, cle -> objet.transformerTableau(cle) { it.takeUnless { e -> e is JsonNull } } }

/** [cle] remplacée par [defaut] si elle est absente ou nulle (`o.espece ?: ""` sous Android). */
internal fun JsonObject.avecDefaut(cle: String, defaut: JsonPrimitive): JsonObject =
    if (this[cle].estNul()) JsonObject(this + (cle to defaut)) else this
