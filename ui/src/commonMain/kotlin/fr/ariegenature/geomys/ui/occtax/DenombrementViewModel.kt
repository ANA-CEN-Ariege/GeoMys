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
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.occtax.ChampNomenclature
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
    var min by mutableStateOf(min)
    var max by mutableStateOf(max)
}

/** Les dénombrements de l'espèce [espece] (le n° 0 tiré de ses champs à plat). */
class DenombrementViewModel(
    private val services: ServicesGeoNature,
    espece: EspeceReleve,
) : ViewModel() {
    private val config = services.config
    private val nomenclatures = services.nomenclatures
    private val formFields = config.formFieldsJson

    /** Le nom de l'espèce, sous le titre. */
    val nomEspece = espece.espece

    private val groupesEtRegno = ChampsTaxon.groupesEtRegno(
        espece.taxon,
        FormulairesEspece.groupe2Inpn(espece, services.taxRef.tousLesGroupes()).orEmpty(),
        nomenclatures,
    )

    /** Les nomenclatures d'un dénombrement ; « Stade de vie » devient « Stade phénologique » pour une
     *  plante. */
    val champs: List<ChampNomenclature> = FormulairesEspece.champsVisibles(FormulairesEspece.COUNTING, formFields).map { champ ->
        val libelle = if (espece.taxon == Taxon.PLANTE && champ.code == "STADE_VIE") "Stade phénologique" else champ.label
        FormulairesEspece.champ(champ, nomenclatures, groupesEtRegno.first, groupesEtRegno.second, libelle)
    }

    val minVisible = champFormVisible(formFields, "count_min")
    val maxVisible = champFormVisible(formFields, "count_max")

    /** Champs additionnels OCCTAX_DENOMBREMENT, restreints au jeu de données et aux listes du taxon. */
    private val defs = run {
        val listesDuTaxon = espece.cdNom?.let { services.taxRef.listesPourCdNom(it) }.orEmpty()
        ChampsAdditionnels.fromJson(config.additionalFieldsOcctaxJsonActif)
            .filter { it.appliqueA(AdditionalFieldsObject.COUNTING) }
            .filter { it.visiblePour(config.idDataset.toIntOrNull(), listesDuTaxon) }
    }

    val cartes = mutableStateListOf<CarteDenombrement>().apply { espece.denombrements().forEach { add(carte(it)) } }

    /** Dossier des copies locales des médias. */
    val dossierMedias: String = services.cheminMedias

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
        additionnels = EtatChampsAdditionnels(defs, d.additionalFields),
    )

    /** « ＋ Ajouter un dénombrement » : un nouveau, de 1. */
    fun ajouter() {
        cartes.add(carte(Denombrement()))
    }

    /** Supprime le dénombrement [index] (il en reste au moins un) et les copies locales de ses
     *  médias. */
    fun supprimer(index: Int) {
        if (cartes.size <= 1 || index !in cartes.indices) return
        cartes[index].medias.forEach(services::supprimerMedia)
        cartes.removeAt(index)
    }

    /** Frappe dans le nombre min : le max le suit s'il le suivait. */
    fun saisirMin(carte: CarteDenombrement, texte: String) {
        carte.max = FormulairesEspece.maxApresMin(carte.min, texte, carte.max)
        carte.min = texte
    }

    fun ajusterMin(carte: CarteDenombrement, delta: Int) = saisirMin(carte, FormulairesEspece.ajuster(carte.min, delta, 1))

    fun ajusterMax(carte: CarteDenombrement, delta: Int) {
        carte.max = FormulairesEspece.ajuster(carte.max, delta, FormulairesEspece.plancherMax(carte.min))
    }

    fun ajouterMedias(index: Int, uris: List<String>) {
        cartes.getOrNull(index)?.medias?.addAll(uris)
    }

    /** Retire un média du dénombrement et supprime sa copie locale. */
    fun retirerMedia(carte: CarteDenombrement, uri: String) {
        services.supprimerMedia(uri)
        carte.medias.remove(uri)
    }

    /** ✓ : les dénombrements relus, ou le message d'un champ obligatoire vide (l'écran reste). */
    fun valider(): Pair<List<Denombrement>?, String?> {
        val manquants = cartes.flatMap { it.additionnels.champsObligatoiresVides() }.distinct()
        if (manquants.isNotEmpty()) return null to "Champs obligatoires à renseigner : ${manquants.joinToString(", ")}"
        return cartes.map { c ->
            val valeurs = champs.associate { champ -> champ.champ.code to champ.code(c.positions[champ.champ.code] ?: 0) }
            FormulairesEspece.appliquerDenombrement(c.base.copy(mediaUris = c.medias.toList()), c.min, c.max, valeurs, c.additionnels.collecter())
        } to null
    }
}
