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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/NouvelleVisiteFragment.kt) :
// la préparation du formulaire d'une nouvelle saisie des Suivis (chargerSchemaEtRendre, enrichirAvecOptions,
// idListeViaDataset, idListeViaDatasetLive), ce que l'enregistrement écrit dans la file d'envoi
// (envoyerVisite), ce qui le suit, et les phrases accordées au type créé. Les valeurs elles-mêmes passent par
// form/ValeursFormulaire (lot 4) ; l'écran et ses dialogues restent dans l'interface. Le mode démo du fragment
// (faux formulaire quand le schéma manque) n'est pas repris : la préparation dit pourquoi elle échoue.

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.monitoring.form.EditableField
import fr.ariegenature.geomys.monitoring.form.PropertyValue
import fr.ariegenature.geomys.monitoring.form.ValeursFormulaire
import fr.ariegenature.geomys.monitoring.form.ViewType
import fr.ariegenature.geomys.monitoring.form.construireFormulaire
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringPropertySchema
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.MonitoringApi.OptionDatalist
import fr.ariegenature.geomys.network.MonitoringSync
import fr.ariegenature.geomys.store.io.listeDepuisJson
import fr.ariegenature.geomys.util.DateHeureDefaut
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Le formulaire d'une nouvelle saisie, prêt à afficher. */
data class FormulairePrepare(
    /** Le type créé (visite, passage, observation…). */
    val schemaObjet: MonitoringSchemaObjet,
    /** Son libellé (« Visite »), à défaut son type capitalisé. */
    val labelType: String,
    /** Son genre grammatical (`M`, `F`), s'il est déclaré. */
    val genre: String?,
    /** Les champs, options chargées, défauts et valeurs d'une saisie rouverte posés. */
    val champs: List<EditableField>,
    /** Champs porteurs d'une valeur sauvegardée : « modifiés par l'utilisateur » d'emblée. */
    val champsDirtyInitiaux: Set<String>,
    /** Le champ qui désigne le parent (id_base_site…), masqué : le parent est déjà choisi. */
    val parentIdField: String?,
    /** Le type de saisie enfant du type créé (visite → observation), pour l'enchaînement. */
    val typeSaisieEnfant: String?,
    /** Toutes les propriétés du schéma, champs techniques compris (le corps envoyé les porte). */
    val nomsChamps: List<String>,
    val uuidFieldName: String?,
    /** Lignes de diagnostic sous le contexte (datalists non chargées, widgets non pris en charge). */
    val avertissements: List<String>,
)

/** Ce que donne la préparation du formulaire. */
sealed interface PreparationFormulaire {
    data class Prete(val formulaire: FormulairePrepare) : PreparationFormulaire

    /** Pas de formulaire : [messages] dit pourquoi (schéma introuvable, type absent…). */
    data class Impossible(val messages: List<String>) : PreparationFormulaire
}

/** Ce dont la préparation a besoin : le cache des Suivis, que remplit la synchro (rien n'est
 *  demandé au serveur, décision de l'utilisateur du 20 octobre). */
interface SourcesFormulaire {
    val urlServeur: String

    /** Le cache des jeux de données de la synchro (`datasetsCacheJson`). */
    val datasetsCacheJson: String

    /** L'utilisateur connecté (0 s'il est inconnu). */
    val idRoleUtilisateur: Int

    suspend fun schema(moduleCode: String): Map<String, MonitoringSchemaObjet>?

    /** Les options d'un champ `datalist`, null si elles n'ont pu être chargées. */
    suspend fun options(prop: MonitoringPropertySchema): List<OptionDatalist>?

    /** L'id_taxa_list du premier jeu de données actif du protocole qui en déclare une, dans le
     *  cache des jeux du protocole. */
    suspend fun idListeViaDatasetsDuModule(moduleCode: String): Int?
}

