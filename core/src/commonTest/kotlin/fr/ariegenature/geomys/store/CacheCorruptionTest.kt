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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/CacheCorruptionTest.kt) :
// Robolectric remplacé par ServicesDeTest (réglages en mémoire, fichiers dans son dossier
// temporaire, démarrage à froid par ServicesDeTest.redemarrer), JUnit par kotlin.test. Les sorties
// sont dans un fichier (sorties_store.json) et non plus dans les SharedPreferences. Les cas de la file
// d'envoi, portés au lot 1 sur une file de test (store/io/StoreFichierJsonTest.kt), le sont ici sur
// la vraie OutboxMonitoring.

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

/** Résilience des stores disque face à un cache CORROMPU : fichier ou réglage tronqué en plein
 *  arrêt forcé (JSON invalide) ou JSON lisible mais structurellement faux (entrée nulle, champ
 *  obligatoire manquant, énumération inconnue). Aucun de ces cas ne doit planter ni au chargement,
 *  ni à l'usage : on repart sur un cache vide, on écarte les entrées invalides. */
class CacheCorruptionTest {

    private val t = ServicesDeTest()
    private val systeme = FileSystem.SYSTEM

    @AfterTest
    fun nettoyer() = t.nettoyer()

    // ── NomenclatureCache (réglages) ──────────────────────────────────────────────

    private fun corrompreNomenclatures(valeur: String) {
        t.services.nomenclatures.vider() // mémo mémoire à null → la prochaine lecture relit les réglages
        t.services.socle.reglages.reglages("nomenclature_cache").apply {
            putString("nom_cache_v1", valeur)
            putString("nom_defauts_v1", valeur)
        }
    }

    @Test
    fun nomenclatures_json_tronque_repart_sur_cache_vide() {
        corrompreNomenclatures("""{"SEXE":[{"id":1,"la""") // arrêt en pleine écriture
        val nomenclatures = t.services.nomenclatures
        assertTrue(nomenclatures.get("SEXE").isEmpty())
        assertEquals(false, nomenclatures.estDisponible)
        assertEquals(null, nomenclatures.defautPour("SEXE"))
    }

    @Test
    fun nomenclatures_html_au_lieu_de_json_repart_sur_cache_vide() {
        corrompreNomenclatures("<html>quota disque plein</html>")
        val nomenclatures = t.services.nomenclatures
        assertTrue(nomenclatures.get("SEXE").isEmpty())
        // Et le cache reste utilisable en écriture après la corruption.
        nomenclatures.setAll(mapOf("SEXE" to listOf(NomValeur(1, "mâle"))))
        assertEquals(1, nomenclatures.get("SEXE").size)
    }

    // ── OutboxMonitoring (fichier monitoring_outbox.json) ─────────────────────────

    /** Écrit [contenu] à la place d'un fichier de saisies, puis simule un démarrage à froid. */
    private fun ecrireFichier(nom: String, contenu: String) {
        systeme.createDirectories(t.dossier / "fichiers")
        systeme.write(t.dossier / "fichiers" / nom) { writeUtf8(contenu) }
        t.redemarrer()
    }

    @Test
    fun outbox_json_tronque_repart_sur_file_vide() = runTest {
        ecrireFichier("monitoring_outbox.json", """[{"uuid":"u1","moduleCode":"STO""") // arrêt en pleine écriture
        assertTrue(t.services.outbox.tout().isEmpty())
        assertEquals(0, t.services.outbox.countEnAttente())
    }

    @Test
    fun outbox_entree_nulle_ou_incomplete_est_ecartee_sans_crash() = runTest {
        // JSON lisible mais invalide : entrée null, entrée sans uuid ni valeurs, état inconnu.
        ecrireFichier(
            "monitoring_outbox.json",
            """[
                null,
                {"moduleCode":"STOC"},
                {"uuid":"u-etat","moduleCode":"STOC","objectType":"visite","valeursJson":"{}","etat":"PAS_UN_ETAT"},
                {"uuid":"u-ok","moduleCode":"STOC","objectType":"visite","valeursJson":"{}","etat":"PENDING"}
            ]""",
        )
        val outbox = t.services.outbox
        val tout = outbox.tout()
        assertEquals(1, tout.size, "seule l'entrée complète doit survivre")
        assertEquals("u-ok", tout.single().uuid)
        // Et l'usage en aval ne plante pas (parcours d'arbre, compteur, mise à jour).
        assertEquals(1, outbox.countEnAttente())
        assertTrue(outbox.descendants("u-ok").isEmpty())
        outbox.mettreAJour("u-ok") { it.copy(etat = SaisieEnAttente.Etat.ERROR) }
        assertEquals(SaisieEnAttente.Etat.ERROR, outbox.tout().single().etat)
    }

    @Test
    fun outbox_brouillon_d_une_version_anterieure_charge_avec_listes_vides() = runTest {
        // Brouillon écrit AVANT la 0.10.4 : mediaPathsLocal / champsTexteLibre absents du JSON. Sous
        // Android, ces listes arrivaient à null et plantaient au premier copy() ou mediasLocaux().
        ecrireFichier(
            "monitoring_outbox.json",
            """[{"uuid":"u-legacy","moduleCode":"STOC","objectType":"visite",
                 "valeursJson":"{}","etat":"PENDING","mediaPathLocal":"file:///x.jpg"}]""",
        )
        val outbox = t.services.outbox
        val s = outbox.tout().single()
        assertTrue(s.nomsChampsSchema.isEmpty())
        assertTrue(s.champsTexteLibre.isEmpty())
        assertEquals(listOf("file:///x.jpg"), s.mediasLocaux(), "repli sur l'ancien fichier unique")
        // copy() (utilisé par toutes les transitions d'état) ne plante plus.
        outbox.mettreAJour("u-legacy") { it.copy(etat = SaisieEnAttente.Etat.SENDING) }
        assertEquals(SaisieEnAttente.Etat.SENDING, outbox.tout().single().etat)
    }

    // ── SortieStore (fichier sorties_store.json) ──────────────────────────────────

    @Test
    fun sorties_json_tronque_repart_sur_liste_vide() = runTest {
        ecrireFichier("sorties_store.json", """[{"id":"s1","observatio""")
        assertTrue(t.services.sortieStore.charger().isEmpty())
    }

    @Test
    fun sorties_entree_nulle_ou_incomplete_est_ecartee_ou_auto_reparee() = runTest {
        // Sortie a tous ses paramètres par défaut : sous Android, Gson passait par son constructeur
        // sans argument, et une entrée sans id s'auto-réparait (id régénéré, listes vides) ; kotlinx
        // fait de même. Seuls null et les champs explicitement null tombent.
        ecrireFichier("sorties_store.json", """[null, {"date":123}, {"id":"s-ok","observations":null}]""")
        val sorties = t.services.sortieStore.charger()
        assertEquals(1, sorties.size, "entrée nulle écartée, sans-id auto-réparée, observations:null écartée")
        val reparee = sorties.single()
        assertEquals(123L, reparee.date)
        assertTrue(reparee.id.isNotEmpty(), "id régénéré par la valeur par défaut")
        // Champs collection absents du JSON → utilisables (non nuls) grâce aux défauts.
        assertTrue(reparee.observations.isEmpty())
        assertTrue(reparee.pointsParcours.isEmpty())
    }
}
