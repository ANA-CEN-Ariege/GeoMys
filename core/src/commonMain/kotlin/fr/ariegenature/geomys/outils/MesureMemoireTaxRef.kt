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

package fr.ariegenature.geomys.outils

import fr.ariegenature.geomys.plateforme.memoireReference
import fr.ariegenature.geomys.plateforme.memoireUtilisee
import fr.ariegenature.geomys.plateforme.ramasserMiettes
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.measureTimedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/** Mesure provisoire (lot 0) : ce que coûte en mémoire le référentiel TaxRef tel que le tient
 *  `TaxRefCache` de GeoMys-Android, porté tel quel avec kotlinx-serialization et okio. Sert à
 *  trancher entre fichiers JSON chargés en mémoire et base SQLite (INVENTAIRE.md § 8.1, point 7,
 *  et § 11, décision 8).
 *
 *  Le déroulé suit l'appli. [preparer] joue la synchronisation : il écrit les huit fichiers du
 *  référentiel. [mesurer] joue un démarrage à froid suivi d'une session de saisie : il relit les
 *  fichiers et construit les index dérivés que `TaxRefCache` garde en mémoire, puis relève le pic
 *  et ce qui reste occupé. La relecture se fait soit en flux (comme GeoMys-Android lit
 *  cache_v3.json), soit fichier par fichier en une seule chaîne (comme il lit les sept autres, et
 *  comme il lisait cache_v3.json avant les OOM au-delà de 200 000 taxons).
 *
 *  Une mesure par processus : sur iOS, la mémoire rendue par le ramasse-miettes reste souvent
 *  attribuée au processus, une seconde mesure dans le même processus serait faussée.
 *
 *  Les taxons sont synthétiques, calés sur l'appareil de référence de GeoMys-Android (voir
 *  [genererTaxons]). */
object MesureMemoireTaxRef {

    /** Même forme que `TaxRefEntry` d'Android, et même JSON que son fichier cache_v3.json. */
    @Serializable
    data class EntreeTaxRef(val cdNom: Int, val sciNom: String, val nomFrOriginal: String? = null)

    /** Même forme que `SuggestionTaxon` d'Android. */
    private data class SuggestionTaxon(val nom: String, val cdNom: Int, val secondaire: String? = null)

    enum class Lecture(val id: String) {
        FLUX("flux"),
        CHAINE("chaine"),
    }

    data class Preparation(val taxons: Int, val noms: Int, val tailleFichiers: Long, val duree: Duration) {
        fun enLigne(): String =
            "MESURE_TAXREF preparation taxons=$taxons noms=$noms fichiers=${mo(tailleFichiers)} " +
                "duree=${duree.inWholeMilliseconds}ms"
    }

    data class Chargement(
        val lecture: Lecture,
        val taxons: Int,
        val noms: Int,
        val tailleFichiers: Long,
        /** Au-dessus de l'occupation de départ. Sur Android, déchets non encore ramassés compris. */
        val pic: Long,
        /** Ce qui reste occupé une fois les index construits et le ramasse-miettes passé. */
        val retenu: Long,
        val duree: Duration,
        val reference: Long,
    ) {
        fun enLigne(): String =
            "MESURE_TAXREF lecture=${lecture.id} taxons=$taxons noms=$noms fichiers=${mo(tailleFichiers)} " +
                "pic=${mo(pic)} retenu=${mo(retenu)} duree=${duree.inWholeMilliseconds}ms " +
                "reference=${mo(reference)} pic_sur_reference=${pourcent(pic, reference)}"
    }

    /** Dossier des fichiers de mesure : le cache de l'appli sur Android, son tmp/ sur iOS. */
    val dossierParDefaut: Path get() = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "mesure-taxref"

    suspend fun preparer(nombreTaxons: Int, dossier: Path = dossierParDefaut): Preparation =
        withContext(Dispatchers.Default) {
            val (noms, duree) = measureTimedValue {
                systeme.deleteRecursively(dossier)
                systeme.createDirectories(dossier)
                ecrireFichiers(genererTaxons(nombreTaxons, Random(42)), dossier)
            }
            Preparation(nombreTaxons, noms, tailleFichiers(dossier), duree)
        }

