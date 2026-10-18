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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/ResolutionDansListeTest.kt) :
// Robolectric remplacé par un dossier temporaire et des réglages en mémoire, l'objet TaxRefCache par
// une instance, JUnit par kotlin.test.

package fr.ariegenature.geomys.store

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okio.FileSystem
import okio.SYSTEM

/**
 * RÉSOLUTION D'UN NOM DANS LA LISTE TAXONOMIQUE D'UN PROTOCOLE (audit 2026-09-17, constat C1).
 *
 * Le champ espèce des formulaires monitoring restreignait ses SUGGESTIONS à la liste du protocole
 * mais résolvait le nom saisi contre le cache ENTIER — la même asymétrie que le bug terrain du
 * 2026-09-16 (« gobemouche gris »), jamais portée au module Suivis. Mesuré sur le cache réel de
 * l'appareil : 12 noms de la liste STERF (109) et 17 de la liste flore (108) envoyaient à GeoNature
 * le cd_nom d'un taxon HORS protocole, souvent d'un autre règne, sans aucun signal.
 *
 * Les cas ci-dessous sont les cas RÉELS relevés, avec leurs cd_nom réels :
 *  - « Souci » : nom usuel de *Colias crocea* (641941, papillon de la liste 109) et de *Calendula*
 *    (190178, la plante, qui possède la clé du cache) ;
 *  - « Paon » : *Aglais io* (608364, le papillon) contre *Pavo cristatus* (199757, l'oiseau) ;
 *  - « Grisette » : porté par DEUX papillons du protocole, donc départage nécessaire.
 */
class ResolutionDansListeTest {

    // Un référentiel neuf par test, dans un dossier temporaire (Robolectric sous Android).
    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val taxRef = TaxRefCache(dossier, MapSettings())

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    private val steRF = 109
    private val flore = 108

    @BeforeTest
    fun setup() {
        taxRef.vider()
        // Le cache tel qu'il est sur l'appareil : les clés « souci » et « paon » appartiennent à
        // la plante et à l'oiseau ; les papillons ne gardent que leur clé scientifique.
        taxRef.remplacerTout(
            mapOf(
                TaxRefCache.normaliser("Souci") to TaxRefEntry(190178, "Calendula", "Souci"),
                TaxRefCache.normaliser("Paon") to TaxRefEntry(199757, "Pavo cristatus", "Paon"),
                TaxRefCache.normaliser("Genette") to TaxRefEntry(60831, "Genetta genetta", "Genette"),
                TaxRefCache.normaliser("Colias crocea") to TaxRefEntry(641941, "Colias crocea", null),
                TaxRefCache.normaliser("Aglais io") to TaxRefEntry(608364, "Aglais io", null),
                TaxRefCache.normaliser("Carcharodus alceae") to TaxRefEntry(53291, "Carcharodus alceae", null),
                TaxRefCache.normaliser("Erynnis tages") to TaxRefEntry(53307, "Erynnis tages", null),
                TaxRefCache.normaliser("Genista pilosa") to TaxRefEntry(99798, "Genista pilosa", null),
            )
        )
        taxRef.ajouterVerns(
            mapOf(
                190178 to listOf("Souci"),
                199757 to listOf("Paon"),
                60831 to listOf("Genette"),
                641941 to listOf("Souci"),
                608364 to listOf("Paon du jour", "Paon"),
                // « Grisette » n'est que le 2ᵉ nom du premier papillon et le 1ᵉʳ du second :
                // c'est ce rang qui doit départager, pas l'ordre d'itération d'un Set.
                53291 to listOf("Hespérie de l'Alcée", "Grisette"),
                53307 to listOf("Grisette", "Point de Hongrie"),
                99798 to listOf("Genette", "Genêt poilu"),
            )
        )
        taxRef.ajouterListesParCdNom(
            mapOf(
                641941 to listOf(100, 109),
                608364 to listOf(100, 109),
                53291 to listOf(100, 109),
                53307 to listOf(100, 109),
                190178 to listOf(100, 108),
                99798 to listOf(100, 108),
                199757 to listOf(100),
                60831 to listOf(100),
            )
        )
    }

