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

// Propre au portage : la fiche d'une espèce de la saisie multi-taxons simplifiée (demande du 14 novembre
// 2026). Depuis le 16 novembre, c'est un seul formulaire : nombre min et max, stade de vie, sexe, état
// biologique, comportement, commentaire, photos et sons, à la place des boutons « Détails »,
// « Comportement » et « Commentaire » qui ouvraient chacun un écran. Les champs viennent du dénombrement
// n° 0 et de la caractérisation ; les règles sont celles de ces deux écrans (occtax/FormulairesEspece),
// le retour celui de occtax/SaisieSimplifiee. ✓ écrit les champs dans le relevé ; un média ajouté ou
// retiré y est écrit aussitôt.

package fr.ariegenature.geomys.ui.occtax

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.occtax.ChampNomenclature
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.occtax.EspeceReleve
import fr.ariegenature.geomys.occtax.FormulairesEspece
import fr.ariegenature.geomys.occtax.ReleveEnCours
import fr.ariegenature.geomys.occtax.SaisieSimplifiee
import fr.ariegenature.geomys.saisie.ChampsTaxon
import fr.ariegenature.geomys.saisie.EtatChampsAdditionnels
import fr.ariegenature.geomys.store.NidificationOiseaux
import fr.ariegenature.geomys.store.champFormVisible
import fr.ariegenature.geomys.ui.ressources.Res
import fr.ariegenature.geomys.util.moisCourant
import kotlinx.coroutines.launch

