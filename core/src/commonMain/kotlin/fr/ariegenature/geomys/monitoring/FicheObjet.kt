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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/FicheObjetFragment.kt :
// afficherEnfants, creerHeaderType, creerLigneSaisieLocale, saisiesLocalesAttacheesA, signatureOutbox) : les
// enfants d'une fiche — groupes par type, saisies locales en tête, enfants du serveur, types de saisie sans
// occurrence — et ce que chacun de leurs boutons ouvre, sortis des vues.

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.network.MonitoringApi.LabelResolver
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringObjet
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.MonitoringSync
import fr.ariegenature.geomys.util.FormatDate

/** Ce qu'ouvre un « + » : une nouvelle saisie de [childObjectType] sous son parent, désigné par
 *  son id serveur ([parentId]) ou, s'il n'est pas encore envoyé, par son uuid local. */
data class NouvelleSaisie(
    val parentObjectType: String,
    val parentId: Int = -1,
    val parentUuidLocal: String? = null,
    val titreSite: String,
    val childObjectType: String,
    /** « Nouvelle visite », « Nouveau passage »… : la description du bouton. */
    val libelle: String,
    /** Le fil d'Ariane du formulaire (ses ancêtres). */
    val fil: List<FilSegment>,
)

/** Un élément de la liste des enfants d'une fiche. */
sealed interface ElementFiche {
    /** « VISITES (3) », et le « + » d'une nouvelle saisie quand le type en est une. */
    data class Entete(val texte: String, val nouvelle: NouvelleSaisie?) : ElementFiche

    /** Saisie locale pas encore envoyée (cadre rouge) : « ⏳ Visite (local) », « 12/06 06:30 · à
     *  envoyer », et le « + » qui y enchaîne une sous-saisie. */
    data class Locale(val uuid: String, val titre: String, val sousTitre: String, val nouvelle: NouvelleSaisie?) : ElementFiche

    /** Enfant du serveur ; [fil] : celui de sa fiche et de sa carte. */
    data class Serveur(
        val type: String,
        val id: Int,
        val nom: String,
        val sousTitre: String,
        val carte: Boolean,
        val nouvelle: NouvelleSaisie?,
        val fil: List<FilSegment>,
    ) : ElementFiche
}

/** La fiche d'un objet de suivi (site, visite, observation…). */
object FicheObjet {

    /** Les saisies locales (file d'envoi, hors envoyées) rattachées à [objet] comme parent
     *  direct, par son id serveur. */
    fun saisiesLocalesAttacheesA(objet: MonitoringObjet, toutes: List<SaisieEnAttente>): List<SaisieEnAttente> =
        toutes.filter {
            it.etat != SaisieEnAttente.Etat.SENT &&
                it.moduleCode == objet.moduleCode &&
                it.parentObjectType == objet.type &&
                it.parentIdServeur == objet.id
        }

    /** Signature des saisies de la file rattachées à [objet] (tous états confondus, envoyées
     *  comprises) : si elle a changé au retour sur la fiche, une saisie a été ajoutée ou envoyée —
     *  la fiche est rechargée (une visite envoyée n'est plus dans la file mais sur le serveur). */
    fun signatureOutbox(objet: MonitoringObjet, toutes: List<SaisieEnAttente>): String =
        toutes
            .filter { it.moduleCode == objet.moduleCode && it.parentObjectType == objet.type && it.parentIdServeur == objet.id }
            .sortedBy { it.uuid }
            .joinToString(",") { "${it.uuid}:${it.etat}" }