/** Les sources de la préparation : le cache des Suivis. */
fun ServicesGeoNature.sourcesFormulaire(): SourcesFormulaire = object : SourcesFormulaire {
    override val urlServeur: String get() = config.urlServeur
    override val datasetsCacheJson: String get() = config.datasetsCacheJson
    override val idRoleUtilisateur: Int get() = config.idRoleUtilisateur

    override suspend fun schema(moduleCode: String) = monitoring.lireSchemaProtocole(moduleCode)

    override suspend fun options(prop: MonitoringPropertySchema) = monitoring.lireOptionsDatalist(config, prop)

    override suspend fun idListeViaDatasetsDuModule(moduleCode: String): Int? {
        // Les jeux actifs du protocole, que la synchro partage avec l'envoi : seul l'id_taxa_list
        // du premier jeu qui en déclare une sert ici.
        val jeux = monitoringDatalists.lireDatasetsDuModule(moduleCode) ?: return null
        for (i in 0 until jeux.length()) {
            val d = jeux.optJSONObject(i) ?: continue
            if (!d.has("id_taxa_list") || d.isNull("id_taxa_list")) continue
            val id = d.optInt("id_taxa_list", -1)
            if (id > 0) return id
        }
        return null
    }
}

/** Ce qui suit l'enregistrement d'une saisie. */
sealed interface SuiteEnregistrement {
    /** Le formulaire de la saisie enfant (visite → observation), sous la saisie locale qu'on vient
     *  d'enregistrer ; il remplace le formulaire courant. */
    data class Enchainer(
        val parentObjectType: String,
        val parentUuidLocal: String,
        val childObjectType: String,
        val titreSite: String,
    ) : SuiteEnregistrement

    /** La fiche du parent serveur, à la place du formulaire (retour d'une modification). */
    data class Fiche(val objectType: String, val id: Int) : SuiteEnregistrement

    /** Chaîne de saisies (observations d'une visite) : le formulaire repart à vide. */
    data object Reinitialiser : SuiteEnregistrement

    /** Retour à l'écran précédent. */
    data object Retour : SuiteEnregistrement

    /** Sortie de la chaîne : la visite parente « à compléter » est d'abord proposée. */
    data object TerminerOuCompleter : SuiteEnregistrement
}

/** La nouvelle saisie (ou la modification d'une saisie en attente) des Suivis. */
object NouvelleVisite {

    private const val TAG = "NouvelleVisite"

    /** Message éphémère affiché quand on ouvre un formulaire parce qu'il reste des champs
     *  obligatoires à saisir (tentative d'envoi d'une saisie « à compléter »). Les champs
     *  concernés portent déjà l'astérisque dans le formulaire. */
    const val MESSAGE_CHAMPS_OBLIGATOIRES = "Veuillez saisir les champs obligatoires (*)"

    /** La saisie est-elle un OBJET DE NIVEAU VISITE — un objet placé directement sous le
     *  PROTOCOLE (visite, transect, pelouse…) — plutôt qu'un objet ENFANT (une espèce, rattachée
     *  à une visite) ? Une saisie dont le PARENT est lui-même un type de saisie est un enfant ; une
     *  saisie rattachée à un site (ou sans parent connu) est l'objet de niveau visite. Deux usages
     *  qui doivent rester cohérents : l'enregistrement INCOMPLET n'est autorisé que sur ces objets
     *  (eux seuls portent des infos connues à la FIN : heure de fin, température de fin), et le
     *  COMPTEUR de « Mes visites » ne totalise qu'eux. */
    fun estObjetDeNiveauVisite(parentObjectType: String?): Boolean {
        val parent = parentObjectType?.trim().orEmpty()
        if (parent.isEmpty()) return true
        return !MonitoringSync.estTypeSaisie(parent)
    }

    /** Mode « chaîne de saisies » : le parent est lui-même une saisie (une visite) — on enchaîne
     *  plusieurs observations sur la même visite, avec un bouton « Terminer ». Jamais en édition. */
    fun estModeChaine(parentObjectType: String?, edition: Boolean): Boolean =
        !edition && !parentObjectType.isNullOrEmpty() && MonitoringSync.estTypeSaisie(parentObjectType)

    // ─── Textes ───

    private val VOYELLES = setOf('a', 'à', 'â', 'e', 'é', 'è', 'ê', 'i', 'î', 'o', 'ô', 'u', 'ù', 'û', 'h', 'y')
    private fun commenceParVoyelle(mot: String) = mot.firstOrNull()?.let { it in VOYELLES } == true

