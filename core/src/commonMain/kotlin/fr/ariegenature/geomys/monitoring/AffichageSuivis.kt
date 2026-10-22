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

// Extrait de GeoMys-Android @ cdd5196 : ce que SuiviDetailFragment et FicheObjetFragment
// (app/src/main/java/fr/ariegenature/geomys/ui/) calculaient pour afficher les objets d'un protocole —
// sousTitrePourEnfant et labelTypeParDefaut, écrits deux fois ; typesAfficherEnListe et labelPour du détail ;
// labelChamp, formatValeur et trierChronologique de la fiche. Mis en commun comme le propose l'inventaire
// (3-formulaires-monitoring.md, § 2.4).

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.network.MonitoringApi.LabelResolver
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringEnfant
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringObjet
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringPropertySchema
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.MonitoringObjets
import fr.ariegenature.geomys.network.MonitoringSync
import fr.ariegenature.geomys.plateforme.comparateurFrancais
import fr.ariegenature.geomys.util.DateAffichage

/** L'affichage des objets d'un protocole de suivi, piloté par son schéma. */
object AffichageSuivis {

    /** Résumé du détail d'un protocole pendant son chargement. */
    const val RESUME_EN_COURS = "Sites : …"

    /** Détail d'un protocole absent de la liste chargée (cache mémoire vide). */
    const val MESSAGE_PROTOCOLE_INCONNU = "Recharge la liste pour voir les détails."

    /** Détail d'un protocole dont la synchro n'a pas mis les sites en cache : les écrans ne lisent
     *  que le cache (décision de l'utilisateur du 20 octobre). */
    const val PROTOCOLE_HORS_CACHE = "Sites absents du cache — lancez « Recharger les données »."

    /** Fiche absente du cache, et de la fiche de son parent. */
    const val FICHE_HORS_CACHE = "Fiche absente du cache — lancez « Recharger les données »."

    /** Fiche reconstituée depuis sa ligne chez son parent (la synchro ne charge pas l'historique :
     *  une visite passée et ses observations), et le serveur n'a pas répondu. */
    const val ENFANTS_HORS_CACHE = "Saisies rattachées absentes du téléphone : ouvrez cette fiche avec du réseau pour les charger."

    /** La même, pendant que sa mise à jour est demandée au serveur. */
    const val ENFANTS_EN_CHARGEMENT = "Chargement des saisies rattachées depuis le serveur…"

    /** Fallback générique quand le schéma serveur ne fournit pas de `label_list`. Capitalise
     *  le code technique et remplace les `_` par des espaces — pas de mapping FR figé qui
     *  dépendrait des conventions d'un protocole. Pour un rendu propre, c'est à l'admin
     *  GeoNature de poser un `label_list` côté schéma. */
    fun labelTypeParDefaut(type: String): String =
        type.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** Libellé d'un object_type en tête de liste (« Sites ») : son `label_list`, sinon son `label`
     *  suivi d'un « s », comme le web (`initTemplate` de monitoring-object-base) ; sans schéma, le
     *  repli générique (`labelPour` du détail). */
    fun libelleListe(type: String, schema: Map<String, MonitoringSchemaObjet>?): String {
        val s = schema?.get(type)
        s?.labelList?.takeIf { it.isNotEmpty() }?.let { return it }
        s?.label?.takeIf { it.isNotEmpty() }?.let { return "${it}s" }
        return labelTypeParDefaut(type)
    }

    /** Libellé d'un objet sous son nom, dans sa fiche (« Site ») : label, puis label_list. */
    fun libelleType(type: String, schemaCe: MonitoringSchemaObjet?): String =
        schemaCe?.label ?: schemaCe?.labelList ?: labelTypeParDefaut(type)

