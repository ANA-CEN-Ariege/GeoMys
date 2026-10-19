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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/FiabiliteEnvoiTest.kt,
// cas panne_reseau_n_est_plus_presentee_comme_identifiants_expires) : les exceptions java.net
// n'existent que sur la JVM. Les autres cas sont dans commonTest (store/FiabiliteEnvoiTest.kt).

package fr.ariegenature.geomys.network

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Lot B de l'audit 2026-08-27 : une panne réseau n'est plus présentée comme des identifiants
 *  expirés. */
class FiabiliteEnvoiJvmTest {

    @Test
    fun panne_reseau_n_est_plus_presentee_comme_identifiants_expires() {
        assertFalse(humaniserErreurReseau(UnknownHostException("geonature.example")).contains("401"))
        assertTrue(humaniserErreurReseau(UnknownHostException("geonature.example")).contains("réseau"))
        assertTrue(humaniserErreurReseau(SocketTimeoutException("timeout")).contains("délai"))
        assertTrue(humaniserErreurReseau(ConnectException("refused")).contains("Connexion"))
    }
}