    /** Le titre avant le chargement du schéma : « Modifier… » en édition, sinon le libellé du type
     *  à créer s'il est connu ([libelleNouveau] : « Nouvelle visite »), « Nouvelle entrée » à défaut. */
    fun titreProvisoire(edition: Boolean, libelleNouveau: String?): String = when {
        edition -> "Modifier…"
        libelleNouveau != null -> libelleNouveau
        else -> "Nouvelle entrée"
    }

    /** « Édition de la visite » / « du passage » / « de l'observation » : article accordé au genre
     *  du schéma, élidé devant une voyelle ; le label passe en minuscules. */
    fun titreEdition(label: String, genre: String?): String {
        val mot = label.lowercase()
        val article = when {
            commenceParVoyelle(mot) -> "de l'"
            genre.equals("M", ignoreCase = true) -> "du "
            else -> "de la "
        }
        return "Édition $article$mot"
    }

    /** « Nouvelle visite » (féminin, défaut), « Nouveau passage », « Nouvel inventaire ». */
    fun titreNouvelle(label: String, genre: String?): String {
        val mot = label.lowercase()
        val masculin = genre.equals("M", ignoreCase = true)
        val adjectif = when {
            !masculin -> "Nouvelle"
            commenceParVoyelle(mot) -> "Nouvel"
            else -> "Nouveau"
        }
        return "$adjectif $mot"
    }

    /** « Sur : <objet> — Protocole : <code> ». */
    fun contexte(titreSite: String, moduleCode: String): String = buildString {
        if (titreSite.isNotEmpty()) append("Sur : $titreSite")
        if (moduleCode.isNotEmpty()) {
            if (isNotEmpty()) append(" — ")
            append("Protocole : $moduleCode")
        }
    }

    /** Le contexte suivi des lignes de diagnostic, séparées d'une ligne vide (`ajouterDebug`). */
    fun contexteAvecDiagnostic(contexte: String, diagnostic: List<String>): String =
        (listOf(contexte).filter { it.isNotEmpty() } + diagnostic).joinToString("\n\n")

    /** Le bouton d'enregistrement selon le mode. */
    fun libelleBouton(edition: Boolean, chaine: Boolean): String = when {
        edition -> "Enregistrer les modifications"
        chaine -> "Enregistrer + suivante"
        else -> "Enregistrer la visite"
    }

    /** Les phrases du flux, accordées au type créé (label et genre du schéma ; repli « entrée »,
     *  féminin, avant son chargement). */
    class Accords(label: String?, genre: String?) {
        private val mot = label ?: "entrée"
        val masculin: Boolean = genre.equals("M", ignoreCase = true)

        /** « Visite » / « Passage ». */
        val labelCap: String = mot.replaceFirstChar { it.uppercase() }

        /** « e » au féminin, rien au masculin (participe passé, adjectif). */
        val e: String = if (masculin) "" else "e"

        /** « la visite » / « le passage » / « l'observation ». */
        val avecArticle: String = mot.lowercase().let { m ->
            val art = when { commenceParVoyelle(m) -> "l'"; masculin -> "le "; else -> "la " }
            "$art$m"
        }

        /** Titre des dialogues de sortie : « Visite non enregistrée ». */
        val titreNonEnregistre: String get() = "$labelCap non enregistré$e"

        /** « Continuer la visite ». */
        val boutonContinuer: String get() = "Continuer $avecArticle"

        /** Sortie d'un formulaire modifié et enregistrable, avec les obligatoires encore vides d'une
         *  visite ([manquants]). */
        fun questionEnregistrer(manquants: List<String>): String {
            val question = "Enregistrer $avecArticle en cours avant de quitter ?"
            return if (manquants.isEmpty()) question else "$question\n\n${messageManquants(manquants)}"
        }

        /** Saisie bloquée : valeur hors bornes, ou champ obligatoire vide d'une saisie d'espèce. */
        val messageIncomplet: String
            get() = "${avecArticle.replaceFirstChar { it.uppercase() }} en cours est " +
                "${if (masculin) "incomplet" else "incomplète"} (champ obligatoire manquant ou valeur hors " +
                "limites) et ne peut pas être enregistré$e."

