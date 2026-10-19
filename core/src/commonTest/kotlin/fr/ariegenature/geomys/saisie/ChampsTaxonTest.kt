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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/ChampsTaxonTest.kt et
// GroupesEtRegnoPlanteTest.kt) : les deux fichiers se rejoignent, la branche PLANTE n'ayant plus
// besoin de Robolectric (TaxRefCache est une instance sur des réglages en mémoire).

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.store.NomenclatureCache
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Contexte taxonomique (groupesEtRegno) pour le filtrage des valeurs de nomenclature. La
 *  visibilité des champs est pilotée par la config serveur (OcctaxFieldsConfig), plus par taxon. */
class ChampsTaxonTest {

    private val t = ServicesDeTest()
    private val nomenclatures get() = t.services.nomenclatures

    @AfterTest
    fun nettoyer() = t.nettoyer()

    @Test
    fun regno_fonge_et_animalia() {
        val fonge = ChampsTaxon.groupesEtRegno(Taxon.FONGE, "", nomenclatures)
        assertEquals("Fungi", fonge.second)
        assertEquals(NomenclatureCache.GROUPES_FONGE, fonge.first)
        assertEquals(Pair(setOf("Animalia"), "Animalia"), ChampsTaxon.groupesEtRegno(Taxon.MOLLUSQUE, "", nomenclatures))
        assertEquals(Pair(setOf("Animalia"), "Animalia"), ChampsTaxon.groupesEtRegno(Taxon.INVERTEBRES, "", nomenclatures))
    }

    @Test
    fun groupe_fallback_par_taxon_quand_groupe2inpn_vide() {
        assertEquals(Pair(setOf("Oiseaux"), "Animalia"), ChampsTaxon.groupesEtRegno(Taxon.OISEAU, "", nomenclatures))
        assertEquals(Pair(setOf("Amphibiens"), "Animalia"), ChampsTaxon.groupesEtRegno(Taxon.BATRACIEN, "", nomenclatures))
    }

    @Test
    fun groupe2inpn_explicite_prime_sur_le_fallback() {
        val (groupes, regno) = ChampsTaxon.groupesEtRegno(Taxon.OISEAU, "Rapaces diurnes", nomenclatures)
        assertEquals(setOf("Rapaces diurnes"), groupes)
        assertEquals("Animalia", regno)
    }

    /** Branche PLANTE : intersection des groupes botaniques connus avec ceux réellement présents
     *  dans le cache TaxRef. */
    @Test
    fun plante_intersecte_avec_les_groupes_presents_dans_le_cache() {
        // Cache contenant un groupe botanique connu + un groupe animal hors botanique.
        t.services.taxRef.comptesGroupes = mapOf("Angiospermes" to 10, "Oiseaux" to 5)
        val (groupes, regno) = ChampsTaxon.groupesEtRegno(Taxon.PLANTE, "", nomenclatures)
        assertEquals(setOf("Angiospermes"), groupes) // "Oiseaux" écarté (non botanique)
        assertEquals("Plantae", regno)
    }

    @Test
    fun plante_fallback_sur_tous_les_groupes_si_cache_vide() {
        t.services.taxRef.comptesGroupes = emptyMap()
        val (groupes, regno) = ChampsTaxon.groupesEtRegno(Taxon.PLANTE, "", nomenclatures)
        assertEquals(NomenclatureCache.GROUPES_BOTANIQUES, groupes)
        assertEquals("Plantae", regno)
    }
}
