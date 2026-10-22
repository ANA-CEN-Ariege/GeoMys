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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/ConfigGeoNatureFragment.kt :
// sauvegarderChamps, reinitialiserSelectionsOcctax, majBandeauRechargement, updateAvertissementListe,
// updateCacheInfo, montrerTaxonsParListe). Ce qui touchait aux réglages et aux caches quitte le
// fragment pour le socle ; l'écran ne garde que l'affichage.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.GeoNatureListe
import fr.ariegenature.geomys.model.GeoNatureObservateur
import fr.ariegenature.geomys.store.io.listeDepuisJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ce qu'a changé l'enregistrement de la connexion saisie. */
enum class ChangementConnexion {
    /** URL, identifiant et mot de passe identiques à ceux enregistrés. */
    AUCUN,

    /** Seul le mot de passe a changé : même serveur, même compte. */
    MOT_DE_PASSE,

    /** L'URL ou l'identifiant a changé : les sélections de l'ancienne identité ont été vidées. */
    IDENTITE,
}

/** Enregistre la connexion saisie dans Paramètres (`sauvegarderChamps` de GeoMys-Android).
 *
 *  Tout changement invalide les caches de SESSION : le jeton du mot de passe précédent n'a plus
 *  cours. L'IDENTITÉ, c'est l'instance (URL) ou le compte (identifiant), pas le mot de passe : une
 *  rotation ou une faute de frappe corrigée ne change ni de serveur ni de compte, donc les
 *  sélections, les caches propres à l'instance (nomenclatures, stations du serveur) et sa version
 *  restent valides. Changer d'identité purge en plus
 *  les pictogrammes et le cache disque des suivis, dont les ids appartiennent à l'ancienne
 *  instance, et réinitialise les trois sélections OCCTAX : un id de liste ou d'observateur peut
 *  exister sur les deux serveurs et y désigner autre chose. */
fun ServicesGeoNature.enregistrerConnexion(url: String, login: String, motDePasse: String): ChangementConnexion {
    val ancien = Triple(config.urlServeur, config.login, config.motDePasse)
    config.urlServeur = url
    config.login = login
    config.motDePasse = motDePasse
    val nouveau = Triple(config.urlServeur, config.login, config.motDePasse)
    if (ancien == nouveau) return ChangementConnexion.AUCUN
    val identiteChangee = ancien.first != nouveau.first || ancien.second != nouveau.second
    if (!identiteChangee) {
        // Même serveur, même compte : seule la session tombe. Les nomenclatures, les stations du
        // serveur et la version restent ; les vider laissait les formulaires sans listes hors réseau,
        // sans qu'aucun rechargement ne soit demandé (audit du 21 octobre, M29).
        invalidationCaches.invaliderSession()
        return ChangementConnexion.MOT_DE_PASSE
    }
    invalidationCaches.invaliderCachesSession()
    pictos.vider()
    monitoringCache.vider()
    // La version affichée appartient à l'ancienne instance ; le nouveau serveur sera jugé à son
    // premier test de connexion.
    config.versionGeoNatureServeur = ""
    config.serveurCompatible = true
    config.reinitialiserSelectionsOcctax()
    return ChangementConnexion.IDENTITE
}

/** Vide les sélections par défaut d'OCCTAX (jeu de données, liste, observateur) et les caches JSON
 *  qui peuplent leurs menus, plus les champs additionnels OCCTAX et les observateurs d'OccHab.
 *  Appelé par « Vider le cache » et au changement d'identité serveur. */
fun GeoNatureConfig.reinitialiserSelectionsOcctax() {
    idDataset = ""
    nomDataset = ""
    taxaListeId = ""
    observateurDefautId = ""
    observateurDefautNom = ""
    datasetsCacheJson = ""
    listesCacheJson = ""
    observateursCacheJson = ""
    observateursOcchabCacheJson = ""
    additionalFieldsOcctaxJson = ""
}

/** Texte du bandeau orange en tête des Paramètres, ou null s'il n'y a rien à signaler : liste de
 *  taxons partiellement téléchargée, qui bloque la saisie ; ou, sans rien bloquer, une mise à jour de
 *  l'application qui lit des données nouvelles. Aucun accès de secours vers les saisies n'accompagne
 *  ce bandeau : décision produit de 2026-09-18 (GeoMys-Android), le blocage de Paramètres est entier. */
fun texteBandeauRechargement(config: GeoNatureConfig, taxRef: TaxRefCache): String? {
    val incompletes = taxRef.listesIncompletes
    if (incompletes.isEmpty()) {
        if (config.versionDonneesChargees >= GeoNatureConfig.VERSION_DONNEES_REQUISE || taxRef.versionSauvegardee == null) return null
        return "Mise à jour de l'application : rechargez les données quand vous aurez du réseau, pour " +
            "profiter de ses nouveautés. L'application reste utilisable d'ici là."
    }
    return "Chargement incomplet : la liste de taxons ${incompletes.joinToString(", ")} n'a pas été " +
        "téléchargée en entier (réseau interrompu ou serveur indisponible).\n" +
        "Relancez « Charger les données » — sans quoi des espèces pourtant valides seraient " +
        "refusées à la saisie. Vos saisies en attente sont conservées."
}

