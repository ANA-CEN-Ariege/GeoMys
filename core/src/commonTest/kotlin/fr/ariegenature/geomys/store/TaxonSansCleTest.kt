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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/TaxonSansCleTest.kt) :
// Robolectric remplacé par un dossier temporaire et des réglages en mémoire, les objets du
// référentiel par des instances, JUnit par kotlin.test. La garde textuelle sur les sources
// d'Android devient un vrai test de la synchro, désormais portée.

package fr.ariegenature.geomys.store

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.TaxRefLocal
import fr.ariegenature.geomys.network.GeoNatureAuth
import fr.ariegenature.geomys.network.GeoNatureBrowse
import fr.ariegenature.geomys.network.GeoNatureSync
import fr.ariegenature.geomys.network.ServeurSimule
import fr.ariegenature.geomys.network.ServeurSimule.Companion.json
import fr.ariegenature.geomys.network.ServeurSimule.Companion.statut
import fr.ariegenature.geomys.model.Taxon
import fr.ariegenature.geomys.util.uuidAleatoire
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.SYSTEM

/**
 * LES TAXONS QUI N'ONT AUCUNE CLÉ DANS LE CACHE (audit 2026-09-17, constat C5).
 *
 * Le cache principal est indexé par NOM : une clé désigne un seul `cd_nom`. Un taxon dont le nom
 * scientifique ET tous les noms français sont déjà pris par d'autres n'y a donc AUCUNE entrée —
 * 260 sur les 100 468 taxons de l'appareil de référence. Il devenait invisible partout : pas
 * proposé, pas résolvable, pas affichable.
 *
 * Cas réel reproduit ici : le genre *Pieris* existe deux fois, chez les papillons (196270,
 * Schrank) et chez les plantes (196271, D.Don — l'andromède). La clé « pieris » revient au plus
 * petit cd_nom, donc au papillon ; et le nom français « Piéris » de la plante se normalise
 * EXACTEMENT en cette clé, déjà posée par la passe scientifique. La plante perd tout.
 *
 * Le correctif : la synchro persiste désormais l'index complet cd_nom → nom scientifique, comme
 * elle le faisait déjà pour les noms français. Le cache reste indexé par nom pour la recherche,
 * mais plus aucun taxon n'y perd son identité.
 */
class TaxonSansCleTest {

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

    private val papillon = 196270
    private val plante = 196271

    @BeforeTest
    fun setup() {
        taxRef.vider()
        // Le cache tel que la synchro le produit : « pieris » est allé au papillon, la plante
        // n'a aucune clé.
        taxRef.remplacerTout(
            mapOf(TaxRefCache.normaliser("Pieris") to TaxRefEntry(papillon, "Pieris", null))
        )
        taxRef.ajouterVerns(mapOf(plante to listOf("Piéris")))
        taxRef.ajouterListesParCdNom(mapOf(papillon to listOf(100), plante to listOf(100, 108)))
        taxRef.setIndexParTaxon(
            mapOf(Taxon.INSECTE to listOf(papillon), Taxon.PLANTE to listOf(plante))
        )
    }

    @Test
    fun sans_l_index_le_taxon_est_introuvable() {
        // État d'un cache écrit avant le correctif : l'index n'existe pas, rien ne change.
        assertNull(taxRef.entreesParCdNom()[plante])
        assertEquals(emptyMap<Int, String>(), taxRef.sciNomsParCdNom())
    }

    @Test
    fun avec_l_index_le_taxon_retrouve_son_identite() {
        taxRef.ajouterSciNoms(mapOf(papillon to "Pieris", plante to "Pieris"))
        val e = taxRef.entreesParCdNom()[plante]
        assertNotNull(e, "le taxon sans clé doit exister par son cd_nom")
        // assertNotNull de kotlin.test garantit le type : plus besoin des « ?. » de la version JUnit.
        assertEquals("Pieris", e.sciNom)
        assertEquals("Piéris", e.nomFrOriginal)
        // Et le taxon qui détient la clé n'est pas altéré.
        assertEquals(papillon, taxRef.get("Pieris")?.cdNom)
    }