    /** Nombre de taxons des fichiers en place, null s'il n'y en a pas. */
    fun taxonsPrepares(dossier: Path = dossierParDefaut): Int? =
        if (systeme.exists(dossier / TAXONS)) systeme.read(dossier / TAXONS) { readUtf8().trim().toIntOrNull() } else null

    suspend fun mesurer(lecture: Lecture, dossier: Path = dossierParDefaut): Chargement =
        withContext(Dispatchers.Default) {
            ramasserEtAttendre()
            val depart = memoireUtilisee()
            var pic = depart
            val (index, duree) = coroutineScope {
                val echantillonneur = launch {
                    while (isActive) {
                        pic = max(pic, memoireUtilisee())
                        delay(5)
                    }
                }
                measureTimedValue { chargerIndex(lecture, dossier) }.also { echantillonneur.cancelAndJoin() }
            }
            pic = max(pic, memoireUtilisee())
            ramasserEtAttendre()
            val retenu = memoireUtilisee() - depart
            // Lus après la mesure : les index restent vivants jusque-là.
            Chargement(
                lecture = lecture,
                taxons = index.sciParCdNom.size,
                noms = index.parNom.size,
                tailleFichiers = tailleFichiers(dossier),
                pic = pic - depart,
                retenu = retenu,
                duree = duree,
                reference = memoireReference(),
            )
        }

    private val systeme = FileSystem.SYSTEM

    private suspend fun ramasserEtAttendre() {
        repeat(2) {
            ramasserMiettes()
            delay(150)
        }
    }

    private fun tailleFichiers(dossier: Path): Long =
        systeme.list(dossier).sumOf { systeme.metadata(it).size ?: 0L }

    // --- Les fichiers de filesDir/taxref/ (TaxRefCache.kt:91-106), avec leurs types ---

    private class Fichier<T>(val nom: String, val serialiseur: KSerializer<T>)

    private val CACHE = Fichier("cache_v3.json", MapSerializer(String.serializer(), EntreeTaxRef.serializer()))
    private val GROUPES = Fichier("groupes_v1.json", MapSerializer(String.serializer(), String.serializer()))
    private val GROUPES1 = Fichier("groupes1_v1.json", MapSerializer(String.serializer(), String.serializer()))
    private val REGNES = Fichier("regnes_v1.json", MapSerializer(String.serializer(), String.serializer()))
    private val INDEX_TAXON =
        Fichier("index_taxon_v1.json", MapSerializer(String.serializer(), ListSerializer(Int.serializer())))
    private val LISTES = Fichier("listes_v1.json", MapSerializer(String.serializer(), ListSerializer(Int.serializer())))
    private val VERNS = Fichier("verns_v1.json", MapSerializer(String.serializer(), ListSerializer(String.serializer())))
    private val SCI = Fichier("sci_v1.json", MapSerializer(String.serializer(), String.serializer()))
    private const val TAXONS = "taxons.txt"

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> ecrire(dossier: Path, fichier: Fichier<T>, valeur: T) {
        systeme.write(dossier / fichier.nom) { Json.encodeToBufferedSink(fichier.serialiseur, valeur, this) }
    }

