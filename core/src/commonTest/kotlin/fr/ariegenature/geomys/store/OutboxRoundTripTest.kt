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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/OutboxRoundTripTest.kt) :
// Robolectric remplacé par ServicesDeTest (le redémarrage par ServicesDeTest.redemarrer), JUnit par
// kotlin.test. La comparaison champ par champ, faite par réflexion sous Android, passe par la forme
// JSON de la saisie (la réflexion n'existe pas en Kotlin commun), complétée par l'égalité de la
// data class.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.SaisieEnAttente
import fr.ariegenature.geomys.store.io.JSON_STOCKAGE
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject

/**
 * ALLER-RETOUR DISQUE de la file des Suivis : une saisie relue après redémarrage doit être
 * IDENTIQUE à celle qui a été écrite, champ par champ.
 *
 * Ce test existe à cause d'un bug terrain (2026-09-03) : sous Android, `OutboxMonitoring.normaliser()`
 * reconstruisait chaque entrée en listant ses champs un par un, et `champsManquants` y avait été
 * oublié. La saisie restait correcte tant qu'elle vivait dans le cache mémoire, puis perdait le
 * champ à la première relecture du disque — une visite « à compléter » redevenait complète et
 * pouvait partir telle quelle au serveur. Tout champ ajouté au modèle et perdu à la relecture
 * (`accepter`, `preparer`) fait échouer ce test.
 */
class OutboxRoundTripTest {

    private val t = ServicesDeTest()

    @AfterTest
    fun nettoyer() = t.nettoyer()

    /** Saisie dont CHAQUE champ porte une valeur non triviale (différente du défaut) : un champ perdu
     *  à l'aller-retour devient donc forcément détectable. */
    private fun saisieComplete() = SaisieEnAttente(
        uuid = "u-1",
        moduleCode = "chronoventaire_ana",
        objectType = "visit",
        parentObjectType = "site",
        parentIdServeur = 555,
        parentUuidLocal = "u-parent",
        parentIdField = "id_base_site",
        nomsChampsSchema = listOf("time_start", "time_end"),
        champsTexteLibre = listOf("comments"),
        valeursJson = """{"time_start":"08:00"}""",
        dateLocale = 1_700_000_000_000L,
        etat = SaisieEnAttente.Etat.ERROR,
        messageErreur = "boum",
        idServeur = 42,
        objetCree = true,
        dejaTentee = true,
        uuidPayload = "uuid-sinp",
        uuidFieldName = "uuid_base_visit",
        mediaPathLocal = "file:///legacy.jpg",
        mediaPathsLocal = listOf("file:///a.jpg", "file:///b.jpg"),
        mediasEnvoyes = listOf("file:///a.jpg"),
        mediaSchemaDotTable = "gn_monitoring.t_base_visits",
        champsManquants = listOf("Heure de fin", "Température à la fin du relevé"),
    )

    /** Force une relecture DEPUIS LE DISQUE (ce que fait un redémarrage de l'appli). */
    private fun relireDepuisLeDisque() = t.redemarrer()

    @Test
    fun tous_les_champs_survivent_a_un_redemarrage() = runTest {
        val ecrite = saisieComplete()
        assertTrue(t.services.outbox.ajouter(ecrite))
        relireDepuisLeDisque()
        val relue = t.services.outbox.tout().single { it.uuid == ecrite.uuid }

        val avant = JSON_STOCKAGE.encodeToJsonElement(SaisieEnAttente.serializer(), ecrite).jsonObject
        val apres = JSON_STOCKAGE.encodeToJsonElement(SaisieEnAttente.serializer(), relue).jsonObject
        assertTrue(avant.isNotEmpty(), "le modèle doit exposer des champs")
        for (champ in avant.keys) {
            assertEquals(
                avant[champ], apres[champ],
                "champ « $champ » PERDU à la relecture du disque — vérifier OutboxMonitoring.accepter et preparer",
            )
        }
        assertEquals(ecrite, relue)
    }

    @Test
    fun une_visite_a_completer_le_reste_apres_redemarrage() = runTest {
        // Le cas terrain exact : sans le correctif, la visite repartait « complète » et pouvait être
        // envoyée avec ses champs obligatoires vides.
        t.services.outbox.ajouter(saisieComplete())
        relireDepuisLeDisque()
        val relue = t.services.outbox.tout().single { it.uuid == "u-1" }
        assertTrue(relue.aCompleter, "la visite doit rester « à compléter »")
        assertEquals(2, relue.manquants().size)
    }

    @Test
    fun saisie_minimale_relue_sans_perte_ni_champ_fantome() = runTest {
        // Saisie aux valeurs par défaut : elle doit rester COMPLÈTE (pas de faux « à compléter »).
        val minimale = SaisieEnAttente(
            uuid = "u-2", moduleCode = "stom", objectType = "visit", valeursJson = "{}",
        )
        t.services.outbox.ajouter(minimale)
        relireDepuisLeDisque()
        val relue = t.services.outbox.tout().single { it.uuid == "u-2" }
        assertTrue(relue.manquants().isEmpty())
        assertTrue(!relue.aCompleter)
        assertEquals(SaisieEnAttente.Etat.PENDING, relue.etat)
    }
}
