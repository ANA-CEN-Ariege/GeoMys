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

// Propre au portage : ces règles vivaient dans l'ExplorerFragment de GeoMys-Android @ cdd5196, sans test. Les
// attendus sont ceux de son code, sauf la durée du compteur (défaut n° 10 de l'inventaire, corrigé).

package fr.ariegenature.geomys.explorer

import fr.ariegenature.geomys.geo.Emprise
import fr.ariegenature.geomys.model.ObsExplorer
import fr.ariegenature.geomys.model.Taxon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExplorerTest {

    private fun obs(
        nom: String,
        lat: Double,
        lon: Double,
        taxon: Taxon? = Taxon.OISEAU,
        date: String = "2026-05-12",
        nombre: Int = 1,
        sci: String = "",
        cite: String = nom,
    ) = ObsExplorer(nomCite = cite, nomVern = nom, nomSci = sci, latitude = lat, longitude = lon, date = date, nombre = nombre, taxon = taxon)

    @Test
    fun les_observations_d_une_meme_cellule_de_110_m_font_un_seul_marqueur() {
        val a = obs("merle noir", 42.96512, 1.60531)
        val b = obs("pic vert", 42.96538, 1.60549, Taxon.OISEAU)
        val c = obs("renard roux", 42.97001, 1.61002, Taxon.MAMMIFERE)
        val groupes = Explorer.regrouper(listOf(a, c, b))
        assertEquals(2, groupes.size)
        assertEquals(listOf(a, b), groupes[0].observations, "dans l'ordre de leur première observation")
        assertEquals(a, groupes[0].premiere)
        assertEquals(42.96512 to 1.60531, groupes[0].latitude to groupes[0].longitude, "à la position de la première")
        assertEquals(listOf(c), groupes[1].observations)
    }

    @Test
    fun le_filtre_garde_le_groupe_choisi_et_rien_ne_filtre_sans_groupe() {
        val liste = listOf(obs("merle", 43.0, 1.6), obs("renard", 43.0, 1.6, Taxon.MAMMIFERE))
        assertEquals(listOf("renard"), Explorer.filtrer(liste, Taxon.MAMMIFERE).map { it.nomVern })
        assertEquals(liste, Explorer.filtrer(liste, null))
        assertEquals(10, Explorer.GROUPES.size)
        assertEquals(Taxon.entries.toSet(), Explorer.GROUPES.toSet())
    }

    @Test
    fun une_observation_sans_groupe_n_est_montree_que_sans_filtre_et_ne_colore_pas_son_marqueur() {
        // 21 octobre 2026 : un taxon absent du téléphone est gardé, sans groupe (icône neutre).
        val inconnu = obs("inconnu", 43.0, 1.6, taxon = null)
        val renard = obs("renard", 43.0, 1.6, Taxon.MAMMIFERE)
        assertEquals(listOf(inconnu, renard), Explorer.filtrer(listOf(inconnu, renard), null))
        assertEquals(listOf(renard), Explorer.filtrer(listOf(inconnu, renard), Taxon.MAMMIFERE))
        // Le marqueur prend le groupe de la première observation qui en a un : la couleur d'avant.
        val groupe = Explorer.regrouper(listOf(inconnu, renard)).single()
        assertEquals(inconnu, groupe.premiere)
        assertEquals(Taxon.MAMMIFERE, groupe.taxon)
        assertNull(Explorer.regrouper(listOf(inconnu)).single().taxon)
    }

    @Test
    fun la_duree_du_compteur_est_celle_du_serveur() {
        // Défaut n° 10 : GeoMys-Android affichait « 12 mois » quelle que soit la profondeur du serveur.
        assertEquals("42 obs. (12 mois)", Explorer.compteur(42, 365))
        assertEquals("24 mois", Explorer.duree(730))
        assertEquals("3 mois", Explorer.duree(90))
        assertEquals("100 j", Explorer.duree(100))
    }

    @Test
    fun au_dela_de_100_km_de_large_rien_n_est_demande() {
        assertTrue(Explorer.largeurKm(Emprise(sud = 42.9, ouest = 1.5, nord = 43.0, est = 1.7)) < Explorer.LARGEUR_MAX_KM)
        assertTrue(Explorer.largeurKm(Emprise(sud = 42.0, ouest = 0.0, nord = 44.0, est = 2.0)) > Explorer.LARGEUR_MAX_KM)
        assertEquals("Zoomez pour charger les observations (zone > 100 km)", Explorer.ZONE_TROP_LARGE)
    }

    @Test
    fun la_liste_d_une_cellule_commence_par_la_plus_recente() {
        val groupe = Explorer.regrouper(listOf(obs("a", 43.0, 1.6, date = "2026-03-01"), obs("b", 43.0, 1.6, date = "2026-09-30"))).single()
        assertEquals(listOf("b", "a"), Explorer.liste(groupe).map { it.nomVern })
    }

    @Test
    fun le_titre_et_le_detail_d_une_observation() {
        assertEquals("Merle noir", Explorer.titre(obs("merle noir", 43.0, 1.6)))
        assertEquals("Turdus merula", Explorer.titre(obs("", 43.0, 1.6, cite = "turdus merula")), "le nom cité faute de vernaculaire")
        assertEquals("2026-05-12 · 3 ind. · Turdus merula", Explorer.detail(obs("merle noir", 43.0, 1.6, nombre = 3, sci = "Turdus merula")))
        assertEquals("2026-05-12", Explorer.detail(obs("merle noir", 43.0, 1.6, sci = "merle noir")), "ni « 1 ind. », ni le nom répété")
        assertEquals("2 ind.", Explorer.detail(obs("merle noir", 43.0, 1.6, date = "", nombre = 2)))
    }
}