    /** La synchronisation : écrit les huit fichiers, rend le nombre de noms du cache principal. */
    private fun ecrireFichiers(taxons: List<TaxonSynthetique>, dossier: Path): Int {
        // Clés uniques par nom : le nom scientifique d'abord, comme le départage de la synchro.
        val cache = LinkedHashMap<String, EntreeTaxRef>()
        for (t in taxons) cache.getOrPut(normaliser(t.sciNom)) { EntreeTaxRef(t.cdNom, t.sciNom) }
        for (t in taxons) for (vern in t.verns) cache.getOrPut(normaliser(vern)) { EntreeTaxRef(t.cdNom, t.sciNom, vern) }
        ecrire(dossier, CACHE, cache)
        ecrire(dossier, GROUPES, taxons.associate { it.cdNom.toString() to it.groupe.groupe2 })
        ecrire(dossier, GROUPES1, taxons.associate { it.cdNom.toString() to it.groupe.groupe1 })
        ecrire(dossier, REGNES, taxons.associate { it.cdNom.toString() to it.groupe.regne })
        ecrire(dossier, INDEX_TAXON, taxons.groupBy({ it.groupe.taxon }, { it.cdNom }))
        ecrire(dossier, LISTES, taxons.associate { it.cdNom.toString() to it.listes })
        ecrire(dossier, VERNS, taxons.filter { it.verns.isNotEmpty() }.associate { it.cdNom.toString() to it.verns })
        ecrire(dossier, SCI, taxons.associate { it.cdNom.toString() to it.sciNom })
        systeme.write(dossier / TAXONS) { writeUtf8(taxons.size.toString()) }
        return cache.size
    }

    // --- Le chargement et les index de TaxRefCache (TaxRefCache.kt:123-150) ---

    /** Tout ce que `TaxRefCache` et `TaxRefLocal` gardent en mémoire après une session de saisie
     *  sur un protocole : les huit fichiers relus, puis leurs index dérivés (memoïzés). */
    private class Index(
        val parNom: Map<String, EntreeTaxRef>,
        val groupes: Map<String, String>,
        val groupes1: Map<String, String>,
        val regnes: Map<String, String>,
        val indexTaxon: Map<String, List<Int>>,
        val listes: Map<String, List<Int>>,
        val vernsParCdNom: Map<Int, List<String>>,
        val sciParCdNom: Map<Int, String>,
        val entreesParCdNom: Map<Int, EntreeTaxRef>,
        val indexMots: Map<String, Set<Int>>,
        val tousLesNoms: List<String>,
        val cdNomsDansListe: Set<Int>,
        val nomsParListe: List<String>,
        val indexResolutionListe: Map<String, EntreeTaxRef>,
        val propositions: Map<String, SuggestionTaxon>,
    )

    @OptIn(ExperimentalSerializationApi::class)
    private fun chargerIndex(lecture: Lecture, dossier: Path): Index {
        fun <T> lire(fichier: Fichier<T>): T = when (lecture) {
            Lecture.FLUX -> systeme.read(dossier / fichier.nom) { Json.decodeFromBufferedSource(fichier.serialiseur, this) }
            Lecture.CHAINE -> Json.decodeFromString(fichier.serialiseur, systeme.read(dossier / fichier.nom) { readUtf8() })
        }

        val parNom = lire(CACHE)
        // vernsParCdNom() et sciNomsParCdNom() : clés converties en Int.
        val verns = lire(VERNS).let { m -> HashMap<Int, List<String>>(m.size).apply { for ((k, v) in m) k.toIntOrNull()?.let { put(it, v) } } }
        val sci = lire(SCI).let { m -> HashMap<Int, String>(m.size).apply { for ((k, v) in m) k.toIntOrNull()?.let { put(it, v) } } }

        // entreesParCdNom() : une entrée par taxon, de préférence avec nom français.
        val entreesParCdNom = HashMap<Int, EntreeTaxRef>(parNom.size)
        for (e in parNom.values) {
            val existante = entreesParCdNom[e.cdNom]
            if (existante == null || (existante.nomFrOriginal == null && e.nomFrOriginal != null)) entreesParCdNom[e.cdNom] = e
        }
        for ((cd, nomSci) in sci) {
            if (cd in entreesParCdNom || nomSci.isEmpty()) continue
            entreesParCdNom[cd] = EntreeTaxRef(cd, nomSci, verns[cd]?.firstOrNull())
        }

        // indexMots() : mot normalisé → cd_nom des noms qui le contiennent.
        val indexMots = HashMap<String, MutableSet<Int>>()
        for ((cle, entree) in parNom) {
            for (mot in cle.split(SEPARATEURS)) {
                if (mot.length < 2) continue
                indexMots.getOrPut(mot) { HashSet() }.add(entree.cdNom)
            }
        }

        // La session se fait sur un protocole : sa liste sert aux suggestions et à la résolution.
        val listes = lire(LISTES)
        val cdNomsDansListe = HashSet<Int>()
        for ((cd, ids) in listes) if (LISTE_PROTOCOLE in ids) cd.toIntOrNull()?.let(cdNomsDansListe::add)
        val nomsParListe = parNom.asSequence().filter { (_, e) -> e.cdNom in cdNomsDansListe }.map { it.key }.toList()

        return Index(
            parNom = parNom,
            groupes = lire(GROUPES),
            groupes1 = lire(GROUPES1),
            regnes = lire(REGNES),
            indexTaxon = lire(INDEX_TAXON),
            listes = listes,
            vernsParCdNom = verns,
            sciParCdNom = sci,
            entreesParCdNom = entreesParCdNom,
            indexMots = indexMots,
            tousLesNoms = parNom.keys.toList(),
            cdNomsDansListe = cdNomsDansListe,
            nomsParListe = nomsParListe,
            indexResolutionListe = indexResolutionListe(cdNomsDansListe, entreesParCdNom, verns),
            propositions = propositions(nomsParListe, parNom),
        )
    }

