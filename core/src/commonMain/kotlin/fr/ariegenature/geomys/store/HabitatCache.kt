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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/HabitatCache.kt) :
// java.io remplacé par okio et FichierAtomique, Gson par kotlinx.serialization, java.text.Normalizer
// par sansDiacritiques ; le séparateur NUL, littéral dans la source, s'écrit '\u0000'. Les objets
// HabitatCache (Occtax) et HabitatCacheOccHab deviennent deux instances de HabitatCacheStore, créées
// par les services de l'appli.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.model.HabitatSuggestion
import fr.ariegenature.geomys.plateforme.exclureDesSauvegardes
import fr.ariegenature.geomys.plateforme.sansDiacritiques
import fr.ariegenature.geomys.store.io.FichierAtomique
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import kotlin.concurrent.Volatile
import kotlinx.serialization.builtins.ListSerializer
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * Cache local d'une liste HABREF téléchargée à la synchro, pour que le champ habitat fonctionne
 * **hors ligne** et plus vite (recherche locale en mémoire, *contains* insensible aux accents/casse).
 *
 * L'endpoint `GET /api/habref/habitats/autocomplete?limit=<grand>[&id_list=<liste>]` renvoie tout le
 * référentiel (ou la liste restreinte) — récupéré une fois et stocké en fichier (trop gros pour
 * SharedPreferences). Une INSTANCE par périmètre : la liste diffère selon le module
 * (`OCCTAX.ID_LIST_HABITAT` vs `OCCHAB.ID_LIST_HABITAT`) : l'appli en crée deux, Occtax et OccHab.
 */
