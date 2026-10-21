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
// la préparation du formulaire d'une nouvelle saisie des Suivis (chargerSchemaEtRendre, enrichirAvecOptions),
// ce que l'enregistrement écrit dans la file d'envoi (envoyerVisite), ce qui le suit, et les phrases accordées
// au type créé. Les valeurs elles-mêmes passent par form/ValeursFormulaire (lot 4) ; l'écran et ses dialogues
// restent dans l'interface. Le mode démo du fragment (faux formulaire quand le schéma manque) n'est pas
// repris : la préparation dit pourquoi elle échoue. Les valeurs initiales suivent le formulaire web
// (vérification des champs du 20 octobre) : la liste taxonomique d'un jeu de données (idListeViaDataset,
// idListeViaDatasetLive), la date et l'heure du moment dans tout champ date ou heure, le défaut d'une
// liste sans sa clé de valeur et l'option unique masquée ne sont plus repris.

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.model.MediaSuivi
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.monitoring.form.EditableField
import fr.ariegenature.geomys.monitoring.form.HiddenExpr
import fr.ariegenature.geomys.monitoring.form.PropertyValue
import fr.ariegenature.geomys.monitoring.form.ValeursFormulaire
import fr.ariegenature.geomys.monitoring.form.ViewType
import fr.ariegenature.geomys.monitoring.form.construireFormulaire
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringPropertySchema
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.MonitoringApi.OptionDatalist
import fr.ariegenature.geomys.network.MonitoringSync
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
    /** Le type de saisie enfant du type créé (visite → observation). */
    val typeSaisieEnfant: String?,
    /** L'enfant UNIQUE du type créé (`children_types` d'un seul type), quand c'est une saisie : le
     *  bouton « Valider et saisir des … » du web. */
    val enfantUnique: String? = null,
    /** Son `label_list` (« Observations »), pour ce bouton. */
    val libelleEnfants: String? = null,
    /** Les propriétés du schéma qui ont un `type_widget`, champs techniques compris : celles que
     *  porte le corps du web (`postData`). */
    val nomsChamps: List<String>,
    /** Les valeurs des propriétés masquées que le formulaire web envoie sans les montrer
     *  ([NouvelleVisite.valeursFixes]) : elles partent avec la saisie. */
    val valeursFixes: Map<String, Any> = emptyMap(),
    val uuidFieldName: String?,
    /** Lignes de diagnostic sous le contexte (datalists non chargées, widgets non pris en charge). */
    val avertissements: List<String>,
    /** Les propriétés masquées (`hidden: true`) requises restées sans valeur, par libellé : le web
     *  garde leur contrôle invalide, l'enregistrement est impossible. */
    val proprietesMasqueesManquantes: List<String> = emptyList(),
) {
    /** Les propriétés de widget `date`, masquées comprises : le web les envoie en
     *  `année-mois-jour` sans zéros. */
    val champsDate: List<String>
        get() = schemaObjet.properties.values.filter { it.typeWidget.equals("date", ignoreCase = true) }.map { it.nom }
}

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

    /** L'utilisateur connecté (0 s'il est inconnu). */
    val idRoleUtilisateur: Int

    /** Son nom complet (« NOM Prénom »), celui que le web montre dans les observateurs. */
    val nomUtilisateur: String get() = ""

    suspend fun schema(moduleCode: String): Map<String, MonitoringSchemaObjet>?

    /** Les options d'un champ `datalist`, null si elles n'ont pu être chargées. */
    suspend fun options(prop: MonitoringPropertySchema): List<OptionDatalist>?
}

/** Les sources de la préparation : le cache des Suivis. */
fun ServicesGeoNature.sourcesFormulaire(): SourcesFormulaire = object : SourcesFormulaire {
    override val urlServeur: String get() = config.urlServeur
    override val idRoleUtilisateur: Int get() = config.idRoleUtilisateur
    override val nomUtilisateur: String get() = config.nomUtilisateur.ifEmpty { config.login }

    override suspend fun schema(moduleCode: String) = monitoring.lireSchemaProtocole(moduleCode)

    override suspend fun options(prop: MonitoringPropertySchema) = monitoring.lireOptionsDatalist(config, prop)
}