    /** Sélectionne les object_types à afficher dans la liste principale et dans le résumé. Comme le
     *  web (`sites-groups.resolver.ts`) : une liste par type enfant du module (les clés de
     *  `tree.module`, que le serveur expose en `children_types` du module), même quand ses objets ont
     *  aussi un autre parent — les sites d'un protocole à groupes de sites, dont ceux sans groupe.
     *  Sans cette déclaration, filtre par `parent_type` : un type est macro s'il déclare le module
     *  comme parent ; à défaut, on garde uniquement les "racines" et on masque les enfants qui
     *  apparaîtront via drill-down sur leur parent. */
    fun typesAfficherEnListe(typesPresents: List<String>, schema: Map<String, MonitoringSchemaObjet>?): List<String> {
        if (schema == null) return typesPresents
        val duModule = schema["module"]?.childrenTypes.orEmpty()
        typesPresents.filter { it in duModule }.takeIf { it.isNotEmpty() }?.let { return it }
        // Préférence : tout type qui déclare le module comme parent.
        val typesMacro = typesPresents.filter { schema[it]?.parentType == "module" }
        if (typesMacro.isNotEmpty()) return typesMacro
        // Fallback purement structurel : retire les types dont le parent est lui-même présent
        // dans la liste — ils ressortiront via drill-down. Plus de hardcoding sites_group/site.
        val typesEnfants = typesPresents.filter { type ->
            val pt = schema[type]?.parentType
            pt != null && pt != "module" && pt in typesPresents
        }
        return if (typesEnfants.isEmpty()) typesPresents else typesPresents - typesEnfants.toSet()
    }

    /** « Sites : 8  ·  Transects : 3 » — le compte par type, en tête de liste. */
    fun resume(types: List<String>, schema: Map<String, MonitoringSchemaObjet>?, comptes: Map<String, Int>): String =
        types.joinToString("  ·  ") { type -> "${libelleListe(type, schema)} : ${comptes[type] ?: 0}" }

    /** Le nom de chaque enfant re-dérivé du `nameField` du schéma, quand il est renseigné : sa valeur
     *  telle que le web l'affiche en titre (`description`, propriété résolue) — date en jj/mm/aaaa,
     *  identifiant traduit par [resolver]. */
    fun renommer(
        liste: List<MonitoringEnfant>,
        schemaType: MonitoringSchemaObjet?,
        resolver: LabelResolver = LabelResolver(),
    ): List<MonitoringEnfant> {
        val nf = schemaType?.nameField ?: return liste
        val prop = schemaType.properties[nf]
        return liste.map { e ->
            val nomSchema = e.proprietes[nf]
            if (!nomSchema.isNullOrEmpty()) e.copy(nom = valeurAffichee(prop, nomSchema, resolver)) else e
        }
    }

    /** Les enfants du détail d'un protocole : renommés par le `nameField`, puis triés comme les listes
     *  du web, sur le premier critère des `sorts` de leur type (décision de l'utilisateur du
     *  21 octobre : « comme le web ») ; voir [trierCommeLeServeur]. */
    fun enfantsDuProtocole(
        enfants: Map<String, List<MonitoringEnfant>>,
        schema: Map<String, MonitoringSchemaObjet>?,
        resolver: LabelResolver = LabelResolver(),
    ): Map<String, List<MonitoringEnfant>> =
        enfants.mapValues { (type, liste) ->
            val schemaType = schema?.get(type)
            val critere = schemaType?.sorts?.firstOrNull()
            trierCommeLeServeur(renommer(liste, schemaType, resolver), critere, critere?.let { estNumerique(schemaType, it.first) })
        }

    /** [liste] triée sur [critere] (propriété, `asc` ou `desc`) comme le serveur trie les listes du
     *  web (`sites-groups.resolver.ts` lui passe `sorts[0]`) : les nombres par valeur, le reste à la
     *  française ; une valeur absente vient en tête d'un tri décroissant et en fin d'un tri croissant,
     *  comme sous PostgreSQL (un site jamais visité en tête d'un tri sur `last_visit desc`). Sans
     *  critère, ou quand aucune ligne ne porte la propriété (le serveur n'envoie d'un enfant que les
     *  champs de sa `display_list`), le tri par nom. */
    fun trierCommeLeServeur(liste: List<MonitoringEnfant>, critere: Pair<String, String>?, numerique: Boolean? = null): List<MonitoringEnfant> {
        val parNom = compareBy(comparateurFrancais()) { e: MonitoringEnfant -> e.nom }
        val (prop, sens) = critere ?: return liste.sortedWith(parNom)
        if (liste.none { it.proprietes.containsKey(prop) }) return liste.sortedWith(parNom)
        val decroissant = sens == "desc"
        val valeurs = comparateurValeurs(liste.mapNotNull { it.proprietes[prop] }, numerique)
        val parCritere = Comparator<MonitoringEnfant> { a, b ->
            val va = a.proprietes[prop]
            val vb = b.proprietes[prop]
            when {
                va == null && vb == null -> 0
                va == null -> if (decroissant) -1 else 1
                vb == null -> if (decroissant) 1 else -1
                else -> valeurs.compare(va, vb).let { if (decroissant) -it else it }
            }
        }
        // Filet : un ordre incohérent ne doit jamais fermer l'appli.
        return try {
            liste.sortedWith(parCritere.then(parNom))
        } catch (_: IllegalArgumentException) {
            liste.sortedWith(parNom)
        }
    }

