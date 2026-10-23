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

// Propre au portage (demande du 23 octobre 2026) : l'avertissement « Donnée exceptionnelle » du web OccTax.
// Le web interroge le serveur au choix de l'espèce ; ici, la vérification part en arrière-plan à la saisie
// s'il y a du réseau, sinon à l'entrée dans « Mes saisies » et à l'envoi (exception voulue à la règle « le
// cache seulement »). Elle ne bloque jamais rien.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.network.ApiProfils
import fr.ariegenature.geomys.network.GeoNatureUpload
import fr.ariegenature.geomys.network.ReponseProfil
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.OcctaxFieldsConfig
import fr.ariegenature.geomys.store.io.FichierAtomique
import fr.ariegenature.geomys.store.io.StoreFichierJson
import fr.ariegenature.geomys.util.DateEnvoi
import fr.ariegenature.geomys.util.FormatDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.encodeUtf8

/** Le résultat de la vérification de l'observation [obsId] : [messages] vides, rien à signaler ;
 *  sinon les phrases du serveur (« Le taxon n'a jamais été observé à cette période »…). [cle] résume
 *  ce qui a été vérifié : une observation modifiée depuis (espèce, date, lieu, altitude, stade de vie)
 *  est à revérifier. */
@Serializable
data class VerificationProfil(val obsId: String, val cle: String, val messages: List<String> = emptyList()) {
    val exceptionnelle: Boolean get() = messages.isNotEmpty()
}

/** Les résultats des vérifications (`profils_taxons.json`). */
class ProfilsStore(fichier: FichierAtomique) :
    StoreFichierJson<VerificationProfil>(VerificationProfil.serializer(), fichier, "ProfilsStore") {

    suspend fun poser(verification: VerificationProfil): Boolean = muter { liste ->
        liste.removeAll { it.obsId == verification.obsId }
        liste.add(verification)
    }

    /** Oublie les résultats des observations qui n'existent plus. */
    suspend fun garderSeulement(obsIds: Set<String>): Boolean = muter { liste -> liste.removeAll { it.obsId !in obsIds } }
}

/** Ce que le serveur compare au profil d'un taxon pour l'observation [obsId] : les valeurs de son relevé
 *  telles qu'elles partiront à l'envoi. Les altitudes nulles sont à demander au MNT. */
data class DemandeProfil(
    val obsId: String,
    val cdNom: Int,
    val dateMin: String,
    val dateMax: String,
    val geometrie: String,
    val altitudeMin: Int?,
    val altitudeMax: Int?,
    val stadesVie: List<String>,
) {
    val cle: String = listOf(cdNom, dateMin, dateMax, geometrie, altitudeMin, altitudeMax, stadesVie)
        .toString().encodeUtf8().sha256().hex().take(16)
}

/** Les demandes de vérification des [observations] d'une saisie : celles qui portent un taxon et ne
 *  sont pas encore parties. Dates et géométrie comme à l'envoi (GeoNatureUpload.releveAEnvoyer) : un
 *  relevé couvre les dates de toutes ses observations, sauf dates posées dans « Détails ». */
fun demandesProfil(observations: List<Observation>): List<DemandeProfil> =
    observations.groupBy { it.releveId ?: it.id }.values.flatMap { groupe ->
        val premiere = groupe.first()
        val debut = premiere.dateDebutReleve
        val dateMin = debut ?: groupe.minOf { it.date }
        val dateMax = premiere.dateFinReleve ?: debut ?: groupe.maxOf { it.date }
        val geometrie = GeoNatureUpload.construireGeometrie(premiere.geometryType, premiere.geometryCoordsJson, premiere.latitude, premiere.longitude)
        val altMin = premiere.champsReleveExtra["altitude_min"]?.trim()?.toIntOrNull()
        val altMax = premiere.champsReleveExtra["altitude_max"]?.trim()?.toIntOrNull()
        groupe.filter { it.cdNom != null && !it.releveSansEspece && !it.envoyeeServeur }.map { obs ->
            DemandeProfil(
                obsId = obs.id,
                cdNom = obs.cdNom!!,
                dateMin = DateEnvoi.date(dateMin),
                dateMax = DateEnvoi.date(dateMax),
                geometrie = geometrie.toString(),
                // Une seule altitude saisie vaut pour les deux, comme une altitude ponctuelle.
                altitudeMin = altMin ?: altMax,
                altitudeMax = altMax ?: altMin,
                stadesVie = (listOf(obs.stadeVie) + obs.denombrementsAdditionnels.map { it.stadeVie })
                    .mapNotNull { it?.takeIf { s -> s.isNotEmpty() } }.distinct(),
            )
        }
    }

