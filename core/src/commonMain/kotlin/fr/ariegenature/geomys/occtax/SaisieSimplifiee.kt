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
// ajoutée ouvre un écran à cinq boutons : « Détails » (nombre, stade de vie, sexe du dénombrement,
// état biologique de la caractérisation), « Comportement » (celui de la caractérisation),
// « Commentaire » (celui de l'occurrence, ajouté le même jour), « Photo » et « Audio ». Ces champs, répartis autrement que dans les écrans Dénombrement et Caractérisation,
// reviennent à leur place dans l'espèce ; les autres gardent leur valeur, le défaut du serveur pour une
// espèce jamais ouverte.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.store.GroupeTaxonomique
import fr.ariegenature.geomys.store.NomenclatureCache

object SaisieSimplifiee {

    /** « Détails » : les nomenclatures du dénombrement n° 0 (code, clé form_fields). */
    val DETAILS_DENOMBREMENT = listOf("STADE_VIE" to "life_stage", "SEXE" to "sex")

    /** « Détails » : la nomenclature de la caractérisation. */
    val DETAILS_OCCURRENCE = listOf("ETA_BIO" to "bio_condition")

    /** La clé form_fields du nombre : celle du nombre min. */
    const val CLE_NOMBRE = "count_min"

    /** La clé form_fields du comportement : sans elle, pas de bouton « Comportement ». */
    const val CLE_COMPORTEMENT = "behaviour"

    /** La clé form_fields du commentaire de l'occurrence : sans elle, pas de bouton « Commentaire ». */
    const val CLE_COMMENTAIRE = "comment_occ"

    /** Les champs qui n'ont de sens que pour un animal : ni sexe ni comportement pour une plante ou un
     *  champignon (demande du 14 novembre 2026). */
    val ANIMAUX_SEULEMENT = setOf("SEXE", "OCC_COMPORTEMENT")

    private val ENTIER = Regex("[0-9]+")

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

    /** Le nombre de « Détails » au départ : le nombre min du dénombrement n° 0. */
    fun nombreInitial(espece: EspeceReleve): String = espece.nombre.toString()

    /** Le refus du nombre tapé, comme les effectifs du web (un entier positif, requis), ou null. */
    fun refusNombre(texte: String): String? = when {
        texte.isEmpty() -> "Renseignez le nombre"
        !ENTIER.matches(texte) -> "Nombre entier attendu"
        // Dix chiffres ou plus : illisible, il était enregistré comme 1 (audit du 14 novembre 2026, M54).
        texte.toIntOrNull() == null -> "Nombre trop grand"
        else -> null
    }

    /** ✓ de « Détails » : le dénombrement n° 0 prend le [nombre] (minimum et maximum ; un maximum
     *  saisi en mode pro reste tant que le nombre ne change pas), le stade de vie et le sexe ;
     *  l'occurrence, l'état biologique. [nomenclatures] : code → valeur retenue des champs AFFICHÉS,
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
    ): EspeceReleve {
        val c0 = espece.denombrements().first()
        val (n, _) = FormulairesEspece.effectifs(nombre, nombre)
        val max = if (n == c0.nombreMin) c0.nombreMax else n
        val codesDenombrement = DETAILS_DENOMBREMENT.map { it.first }.toSet()
        val compte = FormulairesEspece.appliquerDenombrement(
            c0,
            n.toString(),
            max.toString(),
            nomenclatures.filterKeys { it in codesDenombrement },
            c0.additionalFields + additionnelsDenombrement,
        )
        val avecCompte = espece.avecDenombrements(listOf(compte) + espece.denombrementsAdditionnels)
        val codesOccurrence = DETAILS_OCCURRENCE.map { it.first }.toSet()
        return FormulairesEspece.appliquerCaracterisation(
            avecCompte,
            nomenclatures.filterKeys { it in codesOccurrence },
            determinateur = null,
            notes = null,
            preuves = emptyMap(),
            additionnels = if (additionnelsOccurrence.isEmpty()) null else avecCompte.additionalFieldsOccurrence + additionnelsOccurrence,
        )
    }

    /** ✓ de « Commentaire » : le commentaire de l'occurrence, celui que la caractérisation range sous
     *  « Avancé » en mode pro ; des espaces seuls n'en font pas un. */
    fun appliquerCommentaire(espece: EspeceReleve, texte: String): EspeceReleve = espece.copy(notes = texte.trim())

    /** « Photo », « Audio » : les médias s'ajoutent au dénombrement n° 0, comme dans l'écran
     *  Dénombrement. */
    fun ajouterMedias(espece: EspeceReleve, uris: List<String>): EspeceReleve =
        espece.copy(mediaUrisCounting0 = espece.mediaUrisCounting0 + uris.filter { it !in espece.mediaUrisCounting0 })

    fun retirerMedia(espece: EspeceReleve, uri: String): EspeceReleve =
        espece.copy(mediaUrisCounting0 = espece.mediaUrisCounting0 - uri)
}
