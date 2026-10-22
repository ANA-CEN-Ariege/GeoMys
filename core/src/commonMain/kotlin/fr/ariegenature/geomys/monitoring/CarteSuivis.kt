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

// Extrait de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/ui/CarteGeometrieFragment.kt) :
// la lecture des géométries GeoJSON (rendre, extrairePoints, extraireAnneaux), les types « macro » de la carte
// d'un protocole, les actions proposées au toucher d'un objet (montrerActions), son fil d'Ariane (filPourItem)
// et le cadrage (zoomerSur). Un polygone garde ses trous : GeoMys-Android dessinait chaque anneau comme un
// polygone plein (défaut n° 9 de l'inventaire), GeoMys-IOS les rend en creux.

package fr.ariegenature.geomys.monitoring

import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.geo.empriseZoomerSur
import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.json.JSONObject
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringSchemaObjet
import fr.ariegenature.geomys.network.MonitoringSync

/** Une géométrie à dessiner. */
sealed interface GeometrieSuivi {
    data class Point(val position: LatLon) : GeometrieSuivi

    data class Ligne(val sommets: List<LatLon>) : GeometrieSuivi

    /** [anneaux] : le contour, puis les trous. */
    data class Polygone(val anneaux: List<List<LatLon>>) : GeometrieSuivi
}

/** Un objet de la carte : son identité (pour les actions au toucher) et ses géométries.
 *  [enfant] : un enfant de l'objet ouvert, dessiné dans un style plus discret. */
data class ObjetCarte(val type: String, val id: Int, val label: String, val enfant: Boolean, val geometries: List<GeometrieSuivi>)

/** Une action proposée au toucher d'un objet. */
sealed interface ActionCarte {
    val libelle: String

    data object Fiche : ActionCarte {
        override val libelle = "Voir la fiche"
    }

    /** La saisie de [childObjectType] sous l'objet (« Nouvelle visite ») ; [permise] faux : sans le
     *  droit de créer, comme le « + » grisé du web. */
    data class NouvelleSaisie(val childObjectType: String, override val libelle: String, val permise: Boolean = true) : ActionCarte
}

/** La carte des géométries d'un objet de suivi ou d'un protocole. */
object CarteSuivis {

    const val PAS_DE_GEOMETRIE = "Pas de géométrie pour cet objet."
    const val AUCUN_SITE = "Aucun site avec géométrie pour ce protocole."
    const val AUCUNE_GEOMETRIE = "Aucune géométrie à afficher."
    const val TITRE_FOND = "Fond de carte"

    fun geometrieIllisible(message: String?) = "Géométrie illisible : $message"

    /** Les géométries d'un GeoJSON (Point, MultiPoint, LineString, Polygon, MultiPolygon) ; rien
     *  pour un autre type. Lève une exception si le texte n'est pas du JSON. */
    fun lire(geoJson: String): List<GeometrieSuivi> {
        val geo = JSONObject(geoJson)
        val coordonnees = geo.opt("coordinates") as? JSONArray ?: return emptyList()
        return when (geo.optString("type", "")) {
            "Point" -> listOfNotNull(position(coordonnees)?.let { GeometrieSuivi.Point(it) })
            "MultiPoint" -> points(coordonnees).map { GeometrieSuivi.Point(it) }
            "LineString" -> points(coordonnees).takeIf { it.isNotEmpty() }?.let { listOf(GeometrieSuivi.Ligne(it)) }.orEmpty()
            "Polygon" -> listOfNotNull(polygone(coordonnees))
            "MultiPolygon" -> (0 until coordonnees.length()).mapNotNull { i -> coordonnees.optJSONArray(i)?.let(::polygone) }
            else -> emptyList()
        }
    }

    private fun position(arr: JSONArray): LatLon? {
        if (arr.length() < 2) return null
        val lon = arr.optDouble(0, Double.NaN)
        val lat = arr.optDouble(1, Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        return LatLon(lat, lon)
    }

    private fun points(arr: JSONArray): List<LatLon> = (0 until arr.length()).mapNotNull { i -> arr.optJSONArray(i)?.let(::position) }

    private fun polygone(arr: JSONArray): GeometrieSuivi.Polygone? {
        val anneaux = (0 until arr.length()).mapNotNull { i -> arr.optJSONArray(i)?.let(::points)?.takeIf { it.isNotEmpty() } }
        return if (anneaux.isEmpty()) null else GeometrieSuivi.Polygone(anneaux)
    }

    /** Tous les points d'une liste d'objets (pour le cadrage). */
    fun points(objets: List<ObjetCarte>): List<LatLon> = objets.flatMap { o ->
        o.geometries.flatMap { g ->
            when (g) {
                is GeometrieSuivi.Point -> listOf(g.position)
                is GeometrieSuivi.Ligne -> g.sommets
                is GeometrieSuivi.Polygone -> g.anneaux.flatten()
            }
        }
    }

    /** Le cadrage de [points] (`zoomerSur`) : leur emprise agrandie de 30 % ; pour un point seul (ou
     *  des points confondus), une boîte de ±0,002° (≈ 440 m) autour de lui. */
    fun emprise(points: List<LatLon>): Emprise? = empriseZoomerSur(points, demiBoite = 0.002, agrandissement = 1.3)

    /** Les types dessinés sur la carte d'un protocole : ses types « macro » (enfants de `module`
     *  dans le schéma) présents ; sans eux, les types dont le parent n'est pas lui-même présent
     *  (leurs enfants viendront par la carte du parent) ; sans schéma, tous. */
    fun typesMacro(presents: List<String>, schema: Map<String, MonitoringSchemaObjet>?): List<String> {
        val racines = schema?.get("module")?.childrenTypes.orEmpty()
        return when {
            racines.isNotEmpty() -> racines.filter { it in presents }
            schema != null -> {
                val enfants = presents.filter { type ->
                    val parent = schema[type]?.parentType
                    parent != null && parent != "module" && parent in presents
                }.toSet()
                presents.filter { it !in enfants }
            }
            else -> presents
        }
    }

    /** Les actions au toucher d'un objet : sa fiche, sauf s'il est l'objet ouvert ; une nouvelle
     *  saisie si son type déclare un enfant de saisie (comme les « + » des listes). */
    fun actions(
        objet: ObjetCarte,
        objetCourant: Boolean,
        schema: Map<String, MonitoringSchemaObjet>?,
        peutCreer: (String) -> Boolean = { true },
        libelleNouveau: (String) -> String,
    ): List<ActionCarte> = buildList {
        if (!objetCourant) add(ActionCarte.Fiche)
        schema?.get(objet.type)?.childrenTypes?.firstOrNull { MonitoringSync.estTypeSaisie(it) }?.let { enfant ->
            add(ActionCarte.NouvelleSaisie(enfant, libelleNouveau(enfant), peutCreer(enfant)))
        }
    }

    /** Le fil d'Ariane de ce qu'ouvre le toucher d'un objet : celui de la carte, prolongé de l'objet
     *  (sauf s'il est l'objet ouvert, déjà au bout) ; sans fil, la racine des Suivis. */
    fun filPourObjet(objet: ObjetCarte, objetCourant: Boolean, filCarte: List<FilSegment>, labelModule: String): List<FilSegment> {
        val base = filCarte.ifEmpty { filRacineSuivis(labelModule) }
        return if (objetCourant) base else base + FilSegment(objet.type, objet.id, objet.label)
    }
}