/**
 * Le serveur des Paramètres a-t-il des profils de taxons ? Demande de l'utilisateur (23 octobre 2026) :
 * un serveur qui n'en a pas n'est plus interrogé, jusqu'à un autre serveur ou « Recharger les données ».
 *
 * Sans profils : le web n'en montre pas (`FRONTEND.ENABLE_PROFILES` à faux, lu au rechargement), la
 * route n'existe pas, ou aucun des [SEUIL_SANS_PROFIL] premiers taxons vérifiés n'a de profil (calcul
 * jamais lancé sur le serveur). Un seul profil trouvé suffit à écarter ce dernier cas.
 */
class DisponibiliteProfils(private val config: GeoNatureConfig) {

    private val serveur: String get() = config.urlServeur.trim().trimEnd('/')

    /** Le serveur est à interroger. */
    val disponibles: Boolean get() = serveur.isNotEmpty() && config.profilsAbsentsServeur != serveur

    /** Un profil a répondu : le serveur en a, plus besoin de compter les taxons sans profil. */
    fun profilTrouve() {
        config.profilsSondage = "$serveur|$TROUVE"
    }

    /** Le taxon [cdRef] n'a pas de profil. */
    fun sansProfil(cdRef: Int) {
        val (adresse, etat) = config.profilsSondage.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val dejaVus = if (adresse == serveur) etat else ""
        if (dejaVus == TROUVE) return
        val vus = dejaVus.split(',').mapNotNull { it.toIntOrNull() }.toSet() + cdRef
        if (vus.size >= SEUIL_SANS_PROFIL) absents()
        else config.profilsSondage = "$serveur|${vus.joinToString(",")}"
    }

    /** Le serveur n'a pas de profils : il n'est plus interrogé. */
    fun absents() {
        config.profilsAbsentsServeur = serveur
        config.profilsSondage = ""
    }

    /** « Recharger les données » : tout repart de zéro, sauf si la configuration du serveur dit que le
     *  web n'utilise pas les profils ([actifs] à faux). */
    fun recharger(actifs: Boolean) {
        config.profilsSondage = ""
        config.profilsAbsentsServeur = if (actifs) "" else serveur
    }

    companion object {
        /** Taxons sans profil, tous différents, au-delà desquels le serveur est tenu pour sans profils. */
        const val SEUIL_SANS_PROFIL = 10

        private const val TROUVE = "trouve"
    }
}

/**
 * Les vérifications des observations auprès des profils de taxons du serveur, et leurs résultats
 * ([resultats], par observation). [verifier] ne vérifie que ce qui ne l'a pas été (ou plus), une
 * vérification à la fois ; sans réseau, il s'arrête et rend la main : on retentera plus tard.
 */
