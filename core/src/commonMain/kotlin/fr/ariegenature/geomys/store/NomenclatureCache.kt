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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/NomenclatureCache.kt) :
// l'objet devient une classe (réglages et référentiel TaxRef injectés) ; Gson laisse la place à
// kotlinx.serialization (même format) ; les SharedPreferences à multiplatform-settings. Les
// ensembles de groupes et regno restent appelables sans instance, dans le compagnon.

package fr.ariegenature.geomys.store

import com.russhwolf.settings.Settings
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import kotlin.concurrent.Volatile
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
data class TaxrefRestriction(val regne: String, val group2Inpn: String)
/** [cd] = cd_nomenclature serveur (stable inter-instances, contrairement à l'id) — sert aux
 *  règles métier calquées sur le web (ex. OccHab : technique de collecte cd "10" = « Autre,
 *  préciser » → précision technique requise). null sur un cache d'avant l'ajout du champ
 *  (résolu à la prochaine synchro) : les règles qui en dépendent se désactivent proprement. */
@Serializable
data class NomValeur(
    val id: Int,
    val label: String,
    val taxref: List<TaxrefRestriction> = emptyList(),
    val cd: String? = null,
)

/** Les valeurs de nomenclature et leurs défauts, dans les réglages [prefs] (`nomenclature_cache`).
 *  Une seule instance dans l'appli. */
class NomenclatureCache(private val prefs: Settings, private val taxRef: TaxRefCache) {
    // @Volatile : écrits par le thread de sync, lus par le thread UI à la saisie (visibilité
    // inter-thread de la référence, cohérent avec TaxRefCache).
    @Volatile private var mem: Map<String, List<NomValeur>>? = null
    @Volatile private var memDefauts: Map<String, String>? = null

    // Intersection des groupes botaniques connus avec ceux présents dans le cache TaxRef.
    // Fallback sur tous les groupes botaniques si aucun n'est encore synchronisé.
    fun groupesBotaniquesConnus(): Set<String> {
        val connus = taxRef.comptesGroupes.keys.toSet()
        val intersection = connus.intersect(GROUPES_BOTANIQUES)
        return if (intersection.isEmpty()) GROUPES_BOTANIQUES else intersection
    }

    fun setAll(data: Map<String, List<NomValeur>>) {
        prefs.putString(KEY, JSON_STOCKAGE.encodeToString(MAP_VALEURS, data))
        mem = data
    }

    /** Ajoute (ou remplace) les valeurs de quelques types, en gardant les autres (essais). */
    fun completer(data: Map<String, List<NomValeur>>) = setAll(charger() + data)

    fun get(type: String): List<NomValeur> = charger()[type] ?: emptyList()

    val estDisponible: Boolean get() = charger().isNotEmpty()

    // Filtre pour un ensemble de groupes + regno — même logique que l'app iOS.
    // Règles (GeoNature utilise "all" comme joker) :
    //   regno="all"                          → universel, s'applique à tout
    //   regno=X, group2_inpn="all"           → tout le royaume X
    //   group2_inpn=G (insensible casse)     → groupe exact
    // Taxref vide = universel. Groupes vides = inconnu → retourne tout.
    fun filtrerPourGroupes(type: String, groupes: Set<String>, regno: String = ""): List<NomValeur> {
        val groupesLower = groupes.map { it.lowercase() }.toSet()
        return get(type).filter { v ->
            when {
                v.taxref.isEmpty() -> true
                groupes.isEmpty()  -> true
                else -> v.taxref.any { r ->
                    r.regne.equals("all", ignoreCase = true) ||
                    (r.regne.equals(regno, ignoreCase = true) && r.group2Inpn.equals("all", ignoreCase = true)) ||
                    groupesLower.contains(r.group2Inpn.lowercase())
                }
            }
        }
    }

    val count: Int get() = charger().values.sumOf { it.size }

