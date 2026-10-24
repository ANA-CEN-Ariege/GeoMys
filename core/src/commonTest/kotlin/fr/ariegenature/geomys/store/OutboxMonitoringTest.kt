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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/OutboxMonitoringTest.kt) :
// Robolectric remplacé par ServicesDeTest (file neuve à chaque test), JUnit par kotlin.test.

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.SaisieEnAttente
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** File d'attente locale des saisies Suivis : compteur « en attente » (pastille rouge), arbre
 *  parent→enfants et suppression en cascade. */
class OutboxMonitoringTest {

    private val t = ServicesDeTest()
    private val outbox get() = t.services.outbox

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun saisie(
        uuid: String,
        parent: String? = null,
        etat: SaisieEnAttente.Etat = SaisieEnAttente.Etat.PENDING,
    ) = SaisieEnAttente(
        uuid = uuid, moduleCode = "stom", objectType = "visit",
        parentUuidLocal = parent, valeursJson = "{}", etat = etat,
    )

    @Test
    fun count_en_attente_compte_pending_et_error_pas_sent() = runTest {
        outbox.ajouter(saisie("a", etat = SaisieEnAttente.Etat.PENDING))
        outbox.ajouter(saisie("b", etat = SaisieEnAttente.Etat.ERROR))
        outbox.ajouter(saisie("c", etat = SaisieEnAttente.Etat.SENT))
        assertEquals(2, outbox.countEnAttente())
        assertEquals(3, outbox.tout().size)
    }

    @Test
    fun mettre_a_jour_change_l_etat() = runTest {
        outbox.ajouter(saisie("a"))
        outbox.mettreAJour("a") { it.copy(etat = SaisieEnAttente.Etat.SENT) }
        assertEquals(0, outbox.countEnAttente())
    }

    @Test
    fun descendants_parcourt_l_arbre_en_profondeur() = runTest {
        // a → b → c (petit-enfant), a → d
        outbox.ajouter(saisie("a"))
        outbox.ajouter(saisie("b", parent = "a"))
        outbox.ajouter(saisie("c", parent = "b"))
        outbox.ajouter(saisie("d", parent = "a"))
        outbox.ajouter(saisie("autre"))
        val desc = outbox.descendants("a").toSet()
        assertEquals(setOf("b", "c", "d"), desc)
    }

    @Test
    fun supprimer_cascade_retire_l_objet_et_ses_descendants() = runTest {
        outbox.ajouter(saisie("a"))
        outbox.ajouter(saisie("b", parent = "a"))
        outbox.ajouter(saisie("c", parent = "b"))
        outbox.ajouter(saisie("seul"))
        val n = outbox.supprimerCascade("a")
        assertEquals(3, n) // a + b + c
        assertEquals(listOf("seul"), outbox.tout().map { it.uuid })
    }

    @Test
    fun retirer_une_envoyee_emporte_ses_descendants_envoyes() = runTest {
        outbox.ajouter(saisie("a", etat = SaisieEnAttente.Etat.SENT))
        outbox.ajouter(saisie("b", parent = "a", etat = SaisieEnAttente.Etat.SENT))
        outbox.ajouter(saisie("c", parent = "b", etat = SaisieEnAttente.Etat.SENT))
        outbox.ajouter(saisie("autre", etat = SaisieEnAttente.Etat.SENT))
        assertEquals(3, outbox.retirerEnvoyee("a"))
        assertEquals(listOf("autre"), outbox.tout().map { it.uuid })
    }

    @Test
    fun retirer_une_envoyee_dont_un_descendant_reste_a_envoyer_ne_retire_rien() = runTest {
        outbox.ajouter(saisie("a", etat = SaisieEnAttente.Etat.SENT))
        outbox.ajouter(saisie("b", parent = "a", etat = SaisieEnAttente.Etat.ERROR))
        assertEquals(0, outbox.retirerEnvoyee("a"))
        assertEquals(listOf("a", "b"), outbox.tout().map { it.uuid })
    }

    @Test
    fun supprimer_une_seule_entree() = runTest {
        outbox.ajouter(saisie("a"))
        outbox.ajouter(saisie("b"))
        outbox.supprimer("a")
        assertTrue(outbox.tout().none { it.uuid == "a" })
    }

    // ── Médias déjà transmis (audit 2026-09-14, R1-M3) ──

    @Test
    fun mediasARenvoyer_exclut_ceux_deja_transmis() {
        // Un « Réessayer » après un échec partiel ne doit renvoyer QUE les manquants : renvoyer la
        // liste complète empilait les doublons dans gn_commons.t_medias — trois essais échouant sur
        // la 3ᵉ photo laissaient trois exemplaires des deux premières dans la visite GeoNature.
        val s = SaisieEnAttente(
            uuid = "u1", moduleCode = "M", objectType = "visit", valeursJson = "{}",
            mediaPathsLocal = listOf("file:///a.jpg", "file:///b.jpg", "file:///c.jpg"),
            mediasEnvoyes = listOf("file:///a.jpg", "file:///b.jpg"),
        )
        assertEquals(listOf("file:///c.jpg"), s.mediasARenvoyer())
    }

    @Test
    fun mediasARenvoyer_sans_acquis_renvoie_tout() {
        // Premier essai, et saisies écrites AVANT ce champ (relues à null) : tout part.
        val neuve = SaisieEnAttente(
            uuid = "u2", moduleCode = "M", objectType = "visit", valeursJson = "{}",
            mediaPathsLocal = listOf("file:///a.jpg"),
        )
        assertEquals(listOf("file:///a.jpg"), neuve.mediasARenvoyer())
    }

    @Test
    fun mediasARenvoyer_tout_transmis_ne_renvoie_rien() {
        val s = SaisieEnAttente(
            uuid = "u3", moduleCode = "M", objectType = "visit", valeursJson = "{}",
            mediaPathsLocal = listOf("file:///a.jpg"),
            mediasEnvoyes = listOf("file:///a.jpg"),
        )
        assertTrue(s.mediasARenvoyer().isEmpty())
    }
}
