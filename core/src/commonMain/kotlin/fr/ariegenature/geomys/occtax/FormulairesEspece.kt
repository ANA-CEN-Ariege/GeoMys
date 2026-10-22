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

// Extrait de GeoMys-Android @ cdd5196 : la logique des écrans Caractérisation et Dénombrement d'une espèce
// (ui/CaracterisationFragment.kt, ui/DenombrementFragment.kt, ui/saisie/OcctaxFieldsRenderer.kt et
// groupe2InpnPour de ui/SaisieObservationFragment.kt), sortie des vues et de leurs tags.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.plateforme.comparateurFrancais
import fr.ariegenature.geomys.store.GroupeTaxonomique
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.OcctaxFieldsConfig
import fr.ariegenature.geomys.store.OcctaxFieldsConfig.OcctaxField
import fr.ariegenature.geomys.store.champFormVisible

/** Un champ de nomenclature affiché : son [libelle] et ses entrées, l'entrée vide
 *  ([FormulairesEspece.VIDE]) d'abord ([libelles] et [codes] alignés, code vide pour la première). */
data class ChampNomenclature(
    val champ: OcctaxField,
    val libelle: String,
    val libelles: List<String>,
    val codes: List<String>,
) {
    /** Le formulaire web exige une valeur ([FormulairesEspece.OBLIGATOIRES]). */
    val obligatoire: Boolean get() = champ.code in FormulairesEspece.OBLIGATOIRES

    /** Aucune valeur du serveur en cache pour ce taxon : seule l'entrée vide. */
    val sansValeurs: Boolean get() = codes.size <= 1

    /** La position d'un code ; absent des entrées, l'entrée vide. */
    fun position(code: String): Int = codes.indexOf(code).coerceAtLeast(0)

    fun code(position: Int): String = codes.getOrNull(position) ?: ""
}

object FormulairesEspece {

    /** L'entrée vide d'une liste, distincte de la valeur « Non renseigné » que portent plusieurs
     *  nomenclatures du serveur. */
    const val VIDE = "— Non renseigné —"

