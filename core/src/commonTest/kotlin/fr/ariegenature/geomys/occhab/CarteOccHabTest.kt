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

// Gardes du lot 8 sur ce que OccHabCarteFragment, StationsServeurOverlay et StationsSessionOverlay de
// GeoMys-Android @ cdd5196 faisaient sans test : ce qui est dessiné, le classement des stations du
// serveur, l'aimantage, les textes, la fin de saisie. Le cas du cadrage réécrit en test de comportement
// la garde textuelle d'OccHabEmpriseStableTest « le cadrage ne se replie jamais sur les stations ».

package fr.ariegenature.geomys.occhab

import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.model.OccHabSaisie
import fr.ariegenature.geomys.model.OccHabStation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CarteOccHabTest {

    private fun station(id: String, idServeur: Int? = null, envoyee: Boolean = false) =
        OccHabStation(id = id, idStationServeur = idServeur, envoyeGeoNature = envoyee, latitude = 42.9, longitude = 1.4)

    @Test
    fun textes_du_bandeau() {
        assertEquals("Touchez pour placer le point · appui long pour le déplacer", CarteOccHab.texteMode(ModeGeometrie.POINT, chargee = false))
        assertEquals("Touchez pour ajouter des sommets (≥ 3) · appui long pour déplacer", CarteOccHab.texteMode(ModeGeometrie.POLYGONE, chargee = false))
        assertEquals("Appui long sur le point : déplacer · « Annuler » : quitter l'édition", CarteOccHab.texteMode(ModeGeometrie.POINT, chargee = true))
        assertTrue(CarteOccHab.texteMode(ModeGeometrie.POLYGONE, chargee = true).startsWith("Poignée + : ajouter un sommet"))
        assertTrue(CarteOccHab.texteSelection(true).contains("violette = déjà sur GeoNature"))
        assertEquals("Sélectionnez une station pour la modifier, ou saisissez une nouvelle station", CarteOccHab.texteSelection(false))
    }

    @Test
    fun stations_locales_toutes_saisies_sauf_envoyees_et_station_editee() {
        val saisies = listOf(
            OccHabSaisie(id = "s1", stations = listOf(station("a"), station("b", envoyee = true))),
            OccHabSaisie(id = "s2", stations = listOf(station("c"), station("d"))),
        )
        assertEquals(listOf("a", "d"), CarteOccHab.stationsLocales(saisies, stationEditeeId = "c").map { it.id })
    }

    @Test
    fun stations_du_serveur_sans_copie_en_attente_dessinees_en_violet() {
        val serveur = listOf(station("v1", 1), station("v2", 2), station("v3", 3), station("v4", 4))
        val ailleurs = OccHabSaisie(id = "autre", stations = listOf(station("c2", 2)))
        val ici = OccHabSaisie(id = "courante", stations = listOf(station("c3", 3)))
        val classees = CarteOccHab.classerServeur(
            serveur,
            idsNonEnvoyes = setOf(2, 3),
            copiesLocales = mapOf(2 to (ailleurs to ailleurs.stations[0]), 3 to (ici to ici.stations[0])),
            idServeurEdite = 4,
            saisieId = "courante",
        )
        // 2 et 3 ont une copie en attente (rouge), 4 est en cours d'édition (bleue).
        assertEquals(listOf("v1"), classees.violettes.map { it.id })
        assertEquals(1, classees.nbReprisesAilleurs, "seule la copie de 2 vit dans une autre saisie")
    }

    @Test
    fun aimantage_au_plus_proche_sous_le_seuil_de_44_dp() {
        assertEquals(2, CarteOccHab.plusProche(listOf(50f, 30f, 12f, 44f)))
        assertEquals(1, CarteOccHab.plusProche(listOf(60f, 44f)), "le seuil est compris")
        assertNull(CarteOccHab.plusProche(listOf(45f, 100f)))
        assertNull(CarteOccHab.plusProche(emptyList()))
        // Cibles : sommets des polygones, points des stations ponctuelles.
        val poly = OccHabStation(geometryType = "Polygon", geometryCoordsJson = "[[1.0,42.0],[1.5,42.0],[1.5,42.5]]")
        assertEquals(4, CarteOccHab.ciblesAimantage(listOf(poly), listOf(station("p"))).size)
    }

    /** Ancienne garde textuelle : sans géométrie en cours, l'emprise ne saute pas sur les stations
     *  — on reprend l'emprise mémorisée, et à la toute première ouverture l'Ariège. */
    @Test
    fun le_cadrage_ne_se_replie_jamais_sur_les_stations() {
        val memorise = Triple(42.8, 1.6, 15.0)
        assertEquals(CadrageOccHab.Centre(LatLon(42.8, 1.6), 15.0, impose = true), CarteOccHab.cadrageOuverture(emptyList(), memorise))
        assertEquals(CadrageOccHab.Centre(LatLon(42.93, 1.40), 11.0, impose = false), CarteOccHab.cadrageOuverture(emptyList(), null))
        // Une géométrie en cours : son emprise (boîte de ± 0,004° pour un point).
        val surPoint = CarteOccHab.cadrageOuverture(listOf(LatLon(42.0, 1.0)), memorise) as CadrageOccHab.SurEmprise
        assertEquals(41.996, surPoint.emprise.sud, 1e-9)
        assertEquals(1.004, surPoint.emprise.est, 1e-9)
    }

    @Test
    fun message_du_chargement_des_stations_du_serveur() {
        assertEquals("12 station(s) du serveur affichée(s)", CarteOccHab.messageChargement(12, null, 0, 0))
        assertTrue(
            CarteOccHab.messageChargement(12, dateCache = 0L, nbIci = 2, nbAilleurs = 1)
                .matches(Regex("""12 station\(s\) du serveur \(dont 2 déjà dans cette saisie, 1 reprise\(s\) dans une autre saisie\) — synchro du \d\d/\d\d/\d{4}""")),
        )
    }

    @Test
    fun options_de_fin_de_saisie_et_dialogues() {
        assertEquals(listOf("Enregistrer et quitter", "Enregistrer et envoyer sur GeoNature", "Continuer la saisie"), CarteOccHab.optionsTerminer(true))
        assertEquals(listOf("Enregistrer et quitter", "Continuer la saisie"), CarteOccHab.optionsTerminer(false))
        val nommee = OccHabStation(stationName = "Tourbière", habitats = listOf(fr.ariegenature.geomys.model.OccHabHabitat()))
        assertTrue(CarteOccHab.messageImport(nommee).startsWith("« Tourbière »\n\nLa station et ses 1 habitat(s) sera ouverte"))
        assertTrue(CarteOccHab.messageImport(OccHabStation()).startsWith("La station sera ouverte"))
        assertTrue(CarteOccHab.messageCopieLocale(nommee, 0L, traceEnCours = true).endsWith("\n\nLe tracé en cours (non validé) sera abandonné."))
    }
}
