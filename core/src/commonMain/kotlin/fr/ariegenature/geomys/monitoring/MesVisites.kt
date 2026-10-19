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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SaisiesEnAttenteFragment.kt) :
// « Mes visites », la file d'envoi des Suivis présentée en arbre protocole → groupe → saisies, avec ses onglets,
// son résumé (resumeOutbox, porté tel quel), les actions de chaque ligne et les phrases accordées au type des
// saisies. L'écran et ses dialogues restent dans l'interface.

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.network.OutboxEnvoi
import fr.ariegenature.geomys.util.FormatDate

/** Résumé de l'écran « Mes visites » : le texte de synthèse et les deux compteurs d'onglet. */
data class ResumeOutbox(val texte: String, val nbAEnvoyer: Int, val nbEnvoyees: Int)

/**
 * Calcule le résumé — fonction PURE, pour être testable sans Android.
 *
 * Deux exigences qui se contredisaient, d'où ce calcul en deux temps :
 *
 * 1. Le LIBELLÉ compte les objets de niveau VISITE seulement (ceux placés directement sous le
 *    protocole : visite, transect, pelouse…), pas leurs enfants — les observations gonflaient le
 *    total sans correspondre à ce que l'utilisateur appelle une visite (demande terrain
 *    2026-09-03).
 * 2. Mais les COMPTEURS et la phrase « tout a été envoyé » doivent porter sur TOUT ce qui reste.
 *    Ils ne portaient que sur les visites : une observation PENDING ou ERROR sous une visite déjà
 *    SENT — un état qui existe bel et bien (`OutboxMonitoring.purgerSent` conserve un parent SENT
 *    référencé par un enfant non envoyé, et cet écran l'affiche sous « Visite déjà envoyée ») —
 *    n'était comptée nulle part. L'écran affirmait « Toutes les données ont été envoyées » alors
 *    qu'une observation naturaliste n'était jamais partie, et l'onglet annonçait « À envoyer (0) »
 *    au-dessus d'une liste non vide. L'utilisateur pouvait alors vider le cache, changer de compte
 *    ou réinstaller : donnée perdue (audit 2026-09-14, R4-C1).
 *
 * On garde donc le vocabulaire « visites » pour le décompte principal, mais on ne prétend JAMAIS
 * que tout est parti tant qu'il reste quoi que ce soit, et les observations restantes sont
 * nommées à part.
 */
fun resumeOutbox(
    toutes: List<SaisieEnAttente>,
    estNiveauVisite: (String?) -> Boolean,
): ResumeOutbox {
    fun resteAEnvoyer(s: SaisieEnAttente) =
        s.etat == SaisieEnAttente.Etat.PENDING || s.etat == SaisieEnAttente.Etat.ERROR
    val visites = toutes.filter { estNiveauVisite(it.parentObjectType) }
    val aEnvoyerTotal = toutes.count { resteAEnvoyer(it) }
    val visitesAEnvoyer = visites.count { resteAEnvoyer(it) }
    val visitesEnvoyees = visites.count { it.etat == SaisieEnAttente.Etat.SENT }
    val enfantsAEnvoyer = aEnvoyerTotal - visitesAEnvoyer
    val texte = when {
        toutes.isEmpty() -> "Aucune donnée locale."
        aEnvoyerTotal == 0 -> "Toutes les données ont été envoyées ($visitesEnvoyees)."
        else -> buildString {
            val restants = buildList {
                if (visitesAEnvoyer > 0) add("$visitesAEnvoyer visite" + (if (visitesAEnvoyer > 1) "s" else ""))
                if (enfantsAEnvoyer > 0) add("$enfantsAEnvoyer observation" + (if (enfantsAEnvoyer > 1) "s" else ""))
            }
            append(restants.joinToString(" et "))
            append(" en attente · $visitesEnvoyees envoyée")
            if (visitesEnvoyees > 1) append("s")
        }
    }
    // Les COMPTEURS d'onglet portent sur tout : l'onglet « À envoyer » doit s'accorder avec ce que
    // la liste montre réellement, enfants orphelins compris.
    return ResumeOutbox(texte, aEnvoyerTotal, toutes.count { it.etat == SaisieEnAttente.Etat.SENT })
}

/** Ce que « Mes visites » lit du cache des Suivis (aucun réseau) : libellés des protocoles, des
 *  types et des objets, ancêtres d'un objet, noms des taxons. */
