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

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.network.MonitoringApi.LabelResolver
import fr.ariegenature.geomys.network.MonitoringApi.MonitoringPropertySchema
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.TaxRefEntry
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okio.FileSystem
import okio.SYSTEM

/** Un taxon des Suivis s'affiche comme sur le web : le champ `taxonomy` des `display_field_names` du
 *  protocole (21 octobre ; l'appli montrait toujours « nom latin (nom vernaculaire) »). */
class ResolutionTaxonSuivisTest {

    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val taxRef = TaxRefCache(dossier, MapSettings()).apply {
        remplacerTout(
            mapOf(
                "merle noir" to TaxRefEntry(4117, "Turdus merula", "Merle noir"),
                "turdus merula" to TaxRefEntry(4117, "Turdus merula"),
                "carex pendula" to TaxRefEntry(5, "Carex pendula"),
            ),
        )
    }
    private val champ = MonitoringPropertySchema(nom = "cd_nom", typeWidget = "taxonomy", label = "Taxon", obligatoire = false, typeUtil = "taxonomy")

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    @Test
    fun un_taxon_s_affiche_selon_display_field_names() {
        val defaut = LabelResolver(taxRef = taxRef)
        assertEquals("Merle noir", defaut.resoudre(champ, "4117"), "nom_vern,lb_nom, le défaut : le nom vernaculaire")
        assertEquals("Carex pendula", defaut.resoudre(champ, "5"), "sans nom vernaculaire, le nom latin")
        val latin = LabelResolver(taxRef = taxRef, champsAffichage = mapOf("taxonomy" to "lb_nom"))
        assertEquals("Turdus merula", latin.resoudre(champ, "4117"))
        assertNull(defaut.resoudre(champ, "99"), "inconnu : le numéro reste à l'appelant")
    }
}
