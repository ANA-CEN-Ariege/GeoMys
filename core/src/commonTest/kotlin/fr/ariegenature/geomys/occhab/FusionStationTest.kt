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

import fr.ariegenature.geomys.model.OccHabHabitat
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.util.GeoJsonCoords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** La fusion d'une station renvoyée en mise à jour avec sa version relue sur le serveur : ce que
 *  l'appli n'a pas changé depuis l'origine prend la valeur du serveur, sans quoi la copie effaçait
 *  ce qui avait changé sur le web entre-temps. */
class FusionStationTest {

    private val polygone = "[[1.4,42.9],[1.5,42.9],[1.5,43.0]]"

    private fun habitat(id: Int?, cdHab: Int, nom: String, recouvrement: Double? = null) =
        OccHabHabitat(idHabitatServeur = id, cdHab = cdHab, nomCite = nom, recouvrement = recouvrement)

    /** La station telle que reçue du serveur. */
    private val origine = OccHabStation(
        idStationServeur = 42, geometryType = "Polygon", geometryCoordsJson = polygone, latitude = 42.95, longitude = 1.47,
        idDataset = 12, stationName = "Prairie", comment = "Commentaire d'origine", dateMin = 1L, dateMax = 1L,
        altitudeMin = 1200, altitudeMax = 1250, surface = 5000,
        habitats = listOf(habitat(91, 629, "Prairie de fauche")),
        envoyeGeoNature = true, origineServeur = true,
    )

    /** Le serveur, après le passage d'un collègue sur le web : un commentaire et un habitat. */
    private val serveur = origine.copy(
        comment = "Commentaire du web",
        habitats = origine.habitats + habitat(92, 631, "Ajouté sur le web"),
    )

    @Test
    fun un_habitat_cree_par_un_envoi_sans_reponse_n_est_pas_renvoye_en_double() {
        // Audit du 21 octobre, C5 : le POST de mise à jour a créé H2 (id 101) sur le serveur, mais sa
        // réponse s'est perdue. Au renvoi, la relecture le contient : c'est celui de l'appli, reconnu à
        // son identifiant SINP, qui prend l'id du serveur au lieu de repartir sans id.
        val h2 = habitat(null, 640, "Ajouté dans l'appli").copy(uuidHabitat = "AAAA-1111", recouvrement = 20.0)
        val locale = origine.copy(habitats = origine.habitats + h2)
        val surLeServeur = origine.copy(habitats = origine.habitats + habitat(101, 640, "Ajouté dans l'appli").copy(uuidHabitat = "aaaa-1111"))

        val fusion = FusionStation.fusionner(origine, locale, surLeServeur)

        assertEquals(listOf(91, 101), fusion.habitats.map { it.idHabitatServeur })
        assertEquals(20.0, fusion.habitats.last().recouvrement, "le contenu de l'appli")
        assertEquals(h2.id, fusion.habitats.last().id)
    }

    @Test
    fun la_geometrie_se_compare_au_centimetre_pres() {
        val arrondie = origine.copy(geometryCoordsJson = "[[1.40000000004,42.9],[1.5,42.90000000003],[1.5,43.0]]")
        assertTrue(FusionStation.memeGeometrie(origine, arrondie))
        val deplacee = origine.copy(geometryCoordsJson = "[[1.4,42.9],[1.5,42.9],[1.5001,43.0]]")
        assertFalse(FusionStation.memeGeometrie(origine, deplacee))
        assertFalse(FusionStation.memeGeometrie(origine, origine.copy(geometryCoordsJson = "[[1.4,42.9],[1.5,42.9]]")))
        val point = OccHabStation(geometryType = "Point", latitude = 42.9, longitude = 1.4)
        assertTrue(FusionStation.memeGeometrie(point, point.copy(latitude = 42.90000000001)))
        assertFalse(FusionStation.memeGeometrie(point, origine))
    }

    @Test
    fun une_voisine_deplacee_ne_renvoie_que_sa_geometrie() {
        val anneau = GeoJsonCoords.parse("[[1.4,42.9],[1.5,42.9],[1.55,43.05]]")
        val locale = CarteOccHab.voisineDeplacee(origine, anneau, copieLocale = false)

        val fusion = FusionStation.fusionner(locale.origine()!!, locale, serveur)

        assertEquals(locale.geometryCoordsJson, fusion.geometryCoordsJson)
        assertEquals(locale.surface, fusion.surface)
        assertEquals("Commentaire du web", fusion.comment)
        assertEquals(listOf(91, 92), fusion.habitats.map { it.idHabitatServeur }, "l'habitat ajouté sur le web reste")
    }

    @Test
    fun les_modifications_de_l_appli_et_du_serveur_se_cumulent() {
        val locale = origine.copy(
            stationName = "Pelouse sèche",
            habitats = listOf(habitat(91, 629, "Prairie de fauche", recouvrement = 30.0), habitat(null, 640, "Nouveau dans l'appli")),
        )

        val fusion = FusionStation.fusionner(origine, locale, serveur)

        assertEquals("Pelouse sèche", fusion.stationName)
        assertEquals("Commentaire du web", fusion.comment)
        assertEquals(polygone, fusion.geometryCoordsJson)
        assertEquals(1200, fusion.altitudeMin)
        assertEquals(listOf(91, 92, null), fusion.habitats.map { it.idHabitatServeur })
        assertEquals(30.0, fusion.habitats.first().recouvrement, "l'habitat modifié dans l'appli garde sa version")
    }

    @Test
    fun une_valeur_changee_des_deux_cotes_garde_celle_de_l_appli() {
        val fusion = FusionStation.fusionner(origine, origine.copy(comment = "Appli"), serveur)
        assertEquals("Appli", fusion.comment)
    }

    @Test
    fun un_habitat_supprime_d_un_cote_ne_revient_pas() {
        val a = habitat(91, 629, "A")
        val b = habitat(92, 631, "B")
        val base = origine.copy(habitats = listOf(a, b))
        // A supprimé dans l'appli, B supprimé sur le web.
        val fusion = FusionStation.fusionner(base, base.copy(habitats = listOf(b)), base.copy(habitats = listOf(a)))
        assertTrue(fusion.habitats.isEmpty(), "ni l'un ni l'autre ne revient : ${fusion.habitats}")
    }

    @Test
    fun les_dates_d_une_reprise_restent_celles_de_l_appli() {
        // À l'import, la station prend les dates de la session : une reprise sur le terrain est une
        // nouvelle observation.
        val fusion = FusionStation.fusionner(origine, origine.copy(dateMin = 2L, dateMax = 2L), serveur)
        assertEquals(2L, fusion.dateMin)
        assertEquals(2L, fusion.dateMax)
    }

    @Test
    fun une_origine_aux_habitats_sans_id_laisse_la_liste_de_l_appli_faire_foi() {
        // Créés par l'appli avant qu'elle ne garde leurs ids : impossible de savoir lesquels du
        // serveur ils sont, et les ajouter tous les mettrait en double.
        val base = origine.copy(habitats = listOf(habitat(null, 629, "Créé par l'appli")))
        val locale = base.copy(habitats = base.habitats + habitat(null, 640, "Nouveau"))
        val surLeServeur = base.copy(habitats = listOf(habitat(501, 629, "Créé par l'appli"), habitat(502, 631, "Web")))

        assertEquals(locale.habitats, FusionStation.fusionner(base, locale, surLeServeur).habitats)
    }

    @Test
    fun la_base_se_relit_telle_que_figee() {
        val copie = origine.copy(stationName = "Modifiée", origineJson = origine.versOrigineJson())
        assertEquals(origine, copie.origine())
    }
}
