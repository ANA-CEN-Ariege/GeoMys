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
    val surface: String? = null,
    val commentaire: String? = null,
    /** Bloc ANA-EVAL relu de la section, si la station en porte un (sinon on n'y touche pas). */
    val anaEval: (() -> String?)? = null,
)

/** Le dialogue « Informations obligatoires » (démarrage d'une station) / « Détails de la station ». */
object DetailsStation {

    const val TITRE_DEMARRAGE = "Informations obligatoires"
    const val TITRE_DETAILS = "Détails de la station"

    const val LIBELLE_JDD = "Jeu de données *"
    const val INVITE_JDD = "Rechercher un jeu de données"
    const val AUCUN_JDD = "Aucun jeu de données OccHab en cache — lancez « Recharger les données »."
    const val LIBELLE_OBSERVATEURS = "Observateurs *"
    const val LIBELLE_DATE_MIN = "Date de début *"
    const val LIBELLE_DATE_MAX = "Date de fin *"
    const val LIBELLE_NATURE = "Nature de l'objet géographique *"
    const val LIBELLE_ALTITUDE_MIN = "Altitude min (m)"
    const val LIBELLE_ALTITUDE_MAX = "Altitude max (m)"
    const val LIBELLE_SURFACE = "Surface (m²)"
    const val LIBELLE_METHODE = "Méthode de calcul de la surface"
    const val LIBELLE_COMMENTAIRE = "Commentaire"

    /** Jeux de données proposables pour OccHab (`datasetsPourOccHab`) : actifs, rattachés à
     *  OCCHAB (ou sans module déclaré), créables s'il y a une liste des créables ; libellé
     *  « nom (id) ». */
    fun datasets(config: GeoNatureConfig): List<Pair<Int, String>> = try {
        val creables = config.datasetsCreablesOcchab
        listeDepuisJson(GeoNatureDataset.serializer(), config.datasetsOcchabCacheJson)
            .filter { it.actif && (it.moduleCodes.isEmpty() || "OCCHAB" in it.moduleCodes) && (creables.isEmpty() || it.id in creables) }
            .map { it.id to "${it.nom} (${it.id})" }
    } catch (_: Exception) {
        emptyList()
    }

    /** Observateurs proposables, depuis le cache (`observateursPourDetailsReleve`). */
    fun observateurs(config: GeoNatureConfig): List<Pair<Int, String>> = DetailsReleveFormulaire.observateurs(config)

    /** Le jeu de données affiché à l'ouverture : son nom porté, sinon celui de la liste. */
    fun libelleJdd(details: OccHabDetailsSession, datasets: List<Pair<Int, String>>): String? =
        details.nomDataset ?: datasets.firstOrNull { it.first == details.idDataset }?.second

    /** Minuit, heure du téléphone, du jour choisi : les dates de station OccHab sont des jours
     *  (yyyy-MM-dd côté serveur, NgbDate côté web), et deux sélections du même jour donnent la
     *  même valeur. [mois] de 1 à 12. */
    fun jour(annee: Int, mois: Int, jour: Int, fuseau: TimeZone = TimeZone.currentSystemDefault()): Long =
        LocalDate(annee, mois, jour).atStartOfDayIn(fuseau).toEpochMilliseconds()

    /** Le premier champ REQUIS manquant (Validators.required du client web OccHab), ou null. Un
     *  champ MASQUÉ par formConfig n'est pas bloquant (sa valeur est préservée) — anti-impasse,
     *  seule entorse assumée au web. Les champs requis sont présents dans les DEUX modes. */
    fun champManquant(s: SaisiesDetailsStation, config: GeoNatureConfig): String? = when {
        s.idDataset == null -> "Choisissez un jeu de données"
        s.observateurs.isEmpty() -> "Choisissez au moins un observateur"
        config.occhabChampVisible("date_min") && s.dateMin == null -> "Choisissez une date de début"
        config.occhabChampVisible("date_max") && s.dateMax == null -> "Choisissez une date de fin"
        s.natureVisible && s.nature == null -> "Choisissez la nature de l'objet géographique"
        else -> null
    }

    /** Parité minMaxValidator du web : altitudes incohérentes = blocage explicite. */
    fun altitudesIncoherentes(min: String?, max: String?): String? {
        val a = min?.trim()?.toIntOrNull()
        val b = max?.trim()?.toIntOrNull()
        return if (a != null && b != null && a > b) "Altitude min supérieure à l'altitude max" else null
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
            it.observateursIds = s.observateurs.map { o -> o.first }
            it.observateursNoms = s.observateurs.map { o -> o.second }
            it.dateMin = s.dateMin
            it.dateMax = fin
            it.idNomObjetGeographique = nature
        }
        if (demarrage) return
        session.majDetails {
            it.idNomCalculSurface = if (s.methodeVisible) s.methode else avant.idNomCalculSurface
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
     *  défaut de session), ni le commentaire (propre à un relevé) — audit 2026-08-27. */
    fun memoriser(config: GeoNatureConfig, details: OccHabDetailsSession) {
        try {
            config.occhabDetailsPrecedentsJson = details.copy(comment = null).versJson()
        } catch (_: Exception) {
        }
    }
}
