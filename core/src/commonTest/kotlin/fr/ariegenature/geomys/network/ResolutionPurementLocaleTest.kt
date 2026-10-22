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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/ResolutionPurementLocaleTest.kt) :
// Robolectric remplacé par un dossier temporaire et des réglages en mémoire, les objets du
// référentiel par des instances, JUnit par kotlin.test.

package fr.ariegenature.geomys.network

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.TaxRefLocal
import fr.ariegenature.geomys.model.Taxon
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
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.SYSTEM

/**
 * LA RÉSOLUTION D'UN NOM EST PUREMENT LOCALE (décision produit 2026-09-17).
 *
 * L'interrogation de l'API TaxHub a été retirée du chemin de saisie. Elle partait à chaque pause
 * de frappe sur un texte que le cache ne résolvait pas, avec 5 s de timeout — donc autant
 * d'attentes sur le terrain en réseau faible — et, quand elle aboutissait, réécrivait les 10 Mo
 * du cache en invalidant tous les mémos (constat C3 de l'audit). Son apport est devenu nul depuis
 * que la saisie n'accepte que les noms PROPOSÉS, lesquels sortent du cache.
 */
class ResolutionPurementLocaleTest {

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

    private val fichierCache = dossier / "cache_v3.json"

    @BeforeTest
    fun setup() {
        taxRef.vider()
        taxRef.remplacerTout(
            mapOf(
                TaxRefCache.normaliser("Machaon") to TaxRefEntry(54468, "Papilio machaon", "Machaon"),
            )
        )
        taxRef.ajouterVerns(mapOf(54468 to listOf("Machaon")))
        taxRef.setIndexParTaxon(mapOf(Taxon.INSECTE to listOf(54468)))
    }

    private fun lireCache(): String = systeme.read(fichierCache) { readUtf8() }

    /** Un serveur est configuré : avant, le nom inconnu partait vers l'API. Plus maintenant —
     *  et rien n'est écrit sur le disque au passage. */
    @Test
    fun un_nom_inconnu_n_interroge_pas_le_serveur_et_n_ecrit_rien() = runBlocking {
        val config = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
            urlServeur = "https://exemple-inexistant.invalid"
            login = "x"
            motDePasse = "y"
        }
        val avant = lireCache()
        // Un appel réseau vers un hôte inexistant coûterait plusieurs secondes ; celui-ci rend
        // la main immédiatement puisqu'il ne sort jamais du cache.
        val debut = TimeSource.Monotonic.markNow()
        val statut = taxRefService.rechercher("Espèce totalement inconnue", Taxon.INSECTE, config.listeTaxonsOcctax(null))
        val duree = debut.elapsedNow().inWholeMilliseconds
        assertEquals(TaxRefStatut.NonTrouve, statut)
        assertTrue(duree < 2000, "la résolution doit être immédiate (aucun réseau), mesuré : $duree ms")
        assertEquals(avant, lireCache(), "le cache ne doit pas être réécrit")
    }

    @Test
    fun un_nom_du_cache_est_resolu_normalement() = runBlocking {
        val statut = taxRefService.rechercher("Machaon", Taxon.INSECTE, GeoNatureConfig(MapSettings(), CoffreEnMemoire()).listeTaxonsOcctax(null))
        assertEquals(54468, (statut as TaxRefStatut.Trouve).cdNom)
        assertEquals("Papilio machaon", statut.nomScientifique)
    }

    // Gardes textuelles d'Android non portées (elles lisaient les sources) :
    // - le_service_taxref_ne_contient_plus_aucun_appel_reseau : TaxRefService ne reçoit plus aucun
    //   client HTTP (TaxRefService(taxRef, taxRefLocal)), le compilateur le garantit ;
    // - aucun_chemin_de_saisie_n_ecrit_dans_le_cache_taxref : devient un test de comportement. Pour
    //   le formulaire des Suivis, c'est ChampTaxonTest.le_champ_espece_n_ecrit_jamais_dans_le_cache
    //   (lot 4) ; pour les écrans OccTax, il viendra avec eux (inventaire/7-tests.md § 7.3).
}
