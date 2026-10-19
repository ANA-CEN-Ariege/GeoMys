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

// Extrait de GeoMys-Android @ cdd5196 : la logique des écrans Caractérisation et Dénombrement d'une espèce
// (ui/CaracterisationFragment.kt, ui/DenombrementFragment.kt, ui/saisie/OcctaxFieldsRenderer.kt et
// groupe2InpnPour de ui/SaisieObservationFragment.kt), sortie des vues et de leurs tags.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.plateforme.comparateurFrancais
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.OcctaxFieldsConfig
import fr.ariegenature.geomys.store.OcctaxFieldsConfig.OcctaxField
import fr.ariegenature.geomys.store.champFormVisible

/** Un champ de nomenclature affiché : son [libelle] et ses entrées, « Non renseigné » d'abord
 *  ([libelles] et [codes] alignés, code vide pour la première). */
data class ChampNomenclature(
    val champ: OcctaxField,
    val libelle: String,
    val libelles: List<String>,
    val codes: List<String>,
) {
    /** La position d'un code ; absent des entrées, « Non renseigné ». */
    fun position(code: String): Int = codes.indexOf(code).coerceAtLeast(0)

    fun code(position: Int): String = codes.getOrNull(position) ?: ""
}

object FormulairesEspece {

    /** Caractérisation : les champs toujours visibles (code de nomenclature, clé form_fields). */
    val OCC_BASIQUES = listOf("METH_OBS" to "obs_tech", "ETA_BIO" to "bio_condition")

    /** Caractérisation ouverte d'elle-même (nidification) : le seul comportement. */
    val OCC_COMPORTEMENT = listOf("OCC_COMPORTEMENT" to "behaviour")

    /** Caractérisation : les nomenclatures de la section « Plus de champs », dans l'ordre du web. */
    val OCC_AVANCES = listOf(
        "METH_DETERMIN" to "determination_method",
        "STATUT_OBS" to "observation_status",
        "NATURALITE" to "naturalness",
        "STATUT_BIO" to "bio_status",
        "OCC_COMPORTEMENT" to "behaviour",
        "STATUT_SOURCE" to "source_status",
        "DEE_FLOU" to "blurring",
        "PREUVE_EXIST" to "exist_proof",
    )

    /** Les nomenclatures portées par [EspeceReleve.champsOccExtra] (clé form_fields), pas par un
     *  champ de l'espèce. */
    val CODES_EXTRA = setOf("STATUT_SOURCE", "DEE_FLOU")

    /** Les preuves de la section « Plus de champs » : (clé form_fields, libellé, URL ?). */
    val PREUVES = listOf(
        Triple("digital_proof", "Preuve numérique (URL)", true),
        Triple("non_digital_proof", "Preuve non numérique", false),
    )

    /** Dénombrement : les nomenclatures d'un dénombrement, dans l'ordre du web. */
    val COUNTING = listOf("OBJ_DENBR" to "obj_count", "TYP_DENBR" to "type_count", "STADE_VIE" to "life_stage", "SEXE" to "sex")

    /** Les champs de [liste] que form_fields montre. */
    fun champsVisibles(liste: List<Pair<String, String>>, formFieldsJson: String): List<OcctaxField> =
        liste.mapNotNull { (code, cle) -> if (champFormVisible(formFieldsJson, cle)) OcctaxFieldsConfig.parCode[code] else null }

