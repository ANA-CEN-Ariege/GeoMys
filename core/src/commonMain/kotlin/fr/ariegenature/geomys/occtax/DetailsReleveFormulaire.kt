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
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Les valeurs de départ du dialogue « Détails du relevé ». [datasets] et [observateurs] : (id,
 *  libellé) proposés ; [heures] et [avecFin] : ce que le serveur active pour la date ; [titre] :
 *  « Détails du relevé », ou « Détails des relevés » pour toute la saisie. */
data class DonneesDetailsReleve(
    val titre: String,
    val datasets: List<Pair<Int, String>>,
    val idDataset: Int?,
    val nomDataset: String?,
    val observateurs: List<Pair<Int, String>>,
    val idsObservateurs: List<Int>,
    val nomsObservateurs: List<String>,
    /** Les champs additionnels de relevé, tous jeux de données confondus ([defs] les filtre). */
    val defsReleve: List<AdditionalFieldDef>,
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
    /** Les heures portées (« hh:mm »), null si aucune. */
    val heureDebut: String? = null,
    val heureFin: String? = null,
    /** form_fields `hour_max` : l'heure de fin ([heures] : celle de début). */
    val heureFinVisible: Boolean = heures,
    /** `OCCTAX.observers_txt` : des observateurs en texte libre, et leur texte. */
    val observateursEnTexte: Boolean = false,
    val observateursTexte: String = "",
    /** `OCCTAX.DATE_FORM_WITH_TODAY` : un relevé sans date prend celle du jour ; sinon elle reste à
     *  choisir. */
    val dateDuJour: Boolean = true,
) {
    /** Les champs additionnels de relevé que le web montre pour le jeu de données [idDataset]. */
    fun defs(idDataset: Int?): List<AdditionalFieldDef> = defsReleve.filter { it.visiblePour(idDataset) }
}

object DetailsReleveFormulaire {

    /** Jeux de données proposables, ceux du web (`active`, `module_code` et `create` OCCTAX) :
     *  actifs, rattachés à OCCTAX et créables ; sans liste des créables, aucun. Libellé : le nom
     *  seul, comme le web. */
    fun datasets(config: GeoNatureConfig): List<Pair<Int, String>> = try {
        val creables = config.datasetsCreablesOcctax
        listeDepuisJson(GeoNatureDataset.serializer(), config.datasetsCacheJson)
            .filter { it.actif && "OCCTAX" in it.moduleCodes && it.id in creables }
            .map { it.id to it.nom }
    } catch (_: Exception) {
        emptyList()
    }

    // ── Les textes du formulaire du relevé du web (releve.component.html et fr.json) ──

    /** Un relevé sans jeu de données : le jeu est obligatoire, comme sur le web. */
    const val CHOISIR_JDD = "Choisissez un jeu de données"

    /** Une date de début vide : le serveur la veut à choisir (`DATE_FORM_WITH_TODAY` à faux). */
    const val CHOISIR_DATE_DEBUT = "Choisissez une date de début"

    const val LIBELLE_OBSERVATEURS = "Observateurs"
    const val INVITE_OBSERVATEURS = "Observateur(s)"
    const val LIBELLE_JDD = "Jeux de données"
    const val INVITE_JDD = "Jeux de données"
    const val LIBELLE_DATE_DEBUT = "Date début"
    const val LIBELLE_DATE_FIN = "Date fin"
    const val LIBELLE_HEURE_DEBUT = "Heure début"
    const val LIBELLE_HEURE_FIN = "Heure fin"
    const val INVITE_HEURE = "hh:mm"
    const val LIBELLE_HABITAT = "Habitat"
    const val INVITE_HABITAT = "Tapez les premières lettres..."
    const val AUCUN_HABITAT = "Aucun élément trouvé"
    const val LIBELLE_COMMENTAIRE = "Commentaire"
    const val INVITE_PRECISION = "Précision du pointage"

    /** L'heure saisie du web (`hour_min`, `hour_max`) : « h:mm » ou « hh:mm », secondes permises. */
    private val HEURE = Regex("^([0-9]|0[0-9]|1[0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9])?$")

    /** Une heure saisie mais mal formée : le web borde son champ de rouge. */
    fun heureMalFormee(texte: String): Boolean = texte.isNotBlank() && !HEURE.matches(texte.trim())

