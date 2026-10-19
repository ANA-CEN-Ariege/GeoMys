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

// Les attendus suivent DicteeVocale.vocabulaire et premiersMots de GeoMys-IOS (UI/Saisie/DicteeVocale.swift).

package fr.ariegenature.geomys.saisie

import fr.ariegenature.geomys.occtax.groupeDeDepart
import fr.ariegenature.geomys.occtax.libelleChampEspece
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.store.SuggestionTaxon
import kotlin.test.Test
import kotlin.test.assertEquals

/** [vocabulaireDictee], et les petites règles de la palette des groupes. */
class VocabulaireDicteeTest {

    @Test
    fun une_liste_courte_est_soufflee_entiere_sans_article_ni_doublon() {
        val propositions = listOf(
            SuggestionTaxon("Triton palmé (Le)", 1),
            SuggestionTaxon("Triton palmé", 1),
            SuggestionTaxon("Pic vert", 2),
        )
        assertEquals(listOf("Triton palmé", "Pic vert"), vocabulaireDictee(propositions))
    }

    @Test
    fun une_liste_longue_est_resumee_a_ses_premiers_mots() {
        val noms = listOf("Pic vert", "Pic noir", "Mésange bleue", "Mésange charbonnière", "Pic épeiche", "Ai tridactyle", "Bouvreuil pivoine")
        val propositions = noms.mapIndexed { i, nom -> SuggestionTaxon(nom, i) }
        // Les plus répandus d'abord, puis l'ordre alphabétique ; « Ai », trop court, est écarté.
        assertEquals(listOf("Pic", "Mésange", "Bouvreuil"), vocabulaireDictee(propositions, maximum = 3))
    }

    @Test
    fun la_palette_part_du_groupe_voulu_s_il_a_un_bouton() {
        assertEquals(Taxon.PLANTE, groupeDeDepart(Taxon.PLANTE, listOf(Taxon.OISEAU, Taxon.PLANTE)))
        assertEquals(Taxon.OISEAU, groupeDeDepart(Taxon.FONGE, listOf(Taxon.OISEAU, Taxon.PLANTE)))
        assertEquals(Taxon.FONGE, groupeDeDepart(Taxon.FONGE, emptyList()))
        assertEquals("Espèce observée (Autres invertébrés)", libelleChampEspece(Taxon.INVERTEBRES, false))
        assertEquals("Nom scientifique (Amphibiens)", libelleChampEspece(Taxon.BATRACIEN, true))
    }
}