    /** Un ordre TOTAL sur les [valeurs] d'une propriété, comme PostgreSQL trie sa colonne : en nombre
     *  si elle est numérique ([numerique], du schéma ; inconnu, si toutes ses valeurs sont des
     *  nombres), en texte sinon. Mêler les deux selon la paire comparée donnait un ordre non
     *  transitif (« 2 » < « 10 » en nombre, « 10 » < « 1a » < « 2 » en texte) : le tri d'Android pouvait
     *  lever « Comparison method violates its general contract! » et fermer l'appli à l'ouverture d'un
     *  protocole (audit du 21 octobre, M36). Une valeur illisible d'une propriété numérique vient
     *  après les nombres. */
    fun comparateurValeurs(valeurs: Collection<String>, numerique: Boolean?): Comparator<String> {
        val enNombre = numerique ?: valeurs.filter { it.isNotEmpty() }.let { v -> v.isNotEmpty() && v.all { it.toDoubleOrNull() != null } }
        val francais = comparateurFrancais()
        if (!enNombre) return francais
        return Comparator { a, b ->
            val na = a.toDoubleOrNull()
            val nb = b.toDoubleOrNull()
            when {
                na != null && nb != null -> na.compareTo(nb)
                na != null -> -1
                nb != null -> 1
                else -> francais.compare(a, b)
            }
        }
    }

    /** La propriété [nom] de [schemaType] est-elle numérique (widget `number` ou `integer`) ? Null si
     *  le schéma ne la décrit pas (`last_visit`, `nb_visits`… calculés par le serveur). */
    fun estNumerique(schemaType: MonitoringSchemaObjet?, nom: String): Boolean? =
        schemaType?.properties?.get(nom)?.typeWidget?.lowercase()?.let { it == "number" || it == "integer" }

    /** Construit le sous-titre d'une ligne enfant depuis `schema.display_list` (sans le
     *  nameField, déjà en titre), chaque valeur telle que le web l'affiche ([valeurAffichee] ; une
     *  liste, ses valeurs jointes par « , »). Ignore les valeurs vides ou null. */
    fun sousTitrePourEnfant(e: MonitoringEnfant, schemaType: MonitoringSchemaObjet?, resolver: LabelResolver): String {
        val displayList = schemaType?.displayList.orEmpty()
        if (displayList.isEmpty()) return ""
        val nameField = schemaType?.nameField
        return displayList
            .filter { it != nameField }
            .mapNotNull { k -> valeurDe(e.proprietes, e.valeursMultiples, k, schemaType?.properties?.get(k), resolver) }
            .joinToString(" · ")
    }

    /** La valeur affichée de la propriété [cle] : simple ([proprietes]) ou multiple ([multiples],
     *  valeurs jointes par « , » comme `getUtils` du web) ; null si elle est absente ou vide. */
    private fun valeurDe(
        proprietes: Map<String, String>,
        multiples: Map<String, List<String>>,
        cle: String,
        prop: MonitoringPropertySchema?,
        resolver: LabelResolver,
    ): String? {
        proprietes[cle]?.takeIf { it.isNotEmpty() && it != "null" }?.let { return valeurAffichee(prop, it, resolver) }
        return multiples[cle]?.takeIf { it.isNotEmpty() }?.joinToString(", ") { valeurAffichee(prop, it, resolver) }
    }

