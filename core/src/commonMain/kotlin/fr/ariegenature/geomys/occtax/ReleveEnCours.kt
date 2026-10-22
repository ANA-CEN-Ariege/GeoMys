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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SaisieObservationFragment.kt) :
// les champs du fragment qui survivaient à la recréation de sa vue (les espèces en attente, les
// valeurs du relevé) et ses règles : reprise d'un relevé, valeurs de départ d'un nouveau relevé,
// enregistrement au fil de l'eau, relevé sans espèce. L'écran n'en garde que l'affichage.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.Denombrement
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Le déterminateur par défaut d'une nouvelle espèce : l'utilisateur connecté, par son nom complet
 *  (« NOM Prénom », `currentUser.nom_complet` du web), sinon son identifiant — et non l'observateur
 *  par défaut des Paramètres (demande du 21 octobre 2026). */
fun determinateurParDefaut(config: GeoNatureConfig): String = config.nomUtilisateur.ifEmpty { config.login }

/** Une espèce du relevé en cours (PendingObs). [existingId] : l'observation déjà enregistrée
 *  qu'elle modifie (relevé repris) ; [vmId] : son identité stable sinon, pour l'enregistrement au
 *  fil de l'eau. */
data class EspeceReleve(
    val taxon: Taxon,
    val espece: String,
    val cdNom: Int?,
    // ── Dénombrement n° 0 ──
    val nombre: Int = 1,
    val nombreMax: Int? = null,
    val sexe: String = "",
    val stadeVie: String = "",
    val objDenbr: String = "",
    val typDenbr: String = "",
    // ── Dénombrements supplémentaires ──
    val denombrementsAdditionnels: List<Denombrement> = emptyList(),
    // ── Caractérisation de l'occurrence ──
    val statutObs: String = "",
    val techniqueObs: String = "",
    val statutBio: String = "",
    val etaBio: String = "",
    val preuveExist: String = "",
    val comportement: String = "",
    val methDetermin: String = "",
    val naturalite: String = "",
    val determinateur: String = "",
    val notes: String = "",
    // ── Médias du dénombrement n° 0 ──
    val mediaUrisCounting0: List<String> = emptyList(),
    // ── Champs additionnels gn_commons ──
    val additionalFieldsReleve: Map<String, String> = emptyMap(),
    val additionalFieldsOccurrence: Map<String, String> = emptyMap(),
    /** Champs occurrence pilotés par form_fields (statut source, floutage, preuves). */
    val champsOccExtra: Map<String, String> = emptyMap(),
    val additionalFieldsCounting0: Map<String, String> = emptyMap(),
    val existingId: String? = null,
    val vmId: String = uuidAleatoire(),
) {
    /** « × N » de la liste : la somme des count_min de tous les dénombrements. */
    val total: Int get() = nombre + denombrementsAdditionnels.sumOf { it.nombreMin }

    /** Tous les dénombrements, le n° 0 tiré des champs à plat en tête. */
    fun denombrements(): List<Denombrement> = listOf(
        Denombrement(
            nombreMin = nombre,
            nombreMax = nombreMax ?: nombre,
            sexe = sexe.ifEmpty { null },
            stadeVie = stadeVie.ifEmpty { null },
            objDenbr = objDenbr.ifEmpty { null },
            typDenbr = typDenbr.ifEmpty { null },
            mediaUris = mediaUrisCounting0,
            additionalFields = additionalFieldsCounting0,
        ),
    ) + denombrementsAdditionnels

    /** Retour de l'écran Dénombrement : le n° 0 revient dans les champs à plat (un maximum égal au
     *  minimum n'est pas gardé), les suivants dans [denombrementsAdditionnels]. Liste vide :
     *  rien ne change. */
    fun avecDenombrements(liste: List<Denombrement>): EspeceReleve {
        val c0 = liste.firstOrNull() ?: return this
        return copy(
            nombre = c0.nombreMin,
            nombreMax = if (c0.nombreMax != c0.nombreMin) c0.nombreMax else null,
            sexe = c0.sexe ?: "",
            stadeVie = c0.stadeVie ?: "",
            objDenbr = c0.objDenbr ?: "",
            typDenbr = c0.typDenbr ?: "",
            mediaUrisCounting0 = c0.mediaUris,
            additionalFieldsCounting0 = c0.additionalFields,
            denombrementsAdditionnels = liste.drop(1),
        )
    }

    /** [base] portant cette espèce : groupe, cd_nom, dénombrements, caractérisation, médias et
     *  champs de l'occurrence (une valeur vide n'est pas envoyée). Le nom d'espèce et les valeurs
     *  du relevé restent à poser : les saisies mono et multi-taxons les règlent chacune à sa
     *  façon, mais reprennent ainsi les mêmes champs d'occurrence. */
    fun surObservation(base: Observation): Observation = base.copy(
        taxon = taxon,
        cdNom = cdNom,
        notes = notes,
        nombre = nombre,
        nombreMax = nombreMax,
        sexe = sexe.ifEmpty { null },
        stadeVie = stadeVie.ifEmpty { null },
        objDenbr = objDenbr.ifEmpty { null },
        typDenbr = typDenbr.ifEmpty { null },
        denombrementsAdditionnels = denombrementsAdditionnels,
        techniqueObs = techniqueObs.ifEmpty { null },
        statutObs = statutObs.ifEmpty { null },
        statutBio = statutBio.ifEmpty { null },
        etaBio = etaBio.ifEmpty { null },
        preuveExist = preuveExist.ifEmpty { null },
        comportement = comportement.ifEmpty { null },
        methDetermin = methDetermin.ifEmpty { null },
        naturalite = naturalite.ifEmpty { null },
        determinateur = determinateur.ifEmpty { null },
        mediaUrisCounting0 = mediaUrisCounting0,
        additionalFieldsOccurrence = additionalFieldsOccurrence,
        champsOccExtra = champsOccExtra,
        additionalFieldsCounting0 = additionalFieldsCounting0,
    )
}

