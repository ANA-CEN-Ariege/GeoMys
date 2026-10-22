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

package fr.ariegenature.geomys.plateforme

import okio.Path

/** Exclut [dossier] des sauvegardes de l'appareil. Sous Android, c'est le rôle des règles de
 *  sauvegarde (`androidApp/src/main/res/xml/`) : rien à faire ici. Sous iOS, le dossier reçoit
 *  l'attribut « exclu de la sauvegarde ». Pour les caches retéléchargeables (TaxRef, Suivis…),
 *  qui gonfleraient la sauvegarde sans rien protéger. */
expect fun exclureDesSauvegardes(dossier: Path)

/** Rend [dossier] aux sauvegardes de l'appareil : l'inverse de [exclureDesSauvegardes], sous iOS
 *  seulement. */
expect fun rendreAuxSauvegardes(dossier: Path)