/** Les boutons de validation du formulaire web (monitoring-form.component.html). */
enum class ActionValider {
    /** « Valider », ou « Valider et enchaîner les saisies » quand l'enchaînement est activé. */
    VALIDER,

    /** « Valider et saisir des … » : l'enfant unique du type, sous l'objet enregistré. */
    SAISIR_ENFANTS,
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

    /** La fiche du parent serveur, à la place du formulaire : la page que montre le web après
     *  « Valider » (celle de l'objet, introuvable pour une saisie locale, ou celle du parent). */
    data class Fiche(val objectType: String, val id: Int) : SuiteEnregistrement

    /** Saisies enchaînées : un nouveau formulaire du même type sous le même parent, les champs
     *  `keep` repris de la saisie enregistrée ([gardes]). */
    data class Reinitialiser(val gardes: Map<String, Any?> = emptyMap()) : SuiteEnregistrement

    /** Retour à l'écran précédent. */
    data object Retour : SuiteEnregistrement

    /** Fin des saisies sous une saisie locale : la visite parente « à compléter » est d'abord
     *  proposée. */
    data object TerminerOuCompleter : SuiteEnregistrement
}

/** La nouvelle saisie (ou la modification d'une saisie en attente) des Suivis. */
object NouvelleVisite {

    private const val TAG = "NouvelleVisite"

    /** Message éphémère affiché quand on ouvre un formulaire parce qu'il reste des champs
     *  obligatoires à saisir (tentative d'envoi d'une saisie « à compléter »). Les champs
     *  concernés portent la barre rouge dans le formulaire. */
    const val MESSAGE_CHAMPS_OBLIGATOIRES = "Veuillez saisir les champs obligatoires (barre rouge)"

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

    /** Le bouton d'enregistrement du web : « Valider », ou « Valider et enchaîner les saisies »
     *  quand l'enchaînement est activé sur un nouvel objet. */
    fun libelleBouton(edition: Boolean, enchainer: Boolean): String =
        if (enchainer && !edition) "Valider et enchaîner les saisies" else "Valider"

    /** Le bouton qui valide puis ouvre la saisie de l'enfant unique : « Valider et saisir des
     *  Observations ». */
    fun libelleSaisirEnfants(libelleEnfants: String): String = "Valider et saisir des $libelleEnfants"

    /** L'interrupteur du web qui enchaîne les saisies d'un nouvel objet. */
    const val ENCHAINER_LES_SAISIES = "Enchaîner les saisies"

