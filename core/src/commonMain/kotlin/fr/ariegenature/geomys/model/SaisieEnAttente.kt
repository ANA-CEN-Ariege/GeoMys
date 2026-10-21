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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/OutboxMonitoring.kt,
// classe SaisieEnAttente) : sortie du store vers model/, @Serializable, UUID et currentTimeMillis
// remplacés par leurs équivalents communs. Les noms des propriétés forment le format du fichier de
// la file d'envoi : ne pas les renommer. L'énumération Etat s'écrit par son nom, comme avec Gson.

package fr.ariegenature.geomys.model

import fr.ariegenature.geomys.util.maintenantMillis
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlinx.serialization.Serializable

/** Une saisie monitoring (visite, observation, occurrence…) stockée localement en
 *  attendant d'être poussée vers le serveur. L'envoi est exclusivement à la demande
 *  utilisateur (depuis l'écran [SaisiesEnAttenteFragment]) — aucun envoi automatique
 *  au retour réseau.
 *
 *  Le lien parent → enfant utilise [parentUuidLocal] tant que le parent n'est pas envoyé
 *  (= uuid d'une autre entrée outbox), et [parentIdServeur] dès que le parent est SENT.
 *  L'ordonnancement à l'envoi résout les parentUuidLocal en id serveur avant le POST
 *  des enfants. */