/** La fiche de l'espèce [index] du [releve]. */
class FicheEspeceViewModel(
    private val services: ServicesGeoNature,
    val releve: ReleveEnCours,
    private val index: Int,
    /** L'espèce (cd_nom) est-elle en période de nidification ce mois-ci ? Par défaut, le fichier embarqué. */
    private val enPeriodeNidification: suspend (Int) -> Boolean = { cdNom ->
        NidificationOiseaux(services.taxRef) { Res.readBytes(SaisieReleveViewModel.FICHIER_NIDIFICATION).decodeToString() }
            .estEnPeriode(cdNom, moisCourant())
    },
) : ViewModel() {
    private val config = services.config
    private val nomenclatures = services.nomenclatures
    private val formFields = config.formFieldsJson

    /** L'espèce telle qu'elle est à l'ouverture de la fiche ; null si elle n'est plus dans le relevé. */
    private val ouverte: EspeceReleve? = releve.especes.value.getOrNull(index)

    /** Le groupe taxonomique de l'espèce, qui décide des champs qui ont un sens pour elle et filtre
     *  les valeurs proposées, comme sur le web. */
    private val groupe = ouverte?.let { ChampsTaxon.groupeTaxonomique(it.taxon, it.cdNom, services.taxRef) }

    private val denombrement = ouverte?.denombrements()?.first()

    /** Les champs de [liste] que le serveur montre et qui ont un sens pour le groupe de l'espèce. */
    private fun champs(liste: List<Pair<String, String>>): List<ChampNomenclature> {
        val g = groupe ?: return emptyList()
        return FormulairesEspece.champsVisibles(liste, formFields)
            .map { FormulairesEspece.champ(it, nomenclatures, g, config.nomenclaturesFiltreesParTaxon) }
            .filter { SaisieSimplifiee.pertinent(it, g, nomenclatures) }
    }

    private val champsDenombrement = champs(SaisieSimplifiee.DETAILS_DENOMBREMENT)

    /** Sexe, stade de vie (dans cet ordre depuis le 18 novembre 2026), puis état biologique : ceux que le serveur montre, pour le groupe de
     *  l'espèce (pas de sexe pour une plante). */
    val champsDetails: List<ChampNomenclature> = champsDenombrement + champs(SaisieSimplifiee.DETAILS_OCCURRENCE)

    /** Le comportement : si le serveur montre ce champ, et pour un animal. */
    val champComportement: ChampNomenclature? = champs(FormulairesEspece.OCC_COMPORTEMENT).firstOrNull()
    val comportementVisible: Boolean get() = champComportement != null

    private val tousLesChamps = champsDetails + listOfNotNull(champComportement)

    // ─── Nombres ───

    /** Le nombre min et le nombre max, si le serveur les montre (demande du 16 novembre 2026 : « pour
     *  le nombre, remet min et max »). */
    val minVisible = champFormVisible(formFields, SaisieSimplifiee.CLE_NOMBRE)
    val maxVisible = champFormVisible(formFields, SaisieSimplifiee.CLE_NOMBRE_MAX)

    var min by mutableStateOf(denombrement?.nombreMin?.toString() ?: "1")
        private set
    var max by mutableStateOf(denombrement?.nombreMax?.toString() ?: "1")
        private set

    /** « Nombre max » est replié tant qu'il vaut le min (demande du 18 novembre 2026) : un seul champ
     *  « Nombre », et un lien pour le déplier. Replié, il suit le min. Sans champ min, il est montré. */
    var maxDeplie by mutableStateOf(maxVisible && (!minVisible || min != max))
        private set

    fun deplierMax() {
        maxDeplie = true
    }

    /** Le max suit le min tant qu'il n'a pas été saisi à part, comme à l'écran Dénombrement. */
    fun saisirMin(texte: String) {
        max = FormulairesEspece.maxApresMin(min, texte, max)
        min = texte
    }

    fun ajusterMin(delta: Int) = saisirMin(FormulairesEspece.ajuster(min, delta, FormulairesEspece.PLANCHER_MIN))

    fun saisirMax(texte: String) {
        max = texte
    }

    fun ajusterMax(delta: Int) {
        max = FormulairesEspece.ajuster(max, delta, FormulairesEspece.plancherMax(min))
    }

    // ─── Nomenclatures ───

    /** La valeur portée par l'espèce à l'ouverture pour [c]. */
    private fun courant(c: ChampNomenclature): String {
        val e = ouverte ?: return ""
        val d = denombrement ?: return ""
        return if (c in champsDenombrement) FormulairesEspece.valeurDenombrement(d, c.champ) else FormulairesEspece.valeurOccurrence(e, c.champ)
    }

    /** Position choisie de chaque champ, par code : la valeur de l'espèce, sinon le défaut du serveur. */
    val positions = mutableStateMapOf<String, Int>().apply {
        tousLesChamps.forEach { c -> put(c.champ.code, FormulairesEspece.positionInitiale(c, courant(c), nomenclatures)) }
    }
    private val positionsInitiales: Map<String, Int> = positions.toMap()

    /** La valeur retenue de [c] : celle de la liste si elle a changé, sinon celle de l'espèce ou le
     *  défaut du serveur. */
    private fun retenue(c: ChampNomenclature): String {
        val code = c.champ.code
        return FormulairesEspece.valeurRetenue(c, courant(c), nomenclatures.defautPour(code), positionsInitiales[code] ?: 0, positions[code] ?: 0)
    }

    // ─── Champs additionnels obligatoires ───

    private val requis = releve.requis()
    private val observateurs = DetailsReleveFormulaire.observateurs(config)

    /** Les champs additionnels obligatoires de l'occurrence et du dénombrement, montrés ici puisque les
     *  écrans qui les portent ne s'ouvrent pas ; null s'il n'y en a pas. */
    val additionnelsOccurrence: EtatChampsAdditionnels? = requis.occurrence.takeIf { it.isNotEmpty() && ouverte != null }?.let {
        EtatChampsAdditionnels(it, ouverte?.additionalFieldsOccurrence.orEmpty(), observateurs, services.taxRef)
    }
    val additionnelsDenombrement: EtatChampsAdditionnels? = requis.denombrement.takeIf { it.isNotEmpty() && denombrement != null }?.let {
        EtatChampsAdditionnels(it, denombrement?.additionalFields.orEmpty(), observateurs, services.taxRef)
    }

    // ─── Chant ───

    /** Les identifiants des valeurs de chant du serveur, si le bouton « Chant » a sa place : un oiseau,
     *  le comportement montré, et la valeur proposée pour ce taxon. « Chant », et l'indice de
     *  nidification « Mâle chanteur en période de reproduction » des serveurs qui ont les codes atlas. */
    private val idChant: String?
    private val idMaleChanteur: String?

    init {
        val proposes = champComportement?.takeIf { groupe?.let(SaisieSimplifiee::estOiseau) == true }?.codes.orEmpty()
        val valeurs = nomenclatures.get("OCC_COMPORTEMENT")
        idChant = SaisieSimplifiee.valeurChant(valeurs)?.id?.toString()?.takeIf { it in proposes }
        idMaleChanteur = SaisieSimplifiee.valeurMaleChanteur(valeurs)?.id?.toString()?.takeIf { it in proposes }
    }

    /** L'espèce est en période de nidification ce mois-ci (lue à l'ouverture, si le serveur a l'indice). */
    private var nicheur by mutableStateOf(false)

    init {
        val cdNom = ouverte?.cdNom
        if (idMaleChanteur != null && cdNom != null) viewModelScope.launch { nicheur = enPeriodeNidification(cdNom) }
    }

    /** Ce que le bouton poserait maintenant. */
    private val chantAPoser: String? get() = SaisieSimplifiee.idChantAPoser(idChant, idMaleChanteur, nicheur)

    /** « Chant » : pour un oiseau, si le serveur a une valeur de chant qui convient à la saison. */
    val chantVisible: Boolean get() = chantAPoser != null

    /** Le comportement choisi dans la fiche, tel qu'il serait écrit. */
    private val comportementChoisi: String get() = champComportement?.let(::retenue).orEmpty()

    /** Le comportement choisi est une des valeurs de chant. */
    val chante: Boolean get() = comportementChoisi.let { it.isNotEmpty() && (it == idChant || it == idMaleChanteur) }

    /** « Chant » : choisit le chant dans le champ « Comportement » (l'indice « Mâle chanteur » en
     *  période de nidification), ou l'en retire s'il y est, pour revenir au défaut du serveur. Comme
     *  les autres champs, il est écrit au ✓. */
    fun basculerChant() {
        val champ = champComportement ?: return
        val id = chantAPoser ?: return
        val apres = SaisieSimplifiee.comportementApresChant(
            comportementChoisi, id, setOfNotNull(idChant, idMaleChanteur), sansChant = nomenclatures.defautPour("OCC_COMPORTEMENT").orEmpty(),
        )
        positions[champ.champ.code] = champ.position(apres)
    }

    // ─── Commentaire ───

    /** Le commentaire de l'occurrence, si le serveur le montre. */
    val commentaireVisible = champFormVisible(formFields, SaisieSimplifiee.CLE_COMMENTAIRE)

    var commentaire by mutableStateOf(ouverte?.notes.orEmpty())

    // ─── Médias ───

    /** « Photo » et « Audio » : si le serveur active les médias (`OCCTAX.ENABLE_MEDIAS`). */
    val mediasVisibles = config.occtaxMediasActifs

    /** Dossier des copies locales des médias. */
    val dossierMedias: String = services.cheminMedias

    /** L'espèce dans [especes], la liste du relevé ; null si elle n'y est plus. */
    fun espece(especes: List<EspeceReleve>): EspeceReleve? = especes.getOrNull(index)

    /** Un média est écrit aussitôt dans le relevé, sans attendre le ✓ : la photo est déjà prise. */
    fun ajouterMedias(uris: List<String>) {
        releve.modifier(index) { SaisieSimplifiee.ajouterMedias(it, uris) }
    }

    /** Retire un média de l'espèce. Sa copie locale reste : une photo prise depuis la fiche n'existe
     *  que là, d'autres observations d'une série peuvent la partager (audit du 14 novembre 2026, M51).
     *  Le balayage des médias orphelins l'efface après 48 heures. */
    fun retirerMedia(uri: String) {
        releve.modifier(index) { SaisieSimplifiee.retirerMedia(it, uri) }
    }

    /** L'observation est déjà sur le serveur : un média ajouté maintenant ne partirait jamais, la mise
     *  à jour d'une occurrence n'en portant pas (audit du 14 novembre 2026, M52). */
    fun mediasFiges(e: EspeceReleve): Boolean = releve.dejaTransmise(e)

    // ─── ✓ ───

    private fun valeurs(): List<Any?> =
        listOf(min, max, positions.toMap(), commentaire, additionnelsOccurrence?.collecter(), additionnelsDenombrement?.collecter())

    private val initiales = valeurs()

    /** La fiche a changé depuis son ouverture (les médias, déjà écrits, ne comptent pas). */
    fun modifie(): Boolean = valeurs() != initiales

    /** ✓ : écrit la fiche dans l'espèce et rend null, ou rend le message d'un champ refusé (la fiche
     *  reste). L'écriture part de l'espèce telle qu'elle est AU MOMENT du ✓, pas de celle de
     *  l'ouverture : une photo rattachée entre-temps n'est pas écrasée (audit du 14 novembre 2026,
     *  M50). Une fiche laissée telle quelle n'écrit rien : les champs jamais saisis restent vides, et
     *  le serveur y met ses défauts. */
    fun valider(): String? {
        val nomenclaturesRetenues = tousLesChamps.associate { it.champ.code to retenue(it) }
        SaisieSimplifiee.refusEffectifs(min.takeIf { minVisible }, max.takeIf { maxVisible })?.let { return it }
        FormulairesEspece.manque(tousLesChamps, nomenclaturesRetenues)?.let { return it }
        additionnelsOccurrence?.refus()?.let { return it }
        additionnelsDenombrement?.refus()?.let { return it }
        if (!modifie()) return null
        val minSaisi = min.takeIf { minVisible }
        val maxSaisi = max.takeIf { maxVisible }
        val occurrence = additionnelsOccurrence?.collecter().orEmpty()
        val comptage = additionnelsDenombrement?.collecter().orEmpty()
        val texte = commentaire.takeIf { commentaireVisible }
        val vmId = ouverte?.vmId
        releve.modifier(index) { courante ->
            if (courante.vmId != vmId) return@modifier courante
            val details = SaisieSimplifiee.appliquerDetails(
                courante, minSaisi ?: courante.nombre.toString(), nomenclaturesRetenues, occurrence, comptage, max = maxSaisi,
            )
            if (texte != null) SaisieSimplifiee.appliquerCommentaire(details, texte) else details
        }
        return null
    }
}
