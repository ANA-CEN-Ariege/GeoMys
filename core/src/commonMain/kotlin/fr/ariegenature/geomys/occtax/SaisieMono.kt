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

// Porté de GeoMys-Android @ d72b8da (app/src/main/java/fr/ariegenature/geomys/ui/SaisieRapideFragment.kt) : les
// réglages de la saisie mono-taxon — l'espèce, sa caractérisation et son dénombrement, les détails du relevé
// communs à la série — et l'observation que chaque « + » enregistre. Ce que le fragment gardait dans ses
// champs ; l'écran n'en garde que l'affichage.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.geo.distanceMetres
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.util.FormatDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** La saisie mono-taxon dans la [session] : chaque observation enregistrée est son propre relevé
 *  (sans `releveId`). [additionnelsIntercales] : les champs de relevé saisis dans l'écran intercalé,
 *  qui deviennent le défaut de toute la série. */
class SaisieMono(
    private val session: SessionSaisieOcctax,
    private val config: GeoNatureConfig,
    taxonInitial: Taxon,
    additionnelsIntercales: Map<String, String>? = null,
) {
    private val _modele = MutableStateFlow(EspeceReleve(taxonInitial, "", null))

    /** L'espèce de la série et ses caractérisation et dénombrement par défaut : chaque observation en
     *  est une copie. Le nom et le cd_nom ne sont posés qu'au démarrage ([demarrer]). */
    val modele: StateFlow<EspeceReleve> = _modele.asStateFlow()

    // ── Détails du relevé, communs à toutes les observations de la série ──
    var additionnelsReleve: Map<String, String> = additionnelsIntercales.orEmpty()
        private set
    var idDataset: Int? = null
        private set
    var idsObservateurs: List<Int> = emptyList()
        private set
    var nomsObservateurs: List<String> = emptyList()
        private set
    var commentaire: String = ""
        private set
    var cdHab: Int? = null
        private set
    var habitatLabel: String? = null
        private set
    var dateDebut: Long? = null
        private set
    var dateFin: Long? = null
        private set
    var typGrp: String = ""
        private set
    var champsExtra: Map<String, String> = emptyMap()
        private set

    /** Le groupe de la palette suit le modèle. */
    fun choisirTaxon(taxon: Taxon) {
        _modele.value = _modele.value.copy(taxon = taxon)
    }

    /** Retour de la caractérisation ou du dénombrement : le modèle modifié. */
    fun modifier(transformation: (EspeceReleve) -> EspeceReleve) {
        _modele.value = transformation(_modele.value)
    }

    /** « Démarrer la saisie » : l'espèce trouvée ([nom], [cdNom]) devient celle de chaque
     *  observation ; sans nom, le nom du groupe. */
    fun demarrer(nom: String, cdNom: Int?) {
        _modele.value = _modele.value.copy(espece = nom, cdNom = cdNom)
    }

    /** « Valider » des détails : les valeurs de toute la série (sans saisie a posteriori, comme
     *  sous Android). */
    fun appliquerDetails(res: DetailsReleveResult) {
        idDataset = res.idDataset
        idsObservateurs = res.idsObservateurs
        nomsObservateurs = res.nomsObservateurs
        additionnelsReleve = res.additionnels
        commentaire = res.comment
        cdHab = res.cdHab
        habitatLabel = res.habitatLabel
        dateDebut = res.dateDebut
        dateFin = res.dateFin
        typGrp = res.typGrp
        champsExtra = res.champsExtra
    }

    /** Les valeurs de départ du dialogue « Détails du relevé » de la série. */
    fun donneesDetails(): DonneesDetailsReleve = DonneesDetailsReleve(
        titre = "Détails du relevé",
        infos = emptyList(),
        datasets = DetailsReleveFormulaire.datasets(config),
        idDataset = idDataset ?: config.idDataset.toIntOrNull(),
        nomDataset = config.nomDataset.takeIf { it.isNotEmpty() },
        observateurs = DetailsReleveFormulaire.observateurs(config),
        idsObservateurs = DetailsReleveFormulaire.idsObservateursDepart(idsObservateurs, config),
        nomsObservateurs = DetailsReleveFormulaire.nomsObservateursDepart(nomsObservateurs, config),
        defs = DetailsReleveFormulaire.defsReleve(config),
        additionnels = additionnelsReleve,
        formFieldsJson = config.formFieldsJson,
        typGrp = typGrp,
        commentaire = commentaire,
        dateDebut = dateDebut,
        dateFin = dateFin,
        heures = config.heuresVisibles,
        avecFin = config.dateFinVisible,
        cdHab = cdHab,
        habitatLabel = habitatLabel,
        champsExtra = champsExtra,
    )

    /** Ce que « + » enregistre en [latitude], [longitude] : une copie du modèle, relevé à part
     *  entière. Sans détails réglés, les champs de relevé prennent les défauts du serveur ; le
     *  jeu de données est figé au niveau de la saisie (la surcharge, sinon celui des relevés déjà
     *  enregistrés, sinon le défaut des paramètres). */
    fun observation(latitude: Double, longitude: Double): Observation {
        val m = _modele.value
        return m.surObservation(Observation(espece = m.espece.ifEmpty { m.taxon.nomSingulier }, latitude = latitude, longitude = longitude)).copy(
            additionalFieldsReleve = additionnelsReleve.ifEmpty {
                ChampsAdditionnels.defautsChampsReleve(config.additionalFieldsOcctaxJsonActif, config.idDataset.toIntOrNull())
            },
            idDatasetReleve = idDataset ?: session.datasetSortie() ?: config.idDataset.toIntOrNull()?.takeIf { it > 0 },
            typGrpReleve = typGrp.ifEmpty { null },
            champsReleveExtra = champsExtra,
            observateursReleveIds = idsObservateurs,
            observateursReleveNoms = nomsObservateurs,
            commentReleve = commentaire.ifEmpty { null },
            cdHabReleve = cdHab,
            habitatReleveLabel = habitatLabel,
            dateDebutReleve = dateDebut,
            dateFinReleve = dateFin,
        )
    }

    /** « + » : l'observation est ajoutée à la saisie, qui l'enregistre aussitôt. */
    fun enregistrer(latitude: Double, longitude: Double): Observation =
        observation(latitude, longitude).also(session::ajouterObservation)

    // ── Affichage ──

    /** Sous les boutons Caractérisation et Dénombrement : les codes du sexe, du stade et de la
     *  technique réglés, sinon « Détails renseignés » s'il y a d'autres réglages ; null sinon. */
    fun indicateurDetails(): String? {
        val m = _modele.value
        val reglages = m.notes.isNotEmpty() || m.sexe.isNotEmpty() || m.stadeVie.isNotEmpty() ||
            m.techniqueObs.isNotEmpty() || m.statutBio.isNotEmpty() || m.etaBio.isNotEmpty() ||
            m.preuveExist.isNotEmpty() || m.objDenbr.isNotEmpty() || m.typDenbr.isNotEmpty() ||
            m.comportement.isNotEmpty() || m.methDetermin.isNotEmpty() || m.statutObs.isNotEmpty() ||
            m.naturalite.isNotEmpty() || m.champsOccExtra.isNotEmpty() ||
            m.denombrementsAdditionnels.isNotEmpty()
        if (!reglages) return null
        return listOf(m.sexe, m.stadeVie, m.techniqueObs).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Détails renseignés" }
    }

    /** Le résumé du panneau de la carte : « <espèce> × N ». */
    fun resume(): String {
        val m = _modele.value
        return "${m.espece.ifEmpty { m.taxon.nomSingulier }} × ${m.nombre}"
    }

    /** Sous le résumé : les codes du sexe et du stade, s'il y en a. */
    fun resumeDetails(): String? {
        val m = _modele.value
        return listOf(m.sexe, m.stadeVie).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
    }

    companion object {
        const val LIBELLE = "Saisie mono-taxons"

        /** La consigne au-dessus de la carte. */
        const val CONSIGNE = "Touchez pour placer le point · appui long pour le déplacer"

        /** Rayon dans lequel des observations sont « au même endroit » qu'un marqueur touché. */
        private const val RAYON_ICI_METRES = 30.0

        /** Le compteur du panneau de la carte. */
        fun compteur(n: Int): String = "$n obs. enregistrée${if (n > 1) "s" else ""}"

        /** La confirmation qui suit chaque « + ». */
        fun confirmation(observation: Observation): String = "${observation.espece} × ${observation.nombre}"

        /** Marqueur touché : les observations à 30 m au plus de [reference] (les points se
         *  superposent vite sur le terrain), la plus récente d'abord. */
        fun observationsIci(reference: Observation, toutes: List<Observation>): List<Observation> =
            toutes.filter { distanceMetres(reference.latitude, reference.longitude, it.latitude, it.longitude) <= RAYON_ICI_METRES }
                .ifEmpty { listOf(reference) }
                .sortedByDescending { it.date }

        fun titreObservationsIci(n: Int): String = "$n obs. ici — appuyer pour modifier"

        /** « HH:mm  espèce · N ind. », et les notes en dessous. */
        fun ligneObservationIci(o: Observation): String {
            val effectif = if (o.nombre > 1) " · ${o.nombre} ind." else ""
            val notes = if (o.notes.isNotEmpty()) "\n   ${o.notes}" else ""
            return "${FormatDate.heure(o.date)}  ${o.espece}$effectif$notes"
        }
    }
}
