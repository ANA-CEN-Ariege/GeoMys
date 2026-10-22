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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/GeoNatureConfigTest.kt) :
// Robolectric remplacé par des réglages (MapSettings) et un coffre en mémoire ; JUnit par
// kotlin.test. Ajout : le repli mémoire quand le coffre est indisponible.

package fr.ariegenature.geomys.store

import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Persistance et état de la configuration GeoNature (URL/login/mdp/dataset). */
class GeoNatureConfigTest {

    // Les mêmes réglages et le même coffre pour toutes les instances : une nouvelle instance
    // relit ce qu'a écrit la précédente, comme les SharedPreferences sous Android.
    private val reglages = MapSettings()
    private val coffre = CoffreEnMemoire()

    private fun config() = GeoNatureConfig(reglages, coffre)

    @BeforeTest
    fun resetMotDePasseMemoire() {
        // Le repli mémoire est process-wide : il fuirait d'un test à l'autre. On repart propre.
        config().motDePasse = ""
    }

    @Test
    fun setters_getters_roundtrip() {
        val c = config()
        c.urlServeur = "https://geonature.test"
        c.login = "alice"
        c.motDePasse = "secret"
        c.idDataset = "12"
        // Nouvelle instance → relit les mêmes SharedPreferences.
        val c2 = config()
        assertEquals("https://geonature.test", c2.urlServeur)
        assertEquals("alice", c2.login)
        assertEquals("secret", c2.motDePasse)
        assertEquals("12", c2.idDataset)
    }

    @Test
    fun connexion_configuree_exige_url_login_mdp() {
        val c = config()
        c.urlServeur = "https://x"
        c.login = "u"
        assertFalse(c.connexionConfiguree, "mdp manquant")
        c.motDePasse = "p"
        assertTrue(c.connexionConfiguree)
    }

    @Test
    fun est_configuree_sans_jeu_de_donnees_par_defaut() {
        // Le jeu de données par défaut est facultatif : chaque saisie porte le sien.
        val c = config()
        c.urlServeur = "https://x"; c.login = "u"
        assertFalse(c.estConfiguree, "mot de passe manquant")
        c.motDePasse = "p"
        assertTrue(c.estConfiguree)
    }

    @Test
    fun id_role_defaut_moins_un() {
        assertEquals(-1, config().idRoleUtilisateur)
    }

    @Test
    fun dataset_acceptable_pour_envoi_override() {
        val c = config()
        // Cache vide → permissif (on ne bloque pas l'envoi faute de pouvoir trancher).
        assertTrue(c.datasetAcceptablePourEnvoi(416))
        c.datasetsCacheJson = """[{"id":5,"nom":"A"},{"id":12,"nom":"B"}]"""
        assertTrue(c.datasetAcceptablePourEnvoi(12), "dataset présent → accepté")
        assertFalse(c.datasetAcceptablePourEnvoi(416), "dataset fantôme → refusé")
    }

    /** Configuration OCCTAX avec un jeu de données proposé (actif, OCCTAX, créable), une liste et un
     *  observateur dans le cache du serveur courant ; aucune sélection par défaut. */
    private fun configValideOcctax() = config().apply {
        urlServeur = "https://x"; login = "u"; motDePasse = "p"
        datasetsCacheJson = """[{"id":5,"nom":"A","moduleCodes":["OCCTAX"]},{"id":6,"nom":"B","moduleCodes":["OCCHAB"]}]"""
        datasetsCreablesOcctax = setOf(5, 6)
        listesCacheJson = """[{"id":100,"nom":"Saisie"}]"""
        observateursCacheJson = """[{"idRole":8,"nom":"MARTIN Paul"}]"""
    }

    @Test
    fun saisie_occtax_exige_les_trois_selections_par_defaut() {
        // Obligatoires, sans valeur d'office (demande du 20 octobre) : le jeu et la liste se
        // choisissent, l'observateur est l'utilisateur connecté.
        val c = configValideOcctax().apply { idRoleUtilisateur = 3 }
        assertFalse(c.saisieOcctaxValide, "jeu de données et liste à choisir")
        c.idDataset = "5"
        assertFalse(c.saisieOcctaxValide, "liste à choisir")
        c.taxaListeId = "100"
        assertTrue(c.saisieOcctaxValide, "l'observateur est l'utilisateur connecté")
        c.idDataset = "6"
        assertFalse(c.saisieOcctaxValide, "jeu d'OccHab : pas proposé à un relevé")
    }

