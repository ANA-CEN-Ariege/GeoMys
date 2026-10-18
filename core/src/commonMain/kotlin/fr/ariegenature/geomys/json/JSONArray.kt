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
 * Modifié pour GeoMys-Common (ANA - CEN Ariège, 2026) : traduit de Java en Kotlin multiplateforme
 * depuis les sources du SDK Android (android-36.1, org/json/JSONArray.java). Texte de la licence :
 * licences/Apache-2.0.txt.
 */

package fr.ariegenature.geomys.json

import kotlin.math.min

/** Portage d'`org.json.JSONArray` d'Android, à l'identique (voir [JSONObject]). Un tableau peut
 *  contenir des `null` de Kotlin (cases ajoutées par [put] à un indice au-delà de la fin, virgules
 *  en trop à la lecture), que [isNull] traite comme [JSONObject.NULL]. */
class JSONArray {

    private val values: MutableList<Any?>

    /** Un tableau vide. */
    constructor() {
        values = ArrayList()
    }

    /** Copie de [copyFrom] ; chaque valeur passe par [JSONObject.wrap]. */
    constructor(copyFrom: Collection<*>) : this() {
        for (element in copyFrom) {
            put(JSONObject.wrap(element))
        }
    }

    /** Lit le prochain tableau de [readFrom]. */
    constructor(readFrom: JSONTokener) {
        val objet = readFrom.nextValue()
        if (objet is JSONArray) {
            values = objet.values
        } else {
            throw JSON.typeMismatch(objet, "JSONArray")
        }
    }

    /** Lit le texte [json], avec la tolérance de [JSONTokener]. */
    constructor(json: String) : this(JSONTokener(json))

    /** Copie d'un tableau Kotlin, d'objets ou de types primitifs. */
    constructor(array: Any) {
        val elements: List<Any?> = when (array) {
            is Array<*> -> array.asList()
            is BooleanArray -> array.asList()
            is ByteArray -> array.asList()
            is CharArray -> array.asList()
            is DoubleArray -> array.asList()
            is FloatArray -> array.asList()
            is IntArray -> array.asList()
            is LongArray -> array.asList()
            is ShortArray -> array.asList()
            else -> throw JSONException("Not a primitive array: ${array::class}")
        }
        values = ArrayList(elements.size)
        for (element in elements) {
            put(JSONObject.wrap(element))
        }
    }

    fun length(): Int = values.size

    fun put(value: Boolean): JSONArray {
        values.add(value)
        return this
    }

    fun put(value: Double): JSONArray {
        values.add(JSON.checkDouble(value))
        return this
    }

    fun put(value: Int): JSONArray {
        values.add(value)
        return this
    }

    fun put(value: Long): JSONArray {
        values.add(value)
        return this
    }

    /** Sans contrôle des nombres, comme sous Android. */
    fun put(value: Any?): JSONArray {
        values.add(value)
        return this
    }

    internal fun checkedPut(value: Any?) {
        if (value is Number) {
            JSON.checkDouble(value.toDouble())
        }
        put(value)
    }

    fun put(index: Int, value: Boolean): JSONArray = placer(index, value)

    fun put(index: Int, value: Double): JSONArray = placer(index, value)

    fun put(index: Int, value: Int): JSONArray = placer(index, value)

    fun put(index: Int, value: Long): JSONArray = placer(index, value)

    /** Remplit de `null` jusqu'à [index] si besoin. */
    fun put(index: Int, value: Any?): JSONArray = placer(index, value)

    private fun placer(index: Int, value: Any?): JSONArray {
        if (value is Number) {
            JSON.checkDouble(value.toDouble())
        }
        while (values.size <= index) {
            values.add(null)
        }
        values[index] = value
        return this
    }

    fun isNull(index: Int): Boolean {
        val value = opt(index)
        return value == null || value === JSONObject.NULL
    }