    /** `TaxRefCache.indexResolutionListe` : vide au-delà de 60 000 taxons, la liste ne restreint
     *  alors presque rien et la résolution globale suffit. */
    private fun indexResolutionListe(
        autorises: Set<Int>,
        parCdNom: Map<Int, EntreeTaxRef>,
        verns: Map<Int, List<String>>,
    ): Map<String, EntreeTaxRef> {
        if (autorises.size >= SEUIL_INDEX_LISTE) return emptyMap()
        // clé → (rang, cd_nom, nom français d'origine)
        val meilleur = HashMap<String, Triple<Int, Int, String?>>()
        fun proposer(cle: String, rang: Int, cd: Int, nomFr: String?) {
            if (cle.isEmpty()) return
            val actuel = meilleur[cle]
            if (actuel == null || rang < actuel.first || (rang == actuel.first && cd < actuel.second)) {
                meilleur[cle] = Triple(rang, cd, nomFr)
            }
        }
        for (cd in autorises) {
            parCdNom[cd]?.sciNom?.takeIf { it.isNotEmpty() }?.let { proposer(normaliser(it), -1, cd, null) }
            verns[cd].orEmpty().forEachIndexed { rang, nom -> proposer(normaliser(nom), rang, cd, nom) }
        }
        return meilleur.mapValues { (_, v) -> EntreeTaxRef(v.second, parCdNom[v.second]?.sciNom.orEmpty(), v.third) }
    }

    /** `TaxRefLocal.Propositions` pour tous les groupes de la liste du protocole : la liste
     *  proposée, ses clés normalisées, et l'index de résolution qui en sort. */
    private fun propositions(nomsParListe: List<String>, parNom: Map<String, EntreeTaxRef>): Map<String, SuggestionTaxon> {
        val liste = nomsParListe.mapNotNull { cle ->
            parNom[cle]?.let { e -> SuggestionTaxon(e.nomFrOriginal ?: e.sciNom, e.cdNom, e.sciNom.takeIf { e.nomFrOriginal != null }) }
        }
        val normalisees = liste.map { normaliser(it.nom) to it }
        return LinkedHashMap<String, SuggestionTaxon>().apply { for ((cle, s) in normalisees) getOrPut(cle) { s } }
    }

    /** Copie de `TaxRefCache.normaliser` (GeoMys-Android) : minuscules, accents dépliés. */
    private fun normaliser(nom: String): String {
        val bas = nom.trim().lowercase()
        var i = 0
        while (i < bas.length && deplier(bas[i]) == bas[i]) i++
        if (i == bas.length) return bas
        val sb = StringBuilder(bas.length)
        sb.append(bas, 0, i)
        while (i < bas.length) {
            sb.append(deplier(bas[i]))
            i++
        }
        return sb.toString()
    }

