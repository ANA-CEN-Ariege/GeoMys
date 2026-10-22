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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/ConfigGeoNatureFragment.kt,
// configurationComplete) : logique pure, passée des écrans au socle commun, avec le cache TaxRef
// en paramètre.

package fr.ariegenature.geomys.store

/** « Configuration complète » = saisie OCCTAX réellement réalisable (connexion, et les trois
 *  sélections par défaut — jeu de données, liste, observateur — présentes dans les caches) ET des
 *  taxons chargés (une version TaxRef a été enregistrée). Les deux signaux sont **prefs-backed** → lisibles instantanément dès le démarrage
 *  SANS charger le cache TaxRef sur le thread principal (évite tout jank au lancement). Source
 *  UNIQUE partagée par [MainActivity] (redirection au 1er lancement) et l'écran Paramètres (blocage
 *  de sortie tant que ce n'est pas complet). */
fun configurationComplete(cfg: GeoNatureConfig, taxRef: TaxRefCache): Boolean =
    cfg.saisieOcctaxValide && taxRef.versionSauvegardee != null &&
        // Une mise à jour de l'application ne bloque plus rien (21 octobre 2026) : les données en
        // place servent jusqu'au prochain « Recharger les données ».
        // Liste de taxons partiellement téléchargée (coupure réseau, serveur qui lâche en cours de
        // pagination) : le cache contient une liste tronquée mais NON VIDE, donc il se croit
        // complet. La saisie refuserait alors des espèces réellement présentes dans le protocole,
        // avec un « Nom invalide » qui met en cause l'observateur (audit 2026-09-18, T3).
        taxRef.listesIncompletes.isEmpty() &&
        listeDeSaisieChargee(cfg, taxRef)

/** La liste de taxons de la saisie (celle du jeu par défaut, sinon la liste choisie, sinon celle de la
 *  configuration d'OccTax) est sur le téléphone. Depuis le 21 octobre, la synchro ne charge que les
 *  listes utiles : une liste choisie après un premier chargement se télécharge à son choix, et la
 *  saisie attend qu'elle soit là. Sans liste (aucune sur le serveur), rien à attendre. */
fun listeDeSaisieChargee(cfg: GeoNatureConfig, taxRef: TaxRefCache): Boolean =
    cfg.listeTaxonsOcctax(cfg.idDatasetParDefaut)?.let { it in taxRef.listesSynchronisees } ?: true