        /** « Terminer » sur une saisie bloquée. */
        val messageQuitterIncomplet: String
            get() = "$messageIncomplet Quitter et ${if (masculin) "le" else "la"} perdre ?"

        /** Après une création. */
        fun messageEnregistre(manquants: List<String>): String =
            if (manquants.isEmpty()) "$labelCap enregistré$e localement — envoi à la demande depuis « Mes visites »"
            else "$labelCap enregistré$e — ${messageManquants(manquants)}"

        /** Échec d'écriture de la file d'envoi. */
        val messageEcritureImpossible: String
            get() = "L'écriture sur l'appareil a échoué (stockage plein ?). $labelCap n'a PAS été enregistré$e.\n\n" +
                "Libérez de l'espace (photos, cache de cartes) puis réessayez — sans quitter cet écran, sinon les " +
                "valeurs saisies seront perdues."
    }

    /** Après une modification. */
    fun messageModifie(manquants: List<String>): String =
        if (manquants.isEmpty()) "Modifications enregistrées" else "Enregistré — ${messageManquants(manquants)}"

    /** « N champ(s) à compléter avant l'envoi : A, B » — au plus trois libellés cités. */
    fun messageManquants(manquants: List<String>): String {
        val cites = manquants.take(3).joinToString(", ")
        val reste = manquants.size - 3
        return "${manquants.size} champ${if (manquants.size > 1) "s" else ""} à compléter avant " +
            "l'envoi : $cites" + if (reste > 0) "… (+$reste)" else ""
    }

    /** Les LIBELLÉS des champs obligatoires vides [codes] (l'état raisonne en codes, l'utilisateur en
     *  intitulés), le code à défaut de libellé. */
    fun libellesManquants(codes: List<String>, champs: List<EditableField>): List<String> {
        if (codes.isEmpty()) return emptyList()
        val parCode = champs.associateBy { it.code }
        return codes.toSet().map { code -> parCode[code]?.label?.takeIf { it.isNotBlank() } ?: code }
    }

    const val TITRE_ENREGISTREMENT_IMPOSSIBLE = "Enregistrement impossible"

    /** Fin de visite : la visite parente enregistrée « à compléter ». */
    const val TITRE_COMPLETER = "Compléter la visite ?"

    fun messageCompleter(manquants: List<String>): String {
        val n = manquants.size
        return "La visite a été enregistrée sans $n champ${if (n > 1) "s" else ""} " +
            "obligatoire${if (n > 1) "s" else ""} : ${manquants.joinToString(", ")}.\n\n" +
            "Ces informations sont maintenant connues — les saisir permet d'envoyer la visite et ses observations."
    }

    // ─── Préparation ───

    /** Types qui ressemblent à une saisie, pour les appels qui ne disent pas quel type créer. */
    private val CANDIDATS = setOf(
        "visit", "visite", "passage", "releve", "relevé", "session", "observation", "obs", "occurrence", "denombrement",
    )

    /** Le type à créer : celui que désigne l'écran appelant (le « + » d'une ligne sait quel enfant
     *  créer), sinon un type de saisie du schéma, de préférence avec des propriétés. */
    fun schemaACreer(schema: Map<String, MonitoringSchemaObjet>, childObjectType: String?): MonitoringSchemaObjet? =
        if (!childObjectType.isNullOrEmpty()) schema[childObjectType]
        else schema.values.firstOrNull { it.type in CANDIDATS && it.properties.isNotEmpty() }
            ?: schema.values.firstOrNull { it.type in CANDIDATS }

