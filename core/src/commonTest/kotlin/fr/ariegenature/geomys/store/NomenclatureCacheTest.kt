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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/NomenclatureCacheTest.kt) :
// Robolectric remplacé par un dossier temporaire et des réglages en mémoire, les objets du
// référentiel par des instances, JUnit par kotlin.test.

package fr.ariegenature.geomys.store

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.FileSystem
import okio.SYSTEM

/** Filtrage des valeurs de nomenclature par le taxon (règne, group2, group3), celui du serveur
 *  (alimente les listes de caractérisation et de dénombrement), et valeurs par défaut serveur. */
class NomenclatureCacheTest {

    // Les objets d'Android deviennent des instances neuves par test, dans un dossier temporaire
    // et des réglages en mémoire (Robolectric sous Android).
    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val taxRef = TaxRefCache(dossier, MapSettings())
    private val nomenclatures = NomenclatureCache(MapSettings(), taxRef)

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    @BeforeTest
    fun setup() {
        nomenclatures.vider()
        nomenclatures.setDefauts(emptyMap())
    }

    private fun labels(regne: String?, group2: String?, group3: String? = null) =
        nomenclatures.filtrerPourTaxon("STATUT_BIO", GroupeTaxonomique(regne, group2, group3), filtresActifs = true).map { it.label }.toSet()

    @Test
    fun valeur_sans_ligne_taxonomique_exclue_quand_le_taxon_est_connu() {
        // La jointure du serveur : une valeur sans ligne n'est jamais proposée pour un taxon.
        nomenclatures.setAll(mapOf("STATUT_BIO" to listOf(NomValeur(1, "Sans ligne"))))
        assertEquals(emptySet(), labels("Animalia", "Oiseaux"))
        assertEquals(setOf("Sans ligne"), labels(null, null), "sans taxon, tout")
    }

    @Test
    fun la_ligne_doit_verifier_regne_et_groupes_a_la_fois() {
        nomenclatures.setAll(mapOf("STATUT_BIO" to listOf(
            NomValeur(1, "Joker", listOf(TaxrefRestriction("all", "Oiseaux"))),
        )))
        // Règne joker, mais groupe 2 « Oiseaux » : pas pour un mammifère (GeoMys-Android le proposait).
        assertEquals(emptySet(), labels("Animalia", "Mammifères"))
        assertEquals(setOf("Joker"), labels("Animalia", "Oiseaux"))
    }

    @Test
    fun regne_plus_group2_all_couvre_tout_le_royaume() {
        nomenclatures.setAll(mapOf("STATUT_BIO" to listOf(
            NomValeur(1, "Anim", listOf(TaxrefRestriction("Animalia", "all"))),
        )))
        assertTrue("Anim" in labels("Animalia", "Oiseaux"))
        assertFalse("Anim" in labels("Plantae", "Angiospermes"), "autre regne exclu")
    }

    @Test
    fun groupe_exact_insensible_a_la_casse() {
        nomenclatures.setAll(mapOf("STATUT_BIO" to listOf(
            NomValeur(1, "Oiseau", listOf(TaxrefRestriction("Animalia", "Oiseaux"))),
            NomValeur(2, "Plante", listOf(TaxrefRestriction("Plantae", "Angiospermes"))),
        )))
        assertEquals(setOf("Oiseau"), labels("Animalia", "oiseaux"))
    }

    @Test
    fun groupe_3() {
        nomenclatures.setAll(mapOf("STATUT_BIO" to listOf(
            NomValeur(1, "Gastéropode", listOf(TaxrefRestriction("Animalia", "Mollusques", "Gastéropodes"))),
            NomValeur(2, "Mollusque", listOf(TaxrefRestriction("Animalia", "Mollusques"))),
        )))
        assertEquals(setOf("Gastéropode", "Mollusque"), labels("Animalia", "Mollusques", "Gastéropodes"))
        assertEquals(setOf("Mollusque"), labels("Animalia", "Mollusques", "Bivalves"))
    }

    @Test
    fun defauts_roundtrip() {
        nomenclatures.setDefauts(mapOf("STATUT_OBS" to "3"))
        assertEquals("3", nomenclatures.defautPour("STATUT_OBS"))
        assertNull(nomenclatures.defautPour("INEXISTANT"))
        assertEquals(mapOf("STATUT_OBS" to "3"), nomenclatures.tousLesDefauts())
    }

    @Test
    fun est_disponible_reflete_le_cache() {
        assertFalse(nomenclatures.estDisponible)
        nomenclatures.setAll(mapOf("X" to listOf(NomValeur(1, "v"))))
        assertTrue(nomenclatures.estDisponible)
    }
}