    /** Le bouton qui quitte le formulaire. */
    const val ANNULER = "Annuler"

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
    fun libellesManquants(codes: List<String>, champs: List<EditableField>, masques: Set<String> = emptySet()): List<String> {
        if (codes.isEmpty()) return emptyList()
        val parCode = champs.associateBy { it.code }
        return codes.toSet().map { code ->
            val libelle = parCode[code]?.label?.takeIf { it.isNotBlank() } ?: code
            if (code in masques) "$libelle (masqué)" else libelle
        }
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
     * - options des datalists, valeurs initiales, utilisateur connecté ([enrichirAvecOptions]) ;
     * - liste taxonomique des champs espèce sans liste propre : celle du type, sinon celle d'un
     *   autre type du schéma (le module) ;
     * - valeurs des propriétés masquées ([valeursFixes]) ;
     * - valeurs d'une saisie rouverte ([valeursPreremplies], [medias]).
     */
    suspend fun preparer(
        sources: SourcesFormulaire,
        moduleCode: String,
        childObjectType: String?,
        parentObjectType: String?,
        valeursPreremplies: Map<String, Any?>?,
        medias: List<MediaSuivi>,
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
        val (enrichis, echecs) = enrichirAvecOptions(champs, visitSchema, valeursPreremplies, sources.idRoleUtilisateur, sources.nomUtilisateur, sources::options)
        if (echecs.isNotEmpty()) avertissements += "⚠ Datalists non chargées : ${echecs.joinToString(", ")}"
        // La liste du protocole seulement : le web ne restreint pas un champ espèce à celle d'un jeu
        // de données.
        val idListe = visitSchema.idListTaxonomy ?: schema.values.firstNotNullOfOrNull { it.idListTaxonomy }
        if (construction.ignores.isNotEmpty()) {
            val recap = construction.ignores.joinToString(", ") { "${it.first} (${it.second})" }
            avertissements += "⚠ ${construction.ignores.size} champ(s) non supporté(s) : $recap"
        }
        val avecTaxon = avecListeTaxonomique(enrichis, idListe)
        val fixes = valeursFixes(visitSchema, parentIdField, valeursPreremplies, sources::options)
        val masqueesManquantes = proprietesMasqueesManquantes(visitSchema, parentIdField, fixes)
        if (masqueesManquantes.isNotEmpty()) {
            avertissements += "⚠ Requis mais masqué, sans valeur : ${masqueesManquantes.joinToString(", ")}. " +
                "Comme sur le web, l'enregistrement est impossible."
        }
        return PreparationFormulaire.Prete(
            FormulairePrepare(
                schemaObjet = visitSchema,
                labelType = visitSchema.label ?: visitSchema.type.replaceFirstChar { it.uppercase() },
                genre = visitSchema.genre,
                champs = ValeursFormulaire.champsPreremplis(avecTaxon, valeursPreremplies, medias),
                champsDirtyInitiaux = ValeursFormulaire.champsDirtyInitiaux(valeursPreremplies),
                parentIdField = parentIdField,
                typeSaisieEnfant = visitSchema.childrenTypes.firstOrNull { MonitoringSync.estTypeSaisie(it) },
                enfantUnique = visitSchema.childrenTypes.singleOrNull()?.takeIf { MonitoringSync.estTypeSaisie(it) },
                libelleEnfants = visitSchema.childrenTypes.singleOrNull()?.let { AffichageSuivis.libelleListe(it, schema) },
                // Un widget `html` n'a pas de contrôle sur le web : il n'est pas dans le corps.
                nomsChamps = visitSchema.properties.values
                    .filter { it.typeWidget.isNotEmpty() && !it.typeWidget.equals("html", ignoreCase = true) }
                    .map { it.nom },
                valeursFixes = fixes,
                uuidFieldName = visitSchema.uuidFieldName,
                avertissements = avertissements,
                proprietesMasqueesManquantes = masqueesManquantes,
            ),
        )
    }

    /** Les dates que le formulaire web met au jour courant quand elles sont vides
     *  (setDefaultFormValue) ; aucun autre champ date ou heure n'est pré-rempli. */
    private val DATES_DU_JOUR = setOf("visit_date_min", "visit_date_max", "first_use_date")

    /** Les widgets dont le composant web choisit d'office l'option unique d'un champ requis. */
    private val WIDGETS_A_CHOIX_D_OFFICE = setOf("datalist", "dataset")

    /** [valeur] dans le champ, en liste pour un choix multiple. */
    private fun EditableField.avec(valeur: Any): EditableField =
        copy(value = if (viewType == ViewType.SELECT_MULTIPLE) listOf(valeur.toString()) else valeur)

    /**
     * Les options des champs `datalist` (observateurs, jeu de données, nomenclatures…), chargées en
     * parallèle, puis les valeurs initiales que pose le formulaire web, dans son ordre :
     * - la `value` du schéma : scalaire, cd_nom d'un champ espèce, ou objet nomenclature qui désigne
     *   son option par le cd_nomenclature ;
     * - l'option unique d'une liste ou d'un jeu de données requis, choisie d'office (le champ reste
     *   affiché) ;
     * - le `default` d'une datalist, s'il désigne une option par sa clé de valeur ;
     * - la date du jour dans les dates de visite ;
     * - l'utilisateur connecté ([idRole]) dans le champ `observers` affiché, s'il est parmi les
     *   options.
     * Hors ligne, une saisie rouverte dont les options n'ont pu être chargées garde sa valeur
     * ([valeursPreremplies]) en option brute : « Enregistrer les modifications » ne l'efface pas.
     * Rend aussi les champs dont les options n'ont pu être chargées.
     */
    suspend fun enrichirAvecOptions(
        fields: List<EditableField>,
        visitSchema: MonitoringSchemaObjet,
        valeursPreremplies: Map<String, Any?>?,
        idRole: Int?,
        nomUtilisateur: String = "",
        chargerOptions: suspend (MonitoringPropertySchema) -> List<OptionDatalist>?,
    ): Pair<List<EditableField>, List<String>> = coroutineScope {
        val nouveaux = fields.toMutableList()
        // Les options brutes, pour résoudre les valeurs initiales (cd_nomenclature, label_default).
        val optionsParIdx = mutableMapOf<Int, List<OptionDatalist>>()
        val echecs = mutableListOf<String>()
        // Les SELECT et RADIO à `values` statiques n'ont pas d'api : pas chargés, mais ils reçoivent
        // leur valeur initiale plus bas.
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
            nouveaux[idx] = f.copy(values = opts.map { o -> PropertyValue(o.value, o.label, o.cdNomenclature, o.brut) })
        }
        // La `value` du schéma, valeur initiale du contrôle web (toForm).
        nouveaux.forEachIndexed { idx, f ->
            val prop = visitSchema.properties[f.code] ?: return@forEachIndexed
            val valeur: Any = when {
                prop.defaultObjet.isNotEmpty() -> {
                    val cd = prop.defaultObjet["cd_nomenclature"] ?: return@forEachIndexed
                    optionsParIdx[idx]?.firstOrNull { it.cdNomenclature == cd }?.value ?: return@forEachIndexed
                }
                f.viewType == ViewType.TAXON -> prop.defaultValue?.toIntOrNull() ?: return@forEachIndexed
                else -> prop.defaultValue ?: return@forEachIndexed
            }
            nouveaux[idx] = f.avec(valeur)
        }
        // L'option unique d'une liste requise (composants datalist et pnx-datasets).
        val valeursInitiales = nouveaux.associate { it.code to it.value }
        nouveaux.forEachIndexed { idx, f ->
            if (f.value != null) return@forEachIndexed
            val widget = visitSchema.properties[f.code]?.typeWidget?.lowercase() ?: return@forEachIndexed
            if (widget !in WIDGETS_A_CHOIX_D_OFFICE) return@forEachIndexed
            val requis = f.obligatoire || (f.obligatoireExpr != null && HiddenExpr.evaluerBooleen(f.obligatoireExpr, valeursInitiales))
            val unique = f.values.singleOrNull() ?: return@forEachIndexed
            if (requis) nouveaux[idx] = f.avec(unique.value)
        }
        // Le `default` d'une datalist : le composant web l'ignore s'il ne porte pas la clé de valeur
        // de la liste, ou si elle ne désigne aucune option ; les autres clés doivent concorder.
        nouveaux.forEachIndexed { idx, f ->
            if (f.value != null) return@forEachIndexed
            val prop = visitSchema.properties[f.code] ?: return@forEachIndexed
            if (!prop.typeWidget.equals("datalist", ignoreCase = true)) return@forEachIndexed
            val cleValeur = prop.keyValue ?: "value"
            val voulue = prop.defautDatalist[cleValeur] ?: return@forEachIndexed
            val opts = optionsParIdx[idx]
            val concorde = opts == null || opts.any { o ->
                o.value == voulue && prop.defautDatalist.all { (k, v) ->
                    when (k) {
                        "cd_nomenclature" -> o.cdNomenclature == v
                        "label_default" -> o.labelDefaut == v
                        else -> true
                    }
                }
            }
            if (concorde && f.values.any { it.value == voulue }) nouveaux[idx] = f.avec(voulue)
        }
        // La date du jour dans les dates de visite restées vides.
        nouveaux.forEachIndexed { idx, f ->
            if (f.value == null && f.viewType == ViewType.DATE && f.code in DATES_DU_JOUR) {
                nouveaux[idx] = f.copy(value = DateHeureDefaut.dateDuJour())
            }
        }
        // L'utilisateur connecté dans le champ `observers`, s'il est affiché et de widget `observers`,
        // comme le web : même hors de la liste, que le menu montre alors avec lui.
        idRole?.takeIf { it > 0 }?.let { id ->
            val idStr = id.toString()
            val idx = nouveaux.indexOfFirst { it.code == "observers" }.takeIf { it >= 0 } ?: return@let
            val f = nouveaux[idx]
            val prop = visitSchema.properties[f.code] ?: return@let
            if (!prop.typeWidget.equals("observers", ignoreCase = true) || prop.hiddenBool) return@let
            if (f.value != null) return@let
            val valeurs = if (f.values.any { it.value == idStr }) f.values else f.values + PropertyValue(idStr, nomUtilisateur.ifEmpty { idStr }, brut = id)
            nouveaux[idx] = f.copy(values = valeurs).avec(idStr)
        }
        nouveaux to echecs
    }

