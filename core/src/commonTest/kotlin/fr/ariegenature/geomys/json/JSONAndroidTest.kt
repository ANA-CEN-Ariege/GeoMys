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

// Fichier produit par outils/reference-org-json/generer.sh : ne pas le modifier à la main.

package fr.ariegenature.geomys.json

import kotlin.test.Test
import kotlin.test.assertTrue

/** Le portage d'org.json doit rendre exactement ce que rend l'org.json d'Android (sources du SDK
 *  android-36.1, exécutées sur la JVM), sur chacun des cas de outils/reference-org-json/cas.tsv. */
class JSONAndroidTest {

    @Test
    fun memesResultatsQueLOrgJsonDAndroid() {
        val ecarts = CAS.mapNotNull { (cas, attendu) ->
            val obtenu = executerCas(cas)
            if (obtenu == attendu) null else "${cas.replace('\t', '│')}\n    attendu : $attendu\n    obtenu  : $obtenu"
        }
        assertTrue(ecarts.isEmpty(), "${ecarts.size} écarts sur ${CAS.size} cas :\n" + ecarts.joinToString("\n"))
    }
}

/** Interprète un cas comme Harnais.java. */
private fun executerCas(ligne: String): String {
    val resultat = try {
        executer(ligne.split("\t"))
    } catch (e: JSONException) {
        "!JSONException: ${e.message}"
    } catch (e: Exception) {
        "!${e::class.simpleName}"
    }
    return encoder(resultat)
}

private fun decoder(s: String): String = s.replace("⏎", "\n").replace("⇥", "\t").replace("␍", "\r")
    .replace("␈", "\b").replace("␌", "\u000c").replace("␁", "\u0001").replace("␟", "\u001f")
    .replace("⟪BOM⟫", "﻿")

private fun encoder(s: String): String = buildString {
    for (c in s) {
        when (c) {
            '\n' -> append("⏎")
            '\t' -> append("⇥")
            '\r' -> append("␍")
            '\b' -> append("␈")
            '\u000c' -> append("␌")
            '\u0001' -> append("␁")
            '\u001f' -> append("␟")
            '﻿' -> append("⟪BOM⟫")
            ' ' -> append("⟪LS⟫")
            '\u007f' -> append("⟪DEL⟫")
            '￿' -> append("⟪FFFF⟫")
            else -> if (c.code < 0x20) append("⟪").append(c.code.toString(16)).append("⟫") else append(c)
        }
    }
}

private fun fmt(v: Any?): String = when {
    v == null -> "∅"
    v === JSONObject.NULL -> "NULL"
    v is Int -> "Int:$v"
    v is Long -> "Long:$v"
    v is Double -> "Double:$v"
    v is Boolean -> "Boolean:$v"
    v is String -> "String:$v"
    v is JSONObject -> "JSONObject:$v"
    v is JSONArray -> "JSONArray:$v"
    else -> "?${v::class.simpleName}:$v"
}