/** Ce qui ouvre l'écran du relevé : un relevé repris ([releveId], ou une observation seule
 *  [obsId]), sinon un nouveau relevé posé à [geometrie], avec les champs additionnels déjà saisis
 *  dans l'écran intercalé ([additionnelsIntercales]). */
data class OuvertureReleve(
    val releveId: String? = null,
    val obsId: String? = null,
    val geometrie: GeometrieFinale? = null,
    val additionnelsIntercales: Map<String, String>? = null,
)

/** Le relevé en cours de saisie : ses espèces et ses valeurs communes, enregistrés au fil de l'eau
 *  dans la [session]. Le relevé ouvert est décrit par [ouverture] ; [dernierTaxon] est le groupe
 *  mémorisé de la saisie précédente. */
class ReleveEnCours(
    private val session: SessionSaisieOcctax,
    private val config: GeoNatureConfig,
    ouverture: OuvertureReleve,
    dernierTaxon: Taxon?,
) {
    private val _especes = MutableStateFlow<List<EspeceReleve>>(emptyList())

    /** Les espèces du relevé, dans l'ordre de saisie (la liste les AFFICHE à l'envers). */
    val especes: StateFlow<List<EspeceReleve>> = _especes.asStateFlow()

    // ── Valeurs du relevé, communes à toutes ses espèces (« Détails du relevé ») ──
    var additionnelsReleve: Map<String, String> = emptyMap()
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

    /** Géométrie du relevé : "Point" (null), "LineString" ou "Polygon", partagée par toutes ses
     *  espèces, et ses coordonnées JSON (vides pour un point). */
    var geometryType: String? = null
        private set
    var geometryCoordsJson: String? = null
        private set

    var latitude = 0.0
        private set
    var longitude = 0.0
        private set

    /** Relevé repris : son identifiant, réutilisé par les espèces qu'on y ajoute. */
    private var releveIdEdite: String? = null

    /** Identité du relevé pour toute la saisie : celle du relevé repris, sinon une nouvelle. Toutes
     *  ses espèces la partagent, et chaque modification réécrit le même relevé. */
    val releveId: String by lazy { releveIdEdite ?: uuidAleatoire() }

    /** Groupe taxonomique de départ de la palette. */
    val taxonInitial: Taxon

    /** Le relevé à reprendre n'est pas dans la session : sous Android, l'appli rouverte après la mort
     *  de son processus retrouve ses écrans, mais une session vide. L'écran se referme, plutôt que
     *  d'ouvrir un nouveau relevé sans position. */
    val introuvable: Boolean

    init {
        val existantes: List<Observation> = when {
            !ouverture.releveId.isNullOrEmpty() -> session.observations.value.filter { it.releveId == ouverture.releveId }
            ouverture.obsId != null -> session.observations.value.filter { it.id == ouverture.obsId }.take(1)
            else -> emptyList()
        }
        introuvable = existantes.isEmpty() && (!ouverture.releveId.isNullOrEmpty() || ouverture.obsId != null)
        if (existantes.isNotEmpty()) {
            val premier = existantes.first()
            latitude = premier.latitude
            longitude = premier.longitude
            taxonInitial = premier.taxon ?: Taxon.OISEAU
            releveIdEdite = premier.releveId
            // Toutes les observations d'un relevé partagent ses champs de relevé et sa géométrie.
            additionnelsReleve = premier.additionalFieldsReleve
            idDataset = premier.idDatasetReleve
            idsObservateurs = premier.observateursReleveIds.ifEmpty { listOfNotNull(premier.observateurReleveId) }
            nomsObservateurs = premier.observateursReleveNoms.ifEmpty { listOfNotNull(premier.observateurReleveNom) }
            commentaire = premier.commentReleve ?: ""
            cdHab = premier.cdHabReleve
            habitatLabel = premier.habitatReleveLabel
            // Date jamais fixée à la main : celle de la CRÉATION, pas « maintenant » (sinon une
            // saisie de la veille passait à aujourd'hui en rouvrant « Détails »).
            dateDebut = premier.dateDebutReleve ?: premier.date
            dateFin = premier.dateFinReleve
            heureDebut = premier.heureDebutReleve
            heureFin = premier.heureFinReleve
            observateursTexte = premier.observateursReleveTxt
            typGrp = premier.typGrpReleve ?: ""
            champsExtra = premier.champsReleveExtra
            geometryType = premier.geometryType
            geometryCoordsJson = premier.geometryCoordsJson
            _especes.value = existantes.map { o ->
                EspeceReleve(
                    taxon = o.taxon ?: Taxon.OISEAU,
                    espece = o.espece,
                    cdNom = o.cdNom,
                    nombre = o.nombre,
                    nombreMax = o.nombreMax,
                    sexe = o.sexe ?: "",
                    stadeVie = o.stadeVie ?: "",
                    objDenbr = o.objDenbr ?: "",
                    typDenbr = o.typDenbr ?: "",
                    denombrementsAdditionnels = o.denombrementsAdditionnels,
                    statutObs = o.statutObs ?: "",
                    techniqueObs = o.techniqueObs ?: "",
                    statutBio = o.statutBio ?: "",
                    etaBio = o.etaBio ?: "",
                    preuveExist = o.preuveExist ?: "",
                    comportement = o.comportement ?: "",
                    methDetermin = o.methDetermin ?: "",
                    naturalite = o.naturalite ?: "",
                    determinateur = o.determinateur ?: "",
                    notes = o.notes,
                    mediaUrisCounting0 = o.mediaUrisCounting0,
                    additionalFieldsReleve = o.additionalFieldsReleve,
                    additionalFieldsOccurrence = o.additionalFieldsOccurrence,
                    champsOccExtra = o.champsOccExtra,
                    additionalFieldsCounting0 = o.additionalFieldsCounting0,
                    existingId = o.id,
                )
            }
        } else {
            val geometrie = ouverture.geometrie
            latitude = geometrie?.latitude ?: 0.0
            longitude = geometrie?.longitude ?: 0.0
            taxonInitial = dernierTaxon ?: Taxon.OISEAU
            // Valeurs déjà validées dans l'écran intercalé (champs requis vérifiés en amont).
            ouverture.additionnelsIntercales?.takeIf { it.isNotEmpty() }?.let { additionnelsReleve = it }
            geometryType = geometrie?.type
            geometryCoordsJson = geometrie?.coordsJson
            // Jeu de données de la SAISIE : celui des relevés déjà enregistrés ; pour le tout premier,
            // la synchronisation fige le défaut de la config. Surchargé ensuite par les détails
            // communs ou « Détails ».
            idDataset = session.datasetSortie()
            // Saisie a posteriori : ce nouveau relevé HÉRITE de la date choisie plus tôt.
            if (session.saisieAPosteriori) {
                dateDebut = session.dateDebutReleveAPosteriori
                dateFin = session.dateFinReleveAPosteriori
            }
            // Détails COMMUNS de la saisie (« i » de la carte) : valeurs de départ, surchargeables par
            // « Détails ». Les champs additionnels de l'écran intercalé priment. Leur date ne passe
            // que par la saisie a posteriori, ci-dessus (défaut n° 4 de l'inventaire corrigé).
            session.detailsCommuns?.let { dc ->
                idDataset = dc.idDataset
                idsObservateurs = dc.idsObservateurs
                nomsObservateurs = dc.nomsObservateurs
                observateursTexte = dc.observateursTexte
                heureDebut = dc.heureDebut
                heureFin = dc.heureFin
                commentaire = dc.comment
                cdHab = dc.cdHab
                habitatLabel = dc.habitatLabel
                typGrp = dc.typGrp
                champsExtra = dc.champsExtra
                if (additionnelsReleve.isEmpty()) additionnelsReleve = dc.additionnels
            }
        }
    }

    /** Ajoute une espèce CHOISIE (avec son cd_nom, jamais re-résolu depuis son texte) et enregistre
     *  le relevé. Rend son rang. */
    fun ajouter(taxon: Taxon, espece: String, cdNom: Int?): Int {
        _especes.value += EspeceReleve(taxon, espece, cdNom, nombre = 1, determinateur = determinateurParDefaut(config))
        synchroniser()
        return _especes.value.lastIndex
    }

    fun supprimer(index: Int) {
        if (index !in _especes.value.indices) return
        _especes.value = _especes.value.filterIndexed { i, _ -> i != index }
        synchroniser()
    }

    /** Retour d'un sous-écran (Caractérisation, Dénombrement) : l'espèce [index] modifiée, et le
     *  relevé enregistré. */
    fun modifier(index: Int, transformation: (EspeceReleve) -> EspeceReleve) {
        val liste = _especes.value
        if (index !in liste.indices) return
        _especes.value = liste.toMutableList().also { it[index] = transformation(it[index]) }
        synchroniser()
    }

    /** « Détails du relevé » validé : les valeurs communes du relevé, enregistrées aussitôt. Sous
     *  GeoMys-Android, elles attendaient la modification suivante du relevé, et un retour juste
     *  après les perdait. Une date de début RÉELLEMENT modifiée passe la saisie en « a posteriori ». */
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
        if (res.dateModifiee) session.definirDateAPosteriori(res.dateDebut, res.dateFin)
        synchroniser()
    }

    /** Le jeu de données du relevé : celui choisi (« Détails », détails communs), sinon celui des
     *  relevés déjà enregistrés de la saisie (le relevé précédent du web), sinon celui des
     *  Paramètres s'il y en a un ; null : à choisir dans « Détails ». Ses champs additionnels sont
     *  ceux que le web montre pour lui. */
    fun idDatasetEffectif(): Int? = idDataset ?: session.datasetSortie() ?: config.idDatasetParDefaut

    /** La date reste à choisir : le serveur la veut vide au départ (`DATE_FORM_WITH_TODAY` à faux,
     *  comme le web) et « Détails » ne l'a pas encore posée. */
    fun dateAChoisir(): Boolean = !config.occtaxDateDuJour && dateDebut == null

    private val memoRequis = MemoRequis()

    /** Les champs additionnels obligatoires à remplir des espèces, pour le jeu du relevé. */
    fun requis(): RequisEspece = memoRequis.pour(config.additionalFieldsOcctaxJson, idDatasetEffectif())

    /** Les champs additionnels de relevé à enregistrer : ceux du relevé, sinon (écran intercalé
     *  sauté et « Détails » jamais ouvert) les valeurs par défaut du serveur. */
    private fun additionnelsAEnregistrer(): Map<String, String> = additionnelsReleve.ifEmpty {
        ChampsAdditionnels.defautsChampsReleve(config.additionalFieldsOcctaxJson, idDatasetEffectif())
    }

    /** Reflète les espèces dans la session, qui les enregistre aussitôt (synchroniserBatch).
     *  Idempotent : chaque espèce garde son identité, les espèces retirées quittent le relevé. */
    fun synchroniser() {
        val actuelles = session.observations.value
        val nouvelles = _especes.value.map { e ->
            val id = e.existingId ?: e.vmId
            val nom = e.espece.ifEmpty { "Espèce inconnue" }
            // L'observation existante garde sa date et sa position d'origine.
            val base = actuelles.firstOrNull { it.id == id } ?: Observation(id = id, espece = nom, latitude = latitude, longitude = longitude)
            e.surObservation(base).copy(
                espece = nom,
                additionalFieldsReleve = additionnelsAEnregistrer(),
                // Jeu de données FIGÉ à la création du relevé : la surcharge explicite, sinon celui
                // déjà enregistré, sinon celui que « Détails » propose au premier enregistrement.
                idDatasetReleve = idDataset ?: base.idDatasetReleve ?: idDatasetEffectif(),
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
                typGrpReleve = typGrp.ifEmpty { null },
                champsReleveExtra = champsExtra,
                geometryType = geometryType,
                geometryCoordsJson = geometryCoordsJson,
                releveId = releveId,
            )
        }
        session.remplacerObservationsDuReleve(releveId, nouvelles)
    }

    /** ✓ « Terminer le relevé » : sans espèce, un « relevé sans espèce » est enregistré (une donnée
     *  d'ABSENCE, envoyée comme un relevé seul) ; sinon le relevé, déjà enregistré au fil de l'eau,
     *  l'est encore par sécurité. Rend le message à afficher. */
    fun terminer(): String {
        val n = _especes.value.size
        if (n == 0) {
            enregistrerSansEspece()
            return "Relevé sans espèce enregistré — retrouvez-le dans « Mes saisies »"
        }
        synchroniser()
        return "Relevé enregistré ($n espèce${if (n > 1) "s" else ""}) — retrouvez-le dans « Mes saisies »"
    }

    /** Remplace le relevé par une seule observation sans taxon ([Observation.releveSansEspece])
     *  portant sa géométrie et ses détails communs. */
    private fun enregistrerSansEspece() {
        val base = session.observations.value.firstOrNull { it.releveId == releveId }
            ?: Observation(id = uuidAleatoire(), espece = ESPECE_SANS_ESPECE, latitude = latitude, longitude = longitude)
        val substitut = base.copy(
            espece = ESPECE_SANS_ESPECE,
            taxon = null,
            cdNom = null,
            nombre = 0,
            nombreMax = null,
            denombrementsAdditionnels = emptyList(),
            mediaUrisCounting0 = emptyList(),
            releveSansEspece = true,
            latitude = latitude,
            longitude = longitude,
            geometryType = geometryType,
            geometryCoordsJson = geometryCoordsJson,
            releveId = releveId,
            additionalFieldsReleve = additionnelsAEnregistrer(),
            idDatasetReleve = idDataset ?: base.idDatasetReleve ?: idDatasetEffectif(),
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
            typGrpReleve = typGrp.ifEmpty { null },
            champsReleveExtra = champsExtra,
        )
        session.remplacerObservationsDuReleve(releveId, listOf(substitut))
    }

    companion object {
        const val ESPECE_SANS_ESPECE = "Relevé sans espèce"
    }
}
