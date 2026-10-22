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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/network/CompatibiliteServeur.kt) :
// Kotlin pur. Ajouts du 21 octobre 2026 : les messages d'une version non lue, et le « v » de tête.

package fr.ariegenature.geomys.network

/** Version minimale de GeoNature supportée par l'application.
 *
 *  Justification : l'app appelle TaxHub via [GeoNatureConfig.urlTaxhub] — celui du web,
 *  `<URL_GEONATURE>/api/taxhub` (TaxHub INTÉGRÉ, 2.15+), sinon l'URL publiée par le serveur
 *  (`t_mobile_apps` → `settings.sync.taxhub_url`). L'endpoint `/api/gn_commons/config` qui permet de
 *  détecter la version existe lui depuis la 2.12.
 *
 *  Seule une version LUE et inférieure à ce minimum fait échouer le test de connexion :
 *  [fr.ariegenature.geomys.store.GeoNatureConfig.serveurCompatible] invalide alors la config
 *  (toutes les opérations réseau sont gelées) jusqu'à un nouveau test réussi. Une version qui ne se
 *  lit pas (réseau faible, proxy, portail captif, serveur d'avant la 2.12) ne refuse rien : refusée
 *  le matin du 21 octobre, elle gelait toute l'appli sur un simple raté réseau ; l'utilisateur a
 *  choisi ensuite « version lue trop ancienne » seulement. */
const val VERSION_GEONATURE_MINIMALE = "2.15"

/** Le test de connexion réussi d'un serveur dont la version ne s'est pas lue. */
const val CONNEXION_SANS_VERSION = "Connexion réussie (version de GeoNature non lue)"

/** Le test d'un serveur dont la version ne s'est pas lue, alors que la dernière lue était trop
 *  ancienne : le refus tient jusqu'à une version lue à jour. */
const val VERSION_NON_LUE_REFUS_MAINTENU =
    "Version de GeoNature non lue ; la dernière lue était trop ancienne (v$VERSION_GEONATURE_MINIMALE au moins). Réessayez."

/** Compare deux versions pointées numériquement : "2.9" < "2.15" < "2.15.1" (comparaison
 *  par segment, PAS lexicographique). Un suffixe non numérique de segment est ignoré
 *  ("0-rc1" → 0) ; les segments manquants valent 0 ("2.15" == "2.15.0").
 *  Retourne <0, 0 ou >0 comme un Comparator. */
fun comparerVersions(a: String, b: String): Int {
    // « v2.17 » se lit 2.17, et non 0.17.
    fun segments(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.').map { seg ->
        seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }
    val sa = segments(a)
    val sb = segments(b)
    for (i in 0 until maxOf(sa.size, sb.size)) {
        val d = sa.getOrElse(i) { 0 }.compareTo(sb.getOrElse(i) { 0 })
        if (d != 0) return d
    }
    return 0
}

/** La [version] détectée sur le serveur est-elle au moins la minimale supportée ? */
fun versionGeoNatureSupportee(version: String): Boolean =
    comparerVersions(version, VERSION_GEONATURE_MINIMALE) >= 0
