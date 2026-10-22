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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabHabitatFragment.kt) :
// les champs du formulaire d'un habitat, leurs règles (requis, « Autre, préciser », défauts) et
// l'habitat retenu à la validation, hors des vues.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.util.uuidAleatoire

/** Une nomenclature du formulaire d'un habitat : sa clé dans `OCCHAB.formConfig`, son type de
 *  nomenclature et son libellé. */
data class NomenclatureHabitat(val cle: String, val type: String, val libelle: String)

/** Les saisies du formulaire d'un habitat à la validation. Un champ texte masqué par formConfig
 *  vaut null (sa valeur existante est préservée). */
data class SaisiesHabitat(
    val cdHab: Int?,
    val libelle: String,
    val determinateur: String?,
    val precision: String?,
    val recouvrement: String?,
    val interet: Int?,
    val typeDetermination: Int?,
    val technique: Int?,
    val abondance: Int?,
    /** Bloc ANA-EVAL relu de la section (habitat porteur d'un bloc), sinon null. */
    val anaEval: (() -> String?)? = null,
    /** L'habitat a été choisi dans la liste pendant cette édition : le nom cité devient son libellé,
     *  comme sur le web (`patchNomCite` à chaque sélection). Sinon l'habitat garde le sien. */
    val choisiDansLaListe: Boolean = true,
)

/** Le formulaire de création / d'édition d'UN habitat OccHab (champs pilotés par
 *  `OCCHAB.formConfig`). */
object HabitatOccHab {

    // Les textes du formulaire web (occhab-form.component.html), sans astérisques comme lui.
    const val TITRE = "Habitat"
    const val LIBELLE_HABITAT = "Habitat"
    const val INVITE_HABITAT = "Tapez les premières lettres..."
    const val AUCUN_RESULTAT = "Aucun élément trouvé"
    const val AIDE_FACULTATIF =
        "La station est déjà enregistrée — l'habitat est facultatif : revenez en arrière pour la laisser sans habitat."
    const val LIBELLE_DETERMINATEUR = "Déterminateur"
    const val LIBELLE_RECOUVREMENT = "Pourcentage de recouvrement"
    /** La précision n'a pas de libellé sur le web : seulement cette indication. */
    const val INVITE_PRECISION = "Précision sur la technique de collecte"
    const val PRECISION_MANQUANTE = "Veuillez fournir des précisions concernant la technique de collecte"
    const val VALIDER = "Valider cet habitat"

    // Le libellé du web, accents rétablis (« interet »).
    val INTERET = NomenclatureHabitat("community_interest", "HAB_INTERET_COM", "Habitat d'intérêt communautaire")
    val TYPE_DETERMINATION = NomenclatureHabitat("determination_type", "DETERMINATION_TYP_HAB", "Type de détermination")
    val TECHNIQUE = NomenclatureHabitat("collection_technique", "TECHNIQUE_COLLECT_HAB", "Technique de collecte")
    val ABONDANCE = NomenclatureHabitat("abundance", "ABONDANCE_HAB", "Abondance")

    /** Les listes qui ont une entrée vide sur le web (`displayNullValue`) ; les autres n'en ont
     *  pas. */
    fun avecEntreeVide(d: NomenclatureHabitat): Boolean = d == INTERET || d == ABONDANCE

    /** Les nomenclatures d'un NOUVEL habitat que le web pré-remplit du défaut OccHab du serveur
     *  (initHabForm) ; l'intérêt communautaire et l'abondance partent vides. */
    private val TYPES_A_DEFAUT = setOf(TYPE_DETERMINATION.type, TECHNIQUE.type)

    /** Défaut d'une nomenclature : la valeur de l'habitat édité, sinon — pour un NOUVEL habitat —
     *  le défaut OccHab du serveur, pour les seules listes que le web pré-remplit, affichées ou
     *  masquées (le web le pose sur le contrôle, même caché, et l'envoie). */
    fun valeurInitiale(existante: Int?, type: String, nouveau: Boolean, config: GeoNatureConfig): Int? =
        existante ?: if (nouveau && type in TYPES_A_DEFAUT) config.occhabDefautNomenclature(type) else null