    private fun deplier(c: Char): Char = when (c) {
        'à', 'â', 'ä' -> 'a'
        'é', 'è', 'ê', 'ë' -> 'e'
        'î', 'ï' -> 'i'
        'ô', 'ö' -> 'o'
        'ù', 'û', 'ü' -> 'u'
        'ç' -> 'c'
        else -> c
    }

    private val SEPARATEURS = Regex("[ \\-]+")
    private const val SEUIL_INDEX_LISTE = 60_000

    // --- Le référentiel synthétique ---
    //
    // Calé sur les fichiers de l'appareil de référence de GeoMys-Android, synchronisés le
    // 17 octobre 2026 (100 468 taxons, 22,9 Mo en tout) : noms scientifiques sans auteur
    // d'environ 20 caractères (sci_v1.json, 3,2 Mo), un nom français pour un taxon sur quatre
    // environ (verns_v1.json, 0,9 Mo), 1,5 liste par taxon (listes_v1.json, 1,7 Mo). La liste qui
    // couvre tout et les protocoles jusqu'à 45 000 taxons viennent des commentaires de TaxRefCache.

    private class TaxonSynthetique(
        val cdNom: Int,
        val sciNom: String,
        val verns: List<String>,
        val groupe: Groupe,
        val listes: List<Int>,
    )

    private fun genererTaxons(nombre: Int, alea: Random): List<TaxonSynthetique> {
        // Genres, épithètes et mots des noms français se répètent comme dans TaxRef (six espèces
        // par genre, une épithète pour trois taxons) : c'est ce qui dimensionne l'index des mots.
        val genres = List(max(1, nombre / 6)) { mot(alea).replaceFirstChar { it.uppercase() } }
        val epithetes = List(max(1, nombre / 3)) { mot(alea) }
        val motsFrancais = List(max(1, nombre / 10)) { mot(alea) }
        return List(nombre) { i ->
            val groupe = tirerGroupe(alea)
            val infra = if (alea.nextInt(100) < 15) " ${RANGS_INFRA.random(alea)} ${epithetes.random(alea)}" else ""
            val verns = when (alea.nextInt(1000)) {
                in 0..199 -> listOf(nomFrancais(alea, groupe, motsFrancais))
                in 200..234 -> List(2 + alea.nextInt(2)) { nomFrancais(alea, groupe, motsFrancais) }
                else -> emptyList()
            }
            val listes = buildList {
                add(LISTE_TOUT)
                if (alea.nextInt(100) < 45) add(LISTE_PROTOCOLE)
                for (autre in AUTRES_LISTES) if (alea.nextInt(1000) < 25) add(autre)
            }
            val sciNom = "${genres.random(alea)} ${epithetes.random(alea)}$infra"
            TaxonSynthetique(100_000 + i * 7, sciNom, verns, groupe, listes)
        }
    }

    /** Trois ou quatre syllabes : 6 à 16 lettres, 8 en moyenne. */
    private fun mot(alea: Random): String =
        buildString { repeat(3 + alea.nextInt(2)) { append(SYLLABES.random(alea)) } }

    /** « Criquet bolimédo des marais ». */
    private fun nomFrancais(alea: Random, groupe: Groupe, mots: List<String>): String {
        val qualificatif = if (alea.nextBoolean()) " ${QUALIFICATIFS.random(alea)}" else ""
        return "${groupe.tetes.random(alea)} ${mots.random(alea)}$qualificatif"
    }

    private fun tirerGroupe(alea: Random): Groupe {
        var tirage = alea.nextInt(GROUPES_TAXONOMIQUES.sumOf { it.poids })
        for (groupe in GROUPES_TAXONOMIQUES) {
            tirage -= groupe.poids
            if (tirage < 0) return groupe
        }
        return GROUPES_TAXONOMIQUES.last()
    }

