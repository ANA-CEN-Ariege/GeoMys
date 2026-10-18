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

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Exécute chaque cas de cas.tsv avec l'org.json d'Android (sources du SDK, compilées par
 *  generer.sh) et écrit une ligne de résultat par cas. JSONAndroidTest.kt interprète les cas de la
 *  même façon : toute modification ici doit y être reportée (generer.sh en contient le modèle). */
public class Harnais {
    static String decoder(String s) {
        return s.replace("⏎", "\n").replace("⇥", "\t").replace("␍", "\r").replace("␈", "\b").replace("␌", "\f")
                .replace("␁", "\u0001").replace("␟", "\u001f").replace("⟪BOM⟫", "﻿");
    }

    static String encoder(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\n': b.append("⏎"); break;
                case '\t': b.append("⇥"); break;
                case '\r': b.append("␍"); break;
                case '\b': b.append("␈"); break;
                case '\f': b.append("␌"); break;
                case '\u0001': b.append("␁"); break;
                case '\u001f': b.append("␟"); break;
                case '﻿': b.append("⟪BOM⟫"); break;
                case ' ': b.append("⟪LS⟫"); break;
                case '\u007f': b.append("⟪DEL⟫"); break;
                case '￿': b.append("⟪FFFF⟫"); break;
                default:
                    if (c < 0x20) b.append("⟪").append(Integer.toHexString(c)).append("⟫"); else b.append(c);
            }
        }
        return b.toString();
    }

    static String fmt(Object v) {
        if (v == null) return "∅";
        if (v == JSONObject.NULL) return "NULL";
        if (v instanceof Integer) return "Int:" + v;
        if (v instanceof Long) return "Long:" + v;
        if (v instanceof Double) return "Double:" + v;
        if (v instanceof Boolean) return "Boolean:" + v;
        if (v instanceof String) return "String:" + v;
        if (v instanceof JSONObject) return "JSONObject:" + v;
        if (v instanceof JSONArray) return "JSONArray:" + v;
        return "?" + v.getClass().getName() + ":" + v;
    }

    static String executer(String[] f) throws Exception {
        String d;
        switch (f[0]) {
            case "analyser":
                return fmt(new JSONTokener(decoder(f[1])).nextValue());
            case "objet": {
                JSONObject o = new JSONObject(decoder(f[1]));
                String k = f[3];
                d = f.length > 4 ? f[4] : null;
                switch (f[2]) {
                    case "optString": return d == null ? o.optString(k) : o.optString(k, d);
                    case "optInt": return String.valueOf(d == null ? o.optInt(k) : o.optInt(k, Integer.parseInt(d)));
                    case "optLong": return String.valueOf(d == null ? o.optLong(k) : o.optLong(k, Long.parseLong(d)));
                    case "optDouble": return String.valueOf(d == null ? o.optDouble(k) : o.optDouble(k, Double.parseDouble(d)));
                    case "optBoolean": return String.valueOf(d == null ? o.optBoolean(k) : o.optBoolean(k, Boolean.parseBoolean(d)));
                    case "getString": return o.getString(k);
                    case "getInt": return String.valueOf(o.getInt(k));
                    case "getLong": return String.valueOf(o.getLong(k));
                    case "getDouble": return String.valueOf(o.getDouble(k));
                    case "getBoolean": return String.valueOf(o.getBoolean(k));
                    case "getJSONObject": return fmt(o.getJSONObject(k));
                    case "getJSONArray": return fmt(o.getJSONArray(k));
                    case "isNull": return String.valueOf(o.isNull(k));
                    case "has": return String.valueOf(o.has(k));
                    case "opt": return fmt(o.opt(k));
                    case "get": return fmt(o.get(k));
                    case "optJSONObject": return fmt(o.optJSONObject(k));
                    case "optJSONArray": return fmt(o.optJSONArray(k));
                }
                break;
            }
            case "tableau": {
                JSONArray a = new JSONArray(decoder(f[1]));
                int i = Integer.parseInt(f[3]);
                d = f.length > 4 ? f[4] : null;
                switch (f[2]) {
                    case "length": return String.valueOf(a.length());
                    case "optString": return d == null ? a.optString(i) : a.optString(i, d);
                    case "optInt": return String.valueOf(d == null ? a.optInt(i) : a.optInt(i, Integer.parseInt(d)));
                    case "optLong": return String.valueOf(a.optLong(i));
                    case "optDouble": return String.valueOf(d == null ? a.optDouble(i) : a.optDouble(i, Double.parseDouble(d)));
                    case "optBoolean": return String.valueOf(d == null ? a.optBoolean(i) : a.optBoolean(i, Boolean.parseBoolean(d)));
                    case "isNull": return String.valueOf(a.isNull(i));
                    case "get": return fmt(a.get(i));
                    case "opt": return fmt(a.opt(i));
                    case "getString": return a.getString(i);
                    case "getInt": return String.valueOf(a.getInt(i));
                    case "getDouble": return String.valueOf(a.getDouble(i));
                    case "getBoolean": return String.valueOf(a.getBoolean(i));
                    case "getJSONObject": return fmt(a.getJSONObject(i));
                    case "getJSONArray": return fmt(a.getJSONArray(i));
                    case "optJSONObject": return fmt(a.optJSONObject(i));
                    case "optJSONArray": return fmt(a.optJSONArray(i));
                    case "remove": { Object r = a.remove(i); return fmt(r) + " " + a; }
                }
                break;
            }
            case "ecrire":
                return new JSONTokener(decoder(f[1])).nextValue().toString();
            case "ecrire-indente": {
                Object v = new JSONTokener(decoder(f[1])).nextValue();
                int n = Integer.parseInt(f[2]);
                return v instanceof JSONObject ? ((JSONObject) v).toString(n) : ((JSONArray) v).toString(n);
            }
            case "quote":
                return JSONObject.quote("⟪NULL⟫".equals(f[1]) ? null : decoder(f[1]));
            case "nombre": {
                Number n;
                switch (f[1]) {
                    case "int": n = Integer.valueOf(f[2]); break;
                    case "long": n = Long.valueOf(f[2]); break;
                    case "float": n = Float.valueOf(f[2]); break;
                    default: n = Double.valueOf(f[2]);
                }
                return JSONObject.numberToString(n);
            }
            case "mettre": {
                JSONObject o = new JSONObject();
                switch (f[1]) {
                    case "int": o.put("k", Integer.parseInt(f[2])); break;
                    case "long": o.put("k", Long.parseLong(f[2])); break;
                    case "double": o.put("k", Double.parseDouble(f[2])); break;
                    case "boolean": o.put("k", Boolean.parseBoolean(f[2])); break;
                    case "chaine": o.put("k", f[2]); break;
                    case "float": o.put("k", (Object) Float.valueOf(f[2])); break;
                    case "nul": o = new JSONObject("{\"k\":1}"); o.put("k", (Object) null); break;
                }
                return o.toString();
            }
            case "mettre-indice": {
                JSONArray a = new JSONArray();
                a.put(Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                return a.toString();
            }
            case "cumuler": {
                JSONObject o = new JSONObject(f[1]);
                o.accumulate(f[2], Integer.valueOf(f[3]));
                return o.toString();
            }
            case "ajouter": {
                JSONObject o = new JSONObject(f[1]);
                o.append(f[2], Integer.valueOf(f[3]));
                return o.toString();
            }
            case "noms": {
                JSONArray n = new JSONObject(f[1]).names();
                return n == null ? "∅" : n.toString();
            }
            case "cles": {
                StringBuilder b = new StringBuilder();
                Iterator<String> it = new JSONObject(f[1]).keys();
                while (it.hasNext()) { if (b.length() > 0) b.append(","); b.append(it.next()); }
                return b.toString();
            }
            case "joindre":
                return new JSONArray(f[1]).join(f[2]);
            case "vers-tableau": {
                JSONArray r = new JSONObject(f[1]).toJSONArray(new JSONArray(f[2]));
                return r == null ? "∅" : r.toString();
            }
            case "vers-objet": {
                JSONObject r = new JSONArray(f[1]).toJSONObject(new JSONArray(f[2]));
                return r == null ? "∅" : r.toString();
            }
        }
        throw new IllegalArgumentException("cas inconnu : " + String.join(" | ", f));
    }

    public static void main(String[] args) throws Exception {
        List<String> lignes = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        StringBuilder sortie = new StringBuilder();
        for (String ligne : lignes) {
            if (ligne.isEmpty()) continue;
            String resultat;
            try {
                resultat = executer(ligne.split("\t", -1));
            } catch (JSONException e) {
                resultat = "!JSONException: " + e.getMessage();
            } catch (Exception e) {
                resultat = "!" + e.getClass().getSimpleName();
            }
            sortie.append(encoder(resultat)).append('\n');
        }
        Files.write(Paths.get(args[1]), sortie.toString().getBytes(StandardCharsets.UTF_8));
    }
}
