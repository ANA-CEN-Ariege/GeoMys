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

// Les droits de création des Suivis, comme gn_module_monitoring 1.3.2 : config.service.ts
// (moduleCruved), monitoring-datatable.component.ts (canCreateChild), constants/guard.ts, et
// PERMISSION_LEVEL_DEFAULT de conf_schema_toml.py.

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject

/** Le droit de créer (C du CRUVED) d'un module GeoNature : celui du module, et celui de chacun de
 *  ses objets de permission ([objets], par `code_object`). */
data class DroitsModule(val creer: Int, val objets: Map<String, Int>)

/** Le droit de créer de l'utilisateur sur chaque type d'objet d'un protocole, comme le web : le
 *  cruved de l'objet de permission du type (`MONITORINGS.PERMISSION_LEVEL`), sinon celui du module,
 *  lus dans `/api/gn_commons/modules` à la synchro. Sans ce droit, le web grise le « + » d'ajout. */
object DroitsSuivis {

    /** `PERMISSION_LEVEL_DEFAULT` de gn_module_monitoring : l'objet de permission de chaque type. */
    val NIVEAUX_PAR_DEFAUT: Map<String, String> = mapOf(
        "module" to "MONITORINGS_MODULES",
        "site" to "MONITORINGS_SITES",
        "sites_group" to "MONITORINGS_GRP_SITES",
        "visit" to "MONITORINGS_VISITES",
        "observation" to "MONITORINGS_VISITES",
        "observation_detail" to "MONITORINGS_VISITES",
        "individual" to "MONITORINGS_INDIVIDUALS",
        "marking" to "MONITORINGS_MARKINGS",
    )

    /** L'infobulle du web sur un « + » grisé (TOOLTIPMESSAGEALERT). */
    const val SANS_PERMISSION = "Vous n'avez pas les permissions nécessaires"

    /** Le message du formulaire d'un nouvel objet sans le droit de créer (notAllowedMessage). */
    const val SANS_PERMISSION_EDITION = "Vous n'avez pas les permissions nécessaires pour éditer l'objet"

    /** Les droits de création de chaque module de la réponse de `/api/gn_commons/modules` ;
     *  module_code → droits. Un module absent de la réponse (aucun droit) n'y figure pas. */
    fun lire(modulesJson: String): Map<String, DroitsModule> {
        val arr = JSONArray(modulesJson)
        val droits = LinkedHashMap<String, DroitsModule>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val code = m.optString("module_code").takeIf { it.isNotEmpty() } ?: continue
            val objets = LinkedHashMap<String, Int>()
            val liste = m.optJSONArray("objects")
            if (liste != null) {
                for (j in 0 until liste.length()) {
                    val o = liste.optJSONObject(j) ?: continue
                    val codeObjet = o.optString("code_object").takeIf { it.isNotEmpty() } ?: continue
                    objets[codeObjet] = creer(o.optJSONObject("cruved"))
                }
            }
            droits[code] = DroitsModule(creer(m.optJSONObject("cruved")), objets)
        }
        return droits
    }

    /** Le C d'un bloc `cruved` : un niveau (0 à 3), ou un booléen sur les serveurs qui en
     *  envoient ; absent, aucun droit. */
    private fun creer(cruved: JSONObject?): Int = when (val c = cruved?.opt("C")) {
        is Boolean -> if (c) 1 else 0
        is Number -> c.toInt()
        is String -> c.toIntOrNull() ?: 0
        else -> 0
    }

    /** Les objets de permission de [json], le `MONITORINGS.PERMISSION_LEVEL` de `/api/gn_commons/config`
     *  gardé par la synchro ; vide ou illisible, les défauts de gn_module_monitoring. */
    fun niveaux(json: String): Map<String, String> {
        if (json.isBlank()) return NIVEAUX_PAR_DEFAUT
        return try {
            val niveaux = JSONObject(json)
            val lus = LinkedHashMap<String, String>()
            niveaux.keys().forEach { type -> niveaux.optString(type).takeIf { it.isNotEmpty() }?.let { lus[type] = it } }
            lus.ifEmpty { NIVEAUX_PAR_DEFAUT }
        } catch (_: Exception) {
            NIVEAUX_PAR_DEFAUT
        }
    }

    /** L'utilisateur peut-il créer un objet de [type] dans le protocole [moduleCode] ? La règle du
     *  web : le C de l'objet de permission du type, sinon celui du module. Sans droits lus (synchro
     *  antérieure, serveur muet) ou pour un type sans objet de permission : oui, le serveur
     *  tranchera à l'envoi. */
    fun peutCreer(droits: Map<String, DroitsModule>?, niveaux: Map<String, String>, moduleCode: String, type: String): Boolean {
        val module = droits?.get(moduleCode) ?: return droits == null || droits.isEmpty()
        val objet = niveaux[type] ?: return true
        return (module.objets[objet] ?: module.creer) > 0
    }

    /** Les droits en JSON, pour le cache de la synchro. */
    fun versJson(droits: Map<String, DroitsModule>): String {
        val racine = JSONObject()
        droits.forEach { (code, d) ->
            val objets = JSONObject()
            d.objets.forEach { (o, c) -> objets.put(o, c) }
            racine.put(code, JSONObject().put("C", d.creer).put("objets", objets))
        }
        return racine.toString()
    }

    fun depuisJson(json: String): Map<String, DroitsModule> {
        val racine = JSONObject(json)
        val droits = LinkedHashMap<String, DroitsModule>()
        racine.keys().forEach { code ->
            val d = racine.optJSONObject(code) ?: return@forEach
            val objets = LinkedHashMap<String, Int>()
            d.optJSONObject("objets")?.let { o -> o.keys().forEach { k -> objets[k] = o.optInt(k, 0) } }
            droits[code] = DroitsModule(d.optInt("C", 0), objets)
        }
        return droits
    }
}
