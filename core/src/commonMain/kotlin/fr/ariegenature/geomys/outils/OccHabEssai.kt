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

// Essai d'affichage du lot 8, lancé par le paramètre caché `action=occhab-essai` de GeoMysApp, sur
// l'émulateur et le simulateur seulement : de quoi ouvrir les écrans OccHab sans serveur GeoNature —
// jeux de données, nomenclatures et habitats OccHab, stations « déjà sur GeoNature » dans le cache
// hors ligne, une saisie à envoyer et une saisie envoyée. Toutes les données sont fictives.

package fr.ariegenature.geomys.outils

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureObservateur
import fr.ariegenature.geomys.model.HabitatSuggestion
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.store.NomValeur
import fr.ariegenature.geomys.store.io.listeVersJson
import fr.ariegenature.geomys.util.GeoJsonCoords

/** Le cadrage d'ouverture des essais : les stations d'essai, au-dessus de Foix. */
val CADRAGE_OCCHAB_ESSAI = Triple(42.9550, 1.6215, 15.5)

/** Écrit les données d'essai d'OccHab (voir l'en-tête du fichier). */
suspend fun ServicesGeoNature.ajouterOccHabEssai() {
    config.occhabDisponible = true
    config.occhabPeutCreer = true
    config.occhabFormConfigJson = ""
    config.datasetsOcchabCacheJson = listeVersJson(
        GeoNatureDataset.serializer(),
        listOf(
            GeoNatureDataset(21, "Cartographie des habitats du Plantaurel", moduleCodes = listOf("OCCHAB")),
            GeoNatureDataset(22, "Tourbières de l'Ariège", moduleCodes = listOf("OCCHAB")),
        ),
    )
    // Créables (POST `create` OCCHAB) et observateurs de la liste d'OccHab : sans eux, le formulaire
    // ne propose rien, comme le web.
    config.datasetsCreablesOcchab = setOf(21, 22)
    config.observateursOcchabCacheJson = listeVersJson(
        GeoNatureObservateur.serializer(),
        listOf(GeoNatureObservateur(3, "DUPONT Alice"), GeoNatureObservateur(7, "MARTIN Paul"), GeoNatureObservateur(12, "ROUX Inès")),
    )
    nomenclatures.completer(
        mapOf(
            "NAT_OBJ_GEO" to listOf(NomValeur(301, "Inventoriel", cd = "In"), NomValeur(302, "Stationnel", cd = "St"), NomValeur(303, "Ne sait pas", cd = "NSP")),
            "METHOD_CALCUL_SURFACE" to listOf(NomValeur(311, "Calculée à partir de la géométrie", cd = "1"), NomValeur(312, "Estimée sur le terrain", cd = "2")),
            "HAB_INTERET_COM" to listOf(NomValeur(321, "Oui", cd = "1"), NomValeur(322, "Non", cd = "2"), NomValeur(323, "Ne sait pas", cd = "3")),
            "DETERMINATION_TYP_HAB" to listOf(NomValeur(331, "Déterminé sur le terrain", cd = "1"), NomValeur(332, "Photo-interprétation", cd = "2")),
            "TECHNIQUE_COLLECT_HAB" to listOf(NomValeur(341, "In situ", cd = "1"), NomValeur(342, "Télédétection", cd = "2"), NomValeur(343, "Autre, préciser", cd = "10")),
            "ABONDANCE_HAB" to listOf(NomValeur(351, "Rare", cd = "1"), NomValeur(352, "Assez abondant", cd = "2"), NomValeur(353, "Abondant", cd = "3")),
        ),
    )
    config.occhabDefautsNomencJson = """{"NAT_OBJ_GEO":302,"TECHNIQUE_COLLECT_HAB":341,"METHOD_CALCUL_SURFACE":311}"""
    habitatsOccHab.remplacerTout(
        listOf(
            HabitatSuggestion(9001, "Pelouses calcicoles mésophiles"),
            HabitatSuggestion(9002, "Prairies de fauche de basse altitude"),
            HabitatSuggestion(9003, "Tourbières hautes actives"),
            HabitatSuggestion(9004, "Landes sèches européennes"),
            HabitatSuggestion(9005, "Hêtraies acidiphiles atlantiques"),
            HabitatSuggestion(9006, "Mégaphorbiaies hygrophiles"),
        ),
    )

    // Stations « déjà sur GeoNature » (violettes) : une prairie à trou mitoyenne de la pelouse à
    // envoyer, une source ponctuelle, une lande en deux parties (non modifiable dans l'appli).
    stationsServeur.remplacerTout(
        listOf(
            serveur(
                501, "Prairie des Cabannes",
                anneau(42.9540 to 1.6220, 42.9540 to 1.6270, 42.9570 to 1.6270, 42.9570 to 1.6220),
                trou = anneau(42.9550 to 1.6235, 42.9550 to 1.6250, 42.9560 to 1.6250, 42.9560 to 1.6235),
                anaEvalJson = """{"echelle":5000,"enjeu":"fort","statut":"brouillon"}""",
                habitats = listOf(
                    OccHabHabitat(
                        idHabitatServeur = 9101, cdHab = 9002, habitatLabel = "Prairies de fauche de basse altitude",
                        nomCite = "Prairie de fauche", recouvrement = 80.0, idNomTechniqueCollecte = 341,
                        anaEvalJson = """{"etat_conservation":"bon","pee":["Buddleja davidii"]}""",
                    ),
                ),
            ),
            serveur(502, "Source du ruisseau", point = LatLon(42.9515, 1.6240)),
            serveur(503, "Landes du Pech (deux parties)", anneau(42.9580 to 1.6140, 42.9580 to 1.6170, 42.9600 to 1.6170, 42.9600 to 1.6140), partielle = true),
            serveur(504, "Tourbière de la Bouiche", anneau(42.9500 to 1.6180, 42.9500 to 1.6200, 42.9515 to 1.6200, 42.9515 to 1.6180), jdd = 22),
        ),
    )

    // Une saisie à envoyer (une pelouse mitoyenne de la prairie, un point sans habitat) et une
    // saisie envoyée.
    val aEnvoyer = "essai-occhab-a-envoyer"
    occHabStore.supprimer(aEnvoyer)
    occHabStore.upsertStation(
        aEnvoyer,
        locale(
            "essai-occhab-pelouse",
            anneau(42.9540 to 1.6180, 42.9540 to 1.6220, 42.9570 to 1.6220, 42.9570 to 1.6180),
            habitats = listOf(OccHabHabitat(cdHab = 9001, habitatLabel = "Pelouses calcicoles mésophiles", nomCite = "Pelouses calcicoles mésophiles", idNomTechniqueCollecte = 341)),
        ),
    )
    occHabStore.upsertStation(aEnvoyer, locale("essai-occhab-point", point = LatLon(42.9525, 1.6150)))
    val envoyee = "essai-occhab-envoyee"
    occHabStore.supprimer(envoyee)
    occHabStore.upsertStation(envoyee, locale("essai-occhab-envoyee-station", point = LatLon(42.9600, 1.6250)))
    occHabStore.marquerStationEnvoyee(envoyee, "essai-occhab-envoyee-station", 777)
}

