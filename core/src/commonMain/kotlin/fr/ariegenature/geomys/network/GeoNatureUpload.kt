/*
 * GeoMys — application de saisie naturaliste pour GeoNature, Android et iOS.
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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/GeoNatureUpload.kt) :
// l'objet devient une classe injectée (fonctions pures en compagnon) ; HttpURLConnection remplacé par
// ClientsGeoNature (Ktor), y compris le multipart des médias, écrit à la main sous Android ;
// java.io.File et android.net.Uri par okio et cheminDepuisUri ; URLConnection.guessContentTypeFromName
// par typeMime ; SimpleDateFormat par DateEnvoi ; Gson par kotlinx ; Log par Journal. Le marqueur
// d'envoi devient suspend : il écrit dans des stores à verrou de coroutine.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.journal.Journal
import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.AdditionalFieldDef
import fr.ariegenature.geomys.model.AdditionalFieldsObject
import fr.ariegenature.geomys.model.MediaSuivi
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.delete
import fr.ariegenature.geomys.network.http.get
import fr.ariegenature.geomys.network.http.postJson
import fr.ariegenature.geomys.network.http.urlAbsolue
import fr.ariegenature.geomys.plateforme.sansDiacritiques
import fr.ariegenature.geomys.saisie.ChampsAdditionnels
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.OcctaxFieldsConfig
import fr.ariegenature.geomys.store.RelevesOrphelins
import fr.ariegenature.geomys.util.DateEnvoi
import fr.ariegenature.geomys.util.maintenantMillis
import fr.ariegenature.geomys.util.uuidDepuisOctets
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.decodeURLPart
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.IOException
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM

/** Résultat d'un envoi (`GeoNatureUpload.envoyer`). */
data class EnvoiResult(
    /** Nombre d'occurrences créées avec succès côté serveur. */
    val nbCrees: Int,
    /** Nombre total d'occurrences candidates. */
    val nbTotal: Int,
    /** Premier id_releve_occtax créé (pour navigation). */
    val premierIdReleve: Int?,
    /** Nombre de médias uploadés avec succès vers gn_commons. */
    val mediasOK: Int = 0,
    /** Nombre de médias dont l'upload a échoué. */
    val mediasKO: Int = 0,
    /** Message d'erreur du premier média en échec (debug — surfacé dans le toast). */
    val mediaErreurMsg: String? = null,
    /** Relevés créés côté serveur dont toutes les occurrences ont échoué ET dont le DELETE de
     *  rollback a aussi échoué — restent vides côté GeoNature. Vide en cas de succès complet ou de
     *  rollback réussi. */
    val relevesOrphelins: List<Int> = emptyList(),
    /** Ids ([Observation.id]) des observations dont l'occurrence a été CRÉÉE côté serveur pendant
     *  CET envoi. L'appelant les persiste (SortieStore.marquerObservationsEnvoyees) pour qu'un
     *  ré-envoi après succès partiel ne les re-poste pas (anti-doublon). */
    val obsCreesIds: List<String> = emptyList(),
    /** Observations exclues de cet envoi car déjà créées lors d'un envoi précédent
     *  ([Observation.envoyeeServeur]) — pour le récapitulatif utilisateur. */
    val nbDejaEnvoyees: Int = 0,
    /** Dernière erreur rencontrée pendant l'envoi (utile quand nbCrees > 0 : l'envoi PARTIEL ne
     *  lève pas, mais l'appelant doit pouvoir dire pourquoi il manque des obs). */
    val messageDerniereErreur: String? = null,
    /** Observations dont le POST d'occurrence a été émis SANS réponse pendant cet envoi (coupure
     *  après émission), avec l'id du relevé visé : peut-être créées côté serveur. Déjà persistées
     *  via [MarqueurEnvoiOcctax] ; remontées pour le récapitulatif. */
    val obsIncertaines: List<Pair<String, Int>> = emptyList(),
    /** Occurrences et relevés modifiés après leur transmission, mis à jour pendant cet envoi (C3). */
    val nbMisesAJour: Int = 0,
    /** Ceux dont la mise à jour a échoué : la saisie reste à envoyer. */
    val nbMisesAJourEnEchec: Int = 0,
)

/**
 * Persistance AU FIL DE L'EAU pendant l'envoi Occtax, appelée avant tout retour : l'acquis
 * (occurrence créée, ou POST resté sans réponse) survit à une annulation de coroutine (écran
 * détruit pendant l'envoi) et à la mort du processus — sans quoi des occurrences créées côté
 * serveur restaient non marquées et repartaient au ré-envoi (doublons, audit 2026-08-27).
 */
interface MarqueurEnvoiOcctax {
    /** L'occurrence [obsId] est créée côté serveur ; [empreinte] et [empreinteReleve] : ce qui est
     *  parti (cf. [GeoNatureUpload.empreinteOccurrence]). */
    suspend fun occurrenceCreee(obsId: String, empreinte: String? = null, empreinteReleve: String? = null)

    /** Des modifications sont transmises en mise à jour : par observation, les nouvelles empreintes
     *  de son occurrence et de son relevé (une composante nulle ne change pas). */
    suspend fun empreintesTransmises(empreintes: Map<String, Pair<String?, String?>>) {}

    suspend fun occurrenceIncertaine(obsId: String, idReleve: Int)

    /** PRÉ-MARQUAGE d'un groupe, AVANT son premier POST d'occurrence : tant qu'aucune réponse n'est
     *  revenue, chacune a peut-être été créée côté serveur. Une seule écriture pour tout le groupe. */
    suspend fun occurrencesATenter(obsIds: List<String>, idReleve: Int)

    /** Rejet FRANC du serveur (4xx) : rien n'a été créé, l'incertitude du pré-marquage est levée. */
    suspend fun occurrenceEchecNet(obsId: String)

    /** PRÉ-MARQUAGE du RELEVÉ d'un groupe, AVANT son POST de création. */
    suspend fun releveATenter(obsIds: List<String>)

    /** Rejet FRANC du serveur sur la création du relevé : rien n'a été créé. */
    suspend fun releveEchecNet(obsIds: List<String>)
}