    /** La technique choisie vaut-elle « Autre, préciser » (cd_nomenclature « 10 ») ? Un cd absent
     *  du cache (synchro antérieure à l'ajout du champ) rend la règle inactive. */
    fun techniqueEstAutre(technique: Int?, valeurs: List<NomValeur>): Boolean {
        val id = technique ?: return false
        return valeurs.firstOrNull { it.id == id }?.cd == "10"
    }

    /** « Valider cet habitat » actif SEULEMENT quand les champs REQUIS sont remplis, comme les
     *  Validators.required du web (form-service.ts) : l'habitat HABREF, la technique de collecte
     *  (affichée ou non : masquée, elle garde son défaut, et sans lui le web reste bloqué), la
     *  précision si la technique vaut « Autre, préciser » ([precision] null : champ masqué, donc
     *  vide). */
    fun peutValider(cdHab: Int?, technique: Int?, precisionRequise: Boolean, precision: String?): Boolean =
        (cdHab ?: 0) > 0 && technique != null && (!precisionRequise || !precision?.trim().isNullOrEmpty())

    /** Garde MIROIR du bouton grisé (course clic/sélection) : le message du premier manque, ou
     *  null. */
    fun manque(cdHab: Int?, technique: Int?, precisionRequise: Boolean, precision: String?): String? = when {
        technique == null -> "Choisissez une technique de collecte"
        precisionRequise && precision?.trim().isNullOrEmpty() -> PRECISION_MANQUANTE
        (cdHab ?: 0) <= 0 -> "Choisissez un habitat dans la liste HABREF"
        else -> null
    }

    /** Recouvrement affiché : entier sans « .0 ». */
    fun texteRecouvrement(v: Double?): String =
        v?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }.orEmpty()

    /** L'habitat retenu : [existant] mis à jour, ou un nouvel habitat. Champ VISIBLE → sa valeur
     *  fait foi (vide = effacé) ; champ MASQUÉ par formConfig (null) → la valeur existante est
     *  préservée. Le nom cité devient le libellé de l'habitat choisi dans la liste, comme sur le web ;
     *  sans nouveau choix, l'habitat garde ses noms. Le recouvrement accepte la virgule décimale. */
    fun habitat(existant: OccHabHabitat?, s: SaisiesHabitat): OccHabHabitat {
        val cd = s.cdHab ?: 0
        val libelle = s.libelle.trim()
        val rechoisi = s.choisiDansLaListe || existant == null
        return (existant ?: OccHabHabitat()).copy(
            // Son identifiant SINP dès sa création : l'envoi le transmet, le serveur le garde, et un
            // renvoi le reconnaît (audit du 21 octobre, C5).
            uuidHabitat = existant?.uuidHabitat ?: uuidAleatoire(),
            cdHab = cd,
            habitatLabel = if (rechoisi) libelle else existant.habitatLabel,
            nomCite = if (rechoisi) libelle else existant.nomCite.ifBlank { libelle },
            determiner = s.determinateur?.trim()?.takeIf { it.isNotEmpty() } ?: if (s.determinateur == null) existant?.determiner else null,
            precisionTechnique = s.precision?.trim()?.takeIf { it.isNotEmpty() } ?: if (s.precision == null) existant?.precisionTechnique else null,
            recouvrement = s.recouvrement?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: if (s.recouvrement == null) existant?.recouvrement else null,
            idNomTechniqueCollecte = s.technique,
            idNomAbondance = s.abondance,
            idNomTypeDetermination = s.typeDetermination,
            idNomInteretCommunautaire = s.interet,
            // Bloc ANA-EVAL : la SECTION fait foi quand elle est affichée (null = bloc vidé, il
            // disparaîtra du texte à l'envoi) ; sinon le bloc existant traverse tel quel.
            anaEvalJson = s.anaEval.let { if (it != null) it() else existant?.anaEvalJson },
        )
    }
}
