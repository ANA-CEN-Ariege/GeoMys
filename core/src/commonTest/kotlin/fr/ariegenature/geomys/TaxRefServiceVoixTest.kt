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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/TaxRefServiceVoixTest.kt) :
// Robolectric remplacé par un dossier temporaire et des réglages en mémoire, les objets du
// référentiel par des instances, JUnit par kotlin.test.

package fr.ariegenature.geomys

import com.russhwolf.settings.MapSettings
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

/** Résolution vocale multi-candidats (niveau 1a) sur le cache LOCAL (gnConfig=null → pas de
 *  réseau) : [taxRefService.rechercherParmiCandidats] essaie les hypothèses ASR dans l'ordre et
 *  retourne la première résolue (via la recherche étendue mots/approché) avec son texte gagnant. */
class TaxRefServiceVoixTest {

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

    @BeforeTest
    fun setup() {
        taxRef.vider()
        taxRef.remplacerTout(
            listOf("Rougegorge familier" to 4001, "Tichodrome échelette" to 4494, "Bombus lucorum" to 240)
                .associate { (nom, cd) -> TaxRefCache.normaliser(nom) to TaxRefEntry(cd, nom, nom) }
        )
    }

    @Test
    fun candidat_3_gagnant_quand_1_et_2_ne_matchent_pas() = runBlocking {
        val (statut, gagnant) = taxRefService.rechercherParmiCandidats(
            listOf("rouje gorje", "route gorge", "rouge gorge"), taxon = null, gnConfig = null,
        )
        assertTrue(statut is TaxRefStatut.Trouve)
        // assertTrue de kotlin.test garantit le type : plus besoin du cast de la version JUnit.
        assertEquals(4001, statut.cdNom)
        assertEquals("rouge gorge", gagnant)
    }

    @Test
    fun aucun_candidat_ne_matche_renvoie_non_trouve_et_premier_candidat() = runBlocking {
        val (statut, gagnant) = taxRefService.rechercherParmiCandidats(
            listOf("zzz", "qqq"), taxon = null, gnConfig = null,
        )
        assertEquals(TaxRefStatut.NonTrouve, statut)
        assertEquals("zzz", gagnant)
    }

    @Test
    fun faute_de_transcription_rattrapee_par_l_approche() = runBlocking {
        // « bombus lucorun » (n au lieu de m) — aucun candidat exact, l'approché le rattrape.
        val (statut, gagnant) = taxRefService.rechercherParmiCandidats(
            listOf("bombus lucorun"), taxon = null, gnConfig = null,
        )
        assertEquals(240, (statut as TaxRefStatut.Trouve).cdNom)
        assertEquals("bombus lucorun", gagnant)
    }
}
