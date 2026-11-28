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

package fr.ariegenature.geomys.ui.suivis

import fr.ariegenature.geomys.monitoring.form.EditableField
import fr.ariegenature.geomys.monitoring.form.ViewType
import fr.ariegenature.geomys.ui.composants.SEUIL_RECHERCHE_LISTE
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Un observateur à choisir dans un formulaire des Suivis se cherche au clavier à partir de 20 noms
 *  (demande du 27 novembre 2026) ; en dessous, et pour toute autre liste, il reste un menu. Le menu du
 *  système d'iOS ne se compose pas dans les tests d'écran : la règle se vérifie ici. */
class ObservateursAFiltrerTest {

    @Test
    fun les_observateurs_se_filtrent_a_partir_de_vingt_noms() {
        val observateur = EditableField("id_observateur", ViewType.SELECT, "Observateur", effacable = true, observateurs = true)
        assertFalse(observateursAFiltrer(observateur, SEUIL_RECHERCHE_LISTE - 1), "19 noms : le menu")
        assertTrue(observateursAFiltrer(observateur, SEUIL_RECHERCHE_LISTE), "20 noms : le filtre")
        assertFalse(observateursAFiltrer(observateur.copy(observateurs = false), 200), "une autre liste reste un menu")
    }
}
