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
 * depuis les sources du SDK Android (android-36.1, org/json/JSONStringer.java). Texte de la
 * licence : licences/Apache-2.0.txt.
 */

package fr.ariegenature.geomys.json

/** Portage d'`org.json.JSONStringer` d'Android : l'écriture du JSON. Elle échappe `"`, `\` et `/`,
 *  écrit les caractères de contrôle en `\u00xx` minuscule, et ne touche pas au reste (accents,
 *  U+2028…). Seul écart : [toString] rend une chaîne vide, et non `null`, si rien n'a été écrit. */
class JSONStringer internal constructor(
    /** L'indentation par niveau, ou `null` pour tout écrire sur une ligne. */
    private val indent: String?,
) {

    /** Le texte produit. */
    internal val out = StringBuilder()

    /** L'état de chaque niveau ouvert, pour placer virgules et deux-points. */
    internal enum class Scope {
        EMPTY_ARRAY,
        NONEMPTY_ARRAY,
        EMPTY_OBJECT,
        DANGLING_KEY,
        NONEMPTY_OBJECT,

        /** Écriture d'une valeur seule, hors objet et tableau ([JSONObject.quote]). */
        NULL,
    }

    private val stack = ArrayList<Scope>()

    constructor() : this(null)

    internal constructor(indentSpaces: Int) : this(" ".repeat(indentSpaces))

    fun array(): JSONStringer = open(Scope.EMPTY_ARRAY, "[")

    fun endArray(): JSONStringer = close(Scope.EMPTY_ARRAY, Scope.NONEMPTY_ARRAY, "]")

    fun `object`(): JSONStringer = open(Scope.EMPTY_OBJECT, "{")

    fun endObject(): JSONStringer = close(Scope.EMPTY_OBJECT, Scope.NONEMPTY_OBJECT, "}")

    internal fun open(empty: Scope, openBracket: String): JSONStringer {
        if (stack.isEmpty() && out.isNotEmpty()) {
            throw JSONException("Nesting problem: multiple top-level roots")
        }
        beforeValue()
        stack.add(empty)
        out.append(openBracket)
        return this
    }

    internal fun close(empty: Scope, nonempty: Scope, closeBracket: String): JSONStringer {
        val context = peek()
        if (context != nonempty && context != empty) {
            throw JSONException("Nesting problem")
        }
        stack.removeAt(stack.size - 1)
        if (context == nonempty) {
            newline()
        }
        out.append(closeBracket)
        return this
    }

    private fun peek(): Scope {
        if (stack.isEmpty()) {
            throw JSONException("Nesting problem")
        }
        return stack[stack.size - 1]
    }

    private fun replaceTop(topOfStack: Scope) {
        stack[stack.size - 1] = topOfStack
    }

    /** Écrit [value] : objet, tableau, chaîne, nombre, booléen, `null` ou [JSONObject.NULL]. */
    fun value(value: Any?): JSONStringer {
        if (stack.isEmpty()) {
            throw JSONException("Nesting problem")
        }
        if (value is JSONArray) {
            value.writeTo(this)
            return this
        } else if (value is JSONObject) {
            value.writeTo(this)
            return this
        }
        beforeValue()
        if (value == null || value is Boolean || value === JSONObject.NULL) {
            out.append(value)
        } else if (value is Number) {
            out.append(JSONObject.numberToString(value))
        } else {
            string(value.toString())
        }
        return this
    }

    fun value(value: Boolean): JSONStringer {
        if (stack.isEmpty()) {
            throw JSONException("Nesting problem")
        }
        beforeValue()
        out.append(value)
        return this
    }

    fun value(value: Double): JSONStringer {
        if (stack.isEmpty()) {
            throw JSONException("Nesting problem")
        }
        beforeValue()
        out.append(JSONObject.numberToString(value))
        return this
    }

    fun value(value: Long): JSONStringer {
        if (stack.isEmpty()) {
            throw JSONException("Nesting problem")
        }
        beforeValue()
        out.append(value)
        return this
    }

    private fun string(value: String) {
        out.append("\"")
        for (c in value) {
            when (c) {
                '"', '\\', '/' -> out.append('\\').append(c)
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\u000c' -> out.append("\\f")
                else -> if (c.code <= 0x1F) {
                    out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(c)
                }
            }
        }
        out.append("\"")
    }

    private fun newline() {
        if (indent == null) {
            return
        }
        out.append("\n")
        repeat(stack.size) { out.append(indent) }
    }

    fun key(name: String?): JSONStringer {
        if (name == null) {
            throw JSONException("Names must be non-null")
        }
        beforeKey()
        string(name)
        return this
    }

    private fun beforeKey() {
        val context = peek()
        if (context == Scope.NONEMPTY_OBJECT) { // pas la première clé de l'objet
            out.append(',')
        } else if (context != Scope.EMPTY_OBJECT) { // hors d'un objet
            throw JSONException("Nesting problem")
        }
        newline()
        replaceTop(Scope.DANGLING_KEY)
    }

    private fun beforeValue() {
        if (stack.isEmpty()) {
            return
        }
        val context = peek()
        if (context == Scope.EMPTY_ARRAY) { // première valeur du tableau
            replaceTop(Scope.NONEMPTY_ARRAY)
            newline()
        } else if (context == Scope.NONEMPTY_ARRAY) { // valeur suivante du tableau
            out.append(',')
            newline()
        } else if (context == Scope.DANGLING_KEY) { // valeur d'une clé
            out.append(if (indent == null) ":" else ": ")
            replaceTop(Scope.NONEMPTY_OBJECT)
        } else if (context != Scope.NULL) {
            throw JSONException("Nesting problem")
        }
    }

    override fun toString(): String = out.toString()
}
