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

// Propre au portage (demande du 23 octobre 2026) : la dictée ne cherche que parmi les noms proposés, avec la
// tolérance de GeoMys-IOS (`resoudreVocal`, `candidatsVocaux`).

package fr.ariegenature.geomys

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.network.TaxRefService
import fr.ariegenature.geomys.network.TaxRefStatut
import fr.ariegenature.geomys.store.NomenclatureCache
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.TaxRefEntry
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.SYSTEM

/** La résolution d'une dictée par le SON, et les espèces proches quand elle ne reconnaît rien : toujours
 *  dans les propositions du périmètre (groupe, mode d'affichage). */
class DicteeParLeSonTest {

    private val systeme = FileSystem.SYSTEM
    private val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-test-${uuidAleatoire()}"
    private val taxRef = TaxRefCache(dossier, MapSettings())
    private val nomenclatures = NomenclatureCache(MapSettings(), taxRef)
    private val taxRefLocal = TaxRefLocal(taxRef, nomenclatures)
    private val taxRefService = TaxRefService(taxRef, taxRefLocal)

    private class Espece(val cdNom: Int, val sciNom: String, val nomFr: String, val taxon: Taxon)

    private val especes = listOf(
        Espece(3611, "Dendrocopos major", "Pic épeiche", Taxon.OISEAU),
        Espece(3612, "Dryobates minor", "Pic épeichette", Taxon.OISEAU),
        Espece(3603, "Picus viridis", "Pic vert", Taxon.OISEAU),
        Espece(4280, "Phylloscopus collybita", "Pouillot véloce", Taxon.OISEAU),
        Espece(4146, "Curruca melanocephala", "Fauvette mélanocéphale", Taxon.OISEAU),
        Espece(4494, "Tichodroma muraria", "Tichodrome échelette", Taxon.OISEAU),
        Espece(4001, "Erithacus rubecula", "Rougegorge familier", Taxon.OISEAU),
        // Des insectes au même son que ce qui sera dicté chez les oiseaux (noms d'essai).
        Espece(900001, "Insecta essai", "Pic et pêche", Taxon.INSECTE),
        Espece(900002, "Insecta altera", "Psychorame", Taxon.INSECTE),
    )

    @AfterTest
    fun supprimerLeDossier() {
        systeme.deleteRecursively(dossier)
    }

    @BeforeTest
    fun setup() {
        taxRef.vider()
        val entrees = LinkedHashMap<String, TaxRefEntry>()
        for (e in especes) {
            entrees[TaxRefCache.normaliser(e.sciNom)] = TaxRefEntry(e.cdNom, e.sciNom)
            entrees[TaxRefCache.normaliser(e.nomFr)] = TaxRefEntry(e.cdNom, e.sciNom, e.nomFr)
        }
        taxRef.remplacerTout(entrees)
        taxRef.ajouterVerns(especes.associate { it.cdNom to listOf(it.nomFr) })
        taxRef.ajouterSciNoms(especes.associate { it.cdNom to it.sciNom })
        taxRef.setIndexParTaxon(especes.groupBy({ it.taxon }, { it.cdNom }))
    }

    private fun dicter(vararg hypotheses: String, scientifique: Boolean = false) = runBlocking {
        taxRefService.rechercherParmiCandidats(hypotheses.toList(), Taxon.OISEAU, idListe = null, scientifique = scientifique).first
    }

    @Test
    fun le_meme_son_reconnait_l_espece_du_groupe_et_pas_celle_d_un_autre() {
        val statut = dicter("pic et pêche")
        assertEquals(TaxRefStatut.Trouve(3611, "Dendrocopos major", "Pic épeiche"), statut)
    }

    @Test
    fun un_son_de_travers_est_tolere_a_la_mesure_du_nom() {
        assertEquals(4280, (dicter("pouillot vélos") as TaxRefStatut.Trouve).cdNom)
        // Une syllabe de trop, que l'orthographe ne rattrape pas (trois lettres d'écart).
        assertEquals(4146, (dicter("fauvettes mélano acéphale") as TaxRefStatut.Trouve).cdNom)
    }

    @Test
    fun ce_qui_ne_ressemble_assez_a_rien_n_est_pas_reconnu_mais_amene_les_especes_proches() {
        assertEquals(TaxRefStatut.NonTrouve, dicter("psychorame"))
        val proches = taxRefLocal.candidatsVocaux(listOf("psychorame"), Taxon.OISEAU, scientifique = false, idListeFiltre = null)
        assertEquals("Tichodrome échelette", proches.first().nom)
        assertTrue(proches.none { it.cdNom == 900002 }, "l'insecte au même son n'est pas proposé chez les oiseaux : $proches")
    }

    @Test
    fun en_noms_scientifiques_seuls_les_noms_scientifiques_sont_proches() {
        val proches = taxRefLocal.candidatsVocaux(listOf("tichodrome"), Taxon.OISEAU, scientifique = true, idListeFiltre = null)
        assertEquals("Tichodroma muraria", proches.first().nom)
        assertTrue(proches.all { p -> especes.any { it.sciNom == p.nom } }, "$proches")
    }
}
