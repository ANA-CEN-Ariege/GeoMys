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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/TaxRefCacheTest.kt) :
// Robolectric remplacé par un dossier temporaire et des réglages en mémoire, l'objet TaxRefCache par
// une instance, JUnit par kotlin.test.

package fr.ariegenature.geomys.store

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.SYSTEM

/** Cache TaxRef : appartenance d'un cd_nom aux listes UsersHub (alimente la visibilité des
 *  champs additionnels par taxon) + comptes par groupe (alimente la branche PLANTE). */
class TaxRefCacheTest {

    // Un référentiel neuf par test, dans un dossier temporaire (Robolectric sous Android).
    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val taxRef = TaxRefCache(dossier, MapSettings())

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    @BeforeTest
    fun setup() {
        taxRef.vider()
    }

    @Test
    fun listes_pour_cd_nom_roundtrip() {
        taxRef.ajouterListesParCdNom(mapOf(60585 to listOf(100, 200), 4001 to listOf(100)))
        assertEquals(listOf(100, 200), taxRef.listesPourCdNom(60585))
        assertEquals(listOf(100), taxRef.listesPourCdNom(4001))
    }

    @Test
    fun des_donnees_se_voient_sans_charger_le_cache() {
        // Paramètres s'ouvre sur ce signal (21 octobre) : le chargement complet prend de 10 à 30 s
        // sur un téléphone d'entrée de gamme.
        assertFalse(taxRef.aDesDonnees, "rien de chargé")
        taxRef.remplacerTout(mapOf("merle noir" to TaxRefEntry(4117, "Turdus merula", "Merle noir")))
        assertTrue(taxRef.aDesDonnees)
        assertTrue(TaxRefCache(dossier, MapSettings()).aDesDonnees, "relu par un nouveau processus, sans rien charger")
        taxRef.vider()
        assertFalse(taxRef.aDesDonnees, "« Vider le cache » efface le fichier")
    }

    @Test
    fun cd_nom_inconnu_renvoie_liste_vide() {
        assertTrue(taxRef.listesPourCdNom(999999).isEmpty())
    }

    @Test
    fun cd_noms_dans_liste_inverse_l_index() {
        taxRef.ajouterListesParCdNom(mapOf(1 to listOf(100), 2 to listOf(100, 200), 3 to listOf(200)))
        assertEquals(setOf(1, 2), taxRef.cdNomsDansListe(100))
        assertEquals(setOf(2, 3), taxRef.cdNomsDansListe(200))
        assertTrue(taxRef.cdNomsDansListe(999).isEmpty())
    }

    @Test
    fun comptes_groupes_roundtrip() {
        taxRef.comptesGroupes = mapOf("Oiseaux" to 320, "Angiospermes" to 1500)
        assertEquals(320, taxRef.comptesGroupes["Oiseaux"])
        assertEquals(1500, taxRef.comptesGroupes["Angiospermes"])
    }

    @Test
    fun vider_remet_a_zero() {
        taxRef.ajouterListesParCdNom(mapOf(1 to listOf(100)))
        taxRef.comptesGroupes = mapOf("X" to 1)
        taxRef.vider()
        assertTrue(taxRef.listesPourCdNom(1).isEmpty())
        assertTrue(taxRef.comptesGroupes.isEmpty())
    }

    // ── Recherche vocale (niveaux 1b / 3 / 2) ─────────────────────────────────────────────────
    // Les clés du cache sont stockées NORMALISÉES au sync — on reproduit ça via normaliser().

    private fun peupler(vararg noms: Pair<String, Int>) {
        taxRef.remplacerTout(noms.associate { (nom, cd) ->
            TaxRefCache.normaliser(nom) to TaxRefEntry(cd, nom, nom)
        })
    }

    @Test
    fun get_tolere_tiret_espace_et_espaces_multiples() {
        peupler("Pique-prune" to 5000) // séparateur UNIQUE (un tiret)
        // Clé stockée avec tiret ; requêtes vocales avec espace / espaces multiples / tiret.
        assertEquals(5000, taxRef.get("pique prune")?.cdNom)
        assertEquals(5000, taxRef.get("pique  prune")?.cdNom)
        assertEquals(5000, taxRef.get("Pique-prune")?.cdNom)
    }

    @Test
    fun mots_tolere_separateurs_mixtes() {
        peupler("Grand-duc d'Europe" to 3000)
        // Séparateurs MIXTES (tiret + espace) : hors du ressort de get(), résolu par l'index mots.
        assertEquals(3000, taxRef.chercherParMots(TaxRefCache.normaliser("grand duc d'europe"))?.cdNom)
    }

