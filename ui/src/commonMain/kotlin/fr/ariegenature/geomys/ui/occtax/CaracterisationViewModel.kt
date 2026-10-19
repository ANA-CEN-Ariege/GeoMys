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
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.occtax.FormulairesEspece
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.saisie.ChampsTaxon
import fr.ariegenature.geomys.saisie.EtatChampsAdditionnels
import fr.ariegenature.geomys.store.champFormVisible

/** La caractérisation de l'occurrence [espece]. [comportementSeul] : ouverte d'elle-même pour un
 *  oiseau nicheur, réduite au comportement ; rien d'autre n'est ni montré ni réécrit. */
class CaracterisationViewModel(
    services: ServicesGeoNature,
    private val espece: EspeceReleve,
    val comportementSeul: Boolean,
) : ViewModel() {
    private val config = services.config
    private val nomenclatures = services.nomenclatures
    private val formFields = config.formFieldsJson

    private val groupesEtRegno = ChampsTaxon.groupesEtRegno(
        espece.taxon,
        FormulairesEspece.groupe2Inpn(espece, services.taxRef.tousLesGroupes()).orEmpty(),
        nomenclatures,
    )

    private fun champs(liste: List<Pair<String, String>>): List<ChampNomenclature> =
        FormulairesEspece.champsVisibles(liste, formFields).map {
            FormulairesEspece.champ(it, nomenclatures, groupesEtRegno.first, groupesEtRegno.second)
        }

    /** Les champs toujours visibles (le comportement seul en mode nidification). */
    val basiques = champs(if (comportementSeul) FormulairesEspece.OCC_COMPORTEMENT else FormulairesEspece.OCC_BASIQUES)

    /** Les nomenclatures de la section « Plus de champs ». */
    val avances = if (comportementSeul) emptyList() else champs(FormulairesEspece.OCC_AVANCES)

    /** Position choisie de chaque champ, par code. Pas de report d'une espèce à l'autre : la valeur de
     *  l'espèce, sinon le défaut du serveur. */
    val positions = mutableStateMapOf<String, Int>().apply {
        (basiques + avances).forEach { c ->
            put(c.champ.code, FormulairesEspece.positionInitiale(c, FormulairesEspece.valeurOccurrence(espece, c.champ), nomenclatures))
        }
    }

    /** Déterminateur (form_fields « determiner »), null s'il n'est pas montré. */
    var determinateur by mutableStateOf(
        if (!comportementSeul && champFormVisible(formFields, "determiner")) {
            espece.determinateur.ifEmpty { config.observateurDefautNom.ifEmpty { config.nomUtilisateur.ifEmpty { config.login } } }
        } else null,
    )

    /** Les preuves montrées : (clé form_fields, libellé, URL ?) et leur texte. */
    val preuves = if (comportementSeul) emptyList() else FormulairesEspece.PREUVES.filter { champFormVisible(formFields, it.first) }
    val textesPreuves = mutableStateMapOf<String, String>().apply {
        preuves.forEach { (cle, _, _) -> put(cle, espece.champsOccExtra[cle].orEmpty()) }
    }

    /** Commentaire (form_fields « comment_occ »), null s'il n'est pas montré. */
    var commentaire by mutableStateOf(if (!comportementSeul && champFormVisible(formFields, "comment_occ")) espece.notes else null)

    /** La section « Plus de champs » existe, et elle est ouverte. */
    val aDesChampsAvances = determinateur != null || avances.isNotEmpty() || preuves.isNotEmpty() || commentaire != null
    var avanceOuvert by mutableStateOf(false)

    /** Les champs additionnels de l'occurrence (pas ceux du relevé, réglés par « Détails »), absents
     *  en mode comportement seul. */
    val additionnels: EtatChampsAdditionnels? = if (comportementSeul) null else {
        val listesDuTaxon = espece.cdNom?.let { services.taxRef.listesPourCdNom(it) }.orEmpty()
        val defs = ChampsAdditionnels.fromJson(config.additionalFieldsOcctaxJsonActif)
            .filter { it.visiblePour(config.idDataset.toIntOrNull(), listesDuTaxon) }
            .filter { it.appliqueA(AdditionalFieldsObject.OCCURRENCE) }
        EtatChampsAdditionnels(defs, espece.additionalFieldsOccurrence)
    }

    /** ✓ : l'espèce caractérisée, ou le message d'un champ obligatoire vide (l'écran reste). */
    fun valider(): Pair<EspeceReleve?, String?> {
        val manquants = additionnels?.champsObligatoiresVides().orEmpty()
        if (manquants.isNotEmpty()) return null to "Champs obligatoires à renseigner : ${manquants.joinToString(", ")}"
        val valeurs = (basiques + avances).associate { c -> c.champ.code to c.code(positions[c.champ.code] ?: 0) }
        val preuvesSaisies = preuves.associate { (cle, _, _) -> cle to textesPreuves[cle].orEmpty() }
        return FormulairesEspece.appliquerCaracterisation(
            espece,
            valeurs,
            determinateur,
            commentaire,
            preuvesSaisies,
            additionnels?.collecter(),
        ) to null
    }
}
