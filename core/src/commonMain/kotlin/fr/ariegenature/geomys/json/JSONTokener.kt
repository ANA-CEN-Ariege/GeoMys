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
 * depuis les sources du SDK Android (android-36.1, org/json/JSONTokener.java). Texte de la licence :
 * licences/Apache-2.0.txt.
 */

package fr.ariegenature.geomys.json

/** Portage d'`org.json.JSONTokener` d'Android : la lecture tolérante qu'Android applique aux
 *  réponses des serveurs. Elle accepte, en plus du JSON strict, les commentaires (`//`, `/* */`,
 *  `#`), les chaînes entre apostrophes, les clés et valeurs sans guillemets, `=` ou `=>` pour `:`,
 *  `;` pour `,`, et une virgule en trop dans un tableau (qui ajoute un `null`). Les nombres entiers
 *  deviennent des `Int`, ou des `Long` s'ils dépassent ; les autres des `Double`. Un entier qui
 *  commence par 0 est lu en octal, et par 0x en hexadécimal. */
class JSONTokener(input: String) {

    /** Le texte lu, sans son éventuelle marque d'ordre des octets. */
    private val entree: String = if (input.startsWith("﻿")) input.substring(1) else input

    /** L'indice du prochain caractère à lire. */
    private var pos = 0

    /** Lit la prochaine valeur : [JSONObject], [JSONArray], String, Boolean, Int, Long, Double ou
     *  [JSONObject.NULL]. */
    fun nextValue(): Any {
        val c = nextCleanInternal()
        when (c) {
            -1 -> throw syntaxError("End of input")
            '{'.code -> return readObject()
            '['.code -> return readArray()
            '\''.code, '"'.code -> return nextString(c.toChar())
            else -> {
                pos--
                return readLiteral()
            }
        }
    }

    private fun nextCleanInternal(): Int {
        val inLength = entree.length
        while (pos < inLength) {
            val c = entree[pos++].code
            when (c) {
                '\t'.code, ' '.code, '\n'.code, '\r'.code -> continue
                '/'.code -> {
                    if (pos == inLength) {
                        return c
                    }
                    when (entree[pos]) {
                        '*' -> {
                            pos++
                            val commentEnd = entree.indexOf("*/", pos)
                            if (commentEnd == -1) {
                                throw syntaxError("Unterminated comment")
                            }
                            pos = commentEnd + 2
                            continue
                        }
                        '/' -> {
                            pos++
                            skipToEndOfLine()
                            continue
                        }
                        else -> return c
                    }
                }
                '#'.code -> {
                    skipToEndOfLine()
                    continue
                }
                else -> return c
            }
        }
        return -1
    }

    private fun skipToEndOfLine() {
        val inLength = entree.length
        while (pos < inLength) {
            val c = entree[pos]
            if (c == '\r' || c == '\n') {
                pos++
                break
            }
            pos++
        }
    }

    /** Lit une chaîne jusqu'au guillemet [quote], déjà ouvert, en traitant les échappements. */
    fun nextString(quote: Char): String {
        var builder: StringBuilder? = null
        var start = pos
        val inLength = entree.length
        while (pos < inLength) {
            val c = entree[pos++]
            if (c == quote) {
                if (builder == null) {
                    return entree.substring(start, pos - 1)
                } else {
                    builder.appendRange(entree, start, pos - 1)
                    return builder.toString()
                }
            }
            if (c == '\\') {
                if (pos == inLength) {
                    throw syntaxError("Unterminated escape sequence")
                }
                if (builder == null) {
                    builder = StringBuilder()
                }
                builder.appendRange(entree, start, pos - 1)
                builder.append(readEscapeCharacter())
                start = pos
            }
        }
        throw syntaxError("Unterminated string")
    }

    private fun readEscapeCharacter(): Char {
        val escaped = entree[pos++]
        when (escaped) {
            'u' -> {
                if (pos + 4 > entree.length) {
                    throw syntaxError("Unterminated escape sequence")
                }
                val hex = entree.substring(pos, pos + 4)
                pos += 4
                val code = hex.toIntOrNull(16) ?: throw syntaxError("Invalid escape sequence: $hex")
                return code.toChar()
            }
            't' -> return '\t'
            'b' -> return '\b'
            'n' -> return '\n'
            'r' -> return '\r'
            'f' -> return '\u000c'
            else -> return escaped
        }
    }

