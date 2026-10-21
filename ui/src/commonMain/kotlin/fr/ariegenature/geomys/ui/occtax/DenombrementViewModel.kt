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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/DenombrementFragment.kt) : la liste
// des dénombrements d'une espèce, gardée dans l'état de l'écran au lieu de ses vues ; les règles (effectifs,
// nomenclatures) sont dans occtax/FormulairesEspece.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.occtax.ChampNomenclature
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.occtax.FormulairesEspece
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.saisie.ChampsTaxon
import fr.ariegenature.geomys.saisie.EtatChampsAdditionnels
import fr.ariegenature.geomys.store.champFormVisible

/** Un dénombrement à l'écran : effectifs tels que tapés, nomenclatures (position par code),
 *  médias, champs additionnels. [base] garde ce que l'écran ne montre pas. */
class CarteDenombrement(
    val base: Denombrement,
    min: String,
    max: String,
    val positions: SnapshotStateMap<String, Int>,
    val medias: SnapshotStateList<String>,
    val additionnels: EtatChampsAdditionnels,
) {
    /** Les positions à l'ouverture ([FormulairesEspece.valeurRetenue]). */
    val positionsInitiales: Map<String, Int> = positions.toMap()

    var min by mutableStateOf(min)
    var max by mutableStateOf(max)
}

/** Les dénombrements de l'espèce [espece] (le n° 0 tiré de ses champs à plat). [idDataset] : le
 *  jeu de données du relevé, dont dépendent les champs additionnels. */
