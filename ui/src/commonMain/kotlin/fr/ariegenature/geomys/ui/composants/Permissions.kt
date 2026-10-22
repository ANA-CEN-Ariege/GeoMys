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

package fr.ariegenature.geomys.ui.composants

import androidx.compose.runtime.Composable

/** Demande, si besoin, la permission d'afficher la notification de la synchro (POST_NOTIFICATIONS,
 *  Android 13 et suivants) — au mieux : un refus n'empêche pas la synchro, seule la notification
 *  manque. Rien à demander sous iOS, où la synchro ne notifie pas. */
@Composable
expect fun rememberDemandeNotifications(): () -> Unit

/** Demande, si besoin, l'autorisation de localisation, puis appelle [onAccordee] si elle est
 *  donnée : ACCESS_FINE_LOCATION et ACCESS_COARSE_LOCATION sous Android (l'une des deux suffit),
 *  comme les écrans de carte de GeoMys-Android. Sous iOS, c'est CLLocationManager qui la demande au
 *  premier démarrage du suivi : [onAccordee] est appelé tout de suite. */
@Composable
expect fun rememberDemandeLocalisation(onAccordee: () -> Unit): () -> Unit
