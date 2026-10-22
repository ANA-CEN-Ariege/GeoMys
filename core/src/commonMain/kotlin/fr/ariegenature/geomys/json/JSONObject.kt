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
 * depuis les sources du SDK Android (android-36.1, org/json/JSONObject.java). Texte de la licence :
 * licences/Apache-2.0.txt.
 */

package fr.ariegenature.geomys.json

/** Portage d'`org.json.JSONObject` d'Android, à l'identique : le code réseau de GeoMys-Android
 *  s'y porte en ne changeant que les imports, avec les mêmes conversions implicites (voir [JSON]),
 *  le même ordre des clés (celui d'insertion) et la même écriture (`/` échappé en `\/`, nombres
 *  entiers sans `.0`).
 *
 *  Écarts imposés par Kotlin :
 *  - [toString] rend "null", et non `null`, dans le seul cas où l'objet contient un nombre interdit
 *    (NaN, infini) placé sans contrôle ;
 *  - [wrap] ne sait pas convertir les classes `java.*` (BigDecimal, Date…), absentes du code commun. */
class JSONObject {

    private val nameValuePairs: LinkedHashMap<String, Any?>

    /** Un objet vide. */
    constructor() {
        nameValuePairs = LinkedHashMap()
    }

    /** Copie de [copyFrom] ; chaque valeur passe par [wrap]. */
    constructor(copyFrom: Map<*, *>) : this() {
        for ((key, value) in copyFrom) {
            if (key == null) {
                throw NullPointerException("key == null")
            }
            nameValuePairs[key as String] = wrap(value)
        }
    }

    /** Lit le prochain objet de [readFrom]. */
    constructor(readFrom: JSONTokener) {
        val objet = readFrom.nextValue()
        if (objet is JSONObject) {
            nameValuePairs = objet.nameValuePairs
        } else {
            throw JSON.typeMismatch(objet, "JSONObject")
        }
    }

    /** Lit le texte [json], avec la tolérance de [JSONTokener]. */
    constructor(json: String) : this(JSONTokener(json))

    /** Copie des seules clés [names] de [copyFrom]. */
    constructor(copyFrom: JSONObject, names: Array<String>) : this() {
        for (name in names) {
            val value = copyFrom.opt(name)
            if (value != null) {
                nameValuePairs[name] = value
            }
        }
    }

    fun length(): Int = nameValuePairs.size

    fun put(name: String, value: Boolean): JSONObject {
        nameValuePairs[name] = value
        return this
    }

    fun put(name: String, value: Double): JSONObject {
        nameValuePairs[name] = JSON.checkDouble(value)
        return this
    }

    fun put(name: String, value: Int): JSONObject {
        nameValuePairs[name] = value
        return this
    }

    fun put(name: String, value: Long): JSONObject {
        nameValuePairs[name] = value
        return this
    }

    /** Une valeur `null` retire la clé. */
    fun put(name: String, value: Any?): JSONObject {
        if (value == null) {
            nameValuePairs.remove(name)
            return this
        }
        if (value is Number) {
            JSON.checkDouble(value.toDouble())
        }
        nameValuePairs[name] = value
        return this
    }

    fun putOpt(name: String?, value: Any?): JSONObject {
        if (name == null || value == null) {
            return this
        }
        return put(name, value)
    }

    fun accumulate(name: String, value: Any?): JSONObject {
        val current = nameValuePairs[name] ?: return put(name, value)
        if (current is JSONArray) {
            current.checkedPut(value)
        } else {
            val array = JSONArray()
            array.checkedPut(current)
            array.checkedPut(value)
            nameValuePairs[name] = array
        }
        return this
    }

    fun append(name: String, value: Any?): JSONObject {
        val current = nameValuePairs[name]
        val array: JSONArray
        if (current is JSONArray) {
            array = current
        } else if (current == null) {
            val newArray = JSONArray()
            nameValuePairs[name] = newArray
            array = newArray
        } else {
            throw JSONException("Key $name is not a JSONArray")
        }
        array.checkedPut(value)
        return this
    }

    fun remove(name: String?): Any? = if (name == null) null else nameValuePairs.remove(name)

    /** Vrai si la clé manque ou vaut [NULL]. */
    fun isNull(name: String?): Boolean {
        val value = if (name == null) null else nameValuePairs[name]
        return value == null || value === NULL
    }

    fun has(name: String?): Boolean = name != null && nameValuePairs.containsKey(name)

    operator fun get(name: String): Any =
        nameValuePairs[name] ?: throw JSONException("No value for $name")

    fun opt(name: String?): Any? = if (name == null) null else nameValuePairs[name]

    fun getBoolean(name: String): Boolean {
        val objet = get(name)
        return JSON.toBoolean(objet) ?: throw JSON.typeMismatch(name, objet, "boolean")
    }

    fun optBoolean(name: String?): Boolean = optBoolean(name, false)

    fun optBoolean(name: String?, fallback: Boolean): Boolean = JSON.toBoolean(opt(name)) ?: fallback