    /**
     * Prépare le formulaire de [childObjectType] (à défaut, d'un type de saisie du protocole) :
     * - le champ qui désigne le parent ([parentObjectType]) est retiré : le parent est déjà choisi
     *   par le parcours qui mène ici, comme sur le web ;
     * - options des datalists, défauts, utilisateur connecté ([enrichirAvecOptions]) ;
     * - liste taxonomique des champs espèce, en cascade : le type, un autre type du schéma (souvent
     *   « module »), un jeu de données du protocole dans le cache de la synchro, enfin au serveur ;
     * - valeurs d'une saisie rouverte ([valeursPreremplies], [mediaPaths]).
     */
    suspend fun preparer(
        sources: SourcesFormulaire,
        moduleCode: String,
        childObjectType: String?,
        parentObjectType: String?,
        valeursPreremplies: Map<String, Any?>?,
        mediaPaths: List<String>,
    ): PreparationFormulaire {
        val typeDemande = childObjectType?.takeIf { it.isNotEmpty() }
        val schema = sources.schema(moduleCode) ?: run {
            val url = sources.urlServeur.trim().trimEnd('/') + "/api/monitorings/config/" + moduleCode
            return PreparationFormulaire.Impossible(
                listOf("⚠ /api/monitorings/config/$moduleCode a renvoyé null.\nTeste dans ton navigateur :\n$url"),
            )
        }
        val visitSchema = schemaACreer(schema, typeDemande)
            ?: return PreparationFormulaire.Impossible(
                listOf("⚠ Type '${typeDemande ?: "visit/visite/passage/…"}' absent du schéma."),
            )
        if (visitSchema.properties.isEmpty()) {
            return PreparationFormulaire.Impossible(
                listOf(
                    "⚠ Type '${visitSchema.type}' trouvé mais sans propriétés exploitables " +
                        "(le serveur ne déclare peut-être pas `properties[].type_widget`).",
                ),
            )
        }
        val parentIdField = schema[parentObjectType.orEmpty()]?.idFieldName
        val construction = construireFormulaire(visitSchema)
        val champs = if (parentIdField != null) construction.fields.filter { it.code != parentIdField } else construction.fields
        if (champs.isEmpty()) {
            val widgets = visitSchema.properties.values.joinToString(", ") { "${it.nom}=${it.typeWidget}" }
            return PreparationFormulaire.Impossible(listOf("⚠ Aucun widget supporté.\nWidgets reçus : $widgets"))
        }
        val avertissements = mutableListOf<String>()
        val (enrichis, echecs) = enrichirAvecOptions(champs, visitSchema, valeursPreremplies, sources.idRoleUtilisateur, sources::options)
        if (echecs.isNotEmpty()) avertissements += "⚠ Datalists non chargées : ${echecs.joinToString(", ")}"
        val idListe = visitSchema.idListTaxonomy
            ?: schema.values.firstNotNullOfOrNull { it.idListTaxonomy }
            ?: idListeViaDataset(sources.datasetsCacheJson, moduleCode)
            ?: sources.idListeViaDatasetsDuModule(moduleCode)
        if (construction.ignores.isNotEmpty()) {
            val recap = construction.ignores.joinToString(", ") { "${it.first} (${it.second})" }
            avertissements += "⚠ ${construction.ignores.size} champ(s) non supporté(s) : $recap"
        }
        val avecTaxon = avecListeTaxonomique(enrichis, idListe)
        return PreparationFormulaire.Prete(
            FormulairePrepare(
                schemaObjet = visitSchema,
                labelType = visitSchema.label ?: visitSchema.type.replaceFirstChar { it.uppercase() },
                genre = visitSchema.genre,
                champs = ValeursFormulaire.champsPreremplis(avecTaxon, valeursPreremplies, mediaPaths),
                champsDirtyInitiaux = ValeursFormulaire.champsDirtyInitiaux(valeursPreremplies),
                parentIdField = parentIdField,
                typeSaisieEnfant = visitSchema.childrenTypes.firstOrNull { MonitoringSync.estTypeSaisie(it) },
                nomsChamps = visitSchema.properties.keys.toList(),
                uuidFieldName = visitSchema.uuidFieldName,
                avertissements = avertissements,
            ),
        )
    }