interface CacheSuivis {
    fun labelModule(moduleCode: String): String?
    fun labelType(moduleCode: String, type: String): String?
    fun genreType(moduleCode: String, type: String): String?
    fun labelObjet(moduleCode: String, type: String, id: Int): String?

    /** Du parent direct de l'objet vers le plus haut ancêtre : (type, id, libellé). */
    fun chaineParents(moduleCode: String, type: String, id: Int): List<Triple<String, Int, String>>
    fun nomTaxon(cdNom: Int): String?
}

/** Le cache des Suivis de l'appli. */
fun ServicesGeoNature.cacheSuivis(): CacheSuivis = object : CacheSuivis {
    override fun labelModule(moduleCode: String) = monitoringModules.labelModuleEnCache(moduleCode)
    override fun labelType(moduleCode: String, type: String) = monitoringObjets.labelTypeEnCache(moduleCode, type)
    override fun genreType(moduleCode: String, type: String) = monitoringObjets.genreTypeEnCache(moduleCode, type)
    override fun labelObjet(moduleCode: String, type: String, id: Int) = monitoringObjets.labelObjetEnCache(moduleCode, type, id)
    override fun chaineParents(moduleCode: String, type: String, id: Int) = monitoringObjets.chaineParentsEnCache(moduleCode, type, id)
    override fun nomTaxon(cdNom: Int) = taxRef.nomAffichageParCdNom(cdNom)
}

/** Les onglets de « Mes visites ». */
enum class OngletMesVisites { A_ENVOYER, ENVOYEES }

/** Une entrée de la liste : la hiérarchie protocole → groupe → saisies, aplatie. */
sealed interface ItemMesVisites {
    /** Liste vide : pourquoi. */
    data class Vide(val texte: String) : ItemMesVisites

    /** « 🔬 STOM ARIÈGE ». */
    data class Protocole(val moduleCode: String, val texte: String) : ItemMesVisites

    /** « 📍 Point d'écoute : Col de Port » : l'objet serveur auquel le groupe se rattache. */
    data class Groupe(val racine: String, val texte: String) : ItemMesVisites

    /** Une saisie, à sa [profondeur] dans son groupe (0 : la racine). */
    data class Ligne(val saisie: SaisieEnAttente, val profondeur: Int, val titre: String, val sousTitre: String) : ItemMesVisites
}

/** Une action proposée au toucher d'une ligne. */
sealed interface OptionSaisie {
    val libelle: String

    data object Reessayer : OptionSaisie {
        override val libelle = "Réessayer"
    }

    data class EnvoyerGroupe(val nbDonnees: Int) : OptionSaisie {
        override val libelle get() = "Envoyer ce groupe ($nbDonnees données)"
    }

    data class Supprimer(override val libelle: String) : OptionSaisie

    data object Retirer : OptionSaisie {
        override val libelle = "Retirer de la liste"
    }
}

/** « Mes visites ». */
object MesVisites {

    const val TITRE = "Mes visites"
    const val MESSAGE_VIDE = "Les données que vous enregistrez apparaîtront ici jusqu'à leur envoi."
    const val TITRE_TOUT_ENVOYER = "Tout envoyer"
    const val BOUTON_ENVOYER = "Envoyer"
    const val TITRE_CONFIGURATION = "Configuration requise"
    const val MESSAGE_CONFIGURATION = "La connexion GeoNature n'est pas configurée. Ouvrez la configuration (⚙️) avant d'envoyer."
    const val MESSAGE_ENVOI_EN_COURS = "Un envoi est déjà en cours…"
    const val PREPARATION_TOUT = "Préparation de l'envoi…"
    const val PREPARATION_GROUPE = "Préparation du groupe…"
    const val TITRE_RECAPITULATIF = "Envoi"
    const val BOUTON_TOUT_SUPPRIMER = "Tout supprimer"

    fun titreOnglet(onglet: OngletMesVisites, resume: ResumeOutbox): String = when (onglet) {
        OngletMesVisites.A_ENVOYER -> "À envoyer (${resume.nbAEnvoyer})"
        OngletMesVisites.ENVOYEES -> "Envoyées (${resume.nbEnvoyees})"
    }

    fun libelleToutEnvoyer(nb: Int) = "Tout envoyer ($nb)"

