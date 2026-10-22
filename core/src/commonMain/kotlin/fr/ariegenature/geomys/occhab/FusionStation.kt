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

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import kotlin.math.abs

/** [OccHabStation.origineJson] de cette station : son contenu à cet instant. */
fun OccHabStation.versOrigineJson(): String =
    JSON_STOCKAGE.encodeToString(OccHabStation.serializer(), copy(origineJson = null, empreinteOrigine = null))

/** La base de la fusion ([OccHabStation.origineJson]), ou null. */
fun OccHabStation.origine(): OccHabStation? =
    origineJson?.let { runCatching { JSON_STOCKAGE.decodeFromString(OccHabStation.serializer(), it) }.getOrNull() }

/**
 * Une station renvoyée en mise à jour part fusionnée avec sa version relue sur le serveur juste
 * avant l'envoi. Le serveur applique tout ce qu'il reçoit, et supprime un habitat absent de l'envoi :
 * renvoyée telle quelle, la copie repartait avec la version de la dernière synchro, et effaçait ce
 * qui avait changé sur le web entre-temps.
 *
 * Chaque valeur que l'utilisateur n'a pas changée depuis l'origine (la station telle que reçue du
 * serveur, ou telle qu'envoyée) prend celle du serveur ; les autres gardent la sienne. Une voisine
 * déplacée par la topologie partagée ne renvoie ainsi que sa nouvelle géométrie.
 */
object FusionStation {

    fun fusionner(origine: OccHabStation, locale: OccHabStation, serveur: OccHabStation): OccHabStation {
        fun <T> champ(valeur: (OccHabStation) -> T): T = if (valeur(locale) == valeur(origine)) valeur(serveur) else valeur(locale)

        // La géométrie et ses valeurs dérivées (centroïde, surface, altitudes) vont ensemble.
        val geometrieLocale = locale.cleGeometrie() != origine.cleGeometrie()
        val geo = if (geometrieLocale) locale else serveur
        // Les observateurs : leurs ids et leurs noms ensemble.
        val obs = if (locale.observateursIds == origine.observateursIds && locale.observateursNoms == origine.observateursNoms) serveur else locale
        return locale.copy(
            geometryType = geo.geometryType,
            latitude = geo.latitude,
            longitude = geo.longitude,
            geometryCoordsJson = geo.geometryCoordsJson,
            geometryTrousJson = geo.geometryTrousJson,
            geometryPartielle = geo.geometryPartielle,
            surface = if (geometrieLocale) locale.surface else champ { it.surface },
            altitudeMin = if (geometrieLocale) locale.altitudeMin else champ { it.altitudeMin },
            altitudeMax = if (geometrieLocale) locale.altitudeMax else champ { it.altitudeMax },
            idDataset = champ { it.idDataset },
            observateursIds = obs.observateursIds,
            observateursNoms = obs.observateursNoms,
            observateursTxt = champ { it.observateursTxt },
            stationName = champ { it.stationName },
            comment = champ { it.comment },
            anaEvalJson = champ { it.anaEvalJson },
            dateMin = champ { it.dateMin },
            dateMax = champ { it.dateMax },
            profondeurMin = champ { it.profondeurMin },
            profondeurMax = champ { it.profondeurMax },
            precision = champ { it.precision },
            idNomExposition = champ { it.idNomExposition },
            idNomCalculSurface = champ { it.idNomCalculSurface },
            idNomObjetGeographique = champ { it.idNomObjetGeographique },
            idNomTypeSol = champ { it.idNomTypeSol },
            idNomTypeMosaique = champ { it.idNomTypeMosaique },
            habitats = fusionnerHabitats(origine.habitats, locale.habitats, serveur.habitats),
        )
    }