    /**
     * Les options des champs `datalist` (observateurs, jeu de données, nomenclatures…), chargées en
     * parallèle, puis les valeurs par défaut :
     * - option unique d'un champ `dataset` ou `observers` : choisie et masquée (une nomenclature à
     *   valeur unique reste affichée, l'utilisateur doit pouvoir la confirmer) ;
     * - défaut du schéma : scalaire, ou objet `cd_nomenclature` / `label_default` cherché dans les
     *   options ; jamais sur un champ ESPÈCE (le taxon est choisi par l'utilisateur, parité web) ;
     * - date du jour, heure actuelle pour les champs date et heure restés vides ;
     * - l'utilisateur connecté ([idRole]) dans les champs observateurs, s'il est parmi les options.
     * Hors ligne, une saisie rouverte dont les options n'ont pu être chargées garde sa valeur
     * ([valeursPreremplies]) en option brute : « Enregistrer les modifications » ne l'efface pas.
     * Rend aussi les champs dont les options n'ont pu être chargées.
     */
    suspend fun enrichirAvecOptions(
        fields: List<EditableField>,
        visitSchema: MonitoringSchemaObjet,
        valeursPreremplies: Map<String, Any?>?,
        idRole: Int?,
        chargerOptions: suspend (MonitoringPropertySchema) -> List<OptionDatalist>?,
    ): Pair<List<EditableField>, List<String>> = coroutineScope {
        val nouveaux = fields.toMutableList()
        // Les options brutes, pour résoudre les défauts (cd_nomenclature, label_default).
        val optionsParIdx = mutableMapOf<Int, List<OptionDatalist>>()
        val echecs = mutableListOf<String>()
        // Les SELECT et RADIO à `values` statiques n'ont pas d'api : pas chargés, mais ils reçoivent
        // leur défaut plus bas.
        val aCharger = fields.withIndex().filter { (_, f) ->
            (f.viewType == ViewType.SELECT || f.viewType == ViewType.SELECT_MULTIPLE) &&
                f.values.isEmpty() &&
                visitSchema.properties[f.code]?.apiUrl != null
        }
        val resultats = aCharger.map { (idx, f) ->
            val prop = visitSchema.properties.getValue(f.code)
            async { idx to chargerOptions(prop) }
        }.awaitAll()
        for ((idx, opts) in resultats) {
            val f = nouveaux[idx]
            if (opts == null) {
                echecs.add("${f.code} (fetch options échoué)")
                val valeursSauvees = when (val sauvee = valeursPreremplies?.get(f.code)) {
                    null -> emptyList()
                    is List<*> -> sauvee.mapNotNull { it?.toString() }.filter { it.isNotEmpty() }
                    else -> listOf(sauvee.toString()).filter { it.isNotEmpty() }
                }
                if (valeursSauvees.isNotEmpty()) {
                    nouveaux[idx] = f.copy(values = valeursSauvees.map { PropertyValue(it, "$it (option non rechargée — hors-ligne ?)") })
                }
                continue
            }
            optionsParIdx[idx] = opts
            val valeurs = opts.map { o -> PropertyValue(o.value, o.label, o.cdNomenclature) }
            val widget = visitSchema.properties[f.code]?.typeWidget?.lowercase()
            val masquable = widget == "dataset" || widget == "observers"
            nouveaux[idx] = if (masquable && opts.size == 1) {
                val uniq = opts.first().value
                f.copy(values = valeurs, value = if (f.viewType == ViewType.SELECT_MULTIPLE) listOf(uniq) else uniq, masque = true)
            } else {
                f.copy(values = valeurs)
            }
        }
        // Valeurs par défaut du schéma.
        nouveaux.forEachIndexed { idx, f ->
            val prop = visitSchema.properties[f.code] ?: return@forEachIndexed
            if (f.value != null) return@forEachIndexed
            if (f.viewType == ViewType.TAXON) return@forEachIndexed
            if (prop.defaultValue != null && prop.defaultObjet.isEmpty()) {
                nouveaux[idx] = f.copy(value = prop.defaultValue)
                return@forEachIndexed
            }
            if (prop.defaultObjet.isNotEmpty()) {
                val opts = optionsParIdx[idx] ?: return@forEachIndexed
                val cd = prop.defaultObjet["cd_nomenclature"]
                val lbl = prop.defaultObjet["label_default"]
                val choisie = when {
                    cd != null -> opts.firstOrNull { it.cdNomenclature == cd }
                    lbl != null -> opts.firstOrNull { it.labelDefaut == lbl }
                    else -> null
                } ?: return@forEachIndexed
                nouveaux[idx] = f.copy(value = if (f.viewType == ViewType.SELECT_MULTIPLE) listOf(choisie.value) else choisie.value)
            }
        }
        // Date du jour, heure actuelle : le serveur garde la priorité, on ne remplit que ce qu'il
        // laisse vide (en édition, les valeurs saisies les remplacent ensuite).
        nouveaux.forEachIndexed { idx, f ->
            if (f.value != null) return@forEachIndexed
            val defaut = when (f.viewType) {
                ViewType.DATE -> DateHeureDefaut.dateDuJour()
                ViewType.TIME -> DateHeureDefaut.heureActuelle()
                ViewType.DATETIME -> DateHeureDefaut.dateHeureActuelle()
                else -> return@forEachIndexed
            }
            nouveaux[idx] = f.copy(value = defaut)
        }
        // L'utilisateur connecté dans les champs observateurs (widget `observers`, ou `datalist`
        // dont le nom parle d'observateur).
        idRole?.takeIf { it > 0 }?.toString()?.let { idStr ->
            nouveaux.forEachIndexed { idx, f ->
                val prop = visitSchema.properties[f.code] ?: return@forEachIndexed
                val widget = prop.typeWidget.lowercase()
                val estObservateur = widget == "observers" || (widget == "datalist" && f.code.contains("observ"))
                if (!estObservateur || f.values.none { it.value == idStr }) return@forEachIndexed
                nouveaux[idx] = f.copy(value = if (f.viewType == ViewType.SELECT_MULTIPLE) listOf(idStr) else idStr)
            }
        }
        nouveaux to echecs
    }

