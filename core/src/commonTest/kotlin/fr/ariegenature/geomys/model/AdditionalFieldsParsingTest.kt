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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/AdditionalFieldsParsingTest.kt) :
// Gson et AdditionalFieldsRenderer.fromJson remplacés par listeVersJson et listeDepuisJson,
// JUnit par kotlin.test.

package fr.ariegenature.geomys.model

import fr.ariegenature.geomys.store.io.listeDepuisJson
import fr.ariegenature.geomys.store.io.listeVersJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Désérialisation du cache JSON des champs additionnels OccTax (écrit par la synchro).
 *  listeDepuisJson doit être tolérant aux entrées vides / malformées (jamais d'exception). */
class AdditionalFieldsParsingTest {

    private fun fromJson(json: String) = listeDepuisJson(AdditionalFieldDef.serializer(), json)

    @Test
    fun roundtrip_serialize_puis_fromJson_preserve_les_champs() {
        val defs = listOf(
            AdditionalFieldDef(
                idField = 1, fieldName = "altitude_releve", fieldLabel = "Altitude",
                widget = WidgetType.NUMBER, required = true, widgetServeur = "number",
            ),
            AdditionalFieldDef(
                idField = 2, fieldName = "meteo", fieldLabel = "Météo",
                widget = WidgetType.SELECT, fieldValues = listOf("soleil", "pluie"),
                objectsCode = listOf("OCCTAX_RELEVE"), widgetServeur = "select",
                nomenclatureOptions = listOf("85" to "Présent"),
            ),
        )
        val json = listeVersJson(AdditionalFieldDef.serializer(), defs)
        val relu = fromJson(json)

        assertEquals(2, relu.size)
        assertEquals("altitude_releve", relu[0].fieldName)
        assertEquals(WidgetType.NUMBER, relu[0].widget)
        assertTrue(relu[0].required)
        assertEquals(listOf("soleil", "pluie"), relu[1].fieldValues)
        assertEquals(listOf("OCCTAX_RELEVE"), relu[1].objectsCode)
        // Propre au portage : tout le modèle fait l'aller-retour, paires comprises.
        assertEquals(defs, relu)
    }

    @Test
    fun json_vide_renvoie_liste_vide() {
        assertTrue(fromJson("").isEmpty())
        assertTrue(fromJson("[]").isEmpty())
    }

    @Test
    fun json_malforme_ne_jette_pas_et_renvoie_liste_vide() {
        assertTrue(fromJson("{ ceci n'est pas du json").isEmpty())
        assertTrue(fromJson("not json at all").isEmpty())
    }
}
