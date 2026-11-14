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

// Propre au portage : l'écran « Détails » de la saisie multi-taxons simplifiée (demande du 14 novembre
// 2026). Ses champs viennent du dénombrement n° 0 (nombre, stade de vie, sexe) et de la caractérisation
// (état biologique) ; les règles sont celles de ces deux écrans (occtax/FormulairesEspece), le retour
// celui de occtax/SaisieSimplifiee.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.occtax.ChampNomenclature
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.occtax.FormulairesEspece
import fr.ariegenature.geomys.occtax.RequisEspece
import fr.ariegenature.geomys.occtax.SaisieSimplifiee
import fr.ariegenature.geomys.saisie.ChampsTaxon
import fr.ariegenature.geomys.saisie.EtatChampsAdditionnels
import fr.ariegenature.geomys.store.champFormVisible

/** « Détails » de l'espèce [espece] en saisie simplifiée. [idDataset] : le jeu de données du relevé,
 *  dont dépendent les champs additionnels obligatoires, montrés ici puisque les écrans qui les portent
 *  ne s'ouvrent pas. */
class DetailsEspeceViewModel(
    services: ServicesGeoNature,
    private val espece: EspeceReleve,
    idDataset: Int?,
) : ViewModel() {
    private val config = services.config
    private val nomenclatures = services.nomenclatures
    private val formFields = config.formFieldsJson

    /** Le nom de l'espèce, sous le titre. */
    val nomEspece = espece.espece

    /** Le taxon de l'espèce, qui filtre les valeurs proposées comme sur le web. */
    private val groupe = ChampsTaxon.groupeTaxonomique(espece.taxon, espece.cdNom, services.taxRef)

    private val denombrement = espece.denombrements().first()

    /** Les champs de [liste] que le serveur montre et qui ont un sens pour le groupe de l'espèce. */
    private fun champs(liste: List<Pair<String, String>>): List<ChampNomenclature> =
        FormulairesEspece.champsVisibles(liste, formFields)
            .map { FormulairesEspece.champ(it, nomenclatures, groupe, config.nomenclaturesFiltreesParTaxon) }
            .filter { SaisieSimplifiee.pertinent(it, groupe, nomenclatures) }

    private val champsDenombrement = champs(SaisieSimplifiee.DETAILS_DENOMBREMENT)
    private val champsOccurrence = champs(SaisieSimplifiee.DETAILS_OCCURRENCE)

    /** Stade de vie, sexe, puis état biologique : ceux que le serveur montre, pour le groupe de
     *  l'espèce (pas de sexe pour une plante). */
    val champs: List<ChampNomenclature> = champsDenombrement + champsOccurrence

    /** Le nombre, si le serveur montre le nombre min. */
    val nombreVisible = champFormVisible(formFields, SaisieSimplifiee.CLE_NOMBRE)

    var nombre by mutableStateOf(SaisieSimplifiee.nombreInitial(espece))

    /** La valeur portée par l'espèce pour [c]. */
    private fun courant(c: ChampNomenclature): String =
        if (c in champsDenombrement) FormulairesEspece.valeurDenombrement(denombrement, c.champ) else FormulairesEspece.valeurOccurrence(espece, c.champ)

    /** Position choisie de chaque champ, par code : la valeur de l'espèce, sinon le défaut du serveur. */
    val positions = mutableStateMapOf<String, Int>().apply {
        champs.forEach { c -> put(c.champ.code, FormulairesEspece.positionInitiale(c, courant(c), nomenclatures)) }
    }
    private val positionsInitiales: Map<String, Int> = positions.toMap()

    private val requis = RequisEspece.pour(config.additionalFieldsOcctaxJson, idDataset)
    private val observateurs = DetailsReleveFormulaire.observateurs(config)

    /** Les champs additionnels obligatoires de l'occurrence et du dénombrement, null s'il n'y en a pas. */
    val additionnelsOccurrence: EtatChampsAdditionnels? = requis.occurrence.takeIf { it.isNotEmpty() }?.let {
        EtatChampsAdditionnels(it, espece.additionalFieldsOccurrence, observateurs, services.taxRef)
    }
    val additionnelsDenombrement: EtatChampsAdditionnels? = requis.denombrement.takeIf { it.isNotEmpty() }?.let {
        EtatChampsAdditionnels(it, denombrement.additionalFields, observateurs, services.taxRef)
    }

    fun saisirNombre(texte: String) {
        nombre = texte
    }

    fun ajusterNombre(delta: Int) {
        nombre = FormulairesEspece.ajuster(nombre, delta, FormulairesEspece.PLANCHER_MIN)
    }

    private fun valeurs(): List<Any?> =
        listOf(nombre, positions.toMap(), additionnelsOccurrence?.collecter(), additionnelsDenombrement?.collecter())

    private val initiales = valeurs()

    /** L'écran a changé depuis son ouverture. */
    fun modifie(): Boolean = valeurs() != initiales

    /** ✓ : ce qu'il faut écrire dans l'espèce, ou le message d'un champ obligatoire vide (l'écran
     *  reste). La transformation s'applique à l'espèce telle qu'elle est AU MOMENT du ✓, pas à celle de
     *  l'ouverture : une photo rattachée entre-temps n'est pas écrasée (audit du 14 novembre 2026, M50). */
    fun valider(): Pair<((EspeceReleve) -> EspeceReleve)?, String?> {
        val valeurs = champs.associate { c ->
            val code = c.champ.code
            code to FormulairesEspece.valeurRetenue(c, courant(c), nomenclatures.defautPour(code), positionsInitiales[code] ?: 0, positions[code] ?: 0)
        }
        if (nombreVisible) SaisieSimplifiee.refusNombre(nombre)?.let { return null to it }
        FormulairesEspece.manque(champs, valeurs)?.let { return null to it }
        additionnelsOccurrence?.refus()?.let { return null to it }
        additionnelsDenombrement?.refus()?.let { return null to it }
        val nombreSaisi = nombre.takeIf { nombreVisible }
        val occurrence = additionnelsOccurrence?.collecter().orEmpty()
        val comptage = additionnelsDenombrement?.collecter().orEmpty()
        val transformation = { courante: EspeceReleve ->
            SaisieSimplifiee.appliquerDetails(courante, nombreSaisi ?: courante.nombre.toString(), valeurs, occurrence, comptage)
        }
        return transformation to null
    }
}