    /**
     * Les valeurs des propriétés masquées (`hidden: true`) de [schemaObjet], que le formulaire web
     * envoie sans les montrer (il construit leurs contrôles dans un bloc caché) :
     * - celle d'une saisie rouverte ([valeursPreremplies]), qui garde la sienne ;
     * - leur `value` : le cd_nom fixe d'un protocole mono-espèce, un texte, un nombre ; un objet
     *   nomenclature, résolu par les options en cache ;
     * - l'option unique d'une liste ou d'un jeu de données requis, choisie d'office ;
     * - la date du jour dans les dates de visite (`setDefaultFormValue`), visit_date_max comprise.
     * Le champ du parent ([parentIdField]) est posé par l'envoi. Une option part avec le type JSON de
     * sa valeur, comme sur le web.
     */
    suspend fun valeursFixes(
        schemaObjet: MonitoringSchemaObjet,
        parentIdField: String?,
        valeursPreremplies: Map<String, Any?>? = null,
        chargerOptions: suspend (MonitoringPropertySchema) -> List<OptionDatalist>?,
    ): Map<String, Any> {
        val fixes = linkedMapOf<String, Any>()
        fun identifiant(valeur: String): Any = valeur.toIntOrNull() ?: valeur
        for ((nom, prop) in schemaObjet.properties) {
            if (!prop.hiddenBool || nom == parentIdField || prop.typeWidget.isEmpty()) continue
            valeursPreremplies?.get(nom)?.let { fixes[nom] = it; continue }
            prop.valeurTypee?.let { fixes[nom] = it; continue }
            if (nom in DATES_DU_JOUR && prop.typeWidget.equals("date", ignoreCase = true)) {
                fixes[nom] = DateHeureDefaut.dateDuJour()
                continue
            }
            if (prop.apiUrl == null) continue
            val cd = prop.defaultObjet["cd_nomenclature"]
            val widget = prop.typeWidget.lowercase()
            if (cd == null && (widget !in WIDGETS_A_CHOIX_D_OFFICE || !prop.obligatoire)) continue
            val options = chargerOptions(prop) ?: continue
            val choisie = if (cd != null) options.firstOrNull { it.cdNomenclature == cd } else options.singleOrNull()
            choisie ?: continue
            val valeur = choisie.brut ?: identifiant(choisie.value)
            fixes[nom] = if (prop.multiple) listOf(valeur) else valeur
        }
        return fixes
    }

