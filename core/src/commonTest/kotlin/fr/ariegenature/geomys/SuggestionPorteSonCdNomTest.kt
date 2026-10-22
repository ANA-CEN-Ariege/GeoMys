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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/SuggestionPorteSonCdNomTest.kt) :
// Robolectric remplacé par un dossier temporaire et des réglages en mémoire, les objets du
// référentiel par des instances, JUnit par kotlin.test.

package fr.ariegenature.geomys

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.TaxRefEntry
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.FileSystem
import okio.SYSTEM

/**
 * LA SUGGESTION PORTE SON `cd_nom` (audit 2026-09-17, constat C2).
 *
 * Une proposition d'autocomplétion ne transportait que son TEXTE : après le clic, le nom était
 * re-résolu contre le cache, où un seul taxon garde chaque clé. Quand deux taxons du même groupe
 * ET de la même liste portent le nom, la re-résolution pouvait donc enregistrer l'AUTRE — 39 cas
 * mesurés sur le référentiel de l'appareil. Cas réel reproduit ici : « Bousier rhinocéros » est
 * le nom de *Copris lunaris* (10813), mais la clé du cache appartient à *Odonteus armiger*
 * (10534), lui aussi insecte de la liste de saisie : le tap enregistrait l'armiger.
 */
class SuggestionPorteSonCdNomTest {

    // Les objets d'Android deviennent des instances neuves par test, dans un dossier temporaire
    // et des réglages en mémoire (Robolectric sous Android).
    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val taxRef = TaxRefCache(dossier, MapSettings())
    private val nomenclatures = NomenclatureCache(MapSettings(), taxRef)
    private val taxRefLocal = TaxRefLocal(taxRef, nomenclatures)

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    private val liste = 100

    @BeforeTest
    fun setup() {
        taxRef.vider()
        taxRef.remplacerTout(
            mapOf(
                // La clé vernaculaire a été gagnée par l'armiger (plus petit cd_nom).
                TaxRefCache.normaliser("Bousier rhinocéros") to
                    TaxRefEntry(10534, "Odonteus armiger", "Bousier rhinocéros"),
                TaxRefCache.normaliser("Copris lunaris") to TaxRefEntry(10813, "Copris lunaris", null),
                TaxRefCache.normaliser("Machaon") to TaxRefEntry(54468, "Papilio machaon", "Machaon"),
            )
        )
        taxRef.ajouterVerns(
            mapOf(
                10534 to listOf("Bousier armé", "Bousier rhinocéros"),
                10813 to listOf("Bousier rhinocéros", "Copris lunaire"),
                54468 to listOf("Machaon"),
            )
        )
        taxRef.ajouterListesParCdNom(
            mapOf(10534 to listOf(liste), 10813 to listOf(liste), 54468 to listOf(liste))
        )
        taxRef.setIndexParTaxon(mapOf(Taxon.INSECTE to listOf(10534, 10813, 54468)))
    }

    @Test
    fun la_suggestion_designe_le_taxon_dont_c_est_le_nom_usuel_pas_celui_qui_detient_la_cle() {
        val suggestions = taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = false, idListeFiltre = liste)
        val bousier = suggestions.first { it.nom == "Bousier rhinocéros" }
        // Le rang départage : 1ᵉʳ nom de Copris lunaris, 2ᵉ d'Odonteus armiger.
        assertEquals(10813, bousier.cdNom)
        // Contre-épreuve : la re-résolution du seul TEXTE, elle, rend toujours l'autre taxon —
        // c'est précisément ce que le clic ne fait plus.
        assertEquals(10534, taxRef.get("Bousier rhinocéros")?.cdNom)
    }

    @Test
    fun un_nom_porte_par_plusieurs_taxons_donne_une_ligne_par_taxon_la_plus_usuelle_d_abord() {
        // Décision produit 2026-09-17 : plutôt qu'une ligne unique tranchée par une heuristique,
        // l'utilisateur voit les deux taxons et choisit — le nom scientifique affiché sous le nom
        // français rend le choix lisible.
        val suggestions = taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = false, idListeFiltre = liste)
        val bousiers = suggestions.filter { it.nom == "Bousier rhinocéros" }
        assertEquals(2, bousiers.size)
        assertEquals(listOf(10813, 10534), bousiers.map { it.cdNom })
        assertEquals("Copris lunaris", bousiers[0].secondaire)
        assertEquals("Odonteus armiger", bousiers[1].secondaire)
    }

    @Test
    fun le_nom_scientifique_accompagne_chaque_proposition_et_inversement_en_mode_sci() {
        val fr = taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = false, idListeFiltre = liste)
        assertEquals("Papilio machaon", fr.first { it.cdNom == 54468 }.secondaire)
        val sci = taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = true, idListeFiltre = liste)
        assertEquals("Machaon", sci.first { it.cdNom == 54468 }.secondaire)
    }

    @Test
    fun le_texte_affiche_reste_le_nom_choisi() {
        // toString() pilote l'affichage dans la liste ET le texte recopié dans le champ.
        val suggestions = taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = false, idListeFiltre = liste)
        assertEquals("Machaon", suggestions.first { it.cdNom == 54468 }.toString())
    }

    @Test
    fun le_mode_scientifique_propose_les_noms_scientifiques_avec_leur_cd_nom() {
        val suggestions = taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = true, idListeFiltre = liste)
        assertEquals(10813, suggestions.first { it.nom == "Copris lunaris" }.cdNom)
        assertTrue(suggestions.none { it.nom == "Bousier rhinocéros" })
    }

    @Test
    fun la_liste_de_saisie_restreint_les_propositions() {
        taxRef.ajouterListesParCdNom(
            mapOf(10534 to listOf(liste), 10813 to listOf(999), 54468 to listOf(liste))
        )
        val suggestions = taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = false, idListeFiltre = liste)
        // Copris lunaris n'est plus dans la liste : le nom revient à l'armiger, seul restant.
        assertEquals(10534, suggestions.first { it.nom == "Bousier rhinocéros" }.cdNom)
        assertTrue(suggestions.none { it.cdNom == 10813 })
    }

    @Test
    fun toutes_les_propositions_designent_un_taxon_du_groupe_et_de_la_liste() {
        val autorises = taxRef.indexParTaxon(Taxon.INSECTE, liste).orEmpty().toSet()
        taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = false, idListeFiltre = liste)
            .forEach { assertTrue(it.cdNom in autorises, "« ${it.nom} » → ${it.cdNom} hors périmètre") }
    }

    // Gardes textuelles d'Android non portées (elles lisaient la source des Fragments) : les_ecrans_de_saisie_consomment_la_suggestion_et_ne_re_resolvent_pas_son_texte.
    // Elles deviendront des tests de comportement avec les écrans communs (INVENTAIRE.md § 7.3).
}
