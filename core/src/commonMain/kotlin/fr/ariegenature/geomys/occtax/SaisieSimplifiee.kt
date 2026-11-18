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

// Propre au portage : la saisie multi-taxons simplifiée (demande du 14 novembre 2026). Une espèce
// ajoutée ouvre sa fiche, un seul formulaire depuis le 16 novembre (les boutons « Détails »,
// « Comportement » et « Commentaire » ouvraient chacun un écran) : nombre min et max, stade de vie et
// sexe du dénombrement, état biologique et comportement de la caractérisation, commentaire de
// l'occurrence, « Photo » et « Audio ». Ces champs, répartis autrement que dans les écrans Dénombrement
// et Caractérisation, reviennent à leur place dans l'espèce ; les autres gardent leur valeur, le défaut
// du serveur pour une espèce jamais ouverte.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.store.GroupeTaxonomique
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.NomValeur

object SaisieSimplifiee {

    /** « Détails » : les nomenclatures du dénombrement n° 0 (code, clé form_fields). */
    val DETAILS_DENOMBREMENT = listOf("SEXE" to "sex", "STADE_VIE" to "life_stage")

    /** « Détails » : la nomenclature de la caractérisation. */
    val DETAILS_OCCURRENCE = listOf("ETA_BIO" to "bio_condition")

    /** Les clés form_fields des nombres min et max. */
    const val CLE_NOMBRE = "count_min"
    const val CLE_NOMBRE_MAX = "count_max"

    /** Les nomenclatures de la caractérisation que porte la fiche : état biologique et comportement. */
    val FICHE_OCCURRENCE = DETAILS_OCCURRENCE + FormulairesEspece.OCC_COMPORTEMENT

    /** La clé form_fields du comportement : sans elle, pas de bouton « Comportement ». */
    const val CLE_COMPORTEMENT = "behaviour"

    /** La clé form_fields du commentaire de l'occurrence : sans elle, pas de bouton « Commentaire ». */
    const val CLE_COMMENTAIRE = "comment_occ"

    /** Les champs qui n'ont de sens que pour un animal : ni sexe ni comportement pour une plante ou un
     *  champignon (demande du 14 novembre 2026). */
    val ANIMAUX_SEULEMENT = setOf("SEXE", "OCC_COMPORTEMENT")

    /** Le `cd_nomenclature` de « Chant » dans les comportements (`OCC_COMPORTEMENT`), stable d'un
     *  serveur à l'autre. */
    const val CD_CHANT = "18"

    /** Le `cd_nomenclature` de l'indice de nidification « Mâle chanteur (ou cris de nidification) en
     *  période de reproduction » (codes atlas des oiseaux nicheurs, sur les serveurs qui les ont). */
    const val CD_MALE_CHANTEUR = "CA2"

    /** Le groupe 2 INPN des oiseaux : le bouton « Chant » n'est proposé que pour eux. */
    const val GROUPE_OISEAUX = "Oiseaux"

    private val ENTIER = Regex("[0-9]+")

    /** [groupe] est celui des oiseaux. */
    fun estOiseau(groupe: GroupeTaxonomique): Boolean = groupe.group2.equals(GROUPE_OISEAUX, ignoreCase = true)

    /** La valeur « Chant » parmi les comportements du serveur ([valeurs]) : par son code, sinon, pour un
     *  cache d'avant les codes, par son libellé. Null si le serveur n'en a pas : pas de bouton. */
    fun valeurChant(valeurs: List<NomValeur>): NomValeur? =
        valeurs.firstOrNull { it.cd == CD_CHANT } ?: valeurs.firstOrNull { it.cd == null && it.libelle.trim().equals("Chant", ignoreCase = true) }

    /** L'indice « Mâle chanteur en période de reproduction » parmi les comportements du serveur, null
     *  s'il n'a pas les codes atlas. */
    fun valeurMaleChanteur(valeurs: List<NomValeur>): NomValeur? = valeurs.firstOrNull { it.cd == CD_MALE_CHANTEUR }

    /** La valeur que pose le bouton « Chant » : en période de nidification de l'espèce ([nicheur]),
     *  l'indice « Mâle chanteur » si le serveur l'a, sinon « Chant » ; hors période, « Chant » seulement
     *  (l'indice n'a pas de sens hors saison). Null : pas de bouton. */
    fun idChantAPoser(idChant: String?, idMaleChanteur: String?, nicheur: Boolean): String? =
        if (nicheur) idMaleChanteur ?: idChant else idChant

    /** Bouton « Chant » (demande du 15 novembre 2026) : le comportement après un toucher. [aPoser] est
     *  posé, ou enlevé ([sansChant], la valeur de repli) si le comportement [courant] est déjà une des
     *  valeurs de chant ([valeursChant]) ; un autre comportement est remplacé. */
    fun comportementApresChant(courant: String, aPoser: String, valeursChant: Set<String> = setOf(aPoser), sansChant: String = ""): String =
        if (courant in valeursChant) sansChant else aPoser

