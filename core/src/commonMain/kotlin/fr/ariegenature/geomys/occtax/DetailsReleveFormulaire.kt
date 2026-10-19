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

// Extrait de GeoMys-Android @ cdd5196 : la logique du dialogue « Détails du relevé »
// (ui/DetailsReleveDialog.kt — listes proposées, dates, résultat) et ses valeurs de départ, pour un relevé
// (ouvrirDetailsReleve de ui/SaisieObservationFragment.kt) ou pour toute la saisie (ouvrirDetailsCommuns de
// ui/TraceFragment.kt). Le dialogue lui-même est un composable.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureObservateur
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.ListeTaxons
import fr.ariegenature.geomys.store.io.listeDepuisJson
import kotlin.math.max
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Les valeurs de départ du dialogue « Détails du relevé ». [infos] : lignes en lecture seule
 *  (libellé, valeur) ; [datasets] et [observateurs] : (id, libellé) proposés ; [heures] et
 *  [avecFin] : ce que le serveur active pour la date ; [titre] : « Détails du relevé », ou « Détails
 *  des relevés » pour toute la saisie. */
data class DonneesDetailsReleve(
    val titre: String,
    val infos: List<Pair<String, String>>,
    val datasets: List<Pair<Int, String>>,
    val idDataset: Int?,
    val nomDataset: String?,
    val observateurs: List<Pair<Int, String>>,
    val idsObservateurs: List<Int>,
    val nomsObservateurs: List<String>,
    val defs: List<AdditionalFieldDef>,
    val additionnels: Map<String, String>,
    val formFieldsJson: String,
    val typGrp: String,
    val commentaire: String,
    val dateDebut: Long?,
    val dateFin: Long?,
    val heures: Boolean,
    val avecFin: Boolean,
    val cdHab: Int?,
    val habitatLabel: String?,
    val champsExtra: Map<String, String>,
)

object DetailsReleveFormulaire {

    /** Jeux de données proposables : actifs, rattachés à OCCTAX (ou sans module déclaré), créables
     *  s'il y a une liste des créables ; libellé « nom (id) ». */
    fun datasets(config: GeoNatureConfig): List<Pair<Int, String>> = try {
        val creables = config.datasetsCreablesOcctax
        listeDepuisJson(GeoNatureDataset.serializer(), config.datasetsCacheJson)
            .filter { it.actif && (it.moduleCodes.isEmpty() || "OCCTAX" in it.moduleCodes) && (creables.isEmpty() || it.id in creables) }
            .map { it.id to "${it.nom} (${it.id})" }
    } catch (_: Exception) {
        emptyList()
    }

    /** Filtre de la liste d'un champ déroulant (jeu de données, observateurs) : les positions des
     *  [libelles] qui contiennent [saisie], ni les accents ni la casse ne comptant ; saisie vide : tout
     *  (l'AdaptateurAutocomplete de GeoMys-Android). */
    fun filtrer(libelles: List<String>, saisie: String): List<Int> {
        val cle = ListeTaxons.normaliser(saisie)
        return libelles.indices.filter { cle.isEmpty() || ListeTaxons.normaliser(libelles[it]).contains(cle) }
    }

    /** Observateurs proposables, depuis le cache. */
    fun observateurs(config: GeoNatureConfig): List<Pair<Int, String>> = try {
        listeDepuisJson(GeoNatureObservateur.serializer(), config.observateursCacheJson).map { it.idRole to it.nomComplet }
    } catch (_: Exception) {
        emptyList()
    }

    /** Les champs simples du relevé : (clé form_fields, libellé du web, numérique ?). */
    val CHAMPS_SIMPLES = listOf(
        Triple("altitude_min", "Altitude min", true),
        Triple("altitude_max", "Altitude max", true),
        Triple("depth_min", "Profondeur min", true),
        Triple("depth_max", "Profondeur max", true),
        Triple("place_name", "Nom du lieu", false),
        Triple("precision", "Précision du pointage (m)", true),
        Triple("grp_method", "Méthode de regroupement", false),
    )

    /** Les nomenclatures du relevé : (code, clé form_fields, clé de [DonneesDetailsReleve.champsExtra]
     *  ou null pour TYP_GRP, porté à part). */
    val NOMENCLATURES = listOf(
        Triple("TYP_GRP", "group_type", null),
        Triple("NAT_OBJ_GEO", "geo_object_nature", "geo_object_nature"),
        Triple("TECHNIQUE_OBS", "tech_collect", "tech_collect"),
    )