    @Test
    fun get_tolere_concatenation_des_separateurs() {
        peupler("Rougegorge" to 4001) // nom INPN en un mot
        assertEquals(4001, taxRef.get("rouge gorge")?.cdNom)
    }

    @Test
    fun mots_matche_nom_en_un_mot_depuis_deux_mots_dictes() {
        peupler("Rougegorge familier" to 4001, "Mésange bleue" to 4002)
        // « rouge gorge » (ASR, 2 mots) → « Rougegorge familier » (index par mots + concat).
        assertEquals(4001, taxRef.chercherParMots(TaxRefCache.normaliser("rouge gorge"))?.cdNom)
    }

    @Test
    fun mots_tolere_ordre_et_mot_isole() {
        peupler("Rougegorge familier" to 4001, "Tichodrome échelette" to 4494, "Bombus lucorum" to 240)
        // Mots inversés.
        assertEquals(4001, taxRef.chercherParMots(TaxRefCache.normaliser("familier rougegorge"))?.cdNom)
        // Un seul mot discriminant.
        assertEquals(4494, taxRef.chercherParMots(TaxRefCache.normaliser("tichodrome"))?.cdNom)
        assertEquals(240, taxRef.chercherParMots(TaxRefCache.normaliser("lucorum"))?.cdNom)
    }

    @Test
    fun mots_ambigu_ne_matche_pas() {
        peupler("Milan royal" to 2000, "Milan noir" to 2001)
        // « milan » seul désigne 2 espèces → aucun match (pas de faux positif en auto-ajout vocal).
        assertNull(taxRef.chercherParMots(TaxRefCache.normaliser("milan")))
        // …sauf si le groupe taxon restreint à un seul cd_nom.
        assertEquals(2000, taxRef.chercherParMots(TaxRefCache.normaliser("milan"), setOf(2000))?.cdNom)
    }

    @Test
    fun approche_rattrape_une_faute_de_transcription() {
        peupler("Bombus lucorum" to 240, "Mésange bleue" to 4002)
        // « lucorun » (n au lieu de m) → 1 substitution → match.
        assertEquals(240, taxRef.chercherApproche(TaxRefCache.normaliser("bombus lucorun"))?.cdNom)
    }

    @Test
    fun approche_refuse_ambiguite_et_trop_loin() {
        peupler("abcd" to 10, "abce" to 11, "Bombus lucorum" to 240)
        // Deux clés à distance 1 égale → ambigu → null (pas de faux positif).
        assertNull(taxRef.chercherApproche("abcf"))
        // Trop éloigné de tout.
        assertNull(taxRef.chercherApproche("zzzzzz"))
    }

    @Test
    fun set_concurrents_ne_perdent_aucune_entree() = runTest {
        // set() = lire-modifier-écrire de TOUTE la map ; sans le verrou de TaxRefCache, deux
        // résolutions en ligne concurrentes s'écrasent une entrée (lost update). Les fils
        // d'Android deviennent des coroutines sur Dispatchers.Default, qui tournent en parallèle.
        val nb = 8; val parThread = 40
        withContext(Dispatchers.Default) {
            (0 until nb).map { e ->
                launch {
                    for (i in 0 until parThread) taxRef.ajouter(
                        mapOf("t-$e-$i" to TaxRefEntry(1000 * e + i, "Sci $e $i")),
                    )
                }
            }.joinAll()
        }
        assertEquals(nb * parThread, taxRef.count, "aucune entrée perdue")
    }

    /** La normalisation a été réécrite sans allocation (elle est appelée sur 20 à 50 000 noms à
     *  chaque bascule de mode) : son résultat doit rester STRICTEMENT celui de l'ancienne. */
    @Test
    fun normaliser_est_equivalent_a_l_implementation_de_reference() {
        fun reference(nom: String) = nom.trim().lowercase().map { c ->
            when (c) {
                'à', 'â', 'ä' -> 'a'
                'é', 'è', 'ê', 'ë' -> 'e'
                'î', 'ï' -> 'i'
                'ô', 'ö' -> 'o'
                'ù', 'û', 'ü' -> 'u'
                'ç' -> 'c'
                else -> c
            }
        }.joinToString("")

        val echantillons = listOf(
            "", "   ", "Machaon", "  Triton palmé (Le) ", "PIÉRIS", "Œdipode", "Bombus lucorum",
            "Épervier d'Europe", "Ægolius", "Rouge-gorge familier", "ÀÂÄÉÈÊËÎÏÔÖÙÛÜÇ",
            "Cœur-de-Marie", "Sphagnum × auriculatum", "ß-test", "İstanbul", "ARGYNNE D'ÉLISE",
        )
        echantillons.forEach { assertEquals(reference(it), TaxRefCache.normaliser(it), "« $it »") }
    }
}