    fun messageToutEnvoyer(nb: Int) = "Envoyer les $nb donnée(s) en attente vers GeoNature ?"

    /** « Envoi 2/5 · Visite créée », sans point final orphelin. */
    fun progression(envoyees: Int, total: Int, message: String) = "Envoi $envoyees/$total · $message".trim().trimEnd('·', ' ')

    fun recapitulatif(resultat: OutboxEnvoi.Resultat, groupe: Boolean): String = buildString {
        append(if (groupe) "Envoi du groupe terminé" else "Envoi terminé")
        append(" · ${resultat.succes} succès, ${resultat.echecs} échec(s)")
        if (resultat.messages.isNotEmpty()) {
            append("\n\n")
            append(resultat.messages.joinToString("\n"))
        }
    }

    fun messageSupprimees(n: Int) = "$n donnée(s) supprimée(s)"

    private fun resteAEnvoyer(s: SaisieEnAttente) = s.etat == SaisieEnAttente.Etat.PENDING || s.etat == SaisieEnAttente.Etat.ERROR

    /** Les données que « Tout envoyer » ferait partir : en attente ou en échec, dans un protocole
     *  où l'envoi est permis. */
    fun nbEnvoyables(toutes: List<SaisieEnAttente>, envoiAutorise: (String) -> Boolean): Int {
        val memo = mutableMapOf<String, Boolean>()
        return toutes.count { s -> resteAEnvoyer(s) && memo.getOrPut(s.moduleCode) { envoiAutorise(s.moduleCode) } }
    }

    // ─── Terminologie : chaque saisie est nommée par le LABEL SERVEUR de son type, accordé ───

    private val VOYELLES = setOf('a', 'à', 'â', 'e', 'é', 'è', 'ê', 'i', 'î', 'o', 'ô', 'u', 'ù', 'û', 'h', 'y')
    private fun voyelleInitiale(mot: String) = mot.firstOrNull()?.let { it in VOYELLES } == true

    fun labelDuType(s: SaisieEnAttente, cache: CacheSuivis): String =
        cache.labelType(s.moduleCode, s.objectType) ?: s.objectType.replaceFirstChar { it.uppercase() }

    private fun masculin(s: SaisieEnAttente, cache: CacheSuivis) = cache.genreType(s.moduleCode, s.objectType).equals("M", ignoreCase = true)

    /** « cette visite » / « ce passage » / « cet inventaire » / « cette observation ». */
    fun ceTypeDe(s: SaisieEnAttente, cache: CacheSuivis): String {
        val mot = labelDuType(s, cache).lowercase()
        val dem = when { !masculin(s, cache) -> "cette"; voyelleInitiale(mot) -> "cet"; else -> "ce" }
        return "$dem $mot"
    }

    /** « la visite » / « le passage » / « l'observation ». */
    fun leTypeDe(s: SaisieEnAttente, cache: CacheSuivis): String {
        val mot = labelDuType(s, cache).lowercase()
        val art = when { voyelleInitiale(mot) -> "l'"; masculin(s, cache) -> "le "; else -> "la " }
        return "$art$mot"
    }

    fun titreSuppression(s: SaisieEnAttente, cache: CacheSuivis) = "Supprimer ${ceTypeDe(s, cache)} ?"

    /** Suppression d'une saisie qui a des enfants locaux : sans leur parent, ils ne partiraient plus. */
    fun messageSuppression(s: SaisieEnAttente, nbEnfants: Int, cache: CacheSuivis): String =
        "${ceTypeDe(s, cache).replaceFirstChar { it.uppercase() }} a $nbEnfants donnée(s) locale(s) " +
            "rattachée(s) (par ex. des observations). Sans son parent, elles ne pourront " +
            "plus être envoyées.\n\nSupprimer ${leTypeDe(s, cache)} ET ses $nbEnfants enfant(s) ?"

    /** Le titre du menu d'une ligne : « Visite · 19/10 13:39 ». */
    fun titreOptions(s: SaisieEnAttente, cache: CacheSuivis) = "${labelDuType(s, cache)} · ${FormatDate.jourMoisHeure(s.dateLocale)}"

    // ─── Lignes ───

