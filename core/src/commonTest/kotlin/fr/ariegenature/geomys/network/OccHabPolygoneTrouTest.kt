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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/OccHabPolygoneTrouTest.kt) :
// lecture depuis le serveur, renvoi et empreinte (cas 1 à 12, lot 3), puis anneaux, surface nette et
// géométrie de la session (les quatre derniers, lot 8, avec la carte d'édition OccHab). Robolectric
// retiré, JUnit remplacé par kotlin.test, GeoPoint par LatLon, OccHabViewModel par SessionOccHab.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.geo.LatLon
import fr.ariegenature.geomys.geo.airePolygoneM2
import fr.ariegenature.geomys.json.JSONArray
import fr.ariegenature.geomys.model.OccHabStation
import fr.ariegenature.geomys.occhab.SessionOccHab
import fr.ariegenature.geomys.util.GeoJsonCoords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * STATIONS À TROU (« polygone intérieur », bug terrain 2026-08-31, ex. station 2805) : une station
 * dessinée sous QGIS peut porter des anneaux INTÉRIEURS. L'appli n'en lisait que l'anneau extérieur →
 * le trou était invisible sur la carte, et surtout un simple ré-envoi (mise à jour) le SUPPRIMAIT
 * côté serveur. Couvre aussi le MultiPolygon (colonne serveur `Geometry("GEOMETRY")` : tout type est
 * possible).
 */
class OccHabPolygoneTrouTest {

