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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/ProposeEgaleAccepteTest.kt) :
// Robolectric remplacé par un dossier temporaire et des réglages en mémoire, les objets du
// référentiel par des instances, JUnit par kotlin.test.

package fr.ariegenature.geomys

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.network.TaxRefService
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.store.CoffreEnMemoire
import fr.ariegenature.geomys.store.GeoNatureConfig
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.TaxRefEntry
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.SYSTEM

/**
 * PROPOSÉ ⇔ ACCEPTÉ, MODE D'AFFICHAGE COMPRIS (terrain 2026-09-17).
 *
 * Les propositions dépendent du mode : « noms français » n'affiche que des noms vernaculaires.
 * La résolution, elle, acceptait tout nom du cache appartenant au périmètre — donc aussi les noms
 * scientifiques. Sur le référentiel de l'appareil, 42 256 insectes sur 46 382 n'ont aucun nom
 * français : taper « Pieris » dans le groupe Insectes en mode français était accepté (cd_nom
 * 196270) alors que ce nom n'était jamais proposé.
 */
class ProposeEgaleAccepteTest {

    // Les objets d'Android deviennent des instances neuves par test, dans un dossier temporaire
    // et des réglages en mémoire (Robolectric sous Android).
    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val taxRef = TaxRefCache(dossier, MapSettings())
    private val nomenclatures = NomenclatureCache(MapSettings(), taxRef)
    private val taxRefLocal = TaxRefLocal(taxRef, nomenclatures)
    private val taxRefService = TaxRefService(taxRef, taxRefLocal)

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    private val liste = 100
    private val pieris = 196270      // genre de papillons, SANS nom français
    private val machaon = 54468      // avec nom français

    @BeforeTest
    fun setup() {
        taxRef.vider()
        taxRef.remplacerTout(
            mapOf(
                TaxRefCache.normaliser("Pieris") to TaxRefEntry(pieris, "Pieris", null),
                TaxRefCache.normaliser("Machaon") to TaxRefEntry(machaon, "Papilio machaon", "Machaon"),
                TaxRefCache.normaliser("Papilio machaon") to TaxRefEntry(machaon, "Papilio machaon", null),
            )
        )
        taxRef.ajouterVerns(mapOf(machaon to listOf("Machaon")))
        taxRef.ajouterListesParCdNom(mapOf(pieris to listOf(liste), machaon to listOf(liste)))
        taxRef.setIndexParTaxon(mapOf(Taxon.INSECTE to listOf(pieris, machaon)))
    }

    private fun config() = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply { taxaListeId = liste.toString() }

    @Test
    fun en_mode_francais_un_taxon_sans_nom_francais_n_est_ni_propose_ni_accepte() = runBlocking {
        val proposes = taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = false, idListeFiltre = liste)
        assertTrue(proposes.none { it.nom == "Pieris" }, "« Pieris » ne doit pas être proposé")
        // C'était le bug : le statut disait « ✓ Pieris • cd_nom 196270 ».
        assertEquals(
            TaxRefStatut.NonTrouve,
            taxRefService.rechercher("Pieris", Taxon.INSECTE, config(), scientifique = false))
    }

    @Test
    fun en_mode_scientifique_le_meme_nom_est_propose_et_accepte() = runBlocking {
        val proposes = taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = true, idListeFiltre = liste)
        assertTrue(proposes.any { it.nom == "Pieris" && it.cdNom == pieris })
        val statut = taxRefService.rechercher("Pieris", Taxon.INSECTE, config(), scientifique = true)
        assertEquals(pieris, (statut as TaxRefStatut.Trouve).cdNom)
    }

    @Test
    fun en_mode_francais_le_nom_scientifique_d_un_taxon_propose_n_est_pas_accepte_non_plus() = runBlocking {
        // Le Machaon EST proposé, mais par son nom français : son nom scientifique n'est pas dans
        // la liste affichée, il n'est donc pas acceptable dans ce mode.
        assertEquals(
            TaxRefStatut.NonTrouve,
            taxRefService.rechercher("Papilio machaon", Taxon.INSECTE, config(), scientifique = false))
        assertEquals(
            machaon,
            (taxRefService.rechercher("Machaon", Taxon.INSECTE, config(), scientifique = false)
                as TaxRefStatut.Trouve).cdNom)
    }

    @Test
    fun la_dictee_ne_peut_pas_non_plus_rendre_un_taxon_non_propose() = runBlocking {
        // Recherche étendue (mots + approché) : même règle, le taxon retenu doit être proposé.
        val (statut, _) = taxRefService.rechercherParmiCandidats(
            listOf("pieris"), Taxon.INSECTE, config(), scientifique = false,
        )
        assertEquals(TaxRefStatut.NonTrouve, statut)
        val (statutSci, _) = taxRefService.rechercherParmiCandidats(
            listOf("pieris"), Taxon.INSECTE, config(), scientifique = true,
        )
        assertEquals(pieris, (statutSci as TaxRefStatut.Trouve).cdNom)
    }

    @Test
    fun tout_nom_propose_est_accepte_dans_le_meme_mode() = runBlocking {
        for (scientifique in listOf(false, true)) {
            taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique, liste).forEach { s ->
                val statut = taxRefService.rechercher("" + s.nom, Taxon.INSECTE, config(), scientifique)
                assertEquals(
                    s.cdNom, (statut as? TaxRefStatut.Trouve)?.cdNom, "« ${s.nom} » est proposé (mode sci=$scientifique) mais pas accepté")
            }
        }
    }

    @Test
    fun les_propositions_sont_memorisees_par_configuration_et_perimees_par_une_synchro() {
        val fr1 = taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = false, idListeFiltre = liste)
        val sci = taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = true, idListeFiltre = liste)
        val fr2 = taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = false, idListeFiltre = liste)
        // Aller-retour sur le switch : la liste précédente est réutilisée telle quelle (même
        // instance), la bascule n'a donc rien à recalculer.
        assertSame(fr1, fr2, "le retour au mode français doit être instantané")
        assertNotSame(fr1, sci)

        // Une écriture du cache les périme : la version change, tout est recalculé.
        taxRef.ajouterVerns(mapOf(machaon to listOf("Machaon", "Grand porte-queue")))
        assertNotSame(
            fr1,
            taxRefLocal.getSuggestionsTaxon(Taxon.INSECTE, scientifique = false, idListeFiltre = liste))
    }

    // Gardes textuelles d'Android non portées (elles lisaient la source des Fragments) : les_ecrans_retirent_les_propositions_perimees_avant_de_recalculer.
    // Elles deviendront des tests de comportement avec les écrans communs (INVENTAIRE.md § 7.3).
}