    /** Les nomenclatures que le formulaire web rend obligatoires (occurrence.service.ts,
     *  counting.service.ts) : l'entrée vide n'y est pas une réponse. */
    val OBLIGATOIRES = setOf("METH_OBS", "ETA_BIO", "STADE_VIE", "SEXE", "OBJ_DENBR")

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
        Triple("digital_proof", "Preuve numérique", true),
        Triple("non_digital_proof", "Preuve non numérique", false),
    )

    /** Dénombrement : les nomenclatures d'un dénombrement, dans l'ordre du web. */
    val COUNTING = listOf("OBJ_DENBR" to "obj_count", "TYP_DENBR" to "type_count", "STADE_VIE" to "life_stage", "SEXE" to "sex")

    /** Celles que le web place au-dessus des effectifs. */
    val COUNTING_AVANT_EFFECTIFS = setOf("OBJ_DENBR", "TYP_DENBR")

    /** Les messages du web pour une nomenclature obligatoire laissée vide (fr.json d'OccTax). */
    private val MESSAGES_OBLIGATOIRES = mapOf(
        "METH_OBS" to "Veuillez indiquer la technique d'observation",
        "ETA_BIO" to "Veuillez indiquer l'état biologique",
        "OBJ_DENBR" to "Veuillez indiquer l'objet du dénombrement",
        "STADE_VIE" to "Veuillez indiquer le stade de vie",
        "SEXE" to "Veuillez indiquer le sexe",
    )

    /** Les champs de [liste] que form_fields montre. */
    fun champsVisibles(liste: List<Pair<String, String>>, formFieldsJson: String): List<OcctaxField> =
        liste.mapNotNull { (code, cle) -> if (champFormVisible(formFieldsJson, cle)) OcctaxFieldsConfig.parCode[code] else null }

    /** Les nomenclatures que le web ne filtre pas par taxon (sans `regne` dans
     *  occurrence.component.html). */
    val SANS_FILTRE_TAXON = setOf("STATUT_OBS", "STATUT_SOURCE", "DEE_FLOU")

    /** Les entrées d'un champ : l'entrée vide, puis les valeurs du serveur que le web propose pour le
     *  [taxon] ([NomenclatureCache.filtrerPourTaxon] ; [filtresActifs] :
     *  `ENABLE_NOMENCLATURE_TAXONOMIC_FILTERS` ; jamais pour [SANS_FILTRE_TAXON]), toutes (deux
     *  valeurs de même libellé restent deux valeurs), sous leur `label_fr`, dans l'ordre de leur
     *  `label_default` (`orderby: 'label_default'` du web). Sans valeur en cache, rien d'autre :
     *  aucune valeur n'est inventée. */
    fun champ(
        champ: OcctaxField,
        nomenclatures: NomenclatureCache,
        taxon: GroupeTaxonomique,
        filtresActifs: Boolean,
        libelle: String = champ.label,
    ): ChampNomenclature {
        val filtre = if (champ.code in SANS_FILTRE_TAXON) GroupeTaxonomique.AUCUN else taxon
        val valeurs = nomenclatures.filtrerPourTaxon(champ.code, filtre, filtresActifs)
        val comparateur = comparateurFrancais()
        val triees = valeurs.sortedWith { a, b -> comparateur.compare(a.label, b.label) }
        return ChampNomenclature(champ, libelle, listOf(VIDE) + triees.map { it.libelle }, listOf("") + triees.map { it.id.toString() })
    }

    /** Position de départ d'un champ : la valeur portée ([courant]), sinon le défaut du serveur. */
    fun positionInitiale(champ: ChampNomenclature, courant: String, nomenclatures: NomenclatureCache): Int =
        champ.position(courant.ifEmpty { nomenclatures.defautPour(champ.champ.code) ?: "" })

    /** La valeur à retenir d'un champ ouvert sur [courant] à [positionInitiale] et laissé à
     *  [position] : celle de la liste si l'utilisateur en a changé ; sinon [courant], ou à défaut le
     *  [defaut] du serveur, même absents des valeurs proposées pour ce taxon (affichés vides, ils
     *  auraient été effacés ; le web garde de même le défaut qu'il pose dans le champ). */
    fun valeurRetenue(champ: ChampNomenclature, courant: String, defaut: String?, positionInitiale: Int, position: Int): String =
        if (position == positionInitiale) courant.ifEmpty { defaut.orEmpty() } else champ.code(position)

    /** Le message du premier champ [champs] obligatoire laissé vide ([valeurs] : code → valeur
     *  retenue), celui du web, ou null. Une liste sans valeur en cache appelle un rechargement des
     *  données. */
    fun manque(champs: List<ChampNomenclature>, valeurs: Map<String, String>): String? {
        val vide = champs.firstOrNull { it.obligatoire && valeurs[it.champ.code].isNullOrEmpty() } ?: return null
        return if (vide.sansValeurs) "« ${vide.libelle} » : liste vide, rechargez les données"
        else MESSAGES_OBLIGATOIRES[vide.champ.code] ?: "Choisissez : ${vide.libelle}"
    }

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

    /** Le plancher du nombre min : 0, comme le web (`min="0"`) et la base (`count_min >= 0`). */
    const val PLANCHER_MIN = 0

    /** Le plancher du nombre max : le min, au moins 0. */
    fun plancherMax(min: String): Int = min.toIntOrNull()?.coerceAtLeast(PLANCHER_MIN) ?: PLANCHER_MIN

    /** Les contrôles des effectifs du web (counting.service.ts) : nombre min et max requis, entiers
     *  positifs (`[0-9]+`), et le max pas sous le min ; null si tout va. */
    fun refusEffectifs(min: String, max: String): String? {
        val entier = Regex("[0-9]+")
        if (min.isEmpty()) return "Renseignez le nombre min"
        if (max.isEmpty()) return "Renseignez le nombre max"
        if (!entier.matches(min) || !entier.matches(max)) return "Nombre entier attendu"
        val n = min.toIntOrNull() ?: return "Nombre entier attendu"
        val m = max.toIntOrNull() ?: return "Nombre entier attendu"
        return if (m < n) "Le dénombrement maximum ne peut pas être inférieur au dénombrement minimum" else null
    }

    /** Les effectifs relus : le min (au moins 0, 1 s'il est illisible), le max (au moins le min, le
     *  min s'il est illisible). */
    fun effectifs(min: String, max: String): Pair<Int, Int> {
        val n = min.toIntOrNull()?.coerceAtLeast(PLANCHER_MIN) ?: 1
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