/** Envoi Occtax (relevé, occurrences, dénombrements, médias) et upload des médias Suivis. */
class GeoNatureUpload(
    private val auth: GeoNatureAuth,
    private val clients: ClientsGeoNature,
    private val nomenclatures: NomenclatureCache,
    private val geoNatureSync: GeoNatureSync,
    private val relevesOrphelins: RelevesOrphelins,
    private val systeme: FileSystem = FileSystem.SYSTEM,
    /** Le dossier des copies locales des médias, où retrouver un média par son nom (cf. [fichierMedia]). */
    private val dossierMedias: Path? = null,
    private val horloge: () -> Long = ::maintenantMillis,
    /** Le nom scientifique (`lb_nom`) d'un cd_nom, pour les défauts des médias du web. */
    private val nomScientifique: (Int) -> String? = { null },
) {

    private val verrou = SynchronizedObject()
    private val nomenclatureCache = mutableMapOf<String, Int>()

    /** Cache de l'id_table_location de pr_occtax.cor_counting_occtax (résolu au premier appel). */
    private var idTableLocationCountingCache: Long? = null

    /** Vide les caches mémoire : ids de nomenclatures résolus et id_table_location de
     *  cor_counting_occtax. À appeler quand l'URL, l'identifiant ou le mot de passe changent —
     *  sinon on réutilise des ids issus de l'instance précédente (les id_nomenclature et
     *  id_table_location varient d'un GeoNature à l'autre). */
    fun invaliderCaches() {
        synchronized(verrou) {
            nomenclatureCache.clear()
            idTableLocationCountingCache = null
        }
    }

    suspend fun envoyer(
        sortie: Sortie,
        config: GeoNatureConfig,
        /** Marquage persistant au fil de l'eau (cf. [MarqueurEnvoiOcctax]) ; null = aucun (tests) —
         *  l'appelant se contente alors du résultat. */
        marqueur: MarqueurEnvoiOcctax? = null,
    ): EnvoiResult = withContext(Dispatchers.IO) {
        val base = config.urlServeur.trim().trimEnd('/')
        val (token, idRole, cookies) = auth.loginAvecCookies(base, config.login, config.motDePasse)
            ?: throw GNErreur.AuthEchouee(401)

        // On inclut aussi les « relevés sans espèce » (placeholders sans cd_nom) : ils créent un
        // relevé VIDE (aucune occurrence) côté serveur. Les autres obs sans cd_nom (espèce non
        // résolue) restent exclues de l'envoi.
        val obsValides = sortie.observations.filter { it.cdNom != null || it.releveSansEspece }
        if (obsValides.isEmpty()) throw GNErreur.AucuneObservationCompatible()

        // Ré-envoi après succès PARTIEL : les obs déjà créées côté serveur lors d'un envoi
        // précédent (envoyeeServeur, persisté par l'appelant) ne repartent PAS — anti-doublon.
        val nbDejaEnvoyees = obsValides.count { it.envoyeeServeur }
        val aEnvoyer = obsValides.filterNot { it.envoyeeServeur }
        // Transmises, puis modifiées (« Continuer la saisie » après un envoi partiel) : leur
        // modification part en mise à jour (audit du 21 octobre, C3 ; décision de l'utilisateur).
        val occurrencesModifiees = obsValides.filter { it.envoyeeServeur && it.cdNom != null && it.empreinteEnvoi != null && it.empreinteEnvoi != empreinteOccurrence(it) }
        val relevesModifies = obsValides.filter { it.envoyeeServeur && it.empreinteReleveEnvoi != null && it.empreinteReleveEnvoi != empreinteReleve(it) }
        if (aEnvoyer.isEmpty() && occurrencesModifiees.isEmpty() && relevesModifies.isEmpty()) {
            // Tout avait déjà été créé (l'échec précédent portait sur le marquage final ou sur des
            // fichiers média disparus) : rien à poster, l'appelant peut clôturer.
            return@withContext EnvoiResult(nbCrees = 0, nbTotal = 0, premierIdReleve = null, nbDejaEnvoyees = nbDejaEnvoyees)
        }

        // Le jeu de données d'un relevé qui n'en porte pas (enregistré avant que le jeu soit figé à
        // sa création) : celui des Paramètres. Chaque groupe est contrôlé plus bas.
        val datasetParDefaut = config.idDataset.trim().toIntOrNull()?.takeIf { it > 0 }

        // Nettoyage des relevés orphelins d'envois PRÉCÉDENTS (rollback DELETE qui avait échoué —
        // typiquement coupure réseau totale) : on retente leur suppression maintenant que le
        // réseau répond. Au mieux : un échec les laisse mémorisés pour le prochain envoi.
        relevesOrphelins.liste(base).forEach { idOrphelin ->
            // On vérifie qu'il est TOUJOURS vide avant de le supprimer : un relevé mémorisé ici peut
            // avoir été réutilisé entre-temps (retrouvé par son uuid lors d'un ré-envoi) et contenir
            // désormais des occurrences transmises — le DELETE les emporterait. Vérification
            // impossible (réseau, 5xx) ⇒ on ne touche à rien et on le garde pour le prochain envoi.
            when (etatReleveOrphelin(base, token, cookies, idOrphelin)) {
                EtatOrphelin.VIDE ->
                    if (tenterSupprimerReleve(base, token, cookies, idOrphelin)) relevesOrphelins.retirer(base, idOrphelin)
                // Plus vide, ou déjà absent du serveur : il n'y a plus d'orphelin à nettoyer.
                EtatOrphelin.PEUPLE, EtatOrphelin.ABSENT -> relevesOrphelins.retirer(base, idOrphelin)
                EtatOrphelin.INCONNU -> {}
            }
        }

        var nbCrees = 0
        var premierIdReleve: Int? = null
        var derniereErreur: String? = null
        var dernierCodeErreur = 0
        // Ids des observations créées PENDANT cet envoi — persistés par l'appelant pour qu'un
        // ré-envoi partiel ne les re-poste pas.
        val obsCreesIds = mutableListOf<String>()
        // Occurrences dont le POST est resté sans réponse (→ vérification par uuid au prochain envoi).
        val obsIncertaines = mutableListOf<Pair<String, Int>>()
        var mediasOK = 0
        var mediasKO = 0
        var mediaErreurMsg: String? = null
        // Relevés créés côté serveur pour lesquels le POST de l'occurrence a échoué.
        val relevesOrphelinsEnvoi = mutableListOf<Int>()

        val tables = mutableMapOf<String, Map<String, Int>>()
        // Types à résoudre : dérivés du registre unique (+ TYPE_MEDIA), même source que la synchro.
        val typesVoulus = OcctaxFieldsConfig.mnemoniques() + "TYPE_MEDIA"

        // Si le cache est vide, on tente une synchro automatique.
        if (!nomenclatures.estDisponible) {
            try {
                geoNatureSync.synchroniserNomenclatures(config)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        for (type in typesVoulus) {
            val duCache = nomenclatures.get(type)
            tables[type] = if (duCache.isNotEmpty()) {
                duCache.associate { normaliserLabelNomenclature(it.label) to it.id }
            } else {
                resolverNomenclatures(base, token, cookies, type)
            }
        }

        // Regroupement explicite par releveId : la saisie multi-taxons stampille toutes ses obs
        // avec un même UUID, qui devient ici un seul relevé GeoNature avec N occurrences. Pour les
        // obs sans releveId (saisie mono-taxon, import GPX), obs.id sert de clé : chaque obs forme
        // son propre groupe d'un élément.
        val groupes = aEnvoyer.groupBy { it.releveId ?: it.id }

        for ((cleGroupe, groupeBrut) in groupes) {
            // ANTI-DOUBLON (audit 2026-08-27) : une occurrence dont le POST précédent est resté
            // INCERTAIN (coupure après émission) existe peut-être déjà dans le relevé de l'époque.
            // On vérifie par son uuid client AVANT de créer un nouveau relevé : présente → acquise
            // (pas de re-POST) ; absente ou relevé supprimé → repostée normalement ; vérification
            // impossible (réseau) → on NE re-POSTe PAS, elle reste incertaine pour le prochain envoi.
            val groupe = mutableListOf<Observation>()
            for (obs in groupeBrut) {
                val idAncien = obs.idReleveIncertain
                if (idAncien == null) {
                    groupe.add(obs)
                    continue
                }
                when (occurrencePresente(base, token, cookies, idAncien, obs.uuidOccurrence)) {
                    true -> {
                        nbCrees++
                        obsCreesIds.add(obs.id)
                        marqueur?.occurrenceCreee(obs.id, empreinteOccurrence(obs), empreinteReleve(obs))
                    }
                    false -> groupe.add(obs)
                    null -> {
                        dernierCodeErreur = 0
                        derniereErreur = "Vérification anti-doublon impossible pour « ${obs.espece} » " +
                            "(relevé #$idAncien) — retentée au prochain envoi"
                    }
                }
            }
            if (groupe.isEmpty()) continue
            // Le jeu de données est obligatoire, comme sur le web : sans lui, le relevé attend qu'on
            // le choisisse dans « Détails du relevé ». Un jeu absent du serveur courant partirait en
            // FK invalide → 500 opaque : le groupe est sauté avec un message clair.
            val datasetGroupe = groupe.first().idDatasetReleve?.takeIf { it > 0 } ?: datasetParDefaut
            if (datasetGroupe == null) {
                dernierCodeErreur = 0
                derniereErreur = "Jeu de données manquant : choisissez-le dans « Détails du relevé »."
                continue
            }
            if (!config.datasetAcceptablePourEnvoi(datasetGroupe)) {
                dernierCodeErreur = 0
                derniereErreur = "Jeu de données $datasetGroupe (choisi dans « Détails du relevé ») introuvable sur ce serveur."
                continue
            }
            val champsJeu = { objet: String, idJeu: Int? -> ChampsAdditionnels.defsPour(config.additionalFieldsOcctaxJson, objet, idJeu) }
            val uuidGrp = uuidReleveGroupe(cleGroupe)
            val releveEnvoye = releveAEnvoyer(groupe, cleGroupe, datasetGroupe, config, tables, idRole, base, token, cookies)
            val properties = releveEnvoye.properties
            val geometry = releveEnvoye.geometry
            val dateMin = releveEnvoye.dateMin
            val empreinteReleveGroupe = empreinteReleve(groupe.first())

            // ANTI-DOUBLON DU RELEVÉ : si sa création a DÉJÀ été tentée (POST émis lors d'un envoi
            // précédent, puis processus tué ou réseau coupé), le relevé existe peut-être côté
            // serveur. On interroge son uuid AVANT d'en créer un second. Décisif pour un RELEVÉ SANS
            // ESPÈCE, où le relevé EST la donnée (R1-M1).
            var idReleveReutilise: Int? = null
            // Des espèces de ce relevé déjà transmises (envoi partiel) : il existe, les nouvelles le
            // rejoignent au lieu d'en ouvrir un second (audit du 21 octobre, M33).
            val releveDejaCree = obsValides.any { (it.releveId ?: it.id) == cleGroupe && (it.envoyeeServeur || it.releveTente) }
            if (groupe.any { it.releveTente } || releveDejaCree) {
                when (val existant = relevePresent(base, token, cookies, uuidGrp)) {
                    is ReleveExistant.Trouve -> {
                        idReleveReutilise = existant.idReleve
                        // Il va recevoir des occurrences : il ne doit plus figurer parmi les relevés
                        // vides à supprimer, sinon une purge ultérieure l'emporterait avec son contenu.
                        relevesOrphelins.retirer(base, existant.idReleve)
                    }
                    ReleveExistant.Absent -> {}
                    // Vérification IMPOSSIBLE : on ne crée RIEN. Re-POSTer à l'aveugle est exactement
                    // ce que l'invariant anti-doublon du projet interdit.
                    ReleveExistant.Indeterminable -> {
                        derniereErreur = "Impossible de vérifier si le relevé a déjà été créé " +
                            "lors d'un envoi interrompu — rien n'a été renvoyé, réessayez."
                        continue
                    }
                }
            }

            val idReleve: Int
            if (idReleveReutilise != null) {
                idReleve = idReleveReutilise
            } else {
                // PRÉ-MARQUAGE du relevé, AVANT le POST : à partir d'ici il a peut-être été créé.
                marqueur?.releveATenter(groupe.map { it.id })
                val corps = JSONObject()
                    .put("geometry", geometry)
                    .put("status", "to_sync")
                    .put("properties", properties)
                    .toString()
                // Coupure réseau (délai, connexion fermée…) pendant le POST du relevé : aucun id
                // serveur connu → on enregistre l'erreur et on passe au groupe suivant, la sortie
                // reste ré-envoyable telle quelle.
                val reponse = try {
                    clients.postJson("$base/api/occtax/OCCTAX/only/releve", corps, token, cookies, timeoutMs = 30_000)
                } catch (e: IOException) {
                    dernierCodeErreur = 0
                    derniereErreur = "Réseau interrompu pendant la création du relevé (${e.message ?: e::class.simpleName})"
                    continue
                }
                val code1 = reponse.status.value
                if (code1 !in 200..299) {
                    val corpsErreur = lireCorps(reponse)
                    dernierCodeErreur = code1
                    derniereErreur = parseErreur(code1, corpsErreur)
                    // Rejet FRANC : aucun relevé créé, on lève le pré-marquage (sinon chaque envoi
                    // ultérieur paierait une vérification par uuid pour rien).
                    if (code1 in 400..499) marqueur?.releveEchecNet(groupe.map { it.id })
                    continue
                }
                val resp1 = try {
                    JSONObject(lireCorps(reponse).orEmpty())
                } catch (_: Exception) {
                    derniereErreur = "Réponse relevé non-JSON"
                    continue
                }
                // Extraction de l'id robuste (identique iOS).
                val idExtrait = resp1.optInt("id", -1).takeIf { it > 0 }
                    ?: resp1.optJSONObject("properties")?.optInt("id_releve_occtax", -1)?.takeIf { it > 0 }
                    ?: resp1.optInt("id_releve_occtax", -1).takeIf { it > 0 }
                if (idExtrait == null) {
                    val cles = try {
                        resp1.keys().asSequence().toList().sorted().joinToString(",")
                    } catch (_: Exception) { "?" }
                    derniereErreur = "id absent de la réponse (clés: $cles)"
                    continue
                }
                idReleve = idExtrait
            }
            if (premierIdReleve == null) premierIdReleve = idReleve

            // Relevé SANS ESPÈCE : le groupe ne porte que des placeholders (aucune occurrence
            // réelle). Le relevé vient d'être créé avec un t_occurrences_occtax VIDE et on le
            // CONSERVE tel quel — rien à poster, PAS de rollback « relevé orphelin ». Le ou les
            // placeholders sont marqués créés pour ne pas repartir au prochain envoi.
            val occurrencesReelles = groupe.filter { it.cdNom != null }
            if (occurrencesReelles.isEmpty()) {
                nbCrees++
                groupe.forEach {
                    obsCreesIds.add(it.id)
                    marqueur?.occurrenceCreee(it.id, empreinteReleve = empreinteReleveGroupe)
                }
                continue
            }

            // PRÉ-MARQUAGE du groupe, AVANT tout POST d'occurrence : à partir d'ici, chacune a
            // peut-être été créée côté serveur tant qu'aucune réponse n'est revenue (audit
            // 2026-09-14, R1-C1). Une seule écriture pour tout le groupe ; levé au fil des succès
            // par occurrenceCreee et sur rejet franc par occurrenceEchecNet.
            marqueur?.occurrencesATenter(occurrencesReelles.map { it.id }, idReleve)

            var nbReussisGroupe = 0
            // Ids des médias gn_commons uploadés pour ce groupe : sert au rollback si toutes les
            // occurrences du groupe échouent (comme gn_mobile_occtax).
            val mediaIdsGroupe = mutableListOf<Int>()
            for (obs in occurrencesReelles) {
                // ── Étape A : upload des médias par dénombrement AVANT le POST de l'occurrence ──
                // (chaque média uploadé renvoie un JSON Media à inclure dans le dénombrement).
                val mediasParCounting = mutableListOf<List<JSONObject>>()
                // Ids gn_commons uploadés pour CETTE obs : rejoignent mediaIdsGroupe seulement si
                // l'occurrence est bien postée — sinon rollback immédiat.
                val mediaIdsObs = mutableListOf<Int>()
                // true si AU MOINS un média a échoué pour une cause RÉSEAU/SERVEUR : on ne poste pas
                // l'occurrence (un média Occtax ne peut être rattaché qu'à la création du
                // dénombrement — l'envoyer « sans » perdrait la photo définitivement) ; l'obs entière
                // reste à envoyer. Un fichier LOCAL disparu ne compte pas : retenter ne le fera pas
                // réapparaître, on envoie l'occurrence sans lui.
                var mediaEchecReessayable = false
                val urisCounting0 = obs.mediaUrisCounting0
                val urisAdditionnels = obs.denombrementsAdditionnels.map { it.mediaUris }
                val besoinMedia = urisCounting0.isNotEmpty() || urisAdditionnels.any { it.isNotEmpty() }
                if (besoinMedia) {
                    val (idTableLoc, errTable) = resoudreIdTableLocationCounting(base, token, cookies)
                    val typesMediaOcctax = tables["TYPE_MEDIA"] ?: emptyMap()
                    // Les défauts du web (counting.component.ts, defaultsMedia) : les observateurs du
                    // relevé pour auteur, « année_mois_jour_lb_nom_cd_nom » pour titre (le premier
                    // espace du nom en « _ »), « lb_nom observé le jour/mois/année » pour description.
                    val auteur = groupe.first().observateursReleveNoms.filter { it.isNotBlank() }
                        .ifEmpty { listOf(config.nomObservateurParDefaut) }
                        .joinToString(", ")
                    val jour = Instant.fromEpochMilliseconds(dateMin).toLocalDateTime(TimeZone.currentSystemDefault()).date
                    val lbNom = obs.cdNom?.let(nomScientifique) ?: obs.espece
                    val titreMedia = "${jour.year}_${jour.month.number}_${jour.day}_${lbNom.replaceFirst(' ', '_')}_${obs.cdNom ?: ""}"
                    val descriptionMedia = "$lbNom observé le ${jour.day}/${jour.month.number}/${jour.year}"
                    suspend fun upload(uris: List<String>): List<JSONObject> = uris.mapNotNull { uri ->
                        if (idTableLoc == null) {
                            mediasKO++
                            if (mediaErreurMsg == null) mediaErreurMsg = errTable
                            mediaEchecReessayable = true // id_table_location = échec réseau/serveur
                            return@mapNotNull null
                        }
                        // MIME déduit de l'extension du fichier copié localement.
                        val mimeHint = typeMime(cheminDepuisUri(uri).orEmpty()) ?: "image/jpeg"
                        val idTypeMedia = idTypeMediaPour(typesMediaOcctax, mimeHint)
                        val (json, err) = uploaderMediaFile(
                            base = base, token = token, cookies = cookies,
                            mediaPath = uri, author = auteur, titre = titreMedia, description = descriptionMedia,
                            idTableLocation = idTableLoc, idTypeMedia = idTypeMedia,
                        )
                        if (json != null) {
                            mediasOK++
                            // Capture l'id_media pour un rollback éventuel.
                            val idMedia = json.optInt("id_media", -1).takeIf { it > 0 }
                                ?: json.optInt("id", -1).takeIf { it > 0 }
                            if (idMedia != null) mediaIdsObs.add(idMedia)
                            json
                        } else {
                            mediasKO++
                            if (mediaErreurMsg == null) mediaErreurMsg = err
                            if (!erreurMediaLocale(err)) mediaEchecReessayable = true
                            null
                        }
                    }
                    mediasParCounting.add(upload(urisCounting0))
                    for (uris in urisAdditionnels) mediasParCounting.add(upload(uris))
                }
                if (mediaEchecReessayable) {
                    // Photo non transmise pour cause réseau/serveur : l'obs N'est PAS envoyée (photo
                    // et occurrence retentées ensemble au prochain envoi). Rollback des médias de
                    // cette obs déjà partis pour ne pas laisser d'orphelins gn_commons.
                    mediasOK -= mediaIdsObs.count { tenterSupprimerMedia(base, token, cookies, it) }
                    dernierCodeErreur = 0
                    derniereErreur = "photo non transmise (${mediaErreurMsg ?: "erreur réseau"}) — " +
                        "« ${obs.espece} » sera retentée au prochain envoi"
                    continue
                }
                mediaIdsGroupe.addAll(mediaIdsObs)

                // ── Étape B : construction et POST du JSON occurrence, médias inclus ──
                val occ = buildOccurrence(
                    obs, tables, mediasParCounting,
                    ChampsAdditionnelsEnvoi(
                        champsJeu(AdditionalFieldsObject.OCCURRENCE, datasetGroupe),
                        champsJeu(AdditionalFieldsObject.COUNTING, datasetGroupe),
                    ),
                    nomenclatures.defautPour("METH_OBS"),
                )
                // Coupure réseau pendant le POST de l'occurrence : comptée comme un échec ordinaire
                // (code -1) pour que le rollback du relevé en fin de groupe s'applique.
                val code2 = try {
                    val reponse = clients.postJson(
                        "$base/api/occtax/OCCTAX/releve/$idReleve/occurrence", occ.toString(), token, cookies, timeoutMs = 30_000,
                    )
                    val code = reponse.status.value
                    if (code > 0 && code !in 200..299) {
                        dernierCodeErreur = code
                        derniereErreur = parseErreur(code, lireCorps(reponse))
                    }
                    code
                } catch (e: IOException) {
                    dernierCodeErreur = 0
                    derniereErreur = "Réseau interrompu pendant l'envoi de l'occurrence (${e.message ?: e::class.simpleName})"
                    // Impossible de savoir si la requête a atteint le serveur : INCERTAIN — persisté
                    // tout de suite, le prochain envoi vérifiera par uuid client.
                    obsIncertaines.add(obs.id to idReleve)
                    marqueur?.occurrenceIncertaine(obs.id, idReleve)
                    -1
                }
                if (code2 in 200..299) {
                    nbCrees++
                    nbReussisGroupe++
                    obsCreesIds.add(obs.id)
                    // Acquis persisté IMMÉDIATEMENT (avant la suite du lot) : survit à une annulation
                    // ou à la mort du processus pendant les occurrences suivantes. Avec ce qui est
                    // parti : une modification ultérieure partira en mise à jour (C3).
                    marqueur?.occurrenceCreee(obs.id, empreinteOccurrence(obs), empreinteReleveGroupe)
                } else if (code2 > 0) {
                    // SEUL un 4xx vaut rejet franc : rien n'a été créé, on lève l'incertitude posée par
                    // le pré-marquage. Un 5xx ne dit PAS que rien n'a été créé (un proxy peut rendre
                    // 502 ou 504 sur une requête que GeoNature a honorée), et 408 ou 429 non plus : on
                    // GARDE alors l'incertitude, le prochain envoi tranchera par uuid.
                    if (estRejetFranc(code2)) {
                        marqueur?.occurrenceEchecNet(obs.id)
                    } else {
                        obsIncertaines.add(obs.id to idReleve)
                    }
                }
            }

            // Rollback : si aucune occurrence n'a abouti, on supprime le relevé côté serveur (et les
            // médias déjà uploadés pour ce groupe) pour ne pas laisser un relevé vide dans Occtax,
            // comme gn_mobile_occtax. Si le DELETE échoue lui-même, le relevé est signalé orphelin.
            if (nbReussisGroupe == 0) {
                mediasOK -= mediaIdsGroupe.count { tenterSupprimerMedia(base, token, cookies, it) }
                // Le rollback ne vise QUE le relevé que CET envoi vient de créer et qui est resté
                // vide. Un relevé RÉUTILISÉ (retrouvé par son uuid) contient déjà les occurrences
                // transmises la fois d'avant : GeoNature les supprimerait EN CASCADE avec lui.
                if (idReleveReutilise == null) {
                    val releveSupprime = tenterSupprimerReleve(base, token, cookies, idReleve)
                    if (!releveSupprime) {
                        relevesOrphelinsEnvoi.add(idReleve)
                        // Mémorisé pour suppression automatique au prochain envoi.
                        relevesOrphelins.ajouter(base, listOf(idReleve))
                    }
                }
            }
        }

        // ── Mises à jour de ce qui a changé depuis sa transmission (audit du 21 octobre, C3) ──
        var nbMisesAJour = 0
        var nbMisesAJourEnEchec = 0
        val clesModifiees = (occurrencesModifiees + relevesModifies).map { it.releveId ?: it.id }.distinct()
        for (cle in clesModifiees) {
            val obsDuReleve = obsValides.filter { (it.releveId ?: it.id) == cle }
            val occurrencesDuReleve = occurrencesModifiees.filter { (it.releveId ?: it.id) == cle }
            val releveModifie = relevesModifies.any { (it.releveId ?: it.id) == cle }
            val aFaire = occurrencesDuReleve.size + (if (releveModifie) 1 else 0)
            fun echec(message: String) {
                dernierCodeErreur = 0
                derniereErreur = message
                nbMisesAJourEnEchec += aFaire
            }
            val idReleve = when (val existant = relevePresent(base, token, cookies, uuidReleveGroupe(cle))) {
                is ReleveExistant.Trouve -> existant.idReleve
                ReleveExistant.Absent -> { echec("Relevé supprimé sur GeoNature : sa modification n'est pas transmise."); continue }
                ReleveExistant.Indeterminable -> { echec("Relevé introuvable sur GeoNature pour le moment : sa modification sera retentée."); continue }
            }
            val datasetGroupe = obsDuReleve.first().idDatasetReleve?.takeIf { it > 0 } ?: datasetParDefaut
            if (datasetGroupe == null || !config.datasetAcceptablePourEnvoi(datasetGroupe)) {
                echec("Jeu de données du relevé introuvable : sa modification n'est pas transmise.")
                continue
            }
            val transmises = mutableMapOf<String, Pair<String?, String?>>()
            if (releveModifie) {
                val releve = releveAEnvoyer(obsDuReleve, cle, datasetGroupe, config, tables, idRole, base, token, cookies, pourMiseAJour = true)
                val corps = JSONObject().put("geometry", releve.geometry).put("properties", releve.properties).toString()
                val code = try {
                    clients.postJson("$base/api/occtax/OCCTAX/only/releve/$idReleve", corps, token, cookies, timeoutMs = 30_000).let { r ->
                        r.status.value.also { if (it !in 200..299) derniereErreur = parseErreur(it, lireCorps(r)) }
                    }
                } catch (e: IOException) {
                    derniereErreur = "Réseau interrompu pendant la mise à jour du relevé (${e.message ?: e::class.simpleName})"
                    -1
                }
                if (code in 200..299) {
                    nbMisesAJour++
                    val empreinte = empreinteReleve(obsDuReleve.first())
                    obsDuReleve.forEach { transmises[it.id] = null to empreinte }
                } else {
                    nbMisesAJourEnEchec++
                    dernierCodeErreur = code.coerceAtLeast(0)
                }
            }
            if (occurrencesDuReleve.isNotEmpty()) {
                val detail = lireReleve(base, token, cookies, idReleve)
                if (detail == null) {
                    dernierCodeErreur = 0
                    derniereErreur = "Relevé illisible sur GeoNature : la modification des espèces sera retentée."
                    nbMisesAJourEnEchec += occurrencesDuReleve.size
                } else {
                    for (obs in occurrencesDuReleve) {
                        val surLeServeur = occurrenceParUuid(detail, obs.uuidOccurrence)
                        val idOccurrence = surLeServeur?.optInt("id_occurrence_occtax", -1)?.takeIf { it > 0 }
                        if (surLeServeur == null || idOccurrence == null) {
                            nbMisesAJourEnEchec++
                            dernierCodeErreur = 0
                            derniereErreur = "« ${obs.espece} » supprimée sur GeoNature : sa modification n'est pas transmise."
                            continue
                        }
                        val corps = occurrencePourMiseAJour(
                            obs, surLeServeur, tables,
                            ChampsAdditionnelsEnvoi(
                                ChampsAdditionnels.defsPour(config.additionalFieldsOcctaxJson, AdditionalFieldsObject.OCCURRENCE, datasetGroupe),
                                ChampsAdditionnels.defsPour(config.additionalFieldsOcctaxJson, AdditionalFieldsObject.COUNTING, datasetGroupe),
                            ),
                            nomenclatures.defautPour("METH_OBS"),
                        )
                        val code = try {
                            clients.postJson("$base/api/occtax/OCCTAX/occurrence/$idOccurrence", corps.toString(), token, cookies, timeoutMs = 30_000).let { r ->
                                r.status.value.also { if (it !in 200..299) derniereErreur = parseErreur(it, lireCorps(r)) }
                            }
                        } catch (e: IOException) {
                            derniereErreur = "Réseau interrompu pendant la mise à jour de « ${obs.espece} » (${e.message ?: e::class.simpleName})"
                            -1
                        }
                        if (code in 200..299) {
                            nbMisesAJour++
                            transmises[obs.id] = empreinteOccurrence(obs) to transmises[obs.id]?.second
                        } else {
                            nbMisesAJourEnEchec++
                            dernierCodeErreur = code.coerceAtLeast(0)
                        }
                    }
                }
            }
            if (transmises.isNotEmpty()) marqueur?.empreintesTransmises(transmises)
        }

        if (nbCrees == 0 && nbMisesAJour == 0) {
            val msg = buildString {
                append(derniereErreur ?: "Aucun relevé créé")
                if (relevesOrphelinsEnvoi.isNotEmpty()) {
                    append(" — ${relevesOrphelinsEnvoi.size} relevé(s) vide(s) créé(s) côté GeoNature ")
                    append("(id : ${relevesOrphelinsEnvoi.joinToString(", ")}) ; ")
                    append("leur suppression sera retentée automatiquement au prochain envoi.")
                }
            }
            throw GNErreur.EnvoiEchoue(dernierCodeErreur, msg)
        }
        EnvoiResult(
            nbCrees = nbCrees, nbTotal = aEnvoyer.size, premierIdReleve = premierIdReleve,
            mediasOK = mediasOK, mediasKO = mediasKO, mediaErreurMsg = mediaErreurMsg,
            relevesOrphelins = relevesOrphelinsEnvoi.toList(),
            obsCreesIds = obsCreesIds.toList(),
            nbDejaEnvoyees = nbDejaEnvoyees,
            messageDerniereErreur = derniereErreur,
            obsIncertaines = obsIncertaines.toList(),
            nbMisesAJour = nbMisesAJour,
            nbMisesAJourEnEchec = nbMisesAJourEnEchec,
        )
    }

    /** Le relevé d'un groupe, prêt à partir. [dateMin] sert aussi aux titres des médias. */
    private class ReleveAEnvoyer(val properties: JSONObject, val geometry: JSONObject, val dateMin: Long)

    /** Le relevé du [groupe] (ses observations d'un même `releveId`) : ses `properties`, sa géométrie
     *  et les altitudes du MNT quand aucune n'est saisie. [pourMiseAJour] (audit du 21 octobre, C3) :
     *  ni `t_occurrences_occtax` — le serveur remplacerait la liste, et supprimerait les occurrences
     *  absentes —, ni `id_digitiser`, qui reste celui de la création ; un champ vidé part à null. */
    private suspend fun releveAEnvoyer(
        groupe: List<Observation>,
        cleGroupe: String,
        datasetGroupe: Int,
        config: GeoNatureConfig,
        tables: Map<String, Map<String, Int>>,
        idRole: Int?,
        base: String,
        token: String?,
        cookies: String,
        pourMiseAJour: Boolean = false,
    ): ReleveAEnvoyer {
        val premiere = groupe.first()
        // Plage temporelle : par défaut, couvre toutes les obs du groupe (dates de saisie). Si
        // « Détails du relevé » porte une date explicite (dateDebutReleve), elle PRIME (saisie a
        // posteriori) ; dateFinReleve borne la fin, sinon = début.
        val debutOverride = premiere.dateDebutReleve
        val dateMin = debutOverride ?: groupe.minOf { it.date }
        val dateMax = premiere.dateFinReleve ?: debutOverride ?: groupe.maxOf { it.date }
        // Les champs additionnels du formulaire du relevé, typés comme le web : ceux de son jeu de
        // données. Portés par la 1re obs du groupe (toutes les obs d'un même releveId en ont la copie).
        val champsReleve = ChampsAdditionnels.defsPour(config.additionalFieldsOcctaxJson, AdditionalFieldsObject.RELEVE, datasetGroupe)
        // Sans observateur réglé, celui que « Détails » propose : l'observateur par défaut des
        // Paramètres, au départ l'utilisateur connecté, comme le web.
        val observateursGroupe = premiere.observateursReleveIds.filter { it > 0 }
            .ifEmpty { listOfNotNull(premiere.observateurReleveId?.takeIf { it > 0 }) }
            .ifEmpty { listOfNotNull(config.observateurDefautId.trim().toIntOrNull()?.takeIf { it > 0 } ?: idRole) }
        // Un champ facultatif vide : omis à la création, null à la mise à jour (sinon le serveur
        // garderait l'ancienne valeur).
        fun JSONObject.champ(cle: String, valeur: Any?) {
            if (valeur != null) put(cle, valeur) else if (pourMiseAJour) put(cle, JSONObject.NULL)
        }
        val properties = JSONObject().apply {
            // Identité client STABLE du relevé (colonne unique_id_sinp_grp) : permet de
            // retrouver un relevé créé dont la réponse s'est perdue, au lieu d'en créer un
            // second (audit 2026-09-14, R1-M1).
            put("unique_id_sinp_grp", uuidReleveGroupe(cleGroupe))
            put("id_dataset", datasetGroupe)
            put("date_min", DateEnvoi.date(dateMin))
            put("date_max", DateEnvoi.date(dateMax))
            // Les heures saisies dans « Détails », comme sur le web : facultatives, nulles sinon.
            champ("hour_min", premiere.heureDebutReleve)
            champ("hour_max", premiere.heureFinReleve)
            put("additional_fields", ChampsAdditionnels.versJson(premiere.additionalFieldsReleve, champsReleve))
            put("meta_device_entry", "mobile")
            if (!pourMiseAJour) {
                put("t_occurrences_occtax", JSONArray())
                if (idRole != null) put("id_digitiser", idRole)
            }
            // Observateurs en texte libre (`OCCTAX.observers_txt`) : leur texte seul, comme le web,
            // le nom complet de l'observateur par défaut à défaut ; sinon la liste.
            if (config.occtaxObservateursEnTexte) {
                put("observers_txt", premiere.observateursReleveTxt?.takeIf { it.isNotBlank() } ?: config.nomObservateurParDefaut)
            } else if (observateursGroupe.isNotEmpty()) {
                put("observers", JSONArray().apply { observateursGroupe.forEach { put(it) } })
            }
            champ("comment", premiere.commentReleve?.takeIf { it.isNotBlank() })
            champ("cd_hab", premiere.cdHabReleve?.takeIf { it > 0 })
            premiere.typGrpReleve?.takeIf { it.isNotEmpty() }?.let { code ->
                resolverIdNomenclature(code, "TYP_GRP", emptyMap(), tables)?.let { put("id_nomenclature_grp_typ", it) }
            }
            // Champs relevé supplémentaires pilotés par form_fields : entiers (altitude,
            // profondeur, précision), textes (lieu, technique de regroupement) et nomenclatures
            // (nature de l'objet géo, technique de collecte). Omis si vides ou illisibles.
            val extraReleve = premiere.champsReleveExtra
            extraReleve["altitude_min"]?.trim()?.toIntOrNull()?.let { put("altitude_min", it) }
            extraReleve["altitude_max"]?.trim()?.toIntOrNull()?.let { put("altitude_max", it) }
            champ("depth_min", extraReleve["depth_min"]?.trim()?.toIntOrNull())
            champ("depth_max", extraReleve["depth_max"]?.trim()?.toIntOrNull())
            champ("precision", extraReleve["precision"]?.trim()?.toIntOrNull())
            champ("place_name", extraReleve["place_name"]?.trim()?.takeIf { it.isNotEmpty() })
            champ("grp_method", extraReleve["grp_method"]?.trim()?.takeIf { it.isNotEmpty() })
            extraReleve["geo_object_nature"]?.takeIf { it.isNotEmpty() }?.let { code ->
                resolverIdNomenclature(code, "NAT_OBJ_GEO", emptyMap(), tables)
                    ?.let { put("id_nomenclature_geo_object_nature", it) }
            }
            extraReleve["tech_collect"]?.takeIf { it.isNotEmpty() }?.let { code ->
                resolverIdNomenclature(code, "TECHNIQUE_OBS", emptyMap(), tables)
                    ?.let { put("id_nomenclature_tech_collect_campanule", it) }
            }
        }
        // Coordonnées du relevé : la première obs du groupe (toutes identiques pour un lot
        // multi-taxons).
        val geometry = construireGeometrie(
            type = premiere.geometryType,
            coordsJson = premiere.geometryCoordsJson,
            lat = premiere.latitude,
            lon = premiere.longitude,
        )
        // Les altitudes du MNT du serveur (`POST /geo/altitude`), que le web pose dès le
        // pointage : ici à l'envoi (rien n'est téléchargé pendant la saisie, décision du
        // 20 octobre), quand aucune n'est saisie. Best-effort : sans réponse, elles restent vides.
        if (!properties.has("altitude_min") && !properties.has("altitude_max")) {
            altitudesMntDe(clients, base, token, cookies, geometry)?.let { (min, max) ->
                min?.let { properties.put("altitude_min", it) }
                max?.let { properties.put("altitude_max", it) }
            }
        }
        return ReleveAEnvoyer(properties, geometry, dateMin)
    }

    /** Le relevé serveur [idReleve] (`GET /releve/<id>`) : ses `properties`, occurrences et
     *  dénombrements compris ; null s'il ne se lit pas. */
    internal suspend fun lireReleve(base: String, token: String?, cookies: String, idReleve: Int): JSONObject? = try {
        val reponse = clients.get("$base/api/occtax/OCCTAX/releve/$idReleve", token, cookies, timeoutMs = 30_000)
        if (reponse.status.value !in 200..299) {
            null
        } else {
            val racine = JSONObject(reponse.bodyAsText())
            val releve = racine.optJSONObject("releve") ?: racine
            releve.optJSONObject("properties") ?: releve
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Résultat d'une recherche de relevé par uuid client. [Indeterminable] impose de NE RIEN
     *  créer : c'est l'invariant anti-doublon du projet (jamais de POST quand la vérification
     *  échoue). */
    internal sealed class ReleveExistant {
        data class Trouve(val idReleve: Int) : ReleveExistant()
        data object Absent : ReleveExistant()
        data object Indeterminable : ReleveExistant()
    }

    /**
     * Le relevé portant [uuid] (`unique_id_sinp_grp`) existe-t-il déjà sur le serveur ?
     * `GET /api/occtax/OCCTAX/releves?unique_id_sinp_grp=<uuid>&limit=1`.
     *
     * Le point délicat est le serveur qui IGNORE le filtre (version ancienne, colonne absente) : il
     * renvoie alors des relevés quelconques, et conclure « absent » ferait créer un doublon. D'où la
     * règle en trois temps — l'uuid figure dans la réponse ⇒ TROUVÉ ; la réponse est VIDE ⇒ ABSENT ;
     * la réponse est NON VIDE mais sans notre uuid ⇒ le filtre a vraisemblablement été ignoré ⇒
     * INDÉTERMINABLE, on ne crée rien.
     */
    internal suspend fun relevePresent(base: String, token: String?, cookies: String, uuid: String): ReleveExistant {
        if (uuid.isBlank()) return ReleveExistant.Indeterminable
        return try {
            val reponse = clients.get("$base/api/occtax/OCCTAX/releves?unique_id_sinp_grp=$uuid&limit=1", token, cookies, timeoutMs = 30_000)
            when (val code = reponse.status.value) {
                in 200..299 -> {
                    val txt = reponse.bodyAsText()
                    // Réponse d'une forme inconnue : on ne sait PAS si le relevé existe. La traiter
                    // comme une liste vide le déclarerait « absent » et en ferait créer un second.
                    val features = extraireFeaturesReleves(txt) ?: run {
                        Journal.w(TAG, "Vérification relevé : réponse illisible (${txt.take(120)})")
                        return ReleveExistant.Indeterminable
                    }
                    val mien = features.firstOrNull {
                        it.optJSONObject("properties")?.optString("unique_id_sinp_grp").equals(uuid, ignoreCase = true)
                    }
                    when {
                        mien != null -> idDepuisFeatureReleve(mien)?.let { ReleveExistant.Trouve(it) } ?: ReleveExistant.Indeterminable
                        features.isEmpty() -> ReleveExistant.Absent
                        else -> {
                            Journal.w(TAG, "Vérification relevé : le serveur semble ignorer le filtre unique_id_sinp_grp")
                            ReleveExistant.Indeterminable
                        }
                    }
                }
                // 404 = route absente sur cette instance : on ne peut rien conclure.
                else -> {
                    Journal.w(TAG, "Vérification relevé : HTTP $code")
                    ReleveExistant.Indeterminable
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ReleveExistant.Indeterminable
        }
    }

    /** État d'un relevé mémorisé comme orphelin, au moment de le purger. */
    internal enum class EtatOrphelin { VIDE, PEUPLE, ABSENT, INCONNU }

    /** Le relevé [idReleve] est-il toujours vide ? `GET /releve/<id>` : son dump porte un
     *  `id_occurrence_occtax` par occurrence. Toute réponse non reconnue rend [EtatOrphelin.INCONNU] —
     *  on ne supprime jamais sur un doute. */
    internal suspend fun etatReleveOrphelin(base: String, token: String?, cookies: String, idReleve: Int): EtatOrphelin =
        try {
            val reponse = clients.get("$base/api/occtax/OCCTAX/releve/$idReleve", token, cookies, timeoutMs = 30_000)
            when (reponse.status.value) {
                in 200..299 -> {
                    val txt = reponse.bodyAsText()
                    when {
                        txt.contains("id_occurrence_occtax") -> EtatOrphelin.PEUPLE
                        txt.contains("id_releve_occtax") -> EtatOrphelin.VIDE
                        else -> EtatOrphelin.INCONNU
                    }
                }
                404 -> EtatOrphelin.ABSENT
                else -> EtatOrphelin.INCONNU
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            EtatOrphelin.INCONNU
        }

    /**
     * L'occurrence portant [uuid] (`unique_id_occurence_occtax`) existe-t-elle dans le relevé
     * serveur [idReleve] ? `GET /api/occtax/OCCTAX/releve/<id>` (le dump du relevé inclut ses
     * occurrences). true / false (absente, ou relevé supprimé → 404) / null = vérification
     * impossible (réseau, 5xx) — l'appelant ne doit alors PAS re-POSTer.
     */
    internal suspend fun occurrencePresente(base: String, token: String?, cookies: String, idReleve: Int, uuid: String): Boolean? {
        if (uuid.isBlank()) return false
        return try {
            val reponse = clients.get("$base/api/occtax/OCCTAX/releve/$idReleve", token, cookies, timeoutMs = 30_000)
            when (val code = reponse.status.value) {
                in 200..299 -> {
                    val txt = reponse.bodyAsText()
                    when {
                        txt.contains(uuid, ignoreCase = true) -> true
                        // Le corps doit RESSEMBLER au dump d'un relevé pour qu'une absence d'uuid vaille
                        // « occurrence absente ». Sinon (page d'erreur d'un proxy, réponse tronquée), on
                        // ne conclut rien : re-POSTer sur une réponse non comprise fabrique un doublon.
                        txt.contains("id_releve_occtax") || txt.contains("occurrences") -> false
                        else -> {
                            Journal.w(TAG, "Vérification occurrence : réponse non reconnue (${txt.take(120)})")
                            null
                        }
                    }
                }
                404 -> false
                else -> {
                    Journal.w(TAG, "Vérification occurrence : HTTP $code")
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** L'id_table_location de pr_occtax.cor_counting_occtax, mis en cache au premier appel :
     *  `/api/gn_commons/get_id_table_location/pr_occtax.cor_counting_occtax` renvoie un entier
     *  nu, entre guillemets, ou un JSON (cf. gn_mobile_core). */
    private suspend fun resoudreIdTableLocationCounting(base: String, token: String?, cookies: String): Pair<Long?, String?> {
        synchronized(verrou) { idTableLocationCountingCache }?.let { return Pair(it, null) }
        return try {
            val reponse = clients.get("$base/api/gn_commons/get_id_table_location/pr_occtax.cor_counting_occtax", token, cookies, timeoutMs = 10_000)
            val code = reponse.status.value
            if (code != 200) {
                Journal.e(TAG_MEDIA, "get_id_table_location HTTP $code : ${lireCorps(reponse)?.take(200)}")
                return Pair(null, "get_id_table_location HTTP $code")
            }
            val texte = reponse.bodyAsText().trim()
            val id = idTableLocationDepuis(texte)
            if (id != null) {
                synchronized(verrou) { idTableLocationCountingCache = id }
                Journal.i(TAG_MEDIA, "id_table_location pour cor_counting_occtax = $id")
                Pair(id, null)
            } else {
                Journal.e(TAG_MEDIA, "Réponse get_id_table_location non parsable : $texte")
                Pair(null, "Réponse get_id_table_location non parsable : ${texte.take(100)}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Journal.e(TAG_MEDIA, "Exception get_id_table_location", e)
            Pair(null, "Exception : ${e.message}")
        }
    }

    /** Ce que le serveur détient déjà pour un objet : les titres (`title_fr`) de ses médias, et
     *  leurs noms de fichier (celui de `media_path`, sans le préfixe `<id_media>_` que le serveur
     *  ajoute). */
    data class MediasPresents(val titres: Set<String>, val fichiers: Set<String>)

    /** Les médias DÉJÀ attachés à [uuidAttachedRow] côté serveur
     *  (`GET /api/gn_commons/medias/<uuid_attached_row>`). null = vérification impossible (réseau,
     *  route absente, HTTP ≠ 2xx) : l'appelant uploadera sans filtrer — un doublon de photo reste
     *  préférable à des photos de terrain qui n'arrivent jamais. */
    internal suspend fun mediasDejaPresents(base: String, token: String?, cookies: String, uuidAttachedRow: String): MediasPresents? {
        if (uuidAttachedRow.isBlank()) return MediasPresents(emptySet(), emptySet())
        return try {
            val reponse = clients.get("$base/api/gn_commons/medias/$uuidAttachedRow", token, cookies, timeoutMs = 20_000)
            when (val code = reponse.status.value) {
                in 200..299 -> {
                    val arr = JSONArray(reponse.bodyAsText())
                    val objets = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                    MediasPresents(
                        titres = objets.mapNotNull { it.optString("title_fr").takeIf { t -> t.isNotEmpty() } }.toSet(),
                        fichiers = objets.mapNotNull { o ->
                            o.optString("media_path").substringAfterLast('/').replaceFirst(Regex("^\\d+_"), "").takeIf { it.isNotEmpty() }
                        }.toSet(),
                    )
                }
                else -> {
                    Journal.w(TAG_MEDIA, "Médias déjà présents : HTTP $code")
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Résultat d'un upload de médias Suivis. [transmis] = les chemins RÉELLEMENT acquis par le
     *  serveur : l'appelant les persiste pour qu'un « Réessayer » ne les renvoie pas une seconde
     *  fois. */
    data class ResultatMediasMonitoring(
        val ok: Boolean,
        val message: String?,
        val transmis: List<String>,
    )

    /** Upload des médias d'un objet Suivis : l'id_table_location vient du `schema_dot_table` du
     *  champ médias de chacun (ex. `gn_monitoring.t_base_visits`), et `uuid_attached_row` rattache
     *  chaque média à l'objet créé (gn_commons.t_medias). Titre, auteur et description sont ceux
     *  saisis, comme sur le web ; un média d'une saisie plus ancienne, sans titre, prend
     *  [titreParDefaut] et son nom de fichier, [auteurParDefaut] et la description d'alors. Diffère
     *  du flux OCCTAX, qui embarque le média dans le dénombrement. */
    suspend fun uploaderMediaMonitoring(
        config: GeoNatureConfig,
        medias: List<MediaSuivi>,
        uuidAttachedRow: String,
        titreParDefaut: String,
        auteurParDefaut: String,
        /** true = RÉ-ESSAI : on demande d'abord au serveur ce qu'il détient déjà pour cet objet.
         *  Inutile (et un aller-retour de trop) sur un premier envoi. */
        reconcilier: Boolean = false,
    ): ResultatMediasMonitoring = withContext(Dispatchers.IO) {
        if (medias.isEmpty()) return@withContext ResultatMediasMonitoring(true, null, emptyList())
        val mediaPaths = medias.map { it.chemin }
        val base = config.urlServeur.trim().trimEnd('/')
        val (token, _, cookies) = auth.loginAvecCookies(base, config.login, config.motDePasse)
            ?: return@withContext ResultatMediasMonitoring(false, "Authentification GeoNature échouée", emptyList())
        // id_table_location résolu une seule fois par table cible.
        val tables = mutableMapOf<String, Pair<Long?, String?>>()
        suspend fun tablePour(schemaDotTable: String?): Pair<Long?, String?> {
            val table = schemaDotTable?.takeIf { it.isNotEmpty() } ?: return Pair(null, "schema_dot_table absent du champ médias")
            return tables.getOrPut(table) { resoudreIdTableLocationPour(base, token, cookies, table) }
        }
        // id_nomenclature_media_type : NOT NULL côté gn_commons.t_medias — son ABSENCE faisait
        // partir le POST en 500 opaque (« photo en échec », bug terrain ; l'objet, lui, était
        // créé). Résolution identique au flux OCCTAX : cache TYPE_MEDIA, repli sur la résolution
        // en direct si le cache est vide.
        val typesMedia = nomenclatures.get("TYPE_MEDIA")
            .associate { normaliserLabelNomenclature(it.label) to it.id }
            .ifEmpty { resolverNomenclatures(base, token, cookies, "TYPE_MEDIA") }
        // Le titre d'un média : le sien, sinon celui que l'appli posait d'office, DÉTERMINISTE par
        // fichier.
        fun titreDe(m: MediaSuivi) = m.titre.trim().ifEmpty { titreMedia(titreParDefaut, m.chemin) }
        // RÉCONCILIATION AVEC LE SERVEUR (audit 2026-09-14, R1-M3). Mémoriser les fichiers dont la
        // réponse est revenue ne suffit pas : un POST parti, reçu et traité par le serveur, dont la
        // RÉPONSE se perd n'est pas mémorisé — et repart au « Réessayer », en double (constat
        // terrain 2026-09-16). On demande donc au serveur ce qu'il détient DÉJÀ pour cet objet, et
        // on écarte ces fichiers : par leur nom de fichier, que le serveur garde, ou pour un titre
        // posé d'office, par ce titre.
        val dejaLa = if (reconcilier) mediasDejaPresents(base, token, cookies, uuidAttachedRow) else MediasPresents(emptySet(), emptySet())
        fun present(m: MediaSuivi): Boolean {
            val d = dejaLa ?: return false
            val nom = nomFichierServeur(m.chemin)
            return (nom.isNotEmpty() && nom in d.fichiers) || (m.titre.isBlank() && titreDe(m) in d.titres)
        }
        val aEnvoyer = medias.filterNot(::present)
        if (aEnvoyer.isEmpty()) return@withContext ResultatMediasMonitoring(true, null, mediaPaths)
        // Les fichiers déjà détenus par le serveur comptent comme transmis : c'est ce que
        // l'appelant persiste, et c'est vrai.
        val transmis = mutableListOf<String>()
        transmis.addAll(mediaPaths.filterNot { p -> aEnvoyer.any { it.chemin == p } })
        var premiereErreur: String? = null
        for (m in aEnvoyer) {
            val (idTableLoc, errTable) = tablePour(m.schemaDotTable)
            if (idTableLoc == null) {
                if (premiereErreur == null) premiereErreur = errTable ?: "id_table_location introuvable pour ${m.schemaDotTable}"
                continue
            }
            val mimeHint = typeMime(cheminDepuisUri(m.chemin).orEmpty()) ?: "image/jpeg"
            val ancien = m.titre.isBlank()
            val (json, err) = uploaderMediaFile(
                base = base, token = token, cookies = cookies,
                mediaPath = m.chemin,
                author = if (ancien) auteurParDefaut else m.auteur.trim(),
                titre = titreDe(m),
                idTableLocation = idTableLoc, idTypeMedia = idTypeMediaPour(typesMedia, mimeHint),
                uuidAttachedRow = uuidAttachedRow,
                description = if (ancien) DESCRIPTION_MEDIA_ANCIENNE else m.description.trim(),
            )
            // On MÉMORISE chaque fichier réellement transmis (audit 2026-09-14, R1-M3).
            if (json != null) transmis.add(m.chemin) else if (premiereErreur == null) premiereErreur = err
        }
        if (transmis.size == mediaPaths.size) {
            ResultatMediasMonitoring(true, null, transmis)
        } else {
            ResultatMediasMonitoring(
                false,
                "${transmis.size}/${mediaPaths.size} média(s) envoyé(s)" + (premiereErreur?.let { " — $it" } ?: ""),
                transmis,
            )
        }
    }

    /** L'id_table_location d'un `schema.table` quelconque (ex. `gn_monitoring.t_base_visits`). Pas de
     *  cache : un même appareil envoie des saisies de plusieurs types d'objet. */
    private suspend fun resoudreIdTableLocationPour(base: String, token: String?, cookies: String, schemaDotTable: String): Pair<Long?, String?> =
        try {
            val reponse = clients.get("$base/api/gn_commons/get_id_table_location/$schemaDotTable", token, cookies, timeoutMs = 10_000)
            val code = reponse.status.value
            if (code != 200) {
                Pair(null, "get_id_table_location/$schemaDotTable HTTP $code : ${lireCorps(reponse)?.take(200) ?: ""}")
            } else {
                val texte = reponse.bodyAsText().trim()
                val id = idTableLocationDepuis(texte)
                if (id != null) Pair(id, null) else Pair(null, "Réponse get_id_table_location non parsable : ${texte.take(100)}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Pair(null, "Exception get_id_table_location : ${e.message}")
        }

    /** Upload multipart d'un fichier média vers `/api/gn_commons/media` (singulier). Renvoie le JSON
     *  Media du serveur (à inclure dans le dénombrement), ou null et le message d'échec. Le fichier
     *  est lu en entier : HttpURLConnection, faute de longueur fixe, gardait déjà tout le corps en
     *  mémoire sous Android. */
    private suspend fun uploaderMediaFile(
        base: String, token: String?, cookies: String,
        mediaPath: String, author: String, titre: String,
        idTableLocation: Long, idTypeMedia: Int?,
        /** Optionnel : rattache le média à un objet par son uuid (`uuid_attached_row`). */
        uuidAttachedRow: String? = null,
        description: String = DESCRIPTION_MEDIA_ANCIENNE,
    ): Pair<JSONObject?, String?> {
        val chemin = cheminDepuisUri(mediaPath)
        if (chemin == null) {
            Journal.e(TAG_MEDIA, "URI fichier invalide : $mediaPath")
            return Pair(null, "URI fichier invalide : $mediaPath")
        }
        val fichier = fichierMedia(systeme, chemin, dossierMedias)
        val octets = try {
            if (!systeme.exists(fichier)) null else systeme.read(fichier) { readByteArray() }
        } catch (_: Exception) { null }
        if (octets == null) {
            Journal.e(TAG_MEDIA, "Fichier inaccessible : $chemin")
            return Pair(null, "Fichier inaccessible : $chemin")
        }
        val mime = typeMime(fichier.name) ?: "image/jpeg"
        return try {
            val reponse = clients.ecriture.post(urlAbsolue("$base/api/gn_commons/media")) {
                header(HttpHeaders.Accept, "application/json")
                if (token != null) bearerAuth(token)
                if (cookies.isNotEmpty()) header(HttpHeaders.Cookie, cookies)
                timeout {
                    connectTimeoutMillis = 30_000
                    socketTimeoutMillis = 60_000
                }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("id_table_location", idTableLocation.toString())
                            idTypeMedia?.let { append("id_nomenclature_media_type", it.toString()) }
                            uuidAttachedRow?.takeIf { it.isNotEmpty() }?.let { append("uuid_attached_row", it) }
                            append("author", author)
                            append("title_fr", titre)
                            append("description_fr", description)
                            append("file", octets, Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"${fichier.name}\"")
                                append(HttpHeaders.ContentType, mime)
                            })
                        },
                        boundary = "----GeoMysBoundary${horloge()}",
                    )
                )
            }
            val code = reponse.status.value
            if (code in 200..299) {
                Journal.i(TAG_MEDIA, "POST /api/gn_commons/media → $code OK (${octets.size} octets)")
                Pair(try { JSONObject(reponse.bodyAsText()) } catch (_: Exception) { JSONObject() }, null)
            } else {
                val corps = lireCorps(reponse)
                Journal.e(TAG_MEDIA, "POST /api/gn_commons/media → HTTP $code : $corps")
                Pair(null, "HTTP $code : ${corps?.take(200) ?: "(corps vide)"}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Journal.e(TAG_MEDIA, "Exception upload média", e)
            Pair(null, "Exception : ${e.message}")
        }
    }

    /** DELETE /api/occtax/OCCTAX/releve/{id} — rollback d'un relevé créé dont aucune occurrence n'a
     *  abouti. true si le serveur a accepté (2xx, ou 404 = déjà absent), comme gn_mobile_occtax. */
    private suspend fun tenterSupprimerReleve(base: String, token: String?, cookies: String, idReleve: Int): Boolean =
        tenterSupprimer("$base/api/occtax/OCCTAX/releve/$idReleve", token, cookies)

    /** DELETE /api/gn_commons/media/{id} — rollback d'un média uploadé quand le relevé ou
     *  l'occurrence parent est lui-même retiré. Évite des médias orphelins côté serveur. */
    private suspend fun tenterSupprimerMedia(base: String, token: String?, cookies: String, idMedia: Int): Boolean =
        tenterSupprimer("$base/api/gn_commons/media/$idMedia", token, cookies)

    private suspend fun tenterSupprimer(url: String, token: String?, cookies: String): Boolean = try {
        val code = clients.delete(url, token, cookies, timeoutMs = 15_000).status.value
        code in 200..299 || code == 404
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /** Les nomenclatures d'un type, lues en direct : libellé normalisé → id_nomenclature. Les
     *  libellés sont stables d'une instance GeoNature à l'autre, contrairement aux cd_nomenclature. */
    private suspend fun resolverNomenclatures(base: String, token: String?, cookies: String, type: String): Map<String, Int> {
        val enCache = synchronized(verrou) {
            nomenclatureCache.entries.filter { it.key.startsWith("$type:") }.associate { it.key.removePrefix("$type:") to it.value }
        }
        if (enCache.isNotEmpty()) return enCache

        suspend fun lire(url: String): String? = try {
            val reponse = clients.get(url, token, cookies, timeoutMs = 10_000)
            if (reponse.status.value == 200) reponse.bodyAsText() else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { null }

        return try {
            // Étape 1 : id_type par /nomenclature/{TYPE}.
            val texte1 = lire("$base/api/nomenclatures/nomenclature/$type") ?: return emptyMap()
            val typeObj = try {
                JSONObject(texte1)
            } catch (_: Exception) {
                try { JSONArray(texte1).optJSONObject(0) } catch (_: Exception) { null }
            }
            val idType = typeObj?.optInt("id_type", -1)?.takeIf { it > 0 }

            // Étape 2 : les valeurs — plusieurs formes d'URL possibles.
            val urls = buildList {
                if (idType != null) {
                    add("$base/api/nomenclatures/nomenclature?id_type=$idType&limit=200")
                    add("$base/api/nomenclatures/nomenclatures?id_type=$idType&limit=200")
                }
                add("$base/api/nomenclatures/nomenclature?code_nomenclature_type=$type&limit=200")
                add("$base/api/nomenclatures/nomenclatures?code_type=$type&limit=200")
            }
            var table = emptyMap<String, Int>()
            for (url in urls) {
                val texte2 = lire(url) ?: continue
                val tableau = extraireTableauNomenclatures(texte2) ?: continue
                table = tableLibelles(tableau)
                if (table.isNotEmpty()) break
            }
            synchronized(verrou) { table.forEach { (libelle, id) -> nomenclatureCache["$type:$libelle"] = id } }
            table
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { emptyMap() }
    }

    companion object {
        private const val TAG = "GeoNatureUpload"
        private const val TAG_MEDIA = "GeoMysMedia"

        /** Le corps d'une réponse, ou null s'il ne se lit pas. */
        private suspend fun lireCorps(reponse: io.ktor.client.statement.HttpResponse): String? =
            try { reponse.bodyAsText() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

        /** UUID client DÉTERMINISTE du relevé d'un groupe, dérivé de la clé de regroupement locale
         *  (`releveId`, ou l'id de l'observation pour une saisie mono-taxon). Déterministe et non
         *  stocké : la même saisie produit toujours le même uuid, donc on peut interroger le serveur
         *  à son sujet même après un redémarrage. UUID v3, comme `UUID.nameUUIDFromBytes`. */
        internal fun uuidReleveGroupe(cleGroupe: String): String =
            uuidDepuisOctets("geomys-releve:$cleGroupe".encodeToByteArray())

        /** La liste des Feature d'une réponse `/releves`, sous les formes connues :
         *  `{items:{features:[…]}}` (GeoNature actuel), `{items:[…]}`, `{features:[…]}` ou un tableau
         *  nu. **null = réponse non reconnue**, à ne surtout pas confondre avec une liste vide :
         *  l'appelant en conclurait que le relevé n'existe pas et le recréerait. */
        internal fun extraireFeaturesReleves(txt: String): List<JSONObject>? {
            val arr: JSONArray = try {
                val racine = try { JSONObject(txt) } catch (_: Exception) { null }
                when {
                    racine == null -> JSONArray(txt)
                    racine.optJSONObject("items")?.optJSONArray("features") != null ->
                        racine.getJSONObject("items").getJSONArray("features")
                    racine.optJSONArray("items") != null -> racine.getJSONArray("items")
                    racine.optJSONArray("features") != null -> racine.getJSONArray("features")
                    else -> return null
                }
            } catch (_: Exception) { return null }
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }

        /** 4xx « francs » : le serveur a REFUSÉ, rien n'a été créé. 408 (délai) et 429 (trop de
         *  requêtes) en sont exclus — la requête a pu aboutir malgré la réponse —, comme les 5xx,
         *  qu'un proxy peut rendre sur un POST pourtant honoré par GeoNature. */
        internal fun estRejetFranc(code: Int): Boolean = code in 400..499 && code != 408 && code != 429

        private fun idDepuisFeatureReleve(f: JSONObject): Int? =
            f.optJSONObject("properties")?.optInt("id_releve_occtax", -1)?.takeIf { it > 0 }
                ?: f.optInt("id", -1).takeIf { it > 0 }

        /** true si l'échec média est LOCAL et définitif (fichier disparu, URI invalide) : retenter
         *  ne fera pas réapparaître le fichier, on ne bloque donc pas l'occurrence pour ça. Tout
         *  autre échec (HTTP, réseau, id_table_location) est ré-essayable. */
        private fun erreurMediaLocale(err: String?): Boolean =
            err != null && (err.startsWith("URI fichier invalide") || err.startsWith("Fichier inaccessible"))

        /** Les altitudes min et max du MNT du serveur sous [geometry] (`POST /geo/altitude`, avec la
         *  Feature, comme le web), null hors ligne ou en erreur. */
        private suspend fun altitudesMntDe(
            clients: ClientsGeoNature,
            base: String,
            token: String?,
            cookies: String,
            geometry: JSONObject,
        ): Pair<Int?, Int?>? = try {
            val feature = JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", JSONObject())
            val reponse = clients.postJson("$base/api/geo/altitude", feature.toString(), token, cookies, timeoutMs = 15_000)
            if (reponse.status.value != 200) {
                null
            } else {
                val alt = JSONObject(reponse.bodyAsText())
                val min = alt.opt("altitude_min")?.toString()?.toDoubleOrNull()?.toInt()
                val max = alt.opt("altitude_max")?.toString()?.toDoubleOrNull()?.toInt()
                if (min == null && max == null) null else min to max
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        /** L'id_table_location d'une réponse `get_id_table_location` : entier nu, entre guillemets,
         *  ou JSON `{id_table_location}`. */
        private fun idTableLocationDepuis(texte: String): Long? =
            (texte.trim('"').toLongOrNull()
                ?: try { JSONObject(texte).optLong("id_table_location", -1L).takeIf { it > 0 } } catch (_: Exception) { null })
                ?.takeIf { it > 0 }

        /** L'empreinte de l'occurrence de [obs] : les champs qu'en lit [buildOccurrence], médias à part.
         *  Différente de celle enregistrée à sa transmission, l'espèce a été modifiée depuis. */
        fun empreinteOccurrence(obs: Observation): String = empreinte(
            listOf(
                obs.cdNom, obs.espece, obs.notes, obs.nombre, obs.nombreMax, obs.sexe, obs.stadeVie, obs.objDenbr,
                obs.typDenbr, obs.statutObs, obs.techniqueObs, obs.etaBio, obs.comportement, obs.statutBio,
                obs.methDetermin, obs.preuveExist, obs.naturalite, obs.determinateur,
                trie(obs.champsOccExtra), trie(obs.additionalFieldsOccurrence), trie(obs.additionalFieldsCounting0),
            ) + obs.denombrementsAdditionnels.map { d ->
                listOf(d.nombreMin, d.nombreMax, d.sexe, d.stadeVie, d.objDenbr, d.typDenbr, trie(d.additionalFields))
            },
        )

        /** L'empreinte du relevé de [obs] : ce qu'en lit releveAEnvoyer (et la géométrie). */
        fun empreinteReleve(obs: Observation): String = empreinte(
            listOf(
                obs.idDatasetReleve, obs.dateDebutReleve, obs.dateFinReleve, obs.heureDebutReleve, obs.heureFinReleve,
                trie(obs.additionalFieldsReleve), obs.observateursReleveIds, obs.observateurReleveId, obs.observateursReleveTxt,
                obs.commentReleve, obs.cdHabReleve, obs.typGrpReleve, trie(obs.champsReleveExtra),
                obs.geometryType, obs.geometryCoordsJson, obs.latitude, obs.longitude,
            ),
        )

        private fun trie(m: Map<String, String>): List<Pair<String, String>> = m.entries.sortedBy { it.key }.map { it.key to it.value }

        private fun empreinte(valeurs: List<Any?>): String = valeurs.toString().encodeUtf8().sha256().hex().take(16)

        /** L'occurrence portant [uuid] (`unique_id_occurence_occtax`) dans le relevé lu [releve]. */
        internal fun occurrenceParUuid(releve: JSONObject, uuid: String): JSONObject? {
            val occurrences = releve.optJSONArray("t_occurrences_occtax") ?: return null
            return (0 until occurrences.length()).mapNotNull { occurrences.optJSONObject(it) }
                .firstOrNull { it.optString("unique_id_occurence_occtax").equals(uuid, ignoreCase = true) }
        }

        /** Le corps de la mise à jour de l'occurrence [surLeServeur] (`POST /occurrence/<id>`), comme le
         *  web : chaque dénombrement déjà sur le serveur porte son `id_counting_occtax` (dans l'ordre
         *  de création), et le serveur met à jour la même ligne ; un nouveau n'en porte pas, il est
         *  créé ; un dénombrement retiré dans l'appli est supprimé. Ni médias (le serveur garde ceux de
         *  chaque dénombrement), ni identifiant SINP de dénombrement, que le serveur refuse de changer. */
        internal fun occurrencePourMiseAJour(
            obs: Observation,
            surLeServeur: JSONObject,
            nomenclatures: Map<String, Map<String, Int>>,
            champs: ChampsAdditionnelsEnvoi,
            defautTechniqueObs: String?,
        ): JSONObject {
            val corps = buildOccurrence(obs, nomenclatures, emptyList(), champs, defautTechniqueObs, pourMiseAJour = true)
            val existants = surLeServeur.optJSONArray("cor_counting_occtax")?.let { tableau ->
                (0 until tableau.length()).mapNotNull { tableau.optJSONObject(it)?.optInt("id_counting_occtax", -1)?.takeIf { id -> id > 0 } }.sorted()
            }.orEmpty()
            val countings = corps.getJSONArray("cor_counting_occtax")
            for (i in 0 until countings.length()) {
                existants.getOrNull(i)?.let { countings.getJSONObject(i).put("id_counting_occtax", it) }
            }
            return corps
        }

        internal fun buildOccurrence(
            obs: Observation,
            nomenclatures: Map<String, Map<String, Int>>,
            mediasParCounting: List<List<JSONObject>> = emptyList(),
            /** Les champs additionnels du formulaire de l'occurrence et des dénombrements. */
            champs: ChampsAdditionnelsEnvoi = ChampsAdditionnelsEnvoi(),
            /** Le défaut du serveur de la technique d'observation (METH_OBS), pour une espèce dont la
             *  caractérisation n'a pas été ouverte ; null : la base applique le sien. */
            defautTechniqueObs: String? = null,
            /** Une mise à jour (C3) : un texte vidé part à null, sans quoi le serveur garderait l'ancien. */
            pourMiseAJour: Boolean = false,
        ): JSONObject {
            // Dénombrement n° 0 — issu des champs à plat de l'Observation (mono-taxon ou 1er multi).
            val counting0 = buildCounting(
                nombreMin = obs.nombre,
                nombreMax = obs.nombreMax ?: obs.nombre,
                sexe = obs.sexe,
                stadeVie = obs.stadeVie,
                objDenbr = obs.objDenbr,
                typDenbr = obs.typDenbr,
                medias = mediasParCounting.getOrNull(0) ?: emptyList(),
                additionalFields = obs.additionalFieldsCounting0,
                nomenclatures = nomenclatures,
                champs = champs.denombrement,
            )
            val countings = JSONArray().put(counting0)
            // Dénombrements supplémentaires (mode multi-taxon).
            obs.denombrementsAdditionnels.forEachIndexed { i, d ->
                countings.put(buildCounting(
                    nombreMin = d.nombreMin,
                    nombreMax = d.nombreMax,
                    sexe = d.sexe,
                    stadeVie = d.stadeVie,
                    objDenbr = d.objDenbr,
                    typDenbr = d.typDenbr,
                    medias = mediasParCounting.getOrNull(i + 1) ?: emptyList(),
                    additionalFields = d.additionalFields,
                    nomenclatures = nomenclatures,
                    champs = champs.denombrement,
                ))
            }

            return JSONObject().apply {
                put("cd_nom", obs.cdNom!!)
                put("nom_cite", obs.espece)
                // Identité client stable de l'occurrence (acceptée par OccurrenceSchema) : permet de
                // retrouver une occurrence créée sans réponse avant de la re-POSTer (anti-doublon).
                put("unique_id_occurence_occtax", obs.uuidOccurrence)
                if (obs.notes.isNotEmpty()) put("comment", obs.notes) else if (pourMiseAJour) put("comment", JSONObject.NULL)
                put("cor_counting_occtax", countings)
                // Champs de nomenclature de l'occurrence : pilotés par le registre unique. METH_OBS
                // vide prend le défaut du serveur, comme le formulaire web ; GeoMys-Android envoyait
                // « Vu ».
                val valeursOcc = mapOf(
                    "statutObs" to obs.statutObs,
                    "techniqueObs" to (obs.techniqueObs ?: defautTechniqueObs.orEmpty()),
                    "etaBio" to obs.etaBio,
                    "comportement" to obs.comportement,
                    "statutBio" to obs.statutBio,
                    "methDetermin" to obs.methDetermin,
                    "preuveExist" to obs.preuveExist,
                    "naturalite" to obs.naturalite,
                    // Champs occurrence pilotés par form_fields (dans champsOccExtra par clé form_fields).
                    "sourceStatus" to (obs.champsOccExtra["source_status"] ?: ""),
                    "blurring" to (obs.champsOccExtra["blurring"] ?: ""),
                )
                OcctaxFieldsConfig.REGISTRE
                    .filter { it.niveau == OcctaxFieldsConfig.Niveau.INFORMATION }
                    .forEach { f ->
                        valeursOcc[f.svKey]?.let { code ->
                            resolverIdNomenclature(code, f.code, f.uploadLabels, nomenclatures)?.let { put(f.uploadKey, it) }
                        }
                    }
                fun texte(cle: String, valeur: String?) {
                    if (!valeur.isNullOrEmpty()) put(cle, valeur) else if (pourMiseAJour) put(cle, JSONObject.NULL)
                }
                texte("determiner", obs.determinateur)
                // Preuves (texte) pilotées par form_fields.
                texte("digital_proof", obs.champsOccExtra["digital_proof"])
                texte("non_digital_proof", obs.champsOccExtra["non_digital_proof"])
                put("additional_fields", ChampsAdditionnels.versJson(obs.additionalFieldsOccurrence, champs.occurrence))
            }
        }

        /** Le GeoJSON `geometry` d'un relevé (ou d'une station OccHab).
         *  - `type == "Point"` ou null → [lon, lat] depuis lat/lon.
         *  - `type == "LineString"` → coordsJson est `[[lon,lat], …]` (≥ 2 sommets).
         *  - `type == "Polygon"` → coordsJson est `[[lon,lat], …]` (anneau extérieur), refermé si le
         *    dernier sommet diffère du premier et enveloppé dans le tableau supplémentaire du format
         *    GeoJSON Polygon. [trousJson] (OccHab) = anneaux INTÉRIEURS `[[[lon,lat], …], …]` ajoutés
         *    après l'extérieur et refermés de même : sans eux, le ré-envoi d'une station à trou
         *    (dessinée sous QGIS) les SUPPRIMAIT côté serveur (bug 2026-08-31). */
        internal fun construireGeometrie(
            type: String?,
            coordsJson: String?,
            lat: Double,
            lon: Double,
            trousJson: String? = null,
        ): JSONObject {
            if (type.isNullOrEmpty() || type == "Point" || coordsJson.isNullOrEmpty()) {
                return JSONObject().put("type", "Point").put("coordinates", JSONArray().put(lon).put(lat))
            }
            val coords = JSONArray(coordsJson) // [[lon,lat], …]
            return when (type) {
                "LineString" -> JSONObject().put("type", "LineString").put("coordinates", coords)
                "Polygon" -> {
                    // coordinates = [ anneau extérieur, trou, … ], chaque anneau refermé.
                    val anneaux = JSONArray().put(fermerAnneau(coords))
                    try {
                        if (!trousJson.isNullOrEmpty()) {
                            val trous = JSONArray(trousJson)
                            for (i in 0 until trous.length()) {
                                val trou = trous.optJSONArray(i) ?: continue
                                if (trou.length() >= 3) anneaux.put(fermerAnneau(trou))
                            }
                        }
                    } catch (_: Exception) {
                        // Trous illisibles : on envoie au moins l'anneau extérieur (jamais de
                        // géométrie invalide) — le polygone reste complet, seul le trou est perdu.
                    }
                    JSONObject().put("type", "Polygon").put("coordinates", anneaux)
                }
                else -> JSONObject().put("type", "Point").put("coordinates", JSONArray().put(lon).put(lat))
            }
        }

        /** Referme un anneau GeoJSON (dernier sommet = premier) s'il ne l'est pas déjà. L'éditeur
         *  travaille sur des sommets DISTINCTS, le point de fermeture n'est ajouté qu'à l'envoi. */
        private fun fermerAnneau(anneau: JSONArray): JSONArray {
            if (anneau.length() >= 3) {
                val premier = anneau.getJSONArray(0)
                val dernier = anneau.getJSONArray(anneau.length() - 1)
                if (premier.getDouble(0) != dernier.getDouble(0) || premier.getDouble(1) != dernier.getDouble(1)) {
                    anneau.put(JSONArray().put(premier.getDouble(0)).put(premier.getDouble(1)))
                }
            }
            return anneau
        }

        /** Les champs additionnels du formulaire d'une occurrence et de ses dénombrements, pour le jeu
         *  de données de son relevé. */
        internal class ChampsAdditionnelsEnvoi(
            val occurrence: List<AdditionalFieldDef> = emptyList(),
            val denombrement: List<AdditionalFieldDef> = emptyList(),
        )

        private fun buildCounting(
            nombreMin: Int,
            nombreMax: Int,
            sexe: String?,
            stadeVie: String?,
            objDenbr: String?,
            typDenbr: String?,
            medias: List<JSONObject> = emptyList(),
            additionalFields: Map<String, String> = emptyMap(),
            nomenclatures: Map<String, Map<String, Int>>,
            champs: List<AdditionalFieldDef> = emptyList(),
        ): JSONObject = JSONObject().apply {
            put("count_min", nombreMin)
            put("count_max", nombreMax.coerceAtLeast(nombreMin))
            // Champs de nomenclature du dénombrement : pilotés par le registre unique.
            val valeursCnt = mapOf("sexe" to sexe, "stadeVie" to stadeVie, "objDenbr" to objDenbr, "typDenbr" to typDenbr)
            OcctaxFieldsConfig.REGISTRE
                .filter { it.niveau == OcctaxFieldsConfig.Niveau.COUNTING }
                .forEach { f ->
                    valeursCnt[f.svKey]?.let { code ->
                        resolverIdNomenclature(code, f.code, f.uploadLabels, nomenclatures)?.let { put(f.uploadKey, it) }
                    }
                }
            // medias[] : objets renvoyés par POST /api/gn_commons/media — le serveur les lie au
            // dénombrement par id_media (comme gn_mobile_occtax).
            if (medias.isNotEmpty()) {
                val arr = JSONArray()
                medias.forEach { arr.put(it) }
                put("medias", arr)
            }
            put("additional_fields", ChampsAdditionnels.versJson(additionalFields, champs))
        }

        /** Normalise un libellé de nomenclature pour un appariement ROBUSTE entre instances : minuscule,
         *  sans accents, et sans un éventuel préfixe de code « NN - » (certaines instances préfixent
         *  le libellé par un code — ex. l'ANA pour le comportement atlas : « 30 - Nidification… »). */
        internal fun normaliserLabelNomenclature(s: String): String =
            sansDiacritiques(s.trim())
                .replace(Regex("^\\s*\\d+\\s*-\\s*"), "")
                .lowercase()
                .trim()

        // Résout un code interne ou un id_nomenclature direct vers l'id GeoNature.
        // ATTENTION à l'ordre : les ids serveur passent en PREMIER. Sinon un id serveur qui ressemble
        // à un code interne (ex : Adulte a id=3 côté serveur, alors que notre code interne "3"
        // représente "juvénile") serait mal traduit et on enverrait Juvénile.
        internal fun resolverIdNomenclature(
            code: String,
            type: String,
            labels: Map<String, String>,
            nomenclatures: Map<String, Map<String, Int>>,
        ): Int? {
            // 1. Le code est déjà un id_nomenclature de la nomenclature serveur : tel quel.
            val idEntier = code.toIntOrNull()
            if (idEntier != null && nomenclatures[type]?.values?.contains(idEntier) == true) return idEntier

            // 2. Un de nos codes internes ("0", "1", "2"…) : résolution par le libellé.
            labels[code]?.let { libelle ->
                val label = normaliserLabelNomenclature(libelle)
                val resolu = nomenclatures[type]?.get(label)
                if (resolu == null) {
                    Journal.w(TAG_MEDIA, "Nomenclature $type : label '$label' (code interne '$code') absent de la nomenclature serveur → champ omis du payload")
                }
                return resolu
            }

            // 3. Repli : résolution par le texte brut.
            val resolu = nomenclatures[type]?.get(normaliserLabelNomenclature(code))
            if (resolu == null) {
                Journal.w(TAG_MEDIA, "Nomenclature $type : code '$code' non résolu (ni id serveur, ni label connu, ni texte brut) → champ omis du payload")
            }
            return resolu
        }

        /** Titre du média, DÉTERMINISTE pour un fichier local donné : il sert d'identité client, le
         *  seul champ que nous contrôlons et que le serveur nous rend tel quel (`title_fr`). Le nom
         *  de fichier local est déjà unique (horodaté à l'import). Il ne dépend PAS de la position
         *  dans la liste : un titre indexé « (1) », « (2) » se décalait dès qu'un fichier était
         *  sauté, rendant toute réconciliation impossible. */
        /** La description qu'un média recevait d'office, avant les champs du formulaire média du web. */
        internal const val DESCRIPTION_MEDIA_ANCIENNE = "Saisie GeoMys mobile"

        /** Le nom d'un fichier local tel que le serveur le garde (`secure_filename` de werkzeug :
         *  lettres, chiffres, `_`, `.` et `-` ASCII, les espaces en `_`). */
        internal fun nomFichierServeur(mediaPath: String): String =
            mediaPath.substringAfterLast('/').substringBefore('?')
                .replace(' ', '_')
                .filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '.' || it == '-' }
                .trim('.', '_')

        internal fun titreMedia(titreBase: String, mediaPath: String): String {
            val nom = mediaPath.substringAfterLast('/').substringBefore('?').ifEmpty { "media" }
            return "$titreBase — $nom"
        }

        /** Type MIME → id_nomenclature TYPE_MEDIA par les libellés stables entre instances (« photo »,
         *  « audio », « vidéo », variantes « fichier local » / « lien web »). [typesMedia] = libellé
         *  normalisé → id_nomenclature. Null si introuvable. */
        internal fun idTypeMediaPour(typesMedia: Map<String, Int>, mime: String): Int? {
            val candidats = when {
                mime.startsWith("image/") -> listOf("photo", "photo (fichier local)", "photo (lien web)")
                mime.startsWith("audio/") -> listOf("audio", "audio (fichier local)", "audio (lien web)")
                mime.startsWith("video/") -> listOf("vidéo", "vidéo (fichier local)", "vidéo (lien web)", "video")
                else -> emptyList()
            }
            return candidats.firstNotNullOfOrNull { typesMedia[it] }
        }

        /** Le fichier local d'un média enregistré à [chemin] : lui-même s'il existe, sinon le fichier du
         *  même nom dans [dossierMedias]. Propre au portage : sous iOS, le dossier de l'appli change de
         *  chemin à chaque mise à jour, et un chemin absolu enregistré avant ne mène plus nulle part
         *  (GeoMys-IOS enregistrait pour cela « medias/<nom> »). */
        fun fichierMedia(systeme: FileSystem, chemin: String, dossierMedias: Path?): Path {
            val enregistre = chemin.toPath()
            if (dossierMedias == null || systeme.exists(enregistre)) return enregistre
            val repli = dossierMedias / enregistre.name
            return if (systeme.exists(repli)) repli else enregistre
        }

        /** Le chemin d'un média enregistré par URI `file:` (ou par chemin absolu), décodé, sans requête
         *  ni fragment — l'`Uri.parse(…).path` d'Android. Null si l'URI n'en porte pas (URI opaque,
         *  comme `mailto:x`). */
        internal fun cheminDepuisUri(uri: String): String? {
            val sansSuite = uri.substringBefore('#').substringBefore('?')
            val schema = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").find(sansSuite)?.value
            val chemin = when {
                schema == null -> sansSuite
                sansSuite.startsWith("$schema//") -> sansSuite.removePrefix("$schema//").let { reste ->
                    val i = reste.indexOf('/')
                    if (i < 0) "" else reste.substring(i)
                }
                else -> sansSuite.removePrefix(schema).takeIf { it.startsWith("/") } ?: return null
            }
            // Séquence %xx invalide : gardée telle quelle, Uri.decode d'Android ne lève pas non plus.
            val decode = try { chemin.decodeURLPart() } catch (_: Exception) { chemin }
            return decode.takeIf { it.isNotEmpty() }
        }

        /** Le type MIME d'un fichier d'après son extension (`URLConnection.guessContentTypeFromName`
         *  sous Android) ; null si elle est inconnue. Seul le préfixe (image, audio, video) choisit
         *  le type de média ; le type exact accompagne le fichier dans l'envoi. */
        internal fun typeMime(nom: String): String? = when (nom.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "jpe" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "tif", "tiff" -> "image/tiff"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/x-wav"
            "ogg", "oga", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            "amr" -> "audio/amr"
            "3gp" -> "video/3gpp"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            else -> null
        }

        /** Le tableau des valeurs d'une réponse de nomenclatures, sous les formes rencontrées : tableau
         *  nu (ou de descripteurs portant `values`), ou enveloppe items / data / results / values /
         *  nomenclatures. */
        private fun extraireTableauNomenclatures(texte: String): JSONArray? {
            try {
                val arr = JSONArray(texte)
                // Cas : tableau de descripteurs de type, avec « values » imbriqué.
                arr.optJSONObject(0)?.optJSONArray("values")?.let { if (it.length() > 0) return it }
                return arr
            } catch (_: Exception) {}
            val obj = try { JSONObject(texte) } catch (_: Exception) { return null }
            for (cle in listOf("items", "data", "results", "values", "nomenclatures")) {
                val arr = obj.optJSONArray(cle)
                if (arr != null && arr.length() > 0) return arr
            }
            return null
        }

        private fun tableLibelles(tableau: JSONArray): Map<String, Int> {
            val table = mutableMapOf<String, Int>()
            for (i in 0 until tableau.length()) {
                val item = tableau.getJSONObject(i)
                val id = item.optInt("id_nomenclature", -1).takeIf { it > 0 } ?: continue
                val libelle = normaliserLabelNomenclature(item.optString("label_default", "").ifEmpty { item.optString("label_fr", "") })
                if (libelle.isNotEmpty()) table[libelle] = id
            }
            return table
        }
    }
}