    /** Le premier refus des contrôles du formulaire du relevé du web (releve.service.ts) : jeu de
     *  données, observateurs et date de début requis, heure mal formée, heure de début sans heure de
     *  fin ou après elle, dates inversées, altitudes ou profondeurs inversées (une valeur 0 ou vide ne
     *  compte pas, comme dans son minMaxValidator) ; null si tout va. */
    fun refus(
        debut: Long?,
        fin: Long?,
        heureDebut: String,
        heureFin: String,
        observateursPresents: Boolean,
        observateursEnTexte: Boolean,
        simples: Map<String, String>,
        datasetChoisi: Boolean = true,
    ): String? {
        if (!datasetChoisi) return CHOISIR_JDD
        if (!observateursPresents) return if (observateursEnTexte) "Saisissez les observateurs" else "Choisissez un observateur"
        if (debut == null) return CHOISIR_DATE_DEBUT
        val hd = heureDebut.trim()
        val hf = heureFin.trim()
        if ((hd.isNotEmpty() && !HEURE.matches(hd)) || (hf.isNotEmpty() && !HEURE.matches(hf))) return "Heure attendue au format hh:mm"
        val f = fin ?: debut
        val jourDebut = Instant.fromEpochMilliseconds(debut).toLocalDateTime(TimeZone.currentSystemDefault()).date
        val jourFin = Instant.fromEpochMilliseconds(f).toLocalDateTime(TimeZone.currentSystemDefault()).date
        if (jourFin < jourDebut) return "La date de début ne peut pas être postérieure à la date de fin"
        if (hd.isNotEmpty() && hf.isEmpty()) return HEURES_INVERSEES
        if (hd.isNotEmpty() && hf.isNotEmpty()) {
            fun minutes(h: String) = h.split(':').let { it[0].toInt() * 60 + it[1].toInt() }
            if (jourDebut == jourFin && minutes(hd) > minutes(hf)) return HEURES_INVERSEES
        }
        fun entier(cle: String) = simples[cle]?.trim()?.toIntOrNull()?.takeIf { it != 0 }
        val altMin = entier("altitude_min")
        val altMax = entier("altitude_max")
        if (altMin != null && altMax != null && altMin > altMax) return "L'altitude max ne peut être inférieure à l'altitude min"
        val profMin = entier("depth_min")
        val profMax = entier("depth_max")
        if (profMin != null && profMax != null && profMin > profMax) return "La profondeur max ne peut être inférieure à la profondeur min"
        return null
    }

    private const val HEURES_INVERSEES = "L'heure de début ne peut pas être supérieure à l'heure de fin"

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

    /** La clé form_fields qui montre le champ simple [cle] : la sienne, sauf la profondeur max, que
     *  le web montre avec la profondeur min. */
    fun cleVisibilite(cle: String): String = if (cle == "depth_max") "depth_min" else cle

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

    /** Les champs additionnels de relevé, tous jeux de données confondus : ceux d'un jeu se lisent
     *  par [DonneesDetailsReleve.defs], comme le web, qui les recharge à chaque changement de jeu. */
    fun defsReleve(config: GeoNatureConfig): List<AdditionalFieldDef> =
        ChampsAdditionnels.fromJson(config.additionalFieldsOcctaxJson).filter { it.appliqueA(AdditionalFieldsObject.RELEVE) }

    /** Observateurs de départ : ceux portés, sinon l'observateur par défaut des Paramètres, qui est
     *  au départ l'utilisateur connecté, comme le web (`[currentUser]`). */
    fun idsObservateursDepart(ids: List<Int>, config: GeoNatureConfig): List<Int> =
        ids.ifEmpty { listOfNotNull(config.idObservateurParDefaut) }

    fun nomsObservateursDepart(noms: List<String>, config: GeoNatureConfig): List<String> =
        noms.ifEmpty { listOfNotNull(config.nomObservateurParDefaut.takeIf { it.isNotBlank() }) }

    /** Le texte des observateurs de départ (`observers_txt`) : le nom complet de l'observateur par
     *  défaut, au départ l'utilisateur connecté, comme le web. */
    fun observateursTexteDepart(config: GeoNatureConfig): String = config.nomObservateurParDefaut

