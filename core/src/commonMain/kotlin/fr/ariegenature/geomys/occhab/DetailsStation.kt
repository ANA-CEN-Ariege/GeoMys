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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/OccHabDetailsDialog.kt et
// DetailsReleveDialog.kt pour datasetsPourOccHab) : les textes, les listes, la validation et l'application
// du dialogue des détails d'une station. Les saisies en attente vivent dans l'état de l'écran.

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureObservateur
import fr.ariegenature.geomys.occtax.DetailsReleveFormulaire
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.io.listeDepuisJson
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/** Les saisies du dialogue au moment de « Valider » : ce que l'état de l'écran a recueilli.
 *  Un champ masqué par `OCCHAB.formConfig` (widget non créé) vaut null et garde sa valeur. */
data class SaisiesDetailsStation(
    val idDataset: Int?,
    val nomDataset: String?,
    val observateurs: List<Pair<Int, String>>,
    /** Observateurs en texte libre (`OCCHAB.OBSERVER_AS_TXT`), null quand ils se choisissent dans
     *  la liste. */
    val observateursTexte: String? = null,
    val dateMin: Long?,
    val dateMax: Long?,
    /** Nature de l'objet géographique ; [natureVisible] faux = champ masqué. */
    val natureVisible: Boolean,
    val nature: Int?,
    // ── Dialogue « Détails » complet seulement (null = champ masqué) ──
    val methodeVisible: Boolean = false,
    val methode: Int? = null,
    val altitudeMin: String? = null,
    val altitudeMax: String? = null,
    val profondeurMin: String? = null,
    val profondeurMax: String? = null,
    val expositionVisible: Boolean = false,
    val exposition: Int? = null,
    val surface: String? = null,
    val typeSolVisible: Boolean = false,
    val typeSol: Int? = null,
    val commentaire: String? = null,
    /** Bloc ANA-EVAL relu de la section, si la station en porte un (sinon on n'y touche pas). */
    val anaEval: (() -> String?)? = null,
)

/** Le dialogue « Informations obligatoires » (démarrage d'une station) / « Détails de la station ». */
object DetailsStation {

    const val TITRE_DEMARRAGE = "Informations obligatoires"
    const val TITRE_DETAILS = "Détails de la station"

    // Les textes du formulaire web (occhab-form.component.html et fr.json), sans astérisques comme
    // lui. Les profondeurs s'ouvrent par le « + » qui suit les altitudes.
    const val LIBELLE_JDD = "Jeux de données"
    const val INVITE_JDD = "Jeux de données"
    const val AUCUN_JDD = "Aucun jeu de données OccHab en cache — lancez « Recharger les données »."
    const val LIBELLE_OBSERVATEURS = "Observateurs"
    const val INVITE_OBSERVATEURS = "Observateur(s)"
    const val LIBELLE_DATE_MIN = "Date début"
    const val LIBELLE_DATE_MAX = "Date fin"
    const val LIBELLE_NATURE = "Nature objet géographique"
    const val LIBELLE_ALTITUDE_MIN = "Altitude min"
    const val LIBELLE_ALTITUDE_MAX = "Altitude max"
    const val LIBELLE_PROFONDEUR_MIN = "Profondeur min"
    const val LIBELLE_PROFONDEUR_MAX = "Profondeur max"
    const val AFFICHER_PROFONDEURS = "Afficher les profondeurs"
    const val MASQUER_PROFONDEURS = "Masquer les profondeurs"
    const val LIBELLE_EXPOSITION = "Exposition"
    const val LIBELLE_SURFACE = "Surface (en m²)"
    const val LIBELLE_METHODE = "Méthode de calcul de la surface"
    const val LIBELLE_TYPE_SOL = "Type de sol"
    /** Le commentaire n'a pas de libellé sur le web : seulement cette indication. */
    const val INVITE_COMMENTAIRE = "Commentaire"

    /** Jeux de données proposables, ceux du web (POST `meta/datasets` avec `active`, `module_code`
     *  et `create` OCCHAB) : actifs, rattachés à OCCHAB et créables ; sans liste des créables, aucun.
     *  Libellé : le nom seul. */
    fun datasets(config: GeoNatureConfig): List<Pair<Int, String>> = try {
        val creables = config.datasetsCreablesOcchab
        listeDepuisJson(GeoNatureDataset.serializer(), config.datasetsOcchabCacheJson)
            .filter { it.actif && "OCCHAB" in it.moduleCodes && it.id in creables }
            .map { it.id to it.nom }
    } catch (_: Exception) {
        emptyList()
    }

    /** Le nom du jeu [id] s'il est inactif : le web montre alors le jeu d'une station en lecture
     *  seule (« Jeux de données: » et son nom). */
    fun nomJeuInactif(config: GeoNatureConfig, id: Int?): String? = try {
        id?.let { listeDepuisJson(GeoNatureDataset.serializer(), config.datasetsOcchabCacheJson).firstOrNull { d -> d.id == it && !d.actif }?.nom }
    } catch (_: Exception) {
        null
    }

    /** Observateurs proposables : ceux de la liste d'OccHab (`OCCHAB.OBSERVER_LIST_ID`) en cache,
     *  comme le web ; aucun tant que la synchro ne l'a pas chargée. */
    fun observateurs(config: GeoNatureConfig): List<Pair<Int, String>> = try {
        listeDepuisJson(GeoNatureObservateur.serializer(), config.observateursOcchabCacheJson).map { it.idRole to it.nomComplet }
    } catch (_: Exception) {
        emptyList()
    }

    /** Le jeu de données affiché à l'ouverture : son nom dans la liste, sinon celui porté. */
    fun libelleJdd(details: OccHabDetailsSession, datasets: List<Pair<Int, String>>): String? =
        datasets.firstOrNull { it.first == details.idDataset }?.second ?: details.nomDataset

