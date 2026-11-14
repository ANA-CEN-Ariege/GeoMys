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

// Propre au portage : l'écran d'une espèce de la saisie multi-taxons simplifiée (demande du 14 novembre
// 2026), qui mène à « Détails » et « Comportement » et ajoute photos et sons. Il ne garde rien : chaque
// volet validé et chaque média ajouté ou retiré est écrit aussitôt dans le relevé, enregistré au fil de
// l'eau.

package fr.ariegenature.geomys.ui.occtax

import androidx.lifecycle.ViewModel
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.occtax.FormulairesEspece
import fr.ariegenature.geomys.occtax.ReleveEnCours
import fr.ariegenature.geomys.occtax.SaisieSimplifiee
import fr.ariegenature.geomys.saisie.ChampsTaxon
import fr.ariegenature.geomys.store.champFormVisible

/** L'écran de l'espèce [index] du [releve]. */
class FicheEspeceViewModel(
    private val services: ServicesGeoNature,
    val releve: ReleveEnCours,
    private val index: Int,
) : ViewModel() {
    private val config = services.config
    private val nomenclatures = services.nomenclatures
    private val formFields = config.formFieldsJson

    /** Le groupe taxonomique de l'espèce, qui décide des champs qui ont un sens pour elle. */
    private val groupe = releve.especes.value.getOrNull(index)?.let { ChampsTaxon.groupeTaxonomique(it.taxon, it.cdNom, services.taxRef) }

    /** Les codes des champs de [liste] que le serveur montre et qui ont un sens pour l'espèce. */
    private fun codesPertinents(liste: List<Pair<String, String>>): List<String> {
        val g = groupe ?: return emptyList()
        return FormulairesEspece.champsVisibles(liste, formFields)
            .map { FormulairesEspece.champ(it, nomenclatures, g, config.nomenclaturesFiltreesParTaxon) }
            .filter { SaisieSimplifiee.pertinent(it, g, nomenclatures) }
            .map { it.champ.code }
    }

    private val nombreVisible = champFormVisible(formFields, SaisieSimplifiee.CLE_NOMBRE)
    private val codesDetails = codesPertinents(SaisieSimplifiee.DETAILS_DENOMBREMENT + SaisieSimplifiee.DETAILS_OCCURRENCE)

    /** « Détails » : un de ses champs est montré par le serveur, ou un champ additionnel obligatoire. */
    val detailsVisible: Boolean
        get() = nombreVisible || codesDetails.isNotEmpty() || releve.requis().let { it.occurrence.isNotEmpty() || it.denombrement.isNotEmpty() }

    /** « Comportement » : si le serveur montre ce champ, et pour un animal. */
    val comportementVisible = codesPertinents(FormulairesEspece.OCC_COMPORTEMENT).isNotEmpty()

    /** « Commentaire » : si le serveur montre le commentaire de l'occurrence. */
    val commentaireVisible = champFormVisible(formFields, SaisieSimplifiee.CLE_COMMENTAIRE)

    /** « Photo » et « Audio » : si le serveur active les médias (`OCCTAX.ENABLE_MEDIAS`). */
    val mediasVisibles = config.occtaxMediasActifs

    /** Dossier des copies locales des médias. */
    val dossierMedias: String = services.cheminMedias

    /** L'espèce dans [especes], la liste du relevé ; null si elle n'y est plus. */
    fun espece(especes: List<EspeceReleve>): EspeceReleve? = especes.getOrNull(index)

    /** Un champ obligatoire de « Détails » est vide. */
    fun detailsIncomplets(e: EspeceReleve): Boolean = releve.requis().let { it.caracterisationIncomplete(e) || it.denombrementIncomplet(e) }

    /** Le libellé d'une nomenclature [code] de valeur [valeur] ; vide : celui du défaut du serveur,
     *  la valeur qui sera enregistrée. */
    private fun libelle(code: String, valeur: String?): String? {
        val id = valeur?.takeIf { it.isNotEmpty() } ?: nomenclatures.defautPour(code) ?: return null
        return nomenclatures.get(code).firstOrNull { it.id.toString() == id }?.libelle
    }

    /** Sous « Détails » : « 3 · Adulte · Mâle · Vivant ». */
    fun resumeDetails(e: EspeceReleve): String {
        val parties = buildList {
            if (nombreVisible) add(e.nombre.toString() + (e.nombreMax?.let { " à $it" } ?: ""))
            for (code in codesDetails) {
                val valeur = when (code) {
                    "STADE_VIE" -> e.stadeVie
                    "SEXE" -> e.sexe
                    "ETA_BIO" -> e.etaBio
                    else -> null
                }
                libelle(code, valeur)?.let(::add)
            }
        }
        return parties.joinToString(" · ")
    }

    /** Sous « Comportement » : le comportement choisi, ou « Non renseigné ». */
    fun resumeComportement(e: EspeceReleve): String = libelle("OCC_COMPORTEMENT", e.comportement) ?: "Non renseigné"

    /** Sous « Commentaire » : le commentaire, sur trois lignes au plus à l'écran, ou « Aucun ». */
    fun resumeCommentaire(e: EspeceReleve): String = e.notes.trim().ifEmpty { "Aucun" }

    fun ajouterMedias(uris: List<String>) {
        releve.modifier(index) { SaisieSimplifiee.ajouterMedias(it, uris) }
    }

    /** Retire un média de l'espèce. Sa copie locale reste : une photo prise depuis la fiche n'existe
     *  que là, d'autres observations d'une série peuvent la partager, et l'écran n'a pas de « Quitter
     *  sans enregistrer » (audit du 14 novembre 2026, M51). Le balayage des médias orphelins l'efface
     *  après 48 heures. */
    fun retirerMedia(uri: String) {
        releve.modifier(index) { SaisieSimplifiee.retirerMedia(it, uri) }
    }

    /** L'observation est déjà sur le serveur : un média ajouté maintenant ne partirait jamais, la mise
     *  à jour d'une occurrence n'en portant pas (audit du 14 novembre 2026, M52). */
    fun mediasFiges(e: EspeceReleve): Boolean = releve.dejaTransmise(e)
}
