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

package fr.ariegenature.geomys.store

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.model.OccHabStation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Logique de l'écran Paramètres passée au socle : enregistrement de la connexion
 *  (`sauvegarderChamps`), bandeau, message de liste, compteurs et « Taxons par listes ». */
class ParametresGeoNatureTest {

    private val t = ServicesDeTest()
    private val services get() = t.services
    private val config get() = t.services.config

    @AfterTest
    fun nettoyer() = t.nettoyer()

    /** Connexion enregistrée et testée, avec son jeu de données par défaut et ses caches. */
    private fun connexionEtSelections() = config.apply {
        urlServeur = "https://exemple.test"
        login = "u"
        motDePasse = "p"
        versionGeoNatureServeur = "2.17.5"
        serveurCompatible = false
        datasetsCacheJson = """[{"id":1,"nom":"JDD"}]"""
        idDataset = "1"
        nomDataset = "JDD"
        listesCacheJson = """[{"id":100,"nom":"Saisie"}]"""
        observateursCacheJson = """[{"idRole":7,"nomComplet":"DUPONT Jean"}]"""
        additionalFieldsOcctaxJson = "[]"
    }

    private fun stationServeur() = services.stationsServeur.remplacerTout(listOf(OccHabStation(idStationServeur = 12)))

    @Test
    fun rien_de_change_rien_n_est_invalide() {
        connexionEtSelections()
        stationServeur()
        assertEquals(ChangementConnexion.AUCUN, services.enregistrerConnexion("https://exemple.test", "u", "p"))
        assertEquals("2.17.5", config.versionGeoNatureServeur)
        assertFalse(config.serveurCompatible)
        assertEquals(1, services.stationsServeur.count)
    }

    @Test
    fun un_nouveau_mot_de_passe_garde_les_selections_mais_pas_la_session() {
        connexionEtSelections()
        stationServeur()
        assertEquals(ChangementConnexion.MOT_DE_PASSE, services.enregistrerConnexion("https://exemple.test", "u", "p2"))
        assertEquals("p2", config.motDePasse)
        assertEquals("1", config.idDataset)
        assertTrue(config.listesCacheJson.isNotEmpty())
        assertTrue(config.observateursCacheJson.isNotEmpty())
        // Caches de session et version de l'instance : à re-relever au prochain test.
        assertEquals(0, services.stationsServeur.count)
        assertEquals("", config.versionGeoNatureServeur)
        assertTrue(config.serveurCompatible)
    }

    @Test
    fun changer_de_serveur_reinitialise_les_selections() {
        connexionEtSelections()
        assertEquals(ChangementConnexion.IDENTITE, services.enregistrerConnexion("https://autre.test", "u", "p"))
        verifierSelectionsVides()
    }

    @Test
    fun changer_de_compte_reinitialise_les_selections() {
        connexionEtSelections()
        assertEquals(ChangementConnexion.IDENTITE, services.enregistrerConnexion("https://exemple.test", "v", "p"))
        verifierSelectionsVides()
    }

    private fun verifierSelectionsVides() {
        for (valeur in listOf(
            config.idDataset, config.nomDataset, config.datasetsCacheJson, config.listesCacheJson,
            config.observateursCacheJson, config.additionalFieldsOcctaxJson,
        )) assertEquals("", valeur)
        assertEquals("", config.versionGeoNatureServeur)
    }

    @Test
    fun bandeau_d_une_mise_a_jour_qui_lit_des_donnees_nouvelles() {
        // Depuis le 21 octobre, il suggère de recharger sans rien bloquer.
        services.taxRef.versionSauvegardee = "17"
        config.versionDonneesChargees = GeoNatureConfig.VERSION_DONNEES_REQUISE - 1
        val texte = assertNotNull(texteBandeauRechargement(config, services.taxRef))
        assertTrue(texte.startsWith("Mise à jour de l'application : rechargez les données quand vous aurez du réseau"))
        assertTrue(texte.endsWith("L'application reste utilisable d'ici là."))
        config.versionDonneesChargees = GeoNatureConfig.VERSION_DONNEES_REQUISE
        assertNull(texteBandeauRechargement(config, services.taxRef), "données à jour")
    }

    @Test
    fun bandeau_d_une_liste_incomplete_nomme_la_liste() {
        services.taxRef.listesIncompletes = listOf(100, 200)
        val texte = assertNotNull(texteBandeauRechargement(config, services.taxRef))
        assertTrue(texte.startsWith("Chargement incomplet : la liste de taxons 100, 200 n'a pas été téléchargée"))
    }

