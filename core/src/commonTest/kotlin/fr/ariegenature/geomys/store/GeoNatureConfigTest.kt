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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/GeoNatureConfigTest.kt) :
// Robolectric remplacé par des réglages (MapSettings) et un coffre en mémoire ; JUnit par
// kotlin.test. Ajout : le repli mémoire quand le coffre est indisponible.

package fr.ariegenature.geomys.store

import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun est_configuree_exige_en_plus_un_dataset() {
        val c = config()
        c.urlServeur = "https://x"; c.login = "u"; c.motDePasse = "p"
        assertFalse(c.estConfiguree, "dataset manquant")
        c.idDataset = "7"
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

    @Test
    fun dataset_valide_vrai_si_cache_vide() {
        // Cache datasets absent → on ne peut pas trancher : ne pas bloquer.
        val c = config()
        c.idDataset = "416"
        assertTrue(c.datasetValide, "cache vide → considéré valide")
    }

    @Test
    fun dataset_valide_faux_si_id_absent_du_cache() {
        val c = config()
        c.datasetsCacheJson = """[{"id":5,"nom":"A"},{"id":12,"nom":"B"}]"""
        c.idDataset = "416"
        assertFalse(c.datasetValide, "416 absent du cache → invalide")
        c.idDataset = "12"
        assertTrue(c.datasetValide, "12 présent dans le cache → valide")
    }

    @Test
    fun saisie_possible_exige_dataset_present_sur_le_serveur() {
        val c = config()
        c.urlServeur = "https://x"; c.login = "u"; c.motDePasse = "p"
        c.datasetsCacheJson = """[{"id":5,"nom":"A"}]"""
        c.idDataset = "416"
        assertFalse(c.saisiePossible, "dataset fantôme → saisie interdite")
        c.idDataset = "5"
        assertTrue(c.saisiePossible, "dataset présent → saisie autorisée")
    }

    /** Configuration OCCTAX complète et cohérente avec les caches du serveur courant. */
    private fun configValideOcctax() = config().apply {
        urlServeur = "https://x"; login = "u"; motDePasse = "p"
        datasetsCacheJson = """[{"id":5,"nom":"A"}]"""
        listesCacheJson = """[{"id":100,"nom":"L"}]"""
        observateursCacheJson = """[{"idRole":3,"nomComplet":"Alice"}]"""
        idDataset = "5"; taxaListeId = "100"; observateurDefautId = "3"
    }

    @Test
    fun saisie_occtax_valide_quand_tout_present_dans_les_caches() {
        assertTrue(configValideOcctax().saisieOcctaxValide)
    }

    @Test
    fun saisie_occtax_invalide_si_cache_vide() {
        // Sélections renseignées mais aucun cache → on n'a pas la preuve qu'elles existent.
        val c = config()
        c.urlServeur = "https://x"; c.login = "u"; c.motDePasse = "p"
        c.idDataset = "5"; c.taxaListeId = "100"; c.observateurDefautId = "3"
        assertFalse(c.saisieOcctaxValide, "caches vides → non valide")
    }

    @Test
    fun saisie_occtax_invalide_si_une_selection_fantome() {
        configValideOcctax().apply { idDataset = "999" }.let {
            assertFalse(it.saisieOcctaxValide, "dataset fantôme")
        }
        configValideOcctax().apply { taxaListeId = "999" }.let {
            assertFalse(it.saisieOcctaxValide, "liste fantôme")
        }
        configValideOcctax().apply { observateurDefautId = "999" }.let {
            assertFalse(it.saisieOcctaxValide, "observateur fantôme")
        }
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
