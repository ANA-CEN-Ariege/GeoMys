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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/SortiesFragment.kt et son
// SortieAdapter) : les règles et les textes de la liste « Mes saisies », sortis des vues.

package fr.ariegenature.geomys.occtax

import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.util.FormatDate

/** Les onglets de « Mes saisies ». */
enum class OngletMesSaisies(val titre: String, val messageVide: String) {
    A_ENVOYER("À envoyer", "Aucune saisie en attente d'envoi"),
    ENVOYEES("Envoyées", "Aucune saisie envoyée à GeoNature"),
    IMPORTEES("Importées", "Aucune saisie importée"),
}

/** Les règles de la liste « Mes saisies » (sorties OccTax du store). */
object MesSaisies {

    fun appartient(sortie: Sortie, onglet: OngletMesSaisies): Boolean = when (onglet) {
        OngletMesSaisies.ENVOYEES -> sortie.envoyeGeoNature
        OngletMesSaisies.IMPORTEES -> sortie.estImportee
        OngletMesSaisies.A_ENVOYER -> !sortie.envoyeGeoNature && !sortie.estImportee
    }

    /** Les sorties de l'onglet, dans l'ordre du store (la plus récente en tête). */
    fun filtrer(toutes: List<Sortie>, onglet: OngletMesSaisies): List<Sortie> = toutes.filter { appartient(it, onglet) }

    /** « À envoyer (3) ». */
    fun titreOnglet(toutes: List<Sortie>, onglet: OngletMesSaisies): String =
        "${onglet.titre} (${toutes.count { appartient(it, onglet) }})"

    /** Saisie à envoyer (ni envoyée ni importée) : « Continuer la saisie » possible. Une sortie déjà
     *  envoyée n'a pas vocation à être ré-éditée côté local ; une sortie importée (GPX externe)
     *  non plus. */
    fun peutEditer(sortie: Sortie): Boolean = !sortie.envoyeGeoNature && !sortie.estImportee

    /** Sortie réellement envoyable : à envoyer ET au moins une obs déterminée (cd_nom résolu) OU un
     *  relevé sans espèce — sinon rien à envoyer. */
    fun estEnvoyable(sortie: Sortie): Boolean =
        peutEditer(sortie) && sortie.observations.any { it.cdNom != null || it.releveSansEspece }

    fun envoyables(toutes: List<Sortie>): List<Sortie> = toutes.filter(::estEnvoyable)

    /** Bouton « Envoyer au serveur » d'une ligne : sortie envoyable et droit de création OCCTAX
     *  (CRUVED C, détecté à la synchro) — sans lui, le POST partirait en 403. */
    fun peutEnvoyer(sortie: Sortie, envoiAutorise: Boolean): Boolean = envoiAutorise && estEnvoyable(sortie)

    /** Les vraies observations : le placeholder « relevé sans espèce » n'en est pas une. */
    fun nbObservations(sortie: Sortie): Int = sortie.observations.count { !it.releveSansEspece }

    /** Bouton « Lister les espèces » : dès qu'il y a au moins une observation. */
    fun aDesObservations(sortie: Sortie): Boolean = nbObservations(sortie) > 0

    /** Première ligne du dernier échec d'envoi (le message complet tenait sur dix lignes et noyait
     *  la liste), ou null s'il n'y en a pas. */
    fun ligneErreur(sortie: Sortie): String? = sortie.derniereErreurEnvoi?.lineSequence()?.first()

    /** Date d'une ligne : « 19 oct. 2026 07:50 ». */
    fun dateLigne(sortie: Sortie): String = FormatDate.jourMoisLettresHeure(sortie.date)

    /** « Supprimer la saisie du 19/10/2026 07:50 (3 observations) ? Cette action est définitive. » */
    fun messageSuppression(sortie: Sortie): String {
        val date = FormatDate.jourHeure(sortie.date)
        val nbObs = sortie.observations.size
        val descr = when {
            nbObs == 0 -> "saisie du $date"
            nbObs == 1 -> "saisie du $date (1 observation)"
            else -> "saisie du $date ($nbObs observations)"
        }
        return "Supprimer la $descr ? Cette action est définitive."
    }

    /** « Continuer la saisie » d'une saisie MONO-TAXON : elle se rouvre en mono (GeoMys-Android la
     *  rouvrait toujours sur la carte multi-taxons, défaut n° 7 de l'inventaire), avec les champs
     *  additionnels de relevé de sa dernière observation — l'écran intercalé ne repasse pas. */
    fun additionnelsPourContinuerEnMono(sortie: Sortie): Map<String, String> =
        sortie.observations.lastOrNull()?.additionalFieldsReleve.orEmpty()

    /** Les espèces de la saisie avec leur total d'individus (tous les dénombrements), par ordre
     *  alphabétique ; le placeholder « relevé sans espèce » n'est pas une espèce. */
    fun especesAgregees(sortie: Sortie): List<Pair<String, Int>> =
        sortie.observations
            .filterNot { it.releveSansEspece }
            .groupBy { it.espece }
            .map { (espece, obs) -> espece to obs.sumOf { it.totalIndividus } }
            .sortedBy { it.first }

    /** « 3 espèces ». */
    fun titreEspeces(nombre: Int): String = "$nombre espèce${if (nombre > 1) "s" else ""}"

    /** Lignes du récapitulatif des espèces : « Merle noir — 2 ind. ». Une espèce que le serveur
     *  signale ([exceptionnelles], noms d'espèce) est marquée « ⚠ », l'avertissement en dessous. */
    fun lignesEspeces(sortie: Sortie, exceptionnelles: Set<String> = emptySet()): List<String> =
        especesAgregees(sortie).flatMap { (espece, nombre) ->
            if (espece !in exceptionnelles) listOf("$espece — $nombre ind.")
            else listOf("⚠ $espece — $nombre ind.", "      ${TextesProfils.MESSAGE}")
        }
}