    @Test
    fun souci_dans_le_protocole_papillons_designe_le_papillon() {
        // Avant le correctif : 190178 Calendula — une plante envoyée en observation de papillon.
        assertEquals(641941, taxRef.getDansListe("Souci", steRF)?.cdNom)
        assertEquals("Colias crocea", taxRef.getDansListe("Souci", steRF)?.sciNom)
        // Contre-épreuve : hors protocole, la clé appartient toujours à la plante.
        assertEquals(190178, taxRef.get("Souci")?.cdNom)
    }

    @Test
    fun paon_dans_le_protocole_papillons_n_est_plus_l_oiseau() {
        assertEquals(608364, taxRef.getDansListe("Paon", steRF)?.cdNom)
        assertEquals(199757, taxRef.get("Paon")?.cdNom)
    }

    @Test
    fun genette_dans_le_protocole_flore_n_est_plus_le_mammifere() {
        assertEquals(99798, taxRef.getDansListe("Genette", flore)?.cdNom)
        assertEquals(60831, taxRef.get("Genette")?.cdNom)
    }

    @Test
    fun un_nom_etranger_au_protocole_est_refuse() {
        // Le protocole impose son périmètre : un nom qui ne désigne aucun de ses taxons ne doit
        // PAS partir avec le cd_nom d'un taxon d'ailleurs. Le champ affichera « non reconnue ».
        assertNull(taxRef.getDansListe("Genette", steRF))
        assertNull(taxRef.getDansListe("Pavo cristatus", steRF))
    }

    @Test
    fun le_nom_scientifique_du_protocole_reste_resolvable() {
        assertEquals(641941, taxRef.getDansListe("Colias crocea", steRF)?.cdNom)
    }

    @Test
    fun liste_absente_du_cache_aucune_resolution() {
        // Décision produit 2026-09-17 : sans la liste, aucune proposition n'est faite, donc aucun
        // nom ne peut être accepté — plutôt qu'un repli qui laisserait passer un taxon hors
        // protocole. Le cas ne devrait pas se produire (chargement des données obligatoire).
        assertNull(taxRef.getDansListe("Souci", 999))
        assertNull(taxRef.getDansListe("Colias crocea", 999))
        assertEquals(true, taxRef.listeAbsenteDuCache(999))
        assertEquals(false, taxRef.listeAbsenteDuCache(steRF))
    }

    @Test
    fun sans_liste_le_comportement_global_est_inchange() {
        assertEquals(190178, taxRef.getDansListe("Souci", null)?.cdNom)
    }

    @Test
    fun les_variantes_d_ecriture_sont_acceptees_dans_la_liste() {
        // Mêmes écritures que la résolution globale : casse, accents, suffixe d'article INPN,
        // tiret ↔ espace (la factorisation par [variantesCle] garantit qu'elles ne divergent pas).
        assertEquals(641941, taxRef.getDansListe("souci", steRF)?.cdNom)
        assertEquals(608364, taxRef.getDansListe("Paon du jour (Le)", steRF)?.cdNom)
        assertEquals(608364, taxRef.getDansListe("paon-du-jour", steRF)?.cdNom)
    }

    @Test
    fun deux_taxons_du_protocole_portent_le_nom_le_rang_departage() {
        // « Grisette » est le 1ᵉʳ nom d'Erynnis tages et le 2ᵉ de Carcharodus alceae : TaxRef
        // énumère nom_vern par ordre de préférence, le premier est le nom usuel du taxon.
        assertEquals(53307, taxRef.getDansListe("Grisette", steRF)?.cdNom)
    }

    @Test
    fun le_nom_affiche_reste_celui_que_l_utilisateur_a_saisi() {
        assertEquals("Souci", taxRef.getDansListe("Souci", steRF)?.nomFrOriginal)
    }

