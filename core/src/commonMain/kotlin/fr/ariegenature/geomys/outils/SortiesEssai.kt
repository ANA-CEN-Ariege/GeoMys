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

// Essai d'affichage du lot 6, lancé par le paramètre caché `action=sorties-essai` de GeoMysApp, sur
// l'émulateur et le simulateur seulement : le simulateur iOS ne sait pas simuler de toucher, et les
// écrans de saisie qui créeraient ces sorties n'existent pas encore.

package fr.ariegenature.geomys.outils

import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.PointTrace
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.util.maintenantMillis

/** Pose quatre sorties fictives dans « Mes saisies » : une à envoyer (avec un relevé ponctuel, une
 *  ligne et un polygone), une en échec d'envoi, une envoyée, une importée. Identifiants fixes : un
 *  nouveau lancement les remplace au lieu de les dupliquer. */
suspend fun ServicesGeoNature.ajouterSortiesEssai() {
    val maintenant = maintenantMillis()
    val heure = 3_600_000L
    fun obs(espece: String, cdNom: Int?, nombre: Int = 1) =
        Observation(espece = espece, cdNom = cdNom, latitude = 42.96, longitude = 1.60, nombre = nombre, date = maintenant)
    val parcours = listOf(PointTrace(42.960, 1.600), PointTrace(42.962, 1.602), PointTrace(42.965, 1.601))
    sortieStore.remplacer("essai-importee", Sortie(id = "essai-importee", date = maintenant - 72 * heure, observations = listOf(obs("Merle noir", 4117, 2)), estImportee = true))
    sortieStore.remplacer(
        "essai-envoyee",
        Sortie(id = "essai-envoyee", date = maintenant - 48 * heure, observations = listOf(obs("Mésange bleue", 534742)), distanceTotale = 820.4, envoyeGeoNature = true),
    )
    sortieStore.remplacer(
        "essai-echec",
        Sortie(
            id = "essai-echec",
            date = maintenant - 24 * heure,
            observations = listOf(obs("Buse variable", 2623)),
            pointsParcours = parcours,
            distanceTotale = 512.6,
            derniereErreurEnvoi = "Serveur GeoNature injoignable (délai dépassé).\nRéessayez plus tard.",
        ),
    )
    val polygone = "[[1.6030,42.9630],[1.6045,42.9632],[1.6043,42.9620],[1.6028,42.9618]]"
    val ligne = "[[1.5975,42.9655],[1.5985,42.9662],[1.5998,42.9660]]"
    sortieStore.remplacer(
        "essai-a-envoyer",
        Sortie(
            id = "essai-a-envoyer",
            date = maintenant - heure,
            observations = listOf(
                obs("Rougegorge familier", 4001, 3),
                obs("Pinson des arbres", 4564),
                obs("Rougegorge familier", 4001),
                obs("Lézard des murailles", 77756).copy(
                    latitude = 42.9625, longitude = 1.6036, geometryType = "Polygon", geometryCoordsJson = polygone, notes = "lisière",
                ),
                obs("Grenouille rousse", 351, 4).copy(latitude = 42.9659, longitude = 1.5986, geometryType = "LineString", geometryCoordsJson = ligne),
            ),
            pointsParcours = parcours,
            distanceTotale = 1234.5,
        ),
    )
}
