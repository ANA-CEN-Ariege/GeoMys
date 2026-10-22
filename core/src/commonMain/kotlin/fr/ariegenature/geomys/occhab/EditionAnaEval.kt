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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabDetailsDialog.kt,
// construireSectionAnaEval) : la section « Évaluation ANA / Natura 2000 », hors de ses vues — valeurs de
// départ, contrôle des entiers bornés, textes en lecture seule et fusion non destructive du bloc.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.util.AnaEval

/**
 * L'édition du bloc ANA-EVAL du plugin QGIS `occhab-qgis`, affichée UNIQUEMENT quand l'objet
 * édité porte un bloc (stations et habitats lus du serveur). Clés STATION
 * ([AnaEval.CHAMPS_STATION]) dans les détails d'une station, clés HABITAT
 * ([AnaEval.CHAMPS_HABITAT]) et `pee` dans l'habitat.
 *
 * Les clés hors du niveau (statut, recouvrement, clés de l'autre niveau…) sont PRÉSERVÉES telles
 * quelles ; `determination` et `corresp` (arbitrages botaniste) sont montrées en LECTURE SEULE,
 * jamais éditées ici.
 */
class EditionAnaEval(anaEvalJson: String, val champs: List<AnaEval.ChampAnaEval>, val avecPee: Boolean) {

    private val valeurs: Map<String, Any> = AnaEval.depuisJson(anaEvalJson)

    /** Position de départ d'un champ à référentiel : 0 = « — Non renseigné — ». */
    fun positionInitiale(champ: AnaEval.ChampAnaEval): Int {
        val ref = champ.referentiel ?: return 0
        val idx = ref.indexOfFirst { it.first == valeurs[champ.cle] }
        return if (idx >= 0) idx + 1 else 0
    }

    /** Texte de départ d'un champ libre. */
    fun texteInitial(champ: AnaEval.ChampAnaEval): String = valeurs[champ.cle]?.toString().orEmpty()

    /** Les trois taxons PEE (plantes exotiques envahissantes) de départ. */
    fun peeInitiaux(): List<String> {
        val actuels = (valeurs["pee"] as? List<*>).orEmpty()
        return List(3) { i -> actuels.getOrNull(i)?.toString().orEmpty() }
    }

    /** « Détermination hors HABREF » : le nom et son ancre, ou null sans détermination. */
    fun determination(): String? {
        val det = valeurs["determination"] as? Map<*, *> ?: return null
        val ancre = (det["ancre"] as? String)?.let { cle ->
            AnaEval.TYPOLOGIES_CORRESPONDANCE.firstOrNull { it.first == cle }?.second ?: cle
        }
        return det["nom"].toString() + (ancre?.let { " — ancre $it" } ?: "")
    }

    /** « Correspondances arbitrées » : une ligne par typologie (« EUNIS : E1.2 (manuel) »). */
    fun correspondances(): List<String> {
        val corr = valeurs["corresp"] as? Map<*, *> ?: return emptyList()
        return corr.entries.mapNotNull { (typologie, detail) ->
            val d = detail as? Map<*, *> ?: return@mapNotNull null
            val libelle = AnaEval.TYPOLOGIES_CORRESPONDANCE.firstOrNull { it.first == typologie }?.second ?: typologie.toString()
            val code = d["code"]?.toString() ?: "cd_hab ${d["cd_hab"]}"
            val src = (d["src"] as? String)?.let { " ($it)" } ?: ""
            "$libelle : $code$src"
        }
    }

    /** Contrôle bloquant d'un entier borné : message, ou null si la valeur est acceptable (vide
     *  compris). Sans lui, une valeur hors bornes était effacée en silence par `AnaEval`. */
    fun erreurEntier(champ: AnaEval.ChampAnaEval, texte: String): String? {
        if (!champ.entier) return null
        val t = texte.trim()
        val n = t.toIntOrNull()
        val borne = AnaEval.borneEntier(champ.cle)
        return when {
            t.isEmpty() -> null
            n == null -> "${champ.libelle} : nombre entier attendu"
            borne != null && n !in borne.first..borne.second -> "${champ.libelle} : entre ${borne.first} et ${borne.second}"
            else -> null
        }
    }

    /** Premier contrôle bloquant en échec parmi les [textes] (par clé), ou null. */
    fun premiereErreur(textes: Map<String, String>): String? =
        champs.firstNotNullOfOrNull { c -> if (c.entier) erreurEntier(c, textes[c.cle].orEmpty()) else null }

    /** Le bloc mis à jour : fusion NON destructive — on repart des valeurs du bloc (clés
     *  préservées comprises) et on n'y remplace que les clés éditées ici, valeur vidée = clé
     *  retirée. [positions] : champs à référentiel ; [textes] : champs libres ; [pee] : les trois
     *  taxons. JSON canonique validé par [AnaEval], ou null quand plus rien n'est renseigné (le
     *  bloc disparaîtra du texte à l'envoi). */
    fun fusionner(positions: Map<String, Int>, textes: Map<String, String>, pee: List<String>): String? {
        val maj = valeurs.toMutableMap<String, Any?>()
        for (champ in champs) {
            val ref = champ.referentiel
            val v: Any? = if (ref != null) {
                val pos = positions[champ.cle] ?: 0
                if (pos <= 0) null else ref.getOrNull(pos - 1)?.first
            } else {
                val t = textes[champ.cle].orEmpty().trim()
                when {
                    t.isEmpty() -> null
                    champ.entier -> t.toIntOrNull()
                    else -> t
                }
            }
            if (v == null) maj.remove(champ.cle) else maj[champ.cle] = v
        }
        if (avecPee) {
            val taxons = pee.map { it.trim() }.filter { it.isNotEmpty() }
            if (taxons.isEmpty()) maj.remove("pee") else maj["pee"] = taxons
        }
        return AnaEval.versJson(maj)
    }

    companion object {
        const val TITRE = "Évaluation ANA / Natura 2000"
        const val LIBELLE_PEE = "PEE — plantes exotiques envahissantes (3 taxons max)"
        const val LIBELLE_DETERMINATION = "Détermination hors HABREF (non modifiable)"
        const val LIBELLE_CORRESPONDANCES = "Correspondances arbitrées (non modifiables)"
        const val NON_RENSEIGNE = "— Non renseigné —"

        /** La section de la STATION, ou null si elle ne porte pas de bloc. */
        fun station(anaEvalJson: String?): EditionAnaEval? = anaEvalJson?.let { EditionAnaEval(it, AnaEval.CHAMPS_STATION, avecPee = false) }

        /** La section de l'HABITAT, ou null s'il ne porte pas de bloc. */
        fun habitat(anaEvalJson: String?): EditionAnaEval? = anaEvalJson?.let { EditionAnaEval(it, AnaEval.CHAMPS_HABITAT, avecPee = true) }
    }
}
