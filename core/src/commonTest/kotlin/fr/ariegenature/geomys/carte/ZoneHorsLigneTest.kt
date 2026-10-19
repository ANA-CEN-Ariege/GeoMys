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

// Propre au portage : le panneau du Maps Manager, que GeoMys-Android calculait dans CacheManagerFragment
// (`majInfos`) sans test. Les attendus sont ceux de son code.

package fr.ariegenature.geomys.carte

import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.geo.TuileMath
import fr.ariegenature.geomys.util.formaterDecimal
import fr.ariegenature.geomys.util.formaterMilliers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZoneHorsLigneTest {

    private val foix = Emprise(sud = 42.95, ouest = 1.58, nord = 42.98, est = 1.63)

    @Test
    fun une_zone_raisonnable_se_telecharge_du_zoom_affiche_au_zoom_17() {
        val e = ZoneHorsLigne.estimation(FondChoisi.EnLigne(FondCarte.ORTHO), foix, zoomAffiche = 14.7)
        val surface = formaterDecimal(TuileMath.surfaceKm2(foix), 1)
        assertEquals("Surface : $surface km² · zooms 14–17", e.surface)
        assertTrue(surface.contains(','), "virgule décimale : $surface")
        val nombre = TuileMath.nombreDeTuiles(foix, 14, 17)
        assertEquals("≈ ${formaterMilliers(nombre.toLong())} tuiles · ~${formaterDecimal(nombre * 50 / 1024.0, 0)} MB · fond : IGN Ortho", e.tuiles)
        assertNull(e.avertissement)
        assertTrue(e.telechargeable)
    }

    @Test
    fun au_dela_de_200_km2_le_bouton_se_coupe_et_un_avertissement_invite_a_zoomer() {
        val ariege = Emprise(sud = 42.6, ouest = 1.0, nord = 43.3, est = 2.2)
        val e = ZoneHorsLigne.estimation(FondChoisi.EnLigne(FondCarte.TOPO), ariege, zoomAffiche = 9.0)
        assertEquals("Zone trop large (max 200 km²) — zoomer pour réduire.", e.avertissement)
        assertFalse(e.telechargeable)
        assertTrue(e.tuiles.endsWith("fond : IGN Topo"))
    }

    @Test
    fun tous_les_fonds_en_ligne_se_telechargent_openstreetmap_compris() {
        // La décision 13 de l'inventaire (exclure OSM) est écartée le 19 octobre : comme sous GeoMys-Android.
        FondCarte.entries.forEach { fond ->
            val e = ZoneHorsLigne.estimation(FondChoisi.EnLigne(fond), foix, zoomAffiche = 15.0)
            assertTrue(e.telechargeable, fond.name)
            assertNull(e.avertissement, fond.name)
            assertTrue(e.tuiles.endsWith("fond : ${ZoneHorsLigne.nomFond(fond)}"), e.tuiles)
        }
    }

    @Test
    fun un_fond_mbtiles_n_a_rien_a_telecharger() {
        val e = ZoneHorsLigne.estimation(FondChoisi.Mbtiles("ariege.mbtiles"), foix, zoomAffiche = 15.0)
        assertEquals(EstimationZone("Fond hors-ligne (MBTiles)", "Déjà disponible hors-ligne — rien à télécharger", null, false), e)
    }

    @Test
    fun le_premier_niveau_est_le_zoom_affiche_tronque_au_moins_1() {
        assertEquals(1, ZoneHorsLigne.zoomMin(0.4))
        assertEquals(12, ZoneHorsLigne.zoomMin(12.99))
    }

    @Test
    fun le_poids_estime_depend_du_format_des_tuiles() {
        assertEquals(25, ZoneHorsLigne.poidsTuileKo(FondCarte.OPENTOPO))
        assertEquals(50, ZoneHorsLigne.poidsTuileKo(FondCarte.SCAN25))
        assertEquals(listOf("OSM", "OpenTopoMap", "IGN Topo", "IGN Scan25", "IGN Ortho", "Esri Imagery"), FondCarte.entries.map(ZoneHorsLigne::nomFond))
    }

    @Test
    fun l_occupation_passe_en_rouge_a_partir_de_90_pourcent() {
        val mo = 1024L * 1024
        assertEquals("Cache : 120 / 1024 MB (12 %)" to false, ZoneHorsLigne.occupation(120 * mo))
        assertEquals("Cache : 930 / 1024 MB (91 %)" to true, ZoneHorsLigne.occupation(930 * mo))
        assertEquals("Cache : 0 / 1024 MB (0 %)" to false, ZoneHorsLigne.occupation(0))
    }

    @Test
    fun les_milliers_sont_separes_par_une_espace_fine() {
        assertEquals("5 294", formaterMilliers(5294))
        assertEquals("1 234 567", formaterMilliers(1_234_567))
        assertEquals("999", formaterMilliers(999))
    }
}