    /** Le champ [champ] a sa place pour un taxon de [groupe] (demande du 14 novembre 2026 : « les champs
     *  présentés doivent être en adéquation avec ceux du groupe taxonomique », le sexe n'existant pas
     *  pour la flore). Le sexe et le comportement, pour les animaux seulement. Et une nomenclature dont
     *  le serveur ne propose aucune valeur pour ce taxon (filtres taxonomiques du serveur) n'a pas de
     *  sens pour lui ; mais sans aucune valeur en cache, le champ reste, pour dire de recharger les
     *  données. En mode pro, comme sur le web, tous les champs restent. */
    fun pertinent(champ: ChampNomenclature, groupe: GroupeTaxonomique, nomenclatures: NomenclatureCache): Boolean {
        val code = champ.champ.code
        if (code in ANIMAUX_SEULEMENT && !groupe.regne.equals("Animalia", ignoreCase = true)) return false
        return !champ.sansValeurs || nomenclatures.get(code).isEmpty()
    }

    /** Le refus des effectifs tapés dans la fiche, ou null. [min] ou [max] null : le serveur ne montre
     *  pas ce nombre, et l'autre se contrôle seul. Les contrôles sont ceux du web
     *  ([FormulairesEspece.refusEffectifs]) ; dix chiffres ou plus sont illisibles, ils étaient
     *  enregistrés comme 1 (audit du 14 novembre 2026, M54). */
    fun refusEffectifs(min: String?, max: String?): String? {
        for (texte in listOfNotNull(min, max)) {
            if (ENTIER.matches(texte) && texte.toIntOrNull() == null) return "Nombre trop grand"
        }
        return when {
            min != null && max != null -> FormulairesEspece.refusEffectifs(min, max)
            min != null -> FormulairesEspece.refusEffectifs(min, min)
            max != null -> FormulairesEspece.refusEffectifs(max, max)?.replace("nombre min", "nombre max")
            else -> null
        }
    }

    /** ✓ de la fiche : le dénombrement n° 0 prend le nombre min [nombre] et le nombre [max] (null : le
     *  serveur ne montre pas le nombre max, qui suit alors le min ; un maximum saisi en mode pro
     *  reste tant que le min ne change pas), le stade de vie et le sexe ; l'occurrence, l'état
     *  biologique et le comportement. [nomenclatures] : code → valeur retenue des champs AFFICHÉS,
     *  un champ masqué garde la sienne. Les champs additionnels obligatoires montrés
     *  ([additionnelsOccurrence], [additionnelsDenombrement]) s'ajoutent aux valeurs déjà portées.
     *  Le reste de l'espèce ne change pas : objet et type du dénombrement, autres dénombrements,
     *  médias, autres champs de la caractérisation. */
    fun appliquerDetails(
        espece: EspeceReleve,
        nombre: String,
        nomenclatures: Map<String, String>,
        additionnelsOccurrence: Map<String, String> = emptyMap(),
        additionnelsDenombrement: Map<String, String> = emptyMap(),
        max: String? = null,
    ): EspeceReleve {
        val c0 = espece.denombrements().first()
        val (n, saisi) = FormulairesEspece.effectifs(nombre, max ?: nombre)
        val max = if (max != null) saisi else if (n == c0.nombreMin) c0.nombreMax else n
        val codesDenombrement = DETAILS_DENOMBREMENT.map { it.first }.toSet()
        val compte = FormulairesEspece.appliquerDenombrement(
            c0,
            n.toString(),
            max.toString(),
            nomenclatures.filterKeys { it in codesDenombrement },
            c0.additionalFields + additionnelsDenombrement,
        )
        val avecCompte = espece.avecDenombrements(listOf(compte) + espece.denombrementsAdditionnels)
        val codesOccurrence = FICHE_OCCURRENCE.map { it.first }.toSet()
        return FormulairesEspece.appliquerCaracterisation(
            avecCompte,
            nomenclatures.filterKeys { it in codesOccurrence },
            determinateur = null,
            notes = null,
            preuves = emptyMap(),
            additionnels = if (additionnelsOccurrence.isEmpty()) null else avecCompte.additionalFieldsOccurrence + additionnelsOccurrence,
        )
    }

    /** Le commentaire de la fiche : le commentaire de l'occurrence, celui que la caractérisation range sous
     *  « Avancé » en mode pro ; des espaces seuls n'en font pas un. */
    fun appliquerCommentaire(espece: EspeceReleve, texte: String): EspeceReleve = espece.copy(notes = texte.trim())

    /** « Photo », « Audio » : les médias s'ajoutent au dénombrement n° 0, comme dans l'écran
     *  Dénombrement. */
    fun ajouterMedias(espece: EspeceReleve, uris: List<String>): EspeceReleve =
        espece.copy(mediaUrisCounting0 = espece.mediaUrisCounting0 + uris.filter { it !in espece.mediaUrisCounting0 })

    fun retirerMedia(espece: EspeceReleve, uri: String): EspeceReleve =
        espece.copy(mediaUrisCounting0 = espece.mediaUrisCounting0 - uri)
}
