/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifié pour GeoMys (ANA - CEN Ariège, 2026) : traduit de Java en Kotlin multiplateforme
 * depuis les sources du SDK Android (android-36.1, org/json/JSON.java). Texte de la licence :
 * licences/Apache-2.0.txt.
 */

package fr.ariegenature.geomys.json

/** Conversions implicites d'org.json : ce sont elles qui font qu'`optInt` lit la chaîne "12.7"
 *  comme 12, et qu'`optString` rend "null" pour un null explicite. */
internal object JSON {

    /** Rend [d] s'il est admis en JSON ; lève sinon. */
    fun checkDouble(d: Double): Double {
        if (d.isInfinite() || d.isNaN()) {
            throw JSONException("Forbidden numeric value: $d")
        }
        return d
    }

    fun toBoolean(value: Any?): Boolean? {
        if (value is Boolean) {
            return value
        } else if (value is String) {
            if ("true".equals(value, ignoreCase = true)) {
                return true
            } else if ("false".equals(value, ignoreCase = true)) {
                return false
            }
        }
        return null
    }

    fun toDouble(value: Any?): Double? = when (value) {
        is Double -> value
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    fun toInteger(value: Any?): Int? = when (value) {
        is Int -> value
        is Number -> value.toInt()
        is String -> value.toDoubleOrNull()?.toInt()
        else -> null
    }

    fun toLong(value: Any?): Long? = when (value) {
        is Long -> value
        is Number -> value.toLong()
        is String -> value.toDoubleOrNull()?.toLong()
        else -> null
    }

    fun toString(value: Any?): String? = when (value) {
        is String -> value
        null -> null
        else -> value.toString()
    }

    fun typeMismatch(indexOrName: Any, actual: Any?, requiredType: String): JSONException =
        if (actual == null) {
            JSONException("Value at $indexOrName is null.")
        } else {
            JSONException("Value $actual at $indexOrName of type ${nomDeType(actual)} cannot be converted to $requiredType")
        }

    fun typeMismatch(actual: Any?, requiredType: String): JSONException =
        if (actual == null) {
            JSONException("Value is null.")
        } else {
            JSONException("Value $actual of type ${nomDeType(actual)} cannot be converted to $requiredType")
        }

    /** Le nom de classe Java qu'afficherait Android dans ses messages d'erreur. */
    fun nomDeType(valeur: Any): String = when (valeur) {
        is String -> "java.lang.String"
        is Int -> "java.lang.Integer"
        is Long -> "java.lang.Long"
        is Double -> "java.lang.Double"
        is Float -> "java.lang.Float"
        is Short -> "java.lang.Short"
        is Byte -> "java.lang.Byte"
        is Boolean -> "java.lang.Boolean"
        is Char -> "java.lang.Character"
        is JSONObject -> "org.json.JSONObject"
        is JSONArray -> "org.json.JSONArray"
        else -> if (valeur === JSONObject.NULL) "org.json.JSONObject$1" else valeur::class.simpleName ?: "?"
    }
}
