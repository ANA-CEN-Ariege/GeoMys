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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/SortieStoreNormalisationTest.kt) :
// Robolectric remplacé par ServicesDeTest, JUnit par kotlin.test. Le JSON est écrit dans le fichier
// du store (sorties_store.json) et non plus dans la clé `sorties_sauvegardees` des SharedPreferences ;
// la quarantaine est la copie `sorties_store.json.corrupt` de FichierAtomique.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.Sortie
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.SYSTEM

/** Durcissement de SortieStore (audit 2026-07) : normalisation des vieux brouillons (champs ajoutés
 *  au fil des versions, absents des anciens JSON), quarantaine d'un JSON illisible (sinon écrasé par
 *  la sauvegarde suivante = perte définitive), et marquage partiel des obs envoyées. */
class SortieStoreNormalisationTest {

    private val t = ServicesDeTest()
    private val systeme = FileSystem.SYSTEM
    private val fichier get() = t.dossier / "fichiers" / "sorties_store.json"
    private val quarantaine get() = t.dossier / "fichiers" / "sorties_store.json.corrupt"

    @AfterTest
    fun nettoyer() = t.nettoyer()

    /** Écrit [json] à la place du fichier du store, puis redémarre les services (démarrage à froid). */
    private fun ecrireLeFichier(json: String) {
        systeme.createDirectories(t.dossier / "fichiers")
        systeme.write(fichier) { writeUtf8(json) }
        t.redemarrer()
    }

    @Test
    fun vieux_brouillon_sans_les_champs_recents_est_normalise_et_copiable() = runTest {
        // JSON d'une version ancienne : Observation SANS les listes et tables introduites depuis
        // (denombrementsAdditionnels, mediaUrisCounting0, observateursReleveIds, champs
        // additionnels…).
        val json = """[{"id":"s1","date":1,"pointsParcours":[],
            "observations":[{"id":"o1","espece":"Merle noir","latitude":42.9,"longitude":1.4,
                             "date":1,"notes":"","nombre":2}],
            "distanceTotale":0.0,"envoyeGeoNature":false,"estImportee":false}]"""
        ecrireLeFichier(json)

        val o = t.services.sortieStore.charger().single().observations.single()
        // Sous Android, avant le correctif : NPE ici (champ null) — c'est le chemin de GeoNatureUpload.
        assertTrue(o.denombrementsAdditionnels.isEmpty())
        assertTrue(o.mediaUrisCounting0.isEmpty())
        assertTrue(o.observateursReleveIds.isEmpty())
        assertTrue(o.additionalFieldsReleve.isEmpty())
        assertTrue(o.champsReleveExtra.isEmpty())
        assertFalse(o.envoyeeServeur)
        // Sous Android, avant le correctif : copy() → checkNotNullParameter (reprise d'édition d'un
        // vieux brouillon).
        assertEquals(3, o.copy(nombre = 3).nombre)
    }

    @Test
    fun json_illisible_mis_en_quarantaine_avant_d_etre_ecrase() = runTest {
        ecrireLeFichier("{pas du json[[[")

        val store = t.services.sortieStore
        assertTrue(store.charger().isEmpty(), "un store illisible charge une liste vide")
        assertTrue(systeme.exists(quarantaine), "le JSON d'origine doit être préservé en quarantaine")
        assertEquals("{pas du json[[[", systeme.read(quarantaine) { readUtf8() })

        // La sauvegarde suivante écrase le fichier (comportement attendu)… mais la quarantaine, elle,
        // garde le contenu d'ORIGINE (pas réécrasée).
        store.ajouter(Sortie(id = "s2"))
        assertEquals("{pas du json[[[", systeme.read(quarantaine) { readUtf8() })
        t.redemarrer()
        assertEquals("s2", t.services.sortieStore.charger().single().id)
    }

    @Test
    fun marquer_observations_envoyees_ne_touche_que_les_ids_cibles() = runTest {
        val store = t.services.sortieStore
        val sortie = Sortie(id = "s1", observations = listOf(
            Observation(id = "a", espece = "Merle", latitude = 0.0, longitude = 0.0),
            Observation(id = "b", espece = "Grive", latitude = 0.0, longitude = 0.0),
        ))
        store.ajouter(sortie)

        store.marquerObservationsEnvoyees("s1", listOf("a"))

        // Relecture depuis le DISQUE (démarrage à froid) : le marquage doit être durable.
        t.redemarrer()
        val relue = t.services.sortieStore.charger().single()
        assertTrue(relue.observations.first { it.id == "a" }.envoyeeServeur)
        assertFalse(relue.observations.first { it.id == "b" }.envoyeeServeur)
        assertFalse(relue.envoyeGeoNature, "la sortie ne doit PAS être marquée envoyée")
    }
}