    /** Une valeur telle que le web l'affiche (`resolveProperty`) : un identifiant traduit par
     *  [resolver] (le champ de `display_field_names`), sinon [formatValeur] — date en jj/mm/aaaa
     *  pour un champ date, ou une valeur qui en a la forme. */
    fun valeurAffichee(prop: MonitoringPropertySchema?, valeur: String, resolver: LabelResolver): String =
        prop?.let { resolver.resoudre(it, valeur) }
            ?: formatValeur(valeur, date = prop?.typeWidget.equals("date", ignoreCase = true) || prop?.typeUtil == "date")

    /** Premier type de saisie (visite, passage, observation…) parmi les enfants déclarés par le
     *  schéma de [schemaType] : ce que crée le « + » d'une ligne de ce type. */
    fun premierTypeSaisie(schemaType: MonitoringSchemaObjet?): String? =
        schemaType?.childrenTypes?.firstOrNull { MonitoringSync.estTypeSaisie(it) }

    /** Le « + » d'une ligne d'objet SERVEUR : neutralisé quand la ligne est elle-même une saisie
     *  déjà historisée (une visite ancienne ne reçoit pas de sous-saisie a posteriori). */
    fun typeSaisieEnfantServeur(type: String, schemaType: MonitoringSchemaObjet?): String? =
        premierTypeSaisie(schemaType)?.takeIf { !MonitoringSync.estTypeSaisie(type) }

    // ─── Fiche d'un objet ───

    /** Titre d'une fiche : la valeur du nameField déclaré par le schéma, telle que le web l'affiche
     *  ([valeurAffichee]), sinon heuristique. */
    fun nomObjet(objet: MonitoringObjet, schemaCe: MonitoringSchemaObjet?, resolver: LabelResolver = LabelResolver()): String {
        val nf = schemaCe?.nameField
        return nf?.let { objet.proprietes[it] }?.takeIf { it.isNotEmpty() }?.let { valeurAffichee(schemaCe.properties[nf], it, resolver) }
            ?: MonitoringObjets.extraireNomHeuristique(objet.proprietes, objet.type, objet.id)
    }

    /** Les propriétés d'une fiche, « libellé » et « valeur ». Priorité au schéma serveur :
     *  `display_properties` dicte l'ORDRE et le SOUS-ENSEMBLE des champs à afficher (= ce que
     *  montre l'interface web), `attribut_label` dicte le libellé, et chaque valeur est celle du web
     *  ([valeurAffichee] ; une liste, ses valeurs jointes par « , »). Sans schéma exploitable,
     *  fallback sur l'ancien filtre par blacklist. */
    fun proprietesAffichees(
        objet: MonitoringObjet,
        schemaCe: MonitoringSchemaObjet?,
        resolver: LabelResolver,
    ): List<Pair<String, String>> {
        val nameField = schemaCe?.nameField
        val displayList = schemaCe?.displayProperties.orEmpty()
        if (displayList.isNotEmpty()) {
            return displayList.filter { it != nameField }.mapNotNull { k ->
                val prop = schemaCe?.properties?.get(k)
                val valeur = valeurDe(objet.proprietes, objet.valeursMultiples, k, prop, resolver) ?: return@mapNotNull null
                (prop?.label ?: labelChamp(k)) to valeur
            }
        }
        val cles: List<String> = run {
            // Fallback (vieux schéma sans display_properties) : tous les scalaires moins
            // une blacklist de champs techniques.
            objet.proprietes.keys.filter { k ->
                k != nameField &&
                    !k.startsWith("id_") &&
                    !k.startsWith("uuid_") &&
                    !k.startsWith("meta_") &&
                    k != "additional_data_keys" &&
                    k != "pk" &&
                    k != "altitude_min" && k != "altitude_max" &&
                    !k.endsWith("_code") &&
                    k != "geom" && k != "geometry" && !k.startsWith("geom_")
            }
        }
        return cles.mapNotNull { k ->
            val v = objet.proprietes[k] ?: return@mapNotNull null
            // Schéma serveur en priorité (attribut_label), sinon mapping FR par défaut.
            val prop = schemaCe?.properties?.get(k)
            (prop?.label ?: labelChamp(k)) to valeurAffichee(prop, v, resolver)
        }
    }