    @Test
    fun il_redevient_resolvable_dans_son_groupe() {
        taxRef.ajouterSciNoms(mapOf(papillon to "Pieris", plante to "Pieris"))
        // Saisie en groupe PLANTE : « Piéris » doit donner l'andromède, pas le papillon. Avant,
        // le repli sautait ce taxon (`parCdNom[cd] ?: continue`) et rendait l'entrée globale.
        assertEquals(plante, taxRef.get("Piéris", setOf(plante))?.cdNom)
        // Contre-épreuve : en groupe INSECTE, c'est toujours le papillon.
        assertEquals(papillon, taxRef.get("Pieris", setOf(papillon))?.cdNom)
    }

    @Test
    fun il_redevient_proposable_dans_les_deux_modes() {
        taxRef.ajouterSciNoms(mapOf(papillon to "Pieris", plante to "Pieris"))
        val fr = taxRefLocal.getSuggestionsTaxon(Taxon.PLANTE, scientifique = false, idListeFiltre = 100)
        assertEquals(plante, fr.first { it.nom == "Piéris" }.cdNom)
        assertEquals("Pieris", fr.first { it.nom == "Piéris" }.secondaire)
        val sci = taxRefLocal.getSuggestionsTaxon(Taxon.PLANTE, scientifique = true, idListeFiltre = 100)
        assertEquals(plante, sci.first { it.nom == "Pieris" }.cdNom)
    }

    @Test
    fun il_redevient_proposable_dans_un_protocole_monitoring() {
        taxRef.ajouterSciNoms(mapOf(papillon to "Pieris", plante to "Pieris"))
        val props = taxRef.propositionsListe(108)
        assertEquals(plante, props.first { it.nom == "Piéris" }.cdNom)
        assertEquals(plante, taxRef.getDansListe("Piéris", 108)?.cdNom)
    }

    @Test
    fun l_index_est_bien_ecrit_sur_le_disque() {
        // Le mémo masquerait une non-écriture : on lit le fichier lui-même, c'est lui qui sera
        // relu au prochain démarrage.
        taxRef.ajouterSciNoms(mapOf(plante to "Pieris"))
        val fichier = dossier / "sci_v1.json"
        assertTrue(systeme.exists(fichier), "l'index doit être persisté")
        assertTrue(systeme.read(fichier) { readUtf8() }.contains("\"$plante\":\"Pieris\""))
    }

    @Test
    fun vider_le_cache_efface_aussi_l_index() {
        taxRef.ajouterSciNoms(mapOf(plante to "Pieris"))
        taxRef.vider()
        assertEquals(emptyMap<Int, String>(), taxRef.sciNomsParCdNom())
    }

    /** SITE D'APPEL : la synchro doit écrire cet index, sinon le correctif ne sert à rien. Sous
     *  Android, une garde lisait la source de GeoNatureSync ; la synchro portée se vérifie ici. */
    @Test
    fun la_synchro_persiste_l_index_des_noms_scientifiques() = runTest {
        val serveur = ServeurSimule()
        val auth = GeoNatureAuth(serveur.clients)
        val sync = GeoNatureSync(
            auth, serveur.clients, taxRef, nomenclatures, GeoNatureBrowse(auth, serveur.clients, taxRef),
        )
        val config = GeoNatureConfig(MapSettings(), CoffreEnMemoire()).apply {
            urlServeur = serveur.base
            listesCacheJson = """[{"id":108,"nom":"Flore"}]"""
            taxaListeId = "108"
            datasetsCacheJson = """[{"id":1,"nom":"JDD"}]"""
        }
        serveur.router(
            "/api/taxhub/api/taxref/version" to { statut(404) },
            "/api/taxhub/api/taxref" to {
                json(
                    """{"items":[
                      {"cd_nom":$papillon,"lb_nom":"Pieris","group2_inpn":"Insectes","regne":"Animalia"},
                      {"cd_nom":$plante,"lb_nom":"Pieris","nom_vern":"Piéris","group2_inpn":"Angiospermes","regne":"Plantae"}
                    ]}""",
                )
            },
        )
        val (nb, msg) = sync.synchroniserTaxRef(config) { _, _, _ -> }
        assertTrue(nb > 0, msg)
        // La plante n'a aucune clé dans le cache, mais l'index la garde.
        assertNull(taxRef.get("pieris")?.takeIf { it.cdNom == plante })
        assertEquals(mapOf(papillon to "Pieris", plante to "Pieris"), taxRef.sciNomsParCdNom())
    }
}