    /** L'id_taxa_list du premier jeu de données rattaché au protocole qui en déclare une, d'après le
     *  cache des jeux de données de la synchro (`/api/meta/datasets?fields=modules`). */
    fun idListeViaDataset(datasetsCacheJson: String, moduleCode: String): Int? =
        listeDepuisJson(GeoNatureDataset.serializer(), datasetsCacheJson)
            .firstOrNull { moduleCode in it.moduleCodes && it.idTaxaList != null }?.idTaxaList

    /** Les champs TAXON sans liste propre reçoivent [idListe]. */
    fun avecListeTaxonomique(champs: List<EditableField>, idListe: Int?): List<EditableField> =
        if (idListe == null) champs
        else champs.map { f -> if (f.viewType == ViewType.TAXON && f.idListeTaxonomieRestreinte == null) f.copy(idListeTaxonomieRestreinte = idListe) else f }

    /** Les valeurs d'une saisie rouverte en modification ; vide si son `valeursJson` est illisible. */
    fun valeursPourEdition(saisie: SaisieEnAttente): Map<String, Any?> = try {
        ValeursFormulaire.parseValeursJson(saisie.valeursJson)
    } catch (e: Exception) {
        Journal.w(TAG, "valeursPourEdition échoué pour uuid=${saisie.uuid}", e)
        emptyMap()
    }

    // ─── Enregistrement ───

    /** La nouvelle saisie de la file d'envoi. Un uuid est pré-généré dès que le schéma déclare un
     *  `uuid_field_name` : il rattache les médias après création, et identifie la saisie pour
     *  l'anti-doublon (le ré-envoi vérifie par lui qu'elle n'existe pas déjà, cf. OutboxEnvoi). Les
     *  champs obligatoires vides d'une visite ([manquants]) la marquent « à compléter ». */
    fun nouvelleSaisieEnAttente(
        moduleCode: String,
        formulaire: FormulairePrepare,
        enregistrement: ValeursFormulaire.Enregistrement,
        parentObjectType: String?,
        parentIdServeur: Int?,
        parentUuidLocal: String?,
        manquants: List<String>,
    ): SaisieEnAttente = SaisieEnAttente(
        moduleCode = moduleCode,
        objectType = formulaire.schemaObjet.type,
        parentObjectType = parentObjectType,
        parentIdServeur = parentIdServeur,
        parentUuidLocal = parentUuidLocal,
        parentIdField = formulaire.parentIdField,
        nomsChampsSchema = formulaire.nomsChamps,
        champsTexteLibre = enregistrement.champsTexteLibre,
        valeursJson = enregistrement.valeursJson,
        uuidPayload = if (formulaire.uuidFieldName != null) uuidAleatoire() else null,
        uuidFieldName = formulaire.uuidFieldName,
        mediaPathsLocal = enregistrement.mediaPaths,
        mediaSchemaDotTable = enregistrement.mediaSchemaDotTable,
        champsManquants = manquants.takeIf { it.isNotEmpty() },
    )