    /** L'onglet « Propriétés spécifiques » de la fiche d'un site sur le web : les champs propres à
     *  ses types de site ([champs], cf. MonitoringDatalists.lireChampsTypesSite), dans l'ordre de
     *  leur nom (pipe `keyvalue` d'Angular), avec leur valeur affichée. Un champ sans valeur est omis,
     *  comme dans les propriétés. */
    fun proprietesSpecifiques(
        objet: MonitoringObjet,
        champs: Map<String, MonitoringPropertySchema>,
        resolver: LabelResolver,
    ): List<Pair<String, String>> =
        champs.entries.sortedBy { it.key }.mapNotNull { (nom, prop) ->
            valeurDe(objet.proprietes, objet.valeursMultiples, nom, prop, resolver)?.let { prop.label.ifEmpty { labelChamp(nom) } to it }
        }

    /** Label FR pour un nom de champ de gn_module_monitoring (best-effort), sinon clé nettoyée. */
    fun labelChamp(key: String): String = when (key) {
        "base_site_name", "sites_group_name" -> "Nom"
        "base_site_code", "sites_group_code" -> "Code"
        "base_site_description", "sites_group_description" -> "Description"
        "comment_site", "comment" -> "Commentaire"
        "desc_habitat" -> "Habitat"
        "prospect_surf" -> "Surface prospectée (m²)"
        "nb_visits" -> "Nombre de visites"
        "last_visit" -> "Dernière visite"
        "first_use_date" -> "Mise en service"
        "visit_date_min", "date_min" -> "Date de visite"
        "visit_date_max", "date_max" -> "Date de fin"
        "comments" -> "Commentaires"
        "altitude_min" -> "Altitude min (m)"
        "altitude_max" -> "Altitude max (m)"
        else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /** Format léger : booléens en oui/non ; une date en jj/mm/aaaa, comme le web (`Utils.formatDate`),
     *  pour un champ date ([date]) ou une valeur qui a la forme d'une date ISO ; sinon brut. */
    fun formatValeur(v: String, date: Boolean = false): String {
        if (v == "true") return "oui"
        if (v == "false") return "non"
        return if (date || DATE_ISO.matches(v)) DateAffichage.isoVersFr(v) else v
    }

    /** `2026-06-12`, `2026-06-12T08:00:00`, `2026-06-12 08:00:00.123+02:00`… */
    private val DATE_ISO = Regex("""\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?)?""")

    /** Tri chronologique DÉCROISSANT (plus récentes d'abord) des enfants d'un type, en tenant
     *  compte de la DATE puis de l'HEURE de la visite. Schema-driven : le champ date est celui de
     *  `type_widget == "date"` (priorité au `description_field_name` du type, sinon un champ `*_min`,
     *  sinon le premier), l'heure celui de `type_widget == "time"`. Les valeurs ISO `YYYY-MM-DD` +
     *  `HH:MM` se trient lexicographiquement = chronologiquement. Fallback tri alpha par nom quand
     *  le type n'a pas de champ date (parité avec l'ancien comportement). */
    fun trierChronologique(items: List<MonitoringEnfant>, schemaType: MonitoringSchemaObjet?): List<MonitoringEnfant> {
        val props = schemaType?.properties
        val champsDate = props?.filterValues { it.typeWidget.equals("date", ignoreCase = true) }?.keys.orEmpty()
        // Type sans champ date (sites, groupes de sites…) : on respecte les `sorts` déclarés
        // par le schéma (parité web), à défaut le tri alphabétique par nom de trierEnfants.
        if (champsDate.isEmpty()) return MonitoringObjets.trierEnfants(items, schemaType?.sorts.orEmpty())
        val champDate = schemaType?.nameField?.takeIf { it in champsDate }
            ?: champsDate.firstOrNull { it.endsWith("_min") }
            ?: champsDate.first()
        val champHeure = props?.entries
            ?.firstOrNull { it.value.typeWidget.equals("time", ignoreCase = true) }?.key
        fun cle(e: MonitoringEnfant): String {
            val d = e.proprietes[champDate].orEmpty()
            val h = champHeure?.let { e.proprietes[it] }.orEmpty()
            return "$d $h"
        }
        // Décroissant : les visites sans date (clé vide) se retrouvent en bas.
        return items.sortedByDescending { cle(it) }
    }
}