class HabitatCacheStore(
    /** Le dossier des caches retéléchargeables (`filesDir/caches`), exclu des sauvegardes. */
    private val dir: Path,
    private val nomFichier: String,
    /** true = recherche IDENTIQUE au serveur (`ilike` : insensible à la casse mais SENSIBLE aux
     *  accents — « Foret » ne matche pas « Forêts ») pour que la liste locale soit la même que le
     *  web, celle d'OccTax comme celle d'OccHab. false = insensible aux accents aussi. */
    private val sensibleAuxAccents: Boolean = false,
    private val systeme: FileSystem = FileSystem.SYSTEM,
) : HabitatCacheAcces {

    /** Entrée d'index précalculée : [cleMatch] sert au filtrage, [cleTri] au classement. */
    private data class Entree(val s: HabitatSuggestion, val cleMatch: String, val cleTri: String)

    @Volatile private var mem: List<HabitatSuggestion>? = null
    // Index de recherche : clés précalculées UNE fois par entrée (sinon chaque frappe
    // renormaliserait des dizaines de milliers de libellés). Aligné sur `mem`.
    @Volatile private var index: List<Entree>? = null

    private fun fichier(): Path = dir / nomFichier

    /** Remplace tout le cache (chemin de synchro). Écriture atomique. */
    fun remplacerTout(habitats: List<HabitatSuggestion>) {
        try {
            systeme.createDirectories(dir)
            exclureDesSauvegardes(dir)
            if (FichierAtomique(systeme, fichier(), "HabitatCache").ecrire(JSON_STOCKAGE.encodeToString(LISTE, habitats))) {
                mem = habitats
                index = null
            }
        } catch (_: Exception) { /* cache best-effort */ }
    }

    private fun charger(): List<HabitatSuggestion> {
        mem?.let { return it }
        val f = fichier()
        if (!systeme.exists(f)) return emptyList()
        return try {
            JSON_STOCKAGE.decodeFromString(LISTE, systeme.read(f) { readUtf8() }).also { mem = it }
        } catch (_: Exception) { emptyList() }
    }

    private fun indexer(): List<Entree> {
        index?.let { return it }
        return charger().map {
            val match = if (sensibleAuxAccents) it.libelle.lowercase().trim() else normaliser(it.libelle)
            Entree(it, match, cleTri(it.libelle))
        }.also { index = it }
    }

    /** Clé de TRI reproduisant le `ORDER BY lb_code` du serveur avec sa collation Postgres :
     *  tri sur le CODE seul (partie avant « - » du search_name) — un code court passe avant ses
     *  extensions (« 16.27 » avant « 16.271 ») grâce au séparateur U+0000 — et la ponctuation et
     *  les espaces sont IGNORÉS — « (9560 et 5210)-2 » se classe comme « 9560et52102 », après
     *  « 42.A28 » et non en tête à cause de la parenthèse (l'appli remontait en premier des codes
     *  parenthésés que le web classe hors top 20). Vérifié à l'identique sur « genévrier ». */
    private fun cleTri(libelle: String): String {
        val lib = libelle.trim()
        return collation(lib.substringBefore(" - ")) + '\u0000' + collation(lib)
    }

    private fun collation(s: String): String =
        sansDiacritiques(s)
            .lowercase()
            .filter { it.isLetterOrDigit() }

    /** Vrai si la liste a été téléchargée (→ recherche locale possible, y compris hors-ligne). */
    override val estDisponible: Boolean get() = charger().isNotEmpty()

    val count: Int get() = charger().size

    /** Recherche locale.
     *  - Mode OccHab (`sensibleAuxAccents`) : réplique EXACTEMENT la route serveur
     *    `/habref/habitats/autocomplete` — `ilike` insensible à la casse mais sensible aux
     *    accents, espaces du terme = jokers `%` (les mots doivent apparaître DANS L'ORDRE),
     *    tri par [cleCollation] (≈ ORDER BY lb_code, ponctuation ignorée). Mêmes résultats,
     *    même ordre, même top-N que le web.
     *  - Mode Occtax (historique) : *contains* insensible accents/casse, priorité aux libellés
     *    qui commencent par le terme, puis tri alphabétique. */
    override fun rechercher(terme: String, limite: Int, cdTypo: Int?): List<HabitatSuggestion> {
        if (sensibleAuxAccents) {
            val mots = terme.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (mots.sumOf { it.length } < 2) return emptyList()
            return indexer().asSequence()
                .filter { cdTypo == null || it.s.cdTypo == cdTypo }
                .filter { contientMotsDansLOrdre(it.cleMatch, mots) }
                .sortedWith(compareBy({ it.cleTri }, { it.cleMatch }))
                .map { it.s }.take(limite).toList()
        }
        val t = normaliser(terme)
        if (t.length < 2) return emptyList()
        val (debut, autres) = indexer().asSequence()
            .filter { cdTypo == null || it.s.cdTypo == cdTypo }
            .filter { it.cleMatch.contains(t) }
            .partition { it.cleMatch.startsWith(t) }
        return (debut.sortedBy { it.cleMatch } + autres.sortedBy { it.cleMatch })
            .asSequence().map { it.s }.take(limite).toList()
    }

    /** Équivalent du `ilike '%mot1%mot2%'` serveur : chaque mot présent, dans l'ordre. */
    private fun contientMotsDansLOrdre(cle: String, mots: List<String>): Boolean {
        var pos = 0
        for (m in mots) {
            val i = cle.indexOf(m, pos)
            if (i < 0) return false
            pos = i + m.length
        }
        return true
    }

    fun vider() {
        runCatching { systeme.delete(fichier()) }
        mem = null
        index = null
    }

    private fun normaliser(s: String): String =
        sansDiacritiques(s)
            .lowercase()
            .trim()

    private companion object {
        val LISTE = ListSerializer(HabitatSuggestion.serializer())
    }
}

/** Accès commun aux caches HABREF (Occtax / OccHab) — pour pouvoir choisir le cache selon le
 *  contexte dans [fr.ariegenature.geomys.network.HabitatService]. */
interface HabitatCacheAcces {
    val estDisponible: Boolean
    /** [cdTypo] : seulement les habitats de cette typologie (`cd_typo` du web). */
    fun rechercher(terme: String, limite: Int = 20, cdTypo: Int? = null): List<HabitatSuggestion>
}