    /** Stocke la map des défauts par mnémonique pour un module donné (résultat de
     *  `GET /api/<module>/defaultNomenclatures`). Clé = mnémonique du type (STATUT_OBS…),
     *  valeur = id_nomenclature par défaut (String pour matcher les codes utilisés
     *  partout dans l'UI). */
    fun setDefauts(map: Map<String, String>) {
        prefs.putString(KEY_DEFAUTS, JSON_STOCKAGE.encodeToString(MAP_DEFAUTS, map))
        memDefauts = map
    }

    /** Id_nomenclature par défaut pour un mnémonique, ou null si non configuré côté serveur. */
    fun defautPour(mnemonique: String): String? = chargerDefauts()[mnemonique]

    fun tousLesDefauts(): Map<String, String> = chargerDefauts()

    private fun chargerDefauts(): Map<String, String> {
        memDefauts?.let { return it }
        val json = prefs.getStringOrNull(KEY_DEFAUTS) ?: return emptyMap()
        return try {
            JSON_STOCKAGE.decodeFromString(MAP_DEFAUTS, json).also { memDefauts = it }
        } catch (_: Exception) { emptyMap() }
    }

    /** Purge valeurs ET défauts (mémoire + disque). Les id_nomenclature sont propres à une
     *  instance GeoNature : à appeler au changement d'identité serveur, sinon les envois
     *  partent avec des FK de l'ancienne instance. */
    fun vider() {
        prefs.remove(KEY)
        prefs.remove(KEY_DEFAUTS)
        mem = null
        memDefauts = null
    }

    private fun charger(): Map<String, List<NomValeur>> {
        mem?.let { return it }
        val json = prefs.getStringOrNull(KEY) ?: return emptyMap()
        return try {
            JSON_STOCKAGE.decodeFromString(MAP_VALEURS, json).also { mem = it }
        } catch (_: Exception) { emptyMap() }
    }

    companion object {
        // Valeurs group2_inpn correspondant aux poissons dans TaxRef v16 et v17 (même liste iOS)
        val GROUP2_POISSONS: Set<String> = setOf(
            "Poissons osseux", "Poissons cartilagineux", "Agnathes", "Poissons lamproies", "Poissons",
            "Actinoptérygiens", "Chondrichtyens", "Myxines", "Cœlacanthes",
            "Dipneustes", "Sarcoptérygiens", "Actinistiens",
            "Poissons marins", "Poissons d'eau douce"
        )

        val GROUPES_FONGE: Set<String> = setOf(
            "Champignons", "Basidiomycètes", "Ascomycètes", "Myxomycètes",
            "Chytridiomycètes", "Zygomycètes", "Glomeromycètes"
        )

        // Flore : group1_inpn utilisés par GeoNature pour les plantes
        val GROUPES1_FLORE: Set<String> = setOf("Phanérogames", "Ptéridophytes", "Bryophytes")

        // Groupes botaniques connus dans INPN/TaxRef (même liste que l'app iOS)
        val GROUPES_BOTANIQUES: Set<String> = setOf(
            "Angiospermes", "Gymnospermes", "Ptéridophytes",
            "Mousses", "Hépatiques et anthocérotes",
            "Lichens", "Chlorophytes et charophytes", "Rhodophytes",
            "Trachéophytes", "Bryophytes", "Plantes"
        )

        // Déduit le regno (Plantae / Animalia / Fungi…) depuis le group2_inpn.
        fun regno(pourGroupe: String): String = when {
            GROUPES_BOTANIQUES.contains(pourGroupe) -> "Plantae"
            GROUPES_FONGE.contains(pourGroupe)      -> "Fungi"
            else -> "Animalia"
        }

        private const val KEY = "nom_cache_v1"
        private const val KEY_DEFAUTS = "nom_defauts_v1"
        private val MAP_VALEURS = MapSerializer(String.serializer(), ListSerializer(NomValeur.serializer()))
        private val MAP_DEFAUTS = MapSerializer(String.serializer(), String.serializer())
    }
}
