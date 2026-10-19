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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/CacheManagerFragment.kt) :
// `chargerSitesProtocole`, son emprise (`BboxAccumulator`) et ses textes. Les géométries sont lues comme
// sur la carte des Suivis (CarteSuivis), au lieu de l'aiguillage du fragment.

package fr.ariegenature.geomys.carte

import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.geo.empriseZoomerSur
import fr.ariegenature.geomys.monitoring.CarteSuivis
import fr.ariegenature.geomys.monitoring.ObjetCarte
import fr.ariegenature.geomys.network.MonitoringApi
import fr.ariegenature.geomys.network.MonitoringModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Les sites d'un protocole, à dessiner sur la carte du Maps Manager, et leur emprise. */
data class SitesProtocole(val objets: List<ObjetCarte>, val emprise: Emprise?)

/** « Cibler un protocole » : la carte du Maps Manager se cadre sur les sites d'un protocole, que
 *  l'utilisateur veut télécharger avant une tournée. */
object CiblageProtocole {

    const val TITRE = "Cibler un protocole"
    const val RECHERCHE = "Recherche des sites…"
    const val AUCUN_SITE = "Aucun site avec géométrie pour ce protocole"

    fun libelle(module: MonitoringModule) = "${module.moduleLabel} (${module.moduleCode})"

    fun messageModule(code: String) = "Module : $code"

    /** Les enfants DIRECTS du module, le plus haut rang de sa hiérarchie (les groupes de sites d'un
     *  protocole STOM, les sites d'un protocole à plat), avec leur géométrie : celle de la liste, sinon
     *  celle de leur fiche, demandées toutes à la fois. Les sites sans géométrie sont écartés, et une
     *  erreur réseau donne une liste vide. */
    suspend fun charger(
        enfants: suspend () -> Map<String, List<MonitoringApi.MonitoringEnfant>>,
        geometrie: suspend (type: String, id: Int) -> String?,
    ): SitesProtocole = coroutineScope {
        val aLire = try {
            enfants()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }.flatMap { (type, liste) -> liste.map { type to it } }
        val objets = aLire.map { (type, enfant) ->
            async {
                val geoJson = enfant.geometrieGeoJson?.takeIf { it.isNotEmpty() } ?: try {
                    geometrie(type, enfant.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                // Un GeoJSON illisible écarte le site, comme ceux sans géométrie.
                val geometries = geoJson?.takeIf { it.isNotEmpty() }?.let { runCatching { CarteSuivis.lire(it) }.getOrNull() }.orEmpty()
                ObjetCarte(type, enfant.id, enfant.nom, enfant = false, geometries = geometries)
            }
        }.awaitAll().filter { it.geometries.isNotEmpty() }
        SitesProtocole(objets, emprise(objets))
    }

    /** L'emprise de tous les sommets ; ± 0,002° autour d'un point seul, que GeoMys-Android cadrait sur une
     *  boîte nulle. */
    fun emprise(objets: List<ObjetCarte>): Emprise? =
        empriseZoomerSur(CarteSuivis.points(objets), demiBoite = 0.002, agrandissement = 1.0)
}