    @Test
    fun un_taxon_de_la_liste_sans_entree_propre_reste_resolvable() {
        // Cas mesuré (audit C5) : 260 cd_nom n'ont AUCUNE clé dans le cache principal, toutes
        // captées par d'autres taxons. L'index de liste est construit depuis l'index vernaculaire
        // complet, donc il les retrouve — avec un nom scientifique vide faute de mieux.
        taxRef.ajouterVerns(mapOf(957000 to listOf("Papillon fantôme")))
        taxRef.ajouterListesParCdNom(mapOf(957000 to listOf(100, 109)))
        val e = taxRef.getDansListe("Papillon fantôme", steRF)
        assertEquals(957000, e?.cdNom)
        assertEquals("", e?.sciNom)
    }

    /**
     * RÈGLE PRODUIT 2026-09-17 : **les noms proposés et les noms acceptés sont le même
     * ensemble**. Construire les suggestions depuis l'index de résolution rend la propriété
     * vraie par construction — ce test la garde.
     */
    @Test
    fun tout_nom_propose_est_accepte_et_rien_d_autre_ne_l_est() {
        val proposes = taxRef.nomsProposablesListe(steRF)
        // Chaque proposition résout, et résout DANS la liste.
        val dansLaListe = taxRef.cdNomsDansListe(steRF)
        proposes.forEach { nom ->
            val cd = taxRef.getDansListe(nom, steRF)?.cdNom
            assertEquals(true, cd != null, "« $nom » est proposé mais ne résout pas")
            assertEquals(true, cd in dansLaListe, "« $nom » résout hors de la liste")
        }
        // Et un nom qui n'est pas proposé n'est pas accepté.
        assertNull(taxRef.getDansListe("Genette", steRF))
        assertNull(taxRef.getDansListe("Mésange bleue", steRF))
    }

    /** Le nom usuel d'un taxon du protocole est PROPOSÉ même quand un autre taxon possède la
     *  clé dans le cache principal — c'était le cas de « Souci » (Calendula la détenait). */
    @Test
    fun le_nom_usuel_d_un_taxon_du_protocole_est_propose_en_graphie_d_origine() {
        val proposes = taxRef.nomsProposablesListe(steRF)
        assertEquals(true, proposes.contains("Souci"), "« Souci » doit être proposé en STERF")
        assertEquals(true, proposes.contains("Paon"), "« Paon » doit être proposé en STERF")
        // Graphie d'origine, pas la clé normalisée du cache.
        assertEquals(true, proposes.contains("Hespérie de l'Alcée"))
        // Et rien qui n'appartienne au protocole.
        assertEquals(false, proposes.contains("Genette"), "« Genette » n'est pas du protocole")
    }

    /** Liste absente du cache : AUCUNE proposition. Proposé ⇔ accepté reste vrai, les deux
     *  ensembles étant vides — l'écran affiche « Pas de données — rechargez les données ». */
    @Test
    fun liste_absente_du_cache_aucune_proposition() {
        assertEquals(emptyList<String>(), taxRef.nomsProposablesListe(999))
        assertEquals(emptyList<Any>(), taxRef.propositionsListe(999))
    }

    /** Propositions du protocole PORTEUSES de leur taxon : une ligne par taxon quand un nom est
     *  partagé, avec le nom scientifique à afficher dessous (décision produit 2026-09-17). */
    @Test
    fun les_propositions_du_protocole_portent_leur_taxon_et_leur_nom_scientifique() {
        val props = taxRef.propositionsListe(steRF)
        // « Grisette » est porté par deux papillons du protocole : deux lignes, deux cd_nom.
        val grisettes = props.filter { it.nom == "Grisette" }
        assertEquals(2, grisettes.size)
        assertEquals(setOf(53291, 53307), grisettes.map { it.cdNom }.toSet())
        assertEquals(
            setOf("Carcharodus alceae", "Erynnis tages"),
            grisettes.mapNotNull { it.secondaire }.toSet())
        // Le nom français porte son nom scientifique en dessous…
        assertEquals("Colias crocea", props.first { it.nom == "Souci" }.secondaire)
        // …et le nom scientifique porte le nom français.
        assertEquals("Souci", props.first { it.nom == "Colias crocea" }.secondaire)
        // Chaque proposition désigne bien un taxon du protocole.
        val dansLaListe = taxRef.cdNomsDansListe(steRF)
        props.forEach { assertEquals(true, it.cdNom in dansLaListe, "« ${it.nom} » hors protocole") }
    }
}