    @Test
    fun la_liste_imposee_par_le_jeu_ou_aucune_liste_en_cache_suffit() {
        val c = configValideOcctax().apply {
            idRoleUtilisateur = 3
            datasetsCacheJson = """[{"id":5,"nom":"A","moduleCodes":["OCCTAX"],"idTaxaList":100}]"""
            idDataset = "5"
        }
        assertTrue(c.listeParDefautValide, "liste imposée par le jeu")
        assertTrue(c.saisieOcctaxValide)
        c.datasetsCacheJson = """[{"id":5,"nom":"A","moduleCodes":["OCCTAX"]}]"""
        assertFalse(c.listeParDefautValide, "plus de liste imposée : à choisir")
        c.taxaListeId = "999"
        assertFalse(c.listeParDefautValide, "liste fantôme")
        c.listesCacheJson = "[]"
        assertTrue(c.listeParDefautValide, "aucune liste en cache : rien à choisir")
    }

    @Test
    fun l_observateur_par_defaut_doit_etre_connu() {
        val c = configValideOcctax().apply { idDataset = "5"; taxaListeId = "100" }
        assertFalse(c.observateurParDefautValide, "ni utilisateur connecté connu, ni choix")
        assertFalse(c.saisieOcctaxValide)
        c.idRoleUtilisateur = 3
        assertTrue(c.observateurParDefautValide, "l'utilisateur connecté")
        c.observateurDefautId = "8"
        assertTrue(c.observateurParDefautValide, "un observateur du cache")
        c.observateurDefautId = "42"
        assertFalse(c.observateurParDefautValide, "observateur fantôme")
        c.observateurDefautId = "3"
        assertTrue(c.observateurParDefautValide, "l'utilisateur connecté, même hors de la liste")
    }

    @Test
    fun saisie_occtax_invalide_sans_jeu_propose() {
        val c = config()
        c.urlServeur = "https://x"; c.login = "u"; c.motDePasse = "p"
        assertFalse(c.saisieOcctaxValide, "caches vides → non valide")
        c.datasetsCacheJson = """[{"id":6,"nom":"B","moduleCodes":["OCCHAB"]},{"id":7,"nom":"C","actif":false,"moduleCodes":["OCCTAX"]}]"""
        c.datasetsCreablesOcctax = setOf(6, 7)
        assertFalse(c.saisieOcctaxValide, "ni jeu OCCTAX ni jeu actif")
    }

    @Test
    fun le_jeu_par_defaut_fantome_ne_s_impose_pas() {
        val c = configValideOcctax()
        assertNull(c.idDatasetParDefaut, "aucun par défaut")
        c.idDataset = "5"
        assertEquals(5, c.idDatasetParDefaut)
        c.idDataset = "6"
        assertNull(c.idDatasetParDefaut, "jeu d'OccHab : pas proposé à un relevé")
        c.idDataset = "999"
        assertNull(c.idDatasetParDefaut, "jeu fantôme")
    }

    @Test
    fun l_observateur_par_defaut_est_l_utilisateur_connecte_sauf_autre_choix() {
        val c = configValideOcctax().apply {
            idRoleUtilisateur = 3
            nomUtilisateur = "DUPONT Alice"
        }
        assertEquals(3, c.idObservateurParDefaut)
        assertEquals("DUPONT Alice", c.nomObservateurParDefaut)
        c.observateurDefautId = "8"
        c.observateurDefautNom = "MARTIN Paul"
        assertEquals(8, c.idObservateurParDefaut)
        assertEquals("MARTIN Paul", c.nomObservateurParDefaut)
    }

    @Test
    fun la_liste_de_taxons_du_releve_est_celle_du_jeu_sinon_celle_des_parametres_sinon_de_la_configuration() {
        val c = configValideOcctax().apply {
            datasetsCacheJson = """[{"id":5,"nom":"A","idTaxaList":100},{"id":6,"nom":"B"}]"""
        }
        assertEquals(100, c.listeTaxonsOcctax(5))
        assertNull(c.listeTaxonsOcctax(6), "sans liste du jeu ni de la configuration : tous les taxons")
        c.occtaxIdListeTaxons = 200
        assertEquals(200, c.listeTaxonsOcctax(6))
        assertEquals(200, c.listeTaxonsOcctax(null))
        assertEquals(100, c.listeTaxonsOcctax(5), "celle du jeu d'abord")
        c.taxaListeId = "300"
        assertEquals(300, c.listeTaxonsOcctax(6), "la liste des Paramètres avant celle de la configuration")
        assertEquals(100, c.listeTaxonsOcctax(5), "celle du jeu reste la première")
    }

    @Test
    fun mot_de_passe_garde_en_memoire_si_le_coffre_est_indisponible() {
        coffre.operationnel = false
        val c = config()
        c.motDePasse = "secret"
        assertEquals("secret", config().motDePasse, "repli mémoire, partagé par les instances")
        assertEquals(null, coffre.contenu, "rien d'écrit dans le coffre, surtout pas en clair")
        coffre.operationnel = true
        c.motDePasse = "nouveau"
        assertEquals("nouveau", coffre.contenu, "coffre revenu : le mot de passe y retourne")
    }
}
