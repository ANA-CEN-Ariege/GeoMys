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
// GroupesEtRegnoPlanteTest.kt), puis réécrit à la vérification des champs du 20 octobre : le taxon
// filtre les nomenclatures par son règne et ses groupes réels, comme sur le web.

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.store.GroupeTaxonomique
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Le contexte taxonomique ([ChampsTaxon.groupeTaxonomique]) du filtre des valeurs de nomenclature :
 *  le règne et les groupes du taxon dans le cache TaxRef, ceux que le web transmet au serveur. */
class ChampsTaxonTest {

    private val t = ServicesDeTest()
    private val taxRef get() = t.services.taxRef

    @AfterTest
    fun nettoyer() = t.nettoyer()

    @Test
    fun le_regne_et_les_groupes_du_cache() {
        // Helix pomatia : un gastéropode, et non « Animalia » pris pour un groupe.
        taxRef.ajouterGroupes(mapOf(66798 to "Mollusques"))
        taxRef.ajouterGroupes1etRegnes(emptyMap(), mapOf(66798 to "Animalia"))
        taxRef.ajouterGroupes3(mapOf(66798 to "Gastéropodes"))
        assertEquals(
            GroupeTaxonomique("Animalia", "Mollusques", "Gastéropodes"),
            ChampsTaxon.groupeTaxonomique(Taxon.MOLLUSQUE, 66798, taxRef),
        )
    }

    @Test
    fun a_defaut_le_groupe_de_la_palette() {
        assertEquals(GroupeTaxonomique("Animalia", "Oiseaux", null), ChampsTaxon.groupeTaxonomique(Taxon.OISEAU, null, taxRef))
        assertEquals(GroupeTaxonomique("Animalia", "Amphibiens", null), ChampsTaxon.groupeTaxonomique(Taxon.BATRACIEN, 999, taxRef))
        // Les groupes qui en couvrent plusieurs : le règne seul.
        assertEquals(GroupeTaxonomique("Plantae", null, null), ChampsTaxon.groupeTaxonomique(Taxon.PLANTE, null, taxRef))
        assertEquals(GroupeTaxonomique("Fungi", null, null), ChampsTaxon.groupeTaxonomique(Taxon.FONGE, null, taxRef))
        assertEquals(GroupeTaxonomique("Animalia", null, null), ChampsTaxon.groupeTaxonomique(Taxon.INVERTEBRES, null, taxRef))
    }
}
