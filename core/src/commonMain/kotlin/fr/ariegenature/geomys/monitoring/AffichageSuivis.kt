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
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.MonitoringObjets
import fr.ariegenature.geomys.network.MonitoringSync
import fr.ariegenature.geomys.util.DateAffichage

/** L'affichage des objets d'un protocole de suivi, piloté par son schéma. */
object AffichageSuivis {

    /** Résumé du détail d'un protocole pendant son chargement. */
    const val RESUME_EN_COURS = "Sites : …"

    /** Détail d'un protocole absent de la liste chargée (cache mémoire vide). */
    const val MESSAGE_PROTOCOLE_INCONNU = "Recharge la liste pour voir les détails."

    /** Fallback générique quand le schéma serveur ne fournit pas de `label_list`. Capitalise
     *  le code technique et remplace les `_` par des espaces — pas de mapping FR figé qui
     *  dépendrait des conventions d'un protocole. Pour un rendu propre, c'est à l'admin
     *  GeoNature de poser un `label_list` côté schéma. */
    fun labelTypeParDefaut(type: String): String =
        type.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** Libellé d'un object_type en tête de liste (« Sites ») : schéma serveur en priorité
     *  (label_list, puis label), sinon fallback générique (`labelPour` du détail). */
    fun libelleListe(type: String, schema: Map<String, MonitoringSchemaObjet>?): String =
        schema?.get(type)?.let { s -> (s.labelList ?: s.label)?.takeIf { it.isNotEmpty() } }
            ?: labelTypeParDefaut(type)

    /** Libellé d'un objet sous son nom, dans sa fiche (« Site ») : label, puis label_list. */
    fun libelleType(type: String, schemaCe: MonitoringSchemaObjet?): String =
        schemaCe?.label ?: schemaCe?.labelList ?: labelTypeParDefaut(type)

    /** Sélectionne les object_types à afficher dans la liste principale et dans le résumé.
     *  Filtre par `parent_type` : un type est macro s'il déclare le module comme parent.
     *  Si l'API renvoie à plat des types avec parent commun (cas observé : sites_group + site
     *  d'un protocole STOM-like), on garde uniquement les "racines" et on masque les enfants
     *  qui apparaîtront via drill-down sur leur parent. */
    fun typesAfficherEnListe(typesPresents: List<String>, schema: Map<String, MonitoringSchemaObjet>?): List<String> {
        if (schema == null) return typesPresents
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

    /** Le nom de chaque enfant re-dérivé du `nameField` du schéma, quand il est renseigné. */
    fun renommer(liste: List<MonitoringEnfant>, schemaType: MonitoringSchemaObjet?): List<MonitoringEnfant> {
        val nf = schemaType?.nameField ?: return liste
        return liste.map { e ->
            val nomSchema = e.proprietes[nf]
            if (!nomSchema.isNullOrEmpty()) e.copy(nom = nomSchema) else e
        }
    }

    /** Les enfants du détail d'un protocole : renommés par le `nameField`, puis triés par nom. Les
     *  `sorts` du schéma sont volontairement ignorés : les protocoles l'utilisent surtout pour
     *  ordonner par id (peu lisible côté utilisateur). */
    fun enfantsDuProtocole(
        enfants: Map<String, List<MonitoringEnfant>>,
        schema: Map<String, MonitoringSchemaObjet>?,
    ): Map<String, List<MonitoringEnfant>> =
        enfants.mapValues { (type, liste) -> MonitoringObjets.trierEnfants(renommer(liste, schema?.get(type)), emptyList()) }

    /** Construit le sous-titre d'une ligne enfant depuis `schema.display_list` (sans le
     *  nameField, déjà en titre). Formate les dates ISO, résout les IDs en labels via le
     *  resolver, ignore les valeurs vides ou null. */
    fun sousTitrePourEnfant(e: MonitoringEnfant, schemaType: MonitoringSchemaObjet?, resolver: LabelResolver): String {
        val displayList = schemaType?.displayList.orEmpty()
        if (displayList.isEmpty()) return ""
        val nameField = schemaType?.nameField
        return displayList
            .filter { it != nameField }
            .mapNotNull { k ->
                val v = e.proprietes[k]?.takeIf { it.isNotEmpty() && it != "null" } ?: return@mapNotNull null
                // Résolution ID → label si la propriété a un type_util géré.
                val prop = schemaType?.properties?.get(k)
                if (prop != null) resolver.resoudre(prop, v)?.let { return@mapNotNull it }
                DateAffichage.isoVersFr(v)
            }
            .joinToString(" · ")
    }

    /** Premier type de saisie (visite, passage, observation…) parmi les enfants déclarés par le
     *  schéma de [schemaType] : ce que crée le « + » d'une ligne de ce type. */
    fun premierTypeSaisie(schemaType: MonitoringSchemaObjet?): String? =
        schemaType?.childrenTypes?.firstOrNull { MonitoringSync.estTypeSaisie(it) }

    /** Le « + » d'une ligne d'objet SERVEUR : neutralisé quand la ligne est elle-même une saisie
     *  déjà historisée (une visite ancienne ne reçoit pas de sous-saisie a posteriori). */
    fun typeSaisieEnfantServeur(type: String, schemaType: MonitoringSchemaObjet?): String? =
        premierTypeSaisie(schemaType)?.takeIf { !MonitoringSync.estTypeSaisie(type) }

    // ─── Fiche d'un objet ───

    /** Titre d'une fiche : la valeur du nameField déclaré par le schéma, sinon heuristique. */
    fun nomObjet(objet: MonitoringObjet, schemaCe: MonitoringSchemaObjet?): String =
        schemaCe?.nameField?.let { objet.proprietes[it] }?.takeIf { it.isNotEmpty() }
            ?: MonitoringObjets.extraireNomHeuristique(objet.proprietes, objet.type, objet.id)

    /** Les propriétés d'une fiche, « libellé » et « valeur ». Priorité au schéma serveur :
     *  `display_properties` dicte l'ORDRE et le SOUS-ENSEMBLE des champs à afficher (= ce que
     *  montre l'interface web), `attribut_label` dicte le libellé. Sans schéma exploitable,
     *  fallback sur l'ancien filtre par blacklist. */
    fun proprietesAffichees(
        objet: MonitoringObjet,
        schemaCe: MonitoringSchemaObjet?,
        resolver: LabelResolver,
    ): List<Pair<String, String>> {
        val nameField = schemaCe?.nameField
        val displayList = schemaCe?.displayProperties.orEmpty()
        val cles: List<String> = if (displayList.isNotEmpty()) {
            displayList.filter { it != nameField && objet.proprietes.containsKey(it) }
        } else {
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
            val libelle = prop?.label ?: labelChamp(k)
            // Résolution ID → label via le resolver si type_util est géré, sinon formatage.
            val resolu = if (prop != null) resolver.resoudre(prop, v) else null
            libelle to (resolu ?: formatValeur(v))
        }
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

    /** Format léger : booleans en oui/non, dates ISO raccourcies, sinon raw. */
    fun formatValeur(v: String): String {
        if (v == "true") return "oui"
        if (v == "false") return "non"
        // Date ISO "YYYY-MM-DD..." → "YYYY-MM-DD" simple
        return DateAffichage.isoTronquerDate(v)
    }

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