private fun anneau(vararg sommets: Pair<Double, Double>): List<LatLon> = sommets.map { (lat, lon) -> LatLon(lat, lon) }

private fun serveur(
    id: Int,
    nom: String,
    anneau: List<LatLon>? = null,
    trou: List<LatLon>? = null,
    point: LatLon? = null,
    partielle: Boolean = false,
    jdd: Int = 21,
    anaEvalJson: String? = null,
    habitats: List<OccHabHabitat> = emptyList(),
): OccHabStation {
    val centre = point ?: GeoJsonCoords.centroide(anneau.orEmpty())!!
    return OccHabStation(
        idStationServeur = id,
        geometryType = if (anneau != null) "Polygon" else "Point",
        latitude = centre.latitude,
        longitude = centre.longitude,
        geometryCoordsJson = anneau?.let(GeoJsonCoords::format),
        geometryTrousJson = trou?.let { GeoJsonCoords.formatAnneaux(listOf(it)) },
        geometryPartielle = partielle,
        idDataset = jdd,
        observateursIds = listOf(3),
        observateursNoms = listOf("DUPONT Alice"),
        stationName = nom,
        anaEvalJson = anaEvalJson,
        dateMin = 1_779_840_000_000L,
        dateMax = 1_779_840_000_000L,
        idNomObjetGeographique = 302,
        habitats = habitats,
        envoyeGeoNature = true,
        origineServeur = true,
    )
}

private fun locale(id: String, anneau: List<LatLon>? = null, point: LatLon? = null, habitats: List<OccHabHabitat> = emptyList()): OccHabStation {
    val centre = point ?: GeoJsonCoords.centroide(anneau.orEmpty())!!
    return OccHabStation(
        id = id,
        geometryType = if (anneau != null) "Polygon" else "Point",
        latitude = centre.latitude,
        longitude = centre.longitude,
        geometryCoordsJson = anneau?.let(GeoJsonCoords::format),
        idDataset = 21,
        observateursIds = listOf(3),
        observateursNoms = listOf("DUPONT Alice"),
        dateMin = 1_779_926_400_000L,
        dateMax = 1_779_926_400_000L,
        idNomObjetGeographique = 302,
        habitats = habitats,
    )
}
