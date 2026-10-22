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

package fr.ariegenature.geomys.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.Buffer

/** Lecture en flux d'un tableau JSON, à la racine ou enveloppé : mêmes règles que le JsonReader de
 *  Gson dans GeoMys-Android (lireDatasetsStream, lireHabitatsStream). */
class TableauJsonEnFluxTest {

    private val cles = setOf("data", "items", "results")

    /** Les `id` des objets du tableau ; les autres éléments sont écartés. */
    private fun ids(json: String): List<String> =
        lireTableauEnFlux(Buffer().writeUtf8(json), cles) { element: JsonElement ->
            ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.content
        }

    @Test
    fun tableau_a_la_racine() {
        assertEquals(listOf("1", "2"), ids("""  [{"id": 1}, "x", null, {"autre": 3}, {"id": "2"}]"""))
        assertEquals(emptyList(), ids("[]"))
    }

    @Test
    fun tableau_enveloppe_et_cles_inconnues_sautees() {
        assertEquals(
            listOf("1"),
            ids("""{"total": 1, "meta": {"page": [1, 2]}, "items": [{"id": 1}], "fin": true}"""),
        )
    }

    @Test
    fun cles_lues_dans_l_ordre_du_document_jusqu_au_premier_tableau_utile() {
        // « results » vient avant « data » dans le document : c'est lui qui est lu.
        assertEquals(listOf("r"), ids("""{"results": [{"id": "r"}], "data": [{"id": "d"}]}"""))
        // Un tableau vide, ou sans élément retenu, ne compte pas : la clé suivante est lue.
        assertEquals(listOf("d"), ids("""{"items": [], "results": ["x"], "data": [{"id": "d"}]}"""))
        // Valeur null sous une clé : sautée.
        assertEquals(listOf("d"), ids("""{"items": null, "data": [{"id": "d"}]}"""))
    }

    @Test
    fun racine_ni_tableau_ni_objet_donne_une_liste_vide() {
        assertEquals(emptyList(), ids("\"texte\""))
        assertEquals(emptyList(), ids("12"))
        assertEquals(emptyList(), ids("null"))
        assertEquals(emptyList(), ids("{}"))
    }

    @Test
    fun json_invalide_leve_serialization_exception() {
        assertFailsWith<SerializationException> { ids("<html>portail captif</html>") }
        assertFailsWith<SerializationException> { ids("<html>") }
        assertFailsWith<SerializationException> { ids("") }
        assertFailsWith<SerializationException> { ids("""[{"id": 1}, {"id": 2""") }
        assertFailsWith<SerializationException> { ids("""{"data": [{"id": 1}] """) }
    }

    @Test
    fun ecart_assume_valeur_non_tableau_sous_une_cle() {
        // Gson sautait la valeur ; ici la lecture échoue (cf. lireTableauEnFlux).
        val e = runCatching { ids("""{"data": {"id": 1}, "items": [{"id": 2}]}""") }.exceptionOrNull()
        assertTrue(e is SerializationException, "obtenu : $e")
    }
}