class ProfilsTaxons(
    private val store: ProfilsStore,
    private val api: ApiProfils,
    private val nomenclatures: NomenclatureCache,
    private val disponibilite: DisponibiliteProfils,
) {
    private val _resultats = MutableStateFlow<Map<String, VerificationProfil>>(emptyMap())

    /** Les résultats par observation, y compris ceux qui ne valent plus (cf. [resultat]). */
    val resultats: StateFlow<Map<String, VerificationProfil>> = _resultats.asStateFlow()

    private val verrou = Mutex()
    private var charge = false

    /** `cd_ref` par `cd_nom`, le temps du processus. */
    private val cdRefs = HashMap<Int, Int>()

    /** Le résultat de [obs] (relevé [groupe]) parmi [resultats] s'il vaut encore pour elle (même espèce,
     *  même relevé), null sinon. */
    fun resultat(
        obs: Observation,
        groupe: List<Observation>,
        resultats: Map<String, VerificationProfil> = _resultats.value,
    ): VerificationProfil? {
        val demande = demandesProfil(groupe).firstOrNull { it.obsId == obs.id } ?: return null
        return resultats[obs.id]?.takeIf { it.cle == demande.cle }
    }

    /** Les observations des [observations] dont la vérification vaut encore et signale un écart. */
    fun exceptionnelles(
        observations: List<Observation>,
        resultats: Map<String, VerificationProfil> = _resultats.value,
    ): List<Pair<Observation, VerificationProfil>> {
        val actuels = resultats
        val parId = observations.associateBy { it.id }
        return demandesProfil(observations).mapNotNull { d ->
            val v = actuels[d.obsId]?.takeIf { it.cle == d.cle && it.exceptionnelle } ?: return@mapNotNull null
            parId.getValue(d.obsId) to v
        }
    }

    /** Il reste des observations à vérifier parmi [observations]. */
    fun aVerifier(observations: List<Observation>): Boolean {
        val actuels = _resultats.value
        return demandesProfil(observations).any { actuels[it.obsId]?.cle != it.cle }
    }

    suspend fun charger() {
        verrou.withLock { chargerInterne() }
    }

    private suspend fun chargerInterne() {
        if (charge) return
        _resultats.value = store.charger().associateBy { it.obsId }
        charge = true
    }

    /** Vérifie celles des [observations] qui ne l'ont pas été depuis leur dernière modification. Rend
     *  false si le serveur n'a pas répondu (il en reste à vérifier), true sinon. Un serveur sans profils
     *  ([DisponibiliteProfils]) n'est pas interrogé. */
    suspend fun verifier(observations: List<Observation>): Boolean = verrou.withLock {
        chargerInterne()
        val demandes = demandesProfil(observations).filter { _resultats.value[it.obsId]?.cle != it.cle }
        // Le MNT une fois par relevé (même géométrie, mêmes altitudes à demander).
        val altitudesMnt = HashMap<String, Pair<Int, Int>>()
        for (d in demandes) {
            if (!disponibilite.disponibles) break
            val (altMin, altMax) = if (d.altitudeMin != null && d.altitudeMax != null) {
                d.altitudeMin to d.altitudeMax
            } else {
                altitudesMnt[d.geometrie] ?: (api.altitudes(JSONObject(d.geometrie)) ?: return@withLock false)
                    .also { altitudesMnt[d.geometrie] = it }
            }
            val cdRef = cdRefs[d.cdNom] ?: (api.cdRef(d.cdNom) ?: return@withLock false).also { cdRefs[d.cdNom] = it }
            val corps = JSONObject()
                .put("cd_ref", cdRef)
                .put("date_min", d.dateMin)
                .put("date_max", d.dateMax)
                .put("altitude_min", altMin)
                .put("altitude_max", altMax)
                .put("geom", JSONObject(d.geometrie))
                .put("life_stages", JSONArray().apply { idsStadesVie(d.stadesVie).forEach { put(it) } })
            val messages = when (val reponse = api.verifier(corps)) {
                is ReponseProfil.Verifiee -> reponse.messages.also { disponibilite.profilTrouve() }
                ReponseProfil.SansProfil -> emptyList<String>().also { disponibilite.sansProfil(cdRef) }
                ReponseProfil.Invalide -> emptyList()
                ReponseProfil.ServiceAbsent -> {
                    disponibilite.absents()
                    break
                }
                ReponseProfil.Injoignable -> return@withLock false
            }
            val verification = VerificationProfil(d.obsId, d.cle, messages)
            store.poser(verification)
            _resultats.value += d.obsId to verification
        }
        true
    }

    /** Avant un envoi : ce qui n'a pas été vérifié l'est, [delaiMs] au plus, puis les données
     *  exceptionnelles des [observations]. Sans réponse du serveur, l'envoi n'attend pas plus. */
    suspend fun avantEnvoi(observations: List<Observation>, delaiMs: Long = 15_000): List<Pair<Observation, VerificationProfil>> {
        withTimeoutOrNull(delaiMs) { verifier(observations) }
        return exceptionnelles(observations)
    }

    /** Oublie les résultats des observations qui ne sont plus dans [obsIds] (saisies supprimées). */
    suspend fun oublierSauf(obsIds: Set<String>) {
        verrou.withLock {
            chargerInterne()
            if (_resultats.value.keys.all { it in obsIds }) return@withLock
            store.garderSeulement(obsIds)
            _resultats.value = _resultats.value.filterKeys { it in obsIds }
        }
    }

    /** Les id_nomenclature des stades de vie [codes], comme à l'envoi ; un code non résolu est omis. */
    private fun idsStadesVie(codes: List<String>): List<Int> {
        if (codes.isEmpty()) return emptyList()
        val champ = OcctaxFieldsConfig.REGISTRE.firstOrNull { it.svKey == "stadeVie" } ?: return emptyList()
        val table = mapOf(champ.code to nomenclatures.get(champ.code).associate { GeoNatureUpload.normaliserLabelNomenclature(it.label) to it.id })
        return codes.mapNotNull { GeoNatureUpload.resolverIdNomenclature(it, champ.code, champ.uploadLabels, table) }.distinct()
    }
}