    /** Les champs additionnels de relevé visibles pour le jeu de données de la configuration (sans
     *  restriction de liste : un relevé porte plusieurs taxons). */
    fun defsReleve(config: GeoNatureConfig): List<AdditionalFieldDef> =
        ChampsAdditionnels.fromJson(config.additionalFieldsOcctaxJsonActif)
            .filter { it.appliqueA(AdditionalFieldsObject.RELEVE) }
            .filter { it.visiblePour(config.idDataset.toIntOrNull(), emptyList()) }

    /** Observateurs de départ : ceux portés, sinon l'observateur par défaut ou l'utilisateur connecté. */
    fun idsObservateursDepart(ids: List<Int>, config: GeoNatureConfig): List<Int> =
        ids.ifEmpty { listOfNotNull(config.observateurDefautId.toIntOrNull() ?: config.idRoleUtilisateur.takeIf { it > 0 }) }

    fun nomsObservateursDepart(noms: List<String>, config: GeoNatureConfig): List<String> =
        noms.ifEmpty {
            listOfNotNull(config.observateurDefautNom.ifEmpty { config.nomUtilisateur.ifEmpty { config.login } }.takeIf { it.isNotBlank() })
        }

    /** Les détails d'un relevé en cours (bouton « Détails »). */
    fun pourReleve(releve: ReleveEnCours, config: GeoNatureConfig): DonneesDetailsReleve = DonneesDetailsReleve(
        titre = "Détails du relevé",
        // (La ligne « Position » a été retirée : la position reste visible sur la carte.)
        infos = listOfNotNull(releve.geometryType?.takeIf { it.isNotEmpty() && it != "Point" }?.let { "Géométrie" to it }),
        datasets = datasets(config),
        idDataset = releve.idDataset ?: config.idDataset.toIntOrNull(),
        nomDataset = config.nomDataset.takeIf { it.isNotEmpty() },
        observateurs = observateurs(config),
        idsObservateurs = idsObservateursDepart(releve.idsObservateurs, config),
        nomsObservateurs = nomsObservateursDepart(releve.nomsObservateurs, config),
        defs = defsReleve(config),
        additionnels = releve.additionnelsReleve,
        formFieldsJson = config.formFieldsJson,
        typGrp = releve.typGrp,
        commentaire = releve.commentaire,
        dateDebut = releve.dateDebut,
        dateFin = releve.dateFin,
        heures = config.heuresVisibles,
        avecFin = config.dateFinVisible,
        cdHab = releve.cdHab,
        habitatLabel = releve.habitatLabel,
        champsExtra = releve.champsExtra,
    )

    /** Les détails COMMUNS de la saisie (bouton « i » de la carte) : les valeurs communes déjà
     *  réglées, ou les défauts de la configuration au premier usage. */
    fun communs(communs: DetailsReleveResult?, config: GeoNatureConfig): DonneesDetailsReleve = DonneesDetailsReleve(
        titre = "Détails des relevés",
        infos = emptyList(),
        datasets = datasets(config),
        idDataset = communs?.idDataset ?: config.idDataset.toIntOrNull(),
        nomDataset = config.nomDataset.takeIf { it.isNotEmpty() },
        observateurs = observateurs(config),
        idsObservateurs = idsObservateursDepart(communs?.idsObservateurs.orEmpty(), config),
        nomsObservateurs = nomsObservateursDepart(communs?.nomsObservateurs.orEmpty(), config),
        defs = defsReleve(config),
        additionnels = communs?.additionnels.orEmpty(),
        formFieldsJson = config.formFieldsJson,
        typGrp = communs?.typGrp.orEmpty(),
        commentaire = communs?.comment.orEmpty(),
        dateDebut = communs?.dateDebut,
        dateFin = communs?.dateFin,
        heures = config.heuresVisibles,
        avecFin = config.dateFinVisible,
        cdHab = communs?.cdHab,
        habitatLabel = communs?.habitatLabel,
        champsExtra = communs?.champsExtra.orEmpty(),
    )

    // ── Dates ──

    /** La date de fin est-elle montrée d'emblée ? Seulement si le relevé en porte une, distincte du
     *  début (sinon un « ＋ » la révèle). */
    fun finActiveAuDepart(d: DonneesDetailsReleve): Boolean = d.avecFin && d.dateFin != null && d.dateFin != d.dateDebut

    /** [millis] au jour [annee]-[mois]-[jour] (mois de 1 à 12), l'heure gardée (DatePickerDialog
     *  sur un Calendar), dans le fuseau du téléphone. */
    fun avecJour(millis: Long, annee: Int, mois: Int, jour: Int): Long =
        avecJour(millis, LocalDate(annee, mois, jour), TimeZone.currentSystemDefault())

    /** [millis] à l'heure [heure]:[minute] dans le fuseau du téléphone (TimePickerDialog). */
    fun avecHeure(millis: Long, heure: Int, minute: Int): Long = avecHeure(millis, heure, minute, TimeZone.currentSystemDefault())