@Serializable
data class SaisieEnAttente(
    val uuid: String = uuidAleatoire(),
    val moduleCode: String,
    val objectType: String,
    val parentObjectType: String? = null,
    val parentIdServeur: Int? = null,
    val parentUuidLocal: String? = null,
    val parentIdField: String? = null,
    val nomsChampsSchema: List<String> = emptyList(),
    /** Codes des valeurs en texte, qui partent en texte même numériques (un commentaire « 42 »,
     *  une option « 1 »), comme dans le corps du web. Une saisie enregistrée avant le 20 octobre
     *  2026 n'y a que ses champs TEXT et TEXTAREA (audit B6) : ses autres valeurs numériques en
     *  texte partent en nombres, comme alors. */
    val champsTexteLibre: List<String> = emptyList(),
    /** Les champs de widget `date`, envoyés en `année-mois-jour` sans zéros comme sur le web ; vide
     *  pour une saisie enregistrée avant le 20 octobre 2026. */
    val champsDate: List<String> = emptyList(),
    /** Les textes et les heures vidés par l'utilisateur, envoyés en "" comme sur le web ; vide pour
     *  une saisie enregistrée avant le 20 octobre 2026, dont les "" partent en null. */
    val champsVidesGardes: List<String> = emptyList(),
    /** JSON sérialisé du payload des valeurs collectées par le form renderer. Stocké en
     *  String pour éviter les pertes de type Gson (Int → Double, etc.). Reparsé via
     *  JSONObject au moment de l'envoi. */
    val valeursJson: String,
    val dateLocale: Long = maintenantMillis(),
    val etat: Etat = Etat.PENDING,
    val messageErreur: String? = null,
    /** Id serveur attribué après envoi réussi — utile pour le drill-down ou pour purger
     *  cette saisie de la file plus tard. */
    val idServeur: Int? = null,
    /** true dès que l'objet a été CRÉÉ côté serveur (POST 2xx), même si l'upload des médias
     *  a échoué ensuite. Un « Réessayer » sur une saisie objetCree ne re-POSTe PAS l'objet
     *  (doublon sinon) : il ne renvoie que les médias. Permet aussi de débloquer les enfants
     *  d'un parent dont seuls les médias ont échoué. */
    val objetCree: Boolean = false,
    /** true dès qu'un POST a été TENTÉ (passage en SENDING), même sans confirmation. Si la
     *  réponse s'est perdue (coupure pendant le transfert), l'objet peut exister côté serveur
     *  sans que [objetCree] le sache : avant un re-POST, l'envoi vérifie alors par
     *  [uuidPayload] que l'objet n'existe pas déjà (anti-doublon, cf. OutboxEnvoi). */
    val dejaTentee: Boolean = false,
    /** uuid pré-généré côté client à la création de la saisie, injecté dans le payload POST
     *  au champ [uuidFieldName] (ex. `uuid_base_visit`). Sert ensuite à rattacher un média
     *  uploadé sur gn_commons à l'objet créé via `uuid_attached_row`. Null si le schéma ne
     *  déclare pas d'uuid_field_name (vieux protocole) ou pour les saisies sans média. */
    val uuidPayload: String? = null,
    /** Nom du champ uuid du schéma (cf. [uuidPayload]) — recopié du MonitoringSchemaObjet. */
    val uuidFieldName: String? = null,
    /** LEGACY (mono-fichier) : ancien champ unique, conservé en lecture seule pour ne pas perdre
     *  les brouillons créés avant le passage au multi-fichiers. Les nouvelles saisies écrivent
     *  [mediaPathsLocal]. Cf. [mediasLocaux]. */
    val mediaPathLocal: String? = null,
    /** Chemins locaux des fichiers médias à uploader après création de l'objet. Format URI String
     *  pointant vers filesDir/medias/… (copie locale faite à la sélection pour survivre au cycle
     *  de vie de l'Uri ACTION_GET_CONTENT). Vide = pas de média. */
    val mediaPathsLocal: List<String> = emptyList(),
    /** `schema_dot_table` du champ media (ex. `gn_monitoring.t_base_visits`), résolu en
     *  id_table_location côté envoi. Null si pas de média. */
    val mediaSchemaDotTable: String? = null,
    /** Les médias de TOUS les champs `medias`, avec leurs titre, auteur et description saisis comme
     *  sur le web ; null pour une saisie enregistrée avant le 20 octobre 2026, dont les médias sont
     *  [mediaPathsLocal], titrés d'office à l'envoi. */
    val mediasSuivis: List<MediaSuivi>? = null,
    /** Chemins des médias DÉJÀ TRANSMIS au serveur, acquis au fil de l'eau.
     *
     *  Sans cette mémoire, un « Réessayer » après un échec PARTIEL renvoyait la liste COMPLÈTE :
     *  trois essais échouant sur la 3ᵉ photo laissaient trois exemplaires des deux premières dans
     *  gn_commons.t_medias, donc dans la visite GeoNature — et le message affiché promettait
     *  pourtant le contraire. La consommation réseau doublait aussi à chaque essai, sur un lien
     *  déjà fragile (audit 2026-09-14, R1-M3). Nullable : les saisies écrites avant ce champ le
     *  relisent à null (Gson par réflexion, cf. [normaliser]). */
    val mediasEnvoyes: List<String>? = null,
    /** LIBELLÉS des champs OBLIGATOIRES encore vides au dernier enregistrement — saisie
     *  « à compléter » (demande terrain 2026-09-03). Certains protocoles imposent des infos
     *  qu'on ne connaît qu'À LA FIN de la visite (heure de fin, température de fin) : la
     *  saisie est donc enregistrable incomplète, mais elle NE PART PAS tant qu'il reste des
     *  manquants ([OutboxEnvoi] l'écarte, ses enfants restent bloqués avec elle). Recalculé à
     *  chaque enregistrement du formulaire — jamais évalué ailleurs (les règles `required`
     *  dynamiques ont besoin du rendu). Nullable : les saisies écrites avant ce champ le
     *  relisent à null (Gson par réflexion, cf. [manquants]). */
    val champsManquants: List<String>? = null,
) {
    @Serializable
    enum class Etat { PENDING, SENDING, SENT, ERROR }

    /** Libellés des champs obligatoires manquants (liste vide si la saisie est complète). */
    fun manquants(): List<String> = champsManquants ?: emptyList()

    /** true = saisie CONSERVÉE localement mais non envoyable tant qu'elle n'est pas complétée. */
    val aCompleter: Boolean get() = !champsManquants.isNullOrEmpty()

    /** Liste effective des médias : [mediaPathsLocal] si renseignée, sinon repli sur l'ancien
     *  champ mono-fichier [mediaPathLocal] (brouillons d'avant la multi-pj). */
    fun mediasLocaux(): List<String> =
        mediaPathsLocal.ifEmpty { listOfNotNull(mediaPathLocal?.takeIf { it.isNotEmpty() }) }

    /** Médias qu'il RESTE à transmettre : les locaux moins ceux déjà acquis. C'est cette liste
     *  seule qui doit partir à chaque tentative, sinon un « Réessayer » duplique les précédents. */
    fun mediasARenvoyer(): List<String> {
        val acquis = mediasEnvoyes ?: return mediasLocaux()
        return mediasLocaux().filterNot { it in acquis }
    }

    /** Les médias détaillés : [mediasSuivis], sinon ceux d'une saisie plus ancienne, sans titre ni
     *  champ, rattachés à la table de son unique champ médias. */
    fun mediasDetailles(): List<MediaSuivi> =
        mediasSuivis ?: mediasLocaux().map { MediaSuivi(it, schemaDotTable = mediaSchemaDotTable) }

    /** Les médias détaillés qu'il reste à transmettre (cf. [mediasARenvoyer]). */
    fun mediasDetaillesARenvoyer(): List<MediaSuivi> {
        val acquis = mediasEnvoyes ?: return mediasDetailles()
        return mediasDetailles().filterNot { it.chemin in acquis }
    }
}
