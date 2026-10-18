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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/RechargementRequisTest.kt) :
// Robolectric remplacé par ServicesDeTest, les fonctions de CachesSynchronises par sa classe,
// configurationComplete passée au socle, JUnit par kotlin.test.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mécanisme « rechargement des données requis après mise à jour » (2026-08-27) : au lancement,
 * une installation dont les données sont en retard sur [GeoNatureConfig.VERSION_DONNEES_REQUISE]
 * voit le drapeau ARMÉ (Paramètres forcé, bandeau) — SANS purge, qui n'a lieu qu'au début de la
 * synchro (SyncRunner) ; une installation vierge ou à jour n'est pas touchée ; idempotent.
 */
class RechargementRequisTest {

    private val t = ServicesDeTest()
    private val s get() = t.services
    private val cfg get() = s.config

    @AfterTest
    fun nettoyer() = t.nettoyer()

    @BeforeTest
    fun setup() {
        s.taxRef.vider()
        cfg.versionDonneesChargees = 0
        cfg.rechargementRequisApresMaj = false
    }

    @Test
    fun installation_vierge_ni_armement_ni_bandeau() {
        assertFalse(s.cachesSynchronises.armerRechargementSiRequis(cfg))
        assertFalse(cfg.rechargementRequisApresMaj)
    }

    @Test
    fun donnees_en_retard_arme_le_drapeau_sans_purger_et_une_seule_fois() {
        s.taxRef.versionSauvegardee = "taxref-v17"
        assertTrue(s.cachesSynchronises.armerRechargementSiRequis(cfg))
        assertTrue(cfg.rechargementRequisApresMaj)
        assertEquals("taxref-v17", s.taxRef.versionSauvegardee,
            "données conservées jusqu'à la synchro (purge dans SyncRunner)")
        assertFalse(configurationComplete(cfg, s.taxRef), "drapeau armé ⇒ configuration incomplète ⇒ Paramètres forcé")
        assertFalse(s.cachesSynchronises.armerRechargementSiRequis(cfg), "déjà armé → idempotent (relance, synchro en cours…)")
        assertTrue(cfg.rechargementRequisApresMaj, "le drapeau reste armé jusqu'à la synchro")
    }

    @Test
    fun donnees_a_jour_rien_ne_se_passe() {
        s.taxRef.versionSauvegardee = "taxref-v17"
        cfg.versionDonneesChargees = GeoNatureConfig.VERSION_DONNEES_REQUISE
        assertFalse(s.cachesSynchronises.armerRechargementSiRequis(cfg))
        assertEquals("taxref-v17", s.taxRef.versionSauvegardee)
        assertFalse(cfg.rechargementRequisApresMaj)
    }

    @Test
    fun purge_des_caches_synchronises_vide_taxref() {
        s.taxRef.versionSauvegardee = "taxref-v17"
        s.cachesSynchronises.viderCachesSynchronises()
        assertNull(s.taxRef.versionSauvegardee)
    }
}
