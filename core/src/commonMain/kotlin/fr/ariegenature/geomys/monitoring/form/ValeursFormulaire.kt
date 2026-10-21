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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/NouvelleVisiteFragment.kt) :
// valeurToJson, parseValeursJson et typerPourField, et les étapes pures d'envoyerVisite (médias
// retirés du corps, valeursJson, champs texte libre) et de chargerSchemaEtRendre (pré-remplissage
// d'une édition, champs dirty d'emblée). org.json est son portage commun : mêmes formes écrites
// (un Double entier sans « .0 », « / » échappé).

package fr.ariegenature.geomys.monitoring.form

import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.MediaSuivi

/** Le passage des valeurs du formulaire à ce qui est enregistré dans la file d'envoi des Suivis
 *  (`SaisieEnAttente.valeursJson`), puis au formulaire quand on rouvre la saisie. */
object ValeursFormulaire {

    /** Ce qu'un enregistrement tire des valeurs du formulaire. */
    data class Enregistrement(
        /** Le corps de la saisie, sans les champs MEDIA. */
        val valeursJson: String,
        /** Les fichiers de tous les champs MEDIA (envoyés après création de l'objet). */
        val mediaPaths: List<String>,
        /** La table du premier champ MEDIA rempli dans gn_commons (`schema_dot_table`), ou null. */
        val mediaSchemaDotTable: String?,
        /** Codes des valeurs en texte : elles partent en texte, même numériques (une option « 1 », un
         *  commentaire « 42 »), comme dans le corps du web. */
        val champsTexteLibre: List<String>,
        /** Codes des textes et des heures vidés par l'utilisateur, que le web envoie en "". */
        val champsVidesGardes: List<String> = emptyList(),
        /** Les médias de tous les champs MEDIA, avec leurs titre, auteur et description. */
        val medias: List<MediaSuivi> = emptyList(),
    )

    /**
     * Prépare l'enregistrement d'une saisie à partir de [valeursPourEnvoi]
     * ([FormulaireEtat.lireValeursPourEnvoi]) et des [valeursFixes] des propriétés masquées, que les
     * valeurs du formulaire remplacent :
     * - les médias de tous les champs MEDIA ([medias], de [FormulaireEtat.mediasPourEnvoi]) sont mis à
     *   part, et TOUS les champs MEDIA (remplis ou vides) sont retirés du corps : le serveur
     *   Marshmallow n'accepte pas un
     *   media:"file://…" dans le POST de l'objet ; l'envoi des fichiers suit la création, par
     *   gn_commons ;
     * - les valeurs sont écrites en JSON par JSONObject, pour garder leurs types (Int, Double,
     *   Boolean, null, tableaux).
     */
    fun preparerEnregistrement(
        champs: List<EditableField>,
        valeursPourEnvoi: Map<String, Any?>,
        valeursFixes: Map<String, Any> = emptyMap(),
        medias: List<MediaSuivi> = emptyList(),
    ): Enregistrement {
        val valeurs: MutableMap<String, Any?> = (valeursFixes + valeursPourEnvoi).toMutableMap()
        champs.filter { it.viewType == ViewType.MEDIA }.forEach { valeurs.remove(it.code) }
        return Enregistrement(
            valeursJson = valeursJson(valeurs),
            mediaPaths = medias.map { it.chemin },
            mediaSchemaDotTable = medias.firstOrNull()?.schemaDotTable,
            medias = medias,
            // Les valeurs ont déjà leur type (nombre d'un champ numérique ou d'une option numérique) :
            // un texte reste un texte.
            champsTexteLibre = valeurs.filterValues { v -> v is String || (v is List<*> && v.any { it is String }) }.keys.toList(),
            champsVidesGardes = valeursPourEnvoi.filterValues { it == "" }.keys.toList(),
        )
    }

    /** Les valeurs en objet JSON `{code: valeur}`, dans leur ordre. */
    fun valeursJson(valeurs: Map<String, Any?>): String =
        JSONObject().apply { for ((code, v) in valeurs) put(code, valeurToJson(v)) }.toString()

    /** Une valeur du formulaire en valeur acceptée par JSONObject.put : les nombres et les booléens
     *  gardent leur type, une liste devient un JSONArray, null le JSON null, et tout le reste une
     *  chaîne. Une chaîne qui ressemble à un entier (ex. un id_nomenclature) reste une chaîne : la
     *  conversion se fait à l'envoi au serveur, pas ici. */
    fun valeurToJson(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Boolean -> v
        is Number -> v
        is List<*> -> JSONArray().apply { v.forEach { put(valeurToJson(it)) } }
        else -> v.toString()
    }