    /** Carré extérieur 0..10 (fermé) avec un carré intérieur 2..4 (fermé) — le format que renvoie
     *  GeoNature (`ST_AsGeoJSON`, anneaux fermés). */
    private fun featureCollection(geometry: String) = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":$geometry,
           "properties":{"id_station":2805,"id_dataset":12,"station_name":"Tourbière"}}
        ]}
    """.trimIndent()

    private val polygoneATrou = """
        {"type":"Polygon","coordinates":[
          [[0,0],[10,0],[10,10],[0,10],[0,0]],
          [[2,2],[4,2],[4,4],[2,4],[2,2]]
        ]}
    """.trimIndent()

    /** Les sommets d'un anneau `[[lon,lat], …]` (GeoJsonCoords.parse sous Android). */
    private fun sommets(json: String?): JSONArray = JSONArray(json!!)

    /** Les anneaux intérieurs `[[[lon,lat], …], …]`, vides si aucun (GeoJsonCoords.parseAnneaux). */
    private fun anneaux(json: String?): JSONArray = if (json == null) JSONArray() else JSONArray(json)

    // ── Lecture depuis le serveur ──

    @Test
    fun polygone_a_trou_lu_le_trou_est_conserve() {
        val st = OccHabApi.parserFeatureCollection(featureCollection(polygoneATrou)).single()
        assertEquals("Polygon", st.geometryType)
        // Anneau extérieur : point de fermeture retiré (convention de l'éditeur).
        assertEquals(4, sommets(st.geometryCoordsJson).length())
        val trous = anneaux(st.geometryTrousJson)
        assertEquals(1, trous.length(), "un trou conservé")
        assertEquals(4, trous.getJSONArray(0).length(), "fermeture retirée aussi sur le trou")
        assertEquals(2.0, trous.getJSONArray(0).getJSONArray(0).getDouble(0), 1e-9) // longitude
        assertEquals(2.0, trous.getJSONArray(0).getJSONArray(0).getDouble(1), 1e-9) // latitude
        assertFalse(st.geometryPartielle)
    }

    @Test
    fun polygone_plein_pas_de_trou() {
        val st = OccHabApi.parserFeatureCollection(
            featureCollection("""{"type":"Polygon","coordinates":[[[0,0],[10,0],[10,10],[0,0]]]}""")
        ).single()
        assertNull(st.geometryTrousJson)
        assertFalse(st.geometryPartielle)
    }

    @Test
    fun centroide_calcule_sur_l_anneau_exterieur() {
        val st = OccHabApi.parserFeatureCollection(featureCollection(polygoneATrou)).single()
        // Moyenne des 4 sommets extérieurs (0,0)(10,0)(10,10)(0,10) — le trou n'entre pas dedans.
        assertEquals(5.0, st.latitude, 1e-9)
        assertEquals(5.0, st.longitude, 1e-9)
    }

    @Test
    fun trou_degenere_ignore() {
        val st = OccHabApi.parserFeatureCollection(featureCollection("""
            {"type":"Polygon","coordinates":[[[0,0],[10,0],[10,10],[0,0]],[[2,2],[3,3]]]}
        """.trimIndent())).single()
        assertNull(st.geometryTrousJson, "anneau de 2 sommets = pas un trou")
    }

    // ── MultiPolygon (colonne serveur générique : QGIS peut en produire) ──

    @Test
    fun multipolygon_a_une_seule_partie_traite_comme_un_polygone() {
        val st = OccHabApi.parserFeatureCollection(featureCollection("""
            {"type":"MultiPolygon","coordinates":[[
              [[0,0],[10,0],[10,10],[0,10],[0,0]],
              [[2,2],[4,2],[4,4],[2,4],[2,2]]
            ]]}
        """.trimIndent())).single()
        assertEquals("Polygon", st.geometryType)
        assertEquals(4, sommets(st.geometryCoordsJson).length())
        assertEquals(1, anneaux(st.geometryTrousJson).length())
        assertFalse(st.geometryPartielle, "une seule partie = géométrie complète")
    }

    @Test
    fun multipolygon_multi_parties_marque_partielle() {
        val st = OccHabApi.parserFeatureCollection(featureCollection("""
            {"type":"MultiPolygon","coordinates":[
              [[[0,0],[10,0],[10,10],[0,0]]],
              [[[20,20],[30,20],[30,30],[20,20]]]
            ]}
        """.trimIndent())).single()
        assertEquals("Polygon", st.geometryType)
        assertTrue(st.geometryPartielle, "non importable : un renvoi perdrait les autres parties")
        // La 1ʳᵉ partie reste AFFICHABLE (repère sur la carte + aimantage).
        assertEquals(3, sommets(st.geometryCoordsJson).length())
    }

    // ── Renvoi au serveur (mise à jour) ──

    @Test
    fun envoi_referme_l_exterieur_et_les_trous() {
        val g = GeoNatureUpload.construireGeometrie(
            type = "Polygon",
            coordsJson = "[[0,0],[10,0],[10,10],[0,10]]",
            lat = 5.0, lon = 5.0,
            trousJson = "[[[2,2],[4,2],[4,4],[2,4]]]",
        )
        assertEquals("Polygon", g.getString("type"))
        val anneaux = g.getJSONArray("coordinates")
        assertEquals(2, anneaux.length(), "extérieur + 1 trou")
        val ext = anneaux.getJSONArray(0)
        assertEquals(5, ext.length(), "anneau refermé")
        assertEquals(0.0, ext.getJSONArray(4).getDouble(0), 1e-9)
        val trou = anneaux.getJSONArray(1)
        assertEquals(5, trou.length(), "trou refermé")
        assertEquals(2.0, trou.getJSONArray(0).getDouble(0), 1e-9)
        assertEquals(2.0, trou.getJSONArray(4).getDouble(0), 1e-9)
    }

    @Test
    fun envoi_sans_trou_inchange() {
        // Non-régression Occtax : aucun appelant hors OccHab ne passe de trous.
        val g = GeoNatureUpload.construireGeometrie("Polygon", "[[0,0],[10,0],[10,10]]", 0.0, 0.0)
        assertEquals(1, g.getJSONArray("coordinates").length())
    }

    @Test
    fun trous_illisibles_envoi_du_polygone_plein_sans_crash() {
        val g = GeoNatureUpload.construireGeometrie(
            "Polygon", "[[0,0],[10,0],[10,10]]", 0.0, 0.0, trousJson = "{pas du json")
        assertEquals(1, g.getJSONArray("coordinates").length(), "l'extérieur part quand même")
    }

    @Test
    fun round_trip_serveur_le_trou_survit_a_une_mise_a_jour() {
        // LE bug : lire une station à trou puis la renvoyer telle quelle doit redonner la MÊME
        // géométrie (avant, le trou disparaissait du serveur).
        val st = OccHabApi.parserFeatureCollection(featureCollection(polygoneATrou)).single()
        val g = GeoNatureUpload.construireGeometrie(
            st.geometryType, st.geometryCoordsJson, st.latitude, st.longitude, st.geometryTrousJson)
        val anneaux = g.getJSONArray("coordinates")
        assertEquals(2, anneaux.length())
        assertEquals(5, anneaux.getJSONArray(0).length()) // extérieur fermé, 4 sommets + retour
        assertEquals(5, anneaux.getJSONArray(1).length()) // trou fermé
        // Sommets du trou identiques à l'origine.
        val trou = anneaux.getJSONArray(1)
        assertEquals(2.0, trou.getJSONArray(0).getDouble(0), 1e-9)
        assertEquals(4.0, trou.getJSONArray(1).getDouble(0), 1e-9)
        assertEquals(4.0, trou.getJSONArray(2).getDouble(1), 1e-9)
    }

    // ── Empreinte de contenu (« Mes stations » à la 1ʳᵉ modification) ──

    @Test
    fun un_trou_modifie_change_l_empreinte() {
        val base = OccHabStation(
            geometryType = "Polygon", geometryCoordsJson = "[[0,0],[10,0],[10,10]]",
            geometryTrousJson = "[[[2,2],[4,2],[4,4]]]",
        )
        assertNotEquals(
            base.empreinteContenu(),
            base.copy(geometryTrousJson = "[[[2,2],[5,2],[5,5]]]").empreinteContenu(),
        )
        assertNotEquals(
            base.empreinteContenu(), base.copy(geometryTrousJson = null).empreinteContenu(),
            "trou retiré = modification",
        )
    }

    @Test
    fun empreinte_insensible_au_formatage_des_trous() {
        val a = OccHabStation(geometryType = "Polygon", geometryCoordsJson = "[[0,0],[10,0],[10,10]]",
            geometryTrousJson = "[[[2,2],[4,2],[4,4]]]")
        val b = a.copy(geometryTrousJson = "[[[2.0,2.0],[4.0,2.0],[4.0,4.0]]]")
        assertEquals(a.empreinteContenu(), b.empreinteContenu())
    }

    @Test
    fun anneaux_round_trip_et_null_si_aucun_trou() {
        val anneaux = listOf(
            listOf(LatLon(42.0, 1.0), LatLon(42.0, 1.1), LatLon(42.1, 1.1)),
            listOf(LatLon(43.0, 2.0), LatLon(43.0, 2.1), LatLon(43.1, 2.1)),
        )
        val json = GeoJsonCoords.formatAnneaux(anneaux)
        val relus = GeoJsonCoords.parseAnneaux(json)
        assertEquals(2, relus.size)
        assertEquals(3, relus[0].size)
        assertEquals(42.0, relus[0][0].latitude, 1e-9)
        assertEquals(1.0, relus[0][0].longitude, 1e-9)
        assertEquals(43.1, relus[1][2].latitude, 1e-9)
        // Aucun trou = champ ABSENT (jamais un tableau vide) : le polygone est plein.
        assertNull(GeoJsonCoords.formatAnneaux(emptyList()))
        assertNull(GeoJsonCoords.formatAnneaux(listOf(emptyList())))
        assertTrue(GeoJsonCoords.parseAnneaux(null).isEmpty())
        assertTrue(GeoJsonCoords.parseAnneaux("{pas du json").isEmpty())
    }

    // ── Surface : aire NETTE (parité ST_Area côté serveur) ──

    @Test
    fun surface_deduit_les_trous() {
        // Carré ~1° de côté, trou de ~0,5° → l'aire nette vaut environ les 3/4 de la brute.
        val ext = listOf(LatLon(42.0, 1.0), LatLon(42.0, 2.0), LatLon(43.0, 2.0), LatLon(43.0, 1.0))
        val trou = listOf(LatLon(42.2, 1.2), LatLon(42.2, 1.7), LatLon(42.7, 1.7), LatLon(42.7, 1.2))
        val brute = airePolygoneM2(ext)
        val nette = airePolygoneM2(ext, listOf(trou))
        assertTrue(nette < brute, "le trou est déduit")
        assertEquals(brute - airePolygoneM2(trou), nette, 1.0)
        assertEquals(brute, airePolygoneM2(ext, emptyList()), 1e-6, "sans trou = aire brute")
    }

    @Test
    fun surface_jamais_negative() {
        val ext = listOf(LatLon(42.0, 1.0), LatLon(42.0, 1.1), LatLon(42.1, 1.1))
        val trouEnorme = listOf(LatLon(40.0, 0.0), LatLon(40.0, 5.0), LatLon(45.0, 5.0))
        assertEquals(0.0, airePolygoneM2(ext, listOf(trouEnorme)), 1e-9)
    }

    // ── Session : trous conservés à l'édition, purgés au redessin ──

    @Test
    fun definir_geometrie_conserve_ou_purge_les_trous() {
        val vm = SessionOccHab()
        val trous = "[[[2,2],[4,2],[4,4]]]"
        vm.definirGeometrie("Polygon", 5.0, 5.0, "[[0,0],[10,0],[10,10]]", trous)
        assertEquals(trous, vm.station.geometryTrousJson)
        // Redessin (tracé neuf) : aucun trou transmis → la station repart PLEINE.
        vm.definirGeometrie("Polygon", 1.0, 1.0, "[[0,0],[1,0],[1,1]]", null)
        assertNull(vm.station.geometryTrousJson)
        // Bascule en Point : les trous n'ont plus de sens.
        vm.definirGeometrie("Polygon", 5.0, 5.0, "[[0,0],[10,0],[10,10]]", trous)
        vm.definirGeometrie("Point", 42.9, 1.4, null)
        assertNull(vm.station.geometryTrousJson)
    }
}
