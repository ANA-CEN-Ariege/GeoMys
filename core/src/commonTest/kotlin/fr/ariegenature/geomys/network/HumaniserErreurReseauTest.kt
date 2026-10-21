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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/HumaniserErreurReseauTest.kt) :
// JUnit remplacé par kotlin.test.

package fr.ariegenature.geomys.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Conversion des exceptions réseau en messages utilisateur (le code HTTP reste affiché). */
class HumaniserErreurReseauTest {

    @Test
    fun auth_401_invite_a_se_reconnecter() {
        val msg = humaniserErreurReseau(GNErreur.AuthEchouee(401))
        assertTrue(msg.contains("401"))
        assertTrue(msg.lowercase().contains("reconnect"))
    }

    @Test
    fun cruved_403() {
        val msg = humaniserErreurReseau(GNErreur.EnvoiEchoue(403, "forbidden"))
        assertTrue(msg.contains("403"))
        assertTrue(msg.contains("CRUVED"))
    }

    @Test
    fun erreur_404() {
        assertTrue(humaniserErreurReseau(GNErreur.EnvoiEchoue(404, "x")).contains("404"))
    }

    @Test
    fun erreur_serveur_5xx_remonte_le_detail() {
        val msg = humaniserErreurReseau(GNErreur.EnvoiEchoue(500, "id_dataset is required"))
        assertTrue(msg.contains("500"))
        assertTrue(msg.contains("id_dataset is required"))
    }

    @Test
    fun exception_generique_sans_code() {
        val msg = humaniserErreurReseau(RuntimeException("timeout"))
        assertTrue(msg.contains("timeout"))
    }

    @Test
    fun le_refus_d_un_cadre_ferme_d_un_releve_occtax_est_traduit() {
        assertEquals(
            "Cadre d'acquisition fermé, action impossible",
            parseErreur(409, """{"description":"Cannot create or update a releve on a closed acquisition framework."}"""),
        )
        assertEquals("Autre conflit", parseErreur(409, """{"description":"Autre conflit"}"""))
    }
}
