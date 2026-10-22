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

// Propre au portage : GeoMys remplace GeoMys-Android sur Google Play (décision du 22 octobre 2026).

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.SaisieEnAttente
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.SYSTEM

/** Installée par-dessus GeoMys-Android, l'appli retrouve ses saisies non envoyées. */
class RepriseGeoMysAndroidTest {

    private val t = ServicesDeTest()
    private val reglages get() = t.services.socle.reglages

    @AfterTest
    fun nettoyer() = t.nettoyer()

    // Un relevé et une station tels que GeoMys-Android 1.4.7 les rangeait (Gson : les champs null
    // omis), dans ses préférences.
    private val relevesGma = """[{"id":"s1","date":1761040000000,"pointsParcours":[],
        "observations":[{"id":"o1","espece":"Merle noir","cdNom":4117,"latitude":42.95,"longitude":1.61,
                         "date":1761040000000,"notes":"Au bord du chemin","nombre":2,"envoyeeServeur":false}],
        "distanceTotale":0.0,"envoyeGeoNature":false,"estImportee":false}]"""
    private val stationsGma = """[{"id":"h1","date":1761040000000,"envoyeGeoNature":false,
        "stations":[{"id":"st1","uuidStation":"5c0de6f2-3b0c-4f8e-9d55-0a1b2c3d4e5f","date":1761040000000,
                     "geometryType":"Point","latitude":42.96,"longitude":1.6,
                     "habitats":[{"id":"hab1","cdHab":7262,"habitatLabel":"Pelouses sèches","nomCite":"Pelouse"}]}]}]"""

    @Test
    fun les_releves_et_stations_non_envoyes_sont_repris() = runTest {
        reglages.reglages("sorties_store").putString("sorties_sauvegardees", relevesGma)
        reglages.reglages("occhab_store").putString("saisies_sauvegardees", stationsGma)
        t.redemarrer()

        val releve = t.services.sortieStore.charger().single()
        assertEquals("s1", releve.id)
        val obs = releve.observations.single()
        assertEquals("Merle noir", obs.espece)
        assertEquals(4117, obs.cdNom)
        assertEquals("Au bord du chemin", obs.notes)
        val station = t.services.occHabStore.charger().single().stations.single()
        assertEquals("5c0de6f2-3b0c-4f8e-9d55-0a1b2c3d4e5f", station.uuidStation)
        assertEquals("Pelouses sèches", station.habitats.single().habitatLabel)
        // L'original reste dans les préférences.
        assertEquals(relevesGma, reglages.reglages("sorties_store").getStringOrNull("sorties_sauvegardees"))
    }

    @Test
    fun une_seule_fois_une_saisie_effacee_ne_revient_pas() = runTest {
        reglages.reglages("sorties_store").putString("sorties_sauvegardees", relevesGma)
        t.redemarrer()
        t.services.sortieStore.supprimer("s1")
        t.redemarrer()
        assertTrue(t.services.sortieStore.charger().isEmpty())
    }

    @Test
    fun jamais_par_dessus_les_saisies_de_geomys() = runTest {
        // Des relevés déjà rangés par GeoMys : les préférences de GeoMys-Android ne les remplacent pas.
        val fichier = t.dossier / "fichiers" / "sorties_store.json"
        FileSystem.SYSTEM.createDirectories(fichier.parent!!)
        FileSystem.SYSTEM.write(fichier) { writeUtf8("""[{"id":"s2","date":1,"pointsParcours":[],"observations":[]}]""") }
        reglages.reglages("sorties_store").putString("sorties_sauvegardees", relevesGma)
        t.redemarrer()
        assertEquals(listOf("s2"), t.services.sortieStore.charger().map { it.id })
    }

    @Test
    fun les_visites_des_suivis_sont_relues_sur_place() = runTest {
        // Même fichier que GeoMys-Android (filesDir/monitoring_outbox.json), au même format.
        val fichier = t.dossier / "fichiers" / "monitoring_outbox.json"
        FileSystem.SYSTEM.createDirectories(fichier.parent!!)
        FileSystem.SYSTEM.write(fichier) {
            writeUtf8(
                """[{"uuid":"v1","moduleCode":"stom","objectType":"visit","parentObjectType":"site","parentIdServeur":501,
                    "parentIdField":"id_base_site","nomsChampsSchema":["visit_date_min"],
                    "valeursJson":"{\"visit_date_min\":\"2026-10-21\"}","dateLocale":1761040000000,"etat":"PENDING"}]""",
            )
        }
        t.redemarrer()
        val visite = t.services.outbox.tout().single()
        assertEquals("v1", visite.uuid)
        assertEquals(SaisieEnAttente.Etat.PENDING, visite.etat)
        assertEquals(501, visite.parentIdServeur)
    }
}
