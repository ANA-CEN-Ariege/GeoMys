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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/CaracterisationFragment.kt) : l'état
// de l'écran ; les règles (champs montrés, entrées, application des valeurs) sont dans
// occtax/FormulairesEspece.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.occtax.ChampNomenclature
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.occtax.FormulairesEspece
import fr.ariegenature.geomys.occtax.determinateurParDefaut
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.saisie.ChampsTaxon
import fr.ariegenature.geomys.saisie.EtatChampsAdditionnels
import fr.ariegenature.geomys.store.champFormVisible

/** La caractérisation de l'occurrence [espece]. [comportementSeul] : ouverte d'elle-même pour un
 *  oiseau nicheur, réduite au comportement ; rien d'autre n'est ni montré ni réécrit. [idDataset] :
 *  le jeu de données du relevé, dont dépendent les champs additionnels. */
class CaracterisationViewModel(
    services: ServicesGeoNature,
    private val espece: EspeceReleve,
    val comportementSeul: Boolean,
    idDataset: Int?,
) : ViewModel() {
    private val config = services.config
    private val nomenclatures = services.nomenclatures
    private val formFields = config.formFieldsJson

    /** Le taxon de l'espèce, qui filtre les valeurs proposées comme sur le web. */
    private val groupe = ChampsTaxon.groupeTaxonomique(espece.taxon, espece.cdNom, services.taxRef)

    private fun champs(liste: List<Pair<String, String>>): List<ChampNomenclature> =
        FormulairesEspece.champsVisibles(liste, formFields).map {
            FormulairesEspece.champ(it, nomenclatures, groupe, config.nomenclaturesFiltreesParTaxon)
        }

    /** Les champs toujours visibles (le comportement seul en mode nidification). */
    val basiques = champs(if (comportementSeul) FormulairesEspece.OCC_COMPORTEMENT else FormulairesEspece.OCC_BASIQUES)

    /** Les nomenclatures de la section « Avancé ». */
    val avances = if (comportementSeul) emptyList() else champs(FormulairesEspece.OCC_AVANCES)

    /** Position choisie de chaque champ, par code. Pas de report d'une espèce à l'autre : la valeur de
     *  l'espèce, sinon le défaut du serveur. */
    val positions = mutableStateMapOf<String, Int>().apply {
        (basiques + avances).forEach { c ->
            put(c.champ.code, FormulairesEspece.positionInitiale(c, FormulairesEspece.valeurOccurrence(espece, c.champ), nomenclatures))
        }
    }

    /** Les positions à l'ouverture : une valeur que l'utilisateur n'a pas touchée est retenue même
     *  absente des valeurs proposées ([FormulairesEspece.valeurRetenue]). */
    private val positionsInitiales: Map<String, Int> = positions.toMap()

    /** La valeur retenue de chaque champ affiché, par code. */
    private fun valeursRetenues(): Map<String, String> = (basiques + avances).associate { c ->
        val code = c.champ.code
        code to FormulairesEspece.valeurRetenue(
            c, FormulairesEspece.valeurOccurrence(espece, c.champ), nomenclatures.defautPour(code), positionsInitiales[code] ?: 0, positions[code] ?: 0,
        )
    }

    /** Déterminateur (form_fields « determiner »), null s'il n'est pas montré ; celui de l'espèce,
     *  sinon l'utilisateur connecté ([determinateurParDefaut]). */
    var determinateur by mutableStateOf(
        if (!comportementSeul && champFormVisible(formFields, "determiner")) {
            espece.determinateur.ifEmpty { determinateurParDefaut(config) }
        } else null,
    )

    /** Les preuves montrées : (clé form_fields, libellé, URL ?) et leur texte. */
    val preuves = if (comportementSeul) emptyList() else FormulairesEspece.PREUVES.filter { champFormVisible(formFields, it.first) }
    val textesPreuves = mutableStateMapOf<String, String>().apply {
        preuves.forEach { (cle, _, _) -> put(cle, espece.champsOccExtra[cle].orEmpty()) }
    }

    /** La preuve d'existence vaut « Oui » (cd_nomenclature 1) : les preuves ne s'affichent qu'alors,
     *  comme sur le web, qui en exige une et veut une adresse pour la preuve numérique. */
    val preuvesOuvertes: Boolean
        get() {
            val champ = (basiques + avances).firstOrNull { it.champ.code == "PREUVE_EXIST" }
            val valeur = if (champ != null) {
                FormulairesEspece.valeurRetenue(
                    champ, espece.preuveExist, nomenclatures.defautPour("PREUVE_EXIST"), positionsInitiales["PREUVE_EXIST"] ?: 0, positions["PREUVE_EXIST"] ?: 0,
                )
            } else {
                espece.preuveExist.ifEmpty { nomenclatures.defautPour("PREUVE_EXIST").orEmpty() }
            }
            return nomenclatures.get("PREUVE_EXIST").firstOrNull { it.id.toString() == valeur }?.cd == "1"
        }

    private val preuveNumeriqueUrl = config.occtaxPreuveNumeriqueUrl

    /** Commentaire (form_fields « comment_occ »), null s'il n'est pas montré. */
    var commentaire by mutableStateOf(if (!comportementSeul && champFormVisible(formFields, "comment_occ")) espece.notes else null)

    /** La section « Avancé » existe, et elle est ouverte : d'emblée si le serveur le demande
     *  (`EXPANDED_TAXON_ADVANCED_DETAILS`), comme le web. */
    val aDesChampsAvances = determinateur != null || avances.isNotEmpty() || preuves.isNotEmpty() || commentaire != null
    var avanceOuvert by mutableStateOf(config.occtaxAvanceOuvert)

    /** Les champs additionnels de l'occurrence (pas ceux du relevé, réglés par « Détails »), absents
     *  en mode comportement seul. */
    val additionnels: EtatChampsAdditionnels? = if (comportementSeul) null else {
        EtatChampsAdditionnels(
            ChampsAdditionnels.defsPour(config.additionalFieldsOcctaxJson, AdditionalFieldsObject.OCCURRENCE, idDataset),
            espece.additionalFieldsOccurrence,
            DetailsReleveFormulaire.observateurs(config),
            services.taxRef,
        )
    }

    /** Les valeurs saisissables, pour la garde de sortie. */
    private fun valeurs(): List<Any?> =
        listOf(positions.toMap(), determinateur, textesPreuves.toMap(), commentaire, additionnels?.collecter())

    private val initiales = valeurs()

    /** L'écran a changé depuis son ouverture. */
    fun modifie(): Boolean = valeurs() != initiales

    /** ✓ : l'espèce caractérisée, ou le message d'un champ obligatoire vide (l'écran reste). */
    fun valider(): Pair<EspeceReleve?, String?> {
        val valeurs = valeursRetenues()
        if (!comportementSeul) FormulairesEspece.manque(basiques, valeurs)?.let { return null to it }
        additionnels?.refus()?.let { return null to it }
        // Les preuves d'une existence qui n'est pas « Oui » sont vidées : le web les veut nulles.
        val ouvertes = preuvesOuvertes
        val preuvesSaisies = preuves.associate { (cle, _, _) -> cle to if (ouvertes) textesPreuves[cle].orEmpty().trim() else "" }
        if (ouvertes && preuves.isNotEmpty()) {
            if (preuvesSaisies.values.all { it.isEmpty() }) return null to PREUVE_REQUISE
            val numerique = preuvesSaisies["digital_proof"].orEmpty()
            if (preuveNumeriqueUrl && numerique.isNotEmpty() && !URL_PREUVE.matches(numerique)) {
                return null to PREUVE_ADRESSE
            }
        }
        return FormulairesEspece.appliquerCaracterisation(
            espece,
            valeurs,
            determinateur,
            commentaire,
            preuvesSaisies,
            additionnels?.collecter(),
        ) to null
    }

    companion object {
        /** Le motif de la preuve numérique du web (`^(http://|https://|ftp://){1}.+$`). */
        private val URL_PREUVE = Regex("^(http://|https://|ftp://).+$")

        /** Les messages du web (fr.json d'OccTax : NoExistProofError, ExistProofValidation). */
        const val PREUVE_REQUISE = "Si 'preuve d'existence = Oui', un des deux champs de preuve doit être rempli"
        const val PREUVE_ADRESSE = "Ce champs doit débuter par 'http://', 'https://', ou 'ftp://'"
    }
}