    /** Les entrées d'un champ : les valeurs du serveur filtrées par groupe et règne, « Non
     *  renseigné » d'abord, sinon les valeurs de repli du registre ; un libellé déjà vu est retiré
     *  (le premier reste), puis l'ordre alphabétique français, le « Non renseigné » restant en tête. */
    fun champ(
        champ: OcctaxField,
        nomenclatures: NomenclatureCache,
        groupes: Set<String>,
        regno: String,
        libelle: String = champ.label,
    ): ChampNomenclature {
        val valeurs = if (nomenclatures.estDisponible) nomenclatures.filtrerPourGroupes(champ.code, groupes, regno) else emptyList()
        val (libellesBruts, codesBruts) = if (valeurs.isNotEmpty()) {
            Pair(listOf("Non renseigné") + valeurs.map { it.label }, listOf("") + valeurs.map { it.id.toString() })
        } else {
            Pair(champ.fallbackLabels, champ.fallbackCodes)
        }
        val vus = HashSet<String>()
        val paires = libellesBruts.indices
            .filter { vus.add(libellesBruts[it].trim().lowercase()) }
            .map { libellesBruts[it] to codesBruts.getOrElse(it) { "" } }
        val (vides, autres) = paires.partition { it.second.isEmpty() }
        val comparateur = comparateurFrancais()
        val triees = vides + autres.sortedWith { a, b -> comparateur.compare(a.first, b.first) }
        return ChampNomenclature(champ, libelle, triees.map { it.first }, triees.map { it.second })
    }

    /** Position de départ d'un champ : la valeur portée ([courant]), sinon le défaut du serveur. */
    fun positionInitiale(champ: ChampNomenclature, courant: String, nomenclatures: NomenclatureCache): Int =
        champ.position(courant.ifEmpty { nomenclatures.defautPour(champ.champ.code) ?: "" })

    /** La valeur d'une nomenclature de caractérisation portée par l'espèce. */
    fun valeurOccurrence(espece: EspeceReleve, champ: OcctaxField): String = when (champ.code) {
        in CODES_EXTRA -> espece.champsOccExtra[champ.formFieldKey].orEmpty()
        else -> when (champ.svKey) {
            "statutObs" -> espece.statutObs
            "techniqueObs" -> espece.techniqueObs
            "etaBio" -> espece.etaBio
            "comportement" -> espece.comportement
            "statutBio" -> espece.statutBio
            "methDetermin" -> espece.methDetermin
            "naturalite" -> espece.naturalite
            "preuveExist" -> espece.preuveExist
            else -> ""
        }
    }

    /** ✓ de la caractérisation : [nomenclatures] (code → valeur) des champs AFFICHÉS — un champ
     *  masqué n'est pas collecté et garde sa valeur. [determinateur], [notes] et [preuves] : null
     *  quand le champ n'est pas affiché. En mode comportement seul, ni les champs additionnels ni
     *  les champs extra ne sont réécrits ([additionnels] null). */
    fun appliquerCaracterisation(
        espece: EspeceReleve,
        nomenclatures: Map<String, String>,
        determinateur: String?,
        notes: String?,
        preuves: Map<String, String>,
        additionnels: Map<String, String>?,
    ): EspeceReleve {
        var e = espece
        val extra = HashMap(espece.champsOccExtra)
        for ((code, valeur) in nomenclatures) {
            val champ = OcctaxFieldsConfig.parCode[code] ?: continue
            if (code in CODES_EXTRA) {
                if (valeur.isEmpty()) extra.remove(champ.formFieldKey) else extra[champ.formFieldKey] = valeur
                continue
            }
            e = when (champ.svKey) {
                "statutObs" -> e.copy(statutObs = valeur)
                "techniqueObs" -> e.copy(techniqueObs = valeur)
                "etaBio" -> e.copy(etaBio = valeur)
                "comportement" -> e.copy(comportement = valeur)
                "statutBio" -> e.copy(statutBio = valeur)
                "methDetermin" -> e.copy(methDetermin = valeur)
                "naturalite" -> e.copy(naturalite = valeur)
                "preuveExist" -> e.copy(preuveExist = valeur)
                else -> e
            }
        }
        if (determinateur != null) e = e.copy(determinateur = determinateur)
        if (notes != null) e = e.copy(notes = notes)
        for ((cle, valeur) in preuves) {
            val v = valeur.trim()
            if (v.isEmpty()) extra.remove(cle) else extra[cle] = v
        }
        if (additionnels != null) e = e.copy(additionalFieldsOccurrence = additionnels, champsOccExtra = extra)
        return e
    }