    /** Les détails d'un relevé en cours (bouton « Détails »). */
    fun pourReleve(releve: ReleveEnCours, config: GeoNatureConfig): DonneesDetailsReleve = DonneesDetailsReleve(
        titre = "Détails du relevé",
        datasets = datasets(config),
        idDataset = releve.idDatasetEffectif(),
        nomDataset = config.nomDataset.takeIf { it.isNotEmpty() },
        observateurs = observateurs(config),
        idsObservateurs = idsObservateursDepart(releve.idsObservateurs, config),
        nomsObservateurs = nomsObservateursDepart(releve.nomsObservateurs, config),
        defsReleve = defsReleve(config),
        additionnels = releve.additionnelsReleve,
        formFieldsJson = config.formFieldsJson,
        typGrp = releve.typGrp,
        commentaire = releve.commentaire,
        dateDebut = releve.dateDebut,
        dateFin = releve.dateFin,
        heures = config.heuresVisibles,
        avecFin = config.dateFinVisible,
        dateDuJour = config.occtaxDateDuJour,
        cdHab = releve.cdHab,
        habitatLabel = releve.habitatLabel,
        champsExtra = releve.champsExtra,
        heureDebut = releve.heureDebut,
        heureFin = releve.heureFin,
        heureFinVisible = config.heureFinVisible,
        observateursEnTexte = config.occtaxObservateursEnTexte,
        observateursTexte = releve.observateursTexte ?: observateursTexteDepart(config),
    )

    /** Les détails COMMUNS de la saisie (bouton « i » de la carte) : les valeurs communes déjà
     *  réglées, ou les défauts de la configuration au premier usage. */
    fun communs(communs: DetailsReleveResult?, config: GeoNatureConfig): DonneesDetailsReleve = DonneesDetailsReleve(
        titre = "Détails des relevés",
        datasets = datasets(config),
        idDataset = communs?.idDataset ?: config.idDatasetParDefaut,
        nomDataset = config.nomDataset.takeIf { it.isNotEmpty() },
        observateurs = observateurs(config),
        idsObservateurs = idsObservateursDepart(communs?.idsObservateurs.orEmpty(), config),
        nomsObservateurs = nomsObservateursDepart(communs?.nomsObservateurs.orEmpty(), config),
        defsReleve = defsReleve(config),
        additionnels = communs?.additionnels.orEmpty(),
        formFieldsJson = config.formFieldsJson,
        typGrp = communs?.typGrp.orEmpty(),
        commentaire = communs?.comment.orEmpty(),
        dateDebut = communs?.dateDebut,
        dateFin = communs?.dateFin,
        heures = config.heuresVisibles,
        avecFin = config.dateFinVisible,
        dateDuJour = config.occtaxDateDuJour,
        cdHab = communs?.cdHab,
        habitatLabel = communs?.habitatLabel,
        champsExtra = communs?.champsExtra.orEmpty(),
        heureDebut = communs?.heureDebut,
        heureFin = communs?.heureFin,
        heureFinVisible = config.heureFinVisible,
        observateursEnTexte = config.occtaxObservateursEnTexte,
        observateursTexte = communs?.observateursTexte ?: observateursTexteDepart(config),
    )

    // ── Dates ──

    /** [millis] au jour [annee]-[mois]-[jour] (mois de 1 à 12), l'heure gardée (DatePickerDialog
     *  sur un Calendar), dans le fuseau du téléphone. */
    fun avecJour(millis: Long, annee: Int, mois: Int, jour: Int): Long =
        avecJour(millis, LocalDate(annee, mois, jour), TimeZone.currentSystemDefault())

    /** [millis] à l'heure [heure]:[minute] dans le fuseau du téléphone (TimePickerDialog). */
    fun avecHeure(millis: Long, heure: Int, minute: Int): Long = avecHeure(millis, heure, minute, TimeZone.currentSystemDefault())

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
        heures: Pair<String?, String?> = null to null,
        observateursTexte: String? = null,
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
            additionnels = if (d.defs(dataset?.first).isNotEmpty() && additionnels != null) {
                additionnels
            } else {
                // Un jeu sans champ de relevé : comme le web, qui reconstruit le formulaire, rien des
                // champs d'un autre jeu ne part (21 octobre) ; une clé qu'aucun jeu ne définit reste.
                d.additionnels.filterKeys { cle -> d.defsReleve.none { it.fieldName == cle } }
            },
            comment = commentaire.trim(),
            cdHab = cdHab,
            habitatLabel = cdHab?.let { habitatTexte.trim() },
            typGrp = nomenclatures["TYP_GRP"] ?: d.typGrp,
            dateDebut = dates.first,
            dateFin = dates.second,
            champsExtra = extra,
            dateModifiee = dateModifiee,
            heureDebut = heures.first?.trim()?.takeIf { it.isNotEmpty() },
            heureFin = heures.second?.trim()?.takeIf { it.isNotEmpty() },
            observateursTexte = observateursTexte?.trim(),
        )
    }
}
