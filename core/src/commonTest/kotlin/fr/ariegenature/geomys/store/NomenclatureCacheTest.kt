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

/** Filtrage des valeurs de nomenclature par groupe taxonomique / regno (alimente les spinners
 *  de caractérisation/dénombrement) + valeurs par défaut serveur. */
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

    private fun labels(groupes: Set<String>, regno: String) =
        nomenclatures.filtrerPourGroupes("STATUT_BIO", groupes, regno).map { it.label }.toSet()

    @Test
    fun valeur_sans_restriction_taxref_toujours_incluse() {
        nomenclatures.setAll(mapOf("STATUT_BIO" to listOf(NomValeur(1, "Universel"))))
        assertEquals(setOf("Universel"), labels(setOf("Oiseaux"), "Animalia"))
    }

    @Test
    fun groupes_vides_renvoie_tout() {
        nomenclatures.setAll(mapOf("STATUT_BIO" to listOf(
            NomValeur(1, "A", listOf(TaxrefRestriction("Animalia", "Oiseaux"))),
        )))
        assertEquals(setOf("A"), labels(emptySet(), ""))
    }

    @Test
    fun joker_regne_all_universel() {
        nomenclatures.setAll(mapOf("STATUT_BIO" to listOf(
            NomValeur(1, "Joker", listOf(TaxrefRestriction("all", "Oiseaux"))),
        )))
        assertEquals(setOf("Joker"), labels(setOf("Mammifères"), "Animalia"))
    }

    @Test
    fun regne_plus_group2_all_couvre_tout_le_royaume() {
        nomenclatures.setAll(mapOf("STATUT_BIO" to listOf(
            NomValeur(1, "Anim", listOf(TaxrefRestriction("Animalia", "all"))),
        )))
        assertTrue("Anim" in labels(setOf("Oiseaux"), "Animalia"))
        assertFalse("Anim" in labels(setOf("Angiospermes"), "Plantae"), "autre regne exclu")
    }

    @Test
    fun groupe_exact_insensible_a_la_casse() {
        nomenclatures.setAll(mapOf("STATUT_BIO" to listOf(
            NomValeur(1, "Oiseau", listOf(TaxrefRestriction("Animalia", "Oiseaux"))),
            NomValeur(2, "Plante", listOf(TaxrefRestriction("Plantae", "Angiospermes"))),
        )))
        assertEquals(setOf("Oiseau"), labels(setOf("oiseaux"), "Animalia"))
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