    /** Les propriétés masquées (`hidden: true`) requises de [schemaObjet] que rien ne remplit ([fixes]),
     *  par libellé. Le parent ([parentIdField]) et `id_digitiser` (l'utilisateur connecté) sont posés
     *  par l'envoi, comme le web les pose. */
    fun proprietesMasqueesManquantes(schemaObjet: MonitoringSchemaObjet, parentIdField: String?, fixes: Map<String, Any>): List<String> =
        schemaObjet.properties.values
            .filter { it.hiddenBool && it.obligatoire && it.typeWidget.isNotEmpty() }
            .filter { it.nom != parentIdField && it.nom != "id_digitiser" && it.nom != schemaObjet.uuidFieldName && it.nom !in fixes }
            .map { it.label.ifEmpty { it.nom } }

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
        champsDate = formulaire.champsDate,
        champsVidesGardes = enregistrement.champsVidesGardes,
        valeursJson = enregistrement.valeursJson,
        uuidPayload = if (formulaire.uuidFieldName != null) uuidAleatoire() else null,
        uuidFieldName = formulaire.uuidFieldName,
        mediaPathsLocal = enregistrement.mediaPaths,
        mediaSchemaDotTable = enregistrement.mediaSchemaDotTable,
        mediasSuivis = enregistrement.medias,
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
        mediasSuivis = enregistrement.medias,
        uuidPayload = ancien.uuidPayload
            ?: if (enregistrement.mediaPaths.isNotEmpty() && formulaire.uuidFieldName != null) uuidAleatoire() else null,
        uuidFieldName = formulaire.uuidFieldName ?: ancien.uuidFieldName,
        champsTexteLibre = enregistrement.champsTexteLibre,
        champsDate = formulaire.champsDate,
        champsVidesGardes = enregistrement.champsVidesGardes,
        champsManquants = manquants.takeIf { it.isNotEmpty() },
    )

    /** Après la création de [saisie], comme le web (onSubmit) :
     *  - sortie par la garde de sortie ([puisTerminer]) : la visite parente « à compléter » est
     *    proposée ;
     *  - « Valider et saisir des … » ([action]) : le formulaire de l'enfant unique, sous la saisie ;
     *  - « Valider et enchaîner les saisies » ([enchainer]) : un nouveau formulaire du même type,
     *    les champs `keep` repris de [valeurs] ;
     *  - « Valider » : la page que montre le web, celle de l'objet ou de son parent
     *    (`redirect_to_parent`). Une saisie locale n'a pas de page : celle de son parent serveur, où
     *    elle est listée ; sous une saisie locale, la fin de ses saisies (visite à compléter). */
    fun suiteApresCreation(
        saisie: SaisieEnAttente,
        formulaire: FormulairePrepare,
        action: ActionValider,
        enchainer: Boolean,
        puisTerminer: Boolean,
        valeurs: Map<String, Any?> = emptyMap(),
    ): SuiteEnregistrement = when {
        puisTerminer -> SuiteEnregistrement.TerminerOuCompleter
        action == ActionValider.SAISIR_ENFANTS && formulaire.enfantUnique != null -> SuiteEnregistrement.Enchainer(
            parentObjectType = saisie.objectType,
            parentUuidLocal = saisie.uuid,
            childObjectType = formulaire.enfantUnique,
            titreSite = "${Accords(formulaire.labelType, formulaire.genre).labelCap} (local)",
        )
        enchainer -> SuiteEnregistrement.Reinitialiser(formulaire.schemaObjet.gardes.filter { it in valeurs }.associateWith { valeurs[it] })
        !saisie.parentUuidLocal.isNullOrEmpty() -> SuiteEnregistrement.TerminerOuCompleter
        (saisie.parentIdServeur ?: 0) > 0 && !saisie.parentObjectType.isNullOrEmpty() ->
            SuiteEnregistrement.Fiche(saisie.parentObjectType, saisie.parentIdServeur ?: 0)
        else -> SuiteEnregistrement.Retour
    }

    /** Après la modification de la saisie [uuid] (« Valider » du web) :
     *  - complétion (on venait finir des champs manquants) : retour à l'écran d'où l'on vient ;
     *  - sinon la fiche du parent serveur, à défaut le retour. */
    fun suiteApresEdition(
        modeCompletion: Boolean,
        parentObjectType: String?,
        parentIdServeur: Int?,
    ): SuiteEnregistrement = when {
        modeCompletion -> SuiteEnregistrement.Retour
        parentIdServeur != null && parentIdServeur > 0 && !parentObjectType.isNullOrEmpty() ->
            SuiteEnregistrement.Fiche(parentObjectType, parentIdServeur)
        else -> SuiteEnregistrement.Retour
    }
}