    /** Décision produit du 2026-09-18 (GeoMys-Android) : le blocage de Paramètres est entier.
     *  Le bandeau ne renvoie à aucun accès de secours vers les saisies. */
    @Test
    fun le_bandeau_n_ouvre_aucune_porte_derobee_vers_les_saisies() {
        services.taxRef.versionSauvegardee = "17"
        val requis = assertNotNull(texteBandeauRechargement(config, services.taxRef))
        services.taxRef.listesIncompletes = listOf(100)
        val incomplet = assertNotNull(texteBandeauRechargement(config, services.taxRef))
        for (texte in listOf(requis, incomplet)) assertFalse(texte.contains("ci-dessous"))
    }

    @Test
    fun sans_blocage_pas_de_bandeau() {
        assertNull(texteBandeauRechargement(config, services.taxRef))
    }

    private fun poserTaxons() {
        val taxRef = services.taxRef
        taxRef.remplacerTout(
            mapOf(
                TaxRefCache.normaliser("Machaon") to TaxRefEntry(54468, "Papilio machaon", "Machaon"),
                TaxRefCache.normaliser("Papilio machaon") to TaxRefEntry(54468, "Papilio machaon"),
                TaxRefCache.normaliser("Flambé") to TaxRefEntry(54459, "Iphiclides podalirius", "Flambé"),
            )
        )
        taxRef.ajouterListesParCdNom(mapOf(54468 to listOf(100, 200), 54459 to listOf(100)))
        taxRef.listesSynchronisees = listOf(100, 200, 300)
    }

    @Test
    fun message_de_liste() {
        poserTaxons()
        val taxRef = services.taxRef
        assertNull(messageListe(taxRef, null))
        assertEquals(MessageListe("Liste 100 : 2 taxons en cache.", avertissement = false), messageListe(taxRef, 100))
        assertEquals(MessageListe("Liste 200 : 1 taxon en cache.", avertissement = false), messageListe(taxRef, 200))
        val vide = assertNotNull(messageListe(taxRef, 300))
        assertTrue(vide.avertissement)
        assertTrue(vide.texte.startsWith("⚠ La liste 300 ne contient aucun taxon"))
        val absente = assertNotNull(messageListe(taxRef, 400))
        assertTrue(absente.avertissement)
        assertEquals(
            "⚠ La liste 400 n'est pas dans le cache (3 listes synchronisées). Resynchroniser pour l'ajouter.",
            absente.texte,
        )
    }

    @Test
    fun sans_taxons_pas_de_message_de_liste() {
        services.taxRef.listesSynchronisees = listOf(100)
        assertNull(messageListe(services.taxRef, 100))
    }

    @Test
    fun compteurs_du_cache() = runTest {
        connexionEtSelections()
        poserTaxons()
        stationServeur()
        val compteurs = services.compteursCache()
        assertEquals(CompteursCache(taxons = 2, nomenclatures = 0, protocoles = 0, listes = 1, observateurs = 1, stationsOccHab = 1), compteurs)
        assertTrue(compteurs.detailsActif)
        assertTrue(compteurs.viderActif)
        assertFalse(CompteursCache(0, 0, 0, listes = 3, observateurs = 3, stationsOccHab = 0).viderActif)
    }

    @Test
    fun taxons_par_listes_par_effectif_decroissant() = runTest {
        connexionEtSelections()
        poserTaxons()
        val contenu = assertNotNull(services.taxonsParListes())
        assertEquals("Touchez une liste pour voir ses taxons", contenu.sousTitre)
        assertEquals(
            listOf(
                LigneTaxonsParListe(100, "Saisie", "Saisie (100) — 2 taxons"),
                LigneTaxonsParListe(200, "Liste 200", "Liste 200 (200) — 1 taxon"),
            ),
            contenu.lignes,
        )
    }

    @Test
    fun sans_appartenance_aux_listes_pas_de_dialogue() = runTest {
        assertNull(services.taxonsParListes())
    }

    @Test
    fun liste_de_taxons_triee_et_recherche_mot_a_mot() = runTest {
        poserTaxons()
        ListeTaxons.oublier()
        val liste = ListeTaxons.construire(services.taxRef, 100, "Saisie")
        assertEquals("Saisie", liste.titre)
        assertEquals(listOf("Flambé", "Machaon"), liste.entrees.map { it.nomFrOriginal })
        assertEquals(listOf(54459), liste.filtrer("flambe").map { it.cdNom })
        assertEquals(listOf(54468), liste.filtrer("papilio MACH").map { it.cdNom })
        assertEquals(listOf(54468), liste.filtrer("54468").map { it.cdNom })
        assertEquals(2, liste.filtrer("  ").size)
        assertTrue(liste.filtrer("machaon flambe").isEmpty())
        ListeTaxons.oublier()
    }
}