    /** Un `valeursJson` relu en Map, pour pré-remplir le formulaire d'une saisie rouverte : les
     *  tableaux deviennent des List<String>, le JSON null devient null, les autres valeurs gardent le
     *  type que leur donne la lecture JSON (Int, Long, Double, Boolean, String). */
    fun parseValeursJson(json: String): Map<String, Any?> {
        val obj = JSONObject(json)
        val out = mutableMapOf<String, Any?>()
        obj.keys().forEach { k ->
            val v = obj.get(k)
            out[k] = when (v) {
                JSONObject.NULL -> null
                is JSONArray -> (0 until v.length()).map { v.get(it).toString() }
                else -> v
            }
        }
        return out
    }

    /** Une valeur relue ([parseValeursJson]) dans la forme qu'attend la valeur initiale du champ
     *  [f] : Int pour TAXON, chaîne pour DATE et SELECT, liste pour les choix multiples… */
    fun typerPourField(f: EditableField, v: Any?): Any? {
        if (v == null) return null
        return when (f.viewType) {
            ViewType.TEXT, ViewType.TEXTAREA, ViewType.DATE, ViewType.TIME, ViewType.DATETIME,
            ViewType.SELECT, ViewType.RADIO,
            -> v.toString()
            ViewType.TAXON -> when (v) {
                is Int -> v
                is Long -> v.toInt()
                is Number -> v.toInt()
                is String -> v.toIntOrNull()
                else -> v.toString().toIntOrNull()
            }
            // NUMBER : une valeur décimale n'est PAS tronquée (widgets number/float/decimal — cf.
            // EditableField.decimal). Un entier reste Int.
            ViewType.NUMBER -> when (v) {
                is Int -> v
                is Number -> if (v.toDouble() == v.toLong().toDouble()) v.toInt() else v.toDouble()
                is String -> v.toIntOrNull() ?: v.toDoubleOrNull()
                else -> null
            }
            ViewType.SELECT_MULTIPLE, ViewType.CHECKBOX_MULTIPLE -> when (v) {
                is List<*> -> v.map { it.toString() }
                else -> listOf(v.toString())
            }
            ViewType.CHECKBOX -> v
            // MEDIA : le valeursJson ne contient PAS ce champ ; le pré-remplissage d'une édition
            // passe par [champsPreremplis].
            ViewType.MEDIA -> v.toString()
            // Un texte du protocole n'a pas de valeur.
            ViewType.HTML -> null
        }
    }

    /** Les champs d'une saisie rouverte : chaque champ présent dans [valeursPreremplies] reçoit sa
     *  valeur typée ([typerPourField]), et chaque champ MEDIA ses médias ([medias], qui ne font pas
     *  partie du valeursJson). */
    fun champsPreremplis(
        champs: List<EditableField>,
        valeursPreremplies: Map<String, Any?>?,
        medias: List<MediaSuivi>,
    ): List<EditableField> {
        val avecValeurs = valeursPreremplies?.let { valeurs ->
            champs.map { f -> if (!valeurs.containsKey(f.code)) f else f.copy(value = typerPourField(f, valeurs[f.code])) }
        } ?: champs
        if (medias.isEmpty()) return avecValeurs
        // Chaque média revient dans son champ ; ceux d'une saisie plus ancienne, sans champ, dans le
        // premier champ MEDIA.
        val premier = avecValeurs.firstOrNull { it.viewType == ViewType.MEDIA }?.code
        return avecValeurs.map { f ->
            if (f.viewType != ViewType.MEDIA) return@map f
            val siens = medias.filter { it.champ == f.code || (it.champ.isEmpty() && f.code == premier) }
            if (siens.isEmpty()) f else f.copy(value = siens)
        }
    }

    /** Les champs porteurs d'une valeur SAUVEGARDÉE non vide : « modifiés par l'utilisateur »
     *  d'emblée en édition (cf. [FormulaireEtat.champsDirtyInitiaux]). */
    fun champsDirtyInitiaux(valeursPreremplies: Map<String, Any?>?): Set<String> =
        valeursPreremplies?.filterValues { v -> v != null && v.toString().isNotEmpty() }?.keys.orEmpty()
}