    /** Les enfants de [objet], dans l'ordre de la fiche :
     *  1. pour chaque type d'enfant du serveur : son en-tête, ses saisies locales (les plus récentes
     *     d'abord), puis ses enfants du serveur (chronologie décroissante) ;
     *  2. les saisies locales d'un type que le serveur ne déclare pas encore (première visite) ;
     *  3. les types de saisie du schéma sans aucune occurrence : un en-tête « (0) » et son « + ».
     *  [labelTypeEnCache] et [libelleNouveau] : ceux de `MonitoringObjets`. */
    fun elements(
        objet: MonitoringObjet,
        schema: Map<String, MonitoringSchemaObjet>?,
        resolver: LabelResolver,
        toutes: List<SaisieEnAttente>,
        fil: List<FilSegment>,
        labelTypeEnCache: (moduleCode: String, type: String) -> String?,
        libelleNouveau: (moduleCode: String, type: String) -> String,
    ): List<ElementFiche> {
        val elements = mutableListOf<ElementFiche>()
        val saisiesLocalesIci = saisiesLocalesAttacheesA(objet, toutes)
        val schemaObjet = schema?.get(objet.type)

        fun entete(type: String, label: String, nombre: Int): ElementFiche.Entete {
            val nouvelle = if (MonitoringSync.estTypeSaisie(type)) NouvelleSaisie(
                parentObjectType = objet.type,
                parentId = objet.id,
                titreSite = schemaObjet?.nameField?.let { objet.proprietes[it] } ?: "",
                childObjectType = type,
                libelle = libelleNouveau(objet.moduleCode, type),
                fil = fil,
            ) else null
            return ElementFiche.Entete("$label ($nombre)", nouvelle)
        }

        fun locale(type: String, saisie: SaisieEnAttente, typeSaisieEnfant: String?): ElementFiche.Locale {
            val icone = when (saisie.etat) {
                SaisieEnAttente.Etat.ERROR -> "⚠"
                SaisieEnAttente.Etat.SENDING -> "🚀"
                else -> "⏳"
            }
            val labelLocal = labelTypeEnCache(saisie.moduleCode, saisie.objectType) ?: "Donnée"
            val date = FormatDate.jourMoisHeure(saisie.dateLocale)
            // Le « + » pointe le parent par son uuid local : l'envoi en cascade le résoudra en id
            // serveur quand le parent sera parti.
            val nouvelle = typeSaisieEnfant?.let {
                NouvelleSaisie(
                    parentObjectType = type,
                    parentUuidLocal = saisie.uuid,
                    titreSite = "$labelLocal (local) du $date",
                    childObjectType = it,
                    libelle = libelleNouveau(objet.moduleCode, it),
                    fil = fil,
                )
            }
            return ElementFiche.Locale(saisie.uuid, "$icone $labelLocal (local)", "$date · à envoyer", nouvelle)
        }

        objet.enfants.forEach { (type, itemsBruts) ->
            val saisiesLocalesCeType = saisiesLocalesIci.filter { it.objectType == type }
            if (itemsBruts.isEmpty() && saisiesLocalesCeType.isEmpty()) return@forEach
            val schemaType = schema?.get(type)
            val items = AffichageSuivis.trierChronologique(AffichageSuivis.renommer(itemsBruts, schemaType), schemaType)
            val typeSaisieEnfant = AffichageSuivis.premierTypeSaisie(schemaType)
            // Sur un enfant SERVEUR, pas de « + » s'il est lui-même une saisie historisée.
            val typeSaisieEnfantServeur = AffichageSuivis.typeSaisieEnfantServeur(type, schemaType)
            elements += entete(type, AffichageSuivis.libelleListe(type, schema), items.size + saisiesLocalesCeType.size)
            saisiesLocalesCeType.sortedByDescending { it.dateLocale }.forEach { elements += locale(type, it, typeSaisieEnfant) }
            items.forEach { e ->
                val filEnfant = fil + FilSegment(type, e.id, e.nom)
                elements += ElementFiche.Serveur(
                    type = type,
                    id = e.id,
                    nom = e.nom,
                    sousTitre = AffichageSuivis.sousTitrePourEnfant(e, schemaType, resolver),
                    carte = schemaType?.geometryType != null,
                    nouvelle = typeSaisieEnfantServeur?.let {
                        NouvelleSaisie(
                            parentObjectType = type,
                            parentId = e.id,
                            titreSite = e.nom,
                            childObjectType = it,
                            libelle = libelleNouveau(objet.moduleCode, it),
                            fil = filEnfant,
                        )
                    },
                    fil = filEnfant,
                )
            }
        }

        // Types de saisies locales que la fiche serveur ne déclare pas encore (première visite
        // d'un parent : le serveur n'expose pas le type d'enfant, mais la file en contient).
        val typesDejaRendus = objet.enfants.keys
        val orphelines = saisiesLocalesIci.filterNot { it.objectType in typesDejaRendus }
        orphelines.groupBy { it.objectType }.forEach { (type, saisies) ->
            val schemaType = schema?.get(type)
            elements += entete(type, AffichageSuivis.libelleListe(type, schema), saisies.size)
            saisies.sortedByDescending { it.dateLocale }.forEach {
                elements += locale(type, it, AffichageSuivis.premierTypeSaisie(schemaType))
            }
        }

        // Types de saisie déclarés au schéma sans aucune occurrence : un en-tête « (0) » dont le
        // « + » amorce la première saisie. « Déjà affiché » = réellement RENDU plus haut : le
        // serveur renvoie souvent `children: {visit: []}` pour un point sans visite.
        val typesDejaAffiches = objet.enfants.filter { (type, items) ->
            items.isNotEmpty() || saisiesLocalesIci.any { it.objectType == type }
        }.keys + orphelines.map { it.objectType }.toSet()
        schemaObjet?.childrenTypes.orEmpty()
            .filter { MonitoringSync.estTypeSaisie(it) && it !in typesDejaAffiches }
            .forEach { type -> elements += entete(type, AffichageSuivis.libelleListe(type, schema), 0) }
        return elements
    }
}
