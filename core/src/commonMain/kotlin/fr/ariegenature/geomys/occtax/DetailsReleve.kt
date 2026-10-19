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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/DetailsReleveDialog.kt) :
// le résultat du dialogue, sorti du fichier de l'écran.

package fr.ariegenature.geomys.occtax

/** Résultat du dialog « Détails du relevé » : les sélections éditables (jeu de données,
 *  observateur) + les champs additionnels OCCTAX_RELEVE collectés. */
data class DetailsReleveResult(
    val idDataset: Int?,
    /** Observateurs sélectionnés (ids), dans l'ordre d'ajout. Vide = aucun (→ utilisateur connecté). */
    val idsObservateurs: List<Int>,
    /** Libellés des observateurs sélectionnés, alignés sur [idsObservateurs]. */
    val nomsObservateurs: List<String>,
    val additionnels: Map<String, String>,
    /** Commentaire libre du relevé, "" si vide. */
    val comment: String = "",
    /** Code HABREF de l'habitat sélectionné, null si aucun. */
    val cdHab: Int? = null,
    /** Libellé de l'habitat sélectionné, null si aucun. */
    val habitatLabel: String? = null,
    /** Code nomenclature TYP_GRP (type de regroupement du relevé), "" si non renseigné/masqué. */
    val typGrp: String = "",
    /** Date+heure de début du relevé (epoch millis), null si non géré. */
    val dateDebut: Long? = null,
    /** Date+heure de fin du relevé (epoch millis), null si non géré. */
    val dateFin: Long? = null,
    /** Champs relevé supplémentaires saisis (clé = clé `form_fields`), seulement ceux visibles +
     *  renseignés ; les champs masqués conservent leur valeur initiale. */
    val champsExtra: Map<String, String> = emptyMap(),
    /** true si l'utilisateur a réellement MODIFIÉ la date ou l'heure de DÉBUT dans ce dialogue
     *  (≠ simplement validé la valeur par défaut). Signale une saisie a posteriori : la date
     *  se propage aux relevés suivants et la carte garde l'emprise du relevé précédent
     *  (cf. SessionSaisieOcctax.saisieAPosteriori). */
    val dateModifiee: Boolean = false,
)
