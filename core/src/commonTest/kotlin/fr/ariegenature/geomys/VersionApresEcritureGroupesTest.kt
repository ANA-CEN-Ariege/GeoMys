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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/VersionApresEcritureGroupesTest.kt) :
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
import kotlin.test.assertNotEquals
import okio.FileSystem
import okio.SYSTEM

/**
 * ÉCRIRE LES GROUPES PÉRIME LES PROPOSITIONS DÉJÀ CALCULÉES (audit 2026-09-18, constat T6).
 *
 * `taxRef.versionDonnees` est la clé de péremption des mémos EXTERNES : `TaxRefLocal` garde
 * les quatre dernières configurations (groupe × mode × liste) pour rendre la bascule instantanée,
 * et les retrouve par une clé qui porte ce numéro. Toute écriture qui change le résultat d'un
 * calcul mémoïsé doit donc l'incrémenter.
 *
 * `ajouterGroupes` et `ajouterGroupes1etRegnes` ne le faisaient pas, alors que les propositions
 * retombent sur ces index dès que l'index par taxon est absent ou vide. Sans effet tant que la
 * synchro écrit tout d'un bloc — les écritures voisines incrémentent —, mais une écriture isolée
 * des groupes servirait d'anciennes propositions, sans erreur nulle part.
 */
class VersionApresEcritureGroupesTest {

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

    @BeforeTest
    fun setup() {
        taxRef.vider()
    }

    @Test
    fun ecrire_les_groupes_change_la_version_du_cache() {
        val avant = taxRef.versionDonnees
        taxRef.ajouterGroupes(mapOf(4319 to "Oiseaux"))
        assertNotEquals(avant, taxRef.versionDonnees, "ajouterGroupes doit périmer les mémos externes")
    }

    @Test
    fun ecrire_les_group1_et_regnes_change_la_version_du_cache() {
        val avant = taxRef.versionDonnees
        taxRef.ajouterGroupes1etRegnes(mapOf(54468 to "Insectes"), mapOf(54468 to "Animalia"))
        assertNotEquals(
            avant, taxRef.versionDonnees, "ajouterGroupes1etRegnes doit périmer les mémos externes")
    }

    /** La preuve par le COMPORTEMENT, seule mordante : sans péremption, les propositions d'un
     *  groupe déjà consulté restent celles d'avant la réécriture. Le cache est volontairement
     *  privé d'index par taxon, pour emprunter le repli qui lit les groupes. */
    @Test
    fun un_taxon_ajoute_a_un_groupe_apparait_dans_les_propositions_deja_calculees() {
        taxRef.remplacerTout(
            mapOf(
                TaxRefCache.normaliser("Muscicapa striata") to TaxRefEntry(4319, "Muscicapa striata"),
                TaxRefCache.normaliser("Parus major") to TaxRefEntry(3764, "Parus major"),
            )
        )
        taxRef.ajouterGroupes(mapOf(4319 to "Oiseaux"))

        // 1ᵉʳ appel : le mémo de TaxRefLocal retient ce résultat pour la configuration OISEAU.
        assertEquals(
            listOf("Muscicapa striata"),
            taxRefLocal.getSuggestionsTaxon(Taxon.OISEAU, scientifique = true).map { it.nom })

        // Le second oiseau rejoint le groupe — comme le ferait un rattachement corrigé à la synchro.
        taxRef.ajouterGroupes(mapOf(3764 to "Oiseaux"))

        assertEquals(
            listOf("Muscicapa striata", "Parus major"),
            taxRefLocal.getSuggestionsTaxon(Taxon.OISEAU, scientifique = true).map { it.nom }, "les propositions doivent être recalculées après une écriture des groupes")
    }
}