    /** Les dates envoyées, dans le fuseau du téléphone (cf. l'autre [dates]). */
    fun dates(debut: Long, fin: Long, finActive: Boolean, heures: Boolean, avecFin: Boolean): Pair<Long, Long> =
        dates(debut, fin, finActive, heures, avecFin, TimeZone.currentSystemDefault())

    /** Le jour de [millis] : (année, mois de 1 à 12, jour), dans le fuseau du téléphone. */
    fun jourDe(millis: Long): Triple<Int, Int, Int> =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date.let { Triple(it.year, it.month.number, it.day) }

    /** L'heure de [millis] : (heure, minute), dans le fuseau du téléphone. */
    fun heureDe(millis: Long): Pair<Int, Int> =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).let { it.hour to it.minute }

    /** [millis] au jour [date], l'heure gardée. */
    internal fun avecJour(millis: Long, date: LocalDate, fuseau: TimeZone): Long {
        val actuel = Instant.fromEpochMilliseconds(millis).toLocalDateTime(fuseau)
        return LocalDateTime(date, actuel.time).toInstant(fuseau).toEpochMilliseconds()
    }

    /** [millis] à l'heure [heure]:[minute], le jour et les secondes gardés. */
    internal fun avecHeure(millis: Long, heure: Int, minute: Int, fuseau: TimeZone): Long {
        val actuel = Instant.fromEpochMilliseconds(millis).toLocalDateTime(fuseau)
        val temps = LocalTime(heure, minute, actuel.second, actuel.nanosecond)
        return LocalDateTime(actuel.date, temps).toInstant(fuseau).toEpochMilliseconds()
    }

    /** Les dates envoyées : sans heures, la journée entière (00:00 → 23:59) ; sans date de fin, la fin
     *  vaut le début ; la fin n'est jamais avant le début. */
    internal fun dates(debut: Long, fin: Long, finActive: Boolean, heures: Boolean, avecFin: Boolean, fuseau: TimeZone): Pair<Long, Long> {
        var d = debut
        var f = fin
        if (!heures) {
            d = aLHeure(d, 0, 0, fuseau)
            f = aLHeure(f, 23, 59, fuseau)
        }
        return d to (if (avecFin && finActive) max(f, d) else d)
    }

    private fun aLHeure(millis: Long, heure: Int, minute: Int, fuseau: TimeZone): Long {
        val actuel = Instant.fromEpochMilliseconds(millis).toLocalDateTime(fuseau)
        // Les millisecondes restent celles du Calendar d'origine (seules les secondes sont remises à 0).
        val temps = LocalTime(heure, minute, 0, actuel.nanosecond)
        return LocalDateTime(actuel.date, temps).toInstant(fuseau).toEpochMilliseconds()
    }

    // ── Résultat ──

    /** « Valider » : les saisies relues. Un champ masqué garde sa valeur de départ ; un champ simple ou
     *  une nomenclature vidés quittent [DonneesDetailsReleve.champsExtra]. [simples] : texte des
     *  champs simples montrés ; [nomenclatures] : code choisi des nomenclatures montrées ;
     *  [habitatTexte] : le texte du champ habitat, libellé du cd_hab choisi. */
    fun resultat(
        d: DonneesDetailsReleve,
        dataset: Pair<Int, String>?,
        observateurs: List<Pair<Int, String>>,
        additionnels: Map<String, String>?,
        commentaire: String,
        cdHab: Int?,
        habitatTexte: String,
        simples: Map<String, String>,
        nomenclatures: Map<String, String>,
        dates: Pair<Long, Long>,
        dateModifiee: Boolean,
    ): DetailsReleveResult {
        val extra = HashMap(d.champsExtra)
        simples.forEach { (cle, texte) ->
            val v = texte.trim()
            if (v.isEmpty()) extra.remove(cle) else extra[cle] = v
        }
        for ((code, _, cleExtra) in NOMENCLATURES) {
            if (cleExtra == null || code !in nomenclatures) continue
            val v = nomenclatures.getValue(code)
            if (v.isEmpty()) extra.remove(cleExtra) else extra[cleExtra] = v
        }
        return DetailsReleveResult(
            idDataset = dataset?.first,
            idsObservateurs = observateurs.map { it.first },
            nomsObservateurs = observateurs.map { it.second },
            additionnels = if (d.defs.isNotEmpty() && additionnels != null) additionnels else d.additionnels,
            comment = commentaire.trim(),
            cdHab = cdHab,
            habitatLabel = cdHab?.let { habitatTexte.trim() },
            typGrp = nomenclatures["TYP_GRP"] ?: d.typGrp,
            dateDebut = dates.first,
            dateFin = dates.second,
            champsExtra = extra,
            dateModifiee = dateModifiee,
        )
    }
}
