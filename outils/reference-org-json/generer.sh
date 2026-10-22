#!/bin/bash
# GeoMys — application de saisie naturaliste pour GeoNature, Android et iOS.
# Copyright (C) 2026 ANA - CEN Ariège
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

# Régénère core/src/commonTest/kotlin/fr/ariegenature/geomys/json/JSONAndroidTest.kt : pour chaque
# cas de cas.tsv, le résultat que rend l'org.json d'Android. Ce sont les sources mêmes d'Android
# (SDK, android-36.1), compilées et exécutées sur la JVM par Harnais.java. Le test vérifie ensuite
# que le portage Kotlin (core/…/json) rend la même chose, sur la JVM et sur iOS.
#
# Usage : outils/reference-org-json/generer.sh
# Prérequis : les sources du SDK Android (android-36.1, via le SDK Manager) et un JDK (par défaut
# celui d'Android Studio). À relancer après toute modification de cas.tsv ou de Harnais.java.
#
# Format de cas.tsv : une opération par ligne, champs séparés par des tabulations. Dans les champs
# et les résultats, ⏎ ⇥ ␍ ␈ ␌ ␁ ␟ et ⟪BOM⟫ tiennent lieu des caractères de contrôle.

set -euo pipefail

ici="$(cd "$(dirname "$0")" && pwd)"
racine="$(cd "$ici/../.." && pwd)"
sdk="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' "$racine/local.properties")}"
sources="$sdk/sources/android-36.1/org/json"
jdk="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
cible="$racine/core/src/commonTest/kotlin/fr/ariegenature/geomys/json/JSONAndroidTest.kt"

[ -d "$sources" ] || { echo "Sources d'org.json introuvables : $sources" >&2; exit 1; }

travail="$(mktemp -d)"
trap 'rm -rf "$travail"' EXIT

# Les sources d'Android, sans leurs annotations internes (android.*, libcore.*, dalvik.*).
mkdir -p "$travail/src/org/json"
for fichier in "$sources"/*.java; do
    python3 -I - "$fichier" "$travail/src/org/json/$(basename "$fichier")" <<'PY'
import re, sys
texte = open(sys.argv[1], encoding="utf-8").read()
texte = re.sub(r'^import (static )?(android|libcore|dalvik)\..*$', '', texte, flags=re.M)
texte = re.sub(r'@(UnsupportedAppUsage|SystemApi|android\.[\w.]+)(\([^)]*\))?', '', texte)
texte = re.sub(r'@(NonNull|Nullable)\b', '', texte)
open(sys.argv[2], "w", encoding="utf-8").write(texte)
PY
done

"$jdk/bin/javac" -encoding UTF-8 -d "$travail/classes" "$travail"/src/org/json/*.java "$ici/Harnais.java"
"$jdk/bin/java" -cp "$travail/classes" Harnais "$ici/cas.tsv" "$travail/attendus.txt"

mkdir -p "$(dirname "$cible")"

python3 -I - "$ici/cas.tsv" "$travail/attendus.txt" "$cible" <<'PY'
import sys

cas = [l for l in open(sys.argv[1], encoding="utf-8").read().split("\n") if l]
attendus = open(sys.argv[2], encoding="utf-8").read().split("\n")[:len(cas)]
assert len(cas) == len(attendus), (len(cas), len(attendus))

def kotlin(s):
    morceaux = []
    for c in s:
        if c == "\\":
            morceaux.append("\\\\")
        elif c == '"':
            morceaux.append('\\"')
        elif c == "$":
            morceaux.append("\\$")
        elif c == "\t":
            morceaux.append("\\t")
        elif ord(c) < 0x20 or c in "\u007f  ﻿￿":
            morceaux.append("\\u%04x" % ord(c))
        else:
            morceaux.append(c)
    return '"' + "".join(morceaux) + '"'

donnees = "\n".join("    %s to %s," % (kotlin(c), kotlin(a)) for c, a in zip(cas, attendus))

modele = r'''/*
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
%DONNEES%
)
'''
open(sys.argv[3], "w", encoding="utf-8").write(modele.replace("%DONNEES%", donnees))
print(f"{len(cas)} cas écrits dans {sys.argv[3]}")
PY
