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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/InvalidationServeurTest.kt) :
// Robolectric remplacé par ServicesDeTest, la fonction invaliderCachesSession par la classe
// InvalidationCaches, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.store.NomValeur
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Changement d'identité serveur ([InvalidationCaches.invaliderCachesSession], appelée par
 *  l'écran de config quand URL/login/mdp changent) : les id_nomenclature sont propres à chaque
 *  instance GeoNature et GeoNatureUpload.envoyer préfère le cache disque au réseau — un cache
 *  survivant enverrait les FK de l'ancienne instance (500 opaque côté serveur). */
class InvalidationServeurTest {

    private val t = ServicesDeTest()
    private val nomenclatures get() = t.services.nomenclatures

    @AfterTest
    fun nettoyer() = t.nettoyer()

    @BeforeTest
    fun setup() {
        nomenclatures.vider()
    }

    @Test
    fun changement_de_serveur_purge_les_nomenclatures_valeurs_et_defauts() {
        // Cache peuplé depuis le serveur A (valeurs + défauts par module).
        nomenclatures.setAll(mapOf("SEXE" to listOf(NomValeur(123, "mâle"))))
        nomenclatures.setDefauts(mapOf("STATUT_OBS" to "456"))
        assertTrue(nomenclatures.estDisponible)

        // L'utilisateur pointe l'app vers le serveur B.
        t.services.invalidationCaches.invaliderCachesSession()

        // Plus aucun id de l'instance A ne doit pouvoir partir dans un envoi : c'est le
        // cache vide qui force GeoNatureUpload à resynchroniser depuis l'instance B.
        assertFalse(nomenclatures.estDisponible)
        assertTrue(nomenclatures.get("SEXE").isEmpty())
        assertNull(nomenclatures.defautPour("STATUT_OBS"))
        assertTrue(nomenclatures.tousLesDefauts().isEmpty())
    }

    @Test
    fun la_purge_survit_au_redemarrage_pas_seulement_en_memoire() {
        nomenclatures.setAll(mapOf("SEXE" to listOf(NomValeur(123, "mâle"))))
        t.services.invalidationCaches.invaliderCachesSession()
        // Démarrage à froid simulé : ré-init depuis le disque (le memo mémoire ne doit pas
        // être le seul à avoir été purgé).
        t.redemarrer()
        assertFalse(nomenclatures.estDisponible)
        assertEquals(0, nomenclatures.count)
    }

    @Test
    fun invalidation_sans_init_prealable_ne_crashe_pas() {
        // invaliderCachesSession() peut être appelée tôt, sur des caches encore jamais écrits.
        // (Sous Android, Robolectric ne permettait pas de désinitialiser l'objet ; ici, les
        // services neufs n'ont rien écrit.)
        t.redemarrer()
        t.services.invalidationCaches.invaliderCachesSession()
    }
}
