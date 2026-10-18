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

// Cas portés de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/CacheCorruptionTest.kt,
// partie OutboxMonitoring, et StoreConcurrenceTest.kt) : Robolectric remplacé par un dossier
// temporaire réel, les threads par des coroutines sur Dispatchers.Default.

package fr.ariegenature.geomys.store.io

import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.SYSTEM

class StoreFichierJsonTest {

    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val chemin = dossier / "monitoring_outbox.json"
    private val quarantaine = dossier / "monitoring_outbox.json.corrupt"

    @AfterTest
    fun nettoyer() {
        systeme.deleteRecursively(dossier)
    }

    private fun ecrireFichier(contenu: String) {
        systeme.createDirectories(dossier)
        systeme.write(chemin) { writeUtf8(contenu) }
    }

    /** Une nouvelle instance = un démarrage à froid : la lecture repart du disque. */
    private fun nouvelleFile() = FileDEnvoi(FichierAtomique(systeme, chemin, "FileDEnvoi"))

    private fun saisie(uuid: String) = SaisieEnAttente(uuid = uuid, moduleCode = "STOC", objectType = "visite", valeursJson = "{}")

    @Test
    fun jsonTronqueRepartSurFileVide() = runTest {
        ecrireFichier("""[{"uuid":"u1","moduleCode":"STO""") // kill en pleine écriture
        assertTrue(nouvelleFile().tout().isEmpty())
        assertEquals("""[{"uuid":"u1","moduleCode":"STO""", systeme.read(quarantaine) { readUtf8() })
    }

    @Test
    fun entreeNulleOuIncompleteEstEcarteeSansCrash() = runTest {
        // JSON parsable mais invalide : entrée null, entrée sans uuid ni valeurs, état inconnu.
        ecrireFichier(
            """[
                null,
                {"moduleCode":"STOC"},
                {"uuid":"u-etat","moduleCode":"STOC","objectType":"visite","valeursJson":"{}","etat":"PAS_UN_ETAT"},
                {"uuid":"u-ok","moduleCode":"STOC","objectType":"visite","valeursJson":"{}","etat":"PENDING"}
            ]""",
        )
        val file = nouvelleFile()
        val tout = file.tout()
        assertEquals(1, tout.size, "seule l'entrée complète doit survivre")
        assertEquals("u-ok", tout.single().uuid)
        assertTrue(systeme.exists(quarantaine), "le blob d'origine est mis de côté")
        // Et l'usage aval ne crash pas.
        file.mettreAJour("u-ok") { it.copy(etat = SaisieEnAttente.Etat.ERROR) }
        assertEquals(SaisieEnAttente.Etat.ERROR, nouvelleFile().tout().single().etat)
    }

    @Test
    fun brouillonDUneVersionAnterieureChargeAvecListesVides() = runTest {
        // Brouillon sans mediaPathsLocal ni champsTexteLibre (écrit avant la 0.10.4 sous Android).
        ecrireFichier(
            """[{"uuid":"u-legacy","moduleCode":"STOC","objectType":"visite",
                 "valeursJson":"{}","etat":"PENDING","mediaPathLocal":"file:///x.jpg"}]""",
        )
        val file = nouvelleFile()
        val s = file.tout().single()
        assertTrue(s.nomsChampsSchema.isEmpty())
        assertTrue(s.champsTexteLibre.isEmpty())
        assertEquals(listOf("file:///x.jpg"), s.mediasLocaux(), "repli legacy mono-fichier")
        file.mettreAJour("u-legacy") { it.copy(etat = SaisieEnAttente.Etat.SENDING) }
        assertEquals(SaisieEnAttente.Etat.SENDING, file.tout().single().etat)
        assertFalse(systeme.exists(quarantaine), "rien d'écarté, rien en quarantaine")
    }

    @Test
    fun laQuarantaineGardeLePremierIncident() = runTest {
        ecrireFichier("[1,")
        nouvelleFile().tout()
        ecrireFichier("[2,")
        nouvelleFile().tout()
        assertEquals("[1,", systeme.read(quarantaine) { readUtf8() })
    }

    @Test
    fun uneEcritureRateeNeMetPasLeCacheAJour() = runTest {
        val file = object : FileDEnvoi(FichierAtomique(systeme, chemin, "FileDEnvoi")) {
            override suspend fun ecrireBrut(json: String): Boolean = false
        }
        assertFalse(file.ajouter(saisie("a")), "l'appelant doit savoir que rien n'est enregistré")
        assertTrue(file.tout().isEmpty())
    }

    @Test
    fun lesSaisiesSurviventAUnRedemarrage() = runTest {
        val file = nouvelleFile()
        assertTrue(file.ajouter(saisie("a")))
        assertTrue(file.ajouter(saisie("b")))
        assertEquals(listOf("a", "b"), nouvelleFile().tout().map { it.uuid })
        assertFalse(systeme.exists(dossier / "monitoring_outbox.json.tmp"), "pas de fichier temporaire laissé")
    }

    @Test
    fun ajoutsConcurrentsNePerdentAucuneEntree() = runTest(timeout = 2.minutes) {
        val nbEcrivains = 8
        val parEcrivain = 60
        val file = nouvelleFile()
        withContext(Dispatchers.Default) {
            (0 until nbEcrivains).map { e ->
                launch { repeat(parEcrivain) { i -> file.ajouter(saisie("u-$e-$i")) } }
            }.joinAll()
        }
        val toutes = file.tout()
        assertEquals(nbEcrivains * parEcrivain, toutes.size, "aucune saisie perdue")
        assertEquals(nbEcrivains * parEcrivain, toutes.map { it.uuid }.toSet().size, "aucun doublon / uuid écrasé")
        assertEquals(nbEcrivains * parEcrivain, nouvelleFile().tout().size, "et toutes sur le disque")
    }
}

/** La file d'envoi des Suivis réduite à ce que testent ces cas : sa règle de validité (celle
 *  d'OutboxMonitoring.normaliser dans GeoMys-Android) et ses mutations. */
private open class FileDEnvoi(fichier: FichierAtomique) :
    StoreFichierJson<SaisieEnAttente>(SaisieEnAttente.serializer(), fichier, "FileDEnvoi") {

    override fun accepter(brut: JsonObject): Boolean =
        CHAMPS_REQUIS.all { brut[it] != null && brut[it] !is JsonNull } &&
            SaisieEnAttente.Etat.entries.any { it.name == brut["etat"]?.jsonPrimitive?.contentOrNull }

    suspend fun ajouter(saisie: SaisieEnAttente): Boolean = muter { it.add(saisie) }

    suspend fun mettreAJour(uuid: String, transform: (SaisieEnAttente) -> SaisieEnAttente): Boolean =
        muter { liste -> for (i in liste.indices) if (liste[i].uuid == uuid) liste[i] = transform(liste[i]) }

    suspend fun tout(): List<SaisieEnAttente> = charger()

    private companion object {
        val CHAMPS_REQUIS = listOf("uuid", "moduleCode", "objectType", "valeursJson", "etat")
    }
}