private fun executer(f: List<String>): String {
    when (f[0]) {
        "analyser" -> return fmt(JSONTokener(decoder(f[1])).nextValue())
        "objet" -> {
            val o = JSONObject(decoder(f[1]))
            val k = f[3]
            val d = f.getOrNull(4)
            when (f[2]) {
                "optString" -> return if (d == null) o.optString(k) else o.optString(k, d)
                "optInt" -> return (if (d == null) o.optInt(k) else o.optInt(k, d.toInt())).toString()
                "optLong" -> return (if (d == null) o.optLong(k) else o.optLong(k, d.toLong())).toString()
                "optDouble" -> return (if (d == null) o.optDouble(k) else o.optDouble(k, d.toDouble())).toString()
                "optBoolean" -> return (if (d == null) o.optBoolean(k) else o.optBoolean(k, d.equals("true", ignoreCase = true))).toString()
                "getString" -> return o.getString(k)
                "getInt" -> return o.getInt(k).toString()
                "getLong" -> return o.getLong(k).toString()
                "getDouble" -> return o.getDouble(k).toString()
                "getBoolean" -> return o.getBoolean(k).toString()
                "getJSONObject" -> return fmt(o.getJSONObject(k))
                "getJSONArray" -> return fmt(o.getJSONArray(k))
                "isNull" -> return o.isNull(k).toString()
                "has" -> return o.has(k).toString()
                "opt" -> return fmt(o.opt(k))
                "get" -> return fmt(o.get(k))
                "optJSONObject" -> return fmt(o.optJSONObject(k))
                "optJSONArray" -> return fmt(o.optJSONArray(k))
            }
        }
        "tableau" -> {
            val a = JSONArray(decoder(f[1]))
            val i = f[3].toInt()
            val d = f.getOrNull(4)
            when (f[2]) {
                "length" -> return a.length().toString()
                "optString" -> return if (d == null) a.optString(i) else a.optString(i, d)
                "optInt" -> return (if (d == null) a.optInt(i) else a.optInt(i, d.toInt())).toString()
                "optLong" -> return a.optLong(i).toString()
                "optDouble" -> return (if (d == null) a.optDouble(i) else a.optDouble(i, d.toDouble())).toString()
                "optBoolean" -> return (if (d == null) a.optBoolean(i) else a.optBoolean(i, d.equals("true", ignoreCase = true))).toString()
                "isNull" -> return a.isNull(i).toString()
                "get" -> return fmt(a.get(i))
                "opt" -> return fmt(a.opt(i))
                "getString" -> return a.getString(i)
                "getInt" -> return a.getInt(i).toString()
                "getDouble" -> return a.getDouble(i).toString()
                "getBoolean" -> return a.getBoolean(i).toString()
                "getJSONObject" -> return fmt(a.getJSONObject(i))
                "getJSONArray" -> return fmt(a.getJSONArray(i))
                "optJSONObject" -> return fmt(a.optJSONObject(i))
                "optJSONArray" -> return fmt(a.optJSONArray(i))
                "remove" -> {
                    val r = a.remove(i)
                    return "${fmt(r)} $a"
                }
            }
        }
        "ecrire" -> return JSONTokener(decoder(f[1])).nextValue().toString()
        "ecrire-indente" -> {
            val v = JSONTokener(decoder(f[1])).nextValue()
            val n = f[2].toInt()
            return if (v is JSONObject) v.toString(n) else (v as JSONArray).toString(n)
        }
        "quote" -> return JSONObject.quote(if (f[1] == "⟪NULL⟫") null else decoder(f[1]))
        "nombre" -> {
            val n: Number = when (f[1]) {
                "int" -> f[2].toInt()
                "long" -> f[2].toLong()
                "float" -> f[2].toFloat()
                else -> f[2].toDouble()
            }
            return JSONObject.numberToString(n)
        }
        "mettre" -> {
            var o = JSONObject()
            when (f[1]) {
                "int" -> o.put("k", f[2].toInt())
                "long" -> o.put("k", f[2].toLong())
                "double" -> o.put("k", f[2].toDouble())
                "boolean" -> o.put("k", f[2].equals("true", ignoreCase = true))
                "chaine" -> o.put("k", f[2])
                "float" -> o.put("k", f[2].toFloat())
                "nul" -> {
                    o = JSONObject("{\"k\":1}")
                    o.put("k", null)
                }
            }
            return o.toString()
        }
        "mettre-indice" -> {
            val a = JSONArray()
            a.put(f[1].toInt(), f[2].toInt())
            return a.toString()
        }
        "cumuler" -> {
            val o = JSONObject(f[1])
            o.accumulate(f[2], f[3].toInt())
            return o.toString()
        }
        "ajouter" -> {
            val o = JSONObject(f[1])
            o.append(f[2], f[3].toInt())
            return o.toString()
        }
        "noms" -> return JSONObject(f[1]).names()?.toString() ?: "∅"
        "cles" -> return JSONObject(f[1]).keys().asSequence().joinToString(",")
        "joindre" -> return JSONArray(f[1]).join(f[2])
        "vers-tableau" -> return JSONObject(f[1]).toJSONArray(JSONArray(f[2]))?.toString() ?: "∅"
        "vers-objet" -> return JSONArray(f[1]).toJSONObject(JSONArray(f[2]))?.toString() ?: "∅"
    }
    throw IllegalArgumentException("cas inconnu : " + f.joinToString(" | "))
}