    /** Minuit, heure du téléphone, du jour choisi : les dates de station OccHab sont des jours
     *  (yyyy-MM-dd côté serveur, NgbDate côté web), et deux sélections du même jour donnent la
     *  même valeur. [mois] de 1 à 12. */
    fun jour(annee: Int, mois: Int, jour: Int, fuseau: TimeZone = TimeZone.currentSystemDefault()): Long =
        LocalDate(annee, mois, jour).atStartOfDayIn(fuseau).toEpochMilliseconds()

    /** Le premier champ REQUIS manquant (Validators.required du client web OccHab), ou null. Masqué
     *  par formConfig, un champ requis l'est encore, comme sur le web : une date masquée n'a pas de
     *  date du jour, une nature masquée garde le défaut du serveur. Les champs requis sont présents
     *  dans les DEUX modes. */
    fun champManquant(s: SaisiesDetailsStation, @Suppress("UNUSED_PARAMETER") config: GeoNatureConfig): String? = when {
        s.idDataset == null -> "Choisissez un jeu de données"
        s.observateursTexte != null && s.observateursTexte.isBlank() -> "Saisissez les observateurs"
        s.observateursTexte == null && s.observateurs.isEmpty() -> "Choisissez un observateur"
        s.dateMin == null -> "Choisissez une date de début"
        s.dateMax == null -> "Choisissez une date de fin"
        s.nature == null -> "Choisissez la nature de l'objet géographique"
        else -> null
    }

    /** dateValidator du web : une date de fin antérieure au début bloque « Valider ». */
    fun datesIncoherentes(min: Long?, max: Long?): String? =
        if (min != null && max != null && max < min) "La date de début ne peut pas être postérieure à la date de fin" else null

    /** minMaxValidator du web, qui ne compare que deux altitudes non nulles et différentes de 0. */
    fun altitudesIncoherentes(min: String?, max: String?): String? {
        val a = min?.trim()?.toIntOrNull()?.takeIf { it != 0 }
        val b = max?.trim()?.toIntOrNull()?.takeIf { it != 0 }
        return if (a != null && b != null && a > b) "L'altitude max ne peut être inférieure à l'altitude min" else null
    }

    /** Application ATOMIQUE des sélections : rien avant, tout ici (« Annuler » = sans effet).
     *  [demarrage] : formulaire de démarrage → ses valeurs deviennent les défauts des nouvelles
     *  stations ; « i » / « Détails » → la station sélectionnée seulement (détails par station,
     *  décision 2026-08-27). Garde serveur : date_max ≥ date_min. */
    fun appliquer(session: SessionOccHab, s: SaisiesDetailsStation, demarrage: Boolean) {
        val avant = session.details
        val nature = if (s.natureVisible) s.nature else avant.idNomObjetGeographique
        val fin = s.dateMax?.let { f -> maxOf(f, s.dateMin ?: f) }
        session.majDetails(demarrage) {
            it.idDataset = s.idDataset
            it.nomDataset = s.nomDataset
            if (s.observateursTexte != null) {
                it.observateursTxt = s.observateursTexte.trim()
            } else {
                it.observateursIds = s.observateurs.map { o -> o.first }
                it.observateursNoms = s.observateurs.map { o -> o.second }
            }
            it.dateMin = s.dateMin
            it.dateMax = fin
            it.idNomObjetGeographique = nature
        }
        if (demarrage) return
        session.majDetails {
            it.idNomCalculSurface = if (s.methodeVisible) s.methode else avant.idNomCalculSurface
            it.idNomTypeSol = if (s.typeSolVisible) s.typeSol else avant.idNomTypeSol
            it.idNomExposition = if (s.expositionVisible) s.exposition else avant.idNomExposition
            it.profondeurMin = if (s.profondeurMin != null) s.profondeurMin.trim().toIntOrNull() else avant.profondeurMin
            it.profondeurMax = if (s.profondeurMax != null) s.profondeurMax.trim().toIntOrNull() else avant.profondeurMax
            it.comment = if (s.commentaire != null) s.commentaire.trim().takeIf { c -> c.isNotEmpty() } else avant.comment
        }
        // Altitudes et surface : PAR STATION (auto-remplies depuis la géométrie ; une édition
        // manuelle ici tient jusqu'au prochain redessin de la géométrie).
        val st = session.station
        session.definirAltitudes(
            if (s.altitudeMin != null) s.altitudeMin.trim().toIntOrNull() else st.altitudeMin,
            if (s.altitudeMax != null) s.altitudeMax.trim().toIntOrNull() else st.altitudeMax,
        )
        session.definirSurface(if (s.surface != null) s.surface.trim().toLongOrNull() else st.surface)
        // Bloc ANA-EVAL : réécrit sur la STATION courante — il repartira fusionné dans `comment`
        // à l'envoi. Section absente (station sans bloc) → rien n'est touché.
        s.anaEval?.let { session.definirAnaEval(it()) }
    }

    /** Mémorise les infos obligatoires validées AU DÉMARRAGE : le formulaire du RELEVÉ SUIVANT
     *  repartira de ces valeurs (dates exclues au rechargement). Ni depuis le « i » d'une station
     *  (détails PAR station : les observateurs d'une station importée du serveur ne sont pas un
     *  défaut de session), ni le commentaire, l'exposition et les profondeurs (propres à une
     *  station) — audit 2026-08-27. */
    fun memoriser(config: GeoNatureConfig, details: OccHabDetailsSession) {
        try {
            config.occhabDetailsPrecedentsJson =
                details.copy(comment = null, idNomExposition = null, profondeurMin = null, profondeurMax = null).versJson()
        } catch (_: Exception) {
        }
    }
}