    private fun readLiteral(): Any {
        val literal = nextToInternal("{}[]/\\:,=;# \t\u000c")
        if (literal.isEmpty()) {
            throw syntaxError("Expected literal value")
        } else if ("null".equals(literal, ignoreCase = true)) {
            return JSONObject.NULL
        } else if ("true".equals(literal, ignoreCase = true)) {
            return true
        } else if ("false".equals(literal, ignoreCase = true)) {
            return false
        }

        // Un entier, Int s'il tient, Long sinon. Hors de Long, ou en notation exponentielle, on
        // passe aux nombres à virgule.
        if (literal.indexOf('.') == -1) {
            var base = 10
            var number = literal
            if (number.startsWith("0x") || number.startsWith("0X")) {
                number = number.substring(2)
                base = 16
            } else if (number.startsWith("0") && number.length > 1) {
                number = number.substring(1)
                base = 8
            }
            val longValue = number.toLongOrNull(base)
            if (longValue != null) {
                return if (longValue <= Int.MAX_VALUE && longValue >= Int.MIN_VALUE) longValue.toInt() else longValue
            }
        }

        // Puis un nombre à virgule ; à défaut, une chaîne sans guillemets.
        return literal.toDoubleOrNull() ?: literal
    }

    private fun nextToInternal(excluded: String): String {
        val inLength = entree.length
        val start = pos
        while (pos < inLength) {
            val c = entree[pos]
            if (c == '\r' || c == '\n' || excluded.indexOf(c) != -1) {
                return entree.substring(start, pos)
            }
            pos++
        }
        return entree.substring(start)
    }

    private fun readObject(): JSONObject {
        val result = JSONObject()

        // Accolade fermante tout de suite : objet vide.
        val first = nextCleanInternal()
        if (first == '}'.code) {
            return result
        } else if (first != -1) {
            pos--
        }

        val inLength = entree.length
        while (true) {
            val name = nextValue()
            if (name !is String) {
                throw syntaxError("Names must be strings, but $name is of type ${JSON.nomDeType(name)}")
            }

            // `:`, mais aussi `=` ou `=>`.
            val separator = nextCleanInternal()
            if (separator != ':'.code && separator != '='.code) {
                throw syntaxError("Expected ':' after $name")
            }
            if (pos < inLength && entree[pos] == '>') {
                pos++
            }

            result.put(name, nextValue())

            when (nextCleanInternal()) {
                '}'.code -> return result
                ';'.code, ','.code -> continue
                else -> throw syntaxError("Unterminated object")
            }
        }
    }

    private fun readArray(): JSONArray {
        val result = JSONArray()

        // Pour qu'une virgule finale ajoute un null.
        var hasTrailingSeparator = false

        while (true) {
            when (nextCleanInternal()) {
                -1 -> throw syntaxError("Unterminated array")
                ']'.code -> {
                    if (hasTrailingSeparator) {
                        result.put(null)
                    }
                    return result
                }
                ','.code, ';'.code -> {
                    // Une virgule sans valeur avant elle : un null.
                    result.put(null)
                    hasTrailingSeparator = true
                    continue
                }
                else -> pos--
            }

            result.put(nextValue())

            when (nextCleanInternal()) {
                ']'.code -> return result
                ','.code, ';'.code -> {
                    hasTrailingSeparator = true
                    continue
                }
                else -> throw syntaxError("Unterminated array")
            }
        }
    }

    /** Une erreur de syntaxe, qui situe la position dans le texte. */
    fun syntaxError(message: String): JSONException = JSONException(message + this)

    override fun toString(): String = " at character $pos of $entree"

    fun more(): Boolean = pos < entree.length

    fun next(): Char = if (pos < entree.length) entree[pos++] else '\u0000'

    fun next(c: Char): Char {
        val result = next()
        if (result != c) {
            throw syntaxError("Expected $c but was $result")
        }
        return result
    }

    fun nextClean(): Char {
        val nextCleanInt = nextCleanInternal()
        return if (nextCleanInt == -1) '\u0000' else nextCleanInt.toChar()
    }

    fun next(length: Int): String {
        if (pos + length > entree.length) {
            throw syntaxError("$length is out of bounds")
        }
        val result = entree.substring(pos, pos + length)
        pos += length
        return result
    }

    // `trim()` de Java retire tout caractère ≤ U+0020, celui de Kotlin les blancs Unicode.
    fun nextTo(excluded: String): String = nextToInternal(excluded).trim { it <= ' ' }

    fun nextTo(excluded: Char): String = nextToInternal(excluded.toString()).trim { it <= ' ' }

    fun skipPast(thru: String) {
        val thruStart = entree.indexOf(thru, pos)
        pos = if (thruStart == -1) entree.length else thruStart + thru.length
    }

    fun skipTo(to: Char): Char {
        val index = entree.indexOf(to, pos)
        if (index != -1) {
            pos = index
            return to
        } else {
            return '\u0000'
        }
    }

    fun back() {
        if (--pos == -1) {
            pos = 0
        }
    }

    companion object {
        fun dehexchar(hex: Char): Int = when (hex) {
            in '0'..'9' -> hex - '0'
            in 'A'..'F' -> hex - 'A' + 10
            in 'a'..'f' -> hex - 'a' + 10
            else -> -1
        }
    }
}