/** Message sous le choix de la liste ; [avertissement] le colore en orange, sinon simple
 *  information. */
data class MessageListe(val texte: String, val avertissement: Boolean)

/** Ce que contient le cache pour la liste [idConfigure] : absente du cache, vide (la saisie
 *  n'aurait rien à proposer), ou son nombre de taxons. Null tant qu'aucune liste n'est choisie ou
 *  que rien n'est chargé. Parcourt le cache TaxRef : à appeler hors du fil principal. */
fun messageListe(taxRef: TaxRefCache, idConfigure: Int?): MessageListe? {
    val listesCache = taxRef.listesSynchronisees
    if (idConfigure == null || taxRef.count == 0 || listesCache.isEmpty()) return null
    if (idConfigure !in listesCache) {
        return MessageListe(
            "⚠ La liste $idConfigure n'est pas dans le cache (${listesCache.size} listes synchronisées). " +
                "Resynchroniser pour l'ajouter.",
            avertissement = true,
        )
    }
    val n = taxRef.cdNomsDansListe(idConfigure).size
    if (n == 0) {
        return MessageListe(
            "⚠ La liste $idConfigure ne contient aucun taxon — la saisie n'aura rien à proposer. " +
                "Vérifier la liste côté serveur.",
            avertissement = true,
        )
    }
    return MessageListe("Liste $idConfigure : $n taxon${if (n > 1) "s" else ""} en cache.", avertissement = false)
}

/** Compteurs du résumé « Chargement des données ». */
data class CompteursCache(
    /** Taxons UNIQUES (cd_nom distincts), pas le nombre de clés de noms. */
    val taxons: Int,
    val nomenclatures: Int,
    /** Protocoles de suivi accessibles (cache déjà filtré CRUVED). */
    val protocoles: Int,
    val listes: Int,
    val observateurs: Int,
    /** Stations OccHab de l'utilisateur gardées pour le hors-ligne. */
    val stationsOccHab: Int,
) {
    /** « Détails » n'a de sens qu'avec des taxons en cache. */
    val detailsActif: Boolean get() = taxons > 0

    /** « Vider le cache » : actif dès qu'un cache synchronisé contient quelque chose. */
    val viderActif: Boolean get() = taxons > 0 || nomenclatures > 0 || protocoles > 0 || stationsOccHab > 0
}

/** Compte le contenu des caches. Lourd (le cache TaxRef est matérialisé, les caches JSON des
 *  réglages relus) : fait hors du fil principal, comme sous Android. */
suspend fun ServicesGeoNature.compteursCache(): CompteursCache = withContext(Dispatchers.Default) {
    CompteursCache(
        taxons = taxRef.nbTaxonsUniques,
        nomenclatures = nomenclatures.count,
        protocoles = monitoringModules.countModulesEnCache(),
        listes = listeDepuisJson(GeoNatureListe.serializer(), config.listesCacheJson).size,
        observateurs = listeDepuisJson(GeoNatureObservateur.serializer(), config.observateursCacheJson).size,
        stationsOccHab = stationsServeur.count,
    )
}

/** Une liste du dialogue « Taxons par listes ». */
data class LigneTaxonsParListe(val idListe: Int, val nomListe: String, val libelle: String)

/** Contenu du dialogue « Taxons par listes » : tout le cache de taxons regroupé par liste, par
 *  effectif décroissant, indépendamment de la liste sélectionnée. */
data class TaxonsParListes(val sousTitre: String, val lignes: List<LigneTaxonsParListe>)

/** Le contenu du dialogue « Taxons par listes », ou null si aucun taxon n'est rattaché à une
 *  liste (l'écran affiche alors un message bref). Parcourt le cache : hors du fil principal. */
suspend fun ServicesGeoNature.taxonsParListes(): TaxonsParListes? = withContext(Dispatchers.Default) {
    val comptes = taxRef.comptesParListe()
    if (comptes.isEmpty()) return@withContext null
    val nomsListes = listeDepuisJson(GeoNatureListe.serializer(), config.listesCacheJson).associate { it.id to it.nom }
    // Listes rattachées à des protocoles de suivi, et nombre de taxons UNIQUES qu'elles couvrent
    // (union, sans double compte).
    val listesProtocoles = monitoringModules.listesTaxonomieProtocolesEnCache()
    val cdNomsProtocoles = listesProtocoles.flatMap { taxRef.cdNomsDansListe(it) }.toSet()
    val lignes = comptes.entries.sortedByDescending { it.value }.map { (id, n) ->
        val nom = nomsListes[id] ?: "Liste $id"
        val marqueProto = if (id in listesProtocoles) " · protocole" else ""
        LigneTaxonsParListe(id, nom, "$nom ($id) — $n taxon${if (n > 1) "s" else ""}$marqueProto")
    }
    val sousTitre = buildString {
        append("Touchez une liste pour voir ses taxons")
        if (cdNomsProtocoles.isNotEmpty()) append("  ·  ${cdNomsProtocoles.size} via protocoles")
    }
    TaxonsParListes(sousTitre, lignes)
}