private val CAS: List<Pair<String, String>> = listOf(
    "analyser\t1" to "Int:1",
    "analyser\t-1" to "Int:-1",
    "analyser\t+1" to "Int:1",
    "analyser\t2147483647" to "Int:2147483647",
    "analyser\t2147483648" to "Long:2147483648",
    "analyser\t-2147483648" to "Int:-2147483648",
    "analyser\t-2147483649" to "Long:-2147483649",
    "analyser\t9223372036854775807" to "Long:9223372036854775807",
    "analyser\t9223372036854775808" to "Double:9.223372036854776E18",
    "analyser\t-9223372036854775809" to "Double:-9.223372036854776E18",
    "analyser\t1.0" to "Double:1.0",
    "analyser\t1.5" to "Double:1.5",
    "analyser\t-1.5" to "Double:-1.5",
    "analyser\t1e3" to "Double:1000.0",
    "analyser\t1E3" to "Double:1000.0",
    "analyser\t1e-3" to "Double:0.001",
    "analyser\t-0" to "Int:0",
    "analyser\t-0.0" to "Double:-0.0",
    "analyser\t0" to "Int:0",
    "analyser\t00" to "Int:0",
    "analyser\t012" to "Int:10",
    "analyser\t-012" to "Int:-12",
    "analyser\t08" to "Double:8.0",
    "analyser\t0x1F" to "Int:31",
    "analyser\t0X1f" to "Int:31",
    "analyser\t0x" to "String:0x",
    "analyser\t-0x1F" to "String:-0x1F",
    "analyser\t.5" to "Double:0.5",
    "analyser\t5." to "Double:5.0",
    "analyser\t1e400" to "Double:Infinity",
    "analyser\t-1e400" to "Double:-Infinity",
    "analyser\tNaN" to "Double:NaN",
    "analyser\tInfinity" to "Double:Infinity",
    "analyser\t-Infinity" to "Double:-Infinity",
    "analyser\t1d" to "Double:1.0",
    "analyser\t1f" to "Double:1.0",
    "analyser\ttrue" to "Boolean:true",
    "analyser\tTRUE" to "Boolean:true",
    "analyser\tFalse" to "Boolean:false",
    "analyser\tnull" to "NULL",
    "analyser\tNULL" to "NULL",
    "analyser\tNull" to "NULL",
    "analyser\tabc" to "String:abc",
    "analyser\ta b" to "String:a",
    "analyser\t\"abc\"" to "String:abc",
    "analyser\t'abc'" to "String:abc",
    "analyser\t'a\"b'" to "String:a\"b",
    "analyser\t\"a'b\"" to "String:a'b",
    "analyser\t\"a\\\"b\"" to "String:a\"b",
    "analyser\t\"\\u00e9\"" to "String:é",
    "analyser\t\"\\u-001\"" to "String:⟪FFFF⟫",
    "analyser\t\"\\u+041\"" to "String:A",
    "analyser\t\"\\x\"" to "String:x",
    "analyser\t\"\\/\"" to "String:/",
    "analyser\t\"a⏎b\"" to "String:a⏎b",
    "analyser\t{}" to "JSONObject:{}",
    "analyser\t[]" to "JSONArray:[]",
    "analyser\t[1,]" to "JSONArray:[1,null]",
    "analyser\t[,]" to "JSONArray:[null,null]",
    "analyser\t[,1]" to "JSONArray:[null,1]",
    "analyser\t[1;2]" to "JSONArray:[1,2]",
    "analyser\t[1,,2]" to "JSONArray:[1,null,2]",
    "analyser\t{\"a\":1;\"b\":2}" to "JSONObject:{\"a\":1,\"b\":2}",
    "analyser\t{a:1}" to "JSONObject:{\"a\":1}",
    "analyser\t{\"a\"=1}" to "JSONObject:{\"a\":1}",
    "analyser\t{\"a\"=>1}" to "JSONObject:{\"a\":1}",
    "analyser\t{'a':'b'}" to "JSONObject:{\"a\":\"b\"}",
    "analyser\t{\"a\":1,}" to "!JSONException: Expected literal value at character 7 of {\"a\":1,}",
    "analyser\t{1:2}" to "!JSONException: Names must be strings, but 1 is of type java.lang.Integer at character 2 of {1:2}",
    "analyser\t{null:1}" to "!JSONException: Names must be strings, but null is of type org.json.JSONObject\$1 at character 5 of {null:1}",
    "analyser\t{true:1}" to "!JSONException: Names must be strings, but true is of type java.lang.Boolean at character 5 of {true:1}",
    "analyser\t// commentaire⏎{\"a\":1}" to "JSONObject:{\"a\":1}",
    "analyser\t/* c */ [1]" to "JSONArray:[1]",
    "analyser\t# c⏎[1]" to "JSONArray:[1]",
    "analyser\t[1] reste" to "JSONArray:[1]",
    "analyser\t⟪BOM⟫{\"a\":1}" to "JSONObject:{\"a\":1}",
    "analyser\t{\"a\":{\"b\":[1,{\"c\":null}]}}" to "JSONObject:{\"a\":{\"b\":[1,{\"c\":null}]}}",
    "analyser\t{\"a\":1,\"a\":2}" to "JSONObject:{\"a\":2}",
    "analyser\t  12  " to "Int:12",
    "analyser\t\"" to "!JSONException: Unterminated string at character 1 of \"",
    "analyser\t{" to "!JSONException: End of input at character 1 of {",
    "analyser\t[" to "!JSONException: Unterminated array at character 1 of [",
    "analyser\t{\"a\"}" to "!JSONException: Expected ':' after a at character 5 of {\"a\"}",
    "analyser\t{\"a\":1" to "!JSONException: Unterminated object at character 6 of {\"a\":1",
    "analyser\t\"abc" to "!JSONException: Unterminated string at character 4 of \"abc",
    "analyser\t\"\\u12\"" to "!JSONException: Unterminated escape sequence at character 3 of \"\\u12\"",
    "analyser\t\"\\u12zz\"" to "!JSONException: Invalid escape sequence: 12zz at character 7 of \"\\u12zz\"",
    "analyser\t/* abc" to "!JSONException: Unterminated comment at character 2 of /* abc",
    "analyser\t" to "!JSONException: End of input at character 0 of ",
    "analyser\t{\"a\" 1}" to "!JSONException: Expected ':' after a at character 6 of {\"a\" 1}",
    "analyser\t[1 2]" to "!JSONException: Unterminated array at character 4 of [1 2]",
    "analyser\t1.2.3" to "String:1.2.3",
    "analyser\t1e" to "String:1e",
    "analyser\t-" to "String:-",
    "analyser\t0.0000001" to "Double:1.0E-7",
    "analyser\t123456789.123" to "Double:1.23456789123E8",
    "analyser\t1e20" to "Double:1.0E20",
    "analyser\t1e7" to "Double:1.0E7",
    "analyser\t9999999" to "Int:9999999",
    "analyser\t1e-5" to "Double:1.0E-5",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\ts" to "x",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\tn" to "null",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\ti" to "12",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\tl" to "3000000000",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\td" to "12.7",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\te" to "1000.0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\tb" to "true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\to" to "{\"a\":1}",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\tt" to "[1,2]",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\tvide" to "",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\tneg" to "-0.0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\tgrand" to "1.0E20",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\tabsent" to "",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\ti" to "12",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\tl" to "-1294967296",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\td" to "12",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\te" to "1000",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\tns" to "12",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\ts" to "0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\tb" to "0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\tn" to "0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\tgrand" to "2147483647",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\tesp" to "12",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\tnb" to "-3",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\tneg" to "0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\tabsent" to "0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptLong\tl" to "3000000000",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptLong\tns" to "12",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptLong\tgrand" to "9223372036854775807",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptLong\td" to "12",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptLong\ts" to "0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptLong\tesp" to "12",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptLong\tabsent" to "0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\td" to "12.7",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\ti" to "12.0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\tl" to "3.0E9",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\te" to "1000.0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\ts" to "NaN",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\tn" to "NaN",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\tns" to "12.7",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\tneg" to "-0.0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\tesp" to "12.0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\tgrand" to "1.0E20",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\tabsent" to "NaN",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptBoolean\tb" to "true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptBoolean\tbs" to "true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptBoolean\tbf" to "false",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptBoolean\ts" to "false",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptBoolean\ti" to "false",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptBoolean\tn" to "false",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptBoolean\tabsent" to "false",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetString\ts" to "x",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetString\tn" to "null",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetString\ti" to "12",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetString\td" to "12.7",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetString\tb" to "true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetString\to" to "{\"a\":1}",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetString\tabsent" to "!JSONException: No value for absent",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetInt\ti" to "12",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetInt\tns" to "12",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetInt\ts" to "!JSONException: Value x at s of type java.lang.String cannot be converted to int",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetInt\tn" to "!JSONException: Value null at n of type org.json.JSONObject\$1 cannot be converted to int",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetInt\tb" to "!JSONException: Value true at b of type java.lang.Boolean cannot be converted to int",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetInt\to" to "!JSONException: Value {\"a\":1} at o of type org.json.JSONObject cannot be converted to int",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetInt\tabsent" to "!JSONException: No value for absent",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetLong\tl" to "3000000000",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetLong\ts" to "!JSONException: Value x at s of type java.lang.String cannot be converted to long",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetDouble\td" to "12.7",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetDouble\tns" to "12.7",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetDouble\ts" to "!JSONException: Value x at s of type java.lang.String cannot be converted to double",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetDouble\tn" to "!JSONException: Value null at n of type org.json.JSONObject\$1 cannot be converted to double",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetBoolean\tb" to "true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetBoolean\tbs" to "true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetBoolean\tbf" to "!JSONException: Value oui at bf of type java.lang.String cannot be converted to boolean",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetBoolean\ti" to "!JSONException: Value 12 at i of type java.lang.Integer cannot be converted to boolean",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetJSONObject\to" to "JSONObject:{\"a\":1}",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetJSONObject\tt" to "!JSONException: Value [1,2] at t of type org.json.JSONArray cannot be converted to JSONObject",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetJSONObject\ts" to "!JSONException: Value x at s of type java.lang.String cannot be converted to JSONObject",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetJSONObject\tn" to "!JSONException: Value null at n of type org.json.JSONObject\$1 cannot be converted to JSONObject",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetJSONObject\tabsent" to "!JSONException: No value for absent",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetJSONArray\tt" to "JSONArray:[1,2]",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetJSONArray\to" to "!JSONException: Value {\"a\":1} at o of type org.json.JSONObject cannot be converted to JSONArray",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tgetJSONArray\tabsent" to "!JSONException: No value for absent",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tisNull\tn" to "true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tisNull\ts" to "false",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tisNull\tabsent" to "true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\thas\tn" to "true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\thas\ts" to "true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\thas\tabsent" to "false",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\topt\ts" to "String:x",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\topt\tn" to "NULL",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\topt\ti" to "Int:12",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\topt\tl" to "Long:3000000000",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\topt\td" to "Double:12.7",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\topt\te" to "Double:1000.0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\topt\tb" to "Boolean:true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\topt\to" to "JSONObject:{\"a\":1}",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\topt\tt" to "JSONArray:[1,2]",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\topt\tneg" to "Double:-0.0",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\topt\tabsent" to "∅",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tget\ts" to "String:x",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tget\tn" to "NULL",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\tget\tabsent" to "!JSONException: No value for absent",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptJSONObject\to" to "JSONObject:{\"a\":1}",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptJSONObject\ts" to "∅",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptJSONObject\tn" to "∅",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptJSONObject\tabsent" to "∅",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptJSONArray\tt" to "JSONArray:[1,2]",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptJSONArray\to" to "∅",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptJSONArray\tabsent" to "∅",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\tabsent\tdef" to "def",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\tn\tdef" to "null",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptString\ts\tdef" to "x",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\tabsent\t7" to "7",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\ts\t7" to "7",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptInt\tn\t7" to "7",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptLong\tabsent\t7" to "7",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\tabsent\t7.5" to "7.5",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptDouble\ts\t7.5" to "7.5",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptBoolean\tabsent\ttrue" to "true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptBoolean\ts\ttrue" to "true",
    "objet\t{\"s\":\"x\",\"n\":null,\"i\":12,\"l\":3000000000,\"d\":12.7,\"e\":1e3,\"b\":true,\"bs\":\"TRUE\",\"ns\":\"12.7\",\"neg\":-0.0,\"o\":{\"a\":1},\"t\":[1,2],\"vide\":\"\",\"esp\":\" 12 \",\"grand\":1e20,\"bf\":\"oui\",\"nb\":\"-3\"}\toptBoolean\tbs\tfalse" to "true",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tlength\t0" to "9",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t0" to "1",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t1" to "2",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t2" to "null",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t3" to "true",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t4" to "{\"a\":1}",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t5" to "[3]",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t6" to "1.5",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t7" to "",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t8" to "x",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t9" to "",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t-1" to "",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptInt\t0" to "1",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptInt\t1" to "2",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptInt\t2" to "0",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptInt\t3" to "0",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptInt\t6" to "1",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptInt\t7" to "0",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptInt\t9" to "0",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptLong\t1" to "2",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptLong\t6" to "1",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptDouble\t0" to "1.0",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptDouble\t1" to "2.0",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptDouble\t6" to "1.5",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptDouble\t8" to "NaN",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptBoolean\t3" to "true",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptBoolean\t1" to "false",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptBoolean\t8" to "false",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tisNull\t2" to "true",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tisNull\t7" to "true",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tisNull\t0" to "false",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tisNull\t9" to "true",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tget\t0" to "Int:1",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tget\t2" to "NULL",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tget\t7" to "!JSONException: Value at 7 is null.",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tget\t9" to "!JSONException: Index 9 out of range [0..9)",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tget\t-1" to "!JSONException: Index -1 out of range [0..9)",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\topt\t0" to "Int:1",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\topt\t1" to "String:2",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\topt\t2" to "NULL",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\topt\t3" to "Boolean:true",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\topt\t4" to "JSONObject:{\"a\":1}",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\topt\t5" to "JSONArray:[3]",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\topt\t6" to "Double:1.5",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\topt\t7" to "∅",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\topt\t9" to "∅",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\topt\t-1" to "∅",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetString\t0" to "1",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetString\t2" to "null",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetString\t7" to "!JSONException: Value at 7 is null.",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetInt\t1" to "2",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetInt\t8" to "!JSONException: Value x at 8 of type java.lang.String cannot be converted to int",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetInt\t2" to "!JSONException: Value null at 2 of type org.json.JSONObject\$1 cannot be converted to int",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetDouble\t6" to "1.5",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetDouble\t8" to "!JSONException: Value x at 8 of type java.lang.String cannot be converted to double",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetBoolean\t3" to "true",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetBoolean\t0" to "!JSONException: Value 1 at 0 of type java.lang.Integer cannot be converted to boolean",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetJSONObject\t4" to "JSONObject:{\"a\":1}",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetJSONObject\t5" to "!JSONException: Value [3] at 5 of type org.json.JSONArray cannot be converted to JSONObject",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetJSONArray\t5" to "JSONArray:[3]",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tgetJSONArray\t4" to "!JSONException: Value {\"a\":1} at 4 of type org.json.JSONObject cannot be converted to JSONArray",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptJSONObject\t4" to "JSONObject:{\"a\":1}",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptJSONObject\t5" to "∅",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptJSONArray\t5" to "JSONArray:[3]",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptJSONArray\t4" to "∅",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tremove\t2" to "NULL [1,\"2\",true,{\"a\":1},[3],1.5,null,\"x\"]",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\tremove\t9" to "∅ [1,\"2\",null,true,{\"a\":1},[3],1.5,null,\"x\"]",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t9\tdef" to "def",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptString\t2\tdef" to "null",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptInt\t8\t7" to "7",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptDouble\t9\t7.5" to "7.5",
    "tableau\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]\toptBoolean\t9\ttrue" to "true",
    "ecrire\t{\"url\":\"http://x/y\",\"ctl\":\"a␁b\",\"tab\":\"a⇥b\",\"nl\":\"a⏎b\",\"cr\":\"a␍b\",\"bk\":\"a␈b\",\"ff\":\"a␌b\",\"us\":\"a␟b\",\"q\":\"\\\"\",\"bs\":\"\\\\\",\"uni\":\"é€\",\"ls\":\"\\u2028\",\"del\":\"\\u007f\"}" to "{\"url\":\"http:\\/\\/x\\/y\",\"ctl\":\"a\\u0001b\",\"tab\":\"a\\tb\",\"nl\":\"a\\nb\",\"cr\":\"a\\rb\",\"bk\":\"a\\bb\",\"ff\":\"a\\fb\",\"us\":\"a\\u001fb\",\"q\":\"\\\"\",\"bs\":\"\\\\\",\"uni\":\"é€\",\"ls\":\"⟪LS⟫\",\"del\":\"⟪DEL⟫\"}",
    "ecrire\t{\"a\":1.0,\"b\":-0.0,\"c\":1.5,\"d\":1e20,\"e\":100,\"f\":3000000000,\"g\":0.1,\"h\":1e-5,\"i\":1e7,\"j\":123456789.123,\"k\":-1.5e-7,\"l\":1e300,\"m\":9223372036854775807,\"n\":4.9e-324}" to "{\"a\":1,\"b\":-0,\"c\":1.5,\"d\":1.0E20,\"e\":100,\"f\":3000000000,\"g\":0.1,\"h\":1.0E-5,\"i\":10000000,\"j\":1.23456789123E8,\"k\":-1.5E-7,\"l\":1.0E300,\"m\":9223372036854775807,\"n\":4.9E-324}",
    "ecrire\t{\"a\":null,\"b\":true,\"c\":false,\"d\":[],\"e\":{},\"f\":[null,[]],\"g\":\"\",\"h\":\"null\"}" to "{\"a\":null,\"b\":true,\"c\":false,\"d\":[],\"e\":{},\"f\":[null,[]],\"g\":\"\",\"h\":\"null\"}",
    "ecrire\t[1,\"2\",null,true,{\"a\":1},[3],1.5,,\"x\"]" to "[1,\"2\",null,true,{\"a\":1},[3],1.5,null,\"x\"]",
    "ecrire\t[1,,2,]" to "[1,null,2,null]",
    "ecrire\t{\"z\":1,\"a\":2,\"m\":3}" to "{\"z\":1,\"a\":2,\"m\":3}",
    "ecrire-indente\t{\"a\":[1,{\"b\":2}],\"c\":{},\"d\":[]}\t2" to "{⏎  \"a\": [⏎    1,⏎    {⏎      \"b\": 2⏎    }⏎  ],⏎  \"c\": {},⏎  \"d\": []⏎}",
    "ecrire-indente\t[1,[2,[3]],{\"a\":{}}]\t4" to "[⏎    1,⏎    [⏎        2,⏎        [⏎            3⏎        ]⏎    ],⏎    {⏎        \"a\": {}⏎    }⏎]",
    "ecrire-indente\t{}\t2" to "{}",
    "ecrire-indente\t{\"a\":1}\t0" to "{⏎\"a\": 1⏎}",
    "quote\ta/b" to "\"a\\/b\"",
    "quote\ta\"b" to "\"a\\\"b\"",
    "quote\ta\\b" to "\"a\\\\b\"",
    "quote\t␁" to "\"\\u0001\"",
    "quote\t␟" to "\"\\u001f\"",
    "quote\té€" to "\"é€\"",
    "quote\t⇥⏎" to "\"\\t\\n\"",
    "quote\t" to "\"\"",
    "quote\t⟪NULL⟫" to "\"\"",
    "quote\t\\u2028" to "\"\\\\u2028\"",
    "nombre\tint\t5" to "5",
    "nombre\tint\t-7" to "-7",
    "nombre\tlong\t3000000000" to "3000000000",
    "nombre\tlong\t9007199254740993" to "9007199254740993",
    "nombre\tdouble\t1.0" to "1",
    "nombre\tdouble\t-0.0" to "-0",
    "nombre\tdouble\t0.0" to "0",
    "nombre\tdouble\t1.5" to "1.5",
    "nombre\tdouble\t1e20" to "1.0E20",
    "nombre\tdouble\t1e-5" to "1.0E-5",
    "nombre\tdouble\t0.001" to "0.001",
    "nombre\tdouble\t1234567.0" to "1234567",
    "nombre\tdouble\t12345678.9" to "1.23456789E7",
    "nombre\tdouble\t9.2233720368547758E18" to "9223372036854775807",
    "nombre\tdouble\t-1e300" to "-1.0E300",
    "nombre\tfloat\t1.5" to "1.5",
    "nombre\tfloat\t0.1" to "0.1",
    "nombre\tfloat\t-0.0" to "0",
    "nombre\tdouble\tNaN" to "!JSONException: Forbidden numeric value: NaN",
    "nombre\tdouble\tInfinity" to "!JSONException: Forbidden numeric value: Infinity",
    "nombre\tfloat\tNaN" to "!JSONException: Forbidden numeric value: NaN",
    "mettre\tint\t5" to "{\"k\":5}",
    "mettre\tlong\t5" to "{\"k\":5}",
    "mettre\tdouble\t5" to "{\"k\":5}",
    "mettre\tdouble\t0.5" to "{\"k\":0.5}",
    "mettre\tdouble\tNaN" to "!JSONException: Forbidden numeric value: NaN",
    "mettre\tdouble\t-Infinity" to "!JSONException: Forbidden numeric value: -Infinity",
    "mettre\tboolean\ttrue" to "{\"k\":true}",
    "mettre\tchaine\ta/b" to "{\"k\":\"a\\/b\"}",
    "mettre\tfloat\t1.5" to "{\"k\":1.5}",
    "mettre\tfloat\tNaN" to "!JSONException: Forbidden numeric value: NaN",
    "mettre\tnul\t-" to "{}",
    "mettre-indice\t0\t7" to "[7]",
    "mettre-indice\t3\t7" to "[null,null,null,7]",
    "mettre-indice\t-1\t7" to "!IndexOutOfBoundsException",
    "cumuler\t{\"k\":1}\tk\t2" to "{\"k\":[1,2]}",
    "cumuler\t{\"k\":[1]}\tk\t2" to "{\"k\":[1,2]}",
    "cumuler\t{}\tk\t2" to "{\"k\":2}",
    "ajouter\t{\"k\":[1]}\tk\t2" to "{\"k\":[1,2]}",
    "ajouter\t{}\tk\t2" to "{\"k\":[2]}",
    "ajouter\t{\"k\":1}\tk\t2" to "!JSONException: Key k is not a JSONArray",
    "noms\t{\"b\":1,\"a\":2}" to "[\"b\",\"a\"]",
    "cles\t{\"b\":1,\"a\":2}" to "b,a",
    "noms\t{}" to "∅",
    "cles\t{}" to "",
    "joindre\t[1,\"a\",null,true,1.5]\t, " to "1, \"a\", null, true, 1.5",
    "joindre\t[]\t," to "",
    "joindre\t[\"a/b\"]\t|" to "\"a\\/b\"",
    "vers-tableau\t{\"a\":1,\"b\":2}\t[\"b\",\"x\"]" to "[2,null]",
    "vers-tableau\t{\"a\":1}\t[]" to "∅",
    "vers-objet\t[1,2,3]\t[\"a\",\"b\"]" to "{\"a\":1,\"b\":2}",
    "vers-objet\t[1]\t[]" to "∅",
    "vers-objet\t[]\t[\"a\"]" to "∅",
    "vers-objet\t[1,2]\t[\"a\",null]" to "{\"a\":1,\"null\":2}",
    "vers-objet\t[1,null]\t[\"a\",null]" to "{\"a\":1,\"null\":null}",
)
