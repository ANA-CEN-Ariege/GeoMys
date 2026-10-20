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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/MonitoringCacheLabelTest.kt) :
// Robolectric remplacé par MonitoringDeTest (dossier temporaire), les objets par des instances,
// JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Libellés résolus depuis le cache local Monitoring, sans réseau. */
class MonitoringCacheLabelTest {

    private val m = MonitoringDeTest()

    @AfterTest
    fun nettoyer() = m.nettoyer()

    @Test
    fun label_objet_resolu_depuis_les_properties_en_cache() {
        m.cache.setJson(
            m.cache.keyObjet("stom", "site", 5),
            """{"properties":{"base_site_name":"Forêt de Foix"}}""",
        )
        assertEquals("Forêt de Foix", m.objets.labelObjetEnCache("stom", "site", 5))
    }

    @Test
    fun label_objet_absent_du_cache_renvoie_null() {
        assertNull(m.objets.labelObjetEnCache("stom", "site", 999))
    }

    @Test
    fun label_objet_sans_nom_exploitable_renvoie_null() {
        // Pas de champ nom → l'heuristique tombe sur "#id", filtré → null.
        m.cache.setJson(
            m.cache.keyObjet("stom", "site", 6),
            """{"properties":{"altitude":1200}}""",
        )
        assertNull(m.objets.labelObjetEnCache("stom", "site", 6))
    }

    @Test
    fun id_invalide_renvoie_null() {
        assertNull(m.objets.labelObjetEnCache("stom", "site", 0))
    }

    @Test
    fun label_type_depuis_le_schema_en_cache() {
        m.cache.setJson(
            m.cache.keySchema("stom"),
            """{"site":{"label":"Site"},"visit":{"label_list":"Visites"}}""",
        )
        assertEquals("Site", m.objets.labelTypeEnCache("stom", "site"))
        // Repli sur label_list quand label est absent.
        assertEquals("Visites", m.objets.labelTypeEnCache("stom", "visit"))
    }

    @Test
    fun label_type_inconnu_ou_schema_absent_renvoie_null() {
        assertNull(m.objets.labelTypeEnCache("stom", "inconnu"))
        assertNull(m.objets.labelTypeEnCache("module_sans_schema", "site"))
    }

    /** Propre au portage : le schéma analysé reste en mémoire (une analyse par ligne figeait la liste
     *  d'un protocole de 300 sites 2 s sous iOS) ; un schéma réécrit par la synchro doit se voir. */
    @Test
    fun un_schema_reecrit_change_le_libelle_d_une_nouvelle_saisie() {
        m.cache.setJson(m.cache.keySchema("stom"), """{"visit":{"label":"Visite","genre":"F"}}""")
        assertEquals("Nouvelle visite", m.objets.libelleNouveau("stom", "visit"))
        m.cache.setJson(m.cache.keySchema("stom"), """{"visit":{"label":"Passage","genre":"M"}}""")
        assertEquals("Nouveau passage", m.objets.libelleNouveau("stom", "visit"))
    }
}