    /** Les habitats, par leur id serveur :
     *  - créé par un envoi précédent resté sans réponse : le serveur l'a sous l'identifiant SINP
     *    (`unique_id_sinp_hab`) que l'appli lui avait donné ; c'est celui de l'appli, qui prend son id
     *    au lieu de repartir en double (audit du 21 octobre, C5) ;
     *  - ajouté sur le serveur depuis l'origine : gardé ;
     *  - supprimé dans l'appli : omis, donc supprimé par le serveur ;
     *  - supprimé sur le serveur : pas recréé ;
     *  - présent partout : celui de l'appli s'il y a été modifié, sinon celui du serveur ;
     *  - nouveau dans l'appli (sans id serveur) : ajouté.
     *  Une origine aux habitats sans id (créés par l'appli avant qu'elle ne garde leurs ids) ne dit
     *  pas lesquels du serveur ils sont : la liste de l'appli fait alors foi, comme avant la fusion,
     *  pour ne pas les envoyer en double. */
    private fun fusionnerHabitats(
        origine: List<OccHabHabitat>,
        locale: List<OccHabHabitat>,
        serveur: List<OccHabHabitat>,
    ): List<OccHabHabitat> {
        if (origine.any { (it.idHabitatServeur ?: 0) <= 0 }) return locale
        val parIdOrigine = origine.parIdServeur()
        val parIdLocale = locale.parIdServeur()
        val nouveaux = locale.filter { (it.idHabitatServeur ?: 0) <= 0 }
        val nouveauxParUuid = nouveaux.mapNotNull { h -> h.uuidHabitat?.lowercase()?.takeIf { it.isNotBlank() }?.let { it to h } }.toMap()
        val repris = mutableSetOf<String>()
        val fusion = serveur.mapNotNull { s ->
            val id = s.idHabitatServeur?.takeIf { it > 0 }
            val o = id?.let(parIdOrigine::get)
            val l = id?.let(parIdLocale::get)
            val cree = if (o == null && l == null) s.uuidHabitat?.lowercase()?.let(nouveauxParUuid::get) else null
            when {
                cree != null -> {
                    repris += cree.id
                    cree.copy(idHabitatServeur = id)
                }
                o == null -> l ?: s
                l == null -> null
                contenu(l) == contenu(o) -> s
                else -> l
            }
        }
        return fusion + nouveaux.filter { it.id !in repris }
    }

    /** [a] et [b] ont-elles la même géométrie (type, sommets, trous), au centimètre près : le serveur
     *  peut renvoyer des coordonnées moins précises que celles qu'il a reçues. */
    fun memeGeometrie(a: OccHabStation, b: OccHabStation): Boolean {
        if (a.geometryType != b.geometryType) return false
        if (a.geometryType != "Polygon" || a.geometryCoordsJson.isNullOrEmpty() || b.geometryCoordsJson.isNullOrEmpty()) {
            return proches(a.latitude, b.latitude) && proches(a.longitude, b.longitude)
        }
        return memesSommets(anneau(a.geometryCoordsJson), anneau(b.geometryCoordsJson)) &&
            anneaux(a.geometryTrousJson).let { ta ->
                val tb = anneaux(b.geometryTrousJson)
                ta.size == tb.size && ta.indices.all { memesSommets(ta[it], tb[it]) }
            }
    }

    /** Environ 1 cm en latitude. */
    private const val TOLERANCE_DEGRES = 1e-7

    private fun proches(x: Double, y: Double) = abs(x - y) <= TOLERANCE_DEGRES

    private fun memesSommets(a: List<Pair<Double, Double>>?, b: List<Pair<Double, Double>>?): Boolean =
        a != null && b != null && a.size == b.size && a.indices.all { proches(a[it].first, b[it].first) && proches(a[it].second, b[it].second) }

    /** `[[lon, lat], …]`, ou null s'il ne se lit pas. */
    private fun anneau(json: String?): List<Pair<Double, Double>>? = runCatching {
        val tableau = JSONArray(json!!)
        (0 until tableau.length()).map { tableau.getJSONArray(it).let { c -> c.getDouble(0) to c.getDouble(1) } }
    }.getOrNull()

    /** `[[[lon, lat], …], …]` : les trous d'un polygone ; aucun si vide ou illisible. */
    private fun anneaux(json: String?): List<List<Pair<Double, Double>>?> = runCatching {
        val tableau = JSONArray(json!!)
        (0 until tableau.length()).map { anneau(tableau.getJSONArray(it).toString()) }
    }.getOrNull().orEmpty()

    private fun List<OccHabHabitat>.parIdServeur(): Map<Int, OccHabHabitat> =
        mapNotNull { h -> h.idHabitatServeur?.takeIf { it > 0 }?.let { it to h } }.toMap()

    /** Le contenu d'un habitat, sans son id local, tiré au hasard à chaque lecture du serveur. */
    private fun contenu(h: OccHabHabitat): OccHabHabitat = h.copy(id = "")
}
