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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/OccHabStoreRoundTripTest.kt) :
// Robolectric remplacé par ServicesDeTest (le redémarrage par ServicesDeTest.redemarrer), JUnit par
// kotlin.test. La comparaison champ par champ, faite par réflexion sous Android, passe par la forme
// JSON des objets (la réflexion n'existe pas en Kotlin commun), complétée par l'égalité des data
// classes.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.jsonObject

/**
 * ALLER-RETOUR DISQUE des stations OccHab : une station relue après redémarrage doit être IDENTIQUE
 * à celle qui a été écrite, champ par champ — habitats compris.
 *
 * Sous Android, `OccHabStore.normaliserStation` reconstruisait chaque station par constructeur
 * EXPLICITE (elle devait tolérer des null venus d'un vieux JSON) : un champ ajouté au modèle et
 * oublié là était silencieusement remis à son défaut à la première relecture du disque. Cas réel
 * (2026-09-03) : `geometryTrousJson` oublié ⇒ une station à trou perdait son anneau intérieur au
 * redémarrage, et l'envoi l'aurait SUPPRIMÉ côté serveur — exactement le bug que ce champ corrigeait.
 * Ici, un champ perdu par la relecture (`accepter`, `stationNettoyee`) fait échouer ce test.
 */
class OccHabStoreRoundTripTest {

    private val t = ServicesDeTest()

    @AfterTest
    fun nettoyer() = t.nettoyer()

    /** Habitat dont chaque champ porte une valeur non triviale. */
    private fun habitatComplet() = OccHabHabitat(
        id = "h-1",
        idHabitatServeur = 77,
        uuidHabitat = "uuid-hab",
        cdHab = 1234,
        habitatLabel = "Forêts de Fagus",
        nomCite = "Hêtraie",
        determiner = "DUPONT jean",
        recouvrement = 12.5,
        precisionTechnique = "relevé partiel",
        anaEvalJson = """{"typicite":"bonne"}""",
        idNomTypeDetermination = 1,
        idNomTechniqueCollecte = 2,
        idNomAbondance = 3,
        idNomSensibilite = 4,
        idNomInteretCommunautaire = 5,
    )

    /** Station dont chaque champ porte une valeur non triviale — un champ perdu à l'aller-retour
     *  devient donc forcément détectable. */
    private fun stationComplete() = OccHabStation(
        id = "s-1",
        uuidStation = "uuid-station",
        idStationServeur = 2805,
        date = 1_700_000_000_000L,
        geometryType = "Polygon",
        latitude = 42.96895,
        longitude = 1.61010,
        geometryCoordsJson = "[[1.6095,42.9678],[1.6108,42.9678],[1.6108,42.9694]]",
        geometryTrousJson = "[[[1.61,42.96908],[1.61025,42.96897],[1.61035,42.96871]]]",
        geometryPartielle = true,
        idDataset = 12,
        observateursIds = listOf(7, 8),
        observateursNoms = listOf("DUPONT jean", "MARTIN paul"),
        observateursTxt = "DUPONT jean",
        stationName = "Tourbière",
        comment = "Pelouse pâturée",
        anaEvalJson = """{"enjeu":"fort"}""",
        dateMin = 1_700_000_000_000L,
        dateMax = 1_700_003_600_000L,
        altitudeMin = 800,
        altitudeMax = 850,
        profondeurMin = 1,
        profondeurMax = 2,
        surface = 9745L,
        precision = 5,
        idNomExposition = 21,
        idNomCalculSurface = 22,
        idNomObjetGeographique = 23,
        idNomTypeSol = 24,
        idNomTypeMosaique = 25,
        habitats = listOf(habitatComplet()),
        envoyeGeoNature = true,
        origineServeur = true,
        derniereErreurEnvoi = "boum",
        envoiIncertain = true,
        empreinteOrigine = "empreinte",
        origineEnvoyee = true,
    )

    /** Force une relecture DEPUIS LE DISQUE (ce que fait un redémarrage de l'appli). */
    private suspend fun relireDepuisLeDisque(): OccHabStation {
        t.redemarrer()
        return t.services.occHabStore.stationsDeSaisie("saisie-1").single()
    }

    /** Les champs de [ecrit] que [relu] ne porte plus, par leur forme JSON. */
    private fun <T> champsPerdus(serialiseur: KSerializer<T>, ecrit: T, relu: T, ignores: Set<String> = emptySet()): List<String> {
        val avant = JSON_STOCKAGE.encodeToJsonElement(serialiseur, ecrit).jsonObject
        val apres = JSON_STOCKAGE.encodeToJsonElement(serialiseur, relu).jsonObject
        assertTrue(avant.isNotEmpty(), "le modèle doit exposer des champs")
        return avant.keys.filter { it !in ignores && avant[it] != apres[it] }
    }

    @Test
    fun tous_les_champs_d_une_station_survivent_a_un_redemarrage() = runTest {
        val ecrite = stationComplete()
        assertTrue(t.services.occHabStore.upsertStation("saisie-1", ecrite))
        val relue = relireDepuisLeDisque()

        val perdus = champsPerdus(OccHabStation.serializer(), ecrite, relue, ignores = setOf("habitats"))
        assertEquals(emptyList(), perdus, "champ(s) station PERDU(S) à la relecture du disque — vérifier OccHabStore")
        assertEquals(ecrite.copy(habitats = emptyList()), relue.copy(habitats = emptyList()))
    }

    @Test
    fun tous_les_champs_d_un_habitat_survivent_a_un_redemarrage() = runTest {
        t.services.occHabStore.upsertStation("saisie-1", stationComplete())
        val relu = relireDepuisLeDisque().habitats.single()
        val attendu = habitatComplet()
        val perdus = champsPerdus(OccHabHabitat.serializer(), attendu, relu)
        assertEquals(emptyList(), perdus, "champ(s) habitat PERDU(S) à la relecture du disque — vérifier stationNettoyee")
        assertEquals(attendu, relu)
    }

    @Test
    fun le_trou_d_un_polygone_survit_a_un_redemarrage() = runTest {
        // Le cas terrain : sans le correctif, la station repartait SANS son anneau intérieur et
        // l'envoi l'aurait supprimé côté GeoNature.
        t.services.occHabStore.upsertStation("saisie-1", stationComplete().copy(geometryPartielle = false))
        val relue = relireDepuisLeDisque()
        assertEquals("[[[1.61,42.96908],[1.61025,42.96897],[1.61035,42.96871]]]", relue.geometryTrousJson)
    }
}