    private fun icone(etat: SaisieEnAttente.Etat) = when (etat) {
        SaisieEnAttente.Etat.PENDING -> "⏳"
        SaisieEnAttente.Etat.SENDING -> "🚀"
        SaisieEnAttente.Etat.SENT -> "✅"
        SaisieEnAttente.Etat.ERROR -> "⚠"
    }

    /** Le nom du taxon de la saisie (clé `cd_nom` de ses valeurs), s'il est connu du référentiel. */
    fun nomTaxon(s: SaisieEnAttente, cache: CacheSuivis): String? {
        val cdNom = try {
            when (val v = JSONObject(s.valeursJson).opt("cd_nom")) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull()
                else -> null
            }
        } catch (_: Exception) {
            null
        } ?: return null
        if (cdNom <= 0) return null
        return cache.nomTaxon(cdNom)
    }

    /** « ↳ ⏳ Merle noir » : l'état, et le taxon plutôt que le type quand la saisie en porte un.
     *  Ligne identique pour une saisie incomplète : c'est sa flèche d'envoi qui passe au rouge. */
    fun titreLigne(s: SaisieEnAttente, profondeur: Int, cache: CacheSuivis): String =
        "${if (profondeur > 0) "↳ " else ""}${icone(s.etat)} ${nomTaxon(s, cache) ?: labelDuType(s, cache)}"

    fun sousTitre(s: SaisieEnAttente): String =
        "${s.moduleCode} · ${FormatDate.jourMoisHeure(s.dateLocale)}" + (s.idServeur?.let { " · id serveur=$it" } ?: "")

    /** Le contexte d'un groupe : la chaîne des parents serveur de sa racine, du plus large au plus
     *  précis, chaque niveau préfixé du libellé de son type ; null sans parent serveur. */
    fun cheminGroupe(racine: SaisieEnAttente, cache: CacheSuivis): String? {
        val parentType = racine.parentObjectType?.takeIf { it.isNotEmpty() } ?: return null
        val parentId = racine.parentIdServeur ?: return null
        val labelDirect = cache.labelObjet(racine.moduleCode, parentType, parentId) ?: "$parentType #$parentId"
        val ancetres = cache.chaineParents(racine.moduleCode, parentType, parentId)
        return (ancetres.reversed().map { it.first to it.third } + (parentType to labelDirect))
            .joinToString(" › ") { (type, label) ->
                "${cache.labelType(racine.moduleCode, type) ?: type.replaceFirstChar { it.uppercase() }} : $label"
            }
    }

    private fun ordrePourTri(etat: SaisieEnAttente.Etat) = when (etat) {
        SaisieEnAttente.Etat.PENDING, SaisieEnAttente.Etat.SENDING -> 0
        SaisieEnAttente.Etat.ERROR -> 1
        SaisieEnAttente.Etat.SENT -> 2
    }

    private val ORDRE = compareBy<SaisieEnAttente>({ ordrePourTri(it.etat) }, { it.dateLocale })

    /**
     * La liste de l'[onglet] : par protocole (dans l'ordre de leur première racine), chaque groupe
     * (une racine et ses descendants locaux, en profondeur d'abord) sous son contexte. Une racine est
     * une saisie sans parent local, ou dont le parent n'est plus dans la file (déjà envoyé). Un
     * groupe ne passe dans « Envoyées » que lorsque TOUT son sous-arbre est SENT : tant qu'une
     * observation reste à envoyer, il reste dans « À envoyer », hiérarchie conservée.
     */
    fun items(toutes: List<SaisieEnAttente>, onglet: OngletMesVisites, cache: CacheSuivis): List<ItemMesVisites> {
        if (toutes.isEmpty()) return listOf(ItemMesVisites.Vide(MESSAGE_VIDE))
        val parParent = toutes.groupBy { it.parentUuidLocal }
        val uuidsConnus = toutes.mapTo(HashSet()) { it.uuid }
        val racines = toutes
            .filter { it.parentUuidLocal == null || it.parentUuidLocal !in uuidsConnus }
            .sortedWith(ORDRE)
        fun groupeToutEnvoye(racine: SaisieEnAttente): Boolean {
            if (racine.etat != SaisieEnAttente.Etat.SENT) return false
            val file = ArrayDeque<String>().apply { add(racine.uuid) }
            while (file.isNotEmpty()) {
                parParent[file.removeFirst()].orEmpty().forEach {
                    if (it.etat != SaisieEnAttente.Etat.SENT) return false
                    file.add(it.uuid)
                }
            }
            return true
        }
        val racinesOnglet = racines.filter { groupeToutEnvoye(it) == (onglet == OngletMesVisites.ENVOYEES) }
        if (racinesOnglet.isEmpty()) {
            return listOf(
                ItemMesVisites.Vide(
                    if (onglet == OngletMesVisites.ENVOYEES) "Aucune donnée envoyée." else "Aucune donnée en attente d'envoi.",
                ),
            )
        }
        val items = mutableListOf<ItemMesVisites>()
        racinesOnglet.groupBy { it.moduleCode }.forEach { (moduleCode, racinesModule) ->
            items += ItemMesVisites.Protocole(moduleCode, "🔬 ${(cache.labelModule(moduleCode) ?: moduleCode).uppercase()}")
            racinesModule.forEach { racine ->
                cheminGroupe(racine, cache)?.let { items += ItemMesVisites.Groupe(racine.uuid, "📍 $it") }
                fun ajouterArbre(s: SaisieEnAttente, profondeur: Int) {
                    items += ItemMesVisites.Ligne(s, profondeur, titreLigne(s, profondeur, cache), sousTitre(s))
                    parParent[s.uuid].orEmpty().sortedWith(ORDRE).forEach { ajouterArbre(it, profondeur + 1) }
                }
                ajouterArbre(racine, 0)
            }
        }
        return items
    }

    /** Les descendants locaux de [s], en largeur d'abord. */
    fun descendants(s: SaisieEnAttente, toutes: List<SaisieEnAttente>): List<SaisieEnAttente> {
        val parParent = toutes.groupBy { it.parentUuidLocal }
        val resultat = mutableListOf<SaisieEnAttente>()
        val file = ArrayDeque<String>().apply { add(s.uuid) }
        while (file.isNotEmpty()) {
            parParent[file.removeFirst()].orEmpty().forEach {
                resultat += it
                file.add(it.uuid)
            }
        }
        return resultat
    }

    /** Une visite déjà envoyée garde sa flèche tant qu'une de ses observations reste à envoyer. */
    fun aDescendantsAEnvoyer(s: SaisieEnAttente, toutes: List<SaisieEnAttente>): Boolean = descendants(s, toutes).any(::resteAEnvoyer)

    /** Les actions du menu d'une ligne. « Envoyer ce groupe » seulement pour une saisie encore à
     *  envoyer qui a des enfants locaux : pour une saisie seule, il n'apporte rien sur « Tout
     *  envoyer ». */
    fun options(s: SaisieEnAttente, nbEnfants: Int, envoiAutorise: Boolean, cache: CacheSuivis): List<OptionSaisie> = buildList {
        if (s.etat == SaisieEnAttente.Etat.ERROR) add(OptionSaisie.Reessayer)
        if (nbEnfants > 0 && envoiAutorise && resteAEnvoyer(s)) add(OptionSaisie.EnvoyerGroupe(nbEnfants + 1))
        if (s.etat != SaisieEnAttente.Etat.SENT) add(OptionSaisie.Supprimer("Supprimer ${ceTypeDe(s, cache)}"))
        if (s.etat == SaisieEnAttente.Etat.SENT) add(OptionSaisie.Retirer)
    }

    /** Le fil d'Ariane du formulaire d'une saisie rouverte depuis « Mes visites » : le protocole,
     *  puis la chaîne des parents serveur lue dans le cache (chaque niveau garde son id : il reste
     *  cliquable et ouvre sa fiche). */
    fun filPourEdition(s: SaisieEnAttente, cache: CacheSuivis): List<FilSegment> {
        val segments = filRacineSuivis(cache.labelModule(s.moduleCode) ?: s.moduleCode).toMutableList()
        val parentType = s.parentObjectType?.takeIf { it.isNotEmpty() }
        val parentId = s.parentIdServeur
        if (parentType != null && parentId != null) {
            val labelDirect = cache.labelObjet(s.moduleCode, parentType, parentId) ?: "$parentType #$parentId"
            cache.chaineParents(s.moduleCode, parentType, parentId).reversed().forEach { (type, id, label) ->
                segments += FilSegment(type, id, label)
            }
            segments += FilSegment(parentType, parentId, labelDirect)
        }
        return segments
    }
}
