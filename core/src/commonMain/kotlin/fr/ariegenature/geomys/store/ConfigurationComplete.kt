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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/ConfigGeoNatureFragment.kt,
// configurationComplete) : logique pure, passée des écrans au socle commun, avec le cache TaxRef
// en paramètre.

package fr.ariegenature.geomys.store

/** « Configuration complète » = saisie OCCTAX réellement réalisable (connexion + jeu de données /
 *  liste / observateur présents dans les caches) ET des taxons chargés (une version TaxRef a été
 *  enregistrée). Les deux signaux sont **prefs-backed** → lisibles instantanément dès le démarrage
 *  SANS charger le cache TaxRef sur le thread principal (évite tout jank au lancement). Source
 *  UNIQUE partagée par [MainActivity] (redirection au 1er lancement) et l'écran Paramètres (blocage
 *  de sortie tant que ce n'est pas complet). */
fun configurationComplete(cfg: GeoNatureConfig, taxRef: TaxRefCache): Boolean =
    cfg.saisieOcctaxValide && taxRef.versionSauvegardee != null &&
        // Mise à jour exigeant un rechargement (données encore en place, purgées par la synchro) :
        // bloqué jusqu'à la synchro, cf. store/CachesSynchronises.armerRechargementSiRequis.
        !cfg.rechargementRequisApresMaj &&
        // Liste de taxons partiellement téléchargée (coupure réseau, serveur qui lâche en cours de
        // pagination) : le cache contient une liste tronquée mais NON VIDE, donc il se croit
        // complet. La saisie refuserait alors des espèces réellement présentes dans le protocole,
        // avec un « Nom invalide » qui met en cause l'observateur (audit 2026-09-18, T3).
        taxRef.listesIncompletes.isEmpty()