class DenombrementViewModel(
    private val services: ServicesGeoNature,
    espece: EspeceReleve,
    idDataset: Int?,
) : ViewModel() {
    private val config = services.config
    private val nomenclatures = services.nomenclatures
    private val formFields = config.formFieldsJson

    /** Le nom de l'espèce, sous le titre. */
    val nomEspece = espece.espece

    /** Le taxon de l'espèce, qui filtre les valeurs proposées comme sur le web. */
    private val groupe = ChampsTaxon.groupeTaxonomique(espece.taxon, espece.cdNom, services.taxRef)

    /** Les nomenclatures d'un dénombrement, sous les libellés du web (« Stade de vie » aussi pour une
     *  plante). */
    val champs: List<ChampNomenclature> = FormulairesEspece.champsVisibles(FormulairesEspece.COUNTING, formFields).map { champ ->
        FormulairesEspece.champ(champ, nomenclatures, groupe, config.nomenclaturesFiltreesParTaxon)
    }

    /** Objet et type, au-dessus des effectifs ; stade et sexe, dessous. */
    val champsAvantEffectifs = champs.filter { it.champ.code in FormulairesEspece.COUNTING_AVANT_EFFECTIFS }
    val champsApresEffectifs = champs.filter { it.champ.code !in FormulairesEspece.COUNTING_AVANT_EFFECTIFS }

    val minVisible = champFormVisible(formFields, "count_min")
    val maxVisible = champFormVisible(formFields, "count_max")

    /** Le bloc Médias, si le serveur l'active (`OCCTAX.ENABLE_MEDIAS`). */
    val mediasVisibles = config.occtaxMediasActifs

    /** Les observateurs proposés aux champs additionnels `observers`. */
    private val observateurs = DetailsReleveFormulaire.observateurs(config)

    /** Champs additionnels OCCTAX_DENOMBREMENT du jeu de données du relevé. */
    private val defs = ChampsAdditionnels.defsPour(config.additionalFieldsOcctaxJson, AdditionalFieldsObject.COUNTING, idDataset)

    val cartes = mutableStateListOf<CarteDenombrement>().apply { espece.denombrements().forEach { add(carte(it)) } }

    /** Dossier des copies locales des médias. */
    val dossierMedias: String = services.cheminMedias

    /** Les médias présents à l'ouverture, et ceux retirés depuis : leurs fichiers ne s'effacent qu'à
     *  la validation, pour qu'un « Quitter sans enregistrer » les rende à l'espèce. */
    private val mediasInitiaux: Set<String> = cartes.flatMap { it.medias }.toSet()
    private val retires = mutableListOf<String>()

    /** Les valeurs saisissables, pour la garde de sortie. */
    private fun valeurs(): List<Any?> =
        cartes.map { c -> listOf(c.base, c.min, c.max, c.positions.toMap(), c.medias.toList(), c.additionnels.collecter()) }

    private val initiales = valeurs()

    /** L'écran a changé depuis son ouverture. */
    fun modifie(): Boolean = valeurs() != initiales

    private fun carte(d: Denombrement) = CarteDenombrement(
        base = d,
        min = d.nombreMin.toString(),
        max = d.nombreMax.toString(),
        // La valeur du dénombrement, sinon le défaut du serveur : pas de report d'une espèce à l'autre.
        positions = mutableStateMapOf<String, Int>().apply {
            champs.forEach { c ->
                put(c.champ.code, FormulairesEspece.positionInitiale(c, FormulairesEspece.valeurDenombrement(d, c.champ), nomenclatures))
            }
        },
        medias = mutableStateListOf<String>().apply { addAll(d.mediaUris) },
        additionnels = EtatChampsAdditionnels(defs, d.additionalFields, observateurs, services.taxRef),
    )

    /** « ＋ Ajouter un dénombrement » : un nouveau, de 1. */
    fun ajouter() {
        cartes.add(carte(Denombrement()))
    }

    /** Supprime le dénombrement [index] (il en reste au moins un) ; les copies locales de ses
     *  médias s'effaceront à la validation. */
    fun supprimer(index: Int) {
        if (cartes.size <= 1 || index !in cartes.indices) return
        retires += cartes[index].medias
        cartes.removeAt(index)
    }

    /** Frappe dans le nombre min : le max le suit s'il le suivait. */
    fun saisirMin(carte: CarteDenombrement, texte: String) {
        carte.max = FormulairesEspece.maxApresMin(carte.min, texte, carte.max)
        carte.min = texte
    }

    fun ajusterMin(carte: CarteDenombrement, delta: Int) =
        saisirMin(carte, FormulairesEspece.ajuster(carte.min, delta, FormulairesEspece.PLANCHER_MIN))

    fun ajusterMax(carte: CarteDenombrement, delta: Int) {
        carte.max = FormulairesEspece.ajuster(carte.max, delta, FormulairesEspece.plancherMax(carte.min))
    }

    fun ajouterMedias(index: Int, uris: List<String>) {
        cartes.getOrNull(index)?.medias?.addAll(uris)
    }

    /** Retire un média du dénombrement ; sa copie locale s'effacera à la validation. */
    fun retirerMedia(carte: CarteDenombrement, uri: String) {
        retires += uri
        carte.medias.remove(uri)
    }

    /** « Quitter sans enregistrer » : les médias importés ici s'effacent, ceux retirés restent à
     *  l'espèce. */
    fun abandonner() {
        (cartes.flatMap { it.medias } + retires).filter { it !in mediasInitiaux }.forEach(services::supprimerMedia)
        retires.clear()
    }

    /** La valeur retenue de chaque nomenclature affichée de [c], par code. */
    private fun valeursRetenues(c: CarteDenombrement): Map<String, String> = champs.associate { champ ->
        val code = champ.champ.code
        code to FormulairesEspece.valeurRetenue(
            champ, FormulairesEspece.valeurDenombrement(c.base, champ.champ), nomenclatures.defautPour(code),
            c.positionsInitiales[code] ?: 0, c.positions[code] ?: 0,
        )
    }

    /** ✓ : les dénombrements relus, ou le message d'un champ obligatoire vide ou d'effectifs refusés
     *  par le web (l'écran reste). */
    fun valider(): Pair<List<Denombrement>?, String?> {
        val valeurs = cartes.map { valeursRetenues(it) }
        cartes.indices.firstNotNullOfOrNull { i ->
            FormulairesEspece.manque(champsAvantEffectifs, valeurs[i])
                ?: FormulairesEspece.refusEffectifs(cartes[i].min, cartes[i].max)
                ?: FormulairesEspece.manque(champsApresEffectifs, valeurs[i])
        }?.let { return null to it }
        cartes.firstNotNullOfOrNull { it.additionnels.refus() }?.let { return null to it }
        retires.forEach(services::supprimerMedia)
        retires.clear()
        return cartes.mapIndexed { i, c ->
            FormulairesEspece.appliquerDenombrement(c.base.copy(mediaUris = c.medias.toList()), c.min, c.max, valeurs[i], c.additionnels.collecter())
        } to null
    }
}
