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

package fr.ariegenature.geomys.monitoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Les droits de création des Suivis, comme le `moduleCruved` du web : le C de l'objet de
 *  permission du type, sinon celui du module. */
class DroitsSuivisTest {

    // La forme de `/api/gn_commons/modules` : le cruved du module, et celui de ses objets.
    private val modules = """[
        {"module_code":"stom_ariege","cruved":{"C":0,"R":3,"U":0,"V":0,"E":3,"D":0},
         "objects":[{"code_object":"MONITORINGS_MODULES","cruved":{"C":0,"R":3}},
                    {"code_object":"MONITORINGS_SITES","cruved":{"C":0,"R":3}},
                    {"code_object":"MONITORINGS_VISITES","cruved":{"C":2,"R":3}}]},
        {"module_code":"mares_ana","cruved":{"C":1,"R":1}},
        {"module_code":"OCCTAX","cruved":{"C":true,"R":true}}
    ]"""

    private val droits = DroitsSuivis.lire(modules)
    private val niveaux = DroitsSuivis.NIVEAUX_PAR_DEFAUT

    @Test
    fun le_droit_de_l_objet_de_permission_l_emporte_sur_celui_du_module() {
        assertTrue(DroitsSuivis.peutCreer(droits, niveaux, "stom_ariege", "visit"), "MONITORINGS_VISITES : C 2")
        assertTrue(DroitsSuivis.peutCreer(droits, niveaux, "stom_ariege", "observation"), "même objet que la visite")
        assertFalse(DroitsSuivis.peutCreer(droits, niveaux, "stom_ariege", "site"), "MONITORINGS_SITES : C 0")
    }

    @Test
    fun sans_objet_de_permission_le_droit_du_module() {
        assertTrue(DroitsSuivis.peutCreer(droits, niveaux, "mares_ana", "visit"))
        assertFalse(DroitsSuivis.peutCreer(droits, niveaux, "stom_ariege", "sites_group"), "objet absent : le C 0 du module")
        assertTrue(DroitsSuivis.peutCreer(droits, niveaux, "OCCTAX", "visit"), "un booléen vaut un droit")
    }

    @Test
    fun un_protocole_absent_des_modules_de_l_utilisateur_n_a_aucun_droit() {
        assertFalse(DroitsSuivis.peutCreer(droits, niveaux, "chiro_gites", "visit"))
    }

    @Test
    fun sans_droits_lus_ou_pour_un_type_inconnu_le_serveur_tranchera() {
        assertTrue(DroitsSuivis.peutCreer(null, niveaux, "stom_ariege", "site"), "jamais synchronisé")
        assertTrue(DroitsSuivis.peutCreer(emptyMap(), niveaux, "stom_ariege", "site"), "réponse vide")
        assertTrue(DroitsSuivis.peutCreer(droits, niveaux, "stom_ariege", "nid"), "type sans objet de permission")
    }

    @Test
    fun les_objets_de_permission_du_serveur_remplacent_les_defauts() {
        val serveur = DroitsSuivis.niveaux("""{"site":"MONITORINGS_VISITES","visit":"MONITORINGS_SITES"}""")
        assertTrue(DroitsSuivis.peutCreer(droits, serveur, "stom_ariege", "site"))
        assertFalse(DroitsSuivis.peutCreer(droits, serveur, "stom_ariege", "visit"))
        assertEquals(niveaux, DroitsSuivis.niveaux(""), "rien du serveur : les défauts")
        assertEquals(niveaux, DroitsSuivis.niveaux("pas du json"))
    }

    @Test
    fun le_cache_rend_les_memes_droits() {
        assertEquals(droits, DroitsSuivis.depuisJson(DroitsSuivis.versJson(droits)))
        assertEquals(DroitsModule(0, mapOf("MONITORINGS_MODULES" to 0, "MONITORINGS_SITES" to 0, "MONITORINGS_VISITES" to 2)), droits["stom_ariege"])
    }
}