    fun getDouble(name: String): Double {
        val objet = get(name)
        return JSON.toDouble(objet) ?: throw JSON.typeMismatch(name, objet, "double")
    }

    fun optDouble(name: String?): Double = optDouble(name, Double.NaN)

    fun optDouble(name: String?, fallback: Double): Double = JSON.toDouble(opt(name)) ?: fallback

    fun getInt(name: String): Int {
        val objet = get(name)
        return JSON.toInteger(objet) ?: throw JSON.typeMismatch(name, objet, "int")
    }

    fun optInt(name: String?): Int = optInt(name, 0)

    fun optInt(name: String?, fallback: Int): Int = JSON.toInteger(opt(name)) ?: fallback

    fun getLong(name: String): Long {
        val objet = get(name)
        return JSON.toLong(objet) ?: throw JSON.typeMismatch(name, objet, "long")
    }

    fun optLong(name: String?): Long = optLong(name, 0L)

    fun optLong(name: String?, fallback: Long): Long = JSON.toLong(opt(name)) ?: fallback

    /** Un nombre ou un booléen est rendu en texte ; [NULL] donne "null". */
    fun getString(name: String): String {
        val objet = get(name)
        return JSON.toString(objet) ?: throw JSON.typeMismatch(name, objet, "String")
    }

    fun optString(name: String?): String = optString(name, "")

    /** [fallback] seulement si la clé manque : un null explicite donne "null". */
    fun optString(name: String?, fallback: String): String = JSON.toString(opt(name)) ?: fallback

    fun getJSONArray(name: String): JSONArray {
        val objet = get(name)
        return objet as? JSONArray ?: throw JSON.typeMismatch(name, objet, "JSONArray")
    }

    fun optJSONArray(name: String?): JSONArray? = opt(name) as? JSONArray

    fun getJSONObject(name: String): JSONObject {
        val objet = get(name)
        return objet as? JSONObject ?: throw JSON.typeMismatch(name, objet, "JSONObject")
    }

    fun optJSONObject(name: String?): JSONObject? = opt(name) as? JSONObject

    fun toJSONArray(names: JSONArray?): JSONArray? {
        val result = JSONArray()
        if (names == null) {
            return null
        }
        val length = names.length()
        if (length == 0) {
            return null
        }
        for (i in 0 until length) {
            val name = JSON.toString(names.opt(i))
            result.put(opt(name))
        }
        return result
    }

    /** Les clés, dans l'ordre d'insertion. */
    fun keys(): MutableIterator<String> = nameValuePairs.keys.iterator()

    fun names(): JSONArray? =
        if (nameValuePairs.isEmpty()) null else JSONArray(ArrayList<String>(nameValuePairs.keys))

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
        stringer.`object`()
        for ((key, value) in nameValuePairs) {
            stringer.key(key).value(value)
        }
        stringer.endObject()
    }

    companion object {

        /** La valeur JSON `null`, distincte du `null` de Kotlin (clé absente). Elle se déclare
         *  égale à `null`, comme sous Android. */
        val NULL: Any = object {
            override fun equals(other: Any?): Boolean = other === this || other == null

            override fun hashCode(): Int = 0

            override fun toString(): String = "null"
        }

        private val ZERO_NEGATIF = (-0.0).toRawBits()

        /** Écrit [number] comme Android : sans `.0` s'il est entier, "-0" pour le zéro négatif. */
        fun numberToString(number: Number): String {
            val doubleValue = number.toDouble()
            JSON.checkDouble(doubleValue)
            if (number is Double && number.toRawBits() == ZERO_NEGATIF) {
                return "-0"
            }
            val longValue = number.toLong()
            if (doubleValue == longValue.toDouble()) {
                return longValue.toString()
            }
            return number.toString()
        }

        /** [data] entre guillemets, échappé comme le fait Android (`/` compris). */
        fun quote(data: String?): String {
            if (data == null) {
                return "\"\""
            }
            val stringer = JSONStringer()
            stringer.open(JSONStringer.Scope.NULL, "")
            stringer.value(data)
            stringer.close(JSONStringer.Scope.NULL, JSONStringer.Scope.NULL, "")
            return stringer.toString()
        }

        /** Convertit [o] en valeur JSON : collections, tableaux et maps deviennent des
         *  [JSONArray] et des [JSONObject], `null` devient [NULL] ; rend `null` pour un type inconnu. */
        fun wrap(o: Any?): Any? {
            if (o == null) {
                return NULL
            }
            if (o is JSONArray || o is JSONObject) {
                return o
            }
            if (o == NULL) {
                return o
            }
            try {
                when (o) {
                    is Collection<*> -> return JSONArray(o)
                    is Array<*>, is BooleanArray, is ByteArray, is CharArray, is DoubleArray,
                    is FloatArray, is IntArray, is LongArray, is ShortArray -> return JSONArray(o)
                    is Map<*, *> -> return JSONObject(o)
                    is Boolean, is Byte, is Char, is Double, is Float, is Int, is Long, is Short, is String -> return o
                }
            } catch (ignored: Exception) {
            }
            return null
        }
    }
}