    private class Groupe(
        val regne: String,
        val groupe1: String,
        val groupe2: String,
        val taxon: String,
        val poids: Int,
        val tetes: List<String>,
    )

    /** Répartition proche d'une liste « toutes espèces » régionale : surtout insectes et plantes. */
    private val GROUPES_TAXONOMIQUES = listOf(
        Groupe("Animalia", "Arthropodes", "Insectes", "INSECTE", 40, listOf("Criquet", "Papillon", "Bourdon", "Scarabée", "Punaise", "Mouche", "Coccinelle", "Sauterelle")),
        Groupe("Plantae", "Trachéophytes", "Angiospermes", "PLANTE", 30, listOf("Orchis", "Gentiane", "Œillet", "Saxifrage", "Laîche", "Renoncule", "Véronique", "Gaillet")),
        Groupe("Fungi", "Basidiomycètes", "Champignons", "FONGE", 8, listOf("Bolet", "Russule", "Amanite", "Cortinaire", "Lactaire")),
        Groupe("Animalia", "Arthropodes", "Arachnides", "INVERTEBRES", 6, listOf("Araignée", "Épeire", "Opilion", "Tique")),
        Groupe("Animalia", "Mollusques", "Gastéropodes", "MOLLUSQUE", 4, listOf("Escargot", "Limace", "Bulime")),
        Groupe("Animalia", "Chordés", "Oiseaux", "OISEAU", 4, listOf("Mésange", "Fauvette", "Pic", "Bruant", "Pipit", "Busard")),
        Groupe("Animalia", "Chordés", "Mammifères", "MAMMIFERE", 2, listOf("Musaraigne", "Campagnol", "Murin", "Pipistrelle")),
        Groupe("Animalia", "Chordés", "Poissons", "POISSON", 2, listOf("Vairon", "Loche", "Truite", "Chabot")),
        Groupe("Animalia", "Chordés", "Reptiles", "REPTILE", 2, listOf("Lézard", "Couleuvre", "Vipère")),
        Groupe("Animalia", "Chordés", "Amphibiens", "BATRACIEN", 2, listOf("Grenouille", "Crapaud", "Triton", "Salamandre")),
    )

    private val QUALIFICATIFS = listOf(
        "commun", "petit", "grand", "noir", "blanc", "doré", "vert", "rouge", "des bois", "des prés",
        "à ailes", "des marais", "rayé", "tacheté", "cendré", "alpin", "des Pyrénées", "méridional",
        "à cou blanc", "des rochers", "de montagne", "élégant", "trompeur", "velu",
    )

    private val SYLLABES = listOf(
        "ba", "be", "bi", "bo", "ca", "ce", "ci", "co", "da", "de", "di", "do", "fa", "fe", "fi", "la",
        "le", "li", "lo", "lu", "ma", "me", "mi", "mo", "na", "ne", "ni", "no", "pa", "pe", "pi", "po",
        "ra", "re", "ri", "ro", "ru", "sa", "se", "si", "so", "ta", "te", "ti", "to", "tu", "va", "ve",
        "vi", "phy", "tho", "chlo", "stra", "pter", "cor", "mel", "gas", "nyc", "rhi", "scel", "dra", "bra",
    )

    private val RANGS_INFRA = listOf("subsp.", "var.")

    /** Six listes : une qui couvre tout (la 100, comme sur le serveur de référence), celle du
     *  protocole de la session (45 % des taxons), quatre petites. */
    private const val LISTE_TOUT = 100
    private const val LISTE_PROTOCOLE = 101
    private val AUTRES_LISTES = listOf(102, 103, 104, 105)

    private fun mo(octets: Long): String {
        val dixiemes = (abs(octets) * 10 + 524_288) / 1_048_576
        return "${if (octets < 0) "-" else ""}${dixiemes / 10}.${dixiemes % 10}Mo"
    }

    private fun pourcent(part: Long, total: Long): String {
        if (total <= 0) return "?"
        val milliemes = part * 1000 / total
        return "${milliemes / 10}.${abs(milliemes % 10)}%"
    }
}