    /** Le groupe INPN (group2_inpn) de l'espèce, pour filtrer les nomenclatures : celui du cache
     *  TaxRef ([groupes2] par cd_nom), sinon celui du groupe de la palette. */
    fun groupe2Inpn(espece: EspeceReleve, groupes2: Map<String, String>): String? =
        espece.cdNom?.let { groupes2[it.toString()] } ?: when (espece.taxon) {
            Taxon.MAMMIFERE -> "Mammifères"
            Taxon.REPTILE -> "Reptiles"
            Taxon.BATRACIEN -> "Amphibiens"
            Taxon.POISSON -> "Poissons"
            Taxon.INSECTE -> "Insectes"
            Taxon.PLANTE, Taxon.FONGE, Taxon.MOLLUSQUE, Taxon.INVERTEBRES -> null
            Taxon.OISEAU -> "Oiseaux"
        }

    // ── Dénombrement ──

    /** La valeur d'une nomenclature d'un dénombrement. */
    fun valeurDenombrement(d: Denombrement, champ: OcctaxField): String = when (champ.svKey) {
        "sexe" -> d.sexe
        "stadeVie" -> d.stadeVie
        "objDenbr" -> d.objDenbr
        "typDenbr" -> d.typDenbr
        else -> null
    }.orEmpty()

    /** Le nombre max après une frappe dans le nombre min : il suit le min s'il le suivait (vide ou
     *  égal à l'ancien min), pour ne pas écraser une borne saisie volontairement. */
    fun maxApresMin(ancienMin: String, nouveauMin: String, max: String): String =
        if ((max.isEmpty() || max == ancienMin) && max != nouveauMin) nouveauMin else max

    /** « − » et « ＋ » : [texte] (ou le plancher s'il est illisible) plus [delta], jamais sous
     *  [plancher]. */
    fun ajuster(texte: String, delta: Int, plancher: Int): String =
        ((texte.toIntOrNull() ?: plancher) + delta).coerceAtLeast(plancher).toString()

    /** Le plancher du nombre max : le min, au moins 1. */
    fun plancherMax(min: String): Int = min.toIntOrNull()?.coerceAtLeast(1) ?: 1

    /** Les effectifs relus : le min (au moins 1, 1 s'il est illisible), le max (au moins le min, le
     *  min s'il est illisible). */
    fun effectifs(min: String, max: String): Pair<Int, Int> {
        val n = min.toIntOrNull()?.coerceAtLeast(1) ?: 1
        return n to (max.toIntOrNull() ?: n).coerceAtLeast(n)
    }

    /** ✓ du dénombrement : les effectifs relus, les nomenclatures AFFICHÉES ([nomenclatures], code →
     *  valeur ; un champ masqué garde la sienne, une valeur vide n'en est pas une) et les champs
     *  additionnels. */
    fun appliquerDenombrement(
        d: Denombrement,
        min: String,
        max: String,
        nomenclatures: Map<String, String>,
        additionnels: Map<String, String>,
    ): Denombrement {
        val (n, m) = effectifs(min, max)
        val parSvKey = nomenclatures.entries.associate { (OcctaxFieldsConfig.parCode[it.key]?.svKey ?: it.key) to it.value }
        fun champ(svKey: String, actuel: String?): String? =
            if (parSvKey.containsKey(svKey)) parSvKey[svKey]?.ifEmpty { null } else actuel
        return d.copy(
            nombreMin = n,
            nombreMax = m,
            sexe = champ("sexe", d.sexe),
            stadeVie = champ("stadeVie", d.stadeVie),
            objDenbr = champ("objDenbr", d.objDenbr),
            typDenbr = champ("typDenbr", d.typDenbr),
            additionalFields = additionnels,
        )
    }
}