    /** La saisie modifiée : nouvelles valeurs, remise EN ATTENTE (l'erreur précédente effacée). Une
     *  saisie ancienne sans uuid en reçoit un s'il y a un média à envoyer. */
    fun saisieModifiee(
        ancien: SaisieEnAttente,
        formulaire: FormulairePrepare,
        enregistrement: ValeursFormulaire.Enregistrement,
        manquants: List<String>,
    ): SaisieEnAttente = ancien.copy(
        valeursJson = enregistrement.valeursJson,
        etat = SaisieEnAttente.Etat.PENDING,
        messageErreur = null,
        mediaPathLocal = null,
        mediaPathsLocal = enregistrement.mediaPaths,
        mediaSchemaDotTable = enregistrement.mediaSchemaDotTable,
        uuidPayload = ancien.uuidPayload
            ?: if (enregistrement.mediaPaths.isNotEmpty() && formulaire.uuidFieldName != null) uuidAleatoire() else null,
        uuidFieldName = formulaire.uuidFieldName ?: ancien.uuidFieldName,
        champsTexteLibre = enregistrement.champsTexteLibre,
        champsManquants = manquants.takeIf { it.isNotEmpty() },
    )

    /** Après la création de [saisie] :
     *  - sortie par « Terminer » ou par le retour ([puisTerminer]) : la visite parente « à
     *    compléter » est proposée ;
     *  - le type créé a une saisie enfant : son formulaire, sous la saisie locale ;
     *  - chaîne de saisies : le formulaire repart à vide ; sinon, retour. */
    fun suiteApresCreation(
        saisie: SaisieEnAttente,
        formulaire: FormulairePrepare,
        puisTerminer: Boolean,
        chaine: Boolean,
    ): SuiteEnregistrement = when {
        puisTerminer -> SuiteEnregistrement.TerminerOuCompleter
        formulaire.typeSaisieEnfant != null -> SuiteEnregistrement.Enchainer(
            parentObjectType = saisie.objectType,
            parentUuidLocal = saisie.uuid,
            childObjectType = formulaire.typeSaisieEnfant,
            titreSite = "${Accords(formulaire.labelType, formulaire.genre).labelCap} (local)",
        )
        chaine -> SuiteEnregistrement.Reinitialiser
        else -> SuiteEnregistrement.Retour
    }

    /** Après la modification de la saisie [uuid] :
     *  - complétion (on venait finir des champs manquants) : retour à l'écran d'où l'on vient, sans
     *    enchaîner — seules les infos de la visite étaient à finir ;
     *  - le type a une saisie enfant, hors sortie explicite ([puisTerminer]) : son formulaire, comme
     *    après une création ;
     *  - sinon la fiche du parent serveur, à défaut le retour. */
    fun suiteApresEdition(
        uuid: String,
        formulaire: FormulairePrepare,
        modeCompletion: Boolean,
        puisTerminer: Boolean,
        parentObjectType: String?,
        parentIdServeur: Int?,
    ): SuiteEnregistrement = when {
        modeCompletion -> SuiteEnregistrement.Retour
        !puisTerminer && formulaire.typeSaisieEnfant != null -> SuiteEnregistrement.Enchainer(
            parentObjectType = formulaire.schemaObjet.type,
            parentUuidLocal = uuid,
            childObjectType = formulaire.typeSaisieEnfant,
            titreSite = "${Accords(formulaire.labelType, formulaire.genre).labelCap} (local)",
        )
        parentIdServeur != null && parentIdServeur > 0 && !parentObjectType.isNullOrEmpty() ->
            SuiteEnregistrement.Fiche(parentObjectType, parentIdServeur)
        else -> SuiteEnregistrement.Retour
    }
}
