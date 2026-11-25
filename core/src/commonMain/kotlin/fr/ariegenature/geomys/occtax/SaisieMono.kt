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

// Porté de GeoMys-Android @ d72b8da (app/src/main/java/fr/ariegenature/geomys/ui/SaisieRapideFragment.kt) : les
// réglages de la saisie mono-taxon — l'espèce, sa caractérisation et son dénombrement, les détails du relevé
// communs à la série — et l'observation que chaque « + » enregistre. Ce que le fragment gardait dans ses
// champs ; l'écran n'en garde que l'affichage. Depuis le 24 novembre 2026, la caractérisation et le
// dénombrement de la série se règlent dans la fiche de l'espèce, comme en multi-taxons ([ficheSerie]), et
// restent avec la saisie ([Sortie.modeleMono]) ; depuis le 25, le « i » de la carte rouvre cette fiche.

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
    // Le déterminateur du web pour chaque taxon, caractérisation ouverte ou non.
    private val _modele = MutableStateFlow(EspeceReleve(taxonInitial, "", null, determinateur = determinateurParDefaut(config)))

    /** L'espèce de la série et ses valeurs, réglées dans sa fiche : chaque observation en est une copie.
     *  Le nom et le cd_nom sont posés au choix de l'espèce ([choisirEspece]) et au démarrage
     *  ([demarrer]). Sans média : une photo est celle d'une observation. */
    val modele: StateFlow<EspeceReleve> = _modele.asStateFlow()

    private val _especesSerie = MutableStateFlow(listOf(_modele.value))

    /** L'espèce de la série, au rang 0, pour la fiche qui règle ses valeurs (demande du 24 novembre
     *  2026 : comme en multi-taxons, la fiche s'ouvre au choix de l'espèce, et ses valeurs valent pour
     *  chaque « + »). */
    val ficheSerie: EspecesModifiables = object : EspecesModifiables {
        override val especes: StateFlow<List<EspeceReleve>> = _especesSerie.asStateFlow()

        override fun modifier(index: Int, transformation: (EspeceReleve) -> EspeceReleve) {
            if (index == 0) this@SaisieMono.modifier(transformation)
        }

        override fun requis(): RequisEspece = this@SaisieMono.requis()

        override fun dejaTransmise(espece: EspeceReleve): Boolean = false
    }

    /** Le modèle de la série devient [m], sans média. */
    private fun poserModele(m: EspeceReleve) {
        val sansMedias = m.sansMedias()
        _modele.value = sansMedias
        _especesSerie.value = listOf(sansMedias)
    }

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
    var heureDebut: String? = null
        private set
    var heureFin: String? = null
        private set
    var observateursTexte: String? = null
        private set
    var typGrp: String = ""
        private set
    var champsExtra: Map<String, String> = emptyMap()
        private set

    /** Le groupe de la palette suit le modèle. */
    fun choisirTaxon(taxon: Taxon) {
        poserModele(_modele.value.copy(taxon = taxon))
    }

    /** L'espèce choisie dans les réglages ([nom], [cdNom], du groupe [taxon]) : sa fiche, qui s'ouvre
     *  alors, règle les valeurs de la série pour elle. La même espèce garde les valeurs de la série :
     *  celles du modèle, ou celles de la série démarrée ([SessionSaisieOcctax.modeleMono]) si une autre
     *  espèce a été choisie entre-temps sans que sa fiche soit validée. Une autre espèce repart des
     *  valeurs par défaut, comme une espèce ajoutée en multi-taxons. */
    fun choisirEspece(taxon: Taxon, nom: String, cdNom: Int) {
        val m = _modele.value
        val serie = session.modeleMono?.takeIf { it.cdNom == cdNom }
        val base = when {
            m.cdNom == cdNom -> m
            serie != null -> especeDepuisObservation(serie)
            else -> EspeceReleve(taxon, "", null, determinateur = determinateurParDefaut(config))
        }
        poserModele(base.copy(taxon = taxon, espece = nom, cdNom = cdNom))
    }

    /** La fiche de la série validée : le modèle transformé. */
    fun modifier(transformation: (EspeceReleve) -> EspeceReleve) {
        poserModele(transformation(_modele.value))
    }

    /** « Démarrer la saisie » : l'espèce trouvée ([nom], [cdNom]) devient celle de chaque
     *  observation ; sans nom, le nom du groupe. Les valeurs de la série partent alors avec la saisie
     *  ([garderAvecLaSaisie]). */
    fun demarrer(nom: String, cdNom: Int?) {
        poserModele(_modele.value.copy(espece = nom, cdNom = cdNom))
        garderAvecLaSaisie()
    }

    /** Les valeurs de la série partent avec la saisie, pour « Continuer la saisie »
     *  ([SessionSaisieOcctax.definirModeleMono]) : au démarrage de la série, puis au ✓ de sa fiche
     *  rouverte par le « i » de la carte (demande du 25 novembre 2026), dont les valeurs valent pour la
     *  suite. */
    fun garderAvecLaSaisie() {
        val m = _modele.value
        session.definirModeleMono(m.surObservation(Observation(espece = m.espece.ifEmpty { m.taxon.nomSingulier }, latitude = 0.0, longitude = 0.0)))
    }

    /** « Continuer la saisie » : les valeurs de la série reprises de [o], celles écrites avec la saisie
     *  ([Sortie.modeleMono]), sinon celles de sa dernière observation ; sans média. */
    fun reprendreModele(o: Observation) {
        poserModele(especeDepuisObservation(o))
    }

    /** « Continuer la saisie » : les détails du relevé de la série repris de [o], son dernier « + » :
     *  jeu de données, observateurs, date et heures, commentaire, habitat, nomenclatures et champs du
     *  relevé (relecture du 25 novembre 2026). La série repartait des défauts (l'observateur des
     *  Paramètres, la date de chaque « + », sans heure, commentaire ni habitat), et la carte n'ouvre plus
     *  « Détails » pour les reposer. Sans les altitudes, propres à la position de [o] ([ALTITUDES]). Les
     *  champs additionnels du relevé arrivent déjà avec « Continuer la saisie » ([additionnelsReleve]). */
    fun reprendreDetails(o: Observation) {
        o.idDatasetReleve?.let { idDataset = it }
        idsObservateurs = o.observateursReleveIds
        nomsObservateurs = o.observateursReleveNoms
        observateursTexte = o.observateursReleveTxt
        commentaire = o.commentReleve.orEmpty()
        cdHab = o.cdHabReleve
        habitatLabel = o.habitatReleveLabel
        dateDebut = o.dateDebutReleve
        dateFin = o.dateFinReleve
        heureDebut = o.heureDebutReleve
        heureFin = o.heureFinReleve
        typGrp = o.typGrpReleve.orEmpty()
        champsExtra = o.champsReleveExtra - ALTITUDES
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
        heureDebut = res.heureDebut
        heureFin = res.heureFin
        observateursTexte = res.observateursTexte
        typGrp = res.typGrp
        champsExtra = res.champsExtra
    }

    /** Le jeu de données de la série : celui de « Détails », sinon celui de la saisie
     *  ([SessionSaisieOcctax.datasetNouveauReleve]). */
    fun idDatasetEffectif(): Int? = idDataset ?: session.datasetNouveauReleve(config.idDatasetParDefaut)

    /** La date reste à choisir : le serveur la veut vide au départ (`DATE_FORM_WITH_TODAY` à faux,
     *  comme le web) et « Détails » ne l'a pas encore posée. */
    fun dateAChoisir(): Boolean = !config.occtaxDateDuJour && dateDebut == null

    private val memoRequis = MemoRequis()

    /** Les champs additionnels obligatoires à remplir de l'espèce, pour le jeu de la série. */
    fun requis(): RequisEspece = memoRequis.pour(config.additionalFieldsOcctaxJson, idDatasetEffectif())

    /** Un champ additionnel obligatoire de l'espèce (caractérisation ou dénombrement) reste vide dans
     *  les valeurs de la série : sa fiche les demande avant le premier « + ». */
    fun modeleIncomplet(): Boolean {
        val r = requis()
        val m = _modele.value
        return r.caracterisationIncomplete(m) || r.denombrementIncomplet(m)
    }

    /** Les valeurs de départ du dialogue « Détails du relevé » de la série. */
    fun donneesDetails(): DonneesDetailsReleve = DonneesDetailsReleve(
        titre = "Détails du relevé",
        datasets = DetailsReleveFormulaire.datasets(config),
        idDataset = idDatasetEffectif(),
        nomDataset = config.nomDataset.takeIf { it.isNotEmpty() },
        observateurs = DetailsReleveFormulaire.observateurs(config),
        idsObservateurs = DetailsReleveFormulaire.idsObservateursDepart(idsObservateurs, config),
        nomsObservateurs = DetailsReleveFormulaire.nomsObservateursDepart(nomsObservateurs, config),
        defsReleve = DetailsReleveFormulaire.defsReleve(config),
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
        heureDebut = heureDebut,
        heureFin = heureFin,
        heureFinVisible = config.heureFinVisible,
        observateursEnTexte = config.occtaxObservateursEnTexte,
        observateursTexte = observateursTexte ?: DetailsReleveFormulaire.observateursTexteDepart(config),
    )

    /** Ce que « + » enregistre en [latitude], [longitude] : une copie du modèle, relevé à part
     *  entière. Sans détails réglés, les champs de relevé prennent les défauts du serveur ; le
     *  jeu de données est figé au niveau de la saisie (la surcharge, sinon celui des relevés déjà
     *  enregistrés, sinon le défaut des paramètres). */
    fun observation(latitude: Double, longitude: Double): Observation {
        val m = _modele.value
        return m.surObservation(Observation(espece = m.espece.ifEmpty { m.taxon.nomSingulier }, latitude = latitude, longitude = longitude)).copy(
            additionalFieldsReleve = additionnelsReleve.ifEmpty {
                ChampsAdditionnels.defautsChampsReleve(config.additionalFieldsOcctaxJson, idDatasetEffectif())
            },
            idDatasetReleve = idDatasetEffectif(),
            typGrpReleve = typGrp.ifEmpty { null },
            champsReleveExtra = champsExtra,
            observateursReleveIds = idsObservateurs,
            observateursReleveNoms = nomsObservateurs,
            commentReleve = commentaire.ifEmpty { null },
            cdHabReleve = cdHab,
            habitatReleveLabel = habitatLabel,
            dateDebutReleve = dateDebut,
            dateFinReleve = dateFin,
            heureDebutReleve = heureDebut,
            heureFinReleve = heureFin,
            observateursReleveTxt = observateursTexte,
        )
    }

    /** « + » : l'observation est ajoutée à la saisie, qui l'enregistre aussitôt. */
    fun enregistrer(latitude: Double, longitude: Double): Observation =
        observation(latitude, longitude).also(session::ajouterObservation)

    // ── Affichage ──

    /** Sous le champ espèce des réglages, les valeurs de la série : les codes du sexe, du stade et de la
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

    /** Le résumé du panneau de la carte : « <espèce> × N », N le total de tous les dénombrements
     *  (défaut n° 5 de l'inventaire : GeoMys-Android n'y comptait que le n° 0). Seul depuis le
     *  25 novembre 2026 : les codes du sexe et du stade, en dessous, sont retirés à la demande de
     *  l'utilisateur. */
    fun resume(): String {
        val m = _modele.value
        return "${m.espece.ifEmpty { m.taxon.nomSingulier }} × ${m.total}"
    }

    companion object {
        const val LIBELLE = "Saisie mono-taxon"

        /** La consigne au-dessus de la carte. Sa seconde ligne (demande du 25 novembre 2026) dit qu'une
         *  observation touchée s'édite, comme l'aide de la carte multi-taxons ([CarteSaisie.AIDE_REPRISE]) :
         *  la ligne de confirmation de chaque « + », et son « Modifier », ne sont plus sous la carte. */
        const val CONSIGNE = "Touchez pour placer le point · appui long pour le déplacer\nTouchez une observation pour l'éditer"

        /** Le compteur du panneau de la carte. */
        fun compteur(n: Int): String = "$n obs. enregistrée${if (n > 1) "s" else ""}"

        /** « Continuer la saisie » d'une saisie mono-taxon (demande du 24 novembre 2026) : la dernière
         *  observation d'un « + » qui a une espèce de TaxRef (un cd_nom), où la carte s'ouvre sans GPS, et
         *  dont la série reprend l'espèce et les valeurs si la saisie ne les a pas gardées (saisie
         *  d'avant [Sortie.modeleMono]). Une espèce ajoutée par l'écran du relevé d'un « + » rejoint
         *  son relevé, après lui : de chaque relevé, la première. Null s'il n'y en a pas. */
        fun observationAReprendre(observations: List<Observation>): Observation? =
            observations.filter { !it.releveSansEspece && it.cdNom != null && it.espece.isNotBlank() }
                .groupBy { it.releveId?.takeIf(String::isNotEmpty) ?: it.id }
                .values.map { releve -> releve.minBy { it.date } }
                .sortedBy { it.date }.lastOrNull()

        /** Rayon dans lequel des observations sont « au même point » que le marqueur touché :
         *  plusieurs « + » sur une même position GPS (immobile, elle ne change pas) ou au centre de la
         *  carte sans GPS. Leurs marqueurs se recouvrent exactement : seul celui du dessus reçoit le
         *  toucher, et zoomer ne les sépare pas. */
        private const val RAYON_MEME_POINT_METRES = 1.0

        /** Marqueur touché (demande du 24 novembre 2026) : les observations au même point que
         *  [reference], la plus récente d'abord ; [reference] seule si elle n'en a pas, et son popup
         *  s'ouvre aussitôt. Sinon, on choisit d'abord laquelle. La liste ouvrait avant toutes celles
         *  à 30 m, même séparées sur la carte. */
        fun observationsAuPoint(reference: Observation, toutes: List<Observation>): List<Observation> =
            toutes.filter { distanceMetres(reference.latitude, reference.longitude, it.latitude, it.longitude) <= RAYON_MEME_POINT_METRES }
                .ifEmpty { listOf(reference) }
                .sortedByDescending { it.date }

        /** « 3 obs. à ce point ». */
        fun titreObservationsAuPoint(n: Int): String = "$n obs. à ce point"

        /** « HH:mm  espèce · N ind. », et les notes en dessous. */
        fun ligneObservationAuPoint(o: Observation): String {
            val effectif = if (o.totalIndividus > 1) " · ${o.totalIndividus} ind." else ""
            val notes = if (o.notes.isNotEmpty()) "\n   ${o.notes}" else ""
            return "${FormatDate.heure(o.date)}  ${o.espece}$effectif$notes"
        }

        /** Le popup de l'observation touchée, à éditer ou à supprimer : l'espèce en titre (une ligne
         *  sous Android) ; en message, l'heure de l'enregistrement, qui distingue les points de la
         *  série, l'effectif et les notes. */
        fun optionsObservation(o: Observation): Pair<String, String> {
            val heure = FormatDate.heure(o.date)
            if (o.releveSansEspece) return o.espece to "Enregistré à $heure"
            val n = o.totalIndividus
            val ligne = "Enregistrée à $heure · $n individu${if (n > 1) "s" else ""}"
            return o.espece to listOf(ligne, o.notes).filter { it.isNotEmpty() }.joinToString("\n")
        }

        /** « Éditer » : le relevé qui porte la fiche de [o] (null : [o] seule) et son rang dans ce
         *  relevé. Une observation rattachée à un relevé (une espèce y a été ajoutée depuis l'écran du
         *  relevé) s'ouvre par lui, au rang que lui donne [ReleveEnCours] : ouverte seule, elle aurait
         *  remplacé le relevé entier, et ses autres espèces auraient disparu. */
        fun ficheAOuvrir(o: Observation, toutes: List<Observation>): Pair<String?, Int> {
            val rid = o.releveId?.takeIf { it.isNotEmpty() } ?: return null to 0
            val rang = toutes.filter { it.releveId == rid }.indexOfFirst { it.id == o.id }
            return if (rang < 0) null to 0 else rid to rang
        }

        /** Tient sur une ligne sous Android, comme « Supprimer cette espèce ? ». */
        const val TITRE_SUPPRESSION = "Supprimer l'observation ?"

        /** Ce que « Supprimer » emporte, à confirmer comme la corbeille d'une espèce du relevé. La
         *  suppression reste locale : une donnée déjà transmise reste sur GeoNature, ce qu'il dit. */
        fun messageSuppression(o: Observation): String {
            val heure = FormatDate.heure(o.date)
            return if (o.releveSansEspece) {
                "Le relevé sans espèce enregistré à $heure sera supprimé." +
                    if (o.envoyeeServeur) " Il a déjà été transmis à GeoNature, où il restera." else ""
            } else {
                "« ${o.espece} », enregistrée à $heure, sera supprimée avec ce qui a été saisi pour elle." +
                    if (o.envoyeeServeur) " Elle a déjà été transmise à GeoNature, où elle restera." else ""
            }
        }
    }
}