/** Les textes des avertissements « Donnée exceptionnelle ». */
object TextesProfils {
    /** L'avertissement d'une observation, partout le même (demande de l'utilisateur du 23 octobre 2026,
     *  à la place des phrases du serveur, qui ne servent plus qu'à savoir s'il y a un écart). */
    const val MESSAGE = "Donnée exceptionnelle. Le taxon n'a jamais été observé dans cette zone et/ou à cette date"

    const val TITRE_ENVOI = "Données exceptionnelles"
    const val BOUTON_ENVOI = "Envoyer quand même"

    /** Ligne d'une saisie de « Mes saisies » : « ⚠ 2 données exceptionnelles ». */
    fun ligneSaisie(nombre: Int): String =
        "⚠ $nombre donnée${if (nombre > 1) "s" else ""} exceptionnelle${if (nombre > 1) "s" else ""}"

    /** Les espèces signalées, pour la liste des espèces. */
    fun especes(exceptionnelles: List<Pair<Observation, VerificationProfil>>): Set<String> =
        exceptionnelles.mapTo(LinkedHashSet()) { it.first.espece }

    /** « • Hirondelle rustique (15/01/2026) ». */
    fun lignes(exceptionnelles: List<Pair<Observation, VerificationProfil>>): List<String> =
        exceptionnelles.map { (obs, _) -> "• ${obs.espece} (${FormatDate.jour(obs.date)})" }

    /** Le message de confirmation avant l'envoi d'une saisie. */
    fun messageEnvoi(exceptionnelles: List<Pair<Observation, VerificationProfil>>): String =
        "$MESSAGE :\n\n" + lignes(exceptionnelles).joinToString("\n") + "\n\nVérifiez, ou envoyez quand même."

    /** Ce qui s'ajoute à la confirmation de « Tout envoyer ». */
    fun complementToutEnvoyer(exceptionnelles: List<Pair<Observation, VerificationProfil>>): String =
        if (exceptionnelles.isEmpty()) ""
        else "\n\n" + ligneSaisie(exceptionnelles.size) + " (taxon jamais observé dans cette zone et/ou à cette date) :\n" +
            lignes(exceptionnelles).joinToString("\n")
}