    operator fun get(index: Int): Any {
        if (index < 0 || index >= values.size) {
            throw JSONException("Index $index out of range [0..${values.size})")
        }
        return values[index] ?: throw JSONException("Value at $index is null.")
    }

    fun opt(index: Int): Any? = if (index < 0 || index >= values.size) null else values[index]

    fun remove(index: Int): Any? = if (index < 0 || index >= values.size) null else values.removeAt(index)

    fun getBoolean(index: Int): Boolean {
        val objet = get(index)
        return JSON.toBoolean(objet) ?: throw JSON.typeMismatch(index, objet, "boolean")
    }

    fun optBoolean(index: Int): Boolean = optBoolean(index, false)

    fun optBoolean(index: Int, fallback: Boolean): Boolean = JSON.toBoolean(opt(index)) ?: fallback

    fun getDouble(index: Int): Double {
        val objet = get(index)
        return JSON.toDouble(objet) ?: throw JSON.typeMismatch(index, objet, "double")
    }

    fun optDouble(index: Int): Double = optDouble(index, Double.NaN)

    fun optDouble(index: Int, fallback: Double): Double = JSON.toDouble(opt(index)) ?: fallback

    fun getInt(index: Int): Int {
        val objet = get(index)
        return JSON.toInteger(objet) ?: throw JSON.typeMismatch(index, objet, "int")
    }

    fun optInt(index: Int): Int = optInt(index, 0)

    fun optInt(index: Int, fallback: Int): Int = JSON.toInteger(opt(index)) ?: fallback

    fun getLong(index: Int): Long {
        val objet = get(index)
        return JSON.toLong(objet) ?: throw JSON.typeMismatch(index, objet, "long")
    }

    fun optLong(index: Int): Long = optLong(index, 0L)

    fun optLong(index: Int, fallback: Long): Long = JSON.toLong(opt(index)) ?: fallback

    fun getString(index: Int): String {
        val objet = get(index)
        return JSON.toString(objet) ?: throw JSON.typeMismatch(index, objet, "String")
    }

    fun optString(index: Int): String = optString(index, "")

    fun optString(index: Int, fallback: String): String = JSON.toString(opt(index)) ?: fallback

    fun getJSONArray(index: Int): JSONArray {
        val objet = get(index)
        return objet as? JSONArray ?: throw JSON.typeMismatch(index, objet, "JSONArray")
    }

    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray

    fun getJSONObject(index: Int): JSONObject {
        val objet = get(index)
        return objet as? JSONObject ?: throw JSON.typeMismatch(index, objet, "JSONObject")
    }

    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject

    fun toJSONObject(names: JSONArray): JSONObject? {
        val result = JSONObject()
        val length = min(names.length(), values.size)
        if (length == 0) {
            return null
        }
        for (i in 0 until length) {
            val name = JSON.toString(names.opt(i))
            val value = opt(i)
            if (name != null) {
                result.put(name, value)
            } else if (value != null) {
                throw JSONException("Names must be non-null")
            }
        }
        return result
    }

    fun join(separator: String): String {
        val stringer = JSONStringer()
        stringer.open(JSONStringer.Scope.NULL, "")
        for (i in values.indices) {
            if (i > 0) {
                stringer.out.append(separator)
            }
            stringer.value(values[i])
        }
        stringer.close(JSONStringer.Scope.NULL, JSONStringer.Scope.NULL, "")
        return stringer.out.toString()
    }

    override fun toString(): String = try {
        val stringer = JSONStringer()
        writeTo(stringer)
        stringer.toString()
    } catch (e: JSONException) {
        "null"
    }

    fun toString(indentSpaces: Int): String {
        val stringer = JSONStringer(indentSpaces)
        writeTo(stringer)
        return stringer.toString()
    }

    internal fun writeTo(stringer: JSONStringer) {
        stringer.array()
        for (value in values) {
            stringer.value(value)
        }
        stringer.endArray()
    }

    override fun equals(other: Any?): Boolean = other is JSONArray && other.values == values

    override fun hashCode(): Int = values.hashCode()
}
